package com.lusanetech.warmhearth.sos

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.data.FavoriteContact
import com.lusanetech.warmhearth.graph
import com.lusanetech.warmhearth.launcher.Apps
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

data class SosResult(
    val mapsLink: String?,
    val contacts: List<FavoriteContact>,
    val familyNotified: Boolean,
)

object Sos {
    /**
     * What happens on SOS:
     * 1. Get the location (up to 8 seconds).
     * 2. Push an alert to every linked family phone (works even without SMS).
     * 3. Call the first emergency contact.
     * The screen then offers "send my location by SMS" and the national emergency number.
     *
     * We deliberately do not send SMS silently: SEND_SMS is a restricted permission on Google Play
     * and needs a policy declaration. Can be added later once the app has traction.
     */
    suspend fun trigger(context: Context, reason: String, callFirst: Boolean = true): SosResult {
        val graph = context.graph
        val location = currentLocation(context)
        val link = location?.let { "https://maps.google.com/?q=${it.latitude},${it.longitude}" }
        val name = graph.prefs.current().seniorName.ifBlank { context.getString(R.string.your_family_member) }

        val message = when (reason) {
            "fall" -> context.getString(R.string.sos_family_fall, name)
            else -> context.getString(R.string.sos_family_button, name)
        }
        val notified = graph.family.sendEvent(type = "sos", message = message, lat = location?.latitude, lng = location?.longitude)

        val contacts = graph.db.contacts().emergencyContacts().ifEmpty { graph.db.contacts().getAll().take(1) }
        if (callFirst) contacts.firstOrNull()?.let { com.lusanetech.warmhearth.calls.CallHelper.call(context, it.phone) }
        return SosResult(link, contacts, notified)
    }

    /** Opens the SMS app with every emergency contact and the location already filled in. */
    fun shareBySms(context: Context, result: SosResult) {
        val numbers = result.contacts.joinToString(";") { it.phone }
        val body = context.getString(R.string.sos_sms_body) + (result.mapsLink?.let { "\n$it" } ?: "")
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(numbers))).putExtra("sms_body", body)
        Apps.start(context, intent)
    }

    fun emergencyNumber(): String = when (Locale.getDefault().country) {
        "US", "CA" -> "911"
        "GB" -> "999"
        "AU" -> "000"
        else -> "112" // India, EU and most GSM networks
    }

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(context: Context): Location? {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null

        val lastKnown = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }

        if (Build.VERSION.SDK_INT < 30) return lastKnown
        val provider = when {
            fine && lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return lastKnown
        }
        val fresh = withTimeoutOrNull(8_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                lm.getCurrentLocation(provider, null, ContextCompat.getMainExecutor(context)) { loc ->
                    if (cont.isActive) cont.resume(loc)
                }
            }
        }
        return fresh ?: lastKnown
    }
}
