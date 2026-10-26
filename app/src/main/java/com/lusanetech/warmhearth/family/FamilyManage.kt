package com.lusanetech.warmhearth.family

import android.net.Uri
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PersonalInjury
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.data.CallShieldMode
import com.lusanetech.warmhearth.data.FavoriteContact
import com.lusanetech.warmhearth.data.Medicine
import com.lusanetech.warmhearth.data.Tiles
import com.lusanetech.warmhearth.graph
import com.lusanetech.warmhearth.ui.BigButton
import com.lusanetech.warmhearth.ui.Chip
import com.lusanetech.warmhearth.ui.IconBadge
import com.lusanetech.warmhearth.ui.NavRow
import com.lusanetech.warmhearth.ui.Page
import com.lusanetech.warmhearth.ui.Palette
import com.lusanetech.warmhearth.ui.SectionCard
import com.lusanetech.warmhearth.ui.Tone
import kotlinx.coroutines.launch
import java.util.UUID

/** Banner shown on editing screens of a free phone. */
@Composable
private fun PremiumGate(d: DeviceState, onPaywall: () -> Unit) {
    if (d.premium) return
    SectionCard {
        Text(stringResource(R.string.remote_needs_premium), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        BigButton(stringResource(R.string.start_trial), color = Palette.Ember, icon = Icons.Filled.Star, onClick = onPaywall)
    }
    Spacer(Modifier.height(12.dp))
}

/** Save settings, showing a short "Saved" / "No internet" line. */
@Composable
private fun rememberSettingsSaver(d: DeviceState): Pair<String?, (DeviceSettings) -> Unit> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    return status to { s: DeviceSettings ->
        scope.launch {
            val ok = context.graph.remote.saveSettings(d.id, s)
            status = context.getString(if (ok) R.string.saved_will_update else R.string.no_internet)
        }
        Unit
    }
}

// ---------------------------------------------------------------- people & medicines

@Composable
fun RemotePeopleScreen(d: DeviceState, onBack: () -> Unit, onPaywall: () -> Unit) {
    val graph = LocalContext.current.graph
    val contacts by remember(d.id) { graph.family.observeContacts(d.id) }.collectAsState(initial = emptyList())
    var edit by remember { mutableStateOf<Pair<FavoriteContact, Boolean>?>(null) }
    edit?.let { (c, isNew) -> RemoteContactEditor(d, c, isNew, onDone = { edit = null }); return }

    Page(stringResource(R.string.tab_people), onBack = onBack) {
        PremiumGate(d, onPaywall)
        BigButton(stringResource(R.string.add_new_person), icon = Icons.Filled.PersonAdd, color = Palette.Blue, enabled = d.premium) {
            edit = FavoriteContact(id = UUID.randomUUID().toString(), name = "", phone = "", position = contacts.size, isEmergency = contacts.none { it.isEmergency }) to true
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.people_order_hint), style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(contacts.size, key = { contacts[it].id }) { i ->
                val c = contacts[i]
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Palette.Surface)
                        .clickable(enabled = d.premium) { edit = c to false }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RemotePhoto(c.name, c.photoUrl, null, 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.titleMedium)
                        Text(c.phone, style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                            if (c.onHome) Chip(stringResource(R.string.on_home), Tone.Teal)
                            if (c.isEmergency) Chip("SOS", Tone.Red)
                        }
                    }
                    if (d.premium) Column {
                        IconButton(enabled = i > 0, onClick = {
                            graph.remote.reorderContacts(d.id, contacts.toMutableList().apply { add(i - 1, removeAt(i)) })
                        }) { Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.move_up)) }
                        IconButton(enabled = i < contacts.lastIndex, onClick = {
                            graph.remote.reorderContacts(d.id, contacts.toMutableList().apply { add(i + 1, removeAt(i)) })
                        }) { Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.move_down)) }
                    }
                }
            }
        }
    }
}

