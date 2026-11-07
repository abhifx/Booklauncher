package com.woody.pebbledesk

import com.woody.pebbledesk.Db.rows
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/**
 * 회차 하나만 지우기([BookShelf.forgetRound]·[BookRecord.dropOf]): 지운 회차의 완독·별점·감상·읽은 시간이 그 책에서 빠지고 남은 회차는 그대로.
 * 완독한 책을 다시 펴 2회가 시작된 뒤 그 2회만 지우려는 경우(사용자 제보)가 첫 시험.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoundForgetTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val db get() = Db.get(app)
    private val pkg = "com.example.reader"

    @Before fun setUp() = BookShelf.clearCaches()

    @After fun tearDown() {
        BookShelf.clearCaches()
        ReadingLog.clearCache()
        Db.closeForTest()
        Storage.of(app).getDatabasePath("pebble.db").parentFile?.deleteRecursively()
    }

    private fun d(s: String) = LocalDate.parse("2026-$s")
    private fun noon(day: LocalDate) = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 12 * 3_600_000L

    private fun past(id: String = "b-1", title: String = "데미안") =
        db.execSQL("INSERT INTO past (pos, id, title, author, app, updated_at, progress, seen_title, left_at, hidden) VALUES (0, '$id', '$title', '', NULL, 0, 0, '', ${noon(d("01-01"))}, 0)")

    private fun finish(id: Long, day: LocalDate, stars: Int? = null, book: String = "b-1") =
        db.execSQL("INSERT INTO finish (id, uid, book_id, day, at, stars, text) VALUES ($id, 'u$id', '$book', ${day.toEpochDay()}, ${noon(day)}, $stars, ${if (stars == null) "NULL" else "'감상'"})")

    /** 날마다 b-1 을 읽은 분 */
    private fun record(vararg reads: Pair<LocalDate, Int>, id: String = "b-1"): BookRecord {
        val days = reads.groupBy({ it.first }, { it.second }).mapValues { (_, m) -> mapOf(ReadKey(pkg, "데미안", id) to m.sum() * 60_000L) }
        return BookRecord.all(app, days).single { it.book.id == id }
    }

    private fun forget(r: BookRecord, index: Int, today: LocalDate): Boolean {
        val round = r.rounds[index]
        return BookShelf.forgetRound(app, r.book.id, round.finishId, r.dropOf(round, today))
    }

    @Test fun `완독한 책을 다시 펴 시작된 2회만 지우면 1회 완독은 그대로 남는다`() {
        val today = LocalDate.now()
        val done = today.minusDays(3)
        past()
        finish(1, done, stars = 8)
        // 다시 펴 그날 2회가 시작됐고(round_start) 그 회차에 감상도 남겼다가 책을 뺐다. 2회에 10분 읽었다(5분이 넘어 표시를 지워도 회차가 남을 시간).
        db.execSQL("INSERT INTO round_start VALUES ('b-1', ${today.toEpochDay()})")
        db.execSQL("INSERT INTO review VALUES ('b-1', 6, '다시', 0)")
        BookShelf.clearCaches()
        val reads = arrayOf(done.minusDays(1) to 60, done to 30, today to 10)
        val before = record(*reads)
        assertEquals(2, before.rounds.size)

        assertTrue(forget(before, 1, today))
        val after = record(*reads)
        val only = after.rounds.single()
        assertTrue(only.finished)
        assertEquals(1L, only.finishId)
        assertEquals(90 * 60_000L, only.ms)
        assertEquals(8, only.review?.halves)
        assertFalse(after.hidden)
        // 지운 회차의 시간은 이 책의 기록에서 빠진다(올해 읽은 책·결산에서도)
        assertNull(after.dayMs[today])
        assertTrue(BookShelf.roundStarts(app).isEmpty())
        assertTrue(BookShelf.reviews(app).isEmpty())
        // 다시 펴면 또 새 회차가 시작한다
        assertTrue(after.reopenStartsRound)
        // DB 에서 다시 읽어도 같다
        BookShelf.clearCaches()
        assertEquals(1, record(*reads).rounds.size)
        // 뺀 날: 1회 완독한 날 다음 날부터 지운 날까지
        assertEquals(listOf(done.toEpochDay() to today.toEpochDay()), db.rows("SELECT after_day, until_day FROM round_drop") { it.getLong(0) to it.getLong(1) })
    }

    @Test fun `가운데 완독 회차를 지우면 그 회차만 빠지고 앞뒤 회차의 시간은 그대로`() {
        past()
        finish(1, d("03-10"))
        finish(2, d("06-10"), stars = 10)
        finish(3, d("09-10"))
        BookShelf.clearCaches()
        val reads = arrayOf(d("03-01") to 60, d("06-01") to 40, d("06-10") to 5, d("09-01") to 30)
        val before = record(*reads)
        assertEquals(listOf(60, 45, 30).map { it * 60_000L }, before.rounds.map { it.ms })

        assertTrue(forget(before, 1, d("11-04")))
        val after = record(*reads)
        assertEquals(listOf(1, 2), after.rounds.map { it.nth })
        assertEquals(listOf(60, 30).map { it * 60_000L }, after.rounds.map { it.ms })
        assertEquals(listOf(d("03-01"), d("09-01")), after.rounds.map { it.from })
        assertEquals(listOf(1L, 3L), after.rounds.map { it.finishId })
        assertEquals(listOf(d("03-10"), d("09-10")), after.finishes)
        // 캐시와 DB 가 같다
        val cached = BookShelf.finishList(app)["b-1"].orEmpty().map { it.id }
        BookShelf.clearCaches()
        assertEquals(cached, BookShelf.finishList(app)["b-1"].orEmpty().map { it.id })
        assertEquals(listOf(60, 30).map { it * 60_000L }, record(*reads).rounds.map { it.ms })
    }

    @Test fun `첫 회차를 지우면 남은 회차는 그 뒤에 읽은 시간만`() {
        past()
        finish(1, d("03-10"))
        db.execSQL("INSERT INTO round_start VALUES ('b-1', ${d("05-01").toEpochDay()})")
        BookShelf.clearCaches()
        val reads = arrayOf(d("03-01") to 60, d("05-02") to 20)
        assertTrue(forget(record(*reads), 0, d("11-04")))
        val r = record(*reads)
        val only = r.rounds.single()
        assertFalse(only.finished)
        assertFalse(r.finished)
        assertEquals(d("05-02"), only.from)
        assertEquals(20 * 60_000L, only.ms)
    }

    @Test fun `같은 날 두 번 완독한 책에서 두 번째를 지우면 첫 회차의 시간은 그대로, 첫 번째를 지우면 그날까지의 시간이 빠진다`() {
        past("b-1")
        past("b-2", "싯다르타")
        for (book in listOf("b-1", "b-2")) {
            // 같은 날 두 번(두 번째는 1분 뒤에 누름): 두 번째 회차는 그날 하루, 시간 없이
            db.execSQL("INSERT INTO finish (uid, book_id, day, at) VALUES ('${book}a', '$book', ${d("03-10").toEpochDay()}, ${noon(d("03-10"))})")
            db.execSQL("INSERT INTO finish (uid, book_id, day, at) VALUES ('${book}b', '$book', ${d("03-10").toEpochDay()}, ${noon(d("03-10")) + 60_000})")
        }
        BookShelf.clearCaches()
        fun rowId(uid: String) = db.rows("SELECT id FROM finish WHERE uid = '$uid'") { it.getLong(0) }.single()
        val reads = arrayOf(d("03-01") to 60, d("03-10") to 30)
        val first = record(*reads, id = "b-1")
        assertEquals(listOf(90L, 0L).map { it * 60_000L }, first.rounds.map { it.ms })

        // 두 번째 회차: 뺄 날이 없다(앞 회차가 그날 끝났다)
        assertTrue(forget(first, 1, d("11-04")))
        assertEquals(listOf(d("03-10").toEpochDay() to d("03-10").toEpochDay()), db.rows("SELECT after_day, until_day FROM round_drop WHERE book_id = 'b-1'") { it.getLong(0) to it.getLong(1) })
        val left = record(*reads, id = "b-1").rounds.single()
        assertEquals(rowId("b-1a"), left.finishId)
        assertEquals(90 * 60_000L, left.ms)
        assertEquals(d("03-01"), left.from)

        // 첫 번째 회차: 그날까지 읽은 시간이 이 책에서 빠지고 남은 회차(두 번째 완독)는 시간 없이 그날 하루
        assertTrue(forget(record(*reads, id = "b-2"), 0, d("11-04")))
        val only = record(*reads, id = "b-2").rounds.single()
        assertEquals(rowId("b-2b"), only.finishId)
        assertEquals(0L, only.ms)
        assertEquals(listOf(d("03-10")), record(*reads, id = "b-2").finishes)
    }

    @Test fun `읽고 있는 책의 진행 중 회차를 지우면 읽고 있는 책에서도 빠지고 완독 회차만 남는다`() {
        val today = LocalDate.now()
        val done = today.minusDays(3)
        db.execSQL("INSERT INTO shelf VALUES (0, 'b-1', '데미안', '', NULL, 0, 10, -1, '', '')")
        finish(1, done)
        db.execSQL("INSERT INTO round_start VALUES ('b-1', ${today.toEpochDay()})")
        BookShelf.clearCaches()
        val reads = arrayOf(done to 60, today to 10)
        val before = record(*reads)
        assertEquals(BookRecord.State.READING, before.state)
        assertTrue(forget(before, 1, today))
        assertTrue(BookShelf.list(app).isEmpty())
        assertEquals(listOf("b-1" to false), BookShelf.history(app).map { it.book.id to it.hidden })
        val after = record(*reads)
        assertEquals(BookRecord.State.PAST, after.state)
        val only = after.rounds.single()
        assertTrue(only.finished)
        // 지운 회차의 오늘 10분은 이 책에서 빠지고 완독 회차는 그 1시간 그대로, 다시 읽기한 날도 지워진다
        assertEquals(60 * 60_000L, only.ms)
        assertNull(after.dayMs[today])
        assertTrue(BookShelf.roundStarts(app).isEmpty())
    }

    @Test fun `읽고 있는 책의 완독 회차를 지우면 책은 읽고 있는 책 그대로`() {
        db.execSQL("INSERT INTO shelf VALUES (0, 'b-1', '데미안', '', NULL, 0, 10, -1, '', '')")
        finish(1, d("03-10"))
        BookShelf.clearCaches()
        val reads = arrayOf(d("03-01") to 60, d("05-01") to 10)
        assertTrue(forget(record(*reads), 0, d("11-04")))
        assertEquals(listOf("b-1"), BookShelf.list(app).map { it.id })
        val only = record(*reads).rounds.single()
        assertFalse(only.finished)
        assertEquals(10 * 60_000L, only.ms)
    }

    @Test fun `지운 책·다른 책의 완독 줄은 지우지 않는다`() {
        db.execSQL("INSERT INTO past (pos, id, title, author, app, updated_at, progress, seen_title, left_at, hidden) VALUES (0, 'b-1', '데미안', '', NULL, 0, 0, '', 0, 1)")
        finish(1, d("03-10"))
        finish(2, d("04-10"), book = "b-2")
        BookShelf.clearCaches()
        assertFalse(BookShelf.forgetRound(app, "b-1", 1L, BookShelf.Drop(null, d("03-10"))))
        db.execSQL("UPDATE past SET hidden = 0")
        BookShelf.clearCaches()
        assertFalse(BookShelf.forgetRound(app, "b-1", 2L, BookShelf.Drop(null, d("04-10"))))
        assertEquals(0L, db.rows("SELECT COUNT(*) FROM round_drop") { it.getLong(0) }.single())
        assertEquals(2, BookShelf.finishList(app).values.sumOf { it.size })
    }
}
