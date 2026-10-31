package com.woody.booklauncher

import android.Manifest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 책 파일 표지: 파일을 적을 때 뒤에서 꺼내 두고([BookShelf.setFilePath]), 못 했으면 그릴 때 꺼낸다. 안드로이드 10 이라 책 파일 읽기는 저장소 읽기 권한 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class FileCoverTest {
    @get:Rule val tmp = TemporaryFolder()

    private val app get() = RuntimeEnvironment.getApplication()

    /** 1×1 PNG */
    private val png: ByteArray =
        java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=")

    @Before fun setUp() {
        BookShelf.clearCaches()
        // KOReader 가 넣은 만화(만화 묶음의 첫 그림을 고른 앱), 킨들이 넣은 책(기본 길의 앱)
        Db.get(app).execSQL("INSERT INTO shelf VALUES (0, 'b-1', '만화', '', 'org.koreader.launcher/org.koreader.launcher.MainActivity', 0, -1, -1, '', '')")
        Db.get(app).execSQL("INSERT INTO shelf VALUES (1, 'b-2', '다른 앱', '', 'com.amazon.kindle/com.amazon.kindle.UpgradePage', 0, -1, -1, '', '')")
    }

    @After fun tearDown() {
        BookShelf.clearCaches()
        Db.closeForTest()
        Storage.of(app).getDatabasePath("pebble.db").parentFile?.deleteRecursively()
        File(Storage.of(app).filesDir, "books").deleteRecursively()
    }

    private fun cbz() = File(tmp.root, "c.cbz").also { f ->
        ZipOutputStream(f.outputStream()).use { z -> z.putNextEntry(ZipEntry("p1.png")); z.write(png); z.closeEntry() }
    }

    /** 뒤에서 꺼내기가 끝나기를 기다린다(최대 5초) */
    private fun awaitCover(): Boolean {
        repeat(50) { if (BookShelf.hasCover(app, "b-1")) return true; Thread.sleep(100) }
        return false
    }

    @Test fun `파일을 적으면 그리기 전에 뒤에서 표지를 꺼내 둔다`() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        BookShelf.setFilePath(app, "b-1", cbz().path)
        assertTrue(awaitCover())
    }

    @Test fun `만화 묶음의 첫 그림은 그 책을 넣은 앱이 골랐을 때만 - 고르지 않은 앱의 책은 화면에서 자른다`() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        val other = File(tmp.root, "other.cbz").also { cbz().copyTo(it) }
        BookShelf.setFilePath(app, "b-2", other.path)
        BookShelf.cover(app, BookShelf.list(app).first { it.id == "b-2" }, 100)
        Thread.sleep(300)
        assertFalse(BookShelf.hasCover(app, "b-2"))
        assertFalse(ShelfRecorder.coverFromFile(app, other.path, BookShelf.fileCovers(app, "b-2")))
        BookShelf.setFilePath(app, "b-1", cbz().path)
        assertTrue(awaitCover())
        assertTrue(ShelfRecorder.coverFromFile(app, cbz().path, BookShelf.fileCovers(app, "b-1")))
    }

    @Test fun `만화 묶음의 첫 그림을 쓰는 앱은 그 앱 파일에서 고른다`() {
        val on = com.woody.booklauncher.readers.ReaderApps.ALL.filter { it.fileCovers === com.woody.booklauncher.readers.BookFiles.WITH_COMICS }.map { it.pkg }.toSet()
        assertEquals(setOf("org.koreader.launcher", "org.koreader.launcher.fdroid", "com.onyx.kreader", "com.ezne.easyview.n", "com.ezne.easyview.free",
            "com.ezne.easyview.pro", "com.inno.woojoo"), on)
    }

    @Test fun `PDF 는 열지 않고 머리로만 있다고 보고, 못 그린 PDF 는 그 뒤 없다고 답한다`() {
        val covers = com.woody.booklauncher.readers.BookFiles.withPdf(com.woody.booklauncher.readers.BookFiles.BOOKS)
        val pdf = File(tmp.root, "a.pdf").apply { writeText("%PDF-1.4\n%âãÏÓ\n1 0 obj") }.path
        // 머리 앞에 다른 바이트가 붙어도 PDF 리더들처럼 PDF 로 본다
        val junk = File(tmp.root, "b.pdf").apply { writeBytes(ByteArray(100) { 0x20 } + "%PDF-1.7".toByteArray()) }.path
        val notPdf = File(tmp.root, "c.pdf").apply { writeText("<html>not a pdf</html>") }.path
        assertTrue(covers.has(app, pdf))
        assertTrue(covers.has(app, junk))
        assertFalse(covers.has(app, notPdf))
        // 쪽이 없는 가짜 PDF 는 그리지 못한다: 그 뒤로는 없다고 답해 화면에서 자르게
        assertEquals(null, covers.cover(app, pdf))
        assertFalse(covers.has(app, pdf))
    }

    @Test fun `PDF 첫 쪽까지 쓰는 앱은 Librera 둘뿐이다`() {
        val pdf = File(tmp.root, "a.pdf").apply { writeText("x") }.path
        val on = com.woody.booklauncher.readers.ReaderApps.ALL.filter { it.fileCovers.slow(pdf) }.map { it.pkg }.toSet()
        assertEquals(setOf("com.foobnix.pdf.reader", "com.foobnix.pro.pdf.reader"), on)
    }

    @Test fun `책 파일 읽기가 꺼져 있을 때 적은 파일도 허용한 뒤 그릴 때 꺼낸다`() {
        // 권한이 없으면 뒤에서 꺼내지 않는다(그 자리에서 끝난다)
        BookShelf.setFilePath(app, "b-1", cbz().path)
        assertFalse(BookShelf.hasCover(app, "b-1"))
        shadowOf(app).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        BookShelf.cover(app, BookShelf.list(app).first { it.id == "b-1" }, 100)
        assertTrue(BookShelf.hasCover(app, "b-1"))
    }
}
