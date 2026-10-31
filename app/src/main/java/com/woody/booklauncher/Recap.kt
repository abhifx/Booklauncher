package com.woody.booklauncher

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * ⑮ 월간 결산의 데이터와 배치. 한 달의 날마다 읽은 시간, 그달 읽은 책(합쳐 5분 이상 읽었거나 그달 완독한 책)과 책마다 날마다 읽은 시간,
 * 그해 날마다 읽은 시간(아래 올해 잔디). 그림은 [RecapImage], 화면은 [RecapActivity].
 */
object Recap {
    /** 이만큼(일) 넘게 쉬면 달력 막대를 끊는다(사이에 안 읽은 날이 이보다 적으면 한 막대) */
    private const val GAP_DAYS = 3

    /** 달력 한 칸에 보이는 막대 줄 수(주가 다섯이면. 여섯 주인 달은 칸이 낮아 그림이 줄인다). 넘는 책은 그 칸에 `+n권 더`. */
    const val LANES = 4

    /**
     * 그달 읽은 책 한 권: 날마다 읽은 시간(1일부터), 그달 마지막으로 완독한 날(없으면 null), 그달에 걸친 회차의 별점(반 개 단위)·감상,
     * 표지에서 뽑은 막대 색은 그릴 때 정한다.
     */
    class Book(val book: ShelfBook, val dayMs: LongArray, val finishDay: Int?, val halves: Int, val review: String) {
        val ms = dayMs.sum()

        /** 읽은 날(1부터). 완독한 날은 읽은 시간이 없어도 넣는다(막대 끝에 완독 표시가 놓이게). */
        val days: List<Int> = (dayMs.indices.filter { dayMs[it] > 0 }.map { it + 1 } + listOfNotNull(finishDay)).distinct().sorted()
        val firstDay get() = days.firstOrNull() ?: 1
        val lastDay get() = days.lastOrNull() ?: 1

        /** 조금이라도 읽은 날 수 */
        val readDays = dayMs.count { it > 0 }
    }

    /**
     * 한 달: [dayMs] 는 어떤 책인지 모르는 시간까지 더한 날마다의 합계(1일부터). [books] 는 처음 읽은 날 순.
     * [yearDayMs] 는 그해 날마다 합계(올해 잔디), [goalMin] 은 잔디 단계의 기준(하루 목표, 없으면 30분).
     */
    class Month(
        val month: YearMonth, val dayMs: LongArray, val books: List<Book>, val yearDayMs: Map<LocalDate, Long>,
        val goalMin: Int, val firstDayOfWeek: DayOfWeek,
    ) {
        val totalMs = dayMs.sum()

        /** [ReadingLog.MIN_DAY_MS] 이상 읽은 날 수(연속일과 같은 기준) */
        val readDays = dayMs.count { it >= ReadingLog.MIN_DAY_MS }
        val finished = books.count { it.finishDay != null }

        /** 가장 많이 읽은 날(1부터). 읽은 날이 없으면 null. */
        val bestDay: Int? = dayMs.indices.maxByOrNull { dayMs[it] }?.takeIf { dayMs[it] > 0 }?.plus(1)
    }

    /** [month] 의 결산. 사용 기록 권한이 없으면(꺼 둠·잠금 해제 전) 저장해 둔 지난 기록으로. */
    fun load(context: Context, month: YearMonth, prefs: HomePrefs): Month {
        val days = ReadingLog.days(context, ReadingLog.readerPackages(context)) ?: ReadingLog.savedDays(context)
        val records = BookRecord.all(context, days).filter { !it.hidden }
        return build(month, days, records, prefs.readingGoalMin, prefs.firstDayOfWeek)
    }

