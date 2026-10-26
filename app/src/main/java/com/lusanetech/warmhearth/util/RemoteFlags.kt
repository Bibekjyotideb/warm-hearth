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
        "vitals_enabled" to true,             // watch / band readings (Health Connect)
        "shop_enabled" to true,               // "Which watch to buy?" in the family app
        "shop_json" to "",                    // replaces the built-in device list (same shape as DEFAULT_SHOP)
        "shop_disclosure_en" to "",
        "shop_disclosure_hi" to "",
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

    val shopEnabled get() = b("shop_enabled")
    val vitalsEnabled get() = b("vitals_enabled")
    fun shopDisclosure(hindi: Boolean) = lang("shop_disclosure", hindi)

    /**
     * Watches and bands that write to Health Connect, for the family app's "Which watch to buy?".
     * Same list for everyone (never chosen from anyone's health data: Health Connect policy).
     * Links go to warmhearth.lusanetech.com/devices, which carries the affiliate links and the
     * disclosure; replace the whole list from Remote Config (shop_json) without a release.
     * features: steps, hr, spo2, sleep, bp, fall, sos
     */
    data class ShopItem(
        val id: String, val name: String, val tier: String, val features: List<String>,
        val app: String, val note: String, val urlIn: String, val urlIntl: String,
    )

    private const val SITE_DEVICES = "https://warmhearth.lusanetech.com/devices"
    private val DEFAULT_SHOP = """[
      {"id":"xiaomi-band","name":"Xiaomi Smart Band","tier":"budget","features":["steps","hr","sleep"],"app":"Mi Fitness","note":"Light, long battery life. Simple to wear all day."},
      {"id":"amazfit-band","name":"Amazfit Band / Bip","tier":"budget","features":["steps","hr","spo2","sleep"],"app":"Zepp","note":"Big screen for the price; turn on Health Connect in the Zepp app."},
      {"id":"fitbit-charge","name":"Fitbit Charge","tier":"mid","features":["steps","hr","spo2","sleep"],"app":"Fitbit","note":"Comfortable band with clear sleep tracking."},
      {"id":"amazfit-active","name":"Amazfit Active / GTS","tier":"mid","features":["steps","hr","spo2","sleep"],"app":"Zepp","note":"Watch-style screen, easier to read than a band."},
      {"id":"galaxy-watch","name":"Samsung Galaxy Watch","tier":"premium","features":["steps","hr","spo2","sleep","fall","sos"],"app":"Samsung Health","note":"Detects hard falls and can call for help. Best with a Samsung phone."},
      {"id":"pixel-watch","name":"Google Pixel Watch","tier":"premium","features":["steps","hr","spo2","sleep","fall","sos"],"app":"Fitbit","note":"Detects hard falls and can call for help. Works with most Android phones."}
    ]"""

    fun shop(): List<ShopItem> {
        fun parse(json: String) = runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val id = o.getString("id")
                val f = o.optJSONArray("features")
                ShopItem(
                    id = id, name = o.getString("name"), tier = o.optString("tier", "mid"),
                    features = if (f == null) emptyList() else (0 until f.length()).map { f.getString(it) },
                    app = o.optString("app"), note = o.optString("note"),
                    urlIn = o.optString("url_in").ifBlank { "$SITE_DEVICES#$id" },
                    urlIntl = o.optString("url_intl").ifBlank { "$SITE_DEVICES#$id" },
                )
            }
        }.getOrNull()
        return s("shop_json").takeIf { it.isNotBlank() }?.let(::parse) ?: parse(DEFAULT_SHOP).orEmpty()
    }

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
