package com.lusanetech.warmhearth.support

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.lusanetech.warmhearth.BuildConfig
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.graph
import com.lusanetech.warmhearth.ui.BigButton
import com.lusanetech.warmhearth.ui.Page
import com.lusanetech.warmhearth.ui.Palette
import com.lusanetech.warmhearth.ui.Spacing
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.DateFormat
import java.util.Locale

data class Ticket(val id: String, val number: String, val subject: String, val status: String, val updatedAt: Long)
data class TicketMessage(val id: String, val from: String, val text: String, val createdAt: Long)

/** Tickets go through Cloud Functions (validated, rate-limited, emailed); reading is direct from Firestore. */
object SupportRepository {
    private val functions get() = FirebaseFunctions.getInstance("asia-south1")
    private val fs get() = FirebaseFirestore.getInstance()

    suspend fun submit(name: String, email: String, category: String, subject: String, message: String, deviceId: String?): String? =
        runCatching {
            // The phone model helps answer phone-specific problems (battery savers etc.); no personal data.
            val withPhone = "$message\n\n— ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}"
            val res = functions.getHttpsCallable("submitTicket").call(
                mapOf(
                    "name" to name, "email" to email, "category" to category, "subject" to subject, "message" to withPhone,
                    "source" to "app", "appVersion" to BuildConfig.VERSION_NAME, "deviceId" to deviceId,
                    "locale" to Locale.getDefault().toLanguageTag(),
                ),
            ).await()
            @Suppress("UNCHECKED_CAST")
            (res.getData() as? Map<String, Any?>)?.get("number") as? String
        }.getOrNull()

    suspend fun reply(ticketId: String, text: String): Boolean = runCatching {
        functions.getHttpsCallable("addTicketMessage").call(mapOf("ticketId" to ticketId, "text" to text)).await(); true
    }.getOrDefault(false)

