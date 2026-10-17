package dev.yashasvm.mobie.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log

/** Opens only an exported system screen. Some OEMs expose Recents or memory settings as actions. */
internal object MemoryReliefNavigator {
    val actions = listOf(
        "android.intent.action.SHOW_RECENTS",
        "android.settings.MEMORY_SETTINGS",
        Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS,
        Settings.ACTION_APPLICATION_SETTINGS,
        Settings.ACTION_SETTINGS,
    )

    fun open(context: Context): Boolean {
        for (action in actions) {
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val activity = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo ?: continue
            if (!activity.exported || activity.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0) continue
            if (activity.permission != null && context.checkSelfPermission(activity.permission) != PackageManager.PERMISSION_GRANTED) continue
            // Resolve explicitly to the inspected system handler, rather than a third-party activity.
            intent.setClassName(activity.packageName, activity.name)
            try {
                context.startActivity(intent)
                return true
            } catch (error: ActivityNotFoundException) {
                Log.w("MemoryRelief", "System screen disappeared for $action", error)
            } catch (error: SecurityException) {
                Log.w("MemoryRelief", "System screen denied $action", error)
            }
        }
        return false
    }
}
