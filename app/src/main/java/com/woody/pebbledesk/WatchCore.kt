package com.woody.pebbledesk

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.woody.pebbledesk.readers.AppContext
import com.woody.pebbledesk.readers.AppWindows
import com.woody.pebbledesk.readers.Cell
import com.woody.pebbledesk.readers.Click
import com.woody.pebbledesk.readers.CoverSpot
import com.woody.pebbledesk.readers.ReaderAdapter
import com.woody.pebbledesk.readers.ReaderApp
import com.woody.pebbledesk.readers.ScreenReader
import com.woody.pebbledesk.readers.ViewerTime
import com.woody.pebbledesk.readers.installed
import com.woody.pebbledesk.readers.usableThumb
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

/** 시계와 예약(서비스는 메인 스레드 Handler, 시험은 가짜) */
interface WatchClock {
    fun now(): Long
    fun post(r: Runnable, delayMs: Long)
    fun remove(r: Runnable)
}

/** 공통 흐름이 안드로이드에서 얻는 것(접근성 서비스가 준다) */
interface WatchHost {
    val context: Context
    /** [pkg] 의 맨 앞 창(다른 앱·시스템 창이면 null) */
    fun root(pkg: String): AccessibilityNodeInfo?
    /** [pkg] 의 창들(읽는 화면 + 위에 뜬 메뉴·알림) */
    fun windows(pkg: String): AppWindows
    /** 맨 앞 창을 아예 못 얻는가(다른 앱으로 떠난 것은 아니다) */
    val noActiveWindow: Boolean
    /** [cls] 가 [pkg] 의 액티비티인가(대화상자가 아닌가) */
    fun isActivity(pkg: String, cls: String): Boolean
    /** 화면을 찍을 수 있는 기기인가(안드로이드 11 부터) */
    val canScreenshot: Boolean
    /** 화면을 한 번 찍어 [done] 으로(메인 스레드, 실패하면 부르지 않는다) */
    fun screenshot(done: (Bitmap) -> Unit)
    /** 뒤 스레드에서 실행하고 결과는 메인 스레드로 돌린다. */
    fun <T> inBackground(work: () -> T, done: (Result<T>) -> Unit) = Bg.run(work, done)
}

/** 홈에서 서비스로 가는 신호(프로세스 수명). 홈에서 바로 연 책의 파일: 읽는 화면에 그 파일 이름이 보이면 그 책을 편 것이다 */
object HomeSignals {
    private val opened = ConcurrentHashMap<String, Pair<Cell, Long>>()

    /** 홈에서 [book] 의 파일 [path] 를 [pkg] 로 바로 열었다(이지뷰어처럼 읽는 화면에 파일 이름만 보이는 앱) */
    fun fileOpened(pkg: String, book: ShelfBook, path: String) {
        opened[pkg] = Cell(book.title, book.author, -1, -1, null, Rect(), file = path) to System.currentTimeMillis()
    }

    /** [pkg] 로 홈에서 연 파일과 그 때(꺼내면 지운다) */
    fun take(pkg: String): Pair<Cell, Long>? = opened.remove(pkg)
}

/**
 * 읽는 책 자동 추가의 공통 흐름(docs/reader-adapter-plan.md `훅`·`부르는 순서`): 접근성 알림을 받아 앱마다 지금 화면을 기억하고,
 * 그 앱의 어댑터([ReaderAdapter])를 정해 둔 순서로 부른다. 앱 종류로 갈라지지 않는다.
 * 예약(읽는 화면 잠잠·서재 멈춤·마지막 재확인)과 진행률 재시도 횟수는 전체에 종류마다 하나다(새로 걸면 어느 앱 것이든 앞 예약을 지운다).
 */
