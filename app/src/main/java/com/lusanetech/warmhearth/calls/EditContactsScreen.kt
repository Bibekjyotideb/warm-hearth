package com.lusanetech.warmhearth.calls

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.data.FavoriteContact
import com.lusanetech.warmhearth.graph
import com.lusanetech.warmhearth.ui.BigButton
import com.lusanetech.warmhearth.ui.Page
import com.lusanetech.warmhearth.ui.Palette
import com.lusanetech.warmhearth.ui.PersonAvatar
import com.lusanetech.warmhearth.util.PhotoStore
import kotlinx.coroutines.launch
import java.util.UUID

const val FREE_CONTACT_LIMIT = 4

@Composable
fun EditContactsScreen(premium: Boolean, onBack: () -> Unit, onNeedPremium: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val contacts by context.graph.db.contacts().observeAll().collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<FavoriteContact?>(null) }

    val canAdd = premium || contacts.size < FREE_CONTACT_LIMIT

    // Picking from the phone's address book needs no READ_CONTACTS permission:
    // the system grants temporary access to just the chosen entry.
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        scope.launch {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            )
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val id = UUID.randomUUID().toString()
                    val photo = c.getString(2)?.let { PhotoStore.save(context, Uri.parse(it), id) }
                    editing = FavoriteContact(
                        id = id, name = c.getString(0) ?: "", phone = c.getString(1) ?: "",
                        photoPath = photo, position = contacts.size, isEmergency = contacts.none { it.isEmergency },
                    )
                }
            }
        }
    }

    Page(stringResource(R.string.edit_contacts), onBack = onBack) {
        BigButton(stringResource(R.string.add_from_phonebook), icon = Icons.Filled.Contacts) {
            if (!canAdd) onNeedPremium()
            else pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
        }
        Spacer(Modifier.height(8.dp))
        BigButton(stringResource(R.string.add_new_person), icon = Icons.Filled.Add, color = Palette.Teal) {
            if (!canAdd) onNeedPremium()
            else editing = FavoriteContact(
                id = UUID.randomUUID().toString(), name = "", phone = "",
                position = contacts.size, isEmergency = contacts.none { it.isEmergency },
            )
        }
        if (!premium) {
            Text(
                stringResource(R.string.free_contact_limit, FREE_CONTACT_LIMIT),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(contacts, key = { it.id }) { c ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { editing = c }
                        .padding(12.dp),
                ) {
                    PersonAvatar(c.name, c.photoPath, 64.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.titleLarge)
                        Text(c.phone, style = MaterialTheme.typography.bodyLarge)
                        if (c.isEmergency) Text(stringResource(R.string.emergency_contact), color = Palette.Red, style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = {
                        scope.launch { context.graph.family.deleteContact(c) }
                    }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete), tint = Palette.Red) }
                }
            }
        }
    }

    editing?.let { c ->
        ContactEditorDialog(
            initial = c,
            onDismiss = { editing = null },
            onSave = { saved ->
                scope.launch { context.graph.family.saveContact(saved) }
                editing = null
            },
        )
    }
}

/** Name, number, photo and "emergency contact" in one dialog. Shared with the family-side editor. */
@Composable
fun ContactEditorDialog(
    initial: FavoriteContact,
    allowPhoto: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (FavoriteContact) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial.name) }
    var phone by remember { mutableStateOf(initial.phone) }
    var emergency by remember { mutableStateOf(initial.isEmergency) }
    var onHome by remember { mutableStateOf(initial.onHome) }
    var photo by remember { mutableStateOf(initial.photoPath) }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { PhotoStore.save(context, uri, initial.id)?.let { photo = it } }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.person)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (allowPhoto) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PersonAvatar(name.ifBlank { "?" }, photo, 72.dp)
                        Spacer(Modifier.width(12.dp))
                        TextButton(onClick = {
                            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) {
                            Icon(Icons.Filled.Photo, contentDescription = null, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.choose_photo))
                        }
                    }
                }
                OutlinedTextField(name, { name = it.take(40) }, label = { Text(stringResource(R.string.name)) }, singleLine = true)
                OutlinedTextField(
                    phone, { phone = it.take(20) }, label = { Text(stringResource(R.string.phone_number)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = emergency, onCheckedChange = { emergency = it })
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.emergency_contact_toggle))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = onHome, onCheckedChange = { onHome = it })
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.show_on_home))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && phone.any(Char::isDigit),
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(), phone = phone.trim(), isEmergency = emergency, onHome = onHome, photoPath = photo,
                            // A photo picked on this phone replaces (and un-links) one the family uploaded.
                            photoUrl = if (photo != initial.photoPath) null else initial.photoUrl,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
