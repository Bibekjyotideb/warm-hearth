package com.lusanetech.warmhearth.family

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.ui.BigButton
import com.lusanetech.warmhearth.ui.Chip
import com.lusanetech.warmhearth.ui.IconBadge
import com.lusanetech.warmhearth.ui.NavRow
import com.lusanetech.warmhearth.ui.Palette
import com.lusanetech.warmhearth.ui.PersonAvatar
import com.lusanetech.warmhearth.ui.SectionCard
import com.lusanetech.warmhearth.ui.Tone
import com.lusanetech.warmhearth.graph
import com.lusanetech.warmhearth.util.RemoteFlags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ---------------------------------------------------------------- how is she doing?

enum class Level { OK, ATTENTION, ALERT, UNKNOWN }

data class Assessment(val level: Level, val headline: String, val detail: String)

private const val H = 3_600_000L

/** One sentence answer to "Is Maa OK?", from the phone's status and recent alerts. */
fun assess(c: Context, d: DeviceState, events: List<RemoteEvent>, now: Long = System.currentTimeMillis()): Assessment {
    val name = d.name.ifBlank { c.getString(R.string.unnamed_phone) }
    val st = d.status
    val sos = events.firstOrNull { it.type == "sos" && now - it.createdAt < 2 * H }
    if (sos != null) return Assessment(Level.ALERT, c.getString(R.string.st_sos, name), ago(sos.createdAt))
    if (st?.reportedAt == null) return Assessment(Level.UNKNOWN, c.getString(R.string.st_waiting), c.getString(R.string.st_waiting_sub, name))
    if (now - st.reportedAt > 6 * H) return Assessment(Level.ATTENTION, c.getString(R.string.st_offline), c.getString(R.string.st_offline_sub, ago(st.reportedAt)))
    if (st.quiet) return Assessment(Level.ATTENTION, c.getString(R.string.st_silent), c.getString(R.string.st_silent_sub, name))
    if (st.battery != null && st.battery <= 15 && !st.charging) return Assessment(Level.ATTENTION, c.getString(R.string.st_battery, st.battery), c.getString(R.string.st_battery_sub))
    val s = d.settings
    if (s.checkInEnabled && !checkedInToday(d, now) && localHm(st, now) >= s.checkInBy) {
        return Assessment(Level.ATTENTION, c.getString(R.string.st_no_checkin), c.getString(R.string.st_no_checkin_sub, s.checkInBy))
    }
    val last = st.lastActiveAt
    if (last != null && now - last > s.inactiveHours * H) return Assessment(Level.ATTENTION, c.getString(R.string.st_inactive, s.inactiveHours), c.getString(R.string.st_used, ago(last)))
    return Assessment(Level.OK, c.getString(R.string.st_ok), if (last != null) c.getString(R.string.st_used, ago(last)) else c.getString(R.string.st_ok_sub))
}

/** The parent's time zone (a son in Toronto, a mother in Agartala), falling back to ours. */
private fun zoneOf(st: PhoneStatus?): TimeZone = st?.tz?.let { TimeZone.getTimeZone(it) } ?: TimeZone.getDefault()
private fun localHm(st: PhoneStatus, now: Long) = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = zoneOf(st) }.format(Date(now))

fun checkedInToday(d: DeviceState, now: Long = System.currentTimeMillis()): Boolean {
    val at = d.lastCheckInAt ?: return false
    val f = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = zoneOf(d.status) }
    return f.format(Date(at)) == f.format(Date(now))
}

