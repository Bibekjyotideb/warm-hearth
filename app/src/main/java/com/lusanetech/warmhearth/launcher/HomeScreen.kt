package com.lusanetech.warmhearth.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.Screen
import com.lusanetech.warmhearth.ai.VoiceAction
import com.lusanetech.warmhearth.ai.VoiceCommands
import com.lusanetech.warmhearth.ai.rememberVoiceInput
import com.lusanetech.warmhearth.calls.CallConfirmDialog
import com.lusanetech.warmhearth.data.FavoriteContact
import com.lusanetech.warmhearth.data.Settings
import com.lusanetech.warmhearth.data.Tiles
import com.lusanetech.warmhearth.family.FamilyNote
import com.lusanetech.warmhearth.graph
import com.lusanetech.warmhearth.meds.MedicineScheduler
import com.lusanetech.warmhearth.ui.BigButton
import com.lusanetech.warmhearth.ui.BigTile
import com.lusanetech.warmhearth.ui.Palette
import com.lusanetech.warmhearth.ui.PersonAvatar
import com.lusanetech.warmhearth.ui.SoftButton
import com.lusanetech.warmhearth.ui.Tone
import com.lusanetech.warmhearth.util.RemoteFlags
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The senior's home screen, top to bottom:
 *   clock · (OTA banner) · today card ("I'm OK" / photo from family) · faces · tiles
 *   pinned: Speak + SOS, always in the same place.
 */
