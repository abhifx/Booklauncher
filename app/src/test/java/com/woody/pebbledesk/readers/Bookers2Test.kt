package com.woody.pebbledesk.readers

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

/** 부커스 2.0.0: 깔린 판으로 1.x 와 가르기, 웹 화면 서재 칸(격자·리스트)·책 상세, 읽는 화면(Compose). 화면은 갤럭시 A23 에서 본 모양 그대로 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Bookers2Test {
    private val context: Context = RuntimeEnvironment.getApplication()

    private fun node(cls: String, l: Int, t: Int, r: Int, b: Int, text: String? = null, desc: String? = null, vararg kids: AccessibilityNodeInfo) =
        AccessibilityNodeInfo.obtain().apply {
            className = cls
            setBoundsInScreen(Rect(l, t, r, b))
            text?.let { this.text = it }
            desc?.let { contentDescription = it }
            isClickable = cls.endsWith("Button")
            kids.forEach { shadowOf(this).addChild(it) }
        }

    private fun seal(n: AccessibilityNodeInfo): AccessibilityNodeInfo {
        for (i in 0 until n.childCount) seal(n.getChild(i))
        AccessibilityNodeInfo::class.java.getMethod("setSealed", Boolean::class.javaPrimitiveType).invoke(n, true)
        return n
    }

    /** 앱 창: Compose 자리(읽는 화면이면 [overlay]) + 웹 화면([web]) */
    private fun window(web: List<AccessibilityNodeInfo>, overlay: List<AccessibilityNodeInfo> = emptyList()) = seal(node(
        "android.widget.FrameLayout", 0, 0, 1080, 2408, null, null,
        node("E0.v0", 0, 0, 1080, 2408, null, null, node("android.view.View", 0, 0, 1080, 2408, null, null, *overlay.toTypedArray())),
        node("android.widget.FrameLayout", 0, 0, 1080, 2408, null, null,
            node("android.webkit.WebView", 0, 66, 1080, 2273, null, null, node("android.view.View", 0, 66, 1080, 2273, null, null, *web.toTypedArray()))),
    ))

    private val tabs = node("android.widget.Button", 326, 189, 486, 305, "다운로드")
    private fun gridCell(text: String) = node("android.widget.Button", 56, 555, 354, 1258, text)

    @Test fun `2_0_0 부터는 새 앱 파일, 그 전 판은 1_x 파일`() {
        fun install(code: Long) {
            shadowOf(context.packageManager).installPackage(PackageInfo().apply {
                packageName = Bookers.pkg; longVersionCode = code; applicationInfo = ApplicationInfo().apply { packageName = Bookers.pkg }
            })
            Bookers2.forgetVersion()
        }
        install(134)
        assertSame(Bookers, ReaderApps.forPackage(context, Bookers.pkg))
        install(205)
        assertSame(Bookers2, ReaderApps.forPackage(context, Bookers.pkg))
        assertTrue(ReaderApps.isViewer(context, Bookers.pkg, "com.bookers.app.MainActivity#viewer"))
        assertTrue(ReaderApps.isViewer(context, Bookers.pkg, "com.bookers.ebook.ui.viewer.epub.EpubActivity"))
    }

    @Test fun `격자 칸 단추 글자에서 제목·저자·반납일`() {
        val cell = Bookers2.shelfCells(window(listOf(tabs, gridCell("(접근성)직장 노예 D-14 12% (접근성)직장 노예 김형준 43")))).single()
        // `(접근성)` 은 1.x 처럼 그대로(1.x 에서 넣은 같은 책으로 이어지게)
        assertEquals("(접근성)직장 노예", cell.title)
        assertEquals("김형준", cell.author)
        // 서재 진행률은 읽는 화면의 쪽과 셈이 달라 쓰지 않는다
        assertEquals(-1, cell.progress)
        assertEquals(LocalDate.now().toEpochDay() + 14, cell.due)
        // 표지 위에 반납 딱지가 그려져 서재에서는 자르지 않는다
        assertNull(cell.thumb)
        // 반납 문구가 아직 없을 때
        assertEquals(-1, Bookers2.shelfCells(window(listOf(gridCell("(접근성)직장 노예 0% (접근성)직장 노예 김형준 42")))).single().due)
    }

    @Test fun `제목에 퍼센트가 든 격자 칸`() {
        val cell = Bookers2.shelfCells(window(listOf(gridCell("상위 1% 의 비밀 D-3 25% 상위 1% 의 비밀 홍길동 7")))).single()
        assertEquals("상위 1% 의 비밀" to "홍길동", cell.title to cell.author)
        assertEquals(LocalDate.now().toEpochDay() + 3, cell.due)
    }

    @Test fun `앱이 따로 띄운 창(웹 화면 없음)은 읽는 화면이 아니다`() {
        val dialog = seal(node("android.widget.FrameLayout", 100, 800, 980, 1400, null, null,
            node("android.widget.TextView", 140, 900, 940, 960, "다운로드 중입니다"), node("android.widget.Button", 540, 1300, 940, 1380, "취소")))
        assertFalse(Bookers2.showsViewer(dialog))
    }

    @Test fun `닫기 옆이어도 위 막대가 아니면(목차 패널) 제목이 아니다`() {
        val panel = window(listOf(tabs), listOf(
            node("android.widget.TextView", 509, 2225, 616, 2273, "7/343"),
            node("android.view.View", 0, 300, 1080, 400, null, null,
                node("android.view.View", 28, 320, 96, 388, null, "닫기"), node("android.widget.TextView", 102, 320, 265, 388, "목차")),
        ))
        assertNull(Bookers2.readViewer(AppWindows(panel, listOf(panel))).title)
    }

    @Test fun `진행률이 없는 칸(홈의 책 카드)은 칸이 아니다`() {
        assertEquals(emptyList<Cell>(), Bookers2.shelfCells(window(listOf(node("android.widget.Button", 45, 900, 340, 1400, "(접근성)어떻게 살아갈 것인가 (접근성)어떻게 살아갈 것인가 한인수")))))
    }

    @Test fun `내 서재 읽고 싶은 책 탭은 진행률 글자가 없어도 칸(받은 책·받지 않은 책)`() {
        val header = node("android.widget.TextView", 458, 91, 621, 164, "내 서재")
        val got = gridCell("(접근성)스무살, 나답게 산다는 것 (접근성)스무살, 나답게 산다는 것 최재목 10")
        val notYet = node("android.widget.Button", 390, 555, 689, 1258, "(접근성)어떻게 살아갈 것인가 다운로드 (접근성)어떻게 살아갈 것인가 한인수 82")
        val cells = Bookers2.shelfCells(window(listOf(header, tabs, got, notYet)))
        assertEquals(listOf("(접근성)스무살, 나답게 산다는 것" to "최재목", "(접근성)어떻게 살아갈 것인가" to "한인수"), cells.map { it.title to it.author })
        // 다운로드 탭의 받지 않은 칸 `D-14 다운로드 0%`
        val lent = node("android.widget.Button", 56, 555, 354, 1258, "(접근성)어떻게 살아갈 것인가 D-14 다운로드 0% (접근성)어떻게 살아갈 것인가 한인수 82")
        assertEquals(LocalDate.now().toEpochDay() + 14, Bookers2.shelfCells(window(listOf(lent))).single().due)
    }

    @Test fun `아래 탭의 내 서재 단추만 있는 홈의 카드는 칸이 아니고, 다운로드로 시작하는 제목도 칸`() {
        val bottomTab = node("android.widget.Button", 329, 2076, 478, 2203, "내 서재")
        val card = node("android.widget.Button", 45, 900, 340, 1400, "(접근성)직장 노예 (접근성)직장 노예 김형준")
        assertEquals(emptyList<Cell>(), Bookers2.shelfCells(window(listOf(card, bottomTab))))
        val header = node("android.widget.TextView", 458, 91, 621, 164, "내 서재")
        val odd = gridCell("다운로드 대작전 다운로드 대작전 홍길동 5")
        assertEquals(listOf("다운로드 대작전" to "홍길동"), Bookers2.shelfCells(window(listOf(header, odd, bottomTab))).map { it.title to it.author })
        val dday = node("android.widget.Button", 390, 555, 689, 1258, "책 D-day 12% 책 저자 3")
        assertEquals(LocalDate.now().toEpochDay(), Bookers2.shelfCells(window(listOf(dday))).single().due)
    }

    @Test fun `읽고 싶은 책 리스트 보기는 글자 없는 표지 단추·다운로드 단추도 표지, 위 탭은 아니다`() {
        val header = node("android.widget.TextView", 458, 91, 621, 164, "내 서재")
        fun row(top: Int, cover: String?, title: String) = node("android.view.View", 45, top, 1035, top + 560, null, null,
            node("android.widget.Button", 45, top + 50, 340, top + 500, cover),
            node("android.widget.TextView", 390, top + 70, 888, top + 120, title),
            node("android.widget.TextView", 390, top + 150, 480, top + 190, "최재목"),
            node("android.widget.Button", 990, top + 70, 1035, top + 180, null, "더보기"))
        val a = row(590, "다운로드", "(접근성)어떻게 살아갈 것인가")
        val b = row(1150, null, "(접근성)스무살, 나답게 산다는 것")
        val root = window(listOf(header, tabs, a, b))
        assertEquals(listOf("(접근성)어떻게 살아갈 것인가", "(접근성)스무살, 나답게 산다는 것"), Bookers2.shelfCells(root).map { it.title })
        val windows = AppWindows(root, listOf(root))
        assertEquals("(접근성)스무살, 나답게 산다는 것", Bookers2.clickedCell(b.getChild(0), emptyList(), windows, null)?.title)
        assertNull(Bookers2.clickedCell(tabs, emptyList(), windows, null))
    }

    @Test fun `리스트 보기 칸은 표지 단추 옆 제목·저자`() {
        val cover = node("android.widget.Button", 45, 527, 340, 1039, "D-14 3%")
        // 앞에 화면에 보이지 않는 요소(uiautomator 에는 없다)가 와도
        val row = node("android.view.View", 45, 471, 1035, 1098, null, null, node("android.view.View", 0, 0, 0, 0), cover,
            node("android.widget.TextView", 390, 546, 663, 597, "(접근성)직장 노예"),
            node("android.widget.TextView", 390, 622, 480, 667, "김형준"),
            node("android.widget.TextView", 390, 662, 537, 707, "미다스북스"),
            node("android.widget.Button", 990, 546, 1035, 659, null, "더보기"))
        val root = window(listOf(node("android.widget.ListView", 0, 471, 1080, 1098, null, null, row)))
        val cell = Bookers2.shelfCells(root).single()
        assertEquals("(접근성)직장 노예" to "김형준", cell.title to cell.author)
        assertEquals(LocalDate.now().toEpochDay() + 14, cell.due)
        // 표지 단추를 누르면 그 칸
        assertEquals("(접근성)직장 노예", Bookers2.clickedCell(cover, emptyList(), AppWindows(root, listOf(root)), null)?.title)
        // 같은 줄의 다른 단추(더보기)는 책을 여는 것이 아니다
        assertNull(Bookers2.clickedCell(row.getChild(5), emptyList(), AppWindows(root, listOf(root)), null))
    }

    @Test fun `책 상세의 바로읽기는 그 책(표지 그림 자리, 출판사 줄 위의 저자)`() {
        val read = node("android.widget.Button", 652, 1894, 1012, 2020, "바로읽기")
        val root = window(listOf(
            node("android.widget.TextView", 137, 243, 801, 307, "(접근성)직장 노예"),
            node("android.widget.Image", 320, 386, 759, 1016, null, "(접근성)직장 노예"),
            node("android.widget.TextView", 45, 1266, 1035, 1317, "EPUB > 자기계발 > 성공/처세"),
            node("android.widget.TextView", 45, 1371, 1035, 1441, "(접근성)직장 노예"),
            node("android.widget.TextView", 45, 1446, 1035, 1497, "마흔 여덟, 열 번째 퇴사를 준비하다"),
            node("android.widget.TextView", 45, 1551, 1035, 1601, "김형준"),
            node("android.widget.TextView", 45, 1604, 213, 1655, "미다스북스"),
            node("android.widget.TextView", 219, 1607, 233, 1652, "|"),
            node("android.widget.TextView", 241, 1604, 430, 1655, "2023.12.27"),
            node("android.widget.Button", 399, 1894, 627, 2020, "다운로드"),
            read,
        ))
        val cell = Bookers2.clickedCell(read, emptyList(), AppWindows(root, listOf(root)), null)!!
        assertEquals("(접근성)직장 노예" to "김형준", cell.title to cell.author)
        assertEquals(Rect(320, 386, 759, 1016), cell.thumb)
    }

    @Test fun `탭을 눌러도 책이 아니다`() {
        val root = window(listOf(tabs, gridCell("(접근성)직장 노예 0% (접근성)직장 노예 김형준 42")))
        assertNull(Bookers2.clickedCell(tabs, emptyList(), AppWindows(root, listOf(root)), null))
    }

    @Test fun `읽는 화면은 웹 화면 밖에 글자가 있는 동안, 쪽으로 진행률, 메뉴의 닫기 옆 제목`() {
        val shelf = window(listOf(tabs))
        assertFalse(Bookers2.showsViewer(shelf))
        // 메뉴 없이: 아래 쪽과 책갈피 자리만
        val plain = window(listOf(tabs), listOf(
            node("android.widget.TextView", 509, 2225, 616, 2273, "35/343"),
            node("android.view.View", 895, 61, 1030, 196, null, "책갈피 저장"),
        ))
        assertTrue(Bookers2.showsViewer(plain))
        Bookers2.readViewer(AppWindows(plain, listOf(plain))).let { assertNull(it.title); assertEquals(10, it.progress) }
        // 메뉴: 닫기 · 제목 · 검색(안에 글자)
        val menu = window(listOf(tabs), listOf(
            node("android.widget.TextView", 509, 2225, 616, 2273, "7/343"),
            node("android.view.View", 0, 92, 130, 227, null, null, node("android.view.View", 28, 125, 96, 193, null, "닫기")),
            node("android.widget.TextView", 102, 127, 265, 192, "직장 노예"),
            node("android.view.View", 796, 92, 931, 227, null, null, node("android.view.View", 810, 100, 917, 218, null, "검색"), node("android.widget.TextView", 838, 174, 890, 218, "검색")),
        ))
        Bookers2.readViewer(AppWindows(menu, listOf(menu))).let { assertEquals("직장 노예", it.title); assertEquals(2, it.progress) }
    }

    @Test fun `읽는 사람 수에 쉼표가 있어도 저자에 붙지 않는다`() {
        val header = node("android.widget.TextView", 458, 91, 621, 164, "내 서재")
        val title = "위버멘쉬 (30만부 기념 개정판)"
        val cell = Bookers2.shelfCells(window(listOf(header, gridCell("$title $title 프리드리히 니체 (지은이),어나니머스 (옮긴이) 12,335")))).single()
        assertEquals(title to "프리드리히 니체 (지은이),어나니머스 (옮긴이)", cell.title to cell.author)
    }
}
