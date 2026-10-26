package com.lusanetech.warmhearth.ai

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/** Reads answers aloud in the phone's language (Hindi voices ship with most Android phones in India). */
/** [alarm]: speak on the alarm stream, so a reminder is heard even when media volume is low. */
class Speaker(context: Context, private val alarm: Boolean = false) {
    private var ready = false
    private var pending: String? = null
    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts.language = Locale.getDefault()
                tts.setSpeechRate(0.85f) // a little slower than default
                if (alarm) tts.setAudioAttributes(
                    android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build(),
                )
                pending?.let { speak(it) }
                pending = null
            }
        }
    }

    fun speak(text: String) {
        if (!ready) { pending = text; return }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "warmhearth")
    }

    fun stop() { if (ready) tts.stop() }
    fun shutdown() = tts.shutdown()
}

@Composable
fun rememberSpeaker(): Speaker {
    val context = LocalContext.current
    val speaker = remember { Speaker(context) }
    DisposableEffect(Unit) { onDispose { speaker.shutdown() } }
    return speaker
}
