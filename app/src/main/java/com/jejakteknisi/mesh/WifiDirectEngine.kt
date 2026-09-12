package com.jejakteknisi.mesh

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class WifiDirectEngine(private val context: Context) {
    companion object {
        private const val PORT = 45454
        private const val SAMPLE_RATE = 16000
        private const val FRAME_BYTES = 640
    }

    private val manager: WifiP2pManager =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)

    private var receiver: BroadcastReceiver? = null
    private var serverSocket: ServerSocket? = null
    @Volatile private var socket: Socket? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    @Volatile var status: String = "Menyiapkan Wi-Fi Direct..."
        private set
    var onStatus: ((String) -> Unit)? = null

    private fun getChannel(): WifiP2pManager.Channel {
        val existing = channel
        if (existing != null) return existing
        val created = manager.initialize(
            context.applicationContext,
            Looper.getMainLooper(),
            null
        )
        channel = created
        return created
    }

    private fun setStatus(value: String) {
        status = value
        onStatus?.invoke(value)
    }

    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            context.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    fun start() {
        if (running.getAndSet(true)) return
        registerReceiver()
        if (!hasPermission()) {
            setStatus("Izinkan Nearby Devices untuk Wi-Fi Direct")
            return
        }
        setStatus("Mencari HP melalui Wi-Fi Direct...")
        discover()
        scope.launch {
            delay(1200)
            requestPeersAndConnect()
        }
    }

    private fun registerReceiver() {
        if (receiver != null) return
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val enabled = intent.getIntExtra(
                            WifiP2pManager.EXTRA_WIFI_STATE, -1
                        ) == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                        if (enabled) discover()
                        else setStatus("Wi-Fi Direct tidak aktif")
                    }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> requestConnectionInfo()
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> requestPeersAndConnect()
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
    }

    private fun discover() {
        if (!running.get() || !hasPermission()) return
        try {
            manager.discoverPeers(
                getChannel(),
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        setStatus("🔎 Mencari HP lain...")
                    }
                    override fun onFailure(reason: Int) {
                        setStatus("Pencarian gagal ($reason), mencoba lagi...")
                        scope.launch { delay(1500); discover() }
                    }
                }
            )
        } catch (e: Exception) {
            setStatus("Wi-Fi Direct belum siap")
        }
    }

    /** Manual retry button exposed to the UI. */
    fun connectToFirstPeer() {
        if (!running.get()) start() else requestPeersAndConnect()
    }

    private fun requestPeersAndConnect() {
        if (!running.get() || !hasPermission()) return
        try {
            manager.requestPeers(getChannel()) { peers ->
                val peer: WifiP2pDevice? = peers.deviceList.firstOrNull()
                if (peer == null) {
                    setStatus("Menunggu HP kedua...")
                    scope.launch {
                        delay(1500)
                        discover()
                        requestPeersAndConnect()
                    }
                    return@requestPeers
                }

                if (socket?.isClosed == false) return@requestPeers

                val config = WifiP2pConfig().apply {
                    deviceAddress = peer.deviceAddress
                    wps.setup = WpsInfo.PBC
                }

                manager.connect(
                    getChannel(),
                    config,
                    object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {
                            setStatus("Menghubungkan ke ${peer.deviceName}...")
                        }
                        override fun onFailure(reason: Int) {
                            setStatus("Gagal terhubung ($reason), mencoba lagi...")
                            scope.launch {
                                delay(1200)
                                discover()
                                requestPeersAndConnect()
                            }
                        }
                    }
                )
            }
        } catch (e: Exception) {
            setStatus("Wi-Fi Direct belum siap")
        }
    }

    private fun requestConnectionInfo() {
        if (!running.get() || !hasPermission()) return
        try {
            manager.requestConnectionInfo(getChannel()) { info ->
                if (!info.groupFormed) return@requestConnectionInfo
                if (info.isGroupOwner) startServer()
                else info.groupOwnerAddress?.let { connectClient(it) }
            }
        } catch (_: Exception) {
        }
    }

    private fun startServer() {
        if (serverSocket?.isClosed == false) return
        scope.launch {
            try {
                serverSocket = ServerSocket(PORT)
                setStatus("🟡 Menunggu HP kedua...")
                installSocket(serverSocket!!.accept())
            } catch (_: Exception) {
                if (running.get()) {
                    delay(700)
                    startServer()
                }
            }
        }
    }

    private fun connectClient(host: InetAddress) {
        if (socket?.isClosed == false) return
        scope.launch {
            try {
                setStatus("🟡 Menghubungkan audio...")
                val newSocket = Socket()
                newSocket.tcpNoDelay = true
                newSocket.keepAlive = true
                newSocket.connect(InetSocketAddress(host, PORT), 2500)
                installSocket(newSocket)
            } catch (_: Exception) {
                if (running.get()) {
                    delay(900)
                    requestConnectionInfo()
                }
            }
        }
    }

    @Synchronized
    private fun installSocket(newSocket: Socket) {
        if (!running.get()) {
            try { newSocket.close() } catch (_: Exception) {}
            return
        }
        if (socket?.isClosed == false) {
            try { newSocket.close() } catch (_: Exception) {}
            return
        }
        socket = newSocket
        setStatus("🟢 TERHUBUNG — Wi-Fi Direct")
        startAudio(newSocket)
    }

    private fun startAudio(s: Socket) {
        stopAudioOnly()

        val inputMin = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val outputMin = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (inputMin <= 0 || outputMin <= 0) {
            setStatus("Audio tidak didukung")
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            inputMin * 2
        )
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(outputMin * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()

        scope.launch {
            try {
                val output = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
                val buffer = ByteArray(FRAME_BYTES)
                audioRecord?.startRecording()
                while (running.get() && socket === s && !s.isClosed) {
                    val count = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (count > 0) {
                        output.writeInt(count)
                        output.write(buffer, 0, count)
                        output.flush()
                    }
                }
            } catch (_: Exception) {
                disconnect(s)
            }
        }

        scope.launch {
            try {
                val input = DataInputStream(BufferedInputStream(s.getInputStream()))
                while (running.get() && socket === s && !s.isClosed) {
                    val count = input.readInt()
                    if (count !in 1..4096) throw IOException("Invalid audio frame")
                    val buffer = ByteArray(count)
                    input.readFully(buffer)
                    audioTrack?.write(buffer, 0, count)
                }
            } catch (_: Exception) {
                disconnect(s)
            }
        }
    }

    private fun stopAudioOnly() {
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        try { audioTrack?.stop() } catch (_: Exception) {}
        try { audioTrack?.release() } catch (_: Exception) {}
        audioRecord = null
        audioTrack = null
    }

    @Synchronized
    private fun disconnect(oldSocket: Socket) {
        if (socket !== oldSocket) return
        try { oldSocket.close() } catch (_: Exception) {}
        socket = null
        stopAudioOnly()
        if (running.get()) {
            setStatus("🟡 Putus — mencari HP lagi...")
            scope.launch {
                delay(400)
                discover()
                delay(600)
                requestPeersAndConnect()
            }
        }
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        try { receiver?.let { context.unregisterReceiver(it) } } catch (_: Exception) {}
        receiver = null
        try { socket?.close() } catch (_: Exception) {}
        try { serverSocket?.close() } catch (_: Exception) {}
        socket = null
        serverSocket = null
        stopAudioOnly()
        try { manager.stopPeerDiscovery(getChannel(), null) } catch (_: Exception) {}
        try { manager.removeGroup(getChannel(), null) } catch (_: Exception) {}
        setStatus("Tidak terhubung")
    }
}
