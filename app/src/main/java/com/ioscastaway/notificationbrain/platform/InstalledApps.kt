package com.ioscastaway.notificationbrain.platform

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.ioscastaway.notificationbrain.brain.KnownApp

/**
 * Launcher apps by label, so the user can write a rule for an app before it has ever notified.
 * Needs the `<queries>` launcher entry in the manifest (package visibility, API 30+).
 */
object InstalledApps {
    fun launcherApps(context: Context): List<KnownApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { KnownApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }
            .filterNot { it.packageName == context.packageName }
    }
}
