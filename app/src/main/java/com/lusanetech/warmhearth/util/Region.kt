package com.lusanetech.warmhearth.util

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * Where the phone is, for things that differ by country: emergency number, glucose units,
 * price ranges, the AI's idea of local scams. SIM / network country first (right even when the
 * phone's language is English), then the language setting's country.
 */
object Region {
    fun country(context: Context): String {
        val tm = context.getSystemService(TelephonyManager::class.java)
        val fromSim = runCatching { tm?.simCountryIso?.takeIf { it.length == 2 } ?: tm?.networkCountryIso?.takeIf { it.length == 2 } }.getOrNull()
        return (fromSim ?: Locale.getDefault().country).uppercase(Locale.US).ifBlank { "US" }
    }

    /**
     * The number for an ambulance / emergency services. 112 also works from almost any mobile in
     * most countries, so it is the fallback.
     */
    fun emergencyNumber(country: String): String = when (country) {
        "US", "CA", "MX", "PR", "PH", "AR", "UY", "PY", "VE", "PA", "CR", "SV", "GT", "HN", "NI", "DO", "EC", "BO", "SA" -> "911"
        "GB", "IE", "HK", "MY", "BD", "AE", "QA", "BH", "KE", "MU" -> "999"
        "AU" -> "000"
        "NZ" -> "111"
        "JP", "KR", "TW", "LK" -> "119"
        "CN" -> "120"
        "SG" -> "995"
        "BR" -> "192"
        "PK" -> "1122"
        "TH" -> "1669"
        "VN" -> "115"
        "NP" -> "102"
        "EG" -> "123"
        "IL" -> "101"
        "CO" -> "123"
        "CL" -> "131"
        "PE" -> "106"
        else -> "112" // India, EU, Russia, Turkey, Indonesia, much of Africa, and the GSM standard
    }

    /** Countries where blood sugar is usually written in mmol/L rather than mg/dL. */
    private val MMOL = setOf(
        "GB", "IE", "CA", "AU", "NZ", "ZA", "CN", "HK", "RU", "KZ", "UA", "NL", "SE", "NO", "DK", "FI", "IS",
        "CH", "CZ", "SK", "SI", "HR", "EE", "LV", "LT", "MY", "SG", "JM",
    )

    fun usesMmol(country: String) = country in MMOL

    /** "140 mg/dL" or "7.8 mmol/L" for this country. */
    fun glucose(mgDl: Int, country: String): String =
        if (usesMmol(country)) String.format(Locale.getDefault(), "%.1f mmol/L", mgDl / 18.0) else "$mgDl mg/dL"

    enum class Money { INR, GBP, EUR, USD }

    private val EURO = setOf("DE", "FR", "IT", "ES", "NL", "BE", "AT", "PT", "IE", "FI", "GR", "SK", "SI", "LU", "LV", "LT", "EE", "MT", "CY", "HR")

    fun money(country: String): Money = when {
        country == "IN" -> Money.INR
        country == "GB" -> Money.GBP
        country in EURO -> Money.EUR
        else -> Money.USD
    }
}
