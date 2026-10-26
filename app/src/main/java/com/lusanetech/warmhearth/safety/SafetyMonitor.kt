package com.lusanetech.warmhearth.safety

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.lusanetech.warmhearth.BuildConfig
import com.lusanetech.warmhearth.data.AppMode
import com.lusanetech.warmhearth.graph
import com.lusanetech.warmhearth.launcher.Apps
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Senior side of safety alerts. The phone reports *how it is*, never *what's on it*:
 * battery, charging, ringer mode/volume, Do Not Disturb, last time it was used, network type,
 * time zone. The server (functions/src/safetyRules.js) decides when to alert the family.
 *
 * "Used" = screen turned on / unlocked, or WarmHearth touched. No app usage tracking.
 */
object SafetyMonitor {
    private const val SP = "safety"
    private const val MIN_GAP_MS = 10 * 60_000L
    private var started = false

    private fun sp(c: Context) = c.getSharedPreferences(SP, Context.MODE_PRIVATE)

    fun lastActive(c: Context) = sp(c).getLong("last_active", 0L)

    /** Cheap; called on every touch of the launcher. Throttled to one write a minute. */
    fun markActive(c: Context) {
        val now = System.currentTimeMillis()
        if (now - lastActive(c) > 60_000) sp(c).edit().putLong("last_active", now).apply()
    }

    fun callsScreenedToday(c: Context): Int =
        if (sp(c).getString("screened_day", "") == today()) sp(c).getInt("screened", 0) else 0

    fun countScreenedCall(c: Context) {
        val n = callsScreenedToday(c) + 1
        sp(c).edit().putString("screened_day", today()).putInt("screened", n).apply()
    }

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** Senior mode only: listen for screen/ringer/power changes and schedule the 30-minute report. */
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        markActive(app)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.action) {
                    Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> markActive(c)
                    // Ringer changes are reported at once: "on silent" is the #1 "can't reach mum" cause.
                    AudioManager.RINGER_MODE_CHANGED_ACTION, NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED,
                    "android.media.VOLUME_CHANGED_ACTION" -> report(c, force = true)
                    else -> report(c, force = false)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction("android.media.VOLUME_CHANGED_ACTION")
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
        }
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        WorkManager.getInstance(app).enqueueUniquePeriodicWork(
            "status",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<StatusWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
        report(app, force = true)
    }

    /** Writes devices/{id}.status in the background. Throttled unless [force]. */
    fun report(context: Context, force: Boolean) {
        val c = context.applicationContext
        c.graph.scope.launch {
            runCatching { reportNow(c, force) }.onFailure { com.lusanetech.warmhearth.util.Telemetry.recordError(it) }
        }
    }

    suspend fun reportNow(context: Context, force: Boolean = true) {
        val c = context.applicationContext
        val s = sp(c)
        val now = System.currentTimeMillis()
        if (!force && now - s.getLong("last_report", 0L) < MIN_GAP_MS) return
        val graph = c.graph
        run {
            val settings = graph.prefs.current()
            if (settings.mode != AppMode.SENIOR || settings.deviceId.isBlank()) return
            // The senior can switch status sharing off at any time (Privacy & sharing, no PIN needed).
            if (!settings.statusShared || !settings.paired) return
            if (graph.family.uid() == null) return

            val am = c.getSystemService(AudioManager::class.java)
            val nm = c.getSystemService(NotificationManager::class.java)
            val battery = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = battery?.let {
                val l = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val sc = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (l >= 0) l * 100 / sc else null
            }
            val plugged = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
            val ringer = when (am.ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> "silent"
                AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
                else -> "normal"
            }
            val max = am.getStreamMaxVolume(AudioManager.STREAM_RING).coerceAtLeast(1)
            val volume = am.getStreamVolume(AudioManager.STREAM_RING) * 100 / max
            val dnd = nm.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL
            val quiet = ringer != "normal" || volume == 0 || dnd
            // When did it go quiet? Kept locally so a long silence is measured from its start.
            val silentSince = if (quiet) s.getLong("silent_since", 0L).takeIf { it > 0 } ?: now.also {
                s.edit().putLong("silent_since", it).apply()
            } else 0L.also { s.edit().remove("silent_since").apply() }

            val status = hashMapOf<String, Any?>(
                "battery" to level,
                "charging" to plugged,
                "ringer" to ringer,
                "ringVolume" to volume,
                "dnd" to dnd,
                "silentSince" to silentSince.takeIf { it > 0 },
                "lastActiveAt" to lastActive(c).takeIf { it > 0 },
                "network" to network(c),
                "tz" to TimeZone.getDefault().id,
                "appVersion" to BuildConfig.VERSION_NAME,
                "callsScreenedToday" to callsScreenedToday(c),
                "canRestoreRinger" to Ringer.canChangeDnd(c),
                "fallDetection" to (settings.fallDetection && settings.premium),
                "reportedAt" to FieldValue.serverTimestamp(),
            )
            // Reminder sounds on this phone (system sound names only), so the family can pick one per medicine.
            if (s.getString("tones_day", "") != today()) {
                status["tones"] = com.lusanetech.warmhearth.util.Tones.list(c).map { mapOf("title" to it.first, "uri" to it.second) }
                s.edit().putString("tones_day", today()).apply()
            }
            val write = FirebaseFirestore.getInstance().collection("devices").document(settings.deviceId)
                .set(mapOf("status" to status), SetOptions.merge())
            s.edit().putLong("last_report", now).apply()
            // Offline writes are queued by Firestore; wait briefly so a worker doesn't exit first.
            withTimeoutOrNull(10_000) { runCatching { write.await() } }
        }
    }

    private fun network(c: Context): String {
        val cm = c.getSystemService(ConnectivityManager::class.java) ?: return "unknown"
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "none"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
            else -> "other"
        }
    }
}

/** Every 30 minutes (Android may stretch this in deep sleep; the server allows 6 hours before "offline"). */
class StatusWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { SafetyMonitor.reportNow(applicationContext) }
        runCatching { com.lusanetech.warmhearth.health.HealthVitals.sync(applicationContext) }
        return Result.success()
    }
}
