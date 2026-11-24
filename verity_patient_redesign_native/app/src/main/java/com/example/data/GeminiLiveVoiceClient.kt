package com.example.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.io.Closeable
import java.io.IOException
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.sqrt

/** Native Android transport for the backend-issued, one-use Gemini Live credential. */
class GeminiLiveVoiceClient(
    private val credential: String,
    private val model: String,
    private val config: JSONObject,
    private val onCaption: (speaker: String, text: String, final: Boolean) -> Unit,
    private val onReady: () -> Unit,
    private val onError: (String) -> Unit,
    private val persistTurn: suspend (String, String, String) -> Unit,
    private val onVoiceActivity: (Boolean) -> Unit = {}
) : Closeable {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ready = CompletableDeferred<Unit>()
    private val pendingAudio = ArrayDeque<ByteArray>()
    private val preRollFrames = ArrayDeque<ByteArray>()
    private val calibrationFrames = ArrayDeque<Pair<ByteArray, Double>>()
    private val playbackFrames = LinkedBlockingDeque<ByteArray>(MAX_PLAYBACK_FRAMES)
    private val playbackFlushRequested = AtomicBoolean(false)
    private val transmittedAudioFrames = AtomicInteger(0)
    private val receivedAudioFrames = AtomicInteger(0)
    private val awaitingLateInput = ArrayDeque<String>()
    private val persistenceMutex = Mutex()
    @Volatile private var socket: WebSocket? = null
    @Volatile private var muted = false
    @Volatile private var playbackMuted = false
    @Volatile private var closed = false
    @Volatile private var connectStarted = false
    @Volatile private var echoSuppressionUntilMs = 0L
    private var recorder: AudioRecord? = null
    private var track: AudioTrack? = null
    private var captureJob: Job? = null
    private var playbackJob: Job? = null
    private var currentRequestKey: String? = null
    private var currentInput = StringBuilder()
    private var currentOutput = StringBuilder()
    private var lastActivity = false
    private var voicedFrameCount = 0
    private var silenceFrameCount = 0
    private var ambientCalibrationFramesRemaining = AMBIENT_CALIBRATION_FRAMES
    private var ambientRms = 0.0
    private var gateThreshold = MIN_NOISE_GATE_RMS
    private var inputObservedThisTurn = false
    private var openingGreetingSent = false

    suspend fun connect() {
        synchronized(this) {
            check(!closed) { "Gemini Live client is already closed." }
            check(!connectStarted) { "Gemini Live connection has already been started." }
            connectStarted = true
        }
        Log.i(TAG, "Gemini Live connect started model=$model endpoint_version=v1beta constrained_token=true")
        try {
            withContext(Dispatchers.IO) {
                check(!closed) { "Gemini Live client was closed before connection started." }
                startAudioDevices()
                captureJob = launchSafely("microphone capture") { captureMicrophone() }
                val url = "https://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContentConstrained"
                    .toHttpUrl().newBuilder().addQueryParameter("access_token", credential).build()
                val request = Request.Builder().url(url).build()
                if (closed) throw IOException("Gemini Live client was closed before WebSocket setup.")
                socket = http.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                        if (closed) {
                            webSocket.close(1000, "consultation ended")
                            return
                        }
                        Log.i(TAG, "Gemini Live WebSocket handshake opened http=${response.code}; sending setup model=$model")
                        val setup = JSONObject().put("model", "models/$model")
                        val keys = config.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            setup.put(key, config.get(key))
                        }
                        if (webSocket.send(JSONObject().put("setup", setup).toString())) {
                            Log.d(TAG, "Gemini Live setup message sent")
                        } else {
                            failConnection(IOException("Gemini Live rejected the setup message."))
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        if (closed) return
                        runCatching { handleEnvelope(JSONObject(text)) }
                            .onFailure { error ->
                                Log.e(TAG, "Gemini Live event handling failed type=${error.javaClass.simpleName} message=${safeDiagnostic(error.message)}")
                                if (error is RejectedExecutionException) {
                                    failConnection(error)
                                } else {
                                    onError("Gemini Live sent an unreadable event (${error.javaClass.simpleName}).")
                                }
                            }
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        // The constrained Live endpoint uses JSON envelopes; ignore unexpected binary events.
                    }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                    if (closed) return
                    val status = response?.code
                    val body = response?.let(::safeResponseBody)
                    Log.e(
                        TAG,
                        "Gemini Live WebSocket failure type=${t.javaClass.simpleName} " +
                            "message=${safeDiagnostic(t.message)} http=${status ?: "none"} body=${body ?: "none"}"
                    )
                    val detail = buildString {
                        append("Gemini Live connection failed (")
                        append(status?.let { "HTTP $it" } ?: t.javaClass.simpleName)
                        t.message?.takeIf(String::isNotBlank)?.let {
                            append(": ")
                            append(safeDiagnostic(it))
                        }
                        if (!body.isNullOrBlank()) {
                            append("; ")
                            append(body)
                        }
                        append(')')
                    }
                    failConnection(IOException(detail, t))
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    val safeReason = safeDiagnostic(reason)
                    Log.w(TAG, "Gemini Live WebSocket closing code=$code reason=$safeReason")
                    if (!closed && !ready.isCompleted) {
                        failConnection(IOException("Gemini Live closed during setup (code $code${safeReason.takeIf(String::isNotBlank)?.let { ": $it"} ?: ""})."))
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    val safeReason = safeDiagnostic(reason)
                    Log.i(TAG, "Gemini Live WebSocket closed code=$code reason=$safeReason")
                    if (!closed) onError("Gemini Live closed the connection (code $code${safeReason.takeIf(String::isNotBlank)?.let { ": $it"} ?: ""}).")
                }
                })
            }
            withTimeout(25_000) { ready.await() }
        } catch (error: Exception) {
            close()
            throw error
        }
    }

    fun setMuted(value: Boolean) { muted = value }

    fun setPlaybackMuted(value: Boolean) {
        playbackMuted = value
        track?.setVolume(if (value) 0f else 1f)
        if (value) playbackFrames.clear()
    }

    fun sendText(text: String) {
        if (closed || !ready.isCompleted) return
        val clean = text.trim().take(1000)
        if (clean.isNotEmpty()) socket?.send(
            JSONObject().put("realtimeInput", JSONObject().put("text", clean)).toString()
        )
    }

    private fun startAudioDevices() {
        val inputMin = AudioRecord.getMinBufferSize(
            INPUT_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (inputMin <= 0) throw IOException("The microphone is unavailable on this device.")
        recorder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(INPUT_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(inputMin, FRAME_BYTES * 4))
            .build()
        if (recorder?.state != AudioRecord.STATE_INITIALIZED) throw IOException("The microphone could not be initialized.")
        val outputMin = AudioTrack.getMinBufferSize(
            OUTPUT_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (outputMin <= 0) throw IOException("Audio playback is unavailable on this device.")
        track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(OUTPUT_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(outputMin, OUTPUT_RATE / 5))
            .build()
        recorder?.startRecording()
        track?.play()
        playbackJob = launchSafely("audio playback") { playAudio() }
    }

    private fun launchSafely(label: String, block: suspend CoroutineScope.() -> Unit): Job? = try {
        if (closed || !scope.isActive) null else scope.launch(block = block)
    } catch (error: RejectedExecutionException) {
        if (!closed) {
            Log.e(TAG, "Gemini Live $label executor rejected work: ${safeDiagnostic(error.message)}")
            failConnection(IOException("Gemini Live $label executor rejected work.", error))
        }
        null
    }

    private fun failConnection(error: Throwable) {
        if (!ready.isCompleted) ready.completeExceptionally(error)
        if (!closed) onError(error.message?.takeIf(String::isNotBlank)
            ?: "Gemini Live connection failed (${error.javaClass.simpleName}).")
    }

    private fun safeResponseBody(response: okhttp3.Response): String? = runCatching {
        response.peekBody(MAX_ERROR_BODY_BYTES).string()
            .takeIf(String::isNotBlank)
            ?.let(::safeDiagnostic)
    }.getOrNull()

    /** Strip credentials and cap provider-controlled text before it reaches logcat/UI. */
    private fun safeDiagnostic(value: String?): String = value.orEmpty()
        .replace(Regex("(?i)(access_token=)[^&\\s]+")) { "${it.groupValues[1]}[redacted]" }
        .replace(Regex("(?i)(AIza)[A-Za-z0-9_-]{20,}"), "[redacted-api-key]")
        .replace(Regex("(?i)auth_tokens/[A-Za-z0-9._~-]+"), "auth_tokens/[redacted]")
        .replace(Regex("(?i)(bearer\\s+)[A-Za-z0-9._~-]+")) { "${it.groupValues[1]}[redacted]" }
        .replace(Regex("[\\r\\n\\t]+"), " ")
        .take(MAX_ERROR_TEXT_CHARS)

    /** Keep blocking AudioTrack writes off OkHttp's WebSocket callback thread. */
    private suspend fun playAudio() {
        while (scope.isActive && !closed) {
            try {
                val activeTrack = track ?: return
                if (playbackFlushRequested.compareAndSet(true, false)) {
                    activeTrack.pause()
                    activeTrack.flush()
                    activeTrack.play()
                }
                val pcm = playbackFrames.poll(100, TimeUnit.MILLISECONDS) ?: continue
                if (playbackMuted) continue
                var offset = 0
                while (offset < pcm.size && scope.isActive && !closed) {
                    if (playbackFlushRequested.get()) break
                    echoSuppressionUntilMs = SystemClock.elapsedRealtime() + ECHO_TAIL_MS
                    val written = activeTrack.write(
                        pcm, offset, pcm.size - offset, AudioTrack.WRITE_BLOCKING
                    )
                    if (written <= 0) break
                    offset += written
                    echoSuppressionUntilMs = SystemClock.elapsedRealtime() + ECHO_TAIL_MS
                }
            } catch (_: InterruptedException) {
                if (closed) return
            } catch (_: IllegalStateException) {
                if (closed) return
            }
        }
    }

    private suspend fun captureMicrophone() {
        val audioRecord = recorder ?: return
        val frame = ByteArray(FRAME_BYTES)
        while (scope.isActive && !closed) {
            val count = audioRecord.read(frame, 0, frame.size, AudioRecord.READ_BLOCKING)
            if (count <= 0) continue
            val bytes = frame.copyOf(count)
            if (muted) {
                preRollFrames.clear()
                voicedFrameCount = 0
                silenceFrameCount = 0
                if (lastActivity) {
                    lastActivity = false
                    onVoiceActivity(false)
                }
                sendAudio(SILENCE_FRAME)
                continue
            }

            val rms = rmsPcm16(bytes)
            if (ambientCalibrationFramesRemaining > 0) {
                calibrationFrames.addLast(bytes to rms)
                sendAudio(SILENCE_FRAME)
                ambientCalibrationFramesRemaining--
                if (ambientCalibrationFramesRemaining == 0) {
                    val samples = calibrationFrames.map { it.second }.sorted()
                    ambientRms = samples[samples.size / 2]
                    gateThreshold = maxOf(MIN_NOISE_GATE_RMS, ambientRms * NOISE_GATE_MULTIPLIER)
                    Log.i(TAG, "Gemini Live microphone calibrated ambient_rms=${ambientRms.toInt()} gate_rms=${gateThreshold.toInt()}")
                    val buffered = calibrationFrames.toList()
                    calibrationFrames.clear()
                    buffered.forEach { (captured, capturedRms) -> processAudioFrame(captured, capturedRms) }
                }
                continue
            }
            processAudioFrame(bytes, rms)
        }
    }

    private fun processAudioFrame(bytes: ByteArray, rms: Double) {
        // Gemini's playback is half-duplex: suppress speaker echo while audio is
        // being written and for 250 ms after the last played model chunk.
        if (SystemClock.elapsedRealtime() < echoSuppressionUntilMs) {
            preRollFrames.clear()
            voicedFrameCount = 0
            silenceFrameCount = 0
            if (lastActivity) {
                lastActivity = false
                onVoiceActivity(false)
            }
            sendAudio(SILENCE_FRAME)
            return
        }

        val voiceDetected = rms >= gateThreshold
        if (lastActivity) {
            sendAudio(bytes)
            silenceFrameCount = if (voiceDetected) 0 else silenceFrameCount + 1
            if (silenceFrameCount >= HANGOVER_FRAMES) {
                lastActivity = false
                voicedFrameCount = 0
                silenceFrameCount = 0
                onVoiceActivity(false)
            }
            return
        }

        preRollFrames.addLast(bytes)
        while (preRollFrames.size > PRE_ROLL_FRAMES) preRollFrames.removeFirst()
        voicedFrameCount = if (voiceDetected) voicedFrameCount + 1 else 0
        if (voicedFrameCount >= VOICE_OPEN_CONFIRM_FRAMES) {
            lastActivity = true
            silenceFrameCount = 0
            onVoiceActivity(true)
            val buffered = preRollFrames.toList()
            preRollFrames.clear()
            buffered.forEach(::sendAudio)
        } else {
            // Keep the Live stream clock moving even while the local gate is closed.
            sendAudio(SILENCE_FRAME)
        }
    }

    private fun sendAudio(bytes: ByteArray) {
        val activeSocket = socket
        if (closed) return
        if (activeSocket == null || !ready.isCompleted) {
            synchronized(pendingAudio) {
                pendingAudio.addLast(bytes)
                while (pendingAudio.size > MAX_BUFFERED_FRAMES) pendingAudio.removeFirst()
            }
            return
        }
        sendAudioFrame(activeSocket, bytes)
    }

    private fun sendAudioFrame(activeSocket: WebSocket, bytes: ByteArray) {
        if (closed) return
        if (activeSocket.send(audioEnvelope(bytes))) {
            val count = transmittedAudioFrames.incrementAndGet()
            if (count == 1 || count % 20 == 0) {
                Log.i(TAG, "Gemini Live audio transmitted frames=$count latest_frame_bytes=${bytes.size}")
            }
        } else {
            Log.w(TAG, "Gemini Live audio send rejected by WebSocket")
        }
    }

    private fun handleEnvelope(envelope: JSONObject) {
        if (closed) return
        if (envelope.has("error")) {
            val error = envelope.optJSONObject("error") ?: JSONObject()
            val status = error.optString("status", "provider error")
            Log.e(TAG, "Gemini Live provider setup error status=$status message=${safeDiagnostic(error.optString("message"))}")
            if (!ready.isCompleted) ready.completeExceptionally(IOException("Gemini Live $status"))
            onError("Gemini Live setup failed ($status${error.optString("message").takeIf(String::isNotBlank)?.let { ": ${safeDiagnostic(it)}"} ?: ""}).")
            return
        }
        if (envelope.has("setupComplete")) {
            flushPendingAudio()
            if (!ready.isCompleted) ready.complete(Unit)
            Log.i(TAG, "Gemini Live setupComplete received; queued_audio_frames=${transmittedAudioFrames.get()}")
            onReady()
            if (!openingGreetingSent) {
                openingGreetingSent = true
                socket?.send(JSONObject().put("realtimeInput", JSONObject().put(
                    "text",
                    "The patient has just joined. Begin the consultation now by greeting them briefly and asking what is bothering them today. Follow the configured consultation instructions."
                )).toString())
            }
            return
        }
        val content = envelope.optJSONObject("serverContent") ?: return
        if (content.optBoolean("interrupted")) {
            playbackFrames.clear()
            playbackFlushRequested.set(true)
        }
        content.optJSONObject("interimInputTranscription")?.optString("text")
            ?.takeIf(String::isNotBlank)?.let { text ->
                if (currentRequestKey == null) currentRequestKey = UUID.randomUUID().toString()
                inputObservedThisTurn = true
                onCaption("patient", text, false)
            }
        content.optJSONObject("inputTranscription")?.optString("text")
            ?.takeIf(String::isNotBlank)?.let { text ->
                val lateKey = synchronized(awaitingLateInput) {
                    if (awaitingLateInput.isEmpty()) null else awaitingLateInput.removeFirst()
                }
                if (lateKey != null) {
                    onCaption("patient", text, true)
                    persistTurnAsync(lateKey, text, "")
                } else {
                    if (currentRequestKey == null) currentRequestKey = UUID.randomUUID().toString()
                    inputObservedThisTurn = true
                    currentInput = StringBuilder(text)
                    onCaption("patient", text, true)
                }
            }
        content.optJSONObject("outputTranscription")?.optString("text")
            ?.takeIf(String::isNotBlank)?.let { text ->
                if (currentRequestKey == null) currentRequestKey = UUID.randomUUID().toString()
                currentOutput.append(text)
                onCaption("assistant", text, false)
            }
        val parts = content.optJSONObject("modelTurn")?.optJSONArray("parts")
        if (parts != null) for (index in 0 until parts.length()) {
            val inline = parts.optJSONObject(index)?.optJSONObject("inlineData") ?: continue
            val encoded = inline.optString("data")
            if (encoded.isNotBlank()) {
                val pcm = runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull() ?: continue
                val receivedCount = receivedAudioFrames.incrementAndGet()
                if (receivedCount == 1 || receivedCount % 20 == 0) {
                    Log.i(TAG, "Gemini Live response audio received frames=$receivedCount latest_frame_bytes=${pcm.size}")
                }
                if (!playbackMuted && !playbackFrames.offerLast(pcm)) {
                    // If playback falls behind, prefer recent speech and keep latency bounded.
                    playbackFrames.pollFirst()
                    playbackFrames.offerLast(pcm)
                }
            }
        }
        if (content.optBoolean("turnComplete")) {
            if (currentOutput.isNotBlank()) onCaption("assistant", currentOutput.toString(), true)
            persistCompletedTurn()
        }
    }

    private fun persistCompletedTurn() {
        val key = currentRequestKey ?: UUID.randomUUID().toString()
        val input = currentInput.toString().trim()
        val output = currentOutput.toString().trim()
        if (input.isBlank() && output.isBlank()) return
        if (input.isBlank() && output.isNotBlank() && inputObservedThisTurn) {
            synchronized(awaitingLateInput) { awaitingLateInput.addLast(key) }
        }
        persistTurnAsync(key, input, output)
        currentRequestKey = null
        currentInput = StringBuilder()
        currentOutput = StringBuilder()
        inputObservedThisTurn = false
    }

    private fun persistTurnAsync(key: String, input: String, output: String) {
        if (input.isBlank() && output.isBlank()) return
        scope.launch { runCatching { persistenceMutex.withLock { persistTurn(key, input, output) } } }
    }

    private fun flushPendingAudio() {
        val frames = synchronized(pendingAudio) {
            buildList { while (pendingAudio.isNotEmpty()) add(pendingAudio.removeFirst()) }
        }
        val activeSocket = socket ?: return
        for (frame in frames) sendAudioFrame(activeSocket, frame)
    }

    private fun audioEnvelope(bytes: ByteArray): String = JSONObject()
        .put("realtimeInput", JSONObject().put("audio", JSONObject()
            .put("mimeType", "audio/pcm;rate=$INPUT_RATE")
            .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))))
        .toString()

    override fun close() {
        synchronized(this) {
            if (closed) return
            closed = true
        }
        if (!ready.isCompleted) ready.completeExceptionally(IOException("Gemini Live client closed before setup completed."))
        captureJob?.cancel()
        playbackJob?.cancel()
        if (lastActivity) onVoiceActivity(false)
        lastActivity = false
        preRollFrames.clear()
        playbackFrames.clear()
        runCatching { recorder?.stop() }
        recorder?.release()
        recorder = null
        runCatching { track?.stop() }
        track?.release()
        track = null
        socket?.close(1000, "consultation ended")
        socket = null
        scope.cancel()
        // Do not shut down OkHttp's dispatcher here. Its WebSocket callbacks may
        // still be queued; terminating the executor races those callbacks and
        // produces RejectedExecutionException during retries/disposal.
    }

    private fun rmsPcm16(bytes: ByteArray): Double {
        var sumSquares = 0.0
        var count = 0
        var index = 0
        while (index + 1 < bytes.size) {
            val sample = (bytes[index].toInt() and 0xff) or (bytes[index + 1].toInt() shl 8)
            val value = sample.toShort().toInt().toDouble()
            sumSquares += value * value
            count++
            index += 2
        }
        return if (count == 0) 0.0 else sqrt(sumSquares / count)
    }

    private companion object {
        const val TAG = "GeminiLiveVoiceClient"
        const val MAX_ERROR_BODY_BYTES = 2_048L
        const val MAX_ERROR_TEXT_CHARS = 500
        const val INPUT_RATE = 16_000
        const val OUTPUT_RATE = 24_000
        const val FRAME_BYTES = 1_024 // 512 PCM samples: 32 ms per capture packet.
        val SILENCE_FRAME = ByteArray(FRAME_BYTES)
        const val AMBIENT_CALIBRATION_FRAMES = 11 // ~352 ms of room-noise sampling.
        const val MIN_NOISE_GATE_RMS = 250.0
        const val NOISE_GATE_MULTIPLIER = 3.0
        const val ECHO_TAIL_MS = 250L
        const val MAX_BUFFERED_FRAMES = 100
        const val MAX_PLAYBACK_FRAMES = 48 // Bound queued model audio to about 1.5 seconds.
        const val PRE_ROLL_FRAMES = 7 // About 224 ms; preserve the onset of speech.
        const val VOICE_OPEN_CONFIRM_FRAMES = 2 // Reject isolated clicks while keeping the pre-roll.
        const val HANGOVER_FRAMES = 11 // ~352 ms before sending zeroed silence frames.
    }
}
