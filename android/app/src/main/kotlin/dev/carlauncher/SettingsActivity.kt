package dev.carlauncher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * Settings (owner 2026-11-06: «خيارات إعدادات خاصة تتحكم بكل شي معروض» + both languages).
 * Language · what shows on home (every block) · floating Back/Home + mini RAM/CPU · memory page · design.
 * Each change applies immediately; Home rebuilds itself on resume.
 */
class SettingsActivity : Activity() {
    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    override fun onCreate(b: Bundle?) { super.onCreate(b); requestWindowFeature(Window.FEATURE_NO_TITLE) }
    override fun onResume() { super.onResume(); setContentView(build()) }

    private fun build(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Pal.BG_TOP, Pal.BG_BOTTOM)); setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(text(s(R.string.settings), 36f, Pal.TEXT, bold = true), lp(0, -2, 1f))
        val rtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        head.addView(text(if (rtl) "${s(R.string.back)} ›" else "‹ ${s(R.string.back)}", 28f, Color.BLACK, bold = true, gravity = Gravity.CENTER).apply {
            background = rounded(Color.WHITE, dp(30).toFloat()); setOnClickListener { finish() }; pressable()
        }, lp(dp(170), dp(60)))
        root.addView(head)

        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun section(title: Int) = body.addView(text(s(title), 28f, Pal.ACCENT, bold = true), lp(-1, -2).apply { topMargin = dp(18); bottomMargin = dp(8) })
        fun row(title: String, value: String? = null, valueOn: Boolean = true, onClick: () -> Unit) = body.addView(card(vertical = false).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(text(title, SP_MIN + 2, Pal.TEXT), lp(0, -2, 1f))
            if (value != null) addView(text(value, SP_MIN, if (valueOn) Color.BLACK else Pal.SUB, gravity = Gravity.CENTER).apply {
                background = rounded(if (valueOn) Pal.ACCENT else Color.parseColor("#26304D"), dp(22).toFloat()); setPadding(dp(16), dp(6), dp(16), dp(6))
            })
            setOnClickListener { onClick() }; pressable()
        }, lp(-1, dp(80)).apply { bottomMargin = dp(8) })

        // Language — applies to the whole launcher; «لغة الجهاز» follows the unit.
        section(R.string.s_language)
        val cur = Lang.code(this)
        listOf("system" to R.string.lang_system, "ar" to R.string.lang_ar, "en" to R.string.lang_en).forEach { (code, label) ->
            row(s(label), if (cur == code) "✓" else null) {
                Apps.prefs(this).edit().putString("lang", code).apply(); recreate()
            }
        }

        section(R.string.s_show)
        listOf(Show.DATE to R.string.show_date, Show.HIJRI to R.string.show_hijri, Show.RAM to R.string.show_ram, Show.APPS to R.string.show_apps,
            Show.SPEED to R.string.show_speed, Show.WEATHER to R.string.show_weather, Show.PRAYER to R.string.show_prayer,
            Show.MAPS to R.string.show_top_panel, Show.MEDIA to R.string.show_bottom_panel).forEach { (key, label) ->
            val on = Show.on(this, key)
            row(s(label), s(if (on) R.string.on else R.string.off), on) { Show.toggle(this, key); setContentView(build()) }
        }
        body.addView(text(s(R.string.panel_hint), SP_MIN, Pal.DIM), lp(-1, -2).apply { bottomMargin = dp(4) })
        body.addView(text(s(R.string.s_apps_hint), SP_MIN, Pal.DIM))

        section(R.string.s_float)
        row(s(if (FloatService.running) R.string.float_on else R.string.float_off)) { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        val auto = Show.on(this, Show.FLOAT_AUTOHIDE)
        row(s(R.string.float_autohide), s(if (auto) R.string.on else R.string.off), auto) { Show.toggle(this, Show.FLOAT_AUTOHIDE); setContentView(build()) }
        val mini = Show.on(this, Show.MINI_FLOAT)
        row(s(R.string.mini_stats), s(if (mini) R.string.on else R.string.off), mini) { Show.toggle(this, Show.MINI_FLOAT); setContentView(build()) }

        section(R.string.mem_title)
        row(s(R.string.s_memory_sub)) { startActivity(Intent(this, RamActivity::class.java)) }

        // Several designs ship over time; design 1 «طلبك» is the first (owner 2026-11-06).
        section(R.string.s_design)
        val design = Apps.prefs(this).getString("design", "factory")
        row(s(R.string.design_factory), if (design == "factory") "✓" else null) { Apps.prefs(this).edit().putString("design", "factory").apply(); setContentView(build()) }
        row(s(R.string.design_talabak), if (design == "talabak") "✓" else null) { Apps.prefs(this).edit().putString("design", "talabak").apply(); setContentView(build()) }
        body.addView(text(s(R.string.more_designs), SP_MIN, Pal.DIM))

        root.addView(ScrollView(this).apply { addView(body) }, lp(-1, 0, 1f).apply { topMargin = dp(6) })
        return root
    }
}
