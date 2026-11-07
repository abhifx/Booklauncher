package com.woody.pebbledesk

import com.woody.pebbledesk.Db.rows
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 저장 함수([BookShelf.finish]·[BookShelf.restore]·[BookShelf.forget]·[BookShelf.claim]·[BookShelf.remove]·[BookShelf.setFinish])가
 * DB 와 캐시를 같게 바꾸는지. 캐시가 빈 채로(앱을 막 띄운 것처럼) 부르는 경우도 본다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookShelfStoreTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val db get() = Db.get(app)
    private val today = LocalDate.now()

    @Before fun setUp() {
        db.execSQL("INSERT INTO shelf VALUES (0, 'b-1', '데미안', '', NULL, 0, 10, -1, '', '')")
        BookShelf.clearCaches()
    }

    @After fun tearDown() {
        BookShelf.clearCaches()
        ReadingLog.clearCache()
        Db.closeForTest()
        Storage.of(app).getDatabasePath("pebble.db").parentFile?.deleteRecursively()
    }

    private fun dbFinishes() = db.rows("SELECT day, started, stars FROM finish WHERE book_id = 'b-1' ORDER BY day, at, id") {
        Triple(LocalDate.ofEpochDay(it.getLong(0)), if (it.isNull(1)) null else LocalDate.ofEpochDay(it.getLong(1)), if (it.isNull(2)) null else it.getInt(2))
    }

    /** 캐시를 비우고 DB 에서 다시 읽은 것이 지금 캐시와 같은가 */
    private fun assertCacheMatchesDb() {
        val cached = BookShelf.finishList(app)["b-1"].orEmpty().map { Triple(it.day, it.started, it.review?.halves) }
        val cachedReviews = BookShelf.reviews(app)
        val cachedStarts = BookShelf.roundStarts(app)
        BookShelf.clearCaches()
        assertEquals(BookShelf.finishList(app)["b-1"].orEmpty().map { Triple(it.day, it.started, it.review?.halves) }, cached)
        assertEquals(BookShelf.reviews(app), cachedReviews)
        assertEquals(BookShelf.roundStarts(app), cachedStarts)
        assertEquals(dbFinishes(), cached)
    }

    @Test fun `캐시가 빈 채로 완독해도 한 번만 들어간다`() {
        BookShelf.finish(app, "b-1", Review(8, "좋았다"))
        assertEquals(1, BookShelf.finishList(app)["b-1"]?.size)
        assertEquals(listOf(Triple(today, null, 8)), dbFinishes())
        assertTrue(BookShelf.list(app).isEmpty())
        assertEquals(listOf("b-1"), BookShelf.history(app).map { it.book.id })
        assertCacheMatchesDb()
    }

    @Test fun `완독하면 진행 중 회차의 감상과 다시 읽기한 날을 그 완독으로 옮긴다`() {
        BookShelf.finish(app, "b-1", Review(6))
        BookShelf.restore(app, "b-1", newRound = true)
        assertEquals(today, BookShelf.roundStarts(app)["b-1"])
        BookShelf.setReview(app, "b-1", Review(9, "두 번째"))
        BookShelf.finish(app, "b-1", BookShelf.reviews(app)["b-1"]!!)

        assertEquals(listOf(Triple(today, null, 6), Triple(today, today, 9)), dbFinishes())
        assertNull(BookShelf.reviews(app)["b-1"])
        assertNull(BookShelf.roundStarts(app)["b-1"])
        assertCacheMatchesDb()
    }

    @Test fun `진행 중 회차의 고친 읽기 시작한 날은 완독하면 그 완독 줄로 옮긴다`() {
        val began = today.minusDays(30)
        BookShelf.setBegan(app, "b-1", null, began)
        assertEquals(began, BookShelf.roundBegans(app)["b-1"])
        // 기록(상세·올해 읽은 책)의 진행 중 회차가 그날부터
        assertEquals(began, BookRecord.of(app, BookShelf.list(app).single()).lastRound.from)
        BookShelf.finish(app, "b-1", Review())
        assertEquals(listOf(began), BookShelf.finishList(app)["b-1"].orEmpty().map { it.began })
        assertNull(BookShelf.roundBegans(app)["b-1"])
        BookShelf.clearCaches()
        assertEquals(listOf(began), BookShelf.finishList(app)["b-1"].orEmpty().map { it.began })
        assertTrue(BookShelf.roundBegans(app).isEmpty())
    }

    @Test fun `완독한 회차의 읽기 시작한 날을 고치면 그 완독 줄에만, 다른 책의 줄은 못 고친다`() {
        BookShelf.finish(app, "b-1", Review(8))
        val id = BookShelf.finishList(app)["b-1"]!!.single().id
        val began = today.minusDays(10)
        BookShelf.setBegan(app, "b-1", id, began)
        assertEquals(began to 8, BookShelf.finishList(app)["b-1"]!!.single().let { it.began to it.review?.halves })
        BookShelf.clearCaches()
        assertEquals(began, BookShelf.finishList(app)["b-1"]!!.single().began)
        assertTrue(runCatching { BookShelf.setBegan(app, "b-2", id, began) }.isFailure)
    }

    @Test fun `새 회차가 시작하면 앞에서 남은 고친 읽기 시작한 날을 버린다`() {
        BookShelf.finish(app, "b-1", Review())
        // 진행 중 회차 없이 남은 줄(그 회차가 없어진 경우)
        db.execSQL("INSERT INTO round_began VALUES ('b-1', ${today.minusDays(3).toEpochDay()})")
        BookShelf.clearCaches()
        BookShelf.restore(app, "b-1", newRound = true)
        assertNull(BookShelf.roundBegans(app)["b-1"])
        BookShelf.clearCaches()
        assertNull(BookShelf.roundBegans(app)["b-1"])
    }

    @Test fun `같은 날 두 번 완독은 누른 순서대로`() {
        BookShelf.finish(app, "b-1", Review(2))
        BookShelf.restore(app, "b-1", newRound = true)
        BookShelf.finish(app, "b-1", Review(10))
        assertEquals(listOf(2, 10), BookShelf.finishList(app)["b-1"]!!.map { it.review?.halves })
        assertCacheMatchesDb()
    }

    @Test fun `완독한 날을 고치면 순서를 다시 맞춘다`() {
        BookShelf.finish(app, "b-1", Review(2))
        BookShelf.restore(app, "b-1", newRound = true)
        BookShelf.finish(app, "b-1", Review(10))
        val second = BookShelf.finishList(app)["b-1"]!![1]
        BookShelf.setFinish(app, "b-1", second.id, Review(10), today.minusDays(3))
        assertEquals(listOf(10, 2), BookShelf.finishList(app)["b-1"]!!.map { it.review?.halves })
        assertCacheMatchesDb()
    }

    @Test fun `빼기와 그때 고친 감상을 함께 저장한다`() {
        BookShelf.remove(app, "b-1", Review(4, "그만둠"))
        assertTrue(BookShelf.list(app).isEmpty())
        assertEquals(Review(4, "그만둠"), BookShelf.reviews(app)["b-1"])
        assertCacheMatchesDb()
    }

    @Test fun `이미 읽고 있는 책을 다시 읽기·완독해도 한 권만 남는다`() {
        // 나간 책 상세를 연 사이에 이북 앱에서 다시 펴 읽고 있는 책이 된 경우
        BookShelf.restore(app, "b-1", newRound = false)
        assertEquals(listOf("b-1"), BookShelf.list(app).map { it.id })
        BookShelf.finish(app, "b-1", Review(7))
        assertTrue(BookShelf.list(app).isEmpty())
        assertEquals(listOf("b-1"), BookShelf.history(app).map { it.book.id })
        assertEquals(1, BookShelf.finishList(app)["b-1"]?.size)
    }

    @Test fun `완독한 책도 펴면 자동으로 들어오고 그날 새 회차가 시작한다`() {
        BookShelf.finish(app, "b-1", Review(8))
        val added = BookShelf.addOpened(app, "데미안", "", null, 0, -1)
        assertEquals("b-1", added?.id)
        assertEquals(listOf("b-1"), BookShelf.list(app).map { it.id })
        assertTrue(BookShelf.history(app).isEmpty())
        assertEquals(today, BookShelf.roundStarts(app)["b-1"])
        assertCacheMatchesDb()
    }

    @Test fun `나간 책을 완독하면 완독만 더하고 나간 책 그대로`() {
        BookShelf.remove(app, "b-1")
        BookShelf.finish(app, "b-1", Review(6))
        assertTrue(BookShelf.list(app).isEmpty())
        assertEquals(listOf("b-1"), BookShelf.history(app).map { it.book.id })
        assertEquals(listOf(Triple(today, null, 6)), dbFinishes())
        assertCacheMatchesDb()
    }

    @Test fun `진행 중이던 회차를 다시 읽기해도 그 시작일을 그대로 둔다`() {
        BookShelf.finish(app, "b-1", Review(6))
        BookShelf.restore(app, "b-1", newRound = true)
        BookShelf.remove(app, "b-1")
        val earlier = today.minusDays(10)
        db.execSQL("UPDATE round_start SET day = ${earlier.toEpochDay()} WHERE book_id = 'b-1'")
        BookShelf.clearCaches()
        // 오래된 상세가 새 회차라고 잘못 알고 불러도
        BookShelf.restore(app, "b-1", newRound = true)
        assertEquals(earlier, BookShelf.roundStarts(app)["b-1"])
        assertCacheMatchesDb()
    }

    @Test fun `같은 제목의 지운 책이 더 최근이어도 지우지 않은 나간 책을 되살린다`() {
        BookShelf.remove(app, "b-1")
        db.execSQL("INSERT INTO past (pos, id, title, author, app, updated_at, progress, seen_title, left_at, hidden) VALUES (1, 'b-2', '데미안', '', NULL, 0, 0, '', ${System.currentTimeMillis()}, 1)")
        BookShelf.clearCaches()
        assertEquals("b-1", BookShelf.addOpened(app, "데미안", "", null, 0, -1)?.id)
        assertEquals(listOf("b-2"), BookShelf.history(app).map { it.book.id })
    }

    @Test fun `시작일을 모르는 진행 중 회차는 다시 펴도 이어지고 완독하면 시작일 없이 남는다`() {
        BookShelf.finish(app, "b-1", Review(6))
        db.execSQL("INSERT INTO round_start VALUES ('b-1', NULL)")
        BookShelf.clearCaches()
        assertTrue(BookShelf.roundStarts(app).containsKey("b-1"))
        BookShelf.addOpened(app, "데미안", "", null, 0, -1)
        assertNull(BookShelf.roundStarts(app)["b-1"])
        assertTrue(BookShelf.roundStarts(app).containsKey("b-1"))
        BookShelf.finish(app, "b-1", Review(8))
        assertEquals(listOf(Triple(today, null, 6), Triple(today, null, 8)), dbFinishes())
        assertCacheMatchesDb()
    }

    @Test fun `제목을 고친 책도 고치기 전 제목으로 찾을 때 가장 최근 나간 책을 되살린다`() {
        BookShelf.remove(app, "b-1")
        // 더 최근에 나간 같은 책의 다른 판: 제목을 고쳤지만 이북 앱이 보여 준 제목은 '데미안'
        db.execSQL("INSERT INTO past (pos, id, title, author, app, updated_at, progress, seen_title, left_at, hidden) VALUES (1, 'b-2', '헤세의 소설', '', NULL, 0, 0, '데미안', ${System.currentTimeMillis()}, 0)")
        BookShelf.clearCaches()
        assertEquals("b-2", BookShelf.findLivePast(app, "데미안")?.book?.id)
    }

    @Test fun `읽고 있는 책은 고치기 전 제목이 같아도 목록 앞쪽부터 찾는다`() {
        db.execSQL("UPDATE shelf SET pos = 1")
        db.execSQL("INSERT INTO shelf VALUES (0, 'b-2', '헤세의 소설', '', NULL, 0, 0, -1, '데미안', '')")
        BookShelf.clearCaches()
        assertEquals("b-2", BookShelf.findByTitle(app, "데미안")?.id)
    }

    @Test fun `읽고 있는 책은 기록에서 지우지 않는다`() {
        // 나간 책 상세(b-1)를 연 사이 이북 앱에서 펴 다시 읽고 있는 책이 된 경우
        assertEquals(false, BookShelf.forget(app, "b-1"))
        assertTrue(BookShelf.history(app).isEmpty())
        assertEquals(listOf("b-1"), BookShelf.list(app).map { it.id })
    }

    @Test fun `나간 책을 지우면 지운 책으로 남고 표지를 버린다`() {
        BookShelf.remove(app, "b-1")
        assertTrue(BookShelf.forget(app, "b-1"))
        assertEquals(listOf("b-1" to true), BookShelf.history(app).map { it.book.id to it.hidden })
    }

    @Test fun `목록에 없던 책은 그사이 생긴 같은 제목의 책, 없으면 나간 책으로 적어 둔다`() {
        // 목록에 없던 책 상세를 연 사이 같은 제목(고치기 전 제목)이 자동 추가된 경우
        db.execSQL("UPDATE shelf SET title = '헤세의 소설', seen_title = '데미안' WHERE id = 'b-1'")
        BookShelf.clearCaches()
        assertEquals("b-1", BookShelf.claim(app, "데미안", null).id)
        assertTrue(BookShelf.history(app).isEmpty())
        // 같은 제목의 나간 책
        BookShelf.remove(app, "b-1")
        assertEquals("b-1", BookShelf.claim(app, "데미안", null).id)
        // 없으면 적어 두고, 다음에는 그 책
        val kept = BookShelf.claim(app, "인간 실격", null)
        assertEquals(listOf("b-1", kept.id), BookShelf.history(app).map { it.book.id })
        assertEquals(kept.id, BookShelf.claim(app, "인간 실격", null).id)
    }

    @Test fun `다시 읽기 - 이미 읽고 있으면 그대로, 권수 제한은 없다`() {
        BookShelf.restore(app, "b-1", newRound = true)
        assertTrue(BookShelf.roundStarts(app).isEmpty())
        BookShelf.remove(app, "b-1")
        repeat(12) { db.execSQL("INSERT INTO shelf VALUES ($it, 'b-x$it', '책 $it', '', NULL, 0, 0, -1, '', '')") }
        BookShelf.clearCaches()
        BookShelf.restore(app, "b-1", newRound = false)
        assertEquals(13, BookShelf.list(app).size)
        assertEquals("b-1", BookShelf.list(app).first().id)
        assertTrue(BookShelf.history(app).isEmpty())
    }

    @Test fun `완독한 날 없이 별점·감상만 고치면 날은 그대로`() {
        BookShelf.finish(app, "b-1", Review(6))
        val f = BookShelf.finishList(app)["b-1"]!!.single()
        BookShelf.setFinish(app, "b-1", f.id, Review(9, "다시 보니"))
        assertEquals(listOf(Triple(today, null, 9)), dbFinishes())
        assertCacheMatchesDb()
    }

    @Test fun `다른 책의 완독 줄은 고치지 못한다`() {
        db.execSQL("INSERT INTO shelf VALUES (1, 'b-2', '싯다르타', '', NULL, 0, 0, -1, '', '')")
        BookShelf.clearCaches()
        BookShelf.finish(app, "b-1", Review(6))
        val other = BookShelf.finishList(app)["b-1"]!!.single()
        val failed = runCatching { BookShelf.setFinish(app, "b-2", other.id, Review(2), today.minusDays(1)) }.isFailure
        assertTrue(failed)
        assertEquals(listOf(Triple(today, null, 6)), dbFinishes())
        assertCacheMatchesDb()
    }

    // ── codex 리뷰에서 지적한 실패·경계 경로 ──

    @Test fun `완독 중 나간 책 저장이 실패하면 완독도 되돌리고 캐시를 DB 와 맞춘다`() {
        BookShelf.finishList(app); BookShelf.list(app); BookShelf.history(app)
        db.execSQL("DROP TABLE past")
        assertTrue(runCatching { BookShelf.finish(app, "b-1", Review(8)) }.isFailure)
        db.execSQL("CREATE TABLE past (pos INTEGER NOT NULL, id TEXT PRIMARY KEY, title TEXT, author TEXT, app TEXT, updated_at INTEGER, progress INTEGER, seen_title TEXT, left_at INTEGER, hidden INTEGER)")
        // 완독 줄이 남지 않고 책은 읽고 있는 책 그대로
        assertTrue(dbFinishes().isEmpty())
        assertEquals(listOf("b-1"), BookShelf.list(app).map { it.id })
        assertTrue(BookShelf.finishList(app)["b-1"].isNullOrEmpty())
    }

    @Test fun `책 빼기 중 감상 저장이 실패하면 빼기도 되돌린다`() {
        BookShelf.list(app)
        db.execSQL("DROP TABLE review")
        assertTrue(runCatching { BookShelf.remove(app, "b-1", Review(4, "그만둠")) }.isFailure)
        db.execSQL("CREATE TABLE review (book_id TEXT PRIMARY KEY, stars INTEGER NOT NULL, text TEXT NOT NULL, updated_at INTEGER NOT NULL)")
        assertEquals(listOf("b-1"), BookShelf.list(app).map { it.id })
        assertTrue(BookShelf.history(app).isEmpty())
    }

    @Test fun `목록에 없던 책을 적어 두다 읽은 시간 옮기기가 실패하면 적어 둔 것도 되돌린다`() {
        db.execSQL("INSERT INTO reading_day VALUES (1, 'pkg', '인간 실격', '', 600000)")
        db.execSQL("DROP TABLE reading_day")
        db.execSQL("CREATE TABLE reading_day (day INTEGER NOT NULL)")
        assertTrue(runCatching { BookShelf.claim(app, "인간 실격", null) }.isFailure)
        assertTrue(BookShelf.history(app).isEmpty())
    }

    @Test fun `상세를 연 사이 새 회차가 시작돼도 그 완독 줄에 저장한 감상은 그 회차에`() {
        BookShelf.finish(app, "b-1", Review(6))
        val first = BookShelf.finishList(app)["b-1"]!!.single()
        // 상세를 연 사이 다시 읽기(새 회차)
        BookShelf.restore(app, "b-1", newRound = true)
        BookShelf.setFinish(app, "b-1", first.id, Review(9, "다시 보니"))
        assertEquals(listOf(Triple(today, null, 9)), dbFinishes())
        assertNull(BookShelf.reviews(app)["b-1"])
        assertCacheMatchesDb()
    }

    @Test fun `자정을 넘겨 알아본 완독한 책은 편 날에 새 회차가 시작한다`() {
        // 사흘 전 완독해 나간 책을 어제 23:55 에 펴고, 자정을 넘겨 알아냄
        db.execSQL("INSERT INTO finish (uid, book_id, day, at) VALUES ('u1', 'b-1', ${today.minusDays(3).toEpochDay()}, 0)")
        BookShelf.clearCaches()
        BookShelf.remove(app, "b-1")
        val yesterday = today.minusDays(1)
        val at = yesterday.atTime(23, 55).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        BookShelf.addOpened(app, "데미안", "", null, -1, -1, at)
        assertEquals(yesterday, BookShelf.roundStarts(app)["b-1"])
    }

    @Test fun `기기 시각이 뒤로 고쳐져도 같은 책의 다음 완독은 앞 완독보다 늦게 적는다`() {
        // 앞 완독이 지금보다 한 시간 뒤 시각으로 적혀 있다(그 뒤 기기 시각이 뒤로 고쳐짐)
        val later = System.currentTimeMillis() + 3_600_000L
        db.execSQL("INSERT INTO finish (uid, book_id, day, at, stars) VALUES ('u1', 'b-1', ${today.toEpochDay()}, $later, 2)")
        BookShelf.clearCaches()
        BookShelf.finish(app, "b-1", Review(10))
        assertEquals(listOf(2, 10), BookShelf.finishList(app)["b-1"]!!.map { it.review?.halves })
        assertTrue(db.rows("SELECT at FROM finish WHERE stars = 10") { it.getLong(0) }.single() > later)
    }

    @Test fun `기기 날짜가 마지막 완독일보다 이르면 완독을 받지 않는다`() {
        // 마지막 완독이 내일로 적혀 있다(그 뒤 기기 날짜가 하루 뒤로 돌아감)
        db.execSQL("INSERT INTO finish (uid, book_id, day, at) VALUES ('u1', 'b-1', ${today.plusDays(1).toEpochDay()}, 0)")
        BookShelf.clearCaches()
        assertEquals(false, BookShelf.finish(app, "b-1", Review(10)))
        assertEquals(1, BookShelf.finishList(app)["b-1"]?.size)
        assertEquals(listOf("b-1"), BookShelf.list(app).map { it.id })
        // 같은 날이면 받는다
        db.execSQL("UPDATE finish SET day = ${today.toEpochDay()}")
        BookShelf.clearCaches()
        assertTrue(BookShelf.finish(app, "b-1", Review(10)))
    }

    @Test fun `사용 기록 권한이 없어도 저장된 기록으로 진행 중 회차를 알아 새 회차를 열지 않는다`() {
        // 10일 전 완독, 5일 전 6분 읽음(이미 진행 중), 나간 책. 단위 테스트에는 사용 기록 권한이 없다.
        db.execSQL("INSERT INTO finish (uid, book_id, day, at) VALUES ('u1', 'b-1', ${today.minusDays(10).toEpochDay()}, 0)")
        db.execSQL("INSERT INTO reading_day VALUES (${today.minusDays(5).toEpochDay()}, 'pkg', '데미안', 'b-1', 360000)")
        BookShelf.clearCaches(); ReadingLog.clearCache()
        BookShelf.remove(app, "b-1")
        BookShelf.addOpened(app, "데미안", "", null, -1, -1)
        assertNull(BookShelf.roundStarts(app)["b-1"])
        assertTrue("b-1" !in BookShelf.roundStarts(app))
    }

    @Test fun `기기 날짜가 마지막 완독일보다 이르면 다시 읽기는 시작일 모르는 회차로 연다`() {
        db.execSQL("INSERT INTO finish (uid, book_id, day, at) VALUES ('u1', 'b-1', ${today.plusDays(1).toEpochDay()}, 0)")
        BookShelf.clearCaches()
        BookShelf.remove(app, "b-1")
        BookShelf.restore(app, "b-1", newRound = true)
        assertEquals(listOf("b-1"), BookShelf.list(app).map { it.id })
        // 회차는 남고(읽기 전에 다시 빼도 사라지지 않게) 시작일만 모름. DB 에서 다시 읽어도 같다
        assertTrue("b-1" in BookShelf.roundStarts(app))
        assertNull(BookShelf.roundStarts(app)["b-1"])
        BookShelf.clearCaches()
        assertTrue("b-1" in BookShelf.roundStarts(app))
        assertNull(BookShelf.roundStarts(app)["b-1"])
        BookShelf.remove(app, "b-1")
        assertEquals(false, BookRecord.all(app).single { it.book.id == "b-1" }.rounds.last().finished)
    }

    @Test fun `사용 기록 권한이 없을 때 쓰는 저장된 기록은 끝난 날만`() {
        for (d in listOf(today.minusDays(1), today, today.plusDays(1)))
            db.execSQL("INSERT INTO reading_day VALUES (${d.toEpochDay()}, 'pkg', '데미안', 'b-1', 600000)")
        ReadingLog.clearCache()
        assertEquals(setOf(today.minusDays(1)), ReadingLog.savedDays(app).keys)
    }

    @Test fun `읽은 시간까지 지우면 그 책의 칸만 셀 때 빠지고 DB 의 줄은 남는다`() {
        val day = today.minusDays(1).toEpochDay()
        db.execSQL("INSERT INTO reading_day VALUES ($day, 'pkg', '데미안', 'b-1', 600000), ($day, 'pkg', '싯다르타', 'b-2', 300000), ($day, 'pkg', '', '', 60000)")
        ReadingLog.clearCache()
        BookShelf.remove(app, "b-1")
        assertTrue(BookShelf.forget(app, "b-1", dropTime = true))
        assertEquals(360000L, ReadingLog.savedDays(app).total(today.minusDays(1)))
        assertEquals(3L, db.rows("SELECT COUNT(*) FROM reading_day") { it.getLong(0) }.single())
        // DB 에서 다시 읽어도 같다
        BookShelf.clearCaches()
        ReadingLog.clearCache()
        assertEquals(true, BookShelf.history(app).single { it.book.id == "b-1" }.timeDropped)
        assertEquals(360000L, ReadingLog.savedDays(app).total(today.minusDays(1)))
    }

    @Test fun `읽은 시간까지 지운 책은 오늘 센 칸에서도 그 책 칸만 빠지고 날은 남는다`() {
        val gone = PastBook(ShelfBook("b-1", "데미안", "", null, 0), 0, hidden = true, timeDropped = true)
        val kept = PastBook(ShelfBook("b-2", "싯다르타", "", null, 0), 0, hidden = true)
        val days: ReadingDays = mapOf(
            today to mapOf(ReadKey("pkg", "데미안", "b-1") to 10L, ReadKey("pkg", "싯다르타", "b-2") to 20L, ReadKey("pkg") to 40L),
            today.minusDays(1) to mapOf(ReadKey("pkg", "데미안", "b-1") to 80L),
        )
        val out = ReadingLog.withoutDropped(days, listOf(gone, kept))
        assertEquals(60L, out.total(today))
        assertEquals(0L, out.total(today.minusDays(1)))
        assertEquals(days.keys, out.keys)
        // 시간까지 지운 책이 없으면 그대로(사본을 만들지 않는다)
        assertTrue(ReadingLog.withoutDropped(days, listOf(kept)) === days)
    }

    @Test fun `읽은 시간을 남기고 지우면 합계에 그대로`() {
        db.execSQL("INSERT INTO reading_day VALUES (${today.minusDays(1).toEpochDay()}, 'pkg', '데미안', 'b-1', 600000)")
        ReadingLog.clearCache()
        BookShelf.remove(app, "b-1")
        assertTrue(BookShelf.forget(app, "b-1", dropTime = false))
        BookShelf.clearCaches()
        assertEquals(false, BookShelf.history(app).single { it.book.id == "b-1" }.timeDropped)
        assertEquals(600000L, ReadingLog.savedDays(app).total(today.minusDays(1)))
    }

    @Test fun `지운 책은 다시 읽기로 되살리지 않는다(오래 열어 둔 상세)`() {
        BookShelf.remove(app, "b-1")
        assertTrue(BookShelf.forget(app, "b-1"))
        BookShelf.restore(app, "b-1", newRound = false)
        assertTrue(BookShelf.list(app).none { it.id == "b-1" })
        assertEquals(true, BookShelf.history(app).single { it.book.id == "b-1" }.hidden)
    }

    @Test fun `이미 지운 책을 또 지워도 지운 날은 처음 그대로`() {
        BookShelf.remove(app, "b-1")
        assertTrue(BookShelf.forget(app, "b-1"))
        val left = BookShelf.history(app).single { it.book.id == "b-1" }.leftAt
        Thread.sleep(5)
        assertTrue(BookShelf.forget(app, "b-1"))
        BookShelf.clearCaches()
        assertEquals(left, BookShelf.history(app).single { it.book.id == "b-1" }.leftAt)
    }

    @Test fun `열어 둔 진행 중 회차의 감상 저장은 그사이 완독한 회차로 간다(새 회차가 생기지 않음)`() {
        db.execSQL("INSERT INTO reading_day VALUES (${today.minusDays(2).toEpochDay()}, 'pkg', '데미안', 'b-1', 600000)")
        ReadingLog.clearCache()
        val shown = BookRecord.all(app).single { it.book.id == "b-1" }
        assertTrue(BookShelf.finish(app, "b-1", Review()))
        assertEquals(Saved.YES, app.saveRoundReview(shown, shown.lastRound, Review(8, "좋았다")))
        assertEquals(Review(8, "좋았다"), BookShelf.finishList(app)["b-1"]!!.single().review)
        assertNull(BookShelf.reviews(app)["b-1"])
        assertEquals(1, BookRecord.all(app).single { it.book.id == "b-1" }.rounds.size)
    }

    @Test fun `목록에 없던 책의 감상은 그사이 같은 제목의 책이 생겼으면 저장하지 않고, 없으면 그 제목을 나간 책으로 적어 남긴다`() {
        db.execSQL("INSERT INTO reading_day VALUES (${today.minusDays(2).toEpochDay()}, 'pkg', '모모', '', 600000)")
        ReadingLog.clearCache()
        val momo = BookRecord.all(app).single { it.book.title == "모모" }
        assertEquals(BookRecord.State.TITLE_ONLY, momo.state)
        // 같은 제목의 책이 없으면: 나간 책으로 적어 두고 그 책의 진행 중 회차 감상
        assertEquals(Saved.YES, app.saveRoundReview(momo, momo.lastRound, Review(6)))
        val kept = BookShelf.history(app).single { it.book.title == "모모" }.book.id
        assertEquals(Review(6), BookShelf.reviews(app)[kept])
        // 데미안이 목록에 있는데 열어 둔 '데미안' 목록에 없던 기록에서 저장하면 저장하지 않는다
        val ghost = BookRecord(ShelfBook("t:데미안", "데미안", "", null, 0), BookRecord.State.TITLE_ONLY, false, emptyList(),
            mapOf(today.minusDays(3) to 600000L))
        assertEquals(Saved.STALE, app.saveRoundReview(ghost, ghost.lastRound, Review(4)))
        assertNull(BookShelf.reviews(app)["b-1"])
    }

    @Test fun `그사이 나간 책을 빼면(오래 열어 둔 빼기 메뉴) 감상을 지금 회차에 적지 않는다`() {
        assertTrue(BookShelf.finish(app, "b-1", Review()))
        BookShelf.remove(app, "b-1", Review(6, "옛 메뉴의 감상"))
        assertNull(BookShelf.reviews(app)["b-1"])
        assertEquals(1, BookShelf.finishList(app)["b-1"]!!.size)
    }

    @Test fun `옛 판이 끊겨 같은 책이 읽고 있는 책과 나간 책 양쪽에 남아 있어도 완독·빼기가 된다`() {
        // 1.3.0~1.5.3 의 빼기·다시 읽기는 두 표를 따로 저장해, 그 사이 끊기면 같은 책이 양쪽에 남는다
        db.execSQL("INSERT INTO past (pos, id, title, author, app, updated_at, progress, seen_title, left_at, hidden) VALUES (0, 'b-1', '데미안', '', NULL, 0, -1, '', 1000, 0)")
        BookShelf.clearCaches()
        assertTrue(BookShelf.finish(app, "b-1", Review(6)))
        BookShelf.clearCaches()
        assertTrue(BookShelf.list(app).none { it.id == "b-1" })
        assertEquals(1, BookShelf.history(app).count { it.book.id == "b-1" })
        // 빼기도
        db.execSQL("INSERT INTO shelf VALUES (0, 'b-2', '싯다르타', '', NULL, 0, 10, -1, '', '')")
        db.execSQL("INSERT INTO past (pos, id, title, author, app, updated_at, progress, seen_title, left_at, hidden) VALUES (1, 'b-2', '싯다르타', '', NULL, 0, -1, '', 1000, 0)")
        BookShelf.clearCaches()
        BookShelf.remove(app, "b-2")
        BookShelf.clearCaches()
        assertEquals(1, BookShelf.history(app).count { it.book.id == "b-2" })
    }

    @Test fun `옛 판이 끊겨 나간 책에도 남은 읽고 있는 책은 기록에서 지우지 않고 표지도 남긴다`() {
        db.execSQL("INSERT INTO past (pos, id, title, author, app, updated_at, progress, seen_title, left_at, hidden) VALUES (0, 'b-1', '데미안', '', NULL, 0, -1, '', 1000, 0)")
        BookShelf.clearCaches()
        val cover = java.io.File(Storage.of(app).filesDir, "books/b-1.jpg").apply { parentFile!!.mkdirs(); writeText("x") }
        assertEquals(false, BookShelf.forget(app, "b-1"))
        assertTrue(cover.exists())
        assertTrue(BookShelf.history(app).none { it.hidden })
    }

    @Test fun `자동 추가로 되살릴 때 화면에서 모르는 진행률은 예전 값 그대로, 알면 새 값`() {
        db.execSQL("UPDATE shelf SET progress = 47 WHERE id = 'b-1'")
        BookShelf.clearCaches()
        BookShelf.remove(app, "b-1")
        BookShelf.clearCaches()
        assertEquals(47, BookShelf.addOpened(app, "데미안", "", null, -1, -1).progress)
        BookShelf.remove(app, "b-1")
        assertEquals(60, BookShelf.addOpened(app, "데미안", "", null, 60, -1).progress)
        BookShelf.clearCaches()
        assertEquals(60, BookShelf.list(app).single().progress)
    }

    @Test fun `읽는 화는 보이면 새 값, 모르면(빈 값) 그대로이고 나간 책에는 남기지 않는다`() {
        BookShelf.updateStatus(app, "b-1", -1, -1, chapter = "Ch. 3")
        BookShelf.updateStatus(app, "b-1", -1, -1)
        assertEquals("Ch. 3", BookShelf.list(app).single().chapter)
        BookShelf.clearCaches()
        assertEquals("Ch. 3", BookShelf.list(app).single().chapter)
        assertEquals(10, BookShelf.list(app).single().progress)
        BookShelf.remove(app, "b-1")
        assertEquals("", BookShelf.history(app).single().book.chapter)
        // 되살리면 그때 본 읽는 화
        assertEquals("Ch. 5", BookShelf.addOpened(app, "데미안", "", null, -1, -1, chapter = "Ch. 5").chapter)
        BookShelf.clearCaches()
        assertEquals("Ch. 5", BookShelf.list(app).single().chapter)
    }

    @Test fun `나간 책의 반납일은 저장하지 않으니 메모리 사본에도 남기지 않는다(다시 띄운 뒤와 같게)`() {
        // 준비한 b-1 은 반납일이 있다(10)
        BookShelf.remove(app, "b-1")
        assertEquals(-1L, BookShelf.history(app).single().book.due)
        BookShelf.restore(app, "b-1", newRound = false)
        val cached = BookShelf.list(app).single().due
        BookShelf.clearCaches()
        assertEquals(BookShelf.list(app).single().due, cached)
    }

    @Test fun `여러 저장을 묶으면 뒤가 실패할 때 목록에 없던 책을 적어 둔 것도 되돌린다`() {
        assertTrue(runCatching { BookShelf.together(app) { BookShelf.claim(app, "인간 실격", null); error("완독 실패") } }.isFailure)
        assertTrue(BookShelf.history(app).isEmpty())
        BookShelf.clearCaches()
        assertTrue(BookShelf.history(app).isEmpty())
    }

    @Test fun `직접 넣은 책은 읽은 시간이 없어도 넣은 날 회차가 시작해 기록·올해 읽은 책에 들어가고, 완독하면 그 기간으로`() {
        val png = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=")
        val added = BookShelf.add(app, BookResult("y1", "모모", "미하엘 엔데", "", ""), png)
        assertEquals(today, BookShelf.roundStarts(app)[added.id])
        fun record() = BookRecord.all(app, emptyMap()).single { it.book.id == added.id }
        val reading = record()
        assertEquals(BookRecord.State.READING, reading.state)
        assertEquals(today, reading.lastRound.from)
        assertEquals(null, reading.lastRound.to)
        assertEquals(listOf(today.year), reading.readYears)
        assertTrue(reading.canReview)
        assertEquals(listOf(added.id), YearBooksActivity.yearRows(BookRecord.all(app, emptyMap()), today.year).map { it.book.id }.filter { it == added.id })
        // 완독하면 넣은 날 – 오늘 한 회차
        assertTrue(BookShelf.finish(app, added.id, Review(8)))
        val done = record()
        assertEquals(1, done.rounds.size)
        assertEquals(today, done.lastRound.from)
        assertEquals(today, done.lastRound.to)
        assertEquals(listOf(today.year), done.readYears)
        // 완독하지 않고 뺀 직접 추가 책도 시작한 해에 남는다
        val other = BookShelf.add(app, BookResult("y2", "끝없는 이야기", "", "", ""), png)
        BookShelf.remove(app, other.id)
        assertEquals(listOf(today.year), BookRecord.all(app, emptyMap()).single { it.book.id == other.id }.readYears)
    }

    @Test fun `직접 넣다 회차 시작 저장이 실패하면 책도 표지도 남지 않는다`() {
        val png = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=")
        val before = BookShelf.list(app).map { it.id }
        db.execSQL("DROP TABLE round_start")
        assertTrue(runCatching { BookShelf.add(app, BookResult("y1", "모모", "", "", ""), png) }.isFailure)
        assertEquals(before, BookShelf.list(app).map { it.id })
        BookShelf.clearCaches()
        assertEquals(before, BookShelf.list(app).map { it.id })
        val books = java.io.File(Storage.of(app).filesDir, "books")
        assertTrue(books.listFiles().orEmpty().none { it.name.startsWith(BookShelf.MANUAL_PREFIX) })
    }

    /** 사용 기록 권한을 주고, 오늘 읽는 화면을 연 구간 하나를 사용 기록에 넣는다. 그 구간 길이(ms) */
    private fun readToday(): Long {
        org.robolectric.Shadows.shadowOf(app.getSystemService(android.app.AppOpsManager::class.java))
            .setMode(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), app.packageName, android.app.AppOpsManager.MODE_ALLOWED)
        val start = today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() + 1000
        val end = minOf(System.currentTimeMillis() - 1000, start + 600_000)
        val usage = org.robolectric.Shadows.shadowOf(app.getSystemService(android.app.usage.UsageStatsManager::class.java))
        for ((type, at) in listOf(android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND to start, android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND to end))
            usage.addEvent(org.robolectric.shadows.ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage("pkg").setClass("pkg.ViewerActivity").setEventType(type).setTimeStamp(at).build())
        return end - start
    }

    private fun dbDays() = db.rows("SELECT day, ms FROM reading_day ORDER BY day") { LocalDate.ofEpochDay(it.getLong(0)) to it.getLong(1) }

    @Test fun `기기 날짜가 저장한 마지막 날 이전으로 돌아가면 미래가 된 날은 보이지 않고 오늘만 세며 저장하지 않는다`() {
        val read = readToday()
        // 내일까지 저장돼 있다(그 뒤 기기 날짜가 하루 뒤로 돌아감)
        for (d in listOf(today.minusDays(1), today, today.plusDays(1)))
            db.execSQL("INSERT INTO reading_day VALUES (${d.toEpochDay()}, 'pkg', '데미안', 'b-1', 300000)")
        db.execSQL("INSERT INTO reading_meta VALUES ('through', '${today.plusDays(1)}'), ('since', '${today.minusDays(30)}')")
        ReadingLog.clearCache()
        val before = dbDays()
        val days = ReadingLog.days(app, setOf("pkg"))!!
        assertEquals(setOf(today.minusDays(1), today), days.keys)
        // 오늘은 저장한 줄(5분)이 아니라 사용 기록으로 센 시간
        assertEquals(read, days.getValue(today).values.sum())
        // DB 는 그대로(오늘·내일 줄, through)
        assertEquals(before, dbDays())
        assertEquals(listOf(today.plusDays(1).toString()), db.rows("SELECT value FROM reading_meta WHERE name = 'through'") { it.getString(0) })
    }

    @Test fun `기기 날짜가 기록 시작일보다 앞으로 가도 since 는 오늘을 넘지 않는다(오늘이 화면 범위 안)`() {
        db.execSQL("INSERT INTO reading_meta VALUES ('through', '${today.plusDays(5)}'), ('since', '${today.plusDays(3)}')")
        ReadingLog.clearCache()
        assertEquals(today, ReadingLog.since(app))
        // DB 는 그대로
        assertEquals(listOf(today.plusDays(3).toString()), db.rows("SELECT value FROM reading_meta WHERE name = 'since'") { it.getString(0) })
    }

    @Test fun `저장한 마지막 날이 오늘이어도(하루 뒤로 돌아감) 그날 줄은 쓰지 않고 다시 센다`() {
        val read = readToday()
        db.execSQL("INSERT INTO reading_day VALUES (${today.toEpochDay()}, 'pkg', '데미안', 'b-1', 300000)")
        db.execSQL("INSERT INTO reading_meta VALUES ('through', '$today'), ('since', '${today.minusDays(30)}')")
        ReadingLog.clearCache()
        val before = dbDays()
        assertEquals(read, ReadingLog.days(app, setOf("pkg"))!!.getValue(today).values.sum())
        assertEquals(before, dbDays())
    }

    private fun dbShelf() = db.rows("SELECT pos, id, progress, due, author FROM shelf ORDER BY pos") {
        listOf(it.getInt(0), it.getString(1), it.getInt(2), it.getLong(3), it.getString(4))
    }

    @Test fun `서재에서 본 여러 책의 진행률을 순서 그대로 한 번에 쓴다`() {
        db.execSQL("INSERT INTO shelf VALUES (1, 'b-2', '싯다르타', '', NULL, 0, -1, -1, '', '')")
        db.execSQL("INSERT INTO shelf VALUES (2, 'b-3', '모모', '', NULL, 0, 5, -1, '', '')")
        BookShelf.clearCaches()
        val before = Db.changes()
        BookShelf.updateStatuses(app, listOf(
            BookShelf.Status("b-3", 40, 20_000L, "미하엘 엔데"),
            BookShelf.Status("b-1", -1, -1),          // 모르는 값은 그대로
            BookShelf.Status("b-2", 30, -1),
            BookShelf.Status("b-2", -1, -1),          // 같은 책의 뒤 칸이 모르면 앞 칸에서 본 값이 남는다
        ))
        assertEquals(before + 1, Db.changes())
        val want = listOf(listOf(0, "b-1", 10, -1L, ""), listOf(1, "b-2", 30, -1L, ""), listOf(2, "b-3", 40, 20_000L, "미하엘 엔데"))
        assertEquals(want, dbShelf())
        assertEquals(want.map { it[1] to it[2] }, BookShelf.list(app).map { it.id to it.progress })
    }

    @Test fun `바뀐 게 없거나 이미 맨 앞이면 쓰지 않는다`() {
        BookShelf.list(app)
        val before = Db.changes()
        BookShelf.updateStatus(app, "b-1", 10, -1)
        BookShelf.moveToFront(app, "b-1")
        BookShelf.updateStatuses(app, emptyList())
        assertEquals(before, Db.changes())
    }

    @Test fun `순서가 바뀌면 표를 다시 쓴다`() {
        db.execSQL("INSERT INTO shelf VALUES (1, 'b-2', '싯다르타', '', NULL, 0, -1, -1, '', '')")
        BookShelf.clearCaches()
        BookShelf.moveToFront(app, "b-2")
        assertEquals(listOf("b-2", "b-1"), dbShelf().map { it[1] })
        assertEquals(listOf(0, 1), dbShelf().map { it[0] })
        BookShelf.clearCaches()
        assertEquals(listOf("b-2", "b-1"), BookShelf.list(app).map { it.id })
    }
}
