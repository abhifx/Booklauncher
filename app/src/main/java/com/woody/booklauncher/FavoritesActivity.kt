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
 * 자주 쓰는 앱 관리(설정 > 앱 관리 > 자주 쓰는 앱). 맨 위 홈 `보기 격자 ›`(목록 / 격자), 줄마다 앱 이름(누르면 이름 바꾸기)과 `↑ ↓`·`빼기`, 하단 `앱 추가 ›`.
 * 홈에 이 순서대로 보인다.
 */
class FavoritesActivity : EinkActivity() {
    private lateinit var titleCount: TextView
    private lateinit var hint: TextView
    private lateinit var empty: View
    private lateinit var rows: PagedRows
    private lateinit var pageText: TextView
    private lateinit var viewValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = vbox()
        root.addView(titleRow(getString(R.string.favorites)) { finish() }.apply {
            titleCount = text("", 21f, color = Ui.GRAY)
            addView(titleCount, lp(WRAP, WRAP).apply { marginStart = dp(14); topMargin = dp(6) })
        })
        // 홈에서 보는 꼴(목록 / 격자). 설정 줄과 같은 모양.
        viewValue = text("", 20f, Ui.medium)
        fun settingRow(label: Int, value: TextView, onClick: () -> Unit) {
            root.addView(hbox().apply {
                setPadding(dp(Ui.MARGIN), 0, dp(Ui.MARGIN), 0)
                addView(text(getString(label), 22f), lp(0, WRAP, 1f))
                addView(value)
                setOnClickListener { onClick() }
            }, lp(MATCH, rowDp(Ui.SETTING_ROW_DP)))
            root.addView(hline(1, Ui.DIVIDER))
        }
        settingRow(R.string.fav_view, viewValue) {
            val views = listOf(getString(R.string.fav_view_list), getString(R.string.fav_view_grid))
            choose(getString(R.string.fav_view), views, if (prefs.favGrid) 1 else 0) { prefs.favGrid = it == 1; showView() }
        }
        showView()
        hint = text(getString(R.string.favorites_order_hint), 15f, color = Ui.GRAY, lines = 2).apply {
            setPadding(dp(Ui.MARGIN), dp(12), dp(Ui.MARGIN), dp(4))
        }
        root.addView(hint)
        empty = text(getString(R.string.favorites_none), 21f, Ui.bold).apply {
            setPadding(dp(Ui.MARGIN), dp(56), dp(Ui.MARGIN), 0)
        }
        root.addView(empty)
        rows = PagedRows(this, rowDp(Ui.ROW_DP))
        root.addView(softCard(0).apply { addView(rows, lp(MATCH, MATCH)) }, cardLp(weight = 1f, topDp = 8, bottomDp = 10))

        root.addView(hline(1, inset = false))
        val footer = FrameLayout(this)
        footer.addView(text(getString(R.string.add_app_link), 21f, Ui.bold).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Ui.MARGIN), 0, dp(24), 0)
            setOnClickListener { startActivity(AppListActivity.intent(this@FavoritesActivity, AppListActivity.Mode.ADD_FAVORITE)) }
        }, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.START))
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

    private fun showView() {
        viewValue.text = getString(if (prefs.favGrid) R.string.fav_view_grid else R.string.fav_view_list) + "  ›"
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onSwipe(toLeft: Boolean): Boolean {
        if (rows.pageCount <= 1) return false
        if (toLeft) rows.next() else rows.prev()
        return true
    }

    private fun render() {
        val apps = AppStore.loadAndPrune(this, prefs)
        val favs = favoriteApps(apps, prefs)
        titleCount.text = favs.size.toString()
        empty.visibility = if (favs.isEmpty()) View.VISIBLE else View.GONE
        hint.visibility = if (favs.isNotEmpty()) View.VISIBLE else View.GONE
        val icons = prefs.showIcons
        rows.setRows(favs.mapIndexed { i, app ->
            {
                val controls = hbox().apply {
                    addView(orderArrow("↑", i > 0) { prefs.moveFavorite(app.key, -1); render() })
                    addView(orderArrow("↓", i < favs.size - 1) { prefs.moveFavorite(app.key, 1); render() })
                    addView(text(getString(R.string.remove), 19f).apply {
                        paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
                        setPadding(dp(16), dp(14), 0, dp(14))
                        setOnClickListener { prefs.favorites = prefs.favorites - app.key; render() }
                    })
                }
                appRow(app, app.label, Ui.ROW_ICON_DP, Ui.ROW_SP, showIcon = icons, right = controls).apply {
                    setOnClickListener { renameFavorite(app) { render() } }
                }
            }
        })
    }



    companion object {
        fun intent(context: Context) = Intent(context, FavoritesActivity::class.java)
    }
}
