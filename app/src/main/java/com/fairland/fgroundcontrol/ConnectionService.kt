package com.fairland.fgroundcontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder

/** 连接随服务而非页面存活；系统或用户终止进程后不保证持续运行。 */
class ConnectionService : Service() {
    override fun onCreate() {
        super.onCreate()
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel("connection", getString(R.string.connection_service), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, "connection")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.connection_service))
            .setContentIntent(open).setOngoing(true).build()
        startForeground(1, notification)
        (application as GroundControlApp).startConnection()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onDestroy() {
        (application as GroundControlApp).stopConnection()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
