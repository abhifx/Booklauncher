package com.woody.booklauncher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import com.woody.booklauncher.readers.ReaderApps
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 자동 추가 기록(설정 › 기기 정보 › 기록 보기). 사진 한 장으로 받으려고 쪽을 넘기지 않는다: 위에 요약(서비스 연결 · 첫 알림 ·
 * 앱별 받은 알림 수), 아래에 들어가는 만큼 최근 기록. 지우고 한 번 해 본 뒤 찍으면 그 시도가 한 화면에 든다. 기록은 [WatchLog].
 */
class WatchLogActivity : EinkActivity() {
    /** 사진으로 찍어 보내는 진단 화면이라 글자 · 간격 설정과 기기 글꼴 크기를 따르지 않는다. */
    override val fixedTextSize get() = true
    private lateinit var summary: TextView
    private lateinit var rows: PagedRows

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = vbox()
        root.addView(titleRow(getString(R.string.watch_log)) { finish() })
        root.addView(hline(2))
        val body = vbox().apply { setPadding(dp(Ui.MARGIN), dp(8), dp(Ui.MARGIN), 0) }
        body.addView(text(getString(R.string.watch_log_hint), 15f, color = Ui.GRAY, lines = 2))
        summary = text("", 15f, Ui.bold, lines = 5).apply { setLineSpacing(0f, 1.15f) }
        body.addView(summary, lp(MATCH, WRAP).apply { topMargin = dp(10); bottomMargin = dp(8) })
        body.addView(hline(1, Ui.DIVIDER, inset = false))
        rows = PagedRows(this, dp(ROW_DP), dividers = false)
        body.addView(rows, lp(MATCH, 0, 1f))
        root.addView(body, lp(MATCH, 0, 1f))

        root.addView(hline(1, inset = false))
        val footer = FrameLayout(this)
        footer.addView(text(getString(R.string.watch_log_clear), 21f).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Ui.MARGIN), 0, dp(24), 0)
            setOnClickListener { WatchLog.clear(this@WatchLogActivity); refresh() }
        }, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.START))
        root.addView(footer, lp(MATCH, dp(Ui.FOOTER_DP)))
        setContentView(root)
    }

    /** 이북 앱에서 돌아올 때마다 새로 */
    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        summary.text = summaryText()
        val lines = WatchLog.entries(this).map(::line).ifEmpty { listOf(getString(R.string.watch_log_empty)) }
        rows.setRows(lines.map { l -> { text(l, 14f).apply { gravity = Gravity.CENTER_VERTICAL } } })
    }

    /**
     * 세 줄: 서비스 켜짐·마지막 연결·끊긴 횟수 / 언제부터 센 것인지·그 뒤 첫 알림·화면 못 읽은 횟수 /
     * 앱별 받은 알림 수(깔린 이북 앱은 0 이어도 보인다. 알림이 오지 않는 앱을 가린다).
     */
    private fun summaryText(): String {
        val s = WatchLog.summary(this)
        val (connected, _, stops) = ReaderWatchService.lifeLog(this)
        val service = getString(R.string.watch_log_service,
            getString(if (ReaderWatchService.isEnabled(this)) R.string.on else R.string.off), at(connected), stops)
        val first = if (s.firstAt == 0L) getString(R.string.watch_log_none) else "${clock(s.firstAt)} ${WatchLog.shortPkg(s.firstPkg)}"
        val since = getString(R.string.watch_log_since, at(s.since), first, s.noWindow)
        val installed = AppStore.load(this).map { it.pkg }.filter { it in ReaderApps.packages }
        val events = (installed + s.events.keys).distinct()
            .map { it to (s.events[it] ?: (0 to 0L)) }
            .sortedByDescending { it.second.first }
            .joinToString(" · ") { (pkg, e) ->
                WatchLog.shortPkg(pkg) + " ${e.first}" + if (e.second > 0) " (${HM.format(e.second)})" else ""
            }
        return listOf(service, since, getString(R.string.watch_log_events, events.ifEmpty { getString(R.string.watch_log_none) }))
            .joinToString("\n")
    }

    /** 한 줄: `18:31:05  millie.eink  화면 EPubViewActivity · 읽는 화면 ×3`. 오늘이 아니면 날짜를 앞에. */
    private fun line(e: WatchLog.Entry): String {
        val text = when (e.kind) {
            WatchLog.Kind.CONNECTED -> getString(R.string.wl_connected)
            WatchLog.Kind.UNBOUND -> getString(R.string.wl_unbound)
            WatchLog.Kind.DESTROYED -> getString(R.string.wl_destroyed)
            WatchLog.Kind.FIRST_EVENT -> getString(R.string.wl_first)
            WatchLog.Kind.SCREEN -> getString(R.string.wl_screen, e.arg, getString(when (e.detail) {
                WatchLog.Role.SHELF.name -> R.string.wl_role_shelf
                WatchLog.Role.VIEWER.name -> R.string.wl_role_viewer
                else -> R.string.wl_role_other
            }))
            WatchLog.Kind.OTHER_WINDOW -> getString(R.string.wl_other_window, e.arg.ifEmpty { "-" })
            WatchLog.Kind.NO_WINDOW -> getString(R.string.wl_no_window)
            WatchLog.Kind.SHELF -> getString(R.string.wl_shelf, e.arg)
            WatchLog.Kind.CLICK -> if (e.arg.isEmpty()) getString(R.string.wl_click_none) else getString(R.string.wl_click, e.arg)
            WatchLog.Kind.OPENED -> getString(R.string.wl_opened, e.arg, getString(when (e.detail) {
                WatchLog.Opened.NEW.name -> R.string.wl_opened_new
                WatchLog.Opened.FULL.name -> R.string.wl_opened_full
                WatchLog.Opened.FINISHED.name -> R.string.wl_opened_finished
                else -> R.string.wl_opened_front
            }))
            WatchLog.Kind.PROGRESS -> getString(R.string.wl_progress, if (e.arg == "-1") "?" else "${e.arg}%") +
                if (e.detail.isEmpty()) "" else " · ${e.detail}"
            WatchLog.Kind.RECENT_WAIT -> getString(R.string.wl_recent_wait, e.arg)
            WatchLog.Kind.FILE -> getString(R.string.wl_file, e.arg, e.detail)
            WatchLog.Kind.ERROR -> getString(R.string.wl_error, e.arg, e.detail)
            WatchLog.Kind.CRASH -> getString(R.string.wl_crash, e.arg, e.detail)
        }
        val time = if (DateUtils.isToday(e.at)) clock(e.at) else "${MD.format(e.at)} ${clock(e.at)}"
        val app = if (e.pkg.isEmpty()) "" else "  ${WatchLog.shortPkg(e.pkg)}"
        return "$time$app  $text" + if (e.repeat > 1) "  ×${e.repeat}" else ""
    }

    private fun clock(ms: Long) = HMS.format(ms)
    private fun at(ms: Long) = if (ms == 0L) "-" else "${MD.format(ms)} ${HM.format(ms)}"

    companion object {
        private const val ROW_DP = 27
        private val HMS = SimpleDateFormat("HH:mm:ss", Locale.US)
        private val HM = SimpleDateFormat("HH:mm", Locale.US)
        private val MD = SimpleDateFormat("MM/dd", Locale.US)

        fun intent(context: Context) = Intent(context, WatchLogActivity::class.java)
    }
}
