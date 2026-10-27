package com.lusanetech.warmhearth.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.lusanetech.warmhearth.graph
import com.lusanetech.warmhearth.util.RemoteFlags
import com.lusanetech.warmhearth.util.Telemetry
import org.json.JSONObject
import java.util.Locale

/**
 * All AI calls go through here.
 *
 * Runs on Gemini via Firebase AI Logic, so no API key ships inside the app.
 * Cloud (not on-device) on purpose: Google's free on-device Gemini Nano only runs on recent
 * flagships, and most parents use mid-range phones.
 *
 * Cost control: every call counts against a daily per-phone cap (see [AiQuota]).
 * The model name, daily limits and an extra prompt line come from Remote Config (util/RemoteFlags),
 * so a retired model or a misbehaving prompt can be fixed without an app update.
 */
object AiHelper {
    private const val TAG = "Ai"

    private var cachedName: String? = null
    private var cachedModel: com.google.firebase.ai.GenerativeModel? = null
    private val model: com.google.firebase.ai.GenerativeModel
        get() {
            val name = RemoteFlags.aiModel
            cachedModel?.takeIf { cachedName == name }?.let { return it }
            return buildModel(name).also { cachedModel = it; cachedName = name }
        }

    private fun buildModel(name: String) =
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = name,
            generationConfig = generationConfig {
                temperature = 0.2f
                responseMimeType = "application/json"
            },
        )

    private fun note() = RemoteFlags.aiPromptNote.takeIf { it.isNotBlank() }?.let { "\n$it" } ?: ""

    /** Answers in the phone's language (Gemini handles most languages); plain words for an older reader. */
    private fun language(): String = Locale.getDefault().getDisplayLanguage(Locale.ENGLISH).ifBlank { "English" }.let { "simple $it" }

    /** Country name for local context (bank names, scam types, emergency numbers). */
    private fun place(context: Context): String {
        val code = com.lusanetech.warmhearth.util.Region.country(context)
        return Locale("", code).getDisplayCountry(Locale.ENGLISH).ifBlank { "the user's country" }
    }

    enum class Risk { SAFE, SUSPICIOUS, SCAM }
    data class ScamVerdict(val risk: Risk, val explanation: String, val advice: String)
    data class Explanation(val summary: String)
    data class MedicineLabel(val name: String, val dose: String, val instructions: String)

    sealed interface Result<out T> {
        data class Ok<T>(val value: T) : Result<T>
        data object LimitReached : Result<Nothing>
        data object Failed : Result<Nothing>
    }

    suspend fun checkScam(context: Context, message: String): Result<ScamVerdict> = call(context, AiQuota.Kind.SCAM) {
        val prompt = """
            You protect an elderly person in ${place(context)} from scams. Look at this message they received.
            Common scams everywhere: fake bank or payment alerts ("your account is blocked", "verify
            your details", KYC, UPI, card suspended), one-time code / OTP requests, lottery or prize wins,
            parcel or customs fees, a relative or grandchild "in trouble" needing money, tech support or
            refund calls, government / tax / police / utility threats, investment or crypto offers,
            romance, job offers, links asking to log in. Use what is typical in their country.
            Answer in ${language()}, short sentences a 75-year-old understands. No jargon.
            Return JSON: {"risk":"SAFE"|"SUSPICIOUS"|"SCAM","explanation":"why, max 2 sentences","advice":"what to do, max 2 sentences"}
            Never tell them to click links or share OTPs. If unsure, say SUSPICIOUS.

            MESSAGE:
            $message
        """.trimIndent() + note()
        val json = JSONObject(model.generateContent(prompt).text ?: "{}")
        ScamVerdict(
            risk = runCatching { Risk.valueOf(json.optString("risk").uppercase()) }.getOrDefault(Risk.SUSPICIOUS),
            explanation = json.optString("explanation"),
            advice = json.optString("advice"),
        )
    }

    suspend fun explainDocument(context: Context, photo: Bitmap): Result<Explanation> = call(context, AiQuota.Kind.READ) {
        val prompt = """
            An elderly person photographed a letter, bill, notice, form or screen and wants it explained.
            In ${language()}, explain in at most 5 very short sentences: what it is, the important
            numbers or dates, and whether they need to do anything. If it looks like a scam, say so first.
            If the photo is unreadable, say so kindly and ask them to take it again closer.
            Return JSON: {"summary":"..."}
        """.trimIndent() + note()
        val json = JSONObject(model.generateContent(content { image(photo); text(prompt) }).text ?: "{}")
        Explanation(json.optString("summary"))
    }

    suspend fun readMedicine(context: Context, photo: Bitmap): Result<MedicineLabel> = call(context, AiQuota.Kind.READ) {
        val prompt = """
            Read this medicine package or strip. Return JSON:
            {"name":"brand name and strength as printed, e.g. Panadol 500 mg","dose":"how much per time if printed, else empty","instructions":"one short line in ${language()} from the label, else empty"}
            Only use what is printed. Do not give medical advice. If unreadable, return empty strings.
        """.trimIndent() + note()
        val json = JSONObject(model.generateContent(content { image(photo); text(prompt) }).text ?: "{}")
        MedicineLabel(json.optString("name"), json.optString("dose"), json.optString("instructions"))
    }

    private suspend fun <T> call(context: Context, kind: AiQuota.Kind, block: suspend () -> T): Result<T> {
        if (!RemoteFlags.aiEnabled) return Result.Failed // remote kill switch
        val premium = context.graph.prefs.current().premium
        if (!AiQuota.tryUse(context, kind, premium)) return Result.LimitReached
        Telemetry.event(context, "ai_use", "kind" to kind.name, "premium" to premium)
        return try {
            Result.Ok(block())
        } catch (e: Exception) {
            Log.w(TAG, "AI call failed", e)
            Result.Failed
        }
    }

    /** Shrinks camera photos before upload: faster on slow networks, cheaper per call. */
    fun downscale(bitmap: Bitmap, maxSide: Int = 1280): Bitmap {
        val scale = maxSide.toFloat() / maxOf(bitmap.width, bitmap.height)
        return if (scale >= 1f) bitmap
        else Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
    }
}

/**
 * Daily caps per phone. Scam checks are partly free (they are the best advert for the unlock);
 * reading documents and medicine boxes is part of Premium.
 */
object AiQuota {
    enum class Kind { SCAM, READ }

    fun tryUse(context: Context, kind: Kind, premium: Boolean): Boolean {
        val sp = context.getSharedPreferences("ai_quota", Context.MODE_PRIVATE)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
        if (sp.getString("day", "") != today) sp.edit().clear().putString("day", today).apply()
        val key = if (premium) "all" else kind.name
        val limit = when {
            premium -> RemoteFlags.aiPremiumPerDay
            kind == Kind.SCAM -> RemoteFlags.aiFreeScamPerDay
            else -> 0
        }
        val used = sp.getInt(key, 0)
        if (used >= limit) return false
        sp.edit().putInt(key, used + 1).apply()
        return true
    }
}
