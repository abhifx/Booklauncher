package com.woody.booklauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The widgets that ship with the launcher, modelled on the Licorice desktop:
 * clock, reading stats, the current book, music, calendar, quote, device status,
 * weather, recent books and the reading year.
 *
 * They are drawn as e-ink cards on the ⑯ Widgets screen. Android widgets from
 * other apps still live on the pages after these cards.
 */
enum class NativeWidget(val key: String, val title: Int, val wide: Boolean) {
    CLOCK("clock", R.string.native_clock, true),
    STATS("stats", R.string.native_stats, false),
    READING("reading", R.string.native_reading, false),
    MUSIC("music", R.string.native_music, true),
    CALENDAR("calendar", R.string.native_calendar, false),
    QUOTE("quote", R.string.native_quote, false),
    STATUS("status", R.string.native_status, false),
    WEATHER("weather", R.string.native_weather, false),
    HEADLINES("headlines", R.string.native_headlines, true),
    STOCKS("stocks", R.string.native_stocks, true),
    RECENT("recent", R.string.native_recent, true),
    YEAR("year", R.string.native_year, true),
    ;

    companion object {
        fun of(key: String?): NativeWidget? = entries.firstOrNull { it.key == key }
        val DEFAULT = listOf(CLOCK, STATS, READING, MUSIC, CALENDAR, QUOTE, STATUS, WEATHER, HEADLINES, STOCKS, RECENT, YEAR)
    }
}

/** Everything the cards need, read once per page so e-ink only redraws once. */
class WidgetData(context: Context) {
    val today: LocalDate = LocalDate.now()
    val shelf = runCatching { BookShelf.list(context) }.getOrDefault(emptyList())
    val days = runCatching {
        if (Storage.isUnlocked(context)) ReadingLog.days(context, ReadingLog.readerPackages(context)) else null
    }.getOrNull()
    val todayMs: Long = days?.total(today) ?: 0L
    val streak: Int = days?.let { ReadingLog.streak(it, today) } ?: 0
    val goalMin: Int = HomePrefs(context).readingGoalMin
    val goalMs: Long = goalMin * 60_000L
    val weather: WeatherState? = runCatching { Weather.cached(context) }.getOrNull()
    val headlines: List<Headline> = runCatching { Headlines.cached(context) }.getOrDefault(emptyList())
    val stock: StockQuote? = runCatching { Stocks.cached(context) }.getOrNull()
    val status: DeviceStatus = runCatching { DeviceStatus.read(context) }.getOrDefault(
        DeviceStatus(-1, false, false, false, false, false, 0),
    )
    var nowPlaying: NowPlaying? = runCatching { MediaNow.info(context) }.getOrNull()
    var mediaAccess: Boolean = runCatching { MediaNow.hasAccess(context) }.getOrDefault(false)
    val finishedThisYear: Int = runCatching {
        BookShelf.finishList(context).values.flatten().count { it.day.year == today.year }
    }.getOrDefault(0)
    val yearMs: Long = days?.let { d -> d.filterKeys { it.year == today.year }.keys.sumOf { d.total(it) } } ?: 0L

    fun week(): List<Pair<LocalDate, Long>> = (6 downTo 0).map { back ->
        val day = today.minusDays(back.toLong())
        day to (days?.total(day) ?: 0L)
    }
}

/** What a card does when tapped or long-pressed. */
class WidgetActions(
    val open: (NativeWidget) -> Unit,
    val menu: (NativeWidget, View) -> Unit,
    /** Called after a media button so the card can redraw with the new state. */
    val changed: () -> Unit = {},
    /** Open a specific book from the recent-books card. */
    val openBook: (ShelfBook) -> Unit = {},
)

object NativeWidgets {
    private const val PREFS = "widgets"
    private const val KEY = "native"
    /** Every card type this install has already seen, so a removed card is not re-added. */
    private const val KEY_KNOWN = "native_known"