    /** [load] 의 몸통(테스트는 읽은 시간과 기록을 직접 넣는다) */
    internal fun build(month: YearMonth, days: ReadingDays, records: List<BookRecord>, goalMin: Int, firstDayOfWeek: DayOfWeek): Month {
        val n = month.lengthOfMonth()
        val dayMs = LongArray(n) { days.total(month.atDay(it + 1)) }
        val books = records.mapNotNull { r ->
            val per = LongArray(n) { r.dayMs[month.atDay(it + 1)] ?: 0L }
            val finish = r.finishes.filter { YearMonth.from(it) == month }.maxOrNull()
            if (per.sum() < ReadingLog.MIN_DAY_MS && finish == null) return@mapNotNull null
            // 별점·감상: 그달 완독한 회차, 없으면 그달에 걸친 진행 중인 회차
            val round = r.rounds.lastOrNull { it.finished && it.to != null && YearMonth.from(it.to) == month }
                ?: r.rounds.lastOrNull { !it.finished && (it.from == null || YearMonth.from(it.from) <= month) }
            Book(r.book, per, finish?.dayOfMonth, round?.review?.halves ?: 0, round?.review?.text.orEmpty())
        }.sortedWith(compareBy<Book> { it.firstDay }.thenByDescending { it.ms })
        val year = days.keys.filter { it.year == month.year }.associateWith { days.total(it) }
        return Month(month, dayMs, books, year, goalMin.takeIf { it > 0 } ?: DEFAULT_GOAL_MIN, firstDayOfWeek)
    }

    private const val DEFAULT_GOAL_MIN = 30

    /** 읽은 날들을 [GAP_DAYS] 일 넘게 쉬지 않는 구간으로 묶는다(1부터, 오름차순) */
    internal fun segments(days: List<Int>): List<IntRange> {
        val out = mutableListOf<IntRange>()
        for (d in days.sorted()) {
            val last = out.lastOrNull()
            if (last != null && d - last.last <= GAP_DAYS + 1) out[out.size - 1] = last.first..d else out += d..d
        }
        return out
    }

    /**
     * 달력의 막대 한 조각(한 주 안): [week] 번째 주, [lane] 번째 줄, 그 주의 [from]..[to] 칸(0~6), [book] 번째 책.
     * [starts]·[ends] 는 구간이 이 조각에서 시작·끝나는가(둥근 끝), [segment] 는 구간의 날들(완독한 날이 들면 구간 끝 조각에 완독 표시).
     */
    class Piece(val week: Int, val lane: Int, val from: Int, val to: Int, val book: Int, val starts: Boolean, val ends: Boolean, val segment: IntRange)

    /** 달력 배치: 첫 칸의 날, 주 수, 막대 조각, 날마다 칸에 들지 못한 책 수(1일부터, `+n권 더`) */
    class Calendar(val start: LocalDate, val weeks: Int, val pieces: List<Piece>, val hidden: IntArray)

    /**
     * 주마다 막대 조각을 줄에 채운다(구글 캘린더처럼): 그 주에서 먼저 시작하는 조각부터(같으면 긴 것), 비어 있는 가장 위 줄로.
     * [lanes] 줄을 넘는 조각은 그리지 않고 그 날들의 `+n권 더` 로 센다.
     */
    internal fun calendar(month: YearMonth, firstDayOfWeek: DayOfWeek, books: List<Book>, lanes: Int = LANES): Calendar {
        val start = firstCell(month, firstDayOfWeek)
        val weeks = weeks(month, firstDayOfWeek)
        val hidden = IntArray(month.lengthOfMonth())
        val pieces = mutableListOf<Piece>()
        val offset = (month.atDay(1).toEpochDay() - start.toEpochDay()).toInt()
        class Cand(val a0: Int, val a1: Int, val book: Int, val starts: Boolean, val ends: Boolean, val segment: IntRange)
        for (w in 0 until weeks) {
            val ws = 7 * w
            val cands = books.flatMapIndexed { bi, b ->
                segments(b.days).mapNotNull { seg ->
                    val k0 = offset + seg.first - 1
                    val k1 = offset + seg.last - 1
                    val a0 = maxOf(k0, ws)
                    val a1 = minOf(k1, ws + 6)
                    if (a0 <= a1) Cand(a0, a1, bi, a0 == k0, a1 == k1, seg) else null
                }
            }.sortedWith(compareBy<Cand> { it.a0 }.thenByDescending { it.a1 - it.a0 })
            val ends = mutableListOf<Int>()
            for (c in cands) {
                var lane = ends.indexOfFirst { it < c.a0 }
                if (lane < 0) { lane = ends.size; ends += c.a1 } else ends[lane] = c.a1
                if (lane < lanes) pieces += Piece(w, lane, c.a0 - ws, c.a1 - ws, c.book, c.starts, c.ends, c.segment)
                else for (k in c.a0..c.a1) {
                    val day = k - offset + 1
                    if (day in 1..hidden.size) hidden[day - 1]++
                }
            }
        }
        return Calendar(start, weeks, pieces, hidden)
    }

