package com.woody.booklauncher

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

/** 카테고리 저장([Categories])과 통계 거르기([StatsFilter]) */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CategoriesTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @After fun tearDown() {
        Categories.clearCache()
        BookShelf.clearCaches()
        ReadingLog.clearCache()
        Db.closeForTest()
        Storage.of(app).getDatabasePath("pebble.db").parentFile?.deleteRecursively()
    }

    @Test fun `만들기·이름 바꾸기·순서·지우기와 책에 붙이기`() {
        val novel = Categories.add(app, " 소설 ")!!
        val essay = Categories.add(app, "에세이")!!
        assertEquals("소설", novel.name)
        // 같은 이름은 새로 만들지 않는다, 빈 이름은 만들지 않는다
        assertEquals(novel.id, Categories.add(app, "소설")?.id)
        assertNull(Categories.add(app, "  "))
        assertEquals(listOf("소설", "에세이"), Categories.list(app).map { it.name })

        Categories.move(app, essay.id, -1)
        assertEquals(listOf("에세이", "소설"), Categories.list(app).map { it.name })
        assertFalse(Categories.rename(app, essay.id, "소설"))
        assertTrue(Categories.rename(app, essay.id, "산문"))
        // 같은 이름은 저장할 때처럼 다듬어 본다(30자로 자른 뒤), 자기 이름은 빼고
        val long = Categories.add(app, "가".repeat(30))!!
        assertTrue(Categories.sameName(app, " " + "가".repeat(35)))
        assertFalse(Categories.sameName(app, "가".repeat(30), except = long.id))
        Categories.delete(app, long.id)

        Categories.assign(app, "b-1", novel.id)
        Categories.assign(app, "b-2", essay.id)
        assertEquals("소설", Categories.of(app, "b-1")?.name)
        Categories.assign(app, "b-1", essay.id)
        assertEquals("산문", Categories.of(app, "b-1")?.name)
        Categories.assign(app, "b-1", null)
        assertNull(Categories.of(app, "b-1"))

        // 지우면 그 책은 분류 없음, DB 에서 읽어도 같다
        Categories.delete(app, essay.id)
        Categories.clearCache()
        assertNull(Categories.of(app, "b-2"))
        assertEquals(listOf("소설"), Categories.list(app).map { it.name })
    }

    private val day = LocalDate.of(2026, 11, 4)
    private val days: ReadingDays = mapOf(day to mapOf(
        ReadKey("p", "소설책", "b-1") to 10L,
        ReadKey("p", "만화책", "b-2") to 20L,
        ReadKey("p", "없는 책", "b-3") to 40L,
        ReadKey("p", "제목만", "") to 80L,
        ReadKey("p") to 160L,
    ))
    private val cats = listOf(Category(1, "소설", false), Category(2, "만화", true))
    private val books = mapOf("b-1" to 1L, "b-2" to 2L)

    @Test fun `전체는 통계에서 뺀 카테고리의 책만 빼고 모르는 시간까지 센다`() {
        val f = StatsFilter(emptySet(), cats, books)
        assertEquals(10L + 40 + 80 + 160, f.days(days).total(day))
        assertTrue(f.keepsBook("b-1"))
        assertFalse(f.keepsBook("b-2"))
        assertTrue(f.keepsBook("t-제목만"))
    }

    @Test fun `거를 것이 없으면 기록을 그대로 쓴다`() {
        assertSame(days, StatsFilter(emptySet(), listOf(Category(1, "소설", false)), books).days(days))
        assertSame(days, StatsFilter(emptySet(), emptyList(), emptyMap()).days(days))
    }

    @Test fun `고른 카테고리만, 분류 없음은 카테고리 없는 책과 제목만 아는 시간`() {
        assertEquals(10L, StatsFilter(setOf(1L), cats, books).days(days).total(day))
        // 통계에서 뺀 카테고리도 직접 고르면 보인다
        assertEquals(20L, StatsFilter(setOf(2L), cats, books).days(days).total(day))
        assertEquals(40L + 80, StatsFilter(setOf(Categories.NONE), cats, books).days(days).total(day))
        // 통계에서 빼지 않은 것을 모두 고르면 전체(모르는 시간까지)
        assertEquals(10L + 40 + 80 + 160, StatsFilter(setOf(1L, Categories.NONE), cats, books).days(days).total(day))
        // 날은 그대로 둔다(기록이 있는 날)
        assertEquals(setOf(day), StatsFilter(setOf(1L), cats, books).days(days).keys)
    }

    @Test fun `없어진 카테고리를 고른 것은 무시하고 남는 것이 없으면 전체`() {
        val f = StatsFilter(setOf(9L), cats, books)
        assertTrue(f.all)
        assertEquals(setOf(1L), StatsFilter(setOf(1L, 9L), cats, books).picked)
    }

    @Test fun `전체를 한 번 더 누르면 켜는 것은 통계에서 빼지 않은 카테고리와 분류 없음, 꼭 그만큼 고르면 전체`() {
        val three = cats + Category(3, "에세이", false)
        val f = StatsFilter(emptySet(), three, books)
        assertEquals(setOf(1L, 3L, Categories.NONE), f.selectable)
        assertTrue(f.with(setOf(1L, 3L, Categories.NONE)).all)
        // 하나 빼면 그대로(모르는 시간은 빠진다)
        assertEquals(setOf(1L, Categories.NONE), f.with(setOf(1L, Categories.NONE)).picked)
        assertEquals(10L + 40 + 80, f.with(setOf(1L, Categories.NONE)).days(days).total(day))
        // 통계에서 뺀 카테고리까지 켜면 전체와 다른 보기
        assertEquals(setOf(1L, 2L, 3L, Categories.NONE), f.with(setOf(1L, 2L, 3L, Categories.NONE)).picked)
        // 없어진 카테고리를 빼고 남은 것이 꼭 그만큼이면 전체
        assertTrue(f.with(setOf(1L, 3L, 9L, Categories.NONE)).all)
    }
}
