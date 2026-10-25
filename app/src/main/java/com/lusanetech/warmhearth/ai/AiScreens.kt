package com.lusanetech.warmhearth.ai

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.ui.BigButton
import com.lusanetech.warmhearth.ui.Page
import com.lusanetech.warmhearth.ui.Palette
import com.lusanetech.warmhearth.ui.Spacing
import kotlinx.coroutines.launch

/** Set this to your support address: Google Play requires a way to report bad AI answers. */
const val SUPPORT_EMAIL = "support@example.com"

/** "Is this a scam?" Paste or share a message; get a red / orange / green answer read aloud. */
@Composable
fun ScamCheckScreen(initialText: String?, onBack: () -> Unit, onNeedPremium: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val speaker = rememberSpeaker()
    var text by remember { mutableStateOf(initialText.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var verdict by remember { mutableStateOf<AiHelper.ScamVerdict?>(null) }
    var failed by remember { mutableStateOf(false) }

    fun check() {
        if (text.isBlank() || busy) return
        busy = true; failed = false; verdict = null
        scope.launch {
            when (val r = AiHelper.checkScam(context, text)) {
                is AiHelper.Result.Ok -> {
                    verdict = r.value
                    speaker.speak(r.value.explanation + " " + r.value.advice)
                }
                AiHelper.Result.LimitReached -> onNeedPremium()
                AiHelper.Result.Failed -> failed = true
            }
            busy = false
        }
    }
    // Shared from WhatsApp/SMS: check straight away.
    LaunchedEffect(initialText) { if (!initialText.isNullOrBlank()) check() }

    Page(stringResource(R.string.tile_scam), onBack = onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text(stringResource(R.string.scam_intro), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(3000) },
                placeholder = { Text(stringResource(R.string.scam_placeholder)) },
                textStyle = MaterialTheme.typography.bodyLarge,
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            BigButton(stringResource(R.string.paste_message), icon = Icons.Filled.ContentPaste, color = Palette.Grey) {
                val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                clip?.getItemAt(0)?.coerceToText(context)?.toString()?.let { text = it }
            }
            BigButton(stringResource(R.string.check_message), icon = Icons.Filled.Shield, color = Palette.Blue, enabled = text.isNotBlank() && !busy) { check() }

            when {
                busy -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(40.dp))
                    Spacer(Modifier.width(Spacing.m))
                    Text(stringResource(R.string.ai_thinking), style = MaterialTheme.typography.titleMedium)
                }
                failed -> Text(stringResource(R.string.ai_failed), color = Palette.Red, style = MaterialTheme.typography.titleMedium)
                verdict != null -> VerdictCard(verdict!!, onSpeak = { speaker.speak(verdict!!.explanation + " " + verdict!!.advice) })
            }
            Text(stringResource(R.string.scam_tip_share), style = MaterialTheme.typography.bodyMedium, color = Palette.InkSoft)
        }
    }
}

@Composable
private fun VerdictCard(v: AiHelper.ScamVerdict, onSpeak: () -> Unit) {
    val (bg, icon, title) = when (v.risk) {
        AiHelper.Risk.SCAM -> Triple(Palette.Red, Icons.Filled.Report, stringResource(R.string.verdict_scam))
        AiHelper.Risk.SUSPICIOUS -> Triple(Palette.Orange, Icons.Filled.GppMaybe, stringResource(R.string.verdict_suspicious))
        AiHelper.Risk.SAFE -> Triple(Palette.Green, Icons.Filled.GppGood, stringResource(R.string.verdict_safe))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(bg)
            .padding(Spacing.l)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
            Spacer(Modifier.width(Spacing.m))
            Text(title, color = Color.White, style = MaterialTheme.typography.headlineMedium)
        }
        Text(v.explanation, color = Color.White, style = MaterialTheme.typography.titleMedium)
        Text(v.advice, color = Color.White, style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onSpeak) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(Spacing.s))
            Text(stringResource(R.string.read_aloud), color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
    ReportAnswerLink()
}

/** "Read it to me": photograph a letter or bill, hear a simple explanation. */
@Composable
fun ReadItScreen(onBack: () -> Unit, onNeedPremium: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val speaker = rememberSpeaker()
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var answer by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val takePhoto = rememberTakePhoto { bmp ->
        photo = bmp; answer = null; failed = false; busy = true
        scope.launch {
            when (val r = AiHelper.explainDocument(context, bmp)) {
                is AiHelper.Result.Ok -> { answer = r.value.summary; speaker.speak(r.value.summary) }
                AiHelper.Result.LimitReached -> onNeedPremium()
                AiHelper.Result.Failed -> failed = true
            }
            busy = false
        }
    }

    Page(stringResource(R.string.tile_read), onBack = { speaker.stop(); onBack() }) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text(stringResource(R.string.read_intro), style = MaterialTheme.typography.titleMedium)
            BigButton(stringResource(R.string.take_photo), icon = Icons.Filled.CameraAlt, color = Palette.Purple, enabled = !busy) { takePhoto() }
            photo?.let {
                Image(
                    it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(MaterialTheme.shapes.large),
                )
            }
            when {
                busy -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(40.dp))
                    Spacer(Modifier.width(Spacing.m))
                    Text(stringResource(R.string.ai_thinking), style = MaterialTheme.typography.titleMedium)
                }
                failed -> Text(stringResource(R.string.ai_failed), color = Palette.Red, style = MaterialTheme.typography.titleMedium)
                answer != null -> {
                    Text(
                        answer!!,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(Spacing.l)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    BigButton(stringResource(R.string.read_aloud), icon = Icons.AutoMirrored.Filled.VolumeUp, color = Palette.Green) {
                        speaker.speak(answer!!)
                    }
                    ReportAnswerLink()
                }
            }
            Text(stringResource(R.string.ai_disclaimer), style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
        }
    }
}

/** Google Play's AI-content policy asks for an in-app way to flag a bad answer. */
@Composable
fun ReportAnswerLink() {
    val context = LocalContext.current
    TextButton(onClick = { reportAnswer(context) }) {
        Text(stringResource(R.string.report_answer), color = Palette.InkSoft)
    }
}

private fun reportAnswer(context: Context) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT_EMAIL"))
        .putExtra(Intent.EXTRA_SUBJECT, "WarmHearth: problem with an AI answer")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
