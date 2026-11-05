package com.woody.pebbledesk

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.woody.pebbledesk.readers.ReaderApps
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * 자동 추가 기록(설정 › 기기 정보 › 기록 보기). 사진 한 장으로 받으려고 쪽을 넘기지 않는다: 위에 요약(서비스 연결 · 첫 알림 ·
 * 앱별 받은 알림 수)과 오늘 읽은 시간 확인([readingCheck]), 아래에 들어가는 만큼 최근 기록. 지우고 한 번 해 본 뒤 찍으면 그 시도가 한 화면에 든다. 기록은 [WatchLog].
 */
class WatchLogActivity : EinkActivity() {
    /** 사진으로 찍어 보내는 진단 화면이라 글자 · 간격 설정과 기기 글꼴 크기를 따르지 않는다. */
    override val fixedTextSize get() = true
    private lateinit var summary: TextView
    private lateinit var rows: PagedRows
    private lateinit var readingValue: TextView
    private lateinit var eventsValue: TextView
    private lateinit var marksValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = vbox()
        root.addView(titleRow(getString(R.string.watch_log)) { finish() })
        root.addView(hline(2))
        val body = vbox().apply { setPadding(dp(Ui.MARGIN), dp(8), dp(Ui.MARGIN), 0) }
        body.addView(text(getString(R.string.watch_log_hint), 15f, color = Ui.GRAY, lines = 2))
        summary = text("", 15f, Ui.bold, lines = 5).apply { setLineSpacing(0f, 1.15f) }
        body.addView(summary, lp(MATCH, WRAP).apply { topMargin = dp(10); bottomMargin = dp(8) })
        // 읽은 시간이 실제보다 적게 나오는 기기에서 어디서 빠졌는지([readingCheck]). 기기 정보에는 더 넣을 자리가 없다(DESIGN.md 기기 정보).
        body.addView(text(getString(R.string.watch_log_reading), 14f, color = Ui.LIGHT_GRAY))
        readingValue = text("", 14f, lines = 2).apply { setLineSpacing(0f, 1.15f) }
        eventsValue = text("", 14f, lines = 2).apply { setLineSpacing(0f, 1.15f) }
        marksValue = text("", 14f)
        listOf(readingValue, eventsValue, marksValue).forEach { body.addView(it) }
        body.addView(hline(1, Ui.DIVIDER, inset = false).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = dp(8) })
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
        readingCheck()
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

    /**
     * 오늘 읽은 시간 확인 세 줄: 이북 앱마다 `KOReader 앞 32:10 · 센 1:05`([ReadingLog.todayCheck]) / 오늘의 마지막 [EVENT_COUNT] 개
     * 화면 꺼짐·켜짐·잠금과 이북 앱이 앞·뒤로 간 때(앱 이름은 바뀔 때만, 읽는 화면이 아닌 화면이 앞에 오면 `서재`) / 오늘 적은 열림·닫힘 표시(앱마다 오늘 시작 때의 상태부터) 마지막 [MARK_COUNT] 개.
     */
    private fun readingCheck() {
        val packages = ReadingLog.readerPackages(this)
        val check = ReadingLog.todayCheck(this, packages)
        if (check == null) {
            readingValue.text = getString(R.string.device_recent_no_access)
            eventsValue.text = ""
            marksValue.text = ""
            return
        }
        val names = HashMap<String, String>()
        fun name(pkg: String) = names.getOrPut(pkg) {
            runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
        }
        fun dur(ms: Long) = "%d:%02d".format(Locale.US, ms / 60_000, ms / 1000 % 60)
        readingValue.text = check.entries.sortedByDescending { it.value.first }
            .joinToString("  /  ") { (pkg, t) -> getString(R.string.watch_log_reading_app, name(pkg), dur(t.first), dur(t.second)) }
            .ifEmpty { getString(R.string.watch_log_reading_none) }

        val now = System.currentTimeMillis()
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val events = getSystemService(UsageStatsManager::class.java)?.queryEvents(start, now)
        val out = java.util.ArrayDeque<Pair<String?, String>>()
        val e = UsageEvents.Event()
        while (events?.hasNextEvent() == true) {
            events.getNextEvent(e)
            val reading = e.packageName in packages && e.className?.let { ReadingLog.isReaderScreen(this, e.packageName, it) || ReaderApps.hostsViewer(it) } == true
            @Suppress("DEPRECATION")
            val what = when (e.eventType) {
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> R.string.watch_log_ev_screen_off
                UsageEvents.Event.SCREEN_INTERACTIVE -> R.string.watch_log_ev_screen_on
                UsageEvents.Event.KEYGUARD_SHOWN -> R.string.watch_log_ev_lock
                UsageEvents.Event.KEYGUARD_HIDDEN -> R.string.watch_log_ev_unlock
                UsageEvents.Event.MOVE_TO_FOREGROUND -> if (reading) R.string.watch_log_ev_front else if (e.packageName in packages) R.string.watch_log_ev_shelf else null
                UsageEvents.Event.MOVE_TO_BACKGROUND -> if (reading) R.string.watch_log_ev_back else null
                else -> null
            } ?: continue
            val pkg = if (e.packageName in packages) e.packageName else null
            out.addLast(pkg to "${HM.format(e.timeStamp)} ${getString(what)}")
            if (out.size > EVENT_COUNT) out.removeFirst()
        }
        var lastPkg: String? = null
        eventsValue.text = out.joinToString(" · ") { (pkg, text) ->
            if (pkg == null || pkg == lastPkg) text else text.replaceFirst(" ", " ${name(pkg)} ").also { lastPkg = pkg }
        }.ifEmpty { "-" }

        lastPkg = null
        // 오늘 전의 것은 그 앱의 오늘 시작 때 상태(어제 닫힌 표시가 오늘도 자르고 있는지 보이게, 날짜를 붙인다)
        val marks = ReadingLog.marksFrom(this, start).takeLast(MARK_COUNT).joinToString(" · ") { m ->
            val state = getString(if (m.shown) R.string.watch_log_mark_open else R.string.watch_log_mark_closed)
            val time = if (m.at < start) "${MD.format(m.at)} ${HM.format(m.at)}" else HM.format(m.at)
            (if (m.pkg == lastPkg) "" else name(m.pkg) + " ").also { lastPkg = m.pkg } + "$time $state"
        }
        marksValue.text = getString(R.string.watch_log_marks, marks.ifEmpty { "-" })
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
            WatchLog.Kind.NOTE -> e.arg
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
        /** 오늘 읽은 시간 확인에 보이는 사용 기록 수(두 줄에 들게)와 표시 수(한 줄) */
        private const val EVENT_COUNT = 8
        private const val MARK_COUNT = 4
        private val HMS = SimpleDateFormat("HH:mm:ss", Locale.US)
        private val HM = SimpleDateFormat("HH:mm", Locale.US)
        private val MD = SimpleDateFormat("MM/dd", Locale.US)

        fun intent(context: Context) = Intent(context, WatchLogActivity::class.java)
    }
}