class WatchCore(private val host: WatchHost, private val clock: WatchClock, private val recorder: BookRecorder, apps: List<ReaderApp>) {
    private val context get() = host.context
    private val prefs: SharedPreferences by lazy { Storage.of(context).getSharedPreferences(WATCH_PREFS, Context.MODE_PRIVATE) }
    /** 패키지마다 앱 파일의 기억(판에 따라 다른 앱이면 여럿, 알림마다 깔린 판의 것을 고른다 [entry]) */
    private val entries: Map<String, List<Entry>> = apps.map { Entry(it) }.groupBy { it.pkg }

    /** 그 앱의 화면이 마지막으로 바뀐 때(어느 앱이든) */
    private var lastTransition = 0L
    private var lastShot = 0L

    /** 화면이 멈추면 다시 읽을 서재 */
    private var settleEntry: Entry? = null
    private val settleLater = Runnable { settleEntry?.let { e -> settleEntry = null; guard(e) { if (e.screen in e.app.shelves) e.adapter.onShelf(e) } } }
    /** 읽는 화면이 잠잠해지면(쪽 넘김·메뉴 열기 뒤) 읽을 앱 */
    private var viewerEntry: Entry? = null
    /** 진행률을 아직 못 읽어 다시 읽어 볼 횟수(메뉴의 쪽 정보는 조금 늦게 채워지고, 채워질 때 알림이 없다) */
    private var viewerRetries = 0
    private val viewerLater = Runnable {
        viewerEntry?.let { e -> viewerEntry = null; guard(e) { if (e.screen?.let { e.app.isViewer(context, it) } == true) e.adapter.onViewer(e) } }
    }
    /** 읽는 화면이 서재 액티비티 안에 뜨는 앱을 마지막으로 본 때와, 미뤄 두고 다시 볼 앱 */
    private var lastHolderCheck = 0L
    private var holderEntry: Entry? = null
    private val holderLater = Runnable { holderEntry?.let { e -> holderEntry = null; guard(e) { checkHolder(e); readScreen(e, force = false) } } }

