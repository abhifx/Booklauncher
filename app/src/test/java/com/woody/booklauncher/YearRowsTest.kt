package com.woody.booklauncher

import com.woody.booklauncher.BookRecord.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** 올해 읽은 책 목록([YearBooksActivity.yearRows]): 그해 합쳐 5분 이상 읽었거나 그해 완독한 책, 그해 마지막으로 읽은(완독한) 날 순 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class YearRowsTest {
    private fun d(s: String) = LocalDate.parse(s)

    private fun rec(id: String, days: Map<String, Int> = emptyMap(), finishes: List<String> = emptyList()) = BookRecord(
        ShelfBook(id, id, "", null, 0), State.PAST, false,
        finishes.mapIndexed { i, f -> BookShelf.Finish(i.toLong(), d(f), null, null, null) },
        days.entries.associate { d(it.key) to it.value * 60_000L },
    )

    private fun ids(records: List<BookRecord>, year: Int) = YearBooksActivity.yearRows(records, year).map { it.record.book.id }

    @Test fun `그해 합쳐 5분 미만이면 빠지고, 그해 완독했으면 읽은 시간이 없어도 들어간다`() {
        val records = listOf(
            rec("short", mapOf("2026-03-01" to 4)),
            rec("enough", mapOf("2026-03-01" to 3, "2026-03-02" to 3)),
            rec("done", finishes = listOf("2026-02-01")),
        )
        assertEquals(setOf("enough", "done"), ids(records, 2026).toSet())
    }

    @Test fun `그해 마지막으로 읽은 날(조금이라도) 또는 완독한 날 중 늦은 날 순`() {
        val records = listOf(
            rec("sept", mapOf("2026-09-01" to 30)),
            // 하루 5분이 안 되는 날이 그해 마지막 날이어도 정렬에 센다
            rec("oct-short", mapOf("2026-03-01" to 10, "2026-10-01" to 2)),
            rec("done-nov", mapOf("2026-03-01" to 10), finishes = listOf("2026-11-01")),
        )
        assertEquals(listOf("done-nov", "oct-short", "sept"), ids(records, 2026))
    }

    @Test fun `같은 날이면 오래 읽은 순`() {
        val records = listOf(rec("less", mapOf("2026-05-01" to 10)), rec("more", mapOf("2026-05-01" to 40)))
        assertEquals(listOf("more", "less"), ids(records, 2026))
    }

    @Test fun `그해 완독했는가로 완독 거르기를 한다`() {
        val rows = YearBooksActivity.yearRows(listOf(rec("done", mapOf("2025-12-01" to 30), listOf("2026-01-05"))), 2026)
        assertTrue(rows.single().doneInYear)
        assertEquals(false, YearBooksActivity.yearRows(listOf(rec("done", mapOf("2025-12-01" to 30), listOf("2026-01-05"))), 2025).single().doneInYear)
    }

    @Test fun `고를 수 있는 해는 책이 보이는 해·완독한 해와 올해, 최근 순`() {
        val records = listOf(rec("a", mapOf("2024-05-01" to 10)), rec("b", finishes = listOf("2023-01-01")), rec("c", mapOf("2022-05-01" to 2)))
        assertEquals(listOf(2026, 2024, 2023), YearBooksActivity.years(records, d("2026-10-15")))
    }

    @Test fun `같은 날이면 그해 읽은 시간으로 비교한다(다른 해 시간은 빼고)`() {
        val records = listOf(
            rec("old-long", mapOf("2025-03-01" to 6000, "2026-05-01" to 10)),
            rec("this-year", mapOf("2026-05-01" to 40)),
        )
        assertEquals(listOf("this-year", "old-long"), ids(records, 2026))
    }
}
