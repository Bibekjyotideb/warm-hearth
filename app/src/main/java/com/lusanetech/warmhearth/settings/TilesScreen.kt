package com.lusanetech.warmhearth.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lusanetech.warmhearth.R
import com.lusanetech.warmhearth.data.Settings
import com.lusanetech.warmhearth.data.Tiles
import com.lusanetech.warmhearth.graph
import com.lusanetech.warmhearth.launcher.Apps
import com.lusanetech.warmhearth.launcher.InstalledApp
import com.lusanetech.warmhearth.ui.Page
import kotlinx.coroutines.launch

/** Choose which big tiles appear on the home screen, including any installed app. */
@Composable
fun TilesScreen(settings: Settings, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    LaunchedEffect(Unit) { apps = Apps.load(context) }

    val builtIn = listOf(
        Tiles.PHONE to R.string.tile_phone,
        Tiles.CONTACTS to R.string.tile_contacts,
        Tiles.MESSAGES to R.string.tile_messages,
        Tiles.CAMERA to R.string.tile_camera,
        Tiles.PHOTOS to R.string.tile_photos,
        Tiles.WHATSAPP to R.string.tile_whatsapp,
        Tiles.MEDS to R.string.tile_meds,
        Tiles.SCAM to R.string.tile_scam,
        Tiles.READ to R.string.tile_read,
        Tiles.APPS to R.string.tile_apps,
    )
    fun toggle(key: String, on: Boolean) = scope.launch {
        val next = if (on) settings.tiles + key else settings.tiles - key
        context.graph.prefs.setTiles(next.distinct())
    }

    Page(stringResource(R.string.home_tiles), onBack = onBack) {
        LazyColumn {
            items(builtIn, key = { it.first }) { (key, label) ->
                CheckRow(stringResource(label), key in settings.tiles) { toggle(key, it) }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)); Text(stringResource(R.string.add_app_tiles), style = MaterialTheme.typography.titleLarge) }
            items(apps, key = { it.packageName }) { app ->
                val key = "pkg:${app.packageName}"
                CheckRow(app.label, key in settings.tiles, icon = { Image(app.icon, null, Modifier.size(40.dp)) }) { toggle(key, it) }
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, icon: (@Composable () -> Unit)? = null, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        if (icon != null) { icon(); Spacer(Modifier.width(8.dp)) }
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}
