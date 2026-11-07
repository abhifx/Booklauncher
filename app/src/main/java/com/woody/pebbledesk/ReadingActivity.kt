package com.woody.pebbledesk

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * ⑫ 독서 기록: 연속일과 오늘 링, 최근 4주 목표 링 달력(애플 활동 앱처럼), 이번 달 목표 달성, 이번 주·이번 달·올해 합계, 이번 주 책별 시간.
 * 제목 오른쪽 `올해 읽은 책 ›` 은 올해 읽은 책([YearBooksActivity]).
 * 홈 하단의 `오늘 42분`(바형은 `42분`)이나 설정 > 독서 기록으로 연다. 숫자는 [ReadingLog] 에서 온다.
 */
class ReadingActivity : EinkActivity() {
    private lateinit var body: LinearLayout
    /** 이번 주 읽은 책(다섯 권이 넘으면 쪽을 넘긴다). 읽은 책이 없으면 null. */
    private var bookRows: PagedRows? = null
    /** 제목 옆 보기 거르기 `전체 ▾`(카테고리가 있을 때만), 제목 줄과 오른쪽 `올해 읽은 책 ›`(다 들지 않으면 줄인다) */
    private lateinit var filterText: TextView
    private lateinit var titleBar: LinearLayout
    private lateinit var yearLink: TextView

