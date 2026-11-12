package com.woody.booklauncher

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** ③ 모든 앱 / ⑥ 읽고 있는 앱 고르기 / ⑦ 숨긴 앱. 모두 제목 + 페이지 목록 + 하단 줄 구조다. */
class AppListActivity : EinkActivity() {
    /** ADD_FAVORITE 는 자주 쓰는 앱에 넣을 앱 고르기(아직 안 넣은 앱만). */
    enum class Mode { ALL, PICK_READER, HIDDEN, ADD_FAVORITE }

    private val picking get() = mode == Mode.PICK_READER

    private lateinit var mode: Mode
    /** PICK_READER 에서 이 책의 앱을 고른다. null 이면 맨 앞 책. */
    private var bookId: String? = null
    private lateinit var titleCount: TextView
    private lateinit var rows: PagedRows
    private lateinit var footerLeft: TextView
    private lateinit var pageText: TextView
    private var indexCol: LinearLayout? = null
    private var emptyView: LinearLayout? = null
    private var shown: List<AppEntry> = emptyList()
    /** 처음 그릴 때만 지금 읽고 있는 앱이 있는 페이지를 연다. */
    private var firstRefresh = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = Mode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: Mode.ALL.name)
        bookId = intent.getStringExtra(EXTRA_BOOK)
        buildViews()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onSwipe(toLeft: Boolean): Boolean {
        if (rows.pageCount <= 1) return false
        if (toLeft) rows.next() else rows.prev()
        return true
    }

    private fun buildViews() {
        val root = vbox()
        val title = when (mode) {
            Mode.ALL -> getString(R.string.all_apps)
            Mode.PICK_READER -> getString(R.string.pick_reader_title)
            Mode.HIDDEN -> getString(R.string.hidden_apps)
            Mode.ADD_FAVORITE -> getString(R.string.add_app)
        }
        root.addView(titleRow(title) { finish() }.apply {
            titleCount = text("", 21f, color = Ui.GRAY)
            addView(titleCount, lp(WRAP, WRAP).apply { marginStart = dp(14); topMargin = dp(6) })
        })
        if (picking) {
            root.addView(text(getString(R.string.pick_reader_desc), 17f, color = Ui.GRAY, lines = 2).apply {
                setPadding(dp(Ui.MARGIN), dp(14), dp(Ui.MARGIN), dp(4))
            })
        }

        if (mode == Mode.HIDDEN) {
            // 숨긴 앱이 없을 때: 이유와 숨기는 방법
            emptyView = vbox().apply {
                setPadding(dp(Ui.MARGIN), dp(56), dp(Ui.MARGIN), 0)
                visibility = View.GONE
                addView(text(getString(R.string.hidden_empty), 23f, Ui.bold))
                addView(text(getString(R.string.hidden_empty_desc), 17f,
                    color = Ui.GRAY, lines = 3).apply { setLineSpacing(dp(4).toFloat(), 1f) },
                    lp(WRAP, WRAP).apply { topMargin = dp(12) })
            }
            root.addView(emptyView)
        }

        val body = LinearLayout(this)
        rows = PagedRows(this, rowDp(Ui.ROW_DP))
        body.addView(rows, lp(0, MATCH, 1f))
        if (mode == Mode.ALL) {
            indexCol = IndexColumn(this).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL }
            body.addView(indexCol, lp(dp(48), MATCH).apply { marginEnd = dp(Ui.MARGIN - 18) })
        }
        root.addView(softCard(0).apply { addView(body, lp(MATCH, MATCH)) }, cardLp(weight = 1f, topDp = 8, bottomDp = 10))

        root.addView(hline(1, inset = false))
        val footer = FrameLayout(this)
        footerLeft = text("", if (mode == Mode.HIDDEN) 17f else 21f, color = if (mode == Mode.HIDDEN) Ui.GRAY else Ui.BLACK).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Ui.MARGIN), 0, dp(24), 0)
        }
        footer.addView(footerLeft, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.START))
        pageText = text("", 21f).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), 0, dp(Ui.MARGIN), 0)
            pagesClick(rows)
        }
        footer.addView(pageText, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.END))
        root.addView(footer, lp(MATCH, dp(Ui.FOOTER_DP)))

        when (mode) {
            Mode.ALL -> {
                footerLeft.setOnClickListener { startActivity(intent(this, Mode.HIDDEN)) }
            }
            Mode.HIDDEN, Mode.PICK_READER, Mode.ADD_FAVORITE -> Unit
        }
        rows.onPageChanged = {
            pageText.text = pageLabel(rows)
            updateIndex()
        }
        setContentView(root)
    }

    private fun refresh() {
        AppIcons.clearChangedPackages(this)
        val apps = AppStore.loadAndPrune(this, prefs)
        val hidden = prefs.hidden
        val favs = prefs.favorites
        val books = BookShelf.list(this)
        // 고르는 중이면 지금 그 책의 앱에 ✓
        val default = (bookId?.let { id -> books.find { it.id == id } } ?: books.firstOrNull())?.app
        // 모든 앱에서 '읽는 중' 표시: 책에 연결된 앱 전부
        val readingApps = books.mapNotNull { it.app }.toSet()
        val icons = prefs.showIcons

        shown = when (mode) {
            Mode.ALL -> apps.filter { it.key !in hidden }
            Mode.HIDDEN -> apps.filter { it.key in hidden }
            // 이북 앱을 먼저, 그다음 나머지
            Mode.ADD_FAVORITE -> apps.filter { it.key !in hidden && it.key !in favs }
            Mode.PICK_READER -> apps.filter { it.key !in hidden }.sortedBy { if (AppStore.isEbook(it)) 0 else 1 }
        }
        titleCount.text = if (picking) "" else shown.size.toString()
        when (mode) {
            Mode.ALL -> footerLeft.text = getString(R.string.hidden_count_link, hidden.size)
            // 비어 있으면 안내를 본문에 모으고, 목록이 있으면 하단에 한 줄로
            Mode.HIDDEN -> {
                emptyView?.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
                footerLeft.text = if (shown.isEmpty()) "" else getString(R.string.hidden_note)
            }
            Mode.PICK_READER, Mode.ADD_FAVORITE -> Unit
        }

        rows.setRows(shown.map { app ->
            {
                when (mode) {
                    Mode.ALL -> {
                        val mark = when {
                            app.key in readingApps -> text(getString(R.string.mark_reading), 17f)
                            app.key in favs -> text("★", 18f)
                            else -> null
                        }
                        appRow(app, app.label, Ui.ROW_ICON_DP, Ui.ROW_SP, showIcon = icons, right = mark).apply {
                            setOnClickListener { AppStore.launch(this@AppListActivity, app) }
                            setOnLongClickListener { showAppMenu(app) { refresh() }; true }
                        }
                    }
                    Mode.ADD_FAVORITE -> appRow(app, app.label, Ui.ROW_ICON_DP, Ui.ROW_SP, showIcon = icons).apply {
                        setOnClickListener { prefs.favorites = prefs.favorites + app.key; finish() }
                    }
                    Mode.PICK_READER -> {
                        val current = app.key == default
                        appRow(app, app.label, Ui.ROW_ICON_DP, Ui.ROW_SP, if (current) Ui.heavy else Ui.regular,
                            showIcon = icons, right = if (current) text("✓", 24f, Ui.bold) else null).apply {
                            setOnClickListener {
                                val id = bookId ?: BookShelf.list(this@AppListActivity).firstOrNull()?.id
                                if (id != null) BookShelf.setApp(this@AppListActivity, id, app.key)
                                finish()
                            }
                        }
                    }
                    Mode.HIDDEN -> {
                        val show = text(getString(R.string.unhide), 20f).apply {
                            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
                            setPadding(dp(16), dp(14), 0, dp(14))
                            setOnClickListener { prefs.unhide(app.key); refresh() }
                        }
                        appRow(app, app.label, Ui.ROW_ICON_DP, Ui.ROW_SP, color = Ui.GRAY, showIcon = icons, right = show)
                    }
                }
            }
        })
        if (firstRefresh && picking) {
            shown.indexOfFirst { it.key == default }.takeIf { it >= 0 }?.let { rows.reveal(it) }
        }
        firstRefresh = false
    }

    /** 오른쪽 색인: 있는 첫 글자만, 지금 페이지의 글자는 진하게. 누르면 그 글자가 있는 페이지로. */
    private fun updateIndex() {
        val col = indexCol ?: return
        col.removeAllViews()
        if (shown.isEmpty() || rows.perPage <= 0) return
        val initials = shown.map { AppStore.initial(it.label) }
        val letters = initials.distinct()
        val from = rows.page * rows.perPage
        val onPage = initials.subList(from, minOf(initials.size, from + rows.perPage)).toSet()
        letters.forEach { letter ->
            val current = letter in onPage
            col.addView(text(letter, 18f, if (current) Ui.heavy else Ui.regular, if (current) Ui.BLACK else Ui.LIGHT_GRAY).apply {
                gravity = Gravity.CENTER
                setOnClickListener { rows.showPage(rows.pageOf(initials.indexOf(letter))) }
            }, lp(MATCH, dp(INDEX_CELL_DP)))
        }
    }

    /** 색인 칸: 글자 칸 높이를 [INDEX_CELL_DP] 로 두되, 칸 높이에 다 들지 않으면(낮은 화면·가로) 칸 높이를 글자 수로 나눈다. 잴 때 정한다. */
    private class IndexColumn(context: Context) : LinearLayout(context) {
        init {
            orientation = VERTICAL
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val h = MeasureSpec.getSize(heightMeasureSpec)
            if (childCount > 0 && h > 0) {
                val cell = minOf(context.dp(INDEX_CELL_DP), h / childCount)
                for (i in 0 until childCount) getChildAt(i).layoutParams.height = cell
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    companion object {
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_BOOK = "book"
        /** 색인 글자 칸 높이(낮은 화면에서는 줄인다, [IndexColumn]) */
        private const val INDEX_CELL_DP = 48

        fun intent(context: Context, mode: Mode, bookId: String? = null): Intent =
            Intent(context, AppListActivity::class.java).putExtra(EXTRA_MODE, mode.name).putExtra(EXTRA_BOOK, bookId)
    }
}
