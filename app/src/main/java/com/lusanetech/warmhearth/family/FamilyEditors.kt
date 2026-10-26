package com.lusanetech.warmhearth.family

import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.data.FavoriteContact
import com.lusanetech.warmhearth.data.Medicine
import com.lusanetech.warmhearth.graph
import com.lusanetech.warmhearth.ui.BigButton
import com.lusanetech.warmhearth.ui.NavRow
import com.lusanetech.warmhearth.ui.Page
import com.lusanetech.warmhearth.ui.Palette
import com.lusanetech.warmhearth.ui.PersonAvatar
import com.lusanetech.warmhearth.ui.SectionCard
import com.lusanetech.warmhearth.ui.SoftButton
import com.lusanetech.warmhearth.ui.Tone
import com.lusanetech.warmhearth.util.TonePicker
import kotlinx.coroutines.launch
import java.util.Locale

/** Round photo: a newly picked one, the family-uploaded one, or initials. */
@Composable
fun RemotePhoto(name: String, url: String?, picked: Uri?, size: Dp) {
    val model: Any? = picked ?: url
    if (model != null) {
        AsyncImage(model, name, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(CircleShape).background(Palette.SurfaceLow))
    } else PersonAvatar(name.ifBlank { "?" }, null, size)
}

/**
 * Add or edit one of the parent's contacts from the family phone: name, number, photo,
 * SOS contact, shown on the home screen. Saved to their phone within seconds.
 */
