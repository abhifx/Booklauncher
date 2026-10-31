package com.woody.pebbledesk

import com.woody.pebbledesk.BookRecord.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 회차 계산([BookRecord.rounds]): 완독으로 나누기, 진행 중 회차가 생기는 조건(5분), 다시 읽기한 날, 같은 날 두 번 완독. 완독한 날 범위, 목록에 보이는 해 */
class BookRecordRoundsTest {
    private val book = ShelfBook("b-1", "책", "", null, 0)

    private fun d(s: String) = LocalDate.parse("2026-$s")
    private fun minutes(m: Int) = m * 60_000L
    private fun fin(id: Long, day: String, started: String? = null, review: Review? = null, began: String? = null) =
        BookShelf.Finish(id, d(day), null, started?.let(::d), review, began?.let(::d))

    private fun rec(
        state: State = State.PAST, finishes: List<BookShelf.Finish> = emptyList(), days: Map<String, Int> = emptyMap(),
        open: Review? = null, start: String? = null, began: String? = null,
    ) = BookRecord(book, state, false, finishes, days.entries.associate { d(it.key) to minutes(it.value) }, open, start?.let(::d), openBegan = began?.let(::d))

    @Test fun `완독한 적 없는 책은 진행 중 회차 하나`() {
        val r = rec(State.READING, days = mapOf("09-01" to 10, "09-03" to 20)).rounds.single()
        assertNull(r.nth)
        assertFalse(r.finished)
        assertEquals(d("09-01"), r.from)
        assertNull(r.to)
        assertEquals(minutes(30), r.ms)
        assertEquals(d("09-03"), r.lastRead)
    }

    @Test fun `완독하지 않고 뺀 책도 진행 중`() {
        val r = rec(State.PAST, days = mapOf("09-01" to 10)).rounds.single()
        assertFalse(r.finished)
        assertNull(r.to)
    }

    @Test fun `한 번 완독하고 그 뒤 안 읽었으면 완독 회차 하나`() {
        val r = rec(finishes = listOf(fin(1, "09-05")), days = mapOf("09-01" to 10, "09-03" to 20)).rounds.single()
        assertTrue(r.finished)
        assertEquals(d("09-01"), r.from)
        assertEquals(d("09-05"), r.to)
        assertEquals(minutes(30), r.ms)
        assertEquals(1L, r.finishId)
    }

    @Test fun `완독 뒤 5분 미만으로 잠깐 연 것은 회차가 아니다`() {
        assertEquals(1, rec(finishes = listOf(fin(1, "09-05")), days = mapOf("09-01" to 10, "09-10" to 4)).rounds.size)
    }

    @Test fun `완독 뒤 합쳐 5분 이상 읽으면 진행 중 회차`() {
        val rounds = rec(finishes = listOf(fin(1, "09-05")), days = mapOf("09-01" to 10, "09-10" to 3, "09-11" to 3)).rounds
        assertEquals(listOf(1, 2), rounds.map { it.nth })
        val open = rounds[1]
        assertFalse(open.finished)
        assertEquals(d("09-10"), open.from)
        assertEquals(minutes(6), open.ms)
    }

    @Test fun `다시 읽기만 누르고 안 읽었으면 그날부터 시간 없이`() {
        val open = rec(State.READING, listOf(fin(1, "09-05")), mapOf("09-01" to 10), start = "09-20").rounds[1]
        assertEquals(d("09-20"), open.from)
        assertEquals(0L, open.ms)
        assertNull(open.lastRead)
    }

    @Test fun `다시 읽기 전에 잠깐 연 시간은 빼고 그날부터`() {
        val open = rec(State.READING, listOf(fin(1, "09-05")), mapOf("09-10" to 1, "09-21" to 10), start = "09-20").rounds[1]
        assertEquals(d("09-21"), open.from)
        assertEquals(minutes(10), open.ms)
    }

    @Test fun `다시 읽기 전에 5분 이상 읽었으면 이미 시작한 회차`() {
        val open = rec(State.READING, listOf(fin(1, "09-05")), mapOf("09-10" to 6), start = "09-20").rounds[1]
        assertEquals(d("09-10"), open.from)
        assertEquals(minutes(6), open.ms)
    }

