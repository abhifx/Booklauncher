package com.woody.booklauncher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.max

/**
 * ⑯ Widgets: the Licorice-style desktop on the first pages, then Android widgets.
 *
 * The built-in cards (clock, reading stats, current book, music, calendar, quote,
 * device status, weather, recent books and the reading year) are paged as a neat
 * two-column grid. Any Android widget the user adds still gets a full page after
 * them. No animation; only the changed page is rebuilt.
 */
class WidgetsActivity : EinkActivity() {
    private lateinit var host: AppWidgetHost
    private lateinit var manager: AppWidgetManager
    private lateinit var body: FrameLayout
    private lateinit var pageText: TextView
    private lateinit var titleCount: TextView
    private lateinit var addText: TextView
    private lateinit var toolsText: TextView

    /** Android widget ids in page order, after the built-in pages. */
    private var ids: List<Int> = emptyList()
    private var index = 0
    private var pendingId = -1
    private var data: WidgetData? = null
    private var layoutReady = false
    private var nativePages: List<List<NativeWidget>> = emptyList()

    private val native get() = NativeWidgets.load(this)
    private val pageCount get() = max(1, nativePages.size + ids.size)

    private val actions = WidgetActions(
        open = ::openWidget,
        menu = ::widgetMenu,
        changed = ::reload,
        openBook = ::openBook,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        host = AppWidgetHost(this, HOST_ID)
        manager = AppWidgetManager.getInstance(this)
        ids = loadIds()
        buildViews()
        body.post {
            layoutReady = true
            reload()
        }
    }

    override fun onStart() {
        super.onStart()
        runCatching { host.startListening() }
    }

