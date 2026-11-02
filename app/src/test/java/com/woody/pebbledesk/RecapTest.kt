package com.woody.pebbledesk

import com.woody.pebbledesk.BookRecord.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** ⑮ 월간 결산의 데이터와 배치([Recap]): 그달 읽은 책, 막대 끊기, 주마다 줄 채우기, 오른쪽 칸 모양 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecapTest {
    private val oct = YearMonth.of(2026, 10)
    private fun d(s: String) = LocalDate.parse(s)

    private fun rec(id: String, days: Map<String, Int> = emptyMap(), finishes: List<String> = emptyList()) = BookRecord(
        ShelfBook(id, id, "", null, 0), State.PAST, false,
        finishes.mapIndexed { i, f -> BookShelf.Finish(i.toLong(), d(f), null, null, Review(8, "좋았다")) },
        days.entries.associate { d(it.key) to it.value * 60_000L },
    )

    /** 기록에서 날마다 칸(책마다 한 칸, 모르는 시간 [unknown] 은 책 없이) */
    private fun days(records: List<BookRecord>, unknown: Map<String, Int> = emptyMap()): ReadingDays {
        val out = HashMap<LocalDate, HashMap<ReadKey, Long>>()
        records.forEach { r -> r.dayMs.forEach { (day, ms) -> out.getOrPut(day) { HashMap() }[ReadKey("pkg", r.book.title, r.book.id)] = ms } }
        unknown.forEach { (day, m) -> out.getOrPut(d(day)) { HashMap() }[ReadKey("pkg")] = m * 60_000L }
        return out
    }

    private fun book(days: List<Int>, finish: Int? = null): Recap.Book {
        val ms = LongArray(31)
        days.forEach { ms[it - 1] = 60_000L }
        return Recap.Book(ShelfBook("b", "b", "", null, 0), ms, finish, 0, "")
    }

    @Test fun `그달 합쳐 5분 미만인 책은 빠지고, 그달 완독한 책은 시간이 없어도 들어간다, 처음 읽은 날 순`() {
        val records = listOf(
            rec("short", mapOf("2026-10-03" to 4)),
            rec("late", mapOf("2026-10-20" to 30)),
            rec("early", mapOf("2026-10-02" to 10, "2026-09-30" to 60)),
            rec("done", finishes = listOf("2026-10-10")),
            rec("other-month", mapOf("2026-09-10" to 60)),
        )
        val m = Recap.build(oct, days(records, mapOf("2026-10-05" to 15)), records, 30, DayOfWeek.MONDAY)
        assertEquals(listOf("early", "done", "late"), m.books.map { it.book.id })
        // 합계는 모르는 시간까지(책에 붙지 않은 15분 포함), 다른 달은 빼고
        assertEquals((4 + 30 + 10 + 15) * 60_000L, m.totalMs)
        // 5분 이상 읽은 날만 센다(3일 4분은 빠짐)
        assertEquals(3, m.readDays)
        assertEquals(1, m.finished)
        assertEquals(20, m.bestDay)
        val done = m.books.first { it.book.id == "done" }
        assertEquals(10, done.finishDay)
        assertEquals(8, done.halves)
    }

    @Test fun `빈 달은 책도 가장 많이 읽은 날도 없다`() {
        val m = Recap.build(oct, emptyMap(), emptyList(), 0, DayOfWeek.MONDAY)
        assertTrue(m.books.isEmpty())
        assertNull(m.bestDay)
        assertEquals(30, m.goalMin)
    }

    @Test fun `사이에 쉰 날이 3일까지면 한 막대, 넘으면 끊는다`() {
        assertEquals(listOf(1..5), Recap.segments(listOf(1, 5)))
        assertEquals(listOf(1..1, 6..6), Recap.segments(listOf(1, 6)))
        assertEquals(listOf(2..4, 10..12), Recap.segments(listOf(4, 2, 3, 10, 12)))
    }

    @Test fun `완독한 날은 읽은 시간이 없어도 막대에 든다`() {
        assertEquals(listOf(3..8), Recap.segments(book(listOf(3, 5), finish = 8).days))
    }

    @Test fun `달력은 주마다 먼저 시작한 막대부터 위 줄로, 다음 주로 넘어가면 조각이 나뉜다`() {
        // 2026-10-01 은 목요일: 월요일 시작이면 첫 칸은 9월 28일, 다섯 주
        val books = listOf(book(listOf(1, 2, 3, 4, 5, 6)), book(listOf(2)))
        val cal = Recap.calendar(oct, DayOfWeek.MONDAY, books)
        assertEquals(d("2026-09-28"), cal.start)
        assertEquals(5, cal.weeks)
        val first = cal.pieces.filter { it.book == 0 }
        assertEquals(listOf(0 to (3 to 6), 1 to (0 to 1)), first.map { it.week to (it.from to it.to) })
        assertTrue(first[0].starts && !first[0].ends)
        assertTrue(!first[1].starts && first[1].ends)
        assertEquals(1, cal.pieces.first { it.book == 1 }.lane)
    }

    @Test fun `한 칸에 줄이 넘치는 책은 그 날들의 +n권 더 로 센다`() {
        val books = List(Recap.LANES + 2) { book(listOf(13, 14)) }
        val cal = Recap.calendar(oct, DayOfWeek.MONDAY, books)
        assertEquals(Recap.LANES, cal.pieces.size)
        assertEquals(2, cal.hidden[12])
        assertEquals(2, cal.hidden[13])
        assertEquals(0, cal.hidden[14])
    }

    @Test fun `오른쪽 칸은 1권 2권 3~5권 6권부터 격자`() {
        assertEquals(Recap.Side.NONE, Recap.side(0))
        assertEquals(Recap.Side.ONE, Recap.side(1))
        assertEquals(Recap.Side.TWO, Recap.side(2))
        assertEquals(Recap.Side.ROWS, Recap.side(5))
        assertEquals(Recap.Side.GRID, Recap.side(6))
    }

    @Test fun `격자는 표지가 가장 크게 나오는 열 수, 너무 작아지면 오래 읽은 순으로 자르고 나머지는 +n`() {
        val nine = List(9) { book(listOf(it + 1)) }
        val g = Recap.grid(nine, 286f, 760f, 10f, 44f)
        assertEquals(2, g.cols)
        assertEquals(9, g.shown.size)
        assertEquals(0, g.more)
        val many = List(60) { i -> book((1..(i % 5 + 1)).toList()) }
        val small = Recap.grid(many, 286f, 760f, 10f, 44f)
        assertTrue(small.more > 0)
        assertEquals(60, small.shown.size + small.more)
        // 오래 읽은 책이 먼저 남는다(남은 책 중 가장 짧은 것 ≥ 빠진 책 중 가장 긴 것)
        val left = many.indices - small.shown.toSet()
        assertTrue(small.shown.minOf { many[it].ms } >= left.maxOf { many[it].ms })
    }

    @Test fun `잔디 단계는 하루 목표의 1배 2배 4배`() {
        assertEquals(0, Recap.level(0, 30))
        assertEquals(1, Recap.level(29 * 60_000L, 30))
        assertEquals(2, Recap.level(30 * 60_000L, 30))
        assertEquals(3, Recap.level(60 * 60_000L, 30))
        assertEquals(4, Recap.level(120 * 60_000L, 30))
    }

    @Test fun `그달 읽지 않고 완독만 한 책은 읽은 날이 0이고 막대는 완독한 날 하루`() {
        val records = listOf(rec("done", mapOf("2026-09-30" to 60), finishes = listOf("2026-10-02")))
        val b = Recap.build(oct, days(records), records, 30, DayOfWeek.MONDAY).books.single()
        assertEquals(0, b.readDays)
        assertEquals(listOf(2), b.days)
        assertEquals(2, b.firstDay)
    }

    @Test fun `여섯 주인 달은 주 수가 6이고, 줄 수를 줄이면 넘는 책은 +n권 더`() {
        // 2026-08-01 은 토요일: 월요일 시작이면 7월 27일부터 여섯 주
        assertEquals(6, Recap.weeks(YearMonth.of(2026, 8), DayOfWeek.MONDAY))
        assertEquals(5, Recap.weeks(oct, DayOfWeek.MONDAY))
        val books = List(4) { book(listOf(13)) }
        val cal = Recap.calendar(oct, DayOfWeek.MONDAY, books, lanes = 3)
        assertEquals(3, cal.pieces.size)
        assertTrue(cal.pieces.all { it.lane < 3 })
        assertEquals(1, cal.hidden[12])
    }

    @Test fun `크게 보기는 바깥 흰 여백을 잘라 내용 둘레만 남긴다`() {
        val full = android.graphics.Bitmap.createBitmap(1080, 1350, android.graphics.Bitmap.Config.ARGB_8888)
        val c = RecapImage.content(full)
        // 좌우 72-24, 위 40-24, 아래 44-24 를 뺀 크기
        assertEquals(1080 - 2 * 48, c.width)
        assertEquals(1350 - 16 - 20, c.height)
    }

    @Test fun `표지에서 가장 많이 보인 색은 읽은 시간만큼 세고, 색이 없으면 null`() {
        val red = IntArray(10) { android.graphics.Color.rgb(200, 40, 40) }
        val blue = IntArray(10) { android.graphics.Color.rgb(40, 60, 200) }
        val gray = IntArray(10) { android.graphics.Color.rgb(128, 128, 128) }
        // 파랑 표지를 더 오래 읽었으면 파랑(색상 칸 가운데, 0.6~0.7)
        val (hue, sat) = Recap.dominantHue(listOf(red to 10L, blue to 60L, gray to 600L))!!
        assertTrue(hue in 0.58f..0.71f)
        assertTrue(sat > 0.5f)
        // 빨강을 더 오래 읽으면 빨강(첫 칸)
        assertTrue(Recap.dominantHue(listOf(red to 60L, blue to 10L))!!.first < 0.1f)
        // 흑백 표지뿐이면 고르지 못한다(계절 색으로)
        assertNull(Recap.dominantHue(listOf(gray to 100L)))
        assertNull(Recap.dominantHue(emptyList()))
    }

    @Test fun `고친 기간 밖에 읽은 날은 결산의 책 막대·시간에서만 빠지고 합계에는 남는다`() {
        // 11월 1일에 30분 읽고 11월 2일에 완독을 눌렀고, 읽기 시작한 날 10월 14일·완독한 날 10월 15일로 고쳤다
        val pressed = d("2026-11-02").atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val r = BookRecord(
            ShelfBook("m", "몽실 언니", "", null, 0), State.PAST, false,
            listOf(BookShelf.Finish(1, d("2026-10-15"), pressed, null, Review(8, "좋았다"), d("2026-10-14"))),
            mapOf(d("2026-11-01") to 30 * 60_000L),
        )
        val days = days(listOf(r))
        val octM = Recap.build(oct, days, listOf(r), 30, DayOfWeek.MONDAY)
        val b = octM.books.single()
        assertEquals(15, b.finishDay)
        assertEquals(14, b.startDay)
        assertEquals(listOf(14, 15), b.days)
        assertEquals(0L, b.ms)
        assertEquals(8, b.halves)
        assertEquals(1, octM.finished)
        assertEquals(0L, octM.totalMs)
        // 11월: 책 목록에는 없고, 읽은 30분은 그달 합계에 그대로
        val novM = Recap.build(YearMonth.of(2026, 11), days, listOf(r), 30, DayOfWeek.MONDAY)
        assertTrue(novM.books.isEmpty())
        assertEquals(30 * 60_000L, novM.totalMs)
        assertEquals(1, novM.readDays)
        // 결산 밖(상세의 회차 시간)은 그대로
        assertEquals(30 * 60_000L, r.rounds.single().ms)
    }

    @Test fun `날짜를 고치지 않은 책은 완독 뒤 잠깐 연 날도 결산에 그대로`() {
        val pressed = d("2026-10-10").atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val r = BookRecord(
            ShelfBook("a", "a", "", null, 0), State.PAST, false,
            listOf(BookShelf.Finish(1, d("2026-10-10"), pressed, null, null)),
            mapOf(d("2026-10-08") to 30 * 60_000L, d("2026-10-12") to 2 * 60_000L),
        )
        assertEquals(r.dayMs, r.periodDayMs)
        val b = Recap.build(oct, days(listOf(r)), listOf(r), 30, DayOfWeek.MONDAY).books.single()
        assertEquals(listOf(8, 10, 12), b.days)
        assertNull(b.startDay)
    }
}
