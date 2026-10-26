package com.lusanetech.warmhearth.settings

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonalInjury
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lusanetech.warmhearth.BuildConfig
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.Screen
import com.lusanetech.warmhearth.calls.CallShield
import com.lusanetech.warmhearth.data.CallShieldMode
import com.lusanetech.warmhearth.data.Settings
import com.lusanetech.warmhearth.fall.FallDetectionService
import com.lusanetech.warmhearth.graph
import com.lusanetech.warmhearth.safety.Ringer
import com.lusanetech.warmhearth.ui.BigButton
import com.lusanetech.warmhearth.ui.Chip
import com.lusanetech.warmhearth.ui.NavRow
import com.lusanetech.warmhearth.ui.Page
import com.lusanetech.warmhearth.ui.Palette
import com.lusanetech.warmhearth.ui.SectionCard
import com.lusanetech.warmhearth.ui.Tone
import com.lusanetech.warmhearth.util.HomeRole
import com.lusanetech.warmhearth.util.Links
import com.lusanetech.warmhearth.util.RemoteFlags
import kotlinx.coroutines.launch

/** Behind the PIN: set up by the family, on the senior's phone. Most of this can also be done from the family app. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(settings: Settings, onBack: () -> Unit, onOpen: (Screen) -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val scope = rememberCoroutineScope()
    var changingPin by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    // Bumped when we come back from a system screen, so permission rows refresh.
    var refresh by remember { mutableIntStateOf(0) }
    val systemScreen = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }

    Page(stringResource(R.string.settings), onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (!settings.premium) {
                BigButton(stringResource(R.string.unlock_everything), color = Palette.Ember, icon = Icons.Filled.Star) { onOpen(Screen.PAYWALL) }
            } else {
                Chip(stringResource(R.string.premium_active), Tone.Green)
            }

            SectionCard(padding = 8.dp) {
                NavRow(stringResource(R.string.edit_contacts), Icons.Filled.People, Tone.Blue) { onOpen(Screen.EDIT_CONTACTS) }
                NavRow(stringResource(R.string.edit_medicines), Icons.Filled.Medication, Tone.Orange) { onOpen(Screen.EDIT_MEDS) }
                NavRow(stringResource(R.string.home_tiles), Icons.Filled.Dashboard, Tone.Teal) { onOpen(Screen.TILES) }
                NavRow(
                    stringResource(if (settings.paired) R.string.family_linked else R.string.link_family),
                    Icons.Filled.FamilyRestroom, Tone.Green,
                    subtitle = stringResource(R.string.link_family_sub),
                ) { onOpen(Screen.PAIRING) }
            }

            // Text size
            SectionCard {
                Text(stringResource(R.string.text_size), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1.0f to R.string.size_large, 1.25f to R.string.size_xl, 1.5f to R.string.size_huge).forEach { (v, label) ->
                        FilterChip(
                            selected = settings.fontScale == v,
                            onClick = { scope.launch { graph.prefs.setFontScale(v); graph.family.pushLocalSettings() } },
                            label = { Text(stringResource(label), style = MaterialTheme.typography.titleMedium) },
                        )
                    }
                }
            }

            // Safety
            SectionCard(padding = 8.dp) {
                ToggleRow(
                    stringResource(R.string.daily_checkin), stringResource(R.string.daily_checkin_sub), Icons.Filled.TaskAlt, Tone.Green,
                    checked = settings.checkInEnabled,
                ) { on -> scope.launch { graph.prefs.setCheckInEnabled(on) } }

                ToggleRow(
                    stringResource(R.string.fall_detection), stringResource(R.string.fall_detection_body), Icons.Filled.PersonalInjury, Tone.Red,
                    checked = settings.fallDetection && settings.premium,
                ) { on ->
                    if (!settings.premium) { onOpen(Screen.PAYWALL); return@ToggleRow }
                    scope.launch {
                        graph.prefs.setFallDetection(on)
                        if (on) FallDetectionService.start(context) else FallDetectionService.stop(context)
                    }
                }

                remember(refresh) { Ringer.canChangeDnd(context) }.let { granted ->
                    NavRow(
                        stringResource(R.string.allow_ringer_fix), Icons.Filled.NotificationsActive, Tone.Brand,
                        subtitle = stringResource(if (granted) R.string.allowed else R.string.allow_ringer_fix_sub),
                        trailing = {
                            if (granted) Chip(stringResource(R.string.on), Tone.Green)
                            else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Palette.InkSoft)
                        },
                    ) { if (!granted) systemScreen.launch(Ringer.accessIntent(newTask = false)) }
                }
            }

            // Scam call shield (Android 10+)
            if (CallShield.available() && RemoteFlags.callShieldEnabled) {
                SectionCard {
                    val enabled = remember(refresh) { CallShield.isEnabled(context) }
                    Row {
                        Text(stringResource(R.string.call_shield), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        if (enabled) Chip(stringResource(R.string.on), Tone.Green, icon = Icons.Filled.Shield)
                    }
                    Text(stringResource(R.string.call_shield_body), style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
                    Spacer(Modifier.height(10.dp))
                    if (!enabled) {
                        BigButton(stringResource(R.string.turn_on_call_shield), icon = Icons.Filled.Shield) {
                            CallShield.requestIntent(context)?.let { systemScreen.launch(it) }
                        }
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                CallShieldMode.WARN to R.string.shield_mode_warn,
                                CallShieldMode.SILENCE to R.string.shield_mode_silence,
                                CallShieldMode.BLOCK to R.string.shield_mode_block,
                                CallShieldMode.OFF to R.string.shield_mode_off,
                            ).forEach { (mode, label) ->
                                FilterChip(
                                    selected = settings.callShield == mode,
                                    onClick = { scope.launch { graph.prefs.setCallShield(mode); graph.family.pushLocalSettings() } },
                                    label = { Text(stringResource(label), style = MaterialTheme.typography.titleSmall) },
                                )
                            }
                        }
                    }
                }
            }

            // Permissions that make alarms and alerts reliable
            val am = context.getSystemService(AlarmManager::class.java)
            val nm = context.getSystemService(NotificationManager::class.java)
            val needExact = Build.VERSION.SDK_INT >= 31 && !remember(refresh) { am.canScheduleExactAlarms() }
            val needFull = Build.VERSION.SDK_INT >= 34 && !remember(refresh) { nm.canUseFullScreenIntent() }
            if (needExact || needFull) {
                SectionCard(padding = 8.dp) {
                    if (needExact) NavRow(stringResource(R.string.allow_exact_alarms), Icons.Filled.Alarm, Tone.Red) {
                        systemScreen.launch(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                    }
                    if (needFull) NavRow(stringResource(R.string.allow_full_screen), Icons.Filled.Alarm, Tone.Red) {
                        systemScreen.launch(Intent(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")))
                    }
                }
            }

            SectionCard(padding = 8.dp) {
                NavRow(stringResource(R.string.help_support), Icons.Filled.SupportAgent, Tone.Blue) { onOpen(Screen.SUPPORT) }
                NavRow(stringResource(R.string.change_pin), Icons.Filled.Lock, Tone.Grey) { changingPin = true }
                if (!HomeRole.isDefault(context)) {
                    NavRow(stringResource(R.string.set_home_button), Icons.Filled.Home, Tone.Brand) { systemScreen.launch(HomeRole.requestIntent(context)) }
                }
                NavRow(stringResource(R.string.leave_launcher), Icons.AutoMirrored.Filled.ExitToApp, Tone.Grey) { HomeRole.openHomeSettings(context) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { Links.open(context, "/privacy") }) { Text(stringResource(R.string.privacy_policy)) }
                TextButton(onClick = { Links.open(context, "/terms") }) { Text(stringResource(R.string.terms)) }
            }
            TextButton(onClick = { confirmWipe = true }) { Text(stringResource(R.string.delete_phone_data), color = Palette.Red) }
            Text("WarmHearth ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft, modifier = Modifier.padding(start = 12.dp))
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
                    graph.scope.launch {
                        if (graph.family.deleteMyAccount()) {
                            FallDetectionService.stop(context)
                            graph.db.medicines().getActive().forEach { graph.scheduler.cancel(it) }
                            graph.db.clearAllTables()
                            graph.prefs.clearAll()
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
                    scope.launch { graph.prefs.setPin(pin) }
                    changingPin = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { changingPin = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tone: Tone,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    NavRow(title, icon, tone, subtitle = subtitle, trailing = { Switch(checked = checked, onCheckedChange = onChange) }) { onChange(!checked) }
}
