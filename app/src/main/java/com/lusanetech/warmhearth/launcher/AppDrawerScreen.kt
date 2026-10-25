package com.lusanetech.warmhearth.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.ui.Page
import com.lusanetech.warmhearth.ui.Palette

/** All apps as one long list with big icons and big names; no folders, no pages to swipe. */
@Composable
fun AppDrawerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    LaunchedEffect(Unit) { apps = Apps.load(context) }

    Page(stringResource(R.string.tile_apps), onBack = onBack) {
        val list = apps
        if (list == null) {
            CircularProgressIndicator(Modifier.size(64.dp).align(Alignment.CenterHorizontally))
            return@Page
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.packageName }) { app ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(role = Role.Button) { Apps.launchPackage(context, app.packageName) }
                        .padding(8.dp),
                ) {
                    Image(app.icon, contentDescription = null, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(app.label, style = MaterialTheme.typography.titleLarge, color = Palette.Ink)
                }
            }
        }
    }
}
