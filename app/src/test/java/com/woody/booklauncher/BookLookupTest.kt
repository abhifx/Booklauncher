package com.woody.booklauncher

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 읽는 책 자동 추가가 책을 찾고 넣을 때 기대는 [BookShelf] 규칙(docs/reader-adapter-plan.md 1단계: 옮기기 전에 지금 동작을 묶어 둔다).
 * 읽고 있는 책: b-1 데미안, b-2 수레바퀴(파일 /sd/a/wheel.epub). 나간 책: p-1 데미안(지우지 않음), p-2 싯다르타(지움, 파일 /sd/a/sid.epub),
 * p-3 만화(그림 폴더 /sd/comic/01.jpg)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookLookupTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val db get() = Db.get(app)

    @Before fun setUp() {
        db.execSQL("INSERT INTO shelf VALUES (0, 'b-1', '데미안', '', 'x/y', 0, 10, -1, '데미안', '', 0)")
        db.execSQL("INSERT INTO shelf VALUES (1, 'b-2', '수레바퀴 아래서', '헤세', 'x/y', 0, 20, -1, '수레바퀴 아래서', '', 0)")
        db.execSQL("INSERT INTO past (pos, id, title, author, app, updated_at, progress, seen_title, left_at, hidden) VALUES (0, 'p-1', '데미안', '', 'x/y', 0, 50, '데미안', 1000, 0)")
        db.execSQL("INSERT INTO past (pos, id, title, author, app, updated_at, progress, seen_title, left_at, hidden) VALUES (1, 'p-2', '싯다르타', '', 'x/y', 0, 30, '싯다르타', 2000, 1)")
        db.execSQL("INSERT INTO past (pos, id, title, author, app, updated_at, progress, seen_title, left_at, hidden) VALUES (2, 'p-3', '만화', '', 'x/y', 0, 0, '만화', 3000, 0)")
        db.execSQL("INSERT INTO book_file VALUES ('b-2', '/sd/a/wheel.epub')")
        db.execSQL("INSERT INTO book_file VALUES ('p-2', '/sd/a/sid.epub')")
        db.execSQL("INSERT INTO book_file VALUES ('p-3', '/sd/comic/01.jpg')")
        BookShelf.clearCaches()
    }

    @After fun tearDown() {
        BookShelf.clearCaches()
        ReadingLog.clearCache()
        Db.closeForTest()
        Storage.of(app).getDatabasePath("pebble.db").parentFile?.deleteRecursively()
    }

    @Test fun `제목으로 찾기는 읽고 있는 책만`() {
        assertEquals("b-1", BookShelf.findByTitle(app, "데미안")?.id)
        assertNull(BookShelf.findByTitle(app, "싯다르타"))
    }

    @Test fun `파일 없는 같은 제목은 읽고 있는 책 다음 지우지 않은 나간 책`() {
        assertEquals("b-1", BookShelf.idByTitle(app, "데미안") { BookShelf.filePath(app, it.id) == null })
        assertNull(BookShelf.idByTitle(app, "수레바퀴 아래서") { BookShelf.filePath(app, it.id) == null })
        // 지운 나간 책은 제목으로 찾지 않는다
        assertNull(BookShelf.idByTitle(app, "싯다르타") { true })
    }

    @Test fun `파일로 찾기는 대소문자를 가리지 않고 지운 나간 책도`() {
        assertEquals("b-2", BookShelf.idByFile(app, "/SD/A/Wheel.epub"))
        assertEquals("p-2", BookShelf.idByFile(app, "/sd/a/sid.epub"))
        assertEquals("싯다르타", BookShelf.titleByFile(app, "/sd/a/sid.epub"))
        assertEquals("p-2", BookShelf.bookOf(app, setOf("p-2"))?.id)
    }

    @Test fun `그림 폴더는 폴더로 한 권`() {
        assertEquals("p-3", BookShelf.idByFolder(app, "/sd/comic"))
        assertNull(BookShelf.idByFolder(app, "/sd/other"))
    }

    @Test fun `파일 이름으로 찾기는 확장자를 숨겨도`() {
        assertEquals(setOf("b-2"), BookShelf.idsByFileName(app, "wheel.epub"))
        assertEquals(setOf("b-2"), BookShelf.idsByFileName(app, "Wheel"))
    }

    @Test fun `편 책 넣기는 같은 제목의 지우지 않은 나간 책을 되살린다`() {
        val book = BookShelf.addOpened(app, "만화", "작가", "x/y", 5, -1)
        assertEquals("p-3", book.id)
        assertEquals(listOf("p-3", "b-1", "b-2"), BookShelf.list(app).map { it.id })
        assertEquals(5, book.progress)
    }

    @Test fun `지운 나간 책은 되살리지 않고 새 책`() {
        val book = BookShelf.addOpened(app, "싯다르타", "", "x/y", -1, -1)
        assertNotEquals("p-2", book.id)
        assertEquals(listOf("p-1", "p-2", "p-3"), BookShelf.history(app).map { it.book.id })
    }

    @Test fun `되살릴 책을 정하면 그 책만`() {
        val other = BookShelf.addOpened(app, "만화", "", "x/y", -1, -1) { it.id == "nope" }
        assertNotEquals("p-3", other.id)
        val same = BookShelf.addOpened(app, "다른 제목", "", "x/y", -1, -1) { it.id == "p-1" }
        assertEquals("p-1", same.id)
    }

    @Test fun `진행률은 모르면 그대로, 저자는 비어 있을 때만`() {
        BookShelf.updateStatus(app, "b-1", -1, -1, "헤세")
        assertEquals(10, BookShelf.get(app, "b-1")?.progress)
        assertEquals("헤세", BookShelf.get(app, "b-1")?.author)
        BookShelf.updateStatus(app, "b-2", 40, -1, "다른 사람")
        assertEquals(40, BookShelf.get(app, "b-2")?.progress)
        assertEquals("헤세", BookShelf.get(app, "b-2")?.author)
    }

    @Test fun `맨 앞으로`() {
        BookShelf.moveToFront(app, "b-2")
        assertEquals(listOf("b-2", "b-1"), BookShelf.list(app).map { it.id })
    }
}
