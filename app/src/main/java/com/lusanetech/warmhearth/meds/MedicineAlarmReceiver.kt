package com.lusanetech.warmhearth.meds

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.data.AppMode
import com.lusanetech.warmhearth.fall.FallDetectionService
import com.lusanetech.warmhearth.graph
import com.lusanetech.warmhearth.util.Notifications
import kotlinx.coroutines.launch
import java.util.Date

class MedicineAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val medId = intent.getStringExtra(MedicineScheduler.EXTRA_MED) ?: return
        val time = intent.getStringExtra(MedicineScheduler.EXTRA_TIME) ?: return
        val graph = context.graph
        val pending = goAsync()
        graph.scope.launch {
            try {
                val med = graph.db.medicines().get(medId)
                if (med == null || !med.active || time !in med.timeList()) return@launch
                when (intent.action) {
                    MedicineScheduler.ACTION_REMIND -> {
                        val slot = MedicineScheduler.slotFor(Date(), time)
                        showAlert(context, medId, med.name, time, slot)
                        graph.scheduler.schedule(med) // tomorrow
                        graph.scheduler.scheduleMissedCheck(medId, time, slot)
                    }
                    MedicineScheduler.ACTION_SNOOZE -> {
                        val slot = intent.getStringExtra(MedicineScheduler.EXTRA_SLOT) ?: return@launch
                        if (graph.db.doseLogs().countTaken(medId, slot) == 0) showAlert(context, medId, med.name, time, slot)
                    }
                    MedicineScheduler.ACTION_CHECK -> {
                        val slot = intent.getStringExtra(MedicineScheduler.EXTRA_SLOT) ?: return@launch
                        if (graph.db.doseLogs().countTaken(medId, slot) == 0) {
                            val name = graph.prefs.current().seniorName.ifBlank { context.getString(R.string.your_family_member) }
                            graph.family.sendEvent("missed_dose", context.getString(R.string.family_missed_dose, name, med.name, time))
                            graph.family.recordDose(medId, med.name, slot, taken = false)
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun showAlert(context: Context, medId: String, name: String, time: String, slot: String) {
        val open = Intent(context, MedicineAlertActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MedicineScheduler.EXTRA_MED, medId)
            .putExtra(MedicineScheduler.EXTRA_TIME, time)
            .putExtra(MedicineScheduler.EXTRA_SLOT, slot)
        val id = notificationId(medId, time)
        val pi = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, Notifications.CH_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.med_time_title))
            .setContentText(context.getString(R.string.med_time_text, name, time))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        Notifications.notify(context, id, n)
    }

    companion object {
        fun notificationId(medId: String, time: String) = 5000 + ("$medId|$time".hashCode() and 0xFFFF)
    }
}

/** Alarms are wiped when the phone restarts, so put them back (and restart fall detection). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = context.graph
        val pending = goAsync()
        graph.scope.launch {
            try {
                val s = graph.prefs.current()
                if (s.mode == AppMode.SENIOR) {
                    graph.scheduler.rescheduleAll()
                    if (s.fallDetection && s.premium && intent.action == Intent.ACTION_BOOT_COMPLETED) {
                        FallDetectionService.start(context)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
