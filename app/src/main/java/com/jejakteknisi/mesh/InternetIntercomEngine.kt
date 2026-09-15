package com.jejakteknisi.mesh

import android.content.Context
import android.media.*
import android.os.SystemClock
import kotlinx.coroutines.*
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.net.URI
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Internet relay intercom. Audio is sent as small PCM frames through a WebSocket relay. */
class InternetIntercomEngine(private val context: Context) {
    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val FRAME_BYTES = 640 // 20 ms @ 16 kHz mono PCM16
        private const val DEFAULT_SERVER = "wss://YOUR-SERVER.example/ws"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clientId = UUID.randomUUID().toString().replace("-", "").take(10)
    private val running = AtomicBoolean(false)
    private val connecting = AtomicBoolean(false)
    private var ws: WebSocketClient? = null
    private var record: AudioRecord? = null
    private var track: AudioTrack? = null
    private var serverUrl = DEFAULT_SERVER
    private var room = ""
    private var lastRx = 0L

    @Volatile var status = "Siap — masukkan kode ruang"
        private set
    var onStatus: ((String) -> Unit)? = null

    private fun setStatus(value: String) {
        status = value
        onStatus?.invoke(value)
    }

    fun start(server: String, roomCode: String) {
        if (running.get()) return
        serverUrl = server.trim().trimEnd('/')
        room = roomCode.trim().uppercase()
        if (room.length < 4) {
            setStatus("Kode ruang minimal 4 karakter")
            return
        }
        if (!serverUrl.startsWith("ws://") && !serverUrl.startsWith("wss://")) {
            setStatus("Alamat server harus ws:// atau wss://")
            return
        }
        running.set(true)
        connect()
    }

    private fun connect() {
        if (!running.get() || !connecting.compareAndSet(false, true)) return
        scope.launch {
            try {
                ws?.close()
                val uri = URI(serverUrl)
                val c = object : WebSocketClient(uri) {
                    override fun onOpen(handshakedata: ServerHandshake?) {
                        connecting.set(false)
                        send(JSONObject().apply {
                            put("type", "join")
                            put("room", room)
                            put("clientId", clientId)
                        }.toString())
                        setStatus("🟡 Terhubung server — masuk ruang $room")
                    }
                    override fun onMessage(message: String?) {
                        try {
                            val json = message?.let { JSONObject(it) } ?: return
                            if (json.optString("type") == "peer" && json.optInt("count") >= 2) markPeerConnected()
                            else if (json.optString("type") == "waiting") setStatus("🟡 Menunggu HP kedua di ruang $room")
                        } catch (_: Exception) {}
                    }
                    override fun onMessage(bytes: ByteBuffer?) {
                        if (!running.get() || bytes == null || !bytes.hasRemaining()) return
                        val data = ByteArray(bytes.remaining())
                        bytes.get(data)
                        lastRx = SystemClock.elapsedRealtime()
                        track?.write(data, 0, data.size)
                    }
                    override fun onClose(code: Int, reason: String?, remote: Boolean) {
                        connecting.set(false)
                        stopAudio()
                        if (running.get()) {
                            setStatus("🟠 Koneksi putus — reconnect otomatis...")
                            scope.launch { delay(1500); connect() }
                        }
                    }
                    override fun onError(ex: Exception?) {
                        connecting.set(false)
                        if (running.get()) setStatus("🔴 Server belum terhubung")
                    }
                }
                ws = c
                c.connect()
            } catch (_: Exception) {
                connecting.set(false)
                if (running.get()) {
                    setStatus("🔴 Tidak bisa menghubungi server")
                    delay(2000)
                    connect()
                }
            }
        }
    }

    private fun startAudio() {
        stopAudio()
        val minIn = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
        val minOut = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING)
        if (minIn <= 0 || minOut <= 0) {
            setStatus("Audio tidak didukung")
            return
        }
        record = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, SAMPLE_RATE, CHANNEL_IN, ENCODING, maxOf(minIn * 2, FRAME_BYTES * 2))
        track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(SAMPLE_RATE).setEncoding(ENCODING).setChannelMask(CHANNEL_OUT).build())
            .setBufferSizeInBytes(maxOf(minOut * 2, FRAME_BYTES * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        try { track?.play(); record?.startRecording() } catch (_: Exception) { setStatus("Gagal memulai microphone") ; return }

        scope.launch {
            val buffer = ByteArray(FRAME_BYTES)
            try {
                while (running.get()) {
                    val socket = ws
                    if (socket?.isOpen == true) {
                        val n = record?.read(buffer, 0, buffer.size) ?: -1
                        if (n > 0 && socket.isOpen) socket.send(buffer.copyOf(n))
                    } else delay(40)
                }
            } catch (_: Exception) {}
        }
    }

    fun markPeerConnected() {
        if (running.get()) {
            setStatus("🟢 INTERKOM TERHUBUNG — ruang $room")
            startAudio()
        }
    }

    private fun stopAudio() {
        try { record?.stop() } catch (_: Exception) {}
        try { record?.release() } catch (_: Exception) {}
        try { track?.stop() } catch (_: Exception) {}
        try { track?.release() } catch (_: Exception) {}
        record = null
        track = null
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        try { ws?.close() } catch (_: Exception) {}
        ws = null
        stopAudio()
        setStatus("Tidak terhubung")
    }
}
