package com.woody.booklauncher

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView

/**
 * 읽고 있는 책 목록(설정 > 읽고 있는 책). 줄마다 표지·제목·읽고 있는 앱, 오른쪽에 `↑ ↓`(순서)와 `빼기`.
 * 위 [BookShelf.HOME]권이 홈에 바로 보인다(맨 위 책은 크게, 나머지는 함께 읽는 책 다음 쪽). 줄을 누르면 그 책의 앱을 바꾸고, 하단 `책 추가 ›` 로 책을 찾는다.
 * 한 화면에 다 들어가지 않으면 페이지로 넘긴다(하단 오른쪽 `‹ 1 / 2 ›`).
 */
class BooksActivity : EinkActivity() {
    private lateinit var titleCount: TextView
    private lateinit var rows: PagedRows
    private lateinit var pageText: TextView
    private lateinit var empty: View
    private lateinit var addLink: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = vbox()
        root.addView(titleRow(getString(R.string.now_reading), Palette.Area.LIBRARY) { finish() }.apply {
            titleCount = text("", 21f, color = Ui.GRAY)
            addView(titleCount, lp(WRAP, WRAP).apply { marginStart = dp(14); topMargin = dp(6) })
        })
        root.addView(hline(2))

        empty = vbox().apply {
            setPadding(dp(Ui.MARGIN), dp(56), dp(Ui.MARGIN), 0)
            addView(text(getString(R.string.books_empty), 23f, Ui.bold))
            addView(text(getString(R.string.books_empty_desc), 17f, color = Ui.GRAY, lines = 3),
                lp(WRAP, WRAP).apply { topMargin = dp(12) })
        }
        root.addView(empty)
        // 글자를 키우면 줄도 그만큼 높인다(두 줄 제목 아래 진행률이 잘리지 않게. 100% 아래로는 표지 때문에 그대로).
        rows = PagedRows(this, rowDp(ROW_DP * Ui.textGrow))
        root.addView(rows, lp(MATCH, 0, 1f))

        root.addView(hline(1, inset = false))
        val footer = FrameLayout(this)
        addLink = text(getString(R.string.add_book_link), 21f, Ui.bold).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Ui.MARGIN), 0, dp(24), 0)
            setOnClickListener { startActivity(BookSearchActivity.intent(this@BooksActivity)) }
        }
        footer.addView(addLink, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.START))
        pageText = text("", 21f).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), 0, dp(Ui.MARGIN), 0)
            pagesClick(rows)
        }
        footer.addView(pageText, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.END))
        root.addView(footer, lp(MATCH, dp(Ui.FOOTER_DP)))
        rows.onPageChanged = {
            pageText.text = pageLabel(rows)
        }
        setContentView(root)
    }

    override fun onSwipe(toLeft: Boolean): Boolean {
        if (rows.pageCount <= 1) return false
        if (toLeft) rows.next() else rows.prev()
        return true
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val apps = AppStore.loadAndPrune(this, prefs)
        val books = BookShelf.list(this)
        titleCount.text = getString(R.string.books_count, books.size)
        empty.visibility = if (books.isEmpty()) View.VISIBLE else View.GONE

        rows.setRows(books.mapIndexed { i, book -> { bookRow(book, i, books.size, apps) } })
    }

    private fun bookRow(book: ShelfBook, i: Int, count: Int, apps: List<AppEntry>): View {
        val app = book.app?.let { key -> AppStore.find(this, key, apps) }
        return hbox().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Ui.MARGIN), 0, dp(Ui.MARGIN), 0)
            val w = dp(COVER_DP)
            addView(coverImage(BookShelf.cover(context, book, w)), lp(w + dp(2), w * 3 / 2 + dp(2)))
            addView(vbox().apply {
                addView(text(book.title, 22f, Ui.bold, lines = 2))
                val appText = app?.label ?: getString(R.string.pick_reader_link)
                val line = (listOf(appText) + bookStatus(book)).joinToString("  ·  ")
                addView(text(line, 16f, color = if (app != null) Ui.GRAY else Ui.LIGHT_GRAY), lp(WRAP, WRAP).apply { topMargin = dp(6) })
            }, lp(0, WRAP, 1f).apply { marginStart = dp(16) })
            addView(orderArrow("↑", i > 0) { move(book, i, -1) })
            addView(orderArrow("↓", i < count - 1) { move(book, i, 1) })
            addView(text(getString(R.string.remove), 19f).apply {
                paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
                setPadding(dp(16), dp(14), 0, dp(14))
                setOnClickListener { removeBook(BookRecord.of(this@BooksActivity, book), bookSubtitle(book, reading = true)) { render() } }
            })
            setOnClickListener {
                startActivity(AppListActivity.intent(this@BooksActivity, AppListActivity.Mode.PICK_READER, book.id))
            }
            // 길게 누르면 표지를 인터넷([BookSearch])에서 다시 찾아 바꾼다(자동으로 넣은 책도).
            setOnLongClickListener { startActivity(BookSearchActivity.intent(this@BooksActivity, replace = book)); true }
        }
    }

    /** 옮긴 책이 다른 페이지로 넘어가면 그 페이지를 보여 준다. */
    private fun move(book: ShelfBook, i: Int, delta: Int) {
        BookShelf.move(this, book.id, delta)
        render()
        rows.reveal(i + delta)
    }

    companion object {
        private const val COVER_DP = 48
        /** 줄 높이: 표지(48 × 72dp + 테두리) + 위아래 12dp */
        private const val ROW_DP = 98

        fun intent(context: Context) = Intent(context, BooksActivity::class.java)
    }
}
