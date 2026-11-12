"""Synthetic, privacy-safe direct Gemini Live latency benchmark.

Reads a synthetic speech WAV supplied by the developer, never logs audio or
transcripts, and reports aggregate timing only. Requires the local backend
environment and does not write to Supabase.
"""
from __future__ import annotations

import argparse
import asyncio
import base64
import json
import math
import sys
import time
import wave
from array import array
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.parse import quote

import websockets

# ``python scripts/benchmark_gemini_live.py`` places ``scripts/`` first on
# sys.path; anchor imports to this backend so a globally installed ``app.py``
# cannot shadow VerityScribe's ``app`` package.
BACKEND_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BACKEND_ROOT))

from app.core.config import settings
from app.routers.ai_consultations import _safe_live_client_config
from app.services.gemini_live import gemini_live


def _read_mono_pcm16(path: Path) -> tuple[array, int]:
    with wave.open(str(path), "rb") as source:
        if source.getsampwidth() != 2:
            raise ValueError("Synthetic WAV must be 16-bit PCM")
        channels = source.getnchannels()
        sample_rate = source.getframerate()
        raw = source.readframes(source.getnframes())
    samples = array("h")
    samples.frombytes(raw)
    if channels > 1:
        samples = array("h", (sum(samples[i:i + channels]) // channels
                              for i in range(0, len(samples), channels)))
    if sample_rate != 16000:
        target_count = round(len(samples) * 16000 / sample_rate)
        resampled = array("h")
        scale = sample_rate / 16000
        for index in range(target_count):
            position = index * scale
            left = min(int(position), len(samples) - 1)
            right = min(left + 1, len(samples) - 1)
            fraction = position - left
            resampled.append(round(samples[left] * (1 - fraction) + samples[right] * fraction))
        samples = resampled
    return samples, 16000


