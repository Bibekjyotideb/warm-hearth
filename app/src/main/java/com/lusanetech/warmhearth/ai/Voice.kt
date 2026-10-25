package com.lusanetech.warmhearth.ai

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import com.lusanetech.warmhearth.data.FavoriteContact
import com.lusanetech.warmhearth.launcher.InstalledApp
import java.text.Normalizer
import java.util.Locale

/**
 * Voice commands without any AI cost: the phone's own speech recogniser turns speech into text,
 * then simple matching decides what to do. Works offline on most phones, in English and Hindi.
 *
 *  "Call Asha" / "Asha ko call karo" / "आशा को फ़ोन करो"   -> confirm call
 *  "Open WhatsApp" / "WhatsApp kholo" / "व्हाट्सएप खोलो"     -> open app
 *  "Help" / "SOS" / "मदद" / "bachao"                        -> SOS screen
 *  "Medicine" / "dawai" / "दवा"                             -> today's medicines
 *  "Is this a scam" / "fraud"                               -> scam checker
 */
sealed interface VoiceAction {
    data class Call(val contact: FavoriteContact) : VoiceAction
    data class OpenApp(val app: InstalledApp) : VoiceAction
    data object Sos : VoiceAction
    data object Medicines : VoiceAction
    data object ScamCheck : VoiceAction
    data class NotUnderstood(val heard: String) : VoiceAction
}

object VoiceCommands {
    private val sosWords = listOf("sos", "help", "emergency", "madad", "bachao", "मदद", "बचाओ", "इमरजेंसी")
    private val medWords = listOf("medicine", "tablet", "pill", "dawai", "dawa", "दवा", "दवाई", "गोली")
    private val scamWords = listOf("scam", "fraud", "fake", "धोखा", "फ्रॉड")
    private val callWords = listOf("call", "phone", "ring", "dial", "फ़ोन", "फोन", "कॉल", "बात")
    private val openWords = listOf("open", "start", "kholo", "khol", "खोलो", "खोल", "चलाओ")

    fun parse(heard: String, contacts: List<FavoriteContact>, apps: List<InstalledApp>): VoiceAction {
        val t = norm(heard)
        if (sosWords.any { it in t }) return VoiceAction.Sos

        // A contact's name anywhere in the sentence wins when a call word is present (or nothing else matched).
        val contact = contacts.firstOrNull { c -> c.name.split(" ").any { part -> part.length >= 2 && norm(part) in t } }
        if (contact != null && (callWords.any { it in t } || openWords.none { it in t })) return VoiceAction.Call(contact)

        val app = apps.firstOrNull { norm(it.label) in t }
            ?: apps.firstOrNull { a -> norm(a.label).split(" ").any { it.length >= 4 && it in t } }
        if (app != null && (openWords.any { it in t } || contact == null)) return VoiceAction.OpenApp(app)

        if (medWords.any { it in t }) return VoiceAction.Medicines
        if (scamWords.any { it in t }) return VoiceAction.ScamCheck
        return VoiceAction.NotUnderstood(heard)
    }

    private fun norm(s: String) = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFC)
}

/** Launches the system "Speak now" dialog and returns what was heard. */
@Composable
fun rememberVoiceInput(onHeard: (String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onHeard)
    }
    return {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "")
        runCatching { launcher.launch(intent) }
    }
}
