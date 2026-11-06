package dev.carlauncher

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Weather from Open-Meteo (free, no API key). Fetched at most every 30 min, off the UI thread.
object Weather {
    data class Now(val temp: Int, val code: Int, val max: Int, val min: Int)

    fun fetch(lat: Double, lng: Double): Now? = runCatching {
        val u = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lng&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1")
        val c = (u.openConnection() as HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 8000 }
        val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        val cur = j.getJSONObject("current"); val d = j.getJSONObject("daily")
        Now(cur.getDouble("temperature_2m").toInt(), cur.getInt("weather_code"),
            d.getJSONArray("temperature_2m_max").getDouble(0).toInt(), d.getJSONArray("temperature_2m_min").getDouble(0).toInt())
    }.getOrNull()

    // WMO weather codes → string resource (Arabic/English).
    fun label(code: Int): Int = when (code) {
        0 -> R.string.w_clear; 1, 2 -> R.string.w_partly; 3 -> R.string.w_cloudy; 45, 48 -> R.string.w_fog
        in 51..67 -> R.string.w_rain; in 71..77 -> R.string.w_snow; in 80..82 -> R.string.w_showers; in 95..99 -> R.string.w_storm
        else -> R.string.w_unknown
    }
}
