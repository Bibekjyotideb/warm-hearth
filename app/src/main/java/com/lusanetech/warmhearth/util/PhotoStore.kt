package com.lusanetech.warmhearth.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Photos (people, pills) are copied into private storage right away, because URIs from the
 * photo picker or contacts app stop working after the app restarts. They are downscaled to
 * 512px so a hundred photos stay small.
 */
object PhotoStore {
    suspend fun save(context: Context, uri: Uri, id: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
            val scale = 512f / maxOf(bmp.width, bmp.height)
            val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
            val dir = File(context.filesDir, "photos").apply { mkdirs() }
            val file = File(dir, "$id-${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            file.absolutePath
        }.getOrNull()
    }

    suspend fun saveBitmap(context: Context, bitmap: Bitmap, id: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "photos").apply { mkdirs() }
            val file = File(dir, "$id-${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            file.absolutePath
        }.getOrNull()
    }

    fun delete(path: String?) {
        if (path != null) runCatching { File(path).delete() }
    }
}
