package com.lusanetech.warmhearth.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class AppMode { UNSET, SENIOR, FAMILY }

/** Home-screen tiles. Custom apps are stored as "pkg:<packageName>". */
object Tiles {
    const val PHONE = "phone"
    const val CONTACTS = "contacts"
    const val MESSAGES = "messages"
    const val CAMERA = "camera"
    const val PHOTOS = "photos"
    const val WHATSAPP = "whatsapp"
    const val MEDS = "meds"
    const val APPS = "apps"
    const val SCAM = "scam"
    const val READ = "read"
    val DEFAULT = listOf(PHONE, CONTACTS, WHATSAPP, MESSAGES, CAMERA, PHOTOS, MEDS, SCAM, READ, APPS)
}

data class Settings(
    val mode: AppMode,
    val pin: String,
    val fontScale: Float,
    val tiles: List<String>,
    val seniorName: String,
    val deviceId: String,
    val premium: Boolean,
    val fallDetection: Boolean,
    val paired: Boolean,
    /** Call shield for numbers that aren't in WarmHearth's people: off | warn | silence | block. */
    val callShield: String,
    /** Daily "I'm OK" card on the home screen (set by the family). */
    val checkInEnabled: Boolean,
    /** "yyyy-MM-dd" of the last check-in, so the card only shows until they've tapped it today. */
    val lastCheckInDay: String,
    /** updatedAt of the last settings document applied from the family, to avoid loops. */
    val settingsAppliedAt: Long,
)

object CallShieldMode {
    const val OFF = "off"
    const val WARN = "warn"
    const val SILENCE = "silence"
    const val BLOCK = "block"
    val ALL = listOf(OFF, WARN, SILENCE, BLOCK)
}

class Prefs(private val context: Context) {
    private object K {
        val MODE = stringPreferencesKey("mode")
        val PIN = stringPreferencesKey("pin")
        val FONT = floatPreferencesKey("font_scale")
        val TILES = stringPreferencesKey("tiles")
        val NAME = stringPreferencesKey("senior_name")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val PREMIUM = booleanPreferencesKey("premium")
        val FALL = booleanPreferencesKey("fall_detection")
        val PAIRED = booleanPreferencesKey("paired")
        val SHIELD = stringPreferencesKey("call_shield")
        val CHECKIN = booleanPreferencesKey("checkin_enabled")
        val CHECKIN_DAY = stringPreferencesKey("checkin_day")
        val APPLIED = longPreferencesKey("settings_applied_at")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    private fun Preferences.toSettings() = Settings(
        mode = this[K.MODE]?.let { runCatching { AppMode.valueOf(it) }.getOrNull() } ?: AppMode.UNSET,
        pin = this[K.PIN] ?: "0000",
        fontScale = this[K.FONT] ?: 1.25f,
        tiles = this[K.TILES]?.split(",")?.filter { it.isNotBlank() }
            ?: com.lusanetech.warmhearth.util.RemoteFlags.defaultTiles() ?: Tiles.DEFAULT, // OTA default for new installs
        seniorName = this[K.NAME] ?: "",
        deviceId = this[K.DEVICE_ID] ?: "",
        premium = this[K.PREMIUM] ?: false,
        fallDetection = this[K.FALL] ?: false,
        paired = this[K.PAIRED] ?: false,
        callShield = this[K.SHIELD] ?: CallShieldMode.WARN,
        checkInEnabled = this[K.CHECKIN] ?: true,
        lastCheckInDay = this[K.CHECKIN_DAY] ?: "",
        settingsAppliedAt = this[K.APPLIED] ?: 0L,
    )

    /** Stable random id for this phone, created once. Used as the Firestore device document id. */
    suspend fun ensureDeviceId(): String {
        current().deviceId.takeIf { it.isNotBlank() }?.let { return it }
        val id = UUID.randomUUID().toString()
        context.dataStore.edit { it[K.DEVICE_ID] = id }
        return id
    }

    suspend fun setMode(mode: AppMode) = context.dataStore.edit { it[K.MODE] = mode.name }
    suspend fun setPin(pin: String) = context.dataStore.edit { it[K.PIN] = pin }
    suspend fun setFontScale(scale: Float) = context.dataStore.edit { it[K.FONT] = scale }
    suspend fun setTiles(tiles: List<String>) = context.dataStore.edit { it[K.TILES] = tiles.joinToString(",") }
    suspend fun setSeniorName(name: String) = context.dataStore.edit { it[K.NAME] = name }
    suspend fun setPremium(value: Boolean) = context.dataStore.edit { it[K.PREMIUM] = value }
    suspend fun setFallDetection(value: Boolean) = context.dataStore.edit { it[K.FALL] = value }
    suspend fun setPaired(value: Boolean) = context.dataStore.edit { it[K.PAIRED] = value }
    suspend fun setCallShield(mode: String) = context.dataStore.edit { it[K.SHIELD] = mode }
    suspend fun setCheckInEnabled(value: Boolean) = context.dataStore.edit { it[K.CHECKIN] = value }
    suspend fun setLastCheckInDay(day: String) = context.dataStore.edit { it[K.CHECKIN_DAY] = day }

    /** Applies settings the family changed remotely, in one write. */
    suspend fun applyRemote(textScale: Float?, tiles: List<String>?, callShield: String?, checkIn: Boolean?, updatedAt: Long) =
        context.dataStore.edit { p ->
            textScale?.let { p[K.FONT] = it }
            tiles?.let { p[K.TILES] = it.joinToString(",") }
            callShield?.takeIf { it in CallShieldMode.ALL }?.let { p[K.SHIELD] = it }
            checkIn?.let { p[K.CHECKIN] = it }
            p[K.APPLIED] = updatedAt
        }

    /** Used by "Delete this phone's data": back to a fresh install (a new device id is created). */
    suspend fun clearAll() = context.dataStore.edit { it.clear() }
}
