package com.jejakteknisi.mesh

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class IntercomService : Service() {
    companion object {
        private const val CHANNEL_ID = "internet_intercom"
        private const val NOTIFICATION_ID = 1606
        const val ACTION_STOP = "com.jejakteknisi.mesh.STOP"
        @Volatile var engine: InternetIntercomEngine? = null
        @Volatile var serverUrl: String = ""
        @Volatile var roomCode: String = ""
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        if (engine == null) engine = InternetIntercomEngine(applicationContext)
        engine?.onStatus = { updateNotification(it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            engine?.stop(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY
        }
        val notification = buildNotification(engine?.status ?: "Internet Intercom aktif")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(NOTIFICATION_ID, notification)
        val s = serverUrl; val r = roomCode
        if (s.isNotBlank() && r.isNotBlank()) engine?.start(s, r)
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Internet Intercom", NotificationManager.IMPORTANCE_LOW)
        )
    }
    private fun buildNotification(status: String): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(this, 10, Intent(this, MainActivity::class.java), flags)
        val stop = PendingIntent.getService(this, 11, Intent(this, IntercomService::class.java).setAction(ACTION_STOP), flags)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Jejak Teknisi Internet Intercom")
            .setContentText(status).setOngoing(true).setContentIntent(open)
            .addAction(android.R.drawable.ic_media_pause, "Putuskan", stop)
            .setPriority(NotificationCompat.PRIORITY_LOW).build()
    }
    private fun updateNotification(status: String) { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(status)) }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { engine?.stop(); engine = null; super.onDestroy() }
}
