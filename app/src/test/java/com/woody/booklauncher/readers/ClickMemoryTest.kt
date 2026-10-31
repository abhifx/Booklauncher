package com.woody.booklauncher.readers

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.woody.booklauncher.BookLookup
import com.woody.booklauncher.BookRecorder
import com.woody.booklauncher.BookShelf
import com.woody.booklauncher.OpenSteps
import com.woody.booklauncher.Seen
import com.woody.booklauncher.ShelfBook
import com.woody.booklauncher.Target
import com.woody.booklauncher.WatchLog
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 누른 칸·책 상세를 읽는 화면이 열릴 때까지 기억하는 규칙(가짜 앱 문맥·손으로 돌리는 시계):
 * 받기만 한 칸이 그 뒤 연 다른 책 대신 들어가지 않고(교보도서관), 받기 창 뒤에 기억한 상세보다 그 뒤 누른 칸이 먼저다(밀리 e-ink).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClickMemoryTest {
    private val pkg = "test.reader"
    private val shelf = "$pkg.ShelfActivity"
    private val viewer = "$pkg.ViewerActivity"

    /** 서재에서 칸이 아닌 것을 누르면 앞서 누른 칸을 잊는 앱([forgets]) */
    private inner class TestApp(val forgets: Boolean) : ReaderApp(
        pkg, pkg, setOf(shelf), setOf(viewer), titles = listOf("title"), thumbs = listOf("cover"),
        authors = emptyList(), progress = null, due = null, viewerProgress = emptyList(), openOnViewer = true,
    ) {
        override val forgetsClickOnOtherTap get() = forgets
    }

    private val app: ReaderApp = TestApp(forgets = true)

    private fun node(cls: String, l: Int, t: Int, r: Int, b: Int, id: String? = null, text: String? = null, vararg kids: AccessibilityNodeInfo) =
        AccessibilityNodeInfo.obtain().apply {
            className = cls
            setBoundsInScreen(Rect(l, t, r, b))
            id?.let { viewIdResourceName = "$pkg:id/$it" }
            text?.let { this.text = it }
            kids.forEach { shadowOf(this).addChild(it) }
        }

    /** 요소 [n] 을 창 [window] 의 것으로(아래 요소는 사본이 나와 누를 요소는 따로 정한다) */
    private fun inWindow(n: AccessibilityNodeInfo, window: Int) = n.also {
        AccessibilityNodeInfo::class.java.getDeclaredField("mWindowId").apply { isAccessible = true }.setInt(it, window)
    }

    /** 창 [window] 의 요소로 굳힌다(아래 요소까지) */
    private fun seal(n: AccessibilityNodeInfo, window: Int = SHELF_WINDOW): AccessibilityNodeInfo {
        for (i in 0 until n.childCount) seal(n.getChild(i), window)
        inWindow(n, window)
        AccessibilityNodeInfo::class.java.getMethod("setSealed", Boolean::class.javaPrimitiveType).invoke(n, true)
        return n
    }

    /** 서재 화면(창 [SHELF_WINDOW])이 떴다 */
    private fun showShelf(ctx: Ctx, a: ReaderAdapter) {
        ctx.screen = shelf
        a.onScreen(ctx, shelf, null, again = false, byWindow = true)
    }

    private fun cell(top: Int, title: String) = node("android.widget.FrameLayout", 0, top, 300, top + 400, null, null,
        node("android.widget.ImageView", 0, top, 300, top + 300, "cover"),
        node("android.widget.TextView", 0, top + 310, 300, top + 360, "title", title))

    /** 가짜 앱 문맥: 편 책은 [opened] 에 적는다 */
    private inner class Ctx(var t: Long = 1_000_000L) : AppContext {
        val opened = mutableListOf<String>()
        var windowsNow = AppWindows(null, emptyList())
        override val context: Context = RuntimeEnvironment.getApplication()
        override val app: ReaderApp get() = this@ClickMemoryTest.app
        override val recorder: BookRecorder = object : BookRecorder {
            override fun opened(pkg: String, seen: Seen, at: Long?, target: Target, coverKey: String?, steps: OpenSteps?) = error("ctx 로만")
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
        var adapter: ReaderAdapter? = null
        override fun opened(seen: Seen, at: Long?, target: Target, coverKey: String?, steps: OpenSteps?): ShelfBook {
            adapter?.beforeOpened(this)
            opened += seen.title
            return ShelfBook("id-${seen.title}", seen.title, "", null, 0)
        }
        override fun unknown(at: Long) {}
        override var unknownOpenAt: Long? = null
        override val now: Long get() = t
        override var screen: String? = shelf
        override var viewerOpenedAt: Long? = null
        override val lastTransition: Long = 0
        override val lastOpenedAt: Long? = null
        override val root: AccessibilityNodeInfo? get() = windowsNow.active
        override fun windows() = windowsNow
        override val prefs: SharedPreferences get() = context.getSharedPreferences("click_memory_test", Context.MODE_PRIVATE)
        override fun log(kind: WatchLog.Kind, arg: String, detail: String) {}
        override fun noWindow() {}
        override val canShoot = false
        override fun capture(spots: List<CoverSpot>, retry: Boolean) {}
        override fun viewerAgain(ms: Long) {}
        override fun viewerRetry() = false
        override fun <T> inBackground(work: () -> T, done: (Result<T>) -> Unit) = done(runCatching(work))
    }

    private fun enterViewer(ctx: Ctx, a: ReaderAdapter) {
        ctx.screen = viewer
        ctx.viewerOpenedAt = ctx.t
        a.onScreen(ctx, viewer, shelf, again = false, byWindow = true)
    }

    @Test fun `받기만 한 칸 뒤에 서재에서 칸이 아닌 것을 누르면 그 칸을 버린다`() {
        val a = cell(400, "받기만 한 책")
        val tab = inWindow(node("android.widget.TextView", 0, 100, 300, 200, null, "표지 보기"), SHELF_WINDOW)
        val root = seal(node("android.widget.FrameLayout", 0, 0, 1080, 2000, null, null, tab,
            node("androidx.recyclerview.widget.RecyclerView", 0, 300, 1080, 2000, null, null, a)))
        val ctx = Ctx().apply { windowsNow = AppWindows(root, listOf(root)) }
        val r = ScreenReader(app).also { ctx.adapter = it }
        showShelf(ctx, r)
        r.onClick(ctx, Click(a.getChild(0), emptyList()))
        ctx.t += 5_000
        r.onClick(ctx, Click(tab, emptyList()))
        ctx.t += 5_000
        enterViewer(ctx, r)
        assertEquals(emptyList<String>(), ctx.opened)
    }

    @Test fun `화면이 바뀔 때 앞 화면 창을 잡았어도 서재가 멈추면 서재 창으로 다시 잡는다`() {
        val a = cell(400, "받기만 한 책")
        val tab = inWindow(node("android.widget.TextView", 0, 100, 300, 200, null, "표지 보기"), SHELF_WINDOW)
        val root = seal(node("android.widget.FrameLayout", 0, 0, 1080, 2000, null, null, tab,
            node("androidx.recyclerview.widget.RecyclerView", 0, 300, 1080, 2000, null, null, a)))
        val splash = seal(node("android.widget.FrameLayout", 0, 0, 1080, 2000), DIALOG_WINDOW)
        val ctx = Ctx().apply { windowsNow = AppWindows(splash, listOf(splash)) }
        val r = ScreenReader(app).also { ctx.adapter = it }
        showShelf(ctx, r)
        ctx.windowsNow = AppWindows(root, listOf(root))
        r.onShelf(ctx)
        r.onClick(ctx, Click(a.getChild(0), emptyList()))
        ctx.t += 5_000
        r.onClick(ctx, Click(tab, emptyList()))
        ctx.t += 5_000
        enterViewer(ctx, r)
        assertEquals(emptyList<String>(), ctx.opened)
    }

    @Test fun `잊는 앱이 아니면(기본) 서재에서 칸이 아닌 것을 눌러도 앞서 누른 칸을 둔다`() {
        val a = cell(400, "받기만 한 책")
        val tab = inWindow(node("android.widget.TextView", 0, 100, 300, 200, null, "표지 보기"), SHELF_WINDOW)
        val root = seal(node("android.widget.FrameLayout", 0, 0, 1080, 2000, null, null, tab,
            node("androidx.recyclerview.widget.RecyclerView", 0, 300, 1080, 2000, null, null, a)))
        val ctx = Ctx().apply { windowsNow = AppWindows(root, listOf(root)) }
        val r = ScreenReader(TestApp(forgets = false)).also { ctx.adapter = it }
        showShelf(ctx, r)
        r.onClick(ctx, Click(a.getChild(0), emptyList()))
        ctx.t += 5_000
        r.onClick(ctx, Click(tab, emptyList()))
        ctx.t += 5_000
        enterViewer(ctx, r)
        assertEquals(listOf("받기만 한 책"), ctx.opened)
    }

    @Test fun `위에 뜬 창(받기 안내)의 단추는 그 칸을 열려는 것이라 둔다`() {
        val a = cell(400, "안내 뒤에 여는 책")
        val root = seal(node("android.widget.FrameLayout", 0, 0, 1080, 2000, null, null,
            node("androidx.recyclerview.widget.RecyclerView", 0, 300, 1080, 2000, null, null, a)))
        val ok = inWindow(node("android.widget.Button", 600, 1100, 900, 1200, null, "확인"), DIALOG_WINDOW)
        val dialog = seal(node("android.widget.FrameLayout", 100, 800, 980, 1250, null, null, ok), DIALOG_WINDOW)
        val ctx = Ctx().apply { windowsNow = AppWindows(root, listOf(root)) }
        val r = ScreenReader(app).also { ctx.adapter = it }
        showShelf(ctx, r)
        r.onClick(ctx, Click(a.getChild(0), emptyList()))
        ctx.windowsNow = AppWindows(dialog, listOf(dialog, root))
        ctx.t += 2_000
        r.onClick(ctx, Click(ok, emptyList()))
        ctx.t += 2_000
        enterViewer(ctx, r)
        assertEquals(listOf("안내 뒤에 여는 책"), ctx.opened)
    }

    @Test fun `화면만 한 모달 대화상자(아래 창이 창 목록에서 빠짐)나 누름 알림 전에 닫힌 창의 단추도 둔다`() {
        val a = cell(400, "안내 뒤에 여는 책")
        val root = seal(node("android.widget.FrameLayout", 0, 0, 1080, 2000, null, null,
            node("androidx.recyclerview.widget.RecyclerView", 0, 300, 1080, 2000, null, null, a)))
        val ok = inWindow(node("android.widget.Button", 600, 1100, 900, 1200, null, "확인"), DIALOG_WINDOW)
        val dialog = seal(node("android.widget.FrameLayout", 0, 0, 1080, 2000, null, null, ok), DIALOG_WINDOW)
        for (closed in listOf(false, true)) {
            val ctx = Ctx().apply { windowsNow = AppWindows(root, listOf(root)) }
            val r = ScreenReader(app).also { ctx.adapter = it }
            showShelf(ctx, r)
            r.onClick(ctx, Click(a.getChild(0), emptyList()))
            // 모달 창은 그 아래 창을 창 목록에서 뺀다. 닫힌 뒤에 알림을 받으면 맨 앞은 다시 서재다
            ctx.windowsNow = if (closed) AppWindows(root, listOf(root)) else AppWindows(dialog, listOf(dialog))
            ctx.t += 2_000
            r.onClick(ctx, Click(ok, emptyList()))
            ctx.t += 2_000
            enterViewer(ctx, r)
            assertEquals(listOf("안내 뒤에 여는 책"), ctx.opened)
        }
    }

    private companion object {
        const val SHELF_WINDOW = 1
        const val DIALOG_WINDOW = 2
    }

    /** 책 상세를 [detailRoot] 에서만 보는 상세 어댑터. [hidden] 이면 상세가 안 보여도 기억을 둔다(밀리 받기 창) */
    private inner class Detail(val detailRoot: AccessibilityNodeInfo) :
        DetailReader(app, clickOnly = false, { r -> if (r === detailRoot) Cell("상세의 책", "", -1, -1, null, Rect()) else null }) {
        var hidden = false
        override fun keepsHidden(ctx: AppContext, gone: Long) = hidden
        fun click(cell: Cell, at: Long) { clicked = cell to at }
    }

    @Test fun `받기 창 뒤에 기억한 상세보다 그 뒤 서재에서 누른 칸이 먼저다`() {
        val detail = seal(node("android.widget.FrameLayout", 0, 0, 1080, 2000))
        val dialog = seal(node("android.widget.FrameLayout", 100, 800, 980, 1250))
        val ctx = Ctx().apply { windowsNow = AppWindows(detail, listOf(detail)) }
        val d = Detail(detail).also { ctx.adapter = it }
        d.onShelfLive(ctx, force = true)
        // `바로 읽기` → 받기 창(상세가 가려짐) → 받기 취소
        ctx.windowsNow = AppWindows(dialog, listOf(dialog)); d.hidden = true
        ctx.t += 1_000; d.onShelfLive(ctx, force = true)
        ctx.t += 10_000; d.onShelfLive(ctx, force = true)
        // 서재에서 받아 둔 다른 책을 눌러 연다
        ctx.t += 2_000; d.click(Cell("누른 책", "", -1, -1, null, Rect()), ctx.t)
        ctx.t += 1_000
        enterViewer(ctx, d)
        assertEquals(listOf("누른 책"), ctx.opened)
    }

    @Test fun `받는 동안(누른 칸 없이) 상세가 오래 가려져도 그 상세의 책`() {
        val detail = seal(node("android.widget.FrameLayout", 0, 0, 1080, 2000))
        val dialog = seal(node("android.widget.FrameLayout", 100, 800, 980, 1250))
        val ctx = Ctx().apply { windowsNow = AppWindows(detail, listOf(detail)) }
        val d = Detail(detail).also { ctx.adapter = it }
        d.onShelfLive(ctx, force = true)
        ctx.windowsNow = AppWindows(dialog, listOf(dialog)); d.hidden = true
        ctx.t += 1_000; d.onShelfLive(ctx, force = true)
        ctx.t += 15_000; d.onShelfLive(ctx, force = true)
        enterViewer(ctx, d)
        assertEquals(listOf("상세의 책"), ctx.opened)
    }

    @Test fun `상세가 사라진 때를 아직 적지 않았어도 그 뒤 누른 칸이 먼저다`() {
        val detail = seal(node("android.widget.FrameLayout", 0, 0, 1080, 2000))
        val ctx = Ctx().apply { windowsNow = AppWindows(detail, listOf(detail)) }
        val d = Detail(detail).also { ctx.adapter = it }
        d.onShelfLive(ctx, force = true)
        // 뒤로 나가 서재에서 바로 칸을 누른다(1초마다 읽기가 돌기 전)
        ctx.t += 500; d.click(Cell("누른 책", "", -1, -1, null, Rect()), ctx.t)
        ctx.t += 300
        enterViewer(ctx, d)
        assertEquals(listOf("누른 책"), ctx.opened)
    }

    @Test fun `가리는 창이 없으면 사라진 지 3초 넘은 상세는 쓰지 않는다`() {
        val detail = seal(node("android.widget.FrameLayout", 0, 0, 1080, 2000))
        val other = seal(node("android.widget.FrameLayout", 0, 0, 1080, 2000))
        val ctx = Ctx().apply { windowsNow = AppWindows(detail, listOf(detail)) }
        val d = Detail(detail).also { ctx.adapter = it }
        d.onShelfLive(ctx, force = true)
        ctx.windowsNow = AppWindows(other, listOf(other))
        ctx.t += 1_000; d.onShelfLive(ctx, force = true)
        ctx.t += 5_000; d.onShelfLive(ctx, force = true)
        enterViewer(ctx, d)
        assertEquals(emptyList<String>(), ctx.opened)
    }
}
