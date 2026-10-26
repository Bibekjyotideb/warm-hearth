package com.lusanetech.warmhearth.calls

import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.core.app.NotificationCompat
import com.lusanetech.warmhearth.MainActivity
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.data.CallShieldMode
import com.lusanetech.warmhearth.graph
import com.lusanetech.warmhearth.safety.SafetyMonitor
import com.lusanetech.warmhearth.util.Notifications
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Scam call shield (Android 10+). Android only asks us about numbers that are NOT saved in the
 * phone's contacts; we also let through anyone in WarmHearth's people.
 *   warn     ring as normal + a big notification: "Unknown number. Never share an OTP on a call."
 *   silence  don't ring; shows as a missed call they can return
 *   block    reject the call (it still appears in the call log)
 * Numbers are never uploaded; the family only sees a count per day.
 */
object CallShield {
    fun available() = Build.VERSION.SDK_INT >= 29

    fun isEnabled(c: Context): Boolean =
        available() && c.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true

    /** Launch with an ActivityResult launcher; shows Android's "Set WarmHearth as call screening app?". */
    fun requestIntent(c: Context): Intent? =
        if (available()) c.getSystemService(RoleManager::class.java)?.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING) else null

    /** Compare the last 10 digits so "+91 98xxx", "098xxx" and "98xxx" match. */
    fun sameNumber(a: String, b: String): Boolean {
        val da = a.filter(Char::isDigit).takeLast(10)
        val db = b.filter(Char::isDigit).takeLast(10)
        return da.length >= 6 && da == db
    }
}

class CallShieldService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        val allow = CallResponse.Builder().build()
        if (Build.VERSION.SDK_INT >= 29 && details.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(details, allow); return
        }
        val number = details.handle?.schemeSpecificPart.orEmpty()
        val graph = applicationContext.graph
        graph.scope.launch {
            // Android waits ~5 s for an answer; never hold a call up longer than 2 s.
            val decision = withTimeoutOrNull(2_000) {
                val mode = graph.prefs.current().callShield
                val known = number.isNotBlank() && graph.db.contacts().getAll().any { CallShield.sameNumber(it.phone, number) }
                if (known) CallShieldMode.OFF else mode
            } ?: CallShieldMode.OFF

            val response = when (decision) {
                CallShieldMode.SILENCE -> if (Build.VERSION.SDK_INT >= 29) CallResponse.Builder().setSilenceCall(true).build() else allow
                CallShieldMode.BLOCK -> CallResponse.Builder().setDisallowCall(true).setRejectCall(true).setSkipNotification(false).build()
                else -> allow
            }
            respondToCall(details, response)
            if (decision != CallShieldMode.OFF) {
                SafetyMonitor.countScreenedCall(applicationContext)
                notifyUnknown(decision, number)
            }
        }
    }

    private fun notifyUnknown(mode: String, number: String) {
        val pretty = number.ifBlank { getString(R.string.hidden_number) }
        val (title, body) = when (mode) {
            CallShieldMode.WARN -> getString(R.string.shield_warn_title) to getString(R.string.shield_warn_body)
            CallShieldMode.SILENCE -> getString(R.string.shield_silenced_title, pretty) to getString(R.string.shield_after_body)
            else -> getString(R.string.shield_blocked_title, pretty) to getString(R.string.shield_after_body)
        }
        val pi = PendingIntent.getActivity(
            this, 7100, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(this, if (mode == CallShieldMode.WARN) Notifications.CH_ALERTS_SOFT else Notifications.CH_UPDATES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (mode == CallShieldMode.WARN) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .apply { if (mode == CallShieldMode.WARN) setTimeoutAfter(120_000) }
            .build()
        Notifications.notify(this, Notifications.ID_SHIELD + (number.hashCode() and 0xFF), n)
    }
}
