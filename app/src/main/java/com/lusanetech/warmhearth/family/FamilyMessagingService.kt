package com.lusanetech.warmhearth.family

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.lusanetech.warmhearth.MainActivity
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.data.AppMode
import com.lusanetech.warmhearth.graph
import com.lusanetech.warmhearth.util.Notifications
import kotlinx.coroutines.launch

/** Receives SOS / missed-dose alerts on the family member's phone. */
class FamilyMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val graph = applicationContext.graph
        graph.scope.launch { if (graph.prefs.current().mode == AppMode.FAMILY) graph.family.registerFamilyToken() }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = message.notification?.title ?: data["title"] ?: getString(R.string.app_name)
        val body = message.notification?.body ?: data["body"] ?: return
        val lat = data["lat"]?.toDoubleOrNull()
        val lng = data["lng"]?.toDoubleOrNull()

        // Tapping an SOS with a location opens the map; anything else opens the app.
        val tap = if (lat != null && lng != null) {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=$lat,$lng"))
        } else {
            Intent(this, MainActivity::class.java)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(this, body.hashCode(), tap, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val n = NotificationCompat.Builder(this, Notifications.CH_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        Notifications.notify(this, Notifications.ID_FAMILY_BASE + (System.currentTimeMillis() % 1000).toInt(), n)
    }
}