    fun load(context: Context): MutableList<NativeWidget> {
        val sp = prefs(context)
        val saved = sp.getString(KEY, null)
        if (saved == null) {
            val first = NativeWidget.DEFAULT.toMutableList()
            sp.edit()
                .putString(KEY, first.joinToString(",") { it.key })
                .putString(KEY_KNOWN, NativeWidget.entries.joinToString(",") { it.key })
                .apply()
            return first
        }
        val list = saved.split(',')
            .mapNotNull { NativeWidget.of(it) }
            .distinct()
            .toMutableList()
        // A card added in a newer version appears once without wiping the user's
        // order or bringing back a card the user removed.
        val known = sp.getString(KEY_KNOWN, null)
            ?.split(',')?.filter { it.isNotEmpty() }?.toMutableSet()
            ?: list.map { it.key }.toMutableSet()
        val fresh = NativeWidget.entries.filter { it.key !in known }
        if (fresh.isNotEmpty()) {
            list += fresh
            known += fresh.map { it.key }
            sp.edit()
                .putString(KEY, list.joinToString(",") { it.key })
                .putString(KEY_KNOWN, known.joinToString(","))
                .apply()
        }
        return list
    }

    fun save(context: Context, list: List<NativeWidget>) {
        prefs(context).edit().putString(KEY, list.joinToString(",") { it.key }).apply()
    }

    fun add(context: Context, widget: NativeWidget) {
        val list = load(context)
        if (widget !in list) {
            list += widget
            save(context, list)
        }
    }

    fun remove(context: Context, widget: NativeWidget) {
        save(context, load(context) - widget)
    }

    fun move(context: Context, widget: NativeWidget, delta: Int) {
        val list = load(context)
        val from = list.indexOf(widget)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (to == from) return
        list.removeAt(from)
        list.add(to, widget)
        save(context, list)
    }

