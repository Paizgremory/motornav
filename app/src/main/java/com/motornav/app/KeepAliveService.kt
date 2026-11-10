package com.motornav.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

/** Menjaga GPS + BLE tetap jalan saat layar HP mati atau aplikasi di latar belakang. */
class KeepAliveService : Service() {
    private var wl: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("nav", "Navigasi Motor", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "nav")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("MotorNav aktif")
            .setContentText("Mengirim peta ke layar motor")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else startForeground(1, n)
        wl = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "motornav:svc").also { it.acquire() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Core.init(applicationContext)
        Core.start()
        return START_STICKY
    }

    override fun onDestroy() {
        try { wl?.release() } catch (e: Exception) {}
        Music.shutdown()
        Core.stop()
        super.onDestroy()
    }
}