    /** 그달 달력의 주 수(첫 칸은 [firstDayOfWeek] 에 맞춘 1일이 든 주) */
    internal fun weeks(month: YearMonth, firstDayOfWeek: DayOfWeek): Int =
        ((month.atEndOfMonth().toEpochDay() - firstCell(month, firstDayOfWeek).toEpochDay()) / 7 + 1).toInt()

    /** 달력 첫 칸의 날: 1일이 든 주의 [firstDayOfWeek] */
    private fun firstCell(month: YearMonth, firstDayOfWeek: DayOfWeek): LocalDate =
        month.atDay(1).with(TemporalAdjusters.previousOrSame(firstDayOfWeek))

    /** 오른쪽 칸 모양: 책이 없으면 비움, 1권은 큰 표지와 이야기, 2권은 위아래 두 칸, 3~5권은 칸마다 표지와 정보, 6권부터 표지만 격자 */
    enum class Side { NONE, ONE, TWO, ROWS, GRID }

    internal fun side(books: Int): Side = when (books) {
        0 -> Side.NONE
        1 -> Side.ONE
        2 -> Side.TWO
        in 3..ROWS_MAX -> Side.ROWS
        else -> Side.GRID
    }

    private const val ROWS_MAX = 5

    /**
     * 표지 격자: [cols] 열, 표지 폭 [coverW], 보이는 책 [shown](인덱스, 처음 읽은 순), 들지 못한 책 수 [more](마지막 칸 `+n`).
     * 열 수는 [width]×[height] 안에서 표지가 가장 크게 나오는 것. 그래도 [minW] 보다 작으면 [minW] 로 들어가는 만큼만, 오래 읽은 순으로.
     */
    class Grid(val cols: Int, val coverW: Float, val shown: List<Int>, val more: Int)

    internal fun grid(books: List<Book>, width: Float, height: Float, gap: Float, minW: Float, maxCols: Int = 6): Grid {
        val n = books.size
        fun wFor(c: Int): Float {
            val r = (n + c - 1) / c
            return minOf((width - (c - 1) * gap) / c, (height - (r - 1) * gap) / r / 1.5f)
        }
        val cols = (1..maxCols).maxByOrNull { wFor(it) } ?: 1
        val w = wFor(cols)
        if (w >= minW) return Grid(cols, w, books.indices.toList(), 0)
        val c = ((width + gap) / (minW + gap)).toInt().coerceAtLeast(1)
        val r = ((height + gap) / (minW * 1.5f + gap)).toInt().coerceAtLeast(1)
        val cap = c * r
        val top = books.indices.sortedByDescending { books[it].ms }.take(cap - 1).sortedBy { books[it].firstDay }
        return Grid(c, minW, top, n - top.size)
    }

    /** 잔디 단계(0 안 읽음 ~ 4): 목표 미만 · 목표 이상 · 두 배 이상 · 네 배 이상 */
    internal fun level(ms: Long, goalMin: Int): Int {
        val m = ms / 60_000
        return when {
            ms <= 0 -> 0
            m < goalMin -> 1
            m < goalMin * 2 -> 2
            m < goalMin * 4 -> 3
            else -> 4
        }
    }
}
