package com.lusanetech.warmhearth.safety

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.util.Notifications

/** Turning the ringer back on, from a family command. */
object Ringer {
    /** Leaving Do Not Disturb / silent needs "Do Not Disturb access", granted once in Settings. */
    fun canChangeDnd(c: Context): Boolean =
        c.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    fun accessIntent(newTask: Boolean = true) = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        .apply { if (newTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    /** Ringer on at 80% volume. Returns false if Android refused (no DND access). */
    fun restore(c: Context): Boolean = runCatching {
        val am = c.getSystemService(AudioManager::class.java)
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL) {
            if (!nm.isNotificationPolicyAccessGranted) return false
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
        am.ringerMode = AudioManager.RINGER_MODE_NORMAL
        val max = am.getStreamMaxVolume(AudioManager.STREAM_RING)
        am.setStreamVolume(AudioManager.STREAM_RING, (max * 0.8f).toInt().coerceAtLeast(1), 0)
        val nmax = am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
        if (am.getStreamVolume(AudioManager.STREAM_NOTIFICATION) == 0) am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, (nmax * 0.6f).toInt().coerceAtLeast(1), 0)
        true
    }.getOrDefault(false)
}

/**
 * Commands the family sends from their phone (devices/{id}/commands, delivered by FCM through the
 * sendCommand function, with a Firestore listener as backup while the app is running).
 *   ring       ring loudly with a full-screen "Ravi is trying to reach you" (like Find My Device)
 *   ringer_on  take the phone off silent / Do Not Disturb, ringer to 80%
 *   sync       report status now (the dashboard's refresh)
 */
object RemoteCommands {
    private const val SP = "commands"
    private const val MAX_AGE_MS = 10 * 60_000L

    fun handle(context: Context, deviceId: String, id: String, type: String, byName: String, createdAt: Long? = null) {
        val c = context.applicationContext
        val sp = c.getSharedPreferences(SP, Context.MODE_PRIVATE)
        // FCM and the listener can both deliver the same command: run each once.
        if (sp.getBoolean(id, false)) return
        if (createdAt != null && System.currentTimeMillis() - createdAt > MAX_AGE_MS) return
        sp.edit().putBoolean(id, true).apply()

        val result = when (type) {
            "ring" -> { showFindPhone(c, byName); "ringing" }
            "ringer_on" -> { if (Ringer.restore(c)) "ok" else "no_access" }
            "sync" -> "ok"
            else -> return
        }
        SafetyMonitor.report(c, force = true)
        FirebaseFirestore.getInstance().collection("devices").document(deviceId)
            .collection("commands").document(id)
            .update(mapOf("doneAt" to FieldValue.serverTimestamp(), "result" to result))
    }

    private fun showFindPhone(c: Context, byName: String) {
        val open = Intent(c, FindPhoneActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(FindPhoneActivity.EXTRA_BY, byName)
        val pi = PendingIntent.getActivity(c, 7001, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val who = byName.ifBlank { c.getString(R.string.your_family) }
        val n = NotificationCompat.Builder(c, Notifications.CH_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(c.getString(R.string.find_phone_title, who))
            .setContentText(c.getString(R.string.find_phone_body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        Notifications.notify(c, Notifications.ID_FIND_PHONE, n)
    }

    /** Backup path: pending commands found while the app is running (e.g. FCM was delayed). */
    fun fromSnapshot(context: Context, deviceId: String, docs: List<com.google.firebase.firestore.DocumentSnapshot>) {
        docs.filter { it.get("doneAt") == null }.forEach { d ->
            handle(
                context, deviceId, d.id, d.getString("type") ?: return@forEach, d.getString("byName") ?: "",
                d.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis(),
            )
        }
    }
}
