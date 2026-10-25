package com.lusanetech.warmhearth.fall

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.lusanetech.warmhearth.MainActivity
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.util.Notifications
import kotlin.math.sqrt

/**
 * Watches the accelerometer for the classic fall signature:
 *   free fall (near 0 g)  ->  hard impact (> 2.5 g)  ->  lying still for a couple of seconds.
 * When all three happen in order, it opens FallAlertActivity, which counts down and then
 * triggers SOS unless the senior taps "I'm OK".
 *
 * Phone-based fall detection is a helper, not a medical device; the Play listing and the in-app
 * text say so.
 */
class FallDetectionService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "FallDetection"
        private const val FREE_FALL_G = 0.45f
        private const val IMPACT_G = 2.5f
        private const val FREE_FALL_MIN_MS = 60L
        private const val IMPACT_WINDOW_MS = 1_000L
        private const val STILL_CHECK_MS = 2_500L
        private const val STILL_TOLERANCE_G = 0.25f
        private const val COOLDOWN_MS = 60_000L

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, FallDetectionService::class.java))
            } catch (e: Exception) {
                // Android blocks starting foreground services from the background in some states.
                // The service is started again the next time the launcher opens.
                Log.w(TAG, "Could not start fall detection now", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FallDetectionService::class.java))
        }
    }

    private lateinit var sensors: SensorManager
    private var freeFallStart = 0L
    private var impactAt = 0L
    private var lastAlert = 0L
    private val stillSamples = ArrayList<Float>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0
        ServiceCompat.startForeground(this, Notifications.ID_FALL_SERVICE, ongoingNotification(), type)
        sensors = getSystemService(SensorManager::class.java)
        sensors.unregisterListener(this)
        sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (::sensors.isInitialized) sensors.unregisterListener(this)
        super.onDestroy()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
        val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        val now = SystemClock.elapsedRealtime()
        if (now - lastAlert < COOLDOWN_MS) return

        when {
            // Stage 3: after an impact, collect samples and check the phone is lying still.
            impactAt > 0 -> {
                if (now - impactAt < 500) return // ignore the bounce right after impact
                stillSamples += g
                if (now - impactAt >= 500 + STILL_CHECK_MS) {
                    val still = stillSamples.isNotEmpty() && stillSamples.all { kotlin.math.abs(it - 1f) < STILL_TOLERANCE_G }
                    reset()
                    if (still) onFall(now)
                }
            }
            // Stage 1: free fall.
            g < FREE_FALL_G -> if (freeFallStart == 0L) freeFallStart = now
            // Stage 2: impact shortly after a real free fall.
            g > IMPACT_G && freeFallStart > 0 -> {
                val fell = now - freeFallStart
                if (fell in FREE_FALL_MIN_MS..IMPACT_WINDOW_MS) { impactAt = now; stillSamples.clear() } else reset()
            }
            freeFallStart > 0 && now - freeFallStart > IMPACT_WINDOW_MS -> reset()
        }
    }

    private fun reset() {
        freeFallStart = 0L
        impactAt = 0L
        stillSamples.clear()
    }

    private fun onFall(now: Long) {
        lastAlert = now
        Log.i(TAG, "Possible fall detected")
        val open = Intent(this, FallAlertActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(this, 2, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(this, Notifications.CH_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.fall_question))
            .setContentText(getString(R.string.fall_notification_text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        Notifications.notify(this, Notifications.ID_FALL_ALERT, n)
    }

    private fun ongoingNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, Notifications.CH_SERVICE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.fall_service_title))
            .setContentText(getString(R.string.fall_service_text))
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }
}
