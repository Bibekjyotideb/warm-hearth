package com.lusanetech.warmhearth.family

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.lusanetech.warmhearth.MainActivity
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.billing.PaywallScreen
import com.lusanetech.warmhearth.billing.findActivity
import com.lusanetech.warmhearth.data.AppMode
import com.lusanetech.warmhearth.graph
import com.lusanetech.warmhearth.ui.BigButton
import com.lusanetech.warmhearth.ui.Page
import com.lusanetech.warmhearth.ui.Palette
import com.lusanetech.warmhearth.util.RemoteFlags
import kotlinx.coroutines.launch
import java.util.Locale

/** Where the family app is. Flat, like the senior side: Back always goes one step up. */
sealed interface FamilyRoute {
    data object Home : FamilyRoute
    data object Support : FamilyRoute
    data class Dashboard(val id: String) : FamilyRoute
    data class People(val id: String) : FamilyRoute
    data class Medicines(val id: String) : FamilyRoute
    data class HomeSetup(val id: String) : FamilyRoute
    data class Safety(val id: String) : FamilyRoute
    data class Shield(val id: String) : FamilyRoute
    data class Notes(val id: String) : FamilyRoute
    data class Paywall(val id: String) : FamilyRoute
    data class Vitals(val id: String) : FamilyRoute
    data class Devices(val id: String) : FamilyRoute
}

/**
 * Family mode: the son's / daughter's phone. A dashboard per parent:
 * are they OK, is the phone reachable, today's medicines, one-tap fixes, photos for their home screen.
 */
