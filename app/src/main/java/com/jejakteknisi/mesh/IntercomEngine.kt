package com.jejakteknisi.mesh

import android.content.Context
import android.media.*
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.SystemClock
import kotlinx.coroutines.*
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class IntercomEngine(private val context: Context) {
    companion object {
        private const val SERVICE_TYPE = "_jtintercom._tcp."
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val HANDSHAKE = "JT1"
        private const val HEARTBEAT_MS = 2000L
        private const val DEAD_MS = 7000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val localId = UUID.randomUUID().toString().replace("-", "").take(8)
    private val localName = "JT-$localId"

    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private var server: ServerSocket? = null
    @Volatile private var socket: Socket? = null
    private var record: AudioRecord? = null
    private var track: AudioTrack? = null
    private val running = AtomicBoolean(false)
    private val connecting = AtomicBoolean(false)
    @Volatile private var lastRx = 0L

    @Volatile var status: String = "Menyiapkan jaringan..."
        private set
    var onStatus: ((String) -> Unit)? = null

    private fun setStatus(s: String) {
        status = s
        onStatus?.invoke(s)
    }

    fun start() {
        if (running.getAndSet(true)) return
        setStatus("Mencari HP lain...")
        scope.launch { startServer() }
        scope.launch { discoverLoop() }
    }

    private suspend fun startServer() {
        try {
            server = ServerSocket(0)
            val info = NsdServiceInfo().apply {
                serviceName = localName
                serviceType = SERVICE_TYPE
                port = server!!.localPort
            }
            registration = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {}
                override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) {
                    setStatus("Gagal mendaftarkan jaringan ($code)")
                }
                override fun onServiceUnregistered(info: NsdServiceInfo) {}
                override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) {}
            }
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration)

            while (running.get()) {
                val incoming = server!!.accept()
                scope.launch { handleIncoming(incoming) }
            }
        } catch (_: Exception) {
            if (running.get()) setStatus("Server jaringan berhenti, mencoba lagi...")
        }
    }

    private fun discoverLoop() {
        discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, code: Int) {
                setStatus("Pencarian gagal ($code)")
            }
            override fun onStopDiscoveryFailed(type: String, code: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                if (!running.get() || info.serviceType != SERVICE_TYPE) return
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, code: Int) {}
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        if (!running.get()) return
                        val remoteName = serviceInfo.serviceName
                        val remoteId = remoteName.removePrefix("JT-")
                        if (remoteId.isBlank() || remoteId == localId) return

                        // Deterministic rule: only the lexicographically smaller ID connects.
                        // This prevents both phones from opening two sockets to each other.
                        if (localId < remoteId && (socket == null || socket!!.isClosed)) {
                            connectTo(serviceInfo.host, serviceInfo.port)
                        }
                    }
                })
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                if (running.get() && (socket == null || socket!!.isClosed))
                    setStatus("HP terputus — mencari lagi...")
            }
        }
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery)
    }

    private fun connectTo(host: java.net.InetAddress, port: Int) {
        if (!connecting.compareAndSet(false, true)) return
        scope.launch {
            try {
                val s = Socket()
                s.tcpNoDelay = true
                s.keepAlive = true
                s.connect(java.net.InetSocketAddress(host, port), 3000)
                val out = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
                out.writeUTF("$HANDSHAKE:$localId")
                out.flush()
                installSocket(s)
            } catch (_: Exception) {
                setStatus("Mencoba menyambung ulang...")
            } finally {
                connecting.set(false)
            }
        }
    }

    private suspend fun handleIncoming(s: Socket) {
        try {
            s.tcpNoDelay = true
            s.keepAlive = true
            val input = DataInputStream(BufferedInputStream(s.getInputStream()))
            val hello = input.readUTF()
            if (!hello.startsWith("$HANDSHAKE:")) {
                s.close()
                return
            }
            if (socket == null || socket!!.isClosed) {
                withContext(Dispatchers.IO) { installSocket(s) }
            } else {
                s.close()
            }
        } catch (_: Exception) {
            try { s.close() } catch (_: Exception) {}
        }
    }

    @Synchronized
    private fun installSocket(s: Socket) {
        if (!running.get()) {
            try { s.close() } catch (_: Exception) {}
            return
        }
        if (socket != null && !socket!!.isClosed) {
            try { s.close() } catch (_: Exception) {}
            return
        }
        socket = s
        lastRx = SystemClock.elapsedRealtime()
        setStatus("🟢 TERHUBUNG")
        startAudio(s)
    }

    private fun startAudio(s: Socket) {
        val minIn = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
        val minOut = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING)
        if (minIn <= 0 || minOut <= 0) {
            setStatus("Audio tidak didukung")
            return
        }

        record?.release()
        track?.release()

        record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE, CHANNEL_IN, ENCODING, minIn * 2
        )
        track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(ENCODING)
                .setChannelMask(CHANNEL_OUT)
                .build(),
            minOut * 2,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        try { track?.play() } catch (_: Exception) {}

        // Audio sender. TCP carries a simple framed stream: 4-byte length + PCM.
        scope.launch {
            try {
                val out = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
                val buffer = ByteArray(640)
                record?.startRecording()
                while (running.get() && s === socket && !s.isClosed) {
                    val n = record?.read(buffer, 0, buffer.size) ?: -1
                    if (n > 0) {
                        out.writeInt(n)
                        out.write(buffer, 0, n)
                        out.flush()
                    }
                }
            } catch (_: Exception) {
                disconnectIfCurrent(s)
            }
        }

        // Audio receiver.
        scope.launch {
            try {
                val input = DataInputStream(BufferedInputStream(s.getInputStream()))
                while (running.get() && s === socket && !s.isClosed) {
                    val n = input.readInt()
                    if (n !in 1..4096) throw IOException("Invalid audio frame")
                    val data = ByteArray(n)
                    input.readFully(data)
                    lastRx = SystemClock.elapsedRealtime()
                    track?.write(data, 0, n)
                }
            } catch (_: Exception) {
                disconnectIfCurrent(s)
            }
        }

        // Heartbeat/dead connection detector. Audio is continuously sent, but this
        // also lets the app recover when the remote microphone is silent or paused.
        scope.launch {
            try {
                while (running.get() && s === socket && !s.isClosed) {
                    delay(HEARTBEAT_MS)
                    if (SystemClock.elapsedRealtime() - lastRx > DEAD_MS) {
                        disconnectIfCurrent(s)
                        break
                    }
                }
            } catch (_: Exception) {}
        }
    }

    @Synchronized
    private fun disconnectIfCurrent(s: Socket) {
        if (socket !== s) return
        try { s.close() } catch (_: Exception) {}
        socket = null
        try { record?.stop() } catch (_: Exception) {}
        try { track?.pause() } catch (_: Exception) {}
        if (running.get()) setStatus("🟡 Terputus — otomatis mencari lagi...")
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        try { discovery?.let { nsd.stopServiceDiscovery(it) } } catch (_: Exception) {}
        try { registration?.let { nsd.unregisterService(it) } } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        try { server?.close() } catch (_: Exception) {}
        try { record?.stop() } catch (_: Exception) {}
        try { record?.release() } catch (_: Exception) {}
        try { track?.stop() } catch (_: Exception) {}
        try { track?.release() } catch (_: Exception) {}
        socket = null
        setStatus("Tidak terhubung")
    }
}
