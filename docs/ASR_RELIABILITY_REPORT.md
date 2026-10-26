# ASR reliability report

Date: 2026-10-26
Verdict: **MODEL SUITABILITY VERIFIED; ACCOUNT ACCESS AND LIVE TRANSCRIPTION NOT VERIFIED.** The exact fifth model in code order is `@cf/openai/whisper-large-v3-turbo`; it is a genuine batch ASR model, not an image model. `@cf/deepgram/flux` is configured fourth. Both adapters are checked against Cloudflare's documented protocols. Mocked tests pass, but no real audio request was sent, so neither model is claimed to work for the configured Cloudflare account.

## Scope and preserved safeguards

This update addresses only the two failed ASR configurations described in the request. It restores the three reported-working Cloudflare models to the default ensemble without changing their model IDs, provider route, request encoding, response parsing, or weights. Calls remain parallel and independent; failures retain per-model diagnostics, one model failure does not suppress other transcripts, and a single model is never represented as multi-model consensus. Transcript results remain drafts requiring clinician review; this work does not create or approve clinical records.

The failure evidence supplied by the user reports a 400-style `invalid_audio_or_model_request` for Cloudflare Whisper Large-v3 Turbo and `provider_billing_required` for Hugging Face `openai/whisper-large-v3-turbo`. Current repo defaults had drifted to Hugging Face ASR and could send an unverified/paid provider route. The latter is removed from the configurable ASR allowlist. Cloudflare's official Turbo example sends Base64 audio, while the old Cloudflare adapter sent a JSON array of bytes to every Whisper model. That is a plausible and directly fixable cause of Turbo's request rejection; it is not proven as the historical cause because the rejected request body was not retained.

## A. Final model configuration

| Model | Route | Request format | Intended role | Status in this run |
|---|---|---|---|---|
| `@cf/openai/whisper` | Cloudflare Workers AI batch REST | Existing JSON byte-array payload; unchanged | Existing baseline | Mock regression passed; live not tested |
| `@cf/openai/whisper-tiny-en` | Cloudflare Workers AI batch REST | Existing JSON byte-array payload; unchanged | Existing English lightweight baseline | Mock regression passed; live not tested |
| `@cf/deepgram/nova-3` | Cloudflare Workers AI batch REST | Existing raw WAV bytes with `audio/wav`; unchanged | Existing fast baseline | Mock regression passed; live not tested |
| `@cf/deepgram/flux` (4th) | Cloudflare AI Gateway WebSocket → Workers AI (`default` gateway unless overridden) | Raw signed little-endian PCM16 frames, `encoding=linear16`, `sample_rate=16000`, followed by `CloseStream`; expects a `TurnInfo` event with `event=EndOfTurn` and `transcript` | New streaming/low-latency candidate | Exact ID and task verified in official docs; adapter tested with mocked WebSocket only; account access/live not verified |
| `@cf/openai/whisper-large-v3-turbo` (5th) | Cloudflare Workers AI batch REST | Base64-encoded audio JSON, matching Cloudflare's published example | Fixed balanced batch candidate | Exact ID and batch ASR task verified in official docs; adapter tested with mocked response only; account access/live not verified |

The failed Hugging Face `openai/whisper-large-v3-turbo` route has been removed. ASR configuration now accepts only the five allowlisted Cloudflare model IDs; custom endpoints and non-Cloudflare providers are rejected so the ensemble cannot silently route to a paid external provider.

## B. Replacement rationale and limitations

1. **Low-latency candidate — `@cf/deepgram/flux`.** Cloudflare's model catalog explicitly labels the exact ID Automatic Speech Recognition, Deepgram, Cloudflare-hosted, partner, and real-time. The model supports only raw signed little-endian 16-bit PCM with `encoding=linear16`; the request also supplies `sample_rate=16000`. Its documented output includes `event` values such as `EndOfTurn` and a `transcript` string. Cloudflare's AI Gateway realtime WebSocket docs explicitly list both `@cf/deepgram/nova-3` and `@cf/deepgram/flux` as Workers AI STT models and show the `/workers-ai?model=...&encoding=linear16&sample_rate=16000` endpoint and `cf-aig-authorization` header. The app validates a 16 kHz mono PCM16 WAV container, strips the WAV header, streams its raw PCM frames, sends `CloseStream`, and reads final turn transcripts. This adapter is protocol-compatible on paper; it has not been confirmed against the user's account. Since this app uploads a completed recording, the backend replays PCM over WebSocket rather than transcribing during capture, so no real-time latency advantage is claimed. The token needs AI Gateway Run permission for this authenticated WebSocket path, in addition to Workers AI permission needed by the existing REST models. The earlier token screenshot showed Workers AI Read/Edit only; it did not establish AI Gateway Run access. The code now sends the raw token in `cf-aig-authorization` as Cloudflare documents (not a Bearer-prefixed value).
2. **Balanced candidate — `@cf/openai/whisper-large-v3-turbo`.** Cloudflare's catalog marks it batch ASR, and its official tutorial demonstrates Base64 audio in the `audio` field. The previous implementation's JSON byte-array format likely caused its failure; the corrected adapter uses the documented format and supports both flat `result.text` and `result.transcription_info.text` response forms.

