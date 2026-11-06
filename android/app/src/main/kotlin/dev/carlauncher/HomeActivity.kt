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
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import java.util.Date

/**
 * Design 1 «طلبك» (adopted 2026-11-06) — native, framework Views only, Arabic + English.
 * Top to bottom: time/date + RAM pill + all-apps + settings + Home + Back · 4 app tiles · speed / weather / prayer ·
 * Google Maps panel · media panel. Every block can be hidden in Settings (rebuilt on resume).
 * Sized for his TS10S: 768 wide @160 dpi, 832 tall (976 with the climate bar hidden).
 */
class HomeActivity : Activity(), LocationListener {
    private val ui = Handler(Looper.getMainLooper())
    private var timeTv: TextView? = null; private var dateTv: TextView? = null; private var ramTv: TextView? = null
    private var speedTv: TextView? = null; private var weatherTv: TextView? = null; private var weatherSub: TextView? = null
    private var prayerName: TextView? = null; private var prayerLeft: TextView? = null
    private var tiles: LinearLayout? = null
    private var lat = 24.7136; private var lng = 46.6753 // Riyadh until the first GPS fix
    private var lastWeatherAt = 0L
    private var builtLang = ""
    private var lastWeather: Weather.Now? = null
    private var city: String? = null
    // Designs: «factory» = copy of the Dofun home (default, v0.2.0) · «talabak» = design 1 from the prototype.
    private var factory: FactoryHome? = null
    private fun design() = Apps.prefs(this).getString("design", "factory") ?: "factory"

