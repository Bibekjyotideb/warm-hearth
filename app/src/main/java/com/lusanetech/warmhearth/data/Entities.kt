package com.lusanetech.warmhearth.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A person shown as a big photo tile. Tapping it calls them. */
@Entity(tableName = "contacts")
data class FavoriteContact(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    /** Absolute path of a photo copied into app storage, or null for an initials avatar. */
    val photoPath: String? = null,
    val position: Int = 0,
    /** Emergency contacts get the SOS call and share. The first one is called. */
    val isEmergency: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
    /** Shown as a face on the home screen (otherwise only in People and the dialer's list). */
    val onHome: Boolean = true,
    /** Download URL of a photo the family added from their phone, so we know which one we have. */
    val photoUrl: String? = null,
)

@Entity(tableName = "medicines")
data class Medicine(
    @PrimaryKey val id: String,
    val name: String,
    val dose: String = "",
    val photoPath: String? = null,
    /** Comma separated 24h times, e.g. "08:00,20:30". */
    val times: String,
    val active: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis(),
    /** Reminder sound: "" = the phone's default (settings), "alarm", "voice" (speaks the name), or a ringtone URI. */
    val tone: String = "",
    /** Download URL of a pill photo the family added from their phone. */
    val photoUrl: String? = null,
) {
    fun timeList(): List<String> = times.split(",").map { it.trim() }.filter { it.matches(Regex("\\d{2}:\\d{2}")) }
}

/** One row per dose the senior confirmed. slot = "yyyy-MM-dd HH:mm". */
@Entity(tableName = "dose_logs")
data class DoseLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicineId: String,
    val slot: String,
    val takenAt: Long,
)