The official Cloudflare catalog lists these ASR options; it does not provide a second additional batch model that both fits this finished-recording request and differs from the three existing choices. Flux is the only additional distinct ASR route found, and it has a different streaming integration. There is no verified local ASR runtime that fits the existing free Render service, so the five-model configuration is not claimed operational until Flux is deployed and tested.

## C. Cost verification and safeguards

Cloudflare's official pricing page states that Workers AI includes **10,000 neurons/day** at no charge. On **Workers Free**, usage beyond the included allowance fails; the page says upgrading to Workers Paid is needed to use more than the included amount. On **Workers Paid**, excess usage is billed at **$0.011 per 1,000 neurons**. Daily usage resets at 00:00 UTC. Current published prices include Whisper at 41.14 neurons/audio-minute, Turbo at 46.63 neurons/minute, Nova-3 at 472.73 neurons/minute, and Flux at **700 neurons/minute ($0.0077/audio minute)**. These are paid-equivalent meter rates; within the free daily allocation the request incurs no inference charge. All models share the same 10,000-neuron allocation, so the allowance is not guaranteed to cover a given five-model recording. The reviewed pricing page does not list Whisper Tiny.

The code restricts ASR models and providers to the allowlisted Cloudflare Workers AI routes, has no paid-provider fallback, and does not retry Cloudflare HTTP 429 quota/rate-limit errors. This ensures quota exhaustion does not trigger a different paid provider or an application retry. **It cannot prevent Cloudflare account-level paid overage if the account is on Workers Paid.** To keep spend at zero, the account must be Workers Free; verify this in Cloudflare. The app cannot inspect or lock the account's plan from Render. The Flux endpoint targets Workers AI, not the standalone Deepgram API, and its use is covered by Workers AI's shared allowance, subject to the account plan and quota.

## D. Benchmark results

No matching WAV test fixture or authorized live Cloudflare credentials were available to this local test process. Consequently, there are **no new first-request/subsequent-request latency or accuracy measurements**, no clinical-word/number/negation comparison against a reference transcript, and no claim that five real models returned transcripts. The user's earlier single-run observations remain historical only: Whisper 4,221 ms, Whisper Tiny 4,027 ms, Nova-3 825 ms. They are not reused as current benchmarks.

Mock tests validate request construction (including raw Cloudflare gateway auth), response extraction, five-model configuration, partial/all-failure behavior, Flux PCM framing and documented `TurnInfo`/`EndOfTurn` transcript extraction, and that a 429 does not retry. Mock results are not model inference and do not establish account accessibility, transcription quality, or availability.

## E. Regression and test results

Command: `python -m unittest discover -s tests -v` from `backend/`. **23 tests passed.** Coverage includes the three existing Cloudflare wire formats, Turbo Base64 payload/response parsing, Flux mocked WebSocket PCM replay and official transcript envelope/raw-token header, five-model parallel consensus, one/all model failures, external/paid-provider rejection, and one-shot handling of Cloudflare quota 429; existing document extraction tests also passed.

These are unit tests with mocked provider responses, not real inference or a deployed end-to-end test. Live regression of the three existing models was not possible here, so they are preserved in source and their adapter contracts are regression-tested but not freshly verified against Cloudflare.

## F. Error analysis

- Cloudflare Turbo's prior failure is consistent with the old byte-array payload differing from Cloudflare's documented Base64 input. Fix: dedicated Base64 serialization and parsing for the published result envelope. Historical causality remains unconfirmed.
- Hugging Face Turbo's billing error is avoided by removing that inference route from the ASR ensemble; no Hugging Face ASR call or paid fallback is made.
- Flux uses the documented WebSocket route and PCM16/16 kHz input. Its real handshake, replay behavior, final `EndOfTurn` response, and latency remain unverified. Missing gateway permission will appear as an isolated per-model failure.
- Cloudflare quota/rate limit is surfaced as a controlled failed prediction; the request is not automatically retried and no other provider is invoked.
- Invalid audio and provider timeouts are bounded and isolated per model. The endpoints continue retaining a secure recording before ASR; the transcription draft remains subject to clinician review.

