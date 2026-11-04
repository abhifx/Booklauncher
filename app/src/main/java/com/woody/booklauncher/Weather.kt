package com.woody.booklauncher

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean

/** A small weather snapshot for the home widget. No API key: Open-Meteo is free. */
data class WeatherState(
    val city: String,
    val temperature: Int,
    val high: Int,
    val low: Int,
    val code: Int,
    val description: String,
) {
    /** Which simple line icon to draw. */
    val icon: WeatherIcon get() = WeatherIcon.of(code)
}

enum class WeatherIcon {
    SUN, PARTLY, CLOUD, RAIN, SNOW, STORM, FOG;

    companion object {
        fun of(code: Int): WeatherIcon = when (code) {
            0, 1 -> SUN
            2 -> PARTLY
            3, 45, 48 -> CLOUD
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> RAIN
            71, 73, 75, 77, 85, 86 -> SNOW
            95, 96, 99 -> STORM
            else -> CLOUD
        }
    }
}

object Weather {
    private const val PREFS = "weather"
    private const val KEY_CITY = "city"
    private const val KEY_LAT = "lat"
    private const val KEY_LON = "lon"
    private const val KEY_CACHE = "cache"
    private const val KEY_AT = "at"
    private const val KEY_FAILED = "failed"
    private const val FRESH_MS = 30 * 60 * 1000L

    private val loading = AtomicBoolean(false)

    fun city(context: Context): String? =
        prefs(context).getString(KEY_CITY, null)?.takeIf { it.isNotBlank() }

    fun setCity(context: Context, city: String) {
        val clean = city.trim()
        prefs(context).edit().putString(KEY_CITY, clean.ifEmpty { null })
            .remove(KEY_LAT).remove(KEY_LON).remove(KEY_CACHE).remove(KEY_AT).remove(KEY_FAILED).apply()
        if (clean.isNotEmpty()) refresh(context, null)
    }

    fun cached(context: Context): WeatherState? {
        val sp = prefs(context)
        if (city(context) == null) return null
        val raw = sp.getString(KEY_CACHE, null) ?: return null
        val wanted = city(context) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            if (o.getString("city") != wanted) return null
            WeatherState(
                city = o.getString("city"),
                temperature = o.getInt("t"),
                high = o.getInt("h"),
                low = o.getInt("l"),
                code = o.getInt("code"),
                description = o.getString("d"),
            )
        }.getOrNull()
    }

    fun isFresh(context: Context): Boolean =
        System.currentTimeMillis() - prefs(context).getLong(KEY_AT, 0L) < FRESH_MS

    fun failed(context: Context): Boolean = prefs(context).getBoolean(KEY_FAILED, false)

    /**
     * Refreshes the cache in the background. [onDone] is called on the calling
     * thread only for a real change, so the e-ink screen is not refreshed for
     * every network attempt.
     */
    fun refresh(context: Context, onDone: (() -> Unit)? = null) {
        val app = context.applicationContext
        val city = city(app) ?: return
        if (!loading.compareAndSet(false, true)) return
        Thread {
            val before = cached(app)
            val fresh = runCatching { fetch(app, city) }.getOrNull()
            if (fresh != null) {
                prefs(app).edit()
                    .putString(KEY_CACHE, JSONObject().apply {
                        put("city", fresh.city)
                        put("t", fresh.temperature)
                        put("h", fresh.high)
                        put("l", fresh.low)
                        put("code", fresh.code)
                        put("d", fresh.description)
                    }.toString())
                    .putLong(KEY_AT, System.currentTimeMillis())
                    .putBoolean(KEY_FAILED, false)
                    .apply()
            } else {
                prefs(app).edit()
                    .putBoolean(KEY_FAILED, true)
                    .putLong(KEY_AT, System.currentTimeMillis())
                    .apply()
            }
            loading.set(false)
            // The city changed while this request was in flight: ignore the stale
            // answer and fetch the new city instead.
            if (city(app) != city) {
                refresh(app, onDone)
                return@Thread
            }
            if (fresh != null && fresh != before) onDone?.invoke()
        }.start()
    }

    private fun fetch(context: Context, city: String): WeatherState {
        val sp = prefs(context)
        var lat = sp.getString(KEY_LAT, null)?.toDoubleOrNull()
        var lon = sp.getString(KEY_LON, null)?.toDoubleOrNull()
        if (lat == null || lon == null) {
            val q = URLEncoder.encode(city, "UTF-8")
            val geo = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=en&format=json"))
            val first = geo.optJSONArray("results")?.optJSONObject(0)
                ?: throw IllegalStateException("city not found")
            lat = first.getDouble("latitude")
            lon = first.getDouble("longitude")
            sp.edit().putString(KEY_LAT, lat.toString()).putString(KEY_LON, lon.toString()).apply()
        }
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
            "&timezone=auto&forecast_days=1"
        val o = JSONObject(get(url))
        val current = o.getJSONObject("current")
        val daily = o.getJSONObject("daily")
        val code = current.optInt("weather_code", 0)
        return WeatherState(
            city = city,
            temperature = Math.round(current.getDouble("temperature_2m")).toInt(),
            high = Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)).toInt(),
            low = Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)).toInt(),
            code = code,
            description = describe(code),
        )
    }

    private fun get(url: String): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "BookLauncher")
        }
        try {
            if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun prefs(context: Context) =
        Storage.of(context).getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** WMO weather code → a short label. */
    fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55, 56, 57 -> "Drizzle"
        61, 63, 65, 66, 67 -> "Rain"
        71, 73, 75, 77 -> "Snow"
        80, 81, 82 -> "Showers"
        85, 86 -> "Snow showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Weather"
    }
}
