package com.lusanetech.warmhearth.util

import android.content.Context
import android.os.Build
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.lusanetech.warmhearth.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Feeds the admin panel and Firebase Analytics.
 *
 *  - Once a day per phone: lastActiveAt, activeDays (+1), app version, phone model, language.
 *  - Funnel milestones, set once: setHome, addedContact, linkedFamily, addedMedicine, testedSos.
 *    The admin panel's funnel shows where setups stop.
 *  - Analytics events for everything else (paywall views, AI use, purchases).
 *
 * No personal content is ever sent here: no names, numbers, messages or locations.
 */
object Telemetry {
    enum class Step(val key: String) {
        SET_HOME("setHome"), ADDED_CONTACT("addedContact"), LINKED_FAMILY("linkedFamily"),
        ADDED_MEDICINE("addedMedicine"), TESTED_SOS("testedSos"),
    }

    private val fs by lazy { FirebaseFirestore.getInstance() }
    private fun day() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun deviceInfo() = mapOf(
        "appVersion" to BuildConfig.VERSION_NAME,
        "versionCode" to BuildConfig.VERSION_CODE,
        "model" to "${Build.MANUFACTURER} ${Build.MODEL}".take(60),
        "sdk" to Build.VERSION.SDK_INT,
        "locale" to Locale.getDefault().toLanguageTag(),
    )

    /** Call on every app start; writes at most once per day. [collection] is "devices" or "users". */
    fun heartbeat(context: Context, collection: String, id: String) {
        if (id.isBlank()) return
        val sp = context.getSharedPreferences("telemetry", Context.MODE_PRIVATE)
        val key = "last_$collection"
        if (sp.getString(key, "") == day()) return
        fs.collection(collection).document(id).set(
            deviceInfo() + mapOf("lastActiveAt" to FieldValue.serverTimestamp(), "activeDays" to FieldValue.increment(1)),
            SetOptions.merge(),
        ).addOnSuccessListener { sp.edit().putString(key, day()).apply() }
        FirebaseCrashlytics.getInstance().setUserId(id)
    }

    fun milestone(context: Context, deviceId: String, step: Step) {
        val sp = context.getSharedPreferences("telemetry", Context.MODE_PRIVATE)
        if (deviceId.isBlank() || sp.getBoolean("step_${step.key}", false)) return
        fs.collection("devices").document(deviceId)
            .set(mapOf("funnel" to mapOf(step.key to true)), SetOptions.merge())
            .addOnSuccessListener { sp.edit().putBoolean("step_${step.key}", true).apply() }
        event(context, "funnel_${step.key}")
    }

    fun event(context: Context, name: String, vararg params: Pair<String, Any>) {
        val b = Bundle().apply {
            params.forEach { (k, v) ->
                when (v) { is Long -> putLong(k, v); is Int -> putLong(k, v.toLong()); is Boolean -> putString(k, v.toString()); else -> putString(k, v.toString().take(100)) }
            }
        }
        runCatching { FirebaseAnalytics.getInstance(context).logEvent(name, b) }
    }

    fun recordError(t: Throwable) = runCatching { FirebaseCrashlytics.getInstance().recordException(t) }
}
