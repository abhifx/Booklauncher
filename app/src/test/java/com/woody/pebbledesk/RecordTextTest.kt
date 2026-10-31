package com.woody.pebbledesk

import com.woody.pebbledesk.BookRecord.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

/** 상세의 기록 칸·전체 기록에 보이는 글자: 기간, 시간(1분 이상), `완독 N회 · 마지막 …`, `전체 기록 ›`/`고치기 ›`, 요약 줄, 회차 줄 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko")
class RecordTextTest {
    private val c get() = RuntimeEnvironment.getApplication()
    private val today = LocalDate.parse("2026-10-15")
    private val book = ShelfBook("b-1", "책", "", null, 0)

    private fun d(s: String) = LocalDate.parse(s)
    private fun min(m: Int) = m * 60_000L
    private fun fin(id: Long, day: String, started: String? = null) = BookShelf.Finish(id, d(day), null, started?.let(::d), null)
    private fun round(from: String?, to: String?, ms: Long = 0, finished: Boolean = to != null, nth: Int? = null) =
        BookRecord.Round(nth, from?.let(::d), to?.let(::d), ms, finished, null)

    @Test fun `기간`() {
        assertEquals("10월 5일부터", c.period(round("2026-10-05", null), withYear = false))
        assertEquals("시작일 모름", c.period(round(null, null), withYear = false))
        assertEquals("8월 20일 – 9월 6일", c.period(round("2026-08-20", "2026-09-06"), withYear = false))
        assertEquals("12월 25일 – 2027년 1월 6일", c.period(round("2026-12-25", "2027-01-06"), withYear = false))
        assertEquals("2026년 9월 20일 – 10월 8일", c.period(round("2026-09-20", "2026-10-08"), withYear = true))
        assertEquals("10월 5일", c.period(round(null, "2026-10-05"), withYear = false))
    }

    @Test fun `기록 칸의 기간은 올해가 아니면 해를 붙인다`() {
        assertEquals("9월 20일 – 10월 8일", c.recordPeriod(round("2026-09-20", "2026-10-08"), today))
        assertEquals("2025년 3월 2일 – 3월 20일", c.recordPeriod(round("2025-03-02", "2025-03-20"), today))
    }

    @Test fun `시간은 1분 이상이면 보인다`() {
        assertNull(c.roundTime(59_000))
        assertEquals("1분", c.roundTime(min(1)))
        assertEquals("4분", c.roundTime(min(4)))
        assertEquals("3시간 54분", c.roundTime(min(234)))
    }

    @Test fun `완독 요약은 완독한 적이 있을 때만, 마지막 완독이 올해가 아니면 해를 붙인다`() {
        assertNull(c.finishSummary(BookRecord(book, State.READING, false, emptyList(), emptyMap()), today))
        val r = BookRecord(book, State.PAST, false, listOf(fin(1, "2025-05-10"), fin(2, "2026-10-08")), emptyMap())
        assertEquals("완독 2회 · 마지막 10월 8일", c.finishSummary(r, today))
        val old = BookRecord(book, State.PAST, false, listOf(fin(1, "2025-05-10")), emptyMap())
        assertEquals("완독 1회 · 마지막 2025년 5월 10일", c.finishSummary(old, today))
    }

    @Test fun `기록 칸 오른쪽은 회차가 둘 이상이면 전체 기록, 완독한 회차 하나면 고치기, 아니면 없음`() {
        assertNull(c.recordMore(BookRecord(book, State.READING, false, emptyList(), mapOf(d("2026-10-01") to min(10)))))
        assertEquals("고치기 ›", c.recordMore(BookRecord(book, State.PAST, false, listOf(fin(1, "2026-10-08")), emptyMap())))
        assertEquals("전체 기록 ›", c.recordMore(BookRecord(book, State.READING, false, listOf(fin(1, "2026-10-08")), emptyMap())))
    }

    @Test fun `전체 기록 요약 - 모두는 회차 시간의 합, 시작한 해는 여러 해에 걸쳤을 때만`() {
        val days = mapOf(d("2025-05-01") to min(30), d("2026-09-01") to min(90))
        val r = BookRecord(book, State.PAST, false, listOf(fin(1, "2025-05-10"), fin(2, "2026-09-05")), days)
        assertEquals("  ·  모두 2시간  ·  2025년부터", c.historyRest(r, today))
        val sameYear = BookRecord(book, State.PAST, false, listOf(fin(1, "2026-05-10"), fin(2, "2026-09-05")), mapOf(d("2026-05-01") to min(30)))
        assertEquals("  ·  모두 30분", c.historyRest(sameYear, today))
    }

    @Test fun `전체 기록 요약 - 진행 중 회차가 해를 넘기면 시작한 해를 붙인다`() {
        val r = BookRecord(book, State.READING, false, listOf(fin(1, "2025-05-10")), mapOf(d("2025-05-01") to min(30), d("2025-12-20") to min(30)))
        assertEquals("  ·  모두 1시간  ·  2025년부터", c.historyRest(r, today))
    }

    @Test fun `회차 줄 - 진행 중이면 진행 중, 1분 미만이면 시간 없이`() {
        assertEquals("2회  ·  진행 중  ·  3시간 54분", c.roundDetail(round("2026-10-05", null, min(234), nth = 2)))
        assertEquals("1회  ·  4분", c.roundDetail(round("2026-09-01", "2026-09-05", min(4), nth = 1)))
        assertEquals("3회", c.roundDetail(round("2026-10-14", "2026-10-14", 0, nth = 3)))
    }

    @Test fun `완독한 날 범위 글자 - 읽은 기록이 없는 첫 완독은 1900년부터`() {
        val none = BookRecord(book, State.PAST, false, listOf(fin(1, "2026-09-05")), emptyMap())
        assertEquals("1900.01.01 – 2026.10.15 사이", c.finishDayRangeText(none, none.rounds[0], today))
        val read = BookRecord(book, State.PAST, false, listOf(fin(1, "2026-09-05")), mapOf(d("2026-09-01") to min(30)))
        assertEquals("2026.09.01 – 2026.10.15 사이", c.finishDayRangeText(read, read.rounds[0], today))
    }

    @Test fun `읽기 시작한 날 범위 글자 - 첫 회차는 까지, 앞 회차가 있으면 사이`() {
        val first = BookRecord(book, State.READING, false, emptyList(), mapOf(d("2026-10-08") to min(30)))
        assertEquals("2026.10.15 까지", c.startDayRangeText(first, first.rounds[0], today, null))
        assertEquals("2026.10.10 까지", c.startDayRangeText(first, first.rounds[0], today, d("2026-10-10")))
        val second = BookRecord(book, State.READING, false, listOf(fin(1, "2025-05-10")), mapOf(d("2026-10-08") to min(30)))
        assertEquals("2025.05.10 – 2026.10.15 사이", c.startDayRangeText(second, second.rounds[1], today, null))
    }
}