    @Test fun `같은 날 두 번 완독하면 두 회차, 두 번째는 그날 하루 시간 없이`() {
        val rounds = rec(finishes = listOf(fin(1, "09-05"), fin(2, "09-05", started = "09-05")), days = mapOf("09-01" to 10, "09-05" to 10)).rounds
        assertEquals(2, rounds.size)
        assertEquals(minutes(20), rounds[0].ms)
        assertEquals(d("09-05"), rounds[1].from)
        assertEquals(d("09-05"), rounds[1].to)
        assertEquals(0L, rounds[1].ms)
        assertEquals(listOf(1L, 2L), rounds.map { it.finishId })
    }

    @Test fun `다시 읽기로 시작해 안 읽고 완독한 회차는 다시 읽기한 날부터`() {
        val second = rec(finishes = listOf(fin(1, "09-05"), fin(2, "10-14", started = "10-12")), days = mapOf("09-01" to 10)).rounds[1]
        assertEquals(d("10-12"), second.from)
        assertEquals(d("10-14"), second.to)
        assertEquals(0L, second.ms)
    }

    @Test fun `진행 중 회차의 별점만 남겨도 회차가 있다`() {
        val rounds = rec(finishes = listOf(fin(1, "09-05", review = Review(8))), open = Review(6)).rounds
        assertEquals(2, rounds.size)
        assertEquals(Review(8), rounds[0].review)
        assertEquals(Review(6), rounds[1].review)
    }

    @Test fun `회차 번호는 저장하지 않고 순서로 센다`() {
        val rounds = rec(finishes = listOf(fin(7, "03-01"), fin(3, "05-01"), fin(9, "07-01"))).rounds
        assertEquals(listOf(1, 2, 3), rounds.map { it.nth })
        assertEquals(listOf(7L, 3L, 9L), rounds.map { it.finishId })
    }

    @Test fun `완독 뒤 합쳐 정확히 5분이면 진행 중 회차`() {
        assertEquals(2, rec(finishes = listOf(fin(1, "09-05")), days = mapOf("09-10" to 5)).rounds.size)
    }

    @Test fun `다시 읽기 전에 정확히 5분 읽었으면 이미 시작한 회차`() {
        val open = rec(State.READING, listOf(fin(1, "09-05")), mapOf("09-10" to 5), start = "09-20").rounds[1]
        assertEquals(d("09-10"), open.from)
        assertEquals(minutes(5), open.ms)
    }

    @Test fun `완독한 날 범위 - 다시 읽기로 시작해 안 읽은 회차는 시작일부터 오늘까지`() {
        val r = rec(finishes = listOf(fin(1, "09-01"), fin(2, "10-14", started = "10-12")))
        assertEquals(d("10-12") to d("10-15"), r.finishDayRange(r.rounds[1], d("10-15")))
    }

    @Test fun `완독한 날 범위 - 마지막으로 읽은 날부터 다음 회차 시작일까지`() {
        val r = rec(finishes = listOf(fin(1, "09-05"), fin(2, "10-10")), days = mapOf("09-01" to 10, "09-03" to 10, "10-01" to 10))
        assertEquals(d("09-03") to d("10-01"), r.finishDayRange(r.rounds[0], d("10-15")))
        assertEquals(d("10-01") to d("10-15"), r.finishDayRange(r.rounds[1], d("10-15")))
    }

    @Test fun `완독한 날 범위 - 같은 날 두 번 완독한 두 번째 회차는 그날부터`() {
        val r = rec(finishes = listOf(fin(1, "09-05"), fin(2, "09-05")), days = mapOf("09-01" to 10))
        assertEquals(d("09-05") to d("10-15"), r.finishDayRange(r.rounds[1], d("10-15")))
        // 첫 회차는 다음 회차(같은 날 하루)를 넘지 못한다
        assertEquals(d("09-01") to d("09-05"), r.finishDayRange(r.rounds[0], d("10-15")))
    }

    @Test fun `완독한 날 범위 - 첫 회차에 읽은 날이 없으면 아래 제한 없음, 위는 오늘을 넘지 않는다`() {
        val r = rec(finishes = listOf(fin(1, "09-05")))
        assertEquals(null to d("10-15"), r.finishDayRange(r.rounds[0], d("10-15")))
    }

