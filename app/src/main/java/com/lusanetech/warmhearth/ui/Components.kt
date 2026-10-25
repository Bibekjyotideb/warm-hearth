package com.lusanetech.warmhearth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lusanetech.warmhearth.R
import java.io.File

/** A large coloured square with an icon and a label underneath it. */
@Composable
fun BigTile(
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(color)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
        Spacer(Modifier.size(8.dp))
        Text(
            label,
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Full-width button at least 72dp tall: comfortably tappable with shaky hands. */
@Composable
fun BigButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Palette.Brand,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 72.dp),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(text, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    }
}

/** Round photo of a person, or their initials on a coloured circle when there is no photo. */
@Composable
fun PersonAvatar(name: String, photoPath: String?, size: Dp, modifier: Modifier = Modifier) {
    val base = modifier.size(size).clip(CircleShape)
    if (photoPath != null && File(photoPath).exists()) {
        AsyncImage(model = File(photoPath), contentDescription = name, contentScale = ContentScale.Crop, modifier = base)
    } else {
        val colors = listOf(Palette.Brand, Palette.Green, Palette.Purple, Palette.Teal, Palette.Orange, Palette.Pink)
        val bg = colors[(name.hashCode() and 0x7fffffff) % colors.size]
        Box(base.background(bg), contentAlignment = Alignment.Center) {
            val initials = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
            Text(initials.ifBlank { "?" }, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineLarge)
        }
    }
}

/** Standard page: big back button + title, then content. */
@Composable
fun Page(title: String, onBack: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Button(
                    onClick = onBack,
                    modifier = Modifier.heightIn(min = 64.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Grey),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.back), style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.width(16.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        }
        Spacer(Modifier.size(16.dp))
        content()
    }
}
