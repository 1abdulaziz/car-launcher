package dev.carlauncher

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Rect
import android.util.Log

/**
 * Runs a REAL app live inside a launcher panel — the factory launcher's technique (measured on his unit: its Google Maps task
 * is `mode=freeform` with Bounds = the panel rect). We start the app as a freeform window sized to the panel.
 *
 * - setLaunchBounds is public API. setLaunchWindowingMode(5 = FREEFORM) is hidden; we call it by reflection. On his unit
 *   that needs `adb shell settings put global hidden_api_policy 1` once (allows hidden APIs; undo by setting it back to null).
 * - Freeform tasks stay ABOVE the home task, so the rest of our home screen stays usable around the window — only while our
 *   launcher is the default HOME app (home tasks are always at the bottom of the stack).
 * Verified from the shell 2026-11-06: YouTube in freeform at (20,600–748,870) over the factory home. From our app: untested.
 */
object LiveWindow {
    private const val WINDOWING_MODE_FREEFORM = 5

    fun launch(act: Activity, pkg: String, bounds: Rect): Boolean {
        val i = act.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val o = ActivityOptions.makeBasic().setLaunchBounds(bounds)
        // Diagnostics (v0.3.2): the first on-device try opened the app full-screen — log whether the hidden call even works.
        runCatching { ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType).invoke(o, WINDOWING_MODE_FREEFORM) }
            .onSuccess { Log.i("CarLauncher", "LiveWindow: freeform requested for $pkg at $bounds") }
            .onFailure { Log.w("CarLauncher", "LiveWindow: setLaunchWindowingMode unavailable: $it") }
        return runCatching { act.startActivity(i, o.toBundle()); true }.onFailure { Log.w("CarLauncher", "LiveWindow: start failed: $it") }.getOrDefault(false)
    }

    /** «تكبير»: the same app full-screen (windowing mode 1 = FULLSCREEN), so the owner can use it big. */
    fun openFull(act: Activity, pkg: String): Boolean {
        val i = act.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val o = ActivityOptions.makeBasic()
        runCatching { ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType).invoke(o, 1) }
        return runCatching { act.startActivity(i, o.toBundle()); true }.getOrDefault(false)
    }
}
