package com.woody.booklauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView

/**
 * 앱 하나의 아이콘 바꾸기(길게 누르기 › 아이콘 바꾸기): 깔린 아이콘 팩의 그림을 4열 격자로 쪽마다 보여 주고, 누르면 그 앱에만 쓴다.
 * 팩에 없는 앱(교보도서관 등)을 위해서다. 팩마다 그림이 수만 개라 영어 이름으로 찾는다([IconPacks.search]).
 */
class IconPickActivity : EinkActivity() {
    private lateinit var app: AppEntry
    private lateinit var packs: List<IconPacks.Pack>
    private lateinit var pack: IconPacks.Pack
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var rows: PagedRows
    private lateinit var packText: TextView
    private lateinit var pageText: TextView
    /** 지금 팩의 모든 그림 이름(뒤에서 읽는다, 다 읽기 전에는 null) */
    private var all: List<String>? = null
    /** 늦게 끝난 옛 팩 읽기가 새 팩을 덮지 않도록 읽을 때마다 번호를 붙인다. */
    private var loadId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val component = intent.getStringExtra(EXTRA_APP)?.let(ComponentName::unflattenFromString)
        packs = IconPacks.installed(this)
        if (component == null || packs.isEmpty()) { finish(); return }
        app = AppEntry(component, intent.getStringExtra(EXTRA_LABEL).orEmpty())
        // 앱에 고른 아이콘의 팩, 설정에서 고른 팩, 깔린 첫 팩 순서로
        val own = prefs.iconOverrides[app.key]?.substringBefore('/')
        pack = packs.firstOrNull { it.pkg == own } ?: packs.firstOrNull { it.pkg == prefs.iconPack } ?: packs.first()

        val root = vbox()
        root.addView(titleRow(getString(R.string.change_icon)) { finish() })
        root.addView(hline(2))
        root.addView(appRow(app, app.label, Ui.ROW_ICON_DP, Ui.ROW_SP, showIcon = true, right = resetView()), lp(MATCH, rowDp(Ui.ROW_DP)))
        root.addView(hline(1, Ui.DIVIDER))

        // 찾기 줄: 표지 찾기와 같은 모양(박스 없이 아래 굵은 선)
        input = EditText(this).apply {
            hint = getString(R.string.icon_search_hint)
            setSp(24f)
            setTextColor(Ui.BLACK)
            setHintTextColor(Ui.LIGHT_GRAY)
            background = null
            setPadding(0, dp(8), 0, dp(8))
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setOnEditorActionListener { _, action, event ->
                if (action == EditorInfo.IME_ACTION_SEARCH || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                    show(); true
                } else false
            }
        }
        root.addView(hbox().apply {
            setPadding(dp(Ui.MARGIN), dp(10), dp(Ui.MARGIN), 0)
            addView(input, lp(0, WRAP, 1f))
            addView(text(getString(R.string.search), 22f, Ui.bold).apply {
                setPadding(dp(20), dp(12), 0, dp(12))
                setOnClickListener { show() }
            })
        })
        root.addView(hline(2))
        status = text(getString(R.string.searching), 17f, color = Ui.GRAY).apply {
            setPadding(dp(Ui.MARGIN), dp(10), dp(Ui.MARGIN), dp(4))
        }
        root.addView(status)

        val labelH = text("가", LABEL_SP).run { measure(0, 0); measuredHeight }
        rows = PagedRows(this, dp(ICON_DP + CELL_PAD_DP * 2 + LABEL_GAP_DP) + labelH, dividers = false)
        root.addView(rows, lp(MATCH, 0, 1f))

