package dev.carlauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar

/**
 * Design «شكل الوكالة» — version 1 = a faithful copy of the factory Dofun home the owner likes (owner 2026-11-06:
 * «نفس الوكالة أول نسخة، بعدين نعدل حبة حبة»). Measured from his unit's screenshots (768 wide @160 dpi, 1 dp = 1 px):
 *   top: 6 app icons + a dark label strip · middle: a LIVE app window (Google Maps by default) · bottom: two swipeable pages —
 *   (1) music card + clock/weather + radio card, (2) the car + speed.
 * Artwork is drawn in code, NOT copied from the factory theme (the repo is public; the vendor's images aren't ours).
 * Sizes match the factory's own (smaller than design 1) — the owner rejected the big 24dp type.
 */
class FactoryHome(private val a: HomeActivity) {
    private val ctx: Context = a
    private fun px(v: Int) = ctx.dp(v)

    private lateinit var timeTv: TextView; private lateinit var ampmTv: TextView; private lateinit var dateTv: TextView; private lateinit var dayTv: TextView
    private lateinit var tempTv: TextView; private lateinit var condTv: TextView; private lateinit var cityTv: TextView
    private lateinit var srcTv: TextView; private lateinit var titleTv: TextView; private lateinit var playTv: TextView
    private lateinit var speedTv: TextView
    var mapBox: FrameLayout? = null; private set
    private lateinit var pages: FrameLayout; private lateinit var dots: LinearLayout
    private var page = 0