    /** 이북 앱의 접근성 알림. 다른 앱은 무시한다. [click] 은 누름 알림일 때만 */
    fun onEvent(pkg: String, type: Int, cls: String?, click: Click?) {
        val e = entry(pkg) ?: return
        WatchLog.event(context, pkg)
        guard(e) {
            // 서재는 화면이 바뀌는 동안(칸을 다시 채우는 중, 표지를 불러오는 중, 누른 칸이 반전된 순간) 읽지 않고 멈출 때까지 미룬다.
            if (settleEntry === e) scheduleSettle(e)
            when (type) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    // 대화상자 같은 것은 빼고 그 앱의 화면만 기억한다(문리더 목차 `PrefChapters` 처럼 앱 안의 대화상자도 이름이 앱 것이라 액티비티인지 본다).
                    val isScreen = cls != null && cls.startsWith(e.app.classPrefix) && host.isActivity(pkg, cls)
                    val name = cls?.takeIf { isScreen }?.let { withHolder(e, it) }
                    logScreen(e, name ?: cls.orEmpty(), isScreen)
                    if (!isScreen) e.adapter.onWindow(e, cls)
                    name?.let { enterScreen(e, it, byWindow = true) }
                    readScreen(e, force = true)
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    // 앞에 올 때만 데이터로 맞추는 앱(KOReader)은 내용이 바뀔 때마다 읽지 않는다
                    if (!e.app.readsContent) return@guard
                    checkHolder(e)
                    readScreen(e, force = false)
                }
                AccessibilityEvent.TYPE_VIEW_CLICKED -> click?.let { e.adapter.onClick(e, it) }
            }
        }
    }

    /** [pkg] 의 깔린 판을 맡는 앱 파일의 기억([installed]) */
    private fun entry(pkg: String): Entry? {
        val list = entries[pkg] ?: return null
        list.singleOrNull()?.let { return it }
        val app = list.map { it.app }.installed(context)
        return list.first { it.app === app }
    }

    /** 서비스가 붙었다 */
    fun attach() = closeHeldViewers()

    /** 서비스가 끝난다 */
    fun detach() {
        clock.remove(settleLater)
        clock.remove(viewerLater)
        clock.remove(holderLater)
        closeHeldViewers()
    }

    /** 홈으로 돌아왔다(읽은 시간·책을 그리기 전): 데이터로 아는 앱을 맞춘다 */
    fun home() {
        for (e in entries.values.flatten()) guard(e) { e.adapter.onHome(e) }
    }

    /**
     * 표시로 읽은 시간을 자르는 앱의 열림·닫힘을 적는다(서비스가 붙을 때·끝날 때). 읽는 화면이 서재 안에 뜨는 앱(Libby·Readest)은 '닫힘':
     * 서비스가 없는 동안 '열림'이 남으면 그 뒤 서재만 본 시간도 읽은 시간으로 센다(읽는 화면이 떠 있으면 다음 알림에서 다시 '열림').
     * 책이 열린 동안을 데이터로 아는 앱(KOReader)은 '열림': 서비스가 없는 동안 열림·닫힘을 모르니 앞에 있던 동안을 다 센다(다시 읽으면 고친다).
     */
    private fun closeHeldViewers() {
        val now = clock.now()
        for (e in entries.values.flatten()) when (e.app.viewerTime) {
            ViewerTime.MARKS_CLOSED -> {
                e.screen = null
                recorder.viewerShown(e.pkg, shown = false, at = now)
            }
            ViewerTime.MARKS_OPEN -> recorder.viewerShown(e.pkg, shown = true, at = now)
            ViewerTime.SCREEN -> {}
        }
    }

    /**
     * 그 앱의 화면이 [name] 으로 바뀌었다(액티비티, 또는 서재 액티비티 안에 뜬 읽는 화면 `액티비티#요소`).
     * [byWindow]: 창 바뀜 알림으로 들어옴(내용 바뀜으로 안 것이 아님)
     */
    private fun enterScreen(e: Entry, name: String, byWindow: Boolean) {
        val now = clock.now()
        lastTransition = now
        val from = e.screen
        // 같은 화면의 알림이 한 번 더 온 것(새로 들어온 것이 아니다)
        val again = from == name
        e.screen = name
        if (ReadingLog.isReaderScreen(context, e.pkg, name)) e.viewerOpenedAt = now
        e.adapter.onScreen(e, name, from, again, byWindow)
    }

    /** 서재 안에 뜨는 읽는 화면을 아는 앱([ReaderApp.screenInsideActivity])이면 그 이름. 판단할 수 없으면 지금 화면 그대로(같은 액티비티일 때) */
    private fun withHolder(e: Entry, cls: String): String {
        if (!e.app.screenInsideActivity) return cls
        return e.adapter.screenName(e, cls) ?: e.screen?.takeIf { it.substringBefore('#') == cls } ?: cls
    }

    /**
     * 읽는 화면이 서재 액티비티 안에 뜨는 앱은 화면 바뀜 알림 없이 내용만 바뀌므로, 내용이 바뀔 때 읽는 화면이 떴는지·닫혔는지 본다.
     * 웹 화면은 알림이 잦아 [HOLDER_CHECK_MS] 에 한 번만 보고, 그 사이 알림은 마지막에 한 번 더 본다.
     */
    private fun checkHolder(e: Entry) {
        if (!e.app.screenInsideActivity) return
        val now = clock.now()
        clock.remove(holderLater)
        if (now - lastHolderCheck < HOLDER_CHECK_MS) {
            holderEntry = e
            clock.post(holderLater, HOLDER_CHECK_MS)
            return
        }
        lastHolderCheck = now
        val name = e.adapter.screenName(e, e.screen?.substringBefore('#')) ?: return
        if (name == e.screen) return
        logScreen(e, name, isScreen = true)
        enterScreen(e, name, byWindow = false)
    }

    /** 서재 화면이면 멈춘 뒤에 보이는 책들을 읽고, 읽는 화면이면 잠잠해진 뒤 진행률·제목을 읽는다. 그 밖의 화면은 읽지 않는다. */
    private fun readScreen(e: Entry, force: Boolean) {
        val name = e.screen ?: return
        when {
            name in e.app.shelves -> {
                e.adapter.onShelfLive(e, force)
                scheduleSettle(e)
            }
            e.app.isViewer(context, name) -> {
                viewerEntry = e
                viewerRetries = VIEWER_RETRIES
                clock.remove(viewerLater)
                clock.post(viewerLater, VIEWER_SETTLE_MS)
            }
        }
    }

    private fun scheduleSettle(e: Entry) {
        settleEntry = e
        clock.remove(settleLater)
        clock.post(settleLater, SETTLE_MS)
    }

    private fun logScreen(e: Entry, name: String, isScreen: Boolean) {
        WatchLog.add(context, if (isScreen) WatchLog.Kind.SCREEN else WatchLog.Kind.OTHER_WINDOW, e.pkg, name.substringAfterLast('.'),
            if (!isScreen) "" else when {
                name in e.app.shelves -> WatchLog.Role.SHELF.name
                e.app.isViewer(context, name) -> WatchLog.Role.VIEWER.name
                else -> WatchLog.Role.OTHER.name
            })
    }

    /** 앱 처리에서 난 예외는 그 알림 처리만 끝내고 적어 둔다(다음 알림은 그대로 받는다) */
    private inline fun guard(e: Entry, block: () -> Unit) {
        try {
            block()
        } catch (x: Exception) {
            WatchLog.add(context, WatchLog.Kind.ERROR, e.pkg, x.javaClass.simpleName, x.message.orEmpty())
        }
    }

    /**
     * 화면을 한 번 찍어 표지 자리마다 잘라 표지로 저장한다. 아직 목록에 없는 책이면 잘라 두었다가([BookRecorder.pendingCover]) 그 책이 들어올 때 붙인다.
     * 1초에 한 번만 찍을 수 있으므로 너무 이르면 조금 뒤에 다시 한다. 한 가지 색뿐인 칸(그림을 불러오기 전, 화면 캡처를 막은 창)은 버린다.
     */
    private fun capture(e: Entry, spots: List<CoverSpot>, retry: Boolean) {
        if (spots.isEmpty() || !host.canScreenshot || !e.adapter.capturesCovers(e)) return
        val now = clock.now()
        if (now - lastShot < SHOT_INTERVAL_MS) { if (retry) scheduleSettle(e); return }
        lastShot = now
        val wanted = spots.distinctBy { it.id ?: it.key ?: it.title }
        val app = e.app
        val appContext = context.applicationContext
        val screenAt = e.screen
        host.screenshot { shot ->
            // 찍는 사이 그 앱을 떠났거나 그 앱의 다른 화면(읽는 화면 등)으로 넘어갔으면 다른 화면이 찍혔을 수 있어 버린다. 다음에 서재를 볼 때 다시 찍는다
            if (host.root(e.pkg) == null || e.screen != screenAt) { shot.recycle(); return@screenshot }
            // 찍은 뒤 다시 읽기·기록도 앱 처리라 그 예외는 적고 넘어간다(화면 스레드에서 돈다)
            var still = emptyList<CoverSpot>()
            guard(e) { still = e.adapter.stillSpots(e, wanted) }
            if (still.isEmpty()) { shot.recycle(); return@screenshot }
            Bg.run({
                try {
                    still.mapNotNull { spot ->
                        // 화면 밖 자리(웹 화면·Compose 요소가 화면 밖 좌표를 주기도 한다)는 건너뛴다(그 화면의 다른 표지는 그대로)
                        val r = Rect(spot.rect)
                        if (!r.intersect(0, 0, shot.width, shot.height) || !usableThumb(r)) return@mapNotNull null
                        val raw = Bitmap.createBitmap(shot, r.left, r.top, r.width(), r.height())
                        // 이북 앱의 기본 그림(이지뷰어의 파일 종류 아이콘: 그림을 아직 만들지 않은 파일·받기 전의 원격 파일). 칸 바탕이 남은 그림으로 본다.
                        if (app.placeholderCover(appContext, raw)) return@mapNotNull null
                        val crop = CoverCrop.trimEdges(raw)
                        // 화면 캡처를 막은 창(읽는 화면 등)은 검게 찍혀 여기서 버려진다.
                        if (CoverCrop.isFlat(crop)) return@mapNotNull null
                        spot to ByteArrayOutputStream().also { crop.compress(Bitmap.CompressFormat.JPEG, 92, it) }.toByteArray()
                    }
                } finally {
                    shot.recycle()
                }
            }) { result ->
                guard(e) {
                    for ((spot, bytes) in result.getOrNull().orEmpty()) {
                        // 열쇠 자리(파일 칸)는 그 칸의 책(id), 목록에 없으면 그 열쇠로 잘라 둔다
                        val book = if (spot.key != null) spot.id?.let { recorder.lookup.get(it) } else recorder.lookup.byTitle(spot.title)
                        // 한 권이 실패해도(저장 공간 등) 나머지는 넣는다. 못 넣은 책은 다음에 서재를 볼 때 다시 한다.
                        if (book == null) recorder.pendingCover(spot.key ?: normTitle(spot.title), bytes) else recorder.cover(book.id, bytes)
                    }
                }
            }
        }
    }

    /** 앱 하나: 그 앱의 어댑터와 공통 기억, 어댑터에 주는 [AppContext] */
    private inner class Entry(override val app: ReaderApp) : AppContext {
        val adapter: ReaderAdapter = app.newAdapter()
        override var screen: String? = null
        override var viewerOpenedAt: Long? = null
        override var unknownOpenAt: Long? = null
        override var lastOpenedAt: Long? = null

        override val context: Context get() = this@WatchCore.context
        override val recorder: BookRecorder get() = this@WatchCore.recorder
        override val now: Long get() = clock.now()
        override val lastTransition: Long get() = this@WatchCore.lastTransition
        override val root: AccessibilityNodeInfo? get() = host.root(pkg)
        override fun windows() = host.windows(pkg)
        override val prefs: SharedPreferences get() = this@WatchCore.prefs
        override val canShoot: Boolean get() = clock.now() - lastShot >= SHOT_INTERVAL_MS

        override fun opened(seen: Seen, at: Long?, target: Target, coverKey: String?, steps: OpenSteps?): ShelfBook {
            unknownOpenAt = null
            lastOpenedAt = clock.now()
            adapter.beforeOpened(this)
            val book = recorder.opened(pkg, seen, at, target, coverKey, steps)
            adapter.afterOpened(this, book)
            return book
        }

        override fun unknown(at: Long) = recorder.unknown(pkg, at)
        override fun log(kind: WatchLog.Kind, arg: String, detail: String) = WatchLog.add(context, kind, pkg, arg, detail)

        override fun noWindow() {
            if (host.noActiveWindow) WatchLog.add(context, WatchLog.Kind.NO_WINDOW, pkg)
        }

        override fun capture(spots: List<CoverSpot>, retry: Boolean) = capture(this, spots, retry)

        override fun viewerAgain(ms: Long) {
            viewerEntry = this
            clock.remove(viewerLater)
            clock.post(viewerLater, ms)
        }

        override fun viewerRetry(): Boolean = viewerRetries-- > 0
        override fun <T> inBackground(work: () -> T, done: (Result<T>) -> Unit) = host.inBackground(work, done)
    }

    companion object {
        /** 서비스가 남기는 값(최근 읽은 책 줄·탭의 책·책 열쇠 등) */
        const val WATCH_PREFS = "reader_watch"
        /** 읽는 화면이 이만큼 잠잠하면 진행률·제목을 읽는다 */
        private const val VIEWER_SETTLE_MS = 700L
        private const val VIEWER_RETRIES = 3
        private const val SHOT_INTERVAL_MS = 1100L
        /** 서재 화면이 이만큼 그대로여야 표지를 찍는다 */
        private const val SETTLE_MS = 1500L
        /** 읽는 화면이 서재 액티비티 안에 뜨는 앱에서 그 화면이 떴는지 보는 간격 */
        private const val HOLDER_CHECK_MS = 500L
    }
}

