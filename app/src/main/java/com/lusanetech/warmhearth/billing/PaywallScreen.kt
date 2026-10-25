package com.lusanetech.warmhearth.billing

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.graph
import com.lusanetech.warmhearth.launcher.Apps
import com.lusanetech.warmhearth.ui.BigButton
import com.lusanetech.warmhearth.ui.Page
import com.lusanetech.warmhearth.ui.Palette
import com.lusanetech.warmhearth.ui.Spacing
import com.lusanetech.warmhearth.util.Telemetry
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * Plan picker: yearly (pre-selected, shows the saving) and monthly. Trial terms are spelled out
 * next to the button, as Google Play requires: free for 7 days, then the price, renews
 * automatically, cancel any time in Google Play.
 */
@Composable
fun PaywallScreen(onBack: () -> Unit, forDeviceId: String? = null) {
    val context = LocalContext.current
    val billing = context.graph.billing
    val scope = rememberCoroutineScope()
    val plans by billing.plans.collectAsState()
    val error by billing.error.collectAsState()
    val settings by context.graph.prefs.settings.collectAsState(initial = null)
    var selected by remember { mutableStateOf("yearly") }
    LaunchedEffect(Unit) { billing.connect(); Telemetry.event(context, "paywall_view") }

    val yearly = plans.firstOrNull { it.basePlanId == "yearly" }
    val monthly = plans.firstOrNull { it.basePlanId == "monthly" }
    val savePct = if (yearly != null && monthly != null && monthly.priceMicros > 0)
        (100 - yearly.priceMicros * 100.0 / (monthly.priceMicros * 12)).roundToInt() else 0
    val plan = plans.firstOrNull { it.basePlanId == selected } ?: plans.firstOrNull()

    Page(stringResource(R.string.unlock_everything), onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text(stringResource(R.string.paywall_pitch), style = MaterialTheme.typography.titleLarge)
            listOf(R.string.benefit_family, R.string.benefit_meds, R.string.benefit_fall, R.string.benefit_contacts).forEach { b ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Palette.Green, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(Spacing.m))
                    Text(stringResource(b), style = MaterialTheme.typography.titleMedium)
                }
            }

            if (settings?.premium == true && forDeviceId == null) {
                Text(stringResource(R.string.premium_active), color = Palette.Green, style = MaterialTheme.typography.headlineSmall)
                ManageLink()
                return@Column
            }

            if (plans.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(32.dp))
                    Spacer(Modifier.width(Spacing.m))
                    Text(stringResource(R.string.loading_prices))
                }
                return@Column
            }

            yearly?.let { PlanCard(stringResource(R.string.plan_yearly), it.price + " " + stringResource(R.string.per_year), if (savePct > 0) stringResource(R.string.save_pct, savePct) else null, selected == "yearly") { selected = "yearly" } }
            monthly?.let { PlanCard(stringResource(R.string.plan_monthly), it.price + " " + stringResource(R.string.per_month), null, selected == "monthly") { selected = "monthly" } }

            plan?.let { p ->
                BigButton(
                    text = stringResource(if (p.hasFreeTrial) R.string.start_trial else R.string.subscribe),
                    color = Palette.Ember,
                    icon = Icons.Filled.Star,
                ) { context.findActivity()?.let { billing.buy(it, p, forDeviceId) } }
                val period = stringResource(if (p.basePlanId == "yearly") R.string.per_year else R.string.per_month)
                Text(
                    if (p.hasFreeTrial) stringResource(R.string.trial_terms, p.price, period) else stringResource(R.string.renew_terms, p.price, period),
                    style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft,
                )
            }
            if (error != null) Text(stringResource(R.string.purchase_error), color = Palette.Red)
            Row {
                TextButton(onClick = { scope.launch { billing.restore() } }) { Text(stringResource(R.string.restore_purchase)) }
                ManageLink()
            }
        }
    }
}

@Composable
private fun PlanCard(title: String, price: String, badge: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clip(MaterialTheme.shapes.large)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .border(if (selected) 3.dp else 1.dp, if (selected) Palette.Brand else Palette.Outline, MaterialTheme.shapes.large)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(Spacing.l),
    ) {
        Icon(if (selected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked, contentDescription = null, tint = Palette.Brand, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(Spacing.m))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(price, style = MaterialTheme.typography.titleMedium, color = Palette.InkSoft)
        }
        badge?.let {
            Text(it, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(MaterialTheme.shapes.small).background(Palette.Green).padding(horizontal = 10.dp, vertical = 4.dp))
        }
    }
}

@Composable
private fun ManageLink() {
    val context = LocalContext.current
    TextButton(onClick = { Apps.start(context, Intent(Intent.ACTION_VIEW, Uri.parse(BillingManager.MANAGE_URL))) }) {
        Text(stringResource(R.string.manage_subscription))
    }
}