    // ---------- small helpers (factory sizes — no 24dp floor here) ----------
    private fun tv(s: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false, g: Int = Gravity.CENTER) = TextView(ctx).apply {
        text = s; textSize = size; setTextColor(color); gravity = g; includeFontPadding = false
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, 500, false)
    }
    private fun fl(w: Int, h: Int, g: Int, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) =
        FrameLayout.LayoutParams(w, h, g).apply { setMargins(px(l), px(t), px(r), px(b)) }
    private fun circle(colors: IntArray) = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply { shape = GradientDrawable.OVAL }

    fun build(): View {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            // Factory background: magenta-purple top fading to deep blue.
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#4A1A6E"), Color.parseColor("#2A2378"), Color.parseColor("#141B4A")))
            setPadding(px(20), px(14), px(20), px(6))
        }
        root.addView(topApps(), LinearLayout.LayoutParams(-1, px(118)))
        mapBox = mapPanel()
        root.addView(mapBox, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = px(10) })
        root.addView(bottomArea(), LinearLayout.LayoutParams(-1, px(340)).apply { topMargin = px(4) })
        dots = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        root.addView(dots, LinearLayout.LayoutParams(-1, px(14)))
        showPage(0)
        return root
    }

    // ---------- top: 6 apps (icon) + dark strip with their names — exactly the factory arrangement ----------
    private fun topApps(): View = FrameLayout(ctx).apply {
        val strip = View(ctx).apply { background = rounded(Color.parseColor("#661A1F4D"), px(14).toFloat(), px(1), Color.parseColor("#33FFFFFF")) }
        addView(strip, fl(-1, px(34), Gravity.BOTTOM))
        val icons = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val labels = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        FactoryApps.slots(ctx).forEachIndexed { i, pkg ->
            val e = Apps.entry(ctx, pkg)
            val cell = FrameLayout(ctx)
            val icon = ImageView(ctx).apply { if (e != null) setImageDrawable(e.icon) else setImageDrawable(null) }
            cell.addView(icon, fl(px(64), px(64), Gravity.CENTER))
            if (e == null) cell.addView(tv("+", 34f, Color.parseColor("#99FFFFFF")), fl(-2, -2, Gravity.CENTER))
            cell.setOnClickListener { if (e == null || !Apps.launch(ctx, e.pkg)) a.pickFactoryApp(i) }
            cell.setOnLongClickListener { a.pickFactoryApp(i); true } // long-press = change this app
            cell.pressable()
            icons.addView(cell, LinearLayout.LayoutParams(0, -1, 1f))
            labels.addView(tv(e?.label ?: "", 15f, Color.parseColor("#E6FFFFFF")).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -1, 1f))
        }
        addView(icons, fl(-1, px(78), Gravity.TOP))
        addView(labels, fl(-1, px(34), Gravity.BOTTOM))
    }

    // ---------- middle: the live app window ----------
    // The real app runs as a freeform window exactly over this box (LiveWindow). Under the window: a strip that replaces the
    // factory's «layers» button (owner 2026-11-06: «ما أبيه — أبي أحدد أي تطبيق يشتغل في المربع، ومن ضغطه يشتغل أسرع،
    // وزر يكبّر يفتح التطبيق»): quick-switch icons for the window app (+ to add, long-press to remove) and «تكبير ⤢».
    private val STRIP = 58
    private fun mapPanel(): FrameLayout = FrameLayout(ctx).apply {
        background = rounded(Color.parseColor("#0E1430"), px(16).toFloat(), px(2), Color.parseColor("#3A4378"))
        val cur = Panels.get(ctx, Panels.TOP)
        val e = Apps.entry(ctx, cur)
        // placeholder = only visible if the live window can't be shown
        val ph = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        if (e != null) ph.addView(ImageView(ctx).apply { setImageDrawable(e.icon) }, LinearLayout.LayoutParams(px(72), px(72)))
        ph.addView(tv(e?.label ?: ctx.s(R.string.add), 22f, Color.WHITE, bold = true), LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(8) })
        ph.setOnClickListener { a.launchLive() }
        addView(ph, fl(-1, -1, Gravity.CENTER, b = STRIP))
        // control strip under the window
        val strip = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            background = rounded(Color.parseColor("#1A2050"), px(14).toFloat()); setPadding(px(10), 0, px(8), 0)
        }
        WindowApps.list(ctx).forEach { pkg ->
            val app = Apps.entry(ctx, pkg) ?: return@forEach
            val on = pkg == cur
            val chip = FrameLayout(ctx).apply {
                background = rounded(if (on) Color.parseColor("#3A62FF") else Color.TRANSPARENT, px(12).toFloat())
                addView(ImageView(ctx).apply { setImageDrawable(app.icon) }, fl(px(38), px(38), Gravity.CENTER))
                contentDescription = app.label
                setOnClickListener { Panels.set(ctx, Panels.TOP, pkg); a.rebuildAndLaunch() }
                setOnLongClickListener { WindowApps.remove(ctx, pkg); a.rebuildAndLaunch(); true }
                pressable()
            }
            strip.addView(chip, LinearLayout.LayoutParams(px(50), px(50)).apply { marginEnd = px(6) })
        }
        strip.addView(tv("+", 28f, Color.WHITE).apply {
            background = rounded(Color.parseColor("#2A3170"), px(12).toFloat()); contentDescription = ctx.s(R.string.add)
            setOnClickListener { a.addWindowApp() }; pressable()
        }, LinearLayout.LayoutParams(px(50), px(50)))
        strip.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
        strip.addView(tv("⤢  " + ctx.s(R.string.expand), 18f, Color.BLACK, bold = true).apply {
            background = rounded(Color.WHITE, px(14).toFloat()); setPadding(px(16), 0, px(16), 0)
            setOnClickListener { a.openFull(cur) }; pressable()
        }, LinearLayout.LayoutParams(-2, px(46)))
        addView(strip, fl(-1, px(STRIP - 6), Gravity.BOTTOM, l = 4, r = 4, b = 4))
    }

    /** Screen rect of the window box, for the freeform launch. */
    fun mapRect(): Rect? {
        val box = mapBox ?: return null
        if (box.width == 0) return null
        val p = IntArray(2); box.getLocationOnScreen(p)
        return Rect(p[0] + px(2), p[1] + px(2), p[0] + box.width - px(2), p[1] + box.height - px(STRIP))
    }

    // ---------- bottom: two pages, swipe left/right (factory has the same dots) ----------
    private fun bottomArea(): View {
        pages = FrameLayout(ctx)
        pages.addView(page1(), FrameLayout.LayoutParams(-1, -1))
        pages.addView(page2(), FrameLayout.LayoutParams(-1, -1))
        val gd = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (Math.abs(vx) < Math.abs(vy)) return false
                showPage(if (page == 0) 1 else 0); return true
            }
        })
        pages.setOnTouchListener { _, ev -> gd.onTouchEvent(ev) }
        return pages
    }

    private fun showPage(i: Int) {
        page = i
        for (k in 0 until pages.childCount) pages.getChildAt(k).visibility = if (k == i) View.VISIBLE else View.GONE
        dots.removeAllViews()
        for (k in 0..1) dots.addView(View(ctx).apply { background = rounded(if (k == i) Color.WHITE else Color.parseColor("#66FFFFFF"), px(3).toFloat()) },
            LinearLayout.LayoutParams(px(if (k == i) 26 else 18), px(5)).apply { setMargins(px(4), 0, px(4), 0) })
    }

    private fun page1(): View = FrameLayout(ctx).apply {
        layoutDirection = View.LAYOUT_DIRECTION_LTR // the factory composition is fixed: music left, radio right
        // decorative bubbles behind the clock/weather (pink + blue, like the factory)
        addView(View(ctx).apply { background = circle(intArrayOf(Color.parseColor("#FF4FB0"), Color.parseColor("#B0127A"))) }, fl(px(190), px(190), Gravity.TOP or Gravity.LEFT, l = 360, t = 40))
        addView(View(ctx).apply { background = circle(intArrayOf(Color.parseColor("#3D8BFF"), Color.parseColor("#1338A8"))) }, fl(px(170), px(170), Gravity.TOP or Gravity.LEFT, l = 535, t = 52))
        // clock (top-right)
        val clock = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
        ampmTv = tv("", 16f, Color.WHITE); timeTv = tv("", 58f, Color.WHITE, bold = true)
        clock.addView(ampmTv, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(8); rightMargin = px(10) }); clock.addView(timeTv)
        addView(clock, fl(-2, -2, Gravity.TOP or Gravity.RIGHT, t = 12, r = 6))
        val dateCol = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.RIGHT }
        dateTv = tv("", 14f, Color.WHITE, g = Gravity.RIGHT); dayTv = tv("", 14f, Color.WHITE, g = Gravity.RIGHT)
        dateCol.addView(dateTv); dateCol.addView(dayTv, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(8) })
        addView(dateCol, fl(-2, -2, Gravity.TOP or Gravity.RIGHT, t = 84, r = 14))
        // weather (left of the clock)
        val weather = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.LEFT }
        weather.addView(tv("☁", 44f, Color.WHITE, g = Gravity.LEFT))
        val tRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        val tCol = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        condTv = tv("", 13f, Color.WHITE, g = Gravity.LEFT); cityTv = tv("", 13f, Color.WHITE, g = Gravity.LEFT)
        tCol.addView(condTv); tCol.addView(cityTv, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(6) })
        tempTv = tv("—", 34f, Color.WHITE, bold = true)
        tRow.addView(tCol); tRow.addView(tempTv, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = px(16) })
        weather.addView(tRow)
        addView(weather, fl(-2, -2, Gravity.TOP or Gravity.LEFT, l = 372, t = 22))

        // music: vinyl disc + bluetooth badge + card (bottom-left)
        addView(VinylView(ctx), fl(px(200), px(200), Gravity.TOP or Gravity.LEFT, l = 130, t = 0))
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            background = rounded(Color.parseColor("#B32A2F6E"), px(20).toFloat(), px(1), Color.parseColor("#33FFFFFF")); setPadding(px(10), px(54), px(10), px(10))
        }
        srcTv = tv("", 13f, Color.parseColor("#FF4FB0")); titleTv = tv("", 22f, Color.WHITE).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        card.addView(srcTv); card.addView(titleTv, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(6) })
        val ctl = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        fun k(glyph: String, size: Float, onClick: () -> Unit) = tv(glyph, size, Color.WHITE).apply { setOnClickListener { onClick() }; pressable() }
        ctl.addView(k("⏮", 26f) { media(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }, LinearLayout.LayoutParams(0, px(56), 1f))
        playTv = k("⏯", 34f) { media(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
        ctl.addView(playTv, LinearLayout.LayoutParams(0, px(56), 1f))
        ctl.addView(k("⏭", 26f) { media(KeyEvent.KEYCODE_MEDIA_NEXT) }, LinearLayout.LayoutParams(0, px(56), 1f))
        card.addView(ctl, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(14) })
        card.setOnClickListener { val p = activeController()?.packageName; if (p == null || !Apps.launch(ctx, p)) Apps.launch(ctx, "com.tw.music") }
        addView(card, fl(px(360), px(196), Gravity.BOTTOM or Gravity.LEFT, b = 6))
        addView(View(ctx).apply { background = circle(intArrayOf(Color.parseColor("#FF2DB4"), Color.parseColor("#C0137E"))) }, fl(px(58), px(58), Gravity.BOTTOM or Gravity.LEFT, l = 201, b = 173))
        addView(tv("ᛒ", 30f, Color.WHITE), fl(px(58), px(58), Gravity.BOTTOM or Gravity.LEFT, l = 201, b = 173))

        // radio card (bottom-right) — tap opens the factory radio; frequency display = @TODO (needs com.tw.radio's interface)
        val radio = FrameLayout(ctx).apply { background = rounded(Color.parseColor("#B3233A86"), px(20).toFloat(), px(1), Color.parseColor("#33FFFFFF")) }
        radio.addView(View(ctx).apply { background = circle(intArrayOf(Color.parseColor("#3DA0FF"), Color.parseColor("#1B5FD6"))) }, fl(px(60), px(60), Gravity.TOP or Gravity.LEFT, l = 18, t = 22))
        radio.addView(tv("📻", 26f), fl(px(60), px(60), Gravity.TOP or Gravity.LEFT, l = 18, t = 22))
        radio.addView(tv("FM", 44f, Color.WHITE, bold = true), fl(-2, -2, Gravity.TOP or Gravity.LEFT, l = 110, t = 22))
        radio.addView(tv(ctx.s(R.string.radio), 15f, Color.WHITE), fl(-2, -2, Gravity.TOP or Gravity.LEFT, l = 228, t = 32))
        val rctl = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        rctl.addView(k("⏮", 26f) { media(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }, LinearLayout.LayoutParams(0, px(56), 1f))
        rctl.addView(k("◉", 30f) { Apps.launch(ctx, "com.tw.radio") }, LinearLayout.LayoutParams(0, px(56), 1f))
        rctl.addView(k("⏭", 26f) { media(KeyEvent.KEYCODE_MEDIA_NEXT) }, LinearLayout.LayoutParams(0, px(56), 1f))
        radio.addView(rctl, fl(-1, -2, Gravity.BOTTOM, b = 10))
        radio.setOnClickListener { Apps.launch(ctx, "com.tw.radio") }
        addView(radio, fl(px(330), px(178), Gravity.BOTTOM or Gravity.RIGHT, b = 6))
    }

    // page 2: the car + speed (factory page 2). Speed in km/h (the factory showed mph).
    private fun page2(): View = FrameLayout(ctx).apply {
        addView(CarView(ctx), fl(-1, px(230), Gravity.BOTTOM or Gravity.LEFT, b = 0))
        val sp = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_LTR }
        speedTv = tv("0", 64f, Color.WHITE, bold = true)
        sp.addView(speedTv); sp.addView(tv(ctx.s(R.string.kmh), 20f, Color.WHITE), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = px(12) })
        addView(sp, fl(-2, -2, Gravity.TOP or Gravity.RIGHT, t = 30, r = 30))
    }

    // ---------- live data ----------
    fun tick(isArabic: Boolean) {
        val c = Calendar.getInstance()
        val h = c.get(Calendar.HOUR).let { if (it == 0) 12 else it }
        timeTv.text = "%d:%02d".format(h, c.get(Calendar.MINUTE))
        ampmTv.text = if (c.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
        dateTv.text = "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        dayTv.text = android.icu.text.SimpleDateFormat("EEEE", android.icu.util.ULocale(if (isArabic) "ar" else "en")).format(c.time)
        val m = activeController()
        val md = m?.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
        titleTv.text = title ?: "Not Provided"
        srcTv.text = m?.packageName?.let { Apps.entry(ctx, it)?.label } ?: ctx.s(R.string.bt_music)
        playTv.text = if (m?.playbackState?.state == PlaybackState.STATE_PLAYING) "⏸" else "⏯"
    }

    fun onSpeed(kmh: Int) { speedTv.text = "$kmh" }
    fun onWeather(temp: Int, cond: String, city: String?) { tempTv.text = "$temp°"; condTv.text = cond; cityTv.text = city?.let { "⦿ $it" } ?: "" }

    /** The currently playing media app (needs notification-listener access; null if not granted / nothing playing). */
    private fun activeController(): MediaController? = runCatching {
        val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val list = msm.getActiveSessions(ComponentName(ctx, MediaListener::class.java))
        list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
    }.getOrNull()

    private fun media(code: Int) {
        val m = activeController()
        if (m != null) when (code) {
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> return m.transportControls.skipToPrevious()
            KeyEvent.KEYCODE_MEDIA_NEXT -> return m.transportControls.skipToNext()
            else -> return if (m.playbackState?.state == PlaybackState.STATE_PLAYING) m.transportControls.pause() else m.transportControls.play()
        }
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)); am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }
}

