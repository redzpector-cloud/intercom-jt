package com.jejakteknisi.mesh

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class IntercomService : Service() {
    companion object {
        const val CHANNEL_ID = "mesh_intercom"
        const val NOTIFICATION_ID = 1501
        const val ACTION_STOP = "com.jejakteknisi.mesh.STOP"

        @Volatile
        var engine: IntercomEngine? = null
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()

        if (engine == null) {
            engine = IntercomEngine(applicationContext)
        }
        engine?.onStatus = { updateNotification(it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopIntercom()
            return START_NOT_STICKY
        }

        val notification = buildNotification(engine?.status ?: "Menyiapkan interkom...")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        engine?.start()
        return START_STICKY
    }

    private fun stopIntercom() {
        engine?.stop()
        engine?.onStatus = null
        engine = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mesh Intercom",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menjaga interkom tetap aktif saat layar mati atau aplikasi diminimalkan"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingFlags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
            android.app.PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(this, 10, launchIntent, pendingFlags)

        val stopIntent = Intent(this, IntercomService::class.java).setAction(ACTION_STOP)
        val stop = PendingIntent.getService(this, 11, stopIntent, pendingFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Jejak Teknisi Mesh Intercom")
            .setContentText(status)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_media_pause, "Putuskan", stop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(status: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(status))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // START_STICKY + stopWithTask=false keeps the intercom service alive
        // when the activity is removed from recents.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        engine?.stop()
        engine?.onStatus = null
        engine = null
        super.onDestroy()
    }
}
