package dev.carlauncher

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import java.util.Date

/**
 * Design 1 «طلبك» (adopted 2026-11-06) — native, framework Views only.
 * Top to bottom: time/date + RAM pill + all-apps + settings + Home + Back · 4 app tiles · speed / weather / prayer ·
 * Google Maps panel · media panel. Sized for his TS10S: 768 wide @160 dpi, 832 tall (976 with the climate bar hidden).
 */
class HomeActivity : Activity(), LocationListener {
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var timeTv: TextView; private lateinit var dateTv: TextView; private lateinit var ramTv: TextView
    private lateinit var speedTv: TextView; private lateinit var weatherTv: TextView; private lateinit var weatherSub: TextView
    private lateinit var prayerName: TextView; private lateinit var prayerLeft: TextView
    private lateinit var tiles: LinearLayout
    private var lat = 24.7136; private var lng = 46.6753 // Riyadh until the first GPS fix
    private var lastWeatherAt = 0L

    // Gregorian + Hijri (Umm al-Qura) with Latin digits — same as the approved prototype.
    private val hijriFmt = SimpleDateFormat("d MMMM", ULocale("ar_SA@calendar=islamic-umalqura;numbers=latn"))
    private val gregFmt = SimpleDateFormat("EEEE، d MMMM", ULocale("ar_SA@numbers=latn"))

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(build())
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
    }

    override fun onResume() { super.onResume(); startGps(); tick.run(); renderTiles() }
    override fun onPause() { super.onPause(); ui.removeCallbacks(tick); (getSystemService(LOCATION_SERVICE) as LocationManager).removeUpdates(this) }
    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, r: IntArray) { startGps() }
    // A home screen has nowhere to go back to — Back here never leaves the launcher.
    @Deprecated("Deprecated in Java") override fun onBackPressed() {}

    private fun build(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Pal.BG_TOP, Pal.BG_BOTTOM))
            setPadding(dp(14), dp(8), dp(14), dp(12))
        }
        root.addView(topRow(), lp(-1, dp(64)))
        tiles = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(tiles, lp(-1, dp(112)).apply { topMargin = dp(10) })
        root.addView(cardsRow(), lp(-1, dp(136)).apply { topMargin = dp(12) })
        root.addView(mapsPanel(), lp(-1, 0, 1f).apply { topMargin = dp(12) })
        root.addView(mediaPanel(), lp(-1, dp(150)).apply { topMargin = dp(12) })
        return root
    }

    private fun iconBtn(glyph: String, desc: String, light: Boolean = false, onClick: () -> Unit) = text(glyph, 30f, if (light) Color.BLACK else Pal.TEXT, gravity = Gravity.CENTER).apply {
        contentDescription = desc
        background = rounded(if (light) Color.WHITE else Color.parseColor("#1C2540"), dp(16).toFloat(), if (light) 0 else dp(1), Pal.CARD_LINE)
        setOnClickListener { onClick() }; pressable()
    }

    private fun topRow(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        val clock = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        timeTv = text("", 40f, Pal.TEXT, bold = true); dateTv = text("", SP_MIN, Pal.SUB)
        clock.addView(timeTv); clock.addView(dateTv)
        addView(clock, lp(0, -2, 1f))
        ramTv = text("", SP_MIN, Pal.TEXT, gravity = Gravity.CENTER).apply {
            background = rounded(Color.parseColor("#1C2540"), dp(28).toFloat(), dp(1), Pal.CARD_LINE)
            setPadding(dp(14), 0, dp(14), 0); setOnClickListener { startActivity(Intent(context, RamActivity::class.java)) }; pressable()
        }
        addView(ramTv, lp(-2, dp(56)).apply { marginEnd = dp(8) })
        addView(iconBtn("▦", "كل التطبيقات") { appDrawer() }, lp(dp(56), dp(56)).apply { marginEnd = dp(8) })
        addView(iconBtn("⚙", "الإعدادات") { settingsDialog() }, lp(dp(56), dp(56)).apply { marginEnd = dp(14) })
        // Home + Back on the driver side (far left in RTL), replacing the factory climate bar's keys (owner 2026-11-06).
        addView(iconBtn("⌂", "الرئيسية") { }, lp(dp(56), dp(56)).apply { marginEnd = dp(8) })
        addView(iconBtn("›", "رجوع", light = true) { }, lp(dp(56), dp(56)))
    }

    private fun renderTiles() {
        tiles.removeAllViews()
        val slots = Apps.slots(this)
        slots.forEachIndexed { i, pkg ->
            val e = Apps.entry(this, pkg)
            val tile = card().apply { gravity = Gravity.CENTER; setPadding(dp(6), dp(8), dp(6), dp(8)) }
            if (e != null) {
                tile.addView(ImageView(this).apply { setImageDrawable(e.icon) }, lp(dp(56), dp(56)))
                tile.addView(text(e.label, SP_MIN, Pal.TEXT, gravity = Gravity.CENTER).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, lp(-1, -2).apply { topMargin = dp(4) })
                tile.setOnClickListener { if (!Apps.launch(this, e.pkg)) toast("${e.label} ما ينفتح — يمكن انحذف") }
            } else {
                tile.addView(text("+", 40f, Pal.DIM, gravity = Gravity.CENTER)); tile.addView(text("إضافة", SP_MIN, Pal.DIM, gravity = Gravity.CENTER))
                tile.setOnClickListener { pickApp(i) }
            }
            // Long-press = change this tile (the owner can change the bottom/top 4 any time).
            tile.setOnLongClickListener { pickApp(i); true }
            tile.pressable()
            tiles.addView(tile, lp(0, -1, 1f).apply { if (i > 0) marginStart = dp(10) })
        }
    }

    private fun cardsRow(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        val speed = card().apply { gravity = Gravity.CENTER }
        speedTv = text("—", 56f, Pal.TEXT, bold = true, gravity = Gravity.CENTER); speed.addView(speedTv); speed.addView(text("كم/س", SP_MIN, Pal.DIM, gravity = Gravity.CENTER))
        addView(speed, lp(0, -1, 1f))
        val weather = card().apply { gravity = Gravity.CENTER_VERTICAL }
        weatherTv = text("—", 48f, Pal.TEXT, bold = true); weatherSub = text("الطقس", SP_MIN, Pal.SUB)
        weather.addView(weatherTv); weather.addView(weatherSub)
        addView(weather, lp(0, -1, 1.1f).apply { marginStart = dp(10) })
        val prayer = card().apply { gravity = Gravity.CENTER_VERTICAL }
        prayer.addView(text("الصلاة الجاية", SP_MIN, Pal.DIM)); prayerName = text("—", 40f, Pal.TEXT, bold = true); prayerLeft = text("", SP_MIN, Pal.ACCENT)
        prayer.addView(prayerName); prayer.addView(prayerLeft)
        addView(prayer, lp(0, -1, 1.6f).apply { marginStart = dp(10) })
    }

    // Phase 1: one big tap target that opens the REAL Google Maps full-screen (owner: real app, never a look-alike).
    // @TODO phase 2: host the real Google Maps app inside this panel as a freeform window sized to the panel — the exact
    // technique the factory launcher uses (Maps task mode=freeform, Rect(25,167–743,547)); needs enable_freeform_support=1 over ADB.
    private fun mapsPanel(): View = card().apply {
        gravity = Gravity.CENTER
        val maps = Apps.entry(context, "com.google.android.apps.maps")
        if (maps != null) addView(ImageView(context).apply { setImageDrawable(maps.icon) }, lp(dp(88), dp(88)))
        addView(text("قوقل ماب", 36f, Pal.TEXT, bold = true, gravity = Gravity.CENTER), lp(-1, -2).apply { topMargin = dp(10) })
        addView(text("اضغط يفتح بكامل الشاشة", SP_MIN, Pal.DIM, gravity = Gravity.CENTER))
        setOnClickListener { if (!Apps.launch(context, "com.google.android.apps.maps")) toast("قوقل ماب مو مثبّت") }
        pressable()
    }

    // Controls whatever is playing (YouTube, YMusic, BT, radio) by sending media keys — no permission needed.
    // @TODO phase 2: now-playing title/art via MediaSessionManager (notification-listener access, granted over ADB).
    private fun mediaPanel(): View = card().apply {
        gravity = Gravity.CENTER
        addView(text("اللي شغّال الحين", SP_MIN, Pal.DIM, gravity = Gravity.CENTER))
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; layoutDirection = View.LAYOUT_DIRECTION_LTR }
        fun key(glyph: String, desc: String, code: Int, big: Boolean = false) = text(glyph, if (big) 40f else 32f, if (big) Color.BLACK else Pal.TEXT, gravity = Gravity.CENTER).apply {
            contentDescription = desc; background = rounded(if (big) Color.WHITE else Color.parseColor("#1C2540"), dp(40).toFloat())
            setOnClickListener { mediaKey(code) }; pressable()
        }
        row.addView(key("⏮", "السابق", KeyEvent.KEYCODE_MEDIA_PREVIOUS), lp(dp(80), dp(80)))
        row.addView(key("⏯", "تشغيل/إيقاف", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, big = true), lp(dp(96), dp(96)).apply { marginStart = dp(28); marginEnd = dp(28) })
        row.addView(key("⏭", "التالي", KeyEvent.KEYCODE_MEDIA_NEXT), lp(dp(80), dp(80)))
        addView(row, lp(-1, -2).apply { topMargin = dp(6) })
    }

    private fun mediaKey(code: Int) {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)); am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private val tick = object : Runnable {
        override fun run() {
            val now = Date()
            val h = SimpleDateFormat("h:mm", ULocale("en")).format(now); val ap = if (SimpleDateFormat("a", ULocale("en")).format(now) == "AM") "ص" else "م"
            timeTv.text = "$h $ap"; dateTv.text = "${gregFmt.format(now)} · ${hijriFmt.format(now)}"
            val (p, left) = Prayer.next(lat, lng)
            prayerName.text = p.name; prayerLeft.text = "بعد ${left / 60}:${"%02d".format(left % 60)}"
            val r = Stats.ram(this@HomeActivity); val cpu = Stats.cpuPct()
            ramTv.text = if (cpu != null) "رام ${r.usedPct}% · معالج $cpu%" else "رام ${r.usedPct}%"
            if (System.currentTimeMillis() - lastWeatherAt > 30 * 60_000) refreshWeather()
            ui.postDelayed(this, 2000)
        }
    }

    private fun refreshWeather() {
        lastWeatherAt = System.currentTimeMillis()
        val la = lat; val ln = lng
        Thread {
            val w = Weather.fetch(la, ln)
            ui.post { if (w != null) { weatherTv.text = "${w.temp}°"; weatherSub.text = "${Weather.label(w.code)} · ${w.max}°/${w.min}°" } else weatherSub.text = "الطقس — ما فيه نت" }
        }.start()
    }

    @SuppressLint("MissingPermission")
    private fun startGps() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        runCatching { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this) }
        (lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER))?.let { lat = it.latitude; lng = it.longitude }
    }

    override fun onLocationChanged(l: Location) {
        val moved = Math.abs(l.latitude - lat) > 0.05 || Math.abs(l.longitude - lng) > 0.05
        lat = l.latitude; lng = l.longitude
        speedTv.text = if (l.hasSpeed()) "${(l.speed * 3.6f).toInt()}" else "—"
        if (moved) refreshWeather()
    }

    // ---------- dialogs ----------
    private fun sheet(title: String, body: View): Dialog = Dialog(this).apply {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = rounded(Color.parseColor("#0F1730"), dp(28).toFloat(), dp(1), Pal.CARD_LINE); setPadding(dp(20), dp(18), dp(20), dp(18))
        }
        val head = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(text(title, 32f, Pal.TEXT, bold = true), lp(0, -2, 1f))
        head.addView(iconBtn("✕", "إغلاق") { dismiss() }, lp(dp(56), dp(56)))
        box.addView(head); box.addView(body, lp(-1, -2).apply { topMargin = dp(12) })
        setContentView(box)
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.setLayout(dp(720), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun appGrid(onPick: (AppEntry) -> Unit): View {
        val grid = GridLayout(this).apply { columnCount = 4 }
        Apps.all(this).forEach { a ->
            val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(6), dp(10), dp(6), dp(10)) }
            cell.addView(ImageView(this).apply { setImageDrawable(a.icon) }, lp(dp(60), dp(60)))
            cell.addView(text(a.label, SP_MIN, Pal.TEXT, gravity = Gravity.CENTER).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            cell.setOnClickListener { onPick(a) }; cell.pressable()
            grid.addView(cell, GridLayout.LayoutParams().apply { width = dp(160); height = ViewGroup.LayoutParams.WRAP_CONTENT })
        }
        return ScrollView(this).apply { addView(grid); layoutParams = ViewGroup.LayoutParams(-1, dp(620)) }
    }

    private fun appDrawer() { lateinit var d: Dialog; d = sheet("كل التطبيقات", appGrid { a -> d.dismiss(); if (!Apps.launch(this, a.pkg)) toast("${a.label} ما ينفتح") }); d.show() }

    private fun pickApp(slot: Int) {
        lateinit var d: Dialog
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(text("إزالة التطبيق من هالخانة", SP_MIN, Pal.DANGER).apply {
            setPadding(dp(4), dp(6), dp(4), dp(12)); setOnClickListener { val s = Apps.slots(this@HomeActivity); s[slot] = null; Apps.saveSlots(this@HomeActivity, s); d.dismiss(); renderTiles() }
        })
        body.addView(appGrid { a -> val s = Apps.slots(this); s[slot] = a.pkg; Apps.saveSlots(this, s); d.dismiss(); renderTiles() })
        d = sheet("اختر تطبيق للخانة ${slot + 1}", body); d.show()
    }

    private fun settingsDialog() {
        lateinit var d: Dialog
        val p = Apps.prefs(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun row(title: String, sub: String, onClick: () -> Unit) = body.addView(card().apply {
            addView(text(title, 28f, Pal.TEXT, bold = true)); addView(text(sub, SP_MIN, Pal.DIM)); setOnClickListener { onClick() }; pressable()
        }, lp(-1, -2).apply { bottomMargin = dp(10) })
        row("الذاكرة والسرعة", "وش ياكل الرام، وأوقفه بضغطة") { d.dismiss(); startActivity(Intent(this, RamActivity::class.java)) }
        val mini = p.getBoolean("mini_stats", true)
        row("مؤشر الرام والمعالج فوق التطبيقات: ${if (mini) "شغّال" else "طافي"}", "صغير جنب أزرار الرجوع والرئيسية العائمة") { p.edit().putBoolean("mini_stats", !mini).apply(); d.dismiss(); settingsDialog() }
        row("أزرار «رجوع» و«الرئيسية» فوق أي تطبيق", if (FloatService.running) "شغّالة ✓" else "طافية — اضغط لتفعيلها من إمكانية الوصول") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        row("ترتيب التطبيقات", "اضغط مطوّل على أي تطبيق من الأربعة وبدّله") { d.dismiss() }
        // Several designs ship over time; design 1 «طلبك» is the first (owner 2026-11-06).
        row("التصميم: «طلبك» ✓", "تصاميم ثانية بتنضاف هنا") { }
        d = sheet("الإعدادات", ScrollView(this).apply { addView(body) }); d.show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
