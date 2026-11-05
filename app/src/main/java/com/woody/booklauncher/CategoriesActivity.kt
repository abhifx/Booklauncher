package com.woody.booklauncher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView

/**
 * 카테고리 관리(설정 › 독서 › 카테고리, 보기 메뉴·책 상세의 `카테고리 관리 ›`). 줄마다 이름(통계에서 뺐으면 뒤에 회색 `통계에서 뺌`)·붙은 책 수·`↑ ↓`(순서).
 * 줄을 누르면 `이름 바꾸기` / `통계에서 빼기`(`통계에 다시 넣기`) / `지우기`. 하단 왼쪽 `+ 새 카테고리`. 책에 붙이는 것은 책 상세의 `카테고리`.
 */
class CategoriesActivity : EinkActivity() {
    private lateinit var titleCount: TextView
    private lateinit var rows: PagedRows
    private lateinit var pageText: TextView
    private lateinit var empty: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = vbox()
        root.addView(titleRow(getString(R.string.categories_title), Palette.Area.SETTINGS) { finish() }.apply {
            titleCount = text("", 21f, color = Ui.GRAY)
            addView(titleCount, lp(WRAP, WRAP).apply { marginStart = dp(14); topMargin = dp(6) })
        })
        root.addView(hline(2))
        empty = text(getString(R.string.categories_empty), 18f, color = Ui.GRAY).apply {
            setPadding(dp(Ui.MARGIN), dp(40), dp(Ui.MARGIN), 0)
        }
        root.addView(empty)
        rows = PagedRows(this, rowDp(Ui.ROW_DP))
        root.addView(rows, lp(MATCH, 0, 1f))
        root.addView(hline(1, inset = false))
        val footer = FrameLayout(this)
        footer.addView(text(getString(R.string.category_new), 21f, Ui.bold).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Ui.MARGIN), 0, dp(24), 0)
            setOnClickListener { askNew { render() } }
        }, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.START))
        pageText = text("", 21f).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), 0, dp(Ui.MARGIN), 0)
            pagesClick(rows)
        }
        footer.addView(pageText, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.END))
        root.addView(footer, lp(MATCH, dp(Ui.FOOTER_DP)))
        rows.onPageChanged = { pageText.text = pageLabel(rows) }
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
        val list = Categories.list(this)
        val counts = Categories.bookCounts(this)
        titleCount.text = if (list.isEmpty()) "" else getString(R.string.category_count, list.size)
        empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        rows.setRows(list.mapIndexed { i, c -> { row(c, i, list.size, counts[c.id] ?: 0) } })
    }

    private fun row(c: Category, i: Int, size: Int, books: Int): View = hbox().apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(Ui.MARGIN), 0, dp(Ui.MARGIN), 0)
        addView(text(c.name, 22f), lp(WRAP, WRAP))
        if (c.excluded) addView(text(getString(R.string.category_excluded_tag), 15f, color = Ui.GRAY), lp(WRAP, WRAP).apply { marginStart = dp(10) })
        addView(text(bookCount(books), 19f, color = Ui.GRAY).apply { gravity = Gravity.END },
            lp(0, WRAP, 1f).apply { marginStart = dp(16) })
        addView(orderArrow("↑", i > 0) { move(c, i, -1) })
        addView(orderArrow("↓", i < size - 1) { move(c, i, 1) })
        setOnClickListener { menu(c, books) }
    }

    /** 옮긴 줄이 다른 쪽으로 넘어가면 그 쪽을 보여 준다 */
    private fun move(c: Category, i: Int, delta: Int) {
        Categories.move(this, c.id, delta)
        render()
        rows.reveal(i + delta)
    }

    /** 한 줄 메뉴: 이름 바꾸기 / 통계에서 빼기(다시 넣기) / 지우기 */
    private fun menu(c: Category, books: Int) {
        Sheet(this).header(c.name, bookCount(books))
            .item(getString(R.string.category_rename)) {
                Sheet(this).header(getString(R.string.category_rename))
                    .input(c.name, check = { sameName(it, except = c.id) }) { Categories.rename(this, c.id, it); render() }.show()
            }
            .item(getString(if (c.excluded) R.string.category_include else R.string.category_exclude)) {
                Categories.setExcluded(this, c.id, !c.excluded); render()
            }
            .item(getString(R.string.category_delete)) {
                confirm(getString(R.string.category_delete_confirm), if (books == 1) getString(R.string.category_delete_sub_one) else getString(R.string.category_delete_sub, books), getString(R.string.category_delete)) {
                    Categories.delete(this, c.id); render()
                }
            }
            .show()
    }

    /** `12권`(영어는 한 권이면 `1 book`) */
    private fun bookCount(n: Int) = if (n == 1) getString(R.string.category_books_one) else getString(R.string.category_books, n)

    companion object {
        fun intent(context: Context) = Intent(context, CategoriesActivity::class.java)
    }
}