        root.addView(hline(1, inset = false))
        val footer = FrameLayout(this)
        packText = text("", 21f).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Ui.MARGIN), 0, dp(24), 0)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            // 팩이 둘 이상이면 눌러 다른 팩으로
            if (packs.size > 1) setOnClickListener {
                choose(getString(R.string.icon_pack), packs.map { it.label }, packs.indexOf(pack)) {
                    if (packs[it] != pack) { pack = packs[it]; load() }
                }
            }
        }
        footer.addView(packText, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.START))
        pageText = text("", 21f).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), 0, dp(Ui.MARGIN), 0)
            pagesClick(rows)
        }
        footer.addView(pageText, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.END))
        root.addView(footer, lp(MATCH, dp(Ui.FOOTER_DP)))
        rows.onPageChanged = { pageText.text = pageLabel(rows) }
        setContentView(root)
        load()
    }

    override fun onSwipe(toLeft: Boolean): Boolean {
        if (rows.pageCount <= 1) return false
        if (toLeft) rows.next() else rows.prev()
        return true
    }

    /** 오른쪽 끝: 따로 고른 아이콘이 있으면 `원래 아이콘으로`(누르면 지우고 닫는다), 없으면 회색 `기본 아이콘` */
    private fun resetView(): View =
        if (app.key in prefs.iconOverrides) text(getString(R.string.icon_reset), 20f, Ui.bold).apply {
            setPadding(dp(12), dp(12), 0, dp(12))
            setOnClickListener { prefs.setIconOverride(app.key, null); finish() }
        }
        else text(getString(R.string.icon_default_now), 18f, color = Ui.LIGHT_GRAY)

    /** 팩의 그림 이름을 뒤에서 읽고(팩마다 수백 KB) 찾기를 다시 한다. */
    private fun load() {
        val id = ++loadId
        val p = pack
        packText.text = if (packs.size > 1) "${p.label}  ›" else p.label
        all = null
        status.setText(R.string.searching)
        rows.setRows(emptyList())
        Bg.run({ IconPacks.names(applicationContext, p.pkg) }) { result ->
            if (id != loadId || isFinishing || isDestroyed) return@run
            all = result.getOrDefault(emptyList())
            show()
        }
    }

    /** 찾기 칸의 말로 거른 그림을 4열 격자로(찾기 칸이 비면 모두). 그림 이름을 다 읽기 전이면 읽은 뒤에 한다([load]). */
    private fun show() {
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(input.windowToken, 0)
        val names = all ?: return
        val query = input.text.toString().trim()
        val found = IconPacks.search(names, query)
        status.text = when {
            found.isEmpty() -> getString(R.string.icons_none)
            query.isEmpty() -> getString(R.string.icons_all, "%,d".format(found.size))
            else -> getString(R.string.icons_found, query, "%,d".format(found.size))
        }
        val p = pack
        rows.setRows(found.chunked(COLUMNS).map { line -> { gridRow(p, line) } })
        rows.showPage(0)
    }

    /** 그림 한 줄: 칸마다 그림과 이름(`_` 는 띄어 쓴다). 그림 목록에만 있고 그림이 없는 이름은 누를 수 없다(빈 칸). */
    private fun gridRow(p: IconPacks.Pack, names: List<String>): View = hbox().apply {
        setPadding(dp(Ui.MARGIN), 0, dp(Ui.MARGIN), 0)
        names.forEachIndexed { i, name ->
            addView(vbox().apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(CELL_PAD_DP), 0, dp(CELL_PAD_DP))
                val icon = AppIcons.packIcon(context, p.pkg, name, dp(ICON_DP))
                addView(picture(icon), lp(dp(ICON_DP), dp(ICON_DP)))
                addView(text(name.replace('_', ' '), LABEL_SP).apply {
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }, lp(MATCH, WRAP).apply { topMargin = dp(LABEL_GAP_DP) })
                if (icon != null) setOnClickListener {
                    prefs.setIconOverride(app.key, "${p.pkg}/$name")
                    finish()
                }
            }, lp(0, MATCH, 1f).apply { if (i > 0) marginStart = dp(GAP_DP) })
        }
        // 덜 찬 줄도 칸 폭이 같도록 빈 칸을 채운다.
        repeat(COLUMNS - names.size) { addView(View(context), lp(0, 1, 1f).apply { marginStart = dp(GAP_DP) }) }
    }

    companion object {
        private const val COLUMNS = 4
        private const val GAP_DP = 12
        /** 그림(홈 자주 쓰는 앱 격자의 가장 큰 아이콘과 같다) */
        private const val ICON_DP = 56
        private const val CELL_PAD_DP = 8
        private const val LABEL_GAP_DP = 6
        private const val LABEL_SP = 13f
        private const val EXTRA_APP = "app"
        private const val EXTRA_LABEL = "label"

        fun intent(context: Context, app: AppEntry) = Intent(context, IconPickActivity::class.java)
            .putExtra(EXTRA_APP, app.component.flattenToString())
            .putExtra(EXTRA_LABEL, app.label)
    }
}
