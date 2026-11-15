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

/**
 * LibreraX(1.2.87·1.2.90): 서재 리스트·그리드 보기 칸(Compose, 요소 이름 없음), 읽는 화면의 메뉴(제목·저자·쪽 막대)와 상태 줄, 읽는 화면 가리기,
 * 미디어 서비스의 최근 책에서 표지 주소 고르기.
 * 화면은 크레마 페블 Play 에뮬레이터(1072×1456)에서 본 모양 그대로
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibreraXTest {
    private fun node(cls: String, r: Rect, text: String? = null, click: Boolean = false, long: Boolean = false, vararg kids: AccessibilityNodeInfo) =
        AccessibilityNodeInfo.obtain().apply {
            className = cls
            setBoundsInScreen(r)
            text?.let { this.text = it }
            isClickable = click
            isLongClickable = long
            isVisibleToUser = true
            kids.forEach { shadowOf(this).addChild(it) }
        }

    private fun view(l: Int, t: Int, r: Int, b: Int, vararg kids: AccessibilityNodeInfo) = node("android.view.View", Rect(l, t, r, b), null, false, false, *kids)
    private fun button(l: Int, t: Int, r: Int, b: Int, vararg kids: AccessibilityNodeInfo) = node("android.view.View", Rect(l, t, r, b), null, true, false, *kids)
    private fun text(l: Int, t: Int, r: Int, b: Int, s: String) = node("android.widget.TextView", Rect(l, t, r, b), s)

    /** 서재 칸: 누르기·길게 누르기가 되는 묶음 > [표지 묶음, 글자들] */
    private fun cell(cell: Rect, cover: Rect, vararg texts: String): AccessibilityNodeInfo {
        val box = view(cover.left, cover.top, cover.right, cover.bottom, view(cover.left, cover.top, cover.right, cover.bottom))
        val lines = texts.mapIndexed { i, s -> text(cell.left + 4, cover.bottom + 4 + i * 45, cell.right, cover.bottom + 49 + i * 45, s) }
        return node("android.view.View", cell, null, true, true, box, *lines.toTypedArray())
    }

    /** 위 탭 하나(글자, 수가 있으면 수 딱지) */
    private fun tab(l: Int, r: Int, top: Int, bottom: Int, label: String, count: String? = null, long: Boolean = false) = node(
        "android.view.View", Rect(l, top, r, bottom), null, true, long,
        *listOfNotNull(text(l + 49, top + 22, r - 15, bottom - 23, label), count?.let { view(r - 77, top, r + 4, bottom, text(r - 37, top + 34, r - 27, bottom - 34, it)) }).toTypedArray(),
    )

    /** 서재 화면: 책 묶음(넘기는 줄), 위 막대, 위 탭 줄, 아래에 떠 있는 탭 줄 */
    private fun shelf(vararg cells: AccessibilityNodeInfo) = node(
        "android.widget.FrameLayout", Rect(0, 0, 1072, 1456), null, false, false,
        view(0, 0, 1072, 1456,
            view(0, 0, 1072, 1456,
                view(0, 212, 1072, 1456, text(30, 602, 1042, 647, "2026"), *cells),
                view(0, 0, 1072, 143, button(15, 49, 257, 139, text(26, 70, 182, 118, "LibreraX")), button(822, 50, 901, 140), node("android.view.View", Rect(980, 50, 1070, 140), null, true, true)),
                node("android.widget.HorizontalScrollView", Rect(0, 143, 1072, 212), null, false, false,
                    view(23, 133, 1050, 223,
                        tab(23, 235, 133, 223, "라이브러리", "7", long = true), tab(239, 421, 133, 223, "지금 읽는 중"),
                        tab(425, 580, 133, 223, "최근 폴더", long = true), tab(584, 733, 133, 223, "즐겨찾기", long = true),
                    )),
            ),
            view(0, 1309, 1072, 1456),
            view(28, 1349, 1044, 1439,
                button(32, 1349, 228, 1439, text(88, 1400, 173, 1426, "라이브러리")), button(236, 1349, 431, 1439, text(317, 1400, 351, 1426, "폴더")),
                button(439, 1349, 634, 1439, text(503, 1400, 571, 1426, "클라우드")), button(642, 1349, 837, 1439, text(723, 1400, 757, 1426, "노트")),
                button(845, 1349, 1040, 1439, text(926, 1400, 960, 1426, "설정")),
            ),
        ),
    )

    /** 읽는 화면 메뉴: 위 막대(표지·제목 묶음·단추들), 서재 줄(탭·글자 없는 칸), 쪽 막대, 아래 단추 줄 */
    private fun menu(page: String, total: String, vararg title: String) = node(
        "android.widget.FrameLayout", Rect(0, 0, 1072, 1456), null, false, false,
        view(0, 0, 1072, 1456,
            view(0, 0, 1072, 45),
            button(0, 45, 1072, 242,
                button(400, 53, 490, 143, view(402, 55, 488, 141)),
                button(507, 53, 685, 143, *title.mapIndexed { i, s -> text(515, 53 + i * 45, 677, 98 + i * 45, s) }.toTypedArray()),
                button(6, 148, 96, 238), button(296, 148, 386, 238), button(977, 148, 1067, 238),
            ),
            button(0, 858, 1072, 1456,
                node("android.widget.HorizontalScrollView", Rect(0, 858, 1072, 927), null, false, false,
                    tab(15, 227, 848, 927, "라이브러리", "7"), tab(231, 428, 848, 927, "최근 폴더", "1"), tab(432, 581, 848, 927, "즐겨찾기")),
                view(11, 927, 1061, 1184,
                    node("android.view.View", Rect(11, 927, 182, 1184), null, true, true, view(11, 927, 182, 1184, text(40, 951, 165, 997, "이상한 나라의 앨리스"))),
                    node("android.view.View", Rect(197, 927, 368, 1184), null, true, true, view(197, 927, 368, 1184)),
                ),
                button(58, 1189, 148, 1279), button(203, 1189, 293, 1279),
                text(23, 1298, 42, 1343, page),
                node("android.widget.SeekBar", Rect(46, 1279, 1008, 1362)),
                text(1012, 1298, 1049, 1343, total),
                button(5, 1366, 88, 1456),
                button(174, 1366, 895, 1456, text(467, 1388, 492, 1433, "< "), text(492, 1388, 580, 1433, "페이지"), text(580, 1388, 606, 1433, " >")),
            ),
        ),
    )

    /** 메뉴를 닫은 읽는 화면: 상태 줄만(누를 수 있는 것 없음). [status] 가 false 면 상태 줄도 끈 것 */
    private fun reading(page: String = "3", total: String = "81", status: Boolean = true) = node(
        "android.widget.FrameLayout", Rect(0, 0, 1072, 1456), null, false, false,
        view(0, 0, 1072, 1456,
            *(if (status) arrayOf(
                text(30, 1403, 155, 1448, "남은 페이지: "), text(169, 1403, 195, 1448, "000"), text(155, 1403, 169, 1448, "5"),
                text(809, 1403, 868, 1448, "00:24"), view(862, 1381, 952, 1456, text(893, 1417, 920, 1435, "100")),
                text(953, 1403, 979, 1448, "000"), text(979, 1403, 993, 1448, page), text(993, 1403, 1042, 1448, " / $total"),
            ) else emptyArray()),
            view(0, 0, 1072, 68),
        ),
    )

    private fun windows(vararg roots: AccessibilityNodeInfo) = AppWindows(roots.first(), roots.toList())

    @Test fun `그리드 보기 칸은 제목·저자, 진행률은 없고 표지는 자르지 않는다`() {
        val c = LibreraX.shelfCells(shelf(cell(Rect(371, 663, 702, 1303), Rect(371, 663, 702, 1160), "이어 보기 책", "남궁억", "EPUB"))).single()
        assertEquals("이어 보기 책", c.title)
        assertEquals("남궁억", c.author)
        assertEquals(-1, c.progress)
        assertNull(c.thumb)
        assertEquals(Rect(371, 663, 702, 1303), c.cell)
    }

    @Test fun `리스트 보기 칸은 정보 줄의 진행률도 읽는다`() {
        val c = LibreraX.shelfCells(shelf(cell(Rect(0, 985, 1072, 1150), Rect(23, 998, 116, 1137), "이어 보기 책", "남궁억", "3%  EPUB  139 kB (81)  26. 10. 21."))).single()
        assertEquals("이어 보기 책", c.title)
        assertEquals("남궁억", c.author)
        assertEquals(3, c.progress)
    }

    @Test fun `저자 자리의 폴더는 저자가 아니다`() {
        val c = LibreraX.shelfCells(shelf(cell(Rect(0, 820, 1072, 985), Rect(23, 833, 116, 972), "ko-p", "[Download]", "EPUB  12 kB  26. 10. 24."))).single()
        assertEquals("ko-p", c.title)
        assertEquals("", c.author)
    }

    @Test fun `글자가 제목과 정보 줄뿐이면 저자는 없다`() {
        val c = LibreraX.shelfCells(shelf(cell(Rect(0, 820, 1072, 985), Rect(23, 833, 116, 972), "ezview_doc", "20%  PDF  59 kB (5)  26. 10. 21."))).single()
        assertEquals("ezview_doc", c.title)
        assertEquals("", c.author)
        assertEquals(20, c.progress)
    }

    @Test fun `탭·위 막대 단추·글자 없는 칸은 책 칸이 아니다`() {
        assertEquals(emptyList<Cell>(), LibreraX.shelfCells(shelf(node("android.view.View", Rect(23, 316, 188, 564), null, true, true, view(23, 316, 188, 564)))))
    }

    @Test fun `누른 요소가 칸이면 그 칸, 탭이면 칸이 아니다`() {
        val book = cell(Rect(371, 663, 702, 1303), Rect(371, 663, 702, 1160), "이어 보기 책", "남궁억", "EPUB")
        val root = shelf(book)
        assertEquals("이어 보기 책", LibreraX.clickedCell(book, emptyList(), windows(root), null)?.title)
        assertNull(LibreraX.clickedCell(button(236, 1349, 431, 1439, text(317, 1400, 351, 1426, "폴더")), emptyList(), windows(root), null))
    }

    @Test fun `누른 요소에서 아래를 못 읽어도 자리가 같은 칸으로 안다`() {
        val root = shelf(cell(Rect(371, 663, 702, 1303), Rect(371, 663, 702, 1160), "이어 보기 책", "남궁억", "EPUB"))
        val bare = node("android.widget.TextView", Rect(371, 663, 702, 1303), null, true, true)
        assertEquals("이어 보기 책", LibreraX.clickedCell(bare, emptyList(), windows(root), null)?.title)
        // 칸 밑을 지나는 아래 탭은 자리가 달라 칸이 아니다
        assertNull(LibreraX.clickedCell(node("android.widget.TextView", Rect(439, 1349, 634, 1439), null, true), emptyList(), windows(root), null))
    }

    @Test fun `메뉴가 보이면 읽는 화면이고 제목·저자·쪽을 읽는다`() {
        val root = menu("3", "81", "이어 보기 책", "남궁억")
        assertTrue(LibreraX.showsViewer(root))
        val info = LibreraX.readViewer(windows(root))
        assertEquals("이어 보기 책", info.title)
        assertEquals("남궁억", info.author)
        assertEquals(3, info.progress)
        // 메뉴의 서재 줄은 서재 칸으로 읽지 않는다
        assertEquals(emptyList<Cell>(), LibreraX.shelfCells(root))
    }

    @Test fun `책을 여는 중이라 쪽이 아직 숫자가 아니어도 메뉴가 보이면 읽는 화면이다`() {
        val root = menu("", "…", "이어 보기 책", "남궁억")
        assertTrue(LibreraX.showsViewer(root))
        val info = LibreraX.readViewer(windows(root))
        assertEquals("이어 보기 책", info.title)
        assertEquals(-1, info.progress)
    }

    @Test fun `PDF 는 메뉴에 파일 이름만 있고, 저자 자리의 폴더는 저자가 아니다`() {
        val pdf = LibreraX.readViewer(windows(menu("1", "5", "ezview_doc")))
        assertEquals("ezview_doc", pdf.title)
        assertEquals("", pdf.author)
        assertEquals(20, pdf.progress)
        assertEquals("", LibreraX.readViewer(windows(menu("1", "7", "ko-p", "[Download]"))).author)
    }

    @Test fun `메뉴를 닫은 읽는 화면은 누를 것이 없고 상태 줄의 쪽을 읽는다`() {
        val root = reading("3", "81")
        assertTrue(LibreraX.showsViewer(root))
        val info = LibreraX.readViewer(windows(root))
        assertNull(info.title)
        assertEquals(3, info.progress)
    }

    @Test fun `상태 줄을 꺼도 읽는 화면이고 진행률은 모른다`() {
        val root = reading(status = false)
        assertTrue(LibreraX.showsViewer(root))
        assertEquals(-1, LibreraX.readViewer(windows(root)).progress)
    }

    @Test fun `서재는 읽는 화면이 아니다`() {
        val root = shelf(cell(Rect(371, 663, 702, 1303), Rect(371, 663, 702, 1160), "이어 보기 책", "남궁억", "EPUB"))
        assertFalse(LibreraX.showsViewer(root))
        assertFalse(LibreraX.viewerIn(windows(root)))
    }

    @Test fun `읽기 설정 창이 메뉴 위에 떠 있어도 읽는 화면이다`() {
        val dialog = node("android.view.ViewGroup", Rect(75, 312, 997, 1188), null, false, false, text(102, 337, 877, 394, "설정"), button(880, 320, 990, 410))
        assertTrue(LibreraX.viewerIn(windows(dialog, menu("3", "81", "이어 보기 책", "남궁억"))))
    }

    /**
     * TTS 창(1.2.90, 화면 전체를 덮는 따로 뜬 창): 머리 `TTS`·단추, 넘기는 줄 안에 이름 · 값 줄(`Engine` · 엔진 이름, `Voice` · 언어)과 속도.
     * [slider] 면 속도가 막대(앞뒤가 글자인 `SeekBar`, 판에 따라 그럴 수 있는 모양)
     */
    private fun tts(slider: Boolean = false) = node(
        "android.widget.FrameLayout", Rect(0, 45, 1072, 1455), null, false, false,
        view(76, 313, 997, 1188,
            view(76, 313, 997, 418, text(102, 342, 667, 389, "TTS"), button(900, 321, 990, 411)),
            node("android.widget.ScrollView", Rect(76, 418, 997, 1188), null, false, false,
                view(99, 441, 974, 966,
                    button(99, 434, 974, 516, text(129, 456, 207, 501, "Engine"), text(230, 456, 462, 501, "Google TTS engine")),
                    button(99, 516, 974, 636, text(129, 554, 207, 599, "Voice"), text(230, 531, 318, 576, "English")),
                    *(if (slider) arrayOf(text(129, 657, 207, 702, "Speed"), node("android.widget.SeekBar", Rect(230, 657, 792, 702)), text(800, 657, 860, 702, "1.0"))
                    else arrayOf(text(129, 657, 207, 702, "Speed"), text(230, 657, 792, 702, "1.0"))),
                ),
            ),
        ),
    )

    @Test fun `TTS 창의 엔진 줄은 책 제목이 아니다`() {
        // 메뉴 위에 떠도 제목은 메뉴의 책
        assertEquals("이어 보기 책", LibreraX.readViewer(windows(tts(), menu("3", "81", "이어 보기 책", "남궁억"))).title)
        assertEquals("이어 보기 책", LibreraX.readViewer(windows(tts(slider = true), menu("3", "81", "이어 보기 책", "남궁억"))).title)
        // TTS 창만 보이고 그 안에 막대가 있어도 `Engine` 을 제목으로 읽지 않는다
        val alone = LibreraX.readViewer(windows(tts(slider = true)))
        assertNull(alone.title)
        assertEquals(-1, alone.progress)
        assertNull(LibreraX.readViewer(windows(tts())).title)
    }

    @Test fun `서재 위에 누를 것 없는 창이 떠도 읽는 화면이 아니다`() {
        val note = node("android.view.ViewGroup", Rect(75, 312, 997, 500), null, false, false, text(102, 337, 877, 394, "불러오는 중"))
        assertFalse(LibreraX.viewerIn(windows(note, shelf())))
    }

    @Test fun `사용 기록의 별칭 이름도 읽는 화면이 보인 동안만 센다`() {
        assertEquals(setOf("com.librerax.ComposeMainActivity", "com.librerax.reader.ReaderActivity"), LibreraX.markedScreens)
        assertEquals(ViewerTime.MARKS_CLOSED, LibreraX.viewerTime)
    }

    private fun book(title: String) = com.woody.booklauncher.ShelfBook("b-1", title, "", null, 0L)

    @Test fun `미디어 항목은 책 경로·제목·표지 주소로 읽고, 책이 아닌 항목은 버린다`() {
        val icon = android.net.Uri.parse("content://com.librerax.covers/323608571")
        val r = LibreraX.recentItem("book:/storage/emulated/0/Android/data/com.librerax/files/Books/cover-test.epub", "표지 있는 시험 책", icon)!!
        assertEquals("/storage/emulated/0/Android/data/com.librerax/files/Books/cover-test.epub", r.path)
        assertEquals("표지 있는 시험 책", r.title)
        assertEquals(icon, r.cover)
        assertNull(LibreraX.recentItem("__favorites__", "즐겨찾기", null))
        assertNull(LibreraX.recentItem("book:", "제목", null))
        assertNull(LibreraX.recentItem("book:/a.epub", "", null))
    }

    @Test fun `최근 책 가운데 그 책 제목의 첫 줄에서 표지를 고른다`() {
        val a = android.net.Uri.parse("content://com.librerax.covers/1")
        val b = android.net.Uri.parse("content://com.librerax.covers/2")
        val items = listOfNotNull(
            LibreraX.recentItem("book:/x/new.epub", "이어 보기 책", a),
            LibreraX.recentItem("book:/x/old.epub", "이어 보기 책", b),
            LibreraX.recentItem("book:/x/alice.epub", "이상한 나라의 앨리스", null),
        )
        assertEquals(a, LibreraX.recentFor(items, book("이어 보기 책"))?.cover)
        // 표지를 아직 만들지 않은 책은 주소가 없다
        assertNull(LibreraX.recentFor(items, book("이상한 나라의 앨리스"))?.cover)
        assertNull(LibreraX.recentFor(items, book("없는 책")))
    }

    @Test fun `PDF 는 최근 책의 제목이 파일 안의 제목이라 경로의 파일 이름으로 찾는다`() {
        val doc = "content://com.android.providers.downloads.documents/tree/raw%3A%2Fstorage%2Femulated%2F0%2FDownload/document/raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2FSPIEGEL_2026_46.pdf"
        val items = listOfNotNull(
            LibreraX.recentItem("book:/x/other.epub", "SPIEGEL", null),
            LibreraX.recentItem("book:$doc", "DER SPIEGEL 46/2026", null),
        )
        assertEquals(doc, LibreraX.recentFor(items, book("SPIEGEL_2026_46"))?.path)
        // 제목이 맞는 줄이 있으면 그 줄
        assertEquals("/x/other.epub", LibreraX.recentFor(items, book("SPIEGEL"))?.path)
        assertEquals("ezview_doc", LibreraX.fileTitle("content://com.android.externalstorage.documents/tree/primary%3ABooks/document/primary%3Aezview_doc.pdf"))
        assertEquals("cover-test", LibreraX.fileTitle("/storage/emulated/0/Android/data/com.librerax/files/Books/cover-test.epub"))
    }

    private val context get() = org.robolectric.RuntimeEnvironment.getApplication()
    private fun prefs() = com.woody.booklauncher.Storage.of(context).getSharedPreferences(com.woody.booklauncher.WatchCore.WATCH_PREFS, android.content.Context.MODE_PRIVATE)
    private fun home(book: com.woody.booklauncher.ShelfBook) =
        LibreraX.open(HomeContext(context, android.content.ComponentName("com.librerax", "com.librerax.ComposeMainActivity")), book)

    @Test fun `적어 둔 문서 주소로 홈에서 LibreraX 의 읽는 화면을 연다`() {
        val doc = "content://com.android.externalstorage.documents/tree/primary%3ABooks/document/primary%3ABooks%2Fplay_book.epub"
        LibreraX.saveBookPath(prefs(), "b-1", doc)
        val r = home(book("이어 보기 책")) as OpenResult.Start
        assertEquals("com.librerax/com.librerax.reader.ReaderActivity", r.intent.component?.flattenToString())
        assertEquals(android.content.Intent.ACTION_VIEW, r.intent.action)
        assertEquals(doc, r.intent.dataString)
    }

    @Test fun `LibreraX 폴더의 사본은 파일로 열고, 경로를 모르면 앱을 연다`() {
        val copy = "/storage/emulated/0/Android/data/com.librerax/files/Books/cover-test.epub"
        LibreraX.saveBookPath(prefs(), "b-1", copy)
        val r = home(book("표지 있는 시험 책")) as OpenResult.File
        assertEquals(copy, r.path)
        assertEquals("com.librerax.reader.ReaderActivity", r.component.className)
        assertEquals(OpenResult.Launch, home(com.woody.booklauncher.ShelfBook("b-2", "모르는 책", "", null, 0L)))
    }

    @Test fun `문서 주소·절대 경로가 아닌 것은 적지 않는다`() {
        assertFalse(LibreraX.saveBookPath(prefs(), "b-3", "relative/a.epub"))
        assertFalse(LibreraX.saveBookPath(prefs(), "b-3", "gdrive://abc/Book.epub"))
        assertNull(LibreraX.bookPath(context, "b-3"))
        assertTrue(LibreraX.saveBookPath(prefs(), "b-3", "/storage/emulated/0/Android/data/com.librerax/files/Books/a.epub"))
    }

    @Test fun `기록에는 경로의 모양만 남긴다`() {
        assertEquals("content com.android.providers.downloads.documents",
            LibreraX.pathKind("content://com.android.providers.downloads.documents/tree/raw%3A%2Fstorage/document/raw%3A%2Fstorage%2Fa.epub"))
        assertEquals("file", LibreraX.pathKind("/storage/emulated/0/Android/data/com.librerax/files/OPDS/Pride and Prejudice.epub"))
        assertEquals("gdrive", LibreraX.pathKind("gdrive://abc/Book.epub"))
        assertEquals("?", LibreraX.pathKind("relative/a.epub"))
    }
}