/** `새 카테고리` 이름 입력. 같은 이름이 있으면 알리고 만들지 않는다. 만들면 [onAdded]. */
fun Context.askNew(onAdded: (Category) -> Unit) {
    Sheet(this).header(getString(R.string.category_new_title))
        .input("", check = { sameName(it) }) { name -> Categories.add(this, name)?.let(onAdded) }.show()
}

/** 다른 카테고리([except] 빼고)와 이름이 같으면(대소문자 무시, 저장할 때처럼 다듬어) 알릴 글, 아니면 null */
private fun Context.sameName(name: String, except: Long? = null): String? =
    getString(R.string.category_same_name).takeIf { Categories.sameName(this, name, except) }

/** 보기 거르기 이름: `전체` · `소설` · `소설·에세이` · `소설 외 2`(카테고리 순서, `분류 없음`은 끝) */
fun Context.filterName(f: StatsFilter): String {
    if (f.all) return getString(R.string.filter_all)
    val names = f.categories.filter { it.id in f.picked }.map { it.name } +
        listOfNotNull(getString(R.string.category_uncategorized).takeIf { Categories.NONE in f.picked })
    return when (names.size) {
        1 -> names[0]
        2 -> names.joinToString("·")
        else -> getString(R.string.filter_more, names[0], names.size - 1)
    }
}

/**
 * 보기 메뉴(독서 기록·올해 읽은 책·결산 제목 옆 `전체 ▾`): 머리 `보기` · 회색 [sub], 체크 줄 `전체`(통계에서 뺀 카테고리가 있으면 아래 `만화 빼고`) ·
 * 카테고리들(통계에서 뺐으면 뒤에 `통계에서 뺌`) · `분류 없음`, 줄마다 오른쪽에 그것만 골랐을 때의 값([measure], 읽은 시간·권수).
 * 여러 개를 고를 수 있다: `전체`를 누르면 나머지를 끄고, 다른 줄을 켜면 `전체`가 꺼지며, 다 끄면 `전체`. 맨 아래 왼쪽 `카테고리 관리 ›`, 오른쪽 `확인`([onApply]).
 */
fun Context.showStatsFilter(f: StatsFilter, sub: String, measure: (StatsFilter) -> String, onApply: (Set<Long>) -> Unit) {
    val picked = f.picked.toMutableSet()
    val sheet = Sheet(this).header(getString(R.string.filter_title), sub)
    val excluded = f.categories.filter { it.excluded }.map { it.name }
    sheet.check(
        getString(R.string.filter_all), sub = excluded.takeIf { it.isNotEmpty() }?.let { getString(R.string.filter_all_but, it.joinToString("·")) },
        right = measure(f.with(emptySet())), on = { picked.isEmpty() },
    ) { picked.clear() }
    val options = f.categories.map { Triple(it.id, it.name, it.excluded) } + Triple(Categories.NONE, getString(R.string.category_uncategorized), false)
    options.forEach { (id, name, out) ->
        sheet.check(name, tag = if (out) getString(R.string.category_excluded_tag) else null, right = measure(f.with(setOf(id))), on = { id in picked }) {
            if (!picked.remove(id)) picked += id
        }
    }
    sheet.footer(getString(R.string.category_manage)) { startActivity(CategoriesActivity.intent(this)) }
        .done(getString(R.string.filter_done)) { onApply(picked.toSet()) }
        .show()
}