@Composable
fun RemoteMedicinesScreen(d: DeviceState, onBack: () -> Unit, onPaywall: () -> Unit) {
    val family = LocalContext.current.graph.family
    val meds by remember(d.id) { family.observeMedicines(d.id) }.collectAsState(initial = emptyList())
    var edit by remember { mutableStateOf<Pair<Medicine, Boolean>?>(null) }
    edit?.let { (m, isNew) -> RemoteMedicineEditor(d, m, isNew, onDone = { edit = null }); return }

    Page(stringResource(R.string.tab_medicines), onBack = onBack) {
        PremiumGate(d, onPaywall)
        BigButton(stringResource(R.string.add_medicine), icon = Icons.Filled.Add, color = Palette.Orange, enabled = d.premium) {
            edit = Medicine(id = UUID.randomUUID().toString(), name = "", times = "08:00") to true
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(meds, key = { it.id }) { m ->
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Palette.Surface)
                        .clickable(enabled = d.premium) { edit = m to false }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (m.photoUrl != null) {
                        AsyncImage(m.photoUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.small))
                    } else IconBadge(Icons.Filled.Medication, Tone.Orange, size = 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.name + if (m.dose.isNotBlank()) " · ${m.dose}" else "", style = MaterialTheme.typography.titleMedium)
                        Text(m.timeList().joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
                        Text(
                            com.lusanetech.warmhearth.util.toneLabel(m.tone, d.status?.tones.orEmpty()),
                            style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft,
                        )
                    }
                    if (!m.active) Chip(stringResource(R.string.paused), Tone.Grey)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- home screen & text size

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeSetupScreen(d: DeviceState, onBack: () -> Unit, onPaywall: () -> Unit) {
    val (status, save) = rememberSettingsSaver(d)
    val s = d.settings
    val tiles = s.tiles ?: Tiles.DEFAULT
    val builtIn = listOf(
        Tiles.PHONE to R.string.tile_phone, Tiles.CONTACTS to R.string.tile_contacts, Tiles.WHATSAPP to R.string.tile_whatsapp,
        Tiles.MESSAGES to R.string.tile_messages, Tiles.CAMERA to R.string.tile_camera, Tiles.PHOTOS to R.string.tile_photos,
        Tiles.MEDS to R.string.tile_meds, Tiles.SCAM to R.string.tile_scam, Tiles.READ to R.string.tile_read, Tiles.APPS to R.string.tile_apps,
    )
    fun toggle(key: String, on: Boolean) { if (d.premium) save(s.copy(tiles = (if (on) tiles + key else tiles - key).distinct())) else onPaywall() }

    Page(stringResource(R.string.home_and_text), onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PremiumGate(d, onPaywall)
            status?.let { Text(it, color = Palette.Green, fontWeight = FontWeight.SemiBold) }
            SectionCard {
                Text(stringResource(R.string.text_size), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1.0f to R.string.size_large, 1.25f to R.string.size_xl, 1.5f to R.string.size_huge).forEach { (v, label) ->
                        FilterChip(
                            selected = (s.textScale ?: 1.25f) == v,
                            onClick = { if (d.premium) save(s.copy(textScale = v)) else onPaywall() },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
            }
            // The rest of the phone's settings, from here.
            var changingPin by remember { mutableStateOf(false) }
            SectionCard(padding = 8.dp) {
                Text(stringResource(R.string.phone_settings), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(8.dp))
                SwitchRow(stringResource(R.string.fall_detection), stringResource(R.string.fall_detection_remote_sub), Icons.Filled.PersonalInjury, Tone.Red,
                    s.fallDetection ?: d.status?.fallDetectionOn ?: false) { if (d.premium) save(s.copy(fallDetection = it)) else onPaywall() }
                SwitchRow(stringResource(R.string.voice_commands), stringResource(R.string.voice_commands_sub), Icons.Filled.Mic, Tone.Ember,
                    s.voiceCommands) { if (d.premium) save(s.copy(voiceCommands = it)) else onPaywall() }
                Column(Modifier.padding(horizontal = 4.dp)) {
                    com.lusanetech.warmhearth.util.TonePicker(s.medTone, d.status?.tones.orEmpty(), includeDefault = false, enabled = true) {
                        if (d.premium) save(s.copy(medTone = it)) else onPaywall()
                    }
                }
                NavRow(stringResource(R.string.change_pin), Icons.Filled.Lock, Tone.Grey, subtitle = stringResource(R.string.change_pin_remote_sub)) {
                    if (d.premium) changingPin = true else onPaywall()
                }
            }
            if (changingPin) {
                var pin by remember { mutableStateOf("") }
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { changingPin = false },
                    title = { Text(stringResource(R.string.change_pin)) },
                    text = {
                        OutlinedTextField(
                            pin, { v -> pin = v.filter(Char::isDigit).take(6) }, label = { Text(stringResource(R.string.pin_label)) }, singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(enabled = pin.length >= 4, onClick = { save(s.copy(pin = pin)); changingPin = false }) {
                            Text(stringResource(R.string.save))
                        }
                    },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = { changingPin = false }) { Text(stringResource(R.string.cancel)) } },
                )
            }

            SectionCard(padding = 8.dp) {
                Text(stringResource(R.string.home_tiles), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(8.dp))
                builtIn.forEach { (key, label) -> CheckRow(stringResource(label), key in tiles) { toggle(key, it) } }
            }
            val apps = d.status?.installedApps.orEmpty()
            if (apps.isNotEmpty()) {
                SectionCard(padding = 8.dp) {
                    Text(stringResource(R.string.add_app_tiles), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(8.dp))
                    apps.forEach { (pkg, label) -> CheckRow(label, "pkg:$pkg" in tiles) { toggle("pkg:$pkg", it) } }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(role = Role.Checkbox) { onChange(!checked) }.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ---------------------------------------------------------------- safety alerts

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SafetySettingsScreen(d: DeviceState, onBack: () -> Unit, onPaywall: () -> Unit) {
    val (status, save) = rememberSettingsSaver(d)
    val s = d.settings
    val name = d.name.ifBlank { stringResource(R.string.unnamed_phone) }
    fun set(next: DeviceSettings) { if (d.premium) save(next) else onPaywall() }

    Page(stringResource(R.string.safety_alerts), onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PremiumGate(d, onPaywall)
            status?.let { Text(it, color = Palette.Green, fontWeight = FontWeight.SemiBold) }

            SectionCard {
                SwitchRow(stringResource(R.string.daily_checkin), stringResource(R.string.checkin_family_sub, name), Icons.Filled.TaskAlt, Tone.Green, s.checkInEnabled) {
                    set(s.copy(checkInEnabled = it))
                }
                if (s.checkInEnabled) {
                    Text(stringResource(R.string.checkin_by), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("09:00", "10:00", "11:00", "12:00", "14:00").forEach { t ->
                            FilterChip(selected = s.checkInBy == t, onClick = { set(s.copy(checkInBy = t)) }, label = { Text(t) })
                        }
                    }
                    SwitchRow(stringResource(R.string.notify_on_ok), stringResource(R.string.notify_on_ok_sub), Icons.Filled.DoneAll, Tone.Green, s.notifyOnOk) {
                        set(s.copy(notifyOnOk = it))
                    }
                }
            }

            SectionCard {
                Text(stringResource(R.string.alert_me_when), style = MaterialTheme.typography.titleMedium)
                SwitchRow(stringResource(R.string.alert_silent), stringResource(R.string.alert_silent_sub), Icons.Filled.NotificationsOff, Tone.Orange, s.alertSilent) { set(s.copy(alertSilent = it)) }
                SwitchRow(stringResource(R.string.alert_battery), stringResource(R.string.alert_battery_sub), Icons.Filled.BatteryAlert, Tone.Orange, s.alertBattery) { set(s.copy(alertBattery = it)) }
                SwitchRow(stringResource(R.string.alert_offline), stringResource(R.string.alert_offline_sub), Icons.Filled.CloudOff, Tone.Orange, s.alertOffline) { set(s.copy(alertOffline = it)) }
                SwitchRow(stringResource(R.string.alert_inactive, s.inactiveHours), stringResource(R.string.alert_inactive_sub), Icons.Filled.TouchApp, Tone.Orange, s.alertInactive) { set(s.copy(alertInactive = it)) }
                if (s.alertInactive) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 56.dp)) {
                        listOf(6, 8, 12, 24).forEach { h ->
                            FilterChip(selected = s.inactiveHours == h, onClick = { set(s.copy(inactiveHours = h)) }, label = { Text(stringResource(R.string.n_hours, h)) })
                        }
                    }
                }
                SwitchRow(stringResource(R.string.alert_checkin), stringResource(R.string.alert_checkin_sub), Icons.Filled.TaskAlt, Tone.Orange, s.alertCheckIn) { set(s.copy(alertCheckIn = it)) }
            }

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.Bedtime, Tone.Purple)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.sleep_hours), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.sleep_hours_sub), style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
                    }
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("21:00" to "06:00", "22:00" to "07:00", "23:00" to "08:00").forEach { (a, b) ->
                        FilterChip(selected = s.sleepStart == a && s.sleepEnd == b, onClick = { set(s.copy(sleepStart = a, sleepEnd = b)) }, label = { Text("$a – $b") })
                    }
                }
            }
            Text(stringResource(R.string.safety_privacy_note), style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, icon: ImageVector, tone: Tone, checked: Boolean, onChange: (Boolean) -> Unit) {
    NavRow(title, icon, tone, subtitle = subtitle, trailing = { Switch(checked = checked, onCheckedChange = onChange) }) { onChange(!checked) }
}