    /**
     * 가로 화면: 왼쪽 요약 칸(연속일·오늘 링, 읽은 시간, 이번 주 읽은 책)과 오른쪽 달력·그래프(남는 높이를 채운다).
     * 오른쪽이 넓어 달력 둘이 들어가면(바형) 달력과 그래프를 나란히, 아니면(페블) 지금처럼 하나씩 바꿔 본다.
     */
    private val wide by lazy { landscape() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = vbox()
        root.addView(titleRow(getString(R.string.reading_stats), Palette.Area.READING) { finish() }.apply {
            filterText = text("", 21f, color = Ui.GRAY).apply { setPadding(dp(14), dp(20), dp(8), dp(20)) }
            addView(filterText, lp(WRAP, WRAP).apply { topMargin = dp(6) })
            addView(View(context), lp(0, 0, 1f))
            // 올해 읽은 책
            val link = text(getString(R.string.year_books_link), 17f, Ui.bold).apply {
                setPadding(dp(16), dp(20), 0, dp(20))
                setOnClickListener { startActivity(YearBooksActivity.intent(this@ReadingActivity)) }
            }
            addView(link, lp(WRAP, WRAP))
            yearLink = link
            titleBar = this
            fitTitleLink(this, link)
        })
        root.addView(hline(2))
        body = vbox()
        root.addView(body, lp(MATCH, 0, 1f))
        // 가로면 달력과 그래프를 함께 둘지는 오른쪽 칸 폭으로 정한다(높이는 잴 필요 없다)
        if (wide) both = (resources.displayMetrics.widthPixels - summaryWidth()) / 2 - 2 * dp(Ui.MARGIN) >= dp(CAL_MIN_DP)
        checkRoom()
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    /** 달력과 그래프를 함께 보이는가: 세로는 높이가 있으면(처음 그리기 전에 한 번 잰다, [checkRoom]), 가로는 오른쪽 칸 폭으로 만들 때 정한다. 모르면 null */
    private var both: Boolean? = null
    /** 지금 화면에 달력이 있는가(사용 기록 권한이 없으면 안내 글뿐이라 높이를 재지 않는다) */
    private var hasCalendar = false

    /**
     * 지금 그려 둔 기록의 표(오늘·DB 순번·오늘 책별 시간·목표·한 주 시작 요일·달력과 그래프를 함께). 돌아왔을 때 같으면(화면만 껐다 켬, 올해 읽은 책에서 아무것도 바꾸지 않음)
     * 다시 만들지 않는다: 같은 화면을 다시 그려 깜빡이지 않고, 넘겨 둔 달력 쪽도 그대로다.
     */
    private var shown: List<Any?>? = null

    private fun render() {
        val raw = ReadingLog.days(this, ReadingLog.readerPackages(this))
        val today = LocalDate.now()
        // 보기 거르기(고른 카테고리의 책만, `전체`면 통계에서 뺀 카테고리의 책만 빼고). 화면 전체(연속일·링·달력·합계·이번 주 읽은 책)가 거른 기록으로 센다.
        val filter = StatsFilter.of(this, prefs.statsPicked(HomePrefs.StatsScreen.READING))
        // 지난날이 저장되거나 기록을 고치면 DB 순번이 바뀐다(세기가 날이 바뀐 지난날을 저장하므로 DB 순번은 센 뒤에 본다)
        val state = listOf(raw?.get(today), today, Db.changes(), prefs.readingGoalMin, prefs.firstDayOfWeek, both, raw == null, filter.picked)
        if (state == shown) return
        shown = state
        // 거르기는 다시 그릴 때만(모든 날을 거른 사본을 만든다)
        val days = raw?.let(filter::days)
        showFilter(filter, raw, today)
        body.removeAllViews()
        hasCalendar = days != null
        if (days == null) {
            body.addView(text(getString(R.string.reading_no_access), 18f, color = Ui.GRAY, lines = 3).apply {
                setPadding(dp(Ui.MARGIN), dp(40), dp(Ui.MARGIN), 0)
            })
            return
        }
        // 지난날은 그날 정해져 있던 목표로(목표를 바꿔도 지난 링은 그대로). 제목·오늘 링은 지금 목표.
        val goals = prefs.readingGoals()
        val goalMs = prefs.readingGoalMin * 60_000L
        // 목표가 없던 날은 읽은 날(5분 이상)만 꽉 찬 링으로
        fun frac(day: LocalDate): Float {
            val ms = days.total(day)
            val goal = goals(day) * 60_000L
            return if (goal > 0) (ms.toFloat() / goal).coerceAtMost(1f) else if (ms >= ReadingLog.MIN_DAY_MS) 1f else 0f
        }

        val since = ReadingLog.since(this) ?: today
        if (wide) {
            renderWide(days, today, since, goalMs, ::frac)
            return
        }
        val month = monthGoal(days, today, since, goalMs, ::frac)
        // 가운데 글자가 숫자와 링 사이에 다 들지 않으면(좁은 화면) 가로 화면처럼 숫자를 `일 연속` 옆에 둔다
        body.addView(hero(days, today, goalMs, frac(today), month) ?: heroCompact(days, today, goalMs, frac(today), month))
        // 연속일 줄과 달력 사이는 조금 띄운다(달력 제목 줄이 오늘 링 바로 밑에 붙지 않게).
        body.addView(calendar(days, today, since, goalMs, ::frac), lp(MATCH, WRAP).apply {
            topMargin = dp(HERO_GAP_DP - if (Palette.current.color) FIELD_BOTTOM_DP else 0)
        })
        val inner = resources.displayMetrics.widthPixels - 2 * dp(Ui.MARGIN)
        val narrow = inner - dp(TOTALS_W * Ui.textGrow) - dp(BOOKS_GAP) < dp(BOOKS_MIN_DP)
        if (narrow || Ui.tallScreen) {
            // 바형·좁은 화면: 합계를 한 줄 세 칸으로, 그 아래 이번 주 읽은 책을 전체 폭으로.
            // 좁은 화면은 책을 남는 높이에 들어가는 만큼 한 쪽에(세로도 모자라다), 바형은 다섯 권씩(남는 높이에 그래프가 들게, [checkRoom])
            body.addView(section(getString(R.string.read_time), totalsRow(days, today)), lp(MATCH, WRAP).inset().apply { topMargin = dp(10) })
            val page = text("", 13f, Ui.bold, Ui.GRAY)
            body.addView(section(getString(R.string.books_this_week), byBook(days, today, page, fill = narrow), page, fill = narrow),
                lp(MATCH, if (narrow) 0 else WRAP, if (narrow) 1f else 0f).inset().apply { topMargin = dp(STACK_GAP_DP); bottomMargin = dp(12) })
            return
        }
        // 아래: 왼쪽 합계 · 오른쪽 이번 주 읽은 책. 칸마다 달력처럼 제목과 그 아래 선(두 칸 모두 위에 맞춘다).
        body.addView(hbox().apply {
            gravity = Gravity.TOP
            setPadding(dp(Ui.MARGIN), dp(10), dp(Ui.MARGIN), 0)
            addView(section(getString(R.string.read_time), totals(days, today)), lp(dp(TOTALS_W * Ui.textGrow), WRAP))
            val page = text("", 13f, Ui.bold, Ui.GRAY)
            addView(section(getString(R.string.books_this_week), byBook(days, today, page), page), lp(0, WRAP, 1f).apply { marginStart = dp(BOOKS_GAP) })
        })
    }

    /**
     * 제목 옆 `전체 ▾`(카테고리를 하나도 만들지 않았으면 두지 않는다). 누르면 보기 메뉴(줄마다 그것만 골랐을 때 올해 읽은 시간).
     * 제목 줄에 다 들지 않으면 `올해 읽은 책 ›` 을 줄인다(처음 크기부터 다시 잰다).
     */
    private fun showFilter(filter: StatsFilter, raw: ReadingDays?, today: LocalDate) {
        filterText.visibility = if (filter.categories.isEmpty()) View.GONE else View.VISIBLE
        filterText.text = "${filterName(filter)} ▾"
        filterText.setOnClickListener {
            showStatsFilter(filter, getString(R.string.filter_reading_sub), { f ->
                val d = raw?.let(f::days).orEmpty()
                duration(d.filterKeys { it.year == today.year && it <= today }.keys.sumOf { d.total(it) })
            }) { prefs.setStatsPicked(HomePrefs.StatsScreen.READING, it); render() }
        }
        yearLink.setSp(17f)
        fitTitleLink(titleBar, yearLink)
    }

    /** 좌우 여백만큼 들인다 */
    private fun LinearLayout.LayoutParams.inset() = apply { marginStart = dp(Ui.MARGIN); marginEnd = dp(Ui.MARGIN) }

    /** 가로 화면 왼쪽 요약 칸 폭(좌우 여백 포함). 홈처럼 글자 · 간격과 상관없이 같다(칸 자리가 움직이지 않게). */
    private fun summaryWidth() = dp(SUMMARY_DP) + 2 * dp(Ui.MARGIN)

    /**
     * 가로 화면: 왼쪽 요약 칸 | 옅은 세로 선 | 오른쪽 달력·그래프(높이를 채운다).
     * 홈처럼 틀은 글자 · 간격과 상관없이 같다: 칸 폭, 머리(연속일·오늘 링)·칸 제목·읽은 시간 줄·이번 주 읽은 책 줄의 높이는 기준 크기 글자(100%)로 정하고
     * 글자만 키운다. 다 들지 않는 줄은 그리지 않는다([FitColumn]).
     */
    private fun renderWide(days: ReadingDays, today: LocalDate, since: LocalDate, goalMs: Long, frac: (LocalDate) -> Float) {
        val month = monthGoal(days, today, since, goalMs, frac)
        val heroH = Ui.atBaseText { heroCompact(days, today, goalMs, frac(today), month).heightAt(summaryWidth()) }
        body.addView(hbox().apply {
            gravity = Gravity.TOP
            // 글자를 키워 칸 높이를 넘으면 다 들지 않는 칸은 그리지 않는다(반쯤 잘린 줄이 보이지 않게)
            addView(FitColumn(context, unbounded = false).apply {
                addView(heroCompact(days, today, goalMs, frac(today), month, heroH), lp(MATCH, heroH))
                addView(section(getString(R.string.read_time), totalsCompact(days, today), titleTopDp = 12), lp(MATCH, WRAP).inset())
                val page = text("", 13f, Ui.bold, Ui.GRAY)
                // 이번 주 읽은 책은 남는 높이에 들어가는 만큼 한 쪽에
                addView(section(getString(R.string.books_this_week), byBook(days, today, page, fill = true), page, fill = true, titleTopDp = 14),
                    lp(MATCH, 0, 1f).inset().apply { bottomMargin = dp(12) })
            }, lp(summaryWidth(), MATCH))
            addView(vline(16))
            addView(calendar(days, today, since, goalMs, frac, fill = true), lp(0, MATCH, 1f))
        }, lp(MATCH, MATCH))
    }

    /**
     * 처음 그리기 전에 아래 남는 높이를 재서, 그래프 하나(+ 사이)가 더 들어가면 달력과 그래프를 함께 보이게 다시 만든다
     * (세로가 긴 바형 화면. 넘친 화면을 한 번 그리지 않게 이번 그리기는 건너뛴다). 페블·글자를 키운 화면은 지금처럼 하나씩.
     */
    private fun checkRoom() {
        body.viewTreeObserver.addOnPreDrawListener {
            if (both != null || !hasCalendar) return@addOnPreDrawListener true
            // 쓴 높이: 마지막 칸의 아래 끝(칸 사이 여백까지 든다)
            val used = if (body.childCount == 0) 0 else body.getChildAt(body.childCount - 1).bottom
            both = body.height - used >= ringCalendarHeight(this, WEEKS) + dp(GRAPH_GAP_DP)
            if (both == true) { render(); false } else true
        }
    }

    /**
     * 제목(오른쪽 끝에 [page]: 쪽 표시) · 그 아래 선 · [content]. [fill] 이면 [content] 가 남는 높이를 채운다(이 칸도 높이를 받아야 한다).
     * [titleTopDp] 가 있으면(가로 화면) 제목 줄 높이를 그 위 여백 + 기준 크기 글자로 고정하고 글자를 선 쪽에 붙인다(키운 글자는 위 여백으로 자란다. 홈 구역 제목처럼).
     */
    private fun section(title: String, content: View, page: TextView? = null, fill: Boolean = false, titleTopDp: Int? = null) = vbox().apply {
        val titleH = titleTopDp?.let { top -> dp(top) + Ui.atBaseText { text(title, 13f, Ui.bold).run { measure(0, 0); measuredHeight } } } ?: WRAP
        addView(hbox().apply {
            gravity = Gravity.BOTTOM
            addView(text(title, 13f, Ui.bold, Ui.GRAY), lp(0, WRAP, 1f))
            // 쪽 표시가 작아 누르기 어려우므로 제목 줄 어디를 눌러도 넘긴다.
            page?.let { addView(it, lp(WRAP, WRAP)); setOnClickListener { _ -> it.performClick() } }
        }, lp(MATCH, titleH).apply { bottomMargin = dp(8) })
        addView(hline(1, inset = false))
        addView(content, lp(MATCH, if (fill) 0 else WRAP, if (fill) 1f else 0f).apply { topMargin = dp(4) })
    }

    /**
     * 왼쪽 큰 연속일과 이번 달 목표 달성([month]: 제목 to 값), 오른쪽 오늘 링(가운데 오늘 읽은 시간).
     * 가운데 글자가 숫자와 링 사이에 다 들지 않으면(기기가 밀도를 덮어써 폭이 좁은 화면·키운 글자) null: [heroCompact] 를 쓴다.
     */
    private fun hero(days: ReadingDays, today: LocalDate, goalMs: Long, todayFrac: Float, month: Pair<String, String>): LinearLayout? {
        val number = text(ReadingLog.streak(days, today).toString(), 80f, Ui.heavy, Palette.current.goal)
        val column = vbox().apply {
            addView(text(getString(R.string.streak_sub, ReadingLog.longestStreak(days)), 15f, Ui.medium, Ui.GRAY))
            addView(text(getString(R.string.streak_days), 28f, Ui.bold), lp(WRAP, WRAP).apply { topMargin = dp(4) })
            addView(monthLine(month), lp(WRAP, WRAP).apply { topMargin = dp(8) })
        }
        number.measure(0, 0)
        column.measure(0, 0)
        val room = resources.displayMetrics.widthPixels - 2 * dp(Ui.MARGIN) - number.measuredWidth - dp(10) - dp(RING)
        if (column.measuredWidth > room) return null
        return hbox().apply {
            // 컬러면 독서 기록 색면(파랑)을 깔고 아래를 조금 넓힌다(달력과의 사이에서 뺀다).
            val field = Palette.current.field(Palette.Area.READING)
            field?.let { setBackgroundColor(it) }
            setPadding(dp(Ui.MARGIN), dp(8), dp(Ui.MARGIN), if (field != null) dp(FIELD_BOTTOM_DP) else 0)
            addView(number)
            addView(column, lp(0, WRAP, 1f).apply { marginStart = dp(10) })
            addView(todayRing(days, today, goalMs, todayFrac), lp(dp(RING), dp(RING)))
        }
    }

    /**
     * 가로 화면 왼쪽 위(세로 화면도 [hero] 가 들지 않으면): 왼쪽에 `읽는 중 · 최장 N일` · 큰 연속일과 `일 연속` · 이번 달 목표 달성을 위아래로, 오른쪽 오늘 링.
     * 세로 화면([hero])은 큰 숫자가 글자 왼쪽에 있어 좁은 칸에 들지 않는다.
     */
    private fun heroCompact(days: ReadingDays, today: LocalDate, goalMs: Long, todayFrac: Float, month: Pair<String, String>, frameH: Int? = null) = hbox().apply {
        gravity = Gravity.TOP
        val field = Palette.current.field(Palette.Area.READING)
        field?.let { setBackgroundColor(it) }
        setPadding(dp(Ui.MARGIN), dp(12), dp(Ui.MARGIN), if (field != null) dp(FIELD_BOTTOM_DP) else 0)
        val number = text(ReadingLog.streak(days, today).toString(), STREAK_WIDE_SP, Ui.heavy, Palette.current.goal)
        // 칸 높이가 정해져 있으면(글자 · 간격과 상관없이) 다 들지 않는 줄은 그리지 않는다
        val column = FitColumn(context).apply {
            addView(text(getString(R.string.streak_sub, ReadingLog.longestStreak(days)), 15f, Ui.medium, Ui.GRAY))
            addView(hbox().apply {
                gravity = Gravity.BOTTOM
                addView(number)
                addView(text(getString(R.string.streak_days), 24f, Ui.bold), lp(WRAP, WRAP).apply { marginStart = dp(8); bottomMargin = dp(6) })
            }, lp(WRAP, WRAP).apply { topMargin = dp(4) })
            addView(monthLine(month), lp(WRAP, WRAP).apply { topMargin = dp(6) })
        }
        // 키운 글자로 칸을 넘으면 큰 숫자를 기준 크기로 줄여 이번 달 목표 달성 줄이 들게 한다(홈 큰 제목처럼)
        frameH?.let { h ->
            val columnW = dp(SUMMARY_DP) - dp(RING) - dp(10)
            if (column.heightAt(columnW) > h - paddingTop - paddingBottom) number.setTextSize(TypedValue.COMPLEX_UNIT_DIP, STREAK_WIDE_SP)
        }
        // WRAP 이라야 기준 크기로 잴 때 글자 칸 높이가 든다(높이가 WRAP 인 가로 줄은 MATCH 자식을 높이 계산에서 뺀다). 칸 높이가 정해지면 그 안으로 줄어든다.
        addView(column, lp(0, WRAP, 1f))
        addView(todayRing(days, today, goalMs, todayFrac), lp(dp(RING), dp(RING)).apply { marginStart = dp(10) })
    }

    /** 이번 달 목표 달성 한 줄(`10월 목표 달성` 회색 + `11 / 15일` 굵게, [month]: 제목 to 값) */
    private fun monthLine(month: Pair<String, String>) = hbox().apply {
        addView(text(month.first, 15f, Ui.medium, Ui.GRAY))
        addView(text(month.second, 15f, Ui.bold), lp(WRAP, WRAP).apply { marginStart = dp(6) })
    }

    /** 오늘 링(가운데 오늘 읽은 시간과 `/ 30분`) */
    private fun todayRing(days: ReadingDays, today: LocalDate, goalMs: Long, todayFrac: Float): View {
        val time = text(duration(days.total(today)), 17f, Ui.bold)
        val goal = if (goalMs > 0) text(getString(R.string.goal_of, duration(goalMs)), 11f, Ui.medium, Ui.GRAY) else null
        fitInRing(time, goal)
        return FrameLayout(this).apply {
            addView(RingView(context, RING_STROKE).apply { frac = todayFrac }, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(vbox().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                addView(time, lp(WRAP, WRAP))
                goal?.let { addView(it, lp(WRAP, WRAP)) }
            }, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        }
    }

    /** 오늘 링 안쪽 동그라미에 들어가게 [time] 글자를 줄인다(`1시간 34분`처럼 길면 링에 겹친다). */
    private fun fitInRing(time: TextView, goal: TextView?) {
        fun TextView.lineH() = paint.fontMetrics.let { it.descent - it.ascent }
        val r = dpf(RING / 2f - RING_STROKE)
        var sp = 17f
        // 링 크기는 고정이라 글자 배율과 상관없이 실제 12sp 까지 줄인다.
        while (sp > 12f / Ui.textScale) {
            val half = (time.lineH() + (goal?.lineH() ?: 0f)) / 2
            val room = 2 * sqrt(r * r - half * half) - dpf(6)
            if (time.paint.measureText(time.text.toString()) <= room) break
            sp -= 0.5f
            time.setSp(sp)
        }
    }

    /** 이번 주의 첫날(설정의 한 주 시작 요일). 달력·이번 주 합계·이번 주 책별이 모두 따른다. */
    private fun startOfWeek(today: LocalDate): LocalDate = today.with(TemporalAdjusters.previousOrSame(prefs.firstDayOfWeek))

    /**
     * 4주 링 달력 또는 막대그래프(처음에는 이번 주까지 최근 4주). 제목 줄 왼쪽 `달력 · 그래프`로 바꾸고 기억한다(같은 높이, 이 칸만 다시 그린다).
     * 둘 다 들어갈 높이가 있으면([both]) 달력 아래에 그래프를 함께 두고 `달력 · 그래프` 는 뺀다.
     * 달력: 날짜는 링 위에 작게, 오늘은 검은 동그라미 안에. 기록 전([since] 전)·앞날은 흐린 빈 링.
     * 제목 줄 오른쪽 `9월 14일 – 10월 11일  ‹ ›` 로 두 보기를 함께 4주씩 넘긴다. 기록 시작 전으로·이번 주 뒤로는 가지 않는다(흐리게).
     * [fill] 이면(가로 화면) 남는 높이를 채우고, 함께 둘 때는 달력과 그래프를 나란히.
     */
    private fun calendar(days: ReadingDays, today: LocalDate, since: LocalDate, goalMs: Long, progress: (LocalDate) -> Float, fill: Boolean = false) = vbox().apply {
        val locale = resources.configuration.locales[0]
        val latest = startOfWeek(today).minusWeeks(WEEKS - 1L)
        val dateFmt = DateTimeFormatter.ofPattern(getString(R.string.date_short_fmt), locale)
        val yearFmt = DateTimeFormatter.ofPattern(getString(R.string.date_year_fmt), locale)
        // 요일 줄과 4주치 날짜·링을 뷰 하나에 그린다(칸마다 뷰를 만들면 백 개가 넘어 느리다).
        // 가로(fill)는 틀을 글자 · 간격과 상관없이 두어 요일·날짜 칸을 글자 배율만큼 넓히지 않는다
        val grow = if (fill) 1f else Ui.textGrow
        val cal = RingCalendar(context, latest, today, since, WEEKS, progress, grow)
        // 그래프는 달력 자리에 겹쳐 두고 달력 높이를 그대로 쓴다(달력은 숨길 때 INVISIBLE 로 자리를 지킨다).
        val graph = ReadingGraph(context, latest, today, since, WEEKS, goalMs, { days.total(it) }, { progress(it) >= 1f }, grow)
        val toggle = text("", 13f, Ui.bold)
        fun showView() {
            val graphOn = prefs.readingGraph
            toggle.text = SpannableStringBuilder().apply {
                listOf(R.string.view_calendar to !graphOn, R.string.view_graph to graphOn).forEachIndexed { i, (res, on) ->
                    if (i > 0) append("  ·  ", ForegroundColorSpan(Ui.LIGHT_GRAY), 0)
                    append(getString(res), ForegroundColorSpan(if (on) Ui.BLACK else Ui.LIGHT_GRAY), 0)
                }
            }
            cal.visibility = if (graphOn) View.INVISIBLE else View.VISIBLE
            graph.visibility = if (graphOn) View.VISIBLE else View.GONE
        }
        val together = both == true
        if (!together) {
            toggle.setOnClickListener { prefs.readingGraph = !prefs.readingGraph; showView() }
            showView()
        }
        val range = text("", 12f, Ui.medium, Ui.LIGHT_GRAY)
        fun arrow(symbol: String) = text(symbol, 20f, Ui.bold).apply { gravity = Gravity.CENTER; minWidth = dp(40) }
        val prev = arrow("‹")
        val next = arrow("›")
        // 가로에서 글자를 키워(또는 지난해의 연도가 붙어) 범위가 제목 줄에 다 들지 않으면 짧게(`9/21 – 10/18`). 달력·그래프 글자와 `‹ ›` 를 먼저 둔다.
        val room = if (!fill) Float.MAX_VALUE else (resources.displayMetrics.widthPixels - summaryWidth() - dp(1) - dp(2 * Ui.MARGIN - 12) - dp(4) -
            2 * maxOf(dp(40), prev.paint.measureText("‹").toInt())).toFloat() - (if (together) 0f else toggle.paint.measureText(toggle.text.toString()))
        val shortFmt = DateTimeFormatter.ofPattern("M/d", locale)
        val shortYearFmt = DateTimeFormatter.ofPattern("yyyy/M/d", locale)
        fun show(start: LocalDate) {
            cal.start = start
            graph.start = start
            val end = start.plusDays(WEEKS * 7L - 1)
            // 지난해까지 넘기면 연도를 붙인다.
            fun label(day: DateTimeFormatter, yearDay: DateTimeFormatter) =
                "${start.format(if (start.year != today.year) yearDay else day)} – ${end.format(if (end.year != today.year) yearDay else day)}"
            val full = label(dateFmt, yearFmt)
            range.text = if (range.paint.measureText(full) > room) label(shortFmt, shortYearFmt) else full
            val canPrev = start > since
            val canNext = start < latest
            prev.setTextColor(if (canPrev) Ui.BLACK else Ui.DIVIDER)
            next.setTextColor(if (canNext) Ui.BLACK else Ui.DIVIDER)
            prev.setOnClickListener { if (canPrev) show(start.minusWeeks(WEEKS.toLong())) }
            next.setOnClickListener { if (canNext) show(start.plusWeeks(WEEKS.toLong())) }
        }
        show(latest)
        // 가로는 제목 줄 높이도 기준 크기 글자로 고정한다(키운 글자에 달력이 밀려 내려가지 않게)
        val headerH = if (fill) Ui.atBaseText { arrow("‹").run { measure(0, 0); measuredHeight } } + dp(6) else WRAP
        addView(hbox().apply {
            setPadding(dp(Ui.MARGIN), dp(4), dp(Ui.MARGIN - 12), dp(2))
            // 하루 목표는 오늘 링(`/ 30분`)과 그래프의 목표선에 보인다.
            addView(toggle, lp(0, WRAP, 1f))
            // 범위 글자 길이가 바뀌어도 이어 누를 수 있게 `‹ ›` 는 오른쪽 끝에 나란히 고정한다.
            addView(range, lp(WRAP, WRAP).apply { marginEnd = dp(4) })
            addView(prev, lp(WRAP, WRAP))
            addView(next, lp(WRAP, WRAP))
        }, lp(MATCH, headerH))
        addView(hline(1))
        if (fill) {
            // 가로: 남는 높이를 채운다(달력은 주 사이를 넓히고, 그래프는 막대를 높인다)
            if (together) addView(hbox().apply {
                addView(cal, lp(0, MATCH, 1f))
                addView(graph, lp(0, MATCH, 1f))
            }, lp(MATCH, 0, 1f))
            else addView(FrameLayout(context).apply {
                addView(cal, FrameLayout.LayoutParams(MATCH, MATCH))
                addView(graph, FrameLayout.LayoutParams(MATCH, MATCH))
            }, lp(MATCH, 0, 1f))
            addView(View(context), lp(MATCH, dp(16)))
            return@apply
        }
        if (together) {
            addView(cal, lp(MATCH, WRAP))
            addView(graph, lp(MATCH, WRAP).apply { topMargin = dp(GRAPH_GAP_DP) })
        } else addView(FrameLayout(context).apply {
            addView(cal, FrameLayout.LayoutParams(MATCH, WRAP))
            addView(graph, FrameLayout.LayoutParams(MATCH, MATCH))
        }, lp(MATCH, WRAP))
        addView(View(context), lp(MATCH, dp(24)))
    }

    /** 이번 달 목표 달성(그날의 목표로, [progress] 가 꽉 찬 날, 기록이 있는 날 중). 지금 목표가 없으면 제목대로 읽은 날을 센다. */
    private fun monthGoal(days: ReadingDays, today: LocalDate, since: LocalDate, goalMs: Long, progress: (LocalDate) -> Float): Pair<String, String> {
        val monthDays = (1..today.dayOfMonth).map { today.withDayOfMonth(it) }.filter { it >= since }
        val hits = monthDays.count { if (goalMs > 0) progress(it) >= 1f else days.total(it) >= ReadingLog.MIN_DAY_MS }
        val month = today.format(DateTimeFormatter.ofPattern(getString(R.string.month_fmt), resources.configuration.locales[0]))
        return getString(if (goalMs > 0) R.string.month_goal else R.string.month_read, month) to getString(R.string.days_of, hits, monthDays.size)
    }

    /** 이번 주 · 이번 달 · 올해 읽은 시간(이름 to 시간) */
    private fun sums(days: ReadingDays, today: LocalDate): List<Pair<String, String>> {
        fun sum(from: LocalDate) = days.filterKeys { it in from..today }.keys.sumOf { days.total(it) }
        return listOf(
            getString(R.string.this_week) to duration(sum(startOfWeek(today))),
            getString(R.string.this_month) to duration(sum(today.withDayOfMonth(1))),
            getString(R.string.this_year) to duration(sum(today.withDayOfYear(1))),
        )
    }

    /** 왼쪽 칸: 이번 주 · 이번 달 · 올해 읽은 시간(위아래로) */
    private fun totals(days: ReadingDays, today: LocalDate) = vbox().apply {
        setPadding(0, dp(6), 0, 0)
        sums(days, today).forEachIndexed { i, (label, value) ->
            addView(text(label, 13f, Ui.medium, Ui.GRAY), lp(WRAP, WRAP).apply { if (i > 0) topMargin = dp(14) })
            addView(text(value, 20f, Ui.bold), lp(WRAP, WRAP).apply { topMargin = dp(4) })
        }
    }

    /** 바형·좁은 화면: 이번 주 · 이번 달 · 올해 읽은 시간을 한 줄에 세 칸으로(칸마다 이름 아래 시간) */
    private fun totalsRow(days: ReadingDays, today: LocalDate) = hbox().apply {
        gravity = Gravity.TOP
        setPadding(0, dp(6), 0, 0)
        sums(days, today).forEach { (label, value) ->
            addView(vbox().apply {
                addView(text(label, 13f, Ui.medium, Ui.GRAY))
                addView(text(value, 20f, Ui.bold), lp(WRAP, WRAP).apply { topMargin = dp(4) })
            }, lp(0, WRAP, 1f))
        }
    }

    /** 가로 화면 왼쪽 칸: 이번 주 · 이번 달 · 올해 읽은 시간을 한 줄씩(이름 왼쪽, 시간 오른쪽. 줄 높이는 글자 · 간격과 상관없이 같다) */
    private fun totalsCompact(days: ReadingDays, today: LocalDate) = vbox().apply {
        sums(days, today).forEach { (label, value) ->
            addView(hbox().apply {
                addView(text(label, 14f, Ui.medium, Ui.GRAY), lp(0, WRAP, 1f))
                addView(text(value, 19f, Ui.bold), lp(WRAP, WRAP).apply { marginStart = dp(12) })
            }, lp(MATCH, dp(TOTAL_ROW_DP)))
        }
    }

    /**
     * 오른쪽 칸: 이번 주 책별 시간(많은 순, 한 쪽에 [BOOKS]권. [fill] 이면(가로 화면·좁은 화면) 남는 높이에 들어가는 만큼). 한 줄에 책 이름(그 책의 지금 제목, 목록에 없던 책은 편 책 제목) · 시간, 줄 사이에 옅은 선.
     * 책은 읽은 시간 칸에 정해 둔 책 id 로 모은다(같은 제목의 두 책은 두 줄). 어떤 책인지 모르는 시간(이 기능 전, 그 앱에서 편 책을 한 번도
     * 몰랐을 때)은 합계에만 들어가고 여기에는 보이지 않는다.
     */
    private fun byBook(days: ReadingDays, today: LocalDate, page: TextView, fill: Boolean = false): View {
        bookRows = null
        val week = days.filterKeys { it in startOfWeek(today)..today }.values
        val past = BookShelf.history(this)
        val known = BookShelf.list(this).associateBy { it.id } + past.associate { it.book.id to it.book }
        // 기록에서 지운 책은 올해 읽은 책·결산처럼 보이지 않는다(그 시간은 합계에만)
        val gone = past.filter { it.hidden }.map { it.book.id }.toSet()
        val perBook = linkedMapOf<String, Pair<String, Long>>()
        week.forEach { keys ->
            keys.forEach { (k, ms) ->
                if (k.book in gone) return@forEach
                val book = known[k.book]
                if (book == null && k.title.isEmpty()) return@forEach
                val id = book?.id ?: BookRecord.titleOnlyId(k.title)
                val (label, sum) = perBook[id] ?: ((book?.title ?: k.title) to 0L)
                perBook[id] = label to sum + ms
            }
        }
        // 이번 주 합쳐 [ReadingLog.MIN_DAY_MS](5분) 이상 읽은 책만(잠깐 펴 본 책은 올해 읽은 책처럼 뺀다)
        val books = perBook.values.filter { it.second >= ReadingLog.MIN_DAY_MS }.sortedByDescending { it.second }
        if (books.isEmpty()) {
            // 보일 책이 없으면(읽지 않았거나, 잠깐씩만 폈거나, 어떤 책인지 모르는 시간뿐) 한 문구로
            return text(getString(R.string.reading_empty), 15f, color = Ui.LIGHT_GRAY, lines = 3).apply {
                setPadding(0, dp(8), 0, 0)
            }
        }
        // 자주 쓰는 앱처럼 [BOOKS]줄씩, 넘치면 제목 오른쪽 `‹ 1 / 2 ›`(누르거나 밀면 다음 쪽). 덜 찬 쪽도 높이는 같다.
        // 가로 화면은 홈처럼 줄 높이를 글자 · 간격과 상관없이 둔다. [fill] 이면 남는 높이에 들어가는 만큼
        val rowH = if (wide) dp(BOOK_ROW) else rowDp(BOOK_ROW * Ui.textGrow)
        val rows = if (fill) PagedRows(this, rowH, insetDividers = false) else PagedRows(this, rowH, fixedRows = BOOKS, insetDividers = false)
        rows.onPageChanged = { page.text = pageLabel(rows) }
        page.pagesClick(rows)
        rows.setRows(books.map { (label, ms) -> {
            hbox().apply {
                addView(text(label, 16f), lp(0, WRAP, 1f))
                addView(text(duration(ms), 15f, Ui.medium), lp(WRAP, WRAP).apply { marginStart = dp(12) })
            }
        } })
        bookRows = rows
        return rows
    }

    override fun onSwipe(toLeft: Boolean): Boolean {
        val rows = bookRows?.takeIf { it.pageCount > 1 } ?: return false
        if (toLeft) rows.next() else rows.prev()
        return true
    }

    companion object {
        private const val WEEKS = 4
        /** 달력과 그래프를 함께 둘 때 사이(그래프 위 시간 글자가 달력에 붙지 않게) */
        private const val GRAPH_GAP_DP = 28
        /** 연속일 줄과 달력 사이 */
        private const val HERO_GAP_DP = 16
        /** 컬러: 연속일 칸 색면이 아래로 더 내려오는 높이(HERO_GAP_DP 안) */
        private const val FIELD_BOTTOM_DP = 12
        /** 아래 왼쪽 합계 칸 폭(dp) */
        private const val TOTALS_W = 150
        /** 왼쪽 합계 칸과 오른쪽 책 칸 사이(dp) */
        private const val BOOKS_GAP = 24
        /** 오른쪽 책 칸이 이보다 좁아지면(dp) 합계를 한 줄로, 책을 그 아래 전체 폭으로 둔다 */
        private const val BOOKS_MIN_DP = 200
        /** 바형·좁은 화면: 합계 줄과 이번 주 읽은 책 사이(dp) */
        private const val STACK_GAP_DP = 20
        /** 이번 주 읽은 책 한 쪽의 줄 수와 줄 높이(dp) */
        private const val BOOKS = 5
        private const val BOOK_ROW = 36
        /** 오늘 링 지름·굵기(dp) */
        private const val RING = 104
        private const val RING_STROKE = 12f
        /** 가로 화면: 왼쪽 요약 칸 폭(여백 안, dp), 읽은 시간 한 줄 높이, 달력 하나의 최소 폭(여백 안. 오른쪽이 두 개만큼 넓으면 그래프와 나란히) */
        private const val SUMMARY_DP = 290
        private const val TOTAL_ROW_DP = 36
        /** 가로 화면 큰 연속일 글자(sp, 칸을 넘으면 이 크기 dp 로 줄인다) */
        private const val STREAK_WIDE_SP = 48f
        private const val CAL_MIN_DP = 280

        fun intent(context: Context) = Intent(context, ReadingActivity::class.java)
    }
}

/** 둥근 끝 진행 링(애플 활동 링처럼) 그리기. 바탕 링 위에 12시부터 시계 방향으로 [frac](0~1)만큼(검정, 컬러면 [Palette.goal]). */
private class RingPainter(stroke: Float) {
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = stroke; color = Palette.current.goal; strokeCap = Paint.Cap.ROUND
    }
    /** 선 굵기의 절반: 링이 칸 안에 들어오게 이만큼 안쪽에 그린다. */
    val inset = stroke / 2

    fun draw(canvas: Canvas, oval: RectF, frac: Float, track: Int) {
        trackPaint.color = track
        canvas.drawOval(oval, trackPaint)
        if (frac >= 1f) canvas.drawOval(oval, arcPaint)
        else if (frac > 0f) canvas.drawArc(oval, -90f, 360f * frac, false, arcPaint)
    }
}

/** 링 바탕(크레마는 연한 회색을 어둡게 그려서 디자인 값보다 밝게 잡는다) */
private const val RING_TRACK = 0xFFF2F2F2.toInt()
/** 목표를 못 채운 날의 막대(크레마는 연한 회색을 어둡게 그려 #888 은 검정과 잘 구분되지 않는다) */
private const val UNMET_BAR = 0xFFBBBBBB.toInt()
/** 앞날·기록 전 날의 링 바탕 */
private const val NO_DATA_TRACK = 0xFFF9F9F9.toInt()

/** 링 하나(오늘 링) */
class RingView(context: Context, strokeDp: Float) : View(context) {
    var frac = 0f
    private val painter = RingPainter(context.dpf(strokeDp))
    private val oval = RectF()

