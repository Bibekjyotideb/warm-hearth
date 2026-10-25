package com.lusanetech.warmhearth.sos

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.calls.CallHelper
import com.lusanetech.warmhearth.ui.BigButton
import com.lusanetech.warmhearth.ui.Page
import com.lusanetech.warmhearth.ui.Palette
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Hold the red circle for 3 seconds. Holding (not tapping) prevents pocket and accidental alarms. */
@Composable
fun SosScreen(onBack: () -> Unit) {
    var result by remember { mutableStateOf<SosResult?>(null) }
    var sending by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Page(stringResource(R.string.sos_title), onBack = onBack) {
        val r = result
        when {
            sending -> {
                Spacer(Modifier.height(48.dp))
                CircularProgressIndicator(Modifier.size(96.dp).align(Alignment.CenterHorizontally), color = Palette.Red)
                Text(stringResource(R.string.sos_sending), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            r == null -> HoldToSos {
                sending = true
                scope.launch {
                    result = Sos.trigger(context, reason = "button")
                    sending = false
                }
            }
            else -> SosResultView(r)
        }
    }
}

@Composable
private fun HoldToSos(onTriggered: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var job by remember { mutableStateOf<Job?>(null) }
    val sosLabel = stringResource(R.string.sos_title)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.sos_hold_instruction), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress.value },
                modifier = Modifier.size(280.dp),
                color = Palette.Red,
                strokeWidth = 14.dp,
            )
            Box(
                Modifier
                    .size(240.dp)
                    .clip(CircleShape)
                    .background(Palette.Red)
                    .semantics {
                        role = Role.Button
                        contentDescription = sosLabel
                        onClick(label = sosLabel) { onTriggered(); true }
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            job = scope.launch {
                                progress.snapTo(0f)
                                progress.animateTo(1f, tween(3000, easing = LinearEasing))
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onTriggered()
                            }
                            waitForUpOrCancellation()
                            if (progress.value < 1f) {
                                job?.cancel()
                                scope.launch { progress.animateTo(0f, tween(300)) }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("SOS", color = Color.White, style = MaterialTheme.typography.displayLarge)
            }
        }
    }
}

@Composable
private fun SosResultView(r: SosResult) {
    val context = LocalContext.current
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            stringResource(if (r.familyNotified) R.string.sos_done_family else R.string.sos_done_no_family),
            style = MaterialTheme.typography.headlineMedium,
        )
        r.contacts.firstOrNull()?.let { c ->
            BigButton(stringResource(R.string.call_name, c.name), color = Palette.Green, icon = Icons.Filled.Call) {
                CallHelper.call(context, c.phone)
            }
        }
        if (r.contacts.isNotEmpty()) {
            BigButton(stringResource(R.string.sos_send_sms), color = Palette.Brand, icon = Icons.Filled.Sms) { Sos.shareBySms(context, r) }
        }
        val number = Sos.emergencyNumber()
        BigButton(stringResource(R.string.call_emergency, number), color = Palette.Red, icon = Icons.Filled.LocalHospital) {
            CallHelper.call(context, number)
        }
    }
}