    override fun onStop() {
        runCatching { host.stopListening() }
        super.onStop()
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    /** Keeps the music card honest while the screen is open; redraws only on a real change. */
    private val musicPoller = object : Runnable {
        override fun run() {
            val d = data
            if (d != null) {
                val np = runCatching { MediaNow.info(this@WidgetsActivity) }.getOrNull()
                val access = runCatching { MediaNow.hasAccess(this@WidgetsActivity) }.getOrDefault(false)
                if (!samePlaying(d.nowPlaying, np) || d.mediaAccess != access) {
                    d.nowPlaying = np
                    d.mediaAccess = access
                    render()
                }
            }
            handler.postDelayed(this, 10_000)
        }
    }

    override fun onResume() {
        super.onResume()
        if (layoutReady) reload()
        handler.removeCallbacks(musicPoller)
        handler.postDelayed(musicPoller, 10_000)
    }

    override fun onPause() {
        handler.removeCallbacks(musicPoller)
        super.onPause()
    }

    private fun samePlaying(a: NowPlaying?, b: NowPlaying?): Boolean {
        if (a == null || b == null) return a == b
        return a.packageName == b.packageName && a.title == b.title && a.artist == b.artist &&
            a.playing == b.playing && a.durationMs == b.durationMs
    }

    private fun reload() {
        data = WidgetData(this)
        // Weather is the only card that needs the network. Refresh it quietly and
        // redraw only when a new snapshot arrives.
        if (Weather.city(this) != null && !Weather.isFresh(this)) {
            Weather.refresh(this) { runOnUiThread { if (!isFinishing) { data = WidgetData(this); render() } } }
        }
        if (!Headlines.isFresh(this)) {
            Headlines.refresh(this) { runOnUiThread { if (!isFinishing) { data = WidgetData(this); render() } } }
        }
        if (Stocks.symbol(this) != null && !Stocks.isFresh(this)) {
            Stocks.refresh(this) { runOnUiThread { if (!isFinishing) { data = WidgetData(this); render() } } }
        }
        render()
    }

    private fun buildViews() {
        val root = vbox()
        root.addView(titleRow(getString(R.string.widgets_title)) { finish() }.apply {
            titleCount = text("", 21f, color = Ui.GRAY)
            addView(titleCount, lp(WRAP, WRAP).apply { marginStart = dp(14); topMargin = dp(6) })
        })
        // 카드는 책 한 장씩(각 위젯 카드) 흰 둥근 면으로 그리고, 그 사이로 회색 바탕이 보이게 한다.
        body = FrameLayout(this).apply {
            setPadding(dp(Ui.CARD_GAP_DP), dp(8), dp(Ui.CARD_GAP_DP), dp(4))
        }
        root.addView(body, lp(MATCH, 0, 1f))
        root.addView(hline(1, inset = false))

        val footer = FrameLayout(this)
        addText = text(getString(R.string.widget_add), 19f, Ui.bold).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), 0, dp(10), 0)
            setOnClickListener { pickWidget() }
        }
        footer.addView(addText, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.START))
        toolsText = text(getString(R.string.mini_apps) + "  ›", 17f, Ui.medium, Ui.GRAY).apply {
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { startActivity(MiniAppsActivity.intent(this@WidgetsActivity)) }
        }
        footer.addView(toolsText, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.CENTER))
        pageText = text("", 19f).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(20), 0)
            pagesClick(object : Paged {
                override val page get() = index
                override val pageCount get() = this@WidgetsActivity.pageCount
                override fun next() { turn(1) }
                override fun prev() { turn(-1) }
            })
        }
        footer.addView(pageText, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.END))
        root.addView(footer, lp(MATCH, dp(Ui.FOOTER_DP)))
        setContentView(root)
    }

    private fun turn(delta: Int) {
        index = (index + delta).coerceIn(0, pageCount - 1)
        render()
    }

    override fun onSwipe(toLeft: Boolean): Boolean {
        if (pageCount <= 1) return false
        turn(if (toLeft) 1 else -1)
        return true
    }

    /**
     * 위젯 화면에서 아래로 밀면 홈으로 돌아간다(홈에서 위로 민 것의 반대). 안드로이드 위젯 쪽은
     * 위젯이 스크롤·누름을 받을 수 있어 그대로 두고, 기본 카드 쪽에서만 닫는다.
     */
    override fun onVerticalSwipe(up: Boolean): Boolean {
        if (up) return false
        if (ids.isNotEmpty() && index >= nativePages.size) return false
        finish()
        return true
    }

    override fun onPageKey(next: Boolean): Boolean {
        if (pageCount <= 1) return false
        turn(if (next) 1 else -1)
        return true
    }

    private fun render() {
        val widgets = native
        nativePages = paginate(widgets)
        index = index.coerceIn(0, pageCount - 1)
        titleCount.text = (widgets.size + ids.size).toString().takeIf { widgets.isNotEmpty() || ids.isNotEmpty() }.orEmpty()
        pageText.text = "${index + 1} / $pageCount"
        body.removeAllViews()

        if (index < nativePages.size) {
            body.addView(nativePage(nativePages[index]))
            return
        }
        val widgetIndex = index - nativePages.size
        val id = ids.getOrNull(widgetIndex)
        if (id == null) {
            body.addView(emptyPage())
            return
        }
        val info = manager.getAppWidgetInfo(id)
        if (info == null) {
            ids = ids - id
            saveIds()
            index = index.coerceAtMost(pageCount - 1)
            render()
            return
        }
        body.addView(widgetPage(id, info))
    }

    /** Pack cards into rows (wide cards take a whole row) and rows into pages. */
    private fun paginate(widgets: List<NativeWidget>): List<List<NativeWidget>> {
        val rowsPerPage = rowsPerPage()
        val rows = mutableListOf<List<NativeWidget>>()
        var i = 0
        while (i < widgets.size) {
            val w = widgets[i]
            if (w.wide) {
                rows += listOf(w)
                i++
            } else {
                val next = widgets.getOrNull(i + 1)
                if (next != null && !next.wide) {
                    rows += listOf(w, next)
                    i += 2
                } else {
                    rows += listOf(w)
                    i++
                }
            }
        }
        if (rows.isEmpty()) return emptyList()
        return rows.chunked(rowsPerPage).map { it.flatten() }
    }

    private fun rowsPerPage(): Int = (pageHeight() / dp(150)).coerceIn(2, 3)

    /** 카드가 들어갈 높이(화면 바탕 여백은 뺀다). 아직 재기 전이면 화면 높이로 어림한다. */
    private fun pageHeight(): Int =
        (body.height - body.paddingTop - body.paddingBottom).takeIf { it > 0 }
            ?: (resources.displayMetrics.heightPixels - dp(150))

    private fun nativePage(widgets: List<NativeWidget>): View {
        val column = vbox()
        val gap = dp(Ui.CARD_GAP_DP)
        val rows = mutableListOf<List<NativeWidget>>()
        var i = 0
        while (i < widgets.size) {
            val w = widgets[i]
            val next = widgets.getOrNull(i + 1)
            if (w.wide || next == null || next.wide) {
                rows += listOf(w)
                i++
            } else {
                rows += listOf(w, next)
                i += 2
            }
        }
        val height = pageHeight()
        val rowH = ((height - gap * (rows.size - 1)) / rows.size.coerceAtLeast(1))
            .coerceIn(dp(140), dp(240))
        // A short last page (one wide card, for example) sits in the middle instead
        // of hugging the top edge.
        val total = rowH * rows.size + gap * (rows.size - 1)
        column.setPadding(0, ((height - total) / 2).coerceAtLeast(0), 0, 0)
        val d = data ?: WidgetData(this).also { data = it }
        rows.forEachIndexed { r, row ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEachIndexed { c, widget ->
                val card = NativeWidgets.card(this, widget, d, actions)
                val params = if (row.size == 1) {
                    LinearLayout.LayoutParams(MATCH, MATCH)
                } else {
                    LinearLayout.LayoutParams(0, MATCH, 1f)
                }
                if (c > 0) params.marginStart = gap
                line.addView(card, params)
            }
            column.addView(line, LinearLayout.LayoutParams(MATCH, rowH).apply {
                if (r > 0) topMargin = gap
            })
        }
        return column
    }

    private fun emptyPage(): View = softCard().apply {
        addView(text(getString(R.string.widget_none), 22f, Ui.bold))
        addView(
            text(getString(R.string.widget_none_desc), 15f, color = Ui.GRAY, lines = 4),
            lp(MATCH, WRAP).apply { topMargin = dp(8) },
        )
    }

    // ---- card actions -------------------------------------------------------

    private fun openWidget(widget: NativeWidget) {
        when (widget) {
            NativeWidget.CLOCK -> startActivity(Intent(this, SettingsActivity::class.java))
            NativeWidget.STATS, NativeWidget.CALENDAR, NativeWidget.QUOTE ->
                startActivity(ReadingActivity.intent(this))
            NativeWidget.READING -> openFirstBook()
            NativeWidget.MUSIC -> showMediaPanel()
            NativeWidget.STATUS -> startActivity(DeviceInfoActivity.intent(this))
            NativeWidget.WEATHER -> askCity()
            NativeWidget.HEADLINES -> runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://news.ycombinator.com/")))
            }
            NativeWidget.STOCKS -> askSymbol()
            NativeWidget.RECENT -> startActivity(BooksActivity.intent(this))
            NativeWidget.YEAR -> startActivity(YearBooksActivity.intent(this))
        }
    }

    private fun openFirstBook() {
        val book = runCatching { BookShelf.list(this).firstOrNull() }.getOrNull()
            ?: return startActivity(BooksActivity.intent(this))
        openBook(book)
    }

    private fun openBook(book: ShelfBook) {
        BookShelf.moveToFront(this, book.id)
        val app = book.app?.let { AppStore.find(this, it) }
        if (app != null && AppStore.openBook(this, book, app)) return
        startActivity(BooksActivity.intent(this))
    }

    private fun askCity() {
        val current = Weather.city(this).orEmpty()
        Sheet(this).header(getString(R.string.weather_city_title), getString(R.string.weather_city_hint))
            .input(current) { city ->
                Weather.setCity(this, city)
                reload()
            }
            .show()
    }

    private fun askSymbol() {
        Sheet(this).header(getString(R.string.stock_symbol_title), getString(R.string.stock_symbol_hint))
            .input(Stocks.symbol(this).orEmpty()) { symbol ->
                Stocks.setSymbol(this, symbol)
                reload()
            }
            .show()
    }

    private fun widgetMenu(widget: NativeWidget, anchor: View) {
        val title = getString(widget.title)
        val sheet = Sheet(this).header(title, getString(R.string.widget_builtin))
        when (widget) {
            NativeWidget.WEATHER -> sheet.item(getString(R.string.widget_change_city), bold = true) { askCity() }
            NativeWidget.CLOCK -> sheet.item(getString(R.string.widget_clock_mode), bold = true) {
                prefs.clock24h = !prefs.clock24h
                reload()
            }
            NativeWidget.MUSIC -> sheet.item(getString(R.string.widget_media_controls), bold = true) { showMediaPanel() }
            NativeWidget.STOCKS -> sheet.item(getString(R.string.stock_symbol_title), bold = true) { askSymbol() }
            NativeWidget.HEADLINES -> sheet.item(getString(R.string.widget_refresh), bold = true) {
                Headlines.refresh(this) { runOnUiThread { data = WidgetData(this); render() } }
            }
            else -> Unit
        }
        sheet.item(getString(R.string.widget_move_left)) { move(widget, -1) }
        sheet.item(getString(R.string.widget_move_right)) { move(widget, 1) }
        sheet.item(getString(R.string.widget_remove), bold = true) {
            NativeWidgets.remove(this, widget)
            reload()
        }.show()
    }

    private fun move(widget: NativeWidget, delta: Int) {
        NativeWidgets.move(this, widget, delta)
        reload()
    }

    private fun showMediaPanel() {
        val now = runCatching { MediaNow.info(this) }.getOrNull()
        val panel = vbox().apply {
            setPadding(dp(20), dp(4), dp(20), dp(12))
            addView(text(now?.subtitle() ?: getString(R.string.widget_nothing_playing), 20f, Ui.bold, lines = 2))
            addView(
                text(
                    now?.let { "${it.appLabel} · ${it.minuteText()}".trim(' ', '·') } ?: getString(R.string.widget_media_access_needed),
                    13f, color = Ui.GRAY,
                ),
                lp(MATCH, WRAP).apply { topMargin = dp(4) },
            )
            if (now != null && now.progress >= 0) {
                addView(BarView(this@WidgetsActivity, now.progress), lp(MATCH, dp(5)).apply { topMargin = dp(8) })
            }
            addView(hbox().apply {
                setPadding(0, dp(14), 0, 0)
                fun key(icon: Int, label: Int, action: () -> Unit) =
                    CardIconButton(this@WidgetsActivity, icon, getString(label), action)
                val buttons = listOf(
                    key(R.drawable.ic_media_previous, R.string.media_previous) { MediaNow.previous(this@WidgetsActivity) },
                    key(R.drawable.ic_media_rewind, R.string.media_rewind) { MediaNow.seek(this@WidgetsActivity, -10_000) },
                    key(if (now?.playing == true) R.drawable.ic_media_pause else R.drawable.ic_media_play, R.string.media_play_pause) { MediaNow.toggle(this@WidgetsActivity) },
                    key(R.drawable.ic_media_forward, R.string.media_forward) { MediaNow.seek(this@WidgetsActivity, 10_000) },
                    key(R.drawable.ic_media_next, R.string.media_next) { MediaNow.next(this@WidgetsActivity) },
                )
                buttons.forEachIndexed { i, b ->
                    addView(b, lp(0, dp(64), 1f).apply { if (i > 0) marginStart = dp(10) })
                }
            }, lp(MATCH, WRAP))
            addView(
                text(
                    getString(R.string.widget_media_hint),
                    13f, color = Ui.LIGHT_GRAY, lines = 2,
                ),
                lp(MATCH, WRAP).apply { topMargin = dp(10) },
            )
        }
        val sheet = Sheet(this).header(getString(R.string.native_music), getString(R.string.widget_controls)).note(panel)
        if (!MediaNow.hasAccess(this)) {
            sheet.item(getString(R.string.notification_access), bold = true) { MediaNow.requestAccess(this) }
        }
        sheet.onDismiss { reload() }.show()
    }

    // ---- Android widgets ----------------------------------------------------

    private fun widgetPage(id: Int, info: AppWidgetProviderInfo): View {
        val label = runCatching { info.loadLabel(packageManager).toString() }.getOrDefault(info.provider.shortClassName)
        return softCard(0).apply {
            val page = vbox().apply {
                setPadding(dp(16), dp(14), dp(16), dp(12))
                addView(hbox().apply {
                    addView(text(label, 20f, Ui.bold), lp(0, WRAP, 1f))
                    addView(text(getString(R.string.widget_remove), 15f, color = Ui.GRAY).apply {
                        setPadding(dp(16), dp(4), 0, dp(4))
                        setOnClickListener { confirmRemove(id, label) }
                    })
                }, lp(MATCH, WRAP))
                addView(hline(1), lp(MATCH, WRAP).apply { topMargin = dp(8) })
                val frame = FrameLayout(this@WidgetsActivity).apply {
                    setPadding(dp(2), dp(12), dp(2), dp(2))
                    setOnLongClickListener { confirmRemove(id, label); true }
                }
                val view: AppWidgetHostView? = runCatching { host.createView(this@WidgetsActivity, id, info) }.getOrNull()
                if (view == null) {
                    frame.addView(text(getString(R.string.widget_add_failed), 17f, color = Ui.GRAY, lines = 3).apply {
                        setPadding(dp(8), dp(20), dp(8), 0)
                    })
                } else {
                    view.setAppWidget(id, info)
                    frame.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
                    view.post { resize(view, frame) }
                }
                addView(frame, lp(MATCH, 0, 1f))
            }
            addView(page, lp(MATCH, MATCH))
            setOnLongClickListener { confirmRemove(id, label); true }
        }
    }

    private fun resize(view: AppWidgetHostView, frame: FrameLayout) {
        val density = resources.displayMetrics.density
        val w = ((if (frame.width > 0) frame.width else body.width) / density).toInt().coerceAtLeast(1)
        val h = ((if (frame.height > 0) frame.height else body.height) / density).toInt().coerceAtLeast(1)
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, w)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, w)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, h)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, h)
        }
        runCatching { view.updateAppWidgetSize(options, w, h, w, h) }
    }

    private fun confirmRemove(id: Int, label: String) {
        Sheet(this).header(label, getString(R.string.widget_remove_confirm)).centered()
            .item(getString(R.string.widget_remove), bold = true) {
                runCatching { host.deleteAppWidgetId(id) }
                ids = ids - id
                saveIds()
                index = index.coerceAtMost(pageCount - 1)
                render()
            }
            .show()
    }

    private fun pickWidget() {
        startActivityForResult(Intent(this, WidgetPickerActivity::class.java), REQ_PICK)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_PICK -> if (resultCode == RESULT_OK && data != null) {
                WidgetPickerActivity.nativeOf(data)?.let {
                    NativeWidgets.add(this, it)
                    reload()
                } ?: WidgetPickerActivity.providerOf(data)?.let(::addWidget)
            }
            REQ_BIND -> {
                val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId) ?: pendingId
                pendingId = -1
                if (resultCode == RESULT_OK && id >= 0 && manager.getAppWidgetInfo(id) != null) bound(id) else {
                    if (id >= 0) runCatching { host.deleteAppWidgetId(id) }
                    failed()
                }
            }
        }
    }

    /** Attach an Android widget to this app's host, asking the system for permission if needed. */
    private fun addWidget(provider: ComponentName) {
        val id = runCatching { host.allocateAppWidgetId() }.getOrNull() ?: return failed()
        val allowed = runCatching { manager.bindAppWidgetIdIfAllowed(id, provider) }.getOrDefault(false)
        if (allowed) return bound(id)
        pendingId = id
        val bind = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
        }
        runCatching { startActivityForResult(bind, REQ_BIND) }.onFailure {
            runCatching { host.deleteAppWidgetId(id) }
            pendingId = -1
            failed()
        }
    }

    private fun bound(id: Int) {
        if (manager.getAppWidgetInfo(id) == null) {
            runCatching { host.deleteAppWidgetId(id) }
            return failed()
        }
        if (id !in ids) ids = ids + id
        saveIds()
        index = nativePages.size + ids.indexOf(id)
        render()
    }

    private fun failed() {
        Sheet(this).header(getString(R.string.widgets_title), getString(R.string.widget_add_failed)).centered().show()
    }

    private fun loadIds(): List<Int> =
        widgetPrefs().getString(KEY_IDS, null)
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull() }
            ?.filter { manager.getAppWidgetInfo(it) != null }
            ?.distinct()
            .orEmpty()

    private fun saveIds() {
        widgetPrefs().edit().putString(KEY_IDS, ids.joinToString(",")).apply()
    }

    private fun widgetPrefs() = Storage.of(this).getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    companion object {
        /** This app's widget host id (one per app, so it does not collide with another launcher). */
        private const val HOST_ID = 0x0B00C
        private const val PREFS = "widgets"
        private const val KEY_IDS = "ids"
        private const val REQ_PICK = 41
        private const val REQ_BIND = 42
    }
}
