package dev.carlauncher.barhelper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/**
 * Keeps the factory climate bar in the state the owner chose in Car Launcher's settings (default: hidden).
 * The wanted state is stored here, so boot can re-apply it even before the main app runs.
 */
class BarReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val prefs = ctx.getSharedPreferences("bar", Context.MODE_PRIVATE)
        if (i.action == ACTION && i.hasExtra("hide")) prefs.edit().putBoolean("hide", i.getBooleanExtra("hide", true)).apply()
        val want = if (prefs.getBoolean("hide", true)) 0 else 1
        if (i.action == ACTION) { apply(ctx, want); return }
        // On boot the vendor resets the key at an unknown moment, possibly after us — so re-check for ~50 s.
        // @TODO if the bar still comes back late, watch the key with a ContentObserver in a foreground service.
        val pending = goAsync()
        Thread {
            try { for (wait in longArrayOf(0, 5_000, 10_000, 15_000, 20_000)) { Thread.sleep(wait); apply(ctx, want) } }
            finally { pending.finish() }
        }.start()
    }

    private fun apply(ctx: Context, want: Int) = runCatching {
        val cr = ctx.contentResolver
        if (Settings.System.getInt(cr, KEY, 1) != want) {
            Settings.System.putInt(cr, KEY, want)
            Log.i(TAG, "set $KEY=$want")
        }
    }.onFailure { Log.w(TAG, "cannot set $KEY", it) }

    companion object {
        const val ACTION = "dev.carlauncher.barhelper.APPLY"
        private const val KEY = "show_navigationbar" // vendor key on his TS10S: 1 = climate bar shown
        private const val TAG = "BarHelper"
    }
}