fun ago(ms: Long): String = DateUtils.getRelativeTimeSpanString(ms, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

private fun toneOf(level: Level) = when (level) {
    Level.OK -> Tone.Green
    Level.ATTENTION -> Tone.Orange
    Level.ALERT -> Tone.Red
    Level.UNKNOWN -> Tone.Grey
}

private fun iconOf(level: Level) = when (level) {
    Level.OK -> Icons.Filled.CheckCircle
    Level.ATTENTION -> Icons.Filled.WarningAmber
    Level.ALERT -> Icons.Filled.Sos
    Level.UNKNOWN -> Icons.Filled.Schedule
}

// ---------------------------------------------------------------- list card

@Composable
fun DeviceSummaryCard(d: DeviceState, onClick: () -> Unit) {
    val context = LocalContext.current
    val events by remember(d.id) { context.graph.family.observeEvents(d.id) }.collectAsState(initial = emptyList())
    val a = assess(context, d, events)
    val tone = toneOf(a.level)
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Palette.Surface)
            .clickable(role = Role.Button, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(d.name.ifBlank { "?" }, null, 56.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(d.name.ifBlank { stringResource(R.string.unnamed_phone) }, style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(iconOf(a.level), null, tint = tone.solid, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(a.headline, style = MaterialTheme.typography.bodyMedium, color = tone.solid, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!d.premium) Chip(stringResource(R.string.free_plan), Tone.Grey)
    }
}

// ---------------------------------------------------------------- the dashboard

@Composable
fun DeviceDashboard(d: DeviceState, showBack: Boolean, onBack: () -> Unit, go: (FamilyRoute) -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val scope = rememberCoroutineScope()
    val events by remember(d.id) { graph.family.observeEvents(d.id) }.collectAsState(initial = emptyList())
    val contacts by remember(d.id) { graph.family.observeContacts(d.id) }.collectAsState(initial = emptyList())
    val meds by remember(d.id) { graph.family.observeMedicines(d.id) }.collectAsState(initial = emptyList())
    val doses by remember(d.id, meds, d.status?.tz) { graph.remote.observeDoses(d.id, d.status?.tz, meds) }.collectAsState(initial = emptyList())
    var toast by remember { mutableStateOf<String?>(null) }
    val name = d.name.ifBlank { stringResource(R.string.unnamed_phone) }

    // Ask the phone for fresh status when the dashboard opens (Premium; silently ignored otherwise).
    LaunchedEffect(d.id) { if (d.premium) graph.remote.sendCommand(d.id, "sync") }

    fun premium(action: () -> Unit) { if (d.premium) action() else go(FamilyRoute.Paywall(d.id)) }
    fun command(type: String, sending: String) = premium {
        toast = sending
        scope.launch {
            val id = graph.remote.sendCommand(d.id, type)
            if (id == null) { toast = context.getString(R.string.cmd_failed); return@launch }
            val result = withTimeoutOrNull(45_000) { graph.remote.observeCommand(d.id, id).filterNotNull().first() }
            toast = when {
                result == null -> context.getString(R.string.cmd_pending, name)
                result == "ringing" -> context.getString(R.string.cmd_ringing, name)
                result == "ok" -> context.getString(R.string.cmd_ringer_on, name)
                result == "no_access" -> context.getString(R.string.cmd_no_access, name)
                else -> context.getString(R.string.cmd_failed)
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Palette.Background).statusBarsPadding().navigationBarsPadding()) {
        // Header
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showBack) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } else Spacer(Modifier.width(12.dp))
            Text(name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Chip(stringResource(if (d.premium) R.string.premium else R.string.free_plan), if (d.premium) Tone.Ember else Tone.Grey, icon = if (d.premium) Icons.Filled.Star else null)
            if (!showBack) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.People, stringResource(R.string.family_title), tint = Palette.InkSoft) }
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StatusHero(d, events)
            QuickActions(
                d,
                onRing = { command("ring", context.getString(R.string.cmd_sending_ring, name)) },
                onRinger = { command("ringer_on", context.getString(R.string.cmd_sending_ringer, name)) },
                onPhoto = { go(FamilyRoute.Notes(d.id)) },
            )
            toast?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.Brand, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 4.dp)) }

            TodayMedicines(d, doses, meds.isEmpty(), onManage = { go(FamilyRoute.Medicines(d.id)) }, onPaywall = { go(FamilyRoute.Paywall(d.id)) })
            Activity(events)

            SectionCard(padding = 8.dp) {
                Text(stringResource(R.string.manage_phone, name), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(8.dp))
                NavRow(stringResource(R.string.tab_people), Icons.Filled.People, Tone.Blue, subtitle = pluralCount(context, R.plurals.n_people, contacts.size)) { go(FamilyRoute.People(d.id)) }
                NavRow(stringResource(R.string.tab_medicines), Icons.Filled.Medication, Tone.Orange, subtitle = pluralCount(context, R.plurals.n_medicines, meds.size)) { go(FamilyRoute.Medicines(d.id)) }
                NavRow(stringResource(R.string.home_and_text), Icons.Filled.Dashboard, Tone.Teal, subtitle = stringResource(R.string.home_and_text_sub)) { go(FamilyRoute.HomeSetup(d.id)) }
                NavRow(stringResource(R.string.safety_alerts), Icons.Filled.HealthAndSafety, Tone.Green, subtitle = stringResource(R.string.safety_alerts_sub)) { go(FamilyRoute.Safety(d.id)) }
                if (RemoteFlags.callShieldEnabled) NavRow(
                    stringResource(R.string.call_shield), Icons.Filled.Shield, Tone.Brand,
                    subtitle = shieldSubtitle(context, d),
                ) { go(FamilyRoute.Shield(d.id)) }
                if (RemoteFlags.notesEnabled) NavRow(stringResource(R.string.photos_notes), Icons.Filled.AddAPhoto, Tone.Pink, subtitle = stringResource(R.string.photos_notes_sub)) { go(FamilyRoute.Notes(d.id)) }
            }

            if (!d.premium) {
                SectionCard {
                    Text(stringResource(R.string.premium_pitch_title, name), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.premium_pitch_body), style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
                    Spacer(Modifier.height(12.dp))
                    BigButton(stringResource(R.string.start_trial), color = Palette.Ember, icon = Icons.Filled.Star) { go(FamilyRoute.Paywall(d.id)) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun pluralCount(c: Context, id: Int, n: Int) = c.resources.getQuantityString(id, n, n)

private fun shieldSubtitle(c: Context, d: DeviceState): String {
    val mode = when (d.settings.callShield) {
        "silence" -> R.string.shield_mode_silence
        "block" -> R.string.shield_mode_block
        "off" -> R.string.shield_mode_off
        else -> R.string.shield_mode_warn
    }
    val n = d.status?.callsScreenedToday ?: 0
    return c.getString(mode) + if (n > 0) " · " + c.resources.getQuantityString(R.plurals.n_calls_screened, n, n) else ""
}

@Composable
private fun StatusHero(d: DeviceState, events: List<RemoteEvent>) {
    val context = LocalContext.current
    val a = assess(context, d, events)
    val tone = toneOf(a.level)
    val st = d.status
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(tone.soft).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(iconOf(a.level), tone, size = 52.dp, solid = true)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(a.headline, style = MaterialTheme.typography.headlineSmall, color = Palette.Ink)
                Text(a.detail, style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
            }
        }
        // 2 x 2 facts
        val checkIn = if (checkedInToday(d)) stringResource(R.string.fact_checkin_ok, time(d.lastCheckInAt!!)) else stringResource(R.string.fact_checkin_none)
        val battery = st?.battery?.let { "$it%" + if (st.charging) " · " + stringResource(R.string.charging) else "" } ?: "–"
        val ringer = when {
            st?.ringer == null -> "–"
            st.quiet -> stringResource(R.string.fact_ringer_silent)
            else -> stringResource(R.string.fact_ringer_on, st.ringVolume ?: 0)
        }
        val used = st?.lastActiveAt?.let { ago(it) } ?: "–"
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Fact(Modifier.weight(1f), Icons.Filled.TaskAlt, stringResource(R.string.fact_checkin), checkIn, good = checkedInToday(d))
            Fact(Modifier.weight(1f), if (st?.charging == true) Icons.Filled.BatteryChargingFull else if ((st?.battery ?: 100) <= 15) Icons.Filled.BatteryAlert else Icons.Filled.BatteryFull,
                stringResource(R.string.fact_battery), battery, good = (st?.battery ?: 100) > 15 || st?.charging == true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Fact(Modifier.weight(1f), if (st?.quiet == true) Icons.Filled.NotificationsOff else Icons.Filled.VolumeUp, stringResource(R.string.fact_ringer), ringer, good = st?.quiet != true)
            Fact(Modifier.weight(1f), Icons.Filled.TouchApp, stringResource(R.string.fact_used), used, good = true)
        }
    }
}

private fun time(ms: Long) = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(Date(ms))

@Composable
private fun Fact(modifier: Modifier, icon: ImageVector, label: String, value: String, good: Boolean) {
    Row(
        modifier.clip(MaterialTheme.shapes.medium).background(Palette.Surface.copy(alpha = 0.85f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (good) Palette.InkSoft else Palette.Orange, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.InkSoft)
            Text(value, style = MaterialTheme.typography.titleSmall, color = if (good) Palette.Ink else Palette.Orange, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun QuickActions(d: DeviceState, onRing: () -> Unit, onRinger: () -> Unit, onPhoto: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (RemoteFlags.remoteRingEnabled) Action(Modifier.weight(1f), Icons.Filled.NotificationsActive, stringResource(R.string.action_ring_phone), Tone.Brand, locked = !d.premium, onRing)
        Action(Modifier.weight(1f), Icons.Filled.VolumeUp, stringResource(R.string.action_ringer_on), Tone.Ember, locked = !d.premium, onRinger)
        if (RemoteFlags.notesEnabled) Action(Modifier.weight(1f), Icons.Filled.AddAPhoto, stringResource(R.string.action_send_photo), Tone.Pink, locked = false, onPhoto)
    }
}

@Composable
private fun Action(modifier: Modifier, icon: ImageVector, label: String, tone: Tone, locked: Boolean, onClick: () -> Unit) {
    Column(
        modifier.clip(MaterialTheme.shapes.large).background(Palette.Surface).clickable(role = Role.Button, onClick = onClick).padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            IconBadge(icon, tone, size = 48.dp)
            if (locked) Icon(Icons.Filled.Lock, null, tint = Palette.InkSoft, modifier = Modifier.size(16.dp).align(Alignment.BottomEnd).clip(MaterialTheme.shapes.small).background(Palette.Surface))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
private fun TodayMedicines(d: DeviceState, doses: List<DoseStatus>, none: Boolean, onManage: () -> Unit, onPaywall: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.todays_medicines), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            val taken = doses.count { it.state == DoseStatus.State.TAKEN }
            if (doses.isNotEmpty()) Chip("$taken / ${doses.size}", if (taken == doses.size) Tone.Green else Tone.Orange)
        }
        Spacer(Modifier.height(6.dp))
        when {
            !d.premium -> {
                Text(stringResource(R.string.meds_locked), style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
                Text(stringResource(R.string.try_premium), color = Palette.Ember, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onPaywall).padding(vertical = 8.dp))
            }
            none -> Text(stringResource(R.string.meds_none), color = Palette.Brand, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onManage).padding(vertical = 8.dp))
            else -> doses.forEach { dose ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(dose.time, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(56.dp), color = Palette.InkSoft)
                    Text(dose.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    when (dose.state) {
                        DoseStatus.State.TAKEN -> Chip(stringResource(R.string.dose_taken, dose.at?.let { time(it) } ?: ""), Tone.Green)
                        DoseStatus.State.MISSED -> Chip(stringResource(R.string.dose_missed), Tone.Red)
                        DoseStatus.State.DUE -> Chip(stringResource(R.string.dose_due), Tone.Orange)
                        DoseStatus.State.UPCOMING -> Chip(stringResource(R.string.dose_upcoming), Tone.Grey)
                    }
                }
            }
        }
    }
}

@Composable
private fun Activity(events: List<RemoteEvent>) {
    val context = LocalContext.current
    SectionCard {
        Text(stringResource(R.string.recent_activity), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        if (events.isEmpty()) Text(stringResource(R.string.no_alerts), style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
        events.take(6).forEach { e ->
            val (icon, tone) = when (e.type) {
                "sos" -> Icons.Filled.Sos to Tone.Red
                "checkin" -> Icons.Filled.TaskAlt to Tone.Green
                "call_me" -> Icons.Filled.PhoneCallback to Tone.Blue
                "here" -> Icons.Filled.CheckCircle to Tone.Green
                "missed_dose" -> Icons.Filled.Medication to Tone.Orange
                "silent" -> Icons.Filled.NotificationsOff to Tone.Orange
                "low_battery" -> Icons.Filled.BatteryAlert to Tone.Orange
                else -> Icons.Filled.ErrorOutline to Tone.Orange
            }
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                    .clickable(enabled = e.lat != null) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=${e.lat},${e.lng}"))) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(icon, tone, size = 38.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.message, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(ago(e.createdAt) + if (e.lat != null) " · " + stringResource(R.string.tap_for_map) else "", style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
                }
            }
        }
    }
}
