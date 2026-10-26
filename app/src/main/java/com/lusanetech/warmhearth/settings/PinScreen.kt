package com.lusanetech.warmhearth.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.ui.Page
import com.lusanetech.warmhearth.ui.Palette

/** Keeps settings away from accidental taps. The family sets the PIN during setup. */
@Composable
fun PinScreen(expected: String, onBack: () -> Unit, onSuccess: () -> Unit, onPrivacy: (() -> Unit)? = null) {
    var entered by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")

    Page(stringResource(R.string.enter_pin), onBack = onBack) {
        Text(
            "•".repeat(entered.length).ifEmpty { " " },
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (wrong) Text(stringResource(R.string.wrong_pin), color = Palette.Red, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.pin_for_family), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                row.forEach { key ->
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 72.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(if (key.isEmpty()) Color.Transparent else MaterialTheme.colorScheme.surface)
                            .clickable(enabled = key.isNotEmpty(), role = Role.Button) {
                                wrong = false
                                when (key) {
                                    "⌫" -> entered = entered.dropLast(1)
                                    else -> {
                                        entered += key
                                        if (entered.length >= expected.length) {
                                            if (entered == expected) onSuccess() else { wrong = true; entered = "" }
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) { Text(key, style = MaterialTheme.typography.displayMedium) }
                }
            }
        }
        // Always reachable without the PIN: the senior can see and stop what family can see.
        if (onPrivacy != null) {
            androidx.compose.material3.TextButton(onClick = onPrivacy, modifier = Modifier.padding(top = 8.dp)) {
                Text(androidx.compose.ui.res.stringResource(R.string.privacy_sharing_no_pin), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