    override fun onDraw(canvas: Canvas) {
        oval.set(painter.inset, painter.inset, width - painter.inset, height - painter.inset)
        painter.draw(canvas, oval, frac, RING_TRACK)
    }
}

/**
 * 요일 줄 + [weeks]주(월~일) 링 달력. 날짜는 링 위에 작게, 오늘은 검은 동그라미 안 흰 글자.
 * 앞날과 기록 시작일([since]) 전은 흐린 날짜·아주 연한 빈 링.
 */
private class RingCalendar(
    context: Context,
    start: LocalDate,
    private val today: LocalDate,
    private val since: LocalDate,
    private val weeks: Int,
    private val progress: (LocalDate) -> Float,
    /** 요일·날짜 칸을 넓히는 배율(보통 [Ui.textGrow], 가로 화면은 1: 틀을 글자와 상관없이) */
    grow: Float = Ui.textGrow,
) : View(context) {
    /** 첫 주의 첫날. 바꾸면(달력 넘기기) 다시 그린다. 늘 같은 요일(한 주 시작 요일)이다. */
    var start: LocalDate = start
        set(v) { field = v; invalidate() }
    /** [start] 의 요일부터 일주일 */
    private val weekdays = (0L..6L).map { start.dayOfWeek.plus(it).getDisplayName(TextStyle.NARROW, context.resources.configuration.locales[0]) }
    private val margin = context.dpf(Ui.MARGIN)
    private val weekdayTop = context.dpf(8)
    private val weekdayH = context.dpf(CAL_WEEKDAY_DP * grow)
    private val rowGap = context.dpf(CAL_ROW_GAP_DP)
    private val labelH = context.dpf(CAL_LABEL_DP * grow)
    private val pillHalf = context.dpf(10 * grow)
    private val ringGap = context.dpf(CAL_RING_GAP_DP)
    private val ring = context.dpf(CAL_RING_DP)
    private val rowH = rowGap + labelH + ringGap + ring
    private val painter = RingPainter(context.dpf(6))
    private val oval = RectF()
    private val weekdayPaint = context.calPaint(12f, Ui.medium, Ui.LIGHT_GRAY)
    private val datePaint = context.calPaint(11f, Ui.medium, Ui.GRAY)
    private val todayPaint = context.calPaint(11f, Ui.bold, Color.WHITE)
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.BLACK }

