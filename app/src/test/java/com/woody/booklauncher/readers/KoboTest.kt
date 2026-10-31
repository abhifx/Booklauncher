package com.woody.booklauncher.readers

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Kobo Books: My Books 칸·책 상세(Compose, 이름 없음)와 읽는 화면 메뉴의 쪽(React Native testID). 에뮬레이터 페블(Kobo 11.1.2)에서 본 자리. 요소 나무 때문에 Robolectric */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KoboTest {
    private fun node(
        cls: String, l: Int, t: Int, r: Int, b: Int, text: String? = null, vararg kids: AccessibilityNodeInfo,
        id: String? = null, desc: String? = null, click: Boolean = false, scroll: Boolean = false,
    ) = AccessibilityNodeInfo.obtain().apply {
        className = cls
        setBoundsInScreen(Rect(l, t, r, b))
        id?.let { viewIdResourceName = it }
        text?.let { this.text = it }
        desc?.let { contentDescription = it }
        isClickable = click
        isScrollable = scroll
        kids.forEach { shadowOf(this).addChild(it) }
    }

    private fun view(l: Int, t: Int, r: Int, b: Int, vararg kids: AccessibilityNodeInfo, desc: String? = null, click: Boolean = false, scroll: Boolean = false) =
        node("android.view.View", l, t, r, b, null, *kids, desc = desc, click = click, scroll = scroll)
    private fun text(l: Int, t: Int, r: Int, b: Int, s: String, click: Boolean = false) = node("android.widget.TextView", l, t, r, b, s, click = click)
    private fun button(l: Int, t: Int, r: Int, b: Int, label: String) = view(l, t, r, b, text(l + 400, t + 26, r - 400, b - 26, label), node("android.widget.Button", l, t, r, b), click = true)

    /** My Books 한 칸: 표지 자리·제목·`By 저자`·상태·메뉴 단추 */
    private fun libraryCell(top: Int, title: String, by: String, state: String) = view(0, top, 1072, top + 281,
        view(30, top + 31, 176, top + 250, view(30, top + 31, 176, top + 250)),
        text(206, top + 80, 354, top + 118, title), text(206, top + 126, 436, top + 159, by), text(206, top + 174, 299, top + 202, state),
        node("android.widget.ImageView", 975, top + 8, 1065, top + 98, desc = "Menu options", click = true),
        view(997, top + 206, 1072, top + 296), click = true)

    @Test fun `My Books 칸은 표지·제목·저자, 상태 글자는 저자가 아니다`() {
        val root = view(0, 45, 1072, 1456,
            view(0, 255, 1072, 1306,
                view(30, 285, 426, 375, text(68, 311, 343, 349, "Sort: Recent Activity"), click = true),
                text(943, 405, 1042, 443, "2 Books"),
                libraryCell(443, "The Hollow", "By Michael Connelly", "PREVIEW"),
                // 아래 끝에 걸려 표지 아래가 잘린 칸
                libraryCell(1100, "Moby Dick", "Herman Melville", "12% READ"),
                // `By ` 없는 저자 줄(다른 언어): 대소문자가 없는 이름은 상태 글자가 아니다
                libraryCell(724, "노르웨이의 숲", "무라카미 하루키", "PREVIEW"),
                scroll = true))
        val cells = Kobo.libraryCells(root)
        assertEquals(listOf("The Hollow", "Moby Dick", "노르웨이의 숲"), cells.map { it.title })
        assertEquals(listOf("Michael Connelly", "Herman Melville", "무라카미 하루키"), cells.map { it.author })
        assertEquals(Rect(30, 474, 176, 693), cells[0].thumb)
        assertNull(cells[1].thumb)
        assertEquals(-1, cells[0].progress)
    }

    @Test fun `홈의 스토어 줄 칸은 메뉴 단추가 없어 칸이 아니다`() {
        val root = view(0, 45, 1072, 1306,
            view(0, 595, 1072, 1240,
                view(397, 595, 734, 1202, view(397, 595, 734, 1100, view(397, 595, 734, 1100)),
                    text(397, 1123, 545, 1161, "The Hollow"), text(397, 1169, 627, 1202, "By Michael Connelly"), click = true),
                scroll = true),
            scroll = true)
        assertEquals(emptyList<Cell>(), Kobo.libraryCells(root))
        assertNull(Kobo.detail(root))
        // 홈을 넘기면 왼쪽 위의 빈 자리 뒤에 줄 제목·`View More` 단추가 온다(책 상세가 아니다)
        val scrolled = view(0, 45, 1072, 1306,
            view(0, 45, 397, 500),
            text(30, 520, 969, 567, "New Releases"),
            view(969, 498, 1059, 588, view(992, 521, 1037, 566, desc = "View More"), node("android.widget.Button", 986, 515, 1042, 571), click = true),
            scroll = true)
        assertNull(Kobo.detail(scrolled))
    }

    @Test fun `책 상세는 표지 자리 뒤 제목, 눌리는 글자가 저자`() {
        val root = view(0, 45, 1072, 1456,
            node("android.widget.ScrollView", 0, 165, 1072, 1456, null,
                view(403, 202, 669, 601, view(403, 202, 669, 601)),
                text(30, 608, 1042, 684, "The Hollow"),
                text(30, 699, 1042, 737, "A Harry Bosch Novel"),
                // 시리즈 링크는 혼자 한 줄(저자가 아니다)
                text(270, 735, 802, 773, "Book 24 - Harry Bosch", click = true),
                text(404, 752, 443, 790, "by "),
                text(443, 726, 669, 816, "Michael Connelly", click = true),
                view(427, 800, 646, 890, view(427, 828, 646, 861, desc = "rating 0.00 out of 5"), click = true),
                text(30, 914, 222, 952, "Buy the eBook"),
                button(30, 1062, 1042, 1152, "Add To Cart"),
                button(30, 1288, 1042, 1378, "Read Preview"),
                scroll = true),
            view(0, 45, 1072, 165, view(9, 61, 99, 151, view(31, 83, 76, 128, desc = "Back"), click = true)))
        val cell = Kobo.detail(root)!!
        assertEquals("The Hollow", cell.title)
        assertEquals("Michael Connelly", cell.author)
        assertEquals(Rect(403, 202, 669, 601), cell.thumb)
        assertEquals(listOf("The Hollow"), Kobo.shelfCells(root).map { it.title })
    }

    /** My Books 화면: 탭 줄(고른 탭은 눌리지 않는다), 정렬 단추, 칸들 */
    private fun myBooks(tab: String, sort: String, vararg cells: AccessibilityNodeInfo, sortShown: Boolean = true) = view(0, 45, 1072, 1306,
        view(0, 165, 934, 255,
            view(0, 165, 169, 255, text(36, 191, 134, 229, "eBooks"), click = tab != "eBooks"),
            view(169, 165, 388, 255, text(199, 191, 358, 229, "Audiobooks"), click = tab != "Audiobooks")),
        view(0, 255, 1072, 1306,
            *(if (sortShown) arrayOf(view(30, 285, 426, 375, text(68, 311, 343, 349, sort), click = true)) else emptyArray()),
            *cells, scroll = true))

    private val labels = Kobo.Labels("eBooks", "Sort: Recent Activity")

    @Test fun `최근 활동 순 eBooks 탭이 맨 위까지 보이면 맨 위 칸`() {
        val two = arrayOf(libraryCell(443, "Pride and Prejudice", "By Jane Austen", "PREVIEW"), libraryCell(724, "The Hollow", "By Michael Connelly", "PREVIEW"))
        assertEquals("Pride and Prejudice", Kobo.recentTop(myBooks("eBooks", "Sort: Recent Activity", *two), labels)?.title)
        // 다른 정렬·다른 탭·아래로 넘겨 정렬 단추가 안 보이면 모른다
        assertNull(Kobo.recentTop(myBooks("eBooks", "Sort: Title", *two), labels))
        assertNull(Kobo.recentTop(myBooks("Audiobooks", "Sort: Recent Activity", *two), labels))
        assertNull(Kobo.recentTop(myBooks("eBooks", "Sort: Recent Activity", *two, sortShown = false), labels))
    }

    @Test fun `읽는 화면 메뉴 아래의 쪽으로 진행률, 장 안의 쪽은 쓰지 않는다`() {
        val reader = view(0, 45, 1072, 1456,
            node("android.widget.TextView", 0, 1353, 1072, 1434, "1 of 1 in chapter", id = "page-footer-text"),
            view(0, 1120, 1072, 1391,
                node("android.widget.TextView", 161, 1143, 911, 1188, "Part One: The Red Halo", id = "epub-reader-rx-scrubber-headerText"),
                node("android.widget.TextView", 487, 1327, 585, 1365, "13 of 41", id = "epub-reader-rx-scrubber-bookLocation")))
        val shown = Kobo.readViewer(AppWindows(reader, listOf(reader)))
        assertEquals(31, shown.progress)
        assertNull(shown.title)
        // 메뉴가 없으면 모른다
        val plain = view(0, 45, 1072, 1456, node("android.widget.TextView", 0, 1353, 1072, 1434, "1 of 1 in chapter", id = "page-footer-text"))
        assertEquals(-1, Kobo.readViewer(AppWindows(plain, listOf(plain))).progress)
        assertNull(Kobo.pagesOf("Cover"))
        // 전체 쪽을 먼저 쓰는 언어도 작은 것이 지금 쪽
        assertEquals(31, Kobo.pagesOf("41 中 13"))
    }
}
