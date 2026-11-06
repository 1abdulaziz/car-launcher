package dev.carlauncher

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The owner's replacement for the factory climate bar (2026-11-06: «ألغِ اللي تحت، وحط واحد صغير من زرين: رجوع والهوم،
 * ويختفي إذا ما لمست الشاشة»). The climate bar is hidden with `settings put system show_navigationbar 0`; this tiny pill
 * gives Back + Home over EVERY app (incl. the factory home), and fades out after a few seconds without a touch.
 *
 * How it knows you touched the screen: FLAG_WATCH_OUTSIDE_TOUCH → the pill gets ACTION_OUTSIDE for every touch anywhere
 * (Android hides the coordinates of other apps' touches, which we don't need). While hidden it shrinks to a 1 px dot, so
 * it never steals a tap from the app underneath, but still hears touches.
 * Back/Home = performGlobalAction — the same system keys the climate bar had.
 * Enable once over ADB: settings put secure enabled_accessibility_services dev.carlauncher/.FloatService
 */
class FloatService : AccessibilityService() {
    companion object { @Volatile var running = false; private const val SHOW_MS = 4000L }

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager; private lateinit var params: WindowManager.LayoutParams
    private var root: LinearLayout? = null; private var pill: LinearLayout? = null; private var dot: View? = null
    private var stats: TextView? = null
    private val hideRun = Runnable { setShown(false) }

    override fun onServiceConnected() {
        running = true
        KeepAlive.ensureBar(this) // runs after each car start (KeepAlive re-enables this service), so the bar is re-hidden late in boot too
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.LEFT; x = dp(12); y = dp(12) } // bottom-left: where the climate bar's keys were, nearest the driver
        root = build(); wm.addView(root, params)
        setShown(true)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun build(): LinearLayout = LinearLayout(this).apply {
        val rtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_LTR
            background = rounded(Color.parseColor("#D90B1430"), dp(34).toFloat(), dp(1), Pal.CARD_LINE); setPadding(dp(6), dp(6), dp(6), dp(6))
            fun btn(glyph: String, desc: String, light: Boolean, action: Int) = TextView(context).apply {
                text = glyph; textSize = 30f; gravity = Gravity.CENTER; contentDescription = desc
                setTextColor(if (light) Color.BLACK else Color.WHITE); background = rounded(if (light) Color.WHITE else Color.parseColor("#26304D"), dp(28).toFloat())
                setOnClickListener { performGlobalAction(action); setShown(true) }
            }
            addView(btn(if (rtl) "›" else "‹", s(R.string.back), true, GLOBAL_ACTION_BACK), LinearLayout.LayoutParams(dp(60), dp(60)))
            addView(btn("⌂", s(R.string.home), false, GLOBAL_ACTION_HOME), LinearLayout.LayoutParams(dp(60), dp(60)).apply { leftMargin = dp(8) })
            // Optional mini RAM/CPU readout — off by default now (owner: «زرين بس»); Settings can turn it on.
            stats = TextView(context).apply { textSize = 20f; setTextColor(Pal.SUB); setPadding(dp(10), 0, dp(6), 0) }
            addView(stats)
        }
        addView(pill)
        dot = View(context); addView(dot, LinearLayout.LayoutParams(1, 1))
        // Any touch anywhere on the screen (ACTION_OUTSIDE) or on the pill itself brings it back for a few seconds.
        setOnTouchListener { _, e -> if (e.actionMasked == MotionEvent.ACTION_OUTSIDE || e.actionMasked == MotionEvent.ACTION_DOWN) setShown(true); false }
    }

    private fun setShown(show: Boolean) {
        val p = pill ?: return
        ui.removeCallbacks(hideRun)
        if (show) {
            val mini = Show.on(this, Show.MINI_FLOAT)
            if (mini) { val r = Stats.ram(this); val c = Stats.cpuPct(); stats?.text = if (c != null) s(R.string.ram_cpu_pill, r.usedPct, c) else s(R.string.ram_pill, r.usedPct) }
            stats?.visibility = if (mini) View.VISIBLE else View.GONE
            if (p.visibility != View.VISIBLE) { p.alpha = 0f; p.visibility = View.VISIBLE; p.animate().alpha(1f).setDuration(150).start() }
            // v0.3.1 (owner): «الرجوع مختفي — حطه ثابت دائم». Auto-hide is now OFF by default (Settings can turn it back on).
            if (Show.on(this, Show.FLOAT_AUTOHIDE)) ui.postDelayed(hideRun, SHOW_MS)
        } else {
            p.animate().alpha(0f).setDuration(250).withEndAction { p.visibility = View.GONE }.start()
        }
    }

    // Switching apps also counts as activity → show briefly so Back/Home are at hand right after opening something.
    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && e.packageName?.toString() != "com.android.systemui") setShown(true)
    }

    override fun onInterrupt() {}

    override fun onDestroy() { running = false; ui.removeCallbacks(hideRun); root?.let { runCatching { wm.removeView(it) } }; super.onDestroy() }
}
