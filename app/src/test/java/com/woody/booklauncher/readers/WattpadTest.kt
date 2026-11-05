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

/** Wattpad: 읽는 화면 툴바의 제목·화 머리, 이야기 상세(Compose). 에뮬레이터 페블(Wattpad 11.42.0)에서 본 자리. 요소 나무 때문에 Robolectric */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WattpadTest {
    private fun node(
        cls: String, l: Int, t: Int, r: Int, b: Int, text: String? = null, vararg kids: AccessibilityNodeInfo,
        desc: String? = null, click: Boolean = false, visible: Boolean = true,
    ) = AccessibilityNodeInfo.obtain().apply {
        className = cls
        setBoundsInScreen(Rect(l, t, r, b))
        text?.let { this.text = it }
        desc?.let { contentDescription = it }
        isClickable = click
        isVisibleToUser = visible
        kids.forEach { shadowOf(this).addChild(it) }
    }

    private fun view(l: Int, t: Int, r: Int, b: Int, vararg kids: AccessibilityNodeInfo, desc: String? = null, click: Boolean = false) =
        node("android.view.View", l, t, r, b, null, *kids, desc = desc, click = click)
    private fun text(l: Int, t: Int, r: Int, b: Int, s: String, visible: Boolean = true) =
        node("android.widget.TextView", l, t, r, b, s, visible = visible)

    @Test fun `툴바의 글자가 이야기 제목, 숨은 툴바는 모른다`() {
        val toolbar = node("android.view.ViewGroup", 0, 45, 1072, 150, null,
            node("android.widget.ImageButton", 0, 45, 105, 150, desc = "위로 이동", click = true),
            text(135, 71, 586, 123, "Pride and Prejudice (1813)"),
            node("androidx.appcompat.widget.LinearLayoutCompat", 769, 52, 1072, 142, null,
                node("android.widget.Button", 769, 52, 860, 142, desc = "Add to Offline List", click = true)))
        assertEquals("Pride and Prejudice (1813)", Wattpad.toolbarTitle(toolbar))
        toolbar.isVisibleToUser = false
        assertNull(Wattpad.toolbarTitle(toolbar))
    }

    @Test fun `화 이름은 위쪽 반의 가장 아래 머리`() {
        val area = Rect(0, 0, 1072, 1456)
        // 화를 막 펼쳤다
        assertEquals("Chapter I", Wattpad.currentPart(listOf(text(45, 459, 1027, 563, "Chapter I")), area))
        // 앞 화 끝에서 다음 화 머리가 아래 반에 막 보였다: 아직 앞 화(머리는 위로 지나가 없다)
        assertNull(Wattpad.currentPart(listOf(text(45, 1100, 1027, 1204, "Chapter II")), area))
        // 앞 화 머리는 맨 위에 걸쳐 있고 다음 화 머리가 위쪽 반에 올라왔다
        assertEquals("Chapter II", Wattpad.currentPart(listOf(text(45, 20, 1027, 120, "Chapter I"), text(45, 600, 1027, 704, "Chapter II")), area))
        // 보이지 않는 머리·빈 글자는 쓰지 않는다
        assertNull(Wattpad.currentPart(listOf(text(45, 84, 1027, 188, "Chapter IV", visible = false), text(45, 300, 1027, 400, " ")), area))
        assertNull(Wattpad.currentPart(emptyList(), area))
    }

    /** 이야기 상세 한 쪽: 표지 묶음(셋 아래 설명이 붙은 그림)·제목·저자 묶음·읽은 수 줄… */
    private fun detailPage(l: Int, title: String, author: String) = view(l, 225, l + 1072, 1441,
        view(l + 30, 240, l + 225, 544, view(l + 30, 240, l + 225, 544, view(l + 30, 240, l + 225, 544,
            node("android.widget.ImageView", l + 30, 240, l + 225, 544, desc = title)))),
        node("android.widget.TextView", l + 255, 224, l + 636, 314, title, desc = title, click = true),
        view(l + 255, 276, l + 430, 366, view(l + 229, 276, l + 319, 366), text(l + 304, 305, l + 430, 338, author), click = true),
        view(l + 255, 503, l + 592, 536, view(l + 255, 503, l + 353, 536, desc = "10.6M Reads")),
        text(l + 212, 750, l + 324, 783, "Summary"))

    @Test fun `이야기 상세는 표지 설명과 같은 제목, 그 뒤 묶음이 저자`() {
        val root = view(0, 45, 1072, 1456,
            view(0, 165, 1072, 210, view(428, 150, 518, 224)),
            detailPage(0, "Pride and Prejudice (1813)", "JaneAusten"),
            view(0, 45, 1072, 150, node("android.widget.ImageView", 16, 53, 106, 143, desc = "Back", click = true)))
        val cell = Wattpad.detail(root)!!
        assertEquals("Pride and Prejudice (1813)", cell.title)
        assertEquals("JaneAusten", cell.author)
        assertEquals(Rect(30, 240, 225, 544), cell.thumb)
    }

    @Test fun `옆으로 넘기는 중이라 표지가 화면 밖에 걸치면 표지는 자르지 않는다`() {
        val page = detailPage(-100, "Pride and Prejudice (1813)", "JaneAusten")
        val cell = Wattpad.detail(view(0, 45, 1072, 1456, view(0, 225, 1072, 1441, page)))!!
        assertEquals("Pride and Prejudice (1813)", cell.title)
        assertNull(cell.thumb)
    }

    /** 공개 검색 결과(2026-11-04 `Don't Give Up` 으로 받은 것을 줄임): 같은 제목이 여럿 */
    private val search = """{"stories":[{"id":"120956630","title":"Don't give up on me ✓","user":{"name":"BabyInACorner"}},""" +
        """{"id":"1536667","title":"Don't Give Up","user":{"name":"RiceLover"}},{"id":"97950224","title":"Don't Give Up","user":{"name":"Sskcin"}}],""" +
        """"nextUrl":"https://www.wattpad.com/api/v3/stories?offset=5"}"""

    @Test fun `이야기 번호는 제목과 사용자 이름이 함께 맞는 하나`() {
        assertEquals("1536667", Wattpad.matchStory(search, "Don't Give Up", "RiceLover"))
        assertEquals("97950224", Wattpad.matchStory(search, "don't give up", "sskcin"))
        // 제목이 비슷하기만 하거나 저자가 다르면 없다
        assertNull(Wattpad.matchStory(search, "Don't Give Up On Me", "RiceLover"))
        assertNull(Wattpad.matchStory(search, "Don't Give Up", "JaneAusten"))
        assertNull(Wattpad.matchStory(search, "Don't Give Up", ""))
        // 받은 글이 깨졌거나 번호가 숫자가 아니면 없다
        assertNull(Wattpad.matchStory("<html>", "Don't Give Up", "RiceLover"))
        assertNull(Wattpad.matchStory("""{"stories":[{"id":"x1","title":"A","user":{"name":"B"}}]}""", "A", "B"))
    }

    @Test fun `같은 작가의 부제만 다른 이야기는 제목이 글자 그대로 같은 것`() {
        val json = """{"stories":[{"id":"111","title":"Book 1: The Beginning","user":{"name":"Writer"}},""" +
            """{"id":"222","title":"Book 1: Rebirth","user":{"name":"Writer"}}]}"""
        assertEquals("222", Wattpad.matchStory(json, "Book 1: Rebirth", "Writer"))
        assertEquals("111", Wattpad.matchStory(json, " book 1: the beginning ", "writer"))
        // 글자 그대로 같은 것이 없고 정리한 제목(부제 뗌)으로는 둘이면 고르지 않는다
        assertNull(Wattpad.matchStory(json, "Book 1", "Writer"))
        // 정리한 제목으로 하나뿐이면 그것(앱이 부제를 빼고 보여 준 제목)
        assertEquals("111", Wattpad.matchStory("""{"stories":[{"id":"111","title":"Book 1: The Beginning","user":{"name":"Writer"}}]}""", "Book 1", "Writer"))
    }

    @Test fun `검색 주소는 제목과 받을 칸을 주소 글자로`() {
        assertEquals(
            "https://www.wattpad.com/api/v3/stories?query=Pride+and+Prejudice+%281813%29&limit=30&fields=stories%28id%2Ctitle%2Cuser%28name%29%29",
            Wattpad.searchUrl("Pride and Prejudice (1813)"),
        )
    }

    @Test fun `표지 설명과 다른 글자가 뒤에 오면 상세가 아니다`() {
        // 홈 추천 칸: 그림 설명이 없거나 뒤 글자가 태그
        val root = view(0, 45, 1072, 1456,
            view(30, 766, 225, 1070, node("android.widget.ImageView", 30, 766, 225, 1070)),
            text(30, 1085, 113, 1130, "millie"),
            view(450, 766, 645, 1070, node("android.widget.ImageView", 450, 766, 645, 1070, desc = "Pride and Prejudice")),
            text(450, 1085, 600, 1130, "texttospeech"))
        assertNull(Wattpad.detail(root))
    }
}
