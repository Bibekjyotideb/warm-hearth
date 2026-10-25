package com.lusanetech.warmhearth.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Elderly
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.data.AppMode
import com.lusanetech.warmhearth.graph
import com.lusanetech.warmhearth.ui.BigButton
import com.lusanetech.warmhearth.ui.Page
import com.lusanetech.warmhearth.ui.Palette
import com.lusanetech.warmhearth.util.HomeRole
import kotlinx.coroutines.launch

/**
 * First run. Step 0: who is this phone for? Senior path: name, PIN, permissions, set as home.
 * Family path: switches straight to family mode.
 */
@Composable
fun OnboardingScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }

    val permissions = buildList {
        add(Manifest.permission.CALL_PHONE)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    val homeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        scope.launch { context.graph.prefs.setMode(AppMode.SENIOR); context.graph.start() }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        step = 3
    }

    when (step) {
        0 -> Page(stringResource(R.string.welcome_title), onBack = null) {
            Text(stringResource(R.string.welcome_question), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(24.dp))
            BigButton(stringResource(R.string.mode_senior), icon = Icons.Filled.Elderly, color = Palette.Brand) { step = 1 }
            Spacer(Modifier.height(16.dp))
            BigButton(stringResource(R.string.mode_family), icon = Icons.Filled.FamilyRestroom, color = Palette.Green) {
                scope.launch { context.graph.prefs.setMode(AppMode.FAMILY); context.graph.start() }
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.mode_family_hint), style = MaterialTheme.typography.bodyLarge)
        }

        1 -> Page(stringResource(R.string.setup_title), onBack = { step = 0 }) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.setup_helper_note), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(R.string.senior_name_label)) },
                    textStyle = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = pin, onValueChange = { v -> pin = v.filter(Char::isDigit).take(6) },
                    label = { Text(stringResource(R.string.pin_label)) },
                    supportingText = { Text(stringResource(R.string.pin_hint)) },
                    textStyle = MaterialTheme.typography.titleLarge,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(24.dp))
                BigButton(stringResource(R.string.next), enabled = pin.length >= 4) {
                    scope.launch {
                        context.graph.prefs.setSeniorName(name.trim())
                        context.graph.prefs.setPin(pin)
                        step = 2
                    }
                }
            }
        }

        2 -> Page(stringResource(R.string.permissions_title), onBack = { step = 1 }) {
            Text(stringResource(R.string.permissions_body), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(24.dp))
            BigButton(stringResource(R.string.allow)) { permLauncher.launch(permissions) }
        }

        else -> Page(stringResource(R.string.set_home_title), onBack = { step = 2 }) {
            Text(stringResource(R.string.set_home_body), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(24.dp))
            BigButton(stringResource(R.string.set_home_button), icon = Icons.Filled.Home) {
                if (HomeRole.isDefault(context)) {
                    scope.launch { context.graph.prefs.setMode(AppMode.SENIOR); context.graph.start() }
                } else {
                    homeLauncher.launch(HomeRole.requestIntent(context))
                }
            }
        }
    }
}
