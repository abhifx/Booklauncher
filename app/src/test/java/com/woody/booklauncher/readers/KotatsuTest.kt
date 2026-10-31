package com.woody.booklauncher.readers

import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Futon(9.9)·Kotatsu: 누른 칸(내장 메모리 격자 칸, 작품 상세의 `계속`, 탐색의 소스 칸), 읽는 화면 메뉴의 위 막대(작품 이름·화 이름, 불러오는 중), 두 판의 화면 이름.
 * 화면은 크레마 페블 에뮬레이터(1072×1456)에서 본 모양 그대로. 서재 칸은 이름으로 찾는데 Robolectric 은 이름 찾기를 흉내 내지 않아, 칸을 눌렀을 때(직접 훑는다)로 본다
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KotatsuTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val pkg = Futon.pkg

    private fun node(cls: String, l: Int, t: Int, r: Int, b: Int, id: String? = null, text: String? = null, vararg kids: AccessibilityNodeInfo) =
        AccessibilityNodeInfo.obtain().apply {
            className = cls
            setBoundsInScreen(Rect(l, t, r, b))
            id?.let { viewIdResourceName = "$pkg:id/$it" }
            text?.let { this.text = it }
            isVisibleToUser = true
            kids.forEach { shadowOf(this).addChild(it) }
        }

    /** 서비스가 받는 요소처럼 굳힌다(굳지 않은 요소는 이름으로 찾기가 안 된다) */
    private fun seal(n: AccessibilityNodeInfo): AccessibilityNodeInfo {
        for (i in 0 until n.childCount) seal(n.getChild(i))
        AccessibilityNodeInfo::class.java.getMethod("setSealed", Boolean::class.javaPrimitiveType).invoke(n, true)
        return n
    }

    private fun screen(vararg kids: AccessibilityNodeInfo) = seal(node("android.widget.FrameLayout", 0, 0, 1072, 1456, "content", null, *kids))

    /** 내장 메모리 격자 칸: 표지, 표지 위 진행 고리([ring] 이면), 제목 */
    private fun gridCell(l: Int, title: String, ring: Boolean) = node(
        "android.view.ViewGroup", l, 285, l + 230, 693, null, null,
        *listOfNotNull(
            node("android.widget.ImageView", l, 285, l + 230, 603, "imageView_cover"),
            if (ring) node("android.view.View", l + 155, 528, l + 215, 588, "progressView") else null,
            node("android.widget.TextView", l, 603, l + 230, 693, "textView_title", title),
        ).toTypedArray(),
    )

    private fun clicked(root: AccessibilityNodeInfo, source: AccessibilityNodeInfo) =
        Futon.clickedCell(source, emptyList(), AppWindows(root, listOf(root)), null)

    @Test fun `내장 메모리 격자 칸은 제목, 표지 위에 진행 고리가 있으면 표지는 자르지 않는다`() {
        val ringed = gridCell(30, "별빛 도서관", ring = true)
        val clean = gridCell(290, "Sea Song", ring = false)
        val root = screen(node("androidx.recyclerview.widget.RecyclerView", 0, 255, 1072, 1456, "recyclerView", null, ringed, clean))
        val first = clicked(root, ringed.getChild(0))!!
        assertEquals("별빛 도서관", first.title)
        assertNull(first.thumb)
        val second = clicked(root, clean.getChild(1))!!
        assertEquals("Sea Song", second.title)
        assertEquals(Rect(290, 285, 520, 603), second.thumb)
    }

    /** 작품 상세: 넘기는 칸 안에 표지·제목·상세 표(저자·진행률), 아래 시트에 `계속` 단추와 화 목록([chapters]) */
    private fun details(coverTop: Int = 195, vararg chapters: String): Pair<AccessibilityNodeInfo, AccessibilityNodeInfo> {
        val read = node("android.widget.Button", 783, 1351, 956, 1441, "button_read", "계속")
        val root = screen(node(
            "android.widget.ScrollView", 0, 165, 1072, 1246, "scrollView", null,
            node("android.view.ViewGroup", 0, 165, 1072, 1246, null, null,
                node("android.widget.ImageView", 30, coverTop, 352, coverTop + 445, "imageView_cover"),
                node("android.widget.TextView", 382, 195, 1042, 256, "textView_title", "Sea Song"),
                node("androidx.cardview.widget.CardView", 30, 670, 1042, 846, "card_details", null,
                    node("android.widget.TextView", 53, 685, 102, 721, "textView_author_label", "저자"),
                    node("android.widget.TextView", 200, 685, 600, 721, "textView_author", "김바다"),
                    node("android.widget.TextView", 53, 787, 159, 823, "textView_progress_label", "Progress"),
                    node("android.widget.TextView", 961, 787, 1012, 823, "textView_progress", "33%"),
                ),
            ),
        ), node("android.widget.FrameLayout", 0, 1246, 1072, 1456, "container_bottom_sheet", null,
            read,
            node("androidx.recyclerview.widget.RecyclerView", 0, 1456, 1072, 2000, "recyclerView_chapters", null,
                *chapters.mapIndexed { i, c -> node("android.widget.TextView", 30, 1456 + i * 100, 1042, 1540 + i * 100, "textView_title", c) }.toTypedArray()),
        ))
        return root to read
    }

    @Test fun `작품 상세는 표지·제목과 표의 저자·작품 진행률`() {
        val cell = Futon.detail(details(195, "1화", "2화").first)!!
        assertEquals("Sea Song", cell.title)
        assertEquals("김바다", cell.author)
        assertEquals(33, cell.progress)
        assertEquals(Rect(30, 195, 352, 640), cell.thumb)
    }

    @Test fun `넘겨서 위 막대 밑으로 들어간 상세 표지는 자르지 않는다`() = assertNull(Futon.detail(details(100).first)!!.thumb)

    @Test fun `상세 표가 없는 화면(내장 메모리 격자)은 상세가 아니다`() {
        val grid = screen(node("androidx.recyclerview.widget.RecyclerView", 0, 255, 1072, 1456, "recyclerView", null, gridCell(30, "Sea Song", ring = false)))
        assertNull(Futon.detail(grid))
    }

    /** 그래서 상세를 기억해 두는 길([DetailReader])로 안다 */
    @Test fun `아래 시트에 화 목록이 있으면 상세의 계속은 누른 칸으로 잡히지 않는다`() {
        val (root, read) = details(195, "1화", "2화")
        assertNull(clicked(root, read))
    }

    @Test fun `작품 상세 화면의 서재 칸은 상세 하나, 넘겨서 가린 표지는 빼고`() {
        assertEquals(listOf("Sea Song"), Futon.shelfCells(details(195, "1화").first).map { it.title })
        assertNull(Futon.shelfCells(details(100).first).single().thumb)
    }

    @Test fun `탐색 탭의 소스 칸은 책이 아니다`() {
        val source = node("android.view.ViewGroup", 30, 574, 208, 760, null, null,
            node("android.widget.ImageView", 51, 574, 186, 709, "imageView_icon"),
            node("android.widget.TextView", 30, 724, 208, 760, "textView_title", "Royal Road"),
        )
        val root = screen(node("androidx.recyclerview.widget.RecyclerView", 0, 210, 1072, 1456, "recyclerView", null, source))
        assertNull(clicked(root, source))
    }

    /** 읽는 화면: 메뉴를 띄우면 위 막대(`appbar_top` › `toolbar`)에 뒤로 단추와 글자들 */
    private fun reader(vararg bar: String?) = screen(
        node("androidx.viewpager.widget.ViewPager", 0, 0, 1072, 1456, "pager"),
        node("android.widget.LinearLayout", 0, 0, 1072, 165, "appbar_top", null,
            node("android.view.ViewGroup", 0, 45, 1072, 165, "toolbar", null,
                node("android.widget.ImageButton", 0, 52, 105, 157),
                *bar.filterNotNull().mapIndexed { i, t -> node("android.widget.TextView", 112, 56 + i * 56, 400, 112 + i * 41, null, t) }.toTypedArray(),
            ),
        ),
    )

    private fun read(root: AccessibilityNodeInfo) = Futon.readViewer(AppWindows(root, listOf(root)))

    @Test fun `메뉴의 위 막대는 작품 이름과 화 이름, 진행률은 없다`() {
        val shown = read(reader("Sea Song", "Unnamed chapter"))
        assertEquals("Sea Song", shown.title)
        assertEquals("Unnamed chapter", shown.chapter)
        assertEquals(-1, shown.progress)
    }

    @Test fun `작품을 불러오는 동안의 글자 하나는 제목이 아니다`() = assertNull(read(reader("불러오는 중…")).title)

    @Test fun `메뉴를 띄우지 않으면 제목을 모른다`() =
        assertNull(read(screen(node("androidx.viewpager.widget.ViewPager", 0, 0, 1072, 1456, "pager"))).title)

    @Test fun `두 판은 이름공간만 다르고 Futon 은 소설·PDF 읽는 화면도 센다`() {
        assertTrue(Kotatsu.isViewer(context, "org.koitharu.kotatsu.reader.ui.ReaderActivity"))
        assertTrue(Futon.isViewer(context, "io.github.landwarderer.futon.reader.ui.ReaderActivity"))
        assertTrue(Futon.isViewer(context, "io.github.landwarderer.futon.novel.ui.reader.NovelReaderActivity"))
        assertFalse(Kotatsu.isViewer(context, "org.koitharu.kotatsu.novel.ui.reader.NovelReaderActivity"))
        assertTrue("org.koitharu.kotatsu.details.ui.DetailsActivity" in Kotatsu.shelves)
        assertTrue("io.github.landwarderer.futon.details.ui.DetailsActivity" in Futon.shelves)
    }
}
