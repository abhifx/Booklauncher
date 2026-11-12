package com.woody.booklauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * 달력 홈(설정 › 화면 › 홈 화면 › 달력): 부드러운 카드 세 장(지금 읽는 책 · 이번 달 달력 · 자주 쓰는 앱)과 하단 줄.
 * 옅은 회색 바탕 위에 흰 둥근 카드를 얹는다([Ui.CANVAS] · [Ui.softCard]).
 * 달력은 늘 같은 높이로 두어 달을 넘겨도 앱 자리가 움직이지 않고, 앱은 남는 높이만큼 쪽으로 넘긴다.
 * 달력의 날짜를 누르면 독서 기록을 연다(달력 홈에서는 통계 단추를 두지 않는다).
 */
class HomeCalendarView(
    context: Context,
    private val firstDayOfWeek: DayOfWeek,
    private val onOpen: (AppEntry) -> Unit,
    private val onLongPress: (AppEntry) -> Unit,
    private val onOpenApps: () -> Unit,
    private val onOpenMini: () -> Unit,
    private val onOpenGames: () -> Unit,
    private val onOpenSettings: () -> Unit,
    private val onOpenStats: () -> Unit,
) : LinearLayout(context) {
    private var month = YearMonth.now()
    /** 맨 위 지금 읽는 책 카드 자리([setHero]). 책이 없으면 비워 둔다. */
    private val heroSlot: FrameLayout
    private val monthTitle: TextView
    private val streakText: TextView
    private val dayGrid: LinearLayout
    private val calendarCard: View
    private lateinit var favGrid: PagedRows
    private val favPage: TextView
    private val favCard: View
    private var shownFavs: List<Any?>? = null
    /** 달력에 표시할 읽은 시간(날짜별)과 하루 목표. 독서 기록·기본 달력 위젯과 같은 자료를 쓴다. */
    private var readingDays: ReadingDays? = null
    private var goalMs = 0L

    /** 타일 열 수: 6인치는 다섯 칸, 10인치 태블릿은 일곱 칸. */
    private val columns get() = if (Ui.tablet) 7 else 5

    /** 가로 화면: 오른쪽 칸이 낮아 자주 쓰는 앱 카드를 낮게 그린다(제목 줄·줄 높이·안쪽 여백을 줄인다). */
    private val compact get() = context.landscape()

    // 이 클래스는 View 라서 규격 도우미(확장 함수)를 [context] 로 넘겨 부른다.
    private fun dp(v: Number) = context.dp(v)
    private fun hbox() = context.hbox()
    private fun vbox() = context.vbox()
    private fun hline(heightDp: Number, color: Int = Ui.BLACK, inset: Boolean = true, marginDp: Int = Ui.MARGIN) =
        context.hline(heightDp, color, inset, marginDp)
    private fun vline(insetDp: Int) = context.vline(insetDp)
    private fun text(value: CharSequence, sp: Float, face: Typeface = Ui.regular, color: Int = Ui.BLACK, lines: Int = 1) =
        context.text(value, sp, face, color, lines)
    private fun picture(bitmap: Bitmap?) = context.picture(bitmap)
    private fun getString(id: Int, vararg args: Any) = context.getString(id, *args)
    private fun getDrawable(id: Int) = context.getDrawable(id)
    private fun cardLp(weight: Float = 0f, topDp: Int = 0, bottomDp: Int = 0) =
        context.cardLp(weight, Ui.CARD_GAP_DP, topDp, bottomDp)
    private fun softCard(padDp: Int = Ui.CARD_PAD_DP) = context.softCard(padDp)

    init {
        orientation = VERTICAL
        setPadding(0, dp(2), 0, 0)

        // 지금 읽는 책(hero). 책이 없으면 자리를 비운다.
        heroSlot = FrameLayout(context)

        // 이번 달 달력 카드
        val head = hbox().apply { gravity = Gravity.CENTER_VERTICAL }
        monthTitle = text("", if (Ui.tablet) 28f else 23f, Ui.heavy, Palette.current.title(Palette.Area.HOME)).apply {
            setPadding(dp(8), 0, dp(8), 0)
        }
        head.addView(arrow("‹") { showMonth(month.minusMonths(1)) }, lp(dp(30), dp(40)))
        head.addView(monthTitle, lp(0, dp(40), 1f))
        head.addView(arrow("›") { showMonth(month.plusMonths(1)) }, lp(dp(30), dp(40)))
        streakText = text("", if (Ui.tablet) 14f else 12.5f, color = Ui.GRAY).apply { gravity = Gravity.END }
        head.addView(streakText, lp(WRAP, dp(40)).apply { marginStart = dp(8) })

        // 요일 줄. 한 주 시작은 독서 기록과 같은 설정을 따른다.
        val weekdays = hbox().apply {
            setPadding(dp(2), dp(2), dp(2), dp(2))
            repeat(7) { i ->
                val day = DayOfWeek.of((firstDayOfWeek.value - 1 + i) % 7 + 1)
                addView(
                    text(day.getDisplayName(TextStyle.SHORT, locale), if (Ui.tablet) 12.5f else 11f, Ui.medium, Ui.GRAY)
                        .apply { gravity = Gravity.CENTER },
                    lp(0, dp(22), 1f),
                )
            }
        }

        dayGrid = vbox()

        calendarCard = softCard().apply {
            addView(head, lp(MATCH, WRAP))
            addView(weekdays, lp(MATCH, WRAP))
            addView(dayGrid, lp(MATCH, WRAP))
        }

        // 자주 쓰는 앱 카드: 제목과 쪽 표시, 그 아래 타일 격자.
        val favHead = hbox().apply { gravity = Gravity.CENTER_VERTICAL }
        favHead.addView(
            text(getString(R.string.favorites), if (Ui.tablet) 18f else 16f, Ui.heavy),
            lp(0, dp(if (compact) FAV_HEAD_SHORT_DP else 32), 1f),
        )
        favPage = text("", if (Ui.tablet) 14f else 12.5f, Ui.medium, Ui.GRAY).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
        }
        favHead.addView(favPage, lp(WRAP, dp(if (compact) FAV_HEAD_SHORT_DP else 32)))

        favGrid = PagedRows(
            context,
            dp(if (compact) FAV_ROW_SHORT_DP else FAV_ROW_DP),
            dividers = false,
        ).apply { onPageChanged = { favPage.text = pageLabel(favGrid) } }
        favPage.pagesClick { favGrid }

        favCard = softCard(if (compact) 6 else 10).apply {
            addView(favHead, lp(MATCH, WRAP))
            addView(favGrid, lp(MATCH, 0, 1f))
        }

        // 가로 화면: 왼쪽에 달력, 오른쪽에 지금 읽는 책과 자주 쓰는 앱. 세로 화면은 위에서 아래로.
        if (context.landscape()) {
            val row = hbox().apply {
                gravity = Gravity.TOP
                setPadding(dp(Ui.CARD_GAP_DP), 0, dp(Ui.CARD_GAP_DP), 0)
            }
            addView(row, lp(MATCH, 0, 1f))
            row.addView(vbox().apply {
                addView(calendarCard, lp(MATCH, 0, 1f))
            }, lp(0, MATCH, 0.56f))
            row.addView(vline(16))
            row.addView(vbox().apply {
                addView(heroSlot, lp(MATCH, WRAP))
                addView(favCard, lp(MATCH, 0, 1f).apply { topMargin = dp(Ui.CARD_GAP_DP) })
            }, lp(0, MATCH, 0.44f))
        } else {
            addView(heroSlot, cardLp())
            addView(calendarCard, cardLp(topDp = Ui.CARD_GAP_DP))
            addView(favCard, cardLp(weight = 1f, topDp = Ui.CARD_GAP_DP))
        }

        addView(hline(1, Ui.DIVIDER, inset = false))
        addView(footer(), lp(MATCH, dp(if (Ui.tablet) 64 else Ui.FOOTER_DP)).apply { bottomMargin = dp(8) })

        showMonth(month)
        favGrid.onPageChanged?.invoke()
    }

    /** 이번 달로 돌아간다(홈 버튼을 다시 누를 때). */
    fun reset() {
        showMonth(YearMonth.now())
        favGrid.showPage(0)
    }

    /** 맨 위 지금 읽는 책 카드. null 이면 자리를 비운다(책이 없을 때). */
    fun setHero(view: View?) {
        heroSlot.removeAllViews()
        heroSlot.visibility = if (view == null) GONE else VISIBLE
        if (view != null) heroSlot.addView(view, FrameLayout.LayoutParams(MATCH, WRAP))
    }

    /**
     * 달력에 읽은 날 표시를 넣는다(기본 달력 위젯 `MonthDots` 와 같은 규칙): 5분 이상 읽은 날은 검은 원,
     * 하루 목표를 채운 날은 그 둘레에 링, 오늘은 검은 테두리. [days] 가 지난번과 같은 객체면 다시 그리지 않는다.
     */
    fun setReading(days: ReadingDays?, goalMs: Long) {
        if (days === readingDays && goalMs == this.goalMs) return
        readingDays = days
        this.goalMs = goalMs
        showMonth(month)
    }

    /** 자주 쓰는 앱. [state] 가 지난번과 같으면 다시 그리지 않는다. */
    fun setFavorites(favs: List<AppEntry>, state: List<Any?>) {
        if (state == shownFavs) return
        shownFavs = state
        val lines = favs.chunked(columns)
        favGrid.setRows(
            if (favs.isEmpty()) listOf {
                text(getString(R.string.calendar_empty_favorites), 15f, color = Ui.LIGHT_GRAY, lines = 2).apply {
                    setPadding(dp(2), dp(12), dp(2), 0)
                }
            } else lines.map { line -> { tileRow(line) } },
        )
    }

    /** 밀기·페이지 버튼: 앱이 여러 쪽이면 앱을, 한 쪽이면 달을 넘긴다. */
    fun turn(next: Boolean): Boolean {
        if (favGrid.pageCount > 1) return favGrid.turn(next)
        showMonth(if (next) month.plusMonths(1) else month.minusMonths(1))
        return true
    }

    private fun showMonth(m: YearMonth) {
        month = m
        val english = HomePrefs.isEnglish(context)
        monthTitle.text = m.format(DateTimeFormatter.ofPattern(if (english) "MMMM yyyy" else "yyyy년 M월", locale))
        val streak = readingDays?.let { ReadingLog.streak(it) } ?: 0
        streakText.text = if (streak > 0) getString(R.string.widget_streak_short, streak) else ""
        dayGrid.removeAllViews()
        val first = m.atDay(1)
        // 한 주 시작(설정)에 맞춘 격자에서 1일이 몇 번째 칸인지. 언제나 6주를 그려 달을 넘겨도 높이가 같다.
        val offset = (first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
        val start = first.minusDays(offset.toLong())
        val today = LocalDate.now()
        repeat(6) { week ->
            dayGrid.addView(hbox().apply {
                repeat(7) { col ->
                    val date = start.plusDays((week * 7 + col).toLong())
                    val inMonth = YearMonth.from(date) == m
                    val ms = if (inMonth) readingDays?.total(date) ?: 0L else 0L
                    addView(
                        DayCell(
                            context,
                            date.dayOfMonth,
                            date == today,
                            inMonth,
                            Palette.current.title(Palette.Area.HOME),
                            read = ms >= ReadingLog.MIN_DAY_MS,
                            goal = goalMs > 0 && ms >= goalMs,
                        ) { onOpenStats() },
                        lp(0, dp(DAY_ROW_DP), 1f),
                    )
                }
            }, lp(MATCH, dp(DAY_ROW_DP)))
        }
    }

    /**
     * 타일 한 줄: 둥근 회색 타일 안에 앱 아이콘(흑백, 컬러 테마면 원래 색), 아래에 이름 한 줄.
     * [columns] 칸에 맞춰 칸 폭을 같게 두고, 덜 찬 줄은 빈 칸으로 채운다.
     */
    private fun tileRow(line: List<AppEntry>): View = hbox().apply {
        setPadding(0, 0, 0, 0)
        val icon = dp(if (Ui.tablet) 32 else 27)
        line.forEachIndexed { i, app ->
            addView(vbox().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                addView(IconTile(context).apply {
                    addView(picture(AppIcons.icon(context, app, icon)), FrameLayout.LayoutParams(icon, icon, Gravity.CENTER))
                }, lp(dp(if (Ui.tablet) 52 else 46), dp(if (Ui.tablet) 52 else 46)))
                addView(
                    text(app.label, if (Ui.tablet) 13f else 11.5f).apply {
                        gravity = Gravity.CENTER
                        ellipsize = TextUtils.TruncateAt.END
                        setPadding(dp(2), 0, dp(2), 0)
                    },
                    lp(MATCH, WRAP).apply { topMargin = dp(4) },
                )
                setOnClickListener { onOpen(app) }
                setOnLongClickListener { onLongPress(app); true }
            }, lp(0, MATCH, 1f).apply { if (i > 0) marginStart = dp(GAP_DP) })
        }
        repeat(columns - line.size) { addView(View(context), lp(0, 1, 1f).apply { marginStart = dp(GAP_DP) }) }
    }

    private fun arrow(symbol: String, action: () -> Unit) =
        text(symbol, 20f, Ui.medium, Ui.LIGHT_GRAY).apply {
            gravity = Gravity.CENTER
            setOnClickListener { action() }
        }

    private fun footer() = hbox().apply {
        listOf(
            Triple(R.drawable.ic_apps, getString(R.string.all_apps), onOpenApps),
            Triple(R.drawable.ic_mini_apps, getString(R.string.mini_apps), onOpenMini),
            Triple(R.drawable.ic_game, getString(R.string.games), onOpenGames),
            Triple(R.drawable.ic_settings, getString(R.string.settings), onOpenSettings),
        ).forEach { (icon, label, action) ->
            addView(vbox().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(5), 0, dp(4))
                addView(PictureView(context).apply {
                    drawable = getDrawable(icon)
                    contentDescription = label
                }, lp(dp(if (Ui.tablet) 28 else 24), dp(if (Ui.tablet) 28 else 24)))
                addView(text(label, if (Ui.tablet) 13f else 12f, Ui.medium), lp(WRAP, WRAP).apply { topMargin = dp(2) })
                setOnClickListener { action() }
            }, lp(0, MATCH, 1f))
        }
    }

    private val locale: Locale get() = resources.configuration.locales[0]

    /**
     * 날짜 한 칸: 5분 이상 읽은 날은 검은 원 안에 흰 숫자, 하루 목표를 채운 날은 둘레에 링.
     * 오늘은 검은 둥근 테두리로 표시하고, 다른 달 날짜는 그리지 않는다. 누르면 독서 기록.
     */
    private class DayCell(
        context: Context,
        private val day: Int,
        private val today: Boolean,
        private val inMonth: Boolean,
        accent: Int,
        private val read: Boolean,
        private val goal: Boolean,
        onClick: () -> Unit,
    ) : View(context) {
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = context.spf(14f)
            textAlign = Paint.Align.CENTER
            typeface = if (today) Ui.bold else Ui.regular
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = context.dpf(1.2f)
            color = Ui.BLACK
        }
        private val todayRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = context.dpf(2f)
            color = Ui.BLACK
        }

        init {
            isClickable = true
            contentDescription = day.toString()
            setOnClickListener { onClick() }
        }

        override fun onDraw(canvas: Canvas) {
            if (!inMonth) return
            val cx = width / 2f
            val cy = height / 2f
            val r = minOf(width, height) / 2f - context.dpf(4)
            if (read) canvas.drawCircle(cx, cy, r, fill)
            // 오늘은 테두리로 표시하므로 목표 링을 겹쳐 그리지 않는다(링이 두 겹으로 보이지 않게).
            if (goal && !today) canvas.drawCircle(cx, cy, r + context.dpf(1.5f), ring)
            if (today && !read) canvas.drawCircle(cx, cy, r, todayRing)
            text.color = if (read) android.graphics.Color.WHITE else Ui.BLACK
            val y = cy - (text.descent() + text.ascent()) / 2f
            canvas.drawText(day.toString(), cx, y, text)
        }
    }

    /** 자주 쓰는 앱 타일: 옅은 회색 둥근 사각형([IconTile]). */

    companion object {
        /** 타일 사이 간격 */
        private const val GAP_DP = 6
        /** 앱 타일 한 줄 높이(아이콘 타일 46 + 이름 한 줄) */
        private const val FAV_ROW_DP = 68
        /** 가로 화면: 오른쪽 칸이 낮아 자주 쓰는 앱 카드를 낮게 그린다 */
        private const val FAV_ROW_SHORT_DP = 66
        private const val FAV_HEAD_SHORT_DP = 26
        /** 달력 날짜 한 줄 높이(읽은 날 원·목표 링이 들어가고, 아래 자주 쓰는 앱 자리를 남긴다) */
        private const val DAY_ROW_DP = 33
    }
}
