package com.lusanetech.warmhearth.util

import android.content.Context
import android.media.RingtoneManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.ui.Palette

/**
 * Medicine reminder sounds. Values:
 *   ""      use the phone's default (Settings → medicine sound)
 *   "alarm" the phone's alarm sound
 *   "voice" speak "Time for your medicine: Telma 40" in the phone's language
 *   a ringtone URI from this phone (the family picks from the list the phone reports)
 */
object Tones {
    const val DEFAULT = ""
    const val ALARM = "alarm"
    const val VOICE = "voice"

    /** Alarm and notification sounds installed on this phone: (title, uri). */
    fun list(context: Context, max: Int = 30): List<Pair<String, String>> = runCatching {
        val out = LinkedHashMap<String, String>()
        for (type in listOf(RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_NOTIFICATION)) {
            val rm = RingtoneManager(context).apply { setType(type) }
            val c = rm.cursor
            while (c.moveToNext() && out.size < max) {
                val title = c.getString(RingtoneManager.TITLE_COLUMN_INDEX) ?: continue
                out.putIfAbsent(rm.getRingtoneUri(c.position).toString(), title)
            }
        }
        out.map { (uri, title) -> title to uri }
    }.getOrDefault(emptyList())

    /** What the reminder will actually play, after applying the phone default. */
    fun resolve(medicineTone: String, phoneDefault: String) = medicineTone.ifBlank { phoneDefault.ifBlank { ALARM } }
}

@Composable
fun toneLabel(value: String, tones: List<Pair<String, String>>): String = when (value) {
    Tones.DEFAULT -> stringResource(R.string.tone_default)
    Tones.ALARM -> stringResource(R.string.tone_alarm)
    Tones.VOICE -> stringResource(R.string.tone_voice)
    else -> tones.firstOrNull { it.second == value }?.first ?: stringResource(R.string.tone_custom)
}

/** "Reminder sound: Say the medicine name ▸" opening a list to choose from. */
@Composable
fun TonePicker(
    value: String,
    tones: List<Pair<String, String>>,
    includeDefault: Boolean,
    enabled: Boolean = true,
    onChange: (String) -> Unit,
) {
    var showList by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button) { showList = true }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.MusicNote, null, tint = Palette.Orange)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.reminder_sound), style = MaterialTheme.typography.titleSmall)
            Text(toneLabel(value, tones), style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
        }
        Text(stringResource(R.string.change), color = Palette.Brand, style = MaterialTheme.typography.labelLarge)
    }
    if (showList) {
        val options = buildList {
            if (includeDefault) add(Tones.DEFAULT)
            add(Tones.ALARM); add(Tones.VOICE)
            addAll(tones.map { it.second })
        }
        AlertDialog(
            onDismissRequest = { showList = false },
            title = { Text(stringResource(R.string.reminder_sound)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(options) { o ->
                        Row(
                            Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { onChange(o); showList = false }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = o == value, onClick = null)
                            Spacer(Modifier.width(10.dp))
                            Text(toneLabel(o, tones), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showList = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
