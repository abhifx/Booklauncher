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

/**
 * 읽은 시간의 책 id: 목록에 없던 책이 책이 되면 그 제목의 빈 칸을 채우고([ReadingLog.adopt]), 옛 id 를 바꾸면 모든 표가 따라가고
 * ([BookShelf] 의 id 옮기기), `책 추가` 는 같은 제목의 나간 책이 있어도 늘 새 책.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadingBookIdTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val db get() = Db.get(app)
    private val today = LocalDate.now()
    /** 2×2 PNG(바뀐 표지) */
    private val png2: ByteArray =
        java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFklEQVR42mP8z8DwnwEJMKEKMDAwAAATqgMBLXLKhgAAAABJRU5ErkJggg==")

    /** 1×1 PNG(표지로 받은 그림) */
    private val png: ByteArray =
        java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=")

    private val BOOK get() = BookResult("y1", "데미안", "헤르만 헤세", "", "")

    @Before fun setUp() = BookShelf.clearCaches()

    @After fun tearDown() {
        BookShelf.clearCaches()
        ReadingLog.clearCache()
        Db.closeForTest()
        Storage.of(app).getDatabasePath("pebble.db").parentFile?.deleteRecursively()
    }

    private fun read(day: Long, title: String, min: Int, book: String = "") =
        db.execSQL("INSERT INTO reading_day VALUES ($day, 'pkg', '$title', '$book', ${min * 60_000L})")

    /** `날|제목|책|분` (책 id 가 `b-` 로 새로 만든 것이면 `new`) */
    private fun rows(newId: String? = null) = db.rows("SELECT day, title, book_id, ms FROM reading_day ORDER BY day, title, book_id") {
        val book = it.getString(2).let { b -> if (b == newId) "new" else b }
        "${it.getLong(0)}|${it.getString(1)}|$book|${it.getLong(3) / 60_000}"
    }

    private fun shelf(pos: Int, id: String, title: String) = db.execSQL("INSERT INTO shelf VALUES ($pos, '$id', '$title', '', NULL, 0, -1, -1, '', '')")

    private fun past(pos: Int, id: String, title: String, hidden: Boolean = false) =
        db.execSQL("INSERT INTO past (pos, id, title, author, app, updated_at, progress, seen_title, left_at, hidden) VALUES ($pos, '$id', '$title', '', NULL, 0, -1, '', 0, ${if (hidden) 1 else 0})")

    @Test fun `빈 칸을 그 책으로 채우고 이미 있는 칸에는 더한다`() {
        read(1, "데미안", 10)
        read(1, "데미안", 5, "b-1")
        read(2, "데미안: 에밀 싱클레어의 청춘 이야기", 7)
        read(2, "데미안 2", 2)
        read(2, "싯다르타", 3)
        read(3, "데미안", 4, "p-other")
        ReadingLog.adopt(app, "데미안", "", "b-1")
        // 부제가 붙은 제목도 같은 책, 앞부분만 같은 제목·다른 제목·이미 정해진 칸은 그대로
        assertEquals(
            listOf("1|데미안|b-1|15", "2|데미안 2||2", "2|데미안: 에밀 싱클레어의 청춘 이야기|b-1|7", "2|싯다르타||3", "3|데미안|p-other|4"), rows(),
        )
    }

    @Test fun `앱이 보여 준 제목으로도 채운다`() {
        read(1, "데미안", 10)
        ReadingLog.adopt(app, "헤세의 소설", "데미안", "b-1")
        assertEquals(listOf("1|데미안|b-1|10"), rows())
    }

    @Test fun `목록에 없던 책을 나간 책으로 적어 두면 그 책으로`() {
        read(1, "데미안", 10)
        val kept = BookShelf.keep(app, "데미안", null)
        assertEquals(listOf("1|데미안|new|10"), rows(kept.id))
    }

    @Test fun `목록에 없던 책을 고르는 순간 적어 둔 책으로, 다시 고르면 같은 책`() {
        read(1, "데미안", 10)
        val claimed = BookShelf.claim(app, "데미안", null)
        assertEquals(listOf("1|데미안|new|10"), rows(claimed.id))
        assertEquals(claimed.id, BookShelf.claim(app, "데미안", null).id)
        assertTrue(BookShelf.forget(app, claimed.id))
        assertEquals(listOf("1|데미안|new|10"), rows(claimed.id))
    }

    @Test fun `자동 추가로 새 책이 들어오면 그 책으로, 되살린 책은 다른 칸을 건드리지 않는다`() {
        read(1, "데미안", 10)
        read(1, "싯다르타", 10)
        val added = BookShelf.addOpened(app, "데미안", "", null, -1, -1)
        assertEquals(listOf("1|데미안|new|10", "1|싯다르타||10"), rows(added.id))
        past(0, "p-1", "싯다르타")
        BookShelf.clearCaches()
        BookShelf.addOpened(app, "싯다르타", "", null, -1, -1)
        assertEquals(listOf("1|데미안|new|10", "1|싯다르타||10"), rows(added.id))
    }

    @Test fun `책 추가는 늘 새 책이고 제목으로 잇지 않는다 - 어느 책에도 붙지 않은 시간도, 나간 책의 시간도 그대로`() {
        past(0, "p-1", "데미안")
        read(1, "데미안", 10, "p-1")
        read(2, "데미안", 5)
        BookShelf.clearCaches()
        val added = BookShelf.add(app, BOOK, png)
        assertTrue(added.manual && added.id.startsWith("m-"))
        assertEquals(listOf("p-1"), BookShelf.history(app).map { it.book.id })
        assertEquals(listOf("1|데미안|p-1|10", "2|데미안||5"), rows(added.id))
        // 자동 추가·목록에 없던 책·읽은 시간의 책 정하기 모두 이 책을 제목으로 찾지 않는다
        assertNull(BookShelf.findByTitle(app, "데미안"))
        assertEquals(null, bookFor("데미안", today, BookShelf.list(app), emptyList()))
        val opened = BookShelf.addOpened(app, "데미안", "", null, 30, -1)
        assertEquals("p-1", opened.id)  // 같은 제목의 나간 책을 되살린다(직접 넣은 책이 아니라)
        assertEquals(listOf("p-1", added.id), BookShelf.list(app).map { it.id })
        // 다시 띄워도 직접 넣은 책의 id 는 옛 id 바꾸기에 걸리지 않는다(시험의 `p-1` 은 옛 꼴이라 바뀐다)
        BookShelf.clearCaches()
        assertEquals(added.id, BookShelf.list(app).last().id)
    }

    @Test fun `옛 id 를 바꾸면 책 id 가 든 표가 모두 따라간다`() {
        shelf(0, "auto-데미안", "데미안")
        db.execSQL("INSERT INTO finish (uid, book_id, day) VALUES ('u1', 'auto-데미안', 100)")
        db.execSQL("INSERT INTO review VALUES ('auto-데미안', 8, '좋았다', 0)")
        db.execSQL("INSERT INTO round_start VALUES ('auto-데미안', NULL)")
        db.execSQL("INSERT INTO book_open VALUES (0, 'pkg', '데미안', 'auto-데미안')")
        db.execSQL("INSERT INTO round_began VALUES ('auto-데미안', 90)")
        db.execSQL("INSERT INTO round_drop (book_id, after_day, until_day) VALUES ('auto-데미안', NULL, 50)")
        read(1, "데미안", 10, "auto-데미안")
        // 옛 id 로 읽어 둔 지운 회차는 id 를 바꾼 뒤 다시 읽는다
        assertEquals(setOf("auto-데미안"), BookShelf.roundDrops(app).keys)
        val id = BookShelf.list(app).single().id
        assertTrue(id.startsWith("b-"))
        for (table in listOf("finish", "review", "round_start", "round_began", "round_drop", "reading_day", "book_open")) {
            assertEquals(table, listOf(id), db.rows("SELECT book_id FROM $table") { it.getString(0) })
        }
        assertEquals(1, BookShelf.finishList(app)[id]?.size)
        assertEquals("데미안", BookShelf.list(app).single().seenTitle)
        assertEquals(listOf(java.time.LocalDate.ofEpochDay(50)), BookShelf.roundDrops(app)[id]?.map { it.until })
    }

    @Test fun `자동 추가가 꺼지면 앱마다 제목 없는 편 책을 적고, 다시 펴면 그 책`() {
        ReadingLog.bookOpened(app, "pkg", "데미안", "b-1", at = 1_000)
        ReadingLog.bookOpened(app, "other", "싯다르타", "b-2", at = 2_000)
        ReadingLog.watchStopped(app, at = 3_000)
        // 두 번 꺼져도 한 번만
        ReadingLog.watchStopped(app, at = 4_000)
        ReadingLog.bookOpened(app, "pkg", "데미안", "b-1", at = 5_000)
        ReadingLog.clearCache()
        assertEquals(
            listOf("1000|pkg|데미안", "2000|other|싯다르타", "3000|pkg|", "3000|other|", "5000|pkg|데미안"),
            db.rows("SELECT at, pkg, title FROM book_open ORDER BY at, rowid") { "${it.getLong(0)}|${it.getString(1)}|${it.getString(2)}" },
        )
    }

    @Test fun `기기 날짜가 뒤로 가면 더 뒤 때의 줄이 있어도 지금 쓰이는 줄과 비교해 적는다`() {
        val day = 24 * 60 * 60 * 1000L
        val now = System.currentTimeMillis()
        // 열흘 뒤 시각으로 데미안·책 모름이 적혀 있다(그 뒤 기기 날짜가 열흘 뒤로 돌아감)
        ReadingLog.bookOpened(app, "pkg", "데미안", "b-1", at = now + 10 * day)
        ReadingLog.bookOpened(app, "other", "", "", at = now + 10 * day)
        // 지금 데미안을 펴면 적고(지금 쓰이는 줄이 없다), 셀 때 서비스가 꺼져 있으면 other 에도 지금 책 모름을 적는다
        ReadingLog.bookOpened(app, "pkg", "데미안", "b-1", at = now)
        ReadingLog.watchStopped(app, at = now + 1)
        ReadingLog.clearCache()
        assertEquals(
            listOf("${now}|pkg|데미안", "${now + 1}|pkg|", "${now + 1}|other|", "${now + 10 * day}|pkg|데미안", "${now + 10 * day}|other|"),
            db.rows("SELECT at, pkg, title FROM book_open ORDER BY at, rowid") { "${it.getLong(0)}|${it.getString(1)}|${it.getString(2)}" },
        )
    }

    private fun life(connected: Long, disconnected: Long) =
        Storage.of(app).getSharedPreferences("reader_watch_life", android.content.Context.MODE_PRIVATE).edit()
            .putLong("connected", connected).putLong("disconnected", disconnected).commit()

    @Test fun `서비스가 끊김 알림 없이 끝났으면 살아 있던 마지막 때부터 책 모름(앞 책에 붙지 않게)`() {
        val now = System.currentTimeMillis()
        // 한 시간 전 연결, 끊긴 기록 없음(강제 종료·업데이트 등). 그 뒤 이북 앱 알림을 받았다
        life(connected = now - 3_600_000, disconnected = 0)
        WatchLog.event(app, "pkg")
        val died = ReaderWatchService.downSince(app)!!
        assertTrue(died >= now && died <= System.currentTimeMillis())
        assertEquals(died, ReaderWatchService.stoppedAt(app))
        // 그 전에 편 책 뒤에 '책 모름' 이 그때로 적힌다
        ReadingLog.bookOpened(app, "pkg", "데미안", "b-1", at = now - 1_800_000)
        ReadingLog.watchStopped(app, died)
        ReadingLog.clearCache()
        assertEquals(
            listOf("${now - 1_800_000}|데미안", "$died|"),
            db.rows("SELECT at, title FROM book_open WHERE pkg = 'pkg' ORDER BY at, rowid") { "${it.getLong(0)}|${it.getString(1)}" },
        )
    }

    @Test fun `알림을 받은 뒤 편 책을 적고 알림 없이 끝났으면 책 모름은 그 편 책 줄 뒤에(그 책에 붙지 않게)`() {
        val now = System.currentTimeMillis()
        life(connected = now - 3_600_000, disconnected = 0)
        WatchLog.event(app, "pkg")
        // 같은 알림을 처리하며 조금 뒤에 편 책을 적었다(알림 때보다 늦다)
        val opened = WatchLog.lastEventAt(app) + 1
        ReadingLog.bookOpened(app, "pkg", "데미안", "b-1", at = opened)
        Thread.sleep(20)  // 지금이 편 때보다 뒤가 되게(downSince 는 지금을 넘지 않는다)
        val died = ReaderWatchService.downSince(app)!!
        assertTrue(died >= opened)
        ReadingLog.watchStopped(app, died)
        assertNull(ReadingLog.openBook(app, "pkg", at = died))
    }

    @Test fun `정상적으로 끊겼으면(업데이트·재부팅으로 설정이 켜진 채라도) 끊긴 때부터, 기록이 없으면 지금부터 책 모름`() {
        val now = System.currentTimeMillis()
        life(connected = now - 3_600_000, disconnected = now - 600_000)
        // 다시 붙을 때도 셀 때도 끊긴 때로 적는다
        assertEquals(now - 600_000, ReaderWatchService.downSince(app))
        assertEquals(now - 600_000, ReaderWatchService.stoppedAt(app))
        life(connected = 0, disconnected = 0)
        assertNull(ReaderWatchService.downSince(app))
        assertTrue(ReaderWatchService.stoppedAt(app) >= now)
    }

    @Test fun `책 모름 줄이 적힌 뒤에는 같은 책이어도 지금 읽는 책을 모른다`() {
        ReadingLog.bookOpened(app, "pkg", "데미안", "b-1", at = 1_000)
        assertEquals("b-1", ReadingLog.openBook(app, "pkg", at = 2_000))
        ReadingLog.watchStopped(app, at = 3_000)
        assertNull(ReadingLog.openBook(app, "pkg", at = 4_000))
        // 같은 책을 다시 적으면 다시 안다
        ReadingLog.bookOpened(app, "pkg", "데미안", "b-1", at = 5_000)
        assertEquals("b-1", ReadingLog.openBook(app, "pkg", at = 6_000))
        assertNull(ReadingLog.openBook(app, "other", at = 6_000))
    }

    @Test fun `같은 제목의 다른 책이나 책 id 없는 옛 줄이 적혀 있으면 지금 책을 모른다(다시 적어 그 책에 붙게)`() {
        // 나간 책 A 로 적혀 있는데 같은 제목 B 를 책 추가로 넣고 메뉴에서 제목을 봄
        ReadingLog.bookOpened(app, "pkg", "데미안", "b-A", at = 1_000)
        assertEquals("b-A", ReadingLog.openBook(app, "pkg", at = 2_000))
        assertTrue(ReadingLog.openBook(app, "pkg", at = 2_000) != "b-B")
        ReadingLog.bookOpened(app, "pkg", "데미안", "b-B", at = 3_000)
        assertEquals("b-B", ReadingLog.openBook(app, "pkg", at = 4_000))
        // 1.5.3 까지 적은 줄(책 id 없음)은 한 번 다시 적는다
        ReadingLog.bookOpened(app, "old", "데미안", "", at = 1_000)
        assertNull(ReadingLog.openBook(app, "old", at = 2_000))
    }

    @Test fun `제목을 못 읽은 칸을 눌러 들어가면 그 앱만 책 모름, 한 번만`() {
        ReadingLog.bookOpened(app, "pkg", "데미안", "b-1", at = 1_000)
        ReadingLog.bookOpened(app, "other", "싯다르타", "b-2", at = 2_000)
        ReadingLog.bookOpened(app, "pkg", "", "", at = 3_000)
        ReadingLog.bookOpened(app, "pkg", "", "", at = 4_000)
        ReadingLog.clearCache()
        assertEquals(
            listOf("1000|pkg|데미안", "2000|other|싯다르타", "3000|pkg|"),
            db.rows("SELECT at, pkg, title FROM book_open ORDER BY at, rowid") { "${it.getLong(0)}|${it.getString(1)}|${it.getString(2)}" },
        )
    }

    @Test fun `편 책 기록 저장이 실패하면 메모리 사본도 남기지 않아 다음에 다시 적는다`() {
        ReadingLog.bookOpened(app, "pkg", "데미안", "b-1", at = 1_000)
        db.execSQL("DROP TABLE book_open")
        ReadingLog.bookOpened(app, "pkg", "싯다르타", "b-2", at = 2_000)
        db.execSQL("CREATE TABLE book_open (at INTEGER NOT NULL, pkg TEXT NOT NULL, title TEXT NOT NULL, book_id TEXT NOT NULL DEFAULT '')")
        // 실패한 '싯다르타' 가 메모리에만 남아 있었다면 같은 책이라 안 적힌다
        ReadingLog.bookOpened(app, "pkg", "싯다르타", "b-2", at = 3_000)
        assertEquals(listOf("3000|pkg|싯다르타"), db.rows("SELECT at, pkg, title FROM book_open") { "${it.getLong(0)}|${it.getString(1)}|${it.getString(2)}" })
    }

    @Test fun `같은 때 적은 '책 모름' 과 책은 다시 읽어도 적은 순서 그대로(책이 마지막)`() {
        ReadingLog.bookOpened(app, "pkg", "", "", at = 1_000)
        ReadingLog.bookOpened(app, "pkg", "데미안", "b-1", at = 1_000)
        ReadingLog.clearCache()
        // 마지막 줄이 '데미안' 으로 읽혔다면 같은 책이라 새로 안 적는다
        ReadingLog.bookOpened(app, "pkg", "데미안", "b-1", at = 2_000)
        assertEquals(2L, db.rows("SELECT COUNT(*) FROM book_open") { it.getLong(0) }.single())
    }

    @Test fun `옛 id 바꾸기가 실패하면 옛 id 그대로 두고 다음에 다시 바꾼다`() {
        shelf(0, "auto-데미안", "데미안")
        db.execSQL("INSERT INTO finish (uid, book_id, day) VALUES ('u1', 'auto-데미안', 100)")
        // 바꿀 표 하나를 망가뜨림
        db.execSQL("DROP TABLE round_start")
        db.execSQL("CREATE VIEW round_start AS SELECT 'x' AS book_id")
        assertEquals(listOf("auto-데미안"), BookShelf.list(app).map { it.id })
        assertEquals(listOf("auto-데미안"), db.rows("SELECT book_id FROM finish") { it.getString(0) })
        db.execSQL("DROP VIEW round_start")
        db.execSQL("CREATE TABLE round_start (book_id TEXT PRIMARY KEY, day INTEGER)")
        BookShelf.clearCaches()
        val id = BookShelf.list(app).single().id
        assertTrue(id.startsWith("b-"))
        assertEquals(listOf(id), db.rows("SELECT book_id FROM finish") { it.getString(0) })
    }

    @Test fun `표지 다시 찾기는 없는 책이면 아무것도 안 한다`() {
        BookShelf.replace(app, "b-없음", BOOK, png)
        assertTrue(!BookShelf.hasCover(app, "b-없음"))
        assertTrue(BookShelf.list(app).isEmpty())
    }

    @Test fun `표지 다시 찾기에서 책 정보 저장이 실패하면 표지도 예전 것으로`() {
        shelf(0, "b-1", "데미안")
        BookShelf.clearCaches()
        BookShelf.setCover(app, "b-1", png)
        val before = java.io.File(Storage.of(app).filesDir, "books/b-1.jpg").readBytes()
        db.execSQL("DROP TABLE shelf")
        db.execSQL("CREATE VIEW shelf AS SELECT 0 AS pos")
        assertTrue(runCatching { BookShelf.replace(app, "b-1", BOOK, png2) }.isFailure)
        assertTrue(before.contentEquals(java.io.File(Storage.of(app).filesDir, "books/b-1.jpg").readBytes()))
    }

    @Test fun `잘라 온 표지를 넣다 책 정보 저장이 실패하면 표지 파일도 남기지 않는다(다음에 다시 잘라 온다)`() {
        shelf(0, "b-1", "데미안")
        BookShelf.clearCaches()
        BookShelf.list(app)
        db.execSQL("DROP TABLE shelf")
        db.execSQL("CREATE VIEW shelf AS SELECT 0 AS pos")
        assertTrue(runCatching { BookShelf.setCover(app, "b-1", png) }.isFailure)
        assertFalse(BookShelf.hasCover(app, "b-1"))
    }

    @Test fun `표지 다시 찾기에서 표지를 쓰지 못하면 예전 표지 그대로, 백업 파일도 남지 않는다`() {
        shelf(0, "b-1", "데미안")
        BookShelf.clearCaches()
        BookShelf.setCover(app, "b-1", png)
        val dir = java.io.File(Storage.of(app).filesDir, "books")
        val before = java.io.File(dir, "b-1.jpg").readBytes()
        // 임시 파일 자리를 폴더로 막아 표지 쓰기가 실패하게 한다
        java.io.File(dir, "b-1.jpg.tmp/x").apply { parentFile!!.mkdirs(); writeText("x") }
        assertTrue(runCatching { BookShelf.replace(app, "b-1", BOOK, png2) }.isFailure)
        assertTrue(before.contentEquals(java.io.File(dir, "b-1.jpg").readBytes()))
        assertTrue(dir.listFiles().orEmpty().none { it.name.endsWith(".bak") })
    }
}
