package dev.carlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/**
 * His head unit clears `enabled_accessibility_services` on every car start (seen 2026-11-06: null after each boot), which
 * turns our floating Back/Home off — and with the climate bar hidden he then has NO back key. So we switch it back on
 * ourselves: on boot and whenever our app runs. Needs WRITE_SECURE_SETTINGS, granted once over ADB:
 *   adb shell pm grant dev.carlauncher android.permission.WRITE_SECURE_SETTINGS
 */
object KeepAlive {
    private const val ME = "dev.carlauncher/dev.carlauncher.FloatService"
    fun ensureFloat(ctx: Context) = runCatching {
        val cr = ctx.contentResolver
        val cur = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        if (!cur.contains("dev.carlauncher/")) {
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, if (cur.isBlank()) ME else "$cur:$ME")
            Settings.Secure.putString(cr, Settings.Secure.ACCESSIBILITY_ENABLED, "1")
            Log.i("CarLauncher", "re-enabled floating Back/Home (was: '$cur')")
        }
    }.onFailure { Log.w("CarLauncher", "cannot re-enable floating Back/Home — grant WRITE_SECURE_SETTINGS over ADB", it) }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) { KeepAlive.ensureFloat(ctx) }
}
