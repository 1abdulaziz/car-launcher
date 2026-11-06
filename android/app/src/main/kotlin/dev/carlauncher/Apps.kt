package dev.carlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable

data class AppEntry(val pkg: String, val label: String, val icon: Drawable)

// Installed launchable apps + the owner's 4 tiles (saved per device; survives reboots — researched pain point).
object Apps {
    private const val PREFS = "launcher"
    private const val KEY = "slots"
    // Defaults agreed 2026-11-06 (no Waze): matched by LABEL on first run, because package names vary per unit.
    // A label that isn't installed leaves the tile empty («+»).
    private val DEFAULT_LABELS = listOf("YMusic", "AutoKit", "Phone|الهاتف|Dialer", "Chrome|كروم")

    fun all(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(main, 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { r: ResolveInfo -> AppEntry(r.activityInfo.packageName, r.loadLabel(pm).toString(), r.loadIcon(pm)) }
            .sortedBy { it.label }
    }

    fun slots(ctx: Context): MutableList<String?> {
        val saved = ctx.getSharedPreferences(PREFS, 0).getString(KEY, null)
        if (saved != null) return saved.split("|").map { it.ifEmpty { null } }.toMutableList()
        val apps = all(ctx)
        return DEFAULT_LABELS.map { hint ->
            val names = hint.split("|")
            apps.firstOrNull { a -> names.any { a.label.contains(it, ignoreCase = true) } }?.pkg
        }.toMutableList()
    }

    fun saveSlots(ctx: Context, s: List<String?>) =
        ctx.getSharedPreferences(PREFS, 0).edit().putString(KEY, s.joinToString("|") { it ?: "" }).apply()

    fun entry(ctx: Context, pkg: String?): AppEntry? = pkg?.let { p ->
        runCatching {
            val pm = ctx.packageManager
            val ai = pm.getApplicationInfo(p, 0)
            AppEntry(p, pm.getApplicationLabel(ai).toString(), pm.getApplicationIcon(ai))
        }.getOrNull()
    }

    /** Opens an app; returns false (caller shows why) when it isn't installed or has no launch screen. */
    fun launch(ctx: Context, pkg: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, 0)
}

// The two big panels' apps (top = Google Maps, bottom = YouTube by default). Any installed app can go in either.
object Panels {
    const val TOP = "panel_top"; const val BOTTOM = "panel_bottom"
    private val defaults = mapOf(TOP to "com.google.android.apps.maps", BOTTOM to "com.google.android.youtube")
    fun get(ctx: android.content.Context, slot: String): String? = Apps.prefs(ctx).getString(slot, defaults[slot])
    fun set(ctx: android.content.Context, slot: String, pkg: String) = Apps.prefs(ctx).edit().putString(slot, pkg).apply()
}
