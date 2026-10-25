package com.lusanetech.warmhearth.calls

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.ui.BigButton
import com.lusanetech.warmhearth.ui.Page
import com.lusanetech.warmhearth.ui.Palette

@Composable
fun DialerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var number by remember { mutableStateOf("") }
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")

    Page(stringResource(R.string.tile_phone), onBack = onBack) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(80.dp)) {
            Text(
                number.ifEmpty { " " },
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
                modifier = Modifier.weight(1f),
            )
            if (number.isNotEmpty()) {
                Box(
                    Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.delete)) { number = number.dropLast(1) }
                        .padding(16.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = stringResource(R.string.delete), modifier = Modifier.height(40.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                row.forEach { key ->
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 76.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable(role = Role.Button) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (number.length < 20) number += key
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(key, style = MaterialTheme.typography.displayMedium, color = Palette.Ink)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        BigButton(stringResource(R.string.call_now), color = Palette.Green, icon = Icons.Filled.Call, enabled = number.isNotEmpty()) {
            CallHelper.call(context, number)
        }
    }
}
