package com.lusanetech.warmhearth.family

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.graph
import com.lusanetech.warmhearth.ui.Chip
import com.lusanetech.warmhearth.ui.IconBadge
import com.lusanetech.warmhearth.ui.NavRow
import com.lusanetech.warmhearth.ui.Page
import com.lusanetech.warmhearth.ui.Palette
import com.lusanetech.warmhearth.ui.SectionCard
import com.lusanetech.warmhearth.ui.SoftButton
import com.lusanetech.warmhearth.ui.Tone
import com.lusanetech.warmhearth.util.RemoteFlags
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import java.util.TimeZone

private data class Cell(val icon: ImageVector, val tone: Tone, val label: Int, val value: String, val sub: String?)

/** Our /devices page picks the right Amazon store from ?c=GB etc. Other links are left alone. */
private fun withCountry(url: String, country: String): String {
    if (!url.contains("warmhearth.lusanetech.com")) return url
    val base = url.substringBefore('#')
    val frag = url.substringAfter('#', "")
    return base + (if ('?' in base) "&" else "?") + "c=" + country + (if (frag.isNotEmpty()) "#$frag" else "")
}

private fun sleepText(min: Int) = "${min / 60}h ${min % 60}m"

/** One reading in the vitals grid. */
@Composable
private fun Reading(modifier: Modifier, icon: ImageVector, tone: Tone, label: String, value: String, sub: String? = null) {
    Row(modifier.clip(MaterialTheme.shapes.medium).background(tone.soft).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tone.solid, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.InkSoft)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Palette.Ink)
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = Palette.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Dashboard card: latest readings from their watch, or how to get started. */
@Composable
fun VitalsCard(d: DeviceState, onOpen: () -> Unit, onShop: () -> Unit, onPaywall: () -> Unit) {
    val v = d.vitals
    val context = LocalContext.current
    // Units follow the person reading (a daughter in London sees mmol/L, a son in Delhi mg/dL).
    val country = remember { com.lusanetech.warmhearth.util.Region.country(context) }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.vitals_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (v != null && !v.empty) Text(stringResource(R.string.see_7_days), color = Palette.Brand, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(role = Role.Button, onClick = onOpen).padding(4.dp))
        }
        Spacer(Modifier.height(8.dp))
        when {
            !d.premium -> {
                Text(stringResource(R.string.vitals_locked), style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
                Text(stringResource(R.string.try_premium), color = Palette.Ember, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onPaywall).padding(vertical = 8.dp))
            }
            v == null || v.empty -> {
                Text(stringResource(R.string.vitals_none, d.name.ifBlank { stringResource(R.string.unnamed_phone) }), style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
                if (RemoteFlags.shopEnabled) {
                    Spacer(Modifier.height(10.dp))
                    SoftButton(stringResource(R.string.which_watch), Tone.Brand, icon = Icons.Filled.Watch, minHeight = 52.dp, onClick = onShop)
                }
            }
            else -> {
                val cells = buildList {
                    v.heartRate?.let { add(Cell(Icons.Filled.Favorite, Tone.Red, R.string.vt_heart, "$it bpm", v.heartRateAt?.let(::ago))) }
                    v.stepsToday?.let { add(Cell(Icons.Filled.DirectionsWalk, Tone.Green, R.string.vt_steps, NumberFormat.getInstance().format(it), null)) }
                    v.sleepMinutes?.let { add(Cell(Icons.Filled.Bedtime, Tone.Purple, R.string.vt_sleep, sleepText(it), null)) }
                    v.spo2?.let { add(Cell(Icons.Filled.WaterDrop, Tone.Blue, R.string.vt_spo2, "${it.toInt()}%", null)) }
                    if (v.bpSystolic != null && v.bpDiastolic != null) add(Cell(Icons.Filled.MonitorHeart, Tone.Orange, R.string.vt_bp, "${v.bpSystolic}/${v.bpDiastolic}", v.bpAt?.let(::ago)))
                    v.glucoseMgDl?.let { add(Cell(Icons.Filled.Bloodtype, Tone.Pink, R.string.vt_sugar, com.lusanetech.warmhearth.util.Region.glucose(it, country), v.glucoseAt?.let(::ago))) }
                }
                cells.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                        row.forEach { c -> Reading(Modifier.weight(1f), c.icon, c.tone, stringResource(c.label), c.value, c.sub) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                val from = v.sources.joinToString(", ").ifBlank { stringResource(R.string.vt_watch) }
                Text(
                    stringResource(R.string.vt_synced, from, v.syncedAt?.let { ago(it) } ?: "–"),
                    style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft,
                )
            }
        }
    }
}

