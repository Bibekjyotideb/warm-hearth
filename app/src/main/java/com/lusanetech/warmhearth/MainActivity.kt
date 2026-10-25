package com.lusanetech.warmhearth

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lusanetech.warmhearth.billing.PaywallScreen
import com.lusanetech.warmhearth.calls.ContactsScreen
import com.lusanetech.warmhearth.calls.DialerScreen
import com.lusanetech.warmhearth.calls.EditContactsScreen
import com.lusanetech.warmhearth.data.AppMode
import com.lusanetech.warmhearth.data.Settings
import com.lusanetech.warmhearth.family.FamilyApp
import com.lusanetech.warmhearth.family.PairingScreen
import com.lusanetech.warmhearth.launcher.AppDrawerScreen
import com.lusanetech.warmhearth.launcher.HomeScreen
import com.lusanetech.warmhearth.meds.EditMedicinesScreen
import com.lusanetech.warmhearth.meds.TodayMedicinesScreen
import com.lusanetech.warmhearth.onboarding.OnboardingScreen
import com.lusanetech.warmhearth.settings.PinScreen
import com.lusanetech.warmhearth.settings.SettingsScreen
import com.lusanetech.warmhearth.settings.TilesScreen
import com.lusanetech.warmhearth.sos.SosScreen
import com.lusanetech.warmhearth.ui.WarmHearthTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import com.lusanetech.warmhearth.ai.ReadItScreen
import com.lusanetech.warmhearth.ai.ScamCheckScreen
import kotlinx.coroutines.flow.asSharedFlow

class MainActivity : ComponentActivity() {

    companion object {
        private val _homePressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        /** Text shared into the app ("Share > WarmHearth") to be checked for scams. */
        val sharedText = MutableStateFlow<String?>(null)
        /** Emits when the user presses the Home button while we are already the launcher. */
        val homePressed = _homePressed.asSharedFlow()
        const val EXTRA_OPEN = "open"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val openOnStart = intent?.getStringExtra(EXTRA_OPEN)
        handleShare(intent)
        updates = com.lusanetech.warmhearth.util.AppUpdates(this)
        setContent {
            val settings by graph.prefs.settings.collectAsStateWithLifecycle(initialValue = null)
            val s = settings ?: return@setContent
            WarmHearthTheme(scale = if (s.mode == AppMode.FAMILY) 1.0f else s.fontScale) {
                when (s.mode) {
                    AppMode.UNSET -> OnboardingScreen()
                    AppMode.FAMILY -> FamilyApp()
                    AppMode.SENIOR -> SeniorApp(s, openOnStart)
                }
            }
        }
    }

    private var updates: com.lusanetech.warmhearth.util.AppUpdates? = null

    override fun onResume() {
        super.onResume()
        updates?.check() // forced / background updates via Play (see util/AppUpdates.kt)
    }

    override fun onDestroy() {
        updates?.release()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) _homePressed.tryEmit(Unit)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { sharedText.value = it }
        }
    }
}

/** Every screen of the senior side. Kept as a flat enum: no nested back stacks to get lost in. */
enum class Screen { HOME, APPS, DIALER, CONTACTS, MEDS, SOS, SCAM, READ, PIN, SETTINGS, EDIT_CONTACTS, EDIT_MEDS, TILES, PAIRING, PAYWALL, SUPPORT }

@Composable
private fun SeniorApp(s: Settings, openOnStart: String?) {
    var screen by remember { mutableStateOf(if (openOnStart == "meds") Screen.MEDS else Screen.HOME) }
    // Where the paywall returns to, so it can never be used to skip the settings PIN.
    var paywallReturn by remember { mutableStateOf(Screen.HOME) }
    // Settings sub-pages return to Settings; everything else returns Home.
    val settingsChildren = setOf(Screen.EDIT_CONTACTS, Screen.EDIT_MEDS, Screen.TILES, Screen.PAIRING, Screen.SUPPORT)
    val back: () -> Unit = {
        screen = when (screen) {
            Screen.PAYWALL -> paywallReturn
            in settingsChildren -> Screen.SETTINGS
            else -> Screen.HOME
        }
    }
    val openPaywall: () -> Unit = {
        paywallReturn = if (screen in setOf(Screen.HOME, Screen.MEDS, Screen.SCAM, Screen.READ)) Screen.HOME else Screen.SETTINGS
        screen = Screen.PAYWALL
    }

    LaunchedEffect(Unit) { MainActivity.homePressed.collect { screen = Screen.HOME } }
    var scamText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        MainActivity.sharedText.collect { t -> if (t != null) { scamText = t; MainActivity.sharedText.value = null; screen = Screen.SCAM } }
    }
    // On the home screen Back does nothing, so the senior can never "fall out" of the launcher.
    BackHandler(enabled = true) { if (screen != Screen.HOME) back() }

    when (screen) {
        Screen.HOME -> HomeScreen(
            settings = s,
            onOpen = { if ((it == Screen.MEDS || it == Screen.READ) && !s.premium) openPaywall() else screen = it },
        )
        Screen.APPS -> AppDrawerScreen(onBack = back)
        Screen.DIALER -> DialerScreen(onBack = back)
        Screen.CONTACTS -> ContactsScreen(onBack = back)
        Screen.MEDS -> TodayMedicinesScreen(onBack = back)
        Screen.SOS -> SosScreen(onBack = back)
        Screen.SCAM -> ScamCheckScreen(initialText = scamText, onBack = { scamText = null; back() }, onNeedPremium = openPaywall)
        Screen.READ -> ReadItScreen(onBack = back, onNeedPremium = openPaywall)
        Screen.PIN -> PinScreen(expected = s.pin, onBack = back, onSuccess = { screen = Screen.SETTINGS })
        Screen.SETTINGS -> SettingsScreen(
            settings = s,
            onBack = { screen = Screen.HOME },
            onOpen = { if (it == Screen.PAYWALL) openPaywall() else screen = it },
        )
        Screen.EDIT_CONTACTS -> EditContactsScreen(premium = s.premium, onBack = back, onNeedPremium = openPaywall)
        Screen.EDIT_MEDS -> if (s.premium) EditMedicinesScreen(onBack = back) else PaywallScreen(onBack = back)
        Screen.TILES -> TilesScreen(settings = s, onBack = back)
        // Pairing is free: the family member can buy the unlock from their own phone after linking.
        Screen.PAIRING -> PairingScreen(onBack = back)
        Screen.PAYWALL -> PaywallScreen(onBack = back)
        Screen.SUPPORT -> com.lusanetech.warmhearth.support.SupportScreen(onBack = back)
    }
}
