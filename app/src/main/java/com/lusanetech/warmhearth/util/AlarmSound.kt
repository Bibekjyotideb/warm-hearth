package com.lusanetech.warmhearth.util

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Loud looping alarm + vibration for alert screens. Always call stop(). */
class AlarmSound(private val context: Context) {
    private var ringtone: Ringtone? = null
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)

    fun start() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(context, uri)?.apply {
            audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            if (Build.VERSION.SDK_INT >= 28) isLooping = true
            play()
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 500), 0))
    }

    fun stop() {
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
    }
}
