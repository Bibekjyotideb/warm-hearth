package com.lusanetech.warmhearth.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.data.Settings
import com.lusanetech.warmhearth.graph
import com.lusanetech.warmhearth.health.HealthVitals
import com.lusanetech.warmhearth.ui.BigButton
import com.lusanetech.warmhearth.ui.NavRow
import com.lusanetech.warmhearth.ui.Page
import com.lusanetech.warmhearth.ui.Palette
import com.lusanetech.warmhearth.ui.SectionCard
import com.lusanetech.warmhearth.ui.Tone
import com.lusanetech.warmhearth.util.Links
import kotlinx.coroutines.launch

/**
 * What linked family can see and do. Shown before the first pairing code (consent) and kept in
 * "Privacy & sharing". Google Play's User Data policy asks for prominent disclosure and consent;
 * this is the senior's own choice on their own phone, not monitoring of someone else.
 */
@Composable
fun SharingDisclosure() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.share_sees_title), style = MaterialTheme.typography.titleMedium)
        Line(Icons.Filled.BatteryFull, stringResource(R.string.share_sees_status))
        Line(Icons.Filled.CheckCircle, stringResource(R.string.share_sees_checkin))
        Line(Icons.Filled.Medication, stringResource(R.string.share_sees_meds))
        Line(Icons.Filled.LocationOn, stringResource(R.string.share_sees_location))
        Line(Icons.Filled.Favorite, stringResource(R.string.share_sees_health))
        Text(stringResource(R.string.share_does_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
        Line(Icons.Filled.Settings, stringResource(R.string.share_does_settings))
        Line(Icons.Filled.NotificationsActive, stringResource(R.string.share_does_ring))
        Text(stringResource(R.string.share_never), style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
    }
}

@Composable
private fun Line(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = Palette.Brand, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** First time on "Link a family member": explain, then ask for agreement. */
@Composable
fun SharingConsent(onAgree: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.share_consent_intro), style = MaterialTheme.typography.titleMedium)
        SectionCard { SharingDisclosure() }
        Text(stringResource(R.string.share_consent_stop), style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
        TextButton(onClick = { Links.open(context, "/privacy") }) { Text(stringResource(R.string.privacy_policy)) }
        BigButton(stringResource(R.string.share_agree), color = Palette.Green, icon = Icons.Filled.CheckCircle, onClick = onAgree)
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * Privacy & sharing: reachable from the PIN screen WITHOUT the PIN, so the senior can always
 * withdraw consent themselves (the family may know the PIN; the senior must not depend on it).
 */
@Composable
fun SharingScreen(settings: Settings, onBack: () -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val scope = rememberCoroutineScope()
    var confirmUnlink by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    Page(stringResource(R.string.privacy_sharing), onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                stringResource(if (settings.paired) R.string.share_linked_yes else R.string.share_linked_no),
                style = MaterialTheme.typography.titleMedium,
                color = if (settings.paired) Palette.Green else Palette.InkSoft,
            )
            SectionCard { SharingDisclosure() }
            if (settings.paired) {
                SectionCard(padding = 8.dp) {
                    NavRow(
                        stringResource(R.string.share_status_toggle), Icons.Filled.BatteryFull, Tone.Brand,
                        subtitle = stringResource(R.string.share_status_toggle_sub),
                        trailing = { Switch(checked = settings.statusShared, onCheckedChange = { on -> scope.launch { graph.family.setStatusSharing(on) } }) },
                    ) { scope.launch { graph.family.setStatusSharing(!settings.statusShared) } }
                    if (settings.vitalsShared) NavRow(
                        stringResource(R.string.health_stop), Icons.Filled.Favorite, Tone.Red, subtitle = stringResource(R.string.share_health_on),
                    ) { scope.launch { HealthVitals.stopSharing(context) } }
                }
                BigButton(stringResource(R.string.unlink_all), color = Palette.Red, icon = Icons.Filled.LinkOff, enabled = !busy) { confirmUnlink = true }
            }
            TextButton(onClick = { Links.open(context, "/privacy") }) { Text(stringResource(R.string.privacy_policy)) }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (confirmUnlink) {
        AlertDialog(
            onDismissRequest = { confirmUnlink = false },
            title = { Text(stringResource(R.string.unlink_all)) },
            text = { Text(stringResource(R.string.unlink_all_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnlink = false; busy = true
                    scope.launch { graph.family.unlinkAllFamily(); busy = false }
                }) { Text(stringResource(R.string.unlink), color = Palette.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmUnlink = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
