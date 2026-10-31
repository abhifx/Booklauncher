package com.woody.booklauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.woody.booklauncher.readers.AppContext
import com.woody.booklauncher.readers.AppWindows
import com.woody.booklauncher.readers.Cell
import com.woody.booklauncher.readers.Click
import com.woody.booklauncher.readers.CoverSpot
import com.woody.booklauncher.readers.ReaderAdapter
import com.woody.booklauncher.readers.ReaderApp
import com.woody.booklauncher.readers.ScreenReader
import com.woody.booklauncher.readers.ViewerTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 공통 흐름의 연결 시험(docs/reader-adapter-plan.md `시험 › 연결 시험`): 가짜 알림·가짜 시계·가짜 기록기로 훅을 부르는 순서,
 * 예약이 전체에 하나인 것, 화면 기억이 앱마다 따로인 것, 앱 처리에서 난 예외, 연결·끝의 열림·닫힘 표시를 본다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WatchCoreTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @After fun tearDown() {
        BookShelf.clearCaches()
        ReadingLog.clearCache()
        Db.closeForTest()
        Storage.of(app).getDatabasePath("pebble.db").parentFile?.deleteRecursively()
    }

    /** 손으로 돌리는 시계: [advance] 하면 그때가 된 예약을 순서대로 부른다 */
    private class Clock : WatchClock {
        var t = 1_000_000L
        private val posted = mutableListOf<Pair<Long, Runnable>>()
        override fun now() = t
        override fun post(r: Runnable, delayMs: Long) { posted += (t + delayMs) to r }
        override fun remove(r: Runnable) { posted.removeAll { it.second === r } }
        fun advance(ms: Long) {
            val end = t + ms
            while (true) {
                val next = posted.filter { it.first <= end }.minByOrNull { it.first } ?: break
                posted.remove(next)
                t = next.first
                next.second.run()
            }
            t = end
        }
    }

    private class Host(override val context: Context) : WatchHost {
        /** 앞에 있는 앱의 화면(찍기 시험만) */
        var front: AccessibilityNodeInfo? = null
        /** 찍는 사이 앱을 떠난다 */
        var leaveOnShot = false
        override fun root(pkg: String): AccessibilityNodeInfo? = front
        override fun windows(pkg: String) = AppWindows(null, emptyList())
        override val noActiveWindow = false
        override fun isActivity(pkg: String, cls: String) = true
        override var canScreenshot = false
        /** 찍는 사이 일어나는 일 */
        var onShot: () -> Unit = {}
        override fun screenshot(done: (Bitmap) -> Unit) {
            if (leaveOnShot) front = null
            onShot()
            done(Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888))
        }
    }

    /** 부른 것을 적어 두는 기록기(편 책 기록은 앱마다 마지막 것) */
    private class Recorder(private val failOpen: Boolean = false) : BookRecorder {
        val calls = mutableListOf<String>()
        val opened = mutableMapOf<String, String?>()
        override fun opened(pkg: String, seen: Seen, at: Long?, target: Target, coverKey: String?, steps: OpenSteps?): ShelfBook {
            calls += "opened:$pkg:${seen.title}:$at"
            if (failOpen) error("저장 실패")
            opened[pkg] = seen.title
            return ShelfBook("id-${seen.title}", seen.title, "", null, 0)
        }
        override fun unknown(pkg: String, at: Long) { calls += "unknown:$pkg:$at"; opened[pkg] = null }
        override fun status(bookId: String, progress: Int, due: Long, author: String, chapter: String) { calls += "status:$bookId:$progress" }
        override fun statuses(list: List<BookShelf.Status>) {}
        override fun setFile(bookId: String, path: String) {}
        override fun setTitle(bookId: String, title: String) {}
        override fun setAuthor(bookId: String, author: String) {}
        override fun cover(bookId: String, jpeg: ByteArray) {}
        override fun pendingCover(key: String, jpeg: ByteArray) {}
        override fun hasPendingCover(key: String) = false
        override fun hasPendingTitle(title: String) = false
        override fun viewerShown(pkg: String, shown: Boolean, at: Long, current: Boolean) { calls += "shown:$pkg:$shown" }
        override fun openedId(pkg: String) = opened[pkg]?.let { "id-$it" }
        override fun recordedBook(pkg: String) = opened[pkg]?.let { ShelfBook("id-$it", it, "", null, 0) }
        override val lookup: BookLookup get() = error("쓰지 않음")
    }

    /** 바탕 흐름에 훅을 부른 것을 적어 두는 어댑터 */
    private class Probe(app: ReaderApp, val log: MutableList<String>) : ScreenReader(app) {
        var fail = false
        override fun onEnterViewer(ctx: AppContext, from: String?, again: Boolean) { log += "enter:${ctx.pkg}:$again"; super.onEnterViewer(ctx, from, again) }
        /** 서재가 멈추면 찍을 표지 자리 */
        var spots: List<CoverSpot>? = null
        override fun onShelf(ctx: AppContext) { log += "shelf:${ctx.pkg}"; spots?.let { ctx.capture(it) } }
        override fun stillSpots(ctx: AppContext, spots: List<CoverSpot>): List<CoverSpot> { log += "still:${ctx.pkg}"; return emptyList() }
        override fun onShelfLive(ctx: AppContext, force: Boolean) { log += "live:${ctx.pkg}:$force" }
        override fun onViewer(ctx: AppContext) {
            log += "viewer:${ctx.pkg}"
            if (fail) error("앱 처리 실패")
        }
        override fun onClick(ctx: AppContext, click: Click) { log += "click:${ctx.pkg}" }
        override fun onWindow(ctx: AppContext, cls: String?) { log += "window:${ctx.pkg}" }
        override fun beforeOpened(ctx: AppContext) { log += "before:${ctx.pkg}"; super.beforeOpened(ctx) }
        override fun afterOpened(ctx: AppContext, book: ShelfBook) { log += "after:${ctx.pkg}" }
        override fun screenName(ctx: AppContext, activity: String?) = holderName ?: activity
        var holderName: String? = null
        fun open(ctx: AppContext, title: String) = ctx.opened(Seen(title))
        fun click(cell: Cell) { clicked = cell to 1_000_000L }
    }

    private val log = mutableListOf<String>()
    private val adapters = mutableMapOf<String, Probe>()

    private fun app(pkg: String, viewerTime: ViewerTime = ViewerTime.SCREEN, inside: Boolean = false, content: Boolean = true) =
        object : ReaderApp(pkg, "", setOf("$pkg.Shelf"), setOf("$pkg.ViewerActivity", "$pkg.Shelf#ViewerActivity"), emptyList(), emptyList(),
            authors = emptyList(), progress = null, due = null, viewerProgress = emptyList()) {
            override val viewerTime get() = viewerTime
            override val screenInsideActivity get() = inside
            override val readsContent get() = content
            override fun newAdapter(): ReaderAdapter = Probe(this, log).also { adapters[pkg] = it }
        }

    private val clock = Clock()

    private val host by lazy { Host(app) }

    private fun core(recorder: BookRecorder, vararg apps: ReaderApp) = WatchCore(host, clock, recorder, apps.toList())

    private fun WatchCore.window(pkg: String, cls: String) = onEvent(pkg, AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, cls, null)
    private fun WatchCore.content(pkg: String) = onEvent(pkg, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, null, null)

    @Test fun `① 읽는 화면 예약은 전체에 하나라 다른 앱 읽는 화면이 오면 앞 앱 것은 돌지 않는다`() {
        val core = core(Recorder(), app("a"), app("b"))
        core.window("a", "a.ViewerActivity")
        clock.advance(300)
        core.window("b", "b.ViewerActivity")
        clock.advance(2000)
        assertEquals(listOf("viewer:b"), log.filter { it.startsWith("viewer") })
        // 화면 기억은 앱마다 따로: a 로 돌아오면 다시 읽는 화면으로 들어온 것
        core.window("a", "a.ViewerActivity")
        clock.advance(1000)
        assertEquals(listOf("viewer:b", "viewer:a"), log.filter { it.startsWith("viewer") })
    }

    @Test fun `③ 기록이 실패해도 기억은 먼저 비우고 이어 두기는 건너뛰며 다음 알림을 받는다`() {
        val core = core(Recorder(failOpen = true), app("a"))
        core.window("a", "a.Shelf")
        adapters.getValue("a").click(Cell("책", "", -1, -1, null, android.graphics.Rect()))
        core.window("a", "a.ViewerActivity")
        assertTrue("before:a" in log)
        assertTrue("after:a" !in log)
        assertTrue(WatchLog.entries(app).any { it.kind == WatchLog.Kind.ERROR && it.pkg == "a" })
        // 실패한 뒤에도 다음 알림은 처리한다
        core.content("a")
        clock.advance(1000)
        assertTrue("viewer:a" in log)
        // 누른 칸은 이미 비워져 다음에 다시 들어와도 쓰지 않는다
        log.clear()
        core.window("a", "a.Shelf")
        core.window("a", "a.ViewerActivity")
        assertEquals(listOf("enter:a:false"), log.filter { it.startsWith("enter") || it.startsWith("before") })
    }

    @Test fun `④ 연결·끝에 선언대로 열림·닫힘을 적는다`() {
        val rec = Recorder()
        val core = core(rec, app("s"), app("c", ViewerTime.MARKS_CLOSED, inside = true), app("o", ViewerTime.MARKS_OPEN))
        core.attach()
        core.detach()
        assertEquals(listOf("shown:c:false", "shown:o:true", "shown:c:false", "shown:o:true"), rec.calls)
    }

    @Test fun `⑥ 읽는 화면을 떠나면 잠잠 예약이 돌지 않는다`() {
        val core = core(Recorder(), app("a"))
        core.window("a", "a.ViewerActivity")
        clock.advance(1000)
        assertEquals(1, log.count { it == "viewer:a" })
        core.window("a", "a.Shelf")
        clock.advance(5000)
        assertEquals(1, log.count { it == "viewer:a" })
    }

    @Test fun `⑦ 서재 안 읽는 화면은 간격 안 마지막 알림을 0점5초 뒤 다시 본다`() {
        val core = core(Recorder(), app("h", ViewerTime.MARKS_CLOSED, inside = true))
        core.window("h", "h.Shelf")
        clock.advance(600)
        core.content("h")
        // 간격 안에 읽는 화면이 뜬 알림이 오고 끊김
        adapters.getValue("h").holderName = "h.Shelf#ViewerActivity"
        clock.advance(100)
        core.content("h")
        assertTrue(log.none { it.startsWith("enter") })
        clock.advance(600)
        assertTrue("enter:h:false" in log)
    }

    @Test fun `⑦ 서비스가 끝나면 다시 보지 않는다`() {
        val core = core(Recorder(), app("h", ViewerTime.MARKS_CLOSED, inside = true))
        core.window("h", "h.Shelf")
        clock.advance(600)
        core.content("h")
        adapters.getValue("h").holderName = "h.Shelf#ViewerActivity"
        clock.advance(100)
        core.content("h")
        core.detach()
        clock.advance(1000)
        assertTrue(log.none { it.startsWith("enter") })
    }

    @Test fun `⑯ 그 앱의 알림마다 서재 멈춤을 다시 예약하고, 내용을 읽지 않는 앱은 그 밖에 아무것도 하지 않는다`() {
        val core = core(Recorder(), app("k", content = false))
        core.window("k", "k.Shelf")
        clock.advance(1000)
        core.content("k")
        clock.advance(1000)
        assertTrue(log.none { it.startsWith("shelf") })
        clock.advance(600)
        assertEquals(listOf("live:k:true", "shelf:k"), log.filter { it.startsWith("live") || it.startsWith("shelf") })
    }

    @Test fun `앱 처리에서 난 예외는 적고 다음 알림을 받는다`() {
        val core = core(Recorder(), app("a"))
        core.window("a", "a.ViewerActivity")
        adapters.getValue("a").fail = true
        clock.advance(1000)
        assertTrue(WatchLog.entries(app).any { it.kind == WatchLog.Kind.ERROR && it.pkg == "a" })
        core.window("a", "a.Shelf")
        clock.advance(2000)
        assertTrue("shelf:a" in log)
    }

    @Test fun `찍는 사이 그 앱을 떠났으면 찍은 것을 버린다`() {
        host.canScreenshot = true
        host.front = AccessibilityNodeInfo.obtain()
        val core = core(Recorder(), app("a"))
        core.window("a", "a.Shelf")
        adapters.getValue("a").spots = listOf(CoverSpot("책", Rect(0, 0, 5, 5)))
        clock.advance(2000)
        assertTrue("still:a" in log)
        log.clear()
        host.leaveOnShot = true
        core.content("a")
        clock.advance(2000)
        assertTrue("shelf:a" in log)
        assertTrue(log.none { it.startsWith("still") })
    }

    @Test fun `찍는 사이 그 앱의 읽는 화면으로 넘어갔으면 찍은 것을 버린다`() {
        host.canScreenshot = true
        host.front = AccessibilityNodeInfo.obtain()
        val core = core(Recorder(), app("a"))
        core.window("a", "a.Shelf")
        adapters.getValue("a").spots = listOf(CoverSpot("책", Rect(0, 0, 5, 5)))
        host.onShot = { core.window("a", "a.ViewerActivity") }
        clock.advance(2000)
        assertTrue("shelf:a" in log)
        assertTrue(log.none { it.startsWith("still") })
    }

    @Test fun `같은 화면 알림이 한 번 더 오면 다시 들어온 것이 아니다`() {
        val core = core(Recorder(), app("a"))
        core.window("a", "a.ViewerActivity")
        core.window("a", "a.ViewerActivity")
        assertEquals(listOf("enter:a:false", "enter:a:true"), log.filter { it.startsWith("enter") })
    }
}