    /** 높이가 정해져 오면(가로 화면에서 남는 높이를 채울 때) 그 높이, 아니면 [ringCalendarHeight] */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), calendarHeight(context, weeks, heightMeasureSpec))
    }

    /** [cy] 에 세로 가운데로 */
    private fun Canvas.centerText(s: String, cx: Float, cy: Float, p: Paint) =
        drawText(s, cx, cy - (p.ascent() + p.descent()) / 2, p)

    override fun onDraw(canvas: Canvas) {
        val cw = (width - 2 * margin) / 7
        weekdays.forEachIndexed { i, d -> canvas.centerText(d, margin + cw * (i + 0.5f), weekdayTop + (weekdayH - weekdayTop) / 2, weekdayPaint) }
        // 높이가 남으면(가로 화면) 주 사이를 넓힌다: 주마다 남는 높이의 반을 위에 둔다
        val slotH = maxOf(rowH, (height - weekdayH) / weeks)
        for (w in 0 until weeks) for (i in 0 until 7) {
            val day = start.plusDays(w * 7L + i)
            val cx = margin + cw * (i + 0.5f)
            val top = weekdayH + w * slotH + rowGap + (slotH - rowH) / 2
            val label = if (day.dayOfMonth == 1) "${day.monthValue}/1" else day.dayOfMonth.toString()
            val noData = day > today || day < since
            if (day == today) {
                canvas.drawOval(cx - pillHalf, top, cx + pillHalf, top + labelH, pillPaint)
                canvas.centerText(label, cx, top + labelH / 2, todayPaint)
            } else {
                datePaint.color = if (noData) Ui.DIVIDER else Ui.GRAY
                canvas.centerText(label, cx, top + labelH / 2, datePaint)
            }
            val ringTop = top + labelH + ringGap
            oval.set(cx - ring / 2 + painter.inset, ringTop + painter.inset, cx + ring / 2 - painter.inset, ringTop + ring - painter.inset)
            painter.draw(canvas, oval, if (noData) 0f else progress(day), if (noData) NO_DATA_TRACK else RING_TRACK)
        }
    }
}

