package com.lusanetech.warmhearth.safety

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.calls.CallHelper
import com.lusanetech.warmhearth.data.FavoriteContact
import com.lusanetech.warmhearth.graph
import com.lusanetech.warmhearth.ui.BigButton
import com.lusanetech.warmhearth.ui.Palette
import com.lusanetech.warmhearth.ui.WarmHearthTheme
import com.lusanetech.warmhearth.util.AlarmSound
import com.lusanetech.warmhearth.util.Notifications
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** "Ravi is trying to reach you": rings on the alarm channel (heard even on silent) for 60 seconds. */
class FindPhoneActivity : ComponentActivity() {
    companion object { const val EXTRA_BY = "by" }
    private lateinit var sound: AlarmSound

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        NotificationManagerCompat.from(this).cancel(Notifications.ID_FIND_PHONE)
        val by = intent.getStringExtra(EXTRA_BY).orEmpty()
        sound = AlarmSound(this).also { it.start() }

        setContent {
            WarmHearthTheme(scale = 1.3f) {
                var caller by remember { mutableStateOf<FavoriteContact?>(null) }
                LaunchedEffect(by) {
                    caller = graph.db.contacts().getAll().firstOrNull { by.isNotBlank() && it.name.equals(by.substringBefore(' '), ignoreCase = true) }
                        ?: graph.db.contacts().getAll().firstOrNull { by.isNotBlank() && it.name.contains(by.substringBefore(' '), ignoreCase = true) }
                }
                LaunchedEffect(Unit) { delay(60_000); sound.stop() }
                val who = by.ifBlank { stringResource(R.string.your_family) }

                Column(
                    Modifier.fillMaxSize().background(Palette.Background).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(132.dp).clip(CircleShape).background(Palette.BrandSoft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.NotificationsActive, null, tint = Palette.Brand, modifier = Modifier.size(72.dp))
                    }
                    Text(stringResource(R.string.find_phone_title, who), style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.find_phone_body), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, color = Palette.InkSoft)
                    caller?.let { c ->
                        BigButton(stringResource(R.string.call_name, c.name), color = Palette.Green, icon = Icons.Filled.Call) {
                            sound.stop(); CallHelper.call(this@FindPhoneActivity, c.phone); finish()
                        }
                    }
                    BigButton(stringResource(R.string.im_here), color = Palette.Brand, icon = Icons.Filled.Check) {
                        sound.stop()
                        graph.scope.launch {
                            val name = graph.prefs.current().seniorName.ifBlank { getString(R.string.your_family_member) }
                            graph.family.sendEvent("here", getString(R.string.family_here, name))
                        }
                        finish()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (::sound.isInitialized) sound.stop()
        super.onDestroy()
    }
}
