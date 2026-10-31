package com.woody.booklauncher.readers

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 킨들 서재 칸 설명(산 책·구독으로 빌린 책·보낸 문서)과 킨들이 그리는 기본 표지. 요소 나무 때문에 Robolectric */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KindleTest {
    private val pkg = Kindle.pkg

    @Test fun `산 책은 빈 칸으로 가른다`() {
        val (head, state) = Kindle.splitDesc("Wuthering Heights, Brontë, Emily, , Book downloaded.Reading is 4% completed., 1.2 Megabyte")!!
        assertEquals("Wuthering Heights, Brontë, Emily", head)
        assertEquals(4, percent(state))
    }

    @Test fun `구독으로 빌린 책은 구독 이름으로 가른다`() {
        for (origin in listOf("Kindle Unlimited", "Kindle+", "Prime Reading")) {
            val (head, state) = Kindle.splitDesc("The Hating Game, Thorne, Sally, $origin, Book downloaded.Reading is 12% completed., 2.3 Megabyte")!!
            assertEquals(origin, "The Hating Game, Thorne, Sally", head)
            assertEquals(origin, 12, percent(state))
        }
    }

    @Test fun `저자가 없는 보낸 문서`() {
        assertEquals("cover" to ", Book not downloaded.", Kindle.splitDesc("cover, , , Book not downloaded."))
    }

    @Test fun `추천 줄의 설명은 책 칸이 아니다`() {
        assertNull(Kindle.splitDesc("The Ex by Shalini Boland"))
    }

    @Test fun `시리즈 묶음 칸은 책 칸이 아니다`() {
        assertNull(Kindle.splitDesc("Series: Death Stranding, 2 items, , Nojima, Hitori"))
        assertNull(Kindle.splitDesc("Series: Defiance of the Fall, 2 items, Kindle+, TheFirstDefier;Brink, JF"))
    }

    @Test fun `저자가 여럿이면 한 사람씩 이름을 바꾼다`() {
        assertEquals("Emily Brontë", Kindle.personName("Brontë, Emily"))
        assertEquals("Shirtaloon, Travis Deverell", Kindle.personName("Shirtaloon; Deverell, Travis"))
        assertEquals("TheFirstDefier, JF Brink", Kindle.personName("TheFirstDefier;Brink, JF"))
        assertEquals("", Kindle.personName(""))
        // 격자 보기 칸은 설명에서 제목과 저자(끝의 `성, 이름`)를 가른다
        val cell = Kindle.shelfCells(seal(root(grid(100, "He Who Fights with Monsters, Shirtaloon; Deverell, Travis, Kindle+, Book downloaded.Reading is 2% completed.")))).single()
        assertEquals("He Who Fights with Monsters", cell.title)
        assertEquals("Shirtaloon, Travis Deverell", cell.author)
    }

    @Test fun `격자 보기의 구독 책 칸`() {
        val cell = Kindle.shelfCells(seal(root(grid(100, "The Hating Game, Thorne, Sally, Kindle Unlimited, Book not downloaded.")))).single()
        assertEquals("The Hating Game", cell.title)
        assertEquals("Sally Thorne", cell.author)
        assertNotNull(cell.thumb)
    }

    @Test fun `kindle+ 띠가 덮은 표지는 자르지 않는다`() {
        val desc = "The Hating Game, Thorne, Sally, Kindle+, Book not downloaded."
        val band = node(200, 300, 370, 354, "bottom_section_center_badge").apply { isVisibleToUser = true }
        assertNull(Kindle.shelfCells(seal(root(grid(100, desc, band)))).single().thumb)
        // 칸을 다시 써 숨겨 둔 표시는 세지 않는다
        val hidden = node(200, 300, 370, 354, "bottom_section_center_badge")
        assertNotNull(Kindle.shelfCells(seal(root(grid(100, desc, hidden)))).single().thumb)
    }

    @Test fun `기본 표지를 알아본다`() {
        assertTrue(Kindle.placeholder(placeholder(), W, H))
    }

    @Test fun `크레마 화면 대비로 어두워진 기본 표지도 알아본다`() {
        // 크레마 대비 56: 채널마다 밝기^(1/0.37)
        val dark = placeholder().map { p ->
            (0..2).fold(0xFF000000.toInt()) { acc, i ->
                val c = (Math.pow((p shr (i * 8) and 0xff) / 255.0, 1 / 0.37) * 255).toInt()
                acc or (c shl (i * 8))
            }
        }.toIntArray()
        assertTrue(Kindle.placeholder(dark, W, H))
    }

    @Test fun `진짜 표지는 기본 표지가 아니다`() {
        // 위아래가 폭 전체 주황 띠인 표지(펭귄 고전), 흰 바탕 왼쪽 위에 검은 제목, 어두운 표지
        val penguin = paint(0xFFFFFF) { _, y -> if (y < 0.3f || y > 0.75f) 0xFFA800 else null }
        val blackTitle = paint(0xF4F4F4) { x, y -> if (y in 0.15f..0.3f && x < 0.85f && (x * 20).toInt() % 3 != 0) 0x101010 else null }
        val dark = paint(0x303040) { _, _ -> null }
        for (px in listOf(penguin, blackTitle, dark)) assertFalse(Kindle.placeholder(px, W, H))
    }

    /** 격자 보기 칸: 설명을 단 단추 안에 표지 하나(와 표지 위 표시들 [badges]) */
    private fun grid(top: Int, desc: String, vararg badges: AccessibilityNodeInfo) =
        node(200, top, 370, top + 254, "badgeable_cover", desc, node(200, top, 370, top + 254, "cover_image"), *badges)

    private fun root(vararg kids: AccessibilityNodeInfo) = node(0, 0, 1072, 1456, null, null, *kids)

    private fun node(l: Int, t: Int, r: Int, b: Int, id: String?, desc: String? = null, vararg kids: AccessibilityNodeInfo) =
        AccessibilityNodeInfo.obtain().apply {
            className = "android.view.View"
            setBoundsInScreen(Rect(l, t, r, b))
            id?.let { viewIdResourceName = "$pkg:id/$it" }
            desc?.let { contentDescription = it }
            kids.forEach { shadowOf(this).addChild(it) }
        }

    /** 서비스가 받는 요소처럼 굳힌다 */
    private fun seal(n: AccessibilityNodeInfo): AccessibilityNodeInfo {
        for (i in 0 until n.childCount) seal(n.getChild(i))
        AccessibilityNodeInfo::class.java.getMethod("setSealed", Boolean::class.javaPrimitiveType).invoke(n, true)
        return n
    }

    /** 바탕 [bg] 에 [at] 이 준 색(비율 자리마다, null 이면 바탕)을 칠한 그림 */
    private fun paint(bg: Int, at: (Float, Float) -> Int?) = IntArray(W * H) { i ->
        val x = (i % W).toFloat() / W
        val y = (i / W).toFloat() / H
        (0xFF000000.toInt()) or (at(x, y) ?: bg)
    }

    /** 2026-10-26 에뮬레이터 화면의 기본 표지 모양: 연한 회색 바탕, 위쪽 주황 띠(흰 KINDLE), 파일 이름, 큰 회색 k */
    private fun placeholder() = paint(0xECEDEE) { x, y ->
        when {
            y in 0.14f..0.29f && x < 0.87f -> if (y in 0.17f..0.26f && (x * 40).toInt() % 3 == 0) 0xFFFFFF else 0xFFA800
            y in 0.37f..0.41f && x in 0.3f..0.7f && (x * 30).toInt() % 2 == 0 -> 0x333333
            y in 0.72f..0.95f && x in 0.07f..0.12f -> 0xC0C0C0
            else -> null
        }
    }

    private companion object {
        const val W = 170
        const val H = 254
    }
}
