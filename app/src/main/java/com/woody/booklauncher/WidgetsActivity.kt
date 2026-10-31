package com.woody.booklauncher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.min

/**
 * 위젯 ⑯: 늘 첫 쪽에 있는 미디어 조절(오디오북·TTS)과, 사용자가 고른 안드로이드 위젯을 한 쪽에 하나씩 보여 준다.
 * 전자잉크라 위젯도 쪽으로 넘기고, 위젯을 길게 누르면 뺄 수 있다. 위젯은 [AppWidgetHost] 로 받는다.
 */
class WidgetsActivity : EinkActivity() {
    private lateinit var host: AppWidgetHost
    private lateinit var manager: AppWidgetManager
    private lateinit var body: FrameLayout
    private lateinit var pageText: TextView
    private lateinit var titleCount: TextView
    private lateinit var addText: TextView

    /** 받아 둔 위젯 id(쪽 순서). 첫 쪽(0)은 미디어 조절이라 위젯은 1쪽부터. */
    private var ids: List<Int> = emptyList()
    private var index = 0
    private var pendingId = -1

    private val pageCount get() = 1 + ids.size
    private val pager = object : Paged {
        override val page get() = index
        override val pageCount get() = 1 + ids.size
        override fun next() { index = (index + 1).coerceAtMost(pageCount - 1); render() }
        override fun prev() { index = (index - 1).coerceAtLeast(0); render() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        host = AppWidgetHost(this, HOST_ID)
        manager = AppWidgetManager.getInstance(this)
        ids = loadIds()
        buildViews()
    }

    override fun onStart() {
        super.onStart()
        runCatching { host.startListening() }
    }

    override fun onStop() {
        runCatching { host.stopListening() }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun buildViews() {
        val root = vbox()
        root.addView(titleRow(getString(R.string.widgets_title)) { finish() }.apply {
            titleCount = text("", 21f, color = Ui.GRAY)
            addView(titleCount, lp(WRAP, WRAP).apply { marginStart = dp(14); topMargin = dp(6) })
        })
        root.addView(hline(2))
        body = FrameLayout(this)
        root.addView(body, lp(MATCH, 0, 1f))
        root.addView(hline(1, inset = false))

        val footer = FrameLayout(this)
        addText = text(getString(R.string.widget_add), 21f, Ui.bold).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Ui.MARGIN), 0, dp(24), 0)
            setOnClickListener { pickWidget() }
        }
        footer.addView(addText, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.START))
        pageText = text("", 21f).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), 0, dp(Ui.MARGIN), 0)
            pagesClick(pager)
        }
        footer.addView(pageText, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.END))
        root.addView(footer, lp(MATCH, dp(Ui.FOOTER_DP)))
        setContentView(root)
    }

    override fun onSwipe(toLeft: Boolean): Boolean {
        if (pageCount <= 1) return false
        index = (index + if (toLeft) 1 else -1).coerceIn(0, pageCount - 1)
        render()
        return true
    }

    private fun render() {
        index = index.coerceIn(0, maxOf(0, pageCount - 1))
        titleCount.text = if (ids.isEmpty()) "" else ids.size.toString()
        pageText.text = "${index + 1} / $pageCount"
        body.removeAllViews()
        if (index == 0) {
            body.addView(mediaPage())
            return
        }
        val id = ids[index - 1]
        val info = manager.getAppWidgetInfo(id)
        if (info == null) {
            // 그사이 앱을 지운 위젯은 목록에서 지운다.
            ids = ids - id
            saveIds()
            index = index.coerceAtMost(pageCount - 1)
            render()
            return
        }
        body.addView(widgetPage(id, info))
    }

    /** 미디어 조절: 오디오북·TTS 앱이 받는 미디어 키를 보낸다(재생·일시정지, 앞뒤, 되감기·빨리 감기). */
    private fun mediaPage(): View = vbox().apply {
        setPadding(dp(Ui.MARGIN), dp(22), dp(Ui.MARGIN), dp(12))
        addView(text(getString(R.string.widget_media_title), 28f, Ui.bold))
        addView(
            text(getString(R.string.widget_media_hint), 15f, color = Ui.GRAY, lines = 2),
            lp(MATCH, WRAP).apply { topMargin = dp(8) },
        )
        addView(hbox().apply {
            setPadding(0, dp(28), 0, 0)
            fun key(code: Int, icon: Int, label: Int) = MediaButton(this@WidgetsActivity, icon, getString(label)) {
                mediaKey(code)
            }
            // 소리가 나고 있으면 일시정지 그림, 아니면 재생 그림(상태를 읽는 권한 없이 알 수 있는 만큼만).
            val playing = getSystemService(AudioManager::class.java)?.isMusicActive == true
            val buttons = listOf(
                key(KeyEvent.KEYCODE_MEDIA_PREVIOUS, R.drawable.ic_media_previous, R.string.media_previous),
                key(KeyEvent.KEYCODE_MEDIA_REWIND, R.drawable.ic_media_rewind, R.string.media_rewind),
                key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, if (playing) R.drawable.ic_media_pause else R.drawable.ic_media_play, R.string.media_play_pause),
                key(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, R.drawable.ic_media_forward, R.string.media_forward),
                key(KeyEvent.KEYCODE_MEDIA_NEXT, R.drawable.ic_media_next, R.string.media_next),
            )
            buttons.forEachIndexed { i, button ->
                addView(button, lp(0, dp(88), 1f).apply { if (i > 0) marginStart = dp(10) })
            }
        }, lp(MATCH, WRAP))
        addView(
            text(getString(R.string.widget_media_tip), 14f, color = Ui.LIGHT_GRAY, lines = 2),
            lp(MATCH, WRAP).apply { topMargin = dp(18) },
        )
    }

    @Suppress("DEPRECATION")
    private fun mediaKey(code: Int) {
        val audio = getSystemService(AudioManager::class.java) ?: return
        runCatching {
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
    }

    /** 안드로이드 위젯 한 쪽: 위젯 이름과 `빼기`, 그 아래 위젯 화면. 길게 누르면 뺀다. */
    private fun widgetPage(id: Int, info: AppWidgetProviderInfo): View = vbox().apply {
        setPadding(dp(Ui.MARGIN), dp(16), dp(Ui.MARGIN), dp(12))
        val label = runCatching { info.loadLabel(packageManager).toString() }.getOrDefault(info.provider.shortClassName)
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
            // 위젯이 이 칸에 맞게 그려지도록 크기를 알려 준다.
            view.post { resize(view, frame) }
        }
        addView(frame, lp(MATCH, 0, 1f))
        setOnLongClickListener { confirmRemove(id, label); true }
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
                WidgetPickerActivity.providerOf(data)?.let(::addWidget)
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

    /** 위젯을 이 앱의 호스트에 붙인다. 허용이 필요하면 시스템에 묻는다. */
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
        index = ids.indexOf(id) + 1
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

    /** 전자잉크 버튼: 검은 테두리 안에 아이콘 하나. 애니메이션 없이 바로 누른다. */
    private class MediaButton(
        context: Context,
        iconRes: Int,
        description: String,
        onClick: () -> Unit,
    ) : View(context) {
        private val icon: Drawable? = context.getDrawable(iconRes)
        private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Ui.BLACK
            strokeWidth = context.dpf(1.5f)
        }
        private val rect = RectF()

        init {
            contentDescription = description
            isClickable = true
            setOnClickListener { onClick() }
        }

        override fun onDraw(canvas: Canvas) {
            val inset = border.strokeWidth / 2
            val radius = context.dpf(10f)
            rect.set(inset, inset, width - inset, height - inset)
            canvas.drawRoundRect(rect, radius, radius, border)
            val d = icon ?: return
            val size = min(width, height) * 0.42f
            val left = ((width - size) / 2f).toInt()
            val top = ((height - size) / 2f).toInt()
            d.setBounds(left, top, left + size.toInt(), top + size.toInt())
            d.draw(canvas)
        }
    }

    companion object {
        /** 이 앱의 위젯 호스트 번호(다른 앱과 겹치지 않게 앱마다 하나) */
        private const val HOST_ID = 0x0B00C
        private const val PREFS = "widgets"
        private const val KEY_IDS = "ids"
        private const val REQ_PICK = 41
        private const val REQ_BIND = 42
    }
}
