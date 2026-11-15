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
import java.time.LocalDate

/**
 * 북큐브(3.0.43) 내서재 리스트형 칸. 자리는 CremaPebble_A11_Play(1072×1456) 화면 덤프 그대로:
 * 표지 `list_img` (32,246)-(154,406), 왼쪽 위 딱지 `list_icon` (23,246)-(76,272, 표지와 다른 요소), 받지 않은 칸은 표지 위에 `down_back_color`·`list_img_down_icon`.
 * 칸을 누르면 줄 전체(이름 없는 LinearLayout)가 누른 요소로 온다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookcubeTest {
    private val pkg = Bookcube.pkg

    private fun node(cls: String, l: Int, t: Int, r: Int, b: Int, id: String? = null, text: String? = null, visible: Boolean = true, vararg kids: AccessibilityNodeInfo) =
        AccessibilityNodeInfo.obtain().apply {
            className = cls
            setBoundsInScreen(Rect(l, t, r, b))
            id?.let { viewIdResourceName = "$pkg:id/$it" }
            text?.let { this.text = it }
            isVisibleToUser = visible
            kids.forEach { shadowOf(this).addChild(it) }
        }

    private fun seal(n: AccessibilityNodeInfo): AccessibilityNodeInfo {
        for (i in 0 until n.childCount) seal(n.getChild(i))
        AccessibilityNodeInfo::class.java.getMethod("setSealed", Boolean::class.javaPrimitiveType).invoke(n, true)
        return n
    }

    /** 리스트형 줄 하나(왼쪽 위 딱지는 늘 있다). [downloaded] 가 아니면 표지 위에 받기 막 */
    private fun row(title: String, author: String, downloaded: Boolean = true, due: String? = null) = node(
        "android.widget.LinearLayout", 0, 223, 1072, 429, null, null, true,
        node("android.widget.RelativeLayout", 0, 223, 1072, 429, "book_list_layout", null, true,
            node("android.widget.RelativeLayout", 23, 246, 154, 406, "list_img_frame", null, true,
                node("android.widget.RelativeLayout", 32, 246, 154, 406, null, null, true,
                    node("android.widget.ImageView", 32, 246, 154, 406, "list_img"),
                    node("android.widget.ImageView", 32, 246, 154, 406, "list_img_border"),
                    node("android.widget.ImageView", 32, 246, 154, 406, "down_back_color", null, !downloaded),
                    node("android.widget.ImageView", 57, 284, 129, 367, "list_img_down_icon", null, !downloaded),
                ),
                node("android.widget.RelativeLayout", 23, 246, 76, 272, "list_icon", null, true,
                    node("android.widget.ImageView", 23, 246, 76, 272, "list_file_type_icon"),
                ),
            ),
            node("android.widget.TextView", 178, 256, 959, 294, "row_text", title),
            node("android.widget.TextView", 178, 294, 959, 326, "row_text2", author),
            *listOfNotNull(due?.let { node("android.widget.TextView", 178, 330, 959, 362, "row_text3", it) }).toTypedArray(),
            node("android.widget.RelativeLayout", 959, 279, 1053, 373, "progress_bar_layout", null, true,
                node("android.widget.TextView", 984, 310, 1027, 342, "down_progress_text", "19%"),
            ),
        ),
    )

    private fun clicked(row: AccessibilityNodeInfo): Cell? {
        val root = seal(node("android.widget.FrameLayout", 0, 0, 1072, 1456, "content", null, true,
            node("androidx.recyclerview.widget.RecyclerView", 0, 223, 1072, 1353, "book_list_view", null, true, row)))
        return Bookcube.clickedCell(row, emptyList(), AppWindows(root, listOf(root)), null)
    }

    @Test fun `저자는 출판사 앞만, 진행률은 진행 원의 %`() {
        val c = clicked(row("[무료] 체스트넛 스트리트 (체험판)", "메이브 빈치 | 문학동네"))!!
        assertEquals("[무료] 체스트넛 스트리트 (체험판)", c.title)
        assertEquals("메이브 빈치", c.author)
        assertEquals(19, c.progress)
    }

    @Test fun `미리보기 책은 서재에만 붙는 머리를 뗀다`() =
        assertEquals("체스트넛 스트리트", clicked(row("[미리보기]체스트넛 스트리트", "메이브 빈치 | 문학동네"))!!.title)

    @Test fun `표지는 딱지째 표지 자리 그대로(인터넷이 안 될 때 남는 표지)`() =
        assertEquals(Rect(32, 246, 154, 406), clicked(row("소년이 온다", "한강 | 창비"))!!.thumb)

    @Test fun `받지 않은 칸은 표지를 자르지 않는다`() =
        assertNull(clicked(row("소년이 온다", "한강 | 창비", downloaded = false))!!.thumb)

    @Test fun `대여 기간`() =
        assertEquals(LocalDate.of(2026, 11, 20).toEpochDay(), clicked(row("소년이 온다", "한강 | 창비", due = "2026-11-20 23:59 만료"))!!.due)

    @Test fun `연재의 화 칸은 화마다 한 권, 제목은 메뉴 모양, 진행률은 그 화의 %`() {
        val c = clicked(row("포식으로 레벨업하는 군주님 - 1화", "1화"))!!
        assertEquals("포식으로 레벨업하는 군주님 1화", c.title)
        assertEquals("", c.author)
        assertEquals(19, c.progress)
    }

    @Test fun `연재 작품 칸은 그대로`() {
        val c = clicked(row("포식으로 레벨업하는 군주님", "아이박슨 | 연필"))!!
        assertEquals("포식으로 레벨업하는 군주님", c.title)
        assertEquals("아이박슨", c.author)
    }

    @Test fun `한 화의 제목에서 작품`() {
        assertEquals("포식으로 레벨업하는 군주님", Bookcube.workOf("포식으로 레벨업하는 군주님 12화"))
        assertNull(Bookcube.workOf("[무료] 섭정왕의 신의광비 1"))
        assertNull(Bookcube.workOf("세상 편한 집밥"))
    }

    /** 서점 검색 결과 칸(2026-11-14 `/book/m/search.asp?site=book`) */
    private fun bookResult(title: String, author: String, img: String) = """
        <li><a href="https://www.bookcube.com/book/m/detail.asp?series_num=924034802"><div><div class="box"><span class="rm_br">
        <img src="https://bookimg.bookcube.com/270/2409/$img.jpg" alt="$title"></span></div></div>
        <div><p>$title</p><p>$author</p><p>이든서재</p><div class="book-info"><p><span class="list-star rank0"></span></p></div></div></a></li>"""

    @Test fun `서점 검색 결과에서 제목·저자가 같은 표지 원본`() {
        val html = "<ul><li><a>유형</a></li>" + bookResult("우울해서 빵을 샀어 2", "다른 사람", "240900001") +
            bookResult("우울해서 빵을 샀어", "안드레아 카스프르작", "240900763") + "</ul>"
        assertEquals("https://bookimg.bookcube.com/270/2409/240900763.jpg", Bookcube.coverUrl(html, "우울해서 빵을 샀어", "안드레아 카스프르작"))
        assertNull(Bookcube.coverUrl(html, "우울해서 빵을 샀어", "박미란"))
        assertNull(Bookcube.coverUrl(html, "세상 편한 집밥", ""))
    }

    @Test fun `제목에 든 이름은 저자로 보지 않는다`() =
        assertNull(Bookcube.coverUrl("<ul>" + bookResult("한강 걷기", "다른 사람", "240900002") + "</ul>", "한강 걷기", "한강"))

    @Test fun `웹소설 한 화는 작품을 웹소설에서 찾고 270 그림으로`() {
        assertEquals("https://www.bookcube.com/book/m/search.asp?site=novel&searchStr=%ED%8F%AC%EC%8B%9D", Bookcube.searchUrl("포식 2화"))
        val html = """<ul><li class="novel"><a href="/novel/m/detail.asp?serial_num=sf6719"><div class="thum-box">
            <img src="https://serialimg.bookcube.com/200/sf6719.jpg" alt="포식으로 레벨업하는 군주님"></div>
            <div class="list-info"><h4>포식으로 레벨업하는 군주님</h4><p class="list-author">아이박슨</p><p>퓨전</p></div></a></li></ul>"""
        assertEquals("https://serialimg.bookcube.com/270/sf6719.jpg", Bookcube.coverUrl(html, "포식으로 레벨업하는 군주님 2화", "아이박슨"))
    }
}
