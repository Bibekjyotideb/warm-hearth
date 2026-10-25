package com.lusanetech.warmhearth.settings

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SupportAgent
import com.lusanetech.warmhearth.BuildConfig
import com.lusanetech.warmhearth.util.Links
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.Screen
import com.lusanetech.warmhearth.data.Settings
import com.lusanetech.warmhearth.fall.FallDetectionService
import com.lusanetech.warmhearth.graph
import com.lusanetech.warmhearth.ui.BigButton
import com.lusanetech.warmhearth.ui.Page
import com.lusanetech.warmhearth.ui.Palette
import com.lusanetech.warmhearth.util.HomeRole
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(settings: Settings, onBack: () -> Unit, onOpen: (Screen) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var changingPin by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    val homeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    Page(stringResource(R.string.settings), onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!settings.premium) {
                BigButton(stringResource(R.string.unlock_everything), color = Palette.Orange, icon = Icons.Filled.Star) { onOpen(Screen.PAYWALL) }
            } else {
                Text(stringResource(R.string.premium_active), color = Palette.Green, style = MaterialTheme.typography.titleLarge)
            }

            BigButton(stringResource(R.string.edit_contacts), icon = Icons.Filled.People) { onOpen(Screen.EDIT_CONTACTS) }
            BigButton(stringResource(R.string.edit_medicines), icon = Icons.Filled.Medication, color = Palette.Orange) { onOpen(Screen.EDIT_MEDS) }
            BigButton(stringResource(R.string.home_tiles), icon = Icons.Filled.Dashboard, color = Palette.Teal) { onOpen(Screen.TILES) }
            BigButton(
                stringResource(if (settings.paired) R.string.family_linked else R.string.link_family),
                icon = Icons.Filled.FamilyRestroom, color = Palette.Green,
            ) { onOpen(Screen.PAIRING) }

            // Fall detection
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.fall_detection), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.fall_detection_body), style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(
                        checked = settings.fallDetection && settings.premium,
                        onCheckedChange = { on ->
                            if (!settings.premium) { onOpen(Screen.PAYWALL); return@Switch }
                            scope.launch {
                                context.graph.prefs.setFallDetection(on)
                                if (on) FallDetectionService.start(context) else FallDetectionService.stop(context)
                            }
                        },
                    )
                }
            }

            // Text size
            Card {
                Text(stringResource(R.string.text_size), style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1.0f to R.string.size_large, 1.25f to R.string.size_xl, 1.5f to R.string.size_huge).forEach { (v, label) ->
                        FilterChip(
                            selected = settings.fontScale == v,
                            onClick = { scope.launch { context.graph.prefs.setFontScale(v) } },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
            }

            // Permissions that make alarms and alerts reliable
            val am = context.getSystemService(AlarmManager::class.java)
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                BigButton(stringResource(R.string.allow_exact_alarms), icon = Icons.Filled.Alarm, color = Palette.Purple) {
                    context.startActivity(
                        Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
            val nm = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) {
                BigButton(stringResource(R.string.allow_full_screen), icon = Icons.Filled.Alarm, color = Palette.Purple) {
                    context.startActivity(
                        Intent(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }

            BigButton(stringResource(R.string.help_support), icon = Icons.Filled.SupportAgent, color = Palette.Blue) { onOpen(Screen.SUPPORT) }
            BigButton(stringResource(R.string.change_pin), icon = Icons.Filled.Lock, color = Palette.Grey) { changingPin = true }
            if (!HomeRole.isDefault(context)) {
                BigButton(stringResource(R.string.set_home_button), icon = Icons.Filled.Home) { homeLauncher.launch(HomeRole.requestIntent(context)) }
            }
            BigButton(stringResource(R.string.leave_launcher), icon = Icons.Filled.ExitToApp, color = Palette.Grey) {
                HomeRole.openHomeSettings(context)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { Links.open(context, "/privacy") }) { Text(stringResource(R.string.privacy_policy)) }
                TextButton(onClick = { Links.open(context, "/terms") }) { Text(stringResource(R.string.terms)) }
            }
            TextButton(onClick = { confirmWipe = true }) { Text(stringResource(R.string.delete_phone_data), color = Palette.Red) }
            Text("WarmHearth ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmWipe) {
        var busy by remember { mutableStateOf(false) }
        var failed by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busy) confirmWipe = false },
            title = { Text(stringResource(R.string.delete_phone_data)) },
            text = { Text(stringResource(if (failed) R.string.no_internet else R.string.delete_phone_body)) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    context.graph.scope.launch {
                        if (context.graph.family.deleteMyAccount()) {
                            FallDetectionService.stop(context)
                            context.graph.db.medicines().getActive().forEach { context.graph.scheduler.cancel(it) }
                            context.graph.db.clearAllTables()
                            context.graph.prefs.clearAll()
                        } else { failed = true; busy = false }
                    }
                }) { Text(stringResource(R.string.delete_forever), color = Palette.Red) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { confirmWipe = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (changingPin) {
        var pin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { changingPin = false },
            title = { Text(stringResource(R.string.change_pin)) },
            text = {
                OutlinedTextField(
                    pin, { v -> pin = v.filter(Char::isDigit).take(6) },
                    label = { Text(stringResource(R.string.pin_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(enabled = pin.length >= 4, onClick = {
                    scope.launch { context.graph.prefs.setPin(pin) }
                    changingPin = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { changingPin = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) { content() }
}
