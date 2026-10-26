package com.lusanetech.warmhearth.meds

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import coil.compose.AsyncImage
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.data.DoseLog
import com.lusanetech.warmhearth.data.Medicine
import com.lusanetech.warmhearth.graph
import com.lusanetech.warmhearth.ui.BigButton
import com.lusanetech.warmhearth.ui.WarmHearthTheme
import com.lusanetech.warmhearth.ui.Palette
import com.lusanetech.warmhearth.util.AlarmSound
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** Full-screen "Time for your medicine" with the pill's photo. Rings until answered (max 2 minutes). */
class MedicineAlertActivity : ComponentActivity() {
    private lateinit var sound: AlarmSound

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val medId = intent.getStringExtra(MedicineScheduler.EXTRA_MED) ?: return finish()
        val time = intent.getStringExtra(MedicineScheduler.EXTRA_TIME) ?: return finish()
        val slot = intent.getStringExtra(MedicineScheduler.EXTRA_SLOT) ?: return finish()
        NotificationManagerCompat.from(this).cancel(MedicineAlarmReceiver.notificationId(medId, time))
        sound = AlarmSound(this).also { it.start() }

        setContent {
            WarmHearthTheme(scale = 1.3f) {
                var med by remember { mutableStateOf<Medicine?>(null) }
                val scope = rememberCoroutineScope()
                LaunchedEffect(Unit) { med = graph.db.medicines().get(medId) }
                LaunchedEffect(Unit) { delay(120_000); sound.stop() }

                Column(
                    Modifier.fillMaxSize().background(Palette.Background).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.med_time_title), style = MaterialTheme.typography.displayMedium, textAlign = TextAlign.Center)
                    med?.photoPath?.takeIf { File(it).exists() }?.let {
                        AsyncImage(
                            model = File(it), contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(220.dp).clip(MaterialTheme.shapes.extraLarge),
                        )
                    }
                    Text(med?.name ?: "", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                    med?.dose?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.headlineMedium) }
                    BigButton(stringResource(R.string.i_took_it), color = Palette.Green, icon = Icons.Filled.Check) {
                        sound.stop()
                        scope.launch {
                            graph.db.doseLogs().insert(DoseLog(medicineId = medId, slot = slot, takenAt = System.currentTimeMillis()))
                            graph.family.recordDose(medId, med?.name ?: "", slot, taken = true)
                            finish()
                        }
                    }
                    BigButton(stringResource(R.string.remind_10_min), color = Palette.Grey, icon = Icons.Filled.Snooze) {
                        sound.stop()
                        graph.scheduler.snooze(medId, time, slot)
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