## G. Files changed for this update

- `backend/app/services/ai_service.py` — Cloudflare-only allowlist; preserved three existing request routes; Turbo Base64 adapter; Flux WebSocket adapter; no 429 retries.
- `backend/app/routers/integration.py` — user-facing error messages now accurately identify Cloudflare access, auth, billing restrictions, and quota exhaustion.
- `backend/tests/test_asr_consensus.py` — regression and mocked adapter/quota coverage.
- `backend/requirements.txt` — bounded `websockets` dependency required by the Flux adapter.
- `backend/.env.example` — Cloudflare-only ASR config and required Flux Gateway setting.
- `docs/ASR_RELIABILITY_REPORT.md` — this report.

No Flutter app, doctor app, admin portal, Supabase schema/RLS, recording storage, ownership logic, or Render environment values were changed in this update.

## H. Remaining blockers and next verification

1. Confirm the Cloudflare account token has **Account → AI Gateway → Run** (for Flux's authenticated Gateway WebSocket) and **Account → Workers AI → Read** (for REST inference). The earlier screenshot only showed Workers AI Read/Edit, so does not prove Flux authorization. Keep token/account ID server-side. Confirm the configured gateway exists and has authenticated gateway enabled; the app uses `default` unless `CLOUDFLARE_AI_GATEWAY` overrides it.
2. Confirm that the Cloudflare account remains on Workers Free. The repository cannot enforce account-plan selection; on Workers Paid, Cloudflare can bill beyond the daily free allocation.
3. If Render has an old `AI_ASR_MODELS_JSON` override listing Hugging Face models or the previous duplicate Turbo route, clear it so the safe Cloudflare defaults take effect. The code intentionally rejects those routes.
4. Deploy the backend dependency/code changes and run an authorized short synthetic WAV (mono, PCM16, 16 kHz) through an authenticated test appointment. Capture each model's status, returned text, latency, and request ID. Do not use a real patient recording for a test without authorization.
5. Verify Flux emits usable final transcript events on replayed completed audio; verify Turbo returns a usable transcript; confirm the first three still return transcripts unchanged. Run a second request to record warm latency and compare every result to a known reference for omissions, numbers, negations, and medication details.

## I. Final verdict

**NOT VERIFIED for live use.** Cloudflare's official catalog verifies both model IDs and task: the configured fifth model `@cf/openai/whisper-large-v3-turbo` is batch ASR, while fourth-position `@cf/deepgram/flux` is real-time ASR over WebSockets. Turbo costs 46.63 neurons/audio-minute; Flux costs 700 neurons/audio-minute (listed rate; free-plan usage draws from the shared free allowance). Mocked request/response tests pass. The actual account's model access, gateway authentication/token permissions, Workers plan, quota, and live transcript remain unverified because no authorized account credentials/plan status were available to this local test run and no billable inference was attempted. Do not call either replacement a working model until a real-audio request returns usable text. No deploy or push was performed.

## Official references

- [Cloudflare Workers AI pricing and free daily allocation](https://developers.cloudflare.com/workers-ai/platform/pricing/)
- [Cloudflare Whisper Large-v3 Turbo model and input parameters](https://developers.cloudflare.com/workers-ai/models/whisper-large-v3-turbo/)
- [Cloudflare Turbo tutorial using Base64 audio](https://developers.cloudflare.com/workers-ai/guides/tutorials/build-a-workers-ai-whisper-with-chunking/)
- [Cloudflare Flux model and input format](https://developers.cloudflare.com/workers-ai/models/flux/)
- [Cloudflare AI Gateway realtime WebSocket API](https://developers.cloudflare.com/ai-gateway/usage/websockets-api/realtime-api/)
- [Cloudflare authenticated AI Gateway token requirements](https://developers.cloudflare.com/ai-gateway/configuration/authentication/)
- [Cloudflare token permission definitions](https://developers.cloudflare.com/fundamentals/api/reference/permissions/)
- [Deepgram Flux protocol reference: CloseStream and EndOfTurn transcript schema](https://developers.deepgram.com/reference/speech-to-text/listen-flux?explorer=true)
- [Cloudflare Workers AI model catalog](https://developers.cloudflare.com/workers-ai/models/)