def _percentile(values: list[float], fraction: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    return round(ordered[math.ceil(fraction * len(ordered)) - 1], 1)


async def _wait_for_setup(socket) -> float:
    started = time.perf_counter()
    while True:
        raw = await asyncio.wait_for(socket.recv(), timeout=30)
        event = json.loads(raw.decode("utf-8") if isinstance(raw, bytes) else raw)
        if "setupComplete" in event:
            return (time.perf_counter() - started) * 1000
        if "error" in event:
            status = event["error"].get("status", "unknown")
            raise RuntimeError(f"Gemini setup rejected ({status})")


async def _run_turn(socket, audio: bytes, *, chunk_ms: int, silence_ms: int) -> dict[str, float | int]:
    samples_per_chunk = 16000 * chunk_ms // 1000
    chunk_bytes = samples_per_chunk * 2
    started = time.perf_counter()
    local_encode_ms: list[float] = []
    for offset in range(0, len(audio), chunk_bytes):
        chunk = audio[offset:offset + chunk_bytes]
        if not chunk:
            continue
        encode_start = time.perf_counter()
        frame = json.dumps({"realtimeInput": {"audio": {
            "mimeType": "audio/pcm;rate=16000",
            "data": base64.b64encode(chunk).decode("ascii"),
        }}}, separators=(",", ":"))
        local_encode_ms.append((time.perf_counter() - encode_start) * 1000)
        await socket.send(frame)
        if offset + chunk_bytes < len(audio):
            await asyncio.sleep(chunk_ms / 1000)

    speech_end = time.perf_counter()
    silence = bytes(chunk_bytes)
    for _ in range(math.ceil(silence_ms / chunk_ms)):
        await socket.send(json.dumps({"realtimeInput": {"audio": {
            "mimeType": "audio/pcm;rate=16000",
            "data": base64.b64encode(silence).decode("ascii"),
        }}}, separators=(",", ":")))
        await asyncio.sleep(chunk_ms / 1000)

    first_audio_ms = first_caption_ms = None
    received_audio_bytes = 0
    event_shapes: list[dict[str, object]] = []
    deadline = time.perf_counter() + 30
    while time.perf_counter() < deadline:
        raw = await asyncio.wait_for(socket.recv(), timeout=max(0.1, deadline - time.perf_counter()))
        event = json.loads(raw.decode("utf-8") if isinstance(raw, bytes) else raw)
        content = event.get("serverContent") or {}
        event_shapes.append({
            "top_level_keys": sorted(event.keys()),
            "server_content_keys": sorted(content.keys()) if isinstance(content, dict) else [],
            "input_caption_present": bool(content.get("inputTranscription") or content.get("interimInputTranscription")),
            "output_caption_present": bool(content.get("outputTranscription")),
            "part_keys": [sorted(part.keys()) for part in (content.get("modelTurn") or {}).get("parts", [])],
        })
        if content.get("outputTranscription") and first_caption_ms is None:
            first_caption_ms = (time.perf_counter() - speech_end) * 1000
        model_turn = content.get("modelTurn") or {}
        for part in model_turn.get("parts", []):
            inline = part.get("inlineData") or {}
            encoded = inline.get("data")
            if encoded:
                received_audio_bytes += len(base64.b64decode(encoded))
                if first_audio_ms is None:
                    first_audio_ms = (time.perf_counter() - speech_end) * 1000
        if content.get("turnComplete"):
            return {
                "first_audio_ms": round(first_audio_ms, 1) if first_audio_ms is not None else -1,
                "first_caption_ms": round(first_caption_ms, 1) if first_caption_ms is not None else -1,
                "audio_bytes": received_audio_bytes,
                "local_encode_p95_ms": round(_percentile(local_encode_ms, .95) or 0, 3),
                "streamed_speech_ms": round((speech_end - started) * 1000, 1),
                "event_shapes": event_shapes[:12],
            }
    raise TimeoutError("Gemini did not complete the synthetic test turn")


async def benchmark(path: Path, turns: int, chunk_ms: int, silence_ms: int) -> None:
    config = settings()
    if not gemini_live.initialize_client() or gemini_live.summary_client is None:
        raise RuntimeError("Configured GenAI client could not initialize")
    types = gemini_live.types
    audio_config = types.LiveConnectConfig(
        response_modalities=[types.Modality.AUDIO],
        speech_config=types.SpeechConfig(voice_config=types.VoiceConfig(
            prebuilt_voice_config=types.PrebuiltVoiceConfig(voice_name=gemini_live.voice),
        )),
        input_audio_transcription=types.AudioTranscriptionConfig(),
        output_audio_transcription=types.AudioTranscriptionConfig(),
        realtime_input_config=types.RealtimeInputConfig(
            automatic_activity_detection=types.AutomaticActivityDetection(
                disabled=False, prefix_padding_ms=200, silence_duration_ms=silence_ms,
            ),
        ),
    )
    now = datetime.now(timezone.utc)
    token_started = time.perf_counter()
    token = gemini_live.summary_client.auth_tokens.create(config=types.CreateAuthTokenConfig(
        http_options=types.HttpOptions(api_version="v1alpha", timeout=30_000),
        uses=1,
        new_session_expire_time=now + timedelta(minutes=2),
        expire_time=now + timedelta(minutes=30),
        live_connect_constraints=types.LiveConnectConstraints(
            model=gemini_live.model, config=audio_config,
        ),
    ))
    token_issuance_ms = (time.perf_counter() - token_started) * 1000
    safe_config = _safe_live_client_config(audio_config)
    model = gemini_live.model if gemini_live.model.startswith("models/") else f"models/{gemini_live.model}"
    url = (
        "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta."
        "GenerativeService.BidiGenerateContentConstrained?access_token=" + quote(token.name, safe="")
    )
    audio_samples, _ = _read_mono_pcm16(path)
    audio = audio_samples.tobytes()
    connect_started = time.perf_counter()
    async with websockets.connect(url, max_size=None, open_timeout=20, close_timeout=2) as socket:
        connected_ms = (time.perf_counter() - connect_started) * 1000
        setup_started = time.perf_counter()
        await socket.send(json.dumps({"setup": {"model": model, **safe_config}}, separators=(",", ":")))
        setup_complete_ms = await _wait_for_setup(socket)
        setup_total_ms = (time.perf_counter() - setup_started) * 1000
        results = []
        for _ in range(turns):
            results.append(await _run_turn(socket, audio, chunk_ms=chunk_ms, silence_ms=silence_ms))

    audio_latencies = [row["first_audio_ms"] for row in results if row["first_audio_ms"] >= 0]
    caption_latencies = [row["first_caption_ms"] for row in results if row["first_caption_ms"] >= 0]
    encode_samples = [row["local_encode_p95_ms"] for row in results]
    print(json.dumps({
        "model": gemini_live.model,
        "turns_requested": turns,
        "turns_with_audio": len(audio_latencies),
        "token_issuance_ms": round(token_issuance_ms, 1),
        "websocket_connected_ms": round(connected_ms, 1),
        "setup_complete_after_send_ms": round(setup_complete_ms, 1),
        "setup_complete_total_ms": round(setup_total_ms, 1),
        "speech_end_to_first_audio_ms_p50": _percentile(audio_latencies, .50),
        "speech_end_to_first_audio_ms_p95": _percentile(audio_latencies, .95) if len(audio_latencies) >= 20 else None,
        "speech_end_to_first_caption_ms_p50": _percentile(caption_latencies, .50),
        "speech_end_to_first_caption_ms_p95": _percentile(caption_latencies, .95) if len(caption_latencies) >= 20 else None,
        "local_pcm_base64_json_encode_ms_p95": _percentile(encode_samples, .95),
        "playback_startup_ms": None,
        "physical_browser_audio_verified": False,
    }, indent=2))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--audio-wav", required=True, type=Path,
                        help="Synthetic spoken WAV; it is not uploaded to app storage")
    parser.add_argument("--turns", type=int, default=5)
    parser.add_argument("--chunk-ms", type=int, choices=(20, 40), default=40)
    parser.add_argument("--silence-ms", type=int, default=600)
    args = parser.parse_args()
    if not 1 <= args.turns <= 20:
        parser.error("--turns must be between 1 and 20")
    if not 300 <= args.silence_ms <= 2000:
        parser.error("--silence-ms must be between 300 and 2000")
    asyncio.run(benchmark(args.audio_wav, args.turns, args.chunk_ms, args.silence_ms))


if __name__ == "__main__":
    main()
