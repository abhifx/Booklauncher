package com.woody.booklauncher

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean

data class StockQuote(
    val symbol: String,
    val price: Double,
    val change: Double,
    val changePercent: Double,
) {
    val up: Boolean get() = change >= 0
}

/**
 * One symbol from Stooq's free CSV endpoint. Kept deliberately small: the widget
 * shows a price and a day change, not a whole portfolio.
 */
object Stocks {
    private const val PREFS = "feeds"
    private const val KEY_SYMBOL = "stock_symbol"
    private const val KEY_CACHE = "stock_cache"
    private const val KEY_AT = "stock_at"
    private const val KEY_FAILED = "stock_failed"
    private const val FRESH_MS = 15 * 60 * 1000L

    private val loading = AtomicBoolean(false)

    fun symbol(context: Context): String? =
        prefs(context).getString(KEY_SYMBOL, null)?.takeIf { it.isNotBlank() }

    fun setSymbol(context: Context, symbol: String) {
        val clean = symbol.trim().lowercase()
        prefs(context).edit().putString(KEY_SYMBOL, clean.ifEmpty { null })
            .remove(KEY_CACHE).remove(KEY_AT).remove(KEY_FAILED).apply()
        if (clean.isNotEmpty()) refresh(context, null)
    }

    fun cached(context: Context): StockQuote? {
        val raw = prefs(context).getString(KEY_CACHE, null) ?: return null
        return runCatching {
            val p = raw.split('|')
            StockQuote(p[0], p[1].toDouble(), p[2].toDouble(), p[3].toDouble())
        }.getOrNull()
    }

    fun isFresh(context: Context): Boolean =
        System.currentTimeMillis() - prefs(context).getLong(KEY_AT, 0L) < FRESH_MS

    fun failed(context: Context): Boolean = prefs(context).getBoolean(KEY_FAILED, false)

    fun refresh(context: Context, onDone: (() -> Unit)? = null) {
        val app = context.applicationContext
        val symbol = symbol(app) ?: return
        if (!loading.compareAndSet(false, true)) return
        Thread {
            val before = cached(app)
            val fresh = runCatching { fetch(symbol) }.getOrNull()
            if (fresh != null) {
                prefs(app).edit()
                    .putString(KEY_CACHE, listOf(fresh.symbol, fresh.price, fresh.change, fresh.changePercent).joinToString("|"))
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

    private fun fetch(symbol: String): StockQuote {
        val enc = URLEncoder.encode(symbol, "UTF-8")
        val o = org.json.JSONObject(get("https://query1.finance.yahoo.com/v8/finance/chart/$enc?range=1d&interval=1d"))
        val result = o.getJSONObject("chart").optJSONArray("result") ?: throw IllegalStateException("no quote")
        val meta = result.getJSONObject(0).getJSONObject("meta")
        val price = meta.getDouble("regularMarketPrice")
        val previous = meta.optDouble("chartPreviousClose", meta.optDouble("previousClose", price))
        val change = meta.optDouble("regularMarketChange", price - previous)
        val percent = meta.optDouble("regularMarketChangePercent", if (previous != 0.0) change / previous * 100 else 0.0)
        return StockQuote(meta.optString("symbol", symbol.uppercase()), price, change, percent)
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
