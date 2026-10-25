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
 * Swap [MODEL] for the cheapest current "Flash-Lite" model when Google retires this one.
 */
object AiHelper {
    const val MODEL = "gemini-2.5-flash-lite"
    private const val TAG = "Ai"

    private val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL,
            generationConfig = generationConfig {
                temperature = 0.2f
                responseMimeType = "application/json"
            },
        )
    }

    /** Hindi phones get Hindi answers; everything else English. */
    private fun language(): String = if (Locale.getDefault().language == "hi") "Hindi" else "simple English"

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
            You protect an elderly person from scams. Look at this message they received.
            Common scams: fake bank/KYC/UPI alerts, "your account is blocked", lottery or prize,
            OTP requests, parcel/customs fees, fake relatives in trouble, job offers, links asking
            to log in, threats from "police" or "electricity board".
            Answer in ${language()}, short sentences a 75-year-old understands. No jargon.
            Return JSON: {"risk":"SAFE"|"SUSPICIOUS"|"SCAM","explanation":"why, max 2 sentences","advice":"what to do, max 2 sentences"}
            Never tell them to click links or share OTPs. If unsure, say SUSPICIOUS.

            MESSAGE:
            $message
        """.trimIndent()
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
        """.trimIndent()
        val json = JSONObject(model.generateContent(content { image(photo); text(prompt) }).text ?: "{}")
        Explanation(json.optString("summary"))
    }

    suspend fun readMedicine(context: Context, photo: Bitmap): Result<MedicineLabel> = call(context, AiQuota.Kind.READ) {
        val prompt = """
            Read this medicine package or strip. Return JSON:
            {"name":"brand name and strength, e.g. Dolo 650","dose":"how much per time if printed, else empty","instructions":"one short line in ${language()} from the label, else empty"}
            Only use what is printed. Do not give medical advice. If unreadable, return empty strings.
        """.trimIndent()
        val json = JSONObject(model.generateContent(content { image(photo); text(prompt) }).text ?: "{}")
        MedicineLabel(json.optString("name"), json.optString("dose"), json.optString("instructions"))
    }

    private suspend fun <T> call(context: Context, kind: AiQuota.Kind, block: suspend () -> T): Result<T> {
        val premium = context.graph.prefs.current().premium
        if (!AiQuota.tryUse(context, kind, premium)) return Result.LimitReached
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
 * reading documents and medicine boxes is part of the lifetime unlock.
 */
object AiQuota {
    enum class Kind { SCAM, READ }
    private const val FREE_SCAM_PER_DAY = 3
    private const val PREMIUM_PER_DAY = 30

    fun tryUse(context: Context, kind: Kind, premium: Boolean): Boolean {
        val sp = context.getSharedPreferences("ai_quota", Context.MODE_PRIVATE)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
        if (sp.getString("day", "") != today) sp.edit().clear().putString("day", today).apply()
        val key = if (premium) "all" else kind.name
        val limit = when {
            premium -> PREMIUM_PER_DAY
            kind == Kind.SCAM -> FREE_SCAM_PER_DAY
            else -> 0
        }
        val used = sp.getInt(key, 0)
        if (used >= limit) return false
        sp.edit().putInt(key, used + 1).apply()
        return true
    }
}
