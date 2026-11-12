package com.spendplan.app

import android.content.Context

/** Reads the version from the package manager (avoids enabling BuildConfig generation). */
object BuildConfigInfo {
    fun versionName(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
}
