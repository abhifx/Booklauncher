package com.woody.booklauncher

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView

/**
 * 위젯 고르기: 기기에 깔린 안드로이드 위젯을 앱 이름과 함께 목록으로 보여 준다.
 * 시스템 위젯 고르기 창 대신 이 앱의 전자잉크 목록을 쓴다(쪽으로 넘기고, 애니메이션 없음).
 * 고른 위젯은 [WidgetsActivity] 로 돌려준다.
 */
class WidgetPickerActivity : EinkActivity() {
    private lateinit var rows: PagedRows
    private lateinit var pageText: TextView
    private lateinit var empty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = vbox()
        val title = titleRow(getString(R.string.widget_picker_title)) { finish() }
        root.addView(title)
        root.addView(hline(2))
        empty = text(getString(R.string.widget_picker_empty), 19f, color = Ui.GRAY).apply {
            setPadding(dp(Ui.MARGIN), dp(28), dp(Ui.MARGIN), 0)
            visibility = View.GONE
        }
        root.addView(empty)
        rows = PagedRows(this, rowDp(Ui.ROW_DP))
        root.addView(rows, lp(MATCH, 0, 1f))

        root.addView(hline(1, inset = false))
        val footer = FrameLayout(this)
        pageText = text("", 21f).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), 0, dp(Ui.MARGIN), 0)
            pagesClick(rows)
        }
        footer.addView(pageText, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.END))
        root.addView(footer, lp(MATCH, dp(Ui.FOOTER_DP)))
        rows.onPageChanged = { pageText.text = pageLabel(rows) }
        setContentView(root)

        val providers = runCatching { AppWidgetManager.getInstance(this).installedProviders }
            .getOrDefault(emptyList())
            .map { ProviderItem(it, appLabel(it.provider.packageName), widgetLabel(it)) }
            .distinctBy { it.info.provider }
            .sortedWith(compareBy<ProviderItem> { it.appLabel.lowercase() }.thenBy { it.widgetLabel.lowercase() })
        val builtIn = NativeWidget.entries.map { widget -> { nativeRow(widget) } }
        empty.visibility = if (providers.isEmpty() && builtIn.isEmpty()) View.VISIBLE else View.GONE
        rows.setRows(builtIn + providers.map { item -> { pickerRow(item) } })
    }

    override fun onSwipe(toLeft: Boolean): Boolean {
        if (rows.pageCount <= 1) return false
        if (toLeft) rows.next() else rows.prev()
        return true
    }

    private fun appLabel(pkg: String): String = runCatching {
        val info = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(pkg)

    private fun widgetLabel(info: AppWidgetProviderInfo): String =
        runCatching { info.loadLabel(packageManager).toString() }.getOrDefault(info.provider.shortClassName)

    /** Built-in card: name and a short "Built-in" label. */
    private fun nativeRow(widget: NativeWidget): View = hbox().apply {
        setPadding(dp(Ui.MARGIN), 0, dp(Ui.MARGIN), 0)
        addView(vbox().apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(text(getString(widget.title), Ui.ROW_SP))
            addView(
                text(
                    getString(R.string.widget_picker_builtin) +
                        if (widget in NativeWidgets.load(this@WidgetPickerActivity)) " · " + getString(R.string.widget_added) else "",
                    14f, color = Ui.GRAY,
                ),
                lp(MATCH, WRAP).apply { topMargin = dp(2) },
            )
        }, lp(0, WRAP, 1f))
        setOnClickListener {
            setResult(RESULT_OK, Intent().putExtra(EXTRA_NATIVE, widget.key))
            finish()
        }
    }

    /** 앱 아이콘 · 위젯 이름 · 앱 이름. 누르면 고른 위젯의 ComponentName 을 돌려주고 닫는다. */
    private fun pickerRow(item: ProviderItem): View = hbox().apply {
        setPadding(dp(Ui.MARGIN), 0, dp(Ui.MARGIN), 0)
        icon(item.info.provider.packageName)?.let {
            addView(PictureView(this@WidgetPickerActivity).apply { drawable = it }, lp(dp(Ui.ROW_ICON_DP), dp(Ui.ROW_ICON_DP)).apply { marginEnd = dp(16) })
        }
        addView(vbox().apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(text(item.widgetLabel, Ui.ROW_SP))
            addView(text(item.appLabel, 14f, color = Ui.GRAY), lp(MATCH, WRAP).apply { topMargin = dp(2) })
        }, lp(0, WRAP, 1f))
        setOnClickListener {
            setResult(
                RESULT_OK,
                Intent().putExtra(EXTRA_PROVIDER, item.info.provider.flattenToString()),
            )
            finish()
        }
    }

    private fun icon(pkg: String): Drawable? = runCatching { packageManager.getApplicationIcon(pkg) }.getOrNull()

    private data class ProviderItem(val info: AppWidgetProviderInfo, val appLabel: String, val widgetLabel: String)

    companion object {
        const val EXTRA_PROVIDER = "provider"
        const val EXTRA_NATIVE = "native"

        fun providerOf(intent: Intent): ComponentName? =
            intent.getStringExtra(EXTRA_PROVIDER)?.let(ComponentName::unflattenFromString)

        fun nativeOf(intent: Intent): NativeWidget? =
            NativeWidget.of(intent.getStringExtra(EXTRA_NATIVE))
    }
}
