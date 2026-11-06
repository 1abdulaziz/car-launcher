package dev.carlauncher

import android.app.Activity
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.*

/**
 * «الذاكرة والسرعة» — real RAM numbers and one-tap stopping of apps sitting in the background.
 * Android 12 (his unit): killBackgroundProcesses() may stop OTHER apps' background processes (the own-only limit starts at 14).
 * The app in front is never touched by it. Per-app MB is not readable by a normal app since Android 10 → we show «آخر استخدام» instead.
 */
class RamActivity : Activity() {
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var list: LinearLayout; private lateinit var summary: TextView; private lateinit var bar: LinearLayout; private lateinit var result: TextView

    // Never stop the car's own system (BT, reverse camera, car info, AC bar), Google services or the system UI.
    private val protectedPrefixes = listOf("com.tw.", "com.dofun.carsetting", "com.google.android.gms", "com.android.systemui", "com.android.phone",
        "android", "com.android.inputmethod", "com.google.android.inputmethod", "dev.carlauncher")

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    override fun onCreate(b: Bundle?) {
        super.onCreate(b); requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Pal.BG_TOP, Pal.BG_BOTTOM)); setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(text(s(R.string.mem_title), 36f, Pal.TEXT, bold = true), lp(0, -2, 1f))
        val rtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        // The fixed «رجوع» pill — same place as everywhere.
        head.addView(text(if (rtl) "${s(R.string.back)} ›" else "‹ ${s(R.string.back)}", 28f, Color.BLACK, bold = true, gravity = Gravity.CENTER).apply { background = rounded(Color.WHITE, dp(30).toFloat()); setOnClickListener { finish() }; pressable() }, lp(dp(170), dp(60)))
        root.addView(head)

        val sumCard = card()
        summary = text("", 32f, Pal.TEXT, bold = true); sumCard.addView(summary)
        bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; background = rounded(Color.parseColor("#26304D"), dp(10).toFloat()) }
        sumCard.addView(bar, lp(-1, dp(20)).apply { topMargin = dp(12) })
        root.addView(sumCard, lp(-1, -2).apply { topMargin = dp(12) })

        root.addView(text(s(R.string.stop_all), 30f, Color.BLACK, bold = true, gravity = Gravity.CENTER).apply {
            background = rounded(Pal.ACCENT, dp(24).toFloat()); setOnClickListener { stop(recent()) }; pressable()
        }, lp(-1, dp(80)).apply { topMargin = dp(12) })
        result = text("", SP_MIN, Pal.GOOD).apply { visibility = View.GONE }
        root.addView(result, lp(-1, -2).apply { topMargin = dp(10) })

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, lp(-1, 0, 1f).apply { topMargin = dp(12) })
        setContentView(root)
    }

    override fun onResume() { super.onResume(); render() }

    private fun hasUsageAccess(): Boolean {
        val ops = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED
    }

    /** Apps used in the last 24 h, most recent first, minus the protected car/system ones. */
    private fun recent(): List<Pair<String, Long>> {
        if (!hasUsageAccess()) return emptyList()
        val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        return usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 86_400_000L, now)
            .filter { it.lastTimeUsed > 0 && protectedPrefixes.none { p -> it.packageName == p || it.packageName.startsWith(p) } && Apps.entry(this, it.packageName) != null }
            .groupBy { it.packageName }.map { (pkg, s) -> pkg to s.maxOf { it.lastTimeUsed } }
            .sortedByDescending { it.second }
    }

    private fun render() {
        val r = Stats.ram(this)
        summary.text = s(R.string.mem_summary, "%.1f".format((r.totalMb - r.availMb) / 1024.0), "%.1f".format(r.totalMb / 1024.0), "%.1f".format(r.availMb / 1024.0))
        bar.removeAllViews()
        bar.addView(View(this).apply { background = rounded(if (r.usedPct < 70) Pal.ACCENT else Pal.WARN, dp(10).toFloat()) }, LinearLayout.LayoutParams(0, -1, r.usedPct.toFloat()))
        bar.addView(View(this), LinearLayout.LayoutParams(0, -1, (100 - r.usedPct).toFloat()))
        list.removeAllViews()
        if (!hasUsageAccess()) {
            list.addView(card().apply {
                addView(text(s(R.string.usage_needed), 28f, Pal.TEXT))
                addView(text(s(R.string.usage_tap), SP_MIN, Pal.ACCENT)); setOnClickListener { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }; pressable()
            }); return
        }
        val now = System.currentTimeMillis()
        recent().forEach { (pkg, last) ->
            val e = Apps.entry(this, pkg) ?: return@forEach
            val row = card(vertical = false).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(ImageView(this).apply { setImageDrawable(e.icon) }, lp(dp(56), dp(56)))
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), 0) }
            val mins = ((now - last) / 60_000).toInt()
            info.addView(text(e.label, 28f, Pal.TEXT, bold = true)); info.addView(text(if (mins < 60) s(R.string.last_used_min, mins) else s(R.string.last_used_h, mins / 60), SP_MIN, Pal.DIM))
            row.addView(info, lp(0, -2, 1f))
            row.addView(text(s(R.string.stop), 26f, Pal.DANGER, gravity = Gravity.CENTER).apply {
                background = rounded(Color.parseColor("#3A1B26"), dp(28).toFloat(), dp(1), Color.parseColor("#7A2E44")); setOnClickListener { stop(listOf(pkg to last)) }; pressable()
            }, lp(dp(120), dp(64)))
            list.addView(row, lp(-1, -2).apply { bottomMargin = dp(10) })
        }
    }

    /** Stops the given apps' background processes and reports what actually happened (count + freed MB + why the rest stayed). */
    private fun stop(apps: List<Pair<String, Long>>) {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val before = Stats.ram(this).availMb
        apps.forEach { (pkg, _) -> runCatching { am.killBackgroundProcesses(pkg) } }
        ui.postDelayed({
            val freed = (Stats.ram(this).availMb - before).coerceAtLeast(0)
            result.visibility = View.VISIBLE
            result.text = if (apps.isEmpty()) s(R.string.nothing_bg) else s(R.string.stopped_result, apps.size, freed.toInt())
            render()
        }, 800)
    }
}
