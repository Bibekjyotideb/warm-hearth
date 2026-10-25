package com.lusanetech.warmhearth.family

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.billing.PaywallScreen
import com.lusanetech.warmhearth.calls.ContactEditorDialog
import com.lusanetech.warmhearth.data.AppMode
import com.lusanetech.warmhearth.data.FavoriteContact
import com.lusanetech.warmhearth.data.Medicine
import com.lusanetech.warmhearth.graph
import com.lusanetech.warmhearth.meds.MedicineEditorDialog
import com.lusanetech.warmhearth.meds.MedicineList
import com.lusanetech.warmhearth.ui.BigButton
import com.lusanetech.warmhearth.ui.Page
import com.lusanetech.warmhearth.ui.Palette
import com.lusanetech.warmhearth.ui.PersonAvatar
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Family mode: the son/daughter's phone. Normal-sized UI is fine here, but we keep the same
 * components so the app feels like one product.
 */
@Composable
fun FamilyApp() {
    val context = LocalContext.current
    val graph = context.graph
    val devices by remember { graph.family.observeMyDevices() }.collectAsState(initial = null)
    val settings by graph.prefs.settings.collectAsState(initial = null)
    var openDevice by remember { mutableStateOf<RemoteDevice?>(null) }
    var linking by remember { mutableStateOf(false) }
    var paywallFor by remember { mutableStateOf<String?>(null) }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        graph.family.registerFamilyToken()
    }
    // A family member who already bought the unlock gets it on every parent phone they link.
    LaunchedEffect(devices, settings?.premium) {
        if (settings?.premium == true) devices?.filter { !it.premium }?.forEach { graph.family.setDevicePremium(it.id) }
    }

    paywallFor?.let { id ->
        BackHandler { paywallFor = null }
        PaywallScreen(onBack = { paywallFor = null }, forDeviceId = id)
        return
    }
    openDevice?.let { d ->
        val live = devices?.firstOrNull { it.id == d.id } ?: d
        BackHandler { openDevice = null }
        DeviceScreen(live, onBack = { openDevice = null }, onUnlock = { paywallFor = live.id })
        return
    }

    Page(stringResource(R.string.family_title), onBack = null) {
        val list = devices
        if (list.isNullOrEmpty()) {
            Text(stringResource(R.string.family_empty), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
        }
        list?.forEach { d ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { openDevice = d }
                    .padding(16.dp),
            ) {
                PersonAvatar(d.name.ifBlank { "?" }, null, 56.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.name.ifBlank { stringResource(R.string.unnamed_phone) }, style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(if (d.premium) R.string.premium_active else R.string.free_plan),
                        color = if (d.premium) Palette.Green else Palette.Grey,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        BigButton(stringResource(R.string.link_a_phone), icon = Icons.Filled.Link, color = Palette.Green) { linking = true }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = {
            graph.scope.launch { graph.prefs.setMode(AppMode.UNSET) }
        }) { Text(stringResource(R.string.switch_to_senior_mode)) }
    }

    if (linking) LinkDialog(onDismiss = { linking = false })
}

@Composable
private fun LinkDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.link_a_phone)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.enter_code_hint))
                OutlinedTextField(
                    code, { code = it.uppercase().filter(Char::isLetterOrDigit).take(8) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineMedium,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
                error?.let { Text(stringResource(it), color = Palette.Red) }
            }
        },
        confirmButton = {
            TextButton(enabled = code.length == 8 && !busy, onClick = {
                busy = true
                scope.launch {
                    when (context.graph.family.linkWithCode(code)) {
                        is FamilyRepository.LinkResult.Linked -> onDismiss()
                        FamilyRepository.LinkResult.BadCode -> error = R.string.bad_code
                        FamilyRepository.LinkResult.Error -> error = R.string.no_internet
                    }
                    busy = false
                }
            }) { Text(stringResource(R.string.link)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun DeviceScreen(device: RemoteDevice, onBack: () -> Unit, onUnlock: () -> Unit) {
    val context = LocalContext.current
    val family = context.graph.family
    val events by remember(device.id) { family.observeEvents(device.id) }.collectAsState(initial = emptyList())
    val contacts by remember(device.id) { family.observeContacts(device.id) }.collectAsState(initial = emptyList())
    val meds by remember(device.id) { family.observeMedicines(device.id) }.collectAsState(initial = emptyList())
    var tab by remember { mutableIntStateOf(0) }
    var editContact by remember { mutableStateOf<FavoriteContact?>(null) }
    var editMed by remember { mutableStateOf<Medicine?>(null) }

    Page(device.name.ifBlank { stringResource(R.string.unnamed_phone) }, onBack = onBack) {
        if (!device.premium) {
            BigButton(stringResource(R.string.unlock_for_phone), color = Palette.Orange, icon = Icons.Filled.Star, onClick = onUnlock)
            Text(stringResource(R.string.remote_needs_premium), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
        }
        TabRow(selectedTabIndex = tab) {
            listOf(R.string.tab_alerts, R.string.tab_people, R.string.tab_medicines).forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(label)) })
            }
        }
        Spacer(Modifier.height(12.dp))
        when (tab) {
            0 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (events.isEmpty()) item { Text(stringResource(R.string.no_alerts), style = MaterialTheme.typography.titleMedium) }
                items(events, key = { it.id }) { e ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(if (e.type == "sos") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface)
                            .clickable(enabled = e.lat != null) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=${e.lat},${e.lng}")))
                            }
                            .padding(12.dp),
                    ) {
                        Text(e.message, style = MaterialTheme.typography.titleMedium)
                        Text(
                            DateUtils.getRelativeTimeSpanString(e.createdAt).toString() + (if (e.lat != null) " · " + stringResource(R.string.tap_for_map) else ""),
                            style = MaterialTheme.typography.bodyMedium, color = Palette.Grey,
                        )
                    }
                }
            }

            1 -> Column {
                BigButton(stringResource(R.string.add_new_person), icon = Icons.Filled.PersonAdd, color = Palette.Teal, enabled = device.premium) {
                    editContact = FavoriteContact(id = UUID.randomUUID().toString(), name = "", phone = "", position = contacts.size, isEmergency = contacts.none { it.isEmergency })
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(contacts, key = { it.id }) { c ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surface)
                                .clickable(enabled = device.premium) { editContact = c }.padding(12.dp),
                        ) {
                            Icon(Icons.Filled.Phone, contentDescription = null, tint = if (c.isEmergency) Palette.Red else Palette.Brand)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.titleMedium)
                                Text(c.phone, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (device.premium) IconButton(onClick = { family.remoteDeleteContact(device.id, c.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete), tint = Palette.Red)
                            }
                        }
                    }
                }
            }

            else -> Column {
                BigButton(stringResource(R.string.add_medicine), icon = Icons.Filled.Add, color = Palette.Orange, enabled = device.premium) {
                    editMed = Medicine(id = UUID.randomUUID().toString(), name = "", times = "08:00")
                }
                Spacer(Modifier.height(8.dp))
                MedicineList(
                    meds,
                    onEdit = { if (device.premium) editMed = it },
                    onDelete = { if (device.premium) family.remoteDeleteMedicine(device.id, it.id) },
                )
            }
        }
    }

    // Photos are added on the parent's phone itself; the family side edits names, numbers and times.
    editContact?.let { c ->
        ContactEditorDialog(c, allowPhoto = false, onDismiss = { editContact = null }) {
            family.remoteSaveContact(device.id, it); editContact = null
        }
    }
    editMed?.let { m ->
        MedicineEditorDialog(m, allowPhoto = false, onDismiss = { editMed = null }) {
            family.remoteSaveMedicine(device.id, it); editMed = null
        }
    }
}
