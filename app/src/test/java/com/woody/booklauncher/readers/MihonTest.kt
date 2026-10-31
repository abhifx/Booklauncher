package com.woody.booklauncher.readers

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Mihon 작품 상세(Compose, 이름 없음)와 읽는 화면 메뉴의 작품 제목. 요소 나무 때문에 Robolectric. 자리는 에뮬레이터(페블)에서 본 그대로 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MihonTest {
    private fun node(
        cls: String, l: Int, t: Int, r: Int, b: Int, text: String? = null, desc: String? = null, click: Boolean = false, id: String? = null,
        vararg kids: AccessibilityNodeInfo,
    ) = AccessibilityNodeInfo.obtain().apply {
        className = cls
        setBoundsInScreen(Rect(l, t, r, b))
        text?.let { this.text = it }
        desc?.let { contentDescription = it }
        isClickable = click
        id?.let { viewIdResourceName = "${Mihon.pkg}:id/$it" }
        kids.forEach { shadowOf(this).addChild(it) }
    }

    private fun view(l: Int, t: Int, r: Int, b: Int, vararg kids: AccessibilityNodeInfo) = node("android.view.View", l, t, r, b, null, null, false, null, *kids)
    private fun text(l: Int, t: Int, r: Int, b: Int, s: String, click: Boolean = false) = node("android.widget.TextView", l, t, r, b, s, null, click)

    /** 작품 상세 머리: 뒤 그림 · 표지 단추 · 제목 · 저자 줄 · 상태 줄 */
    private fun detail(title: String, author: String) = view(0, 0, 1072, 1456,
        view(0, 0, 1072, 477),
        node("android.widget.Button", 30, 195, 218, 477, null, "표지", true),
        text(248, 242, 536, 332, title, click = true),
        view(248, 299, 408, 389, text(286, 299, 408, 375, author, click = true)),
        view(248, 349, 521, 439, text(286, 375, 367, 413, "연재 중"), text(367, 375, 391, 413, " • "), text(391, 349, 521, 439, "로컬 저장소", click = true)),
    )

    @Test fun `작품 상세의 제목·저자·표지`() {
        val bar = view(0, 0, 1072, 165, view(0, 45, 1072, 165, node("android.view.View", 9, 61, 99, 151, null, null, true)))
        val cell = Mihon.detail(view(0, 0, 1072, 1456, detail("Red Moon Story", "Lee Writer"), bar))
        assertEquals("Red Moon Story", cell?.title)
        assertEquals("Lee Writer", cell?.author)
        assertEquals(Rect(30, 195, 218, 477), cell?.thumb)
    }

    @Test fun `Kahon 은 표지가 설명과 단추를 품은 묶음`() {
        val header = view(0, 0, 1072, 1456,
            view(0, 0, 1072, 477),
            node("android.view.View", 30, 195, 218, 477, null, null, true, null,
                node("android.view.View", 30, 195, 218, 477, null, "표지"),
                node("android.widget.Button", 30, 195, 218, 477)),
            text(248, 242, 536, 332, "Red Moon Story", click = true),
            view(248, 299, 408, 389, text(286, 299, 408, 375, "Lee Writer", click = true)),
        )
        val cell = Kahon.detail(view(0, 0, 1072, 1456, header))
        assertEquals("Red Moon Story", cell?.title)
        assertEquals("Lee Writer", cell?.author)
        assertEquals(Rect(30, 195, 218, 477), cell?.thumb)
    }

    @Test fun `위 막대의 뒤로 단추와 제목은 작품 상세가 아니다`() {
        val bar = view(0, 45, 1072, 165,
            node("android.view.View", 9, 61, 99, 151, null, null, true, null, node("android.view.View", 31, 83, 76, 128, null, "위로 이동")),
            text(106, 74, 300, 135, "Red Moon Story", click = true),
        )
        assertNull(Mihon.detail(view(0, 0, 1072, 1456, bar)))
    }

    @Test fun `상세를 올려 표지가 위 막대 밑으로 들어가면 표지는 자르지 않는다`() {
        val bar = view(0, 0, 1072, 165, view(0, 45, 1072, 165, node("android.view.View", 9, 61, 99, 151, null, null, true)))
        val scrolled = view(0, 0, 1072, 1456,
            view(0, 0, 1072, 336),
            node("android.widget.Button", 30, 54, 218, 336, null, "표지", true),
            text(248, 101, 442, 191, "Long Story", click = true),
            view(248, 158, 390, 248, text(286, 158, 390, 234, "작가 미상", click = true)),
        )
        val cell = Mihon.detail(view(0, 0, 1072, 1456, scrolled, bar))
        assertEquals("Long Story", cell?.title)
        assertNull(cell?.thumb)
    }

    /** 기록 탭 줄(에뮬레이터 페블에서 본 자리): 표지 단추 · 제목 · 화와 때 · 삭제 */
    private fun historyRow(top: Int, title: String) = node("android.view.View", 0, top, 1072, top + 180, null, null, true, null,
        node("android.widget.Button", 30, top + 15, 130, top + 165, null, null, true),
        text(160, top + 48, 353, top + 86, title),
        text(160, top + 94, 329, top + 132, "2화 - 오전 4:22"),
        view(975, top + 68, 1020, top + 113))

    @Test fun `기록 탭 줄의 표지와 제목, 아래 탭 줄에 걸린 표지는 자르지 않는다`() {
        val nav = view(0, 1306, 1072, 1456, node("android.view.View", 0, 1321, 214, 1456, null, null, true))
        val root = view(0, 0, 1072, 1456,
            view(0, 0, 1072, 1306, historyRow(233, "Red Moon Story"), historyRow(1213, "Long Story")),
            nav)
        val cells = Mihon.shelfCells(root)
        assertEquals(listOf("Red Moon Story", "Long Story"), cells.map { it.title })
        assertEquals(Rect(30, 248, 130, 398), cells[0].thumb)
        assertNull(cells[1].thumb)
        // Kahon 은 표지가 그림·단추를 품은 묶음
        val kahonRow = node("android.view.View", 0, 233, 1072, 413, null, null, true, null,
            node("android.view.View", 30, 248, 130, 398, null, null, true, null, view(30, 248, 130, 398), node("android.widget.Button", 30, 248, 130, 398)),
            text(160, 281, 353, 319, "Red Moon Story"), text(160, 327, 344, 365, "2화 - 오전 10:31"))
        assertEquals(Rect(30, 248, 130, 398), Kahon.shelfCells(view(0, 0, 1072, 1456, kahonRow)).single().thumb)
        // 작품 상세(설명 붙은 표지 단추, 눌리는 제목)는 기록 탭 줄이 아니다
        assertEquals(emptyList<Cell>(), Mihon.shelfCells(view(0, 0, 1072, 1456, detail("Red Moon Story", "Lee Writer"))))
    }

    /** 서재 작은 격자 칸(에뮬레이터 페블에서 본 자리): 표지 · 아래 그늘 · 표지 안의 제목 · 위 모서리 표시 둘 */
    private fun gridCell(left: Int, top: Int, title: String) = node("android.view.View", left - 8, top - 8, left + 247, top + 367, null, null, true, null,
        view(left, top, left + 239, top + 359),
        view(left, top + 241, left + 239, top + 359),
        text(left + 15, top + 310, left + 224, top + 344, title),
        view(left - 23, top - 20, left + 67, top + 70, text(left + 14, top + 10, left + 29, top + 40, "3")),
        view(left + 168, top - 20, left + 258, top + 70, text(left + 201, top + 10, left + 225, top + 40, "\uE2C7")))

    @Test fun `서재 격자 칸은 표지 위 제목·표시가 있어도 자르고, 아래 탭 줄에 걸린 칸은 자르지 않는다`() {
        val nav = view(0, 1306, 1072, 1456, node("android.view.View", 0, 1321, 214, 1456, null, null, true))
        val root = view(0, 0, 1072, 1456,
            view(0, 165, 1072, 1306, gridCell(23, 188, "Red Moon Story"), gridCell(23, 1100, "Long Story")),
            nav)
        val cells = Mihon.shelfCells(root)
        assertEquals(listOf("Red Moon Story", "Long Story"), cells.map { it.title })
        assertEquals(Rect(23, 188, 262, 547), cells[0].thumb)
        assertNull(cells[1].thumb)
    }

    /** 임시 Mihon 저장 폴더에서 [body] 를 돌리고 지운다 */
    private fun withStorage(body: (java.io.File) -> Unit) {
        val storage = java.nio.file.Files.createTempDirectory("mihon").toFile()
        try { body(storage) } finally { storage.deleteRecursively() }
    }

    private fun dir(parent: java.io.File, name: String, vararg files: Pair<String, String>) =
        java.io.File(parent, name).apply { mkdirs(); files.forEach { (n, t) -> java.io.File(this, n).writeText(t) } }

    @Test fun `로컬 만화 표지 그림·저자는 작품 폴더에서(폴더 이름, 없으면 details·ComicInfo 의 제목)`() = withStorage { storage ->
        val local = java.io.File(storage, "local")
        dir(local, "Red Moon Story", "cover.jpg" to "x", "Chapter 1.cbz" to "x", "details.json" to """{"title": "Red Moon Story", "author": "Lee \"Red\" Writer"}""")
        dir(local, "blue_sky", "details.json" to """{"title": "Blue Sky Manga", "author": "Kim Author"}""", "Cover.PNG" to "x")
        dir(local, "green", "ComicInfo.xml" to "<ComicInfo><Series>Green Leaf</Series><Writer>Park &amp; Choi</Writer></ComicInfo>", "cover.webp" to "x")
        dir(local, "No Cover", "Chapter 1.cbz" to "x")
        val storages = listOf(storage)
        val red = Mihon.fileInfoIn(storages, "Red Moon Story")
        assertEquals("cover.jpg", red.cover?.name)
        assertEquals("Lee \"Red\" Writer", red.author)
        assertEquals("Cover.PNG", Mihon.fileInfoIn(storages, "Blue Sky Manga").cover?.name)
        val green = Mihon.fileInfoIn(storages, "Green Leaf")
        assertEquals("cover.webp", green.cover?.name)
        assertEquals("Park & Choi", green.author)
        assertNull(Mihon.fileInfoIn(storages, "No Cover").cover)
        assertNull(Mihon.fileInfoIn(storages, "Online Only").cover)
    }

    @Test fun `받은 화의 ComicInfo 에서 저자(화 폴더, CBZ, 폴더 이름이 다르면 Series 로)`() = withStorage { storage ->
        val source = java.io.File(storage, "downloads/MangaDex (EN)")
        dir(java.io.File(source, "Solo Hunter"), "Ch. 1", "ComicInfo.xml" to "<ComicInfo><Series>Solo Hunter</Series><Writer>Chugong</Writer></ComicInfo>", "001.jpg" to "x")
        val cbzManga = dir(source, "Night Walk")
        java.util.zip.ZipOutputStream(java.io.File(cbzManga, "Ch. 1.cbz").outputStream()).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("001.jpg")); z.write(1); z.closeEntry()
            z.putNextEntry(java.util.zip.ZipEntry("ComicInfo.xml")); z.write("<ComicInfo><Series>Night Walk</Series><Writer>Han Writer</Writer></ComicInfo>".toByteArray()); z.closeEntry()
        }
        // 파일 이름에 영어만 쓰는 설정이면 한글 제목이 폴더 이름에서 빠진다
        dir(java.io.File(source, "_"), "1화", "ComicInfo.xml" to "<ComicInfo><Series>나 혼자 산책</Series><Writer>김작가</Writer></ComicInfo>")
        val storages = listOf(storage)
        assertEquals("Chugong", Mihon.fileInfoIn(storages, "Solo Hunter").author)
        assertEquals("Han Writer", Mihon.fileInfoIn(storages, "Night Walk").author)
        assertEquals("김작가", Mihon.fileInfoIn(storages, "나 혼자 산책").author)
        assertNull(Mihon.fileInfoIn(storages, "Solo Hunter", withAuthor = false).author)
    }

    /** protobuf 길이 붙은 필드 하나 */
    private fun ld(field: Int, value: ByteArray): ByteArray {
        fun varint(v: Int): ByteArray { val out = java.io.ByteArrayOutputStream(); var x = v; while (x >= 0x80) { out.write(x and 0x7f or 0x80); x = x ushr 7 }; out.write(x); return out.toByteArray() }
        return varint(field shl 3 or 2) + varint(value.size) + value
    }

    @Test fun `자동 백업에서 서재 작품의 저자(가장 새 백업)`() = withStorage { storage ->
        fun manga(title: String, author: String?) =
            byteArrayOf(0x08, 0x96.toByte(), 0x01) + ld(2, "/m/1".toByteArray()) + ld(3, title.toByteArray()) + (author?.let { ld(5, it.toByteArray()) } ?: byteArrayOf()) +
                ld(16, ld(1, "/ch/1".toByteArray()))
        val backup = ld(1, manga("Online Manga", "Oda")) + ld(1, manga("No Author", null)) + ld(2, ld(1, "Default".toByteArray()))
        val dir = java.io.File(storage, "autobackup").apply { mkdirs() }
        java.io.File(dir, "app.mihon_2026-10-25_00-00.tachibk").apply {
            java.util.zip.GZIPOutputStream(outputStream()).use { it.write(ld(1, manga("Online Manga", "Old"))) }
            setLastModified(1_000_000L)
        }
        java.io.File(dir, "app.mihon_2026-10-26_00-00.tachibk").apply {
            java.util.zip.GZIPOutputStream(outputStream()).use { it.write(backup) }
            setLastModified(2_000_000L)
        }
        val storages = listOf(storage)
        assertEquals("Oda", Mihon.fileInfoIn(storages, "Online Manga").author)
        assertNull(Mihon.fileInfoIn(storages, "No Author").author)
        // 깨진 묶음은 거기서 멈춘다
        assertEquals(emptyMap<String, String>(), Mihon.backupAuthorsOf(byteArrayOf(0x0a, 0x7f, 0x01)))
    }

    @Test fun `기록 탭 칸(설명 없는 표지 단추, 눌리지 않는 제목)은 작품 상세가 아니다`() {
        val row = view(0, 233, 1072, 413,
            node("android.widget.Button", 30, 248, 130, 398, null, null, true),
            text(160, 281, 353, 319, "Red Moon Story"),
            text(160, 327, 329, 365, "2화 - 오전 4:22"),
        )
        assertNull(Mihon.detail(view(0, 0, 1072, 1456, row)))
    }

    @Test fun `읽는 화면 메뉴의 위 줄이 작품 제목, 그 아래가 화 이름, 메뉴가 없으면 모른다`() {
        val menu = node("androidx.compose.ui.platform.ComposeView", 0, 0, 1072, 1456, null, null, false, "compose_overlay",
            view(0, 0, 1072, 1456,
                text(106, 112, 224, 150, "Chapter 1"),
                text(106, 59, 394, 112, "Red Moon Story"),
                text(927, 1291, 937, 1321, "8"),
            ),
            text(509, 1430, 564, 1456, "1 / 8"),
        )
        assertEquals(listOf("Red Moon Story", "Chapter 1"), Mihon.menuLines(menu))
        val hidden = node("androidx.compose.ui.platform.ComposeView", 0, 0, 1072, 1456, null, null, false, "compose_overlay",
            text(509, 1430, 564, 1456, "1 / 8"))
        assertEquals(emptyList<String>(), Mihon.menuLines(hidden))
    }

    @Test fun `아래로 넘긴 작품 상세는 위 막대의 제목이 마지막으로 본 머리와 같을 때만 그 작품`() {
        // 2026-10-26 에뮬레이터 Mihon 0.21.3: 120화 작품을 넘기면 머리 요소가 없어지고 위 막대에 `뒤로 · 제목 · 필터 · 기타 옵션`
        fun bar(title: String) = view(0, 0, 1072, 1456,
            view(0, 0, 1072, 1321, node("android.view.View", 0, 51, 1072, 178, null, null, true, null, text(99, 74, 179, 112, "Chapter 010"))),
            view(0, 45, 1072, 165,
                node("android.view.View", 9, 61, 99, 151, null, null, true, null,
                    node("android.view.View", 31, 83, 76, 128, null, "위로 이동"), node("android.widget.Button", 16, 68, 91, 143)),
                view(106, 60, 354, 150, text(106, 78, 354, 131, title)),
                node("android.view.View", 885, 61, 975, 151, null, null, true, null, node("android.view.View", 907, 83, 952, 128, null, "필터")),
            ),
        )
        assertEquals("Long Journey", Mihon.barTitle(bar("Long Journey")))
        val details = MihonDetails(Mihon)
        // 머리를 보기 전에는 위 막대만으로 작품 상세라고 보지 않는다(찾아보기의 소스 화면도 같은 모양)
        assertNull(details.of(bar("Long Journey")))
        assertEquals("Red Moon Story", details.of(detail("Red Moon Story", "Lee Writer"))?.title)
        val scrolled = details.of(bar("Red Moon Story"))
        assertEquals("Red Moon Story", scrolled?.title)
        assertEquals("Lee Writer", scrolled?.author)
        assertNull(scrolled?.thumb)
        assertNull(details.of(bar("로컬 저장소")))
    }

    @Test fun `빈 표지를 알아본다(서재 격자 칸·상세·어두운 테마·크레마 화면 대비)`() {
        // 2026-10-26 에뮬레이터: 바탕 238 가운데 아이콘 225, 서재 격자 칸은 아래 그늘·흰 제목과 위 모서리 표시
        val grid = paint(0xEFEEF1) { x, y ->
            when {
                y > 0.65f -> if (y in 0.85f..0.92f && x in 0.06f..0.7f && (x * 40).toInt() % 2 == 0) 0xFFFFFF else 0x606060
                y < 0.1f && x < 0.18f -> 0x1565C0
                x in 0.38f..0.62f && y in 0.38f..0.54f -> 0xE1E1E3
                else -> null
            }
        }
        val detail = paint(0xEFEEF1) { x, y -> if (x in 0.38f..0.62f && y in 0.4f..0.56f) 0xE1E1E3 else null }
        val dark = paint(0x212121) { x, y -> if (x in 0.38f..0.62f && y in 0.4f..0.56f) 0x2E2E2E else null }
        for (px in listOf(grid, detail, dark, crema(grid))) assertTrue(Mihon.placeholder(px, W, H))
    }

    @Test fun `진짜 표지는 빈 표지가 아니다`() {
        // 흰 바탕 가운데 검은 제목, 한 가지 옅은 색 바탕에 제목, 흰 표지, 그림이 가득한 표지
        val whiteTitle = paint(0xFFFFFF) { x, y -> if (y in 0.3f..0.4f && x in 0.2f..0.8f && (x * 30).toInt() % 2 == 0) 0x101010 else null }
        val pastel = paint(0xF6D5DC) { x, y -> if (y in 0.3f..0.36f && x in 0.3f..0.7f) 0xA03050 else null }
        val white = paint(0xFFFFFF) { _, _ -> null }
        val art = paint(0x000000) { x, y -> ((x * 255).toInt() shl 16) or ((y * 255).toInt() shl 8) or 0x80 }
        for (px in listOf(whiteTitle, pastel, white, art)) assertFalse(Mihon.placeholder(px, W, H))
    }

    /** 바탕 [bg] 에 [at] 이 준 색(비율 자리마다, null 이면 바탕)을 칠한 그림 */
    private fun paint(bg: Int, at: (Float, Float) -> Int?) = IntArray(W * H) { i ->
        (0xFF000000.toInt()) or (at((i % W).toFloat() / W, (i / W).toFloat() / H) ?: bg)
    }

    /** 크레마 화면 대비 56 으로 어두워진 캡처: 채널마다 밝기^(1/0.37) */
    private fun crema(px: IntArray) = px.map { p ->
        (0..2).fold(0xFF000000.toInt()) { acc, i -> acc or ((Math.pow((p shr (i * 8) and 0xff) / 255.0, 1 / 0.37) * 255).toInt() shl (i * 8)) }
    }.toIntArray()

    private companion object {
        /** 서재 격자 칸 표지 크기(페블) */
        const val W = 238
        const val H = 329
    }
}
