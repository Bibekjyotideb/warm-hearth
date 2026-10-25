package com.lusanetech.warmhearth.calls

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.data.FavoriteContact
import com.lusanetech.warmhearth.launcher.Apps
import com.lusanetech.warmhearth.ui.BigButton
import com.lusanetech.warmhearth.ui.Palette
import com.lusanetech.warmhearth.ui.PersonAvatar

object CallHelper {
    /** Places the call directly when allowed; otherwise opens the dialer with the number filled in. */
    fun call(context: Context, number: String) {
        val clean = number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (clean.isBlank()) return
        val uri = Uri.parse("tel:" + Uri.encode(clean))
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        Apps.start(context, Intent(if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri))
    }
}

/** "Call Asha?" with a huge photo and two huge buttons, so a stray tap never starts a call. */
@Composable
fun CallConfirmDialog(contact: FavoriteContact, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = null,
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                PersonAvatar(contact.name, contact.photoPath, 140.dp)
                Text(
                    stringResource(R.string.call_question, contact.name),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                BigButton(stringResource(R.string.call_yes), color = Palette.Green, icon = Icons.Filled.Call) {
                    CallHelper.call(context, contact.phone)
                    onDismiss()
                }
                BigButton(stringResource(R.string.call_no), color = Palette.Grey, icon = Icons.Filled.Close, onClick = onDismiss)
            }
        },
    )
}