    private fun prefs(context: Context) =
        Storage.of(context).getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** One card, ready to drop into the dashboard grid. */
    fun card(context: Context, widget: NativeWidget, data: WidgetData, actions: WidgetActions): View = Ui.atBaseText {
        val card = CardFrame(context)
        card.addView(header(context, widget), LinearLayout.LayoutParams(MATCH, WRAP))
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        card.addView(body, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = context.dp(6) })
        when (widget) {
            NativeWidget.CLOCK -> clock(context, body, data)
            NativeWidget.STATS -> stats(context, body, data)
            NativeWidget.READING -> reading(context, body, data)
            NativeWidget.MUSIC -> music(context, body, data, actions.changed)
            NativeWidget.CALENDAR -> calendar(context, body, data)
            NativeWidget.QUOTE -> quote(context, body, data)
            NativeWidget.STATUS -> status(context, body, data)
            NativeWidget.WEATHER -> weather(context, body, data)
            NativeWidget.HEADLINES -> headlines(context, body, data)
            NativeWidget.STOCKS -> stocks(context, body, data)
            NativeWidget.RECENT -> recent(context, body, data, actions)
            NativeWidget.YEAR -> year(context, body, data)
        }
        card.setOnClickListener { actions.open(widget) }
        card.setOnLongClickListener { actions.menu(widget, card); true }
        card
    }

    private fun header(context: Context, widget: NativeWidget): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(context.text(context.getString(widget.title), 11.5f, Ui.bold, Ui.GRAY), LinearLayout.LayoutParams(0, WRAP, 1f))
        val hint = when (widget) {
            NativeWidget.WEATHER -> context.getString(if (Weather.city(context) == null) R.string.widget_setup else R.string.widget_city)
            NativeWidget.MUSIC -> context.getString(R.string.widget_controls)
            NativeWidget.STOCKS -> Stocks.symbol(context) ?: context.getString(R.string.widget_setup)
            NativeWidget.HEADLINES -> "Hacker News"
            else -> ""
        }
        if (hint.isNotEmpty()) addView(context.text(hint, 11f, Ui.regular, Ui.LIGHT_GRAY))
    }

    private fun clock(context: Context, body: LinearLayout, data: WidgetData) {
        val time = java.text.SimpleDateFormat(
            if (HomePrefs(context).clock24h) "HH:mm" else "h:mm",
            Locale.getDefault(),
        ).format(java.util.Date())
        body.addView(context.text(time, 52f, Ui.heavy), LinearLayout.LayoutParams(WRAP, 0, 1f).apply { gravity = Gravity.CENTER_VERTICAL })
        body.addView(
            context.text(
                data.today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())),
                15f, Ui.medium, Ui.GRAY,
            ),
        )
        body.addView(
            context.text(
                if (data.todayMs > 0) {
                    context.getString(R.string.widget_today_streak, context.duration(data.todayMs), data.streak)
                } else {
                    context.getString(R.string.widget_streak_only, data.streak)
                },
                13f, Ui.regular, Ui.LIGHT_GRAY,
            ),
            LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = context.dp(2) },
        )
    }

    private fun stats(context: Context, body: LinearLayout, data: WidgetData) {
        body.addView(context.text(context.duration(data.todayMs), 34f, Ui.heavy), LinearLayout.LayoutParams(WRAP, WRAP))
        val sub = context.getString(R.string.widget_streak_short, data.streak) +
            if (data.goalMs > 0) " · " + context.getString(R.string.widget_goal_of, context.duration(data.goalMs)) else ""
        body.addView(
            context.text(sub, 12f, Ui.medium, Ui.GRAY),
            LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = context.dp(1) },
        )
        if (data.goalMs > 0) {
            val frac = (data.todayMs.toFloat() / data.goalMs).coerceIn(0f, 1f)
            body.addView(BarView(context, frac), LinearLayout.LayoutParams(MATCH, context.dp(5)).apply { topMargin = context.dp(6) })
        }
        body.addView(WeekBars(context, data), LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = context.dp(6) })
    }

    private fun reading(context: Context, body: LinearLayout, data: WidgetData) {
        val book = data.shelf.firstOrNull()
        if (book == null) {
            body.addView(context.text(context.getString(R.string.widget_no_book), 15f, Ui.regular, Ui.GRAY, lines = 2))
            return
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val coverW = context.dp(44)
        row.addView(
            context.coverImage(BookShelf.cover(context, book, coverW)),
            LinearLayout.LayoutParams(coverW, (coverW * 1.5f).roundToInt()).apply { marginEnd = context.dp(10) },
        )
        val info = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        info.addView(context.text(book.title, 17f, Ui.bold, lines = 2))
        info.addView(
            context.text(
                listOfNotNull(book.author.takeIf { it.isNotBlank() }, appLabel(context, book.app)).joinToString(" · "),
                11f, Ui.regular, Ui.GRAY,
            ),
            LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = context.dp(2) },
        )
        if (book.chapter.isNotEmpty()) {
            info.addView(context.text(book.chapter, 12f, Ui.medium, Ui.GRAY), LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = context.dp(2) })
        }
        if (book.progress >= 0) {
            info.addView(BarView(context, book.progress / 100f), LinearLayout.LayoutParams(MATCH, context.dp(4)).apply { topMargin = context.dp(6) })
            info.addView(context.text(context.getString(R.string.progress_pct, book.progress), 11f, Ui.medium, Ui.GRAY), LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = context.dp(2) })
        }
        row.addView(info, LinearLayout.LayoutParams(0, WRAP, 1f))
        body.addView(row, LinearLayout.LayoutParams(MATCH, 0, 1f))
    }

    private fun music(context: Context, body: LinearLayout, data: WidgetData, changed: () -> Unit) {
        fun after(action: () -> Unit) {
            action()
            android.os.Handler(context.mainLooper).postDelayed({ changed() }, 250)
        }
        val now = data.nowPlaying
        val audio = runCatching { context.getSystemService(AudioManager::class.java)?.isMusicActive == true }.getOrDefault(false)
        val title = now?.title?.takeIf { it.isNotBlank() }
            ?: if (audio) context.getString(R.string.widget_audio_playing) else context.getString(R.string.widget_nothing_playing)
        body.addView(context.text(title, 17f, Ui.bold, lines = 2), LinearLayout.LayoutParams(MATCH, WRAP))
        val second = when {
            now?.artist?.isNotBlank() == true -> now.artist
            now != null -> now.appLabel
            else -> context.getString(if (data.mediaAccess) R.string.widget_no_session else R.string.widget_media_access_needed)
        }
        body.addView(
            context.text(second, 12f, Ui.medium, Ui.GRAY, lines = 1),
            LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = context.dp(2) },
        )
        if (now != null && now.minuteText().isNotEmpty()) {
            body.addView(
                context.text("${now.appLabel} · ${now.minuteText()}", 11f, Ui.regular, Ui.LIGHT_GRAY, lines = 1),
                LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = context.dp(1) },
            )
        }
        if (now != null && now.progress >= 0) {
            body.addView(BarView(context, now.progress), LinearLayout.LayoutParams(MATCH, context.dp(4)).apply { topMargin = context.dp(5) })
        }
        // Controls sit at the bottom of a tall card.
        body.addView(View(context), LinearLayout.LayoutParams(MATCH, 0, 1f))
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun key(icon: Int, label: Int, action: () -> Unit): CardIconButton =
            CardIconButton(context, icon, context.getString(label), action)
        val buttons = listOf(
            key(R.drawable.ic_media_previous, R.string.media_previous) { after { MediaNow.previous(context) } },
            key(R.drawable.ic_media_rewind, R.string.media_rewind) { after { MediaNow.seek(context, -10_000) } },
            key(if (now?.playing == true) R.drawable.ic_media_pause else R.drawable.ic_media_play, R.string.media_play_pause) { after { MediaNow.toggle(context) } },
            key(R.drawable.ic_media_forward, R.string.media_forward) { after { MediaNow.seek(context, 10_000) } },
            key(R.drawable.ic_media_next, R.string.media_next) { after { MediaNow.next(context) } },
        )
        buttons.forEachIndexed { i, b ->
            row.addView(b, LinearLayout.LayoutParams(0, context.dp(34), 1f).apply { if (i > 0) marginStart = context.dp(6) })
        }
        body.addView(row, LinearLayout.LayoutParams(MATCH, context.dp(34)).apply { topMargin = context.dp(6) })
    }

    private fun calendar(context: Context, body: LinearLayout, data: WidgetData) {
        body.addView(
            context.text(data.today.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())), 15f, Ui.bold),
            LinearLayout.LayoutParams(MATCH, WRAP),
        )
        body.addView(MonthDots(context, data), LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = context.dp(4) })
    }

    private fun quote(context: Context, body: LinearLayout, data: WidgetData) {
        val (quote, author) = Quotes.of(data.today)
        body.addView(
            context.text("“$quote”", 16f, Ui.medium, lines = 4),
            LinearLayout.LayoutParams(MATCH, 0, 1f).apply { gravity = Gravity.CENTER_VERTICAL },
        )
        body.addView(context.text("— $author", 12f, Ui.regular, Ui.GRAY), LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = context.dp(3) })
    }

    private fun status(context: Context, body: LinearLayout, data: WidgetData) {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(context.text(if (data.status.battery >= 0) "${data.status.battery}%" else "—", 36f, Ui.heavy))
        row.addView(
            context.text(if (data.status.charging) context.getString(R.string.widget_charging) else context.getString(R.string.widget_battery), 12f, Ui.regular, Ui.GRAY),
            LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = context.dp(8); bottomMargin = context.dp(6) },
        )
        body.addView(row)
        val line = listOfNotNull(
            context.getString(if (data.status.wifi) R.string.widget_wifi_on else R.string.widget_wifi_off),
            context.getString(
                when {
                    !data.status.bluetooth -> R.string.widget_bt_off
                    data.status.bluetoothConnected -> R.string.widget_bt_connected
                    else -> R.string.widget_bt_on
                },
            ),
            context.getString(if (data.status.light) R.string.widget_light_on else R.string.widget_light_off),
        ).joinToString(" · ")
        body.addView(context.text(line, 13f, Ui.medium, Ui.GRAY, lines = 2).apply { gravity = Gravity.CENTER_VERTICAL }, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = context.dp(6) })
    }

    private fun weather(context: Context, body: LinearLayout, data: WidgetData) {
        val w = data.weather
        if (w == null) {
            val city = Weather.city(context)
            val message = when {
                city == null -> R.string.widget_weather_setup
                Weather.failed(context) -> R.string.widget_weather_failed
                else -> R.string.widget_weather_waiting
            }
            body.addView(
                context.text(context.getString(message), 15f, Ui.regular, Ui.GRAY, lines = 2),
                LinearLayout.LayoutParams(MATCH, 0, 1f).apply { gravity = Gravity.CENTER_VERTICAL },
            )
            return
        }
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(WeatherArt(context, w.icon), LinearLayout.LayoutParams(context.dp(46), context.dp(46)).apply { marginEnd = context.dp(10) })
        val temp = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        temp.addView(context.text("${w.temperature}°", 36f, Ui.heavy))
        temp.addView(context.text(w.description, 13f, Ui.medium, Ui.GRAY))
        row.addView(temp, LinearLayout.LayoutParams(0, WRAP, 1f))
        body.addView(row, LinearLayout.LayoutParams(MATCH, 0, 1f))
        body.addView(
            context.text(context.getString(R.string.widget_weather_range, w.high, w.low, w.city), 11.5f, Ui.regular, Ui.GRAY),
            LinearLayout.LayoutParams(MATCH, WRAP),
        )
    }

    private fun headlines(context: Context, body: LinearLayout, data: WidgetData) {
        if (data.headlines.isEmpty()) {
            val message = if (Headlines.failed(context)) R.string.widget_headlines_failed else R.string.widget_headlines_waiting
            body.addView(
                context.text(context.getString(message), 15f, Ui.regular, Ui.GRAY),
                LinearLayout.LayoutParams(MATCH, 0, 1f).apply { gravity = Gravity.CENTER_VERTICAL },
            )
            return
        }
        data.headlines.take(3).forEachIndexed { i, h ->
            body.addView(
                context.text("${i + 1}  ${h.title}", 12.5f, if (i == 0) Ui.medium else Ui.regular, lines = 1),
                LinearLayout.LayoutParams(MATCH, 0, 1f).apply { gravity = Gravity.CENTER_VERTICAL },
            )
        }
    }

    private fun stocks(context: Context, body: LinearLayout, data: WidgetData) {
        val q = data.stock
        if (q == null) {
            val message = when {
                Stocks.symbol(context) == null -> R.string.widget_stock_setup
                Stocks.failed(context) -> R.string.widget_stock_failed
                else -> R.string.widget_stock_waiting
            }
            body.addView(
                context.text(context.getString(message), 15f, Ui.regular, Ui.GRAY, lines = 2),
                LinearLayout.LayoutParams(MATCH, 0, 1f).apply { gravity = Gravity.CENTER_VERTICAL },
            )
            return
        }
        body.addView(
            context.text(q.symbol, 12f, Ui.bold, Ui.GRAY),
            LinearLayout.LayoutParams(MATCH, WRAP),
        )
        val sign = if (q.up) "+" else ""
        body.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(context.text("%.2f".format(Locale.US, q.price), 34f, Ui.heavy))
            addView(
                context.text(
                    context.getString(R.string.widget_stock_change, "$sign${"%.2f".format(Locale.US, q.changePercent)}"),
                    13f, Ui.medium, if (q.up) Ui.BLACK else Ui.GRAY,
                ),
                LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = context.dp(2) },
            )
        }, LinearLayout.LayoutParams(MATCH, 0, 1f))
    }

    private fun recent(context: Context, body: LinearLayout, data: WidgetData, actions: WidgetActions) {
        val books = data.shelf.take(4)
        if (books.isEmpty()) {
            body.addView(context.text(context.getString(R.string.widget_no_book), 15f, Ui.regular, Ui.GRAY))
            return
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        books.forEachIndexed { i, book ->
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setOnClickListener { actions.openBook(book) }
            }
            val w = context.dp(52)
            cell.addView(context.coverImage(BookShelf.cover(context, book, w)), LinearLayout.LayoutParams(w, (w * 1.5f).roundToInt()))
            cell.addView(
                context.text(book.title, 10.5f, Ui.medium, lines = 1),
                LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = context.dp(3) },
            )
            row.addView(cell, LinearLayout.LayoutParams(0, WRAP, 1f).apply { if (i > 0) marginStart = context.dp(8) })
        }
        body.addView(row, LinearLayout.LayoutParams(MATCH, 0, 1f))
    }

    private fun year(context: Context, body: LinearLayout, data: WidgetData) {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        row.addView(context.text(data.finishedThisYear.toString(), 40f, Ui.heavy))
        row.addView(
            context.text(context.getString(R.string.widget_books_finished), 12f, Ui.medium, Ui.GRAY),
            LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = context.dp(8); bottomMargin = context.dp(7) },
        )
        body.addView(row)
        body.addView(
            context.text(context.getString(R.string.widget_year_time, data.today.year, context.duration(data.yearMs)), 12.5f, Ui.regular, Ui.GRAY),
            LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = context.dp(4) },
        )
        body.addView(YearBars(context, data), LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = context.dp(6) })
    }

    private fun appLabel(context: Context, pkg: String?): String? = pkg?.let {
        runCatching {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(it, 0)).toString()
        }.getOrNull()
    }

    private const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
    private const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
}