@Composable
fun HomeScreen(settings: Settings, onOpen: (Screen) -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val contacts by graph.db.contacts().observeAll().collectAsState(initial = emptyList())
    val notes by remember(settings.deviceId, settings.paired) { graph.family.observeNotesForSenior() }.collectAsState(initial = emptyList())
    var calling by remember { mutableStateOf<FavoriteContact?>(null) }
    var notUnderstood by remember { mutableStateOf<String?>(null) }
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    LaunchedEffect(Unit) { apps = Apps.load(context) }
    val listen = rememberVoiceInput { heard ->
        when (val action = VoiceCommands.parse(heard, contacts, apps)) {
            is VoiceAction.Call -> calling = action.contact
            is VoiceAction.OpenApp -> Apps.launchPackage(context, action.app.packageName)
            VoiceAction.Sos -> onOpen(Screen.SOS)
            VoiceAction.Medicines -> onOpen(Screen.MEDS)
            VoiceAction.ScamCheck -> onOpen(Screen.SCAM)
            is VoiceAction.NotUnderstood -> notUnderstood = action.heard
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            ClockHeader(onSettings = { onOpen(Screen.PIN) })

            // Remote Config banner: outage notices, tips, seasonal greetings. No app update needed.
            val notice = remember { RemoteFlags.announcement(Locale.getDefault().language == "hi") }
            if (notice.isNotBlank()) {
                Text(
                    notice,
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Brand,
                    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Palette.BrandSoft).padding(14.dp),
                )
            }

            val today = MedicineScheduler.today()
            val showCheckIn = settings.paired && settings.checkInEnabled && settings.lastCheckInDay != today && RemoteFlags.checkInEnabled
            if (showCheckIn) CheckInCard(settings.seniorName)

            // Newest photo / note from family in the last 3 days.
            val note = notes.firstOrNull { System.currentTimeMillis() - it.createdAt < 3 * 86_400_000L }
            if (note != null && RemoteFlags.notesEnabled) {
                val sender = contacts.firstOrNull { note.fromName.isNotBlank() && it.name.contains(note.fromName, ignoreCase = true) }
                NoteCard(note, sender, onCall = { calling = it })
            }

            if (contacts.any { it.onHome }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(contacts.filter { it.onHome }.take(8), key = { it.id }) { c ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(92.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.call_name, c.name)) { calling = c }
                                .padding(vertical = 4.dp),
                        ) {
                            PersonAvatar(c.name, c.photoPath, 80.dp)
                            Spacer(Modifier.height(6.dp))
                            Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            if (settings.paired) AskToCallButton()

            // Tiles: two per row. Plain rows (not a lazy grid) so the whole page scrolls as one.
            settings.tiles.mapNotNull { key -> tileFor(context, key)?.let { key to it } }.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (key, tile) ->
                        BigTile(tile.label, tile.icon, tile.tone, Modifier.weight(1f).aspectRatio(1.18f)) {
                            when (key) {
                                Tiles.PHONE -> onOpen(Screen.DIALER)
                                Tiles.CONTACTS -> onOpen(Screen.CONTACTS)
                                Tiles.MESSAGES -> Apps.openMessages(context)
                                Tiles.CAMERA -> Apps.openCamera(context)
                                Tiles.PHOTOS -> Apps.openGallery(context)
                                Tiles.WHATSAPP -> Apps.launchPackage(context, Apps.WHATSAPP)
                                Tiles.MEDS -> onOpen(Screen.MEDS)
                                Tiles.APPS -> onOpen(Screen.APPS)
                                Tiles.SCAM -> onOpen(Screen.SCAM)
                                Tiles.READ -> onOpen(Screen.READ)
                                else -> Apps.launchPackage(context, key.removePrefix("pkg:"))
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // Always visible, always in the same place.
        Row(
            Modifier.fillMaxWidth().background(Palette.Background).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (RemoteFlags.voiceEnabled && settings.voiceCommands) SpeakButton(Modifier.weight(0.42f), onClick = listen)
            SosBar(Modifier.weight(0.58f)) { onOpen(Screen.SOS) }
        }
    }

    notUnderstood?.let { heard ->
        AlertDialog(
            onDismissRequest = { notUnderstood = null },
            title = { Text(stringResource(R.string.voice_not_understood)) },
            text = { Text(stringResource(R.string.voice_examples, heard), style = MaterialTheme.typography.titleMedium) },
            confirmButton = { TextButton(onClick = { notUnderstood = null; listen() }) { Text(stringResource(R.string.try_again)) } },
            dismissButton = { TextButton(onClick = { notUnderstood = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    calling?.let { c -> CallConfirmDialog(contact = c, onDismiss = { calling = null }) }
}

private data class TileSpec(val label: String, val icon: ImageVector, val tone: Tone)

@Composable
private fun tileFor(context: Context, key: String): TileSpec? = when (key) {
    Tiles.PHONE -> TileSpec(stringResource(R.string.tile_phone), Icons.Filled.Call, Tone.Green)
    Tiles.CONTACTS -> TileSpec(stringResource(R.string.tile_contacts), Icons.Filled.Contacts, Tone.Blue)
    Tiles.MESSAGES -> TileSpec(stringResource(R.string.tile_messages), Icons.AutoMirrored.Filled.Message, Tone.Purple)
    Tiles.CAMERA -> TileSpec(stringResource(R.string.tile_camera), Icons.Filled.PhotoCamera, Tone.Grey)
    Tiles.PHOTOS -> TileSpec(stringResource(R.string.tile_photos), Icons.Filled.PhotoLibrary, Tone.Pink)
    Tiles.WHATSAPP -> TileSpec("WhatsApp", Icons.AutoMirrored.Filled.Chat, Tone.WhatsApp)
    Tiles.MEDS -> TileSpec(stringResource(R.string.tile_meds), Icons.Filled.Medication, Tone.Orange)
    Tiles.APPS -> TileSpec(stringResource(R.string.tile_apps), Icons.Filled.Apps, Tone.Teal)
    Tiles.SCAM -> TileSpec(stringResource(R.string.tile_scam), Icons.Filled.Shield, Tone.Brand)
    Tiles.READ -> TileSpec(stringResource(R.string.tile_read), Icons.Filled.DocumentScanner, Tone.Purple)
    else -> if (key.startsWith("pkg:")) {
        Apps.label(context, key.removePrefix("pkg:"))?.let { TileSpec(it, Icons.Filled.Widgets, Tone.Teal) }
    } else null
}

/** "Good morning, Kamala. Let your family know you're OK today." One big green button. */
@Composable
private fun CheckInCard(name: String) {
    val context = LocalContext.current
    var done by remember { mutableStateOf(false) }
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = stringResource(
        when { hour < 12 -> R.string.good_morning; hour < 17 -> R.string.good_afternoon; else -> R.string.good_evening },
    )
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(if (done) Palette.GreenSoft else Palette.BrandSoft).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(if (name.isNotBlank()) "$greeting, $name" else greeting, style = MaterialTheme.typography.headlineSmall, color = Palette.Ink)
        if (!done) {
            Text(stringResource(R.string.checkin_prompt), style = MaterialTheme.typography.titleMedium, color = Palette.InkSoft)
            BigButton(stringResource(R.string.im_ok), color = Palette.Green, icon = Icons.Filled.Check) {
                done = true
                context.graph.scope.launch { context.graph.family.checkIn() }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, tint = Palette.Green, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.checkin_done), style = MaterialTheme.typography.titleLarge, color = Palette.Green)
            }
        }
    }
}

/** Photo / note from family. Marked as seen once shown, so the sender sees "Seen". */
@Composable
private fun NoteCard(note: FamilyNote, sender: FavoriteContact?, onCall: (FavoriteContact) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(note.id) { if (note.seenAt == null) context.graph.family.markNoteSeen(note.id) }
    val from = note.fromName.ifBlank { stringResource(R.string.your_family) }
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Palette.Surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        note.photoUrl?.let { url ->
            AsyncImage(
                model = url, contentDescription = stringResource(R.string.photo_from, from), contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(MaterialTheme.shapes.medium).background(Palette.SurfaceLow),
            )
        }
        Text(
            stringResource(R.string.note_from, from) + " · " + DateUtils.getRelativeTimeSpanString(note.createdAt),
            style = MaterialTheme.typography.titleSmall, color = Palette.InkSoft,
        )
        if (note.text.isNotBlank()) Text(note.text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        if (sender != null) {
            SoftButton(stringResource(R.string.call_name, sender.name), Tone.Green, icon = Icons.Filled.Call, minHeight = 64.dp) { onCall(sender) }
        }
    }
}

@Composable
private fun AskToCallButton() {
    val context = LocalContext.current
    var sent by remember { mutableStateOf(false) }
    SoftButton(
        stringResource(if (sent) R.string.call_request_sent else R.string.ask_to_call),
        if (sent) Tone.Green else Tone.Blue,
        icon = if (sent) Icons.Filled.CheckCircle else Icons.Filled.PhoneCallback,
        minHeight = 64.dp,
        enabled = !sent,
    ) {
        sent = true
        context.graph.scope.launch { context.graph.family.askToBeCalled() }
    }
}

@Composable
private fun ClockHeader(onSettings: () -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(10_000) }
    }
    var battery by remember { mutableIntStateOf(-1) }
    var charging by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (level >= 0) battery = level * 100 / scale
                charging = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(now)), style = MaterialTheme.typography.displayLarge)
            Text(DateFormat.getDateInstance(DateFormat.FULL).format(Date(now)), style = MaterialTheme.typography.titleLarge, color = Palette.InkSoft)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (battery >= 0) {
                val low = battery <= 20 && !charging
                Row(
                    Modifier.clip(CircleShape).background(if (low) Palette.RedSoft else Palette.Surface).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.BatteryFull, contentDescription = null, tint = if (low) Palette.Red else Palette.Green, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("$battery%", style = MaterialTheme.typography.titleMedium, color = if (low) Palette.Red else Palette.Ink)
                }
            }
            // Small and grey on purpose: settings are for the family, behind a PIN.
            IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings), tint = Palette.InkSoft)
            }
        }
    }
}

@Composable
private fun SpeakButton(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = 80.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Palette.EmberSoft)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 18.dp)) {
            Icon(Icons.Filled.Mic, contentDescription = null, tint = Palette.Ember, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.speak), color = Palette.Ember, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SosBar(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Palette.Red)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 16.dp)) {
            Icon(Icons.Filled.Sos, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.sos_help), color = Color.White, style = MaterialTheme.typography.headlineSmall)
        }
    }
}