// ---------------------------------------------------------------- call shield

@Composable
fun CallShieldSettingsScreen(d: DeviceState, onBack: () -> Unit, onPaywall: () -> Unit) {
    val (status, save) = rememberSettingsSaver(d)
    val s = d.settings
    val name = d.name.ifBlank { stringResource(R.string.unnamed_phone) }
    Page(stringResource(R.string.call_shield), onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.call_shield_body), style = MaterialTheme.typography.bodyLarge)
            PremiumGate(d, onPaywall)
            status?.let { Text(it, color = Palette.Green, fontWeight = FontWeight.SemiBold) }
            SectionCard(padding = 8.dp) {
                listOf(
                    CallShieldMode.WARN to (R.string.shield_mode_warn to R.string.shield_mode_warn_sub),
                    CallShieldMode.SILENCE to (R.string.shield_mode_silence to R.string.shield_mode_silence_sub),
                    CallShieldMode.BLOCK to (R.string.shield_mode_block to R.string.shield_mode_block_sub),
                    CallShieldMode.OFF to (R.string.shield_mode_off to R.string.shield_mode_off_sub),
                ).forEach { (mode, labels) ->
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                            .clickable(role = Role.RadioButton) { if (d.premium) save(s.copy(callShield = mode)) else onPaywall() }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = s.callShield == mode, onClick = null)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(stringResource(labels.first), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(labels.second), style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
                        }
                    }
                }
            }
            val n = d.status?.callsScreenedToday ?: 0
            if (n > 0) Chip(LocalContext.current.resources.getQuantityString(R.plurals.n_calls_screened, n, n), Tone.Brand)
            Text(stringResource(R.string.shield_setup_note, name), style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
        }
    }
}

