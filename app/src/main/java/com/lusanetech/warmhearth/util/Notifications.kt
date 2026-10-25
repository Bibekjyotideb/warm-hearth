package com.lusanetech.warmhearth.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lusanetech.warmhearth.R

object Notifications {
    const val CH_ALERTS = "alerts"
    const val CH_SERVICE = "service"

    const val ID_FALL_SERVICE = 1001
    const val ID_FALL_ALERT = 1002
    const val ID_FAMILY_BASE = 3000
    // Medicine notifications use ids derived from the alarm request code.

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val alerts = NotificationChannel(CH_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
            setSound(alarmSound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
        val service = NotificationChannel(CH_SERVICE, context.getString(R.string.channel_service), NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannels(listOf(alerts, service))
    }

    fun canPost(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ||
            android.os.Build.VERSION.SDK_INT < 33

    @Suppress("MissingPermission")
    fun notify(context: Context, id: Int, notification: android.app.Notification) {
        if (canPost(context)) NotificationManagerCompat.from(context).notify(id, notification)
    }
}
