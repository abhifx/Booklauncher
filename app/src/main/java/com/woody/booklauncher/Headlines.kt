package com.woody.booklauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/** A short Hacker News top list, the free "news feed" for the desktop widget. */
data class Headline(val title: String, val score: Int, val url: String)

object Headlines {
    private const val PREFS = "feeds"
    private const val KEY_CACHE = "headlines"
    private const val KEY_AT = "headlines_at"
    private const val KEY_FAILED = "headlines_failed"
    private const val FRESH_MS = 30 * 60 * 1000L
    private const val COUNT = 3

    private val loading = AtomicBoolean(false)

    fun cached(context: Context): List<Headline> {
        val raw = prefs(context).getString(KEY_CACHE, null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).mapNotNull { i ->
                runCatching {
                    val o = a.getJSONObject(i)
                    Headline(o.getString("title"), o.optInt("score"), o.optString("url"))
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    fun isFresh(context: Context): Boolean =
        System.currentTimeMillis() - prefs(context).getLong(KEY_AT, 0L) < FRESH_MS

    fun failed(context: Context): Boolean = prefs(context).getBoolean(KEY_FAILED, false)

    fun refresh(context: Context, onDone: (() -> Unit)? = null) {
        val app = context.applicationContext
        if (!loading.compareAndSet(false, true)) return
        Thread {
            val before = cached(app)
            val fresh = runCatching { fetch() }.getOrNull()
            if (fresh != null) {
                val a = JSONArray()
                fresh.forEach { h ->
                    a.put(JSONObject().apply { put("title", h.title); put("score", h.score); put("url", h.url) })
                }
                prefs(app).edit()
                    .putString(KEY_CACHE, a.toString())
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
            if (fresh != null && fresh != before) onDone?.invoke()
        }.start()
    }

    private fun fetch(): List<Headline> {
        val ids = JSONArray(get("https://hacker-news.firebaseio.com/v0/topstories.json"))
        return (0 until minOf(COUNT, ids.length())).mapNotNull { i ->
            runCatching {
                val o = JSONObject(get("https://hacker-news.firebaseio.com/v0/item/${ids.getLong(i)}.json"))
                Headline(
                    title = o.optString("title"),
                    score = o.optInt("score"),
                    url = o.optString("url"),
                )
            }.getOrNull()
        }.filter { it.title.isNotBlank() }
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
}