/** 화면에서 자른 표지 다듬기 */
internal object CoverCrop {
    /** 거의 한 가지 색뿐인 그림(아직 그려지지 않은 표지 칸)인가. 16×16 점의 밝기 차로 본다. */
    fun isFlat(b: Bitmap): Boolean {
        var min = 255
        var max = 0
        for (y in 0 until 16) for (x in 0 until 16) {
            val l = luma(b.getPixel((x * 2 + 1) * b.width / 32, (y * 2 + 1) * b.height / 32))
            if (l < min) min = l
            if (l > max) max = l
        }
        return max - min < FLAT_RANGE
    }

    private const val FLAT_RANGE = 40

    /** 색 하나의 밝기(0~255) */
    private fun luma(c: Int) = (299 * (c shr 16 and 0xFF) + 587 * (c shr 8 and 0xFF) + 114 * (c and 0xFF)) / 1000

    /**
     * 표지 칸 가장자리의 테두리 선과 여백(밀리는 칸에 1px 선을 두르고 표지를 가운데 맞춰 양옆이 빈다)을 걷어 낸다.
     * 바깥 2px 안의 한 가지 색 줄(테두리 선)과 흰 줄(여백)만 지운다. 표지 바탕이 한 가지 색(빨간 표지 등)이어도
     * 깎지 않도록 그 밖의 줄은 두고, 한쪽에 [MAX_TRIM] 까지만.
     */
    fun trimEdges(b: Bitmap): Bitmap {
        /** [depth]: 이 줄이 가장자리에서 몇 번째인가 */
        fun trimmable(px: IntArray, depth: Int): Boolean {
            var min = 255
            var max = 0
            for (c in px) {
                val l = luma(c)
                if (l < min) min = l
                if (l > max) max = l
            }
            return max - min < FLAT_RANGE && (depth < FRAME_PX || min >= WHITE)
        }
        val w = b.width
        val h = b.height
        val maxX = (w * MAX_TRIM).toInt()
        val maxY = (h * MAX_TRIM).toInt()
        var left = 0
        var right = w
        var top = 0
        var bottom = h
        // 지금 남은 범위 안에서만 본다(테두리 선을 걷어야 그 안쪽 여백 줄이 한 가지 색이 된다).
        fun row(y: Int, depth: Int) =
            trimmable(IntArray(right - left).also { b.getPixels(it, 0, right - left, left, y, right - left, 1) }, depth)
        fun col(x: Int, depth: Int) =
            trimmable(IntArray(bottom - top).also { b.getPixels(it, 0, 1, x, top, 1, bottom - top) }, depth)
        do {
            var changed = false
            if (left < maxX && col(left, left)) { left++; changed = true }
            if (w - right < maxX && col(right - 1, w - right)) { right--; changed = true }
            if (top < maxY && row(top, top)) { top++; changed = true }
            if (h - bottom < maxY && row(bottom - 1, h - bottom)) { bottom--; changed = true }
        } while (changed)
        if (left == 0 && top == 0 && right == w && bottom == h) return b
        return Bitmap.createBitmap(b, left, top, right - left, bottom - top)
    }

    private const val MAX_TRIM = 0.08f
    /** 테두리 선으로 보는 가장자리 두께 */
    private const val FRAME_PX = 2
    /** 여백으로 보는 밝기 */
    private const val WHITE = 230
}
