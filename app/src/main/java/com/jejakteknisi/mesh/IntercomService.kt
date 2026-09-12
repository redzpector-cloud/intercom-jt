package com.jejakteknisi.mesh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class IntercomService : Service() {
    companion object {
        private const val CHANNEL_ID = "mesh_intercom"
        private const val NOTIFICATION_ID = 1606
        const val ACTION_STOP = "com.jejakteknisi.mesh.STOP"
        @Volatile var engine: WifiDirectEngine? = null
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        if (engine == null) engine = WifiDirectEngine(applicationContext)
        engine?.onStatus = { status -> updateNotification(status) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            engine?.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification(engine?.status ?: "Wi-Fi Direct aktif")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        engine?.start()
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Mesh Intercom", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(status: String): Notification {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(this, 10, Intent(this, MainActivity::class.java), flags)
        val stop = PendingIntent.getService(
            this, 11,
            Intent(this, IntercomService::class.java).setAction(ACTION_STOP),
            flags
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Jejak Teknisi Mesh Intercom")
            .setContentText(status)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_pause, "Putuskan", stop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(status: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(status))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        engine?.stop()
        engine = null
        super.onDestroy()
    }
}