@Composable
fun RemoteContactEditor(d: DeviceState, initial: FavoriteContact, isNew: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial.name) }
    var phone by remember { mutableStateOf(initial.phone) }
    var sos by remember { mutableStateOf(initial.isEmergency) }
    var onHome by remember { mutableStateOf(initial.onHome) }
    var picked by remember { mutableStateOf<Uri?>(null) }
    var photoUrl by remember { mutableStateOf(initial.photoUrl) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { if (it != null) picked = it }
    BackHandler(onBack = onDone)

    Page(stringResource(if (isNew) R.string.add_new_person else R.string.edit_person), onBack = onDone) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RemotePhoto(name, photoUrl, picked, 84.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                            Icon(Icons.Filled.AddAPhoto, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.choose_photo))
                        }
                        if (picked != null || photoUrl != null) TextButton(onClick = { picked = null; photoUrl = null }) {
                            Text(stringResource(R.string.remove_photo), color = Palette.Red)
                        }
                    }
                }
                Text(stringResource(R.string.family_photo_hint), style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
            }
            OutlinedTextField(
                name, { name = it.take(40) }, label = { Text(stringResource(R.string.name)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                phone, { phone = it.take(20) }, label = { Text(stringResource(R.string.phone_number)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
            )
            SectionCard(padding = 8.dp) {
                NavRow(stringResource(R.string.show_on_home), Icons.Filled.Home, Tone.Teal, subtitle = stringResource(R.string.show_on_home_sub),
                    trailing = { Switch(checked = onHome, onCheckedChange = { onHome = it }) }) { onHome = !onHome }
                NavRow(stringResource(R.string.emergency_contact_toggle), Icons.Filled.Sos, Tone.Red, subtitle = stringResource(R.string.sos_contact_sub),
                    trailing = { Switch(checked = sos, onCheckedChange = { sos = it }) }) { sos = !sos }
            }
            if (error) Text(stringResource(R.string.no_internet), color = Palette.Red)
            BigButton(stringResource(R.string.save), enabled = !busy && name.isNotBlank() && phone.any(Char::isDigit)) {
                busy = true; error = false
                scope.launch {
                    val url = picked?.let { graph.remote.uploadPhoto(d.id, "contacts", initial.id, it) ?: run { error = true; busy = false; return@launch } } ?: photoUrl
                    graph.family.remoteSaveContact(d.id, initial.copy(name = name.trim(), phone = phone.trim(), isEmergency = sos, onHome = onHome, photoUrl = url))
                    onDone()
                }
            }
            if (!isNew) TextButton(onClick = { graph.family.remoteDeleteContact(d.id, initial.id); onDone() }) {
                Icon(Icons.Filled.Delete, null, tint = Palette.Red); Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.delete_person), color = Palette.Red)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * Add or edit a medicine from the family phone: name, dose, times, reminder sound (including
 * "say the medicine name"), photo of the strip, and pause / resume reminders.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RemoteMedicineEditor(d: DeviceState, initial: Medicine, isNew: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial.name) }
    var dose by remember { mutableStateOf(initial.dose) }
    var tone by remember { mutableStateOf(initial.tone) }
    var active by remember { mutableStateOf(initial.active) }
    var picked by remember { mutableStateOf<Uri?>(null) }
    var photoUrl by remember { mutableStateOf(initial.photoUrl) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val times = remember { mutableStateListOf<String>().apply { addAll(initial.timeList()) } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { if (it != null) picked = it }
    fun pickTime(start: String, onPicked: (String) -> Unit) {
        val (h, m) = start.split(":").map { it.toIntOrNull() ?: 0 }.let { (it.getOrNull(0) ?: 8) to (it.getOrNull(1) ?: 0) }
        TimePickerDialog(context, { _, hh, mm -> onPicked(String.format(Locale.US, "%02d:%02d", hh, mm)) }, h, m, true).show()
    }
    BackHandler(onBack = onDone)

    Page(stringResource(if (isNew) R.string.add_medicine else R.string.edit_medicine), onBack = onDone) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(name, { name = it.take(40) }, label = { Text(stringResource(R.string.medicine_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(dose, { dose = it.take(40) }, label = { Text(stringResource(R.string.dose_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())

            SectionCard {
                Text(stringResource(R.string.times_per_day), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    times.forEachIndexed { i, t ->
                        AssistChip(
                            onClick = { pickTime(t) { picked2 -> if (picked2 !in times) times[i] = picked2 } },
                            label = { Text(t, style = MaterialTheme.typography.titleMedium) },
                            trailingIcon = { Icon(Icons.Filled.Close, stringResource(R.string.delete), Modifier.size(18.dp).clickable { times.removeAt(i) }) },
                        )
                    }
                    AssistChip(
                        onClick = { pickTime("08:00") { if (it !in times) times += it } },
                        label = { Text(stringResource(R.string.add_time)) },
                        leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) },
                    )
                }
            }

            SectionCard {
                TonePicker(tone, d.status?.tones.orEmpty(), includeDefault = true) { tone = it }
                if (d.status?.tones.isNullOrEmpty()) {
                    Text(stringResource(R.string.tones_loading), style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
                }
                NavRow(stringResource(R.string.reminders_on), Icons.Filled.NotificationsActive, Tone.Orange, subtitle = stringResource(R.string.reminders_on_sub),
                    trailing = { Switch(checked = active, onCheckedChange = { active = it }) }) { active = !active }
            }

            SectionCard {
                Text(stringResource(R.string.photo_of_pill), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                val model: Any? = picked ?: photoUrl
                if (model != null) {
                    AsyncImage(model, null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(160.dp).clip(MaterialTheme.shapes.medium).background(Palette.SurfaceLow))
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftButton(stringResource(if (model == null) R.string.add_photo else R.string.change_photo), Tone.Orange, Modifier.weight(1f),
                        icon = Icons.Filled.AddAPhoto, minHeight = 52.dp) {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    if (model != null) SoftButton(stringResource(R.string.remove_photo), Tone.Grey, Modifier.weight(1f), minHeight = 52.dp) { picked = null; photoUrl = null }
                }
            }

            if (error) Text(stringResource(R.string.no_internet), color = Palette.Red)
            BigButton(stringResource(R.string.save), color = Palette.Orange, enabled = !busy && name.isNotBlank() && times.isNotEmpty()) {
                busy = true; error = false
                scope.launch {
                    val url = picked?.let { graph.remote.uploadPhoto(d.id, "medicines", initial.id, it) ?: run { error = true; busy = false; return@launch } } ?: photoUrl
                    graph.family.remoteSaveMedicine(
                        d.id,
                        initial.copy(name = name.trim(), dose = dose.trim(), times = times.sorted().joinToString(","), tone = tone, active = active, photoUrl = url),
                    )
                    onDone()
                }
            }
            if (!isNew) TextButton(onClick = { graph.family.remoteDeleteMedicine(d.id, initial.id); onDone() }) {
                Icon(Icons.Filled.Delete, null, tint = Palette.Red); Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.delete_medicine), color = Palette.Red)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

