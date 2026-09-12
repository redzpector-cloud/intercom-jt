package com.jejakteknisi.mesh

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.*
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class IntercomEngine(private val context: Context) {
    companion object {
        private const val SERVICE_TYPE = "_jtintercom._tcp."
        private const val SERVICE_NAME = "JejakTeknisi"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var server: ServerSocket? = null
    private var socket: Socket? = null
    private var record: AudioRecord? = null
    private var track: AudioTrack? = null
    private val running = AtomicBoolean(false)
    @Volatile var status: String = "Mencari HP lain..."
        private set
    var onStatus: ((String) -> Unit)? = null

    private fun setStatus(s: String) { status = s; onStatus?.invoke(s) }

    fun start() {
        if (running.getAndSet(true)) return
        setStatus("Mencari HP lain...")
        scope.launch { startServerAndAdvertise() }
        discover()
    }

    private fun startServerAndAdvertise() {
        try {
            server = ServerSocket(0)
            val port = server!!.localPort
            val info = NsdServiceInfo().apply {
                serviceName = SERVICE_NAME
                serviceType = SERVICE_TYPE
                this.port = port
            }
            registrationListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                    setStatus("Menunggu HP lain...")
                }
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            }
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, registrationListener)
            while (running.get()) {
                val client = server!!.accept()
                if (socket == null || socket!!.isClosed) {
                    connectSocket(client)
                } else client.close()
            }
        } catch (e: Exception) {
            if (running.get()) setStatus("Server error: ${e.message}")
        }
    }

    private fun discover() {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onDiscoveryStopped(regType: String) {}
            override fun onStartDiscoveryFailed(regType: String, errorCode: Int) {
                setStatus("Discovery gagal ($errorCode)")
            }
            override fun onStopDiscoveryFailed(regType: String, errorCode: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceType != SERVICE_TYPE) return
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        if (!running.get()) return
                        if (serviceInfo.serviceName == SERVICE_NAME &&
                            (socket == null || socket!!.isClosed)) {
                            scope.launch {
                                try {
                                    connectSocket(Socket(serviceInfo.host, serviceInfo.port))
                                } catch (_: Exception) {}
                            }
                        }
                    }
                })
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                if (running.get() && (socket == null || socket!!.isClosed))
                    setStatus("HP terputus, mencari lagi...")
            }
        }
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    @Synchronized
    private fun connectSocket(s: Socket) {
        if (!running.get()) { s.close(); return }
        if (socket != null && !socket!!.isClosed) { s.close(); return }
        socket = s
        s.tcpNoDelay = true
        s.keepAlive = true
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
        record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE, CHANNEL_IN, ENCODING, minIn * 2
        )
        track = AudioTrack(
            AudioManager.STREAM_VOICE_CALL, SAMPLE_RATE, CHANNEL_OUT, ENCODING,
            minOut * 2, AudioTrack.MODE_STREAM
        )
        track?.play()

        scope.launch {
            try {
                val out = BufferedOutputStream(s.getOutputStream())
                val buffer = ByteArray(640)
                record?.startRecording()
                while (running.get() && !s.isClosed) {
                    val n = record?.read(buffer, 0, buffer.size) ?: -1
                    if (n > 0) {
                        out.write(buffer, 0, n)
                        out.flush()
                    }
                }
            } catch (_: Exception) {
                handleDisconnect()
            }
        }

        scope.launch {
            try {
                val input = BufferedInputStream(s.getInputStream())
                val buffer = ByteArray(640)
                while (running.get() && !s.isClosed) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (n > 0) track?.write(buffer, 0, n)
                }
            } catch (_: Exception) {
                handleDisconnect()
            }
        }
    }

    private fun handleDisconnect() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        record?.stop()
        track?.pause()
        if (running.get()) setStatus("🟡 Terputus — mencari lagi...")
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        try { discoveryListener?.let { nsd.stopServiceDiscovery(it) } } catch (_: Exception) {}
        try { registrationListener?.let { nsd.unregisterService(it) } } catch (_: Exception) {}
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