/** 7-day history and the (optional, informational) heart-rate alert. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VitalsScreen(d: DeviceState, onBack: () -> Unit, onShop: () -> Unit, onPaywall: () -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val scope = rememberCoroutineScope()
    val days by remember(d.id) { graph.remote.observeVitalsHistory(d.id) }.collectAsState(initial = emptyList())
    var saved by remember { mutableStateOf<String?>(null) }
    val s = d.settings
    fun save(next: DeviceSettings) {
        if (!d.premium) { onPaywall(); return }
        scope.launch { saved = context.getString(if (graph.remote.saveSettings(d.id, next)) R.string.saved_will_update else R.string.no_internet) }
    }

    Page(stringResource(R.string.vitals_title), onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            VitalsCard(d, onOpen = {}, onShop = onShop, onPaywall = onPaywall)

            if (days.isNotEmpty()) SectionCard {
                Text(stringResource(R.string.last_7_days), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                val maxSteps = (days.maxOfOrNull { it.steps ?: 0L } ?: 0L).coerceAtLeast(1L)
                days.forEach { day ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(day.date.takeLast(5), style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(56.dp))
                            // Steps as a simple bar: the shape of the week at a glance.
                            val frac = ((day.steps ?: 0L).toFloat() / maxSteps).coerceIn(0.02f, 1f)
                            Row(Modifier.weight(1f).height(10.dp).clip(MaterialTheme.shapes.small).background(Palette.SurfaceLow)) {
                                Spacer(Modifier.fillMaxWidth(frac).height(10.dp).clip(MaterialTheme.shapes.small).background(Palette.Green))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(day.steps?.let { NumberFormat.getInstance().format(it) } ?: "–", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(56.dp))
                        }
                        val bits = listOfNotNull(
                            day.hrAvg?.let { stringResource(R.string.vt_hr_avg, it) },
                            day.sleepMinutes?.let { stringResource(R.string.vt_sleep) + " " + sleepText(it) },
                            if (day.bpSystolic != null) "${stringResource(R.string.vt_bp)} ${day.bpSystolic}/${day.bpDiastolic}" else null,
                            day.glucose?.let { "${stringResource(R.string.vt_sugar)} ${com.lusanetech.warmhearth.util.Region.glucose(it, com.lusanetech.warmhearth.util.Region.country(context))}" },
                        )
                        if (bits.isNotEmpty()) Text(bits.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft, modifier = Modifier.padding(start = 56.dp))
                    }
                }
            }

            SectionCard {
                NavRow(
                    stringResource(R.string.hr_alerts), Icons.Filled.Favorite, Tone.Red, subtitle = stringResource(R.string.hr_alerts_sub),
                    trailing = { Switch(checked = s.hrAlerts, onCheckedChange = { save(s.copy(hrAlerts = it)) }) },
                ) { save(s.copy(hrAlerts = !s.hrAlerts)) }
                if (s.hrAlerts) {
                    Text(stringResource(R.string.hr_above), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(100, 110, 120, 130, 140).forEach { v -> FilterChip(s.hrHigh == v, { save(s.copy(hrHigh = v)) }, { Text("$v") }) }
                    }
                    Text(stringResource(R.string.hr_below), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(40, 45, 50, 55).forEach { v -> FilterChip(s.hrLow == v, { save(s.copy(hrLow = v)) }, { Text("$v") }) }
                    }
                }
                saved?.let { Text(it, color = Palette.Green, modifier = Modifier.padding(top = 6.dp)) }
            }
            Text(stringResource(R.string.vitals_disclaimer), style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
            Text(stringResource(R.string.vitals_setup_hint), style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * "Which watch to buy?": watches and bands that send readings to WarmHearth through Health Connect.
 * The list is the same for everyone and comes from Remote Config; links open our website, where
 * the affiliate links and the disclosure live.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DevicesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val hindi = Locale.getDefault().language == "hi"
    val country = remember { com.lusanetech.warmhearth.util.Region.country(context) }
    val india = country == "IN"
    val money = com.lusanetech.warmhearth.util.Region.money(country)
    val items = remember { RemoteFlags.shop() }
    var tier by remember { mutableStateOf<String?>(null) }
    fun label(inr: Int, gbp: Int, eur: Int, usd: Int) = stringResource(
        when (money) {
            com.lusanetech.warmhearth.util.Region.Money.INR -> inr
            com.lusanetech.warmhearth.util.Region.Money.GBP -> gbp
            com.lusanetech.warmhearth.util.Region.Money.EUR -> eur
            com.lusanetech.warmhearth.util.Region.Money.USD -> usd
        },
    )
    val tiers = listOf(
        "budget" to label(R.string.tier_budget_in, R.string.tier_budget_gb, R.string.tier_budget_eu, R.string.tier_budget_intl),
        "mid" to label(R.string.tier_mid_in, R.string.tier_mid_gb, R.string.tier_mid_eu, R.string.tier_mid_intl),
        "premium" to label(R.string.tier_premium_in, R.string.tier_premium_gb, R.string.tier_premium_eu, R.string.tier_premium_intl),
    )
    val featureLabel = mapOf(
        "steps" to R.string.ft_steps, "hr" to R.string.ft_hr, "spo2" to R.string.ft_spo2, "sleep" to R.string.ft_sleep,
        "bp" to R.string.ft_bp, "fall" to R.string.ft_fall, "sos" to R.string.ft_sos,
    )

    Page(stringResource(R.string.which_watch), onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.shop_intro), style = MaterialTheme.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(tier == null, { tier = null }, { Text(stringResource(R.string.all)) })
                tiers.forEach { (k, label) -> FilterChip(tier == k, { tier = k }, { Text(label) }) }
            }
            items.filter { tier == null || it.tier == tier }.forEach { item ->
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Filled.Watch, if (item.features.contains("fall")) Tone.Red else Tone.Brand)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium)
                            Text(tiers.firstOrNull { it.first == item.tier }?.second.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        item.features.forEach { f ->
                            featureLabel[f]?.let { Chip(stringResource(it), if (f == "fall" || f == "sos") Tone.Red else Tone.Grey) }
                        }
                    }
                    if (item.note.isNotBlank()) Text(item.note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    if (item.app.isNotBlank()) Text(stringResource(R.string.syncs_via, item.app), style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(10.dp))
                    SoftButton(stringResource(R.string.see_prices), Tone.Brand, icon = Icons.Filled.OpenInNew, minHeight = 50.dp) {
                        com.lusanetech.warmhearth.util.Telemetry.event(context, "shop_click", "item" to item.id)
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(withCountry(if (india) item.urlIn else item.urlIntl, country))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                }
            }
            Text(stringResource(R.string.shop_check_note), style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
            Text(RemoteFlags.shopDisclosure(hindi).ifBlank { stringResource(R.string.shop_disclosure) }, style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
            Spacer(Modifier.height(12.dp))
        }
    }
}
