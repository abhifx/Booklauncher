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

/** 누른 책 칸: 탭·단추를 누른 것이 서재의 한 권으로 잡히지 않는다(북커스 내서재, 책이 한 권일 때). 요소 나무 때문에 Robolectric */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClickedCellTest {
    private val pkg = Bookers.pkg

    private fun node(cls: String, l: Int, t: Int, r: Int, b: Int, id: String? = null, text: String? = null, vararg kids: AccessibilityNodeInfo) =
        AccessibilityNodeInfo.obtain().apply {
            className = cls
            setBoundsInScreen(Rect(l, t, r, b))
            id?.let { viewIdResourceName = "$pkg:id/$it" }
            text?.let { this.text = it }
            kids.forEach { shadowOf(this).addChild(it) }
        }

    private fun cell(top: Int, title: String) = node(
        "android.widget.FrameLayout", 20, top, 220, top + 410, null, null,
        node("android.widget.ImageView", 25, top, 190, top + 255, "iv_cover"),
        node("android.widget.TextView", 20, top + 280, 200, top + 360, "tv_title", title),
        node("android.widget.TextView", 20, top + 370, 200, top + 400, "tv_percent", "1%"),
    )

    /** 북커스 내서재: 위에 탭 줄, 아래 목록(RecyclerView)에 책 칸들 */
    private fun shelf(vararg cells: AccessibilityNodeInfo): Triple<AccessibilityNodeInfo, AccessibilityNodeInfo, AccessibilityNodeInfo> {
        val tab = node("android.widget.TextView", 214, 180, 428, 270, null, "다운로드")
        val tabs = node("android.widget.LinearLayout", 0, 180, 1072, 270, null, null, node("android.widget.TextView", 0, 180, 214, 270, null, "읽고 싶은 책"), tab)
        val list = node("androidx.recyclerview.widget.RecyclerView", 0, 350, 1072, 1350, null, null, *cells)
        return Triple(seal(node("android.widget.FrameLayout", 0, 0, 1072, 1456, null, null, tabs, list)), tab, list)
    }

    /** 서비스가 받는 요소처럼 굳힌다(굳지 않은 요소는 이름으로 찾기가 안 된다) */
    private fun seal(n: AccessibilityNodeInfo): AccessibilityNodeInfo {
        for (i in 0 until n.childCount) seal(n.getChild(i))
        AccessibilityNodeInfo::class.java.getMethod("setSealed", Boolean::class.javaPrimitiveType).invoke(n, true)
        return n
    }

    private fun clicked(root: AccessibilityNodeInfo, source: AccessibilityNodeInfo, text: List<String>) =
        Bookers.clickedCell(source, text, AppWindows(root, listOf(root)), null)

    @Test fun `책이 한 권일 때 탭을 누르면 책 칸이 아니다`() {
        val (root, tab, _) = shelf(cell(390, "우주를 보면 떠오르는 이상한 질문들"))
        assertNull(clicked(root, tab, listOf("다운로드")))
    }

    @Test fun `책 칸의 표지를 누르면 그 칸`() {
        val one = cell(390, "우주를 보면 떠오르는 이상한 질문들")
        val (root, _, _) = shelf(one)
        val cover = one.getChild(0)
        assertEquals("우주를 보면 떠오르는 이상한 질문들", clicked(root, cover, emptyList())?.title)
        assertEquals(1, clicked(root, cover, emptyList())?.progress)
    }

    @Test fun `누른 요소를 창에서 못 찾아도 칸은 목록의 한 칸까지`() {
        // 책이 한 권이면 위로 올라가며 제목이 하나뿐인 묶음이 목록 밖(탭이 든 화면 전체)까지 커졌다(밀리 e-ink: 그 칸 자리에 탭이 들어 탭을 누르면 그 책)
        val one = cell(390, "우주를 보면 떠오르는 이상한 질문들")
        shelf(one)
        val found = Bookers.clickedCell(one.getChild(0), emptyList(), AppWindows(null, emptyList()), null)
        assertEquals(Rect(20, 390, 220, 800), found?.cell)
    }

    @Test fun `책이 둘이어도 누른 칸`() {
        val second = cell(820, "소년이 온다")
        val (root, tab, _) = shelf(cell(390, "우주를 보면 떠오르는 이상한 질문들"), second)
        assertEquals("소년이 온다", clicked(root, second.getChild(1), emptyList())?.title)
        assertNull(clicked(root, tab, listOf("다운로드")))
    }
}