@Composable
fun FamilyApp() {
    val context = LocalContext.current
    val graph = context.graph
    var signedIn by remember { mutableStateOf(GoogleAccount.signedInEmail != null) }
    if (!signedIn) {
        FamilySignIn(onSignedIn = { signedIn = true })
        return
    }
    val devices by remember { graph.remote.observeDevices() }.collectAsState(initial = null)
    val settings by graph.prefs.settings.collectAsState(initial = null)
    var route by remember { mutableStateOf<FamilyRoute>(FamilyRoute.Home) }
    var autoOpened by rememberSaveable { mutableStateOf(false) }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        graph.family.registerFamilyToken()
        graph.family.watchFamilyPremium()
    }
    // Opened from a notification about one parent.
    LaunchedEffect(Unit) {
        MainActivity.openDevice.collect { id -> if (id != null) { route = FamilyRoute.Dashboard(id); MainActivity.openDevice.value = null } }
    }
    // One parent linked (the usual case): go straight to their dashboard.
    LaunchedEffect(devices) {
        val list = devices ?: return@LaunchedEffect
        if (!autoOpened && list.size == 1 && route == FamilyRoute.Home) route = FamilyRoute.Dashboard(list.first().id)
        if (list.isNotEmpty()) autoOpened = true
    }
    // Ask for a Play rating once a parent's phone has been linked for 3 days (they've seen it work).
    LaunchedEffect(devices) {
        if (devices.isNullOrEmpty()) return@LaunchedEffect
        val sp = context.getSharedPreferences("review", 0)
        val first = sp.getLong("first_link", 0L).takeIf { it > 0 } ?: System.currentTimeMillis().also { sp.edit().putLong("first_link", it).apply() }
        if (System.currentTimeMillis() - first > 3 * 86_400_000L) context.findActivity()?.let { a ->
            (a as? androidx.activity.ComponentActivity)?.let { com.lusanetech.warmhearth.util.AppUpdates.maybeAskForReview(it) }
        }
    }
    // A subscriber gets Premium on every parent phone they link: restore() re-sends the purchase
    // to the server with the current list of linked phones.
    LaunchedEffect(devices, settings?.premium) {
        graph.billing.familyDeviceIds = devices.orEmpty().map { it.id }
        if (settings?.premium == true && devices.orEmpty().any { !it.premium }) graph.billing.restore()
    }

    val up: () -> Unit = {
        route = when (val r = route) {
            is FamilyRoute.Dashboard, FamilyRoute.Support, FamilyRoute.Home -> FamilyRoute.Home
            is FamilyRoute.People -> FamilyRoute.Dashboard(r.id)
            is FamilyRoute.Medicines -> FamilyRoute.Dashboard(r.id)
            is FamilyRoute.HomeSetup -> FamilyRoute.Dashboard(r.id)
            is FamilyRoute.Safety -> FamilyRoute.Dashboard(r.id)
            is FamilyRoute.Shield -> FamilyRoute.Dashboard(r.id)
            is FamilyRoute.Notes -> FamilyRoute.Dashboard(r.id)
            is FamilyRoute.Paywall -> FamilyRoute.Dashboard(r.id)
            is FamilyRoute.Vitals -> FamilyRoute.Dashboard(r.id)
            is FamilyRoute.Devices -> FamilyRoute.Dashboard(r.id)
        }
    }
    BackHandler(enabled = route != FamilyRoute.Home, onBack = up)

    fun device(id: String) = devices?.firstOrNull { it.id == id }
    val go: (FamilyRoute) -> Unit = { route = it }

    when (val r = route) {
        FamilyRoute.Home -> FamilyHome(devices, onOpen = { go(FamilyRoute.Dashboard(it)) }, onSupport = { go(FamilyRoute.Support) }, onSignedOut = { signedIn = false })
        FamilyRoute.Support -> com.lusanetech.warmhearth.support.SupportScreen(onBack = up)
        is FamilyRoute.Paywall -> PaywallScreen(onBack = up, forDeviceId = r.id)
        is FamilyRoute.Devices -> DevicesScreen(onBack = up)
        else -> {
            val id = when (r) {
                is FamilyRoute.Dashboard -> r.id; is FamilyRoute.People -> r.id; is FamilyRoute.Medicines -> r.id
                is FamilyRoute.HomeSetup -> r.id; is FamilyRoute.Safety -> r.id; is FamilyRoute.Shield -> r.id
                is FamilyRoute.Notes -> r.id; is FamilyRoute.Vitals -> r.id; else -> ""
            }
            val d = device(id)
            if (d == null) {
                // Still loading, or this phone was unlinked.
                if (devices != null) LaunchedEffect(Unit) { route = FamilyRoute.Home }
                return
            }
            when (r) {
                is FamilyRoute.Dashboard -> DeviceDashboard(d, showBack = (devices?.size ?: 0) > 1, onBack = up, go = go)
                is FamilyRoute.People -> RemotePeopleScreen(d, onBack = up, onPaywall = { go(FamilyRoute.Paywall(d.id)) })
                is FamilyRoute.Medicines -> RemoteMedicinesScreen(d, onBack = up, onPaywall = { go(FamilyRoute.Paywall(d.id)) })
                is FamilyRoute.HomeSetup -> HomeSetupScreen(d, onBack = up, onPaywall = { go(FamilyRoute.Paywall(d.id)) })
                is FamilyRoute.Safety -> SafetySettingsScreen(d, onBack = up, onPaywall = { go(FamilyRoute.Paywall(d.id)) })
                is FamilyRoute.Shield -> CallShieldSettingsScreen(d, onBack = up, onPaywall = { go(FamilyRoute.Paywall(d.id)) })
                is FamilyRoute.Notes -> NotesScreen(d, onBack = up)
                is FamilyRoute.Vitals -> VitalsScreen(d, onBack = up, onShop = { go(FamilyRoute.Devices(d.id)) }, onPaywall = { go(FamilyRoute.Paywall(d.id)) })
                else -> Unit
            }
        }
    }
}