/** A drawn vinyl record (our own artwork, factory-like). */
class VinylView(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(cv: Canvas) {
        val cx = width / 2f; val cy = height / 2f; val r = minOf(cx, cy)
        p.shader = RadialGradient(cx, cy, r, intArrayOf(Color.parseColor("#2A2A2E"), Color.parseColor("#0A0A0C")), null, Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r, p); p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.2f; p.color = Color.parseColor("#22FFFFFF")
        var rr = r * 0.95f; while (rr > r * 0.35f) { cv.drawCircle(cx, cy, rr, p); rr -= r * 0.06f }
        p.style = Paint.Style.FILL; p.color = Color.parseColor("#C0137E"); cv.drawCircle(cx, cy, r * 0.28f, p)
    }
}

/** A drawn sports-car side view + city glow for page 2 (our own artwork, factory-like). */
class CarView(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        // skyline glow
        p.shader = LinearGradient(0f, 0f, 0f, h, Color.parseColor("#002E7BFF"), Color.parseColor("#662E7BFF"), Shader.TileMode.CLAMP)
        var x = 0f; var i = 0
        while (x < w) { val bh = h * (0.25f + ((i * 37) % 50) / 100f); cv.drawRect(x, h - bh, x + 34f, h, p); x += 40f; i++ }
        p.shader = null
        // car body
        val body = Path().apply {
            moveTo(w * 0.12f, h * 0.82f); lineTo(w * 0.14f, h * 0.62f); cubicTo(w * 0.22f, h * 0.55f, w * 0.30f, h * 0.52f, w * 0.38f, h * 0.50f)
            cubicTo(w * 0.46f, h * 0.30f, w * 0.62f, h * 0.28f, w * 0.72f, h * 0.44f); cubicTo(w * 0.80f, h * 0.48f, w * 0.88f, h * 0.55f, w * 0.90f, h * 0.66f)
            lineTo(w * 0.90f, h * 0.82f); close()
        }
        p.shader = LinearGradient(0f, h * 0.3f, 0f, h * 0.85f, Color.parseColor("#B8C2D8"), Color.parseColor("#3A4258"), Shader.TileMode.CLAMP)
        cv.drawPath(body, p); p.shader = null
        p.color = Color.parseColor("#0E1220"); cv.drawCircle(w * 0.27f, h * 0.82f, h * 0.11f, p); cv.drawCircle(w * 0.76f, h * 0.82f, h * 0.11f, p)
        p.color = Color.parseColor("#FF3B4A"); cv.drawRect(w * 0.12f, h * 0.64f, w * 0.18f, h * 0.68f, p)
    }
}

