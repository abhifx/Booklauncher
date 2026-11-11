package com.woody.booklauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 읽은 시간 한 칸을 붙일 책([bookFor]): 같은 제목의 ① 그날이 나간 날(지운 책은 지운 날) 이전인 나간 책 중 먼저 나간 책
 * ② 읽고 있는 책(목록 앞쪽) ③ 지우지 않은 나간 책 중 가장 최근 ④ 없으면 null. 고친 제목은 앱이 보여 준 제목으로도 찾는다.
 */
class BookForTest {
    private val zone = ZoneOffset.UTC
    private fun d(s: String) = LocalDate.parse("2026-$s")
    private fun noon(s: String) = d(s).atStartOfDay(zone).toInstant().toEpochMilli() + 12 * 3_600_000L
    private fun book(id: String, title: String, seen: String = "") = ShelfBook(id, title, "", null, 0, seenTitle = seen)
    private fun past(id: String, title: String, left: String, hidden: Boolean = false, seen: String = "") =
        PastBook(book(id, title, seen), noon(left), hidden)

    private fun of(title: String, day: String, shelf: List<ShelfBook> = emptyList(), past: List<PastBook> = emptyList(), first: Map<String, String> = emptyMap()) =
        bookFor(title, d(day), shelf, past, first.mapValues { d(it.value) }, zone)

    @Test fun `읽고 있는 책`() = assertEquals("b-1", of("데미안", "10-01", shelf = listOf(book("b-1", "데미안"))))

    @Test fun `제목을 고친 책은 앱이 보여 준 제목으로`() =
        assertEquals("b-1", of("데미안", "10-01", shelf = listOf(book("b-1", "헤세의 소설", seen = "데미안"))))

    @Test fun `같은 제목이 둘이면 읽고 있는 책 중 목록 앞쪽`() =
        assertEquals("b-front", of("데미안", "10-01", shelf = listOf(book("b-front", "데미안"), book("b-back", "데미안"))))

    @Test fun `읽고 있는 책이 없으면 지우지 않은 나간 책 중 가장 최근`() =
        assertEquals("p-new", of("데미안", "10-01", past = listOf(past("p-old", "데미안", "03-01"), past("p-new", "데미안", "05-01"))))

    @Test fun `나간 날까지 읽은 시간은 같은 제목의 새 책이 있어도 나간 책에`() {
        val shelf = listOf(book("b-new", "데미안"))
        val past = listOf(past("p-a", "데미안", "01-20"))
        assertEquals("p-a", of("데미안", "01-05", shelf, past))
        // 나간 날 그날까지
        assertEquals("p-a", of("데미안", "01-20", shelf, past))
        assertEquals("b-new", of("데미안", "01-21", shelf, past))
    }

    @Test fun `나간 날 이전인 나간 책이 여럿이면 먼저 나간 책`() {
        val past = listOf(past("p-a", "데미안", "01-20"), past("p-b", "데미안", "05-01"))
        assertEquals("p-a", of("데미안", "01-05", past = past))
        assertEquals("p-b", of("데미안", "03-01", past = past))
        assertEquals("p-b", of("데미안", "06-01", past = past))
    }

    @Test fun `지운 책에는 지운 날까지만, 그 뒤는 지우지 않은 책이나 목록에 없던 책`() {
        val past = listOf(past("p-gone", "인간 실격", "03-10", hidden = true))
        assertEquals("p-gone", of("인간 실격", "03-10", past = past))
        assertNull(of("인간 실격", "04-01", past = past))
        assertEquals("b-new", of("인간 실격", "04-01", shelf = listOf(book("b-new", "인간 실격")), past = past))
        assertEquals("p-gone", of("인간 실격", "03-01", shelf = listOf(book("b-new", "인간 실격")), past = past))
    }

    @Test fun `앱이 보여 준 제목만 같은 나간 책도 나간 날까지는 그 책`() =
        assertEquals("p-a", of("데미안", "01-05", shelf = listOf(book("b-new", "데미안")), past = listOf(past("p-a", "헤세의 소설", "01-20", seen = "데미안"))))

