package dev.carlauncher

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.*

// On-device prayer times, Umm al-Qura method (Saudi official): Fajr 18.5°, Isha = Maghrib + 90 min, Asr = shadow factor 1.
// Algorithm after praytimes.org (sun declination + equation of time). No network needed.
object Prayer {
    data class Time(val name: Int, val minutes: Int) // name = string resource id; minutes after local midnight

    private fun dsin(d: Double) = sin(Math.toRadians(d)); private fun dcos(d: Double) = cos(Math.toRadians(d))
    private fun dtan(d: Double) = tan(Math.toRadians(d))
    private fun darcsin(x: Double) = Math.toDegrees(asin(x)); private fun darccos(x: Double) = Math.toDegrees(acos(x))
    private fun darctan2(y: Double, x: Double) = Math.toDegrees(atan2(y, x)); private fun darccot(x: Double) = Math.toDegrees(atan(1 / x))
    private fun fix(a: Double, b: Double): Double { val r = a - b * floor(a / b); return if (r < 0) r + b else r }

    private fun sun(jd: Double): Pair<Double, Double> { // declination, equation of time (hours)
        val d = jd - 2451545.0
        val g = fix(357.529 + 0.98560028 * d, 360.0); val q = fix(280.459 + 0.98564736 * d, 360.0)
        val l = fix(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g), 360.0); val e = 23.439 - 0.00000036 * d
        val ra = fix(darctan2(dcos(e) * dsin(l), dcos(l)) / 15, 24.0)
        return darcsin(dsin(e) * dsin(l)) to (q / 15 - ra)
    }

    fun today(lat: Double, lng: Double, cal: Calendar = Calendar.getInstance()): List<Time> {
        val y = cal.get(Calendar.YEAR); val m = cal.get(Calendar.MONTH) + 1; val dd = cal.get(Calendar.DAY_OF_MONTH)
        var yy = y; var mm = m; if (mm <= 2) { yy -= 1; mm += 12 }
        val a = floor(yy / 100.0); val b = 2 - a + floor(a / 4)
        val jd = floor(365.25 * (yy + 4716)) + floor(30.6001 * (mm + 1)) + dd + b - 1524.5 - lng / (15 * 24)
        val tz = TimeZone.getDefault().getOffset(cal.timeInMillis) / 3600000.0
        fun noon(t: Double) = fix(12 - sun(jd + t / 24).second, 24.0)
        fun angleTime(angle: Double, t: Double, ccw: Boolean): Double {
            val decl = sun(jd + t / 24).first
            val v = darccos((-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat))) / 15
            return noon(t) + if (ccw) -v else v
        }
        fun asr(t: Double): Double { val decl = sun(jd + t / 24).first; val ang = -darccot(1 + dtan(abs(lat - decl))); return angleTime(ang, t, false) }
        val fajr = angleTime(18.5, 5.0, true); val dhuhr = noon(12.0); val asr = asr(13.0)
        val maghrib = angleTime(0.833, 18.0, false); val isha = maghrib + 1.5
        fun toMin(h: Double) = ((h + tz - lng / 15) * 60).roundToInt().mod(1440)
        return listOf(Time(R.string.fajr, toMin(fajr)), Time(R.string.dhuhr, toMin(dhuhr)), Time(R.string.asr, toMin(asr)), Time(R.string.maghrib, toMin(maghrib)), Time(R.string.isha, toMin(isha)))
    }

    /** Next prayer and minutes left (wraps to tomorrow's Fajr). */
    fun next(lat: Double, lng: Double): Pair<Time, Int> {
        val now = Calendar.getInstance(); val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val list = today(lat, lng, now)
        val n = list.firstOrNull { it.minutes > nowMin }
        return if (n != null) n to (n.minutes - nowMin) else list[0] to (list[0].minutes + 1440 - nowMin)
    }
}