/** 링 달력 치수(dp): 요일 줄, 주마다 위 여백 · 날짜 · 날짜와 링 사이 · 링 */
private const val CAL_WEEKDAY_DP = 22
private const val CAL_ROW_GAP_DP = 4
private const val CAL_LABEL_DP = 17
private const val CAL_RING_GAP_DP = 2
private const val CAL_RING_DP = 28

/** [heightMeasureSpec] 이 정해진 높이면 그 높이(가로 화면에서 채울 때), 아니면 [ringCalendarHeight] */
private fun calendarHeight(context: Context, weeks: Int, heightMeasureSpec: Int): Int =
    if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.EXACTLY) View.MeasureSpec.getSize(heightMeasureSpec)
    else ringCalendarHeight(context, weeks)

/** 링 달력 높이. 같은 자리의 그래프도 이 높이를 쓴다. */
private fun ringCalendarHeight(context: Context, weeks: Int): Int {
    val s = Ui.textGrow
    return context.dpf(CAL_WEEKDAY_DP * s + weeks * (CAL_ROW_GAP_DP + CAL_LABEL_DP * s + CAL_RING_GAP_DP + CAL_RING_DP)).toInt()
}

/** 달력·그래프 글자 붓 */
private fun Context.calPaint(sp: Float, face: android.graphics.Typeface, color: Int, align: Paint.Align = Paint.Align.CENTER) =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = align
        textSize = spf(sp)
        typeface = face
        this.color = color
    }

