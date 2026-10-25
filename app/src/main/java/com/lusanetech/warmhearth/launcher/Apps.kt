package com.lusanetech.warmhearth.launcher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.lusanetech.warmhearth.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(val label: String, val packageName: String, val icon: ImageBitmap)

object Apps {
    const val WHATSAPP = "com.whatsapp"

    /** Every app that has a launcher icon, except ourselves, sorted by name. */
    suspend fun load(context: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map {
                InstalledApp(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    icon = it.loadIcon(pm).toBitmap(144, 144).asImageBitmap(),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun label(context: Context, packageName: String): String? = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()

    fun isInstalled(context: Context, packageName: String) =
        context.packageManager.getLaunchIntentForPackage(packageName) != null

    fun launchPackage(context: Context, packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) start(context, intent)
        else start(context, Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
    }

    fun openMessages(context: Context) =
        start(context, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING))

    fun openGallery(context: Context) =
        start(context, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY))

    fun openCamera(context: Context) =
        start(context, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))

    fun start(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.app_not_found, Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, R.string.app_not_found, Toast.LENGTH_LONG).show()
        }
    }
}
