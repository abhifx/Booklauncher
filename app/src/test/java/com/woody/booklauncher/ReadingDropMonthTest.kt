package com.woody.booklauncher

import com.woody.booklauncher.Db.rows
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth

/** 기기 정보의 `읽은 시간 지우기`: 달마다 저장한 시간([ReadingLog.savedMonths])과 한 달 지우기([ReadingLog.dropMonth]) */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadingDropMonthTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val db get() = Db.get(app)
    private val now = YearMonth.now()
    private val prev = now.minusMonths(1)
    private val prev2 = now.minusMonths(2)

    @Before fun setUp() {
        BookShelf.clearCaches()
        ReadingLog.clearCache()
    }

    @After fun tearDown() {
        BookShelf.clearCaches()
        ReadingLog.clearCache()
        Db.closeForTest()
        Storage.of(app).getDatabasePath("pebble.db").parentFile?.deleteRecursively()
    }

    private fun read(day: LocalDate, min: Int, book: String = "", title: String = "") =
        db.execSQL("INSERT INTO reading_day VALUES (${day.toEpochDay()}, 'pkg', '$title', '$book', ${min * 60_000L})")

    private fun meta(through: LocalDate, since: LocalDate) =
        db.execSQL("INSERT INTO reading_meta VALUES ('through', '$through'), ('since', '$since')")

    private fun through() = db.rows("SELECT value FROM reading_meta WHERE name = 'through'") { it.getString(0) }.single()

    private fun days() = db.rows("SELECT day FROM reading_day ORDER BY day") { LocalDate.ofEpochDay(it.getLong(0)) }

    @Test fun `달마다 저장한 시간을 더한다`() {
        read(prev2.atDay(3), 20)
        read(prev.atDay(4), 30)
        read(prev.atDay(4), 10, book = "b1", title = "데미안")
        read(prev.atDay(9), 5)
        meta(prev.atEndOfMonth(), prev2.atDay(1))
        assertEquals(mapOf(prev2 to 20 * 60_000L, prev to 45 * 60_000L), ReadingLog.savedMonths(app))
    }

    @Test fun `그달 줄만 책에 붙은 것까지 지우고 마지막으로 센 날을 그달 끝으로 올린다`() {
        read(prev2.atEndOfMonth(), 20)
        read(prev.atDay(1), 30)
        read(prev.atDay(4), 10, book = "b1", title = "데미안")
        read(prev.atEndOfMonth(), 15)
        val through = prev.atDay(4)
        meta(through, prev2.atDay(1))
        ReadingLog.savedMonths(app)  // 메모리 사본을 채워 두고, 지운 뒤 비우는지 본다
        assertEquals(3, ReadingLog.dropMonth(app, prev))
        assertEquals(listOf(prev2.atEndOfMonth()), days())
        assertEquals(minOf(prev.atEndOfMonth(), LocalDate.now().minusDays(1)).toString(), through())
        assertEquals(mapOf(prev2 to 20 * 60_000L), ReadingLog.savedMonths(app))
    }

    @Test fun `그달 앞에서 센 날이 멈췄으면 그 사이 날을 남겨 둔다`() {
        read(prev2.atDay(10), 20)
        meta(prev2.atDay(20), prev2.atDay(1))
        assertEquals(0, ReadingLog.dropMonth(app, now))
        assertEquals(prev2.atDay(20).toString(), through())
        assertEquals(listOf(prev2.atDay(10)), days())
    }
}
