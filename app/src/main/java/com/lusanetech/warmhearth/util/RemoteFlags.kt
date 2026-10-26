package com.lusanetech.warmhearth.util

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.lusanetech.warmhearth.BuildConfig
import org.json.JSONArray

/**
 * Over-the-air changes, made in Firebase console → Remote Config. Published values reach phones
 * within about an hour (instantly for new app starts after a real-time update), no Play release.
 *
 * What can change OTA (data, not code: Google Play's Device and Network Abuse policy forbids
 * downloading new executable code, but changing content and switches is normal and allowed):
 *   - Feature switches: AI, voice, fall detection, check-ins, photo notes, call shield, remote ring
 *   - Text: home-screen banner, paywall headline, an offer line, family-app promo card
 *   - Defaults: home tiles for new installs, paywall's pre-selected plan, AI limits and model
 *   - Help: the FAQ shown in Help & support (JSON)
 *   - Forced / suggested updates (min_version_code / recommended_version_code)
 * Prices are also OTA: they come live from Play Console, never from the app.
 * Pop-up campaigns (images, buttons, targeting) run from Firebase In-App Messaging, also no release.
 *
 * Every value has a safe default below, so the app works even if Remote Config is unreachable.
 */
object RemoteFlags {
    private val rc by lazy { FirebaseRemoteConfig.getInstance() }

    private val defaults = mapOf<String, Any>(
        // AI
        "ai_enabled" to true,
        "ai_model" to "gemini-2.5-flash-lite",
        "ai_free_scam_per_day" to 3L,
        "ai_premium_per_day" to 30L,
        "ai_prompt_note" to "",               // extra line appended to every AI prompt, to patch behaviour
        // Feature switches (kill switches if something misbehaves on some phones)
        "fall_detection_enabled" to true,
        "voice_enabled" to true,
        "checkin_enabled" to true,
        "notes_enabled" to true,
        "call_shield_enabled" to true,
        "remote_ring_enabled" to true,
        // Updates
        "min_version_code" to 1L,             // below this: forced (immediate) update
        "recommended_version_code" to 1L,     // below this: flexible update prompt
        // Content
        "announcement_en" to "",              // banner on the senior home screen, e.g. outage notice
        "announcement_hi" to "",
        "family_promo_en" to "",              // card at the top of the family app (tips, offers)
        "family_promo_hi" to "",
        "paywall_headline_en" to "",          // replaces the paywall pitch line when set
        "paywall_headline_hi" to "",
        "paywall_offer_en" to "",             // small highlighted line, e.g. "Diwali: first month ₹49"
        "paywall_offer_hi" to "",
        "paywall_default_plan" to "yearly",   // yearly | monthly
        "default_tiles" to "",                // comma list for new installs, e.g. "phone,contacts,whatsapp,…"
        "help_faq_json" to "",                // [{"q_en":"…","a_en":"…","q_hi":"…","a_hi":"…"}]
    )

    fun init() {
        runCatching {
            rc.setConfigSettingsAsync(remoteConfigSettings { minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 60 else 3600 })
            rc.setDefaultsAsync(defaults)
            rc.fetchAndActivate()
            // Real-time: published changes arrive within seconds while the app is open.
            rc.addOnConfigUpdateListener(object : com.google.firebase.remoteconfig.ConfigUpdateListener {
                override fun onUpdate(configUpdate: com.google.firebase.remoteconfig.ConfigUpdate) { rc.activate() }
                override fun onError(error: com.google.firebase.remoteconfig.FirebaseRemoteConfigException) = Unit
            })
        }
    }

    private fun b(key: String) = runCatching { rc.getBoolean(key) }.getOrDefault(defaults[key] as Boolean)
    private fun l(key: String) = runCatching { rc.getLong(key) }.getOrDefault(defaults[key] as Long)
    private fun s(key: String) = runCatching { rc.getString(key) }.getOrDefault(defaults[key] as String)
    private fun lang(base: String, hindi: Boolean) = s(if (hindi) "${base}_hi" else "${base}_en").ifBlank { s("${base}_en") }

    val aiEnabled get() = b("ai_enabled")
    val aiModel get() = s("ai_model").ifBlank { defaults["ai_model"] as String }
    val aiFreeScamPerDay get() = l("ai_free_scam_per_day").toInt()
    val aiPremiumPerDay get() = l("ai_premium_per_day").toInt()
    val aiPromptNote get() = s("ai_prompt_note")
    val fallDetectionEnabled get() = b("fall_detection_enabled")
    val voiceEnabled get() = b("voice_enabled")
    val checkInEnabled get() = b("checkin_enabled")
    val notesEnabled get() = b("notes_enabled")
    val callShieldEnabled get() = b("call_shield_enabled")
    val remoteRingEnabled get() = b("remote_ring_enabled")
    val minVersionCode get() = l("min_version_code")
    val recommendedVersionCode get() = l("recommended_version_code")
    val paywallDefaultPlan get() = s("paywall_default_plan").takeIf { it == "monthly" } ?: "yearly"

    fun announcement(hindi: Boolean) = lang("announcement", hindi)
    fun familyPromo(hindi: Boolean) = lang("family_promo", hindi)
    fun paywallHeadline(hindi: Boolean) = lang("paywall_headline", hindi)
    fun paywallOffer(hindi: Boolean) = lang("paywall_offer", hindi)

    /** Tiles for a brand-new install, or null to use the built-in default. */
    fun defaultTiles(): List<String>? = s("default_tiles").split(",").map { it.trim() }.filter { it.isNotBlank() }.takeIf { it.size >= 2 }

    data class Faq(val q: String, val a: String)

    /** Extra FAQ entries for Help & support. Bad JSON is ignored, never crashes. */
    fun faq(hindi: Boolean): List<Faq> = runCatching {
        val arr = JSONArray(s("help_faq_json").ifBlank { "[]" })
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val q = o.optString(if (hindi) "q_hi" else "q_en").ifBlank { o.optString("q_en") }
            val a = o.optString(if (hindi) "a_hi" else "a_en").ifBlank { o.optString("a_en") }
            if (q.isBlank() || a.isBlank()) null else Faq(q, a)
        }
    }.getOrDefault(emptyList())
}
