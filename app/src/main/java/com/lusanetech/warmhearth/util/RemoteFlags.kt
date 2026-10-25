package com.lusanetech.warmhearth.util

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.lusanetech.warmhearth.BuildConfig

/**
 * "Over the air" switches, changed in Firebase console → Remote Config, no app update needed.
 *
 * Google Play does not allow apps to download new code, so OTA here means *settings and content*:
 * which AI model to call, daily limits, turning a broken feature off, a message banner, and the
 * minimum app version (older versions are then forced to update through Play).
 *
 * Every value has a safe default below, so the app works even if Remote Config is unreachable.
 */
object RemoteFlags {
    private val rc by lazy { FirebaseRemoteConfig.getInstance() }

    private val defaults = mapOf<String, Any>(
        "ai_enabled" to true,
        "ai_model" to "gemini-2.5-flash-lite",
        "ai_free_scam_per_day" to 3L,
        "ai_premium_per_day" to 30L,
        "ai_prompt_note" to "",               // extra line appended to every AI prompt, to patch behaviour
        "fall_detection_enabled" to true,
        "voice_enabled" to true,
        "min_version_code" to 1L,             // below this: forced (immediate) update
        "recommended_version_code" to 1L,     // below this: flexible update prompt
        "announcement_en" to "",              // banner on the home screen, e.g. outage notice
        "announcement_hi" to "",
    )

    fun init() {
        runCatching {
            rc.setConfigSettingsAsync(remoteConfigSettings { minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 60 else 3600 })
            rc.setDefaultsAsync(defaults)
            rc.fetchAndActivate()
        }
    }

    private fun b(key: String) = runCatching { rc.getBoolean(key) }.getOrDefault(defaults[key] as Boolean)
    private fun l(key: String) = runCatching { rc.getLong(key) }.getOrDefault(defaults[key] as Long)
    private fun s(key: String) = runCatching { rc.getString(key) }.getOrDefault(defaults[key] as String)

    val aiEnabled get() = b("ai_enabled")
    val aiModel get() = s("ai_model").ifBlank { defaults["ai_model"] as String }
    val aiFreeScamPerDay get() = l("ai_free_scam_per_day").toInt()
    val aiPremiumPerDay get() = l("ai_premium_per_day").toInt()
    val aiPromptNote get() = s("ai_prompt_note")
    val fallDetectionEnabled get() = b("fall_detection_enabled")
    val voiceEnabled get() = b("voice_enabled")
    val minVersionCode get() = l("min_version_code")
    val recommendedVersionCode get() = l("recommended_version_code")
    fun announcement(hindi: Boolean) = s(if (hindi) "announcement_hi" else "announcement_en").ifBlank { s("announcement_en") }
}
