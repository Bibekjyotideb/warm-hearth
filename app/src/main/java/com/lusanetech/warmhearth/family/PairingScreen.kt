package com.lusanetech.warmhearth.family

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lusanetech.warmhearth.R
import kotlinx.coroutines.launch
import com.lusanetech.warmhearth.graph
import com.lusanetech.warmhearth.ui.BigButton
import com.lusanetech.warmhearth.ui.Page
import com.lusanetech.warmhearth.ui.Palette

/** Senior side: shows a code to type into the family member's phone. */
@Composable
fun PairingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by context.graph.prefs.settings.collectAsState(initial = null)
    var code by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }

    val consented = (settings?.sharingConsentAt ?: 0L) > 0L
    LaunchedEffect(attempt, consented) {
        if (!consented) return@LaunchedEffect
        failed = false
        code = null
        context.graph.family.startSeniorSync()
        code = context.graph.family.createPairCode()
        failed = code == null
    }

    Page(stringResource(R.string.link_family), onBack = onBack) {
        if (settings == null) return@Page
        // Consent first: what linked family will see and be able to do (Play User Data policy, DPDP Act).
        if (!consented) {
            com.lusanetech.warmhearth.settings.SharingConsent(onAgree = {
                context.graph.scope.launch { context.graph.prefs.setSharingConsent(System.currentTimeMillis()) }
            })
            return@Page
        }
        if (settings?.paired == true) {
            Text(stringResource(R.string.family_linked_body), color = Palette.Green, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
        }
        Text(stringResource(R.string.pair_instructions), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(24.dp))
        when {
            failed -> Text(stringResource(R.string.no_internet), color = Palette.Red, style = MaterialTheme.typography.titleLarge)
            code == null -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            else -> Text(
                code!!.chunked(4).joinToString(" "),
                style = MaterialTheme.typography.displayMedium,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(24.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.pair_code_expiry), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        BigButton(stringResource(R.string.new_code), icon = Icons.Filled.Refresh, color = Palette.Grey) { attempt++ }
    }
}