/** The list of linked parents (shown when there are several, or none yet). */
@Composable
private fun FamilyHome(devices: List<DeviceState>?, onOpen: (String) -> Unit, onSupport: () -> Unit, onSignedOut: () -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    var linking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(Palette.Background).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.family_title), style = MaterialTheme.typography.headlineLarge)

        // Remote Config: tips, offers, "new in this version". No release needed.
        RemoteFlags.familyPromo(Locale.getDefault().language == "hi").takeIf { it.isNotBlank() }?.let { promo ->
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Palette.EmberSoft).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Campaign, null, tint = Palette.Ember)
                Spacer(Modifier.width(10.dp))
                Text(promo, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink)
            }
        }

        when {
            devices == null -> Unit
            devices.isEmpty() -> EmptyFamily()
            else -> devices.forEach { d -> DeviceSummaryCard(d) { onOpen(d.id) } }
        }

        BigButton(stringResource(if (devices.isNullOrEmpty()) R.string.link_a_phone else R.string.link_another_phone), icon = Icons.Filled.Link) { linking = true }

        Spacer(Modifier.height(12.dp))
        GoogleAccount.signedInEmail?.let { Text(stringResource(R.string.signed_in_as, it), style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft) }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onSupport) { Text(stringResource(R.string.help_support)) }
            TextButton(onClick = {
                graph.scope.launch { context.findActivity()?.let { GoogleAccount.signOut(it) } }
                onSignedOut()
            }) { Text(stringResource(R.string.sign_out)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { graph.scope.launch { graph.prefs.setMode(AppMode.UNSET) } }) { Text(stringResource(R.string.switch_to_senior_mode)) }
            TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.delete_my_account), color = Palette.Red) }
        }
    }

    if (linking) LinkDialog(onDismiss = { linking = false })
    if (confirmDelete) {
        var busy by remember { mutableStateOf(false) }
        var failed by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busy) confirmDelete = false },
            title = { Text(stringResource(R.string.delete_my_account)) },
            text = { Text(stringResource(if (failed) R.string.no_internet else R.string.delete_account_body)) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    graph.scope.launch {
                        if (graph.family.deleteMyAccount()) {
                            graph.prefs.clearAll()
                            context.findActivity()?.let { GoogleAccount.signOut(it) }
                        } else { failed = true; busy = false }
                    }
                }) { Text(stringResource(R.string.delete_forever), color = Palette.Red) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun EmptyFamily() {
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Palette.Surface).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.family_empty_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        listOf(R.string.family_empty_1, R.string.family_empty_2, R.string.family_empty_3).forEachIndexed { i, s ->
            Text("${i + 1}. " + stringResource(s), style = MaterialTheme.typography.bodyLarge, color = Palette.InkSoft)
        }
    }
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

/** First screen in family mode: why we need a Google account, then one tap to sign in. */
@Composable
private fun FamilySignIn(onSignedIn: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Page(stringResource(R.string.family_signin_title), onBack = null) {
        Text(stringResource(R.string.family_signin_body), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        BigButton(stringResource(R.string.continue_with_google), icon = Icons.Filled.AccountCircle, enabled = !busy) {
            busy = true; error = null
            scope.launch {
                when (val r = context.findActivity()?.let { GoogleAccount.signIn(it) }) {
                    GoogleAccount.Result.Ok -> { context.graph.family.registerFamilyToken(); onSignedIn() }
                    is GoogleAccount.Result.Failed -> error = r.message
                    else -> Unit
                }
                busy = false
            }
        }
        error?.let { Text(stringResource(R.string.signin_failed), color = Palette.Red, modifier = Modifier.padding(top = 12.dp)) }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.agree_terms), style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
        Row {
            TextButton(onClick = { com.lusanetech.warmhearth.util.Links.open(context, "/privacy") }) { Text(stringResource(R.string.privacy_policy)) }
            TextButton(onClick = { com.lusanetech.warmhearth.util.Links.open(context, "/terms") }) { Text(stringResource(R.string.terms)) }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { context.graph.scope.launch { context.graph.prefs.setMode(AppMode.UNSET) } }) { Text(stringResource(R.string.switch_to_senior_mode)) }
    }
}
