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
 * 맨 앞 책과 나머지 책 일부가 홈의 `내 책장` 첫 쪽에 보인다. 줄을 누르면 그 책의 앱을 바꾸고, 하단 `책 추가 ›` 로 책을 찾는다.
 * 홈에서 숨긴 책([ShelfBook.homeHidden])도 제자리에 흐리게(표지·제목, 앱 줄 앞에 `홈에서 숨김`) 보이고 `↑ ↓` 대신 `보이기`(홈에 다시 보이게).
 * 순서는 홈에 보이는 책끼리 바꾼다(숨긴 책은 건너뛴다, [BookShelf.move]).
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
            setOnClickListener { addBookSheet() }
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

        rows.setRows(books.mapIndexed { i, book -> { bookRow(book, i, books, apps) } })
    }

    private fun bookRow(book: ShelfBook, i: Int, books: List<ShelfBook>, apps: List<AppEntry>): View {
        val app = book.app?.let { key -> AppStore.find(this, key, apps) }
        val hidden = book.homeHidden
        return hbox().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Ui.MARGIN), 0, dp(Ui.MARGIN), 0)
            val w = dp(COVER_DP)
            addView(coverImage(BookShelf.cover(context, book, w)).apply { if (hidden) alpha = HIDDEN_ALPHA }, lp(w + dp(2), w * 3 / 2 + dp(2)))
            addView(vbox().apply {
                addView(text(book.title, 22f, Ui.bold, if (hidden) Ui.LIGHT_GRAY else Ui.BLACK, lines = 2))
                val appText = app?.label ?: getString(R.string.pick_reader_link)
                val line = (listOfNotNull(getString(R.string.home_hidden_tag).takeIf { hidden }, appText) + bookStatus(book)).joinToString("  ·  ")
                addView(text(line, 16f, color = if (app != null && !hidden) Ui.GRAY else Ui.LIGHT_GRAY), lp(WRAP, WRAP).apply { topMargin = dp(6) })
            }, lp(0, WRAP, 1f).apply { marginStart = dp(16) })
            if (hidden) addView(link(getString(R.string.unhide)) { BookShelf.setHomeHidden(this@BooksActivity, book.id, false); render() })
            else {
                addView(orderArrow("↑", BookShelf.movableTo(books, i, -1) != null) { move(book, -1) })
                addView(orderArrow("↓", BookShelf.movableTo(books, i, 1) != null) { move(book, 1) })
            }
            addView(link(getString(R.string.remove)) { removeBook(BookRecord.of(this@BooksActivity, book), bookSubtitle(book, reading = true)) { render() } })
            setOnClickListener {
                startActivity(AppListActivity.intent(this@BooksActivity, AppListActivity.Mode.PICK_READER, book.id))
            }
            // 길게 누르면 표지를 인터넷([BookSearch])에서 다시 찾아 바꾼다(자동으로 넣은 책도).
            setOnLongClickListener { startActivity(BookSearchActivity.intent(this@BooksActivity, replace = book)); true }
        }
    }

    /** 줄 오른쪽 밑줄 글자(`보이기`·`빼기`) */
    private fun link(label: String, onClick: () -> Unit) = text(label, 19f).apply {
        paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
        setPadding(dp(16), dp(14), 0, dp(14))
        setOnClickListener { onClick() }
    }

    /** 옮긴 책이 다른 페이지로 넘어가면 그 페이지를 보여 준다(숨긴 책을 건너뛰어 간 자리). */
    private fun move(book: ShelfBook, delta: Int) {
        val to = BookShelf.move(this, book.id, delta)
        render()
        to?.let { rows.reveal(it) }
    }

    companion object {
        private const val COVER_DP = 48
        /** 줄 높이: 표지(48 × 72dp + 테두리) + 위아래 12dp */
        private const val ROW_DP = 98
        /** 홈에서 숨긴 책의 표지 진하기 */
        private const val HIDDEN_ALPHA = 0.4f

        fun intent(context: Context) = Intent(context, BooksActivity::class.java)
    }
}

/**
 * ⑨ 책 추가(지원하지 않는 이북 앱용): 아래 메뉴에 제목·저자(비워도 됨) 두 줄을 적고 `넣기` 면 인터넷 없이 표지 그림 없는 새 책으로 맨 앞에 넣고([BookShelf.add])
 * 그 책의 읽고 있는 앱을 고르러 간다. 표지는 나중에 책 정보 고치기의 `표지 다시 찾기`. 읽고 있는 책 목록의 `책 추가 ›` 와 빈 홈의 `직접 추가 ›` 가 부른다.
 */
fun Context.addBookSheet() {
    Sheet(this).header(getString(R.string.add_book_title), getString(R.string.add_book_desc))
        .inputs(listOf("" to getString(R.string.book_query_hint), "" to getString(R.string.add_book_author_hint)), getString(R.string.add_book_save)) { (title, author) ->
            val added = BookShelf.add(this, title, author)
            startActivity(AppListActivity.intent(this, AppListActivity.Mode.PICK_READER, added.id))
        }.show()
}
