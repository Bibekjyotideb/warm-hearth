package com.lusanetech.warmhearth.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * Opens the phone's own camera app (no CAMERA permission needed) and returns a full-resolution,
 * correctly rotated, downscaled photo.
 */
@Composable
fun rememberTakePhoto(onPhoto: (Bitmap) -> Unit): () -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    val target = remember { photoUri(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) loadBitmap(context, target)?.let { onPhoto(AiHelper.downscale(it)) }
    }
    return { launcher.launch(target) }
}

private fun photoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "capture.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}

private fun loadBitmap(context: Context, uri: Uri): Bitmap? = runCatching {
    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
    val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
    val rotation = context.contentResolver.openInputStream(uri)?.use {
        when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } ?: 0f
    if (rotation == 0f) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)
}.getOrNull()