    @Test fun `올해 읽은 책 - 합쳐 5분 미만이면 빠지지만 별점·감상을 남겼으면 보인다`() {
        assertTrue(rec(days = mapOf("09-01" to 2)).readYears.isEmpty())
        assertEquals(listOf(2026), rec(days = mapOf("09-01" to 2), open = Review(4)).readYears)
        assertEquals(listOf(2026), rec(days = mapOf("09-01" to 3, "09-02" to 3)).readYears)
    }

    @Test fun `읽은 시간 없이 감상만 있는 책(1_5_3 이전)은 올해 읽은 책에 없지만 지금 회차 감상은 남아 빼기에서 보인다`() {
        val r = rec(State.READING, open = Review(6, "예전 감상"))
        assertTrue(r.readYears.isEmpty())
        assertFalse(r.inYearBooks)
        assertEquals(Review(6, "예전 감상"), r.lastRound.review)
        // 상세의 별점 칸·`책 빼기` 가 별점·감상 메뉴를 여는 조건
        assertTrue(r.canReview)
        assertFalse(rec(State.READING, days = mapOf("09-01" to 4)).canReview)
        assertTrue(rec(State.READING, days = mapOf("09-01" to 5)).canReview)
    }

    @Test fun `다른 해 회차의 감상으로는 그해 짧게 연 것을 목록에 넣지 않는다`() {
        val r = BookRecord(
            book, State.PAST, false, listOf(BookShelf.Finish(1, LocalDate.parse("2025-05-10"), null, null, Review(8))),
            mapOf(LocalDate.parse("2025-05-01") to minutes(30), d("03-01") to minutes(2)),
        )
        assertEquals(listOf(2025), r.readYears)
    }

    @Test fun `시작일을 모르는 진행 중 회차는 책을 빼도 남는다`() {
        val r = BookRecord(book, State.PAST, false, listOf(fin(1, "09-05")), emptyMap(), null, null, openRound = true)
        assertEquals(2, r.rounds.size)
        assertFalse(r.lastRound.finished)
        assertNull(r.lastRound.from)
    }

    @Test fun `완독한 날 범위 - 다음 회차가 다시 읽기로 시작했으면 그날까지(처음 읽은 날이 더 늦어도)`() {
        val r = rec(finishes = listOf(fin(1, "09-05"), fin(2, "10-10", started = "09-10")), days = mapOf("09-01" to 10, "09-20" to 10))
        assertEquals(d("09-01") to d("09-10"), r.finishDayRange(r.rounds[0], d("10-15")))
        assertEquals(d("09-10"), r.rounds[1].begin)
        assertEquals(d("09-20"), r.rounds[1].from)
    }

    @Test fun `완독한 날로 받는 날 - 범위 안, 1900년 이후`() {
        val r = rec(finishes = listOf(fin(1, "09-05")))
        val round = r.rounds[0]
        assertTrue(r.canFinishOn(round, LocalDate.parse("1990-03-01"), d("10-15")))
        assertFalse(r.canFinishOn(round, LocalDate.parse("0206-01-01"), d("10-15")))
        assertFalse(r.canFinishOn(round, d("10-16"), d("10-15")))
    }

    @Test fun `다시 펴면 새 회차인가 - 마지막 회차가 완독으로 끝났을 때만`() {
        assertTrue(rec(finishes = listOf(fin(1, "09-05"))).reopenStartsRound)
        // 완독 뒤 5분 넘게 읽어 이미 진행 중
        assertFalse(rec(finishes = listOf(fin(1, "09-05")), days = mapOf("10-01" to 6)).reopenStartsRound)
        // 감상을 남긴 진행 중 회차
        assertFalse(rec(finishes = listOf(fin(1, "09-05")), open = Review(6)).reopenStartsRound)
        // 완독한 적이 없는 책
        assertFalse(rec(days = mapOf("10-01" to 6)).reopenStartsRound)
    }

    @Test fun `고친 읽기 시작한 날 - 기간만 그날부터, 읽은 시간·기록된 시작 날은 그대로`() {
        val open = rec(State.READING, days = mapOf("10-08" to 10, "10-09" to 20), began = "09-20").rounds.single()
        assertEquals(d("09-20"), open.from)
        assertEquals(d("10-08"), open.recordedFrom)
        assertEquals(d("10-08"), open.begin)
        assertEquals(minutes(30), open.ms)
        val done = rec(finishes = listOf(fin(1, "10-10", began = "09-01")), days = mapOf("10-08" to 10)).rounds.single()
        assertEquals(d("09-01"), done.from)
        assertEquals(d("10-10"), done.to)
    }

