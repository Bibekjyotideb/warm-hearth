package com.lusanetech.warmhearth.family

import com.google.firebase.firestore.DocumentSnapshot
import com.lusanetech.warmhearth.data.CallShieldMode

/**
 * Settings of a senior's phone that the family can change from their own phone.
 * Stored as devices/{id}.settings. Both phones write it; the newer updatedAt wins.
 * The server reads checkIn / sleep / inactiveHours / alerts for safety alerts (functions/src/safetyRules.js).
 */
data class DeviceSettings(
    val textScale: Float? = null,
    val tiles: List<String>? = null,
    val callShield: String = CallShieldMode.WARN,
    val checkInEnabled: Boolean = true,
    val checkInBy: String = "11:00",
    val notifyOnOk: Boolean = true,
    val sleepStart: String = "22:00",
    val sleepEnd: String = "07:00",
    val inactiveHours: Int = 12,
    val alertCheckIn: Boolean = true,
    val alertInactive: Boolean = true,
    val alertBattery: Boolean = true,
    val alertSilent: Boolean = true,
    val alertOffline: Boolean = true,
    val updatedAt: Long = 0L,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "textScale" to textScale,
        "tiles" to tiles,
        "callShield" to callShield,
        "checkIn" to mapOf("enabled" to checkInEnabled, "by" to checkInBy, "notifyOnOk" to notifyOnOk),
        "sleep" to mapOf("start" to sleepStart, "end" to sleepEnd),
        "inactiveHours" to inactiveHours,
        "alerts" to mapOf(
            "checkin" to alertCheckIn, "inactive" to alertInactive, "battery" to alertBattery,
            "silent" to alertSilent, "offline" to alertOffline,
        ),
        "updatedAt" to updatedAt,
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun from(raw: Any?): DeviceSettings {
            val m = raw as? Map<String, Any?> ?: return DeviceSettings()
            val checkIn = m["checkIn"] as? Map<String, Any?> ?: emptyMap()
            val sleep = m["sleep"] as? Map<String, Any?> ?: emptyMap()
            val alerts = m["alerts"] as? Map<String, Any?> ?: emptyMap()
            fun b(map: Map<String, Any?>, k: String, d: Boolean) = map[k] as? Boolean ?: d
            fun t(v: Any?, d: String) = (v as? String)?.takeIf { it.matches(Regex("\\d{2}:\\d{2}")) } ?: d
            return DeviceSettings(
                textScale = (m["textScale"] as? Number)?.toFloat(),
                tiles = (m["tiles"] as? List<*>)?.filterIsInstance<String>(),
                callShield = (m["callShield"] as? String)?.takeIf { it in CallShieldMode.ALL } ?: CallShieldMode.WARN,
                checkInEnabled = b(checkIn, "enabled", true),
                checkInBy = t(checkIn["by"], "11:00"),
                notifyOnOk = b(checkIn, "notifyOnOk", true),
                sleepStart = t(sleep["start"], "22:00"),
                sleepEnd = t(sleep["end"], "07:00"),
                inactiveHours = (m["inactiveHours"] as? Number)?.toInt()?.coerceIn(2, 48) ?: 12,
                alertCheckIn = b(alerts, "checkin", true),
                alertInactive = b(alerts, "inactive", true),
                alertBattery = b(alerts, "battery", true),
                alertSilent = b(alerts, "silent", true),
                alertOffline = b(alerts, "offline", true),
                updatedAt = (m["updatedAt"] as? Number)?.toLong() ?: 0L,
            )
        }
    }
}

/** What the senior's phone reports about itself (devices/{id}.status). No content, no location. */
data class PhoneStatus(
    val battery: Int? = null,
    val charging: Boolean = false,
    /** normal | vibrate | silent */
    val ringer: String? = null,
    val ringVolume: Int? = null,
    val dnd: Boolean = false,
    val lastActiveAt: Long? = null,
    val reportedAt: Long? = null,
    val network: String? = null,
    val callsScreenedToday: Int = 0,
    val installedApps: List<Pair<String, String>> = emptyList(),
    /** The phone's time zone, e.g. "Asia/Kolkata": "today" and "10:00" mean the parent's day. */
    val tz: String? = null,
    /** Whether the phone allowed WarmHearth to turn the ringer back on (Do Not Disturb access). */
    val canRestoreRinger: Boolean = false,
) {
    val quiet get() = ringer != null && (ringer != "normal" || ringVolume == 0 || dnd)

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun from(raw: Any?): PhoneStatus? {
            val m = raw as? Map<String, Any?> ?: return null
            fun ms(v: Any?): Long? = when (v) {
                is Number -> v.toLong()
                is com.google.firebase.Timestamp -> v.toDate().time
                else -> null
            }
            return PhoneStatus(
                battery = (m["battery"] as? Number)?.toInt(),
                charging = m["charging"] as? Boolean ?: false,
                ringer = m["ringer"] as? String,
                ringVolume = (m["ringVolume"] as? Number)?.toInt(),
                dnd = m["dnd"] as? Boolean ?: false,
                lastActiveAt = ms(m["lastActiveAt"]),
                reportedAt = ms(m["reportedAt"]),
                network = m["network"] as? String,
                callsScreenedToday = (m["callsScreenedToday"] as? Number)?.toInt() ?: 0,
                tz = m["tz"] as? String,
                canRestoreRinger = m["canRestoreRinger"] as? Boolean ?: false,
                installedApps = (m["installedApps"] as? List<*>)?.mapNotNull { a ->
                    val am = a as? Map<String, Any?> ?: return@mapNotNull null
                    val pkg = am["pkg"] as? String ?: return@mapNotNull null
                    pkg to (am["label"] as? String ?: pkg)
                } ?: emptyList(),
            )
        }
    }
}

/** Everything the family dashboard shows about one parent's phone. */
data class DeviceState(
    val id: String,
    val name: String,
    val premium: Boolean,
    val status: PhoneStatus?,
    val settings: DeviceSettings,
    val lastCheckInAt: Long?,
) {
    companion object {
        fun from(d: DocumentSnapshot) = DeviceState(
            id = d.id,
            name = d.getString("name") ?: "",
            premium = d.getBoolean("premium") == true,
            status = PhoneStatus.from(d.get("status")),
            settings = DeviceSettings.from(d.get("settings")),
            lastCheckInAt = d.getTimestamp("lastCheckInAt")?.toDate()?.time,
        )
    }
}

data class FamilyNote(
    val id: String,
    val fromName: String,
    val text: String,
    val photoUrl: String?,
    val createdAt: Long,
    val seenAt: Long?,
)

/** One scheduled dose today and what happened to it. */
data class DoseStatus(val medicineId: String, val name: String, val time: String, val state: State, val at: Long?) {
    enum class State { TAKEN, MISSED, UPCOMING, DUE }
}
