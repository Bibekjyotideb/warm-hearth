package com.lusanetech.warmhearth.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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
)

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
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    private fun Preferences.toSettings() = Settings(
        mode = this[K.MODE]?.let { runCatching { AppMode.valueOf(it) }.getOrNull() } ?: AppMode.UNSET,
        pin = this[K.PIN] ?: "0000",
        fontScale = this[K.FONT] ?: 1.25f,
        tiles = this[K.TILES]?.split(",")?.filter { it.isNotBlank() } ?: Tiles.DEFAULT,
        seniorName = this[K.NAME] ?: "",
        deviceId = this[K.DEVICE_ID] ?: "",
        premium = this[K.PREMIUM] ?: false,
        fallDetection = this[K.FALL] ?: false,
        paired = this[K.PAIRED] ?: false,
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
}
