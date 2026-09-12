package com.jejakteknisi.mesh

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.*
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.*
import android.os.Build
import android.os.Looper
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import java.util.concurrent.atomic.AtomicBoolean

class WifiDirectEngine(private val context: Context) {
    companion object { private const val PORT=45454; private const val SAMPLE_RATE=16000; private const val FRAME_BYTES=640 }
    private val app = context.applicationContext
    private val manager = app.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    private val scope = CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val running=AtomicBoolean(false)
    private var receiver: BroadcastReceiver?=null
    private var serverSocket:ServerSocket?=null
    @Volatile private var socket:Socket?=null
    private var audioRecord:AudioRecord?=null
    private var audioTrack:AudioTrack?=null
    @Volatile var status="Menyiapkan Wi-Fi Direct..."; private set
    var onStatus:((String)->Unit)?=null
    private fun ch()=channel?:manager.initialize(app,Looper.getMainLooper(),null).also{channel=it}
    private fun post(s:String){status=s;onStatus?.invoke(s)}
    private fun hasPerm():Boolean=if(Build.VERSION.SDK_INT>=33) app.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)==PackageManager.PERMISSION_GRANTED else app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
    private fun locationOn():Boolean { val lm=app.getSystemService(Context.LOCATION_SERVICE) as LocationManager; return if(Build.VERSION.SDK_INT>=28) lm.isLocationEnabled else true }
    fun start(){ if(running.getAndSet(true))return; register(); if(!hasPerm()){post("Izinkan Nearby Devices/Lokasi");return}; if(!locationOn() && Build.VERSION.SDK_INT<33){post("Aktifkan Lokasi untuk pencarian Wi-Fi Direct");return}; post("🔎 Memulai pencarian HP..."); discover(); retryLoop() }
    private fun register(){ if(receiver!=null)return; receiver=object:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){when(i.action){WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION->{val on=i.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE,-1)==WifiP2pManager.WIFI_P2P_STATE_ENABLED;if(on){post("🔎 Wi-Fi Direct aktif — mencari HP...");discover()}else post("Aktifkan Wi-Fi")} WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION->{requestPeers()} WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION->{requestConnectionInfo()}}}}
        val f=IntentFilter().apply{addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)}
        if(Build.VERSION.SDK_INT>=33)app.registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED) else @Suppress("DEPRECATION") app.registerReceiver(receiver,f)
    }
    private fun discover(){if(!running.get()||!hasPerm())return;try{manager.discoverPeers(ch(),object:WifiP2pManager.ActionListener{override fun onSuccess(){post("🔎 Mencari HP lain...");requestPeers()};override fun onFailure(r:Int){post("Pencarian gagal ($r) — mencoba lagi...")}})}catch(e:SecurityException){post("Izin Wi-Fi Direct belum diberikan")}catch(e:Exception){post("Wi-Fi Direct belum siap")}}
    private fun retryLoop(){scope.launch{delay(2500);while(running.get()){if(socket?.isClosed!=false){discover();requestPeers()}delay(4500)}}}
    fun connectToFirstPeer(){if(!running.get())start() else {discover();requestPeers()}}
    private fun requestPeers(){if(!running.get()||!hasPerm())return;try{manager.requestPeers(ch()){list->if(list.deviceList.isEmpty()){post("Menunggu HP kedua... (pencarian aktif)");return@requestPeers};val peer=list.deviceList.firstOrNull{it.status!=WifiP2pDevice.UNAVAILABLE}?:list.deviceList.first();post("📱 Ditemukan: ${peer.deviceName.ifBlank{"HP lain"}} — menghubungkan...");connect(peer)}}catch(e:Exception){post("Gagal membaca perangkat — mencoba lagi...")}}
    private fun connect(peer:WifiP2pDevice){if(socket?.isClosed==false)return;val cfg=WifiP2pConfig().apply{deviceAddress=peer.deviceAddress;wps.setup=WpsInfo.PBC;groupOwnerIntent=0};try{manager.connect(ch(),cfg,object:WifiP2pManager.ActionListener{override fun onSuccess(){post("🟡 Menghubungkan ke ${peer.deviceName.ifBlank{"HP"}}...")};override fun onFailure(r:Int){post("Koneksi gagal ($r) — mencari lagi...")}})}catch(e:Exception){post("Tidak bisa memulai koneksi")}}
    private fun requestConnectionInfo(){if(!running.get()||!hasPerm())return;try{manager.requestConnectionInfo(ch()){info->if(!info.groupFormed)return@requestConnectionInfo;if(info.isGroupOwner)startServer() else info.groupOwnerAddress?.let{connectClient(it)}}}catch(_:Exception){}}
    private fun startServer(){if(serverSocket?.isClosed==false)return;scope.launch{try{serverSocket=ServerSocket(PORT);post("🟡 HP ditemukan — menunggu koneksi audio...");installSocket(serverSocket!!.accept())}catch(_:Exception){if(running.get()){delay(500);startServer()}}}}
    private fun connectClient(host:InetAddress){if(socket?.isClosed==false)return;scope.launch{try{val s=Socket();s.tcpNoDelay=true;s.keepAlive=true;s.connect(InetSocketAddress(host,PORT),3000);installSocket(s)}catch(_:Exception){if(running.get()){delay(700);requestConnectionInfo()}}}}
    @Synchronized private fun installSocket(s:Socket){if(!running.get()){s.close();return};if(socket?.isClosed==false){s.close();return};socket=s;post("🟢 TERHUBUNG — Wi-Fi Direct");startAudio(s)}
    private fun startAudio(s:Socket){stopAudio();val mi=AudioRecord.getMinBufferSize(SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);val mo=AudioTrack.getMinBufferSize(SAMPLE_RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);if(mi<=0||mo<=0){post("Audio tidak didukung");return};audioRecord=AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,mi*2);audioTrack=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setAudioFormat(AudioFormat.Builder().setSampleRate(SAMPLE_RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setBufferSizeInBytes(mo*2).setTransferMode(AudioTrack.MODE_STREAM).build();audioTrack?.play();scope.launch{try{val o=DataOutputStream(BufferedOutputStream(s.getOutputStream()));val b=ByteArray(FRAME_BYTES);audioRecord?.startRecording();while(running.get()&&socket===s&&!s.isClosed){val n=audioRecord?.read(b,0,b.size)?:-1;if(n>0){o.writeInt(n);o.write(b,0,n);o.flush()}}}catch(_:Exception){disconnect(s)}};scope.launch{try{val i=DataInputStream(BufferedInputStream(s.getInputStream()));while(running.get()&&socket===s&&!s.isClosed){val n=i.readInt();if(n !in 1..4096)throw IOException();val b=ByteArray(n);i.readFully(b);audioTrack?.write(b,0,n)}}catch(_:Exception){disconnect(s)}}}
    private fun stopAudio(){try{audioRecord?.stop()}catch(_:Exception){};try{audioRecord?.release()}catch(_:Exception){};try{audioTrack?.stop()}catch(_:Exception){};try{audioTrack?.release()}catch(_:Exception){};audioRecord=null;audioTrack=null}
    @Synchronized private fun disconnect(old:Socket){if(socket!==old)return;try{old.close()}catch(_:Exception){};socket=null;stopAudio();if(running.get()){post("🟡 Putus — mencari HP lagi...");scope.launch{delay(500);discover();requestPeers()}}}
    fun stop(){if(!running.getAndSet(false))return;try{receiver?.let{app.unregisterReceiver(it)}}catch(_:Exception){};receiver=null;try{socket?.close()}catch(_:Exception){};try{serverSocket?.close()}catch(_:Exception){};socket=null;serverSocket=null;stopAudio();try{manager.stopPeerDiscovery(ch(),null)}catch(_:Exception){};try{manager.removeGroup(ch(),null)}catch(_:Exception){};post("Tidak terhubung")}
}