    @Test fun `같은 제목이 어디에도 없으면 null`() = assertNull(of("싯다르타", "10-01", shelf = listOf(book("b-1", "데미안"))))

    @Test fun `그날 뒤에야 처음 읽은 책(아직 없던 책)에는 붙이지 않는다`() {
        // A 는 1월 20일에 나감, B 는 4월에 처음 읽고 5월 1일에 나감: 3월의 모르는 시간은 B 가 아니라 A(지우지 않은 나간 책 중 그날 있던 것)
        val past = listOf(past("p-a", "데미안", "01-20"), past("p-b", "데미안", "05-01"))
        assertEquals("p-a", of("데미안", "03-01", past = past, first = mapOf("p-a" to "01-05", "p-b" to "04-02")))
        assertEquals("p-b", of("데미안", "04-10", past = past, first = mapOf("p-a" to "01-05", "p-b" to "04-02")))
        // 읽고 있는 책도 처음 읽은 날 전이면 아니다
        assertNull(of("싯다르타", "03-01", shelf = listOf(book("b-1", "싯다르타")), first = mapOf("b-1" to "10-01")))
        // 처음 읽은 날을 모르면(그 id 가 붙은 시간이 아직 없으면) 있던 책으로 본다
        assertEquals("b-1", of("싯다르타", "03-01", shelf = listOf(book("b-1", "싯다르타"))))
    }

    @Test fun `처음 읽은 날은 그 id 가 붙은 가장 이른 날`() {
        val days = mapOf(
            d("05-01") to mapOf(ReadKey("pkg", "데미안", "b-1") to 1L, ReadKey("pkg", "데미안") to 1L),
            d("03-01") to mapOf(ReadKey("pkg", "데미안", "b-1") to 1L),
            d("04-01") to mapOf(ReadKey("pkg", "싯다르타", "b-2") to 1L),
        )
        assertEquals(mapOf("b-1" to d("03-01"), "b-2" to d("04-01")), days.firstReadDays())
    }

    @Test fun `제목은 전부 같아야 같은 책(부제·공백·기호는 무시)`() {
        val shelf = listOf(book("b-1", "데미안"))
        assertNull(of("데미안 2", "10-01", shelf = shelf))
        assertNull(of("데미", "10-01", shelf = shelf))
        assertEquals("b-1", of("데미안: 에밀 싱클레어의 청춘 이야기", "10-01", shelf = shelf))
        assertEquals("b-1", of(" 데 미 안 ", "10-01", shelf = shelf))
    }

    @Test fun `+ 와 # 은 제목을 가르고, 기호만 있는 제목끼리는 원래 글자로`() {
        assertTrue(sameTitle("C++", "c++"))
        assertFalse(sameTitle("C++", "C#"))
        assertFalse(sameTitle("C++", "C"))
        assertFalse(sameTitle("!!", "??"))
        assertTrue(sameTitle(" !! ", "!!"))
        // `:` 앞이 비면 부제로 나누지 않는다
        assertFalse(sameTitle(":", "::"))
        assertFalse(sameTitle(": 부제", ": 다른 부제"))
        assertTrue(sameTitle(": 부제", ":부제"))
    }

    @Test fun `부제는 한쪽에만 있으면 무시하고 둘 다 있는데 다르면 다른 책`() {
        assertTrue(sameTitle("달러구트 꿈 백화점", "달러구트 꿈 백화점 : 잠들어야만 입장 가능합니다"))
        assertTrue(sameTitle("해리포터: 1권", "해리포터 : 1 권"))
        assertFalse(sameTitle("해리포터: 1권", "해리포터: 2권"))
        val shelf = listOf(book("b-1", "해리포터: 1권"), book("b-2", "해리포터: 2권"))
        assertEquals("b-2", of("해리포터: 2권", "10-01", shelf = shelf))
        // 앱이 보여 준 제목(제목을 고친 책)도 같은 규칙
        assertEquals("b-3", of("시리즈: 2권", "10-01", shelf = listOf(book("b-4", "고친 1권", seen = "시리즈: 1권"), book("b-3", "고친 2권", seen = "시리즈: 2권"))))
    }
}
