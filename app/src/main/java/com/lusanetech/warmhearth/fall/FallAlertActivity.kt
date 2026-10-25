package com.lusanetech.warmhearth.fall

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.sos.Sos
import com.lusanetech.warmhearth.ui.BigButton
import com.lusanetech.warmhearth.ui.WarmHearthTheme
import com.lusanetech.warmhearth.ui.Palette
import com.lusanetech.warmhearth.util.AlarmSound
import com.lusanetech.warmhearth.util.Notifications
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** "Did you fall?" with a 30-second countdown. No answer = SOS. */
class FallAlertActivity : ComponentActivity() {
    private lateinit var sound: AlarmSound

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        NotificationManagerCompat.from(this).cancel(Notifications.ID_FALL_ALERT)
        sound = AlarmSound(this).also { it.start() }

        setContent {
            WarmHearthTheme(scale = 1.3f) {
                var seconds by remember { mutableIntStateOf(30) }
                var sent by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                fun sendSos() {
                    if (sent) return
                    sent = true
                    sound.stop()
                    scope.launch {
                        Sos.trigger(this@FallAlertActivity, reason = "fall")
                        finish()
                    }
                }

                LaunchedEffect(Unit) {
                    while (seconds > 0) { delay(1000); seconds-- }
                    sendSos()
                }

                Column(
                    Modifier.fillMaxSize().background(Palette.Background).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.fall_question), style = MaterialTheme.typography.displayMedium, textAlign = TextAlign.Center)
                    Text(
                        if (sent) stringResource(R.string.sos_sending) else stringResource(R.string.fall_countdown, seconds),
                        style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, color = Palette.Red,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                    )
                    BigButton(stringResource(R.string.im_ok), color = Palette.Green, icon = Icons.Filled.Check, enabled = !sent) {
                        sound.stop(); finish()
                    }
                    BigButton(stringResource(R.string.get_help_now), color = Palette.Red, icon = Icons.Filled.Sos, enabled = !sent) { sendSos() }
                }
            }
        }
    }

    override fun onDestroy() {
        sound.stop()
        super.onDestroy()
    }
}
