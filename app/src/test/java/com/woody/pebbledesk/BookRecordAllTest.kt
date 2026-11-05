package com.woody.pebbledesk

import com.woody.pebbledesk.BookRecord.State
import org.junit.After
import org.junit.Assert.assertEquals
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
 * 읽은 시간을 책에 모으기([BookRecord.all]): 칸에 정해 둔 책 id 로 모으고(제목으로 다시 찾지 않는다), 책을 모르는 칸은 목록에 없던 책.
 * 어느 책으로 정하는지는 [BookForTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookRecordAllTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val db get() = Db.get(app)
    private val pkg = "com.amazon.kindle"

    @Before fun setUp() = BookShelf.clearCaches()

    @After fun tearDown() {
        BookShelf.clearCaches()
        ReadingLog.clearCache()
        Db.closeForTest()
        Storage.of(app).getDatabasePath("pebble.db").parentFile?.deleteRecursively()
    }

    private fun d(s: String) = LocalDate.parse("2026-$s")
    private fun noon(s: String) = d(s).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 12 * 3_600_000L

    private fun shelf(pos: Int, id: String, title: String, seen: String = "") =
        db.execSQL("INSERT INTO shelf VALUES ($pos, '$id', '$title', '', NULL, 0, 0, -1, '$seen', '')")

    private fun past(pos: Int, id: String, title: String, left: String, hidden: Boolean = false) =
        db.execSQL("INSERT INTO past VALUES ($pos, '$id', '$title', '', NULL, 0, 0, '', ${noon(left)}, ${if (hidden) 1 else 0})")

    /** 그날 그 제목을 몇 분, 붙은 책 id(모르면 "") */
    private class Read(val day: String, val title: String, val min: Int, val book: String = "")

    private fun all(vararg reads: Read): List<BookRecord> {
        BookShelf.clearCaches()
        val days = reads.groupBy { d(it.day) }.mapValues { (_, l) ->
            l.groupBy({ ReadKey(pkg, it.title, it.book) }, { it.min * 60_000L }).mapValues { it.value.sum() }
        }
        return BookRecord.all(app, days)
    }

    private fun List<BookRecord>.of(id: String) = single { it.book.id == id }
    private fun BookRecord.days() = dayMs.keys.sorted()

    @Test fun `정해 둔 책 id 로 모은다`() {
        shelf(0, "b-1", "데미안")
        val r = all(Read("10-01", "데미안", 10, "b-1")).of("b-1")
        assertEquals(State.READING, r.state)
        assertEquals(listOf(d("10-01")), r.days())
    }

    @Test fun `같은 제목의 새 책이 생겨도 지난 시간은 정해 둔 책에 남는다`() {
        past(0, "p-a", "데미안", "01-20")
        shelf(0, "b-new", "데미안")
        val records = all(Read("01-05", "데미안", 60, "p-a"), Read("10-12", "데미안", 30, "b-new"))
        assertEquals(listOf(d("01-05")), records.of("p-a").days())
        assertEquals(listOf(d("10-12")), records.of("b-new").days())
    }

    @Test fun `제목을 고쳐도 그 책에 붙는다`() {
        shelf(0, "b-1", "헤세의 소설")
        assertEquals(listOf(d("10-01")), all(Read("10-01", "데미안", 10, "b-1")).of("b-1").days())
    }

    @Test fun `같은 제목의 두 책은 따로`() {
        shelf(0, "b-1", "데미안")
        shelf(1, "b-2", "데미안")
        val records = all(Read("10-01", "데미안", 10, "b-1"), Read("10-01", "데미안", 20, "b-2"))
        assertEquals(10 * 60_000L, records.of("b-1").ms)
        assertEquals(20 * 60_000L, records.of("b-2").ms)
    }

    @Test fun `책을 모르는 칸은 같은 제목의 책이 있어도 목록에 없던 책`() {
        shelf(0, "b-1", "데미안")
        val records = all(Read("10-01", "데미안", 10))
        assertEquals(State.TITLE_ONLY, records.single { it.dayMs.isNotEmpty() }.state)
        assertTrue(records.none { it.book.id == "b-1" })
    }

    @Test fun `어디에도 없는 책 id 의 시간은 다른 책에 붙이지 않는다`() {
        shelf(0, "b-1", "데미안")
        val records = all(Read("10-01", "데미안", 10, "b-lost"))
        assertEquals(State.TITLE_ONLY, records.single().state)
    }

    @Test fun `지운 책`() {
        past(0, "p-gone", "인간 실격", "03-10", hidden = true)
        val records = all(Read("03-01", "인간 실격", 10, "p-gone"), Read("04-01", "인간 실격", 10))
        assertTrue(records.of("p-gone").hidden)
        assertEquals(listOf(d("03-01")), records.of("p-gone").days())
        assertEquals(listOf(d("04-01")), records.single { it.state == State.TITLE_ONLY }.days())
    }

    @Test fun `앱만 아는 시간은 책에 붙이지 않는다`() {
        val days = mapOf(d("10-01") to mapOf(ReadKey(pkg) to 600_000L))
        BookShelf.clearCaches()
        assertTrue(BookRecord.all(app, days).isEmpty())
    }

    @Test fun `읽은 시간 없이 완독만 있는 책(기록을 켜기 전에 읽은 책)도 들어간다`() {
        past(0, "p-old", "어린 왕자", "02-01")
        db.execSQL("INSERT INTO finish (uid, book_id, day) VALUES ('u1', 'p-old', ${d("02-01").toEpochDay()})")
        val r = all().of("p-old")
        assertTrue(r.finished)
        assertTrue(r.dayMs.isEmpty())
        assertEquals(State.PAST, r.state)
    }

    @Test fun `목록에 없던 책이 그사이 읽고 있는 책이 됐으면 다시 띄울 때 그 책으로`() {
        val stale = all(Read("10-01", "데미안", 10)).single { it.state == State.TITLE_ONLY }
        // 상세를 연 사이 자동 추가로 들어옴(그 제목의 칸이 그 책으로 채워진다)
        shelf(0, "b-1", "헤세의 소설", seen = "데미안")
        val now = all(Read("10-01", "데미안", 10, "b-1"))
        assertEquals("b-1", stale.reloadIn(app, now).book.id)
        assertEquals(State.READING, stale.reloadIn(app, now).state)
    }

    @Test fun `다시 띄울 때 - 목록에 없던 책이 나간 책이 됐으면 그 책, 기록에 없는 책은 빈 기록, 어디에도 없으면 그대로`() {
        val stale = all(Read("10-01", "인간 실격", 10)).single { it.state == State.TITLE_ONLY }
        past(0, "p-1", "인간 실격", "10-02")
        val now = all(Read("10-01", "인간 실격", 10, "p-1"))
        assertEquals("p-1" to State.PAST, stale.reloadIn(app, now).let { it.book.id to it.state })
        // 읽은 시간도 완독도 없어 all 에 없는 읽고 있는 책
        shelf(0, "b-new", "싯다르타")
        BookShelf.clearCaches()
        val fresh = BookRecord(ShelfBook("b-new", "싯다르타", "", null, 0), State.READING, false, emptyList(), emptyMap())
        val again = fresh.reloadIn(app, emptyList())
        assertEquals(State.READING, again.state)
        assertTrue(again.dayMs.isEmpty())
        // 어디에도 없는 id 는 그대로
        val ghost = BookRecord(ShelfBook("b-ghost", "없는 책", "", null, 0), State.PAST, false, emptyList(), emptyMap())
        assertTrue(ghost.reloadIn(app, emptyList()) === ghost)
    }

    @Test fun `기록이 그대로면 지난번 목록을, 완독·감상을 저장하면 새 목록을`() {
        shelf(0, "b-1", "데미안")
        db.execSQL("INSERT INTO reading_day VALUES (${LocalDate.now().minusDays(2).toEpochDay()}, '$pkg', '데미안', 'b-1', 600000)")
        BookShelf.clearCaches()
        ReadingLog.clearCache()
        val first = BookRecord.all(app)
        assertTrue(BookRecord.all(app) === first)
        BookShelf.setReview(app, "b-1", Review(6, "좋다"))
        val reviewed = BookRecord.all(app)
        assertTrue(reviewed !== first)
        assertEquals(Review(6, "좋다"), reviewed.of("b-1").lastRound.review)
        assertTrue(BookShelf.finish(app, "b-1", Review(8)))
        val finished = BookRecord.all(app).of("b-1")
        assertEquals(State.PAST, finished.state)
        assertTrue(finished.lastRound.finished)
    }

    @Test fun `캐시를 비우면(되돌린 트랜잭션) 지난번 목록을 버린다`() {
        shelf(0, "b-1", "데미안")
        BookShelf.clearCaches()
        val first = BookRecord.all(app)
        // 트랜잭션 밖에서 고치면 DB 순번이 그대로다: 되돌린 트랜잭션처럼 캐시를 비워야 새로 읽는다
        shelf(1, "b-2", "싯다르타")
        db.execSQL("INSERT INTO finish (uid, book_id, day, at) VALUES ('u-2', 'b-2', ${LocalDate.now().minusDays(1).toEpochDay()}, 0)")
        assertTrue(BookRecord.all(app) === first)
        BookShelf.clearCaches()
        assertEquals(listOf("b-2"), BookRecord.all(app).map { it.book.id })
    }
}