    fun myTickets(): Flow<List<Ticket>> = callbackFlow {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) { trySend(emptyList()); close(); return@callbackFlow }
        val reg = fs.collection("tickets").whereEqualTo("uid", uid).orderBy("updatedAt", Query.Direction.DESCENDING).limit(30)
            .addSnapshotListener { s, _ ->
                trySend(s?.documents?.map {
                    Ticket(it.id, it.getString("number") ?: "", it.getString("subject") ?: "", it.getString("status") ?: "open",
                        it.getTimestamp("updatedAt")?.toDate()?.time ?: 0L)
                } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    fun messages(ticketId: String): Flow<List<TicketMessage>> = callbackFlow {
        val reg = fs.collection("tickets").document(ticketId).collection("messages").orderBy("createdAt")
            .addSnapshotListener { s, _ ->
                trySend(s?.documents?.map {
                    TicketMessage(it.id, it.getString("from") ?: "user", it.getString("text") ?: "", it.getTimestamp("createdAt")?.toDate()?.time ?: 0L)
                } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }
}

private val categories = listOf(
    "problem" to R.string.cat_problem,
    "question" to R.string.cat_question,
    "billing" to R.string.cat_billing,
    "refund" to R.string.cat_refund,
    "feature" to R.string.cat_feature,
)

/** Help & support: my tickets, a new message form, and each conversation. Used in both modes. */
@Composable
fun SupportScreen(onBack: () -> Unit) {
    var open by remember { mutableStateOf<Ticket?>(null) }
    var composing by remember { mutableStateOf(false) }
    when {
        open != null -> TicketThread(open!!, onBack = { open = null })
        composing -> NewTicket(onBack = { composing = false }, onSent = { composing = false })
        else -> TicketList(onBack = onBack, onOpen = { open = it }, onNew = { composing = true })
    }
}

@Composable
private fun TicketList(onBack: () -> Unit, onOpen: (Ticket) -> Unit, onNew: () -> Unit) {
    val tickets by remember { SupportRepository.myTickets() }.collectAsState(initial = emptyList())
    Page(stringResource(R.string.help_support), onBack = onBack) {
        Text(stringResource(R.string.help_intro), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(Spacing.l))
        BigButton(stringResource(R.string.new_message), icon = Icons.Filled.Add, onClick = onNew)
        Spacer(Modifier.height(Spacing.l))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            items(tickets, key = { it.id }) { t ->
                Column(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surface)
                        .clickable { onOpen(t) }.padding(Spacing.l),
                ) {
                    Text(t.subject, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${t.number} · " + stringResource(
                            when (t.status) { "waiting_on_user" -> R.string.status_replied; "closed" -> R.string.status_closed; else -> R.string.status_open },
                        ),
                        color = if (t.status == "waiting_on_user") Palette.Green else Palette.InkSoft,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewTicket(onBack: () -> Unit, onSent: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user = FirebaseAuth.getInstance().currentUser
    val settings by context.graph.prefs.settings.collectAsState(initial = null)
    var name by remember { mutableStateOf(user?.displayName ?: "") }
    var email by remember { mutableStateOf(user?.takeIf { !it.isAnonymous }?.email ?: "") }
    var category by remember { mutableStateOf("problem") }
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(settings) { if (name.isBlank()) settings?.seniorName?.let { name = it } }

    Page(stringResource(R.string.new_message), onBack = onBack) {
        if (result != null) {
            Text(stringResource(R.string.ticket_sent, result!!), style = MaterialTheme.typography.titleLarge, color = Palette.Green)
            Spacer(Modifier.height(Spacing.l))
            BigButton(stringResource(R.string.done), onClick = onSent)
            return@Page
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            item { OutlinedTextField(name, { name = it.take(80) }, label = { Text(stringResource(R.string.your_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item {
                OutlinedTextField(
                    email, { email = it.take(120) }, label = { Text(stringResource(R.string.email_for_reply)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Column {
                Text(stringResource(R.string.about), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    categories.forEach { (key, label) ->
                        FilterChip(selected = category == key, onClick = { category = key }, label = { Text(stringResource(label)) })
                    }
                }
                }
            }
            item { OutlinedTextField(subject, { subject = it.take(140) }, label = { Text(stringResource(R.string.subject)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(message, { message = it.take(5000) }, label = { Text(stringResource(R.string.message)) }, minLines = 5, modifier = Modifier.fillMaxWidth()) }
            item { if (failed) Text(stringResource(R.string.ticket_failed), color = Palette.Red) }
            item {
                val valid = name.isNotBlank() && email.contains("@") && message.trim().length >= 5
                BigButton(stringResource(R.string.send), icon = Icons.AutoMirrored.Filled.Send, enabled = valid && !busy) {
                    busy = true; failed = false
                    scope.launch {
                        val n = SupportRepository.submit(name.trim(), email.trim(), category, subject.trim().ifBlank { message.take(60) }, message.trim(), settings?.deviceId)
                        if (n != null) result = n else failed = true
                        busy = false
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketThread(ticket: Ticket, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val messages by remember(ticket.id) { SupportRepository.messages(ticket.id) }.collectAsState(initial = emptyList())
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    Page(ticket.subject, onBack = onBack) {
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            items(messages, key = { it.id }) { m ->
                val mine = m.from != "admin"
                Column(
                    Modifier.fillMaxWidth().padding(start = if (mine) Spacing.xxl else 0.dp, end = if (mine) 0.dp else Spacing.xxl)
                        .clip(MaterialTheme.shapes.medium)
                        .background(if (mine) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer)
                        .padding(Spacing.m),
                    horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
                ) {
                    Text(m.text, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        (if (mine) stringResource(R.string.you) else stringResource(R.string.support_team)) + " · " + fmt.format(m.createdAt),
                        style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft,
                    )
                }
            }
        }
        if (ticket.status != "closed") {
            OutlinedTextField(text, { text = it.take(5000) }, label = { Text(stringResource(R.string.reply)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Spacing.s))
            BigButton(stringResource(R.string.send), icon = Icons.AutoMirrored.Filled.Send, enabled = text.isNotBlank() && !busy) {
                busy = true
                scope.launch { if (SupportRepository.reply(ticket.id, text.trim())) text = ""; busy = false }
            }
        } else {
            Text(stringResource(R.string.ticket_closed_note), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

