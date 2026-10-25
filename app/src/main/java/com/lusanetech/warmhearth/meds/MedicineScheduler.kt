package com.lusanetech.warmhearth.meds

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.lusanetech.warmhearth.MainActivity
import com.lusanetech.warmhearth.data.AppDatabase
import com.lusanetech.warmhearth.data.Medicine
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * One alarm per (medicine, time of day). When it fires, the receiver shows the alert and
 * schedules tomorrow's alarm, plus a "missed dose" check 30 minutes later.
 */
class MedicineScheduler(private val context: Context, private val db: AppDatabase) {

    companion object {
        const val ACTION_REMIND = "com.lusanetech.warmhearth.REMIND"
        const val ACTION_CHECK = "com.lusanetech.warmhearth.CHECK_MISSED"
        const val ACTION_SNOOZE = "com.lusanetech.warmhearth.SNOOZE"
        const val EXTRA_MED = "med"
        const val EXTRA_TIME = "time"
        const val EXTRA_SLOT = "slot"
        const val MISSED_AFTER_MIN = 30

        private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fun slotFor(date: Date, time: String) = "${dayFormat.format(date)} $time"
        fun today(): String = dayFormat.format(Date())
    }

    private val alarms = context.getSystemService(AlarmManager::class.java)

    suspend fun rescheduleAll() {
        db.medicines().getActive().forEach { schedule(it) }
    }

    fun schedule(med: Medicine) {
        if (!med.active) return
        med.timeList().forEach { time -> setAlarm(ACTION_REMIND, med.id, time, nextTrigger(time), null) }
    }

    fun cancel(med: Medicine) {
        med.timeList().forEach { time ->
            alarms.cancel(pending(ACTION_REMIND, med.id, time, null))
            alarms.cancel(pending(ACTION_CHECK, med.id, time, null))
        }
    }

    fun scheduleMissedCheck(medId: String, time: String, slot: String) {
        val at = System.currentTimeMillis() + MISSED_AFTER_MIN * 60_000L
        setAlarm(ACTION_CHECK, medId, time, at, slot)
    }

    fun snooze(medId: String, time: String, slot: String, minutes: Int = 10) {
        setAlarm(ACTION_SNOOZE, medId, time, System.currentTimeMillis() + minutes * 60_000L, slot)
    }

    /** Next time today or tomorrow when "HH:mm" occurs, in millis. */
    private fun nextTrigger(time: String): Long {
        val (h, m) = time.split(":").map { it.toInt() }
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis() + 5_000) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    private fun setAlarm(action: String, medId: String, time: String, at: Long, slot: String?) {
        val pi = pending(action, medId, time, slot)
        val exact = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
        if (exact) {
            // setAlarmClock is the most reliable exact alarm (survives Doze, shows the alarm icon).
            val show = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, "meds"),
                PendingIntent.FLAG_IMMUTABLE,
            )
            alarms.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
        } else {
            // Without the exact-alarm permission Android may delay this by a few minutes.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun pending(action: String, medId: String, time: String, slot: String?): PendingIntent {
        val intent = Intent(context, MedicineAlarmReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_MED, medId)
            .putExtra(EXTRA_TIME, time)
            .apply { if (slot != null) putExtra(EXTRA_SLOT, slot) }
        val code = "$action|$medId|$time".hashCode()
        return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
