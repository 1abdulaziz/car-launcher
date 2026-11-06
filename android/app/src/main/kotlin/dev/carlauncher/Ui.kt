package dev.carlauncher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

// Design 1 «طلبك» palette — dark navy + cyan accent (the adopted identity). Amber = warning only.
object Pal {
    val BG_TOP = Color.parseColor("#0B1430")
    val BG_BOTTOM = Color.parseColor("#05070D")
    val CARD = Color.parseColor("#141C33")
    val CARD_LINE = Color.parseColor("#26304D")
    val TEXT = Color.parseColor("#F4F6FA")
    val SUB = Color.parseColor("#B8C0D0")
    val DIM = Color.parseColor("#8C95A8")
    val ACCENT = Color.parseColor("#22D3EE")
    val ACCENT_DARK = Color.parseColor("#0E7490")
    val WARN = Color.parseColor("#FBBF24")
    val GOOD = Color.parseColor("#34D399")
    val DANGER = Color.parseColor("#FB7185")
}

// His unit is 160 dpi → 1dp = 1px. Android for Cars minimum type is 24dp; nothing on screen goes below SP_MIN.
const val SP_MIN = 24f

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun rounded(color: Int, radius: Float, stroke: Int = 0, strokeColor: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(color); cornerRadius = radius
        if (stroke > 0) setStroke(stroke, strokeColor)
    }

fun Context.text(s: CharSequence, size: Float, color: Int = Pal.TEXT, bold: Boolean = false, gravity: Int = Gravity.START): TextView =
    TextView(this).apply {
        text = s; textSize = maxOf(size, SP_MIN); setTextColor(color); this.gravity = gravity
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, 500, false) // medium, never bold (Android for Cars)
        includeFontPadding = false
    }

fun Context.card(vertical: Boolean = true): LinearLayout = LinearLayout(this).apply {
    orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
    background = rounded(Pal.CARD, dp(24).toFloat(), dp(1), Pal.CARD_LINE)
    setPadding(dp(16), dp(14), dp(16), dp(14))
}

fun lp(w: Int, h: Int, weight: Float = 0f, margin: Int = 0): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(w, h, weight).apply { setMargins(margin, margin, margin, margin) }

fun View.pressable(): View = apply {
    isClickable = true
    setOnTouchListener { v, e ->
        when (e.action) {
            android.view.MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start()
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        }
        false
    }
}

// ---------- language + display settings ----------
// Language: «system» follows the unit's language; «ar» / «en» override it for this app only (owner 2026-11-06: support both).
object Lang {
    fun code(ctx: Context): String = Apps.prefs(ctx).getString("lang", "system") ?: "system"
    fun wrap(base: Context): Context {
        val c = code(base); if (c == "system") return base
        val loc = java.util.Locale(c)
        val cfg = android.content.res.Configuration(base.resources.configuration).apply { setLocale(loc); setLayoutDirection(loc) }
        return base.createConfigurationContext(cfg)
    }
}

// Everything on the home screen can be shown/hidden (owner 2026-11-06: «خيارات إعدادات تتحكم بكل شي معروض»). Default: all on.
object Show {
    const val DATE = "show_date"; const val HIJRI = "show_hijri"; const val RAM = "show_ram"; const val APPS = "show_apps"
    const val SPEED = "show_speed"; const val WEATHER = "show_weather"; const val PRAYER = "show_prayer"
    const val MAPS = "show_maps"; const val MEDIA = "show_media"; const val MINI = "mini_stats"
    // The floating Back/Home pill shows just two buttons by default (owner: «زرين بس»); its RAM/CPU readout is opt-in.
    const val MINI_FLOAT = "mini_stats_float"
    const val FLOAT_AUTOHIDE = "float_autohide" // hide Back/Home after 4 s idle — off by default (owner wants them always visible)
    // Hide the factory climate bar (default on, owner 2026-11-06). Applied by the separate bar-helper APK — see KeepAlive.ensureBar.
    const val HIDE_BAR = "hide_climate_bar"
    private val defaultOff = setOf(MINI_FLOAT, FLOAT_AUTOHIDE)
    fun on(ctx: Context, key: String) = Apps.prefs(ctx).getBoolean(key, key !in defaultOff)
    fun toggle(ctx: Context, key: String) = Apps.prefs(ctx).edit().putBoolean(key, !on(ctx, key)).apply()
}

fun Context.s(id: Int, vararg args: Any): String = if (args.isEmpty()) getString(id) else getString(id, *args)
