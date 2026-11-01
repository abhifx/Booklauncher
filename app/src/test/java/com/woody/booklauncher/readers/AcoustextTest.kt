package com.woody.booklauncher.readers

import android.content.ComponentName
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.woody.booklauncher.ShelfBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AcoustextTest {
    private val pkg = Acoustext.pkg

    private fun node(
        cls: String, l: Int, t: Int, r: Int, b: Int, id: String? = null, text: String? = null,
        vararg kids: AccessibilityNodeInfo, desc: String? = null, click: Boolean = false,
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

    private fun seal(n: AccessibilityNodeInfo): AccessibilityNodeInfo {
        for (i in 0 until n.childCount) seal(n.getChild(i))
        AccessibilityNodeInfo::class.java.getMethod("setSealed", Boolean::class.javaPrimitiveType).invoke(n, true)
        return n
    }

    /** 기기(AcousText 3.x)에서 본 Continue reading 머리 카드: 표지 안쪽 단추·안내 글자·진행률 표시가 함께 온다 */
    private fun heroCard() = view(
        35, 482, 1369, 946,
        view(
            70, 517, 333, 911,
            view(70, 517, 333, 911),
            view(29, 517, 134, 911),
            view(79, 526, 145, 569, text(88, 530, 136, 565, "EPUB")),
            view(278, 859, 324, 902, text(287, 863, 315, 898, "5%")),
            click = true,
        ),
        view(263, 483, 368, 588),
        text(377, 603, 619, 638, "CONTINUE READING"),
        text(377, 642, 854, 699, "Bhagavad-gita As It Is"),
        text(377, 699, 1137, 743, "His Divine Grace A. C. Bhaktivedanta Swami Prabhupada"),
        text(377, 791, 544, 826, "5% completed"),
        click = true,
    )

    /** Recent 줄의 작은 카드: 표지 아래에 제목만 */
    private fun recentCard() = view(
        70, 1149, 289, 1530,
        view(70, 1149, 289, 1477),
        view(29, 1149, 134, 1477),
        view(234, 1425, 280, 1468, text(243, 1429, 271, 1464, "23%")),
        text(70, 1495, 289, 1530, "Children of Time"),
        click = true,
    )

    /**
     * AcousText 의 서재(Library) 탭: 위 탭 줄(`Library`·`Get Books`·`Discover`·`Audio`)이 있고
     * `Library` 칸의 묶음이 `isSelected` 다(3.x 기기 덤프와 같다).
     */
    private fun library(vararg kids: AccessibilityNodeInfo) = view(
        0, 0, 720, 1440,
        view(0, 0, 180, 120, text(50, 20, 130, 60, "Library")).apply { isSelected = true },
        view(180, 0, 360, 120, text(210, 20, 330, 60, "Get Books"), click = true),
        view(360, 0, 540, 120, text(390, 20, 500, 60, "Discover"), click = true),
        view(540, 0, 720, 120, text(570, 20, 680, 60, "Audio"), click = true),
        *kids,
    )

    @Test fun `AcousText 패키지 이름 및 매핑`() {
        assertEquals("com.opsdevabhi.acoustext", Acoustext.pkg)
        assertEquals(Acoustext, ReaderApps.ALL.first { it.pkg == "com.opsdevabhi.acoustext" })
    }

    @Test fun `Continue reading 머리는 안내 글자가 아니라 책 제목·표지·진행률`() {
        val root = seal(library(heroCard()))
        val cells = Acoustext.shelfCells(root)
        assertEquals(listOf("Bhagavad-gita As It Is"), cells.map { it.title })
        assertEquals(5, cells[0].progress)
        assertEquals(Rect(70, 517, 333, 911), cells[0].thumb)
    }

    @Test fun `Recent 줄의 카드도 제목·진행률·표지를 읽는다`() {
        val root = seal(library(recentCard()))
        val cells = Acoustext.shelfCells(root)
        assertEquals(listOf("Children of Time"), cells.map { it.title })
        assertEquals(23, cells[0].progress)
        assertEquals(Rect(70, 1149, 289, 1477), cells[0].thumb)
    }

    @Test fun `제목에 퍼센트가 들어가도 진행률 표시와 헷갈리지 않는다`() {
        val card = view(
            70, 1107, 468, 1729,
            view(70, 1107, 468, 1676),
            text(70, 1694, 352, 1729, "100% Clean Code"),
            text(413, 1624, 459, 1667, "12%"),
            click = true,
        )
        val cells = Acoustext.shelfCells(seal(library(card)))
        assertEquals("100% Clean Code", cells.single().title)
        assertEquals(12, cells.single().progress)
    }

    @Test fun `Library 탭이 아닌 Notes 화면은 책으로 읽지 않는다`() {
        val note = view(
            70, 200, 300, 500,
            view(70, 200, 300, 400),
            text(70, 410, 300, 450, "Meeting notes"),
            click = true,
        )
        val root = seal(view(0, 0, 720, 1440, text(50, 20, 210, 60, "Notes"), note))
        assertTrue(Acoustext.shelfCells(root).isEmpty())
    }

    @Test fun `Audio 탭은 책으로 읽지 않는다`() {
        val audio = view(
            70, 200, 300, 500,
            view(70, 200, 300, 400),
            text(70, 410, 300, 450, "An audiobook"),
            click = true,
        )
        val root = seal(view(
            0, 0, 720, 1440,
            view(0, 0, 180, 120, text(50, 20, 130, 60, "Library"), click = true),
            view(180, 0, 360, 120, text(210, 20, 330, 60, "Get Books"), click = true),
            view(360, 0, 540, 120, text(390, 20, 500, 60, "Discover"), click = true),
            view(540, 0, 720, 120, text(570, 20, 680, 60, "Audio")).apply { isSelected = true },
            audio,
        ))
        assertTrue(Acoustext.shelfCells(root).isEmpty())
    }

    @Test fun `같은 책이 머리와 Recent 에 있어도 그중 하나라도 그 자리면 표지를 자른다`() {
        val top = Rect(70, 517, 333, 911)
        val bottom = Rect(70, 1149, 289, 1477)
        val now = listOf(
            Cell("Children of Time", "", 23, -1, top, top),
            Cell("Children of Time", "", 23, -1, bottom, bottom),
        )
        assertTrue(Acoustext.coverStill(now, "Children of Time", top))
        assertTrue(Acoustext.coverStill(now, "Children of Time", bottom))
    }

    @Test fun `읽는 화면은 위 막대의 제목과 상태 줄의 진행률을 읽고 본문은 훑지 않는다`() {
        val reader = seal(node(
            "android.widget.FrameLayout", 0, 0, 1404, 1872, "reader_root_container", null,
            view(
                0, 0, 1404, 1872,
                view(42, 48, 83, 89, desc = "Back"),
                text(111, 42, 278, 95, "Bhagavad-gita As It Is"),
                text(272, 1395, 448, 1431, "Chapter 1   5%"),
                node("android.webkit.WebView", 0, 0, 1404, 1872, null, null, text(0, 0, 0, 0, "CHAPTER ONE")),
            ),
        ))
        val info = Acoustext.readViewer(AppWindows(reader, listOf(reader)))
        assertEquals(5, info.progress)
        assertEquals("Chapter 1", info.chapter)
        assertEquals("Bhagavad-gita As It Is", info.title)
    }

    @Test fun `읽는 화면 제목이 아직 Reader 이면 책으로 적지 않는다`() {
        val reader = seal(node(
            "android.widget.FrameLayout", 0, 0, 720, 1440, "reader_root_container", null,
            view(
                0, 0, 720, 1440,
                view(42, 48, 83, 89, desc = "Back"),
                text(111, 42, 278, 95, "Reader"),
                text(42, 1290, 228, 1326, "3% • I The Lovers"),
            ),
        ))
        val info = Acoustext.readViewer(AppWindows(reader, listOf(reader)))
        assertEquals(3, info.progress)
        assertNull(info.title)
    }

    @Test fun `읽는 화면 제목이 쪽 표시뿐이면 책으로 적지 않는다`() {
        val reader = seal(node(
            "android.widget.FrameLayout", 0, 0, 720, 1440, "reader_root_container", null,
            view(
                0, 0, 720, 1440,
                view(42, 48, 83, 89, desc = "Back"),
                text(111, 42, 278, 95, "Page 1 of ~1"),
                text(42, 1290, 228, 1326, "3% • I The Lovers"),
            ),
        ))
        assertNull(Acoustext.readViewer(AppWindows(reader, listOf(reader))).title)
    }

    @Test fun `배터리 퍼센트는 진행률로 읽지 않는다`() {
        val reader = seal(node(
            "android.widget.FrameLayout", 0, 0, 1404, 1872, "reader_root_container", null,
            view(0, 0, 1404, 1872, text(1200, 40, 1300, 80, "86%")),
            view(0, 0, 1404, 1872, text(0, 40, 200, 80, "14:23 • 86%")),
            view(0, 0, 1404, 1872, text(400, 1820, 900, 1855, "Chapter 2 • 41%")),
        ))
        val info = Acoustext.readViewer(AppWindows(reader, listOf(reader)))
        assertEquals(41, info.progress)
        assertEquals("Chapter 2", info.chapter)
    }

    @Test fun `퍼센트가 앞에 오는 상태 줄도 진행률과 화를 읽는다`() {
        val reader = seal(node(
            "android.widget.FrameLayout", 0, 0, 720, 1440, "reader_root_container", null,
            view(0, 0, 720, 1440, text(42, 1290, 228, 1326, "3% • I The Lovers")),
        ))
        val info = Acoustext.readViewer(AppWindows(reader, listOf(reader)))
        assertEquals(3, info.progress)
        assertEquals("I The Lovers", info.chapter)
    }

    @Test fun `상태 줄 진행률이 없으면 읽는 화면 아래 쪽 표시로 센다`() {
        val reader = seal(node(
            "android.widget.FrameLayout", 0, 0, 1404, 1872, "reader_root_container", null,
            view(0, 0, 1404, 1872, text(53, 1657, 235, 1692, "Page 48 of 894")),
            view(0, 0, 1404, 1872, text(53, 1692, 83, 1727, "5%")),
        ))
        val info = Acoustext.readViewer(AppWindows(reader, listOf(reader)))
        assertEquals(5, info.progress)
    }

    @Test fun `홈에서 책을 누르면 AcousText 가 보여 준 제목으로 그 책을 연다`() {
        val context = RuntimeEnvironment.getApplication()
        val component = ComponentName(pkg, "com.opsdevabhi.acoustext.MainActivity")
        val book = ShelfBook("b-1", "내가 고친 제목", "", pkg, 0, seenTitle = "Bhagavad-gita As It Is")
        val result = Acoustext.open(HomeContext(context, component), book)
        val start = result as OpenResult.Start
        assertEquals("acoustext", start.intent.data!!.scheme)
        assertEquals("book", start.intent.data!!.host)
        assertEquals("Bhagavad-gita As It Is", start.intent.data!!.getQueryParameter("title"))
        assertEquals(component, start.intent.component)
        // 서비스가 읽는 화면을 열 때 그 책으로 적도록 제목을 남긴다(한 번 꺼내면 지운다)
        start.started!!.invoke()
        assertEquals("Bhagavad-gita As It Is", Acoustext.takePending(context)?.title)
        assertNull(Acoustext.pendingBook(context))
    }
}
