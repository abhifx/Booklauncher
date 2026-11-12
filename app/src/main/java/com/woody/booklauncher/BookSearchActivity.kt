package com.woody.booklauncher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.text.InputType
import android.util.LruCache
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

/**
 * 표지 다시 찾기: 책 제목(과 저자)으로 표지를 찾아([BookSearch]: 한국어는 YES24, 그 밖은 Apple Books·Open Library) 고르면 그 책의 표지·제목·저자를 바꾼다
 * (자리·앱·진행률은 그대로). 결과는 표지 4열 그리드(한 페이지 두 줄)로 보여 주고 페이지로 넘긴다. 책 추가는 인터넷 없이 제목·저자만 적는다([addBookSheet]).
 */
class BookSearchActivity : EinkActivity() {
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var rows: PagedRows
    private lateinit var pageText: TextView
    /** 칸 폭·한 줄 권수·칸 사이([coverGrid]): 세로 화면은 [COLUMNS]권, 가로 화면은 표지를 세로와 같은 크기로 두고 들어가는 만큼(남는 폭은 칸 사이에) */
    private lateinit var fit: CoverGrid
    private var coverH = 0
    /** 늦게 끝난 옛 검색이 새 결과를 덮지 않도록 검색마다 번호를 붙인다. */
    private var searchId = 0
    private var busy = false
    /** 표지를 바꿀 책 */
    private val replaceId by lazy { intent.getStringExtra(EXTRA_REPLACE).orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fit = coverGrid(COLUMNS, GAP_DP)
        coverH = fit.cellW * 3 / 2

        val root = vbox()
        root.addView(titleRow(getString(R.string.find_cover), Palette.Area.LIBRARY) { finish() })

        // 제목 입력 줄: 박스 없이 아래 굵은 선만
        input = EditText(this).apply {
            hint = getString(R.string.book_query_hint)
            setSp(24f)
            setTextColor(Ui.BLACK)
            setHintTextColor(Ui.LIGHT_GRAY)
            background = null
            setPadding(0, dp(8), 0, dp(8))
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            // 가로 화면에서도 전체 화면 입력 창으로 바꾸지 않는다(결과 칸이 가려진다)
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setOnEditorActionListener { _, action, event ->
                if (action == EditorInfo.IME_ACTION_SEARCH || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                    search(); true
                } else false
            }
        }
        root.addView(hbox().apply {
            setPadding(dp(Ui.MARGIN), dp(18), dp(Ui.MARGIN), 0)
            addView(input, lp(0, WRAP, 1f))
            addView(text(getString(R.string.search), 22f, Ui.bold).apply {
                setPadding(dp(20), dp(12), 0, dp(12))
                setOnClickListener { search() }
            })
        })

        status = text(getString(R.string.book_search_idle), 17f, color = Ui.GRAY, lines = 4).apply {
            setPadding(dp(Ui.MARGIN), dp(12), dp(Ui.MARGIN), dp(8))
        }
        root.addView(status)

        rows = PagedRows(this, coverH + dp(56))
        root.addView(softCard(0).apply { addView(rows, lp(MATCH, MATCH)) }, cardLp(weight = 1f, topDp = 8, bottomDp = 10))

        root.addView(hline(1, inset = false))
        val footer = FrameLayout(this)
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

        val preset = intent.getStringExtra(EXTRA_QUERY)
        if (!preset.isNullOrBlank()) {
            // 그 책의 제목·저자로 바로 찾는다.
            input.setText(preset)
            search()
        } else {
            input.requestFocus()
            input.post { getSystemService(InputMethodManager::class.java).showSoftInput(input, 0) }
        }
    }

    override fun onSwipe(toLeft: Boolean): Boolean {
        if (rows.pageCount <= 1) return false
        if (toLeft) rows.next() else rows.prev()
        return true
    }

    private fun search() {
        val query = input.text.toString().trim()
        if (query.isEmpty() || busy) return
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(input.windowToken, 0)
        val id = ++searchId
        status.setText(R.string.searching)
        val korean = !HomePrefs.isEnglish(this)
        Bg.run({ BookSearch.search(query, korean) }) { result ->
            if (id != searchId || isFinishing || isDestroyed) return@run
            result.onSuccess { books ->
                status.text = if (books.isEmpty()) getString(R.string.book_none) else getString(R.string.book_results, books.size)
                rows.setRows(books.chunked(fit.columns).map { line -> { gridRow(line) } })
                rows.showPage(0)
            }.onFailure { status.text = errorText(R.string.book_error, it) }
        }
    }

