package com.woody.booklauncher.readers

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.woody.booklauncher.Seen
import com.woody.booklauncher.ShelfBook
import com.woody.booklauncher.Storage
import com.woody.booklauncher.Target
import com.woody.booklauncher.WatchCore
import com.woody.booklauncher.WatchLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

/**
 * Readest(Bilingify, 오픈 소스 `com.bilingify.readest`, Tauri 앱): 액티비티가 `MainActivity` 하나이고 서재·읽는 화면이 모두 그 안의 웹 화면이다.
 * 웹 화면의 HTML id 가 요소 이름으로 나온다(앞에 패키지 없음). 읽는 화면에는 늘 닫기 단추 `titlebar-close` 와 책마다 `gridcell-<책 해시>-<번호>` 가
 * 있어 이것으로 읽는 화면을 가르고([showsViewer]) 어떤 책인지 안다([viewerKey], 해시는 Readest 가 책 파일로 매기는 고정 값).
 * 해시로 책을 알면 나머지는 Readest 데이터 폴더에서 읽는다([keyBook]·[keyCover], [ReadestData]): 기본 위치(앱 전용 저장소)는 읽을 수 없고,
 * 사용자가 Readest › 고급 설정 › `데이터 위치 변경` 으로 공유 저장소(`/sdcard/Readest` 등)로 옮기고 책 파일 읽기를 켜면 읽는다.
 * 못 읽으면 화면으로만:
 * - 서재(격자·목록 보기): 책 칸이 단추 하나이고 설명이 제목이다(시리즈로 묶으면 `제목 #2`). 저자·진행률은 단추에 묶여 나오지 않는다. 표지는 요소가 없어
 *   칸 자리로 셈해 자른다([coverOf]).
 * - 읽는 화면: 제목·저자는 목차 창(`NavigationView`)을 열었을 때만, 진행률은 `읽기 진행 상황` 창의 `3 / 81` 을 볼 때만 안다. 그 뒤로는 쪽을 넘길 때마다
 *   바뀌는 숨은 안내 글자(`3 페이지`)를 그 책의 전체 쪽으로 나눠 센다. 화면 아래 쪽 표시(`24 / 81`)는 `aria-hidden` 이라 나오지 않는다.
 * 홈에서 책을 누르면 Readest 위젯처럼 `readest://book/<해시>` 로 그 책을 바로 연다([open]). 판단과 기억은 [ReadestReader].
 * (Readest 0.12.16, 2026-10-22 에뮬레이터 `CremaPebble_A11_Play` WebView 155 에서 확인)
 */
