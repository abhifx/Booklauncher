package com.woody.booklauncher.readers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** KOReader 가 남기는 기록 읽기([LuaData], [KOReaderData], EPUB 책 정보 [BookFiles.epub]) */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KOReaderDataTest {
    @get:Rule val tmp = TemporaryFolder()
    private val app get() = org.robolectric.RuntimeEnvironment.getApplication()

    private val ko get() = File(tmp.root, "koreader").apply { mkdirs() }

    private fun write(file: File, text: String) = file.apply { parentFile?.mkdirs(); writeText(text) }

    @Test fun `Lua 값 파일을 읽는다`() {
        val v = LuaData.parse("""
            -- /storage/emulated/0/koreader/history.lua
            return {
                [1] = {
                    ["time"] = 1792314571,
                    ["file"] = "/a/\"b\"\\c\
            d\0651.epub",
                },
                ["n"] = -0.5,
                ["t"] = true,
                ["x"] = nil --[[ LOOP:
                ^------- ]],
                name = 'q',
                ["f"] = 1e+20,
            }
        """.trimIndent().toByteArray()) as Map<*, *>
        val e = v[1.0] as Map<*, *>
        assertEquals(1792314571.0, e["time"])
        assertEquals("/a/\"b\"\\c\nd" + "A" + "1.epub", e["file"])
        assertEquals(-0.5, v["n"])
        assertEquals(true, v["t"])
        assertEquals(null, v["x"])
        assertEquals("q", v["name"])
        assertEquals(1e20, v["f"])
    }

    @Test fun `이름 없는 값은 1 부터`() {
        val v = LuaData.parse("return { \"a\", \"b\" }".toByteArray()) as Map<*, *>
        assertEquals("a", v[1.0])
        assertEquals("b", v[2.0])
    }

    @Test fun `한글은 바이트 그대로`() {
        val v = LuaData.parse("return { [\"title\"] = \"시험 책 하나\" }".toByteArray()) as Map<*, *>
        assertEquals("시험 책 하나", v["title"])
    }

    @Test fun `쓰는 중이라 못 읽은 history 는 담아 두지 않는다`() {
        val h = write(File(ko, "history.lua"), """return { [1] = { ["time"] = 1, ["file"] = "/b/a.epub" """)
        assertNull(KOReaderData(ko).books(0))
        // 같은 크기·같은 시각으로 다 쓰였어도 다시 읽는다
        val stamp = h.lastModified()
        h.writeText("""return { [1] = { ["time"] = 1, ["file"] = "/b/b.epub" }}""")
        h.setLastModified(stamp)
        assertEquals("/b/b.epub", KOReaderData(ko).books(0)?.last()?.path)
    }

    @Test fun `못 읽으면 null`() {
        assertNull(LuaData.parse("return { [\"a\"] = ".toByteArray()))
        assertNull(LuaData.parse("x = 1".toByteArray()))
    }

    @Test fun `맨 앞 책과 연 때, KOReader 안내서만 뺀다`() {
        write(File(ko, "history.lua"), """
            return {
                [2] = { ["time"] = 100, ["file"] = "/b/old.epub" },
                [1] = { ["time"] = 300, ["file"] = "${ko.path}/help/quickstart-ko.html" },
                [3] = { ["time"] = 200, ["file"] = "/b/c.pdf" },
                [4] = { ["time"] = 50, ["file"] = "${ko.path}/books/in-koreader.epub" },
            }
        """.trimIndent())
        // 오래된 것부터, 지금 책이 끝. koreader 폴더 안에 둔 책은 넣는다
        assertEquals(listOf("${ko.path}/books/in-koreader.epub", "/b/c.pdf", "/b/old.epub"), KOReaderData(ko).books(0)?.map { it.path })
        // 책 파일이 없어 접근 시각을 모른다: 열림·닫힘 모름
        assertEquals(0L, KOReaderData(ko).books(0)?.last()?.openedAt)
    }

    @Test fun `기록 파일이 없으면 null`() {
        assertNull(KOReaderData(ko).books(0))
    }

    @Test fun `지난번 뒤의 책만, 지금 책은 늘`() {
        write(File(ko, "history.lua"), """
            return {
                [1] = { ["time"] = 100, ["file"] = "/b/now.epub" },
                [2] = { ["time"] = 90, ["file"] = "/b/b.epub" },
                [3] = { ["time"] = 50, ["file"] = "/b/a.epub" },
            }
        """.trimIndent())
        assertEquals(listOf("/b/b.epub", "/b/now.epub"), KOReaderData(ko).books(60_000)?.map { it.path })
        assertEquals(listOf("/b/now.epub"), KOReaderData(ko).books(200_000)?.map { it.path })
    }

    @Test fun `책 옆 sdr 의 제목·저자·진행률, 고친 책 정보가 먼저`() {
        val book = File(tmp.root, "Books/어떤 파일.epub").apply { parentFile!!.mkdirs(); writeText("x") }
        val sdr = File(tmp.root, "Books/어떤 파일.sdr")
        write(File(sdr, "metadata.epub.lua"), """
            return {
                ["doc_props"] = { ["title"] = "진짜 제목", ["authors"] = "김작가\n이작가" },
                ["percent_finished"] = 0.4271,
            }
        """.trimIndent())
        KOReaderData(ko).book(book.path).let {
            assertEquals("진짜 제목", it.title)
            assertEquals("김작가, 이작가", it.author)
            assertEquals(42, it.progress)
        }
        write(File(sdr, "custom_metadata.lua"), """return { ["custom_props"] = { ["title"] = "고친 제목" } }""")
        KOReaderData(ko).book(book.path).let {
            assertEquals("고친 제목", it.title)
            assertEquals("김작가, 이작가", it.author)
        }
    }

    @Test fun `metadata 없이 고친 책 정보만 있어도 쓴다`() {
        val book = File(tmp.root, "Books/x.pdf").apply { parentFile!!.mkdirs(); writeText("x") }
        write(File(tmp.root, "Books/x.sdr/custom_metadata.lua"), """return { ["custom_props"] = { ["title"] = "고친 제목", ["authors"] = "박작가" } }""")
        KOReaderData(ko).book(book.path).let {
            assertEquals("고친 제목", it.title)
            assertEquals("박작가", it.author)
        }
    }

    @Test fun `docsettings 폴더와 hashdocsettings 폴더의 sdr, 가장 최근 것`() {
        val book = File(tmp.root, "Books/a.pdf").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(5000) { (it % 251).toByte() }) }
        val dir = write(File(ko, "docsettings${tmp.root.path}/Books/a.sdr/metadata.pdf.lua"), """return { ["doc_props"] = { ["title"] = "dir" } }""")
        assertEquals("dir", KOReaderData(ko).book(book.path).title)
        val hash = write(File(ko, "hashdocsettings/e7/e77dcca7f22a949ae8492c260ca19f32.sdr/metadata.pdf.lua"), """return { ["doc_props"] = { ["title"] = "hash" } }""")
        hash.setLastModified(dir.lastModified() + 10_000)
        assertEquals("hash", KOReaderData(ko).book(book.path).title)
    }

    @Test fun `KOReader 의 부분 MD5`() {
        val f = File(tmp.root, "x.bin").apply { writeBytes(ByteArray(5000) { (it % 251).toByte() }) }
        assertEquals("e77dcca7f22a949ae8492c260ca19f32", KOReaderData.partialMd5(f))
    }

    @Test fun `sdr 이 없으면 EPUB 책 정보, 아니면 파일 이름`() {
        val epub = File(tmp.root, "Books/test-a.epub").also { it.parentFile!!.mkdirs(); writeEpub(it, "시험 책 하나", "김작가") }
        KOReaderData(ko).book(epub.path).let {
            assertEquals("시험 책 하나", it.title)
            assertEquals("김작가", it.author)
            assertEquals(-1, it.progress)
        }
        val txt = File(tmp.root, "Books/그냥 글.txt").apply { writeText("x") }
        assertEquals("그냥 글", KOReaderData(ko).book(txt.path).title)
    }

    private fun atime(file: File, sec: Long) = Files.setAttribute(file.toPath(), "lastAccessTime", FileTime.fromMillis(sec * 1000))

    @Test fun `지금 책이 열려 있다 - 접근 시각이 기록의 때와 같다`() {
        val epub = File(tmp.root, "Books/test-a.epub").also { it.parentFile!!.mkdirs(); writeEpub(it, "시험 책 하나", "김작가") }
        atime(epub, 1_800_000_005)
        write(File(ko, "history.lua"), """return { [1] = { ["time"] = 1800000005, ["file"] = "${epub.path}" } }""")
        val b = KOReaderData(ko).books(0)!!.last()
        assertEquals("시험 책 하나", b.title)
        assertEquals(epub.path, b.path)
        assertEquals(1_800_000_005_000L, b.openedAt)
        assertEquals(0L, b.closedAt)
    }

    @Test fun `기록의 때가 늦으면 닫혔다, 앞 책도`() {
        val a = File(tmp.root, "Books/a.txt").apply { parentFile!!.mkdirs(); writeText("a") }
        val b = File(tmp.root, "Books/b.txt").apply { writeText("b") }
        atime(a, 1_800_000_000)
        atime(b, 1_800_000_200)
        write(File(ko, "history.lua"), """
            return {
                [1] = { ["time"] = 1800000300, ["file"] = "${b.path}" },
                [2] = { ["time"] = 1800000100, ["file"] = "${a.path}" },
            }
        """.trimIndent())
        val (prev, cur) = KOReaderData(ko).books(0)!!
        assertEquals(1_800_000_200_000L, cur.openedAt)
        assertEquals(1_800_000_300_000L, cur.closedAt)
        assertEquals(1_800_000_000_000L, prev.openedAt)
        assertEquals(1_800_000_100_000L, prev.closedAt)
    }

    @Test fun `접근 시각이 기록보다 늦으면(다른 앱이 바꿈) 모른다`() {
        val a = File(tmp.root, "a.txt").apply { writeText("a") }
        atime(a, 1_800_000_900)
        write(File(ko, "history.lua"), """return { [1] = { ["time"] = 1800000100, ["file"] = "${a.path}" } }""")
        val cur = KOReaderData(ko).books(0)!!.last()
        assertEquals(0L, cur.openedAt)
        assertEquals(0L, cur.closedAt)
    }

    @Test fun `EPUB 표지 경로 - 퍼센트 인코딩은 풀고 +는 그대로`() {
        val epub = File(tmp.root, "c.epub").also { writeEpub(it, "t", "a", cover = true) }
        assertEquals("OEBPS/images/cover+1.jpg", BookFiles.epub(epub.path)?.cover)
    }

    @Test fun `CBZ 표지는 첫 그림 - 숫자는 크기 순, 숨긴 항목·__MACOSX 는 뺀다`() {
        val cbz = File(tmp.root, "c.cbz")
        ZipOutputStream(cbz.outputStream()).use { z ->
            for (name in listOf("ComicInfo.xml", "__MACOSX/v1/._p1.jpg", "v1/.thumb.jpg", "v1/p10.jpg", "v1/p2.JPG", "v1/")) {
                z.putNextEntry(ZipEntry(name)); z.closeEntry()
            }
        }
        assertEquals("v1/p2.JPG", java.util.zip.ZipFile(cbz).use { BookFiles.comicCover(it)?.name })
    }

    @Test fun `그림이 없는 CBZ·ZIP 이 아닌 cbz 는 표지가 없다`() {
        val noImage = File(tmp.root, "n.cbz")
        ZipOutputStream(noImage.outputStream()).use { z -> z.putNextEntry(ZipEntry("ComicInfo.xml")); z.closeEntry() }
        assertFalse(BookFiles.WITH_COMICS.has(app, noImage.path))
        assertNull(BookFiles.WITH_COMICS.cover(app, noImage.path))
        // RAR 을 이름만 바꾼 것
        val rar = File(tmp.root, "r.cbz").apply { writeBytes("Rar!\u001a\u0007\u0000".toByteArray()) }
        assertFalse(BookFiles.WITH_COMICS.has(app, rar.path))
        assertNull(BookFiles.WITH_COMICS.cover(app, rar.path))
    }

    private fun writeEpub(file: File, title: String, author: String, cover: Boolean = false) {
        ZipOutputStream(file.outputStream()).use { z ->
            fun put(name: String, text: String) { z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray()); z.closeEntry() }
            put("mimetype", "application/epub+zip")
            put("META-INF/container.xml", """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
            val meta = if (cover) """<meta name="cover" content="img"/>""" else ""
            val item = if (cover) """<item id="img" href="images/cover+1%2Ejpg" media-type="image/jpeg"/>""" else ""
            put("OEBPS/content.opf", """<?xml version="1.0" encoding="UTF-8"?><package xmlns="http://www.idpf.org/2007/opf" version="2.0"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>$title</dc:title><dc:creator>$author</dc:creator>$meta</metadata><manifest>$item</manifest></package>""")
        }
    }
}
