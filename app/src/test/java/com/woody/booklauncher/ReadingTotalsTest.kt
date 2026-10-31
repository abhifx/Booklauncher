package com.woody.booklauncher

import com.woody.booklauncher.ReadingLog.Mark
import com.woody.booklauncher.ReadingLog.Open
import com.woody.booklauncher.ReadingLog.Span
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 읽는 화면 구간을 칸으로 나누기([ReadingLog.totalsOf]: 구간 안에서 다른 책을 펴면 거기서 나눈다)와
 * 책 id 정하기([ReadingLog.withBooks]: 모르는 칸은 [bookFor], 지운 책의 id 는 지운 날 뒤면 다시 정한다).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadingTotalsTest {
    private val zone = ZoneOffset.UTC
    private val day = LocalDate.parse("2026-10-16")
    private fun at(h: Int, m: Int = 0) = day.atStartOfDay(zone).toInstant().toEpochMilli() + (h * 60L + m) * 60_000
    private val min = 60_000L

    private fun ReadingDays.minutes() = getValue(day).mapKeys { "${it.key.title}|${it.key.book}" }.mapValues { it.value / min }

    @Test fun `보인 동안만 세는 앱 - Libby 는 적힌 것이 없으면 안 셈, KOReader 는 다 셈`() {
        val opens = listOf(Open(at(9), "org.koreader.launcher", "데미안", "b-a"), Open(at(9), "com.overdrive.mobile.android.libby", "싯다르타", "b-b"))
        val spans = listOf(
            Span("org.koreader.launcher", at(10), at(11), hosted = true),
            Span("com.overdrive.mobile.android.libby", at(12), at(13), hosted = true),
        )
        assertEquals(mapOf("데미안|b-a" to 60L), ReadingLog.totalsOf(spans, opens, zone).minutes())
    }

    @Test fun `KOReader 책을 닫고 연 사이(파일 목록)는 빼고 센다`() {
        val opens = listOf(Open(at(9), "org.koreader.launcher", "데미안", "b-a"), Open(at(10, 40), "org.koreader.launcher", "싯다르타", "b-b"))
        val marks = listOf(
            Mark(at(10), "org.koreader.launcher", true),
            Mark(at(10, 20), "org.koreader.launcher", false),
            Mark(at(10, 40), "org.koreader.launcher", true),
        )
        val days = ReadingLog.totalsOf(listOf(Span("org.koreader.launcher", at(10), at(11), hosted = true)), opens, zone, marks)
        assertEquals(mapOf("데미안|b-a" to 20L, "싯다르타|b-b" to 20L), days.minutes())
    }

    @Test fun `구간 안에서 다른 책을 펴면 그때 나눈다`() {
        val opens = listOf(Open(at(9), "pkg", "데미안", "b-a"), Open(at(10, 30), "pkg", "싯다르타", "b-b"))
        val days = ReadingLog.totalsOf(listOf(Span("pkg", at(10), at(11))), opens, zone)
        assertEquals(mapOf("데미안|b-a" to 30L, "싯다르타|b-b" to 30L), days.minutes())
    }

    @Test fun `구간이 닫힐 때까지 알게 된 책(서재로 돌아와서야 아는 앱)은 그 구간 전체`() {
        // 읽는 화면을 연 때(10시)로 적힌다
        val opens = listOf(Open(at(10), "pkg", "데미안", "b-a"))
        val days = ReadingLog.totalsOf(listOf(Span("pkg", at(10), at(11))), opens, zone)
        assertEquals(mapOf("데미안|b-a" to 60L), days.minutes())
    }

    @Test fun `다른 앱에서 편 책으로는 나누지 않는다`() {
        val opens = listOf(Open(at(9), "pkg", "데미안", "b-a"), Open(at(10, 30), "other", "싯다르타", "b-b"))
        val days = ReadingLog.totalsOf(listOf(Span("pkg", at(10), at(11))), opens, zone)
        assertEquals(mapOf("데미안|b-a" to 60L), days.minutes())
    }

    @Test fun `모르는 칸은 그때의 목록으로 정하고, 지운 책의 id 는 지운 날 뒤면 다시 정한다`() {
        val gone = PastBook(ShelfBook("p-gone", "데미안", "", null, 0), at(0) - 24 * 3_600_000L, hidden = true)
        val shelf = listOf(ShelfBook("b-new", "싯다르타", "", null, 0))
        val days = mapOf(day to mapOf(
            ReadKey("pkg", "데미안", "p-gone") to 10 * min,
            ReadKey("pkg", "싯다르타") to 5 * min,
        ))
        val filled = ReadingLog.withBooks(days, shelf, listOf(gone))
        assertEquals(mapOf("데미안|" to 10L, "싯다르타|b-new" to 5L), filled.minutes())
        // 지운 날까지는 그 책 그대로
        val before = ReadingLog.withBooks(mapOf(day.minusDays(1) to mapOf(ReadKey("pkg", "데미안", "p-gone") to min)), shelf, listOf(gone))
        assertEquals(setOf(ReadKey("pkg", "데미안", "p-gone")), before.getValue(day.minusDays(1)).keys)
    }

    @Test fun `자동 추가가 꺼진 뒤(제목 없는 편 책) 읽은 시간은 책 없이 시간만`() {
        // 9시에 데미안을 폈고 10시 30분에 서비스가 꺼짐(제목 없는 편 책)
        val opens = listOf(Open(at(9), "pkg", "데미안", "b-a"), Open(at(10, 30), "pkg", ""))
        val days = ReadingLog.totalsOf(listOf(Span("pkg", at(10), at(11))), opens, zone)
        assertEquals(mapOf("데미안|b-a" to 30L, "|" to 30L), days.minutes())
        // 하루 합계에는 모두 들어간다
        assertEquals(60 * min, days.total(day))
    }

    @Test fun `구간 끝과 같은 때 편 책은 그 구간에 붙이지 않는다`() {
        val opens = listOf(Open(at(9), "pkg", "데미안", "b-a"), Open(at(11), "pkg", "싯다르타", "b-b"))
        val days = ReadingLog.totalsOf(listOf(Span("pkg", at(10), at(11))), opens, zone)
        assertEquals(mapOf("데미안|b-a" to 60L), days.minutes())
    }

    @Test fun `같은 때 '책 모름' 뒤에 적은 책이 이긴다(제목 없는 칸을 눌러 들어간 뒤 메뉴의 제목을 읽음)`() {
        val opens = listOf(Open(at(9), "pkg", "데미안", "b-a"), Open(at(10), "pkg", ""), Open(at(10), "pkg", "싯다르타", "b-b"))
        val days = ReadingLog.totalsOf(listOf(Span("pkg", at(10), at(11))), opens, zone)
        assertEquals(mapOf("싯다르타|b-b" to 60L), days.minutes())
    }

    @Test fun `자정을 넘긴 구간은 하루씩 나눈다`() {
        val opens = listOf(Open(at(9), "pkg", "데미안", "b-a"))
        val start = at(23, 50)
        val days = ReadingLog.totalsOf(listOf(Span("pkg", start, start + 30 * min)), opens, zone)
        assertEquals(10 * min, days.getValue(day)[ReadKey("pkg", "데미안", "b-a")])
        assertEquals(20 * min, days.getValue(day.plusDays(1))[ReadKey("pkg", "데미안", "b-a")])
    }
}