/**
 * White rounded card for a built-in widget. Painted in [dispatchDraw] (no background drawable) so
 * Crema's bleach filter is not involved and the card stands out from the grey canvas like the home cards.
 */
class CardFrame(context: Context) : LinearLayout(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.CARD }

    init {
        orientation = VERTICAL
        setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
        setWillNotDraw(false)
        isClickable = true
        isLongClickable = true
    }

    override fun dispatchDraw(canvas: Canvas) {
        val r = context.dpf(Ui.CARD_RADIUS_DP - 4)
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, fill)
        super.dispatchDraw(canvas)
    }
}

/** Thin horizontal progress line, 0..1. */
class BarView(context: Context, private val fraction: Float) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        paint.color = Ui.DIVIDER
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.color = Ui.BLACK
        canvas.drawRect(0f, 0f, width * fraction.coerceIn(0f, 1f), height.toFloat(), paint)
    }
}

/** Seven little bars for the last seven days. */
class WeekBars(context: Context, private val data: WidgetData) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = context.spf(9f)
        color = Ui.GRAY
        textAlign = Paint.Align.CENTER
        typeface = Ui.medium
    }

    override fun onDraw(canvas: Canvas) {
        val week = data.week()
        val maxMs = maxOf(week.maxOfOrNull { it.second } ?: 0L, data.goalMs.coerceAtLeast(1L))
        val labelH = context.dpf(12f)
        val barBottom = height - labelH
        val gap = width / 7f
        val barW = gap * 0.52f
        week.forEachIndexed { i, (day, ms) ->
            val h = ((barBottom - 2) * (ms.toFloat() / maxMs)).coerceAtLeast(if (ms > 0) context.dpf(2f) else 0f)
            val cx = gap * i + gap / 2
            paint.color = if (day == data.today) Ui.BLACK else Ui.LIGHT_GRAY
            canvas.drawRect(cx - barW / 2, barBottom - h, cx + barW / 2, barBottom, paint)
            canvas.drawText(day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), cx, height - 1f, label)
        }
    }
}