    /** 표지 한 줄. 표지 아래에 제목과 저자를 한 줄씩. */
    private fun gridRow(books: List<BookResult>): View = hbox().apply {
        gravity = Gravity.TOP
        setPadding(dp(Ui.MARGIN), dp(12), dp(Ui.MARGIN), 0)
        books.forEachIndexed { i, book ->
            addView(vbox().apply {
                val cover = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setBackgroundColor(Ui.TILE)
                }
                addView(cover, lp(fit.cellW, coverH))
                loadThumb(cover, book.thumbUrl)
                addView(text(book.title, 14f, Ui.bold), lp(MATCH, WRAP).apply { topMargin = dp(6) })
                if (book.author.isNotBlank()) addView(text(book.author, 12f, color = Ui.GRAY), lp(MATCH, WRAP).apply { topMargin = dp(2) })
                setOnClickListener { pick(book) }
            }, lp(fit.cellW, MATCH).apply { if (i > 0) marginStart = fit.gap })
        }
    }

    private fun loadThumb(view: ImageView, url: String) {
        view.tag = url
        // 흑백·컬러를 따로 둔다(설정에서 색을 바꾸고 다시 찾을 때).
        val gray = !Palette.current.color
        val key = if (gray) url else "$url@c"
        thumbs.get(key)?.let { view.setImageBitmap(it); view.background = null; return }
        val width = fit.cellW
        Bg.run({ BookSearch.scaledCover(BookSearch.download(url), width, gray) }) { result ->
            val bmp = result.getOrNull() ?: return@run
            thumbs.put(key, bmp)
            if (view.tag == url) {
                view.setImageBitmap(bmp)
                view.background = null
            }
        }
    }

    /**
     * 표지를 받아 이 책의 표지·제목·저자를 바꾸고 닫는다. 저장은 받은 뒤 화면 스레드에서 바로 한다(뒤로 가기도 화면 스레드라 '살아 있나' 확인과
     * 저장 사이에 끼어들 수 없다. 받는 사이 뒤로 갔으면 저장하지 않는다). 받기·저장이 실패하면 오류만 보인다.
     */
    private fun pick(book: BookResult) {
        if (busy) return
        busy = true
        // 진행 중인 검색은 버린다(늦게 온 결과가 `표지 받는 중`과 고른 표지를 덮지 않게. 표지를 받는 동안은 다른 표지를 누를 수 없다)
        searchId++
        status.setText(R.string.cover_loading)
        Bg.run({ runCatching { BookSearch.download(book.coverUrl) }.getOrElse { BookSearch.download(book.thumbUrl) } }) { got ->
            busy = false
            if (isFinishing || isDestroyed) return@run
            got.mapCatching { BookShelf.replace(this, replaceId, book, it) }.onSuccess { finish() }.onFailure { status.text = errorText(R.string.cover_error, it) }
        }
    }

    /** 연결 문제면 와이파이를 확인하라고, 그 밖에는 [res] 에 오류 내용을 넣어 보여 준다. */
    private fun errorText(res: Int, e: Throwable): String =
        if (BookSearch.isConnectionError(e)) getString(R.string.no_network) else getString(res, e.message ?: e.javaClass.simpleName)

    companion object {
        private const val COLUMNS = 4
        private const val GAP_DP = 14
        /** 검색 결과 표지(줄인 것, 흑백 또는 컬러) */
        private val thumbs = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap) = value.byteCount
        }

        private const val EXTRA_REPLACE = "replace"
        private const val EXTRA_QUERY = "query"

        /** [replace] 의 표지 다시 찾기(제목·저자로 바로 검색) */
        fun intent(context: Context, replace: ShelfBook) = Intent(context, BookSearchActivity::class.java).apply {
            putExtra(EXTRA_REPLACE, replace.id); putExtra(EXTRA_QUERY, listOf(replace.title, replace.author).filter(String::isNotBlank).joinToString(" "))
        }
    }
}
