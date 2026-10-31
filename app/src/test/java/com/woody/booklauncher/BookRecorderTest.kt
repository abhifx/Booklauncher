package com.woody.booklauncher

import com.woody.booklauncher.Db.rows
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

/**
 * 공통 기록기([ShelfRecorder.opened])의 기록 규칙(docs/reader-adapter-plan.md `기록기`): 제목으로·앱이 정한 책으로 넣기, 되살리기, 잘라 둔 표지,
 * 안의 순서(찾기·넣기 → found → 편 책 기록 → logged → 표지)와 단계별 실패.
 * 읽고 있는 책: b-1 데미안(진행률 10). 나간 책: p-1 싯다르타(지우지 않음), p-2 데미안(지우지 않음, 파일 /sd/d.epub)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookRecorderTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val db get() = Db.get(app)
    private val pkg = "com.example.reader"
    private lateinit var rec: ShelfRecorder

    @Before fun setUp() {
        db.execSQL("INSERT INTO shelf VALUES (0, 'b-1', '데미안', '', 'x/y', 0, 10, -1, '데미안', '')")
        db.execSQL("INSERT INTO past VALUES (0, 'p-1', '싯다르타', '', 'x/y', 0, 50, '싯다르타', 1000, 0)")
        db.execSQL("INSERT INTO past VALUES (1, 'p-2', '데미안', '', 'x/y', 0, 30, '데미안', 2000, 0)")
        db.execSQL("INSERT INTO book_file VALUES ('p-2', '/sd/d.epub')")
        BookShelf.clearCaches()
        ReadingLog.clearCache()
        rec = ShelfRecorder(app)
    }

    @After fun tearDown() {
        BookShelf.clearCaches()
        ReadingLog.clearCache()
        Db.closeForTest()
        Storage.of(app).getDatabasePath("pebble.db").parentFile?.deleteRecursively()
    }

    private fun jpeg(): ByteArray = ByteArrayOutputStream().also {
        android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888).compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it)
    }.toByteArray()

    private fun ids() = BookShelf.list(app).map { it.id }

    @Test fun `제목으로 읽고 있는 책을 맨 앞으로, 진행률을 적고 편 책 기록`() {
        db.execSQL("INSERT INTO shelf VALUES (1, 'b-2', '수레바퀴', '', 'x/y', 0, 0, -1, '수레바퀴', '')")
        BookShelf.clearCaches()
        val book = rec.opened(pkg, Seen("수레바퀴", "헤세", 20, -1), 5000L, Target.ByTitle)
        assertEquals("b-2", book.id)
        assertEquals(listOf("b-2", "b-1"), ids())
        assertEquals(20, BookShelf.get(app, "b-2")?.progress)
        assertEquals("헤세", BookShelf.get(app, "b-2")?.author)
        assertEquals("b-2", rec.openedId(pkg))
        assertEquals("b-2", rec.recordedBook(pkg)?.id)
    }

    @Test fun `제목으로 나간 책을 되살린다`() {
        assertEquals("p-1", rec.opened(pkg, Seen("싯다르타"), null, Target.ByTitle).id)
        assertEquals(listOf("p-1", "b-1"), ids())
    }

    @Test fun `앱이 정한 책 null 이면 같은 제목이 있어도 새 책`() {
        val book = rec.opened(pkg, Seen("데미안"), null, Target.Exact(null))
        assertTrue(book.id !in setOf("b-1", "p-2"))
        assertEquals(listOf(book.id, "b-1"), ids())
        assertEquals(listOf("p-1", "p-2"), BookShelf.history(app).map { it.book.id })
    }

    @Test fun `앱이 정한 나간 책은 그 책만 되살리고 그 책 제목으로 기록`() {
        val book = rec.opened(pkg, Seen("d.epub"), null, Target.Exact("p-2"))
        assertEquals("p-2", book.id)
        assertEquals(listOf("p-2", "b-1"), ids())
        assertEquals(listOf("데미안"), db.rows("SELECT title FROM book_open WHERE pkg = ?", arrayOf(pkg)) { it.getString(0) })
    }

    @Test fun `제목 자리의 잘라 둔 표지는 들어올 때 붙고, 앱이 정한 책은 열쇠를 줄 때만`() {
        rec.pendingCover(normTitle("새 책"), jpeg())
        assertTrue(rec.hasPendingTitle("새 책 : 부제"))
        val a = rec.opened(pkg, Seen("새 책"), null, Target.ByTitle)
        assertTrue(BookShelf.hasCover(app, a.id))
        assertFalse(rec.hasPendingTitle("새 책"))

        rec.pendingCover(normTitle("다른 책"), jpeg())
        val b = rec.opened(pkg, Seen("다른 책"), null, Target.Exact(null))
        assertFalse(BookShelf.hasCover(app, b.id))

        rec.pendingCover("file:#휴대폰/a/x.epub", jpeg())
        val c = rec.opened(pkg, Seen("x"), null, Target.Exact(null), coverKey = "file:#휴대폰/a/x.epub")
        assertTrue(BookShelf.hasCover(app, c.id))
        assertFalse(rec.hasPendingCover("file:#휴대폰/a/x.epub"))
    }

    @Test fun `안의 순서는 found 다음 편 책 기록 다음 logged`() {
        val seen = mutableListOf<String>()
        rec.opened(pkg, Seen("싯다르타"), null, Target.ByTitle, steps = object : OpenSteps {
            override fun found(book: ShelfBook) { seen += "found:${rec.openedId(pkg)}" }
            override fun logged(book: ShelfBook) { seen += "logged:${rec.openedId(pkg)}" }
        })
        assertEquals(listOf("found:null", "logged:p-1"), seen)
    }

    @Test fun `found 가 실패하면 그 뒤 단계를 하지 않고 먼저 끝난 저장은 남는다`() {
        rec.pendingCover(normTitle("싯다르타"), jpeg())
        val error = runCatching {
            rec.opened(pkg, Seen("싯다르타"), null, Target.ByTitle, steps = object : OpenSteps {
                override fun found(book: ShelfBook) {
                    rec.setFile(book.id, "/sd/s.epub")
                    error("저장 실패")
                }
            })
        }.exceptionOrNull()
        assertNotEquals(null, error)
        assertEquals(listOf("p-1", "b-1"), ids())
        assertEquals("/sd/s.epub", BookShelf.filePath(app, "p-1"))
        assertNull(rec.openedId(pkg))
        assertFalse(BookShelf.hasCover(app, "p-1"))
    }

    @Test fun `알게 된 파일의 제목`() {
        assertEquals("데미안", rec.lookup.knownTitle("/sd/D.epub", "책 정보 제목"))
        db.execSQL("INSERT INTO shelf VALUES (1, 'b-3', 'story', '', 'x/y', 0, 0, -1, 'story', '')")
        BookShelf.clearCaches()
        assertEquals("story", rec.lookup.knownTitle("/sd/books/story.epub", "Story Title"))
        assertEquals("새 제목", rec.lookup.knownTitle("/sd/books/none.epub", "새 제목"))
    }
}