/** The factory-style top row: 6 apps, saved per device; defaults = the factory's first page. */
object FactoryApps {
    private const val KEY = "factory_top"
    private val DEFAULTS = listOf("com.google.android.apps.maps", "com.tw.music", "com.google.android.youtube", "com.tw.radio", "com.tw.bt", "com.android.vending")
    fun slots(ctx: Context): MutableList<String?> =
        Apps.prefs(ctx).getString(KEY, null)?.split("|")?.map { it.ifEmpty { null } }?.toMutableList() ?: DEFAULTS.toMutableList()
    fun save(ctx: Context, s: List<String?>) = Apps.prefs(ctx).edit().putString(KEY, s.joinToString("|") { it ?: "" }).apply()
}

/** Apps offered as quick switches for the live window (owner picks with «+», long-press removes). Default: Maps + YouTube. */
object WindowApps {
    private const val KEY = "window_apps"
    fun list(ctx: Context): List<String> = (Apps.prefs(ctx).getString(KEY, null) ?: "com.google.android.apps.maps|com.google.android.youtube").split("|").filter { it.isNotEmpty() }
    fun add(ctx: Context, pkg: String) { val l = list(ctx); if (pkg !in l) Apps.prefs(ctx).edit().putString(KEY, (l + pkg).joinToString("|")).apply() }
    fun remove(ctx: Context, pkg: String) = Apps.prefs(ctx).edit().putString(KEY, list(ctx).filter { it != pkg }.joinToString("|")).apply()
}