// ---------------------------------------------------------------- photos & notes

@Composable
fun NotesScreen(d: DeviceState, onBack: () -> Unit) {
    val context = LocalContext.current
    val remote = context.graph.remote
    val scope = rememberCoroutineScope()
    val notes by remember(d.id) { remote.observeNotes(d.id) }.collectAsState(initial = emptyList())
    var text by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) photo = uri }
    val name = d.name.ifBlank { stringResource(R.string.unnamed_phone) }

    Page(stringResource(R.string.photos_notes), onBack = onBack) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SectionCard {
                    Text(stringResource(R.string.note_compose_title, name), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    if (photo != null) {
                        AsyncImage(
                            photo, null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(MaterialTheme.shapes.medium).clickable { photo = null },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        text, { text = it.take(500) },
                        placeholder = { Text(stringResource(R.string.note_placeholder)) },
                        modifier = Modifier.fillMaxWidth(), minLines = 2,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        com.lusanetech.warmhearth.ui.SoftButton(
                            stringResource(if (photo == null) R.string.add_photo else R.string.change_photo), Tone.Pink,
                            Modifier.weight(1f), icon = Icons.Filled.AddAPhoto, minHeight = 56.dp,
                        ) { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                        com.lusanetech.warmhearth.ui.SoftButton(
                            stringResource(R.string.send), Tone.Brand, Modifier.weight(1f), icon = Icons.Filled.Send, minHeight = 56.dp,
                            enabled = !busy && (text.isNotBlank() || photo != null),
                        ) {
                            busy = true; result = null
                            scope.launch {
                                val ok = remote.sendNote(d.id, text, photo)
                                result = context.getString(if (ok) R.string.note_sent else R.string.no_internet, name)
                                if (ok) { text = ""; photo = null }
                                busy = false
                            }
                        }
                    }
                    result?.let { Text(it, color = Palette.Green, modifier = Modifier.padding(top = 8.dp)) }
                    Text(stringResource(R.string.note_privacy), style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft, modifier = Modifier.padding(top = 8.dp))
                }
            }
            items(notes, key = { it.id }) { n ->
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Palette.Surface).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (n.photoUrl != null) {
                        AsyncImage(n.photoUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp).clip(MaterialTheme.shapes.small))
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        if (n.text.isNotBlank()) Text(n.text, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                        Text(
                            ago(n.createdAt) + " · " + stringResource(if (n.seenAt != null) R.string.seen else R.string.not_seen_yet),
                            style = MaterialTheme.typography.bodySmall, color = if (n.seenAt != null) Palette.Green else Palette.InkSoft,
                        )
                    }
                    IconButton(onClick = { remote.deleteNote(d.id, n.id) }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = Palette.InkSoft) }
                }
            }
        }
    }
}
