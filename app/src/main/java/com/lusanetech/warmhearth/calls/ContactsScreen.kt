package com.lusanetech.warmhearth.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.data.FavoriteContact
import com.lusanetech.warmhearth.graph
import com.lusanetech.warmhearth.ui.Page
import com.lusanetech.warmhearth.ui.PersonAvatar

/** The senior's people, as big faces. Editing happens in Settings (behind the PIN) or from the family phone. */
@Composable
fun ContactsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val contacts by context.graph.db.contacts().observeAll().collectAsState(initial = emptyList())
    var calling by remember { mutableStateOf<FavoriteContact?>(null) }

    Page(stringResource(R.string.tile_contacts), onBack = onBack) {
        if (contacts.isEmpty()) {
            Text(stringResource(R.string.contacts_empty), style = MaterialTheme.typography.titleLarge)
            return@Page
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(contacts, key = { it.id }) { c ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(role = Role.Button, onClickLabel = c.name) { calling = c }
                        .padding(12.dp),
                ) {
                    PersonAvatar(c.name, c.photoPath, 120.dp)
                    Text(
                        c.name,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    calling?.let { CallConfirmDialog(it) { calling = null } }
}
