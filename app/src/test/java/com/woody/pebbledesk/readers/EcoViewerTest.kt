package com.woody.pebbledesk.readers

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 이씨오 범용뷰어 3.0(3.0.44): 서재 목록·격자 보기 칸(Compose, 요소 이름 없음)과 읽는 화면 웹 화면의 표지 단추·장 순서.
 * 화면은 크레마 페블 Play 에뮬레이터(1072×1456)에서 본 모양 그대로
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EcoViewerTest {
    private fun node(cls: String, l: Int, t: Int, r: Int, b: Int, text: String? = null, desc: String? = null, click: Boolean = false, vararg kids: AccessibilityNodeInfo) =
        AccessibilityNodeInfo.obtain().apply {
            className = cls
            setBoundsInScreen(Rect(l, t, r, b))
            text?.let { this.text = it }
            desc?.let { contentDescription = it }
            isClickable = click
            isVisibleToUser = true
            kids.forEach { shadowOf(this).addChild(it) }
        }

    private fun view(l: Int, t: Int, r: Int, b: Int, vararg kids: AccessibilityNodeInfo) = node("android.view.View", l, t, r, b, null, null, false, *kids)
    private fun text(l: Int, t: Int, r: Int, b: Int, s: String) = node("android.widget.TextView", l, t, r, b, s)

    /** 서재 칸 하나: 누를 수 있는 칸 > 묶음 > [표지 묶음(표지 그림·진행 막대), 제목, 날짜들, 반납 단추] */
    private fun cell(cell: Rect, cover: Rect, title: String, loan: String, due: String) = node(
        "android.view.View", cell.left, cell.top, cell.right, cell.bottom, null, null, true,
        view(cell.left + 8, cell.top + 8, cell.right - 8, cell.bottom - 8,
            view(cover.left, cover.top, cover.right, cover.bottom + 38,
                node("android.view.View", cover.left, cover.top, cover.right, cover.bottom, null, "책 표지 이미지"),
                node("android.widget.ProgressBar", cover.left, cover.bottom, cover.right, cover.bottom + 38),
            ),
            text(cover.left, cover.bottom + 40, cover.right, cover.bottom + 75, title),
            text(30, 598, 74, 623, "대출일"),
            text(30, 623, 104, 648, "반납예정일"),
            text(251, 598, 337, 623, loan),
            text(251, 623, 346, 648, due),
            node("android.view.View", 933, 351, 1042, 441, null, null, true, text(970, 381, 1006, 411, "반납"), node("android.widget.Button", 933, 358, 1042, 433)),
        ),
    )

    /** 서재 화면: 위 막대(도서관 이름·새로고침·로그아웃), 보기 단추, 칸들 */
    private fun shelf(vararg cells: AccessibilityNodeInfo) = node(
        "android.widget.FrameLayout", 0, 0, 1072, 1456, null, null, false,
        view(0, 45, 1072, 1456,
            view(0, 45, 1072, 157, text(30, 79, 170, 123, "경기도서관"), node("android.widget.Button", 885, 57, 975, 147, click = true)),
            node("android.view.View", 893, 173, 983, 262, null, null, true, node("android.view.View", 915, 195, 960, 240, null, "메뉴")),
            node("android.view.View", 983, 173, 1072, 262, null, null, true, node("android.view.View", 1005, 195, 1050, 240, null, "격자 정렬")),
            view(0, 262, 1072, 1456, *cells),
        ),
        node("android.widget.ProgressBar", 522, 0, 552, 23),
    )

    private val due = LocalDate.of(2026, 11, 14).toEpochDay()

    @Test fun `목록 보기 칸은 제목·반납 예정일·표지, 진행 막대는 쓰지 않는다`() {
        val cells = EcoViewer.shelfCells(shelf(cell(Rect(0, 262, 1072, 528), Rect(47, 292, 182, 471), "레 미제라블 3(한글판)", "2026. 11. 7.", "2026. 11. 14.")))
        val c = cells.single()
        assertEquals("레 미제라블 3(한글판)", c.title)
        assertEquals(due, c.due)
        assertEquals(-1, c.progress)
        assertEquals(Rect(47, 292, 182, 471), c.thumb)
        assertEquals(Rect(0, 262, 1072, 528), c.cell)
    }

    @Test fun `격자 보기 칸도 같은 모양으로 읽는다`() {
        val cells = EcoViewer.shelfCells(shelf(
            cell(Rect(21, 285, 354, 757), Rect(105, 293, 270, 517), "레 미제라블 3(한글판)", "2026. 11. 7.", "2026. 11. 14."),
            cell(Rect(370, 285, 703, 757), Rect(454, 293, 619, 517), "7일 만에 끝내는 책", "2026. 11. 1.", "2026. 11. 8."),
        ))
        assertEquals(listOf("레 미제라블 3(한글판)", "7일 만에 끝내는 책"), cells.map { it.title })
        assertEquals(Rect(105, 293, 270, 517), cells[0].thumb)
        // 제목의 `7일` 은 반납일이 아니다(날짜 글자만 본다)
        assertEquals(LocalDate.of(2026, 11, 8).toEpochDay(), cells[1].due)
    }

    @Test fun `칸이 없는 서재(대출한 책 없음)는 위 막대·보기 단추를 칸으로 보지 않는다`() = assertEquals(emptyList<Cell>(), EcoViewer.shelfCells(shelf()))

    /** 읽는 화면 웹 화면: 본문, 아래 메뉴(장 이름, `7` `/` `12`, `7 / 12`), 옆 메뉴 표지 단추 */
    private fun web(cover: String? = "표지 이미지 레 미제라블 3(한글판) 빅토르 위고", vararg menu: AccessibilityNodeInfo) = node(
        "android.webkit.WebView", 0, 0, 1072, 1456, null, null, true,
        node("android.webkit.WebView", 0, 0, 1072, 1456, null, null, false,
            node("android.widget.Button", 0, 0, 1072, 470, "이전 장", null, true,
                text(58, 82, 1035, 292, "‍ 3.  할아버지와 손자 ‍"),
                text(78, 721, 993, 1413, "질노르망 씨가 세르방도니 거리에서 살던 무렵, 그는 1 / 2 매우 훌륭한 상류의 몇몇 귀족 살롱에 출입하고 있었다."),
            ),
            *menu,
            *listOfNotNull(cover?.let { node("android.widget.Button", 1072, 150, 1072, 331, it, null, true) }).toTypedArray(),
            node("android.widget.Button", 0, 528, 0, 626, "7 3. 할아버지와 손자", null, true),
        ),
    )

    private val bottomMenu = arrayOf(
        text(45, 1312, 260, 1344, "3. 할아버지와 손자"),
        text(961, 1310, 978, 1340, "7"),
        text(975, 1310, 999, 1340, " / "),
        text(995, 1310, 1027, 1340, "12"),
        text(35, 1269, 101, 1297, "7 / 12"),
    )

    private val known = setOf("레 미제라블 3(한글판)")

    @Test fun `표지 단추를 아는 제목과 저자로 나누고 장 순서로 진행률`() {
        val info = EcoViewer.readWeb(web(menu = bottomMenu)) { it in known }.info
        assertEquals("레 미제라블 3(한글판)", info.title)
        assertEquals("빅토르 위고", info.author)
        assertEquals(58, info.progress)
    }

    @Test fun `모르는 제목이면 제목은 비우고 진행률만`() {
        val info = EcoViewer.readWeb(web(menu = bottomMenu)) { false }.info
        assertNull(info.title)
        assertEquals(58, info.progress)
    }

    @Test fun `장 순서 세 글자가 없으면 한 글자 7 슬래시 12, 둘 다 없으면 모름`() {
        assertEquals(25, EcoViewer.readWeb(web(null, text(35, 1269, 101, 1297, "3 / 12"))) { false }.info.progress)
        assertEquals(-1, EcoViewer.readWeb(web(null)) { false }.info.progress)
    }

    @Test fun `제목 나누기는 가장 긴 아는 앞부분, 저자가 없으면 통째`() {
        val titles = setOf("레 미제라블", "레 미제라블 3(한글판)")
        assertEquals("레 미제라블 3(한글판)" to "빅토르 위고", EcoViewer.splitCover("레 미제라블 3(한글판) 빅토르 위고") { it in titles })
        assertEquals("레 미제라블 3(한글판)" to "", EcoViewer.splitCover("레 미제라블 3(한글판)") { it in titles })
        assertNull(EcoViewer.splitCover("모르는 책 김작가") { it in titles })
    }

    @Test fun `부제가 있는 제목도 저자를 떼어 나눈다`() {
        assertEquals("사피엔스: 유인원에서 사이보그까지" to "유발 하라리",
            EcoViewer.splitCover("사피엔스: 유인원에서 사이보그까지 유발 하라리") { EcoViewer.knownTitle(it, sequenceOf("사피엔스: 유인원에서 사이보그까지")) })
    }

    @Test fun `빈 제목·기호뿐인 앞부분은 아는 제목이 아니다`() {
        val titles = sequenceOf("", "레 미제라블 3(한글판)")
        assertEquals(false, EcoViewer.knownTitle("★", titles))
        assertEquals(true, EcoViewer.knownTitle("레미제라블 3 (한글판)", titles))
        assertEquals("★ 레 미제라블 3(한글판)" to "빅토르 위고",
            EcoViewer.splitCover("★ 레 미제라블 3(한글판) 빅토르 위고") { EcoViewer.knownTitle(it, sequenceOf("", "★ 레 미제라블 3(한글판)")) })
    }

    @Test fun `PDF 뷰어(쪽 수 요소 numPages)는 PDF 로 알아보고, EPUB 뷰어는 아니다`() {
        val pages = node("android.widget.TextView", 508, 0, 598, 0, "/ 348").apply { viewIdResourceName = "numPages" }
        val pdf = node("android.webkit.WebView", 0, 0, 1072, 1456, null, null, true,
            node("android.widget.EditText", 399, 0, 502, 0, "1").apply { viewIdResourceName = "pageNumber" }, pages)
        val shown = EcoViewer.readWeb(pdf) { true }
        assertEquals(true, shown.pdf)
        assertNull(shown.info.title)
        assertEquals(false, EcoViewer.readWeb(web(menu = bottomMenu)) { it in known }.pdf)
    }

    @Test fun `지금 장이 전체보다 크면 버린다`() = assertEquals(-1, EcoViewer.chapterPercent(listOf("13", "/", "12")))
}