    @Test fun `고친 읽기 시작한 날이 그 회차 범위 밖이면(앞 완독보다 앞, 기록된 시작 뒤) 기록된 날`() {
        // 앞 회차 완독(09-05)보다 앞
        assertEquals(d("10-01"), rec(State.READING, listOf(fin(1, "09-05")), mapOf("09-01" to 10, "10-01" to 10), began = "09-01").rounds[1].from)
        // 기록된 시작 날보다 뒤
        assertEquals(d("10-01"), rec(State.READING, days = mapOf("10-01" to 10), began = "10-05").rounds.single().from)
        // 앞 완독한 날과 같은 날은 받는다
        assertEquals(d("09-05"), rec(State.READING, listOf(fin(1, "09-05")), mapOf("09-01" to 10, "10-01" to 10), began = "09-05").rounds[1].from)
    }

    @Test fun `고친 읽기 시작한 날 - 읽은 기록이 없는 회차도 그날부터`() {
        val r = rec(State.READING, listOf(fin(1, "09-05")), mapOf("09-01" to 10), start = "10-01", began = "09-20").rounds[1]
        assertEquals(d("09-20"), r.from)
        assertEquals(d("10-01"), r.recordedFrom)
    }

    @Test fun `읽기 시작한 날 범위 - 앞 회차 완독한 날부터 기록된 시작 날까지(첫 회차는 아래 제한 없음)`() {
        val r = rec(State.READING, listOf(fin(1, "09-05")), mapOf("09-01" to 10, "10-01" to 10))
        assertEquals(null to d("09-01"), r.startDayRange(r.rounds[0], d("10-15")))
        assertEquals(d("09-05") to d("10-01"), r.startDayRange(r.rounds[1], d("10-15")))
        // 고친 뒤에도 범위는 기록된 시작 날까지(더 앞으로도, 다시 뒤로도 고칠 수 있게)
        val moved = rec(State.READING, listOf(fin(1, "09-05")), mapOf("09-01" to 10, "10-01" to 10), began = "09-10")
        assertEquals(d("09-05") to d("10-01"), moved.startDayRange(moved.rounds[1], d("10-15")))
    }

    @Test fun `읽기 시작한 날 범위 - 읽은 기록이 없으면 완독한 날(함께 고르는 날)·오늘까지`() {
        val r = rec(finishes = listOf(fin(1, "09-05")))
        assertEquals(null to d("09-05"), r.startDayRange(r.rounds[0], d("10-15")))
        assertEquals(null to d("08-30"), r.startDayRange(r.rounds[0], d("10-15"), to = d("08-30")))
        val open = rec(State.READING)
        assertEquals(null to d("10-15"), open.startDayRange(open.rounds.single(), d("10-15")))
    }

    @Test fun `읽기 시작한 날로 받는 날 - 범위 안, 1900년 이후`() {
        val r = rec(State.READING, listOf(fin(1, "09-05")), mapOf("09-01" to 10, "10-01" to 10))
        val open = r.rounds[1]
        assertTrue(r.canStartOn(open, d("09-05"), d("10-15")))
        assertTrue(r.canStartOn(open, d("10-01"), d("10-15")))
        assertFalse(r.canStartOn(open, d("09-04"), d("10-15")))
        assertFalse(r.canStartOn(open, d("10-02"), d("10-15")))
        assertFalse(rec(State.READING).let { it.canStartOn(it.rounds.single(), LocalDate.parse("0206-01-01"), d("10-15")) })
    }

    @Test fun `완독한 날 범위 - 다음 회차의 고친 읽기 시작한 날을 넘지 못하고, 그 회차의 고친 시작일보다 앞으로도 못 간다`() {
        val r = rec(State.READING, listOf(fin(1, "09-05")), mapOf("09-01" to 10, "10-01" to 10), began = "09-20")
        assertEquals(d("09-01") to d("09-20"), r.finishDayRange(r.rounds[0], d("10-15")))
        val none = rec(finishes = listOf(fin(1, "10-10", began = "09-01")))
        assertEquals(d("09-01") to d("10-15"), none.finishDayRange(none.rounds[0], d("10-15")))
    }
}
