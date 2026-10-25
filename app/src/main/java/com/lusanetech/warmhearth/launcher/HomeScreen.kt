package com.lusanetech.warmhearth.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.Screen
import com.lusanetech.warmhearth.calls.CallConfirmDialog
import com.lusanetech.warmhearth.ai.VoiceAction
import com.lusanetech.warmhearth.ai.VoiceCommands
import com.lusanetech.warmhearth.ai.rememberVoiceInput
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.lusanetech.warmhearth.data.FavoriteContact
import com.lusanetech.warmhearth.data.Settings
import com.lusanetech.warmhearth.data.Tiles
import com.lusanetech.warmhearth.graph
import com.lusanetech.warmhearth.ui.BigTile
import com.lusanetech.warmhearth.ui.Palette
import com.lusanetech.warmhearth.ui.PersonAvatar
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(settings: Settings, onOpen: (Screen) -> Unit) {
    val context = LocalContext.current
    val contacts by context.graph.db.contacts().observeAll().collectAsState(initial = emptyList())
    var calling by remember { androidx.compose.runtime.mutableStateOf<FavoriteContact?>(null) }
    var notUnderstood by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var apps by remember { androidx.compose.runtime.mutableStateOf<List<InstalledApp>>(emptyList()) }
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
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        ClockHeader(settings.seniorName, onSettings = { onOpen(Screen.PIN) })

        if (contacts.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                items(contacts.take(6), key = { it.id }) { c ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(96.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable(role = Role.Button) { calling = c },
                    ) {
                        PersonAvatar(c.name, c.photoPath, 84.dp)
                        Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
        ) {
            items(settings.tiles, key = { it }) { key ->
                val tile = tileFor(context, key) ?: return@items
                BigTile(
                    label = tile.label, icon = tile.icon, color = tile.color,
                    modifier = Modifier.aspectRatio(1.1f),
                ) {
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
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SpeakButton(Modifier.weight(0.42f), onClick = listen)
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

private data class TileSpec(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: Color)

@Composable
private fun tileFor(context: Context, key: String): TileSpec? = when (key) {
    Tiles.PHONE -> TileSpec(stringResource(R.string.tile_phone), Icons.Filled.Call, Palette.Green)
    Tiles.CONTACTS -> TileSpec(stringResource(R.string.tile_contacts), Icons.Filled.Contacts, Palette.Blue)
    Tiles.MESSAGES -> TileSpec(stringResource(R.string.tile_messages), Icons.AutoMirrored.Filled.Message, Palette.Purple)
    Tiles.CAMERA -> TileSpec(stringResource(R.string.tile_camera), Icons.Filled.PhotoCamera, Palette.Grey)
    Tiles.PHOTOS -> TileSpec(stringResource(R.string.tile_photos), Icons.Filled.PhotoLibrary, Palette.Pink)
    Tiles.WHATSAPP -> TileSpec("WhatsApp", Icons.AutoMirrored.Filled.Chat, Palette.WhatsApp)
    Tiles.MEDS -> TileSpec(stringResource(R.string.tile_meds), Icons.Filled.Medication, Palette.Orange)
    Tiles.APPS -> TileSpec(stringResource(R.string.tile_apps), Icons.Filled.Apps, Palette.Teal)
    Tiles.SCAM -> TileSpec(stringResource(R.string.tile_scam), Icons.Filled.Shield, Palette.Blue)
    Tiles.READ -> TileSpec(stringResource(R.string.tile_read), Icons.Filled.DocumentScanner, Palette.Purple)
    else -> if (key.startsWith("pkg:")) {
        Apps.label(context, key.removePrefix("pkg:"))?.let { TileSpec(it, Icons.Filled.Widgets, Palette.Teal) }
    } else null
}

@Composable
private fun ClockHeader(name: String, onSettings: () -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(10_000) }
    }
    var battery by remember { mutableIntStateOf(-1) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (level >= 0) battery = level * 100 / scale
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
            Text(DateFormat.getDateInstance(DateFormat.FULL).format(Date(now)), style = MaterialTheme.typography.titleLarge)
            if (name.isNotBlank()) Text(stringResource(R.string.hello_name, name), style = MaterialTheme.typography.titleMedium)
        }
        Column(horizontalAlignment = Alignment.End) {
            // Small and grey on purpose: settings are for the family, behind a PIN.
            IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings), tint = Palette.Grey)
            }
            if (battery >= 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.BatteryFull, contentDescription = null,
                        tint = if (battery <= 20) Palette.Red else Palette.Green,
                    )
                    Text("$battery%", style = MaterialTheme.typography.titleLarge, color = if (battery <= 20) Palette.Red else Palette.Ink)
                }
            }
        }
    }
}

@Composable
private fun SpeakButton(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(80.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Palette.Brand)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.speak), color = Color.White, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun SosBar(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Palette.Red)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Sos, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.sos_help), color = Color.White, style = MaterialTheme.typography.headlineMedium)
        }
    }
}
