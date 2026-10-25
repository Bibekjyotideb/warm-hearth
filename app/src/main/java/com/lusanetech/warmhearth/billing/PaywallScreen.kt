package com.lusanetech.warmhearth.billing

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.graph
import com.lusanetech.warmhearth.ui.BigButton
import com.lusanetech.warmhearth.ui.Page
import com.lusanetech.warmhearth.ui.Palette
import com.lusanetech.warmhearth.ui.Spacing
import kotlinx.coroutines.launch

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * One screen, one price, one button. No subscriptions, no tiers: "pay once, yours forever"
 * is the whole pitch. [forDeviceId] is set when a family member unlocks a parent's phone.
 */
@Composable
fun PaywallScreen(onBack: () -> Unit, forDeviceId: String? = null) {
    val context = LocalContext.current
    val billing = context.graph.billing
    val scope = rememberCoroutineScope()
    val price by billing.price.collectAsState()
    val error by billing.error.collectAsState()
    val premium by context.graph.prefs.settings.collectAsState(initial = null)
    LaunchedEffect(Unit) { billing.connect() }

    Page(stringResource(R.string.unlock_everything), onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text(stringResource(R.string.paywall_pitch), style = MaterialTheme.typography.titleLarge)
            listOf(
                R.string.benefit_family,
                R.string.benefit_meds,
                R.string.benefit_fall,
                R.string.benefit_contacts,
                R.string.benefit_forever,
            ).forEach { b ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Palette.Green, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(Spacing.m))
                    Text(stringResource(b), style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.size(Spacing.s))
            if (premium?.premium == true && forDeviceId == null) {
                Text(stringResource(R.string.premium_active), color = Palette.Green, style = MaterialTheme.typography.headlineMedium)
            } else {
                BigButton(
                    text = price?.let { stringResource(R.string.buy_for, it) } ?: stringResource(R.string.buy),
                    color = Palette.Orange,
                    icon = Icons.Filled.Star,
                ) { context.findActivity()?.let { billing.buy(it, forDeviceId) } }
                if (error != null) Text(stringResource(R.string.purchase_error), color = Palette.Red)
                TextButton(onClick = { scope.launch { billing.restore() } }) { Text(stringResource(R.string.restore_purchase)) }
                Text(stringResource(R.string.paywall_fine_print), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
