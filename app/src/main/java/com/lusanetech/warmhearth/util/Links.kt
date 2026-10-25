package com.lusanetech.warmhearth.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.lusanetech.warmhearth.launcher.Apps

/** Links to the public website (privacy, terms, refunds, support). */
object Links {
    const val SITE = "https://warmhearth.lusanetech.com"
    fun open(context: Context, path: String) = Apps.start(context, Intent(Intent.ACTION_VIEW, Uri.parse(SITE + path)))
}
