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
    /** Fall detection on the senior phone (null = leave as set on the phone). */
    val fallDetection: Boolean? = null,
    val voiceCommands: Boolean = true,
    /** New settings PIN set by the family (never sent back by the senior phone). */
    val pin: String? = null,
    /** Default medicine reminder sound: "alarm", "voice" or a ringtone URI on the senior phone. */
    val medTone: String = "alarm",
    /** Heart-rate alerts from watch readings: off by default, informational only (not a medical alarm). */
    val hrAlerts: Boolean = false,
    val hrHigh: Int = 120,
    val hrLow: Int = 45,
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
        "fallDetection" to fallDetection,
        "voiceCommands" to voiceCommands,
        "pin" to pin,
        "medTone" to medTone,
        "vitals" to mapOf("hrAlerts" to hrAlerts, "hrHigh" to hrHigh, "hrLow" to hrLow),
        "updatedAt" to updatedAt,
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        /**
         * [includePin]: only the senior phone reads the PIN. The family app always sends null unless
         * it's changing the PIN, so an old PIN in the document can't overwrite a newer one set on the phone.
         */
        fun from(raw: Any?, includePin: Boolean = false): DeviceSettings {
            val m = raw as? Map<String, Any?> ?: return DeviceSettings()
            val checkIn = m["checkIn"] as? Map<String, Any?> ?: emptyMap()
            val sleep = m["sleep"] as? Map<String, Any?> ?: emptyMap()
            val alerts = m["alerts"] as? Map<String, Any?> ?: emptyMap()
            val vitals = m["vitals"] as? Map<String, Any?> ?: emptyMap()
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
                fallDetection = m["fallDetection"] as? Boolean,
                voiceCommands = m["voiceCommands"] as? Boolean ?: true,
                pin = if (includePin) m["pin"] as? String else null,
                medTone = (m["medTone"] as? String)?.takeIf { it.isNotBlank() } ?: "alarm",
                hrAlerts = b(vitals, "hrAlerts", false),
                hrHigh = (vitals["hrHigh"] as? Number)?.toInt()?.coerceIn(80, 200) ?: 120,
                hrLow = (vitals["hrLow"] as? Number)?.toInt()?.coerceIn(30, 70) ?: 45,
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
    /** Alarm / notification sounds available on the senior phone: (title, uri). */
    val tones: List<Pair<String, String>> = emptyList(),
    val fallDetectionOn: Boolean = false,
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
                tones = (m["tones"] as? List<*>)?.mapNotNull { t ->
                    val tm = t as? Map<String, Any?> ?: return@mapNotNull null
                    val uri = tm["uri"] as? String ?: return@mapNotNull null
                    (tm["title"] as? String ?: uri) to uri
                } ?: emptyList(),
                fallDetectionOn = m["fallDetection"] as? Boolean ?: false,
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
    val vitals: Vitals?,
) {
    companion object {
        fun from(d: DocumentSnapshot) = DeviceState(
            id = d.id,
            name = d.getString("name") ?: "",
            premium = d.getBoolean("premium") == true,
            status = PhoneStatus.from(d.get("status")),
            settings = DeviceSettings.from(d.get("settings")),
            lastCheckInAt = d.getTimestamp("lastCheckInAt")?.toDate()?.time,
            vitals = Vitals.from(d.get("vitals")),
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

/**
 * Latest watch / band readings from Health Connect on the senior phone (devices/{id}.vitals).
 * Null fields = that device doesn't measure it, or nothing recent.
 */
data class Vitals(
    val heartRate: Int? = null,
    val heartRateAt: Long? = null,
    val restingHeartRate: Int? = null,
    val hrMin: Int? = null,
    val hrMax: Int? = null,
    val stepsToday: Long? = null,
    val sleepMinutes: Int? = null,
    val spo2: Double? = null,
    val bpSystolic: Int? = null,
    val bpDiastolic: Int? = null,
    val bpAt: Long? = null,
    val glucoseMgDl: Int? = null,
    val glucoseAt: Long? = null,
    val syncedAt: Long? = null,
    val sources: List<String> = emptyList(),
) {
    val empty get() = heartRate == null && stepsToday == null && sleepMinutes == null && spo2 == null && bpSystolic == null && glucoseMgDl == null

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun from(raw: Any?): Vitals? {
            val m = raw as? Map<String, Any?> ?: return null
            fun i(k: String) = (m[k] as? Number)?.toInt()
            fun l(k: String) = (m[k] as? Number)?.toLong()
            fun ts(k: String) = when (val v = m[k]) { is Number -> v.toLong(); is com.google.firebase.Timestamp -> v.toDate().time; else -> null }
            return Vitals(
                heartRate = i("heartRate"), heartRateAt = ts("heartRateAt"), restingHeartRate = i("restingHeartRate"),
                hrMin = i("hrMin"), hrMax = i("hrMax"), stepsToday = l("stepsToday"), sleepMinutes = i("sleepMinutes"),
                spo2 = (m["spo2"] as? Number)?.toDouble(), bpSystolic = i("bpSystolic"), bpDiastolic = i("bpDiastolic"), bpAt = ts("bpAt"),
                glucoseMgDl = i("glucoseMgDl"), glucoseAt = ts("glucoseAt"), syncedAt = ts("syncedAt"),
                sources = (m["sources"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            )
        }
    }
}

data class DayVitals(
    val date: String,
    val steps: Long?,
    val hrAvg: Int?,
    val hrMin: Int?,
    val hrMax: Int?,
    val sleepMinutes: Int?,
    val spo2: Double?,
    val bpSystolic: Int?,
    val bpDiastolic: Int?,
    val glucose: Int?,
)
