package com.lusanetech.warmhearth.family

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.lusanetech.warmhearth.MainActivity
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.data.AppMode
import com.lusanetech.warmhearth.graph
import com.lusanetech.warmhearth.safety.RemoteCommands
import com.lusanetech.warmhearth.util.Notifications
import kotlinx.coroutines.launch

/**
 * Push messages, both sides:
 *  - Senior phone: remote commands from family ("ring", "ringer_on", "sync").
 *  - Family phone: SOS, call requests, check-ins and safety alerts, with one-tap actions
 *    such as "Turn ringer on" right in the notification.
 */
class FamilyMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val graph = applicationContext.graph
        graph.scope.launch {
            when (graph.prefs.current().mode) {
                AppMode.FAMILY -> graph.family.registerFamilyToken()
                AppMode.SENIOR -> graph.family.registerDeviceToken()
                else -> Unit
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["kind"] == "command") {
            val graph = applicationContext.graph
            graph.scope.launch {
                val s = graph.prefs.current()
                if (s.mode == AppMode.SENIOR && s.deviceId.isNotBlank()) {
                    RemoteCommands.handle(this@FamilyMessagingService, s.deviceId, data["id"] ?: return@launch, data["type"] ?: return@launch, data["byName"] ?: "")
                }
            }
            return
        }
        showFamilyAlert(this, data, message.notification?.title, message.notification?.body)
    }

    companion object {
        private val URGENT = setOf("sos", "missed_dose", "no_checkin", "inactive", "offline", "silent")

        fun showFamilyAlert(c: Context, data: Map<String, String>, fallbackTitle: String?, fallbackBody: String?) {
            val type = data["type"].orEmpty()
            val title = data["title"] ?: fallbackTitle ?: c.getString(R.string.app_name)
            val body = data["body"] ?: fallbackBody ?: return
            val deviceId = data["deviceId"]
            val lat = data["lat"]?.toDoubleOrNull()
            val lng = data["lng"]?.toDoubleOrNull()
            val id = Notifications.ID_FAMILY_BASE + ((type + deviceId).hashCode() and 0x3FF)

            // Tapping an SOS with a location opens the map; anything else opens the app on that parent.
            val tap = if (lat != null && lng != null) {
                Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=$lat,$lng"))
            } else {
                Intent(c, MainActivity::class.java).putExtra(MainActivity.EXTRA_DEVICE, deviceId)
            }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pi = PendingIntent.getActivity(c, id, tap, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

            val urgent = type in URGENT && data["quiet"] != "1"
            val b = NotificationCompat.Builder(c, if (urgent) Notifications.CH_ALERTS else Notifications.CH_ALERTS_SOFT)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(if (urgent) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
                .setCategory(if (urgent) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_MESSAGE)
                .setContentIntent(pi)
                .setAutoCancel(true)

            // One-tap fixes from the notification itself (Premium; the command is refused otherwise).
            if (deviceId != null) {
                when (type) {
                    "silent" -> b.addAction(0, c.getString(R.string.action_ringer_on), AlertActionReceiver.pending(c, deviceId, "ringer_on", id))
                    "inactive", "no_checkin", "offline" -> b.addAction(0, c.getString(R.string.action_ring_phone), AlertActionReceiver.pending(c, deviceId, "ring", id))
                }
            }
            Notifications.notify(c, id, b.build())
        }
    }
}

/** Handles "Turn ringer on" / "Ring her phone" tapped on a family notification. */
class AlertActionReceiver : BroadcastReceiver() {
    companion object {
        private const val EXTRA_DEVICE = "device"
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_NOTIF = "notif"

        fun pending(c: Context, deviceId: String, type: String, notifId: Int): PendingIntent = PendingIntent.getBroadcast(
            c, (deviceId + type).hashCode(),
            Intent(c, AlertActionReceiver::class.java).putExtra(EXTRA_DEVICE, deviceId).putExtra(EXTRA_TYPE, type).putExtra(EXTRA_NOTIF, notifId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        val deviceId = intent.getStringExtra(EXTRA_DEVICE) ?: return
        val type = intent.getStringExtra(EXTRA_TYPE) ?: return
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra(EXTRA_NOTIF, 0))
        val pending = goAsync()
        context.graph.scope.launch {
            try { context.graph.remote.sendCommand(deviceId, type) } finally { pending.finish() }
        }
    }
}
