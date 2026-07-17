package com.bychat.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class HostService : Service() {
    private var server: ChatServer? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "边聊服务器", NotificationManager.IMPORTANCE_LOW))
        val pending = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startForeground(7, NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("边聊服务器运行中")
            .setContentText("点击返回聊天")
            .setOngoing(true)
            .setContentIntent(pending)
            .build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") { stopSelf(); return START_NOT_STICKY }
        if (intent != null) {
            server?.stop()
            server = null
            try {
                server = ChatServer(
                    (application as BychatApp).db,
                    intent.getIntExtra("port", 18888),
                    intent.getStringExtra("owner").orEmpty(),
                    intent.getStringExtra("credential").orEmpty(),
                    intent.getStringExtra("room").orEmpty(),
                    intent.getStringExtra("password").orEmpty()
                ).also(ChatServer::start)
                running = true
            } catch (_: Exception) {
                running = false
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() { server?.stop(); server = null; running = false; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "bychat_host"
        @Volatile var running = false
            private set
    }
}