/** Twelve bars, one per month of the current year. */
class YearBars(context: Context, private val data: WidgetData) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = context.spf(8.5f)
        color = Ui.GRAY
        textAlign = Paint.Align.CENTER
        typeface = Ui.medium
    }

    override fun onDraw(canvas: Canvas) {
        val days = data.days
        val sums = (1..12).map { m ->
            days?.let { d -> d.filterKeys { it.year == data.today.year && it.monthValue == m }.keys.sumOf { d.total(it) } } ?: 0L
        }
        val maxMs = maxOf(sums.maxOrNull() ?: 0L, 60 * 60_000L)
        val labelH = context.dpf(12f)
        val bottom = height - labelH
        val gap = width / 12f
        val barW = gap * 0.54f
        val names = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
        sums.forEachIndexed { i, ms ->
            val cx = gap * i + gap / 2
            val h = ((bottom - 2) * (ms.toFloat() / maxMs)).coerceAtLeast(if (ms > 0) context.dpf(2f) else 0f)
            paint.color = if (i + 1 == data.today.monthValue) Ui.BLACK else Ui.LIGHT_GRAY
            canvas.drawRect(cx - barW / 2, bottom - h, cx + barW / 2, bottom, paint)
            canvas.drawText(names[i], cx, height - 1f, label)
        }
    }
}

