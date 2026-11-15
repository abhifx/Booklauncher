package com.woody.booklauncher.readers

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 리디 서재 칸(React Native, 요소 이름 없음). 26.10 은 서재 표시 `libraryGnbMenus`, 26.11 은 머리 `모든 책장`.
 * 26.11 자리는 갤럭시 A23(1080×2408)에서 본 그대로(2026-11-13). 요소 나무 때문에 Robolectric
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RidiTest {
    private fun node(
        cls: String, l: Int, t: Int, r: Int, b: Int, text: String? = null, desc: String? = null, id: String? = null,
        vararg kids: AccessibilityNodeInfo,
    ) = AccessibilityNodeInfo.obtain().apply {
        className = cls
        setBoundsInScreen(Rect(l, t, r, b))
        text?.let { this.text = it }
        desc?.let { contentDescription = it }
        id?.let { viewIdResourceName = it }
        isVisibleToUser = true
        kids.forEach { shadowOf(this).addChild(it) }
    }

    private fun group(l: Int, t: Int, r: Int, b: Int, desc: String? = null, vararg kids: AccessibilityNodeInfo) =
        node("android.view.ViewGroup", l, t, r, b, null, desc, null, *kids)
    private fun text(l: Int, t: Int, r: Int, b: Int, s: String) = node("android.widget.TextView", l, t, r, b, s)
    private fun image(l: Int, t: Int, r: Int, b: Int) = node("android.widget.ImageView", l, t, r, b)
    private fun root(vararg kids: AccessibilityNodeInfo) = group(0, 0, 1080, 2273, null, *kids)

    /** 26.11 서재 머리와 탭 */
    private fun header() = group(0, 66, 1080, 314, null,
        text(22, 66, 233, 201, "모든 책장"),
        group(193, 218, 408, 314, "구매 목록, 구매 목록", text(226, 234, 374, 296, "구매 목록")),
    )

    /** 26.11 구매 목록 격자 칸 */
    private fun grid(l: Int, desc: String, title: String, vararg extra: AccessibilityNodeInfo) =
        group(l, 489, l + 315, 1115, desc, image(l, 489, l + 315, 946), *extra, text(l, 957, l + 248, 1059, title))

    @Test fun `26_11 구매 목록 격자는 머리 글자로 서재를 알아본다`() {
        val cells = Ridi.shelfCells(root(header(),
            grid(45, "반딧불이의 혼례, 대여 만료", "반딧불이의 혼례", text(45, 1064, 293, 1115, "대여 만료")),
            grid(383, "총 5권, 본격 한중일 세계사, 이용 권한 없음", "본격 한중일 세계사", text(501, 872, 580, 917, "총 5권")),
        ))
        assertEquals(listOf("반딧불이의 혼례", "본격 한중일 세계사"), cells.map { it.title })
        assertEquals(Rect(383, 489, 698, 946), cells[1].thumb)
    }

    @Test fun `26_11 최근 본 목록`() {
        val cell = group(0, 466, 799, 756, "시민의 교양, 2주 전 열람, 소장",
            group(45, 489, 214, 734, null, image(45, 489, 214, 734)),
            text(259, 523, 799, 585, "시민의 교양"), text(259, 590, 799, 641, "2주 전 열람"), text(259, 647, 799, 698, "소장"))
        val cells = Ridi.shelfCells(root(header(), cell, group(799, 466, 1080, 756, "이어보기, 0%", text(873, 549, 990, 600, "이어보기"))))
        assertEquals("시민의 교양", cells.single().title)
        assertEquals(Rect(45, 489, 214, 734), cells.single().thumb)
    }

    @Test fun `26_11 작품 화면 권 칸은 권 번호까지, 받지 않은 권은 뺀다`() {
        fun volume(l: Int, n: Int, download: Boolean) = group(l, 781, l + 315, 1406, "본격 한중일 세계사 , $n, 본격 한중일 세계사 $n, ${if (download) "이용 권한 없음" else "소장"}",
            image(l, 781, l + 315, 1238),
            *(if (download) arrayOf(group(l + 211, 807, l + 290, 885, "다운로드")) else emptyArray()),
            text(l, 1249, l + 306, 1301, "본격 한중일 세계사 "), text(l, 1299, l + 23, 1350, "$n"))
        val cells = Ridi.shelfCells(root(header(), volume(45, 1, download = false), volume(383, 2, download = true)))
        assertEquals(listOf("본격 한중일 세계사 1"), cells.map { it.title })
    }

    @Test fun `26_10 서재는 libraryGnbMenus 로 알아보고 받기 단추 이름이 있는 칸은 뺀다`() {
        val marker = group(0, 0, 1072, 120, null, node("android.view.ViewGroup", 0, 0, 1072, 120, id = "libraryGnbMenus"))
        val ok = group(30, 200, 360, 800, "정의란 무엇인가, 정의란 무엇인가, 소장", image(30, 200, 360, 650), text(30, 660, 360, 720, "정의란 무엇인가"))
        val dark = group(380, 200, 710, 800, "데미안, 데미안, 소장", image(380, 200, 710, 650),
            node("android.view.ViewGroup", 650, 210, 700, 260, id = "download"), text(380, 660, 710, 720, "데미안"))
        val list = group(0, 900, 1072, 1100, "시민의 교양, 26.7MB, 소장, 48%", image(30, 910, 116, 1090), text(140, 920, 800, 980, "시민의 교양"))
        val cells = Ridi.shelfCells(root(marker, ok, dark, list))
        assertEquals(listOf("정의란 무엇인가", "시민의 교양"), cells.map { it.title })
        assertEquals(48, cells[1].progress)
    }

    @Test fun `숫자 제목은 권 번호로 겹쳐 붙지 않는다`() {
        val cells = Ridi.shelfCells(root(header(),
            grid(45, "1984, 1984, 소장", "1984"),
            group(383, 781, 698, 1406, "1984 , 2, 1984 2, 소장", image(383, 781, 698, 1238), text(383, 1249, 689, 1301, "1984 "), text(383, 1299, 406, 1350, "2")),
        ))
        assertEquals(listOf("1984", "1984 2"), cells.map { it.title })
    }

    /** 폰(갤럭시 A23 450dpi 에 가까운 xxhdpi): 아래 줄 표지 98px 은 빼고 최근 본 목록 표지 169px 은 받는다 */
    @Config(qualifiers = "xxhdpi")
    @Test fun `아래 읽고 있는 작품 줄의 작은 표지는 쓰지 않는다`() {
        val bar = group(0, 1956, 781, 2097, "경제학이 필요한 시간, 경제학이 필요한 시간",
            group(45, 1956, 143, 2097, null, image(45, 1956, 143, 2097)), text(188, 1995, 518, 2058, "경제학이 필요한 시간"))
        val list = group(0, 466, 799, 756, "시민의 교양, 2주 전 열람, 소장", image(45, 489, 214, 734), text(259, 523, 799, 585, "시민의 교양"))
        assertEquals(listOf("시민의 교양"), Ridi.shelfCells(root(header(), list, bar)).map { it.title })
    }

    @Test fun `서재 표시가 없는 화면(홈·검색 탭)은 읽지 않는다`() {
        val cells = Ridi.shelfCells(root(grid(45, "반딧불이의 혼례, 대여 만료", "반딧불이의 혼례")))
        assertTrue(cells.isEmpty())
    }
}
