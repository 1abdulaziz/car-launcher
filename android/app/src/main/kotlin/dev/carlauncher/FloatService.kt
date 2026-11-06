package dev.carlauncher

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
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
 * ⚠️ Owner's top requirement: a fixed «رجوع» + «الرئيسية» that works from ANY app (Google Maps, YouTube…).
 * An accessibility service can draw over every app (TYPE_ACCESSIBILITY_OVERLAY, no extra permission) and press the system
 * Back/Home itself (performGlobalAction). Hidden while our own home is in front (it has its own buttons).
 * Also carries the optional mini RAM/CPU readout. Drag the «⋮» grip to move it up/down the left edge.
 * Enable once over ADB: settings put secure enabled_accessibility_services dev.carlauncher/.FloatService
 */
class FloatService : AccessibilityService() {
    companion object { @Volatile var running = false }

    private val ui = Handler(Looper.getMainLooper())
    private var pill: LinearLayout? = null; private var stats: TextView? = null
    private lateinit var wm: WindowManager; private lateinit var params: WindowManager.LayoutParams

    override fun onServiceConnected() {
        running = true
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.LEFT; x = dp(8); y = Apps.prefs(this@FloatService).getInt("float_y", dp(64)) }
        pill = build(); wm.addView(pill, params); refresh.run()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun build(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        background = rounded(Color.parseColor("#E60B1430"), dp(32).toFloat(), dp(1), Pal.CARD_LINE); setPadding(dp(6), dp(6), dp(10), dp(6))
        fun btn(glyph: String, desc: String, light: Boolean, action: Int) = TextView(context).apply {
            text = glyph; textSize = 30f; gravity = Gravity.CENTER; contentDescription = desc
            setTextColor(if (light) Color.BLACK else Color.WHITE); background = rounded(if (light) Color.WHITE else Color.parseColor("#26304D"), dp(28).toFloat())
            setOnClickListener { performGlobalAction(action) }
        }
        addView(btn("›", "رجوع", true, GLOBAL_ACTION_BACK), LinearLayout.LayoutParams(dp(56), dp(56)))
        addView(btn("⌂", "الرئيسية", false, GLOBAL_ACTION_HOME), LinearLayout.LayoutParams(dp(56), dp(56)).apply { leftMargin = dp(6) })
        stats = TextView(context).apply { textSize = 22f; setTextColor(Pal.SUB); setPadding(dp(10), 0, dp(4), 0) }
        addView(stats)
        val grip = TextView(context).apply { text = "⋮"; textSize = 30f; setTextColor(Pal.DIM); setPadding(dp(8), 0, dp(4), 0) }
        var startY = 0f; var startParam = 0
        grip.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { startY = e.rawY; startParam = params.y }
                MotionEvent.ACTION_MOVE -> { params.y = (startParam + (e.rawY - startY)).toInt().coerceAtLeast(0); wm.updateViewLayout(this, params) }
                MotionEvent.ACTION_UP -> Apps.prefs(context).edit().putInt("float_y", params.y).apply()
            }
            true
        }
        addView(grip)
    }

    private val refresh = object : Runnable {
        override fun run() {
            val p = pill ?: return
            val on = Apps.prefs(this@FloatService).getBoolean("mini_stats", true)
            if (on && p.visibility == View.VISIBLE) {
                val r = Stats.ram(this@FloatService); val c = Stats.cpuPct()
                stats?.text = if (c != null) "رام ${r.usedPct}% · معالج $c%" else "رام ${r.usedPct}%"
                stats?.visibility = View.VISIBLE
            } else stats?.visibility = View.GONE
            ui.postDelayed(this, 2000)
        }
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        // Our home already shows Back/Home in its top row → hide the floating copy there; show it over everything else.
        pill?.visibility = if (pkg == packageName) View.GONE else View.VISIBLE
    }

    override fun onInterrupt() {}

    override fun onDestroy() { running = false; ui.removeCallbacks(refresh); pill?.let { runCatching { wm.removeView(it) } }; super.onDestroy() }
}
