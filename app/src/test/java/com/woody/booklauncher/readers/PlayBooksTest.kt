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

/** Google Play 북: 본문 설명의 쪽으로 센 진행률과 스토어 책 상세의 표지·제목·저자. 요소 나무 때문에 Robolectric */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayBooksTest {
    private val pkg = PlayBooks.pkg

    private fun node(
        cls: String, l: Int, t: Int, r: Int, b: Int, id: String? = null, text: String? = null, vararg kids: AccessibilityNodeInfo,
        desc: String? = null, click: Boolean = false,
    ) = AccessibilityNodeInfo.obtain().apply {
        className = cls
        setBoundsInScreen(Rect(l, t, r, b))
        id?.let { viewIdResourceName = "$pkg:id/$it" }
        text?.let { this.text = it }
        desc?.let { contentDescription = it }
        isClickable = click
        kids.forEach { shadowOf(this).addChild(it) }
    }

    private fun view(l: Int, t: Int, r: Int, b: Int, vararg kids: AccessibilityNodeInfo, desc: String? = null, click: Boolean = false) =
        node("android.view.View", l, t, r, b, null, null, *kids, desc = desc, click = click)
    private fun text(l: Int, t: Int, r: Int, b: Int, s: String) = node("android.widget.TextView", l, t, r, b, null, s)

    /** 라이브러리 쪽 넘김(에뮬레이터 페블에서 본 자리) */
    private fun library(vararg cells: AccessibilityNodeInfo) = node("androidx.viewpager.widget.ViewPager", 0, 240, 1072, 1336, "books_view_pager", null,
        view(0, 240, 1072, 1456,
            view(0, 240, 1072, 353, view(30, 259, 124, 349, view(30, 274, 124, 334, desc = "모든 필터 표시"), click = true), desc = "라이브러리 필터"),
            *cells))

    @Test fun `본문 설명의 쪽을 올림해 진행률(앱 아래 % 와 1 차이가 날 수 있다)`() {
        assertEquals(1, PlayBooks.pagesOf("Title Page, 511페이지 중 1페이지"))
        assertEquals(3, PlayBooks.pagesOf("Secrets by Specification, 511페이지 중 12페이지"))
        assertEquals(3, PlayBooks.pagesOf("Secrets by Specification, Page 12 of 511"))
        // 장 제목의 숫자·쉼표는 보지 않는다
        assertEquals(50, PlayBooks.pagesOf("Chapter 3, Part 2, Page 50 of 100"))
        assertNull(PlayBooks.pagesOf("Title Page"))
    }

    @Test fun `스토어 책 상세는 첫 줄이 제목, 둘째 줄이 저자, 옆 그림이 표지`() {
        val line = "multiline_description_widget_description_line"
        val header = node("android.widget.LinearLayout", 60, 195, 1012, 453, null, null,
            node("android.view.ViewGroup", 60, 195, 277, 453, "thumbnail", null,
                node("androidx.cardview.widget.CardView", 64, 195, 273, 449, "book_card_cover_card", null,
                    node("android.widget.ImageView", 64, 195, 273, 449, "book_card_image"))),
            node("android.widget.LinearLayout", 307, 195, 1012, 443, null, null,
                node("android.widget.LinearLayout", 307, 195, 1012, 443, "detail_page_header_description_line_container", null,
                    node("android.widget.TextView", 307, 195, 1012, 337, line, "CSS Secrets: Better Solutions to Everyday Web Design Problems"),
                    node("android.widget.TextView", 307, 345, 1012, 390, line, "Lea Verou"),
                    node("android.widget.TextView", 307, 398, 1012, 443, line, "2015년 6월 4일 출시"))),
        )
        val cell = PlayBooks.header(header.getChild(0))!!
        assertEquals("CSS Secrets: Better Solutions to Everyday Web Design Problems", cell.title)
        assertEquals("Lea Verou", cell.author)
        assertEquals(Rect(64, 195, 273, 449), cell.thumb)
    }

    @Test fun `라이브러리 목록 보기는 표지·제목·저자, 진행률 글자는 저자가 아니다`() {
        val cells = PlayBooks.libraryCells(library(
            view(0, 353, 1072, 518,
                view(30, 368, 120, 503, view(30, 368, 120, 503)),
                text(150, 368, 284, 406, "플레이 첫 책"), text(150, 414, 212, 446, "홍길동"), text(150, 454, 243, 486, "24% 완료"),
                view(952, 368, 1042, 458, desc = "다운로드됨"), click = true),
            view(0, 518, 1072, 683,
                view(30, 533, 120, 668, view(30, 533, 120, 668)),
                text(150, 533, 308, 571, "업로드한 책"), text(150, 579, 243, 611, "12% 완료"), click = true),
        ))
        assertEquals(listOf("플레이 첫 책", "업로드한 책"), cells.map { it.title })
        assertEquals(listOf("홍길동", ""), cells.map { it.author })
        assertEquals(Rect(30, 368, 120, 503), cells[0].thumb)
        assertEquals(-1, cells[0].progress)
    }

    @Test fun `라이브러리 격자 보기에서 받기 아이콘이 겹친 표지와 목록 끝에 잘린 표지는 자르지 않는다`() {
        val cells = PlayBooks.libraryCells(library(
            view(30, 368, 348, 925,
                view(30, 368, 348, 845, view(30, 368, 348, 845), view(331, 828, 333, 830, desc = "다운로드됨")),
                text(30, 849, 145, 881, "플레이 첫 책"), text(30, 889, 92, 921, "홍길동"), click = true),
            view(378, 368, 695, 924,
                view(378, 368, 695, 844, view(378, 368, 695, 844), view(634, 783, 680, 829, desc = "다운로드되지 않음")),
                text(378, 848, 513, 880, "플레이 둘째 책"), text(378, 888, 440, 920, "김철수"), click = true),
            // 아래로 넘겨 표지 위쪽이 목록 위 끝에 잘림
            view(726, 240, 1042, 500,
                view(726, 240, 1042, 420, view(726, 240, 1042, 420)),
                text(726, 424, 900, 456, "잘린 책"), click = true),
        ))
        assertEquals(listOf("플레이 첫 책", "플레이 둘째 책"), cells.map { it.title })
        assertEquals(Rect(30, 368, 348, 845), cells[0].thumb)
        assertNull(cells[1].thumb)
    }
}