    override fun attachBaseContext(base: Context) = super.attachBaseContext(Lang.wrap(base))

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        builtLang = Lang.code(this)
        KeepAlive.ensureFloat(this)
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
    }

    override fun onResume() {
        super.onResume()
        // Language changed in Settings → recreate so resources + layout direction follow it.
        if (Lang.code(this) != builtLang) { recreate(); return }
        render(); startGps(); tick.run()
    }
    override fun onPause() { super.onPause(); ui.removeCallbacks(tick); (getSystemService(LOCATION_SERVICE) as LocationManager).removeUpdates(this) }
    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, r: IntArray) { startGps() }
    // A home screen has nowhere to go back to — Back here never leaves the launcher.
    @Deprecated("Deprecated in Java") override fun onBackPressed() {}

    private fun render() {
        if (design() == "factory") {
            factory = FactoryHome(this).also { setContentView(it.build()) }
            showWeather()
            // NO auto-launch (v0.3.3, owner: «يفتح على طول كأنه أوتو أوبن»): on his unit the platform refuses freeform for a
            // normal app (verified: request made, app still opened full-screen — even with enable_freeform_support=1 + reboot),
            // so auto-launching just covered the home screen. The app opens only when the owner taps the window box.
        } else { factory = null; setContentView(build()); renderTiles(); showWeather() }
    }

    // ---------- factory design hooks ----------
    fun launchLive() {
        val f = factory ?: return
        val r = f.mapRect() ?: return
        val pkg = Panels.get(this, Panels.TOP) ?: return
        if (!LiveWindow.launch(this, pkg, r)) toast(s(R.string.app_wont_open, Apps.entry(this, pkg)?.label ?: pkg))
    }
    fun rebuildAndLaunch() = render()
    fun openFull(pkg: String?) { if (pkg == null || !LiveWindow.openFull(this, pkg)) toast(s(R.string.maps_missing)) }
    fun pickFactoryApp(slot: Int) {
        lateinit var d: Dialog
        d = sheet(s(R.string.pick_for_slot, slot + 1), appGrid { a -> val sl = FactoryApps.slots(this); sl[slot] = a.pkg; FactoryApps.save(this, sl); d.dismiss(); render() })
        d.show()
    }
    fun pickPanelPublic(slot: String) = pickPanel(slot)
    fun pickBottomCard(slot: Int) {
        if (slot < 0) return
        lateinit var d: Dialog
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        BottomCards.ALL.forEach { type ->
            body.addView(text(s(BottomCards.label(type)), 26f, Pal.TEXT).apply {
                setPadding(dp(8), dp(14), dp(8), dp(14)); setOnClickListener { BottomCards.set(this@HomeActivity, slot, type); d.dismiss(); render() }; pressable()
            })
        }
        d = sheet(s(R.string.pick_card), body); d.show()
    }
    fun addWindowApp() {
        lateinit var d: Dialog
        d = sheet(s(R.string.add_window_app), appGrid { a -> WindowApps.add(this, a.pkg); Panels.set(this, Panels.TOP, a.pkg); d.dismiss(); render() })
        d.show()
    }

    private fun build(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Pal.BG_TOP, Pal.BG_BOTTOM))
            setPadding(dp(14), dp(8), dp(14), dp(12))
        }
        timeTv = null; dateTv = null; ramTv = null; speedTv = null; weatherTv = null; weatherSub = null; prayerName = null; prayerLeft = null; tiles = null
        root.addView(topRow(), lp(-1, dp(64)))
        if (Show.on(this, Show.APPS)) {
            tiles = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            root.addView(tiles, lp(-1, dp(112)).apply { topMargin = dp(10) })
        }
        cardsRow()?.let { root.addView(it, lp(-1, dp(136)).apply { topMargin = dp(12) }) }
        if (Show.on(this, Show.MAPS)) root.addView(appPanel(Panels.TOP, media = false), lp(-1, 0, 1f).apply { topMargin = dp(12) })
        else root.addView(View(this), lp(-1, 0, 1f)) // keeps the bottom panel at the bottom when the top one is hidden
        if (Show.on(this, Show.MEDIA)) root.addView(appPanel(Panels.BOTTOM, media = true), lp(-1, dp(190)).apply { topMargin = dp(12) })
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
        timeTv = text("", 40f, Pal.TEXT, bold = true); clock.addView(timeTv)
        if (Show.on(context, Show.DATE) || Show.on(context, Show.HIJRI)) { dateTv = text("", SP_MIN, Pal.SUB); clock.addView(dateTv) }
        addView(clock, lp(0, -2, 1f))
        if (Show.on(context, Show.RAM)) {
            ramTv = text("", SP_MIN, Pal.TEXT, gravity = Gravity.CENTER).apply {
                background = rounded(Color.parseColor("#1C2540"), dp(28).toFloat(), dp(1), Pal.CARD_LINE)
                setPadding(dp(14), 0, dp(14), 0); setOnClickListener { startActivity(Intent(context, RamActivity::class.java)) }; pressable()
            }
            addView(ramTv, lp(-2, dp(56)).apply { marginEnd = dp(8) })
        }
        addView(iconBtn("▦", s(R.string.all_apps)) { appDrawer() }, lp(dp(56), dp(56)).apply { marginEnd = dp(8) })
        addView(iconBtn("⚙", s(R.string.settings)) { startActivity(Intent(context, SettingsActivity::class.java)) }, lp(dp(56), dp(56)).apply { marginEnd = dp(14) })
        // Home + Back on the driver side, replacing the factory climate bar's keys (owner 2026-11-06).
        // Back's arrow follows the reading direction (› in Arabic, ‹ in English).
        val rtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        addView(iconBtn("⌂", s(R.string.home)) { }, lp(dp(56), dp(56)).apply { marginEnd = dp(8) })
        addView(iconBtn(if (rtl) "›" else "‹", s(R.string.back), light = true) { }, lp(dp(56), dp(56)))
    }

    private fun renderTiles() {
        val row = tiles ?: return
        row.removeAllViews()
        Apps.slots(this).forEachIndexed { i, pkg ->
            val e = Apps.entry(this, pkg)
            val tile = card().apply { gravity = Gravity.CENTER; setPadding(dp(6), dp(8), dp(6), dp(8)) }
            if (e != null) {
                tile.addView(ImageView(this).apply { setImageDrawable(e.icon) }, lp(dp(56), dp(56)))
                tile.addView(text(e.label, SP_MIN, Pal.TEXT, gravity = Gravity.CENTER).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, lp(-1, -2).apply { topMargin = dp(4) })
                tile.setOnClickListener { if (!Apps.launch(this, e.pkg)) toast(s(R.string.app_wont_open, e.label)) }
            } else {
                tile.addView(text("+", 40f, Pal.DIM, gravity = Gravity.CENTER)); tile.addView(text(s(R.string.add), SP_MIN, Pal.DIM, gravity = Gravity.CENTER))
                tile.setOnClickListener { pickApp(i) }
            }
            tile.setOnLongClickListener { pickApp(i); true } // long-press = change this tile
            tile.pressable()
            row.addView(tile, lp(0, -1, 1f).apply { if (i > 0) marginStart = dp(10) })
        }
    }

    private fun cardsRow(): View? {
        val sp = Show.on(this, Show.SPEED); val we = Show.on(this, Show.WEATHER); val pr = Show.on(this, Show.PRAYER)
        if (!sp && !we && !pr) return null
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            var first = true
            fun add(v: View, w: Float) { addView(v, lp(0, -1, w).apply { if (!first) marginStart = dp(10) }); first = false }
            if (sp) add(card().apply {
                gravity = Gravity.CENTER
                speedTv = text("—", 56f, Pal.TEXT, bold = true, gravity = Gravity.CENTER); addView(speedTv); addView(text(s(R.string.kmh), SP_MIN, Pal.DIM, gravity = Gravity.CENTER))
            }, 1f)
            if (we) add(card().apply {
                gravity = Gravity.CENTER_VERTICAL
                weatherTv = text("—", 48f, Pal.TEXT, bold = true); weatherSub = text(s(R.string.weather), SP_MIN, Pal.SUB); addView(weatherTv); addView(weatherSub)
            }, 1.1f)
            if (pr) add(card().apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(text(s(R.string.next_prayer), SP_MIN, Pal.DIM)); prayerName = text("—", 40f, Pal.TEXT, bold = true); prayerLeft = text("", SP_MIN, Pal.ACCENT)
                addView(prayerName); addView(prayerLeft)
            }, 1.6f)
        }
    }

    // The two big panels hold ANY app the owner picks (owner 2026-11-06: «مثل اللانشر الحالي، أحط أي تطبيق ينعرض — فكرة أسطورية»).
    // Defaults: Google Maps on top, YouTube below. Long-press a panel to change its app. Tap = open that real app full-screen.
    // @TODO phase 2: show the chosen app LIVE inside its panel as a freeform window sized to the panel — exactly what the
    // factory launcher does (its Maps task: mode=freeform, Rect(25,167–743,547)); needs enable_freeform_support=1 over ADB.
    private fun appPanel(slot: String, media: Boolean): View = card().apply {
        gravity = Gravity.CENTER
        val e = Apps.entry(context, Panels.get(context, slot))
        val head = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        if (e != null) head.addView(ImageView(context).apply { setImageDrawable(e.icon) }, lp(dp(if (media) 64 else 88), dp(if (media) 64 else 88)))
        val names = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(14), 0) }
        names.addView(text(e?.label ?: s(R.string.add), if (media) 30f else 36f, Pal.TEXT, bold = true))
        names.addView(text(s(R.string.panel_hint), SP_MIN, Pal.DIM))
        head.addView(names)
        addView(head)
        setOnClickListener { val pk = e?.pkg; if (pk == null) pickPanel(slot) else if (!Apps.launch(context, pk)) toast(s(R.string.app_wont_open, e.label)) }
        setOnLongClickListener { pickPanel(slot); true }
        pressable()
        // Media keys control whatever is playing (YouTube, YMusic, BT, radio) — no permission needed.
        // @TODO phase 2: now-playing title/art via MediaSessionManager (notification-listener access, granted over ADB).
        if (media) {
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; layoutDirection = View.LAYOUT_DIRECTION_LTR }
            fun key(glyph: String, desc: String, code: Int, big: Boolean = false) = text(glyph, if (big) 40f else 32f, if (big) Color.BLACK else Pal.TEXT, gravity = Gravity.CENTER).apply {
                contentDescription = desc; background = rounded(if (big) Color.WHITE else Color.parseColor("#1C2540"), dp(40).toFloat())
                setOnClickListener { mediaKey(code) }; pressable()
            }
            row.addView(key("⏮", s(R.string.prev), KeyEvent.KEYCODE_MEDIA_PREVIOUS), lp(dp(76), dp(76)))
            row.addView(key("⏯", s(R.string.play_pause), KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, big = true), lp(dp(88), dp(88)).apply { marginStart = dp(28); marginEnd = dp(28) })
            row.addView(key("⏭", s(R.string.next), KeyEvent.KEYCODE_MEDIA_NEXT), lp(dp(76), dp(76)))
            addView(row, lp(-1, -2).apply { topMargin = dp(10) })
        }
    }

    private fun pickPanel(slot: String) {
        lateinit var d: Dialog
        d = sheet(s(if (slot == Panels.TOP) R.string.pick_top else R.string.pick_bottom), appGrid { a -> Panels.set(this, slot, a.pkg); d.dismiss(); setContentView(build()); renderTiles(); showWeather() })
        d.show()
    }

    private fun mediaKey(code: Int) {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code)); am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun isArabic() = resources.configuration.locales[0].language == "ar"

    private val tick = object : Runnable {
        override fun run() {
            factory?.let { f ->
                f.tick(isArabic(), lat, lng)
                if (System.currentTimeMillis() - lastWeatherAt > 30 * 60_000) refreshWeather()
                ui.postDelayed(this, 2000); return
            }
            val now = Date()
            val h = SimpleDateFormat("h:mm", ULocale("en")).format(now)
            val pm = SimpleDateFormat("a", ULocale("en")).format(now) != "AM"
            timeTv?.text = "$h ${s(if (pm) R.string.pm else R.string.am)}"
            dateTv?.let { tv ->
                // Latin digits in both languages (agreed in the prototype). Hijri = Umm al-Qura.
                val base = if (isArabic()) "ar_SA" else "en"
                val parts = mutableListOf<String>()
                if (Show.on(this@HomeActivity, Show.DATE)) parts += SimpleDateFormat(if (isArabic()) "EEEE، d MMMM" else "EEEE, d MMMM", ULocale("$base@numbers=latn")).format(now)
                if (Show.on(this@HomeActivity, Show.HIJRI)) parts += SimpleDateFormat("d MMMM", ULocale("$base@calendar=islamic-umalqura;numbers=latn")).format(now)
                tv.text = parts.joinToString(" · ")
            }
            prayerName?.let { pn ->
                val (p, left) = Prayer.next(lat, lng)
                pn.text = s(p.name); prayerLeft?.text = s(R.string.in_time, "${left / 60}:${"%02d".format(left % 60)}")
            }
            ramTv?.let { rt ->
                val r = Stats.ram(this@HomeActivity); val cpu = Stats.cpuPct()
                rt.text = if (cpu != null) s(R.string.ram_cpu_pill, r.usedPct, cpu) else s(R.string.ram_pill, r.usedPct)
            }
            if (weatherTv != null && System.currentTimeMillis() - lastWeatherAt > 30 * 60_000) refreshWeather()
            ui.postDelayed(this, 2000)
        }
    }

    private fun refreshWeather() {
        lastWeatherAt = System.currentTimeMillis()
        val la = lat; val ln = lng
        Thread {
            val w = Weather.fetch(la, ln)
            // City name for the factory weather block (on-device Geocoder; null when offline).
            val c = runCatching { android.location.Geocoder(this, resources.configuration.locales[0]).getFromLocation(la, ln, 1)?.firstOrNull()?.let { it.subLocality ?: it.locality } }.getOrNull()
            ui.post { if (w != null) lastWeather = w; if (c != null) city = c; showWeather(offline = w == null) }
        }.start()
    }

    private fun showWeather(offline: Boolean = false) {
        val w = lastWeather
        if (w != null) factory?.onWeather(w.temp, s(Weather.label(w.code)), city)
        if (w != null) { weatherTv?.text = "${w.temp}°"; weatherSub?.text = "${s(Weather.label(w.code))} · ${w.max}°/${w.min}°" }
        else if (offline) weatherSub?.text = s(R.string.weather_offline)
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
        speedTv?.text = if (l.hasSpeed()) "${(l.speed * 3.6f).toInt()}" else "—"
        if (l.hasSpeed()) factory?.onSpeed((l.speed * 3.6f).toInt())
        if (moved && (weatherTv != null || factory != null)) refreshWeather()
    }

    // ---------- dialogs ----------
    private fun sheet(title: String, body: View): Dialog = Dialog(this).apply {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_LOCALE
            background = rounded(Color.parseColor("#0F1730"), dp(28).toFloat(), dp(1), Pal.CARD_LINE); setPadding(dp(20), dp(18), dp(20), dp(18))
        }
        val head = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(text(title, 32f, Pal.TEXT, bold = true), lp(0, -2, 1f))
        head.addView(iconBtn("✕", s(R.string.close)) { dismiss() }, lp(dp(56), dp(56)))
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

    private fun appDrawer() { lateinit var d: Dialog; d = sheet(s(R.string.all_apps), appGrid { a -> d.dismiss(); if (!Apps.launch(this, a.pkg)) toast(s(R.string.app_wont_open, a.label)) }); d.show() }

    private fun pickApp(slot: Int) {
        lateinit var d: Dialog
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(text(s(R.string.remove_from_slot), SP_MIN, Pal.DANGER).apply {
            setPadding(dp(4), dp(6), dp(4), dp(12)); setOnClickListener { val sl = Apps.slots(this@HomeActivity); sl[slot] = null; Apps.saveSlots(this@HomeActivity, sl); d.dismiss(); renderTiles() }
        })
        body.addView(appGrid { a -> val sl = Apps.slots(this); sl[slot] = a.pkg; Apps.saveSlots(this, sl); d.dismiss(); renderTiles() })
        d = sheet(s(R.string.pick_for_slot, slot + 1), body); d.show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
