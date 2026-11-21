package com.jarvis.app

import android.annotation.SuppressLint
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.sqrt

object Jarvis { val state = MutableStateFlow("idle"); val level = MutableStateFlow(0f) }

const val MODEL = "models/gemini-2.5-flash-native-audio-latest" // change here if Google renames it
const val VOICE = "Charon"
const val PERSONA = "You are Jarvis, a calm, witty, warm personal assistant. Talk like a real person: short, natural, relaxed. Never read out lists or markdown."

class LiveClient(private val key: String, private val onEnd: () -> Unit) {
    private var ws: WebSocket? = null
    private var rec: AudioRecord? = null
    private var track: AudioTrack? = null
    private val q = LinkedBlockingQueue<ByteArray>()
    @Volatile private var running = false
    @Volatile private var last = System.currentTimeMillis()

    fun start() {
        running = true; Jarvis.state.value = "connecting"
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$key"
        ws = OkHttpClient().newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val cfg = JSONObject("""{"setup":{"model":"$MODEL","generationConfig":{"responseModalities":["AUDIO"],"speechConfig":{"voiceConfig":{"prebuiltVoiceConfig":{"voiceName":"$VOICE"}}}}}}""")
                cfg.getJSONObject("setup").put("systemInstruction", JSONObject("""{"parts":[{"text":"$PERSONA"}]}"""))
                webSocket.send(cfg.toString())
            }
            override fun onMessage(webSocket: WebSocket, text: String) = handle(text)
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handle(bytes.utf8())
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { Jarvis.state.value = "error: ${t.message}"; stop() }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { if (code != 1000) Jarvis.state.value = "error: $reason"; stop() }
        })
    }

    private fun handle(msg: String) {
        last = System.currentTimeMillis()
        val j = JSONObject(msg)
        if (j.has("setupComplete")) { initAudio(); Jarvis.state.value = "listening"; return }
        val sc = j.optJSONObject("serverContent") ?: return
        if (sc.optBoolean("interrupted")) { q.clear(); track?.apply { pause(); flush(); play() } }
        val parts = sc.optJSONObject("modelTurn")?.optJSONArray("parts")
        if (parts != null) for (i in 0 until parts.length()) {
            val d = parts.getJSONObject(i).optJSONObject("inlineData") ?: continue
            q.put(Base64.decode(d.getString("data"), Base64.DEFAULT)); Jarvis.state.value = "speaking"
        }
        if (sc.optBoolean("turnComplete")) Jarvis.state.value = "listening"
    }

    @SuppressLint("MissingPermission")
    private fun initAudio() {
        val min = AudioTrack.getMinBufferSize(24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(24000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(min * 4).setTransferMode(AudioTrack.MODE_STREAM).build().also { it.play() }
        thread { while (running) { val b = q.poll(200, TimeUnit.MILLISECONDS) ?: continue; level(b, true); track?.write(b, 0, b.size) } }
        val bs = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bs * 2)
        rec = r
        if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(r.audioSessionId)?.enabled = true
        r.startRecording()
        thread {
            val buf = ByteArray(3200)
            while (running) {
                val n = r.read(buf, 0, buf.size); if (n <= 0) continue
                val lv = level(buf.copyOf(n), Jarvis.state.value != "speaking")
                if (lv > 0.15f) last = System.currentTimeMillis()
                val audio = JSONObject().put("data", Base64.encodeToString(buf, 0, n, Base64.NO_WRAP)).put("mimeType", "audio/pcm;rate=16000")
                ws?.send(JSONObject().put("realtimeInput", JSONObject().put("audio", audio)).toString())
                if (System.currentTimeMillis() - last > 25000) stop()
            }
        }
    }

    private fun level(b: ByteArray, show: Boolean): Float {
        var s = 0.0; val n = b.size / 2
        for (i in 0 until n) { val v = ((b[2 * i + 1].toInt() shl 8) or (b[2 * i].toInt() and 0xff)).toShort().toInt(); s += v.toDouble() * v }
        val l = (sqrt(s / maxOf(n, 1)) / 5000.0).toFloat().coerceIn(0f, 1f)
        if (show) Jarvis.level.value = l
        return l
    }

    fun stop() {
        if (!running) return
        running = false
        runCatching { rec?.stop(); rec?.release() }; runCatching { track?.stop(); track?.release() }
        runCatching { ws?.close(1000, null) }
        Jarvis.level.value = 0f
        if (!Jarvis.state.value.startsWith("error")) Jarvis.state.value = "idle"
        onEnd()
    }
}
