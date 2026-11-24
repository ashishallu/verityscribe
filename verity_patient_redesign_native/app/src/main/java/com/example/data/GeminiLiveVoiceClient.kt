package com.example.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
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
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

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
    private val awaitingLateInput = ArrayDeque<String>()
    private val persistenceMutex = Mutex()
    @Volatile private var socket: WebSocket? = null
    @Volatile private var muted = false
    @Volatile private var playbackMuted = false
    @Volatile private var closed = false
    private var recorder: AudioRecord? = null
    private var track: AudioTrack? = null
    private var captureJob: Job? = null
    private var currentRequestKey: String? = null
    private var currentInput = StringBuilder()
    private var currentOutput = StringBuilder()
    private var lastActivity = false
    private var voicedFrameCount = 0
    private var silenceFrameCount = 0
    private var inputObservedThisTurn = false
    private var openingGreetingSent = false

    suspend fun connect() {
        withContext(Dispatchers.IO) {
            startAudioDevices()
            captureJob = scope.launch { captureMicrophone() }
            val url = "https://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContentConstrained"
                .toHttpUrl().newBuilder().addQueryParameter("access_token", credential).build()
            val request = Request.Builder().url(url).build()
            socket = http.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    val setup = JSONObject().put("model", "models/$model")
                    val keys = config.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        setup.put(key, config.get(key))
                    }
                    webSocket.send(JSONObject().put("setup", setup).toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    runCatching { handleEnvelope(JSONObject(text)) }
                        .onFailure { onError("Gemini Live sent an unreadable event.") }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    // The constrained Live endpoint uses JSON envelopes; ignore unexpected binary events.
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                    if (!ready.isCompleted) ready.completeExceptionally(t)
                    if (!closed) onError("Gemini Live connection failed (${response?.code ?: t.javaClass.simpleName}).")
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (!closed) onError("Gemini Live closed the connection (code $code).")
                }
            })
        }
        try {
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
    }

    fun sendText(text: String) {
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
                continue
            }

            val peak = peakPcm16(bytes)
            if (lastActivity) {
                // Once open, pass quieter syllables and a short silence tail to Gemini's native VAD.
                sendAudio(bytes)
                silenceFrameCount = if (peak < GATE_CLOSE_THRESHOLD) silenceFrameCount + 1 else 0
                if (silenceFrameCount >= SILENCE_TAIL_FRAMES) {
                    lastActivity = false
                    voicedFrameCount = 0
                    silenceFrameCount = 0
                    onVoiceActivity(false)
                }
            } else {
                // Buffer 200 ms while closed so the first phoneme is included when speech is detected.
                preRollFrames.addLast(bytes)
                while (preRollFrames.size > PRE_ROLL_FRAMES) preRollFrames.removeFirst()
                voicedFrameCount = if (peak >= VOICE_ACTIVITY_THRESHOLD) voicedFrameCount + 1 else 0
                if (voicedFrameCount >= VOICE_OPEN_CONFIRM_FRAMES) {
                    lastActivity = true
                    silenceFrameCount = 0
                    onVoiceActivity(true)
                    val buffered = preRollFrames.toList()
                    preRollFrames.clear()
                    buffered.forEach(::sendAudio)
                }
            }
        }
    }

    private fun sendAudio(bytes: ByteArray) {
        val activeSocket = socket
        if (activeSocket == null || !ready.isCompleted) {
            synchronized(pendingAudio) {
                pendingAudio.addLast(bytes)
                while (pendingAudio.size > MAX_BUFFERED_FRAMES) pendingAudio.removeFirst()
            }
            return
        }
        activeSocket.send(audioEnvelope(bytes))
    }

    private fun handleEnvelope(envelope: JSONObject) {
        if (envelope.has("error")) {
            val error = envelope.optJSONObject("error") ?: JSONObject()
            val status = error.optString("status", "provider error")
            if (!ready.isCompleted) ready.completeExceptionally(IOException("Gemini Live $status"))
            onError("Gemini Live setup failed ($status).")
            return
        }
        if (envelope.has("setupComplete")) {
            flushPendingAudio()
            if (!ready.isCompleted) ready.complete(Unit)
            onReady()
            if (!openingGreetingSent) {
                openingGreetingSent = true
                socket?.send(JSONObject().put("clientContent", JSONObject()
                    .put("turns", org.json.JSONArray().put(JSONObject()
                        .put("role", "user")
                        .put("parts", org.json.JSONArray().put(JSONObject().put(
                            "text",
                            "Greet the patient warmly and briefly, then ask one focused question: what is bothering them today? Follow the consultation instructions already configured for this session."
                        )))))
                    .put("turnComplete", true)).toString())
            }
            return
        }
        val content = envelope.optJSONObject("serverContent") ?: return
        if (content.optBoolean("interrupted")) {
            track?.pause()
            track?.flush()
            track?.play()
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
                if (!playbackMuted) track?.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
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
        for (frame in frames) socket?.send(audioEnvelope(frame))
    }

    private fun audioEnvelope(bytes: ByteArray): String = JSONObject()
        .put("realtimeInput", JSONObject().put("audio", JSONObject()
            .put("mimeType", "audio/pcm;rate=$INPUT_RATE")
            .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))))
        .toString()

    override fun close() {
        if (closed) return
        closed = true
        captureJob?.cancel()
        if (lastActivity) onVoiceActivity(false)
        lastActivity = false
        preRollFrames.clear()
        runCatching { recorder?.stop() }
        recorder?.release()
        recorder = null
        runCatching { track?.stop() }
        track?.release()
        track = null
        socket?.close(1000, "consultation ended")
        socket = null
        scope.cancel()
        http.dispatcher.executorService.shutdown()
    }

    private fun peakPcm16(bytes: ByteArray): Int {
        var peak = 0
        var index = 0
        while (index + 1 < bytes.size) {
            val sample = (bytes[index].toInt() and 0xff) or (bytes[index + 1].toInt() shl 8)
            peak = maxOf(peak, abs(sample.toShort().toInt()))
            index += 2
        }
        return peak
    }

    private companion object {
        const val INPUT_RATE = 16_000
        const val OUTPUT_RATE = 24_000
        const val FRAME_BYTES = 3_200
        const val MAX_BUFFERED_FRAMES = 100
        const val VOICE_ACTIVITY_THRESHOLD = 800
        const val GATE_CLOSE_THRESHOLD = 600
        const val PRE_ROLL_FRAMES = 2 // 2 x 100 ms frames at 16 kHz mono PCM16.
        const val VOICE_OPEN_CONFIRM_FRAMES = 2 // Reject isolated clicks while keeping the pre-roll.
        const val SILENCE_TAIL_FRAMES = 6 // 600 ms reaches Gemini's native server-side VAD.
    }
}