object Readest : ReaderApp(
    pkg = "com.bilingify.readest", classPrefix = "com.bilingify.readest",
    shelves = setOf(MAIN),
    viewers = setOf("$MAIN#$HOLDER"),
    titles = emptyList(), thumbs = emptyList(), authors = emptyList(), progress = null, due = null,
    viewerProgress = emptyList(), viewerHolder = HOLDER,
    eagerCovers = true, openOnViewer = true, clickByPlace = false, needsBookFiles = true,
) {
    /** 목차 창을 열면 제목이 보인다 */
    override val tellsTitle get() = true

    /** 서재 칸의 표지 자리는 셈한 것이라 찍은 뒤에도 같은 자리에 있을 때만 자른다 */
    override val cellsByPosition get() = true

    override fun newAdapter(): ReaderAdapter = ReadestReader()

    /** 열쇠를 아는 책은 그 앱 링크로 바로 연다(BOOX 가 얼린 앱은 런처로 풀어야 해서 앱을 연다) */
    override fun open(ctx: HomeContext, book: ShelfBook): OpenResult =
        ReadestReader.bookKey(ctx.context, book.id)?.let(::keyIntent)?.takeIf { !ctx.frozen }?.let { OpenResult.Start(it) } ?: OpenResult.Launch

    /** 책장 줄(캐러셀)을 켜면 같은 책 칸이 위아래로 두 번 보여, 그 제목의 칸 가운데 하나라도 그 자리면 그대로다 */
    override fun coverStill(now: List<Cell>, title: String, rect: Rect): Boolean = now.any { it.title == title && it.thumb == rect }

    /** 읽는 화면의 요소(닫기 단추·책 칸)가 있으면 읽는 화면 */
    override fun showsViewer(root: AccessibilityNodeInfo): Boolean =
        find(root) { val id = it.viewIdResourceName; id == CLOSE || id?.startsWith(GRID) == true } != null

    /** 읽는 화면의 지금 책 열쇠(책 해시). 모르면 null */
    fun viewerKey(windows: AppWindows): String? = windows.all.firstNotNullOfOrNull { root ->
        find(root) { it.viewIdResourceName?.startsWith(GRID) == true }?.viewIdResourceName?.let { GRID_ID.find(it)?.groupValues?.get(1) }
    }

    /**
     * 읽는 화면: 목차 창의 제목·저자([sidebarTitle]), 진행률은 `읽기 진행 상황` 창의 `지금 쪽 / 전체 쪽`(그 책의 전체 쪽을 기억해 둔다),
     * 없으면 쪽 안내 글자 ÷ 기억해 둔 전체 쪽. 쪽 안내 글자는 언어마다 달라(`3 페이지`·`Page 3`) 앞뒤 쪽 단추 설명의 끝(`이전 페이지, 3 페이지`)과 같은 글자로 찾는다.
     * 본문(`foliate-view-…`)은 훑지 않는다(문단이 요소로 나온다).
     */
    override fun readViewer(windows: AppWindows): ViewerInfo {
        var title: String? = null
        var author = ""
        var pages: Pair<Int, Int>? = null
        var key: String? = null
        val numbers = mutableListOf<String>()
        val labels = mutableListOf<String>()
        for (root in windows.all) walk(root) { n ->
            val id = n.viewIdResourceName
            if (key == null && id?.startsWith(GRID) == true) key = GRID_ID.find(id)?.groupValues?.get(1)
            if (title == null && n.className?.endsWith("NavigationView") == true) sidebarTitle(n)?.let { (t, a) -> title = t; author = a }
            val text = n.text?.let(::plain).orEmpty()
            if (n.className?.endsWith("EditText") == true) PAGES.matchEntire(text)?.let { m -> pages = m.groupValues[1].toInt() to m.groupValues[2].toInt() }
            else if (ONE_NUMBER.matches(text)) numbers += text
            if (n.isClickable) n.contentDescription?.let(::plain)?.let { labels += it }
            true
        }
        val k = key
        val progress = pages?.takeIf { it.second > 0 }?.let { (cur, total) -> if (k != null) totals[k] = total; roundPercent(cur, total) }
            ?: numbers.firstOrNull { n -> labels.any { it.endsWith(", $n") } }?.let { n ->
                val total = k?.let { totals[it] } ?: return@let null
                ONE_NUMBER.find(n)?.groupValues?.get(1)?.toIntOrNull()?.let { roundPercent(it, total) }
            }
            ?: -1
        return ViewerInfo(title, author, progress)
    }

    /** 책마다(해시) `읽기 진행 상황` 창에서 본 전체 쪽. 글자 크기·여백을 바꾸면 달라져, 다음에 그 창을 볼 때까지는 조금 틀린다. */
    private val totals = HashMap<String, Int>()

    /** 목차 창 위의 책 머리: 표지 단추(글자 없는 세로로 긴 단추) 바로 뒤의 글자 둘이 제목·저자 */
    private fun sidebarTitle(nav: AccessibilityNodeInfo): Pair<String, String>? {
        val kids = (0 until nav.childCount).mapNotNull { nav.getChild(it) }
        val i = kids.indexOfFirst { it.isClickable && it.className?.endsWith("Button") == true && it.text.isNullOrEmpty() && it.bounds().let { r -> r.height() > r.width() } }
        if (i < 0) return null
        fun text(j: Int) = kids.getOrNull(j)?.takeIf { it.className?.endsWith("TextView") == true }?.text?.let(::plain).orEmpty()
        val title = text(i + 1).ifEmpty { return null }
        return title to text(i + 2)
    }

    /** 서재의 책 칸들(격자·목록 보기) */
    override fun shelfCells(root: AccessibilityNodeInfo): List<Cell> {
        val found = mutableListOf<Cell>()
        walk(root) { n -> bookButton(n)?.let { found += it; false } ?: true }
        return found
    }

    /**
     * 누른 책 칸: 누른 단추가 책 칸이면 그 칸, 아니면(설명 없이 온 알림) 누른 요소와 자리가 똑같은 칸. 자리 안에 드는 것으로는 보지 않는다
     * (서재 위에 뜬 설정 창의 단추를 누르면 그 뒤에 깔린 칸을 누른 것으로 보았다)
     */
    override fun clickedCell(source: AccessibilityNodeInfo?, text: List<CharSequence>, windows: AppWindows, last: List<Cell>?): Cell? {
        source?.let(::bookButton)?.let { return it }
        val at = source?.bounds()?.takeIf { !it.isEmpty } ?: return null
        val cells = windows.active?.let(::shelfCells)?.ifEmpty { null } ?: last
        return cells?.firstOrNull { it.cell == at }
    }

    /** 책 칸 단추: 글자 없이 설명(제목, 시리즈 번호 뗌)만 있는 큰 단추. 묶음(그룹) 칸도 같은 모양이라 묶음 이름이 제목으로 온다(펴는 책이 아니라 쓰이지 않는다). */
    private fun bookButton(n: AccessibilityNodeInfo): Cell? {
        if (!n.isClickable || n.className?.endsWith("Button") != true || !n.text.isNullOrEmpty()) return null
        val title = n.contentDescription?.let(::plain)?.replace(SERIES, "")?.trim()?.ifEmpty { null } ?: return null
        val r = n.bounds()
        if (r.width() < MIN_CELL_PX || r.height() < MIN_CELL_PX) return null
        return Cell(title, "", -1, -1, coverOf(r), r)
    }

    /**
     * 칸 [r] 안의 표지 자리(표지는 28:41). 격자 보기는 칸 폭 그대로 위에 놓이고 위에 작은 여백이 있어, 위쪽 끝부터 여백만큼 더 잡는다
     * (위의 바탕 줄은 자를 때 걷힌다). 목록 보기는 칸 왼쪽에 위아래 여백을 두고 세로 가운데.
     */
    internal fun coverOf(r: Rect): Rect {
        if (r.width() > r.height()) {
            val pad = r.height() * LIST_PAD_PCT / 100
            val h = r.height() - 2 * pad
            return Rect(r.left, r.top + pad, r.left + h * 28 / 41, r.bottom - pad)
        }
        return Rect(r.left, r.top, r.right, minOf(r.bottom, r.top + r.width() * 41 / 28 + r.width() * GRID_GAP_PCT / 100))
    }

    /** 열쇠 [key] 의 책을 데이터 폴더에서(제목·저자·진행률). 읽을 수 없거나 모르면 null */
    fun keyBook(context: Context, key: String): FileBook? = data(context)?.book(key)

    /** 열쇠 [key] 의 책 표지(JPEG). 모르면 null. 그림을 다루므로 화면 스레드에서 부르지 않는다 */
    fun keyCover(context: Context, key: String): ByteArray? = data(context)?.cover(key)?.let(BookFiles::imageCover)

    /** 홈에서 열쇠 [key] 의 책을 바로 여는 인텐트 */
    fun keyIntent(key: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("readest://book/$key")).setClassName(pkg, MAIN)

    /** 데이터 폴더에서 표지를 가져올 수 있다: 서재 화면에서 표지를 자르지 않는다(데이터에 표지가 없는 책은 자른다, [hasDataCover]) */
    fun dataCovers(context: Context): Boolean = data(context) != null

    /** 열쇠 [key] 의 책 표지가 데이터 폴더에 있다 */
    fun hasDataCover(context: Context, key: String): Boolean = data(context)?.cover(key) != null

    /** 데이터 폴더를 읽을 수 있으면 그것(책 파일 읽기를 허용했고 데이터를 공유 저장소로 옮겼을 때) */
    private fun data(context: Context): ReadestData? {
        if (!BookFiles.canRead(context)) return null
        val books = booksDir(context) ?: return null
        return cached?.takeIf { it.dir == books } ?: ReadestData(books).also { cached = it }
    }

    @Volatile private var cached: ReadestData? = null
    @Volatile private var foundDir: File? = null
    @Volatile private var lookedAt = 0L
    @Volatile private var searchedAt = 0L

    /**
     * Readest 데이터 폴더의 `Books`(안에 `library.json`): 데이터 위치 변경 화면이 고르게 하는 자리(저장소·SD 카드의 맨 위·`Books`·`Documents`·`Download`
     * 아래 `Readest`), 없으면 저장소를 세 단계까지 훑어 `Readest/Books/library.json` 이 있는 폴더(직접 고른 폴더. 화면 스레드를 막지 않게 뒤에서 훑고,
     * 찾으면 다음에 부를 때부터 쓴다). 찾은 것은 기억한다. 못 찾으면 알려진 자리는 [LOOK_MS], 저장소 훑기는 [SEARCH_MS] 동안 다시 하지 않는다
     * (데이터를 옮기지 않은 채 읽는 동안 쪽을 넘길 때마다 불린다).
     */
    private fun booksDir(context: Context): File? {
        foundDir?.takeIf { File(it, LIBRARY).isFile }?.let { return it }
        val now = SystemClock.elapsedRealtime()
        if (lookedAt != 0L && now - lookedAt < LOOK_MS) return null
        lookedAt = now
        val roots = (listOf(BookFiles.storageRoot) + context.getExternalFilesDirs(null).filterNotNull()
            .mapNotNull { f -> f.path.substringBefore("/Android/", "").ifEmpty { null }?.let(::File) }).distinct()
        val known = roots.flatMap { r -> listOf("", "Books/", "Documents/", "Download/").map { File(r, "${it}$DATA/Books") } }
        known.firstOrNull { File(it, LIBRARY).isFile }?.let { foundDir = it; return it }
        if (searchedAt == 0L || now - searchedAt >= SEARCH_MS) {
            searchedAt = now
            thread(isDaemon = true, name = "readest-data") { roots.firstNotNullOfOrNull { search(it, 0) }?.let { foundDir = it } }
        }
        return null
    }

    private fun search(dir: File, depth: Int): File? {
        for (f in dir.listFiles().orEmpty()) {
            if (!f.isDirectory || f.name.startsWith('.') || f.name == "Android") continue
            if (f.name == DATA && File(f, "Books/$LIBRARY").isFile) return File(f, "Books")
            if (depth < SEARCH_DEPTH) search(f, depth + 1)?.let { return it }
        }
        return null
    }

    /** [root] 아래를 훑는다([visit] 이 false 면 그 아래로 들어가지 않는다). 본문(`foliate-view-…`) 안은 들어가지 않는다. */
    private fun walk(root: AccessibilityNodeInfo, visit: (AccessibilityNodeInfo) -> Boolean) {
        fun go(n: AccessibilityNodeInfo, depth: Int) {
            if (n.viewIdResourceName?.startsWith(FOLIATE) == true) return
            if (!visit(n)) return
            if (depth < DEEP_WALK_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { go(it, depth + 1) }
        }
        go(root, 0)
    }

    /** [root] 아래에서 [match] 인 첫 요소 */
    private fun find(root: AccessibilityNodeInfo, match: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        var found: AccessibilityNodeInfo? = null
        walk(root) { n -> if (found == null && match(n)) found = n; found == null }
        return found
    }

    /** 시리즈로 묶어 볼 때 칸 설명 끝의 권 번호(`제목 #2`, `#1.5`) */
    private val SERIES = Regex("""\s+#\d+(\.\d+)?$""")
    /** 진행 창의 쪽 입력 칸 `3 / 81` */
    private val PAGES = Regex("""^\s*(\d+)\s*/\s*(\d+)\s*$""")
    /** 숫자 하나뿐인 글자(쪽 안내 `3 페이지`·`Page 3`) */
    private val ONE_NUMBER = Regex("""^\D*(\d+)\D*$""")
    private val GRID_ID = Regex("""^gridcell-([0-9a-f]{32})-""")

    /** 이보다 작은 단추는 책 칸이 아니다(선택 모드의 `전체선택` 등) */
    private const val MIN_CELL_PX = 100
    /** 목록 보기 칸의 위아래 여백(칸 높이의 %) */
    private const val LIST_PAD_PCT = 7
    /** 격자 보기 표지 위의 여백(칸 폭의 %) */
    private const val GRID_GAP_PCT = 5
    private const val SEARCH_DEPTH = 2
    private const val LOOK_MS = 60_000L
    private const val SEARCH_MS = 30 * 60_000L
}

/**
 * Readest 의 판단과 기억: 읽는 화면에 늘 보이는 책 열쇠(해시)로 책을 가른다. 한 번 안 열쇠는 그 책으로 적어 두어(서비스가 다시 시작돼도)
 * 서재를 거치지 않고 연 책(Readest '시작 시 마지막 책 열기'·파일 관리자·홈에서 바로 열기)도 알아본다. 서재에서 누른 칸은 읽는 화면에 들어올 때
 * 기록하지 않고 처음 보는 열쇠의 제목으로 쓴다. 데이터 폴더를 읽을 수 있으면 진행률·저자·표지는 데이터에서(서재에서는 데이터에 표지가 없는 책만 자른다).
 */
class ReadestReader : ScreenReader(Readest) {
    /** 제목을 아직 몰라 책 모름으로 둔 열쇠 */
    private var unknownKey: String? = null
    /** 이 읽는 화면(연 때)에서 처음 본 열쇠와 그 뒤 열쇠가 바뀐 때: 같은 읽는 화면 안에서 다른 책으로 넘어가면 그때부터 그 책 */
    private var screenKey: Triple<Long?, String, Long>? = null
    /** 데이터에서 표지를 가져오는 중이거나 가져온 열쇠([keyCover]) */
    private val keyCovers = mutableSetOf<String>()

    /** 누른 칸은 읽는 화면의 열쇠를 볼 때 쓴다([readKeyed]. 열쇠로 이미 아는 책이면 그 책) */
    override fun onEnterViewer(ctx: AppContext, from: String?, again: Boolean) {}

    override fun onViewer(ctx: AppContext) {
        val windows = ctx.windows().takeIf { it.all.isNotEmpty() } ?: return ctx.noWindow()
        val shown = app.readViewer(windows)
        Readest.viewerKey(windows)?.let { return readKeyed(ctx, it, shown) }
        // 웹 화면이 안쪽 요소를 늦게 내놓는다: 조금 뒤 다시 본다
        if (ctx.viewerRetry()) ctx.viewerAgain(VIEWER_RETRY_MS)
    }

    /** 홈으로 돌아올 때 마지막 책을 데이터로 맞춘다(읽는 화면을 닫을 때 적힌 마지막 진행률) */
    override fun onHome(ctx: AppContext) {
        val key = ctx.prefs.getString(lastKeyPref(), null) ?: return
        val data = Readest.keyBook(ctx.context, key) ?: return
        val book = keyedBook(ctx, key) ?: return
        if (data.progress >= 0 || data.author.isNotEmpty()) ctx.recorder.status(book.id, data.progress, -1, data.author)
        if (!ctx.recorder.lookup.hasCover(book.id)) keyCover(ctx, key, book.id)
    }

    override fun beforeOpened(ctx: AppContext) {
        super.beforeOpened(ctx)
        unknownKey = null
    }

    /** 서재 칸 표지는 데이터에 표지가 없는 책만 찍는다([cropSpots]) */
    override fun onShelfLive(ctx: AppContext, force: Boolean) = super.onShelfLive(cropOnly(ctx), force)

    override fun onShelf(ctx: AppContext) = super.onShelf(cropOnly(ctx))

    /** 찍기 전에 [cropSpots] 로 거르는 문맥(찍고 버리지 않게) */
    private fun cropOnly(ctx: AppContext): AppContext = object : AppContext by ctx {
        override fun capture(spots: List<CoverSpot>, retry: Boolean) = ctx.capture(cropSpots(ctx, spots), retry)
    }

    /** 찍은 뒤 다시 보기도 같은 규칙으로 */
    override fun stillSpots(ctx: AppContext, spots: List<CoverSpot>): List<CoverSpot> = cropSpots(ctx, super.stillSpots(ctx, spots))

    /**
     * 데이터 폴더를 읽을 수 있으면 표지는 데이터에서 가져오므로, 데이터에 표지(`cover.png`)가 없는 책의 자리만 자른다
     * (열쇠를 모르는 책·목록에 없는 책은 데이터에서 가져올 수 있으므로 자르지 않는다)
     */
    private fun cropSpots(ctx: AppContext, spots: List<CoverSpot>): List<CoverSpot> {
        if (spots.isEmpty() || !Readest.dataCovers(ctx.context)) return spots
        return spots.filter { spot ->
            val book = spot.id?.let { ctx.recorder.lookup.get(it) } ?: ctx.recorder.lookup.byTitle(spot.title) ?: return@filter false
            val key = ReadestReader.bookKey(ctx.context, book.id) ?: return@filter false
            !Readest.hasDataCover(ctx.context, key)
        }
    }

    /**
     * 열쇠 [key] 의 책: 적어 둔 책이면 그 책(제목을 고쳐도), 처음 보는 열쇠는 데이터의 제목, 없으면 이 읽는 화면을 열기 전에 서재에서 누른 칸,
     * 없으면 읽는 화면의 제목(목차 창). 그때까지는 책 모름(앞서 편 책에 붙이지 않는다). 진행률은 데이터, 없으면 화면에서 읽은 것.
     */
    private fun readKeyed(ctx: AppContext, key: String, shown: ViewerInfo) {
        val recorder = ctx.recorder
        // 읽는 화면이 열린 때. 같은 읽는 화면 안에서 열쇠가 바뀌었으면(서재를 못 보고 다른 책을 엶) 바뀐 때
        val opened = screenKey?.takeIf { it.first == ctx.viewerOpenedAt && it.second == key }?.third
            ?: (if (screenKey?.first == ctx.viewerOpenedAt && ctx.viewerOpenedAt != null) ctx.now else ctx.viewerOpenedAt ?: ctx.now)
                .also { screenKey = Triple(ctx.viewerOpenedAt, key, it) }
        val data = Readest.keyBook(ctx.context, key)
        val progress = data?.progress?.takeIf { it >= 0 } ?: shown.progress
        ctx.log(WatchLog.Kind.PROGRESS, "$progress", data?.title ?: shown.title.orEmpty())
        if (ctx.prefs.getString(lastKeyPref(), null) != key) ctx.prefs.edit().putString(lastKeyPref(), key).apply()
        // 해시로 이어 둔 책은 제목이 달라도 그 책(Readest 에서 메타데이터 편집으로 제목을 바꿔도 같은 책)
        val book = keyedBook(ctx, key)?.also { b ->
            clicked = null
            if (recorder.recordedBook(ctx.pkg)?.id != b.id) ctx.opened(Seen(b.title), opened, Target.Exact(b.id))
        } ?: newKeyed(ctx, key, data, shown, progress, opened)
        if (book != null) {
            if (progress >= 0 || !data?.author.isNullOrEmpty()) recorder.status(book.id, progress, -1, data?.author.orEmpty())
            if (!recorder.lookup.hasCover(book.id)) keyCover(ctx, key, book.id)
        }
        // 진행률은 늦게 채워질 수 있다(웹 화면이 안쪽 요소를 늦게 내놓는다)
        if (progress < 0 && ctx.viewerRetry()) ctx.viewerAgain(VIEWER_RETRY_MS)
    }

    /**
     * 처음 보는(또는 그 책을 빼고 다시 연) 열쇠 [key] 의 책을 제목으로 넣고, 편 책 기록의 책을 다시 읽어 열쇠 ↔ 책으로 적는다
     * (편 책 저장이 조용히 실패했으면 앞서 편 책이나 null). 제목을 아직 모르면 책 모름으로 두고 null
     */
    private fun newKeyed(ctx: AppContext, key: String, data: FileBook?, shown: ViewerInfo, progress: Int, opened: Long): ShelfBook? {
        val click = clicked?.takeIf { ctx.now - it.second < CLICK_OPEN_MS }?.first
        val title = data?.title?.ifEmpty { null } ?: click?.title ?: shown.title
        if (title == null) {
            // 책 모름: 그 앞에 편 책에 이 시간을 붙이지 않게(같은 열쇠에 한 번만)
            if ((unknownKey != key).also { unknownKey = key } && ctx.recorder.openedId(ctx.pkg) != null) ctx.unknown(opened)
            return null
        }
        val author = data?.author?.ifEmpty { null } ?: shown.author.takeIf { shown.title == title }.orEmpty()
        val lookup = ctx.recorder.lookup
        // 이 열쇠로 적어 둔 책이 나간 책이면 그 책만 되살린다(같은 제목의 다른 나간 책이 아니라. 기록에서 지운 책은 다시 알아본다)
        val own = ctx.prefs.getString(keyPref(key), null)?.takeIf { lookup.withoutFile(title) == it }
        // 제목이 같은 책(나간 책도)이 이미 다른 열쇠로 이어져 있으면(`X: 1권`·`X: 2권` 은 정리한 제목이 같다) 다른 책이다
        val sameTitle = if (own == null) lookup.byTitle(title)?.id ?: lookup.withoutFile(title) else null
        val otherKey = sameTitle?.let { bookKey(ctx.context, it) }?.takeIf { it != key }
        val target = when {
            own != null -> Target.Exact(own)
            otherKey != null -> Target.Exact(null)
            else -> Target.ByTitle
        }
        val book = ctx.opened(Seen(title, author, progress), opened, target)
        // 편 책 기록에 그 책이 적혔을 때만 열쇠 ↔ 책(저장이 조용히 실패했으면 다음에 다시)
        return book.takeIf { ctx.recorder.recordedBook(ctx.pkg)?.id == it.id }?.also { setKeyBook(ctx, key, it.id) }
    }

    /** 열쇠 [key] 로 적어 둔 책(읽고 있는 책일 때만. 빼거나 지운 책은 다시 알아본다) */
    private fun keyedBook(ctx: AppContext, key: String): ShelfBook? =
        ctx.prefs.getString(keyPref(key), null)?.let { ctx.recorder.lookup.get(it) }

    private fun setKeyBook(ctx: AppContext, key: String, id: String) {
        ctx.prefs.edit().putString(keyPref(key), id).putString(bookKeyPref(id), key).apply()
    }

    /** 책 [id] 에 열쇠 [key] 의 표지를 데이터에서 붙인다(뒤에서. 가져오는 중이면 다시 하지 않고, 못 가져오면 다음에 다시) */
    private fun keyCover(ctx: AppContext, key: String, id: String) {
        if (!keyCovers.add(key)) return
        ctx.inBackground({ Readest.keyCover(ctx.context, key) }) done@{ result ->
            val bytes = result.getOrNull()
            if (bytes == null) { keyCovers -= key; return@done }
            ctx.recorder.cover(id, bytes)
        }
    }

    companion object {
        private val PKG = Readest.pkg
        private fun lastKeyPref() = "last_key_$PKG"
        /** 열쇠 → 책 id, 책 id → 열쇠 */
        private fun keyPref(key: String) = "key_${PKG}_$key"
        private fun bookKeyPref(id: String) = "book_key_${PKG}_$id"

        /** 책 [id] 를 연 열쇠(홈에서 그 책을 바로 연다). 모르면 null */
        fun bookKey(context: Context, id: String): String? =
            Storage.of(context).getSharedPreferences(WatchCore.WATCH_PREFS, Context.MODE_PRIVATE).getString(bookKeyPref(id), null)
    }
}

/** 지금 쪽 ÷ 전체 쪽을 반올림한 %(Readest 서재·진행 창과 같게: 24/81 → 30%) */
internal fun roundPercent(cur: Int, total: Int): Int =
    if (total <= 0) -1 else ((cur * 200L + total) / (2L * total)).toInt().coerceIn(0, 100)

/**
 * Readest 데이터 폴더의 `Books` [dir]: `library.json`(책마다 해시·제목·저자·진행률 `[지금 쪽, 전체 쪽]`·지운 때, 책을 닫을 때 쓴다)과
 * 책마다 `<해시>/config.json`(그 책의 진행률, 쪽을 넘길 때마다 쓴다)·`cover.png`·책 파일. 같은 파일은 바뀌었을 때만 다시 읽는다.
 */
internal class ReadestData(val dir: File) {
    /** 해시 [key] 의 책(제목·저자·진행률. 책 파일 경로는 쓰지 않아 비운다). 지운 책·없는 책은 null */
    fun book(key: String): FileBook? {
        val entry = library()[key] ?: return null
        val progress = configProgress(key).takeIf { it >= 0 } ?: progressOf(entry.optJSONArray("progress"))
        return FileBook(entry.optString("title").trim(), entry.optString("author").trim(), "", progress)
    }

    /** 해시 [key] 의 표지 그림(없으면 null) */
    fun cover(key: String): File? = File(File(dir, key), COVER).takeIf { it.isFile }

    /** 마지막으로 읽은 library.json: 고친 때·길이 → 책들 */
    private var read: Pair<Pair<Long, Long>, Map<String, JSONObject>>? = null
    /** 마지막으로 읽은 config.json: 해시·고친 때·길이 → 진행률(쪽을 넘길 때마다 묻는다) */
    private var config: Pair<Triple<String, Long, Long>, Int>? = null

    /**
     * 해시 [key] 의 config.json 진행률(바뀌었을 때만 다시 읽는다). 없으면 -1. 고친 때는 거칠 수 있어(SD 카드 2초) 막 고친 파일은 늘 다시 읽고,
     * 쓰는 도중이라 못 읽은 것은 기억하지 않는다
     */
    @Synchronized private fun configProgress(key: String): Int {
        val file = File(File(dir, key), CONFIG)
        val modified = file.lastModified()
        val stamp = Triple(key, modified, file.length())
        val settled = modified != 0L && System.currentTimeMillis() - modified >= FRESH_MS
        config?.takeIf { settled && it.first == stamp }?.let { return it.second }
        val json = runCatching { JSONObject(file.readText()) }.getOrNull()
        val progress = progressOf(json?.optJSONArray("progress"))
        config = if (json != null) stamp to progress else null
        return progress
    }

    /** library.json 의 책들(해시 → 줄, 지운 책은 뺀다). 못 읽으면 빈 목록 */
    @Synchronized private fun library(): Map<String, JSONObject> {
        val file = File(dir, LIBRARY)
        val modified = file.lastModified()
        val stamp = modified to file.length()
        val settled = modified != 0L && System.currentTimeMillis() - modified >= FRESH_MS
        read?.takeIf { settled && it.first == stamp }?.let { return it.second }
        val list = runCatching { JSONArray(file.readText()) }.getOrNull()
        val books = list?.let {
            (0 until it.length()).mapNotNull(it::optJSONObject)
                .filter { o -> o.isNull("deletedAt") && o.optString("hash").isNotEmpty() }
                .associateBy { o -> o.optString("hash") }
        }.orEmpty()
        // 쓰는 도중이라 못 읽은 것은 기억하지 않는다
        read = if (list != null) stamp to books else null
        return books
    }

    /** `[지금 쪽, 전체 쪽]` → %. 없으면 -1 */
    private fun progressOf(a: JSONArray?): Int {
        if (a == null || a.length() < 2) return -1
        return roundPercent(a.optInt(0, -1).takeIf { it >= 0 } ?: return -1, a.optInt(1, 0))
    }
}

private const val MAIN = "com.bilingify.readest.MainActivity"
/** 읽는 화면의 이름(`MainActivity#reader`). 요소 이름이 아니라 [Readest.showsViewer] 로 가른다 */
private const val HOLDER = "reader"
private const val CLOSE = "titlebar-close"
private const val GRID = "gridcell-"
private const val FOLIATE = "foliate-view-"
private const val DATA = "Readest"
private const val LIBRARY = "library.json"
private const val CONFIG = "config.json"
private const val COVER = "cover.png"
/** 이만큼 안에 고친 config.json 은 고친 때가 같아도 다시 읽는다(고친 때가 2초 단위인 저장소) */
private const val FRESH_MS = 3000L