/** A month of reading days: filled = read 5+ minutes, ring = goal reached, box = today. */
class MonthDots(context: Context, private val data: WidgetData) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpf(1f)
        color = Ui.BLACK
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = context.spf(9f)
        textAlign = Paint.Align.CENTER
        typeface = Ui.medium
    }

    override fun onDraw(canvas: Canvas) {
        val month = YearMonth.from(data.today)
        val first = month.atDay(1)
        val offset = first.dayOfWeek.value - 1
        val days = month.lengthOfMonth()
        val cols = 7
        val rows = 6
        val cw = width.toFloat() / cols
        val ch = height.toFloat() / rows
        val r = min(cw, ch) * 0.34f
        val header = context.spf(8f)
        for (d in 1..days) {
            val day = month.atDay(d)
            val index = offset + d - 1
            val cx = cw * (index % cols) + cw / 2
            val cy = ch * (index / cols) + ch / 2
            val ms = data.days?.total(day) ?: 0L
            val read = ms >= ReadingLog.MIN_DAY_MS
            val goal = data.goalMs > 0 && ms >= data.goalMs
            if (read) {
                fill.color = Ui.BLACK
                canvas.drawCircle(cx, cy, r, fill)
            }
            if (goal) {
                line.color = Ui.BLACK
                canvas.drawCircle(cx, cy, r + context.dpf(1.4f), line)
            }
            text.color = if (read) android.graphics.Color.WHITE else if (day == data.today) Ui.BLACK else Ui.GRAY
            text.typeface = if (day == data.today) Ui.bold else Ui.medium
            val baseline = cy - (text.descent() + text.ascent()) / 2
            canvas.drawText(d.toString(), cx, baseline, text)
        }
    }
}

