package com.woody.pebbledesk.readers

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.woody.pebbledesk.BookLookup
import com.woody.pebbledesk.BookRecorder
import com.woody.pebbledesk.BookShelf
import com.woody.pebbledesk.OpenSteps
import com.woody.pebbledesk.Seen
import com.woody.pebbledesk.ShelfBook
import com.woody.pebbledesk.Target
import com.woody.pebbledesk.WatchLog
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
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadows.ShadowAccessibilityNodeInfo

/**
 * YES24 뷰어(YES24 eBook·YES24 도서관): 메뉴 위 막대의 제목만 책 제목으로, 볼륨키 안내 창의 `tv_title` 은 아니다([Yes24Viewer]).
 * YES24 eBook 내서재 표지는 앞서 읽었을 때와 자리가 같고([Yes24Ebook.eagerWhenStill]) 찍은 뒤에도 그대로인 칸만([Yes24Ebook.recheckCovers]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [Yes24ViewerTest.Nodes::class])
class Yes24ViewerTest {
    /** Robolectric 에는 요소 이름으로 찾기가 없어 안드로이드처럼 나무를 훑어 찾는다(제목은 위 막대 안에서 다시 이름으로 찾는다) */
    @Implements(AccessibilityNodeInfo::class)
    class Nodes : ShadowAccessibilityNodeInfo() {
        @RealObject lateinit var node: AccessibilityNodeInfo
        @Implementation
        protected fun findAccessibilityNodeInfosByViewId(id: String): List<AccessibilityNodeInfo> = ArrayList<AccessibilityNodeInfo>().apply {
            fun walk(n: AccessibilityNodeInfo) {
                if (n.viewIdResourceName == id) add(n)
                for (i in 0 until n.childCount) n.getChild(i)?.let(::walk)
            }
            walk(node)
        }
    }

    private fun node(pkg: String, cls: String, l: Int, t: Int, r: Int, b: Int, id: String? = null, text: String? = null, vararg kids: AccessibilityNodeInfo) =
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

    private fun window(pkg: String, vararg kids: AccessibilityNodeInfo) =
        seal(node(pkg, "android.widget.FrameLayout", 0, 0, 1072, 1456, "content", null, *kids))

    /** 메뉴: 위 막대(뒤로·제목), 아래 막대(쪽) */
    private fun menu(pkg: String) = window(pkg,
        node(pkg, "androidx.constraintlayout.widget.ConstraintLayout", 0, 0, 1072, 150, "cl_top_area", null,
            node(pkg, "android.widget.ImageButton", 0, 30, 120, 150, "ib_toolbar_back"),
            node(pkg, "android.widget.TextView", 120, 60, 800, 120, "tv_title", "자연은 어떻게 발명하는가"),
        ),
        node(pkg, "androidx.constraintlayout.widget.ConstraintLayout", 0, 1200, 1072, 1456, "cl_bottom_area", null,
            node(pkg, "android.widget.LinearLayout", 400, 1220, 700, 1270, "ll_menu_page_area", null,
                node(pkg, "android.widget.TextView", 400, 1220, 500, 1270, null, "12"),
                node(pkg, "android.widget.TextView", 500, 1220, 550, 1270, null, "/"),
                node(pkg, "android.widget.TextView", 550, 1220, 700, 1270, null, "519"),
            ),
        ),
    )

    /** 읽는 화면에 뜨는 볼륨키 안내 창(기기에 따라) */
    private fun volumeGuide(pkg: String) = window(pkg,
        node(pkg, "androidx.constraintlayout.widget.ConstraintLayout", 100, 400, 972, 1100, null, null,
            node(pkg, "android.widget.ImageButton", 880, 420, 950, 490, "ib_close"),
            node(pkg, "android.widget.TextView", 150, 650, 922, 710, "tv_title", "볼륨키로 페이지를 넘겨보세요."),
            node(pkg, "android.widget.TextView", 150, 720, 922, 820, "tv_description", "볼륨키 상하 버튼으로 이전, 다음 페이지로 이동할 수 있습니다."),
            node(pkg, "android.widget.Button", 150, 950, 922, 1050, "btn_accept", "확인"),
        ),
    )

    private fun read(app: ReaderApp, vararg windows: AccessibilityNodeInfo) = app.readViewer(AppWindows(windows.last(), windows.toList()))

    @Test fun `볼륨키 안내 창의 제목은 책 제목이 아니다`() {
        for (app in listOf(Yes24Ebook, Yes24Library, Yes24LibraryMobile)) {
            assertNull(app.pkg, read(app, volumeGuide(app.pkg)).title)
        }
    }

    @Test fun `메뉴 위 막대의 제목은 책 제목, 안내 창이 함께 떠도 그대로`() {
        for (app in listOf(Yes24Ebook, Yes24Library, Yes24LibraryMobile)) {
            assertEquals(app.pkg, "자연은 어떻게 발명하는가", read(app, menu(app.pkg)).title)
            assertEquals(app.pkg, "자연은 어떻게 발명하는가", read(app, menu(app.pkg), volumeGuide(app.pkg)).title)
        }
    }

    @Test fun `YES24 eBook 내서재 표지는 찍은 뒤 자리가 그대로인 칸만`() {
        assertTrue(Yes24Ebook.recheckCovers)
        val cover = Rect(30, 300, 230, 590)
        fun cell(title: String, thumb: Rect?) = Cell(title, "", -1, -1, thumb, Rect())
        // 같은 책 칸이 둘(화면 밖 탭의 칸)이어도 하나가 그 자리면 된다
        assertTrue(Yes24Ebook.coverStill(listOf(cell("책", cover), cell("책", Rect())), "책", cover))
        // 넘기는 중이었다: 찍은 뒤 다시 읽으니 칸이 옆으로 갔다
        assertFalse(Yes24Ebook.coverStill(listOf(cell("책", Rect(-500, 300, -300, 590))), "책", cover))
        // 바로 자르기는 앞서 읽었을 때와 같은 자리의 표지만
        assertTrue(Yes24Ebook.eagerWhenStill)
        // 다른 앱(이름으로 칸을 찾는 앱)은 그대로
        assertFalse(Yes24Library.recheckCovers)
        assertFalse(Yes24Library.eagerWhenStill)
    }

    /** 내서재 목록: 칸 하나(표지 왼쪽 끝 [left], 제목) */
    private fun shelf(pkg: String, left: Int) = window(pkg,
        node(pkg, "androidx.recyclerview.widget.RecyclerView", 0, 250, 1072, 1456, "recycler_view", null,
            node(pkg, "android.view.ViewGroup", left - 56, 290, left + 1016, 600, null, null,
                node(pkg, "android.widget.ImageView", left, 317, left + 151, 541, "iv_cover"),
                node(pkg, "android.widget.TextView", left + 198, 330, left + 931, 371, "tv_title", "쿵후보이 친미 (개정판)"),
            ),
        ),
    )

    /** 가짜 앱 문맥: 바로 자르기로 넘긴 표지 자리는 [shots] 에(찍을 수 있고, 목록에 없는 책·잘라 둔 표지 없음) */
    private class Ctx(override val app: ReaderApp) : AppContext {
        var t = 10_000L
        var rootNow: AccessibilityNodeInfo? = null
        val shots = mutableListOf<List<Rect>>()
        override val context: Context = RuntimeEnvironment.getApplication()
        override val recorder: BookRecorder = object : BookRecorder {
            override fun opened(pkg: String, seen: Seen, at: Long?, target: Target, coverKey: String?, steps: OpenSteps?) = error("자르기만")
            override fun unknown(pkg: String, at: Long) {}
            override fun status(bookId: String, progress: Int, due: Long, author: String, chapter: String) {}
            override fun statuses(list: List<BookShelf.Status>) {}
            override fun setFile(bookId: String, path: String) {}
            override fun setTitle(bookId: String, title: String) {}
            override fun setAuthor(bookId: String, author: String) {}
            override fun cover(bookId: String, jpeg: ByteArray) {}
            override fun pendingCover(key: String, jpeg: ByteArray) {}
            override fun hasPendingCover(key: String) = false
            override fun hasPendingTitle(title: String) = false
            override fun viewerShown(pkg: String, shown: Boolean, at: Long, current: Boolean) {}
            override fun openedId(pkg: String): String? = null
            override fun recordedBook(pkg: String): ShelfBook? = null
            override val lookup: BookLookup = object : BookLookup {
                override fun byTitle(title: String): ShelfBook? = null
                override fun get(id: String): ShelfBook? = null
                override fun bookOf(id: String): ShelfBook? = null
                override fun byFile(path: String): String? = null
                override fun byPath(path: String): String? = null
                override fun withoutFile(title: String): String? = null
                override fun knownTitle(path: String, title: String) = title
                override fun idsByFileName(name: String) = emptySet<String>()
                override fun filePath(id: String): String? = null
                override fun hasCover(id: String) = false
            }
        }
        override fun opened(seen: Seen, at: Long?, target: Target, coverKey: String?, steps: OpenSteps?) = error("자르기만")
        override fun unknown(at: Long) {}
        override var unknownOpenAt: Long? = null
        override val now: Long get() = t
        override val screen: String = app.shelves.first()
        override val viewerOpenedAt: Long? = null
        override val lastTransition: Long = 0
        override val lastOpenedAt: Long? = null
        override val root: AccessibilityNodeInfo? get() = rootNow
        override fun windows() = AppWindows(rootNow, listOfNotNull(rootNow))
        override val prefs: SharedPreferences get() = context.getSharedPreferences("yes24_viewer_test", Context.MODE_PRIVATE)
        override fun log(kind: WatchLog.Kind, arg: String, detail: String) {}
        override fun noWindow() {}
        override val canShoot = true
        override fun capture(spots: List<CoverSpot>, retry: Boolean) { shots += spots.map { it.rect } }
        override fun viewerAgain(ms: Long) {}
        override fun viewerRetry() = false
        override fun <T> inBackground(work: () -> T, done: (Result<T>) -> Unit) = done(runCatching(work))
    }

    /** [gap] 간격으로 서재를 [lefts] 자리에서 차례로 읽어(바로 자르기, 1초보다 짧으면 창 바뀜 알림처럼 간격을 건너뛰고) 매번 넘긴 표지 자리 */
    private fun eager(app: ReaderApp, vararg lefts: Int, gap: Long = 1_000): List<List<Rect>> {
        val ctx = Ctx(app)
        val r = app.newAdapter()
        for (left in lefts) {
            ctx.rootNow = shelf(app.pkg, left)
            r.onShelfLive(ctx, force = gap < 1_000)
            ctx.t += gap
        }
        return ctx.shots
    }

    @Test fun `YES24 eBook 바로 자르기는 앞서 읽었을 때와 같은 자리의 표지만`() {
        val still = Rect(56, 317, 207, 541)
        // 처음 읽은 때는 자르지 않고, 1초 뒤 같은 자리면 자른다
        assertEquals(listOf(emptyList(), listOf(still)), eager(Yes24Ebook, 56, 56))
        // 탭을 넘기는 중(자리가 바뀌는 동안)은 자르지 않고, 멈춘 자리가 두 번 보이면 자른다
        assertEquals(listOf(emptyList(), emptyList(), emptyList(), listOf(still)), eager(Yes24Ebook, 600, 250, 56, 56))
        // 창 바뀜 알림으로 1초 안에 다시 읽은 것은 같은 자리여도 멈춘 것으로 보지 않는다
        assertEquals(listOf(emptyList<Rect>(), emptyList()), eager(Yes24Ebook, 56, 56, gap = 100))
    }

    @Test fun `다른 앱의 바로 자르기는 보이는 대로`() =
        assertEquals(listOf(listOf(Rect(600, 317, 751, 541))), eager(Yes24Library, 600))
}