/**
 * [weeks]주를 날마다 막대 하나로(독서 기록의 `그래프`). 높이는 같은 자리의 링 달력과 같다.
 * 하루 목표([goalMs])에 점선과 `목표 30분`, 목표를 채운 날([met])은 검정(컬러면 [Palette.goal])·못 채운 날은 회색 막대.
 * 가장 많이 읽은 날과 오늘은 막대 위에 시간, 아래에는 주마다 첫날 `10/5`, 오늘은 검은 동그라미 안 날짜. 기록 전 날짜는 흐리게.
 */
private class ReadingGraph(
    context: Context,
    start: LocalDate,
    private val today: LocalDate,
    private val since: LocalDate,
    private val weeks: Int,
    private val goalMs: Long,
    private val ms: (LocalDate) -> Long,
    private val met: (LocalDate) -> Boolean,
    /** 막대 위아래 글자 자리를 넓히는 배율(보통 [Ui.textGrow], 가로 화면은 1) */
    grow: Float = Ui.textGrow,
) : View(context) {
    /** 첫날. 바꾸면(넘기기) 다시 그린다. */
    var start: LocalDate = start
        set(v) { field = v; invalidate() }
    private val margin = context.dpf(Ui.MARGIN)
    /** 위(막대 위 시간)·아래(날짜) 글자 자리 */
    private val topRoom = context.dpf(34 * grow)
    private val bottomRoom = context.dpf(28 * grow)
    private val valuePaint = context.calPaint(12f, Ui.bold, Ui.BLACK)
    private val datePaint = context.calPaint(11f, Ui.medium, Ui.GRAY)
    private val todayPaint = context.calPaint(11f, Ui.bold, Color.WHITE)
    private val goalLabelPaint = context.calPaint(11f, Ui.medium, Ui.GRAY, Paint.Align.LEFT)
    private val barPaint = Paint()
    private val metColor = Palette.current.goal
    private val basePaint = Paint().apply { color = Ui.DIVIDER; strokeWidth = context.dpf(1) }
    private val goalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.GRAY; strokeWidth = context.dpf(1); style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(context.dpf(4), context.dpf(4)), 0f)
    }
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.BLACK }
    private val pillHalf = context.dpf(10 * grow)
    private val pillH = context.dpf(17 * grow)
    private val minBar = context.dpf(2)
    private val goalLine = Path()
    /** `목표 30분` 뒤의 흰 바탕 */
    private val labelBg = Paint().apply { color = Color.WHITE }

    private fun Canvas.centerText(s: String, cx: Float, cy: Float, p: Paint) = drawText(s, cx, cy - (p.ascent() + p.descent()) / 2, p)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), calendarHeight(context, weeks, heightMeasureSpec))
    }

    /** 주 첫날 날짜 글자(`10/12`)가 바로 옆 오늘 표시에 겹치면 날짜를 뺀다(오늘이 주 둘째 날·앞 주 마지막 날일 때). */
    private fun nearToday(day: LocalDate, slot: Float): Boolean {
        val gap = abs(ChronoUnit.DAYS.between(day, today)) * slot
        return gap > 0 && gap < datePaint.measureText("${day.monthValue}/${day.dayOfMonth}") / 2 + pillHalf + context.dpf(2)
    }

    override fun onDraw(canvas: Canvas) {
        val n = weeks * 7
        val days = (0 until n).map { start.plusDays(it.toLong()) }
        // 기록이 있는 날(기록 시작일~오늘)만 센다.
        val values = days.map { if (it in since..today) ms(it) else 0L }
        val bottom = height - bottomRoom
        val top = topRoom
        // 막대 높이 기준: 가장 많이 읽은 날, 목표의 1.5배, 1분 중 큰 값
        val peak = maxOf(values.maxOrNull() ?: 0L, goalMs * 3 / 2, ReadingLog.MIN_DAY_MS).toFloat()
        fun y(v: Long) = bottom - (bottom - top) * v / peak
        val slot = (width - 2 * margin) / n
        val barW = slot * 0.6f
        canvas.drawLine(margin, bottom, width - margin, bottom, basePaint)
        val gy = y(goalMs)
        if (goalMs > 0) {
            goalLine.reset(); goalLine.moveTo(margin, gy); goalLine.lineTo(width - margin, gy)
            canvas.drawPath(goalLine, goalPaint)
        }
        val best = values.indices.maxByOrNull { values[it] }?.takeIf { values[it] >= ReadingLog.MIN_DAY_MS }?.let { days[it] }
        days.forEachIndexed { i, day ->
            val cx = margin + slot * (i + 0.5f)
            val v = values[i]
            if (v > 0) {
                barPaint.color = if (met(day)) metColor else UNMET_BAR
                canvas.drawRect(cx - barW / 2, minOf(y(v), bottom - minBar), cx + barW / 2, bottom, barPaint)
            }
            val labelY = bottom + context.dpf(4) + pillH / 2
            if (day == today) {
                canvas.drawOval(cx - pillHalf, labelY - pillH / 2, cx + pillHalf, labelY + pillH / 2, pillPaint)
                canvas.centerText(day.dayOfMonth.toString(), cx, labelY, todayPaint)
            } else if (i % 7 == 0 && !nearToday(day, slot)) {
                datePaint.color = if (day < since || day > today) Ui.DIVIDER else Ui.GRAY
                canvas.centerText("${day.monthValue}/${day.dayOfMonth}", cx, labelY, datePaint)
            }
        }
        // 오늘과 가장 많이 읽은 날은 막대 위에 시간. 옆 막대에 가리지 않게 막대를 다 그린 뒤에, 화면 밖으로 나가지 않게 안쪽으로.
        // 두 글자가 겹치면(가까운 날) 오늘 것만.
        val drawn = mutableListOf<RectF>()
        listOfNotNull(today, best).distinct().forEach { day ->
            val i = days.indexOf(day).takeIf { it >= 0 } ?: return@forEach
            val v = values[i]
            if (v < ReadingLog.MIN_DAY_MS) return@forEach
            val label = context.duration(v)
            val half = valuePaint.measureText(label) / 2
            val cx = (margin + slot * (i + 0.5f)).coerceIn(margin + half, width - margin - half)
            val base = y(v) - context.dpf(5)
            val box = RectF(cx - half, base + valuePaint.ascent(), cx + half, base + valuePaint.descent())
            if (drawn.any { RectF.intersects(it, box) }) return@forEach
            drawn += box
            canvas.drawText(label, cx, base, valuePaint)
        }
        // 목표 이름은 막대 뒤에 가리지 않게 마지막에 흰 바탕 위에 쓴다.
        if (goalMs > 0) {
            val label = context.getString(R.string.goal_line, context.duration(goalMs))
            val pad = context.dpf(3)
            val base = gy - context.dpf(4)
            canvas.drawRect(margin, base + goalLabelPaint.ascent() - pad, margin + goalLabelPaint.measureText(label) + 2 * pad, base + goalLabelPaint.descent(), labelBg)
            canvas.drawText(label, margin + pad, base, goalLabelPaint)
        }
    }
}
