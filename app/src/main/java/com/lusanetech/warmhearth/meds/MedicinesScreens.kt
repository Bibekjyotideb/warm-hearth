package com.lusanetech.warmhearth.meds

import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import com.lusanetech.warmhearth.ai.AiHelper
import com.lusanetech.warmhearth.ai.rememberTakePhoto
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.data.DoseLog
import com.lusanetech.warmhearth.data.Medicine
import com.lusanetech.warmhearth.graph
import com.lusanetech.warmhearth.ui.BigButton
import com.lusanetech.warmhearth.ui.Page
import com.lusanetech.warmhearth.ui.Palette
import com.lusanetech.warmhearth.util.PhotoStore
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Senior view: today's doses in time order, each with a big "I took it" button. */
@Composable
fun TodayMedicinesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = MedicineScheduler.today()
    val meds by context.graph.db.medicines().observeAll().collectAsState(initial = emptyList())
    val logs by context.graph.db.doseLogs().observeForDay(today).collectAsState(initial = emptyList())
    val now = SimpleDateFormat("HH:mm", Locale.US).format(Date())

    val doses = meds.filter { it.active }
        .flatMap { m -> m.timeList().map { t -> m to t } }
        .sortedBy { it.second }

    Page(stringResource(R.string.tile_meds), onBack = onBack) {
        if (doses.isEmpty()) {
            Text(stringResource(R.string.meds_empty), style = MaterialTheme.typography.titleLarge)
            return@Page
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(doses, key = { "${it.first.id}|${it.second}" }) { (med, time) ->
                val slot = "$today $time"
                val taken = logs.any { it.medicineId == med.id && it.slot == slot }
                val late = !taken && time < now
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MedPhoto(med, 80)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(time, style = MaterialTheme.typography.headlineMedium)
                            Text(med.name, style = MaterialTheme.typography.titleLarge)
                            if (med.dose.isNotBlank()) Text(med.dose, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (taken) {
                        Text("✓ " + stringResource(R.string.taken), color = Palette.Green, style = MaterialTheme.typography.titleLarge)
                    } else {
                        BigButton(
                            stringResource(R.string.i_took_it),
                            color = if (late) Palette.Orange else Palette.Green,
                            icon = Icons.Filled.Check,
                        ) {
                            scope.launch {
                                context.graph.db.doseLogs().insert(DoseLog(medicineId = med.id, slot = slot, takenAt = System.currentTimeMillis()))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MedPhoto(med: Medicine, sizeDp: Int) {
    val path = med.photoPath?.takeIf { File(it).exists() }
    val mod = Modifier.size(sizeDp.dp).clip(MaterialTheme.shapes.medium)
    if (path != null) {
        AsyncImage(model = File(path), contentDescription = null, contentScale = ContentScale.Crop, modifier = mod)
    } else {
        Column(mod.background(Palette.Orange), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Medication, contentDescription = null, tint = Color.White, modifier = Modifier.size((sizeDp * 0.6f).dp))
        }
    }
}

/** Family / settings view: add, edit and delete medicines. */
@Composable
fun EditMedicinesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val meds by context.graph.db.medicines().observeAll().collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<Medicine?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var scanFailed by remember { mutableStateOf(false) }

    // AI: photograph the medicine box, the name and dose are filled in for you to check.
    val scan = rememberTakePhoto { bmp ->
        scanning = true; scanFailed = false
        scope.launch {
            val id = UUID.randomUUID().toString()
            val photo = PhotoStore.saveBitmap(context, bmp, id)
            when (val r = AiHelper.readMedicine(context, bmp)) {
                is AiHelper.Result.Ok -> editing = Medicine(id = id, name = r.value.name, dose = r.value.dose, photoPath = photo, times = "08:00")
                else -> { scanFailed = true; editing = Medicine(id = id, name = "", photoPath = photo, times = "08:00") }
            }
            scanning = false
        }
    }

    Page(stringResource(R.string.edit_medicines), onBack = onBack) {
        BigButton(stringResource(R.string.scan_medicine), icon = Icons.Filled.DocumentScanner, color = Palette.Purple, enabled = !scanning) { scan() }
        if (scanning) Text(stringResource(R.string.ai_thinking), style = MaterialTheme.typography.titleMedium)
        if (scanFailed) Text(stringResource(R.string.scan_failed), color = Palette.Red)
        Spacer(Modifier.height(8.dp))
        BigButton(stringResource(R.string.add_medicine), icon = Icons.Filled.Add, color = Palette.Orange) {
            editing = Medicine(id = UUID.randomUUID().toString(), name = "", times = "08:00")
        }
        Spacer(Modifier.height(12.dp))
        MedicineList(meds, onEdit = { editing = it }, onDelete = { m -> scope.launch { context.graph.family.deleteMedicine(m) } })
    }

    editing?.let { m ->
        MedicineEditorDialog(m, onDismiss = { editing = null }) { saved ->
            scope.launch { context.graph.family.saveMedicine(saved) }
            editing = null
        }
    }
}

@Composable
fun MedicineList(meds: List<Medicine>, onEdit: (Medicine) -> Unit, onDelete: (Medicine) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(meds, key = { it.id }) { m ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onEdit(m) }
                    .padding(12.dp),
            ) {
                MedPhoto(m, 56)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.name, style = MaterialTheme.typography.titleLarge)
                    Text(m.timeList().joinToString("  ·  "), style = MaterialTheme.typography.bodyLarge)
                }
                IconButton(onClick = { onDelete(m) }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete), tint = Palette.Red)
                }
            }
        }
    }
}

@Composable
fun MedicineEditorDialog(
    initial: Medicine,
    allowPhoto: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (Medicine) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial.name) }
    var dose by remember { mutableStateOf(initial.dose) }
    var photo by remember { mutableStateOf(initial.photoPath) }
    val times = remember { mutableStateListOf<String>().apply { addAll(initial.timeList()) } }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { PhotoStore.save(context, uri, initial.id)?.let { photo = it } }
    }
    fun pickTime(onPicked: (String) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(String.format(Locale.US, "%02d:%02d", h, m)) }, 8, 0, true).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.medicine)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (allowPhoto) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MedPhoto(initial.copy(photoPath = photo), 64)
                        TextButton(onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                            Icon(Icons.Filled.Photo, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.photo_of_pill))
                        }
                    }
                }
                OutlinedTextField(name, { name = it.take(40) }, label = { Text(stringResource(R.string.medicine_name)) }, singleLine = true)
                OutlinedTextField(dose, { dose = it.take(40) }, label = { Text(stringResource(R.string.dose_hint)) }, singleLine = true)
                Text(stringResource(R.string.times_per_day), style = MaterialTheme.typography.titleMedium)
                times.forEachIndexed { i, t ->
                    AssistChip(
                        onClick = { pickTime { picked -> times[i] = picked } },
                        label = { Text(t, style = MaterialTheme.typography.titleMedium) },
                        trailingIcon = {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.delete), modifier = Modifier.clickable { times.removeAt(i) })
                        },
                    )
                }
                TextButton(onClick = { pickTime { if (it !in times) times += it } }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(R.string.add_time))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && times.isNotEmpty(),
                onClick = {
                    onSave(initial.copy(name = name.trim(), dose = dose.trim(), photoPath = photo, times = times.sorted().joinToString(","), updatedAt = System.currentTimeMillis()))
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