/** A small line-art weather mark (no emoji fonts needed). */
class WeatherArt(context: Context, private val kind: WeatherIcon) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpf(1.8f)
        strokeCap = Paint.Cap.ROUND
        color = Ui.BLACK
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.BLACK }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2
        val cy = h / 2
        val r = min(w, h) * 0.22f
        when (kind) {
            WeatherIcon.SUN -> {
                canvas.drawCircle(cx, cy, r, p)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val dx = Math.cos(a).toFloat()
                    val dy = Math.sin(a).toFloat()
                    canvas.drawLine(cx + dx * (r + 4), cy + dy * (r + 4), cx + dx * (r + 9), cy + dy * (r + 9), p)
                }
            }
            WeatherIcon.PARTLY -> {
                canvas.drawCircle(cx - r * 0.5f, cy - r * 0.5f, r * 0.7f, p)
                cloud(canvas, cx + r * 0.2f, cy + r * 0.2f, r)
            }
            WeatherIcon.CLOUD -> cloud(canvas, cx, cy, r * 1.15f)
            WeatherIcon.RAIN -> {
                cloud(canvas, cx, cy - r * 0.3f, r)
                for (i in -1..1) canvas.drawLine(cx + i * r * 0.8f, cy + r * 1.2f, cx + i * r * 0.8f - 3, cy + r * 1.8f, p)
            }
            WeatherIcon.SNOW -> {
                cloud(canvas, cx, cy - r * 0.3f, r)
                for (i in -1..1) canvas.drawCircle(cx + i * r * 0.8f, cy + r * 1.5f, 2.2f, fill)
            }
            WeatherIcon.STORM -> {
                cloud(canvas, cx, cy - r * 0.3f, r)
                val path = android.graphics.Path().apply {
                    moveTo(cx + r * 0.2f, cy + r * 0.9f)
                    lineTo(cx - r * 0.3f, cy + r * 1.6f)
                    lineTo(cx + r * 0.2f, cy + r * 1.6f)
                    lineTo(cx - r * 0.2f, cy + r * 2.2f)
                }
                canvas.drawPath(path, p)
            }
            WeatherIcon.FOG -> {
                for (i in -1..1) canvas.drawLine(cx - r * 1.4f, cy + i * r * 0.8f, cx + r * 1.4f, cy + i * r * 0.8f, p)
            }
        }
    }

    private fun cloud(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val path = android.graphics.Path()
        path.moveTo(cx - r * 1.35f, cy + r * 0.55f)
        path.cubicTo(cx - r * 1.7f, cy - r * 0.2f, cx - r * 0.9f, cy - r * 0.75f, cx - r * 0.3f, cy - r * 0.4f)
        path.cubicTo(cx - r * 0.1f, cy - r * 1.3f, cx + r * 0.95f, cy - r * 1.1f, cx + r * 0.9f, cy - r * 0.15f)
        path.cubicTo(cx + r * 1.75f, cy - r * 0.1f, cx + r * 1.5f, cy + r * 0.9f, cx + r * 0.9f, cy + r * 0.9f)
        path.lineTo(cx - r * 0.9f, cy + r * 0.9f)
        path.cubicTo(cx - r * 1.6f, cy + r * 0.9f, cx - r * 1.7f, cy + r * 0.8f, cx - r * 1.35f, cy + r * 0.55f)
        path.close()
        canvas.drawPath(path, fill)
    }
}

/** Square bordered icon button used by the music card. */
class CardIconButton(
    context: Context,
    iconRes: Int,
    description: String,
    onClick: () -> Unit,
) : View(context) {
    private val icon: Drawable? = context.getDrawable(iconRes)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpf(1.2f)
        color = Ui.BLACK
    }
    private val rect = RectF()

    init {
        contentDescription = description
        isClickable = true
        setOnClickListener { onClick() }
    }

    override fun onDraw(canvas: Canvas) {
        val i = border.strokeWidth / 2
        rect.set(i, i, width - i, height - i)
        canvas.drawRoundRect(rect, context.dpf(6f), context.dpf(6f), border)
        val d = icon ?: return
        val size = min(width, height) * 0.46f
        val left = ((width - size) / 2f).roundToInt()
        val top = ((height - size) / 2f).roundToInt()
        d.setBounds(left, top, left + size.toInt(), top + size.toInt())
        d.draw(canvas)
    }
}
