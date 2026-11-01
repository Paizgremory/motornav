package com.motornav.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telephony.TelephonyManager

/** Membaca notifikasi HP (butuh "Akses notifikasi" diberikan di Pengaturan). */
class NotifService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?, rankingMap: RankingMap?) {
        if (sbn == null) return
        try { Notify.onPosted(applicationContext, sbn, rankingMap) } catch (e: Exception) {}
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {}

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        try {
            if (sbn.notification?.category == android.app.Notification.CATEGORY_CALL) Notify.callNotif(applicationContext, sbn, false)
        } catch (e: Exception) {}
    }
}

/** Status telepon: berdering / diangkat / selesai. Butuh izin Telepon (nomor butuh izin Log panggilan). */
class CallReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING -> Notify.phoneRinging(ctx.applicationContext, intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER))
            TelephonyManager.EXTRA_STATE_OFFHOOK -> Notify.phoneAnswered()
            TelephonyManager.EXTRA_STATE_IDLE -> Notify.phoneIdle(ctx.applicationContext)
        }
    }
}
