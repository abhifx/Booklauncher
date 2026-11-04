package com.woody.pebbledesk.readers

import android.content.Context
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.woody.pebbledesk.HomePrefs
import com.woody.pebbledesk.TitlePair
import com.woody.pebbledesk.WatchLog
import com.woody.pebbledesk.hasTitle
import com.woody.pebbledesk.normTitle
import com.woody.pebbledesk.sameTitle

/**
 * 부커스 2.0.0(2026-10-24 Play, versionCode 205~): 1.x([Bookers])와 패키지만 같은 새 앱이라 따로 둔다([handles] 로 깔린 판을 고른다).
 * 액티비티가 `com.bookers.app.MainActivity` 하나이고, 홈·내 서재·책 상세는 웹 화면(요소 이름 없이 글자·설명만. 단추는 안의 글자를 한 줄로 합쳐 내놓는다),
 * 읽는 화면은 그 위에 겹쳐 뜨는 Compose 화면(요소 이름 없음, 화면 캡처를 막는다)이다. 그래서 읽는 화면은 웹 화면과 함께 그 밖에 글자가 보이는 동안이다
 * ([showsViewer]. 앱이 따로 띄운 창에는 웹 화면이 없어 읽는 화면으로 보지 않는다).
 * 책을 열고 닫아도 화면 바뀜 알림은 없고 내용 바뀜만 온다(Libby 와 같다).
 * 내 서재(`다운로드` 탭)의 칸: 격자 보기는 칸 단추 하나에 `표지 이름 D-14 0% 제목 저자 읽는 사람 수`, 리스트 보기는 표지 단추(`D-14 0%`) 옆에
 * 제목·저자·출판사 글자(반납 문구 `D-14` 는 불러온 뒤에 붙는다). 받지 않은 책은 그 사이에 `다운로드`, `읽고 싶은 책` 탭은 진행률·반납 글자가 없다
 * (격자 `표지 이름 [다운로드] 제목 저자 수`, 리스트 표지 단추가 `다운로드`·빈 글자). 홈의 책 카드도 `제목 제목 저자` 라 진행률 없는 칸은 내 서재 화면에서만 칸으로 본다. 서재의 표지는 자르지 않는다: 대출한 책은 표지 위에 반납 딱지(`⏰ D-14`)가 그려지고
 * 딱지와 글자가 같이 붙는다는 보장이 없다(격자·리스트 모두, 칸 단추 위쪽 폭의 8% 아래부터 폭의 1.47 배 높이가 표지). 표지는 책 상세에서 자른다.
 * 서재의 진행률은 읽는 화면의 쪽보다 늦게 바뀌어(343쪽 가운데 12쪽에서 `1%`, 몇 분 뒤 `3%`) 쓰지 않는다.
 * 책 상세(`바로읽기`)는 표지 그림(설명이 제목, 딱지 없음)과 제목·저자 글자가 따로 있다.
 * 읽는 화면: 아래 가운데 쪽 `7/343` 이 늘 보이고, 가운데를 눌러 메뉴를 띄우면(책을 열면 처음에 떠 있다) 위 막대의 `닫기` 옆에 제목이 보인다
 * (같은 막대에 `검색`·`책갈피` 가 있는 것만 위 막대로 본다). 처음 연 책은 첫 실행 안내(`화면 중앙을 터치하시면…`)가 메뉴를 가려, 닫을 때까지 제목이 없다.
 * 서재·상세의 제목은 도서관에 등록된 상품 이름(`위버멘쉬 (30만부 기념 개정판)`·`(접근성)직장 노예`·`[미리보기] …`)이고, 메뉴의 제목은 EPUB 파일의
 * 제목(`reader.html` 이 `book.loaded.metadata.title` 을 넘긴다, `위버멘쉬`·`직장 노예`)이라 서로 다를 수 있다(2.0.1 에서 확인. 제목 말고 밖에서 읽을 수 있는
 * 책 식별자는 없다). 그래서 책은 메뉴의 제목으로만 넣는다(서재 칸·`바로읽기` 를 눌러 열어도 그 칸의 제목으로 넣지 않는다, [Bookers2Reader]).
 * 서재의 반납일·저자와 상세의 표지는 제목이 같은 책에만 붙어, 연 칸의 제목이 메뉴 제목과 다르면 홈에서 `같은 책인가요?` 로 묻고([HomePrefs.sameBookAsk])
 * `같은 책` 이면 그 서재 제목의 칸·상세를 메뉴 제목의 책으로 읽는다([useAnswers]). 서재 제목이 메뉴 제목에 `(접근성)` 만 붙인 것이면 묻지 않고 같은 책으로.
 * 1.x·예전 판이 `(접근성)` 을 붙여 넣은 책은 메뉴 제목으로 그 책을 찾는다([listedTitle]).
 */
object Bookers2 : ReaderApp(
    pkg = "com.bookers.ebook", classPrefix = "com.bookers",
    shelves = setOf(MAIN),
    viewers = setOf("$MAIN#$VIEWER"),
    titles = emptyList(), thumbs = emptyList(), authors = emptyList(), progress = null, due = null,
    viewerProgress = emptyList(), viewerHolder = VIEWER,
    eagerCovers = true, openOnViewer = true,
) {
    override fun newAdapter(): ReaderAdapter = Bookers2Reader()

    /** 깔린 판(versionCode)과 확인한 때(부팅 뒤 ms). 알림마다 묻으므로 [RECHECK_MS] 동안은 다시 보지 않는다 */
    @Volatile private var version: Pair<Long, Long>? = null

    /** 2.0.0 부터 맡는다(그 전 판은 [Bookers]) */
    override fun handles(context: Context): Boolean {
        val now = SystemClock.elapsedRealtime()
        val code = version?.takeIf { now - it.second < RECHECK_MS }?.first
            ?: (runCatching { context.packageManager.getPackageInfo(pkg, 0).longVersionCode }.getOrDefault(-1L)).also { version = it to now }
        return code >= FIRST_VERSION
    }

    /** 시험에서 판을 바꿔 깔 때 기억한 판을 버린다 */
    internal fun forgetVersion() { version = null }

    /** 책을 열면 메뉴가 떠 있어 제목을 안다 */
    override val tellsTitle get() = true

    /** 표지(책 상세의 그림)는 요소 이름 없이 찾은 자리라, 찍은 뒤에도 같은 자리에 있을 때만 자른다 */
    override val cellsByPosition get() = true

    override fun showsViewer(root: AccessibilityNodeInfo): Boolean = hasWebView(root) && overlay(root).isNotEmpty()

    /**
     * `같은 책` 이라고 답한 짝(정리한 서재 제목 → 읽는 화면 제목, [HomePrefs.sameBooks]). 어댑터가 서재를 읽기 전에 채운다([useAnswers]).
     * 서재·상세의 칸 제목을 읽는 화면 제목으로 바꿔, 메뉴 제목으로 넣은 책에 반납일·저자·표지가 붙게 한다
     */
    @Volatile private var answers: Map<String, String> = emptyMap()

    internal fun useAnswers(pairs: List<TitlePair>) { answers = pairs.associate { normTitle(it.shelf) to it.read } }

    /** 답한 짝이면 칸 제목을 읽는 화면 제목으로 */
    private fun named(cell: Cell): Cell = answers[normTitle(cell.title)]?.let { cell.copy(title = it) } ?: cell

    /** 내 서재의 칸(격자·리스트)과 책 상세의 책 */
    override fun shelfCells(root: AccessibilityNodeInfo): List<Cell> = (myShelf(root) + listOfNotNull(detail(root))).map(::named)

    /** 내 서재의 칸(격자·리스트) */
    private fun myShelf(root: AccessibilityNodeInfo): List<Cell> {
        val found = mutableListOf<Cell>()
        val mine = lazy { isMyShelf(root) }
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            (gridCell(n, mine) ?: listCell(n, mine))?.let { found += it; return }
            if (depth < DEEP_WALK_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        return found
    }

    /**
     * 누른 칸: 격자 칸 단추는 그 글자로, 리스트 보기의 표지 단추는 지금 화면의 서재 칸 가운데 그 자리의 칸(없으면 부모 묶음), 책 상세의 `바로읽기` 는 그 상세의 책.
     * 그 밖(리스트 줄의 `더보기`·`독서 감상문 쓰기`, 제목 글자 — 상세가 뜬다)은 책을 여는 것이 아니다.
     * 누른 칸으로 책을 넣지는 않고, 그 칸의 제목이 메뉴 제목과 다르면 같은 책인지 묻는다([Bookers2Reader]).
     */
    override fun clickedCell(source: AccessibilityNodeInfo?, text: List<CharSequence>, windows: AppWindows, last: List<Cell>?): Cell? =
        clicked(source, windows)?.let(::named)

    private fun clicked(source: AccessibilityNodeInfo?, windows: AppWindows): Cell? {
        val s = source ?: return null
        val mine = lazy { windows.active?.let(::isMyShelf) == true }
        gridCell(s, mine)?.let { return it }
        val label = s.text?.let(::plain).orEmpty()
        if (isCoverButton(s)) {
            val r = s.bounds()
            // 누른 요소는 위(부모)로 올라가지 못하기도 해 지금 화면의 서재 칸에서 그 단추 자리를 품은 칸을 찾는다(위 탭의 `다운로드` 는 어느 칸 안에도 없다)
            return windows.active?.let(::myShelf)?.firstOrNull { it.cell.contains(r) } ?: s.parent?.let { listCell(it, mine) }
        }
        if (label == READ_NOW) return windows.active?.let(::detail)
        return null
    }

    /**
     * 내 서재 화면(위 제목 글자 `내 서재`. 아래 탭의 `내 서재` 는 단추라 아니다)인가. 홈의 책 카드는 `제목 제목 저자` 로 서재 칸과 비슷해,
     * 진행률 글자 없는 칸은 여기서만 칸으로 본다
     */
    private fun isMyShelf(root: AccessibilityNodeInfo): Boolean {
        fun walk(n: AccessibilityNodeInfo, depth: Int): Boolean =
            (n.childCount == 0 && !n.isClickable && n.className?.endsWith("TextView") == true && n.text?.let(::plain) == MY_SHELF) ||
                (depth < OVERLAY_DEPTH && (0 until n.childCount).any { i -> n.getChild(i)?.let { walk(it, depth + 1) } == true })
        return walk(root, 0)
    }

    /**
     * 리스트 보기의 표지 단추: 글자가 `[반납] 0%`·`다운로드`(받지 않은 책)이거나 없는(읽고 싶은 책 탭에서 받은 책) 단추
     * (진행률 글자 없는 것은 내 서재 화면에서만 칸이 된다, [listCell])
     */
    private fun isCoverButton(n: AccessibilityNodeInfo): Boolean {
        if (n.className?.endsWith("Button") != true || !n.contentDescription.isNullOrEmpty()) return false
        val label = n.text?.let(::plain).orEmpty()
        return label.isEmpty() || label == DOWNLOAD || COVER_BUTTON.matches(label)
    }

    /**
     * 메뉴 제목 [shown] 이 `(접근성)` 을 붙여 들어간 책(편 책 기록의 책이 먼저, 읽고 있는 책, 없으면 나간 책 — 다시 열면 그 책이 되살아나게)의 것이면
     * 그 제목, 아니면 null. 편 책 기록의 책이 붙이지 않은 제목 그대로면(일반판) 그 책
     */
    internal fun listedTitle(ctx: AppContext, shown: String): String? {
        val accessible = ACCESSIBLE + shown
        val recorded = ctx.recorder.recordedBook(ctx.pkg)
        if (recorded?.hasTitle(accessible) == true) return accessible
        // 붙이지 않은 제목의 책(일반판)을 읽고 있으면 그 책(나간 `(접근성)` 책을 되살리지 않게)
        if (recorded?.hasTitle(shown) == true || ctx.recorder.lookup.byTitle(shown) != null) return null
        return accessible.takeIf { ctx.recorder.lookup.byTitle(it) != null || ctx.recorder.lookup.withoutFile(it) != null }
    }

    /** 읽는 화면: 아래 쪽 `7/343` → 진행률, 메뉴가 떠 있으면 `닫기` 옆 제목 */
    override fun readViewer(windows: AppWindows): ViewerInfo {
        val page = windows.all.flatMap(::overlay).firstNotNullOfOrNull { n -> n.text?.let(::plain)?.let { PAGE.matchEntire(it) } }
        val progress = page?.let { pagePercent(it.groupValues[1], it.groupValues[2], roundUp = false) } ?: -1
        return ViewerInfo(windows.all.firstNotNullOfOrNull(::menuTitle)?.trim(), "", progress)
    }

    /**
     * 격자 칸 단추 `표지 이름 [반납] [다운로드] [0%] 제목 저자 [읽는 사람 수]` → 칸(표지 이름과 제목이 같을 때만). 진행률 글자가 있으면 내 서재의 책,
     * 없으면(읽고 싶은 책 탭·받지 않은 책) [mine](내 서재 화면)일 때만. 제목에도 `1% `·`다운로드` 같은 글자가 들 수 있어,
     * 앞이 표지 이름이고 그 뒤가 상태 글자만 지나 같은 이름으로 다시 시작하는 곳 가운데 가장 긴 이름을 쓴다.
     */
    private fun gridCell(n: AccessibilityNodeInfo, mine: Lazy<Boolean> = lazyOf(false)): Cell? {
        if (n.className?.endsWith("Button") != true) return null
        val text = n.text?.let(::plain) ?: return null
        var found: Cell? = null
        var at = text.indexOf(' ')
        while (at > 0) {
            val alt = text.substring(0, at)
            // 상태 글자 뒤에 같은 이름이 낱말로 다시 온다(상태 글자는 제목이 `다운로드`·`1%` 로 시작해도 되돌아가 맞춘다)
            val m = if (text.indexOf(alt, at + 1) < 0) null
                else Regex(GRID_STATUS + Regex.escape(alt) + "(?=\\s|$)").find(text.substring(at))
            if (m != null && (m.groups[2] != null || mine.value)) {
                val author = text.substring(at + m.range.last + 1).trim().replace(READERS, "")
                found = Cell(bookTitle(alt), author, -1, dueDate(m.groups[1]?.value.orEmpty()), null, n.bounds())
            }
            at = text.indexOf(' ', at + 1)
        }
        return found
    }

    /**
     * 리스트 보기 칸: 아이 가운데 표지 단추([isCoverButton])와 `더보기` 단추가 있고 표지 단추 뒤 글자가 제목·저자인 묶음.
     * 표지 단추에 진행률 글자가 없으면 [mine](내 서재 화면)일 때만
     */
    private fun listCell(n: AccessibilityNodeInfo, mine: Lazy<Boolean>): Cell? {
        if (n.childCount < 2) return null
        val kids = (0 until n.childCount).mapNotNull { n.getChild(it) }
        if (kids.none { it.contentDescription?.toString() == MORE }) return null
        val at = kids.indexOfFirst(::isCoverButton)
        if (at < 0) return null
        val status = kids[at].text?.let(::plain)?.let(COVER_BUTTON::matchEntire)
        if (status == null && !mine.value) return null
        val dueText = status?.groupValues?.get(1).orEmpty()
        val texts = kids.drop(at + 1).mapNotNull { k -> k.takeIf { it.childCount == 0 }?.text?.let(::plain)?.takeIf(String::isNotEmpty) }
        val title = texts.firstOrNull() ?: return null
        return Cell(bookTitle(title), texts.getOrNull(1).orEmpty(), -1, dueDate(dueText), null, n.bounds())
    }

    /**
     * 책 상세(`바로읽기` 단추가 있는 웹 화면)의 책: 표지 그림(설명이 제목)과 그 자리, 저자는 출판사 줄(`출판사` `|` `날짜`) 바로 위 글자.
     * 진행률·반납일은 없다.
     */
    private fun detail(root: AccessibilityNodeInfo): Cell? {
        var readNow = false
        val images = mutableListOf<AccessibilityNodeInfo>()
        val texts = mutableListOf<String>()
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            val t = n.text?.let(::plain).orEmpty()
            if (t == READ_NOW && n.isClickable) readNow = true
            if (n.className?.endsWith(".Image") == true && !n.contentDescription.isNullOrBlank()) images += n
            if (n.childCount == 0 && t.isNotEmpty() && n.className?.endsWith("TextView") == true) texts += t
            if (depth < DEEP_WALK_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        if (!readNow) return null
        // 표지는 설명이 상세의 제목 글자와 같은 그림(다른 그림 단추를 표지로 보지 않게)
        val image = images.firstOrNull { plain(it.contentDescription) in texts } ?: return null
        val title = plain(image.contentDescription)
        val publisher = texts.indexOf("|") - 1
        val author = texts.getOrNull(publisher - 1)?.takeIf { publisher > 0 && it != title }.orEmpty()
        val r = image.bounds()
        return Cell(bookTitle(title), author, -1, -1, r, r)
    }


    /** [root] 안에 웹 화면이 있는가(앱 화면 창. 따로 뜬 대화상자 창에는 없다) */
    private fun hasWebView(root: AccessibilityNodeInfo): Boolean {
        fun walk(n: AccessibilityNodeInfo, depth: Int): Boolean =
            n.isWebView() || (depth < OVERLAY_DEPTH && (0 until n.childCount).any { i -> n.getChild(i)?.let { walk(it, depth + 1) } == true })
        return walk(root, 0)
    }

    /** 웹 화면 밖(읽는 화면 Compose)의 글자·설명이 있는 요소들 */
    private fun overlay(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val found = mutableListOf<AccessibilityNodeInfo>()
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (n.isWebView()) return
            if (!n.text.isNullOrBlank() || !n.contentDescription.isNullOrBlank()) found += n
            if (depth < OVERLAY_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        return found
    }

    /**
     * 메뉴 위 막대: `닫기`(설명) 단추 다음 형제 가운데 처음 글자(쪽 표시는 빼고). 같은 형제에 `검색`·`책갈피` 가 있는 묶음만(목차·설정 패널의 `닫기` 옆 이름을
     * 책 제목으로 보지 않게). 메뉴가 없으면 null
     */
    private fun menuTitle(root: AccessibilityNodeInfo): String? {
        fun has(k: AccessibilityNodeInfo, desc: String) = k.contentDescription?.toString() == desc ||
            (0 until k.childCount).any { k.getChild(it)?.contentDescription?.toString() == desc }
        fun walk(n: AccessibilityNodeInfo, depth: Int): String? {
            if (n.isWebView()) return null
            val kids = (0 until n.childCount).mapNotNull { n.getChild(it) }
            val close = kids.indexOfFirst { has(it, CLOSE) }
            val topBar = close >= 0 && kids.any { k -> TOP_BAR_ICONS.any { has(k, it) } }
            if (topBar) kids.drop(close + 1).firstNotNullOfOrNull { k -> k.text?.let(::plain)?.takeIf { it.isNotEmpty() && !PAGE.matches(it) } }?.let { return it }
            if (depth < OVERLAY_DEPTH) for (k in kids) walk(k, depth + 1)?.let { return it }
            return null
        }
        return walk(root, 0)
    }

    /** 서재·상세의 제목(`(접근성)` 은 1.x 처럼 그대로 둔다) */
    private fun bookTitle(text: String) = text.trim()

    private const val FIRST_VERSION = 205L
    private const val RECHECK_MS = 10_000L
    private const val OVERLAY_DEPTH = 24
    private const val READ_NOW = "바로읽기"
    private const val MY_SHELF = "내 서재"
    /** 받지 않은 책의 표지 위 글자 */
    private const val DOWNLOAD = "다운로드"
    /** 리스트 줄 오른쪽 위 단추의 설명 */
    private const val MORE = "더보기"
    private const val CLOSE = "닫기"
    /** 읽는 화면 위 막대에서 `닫기` 와 함께 있는 단추 */
    private val TOP_BAR_ICONS = listOf("검색", "책갈피")
    internal const val ACCESSIBLE = "(접근성)"
    /** 격자 칸 글자에서 표지 이름 뒤의 상태 자리 ` [D-14|만료] [다운로드] [0%] `(그 뒤가 제목 저자 수). 1 은 반납, 2 는 진행률 */
    private const val GRID_STATUS = """^\s+(?:((?i:D-\s*\d+|D-Day)|만료)\s+)?(?:다운로드\s+)?(?:(\d{1,3})%\s+)?"""
    /** 리스트 보기 표지 단추 `[D-14|만료] 0%` */
    private val COVER_BUTTON = Regex("""^(?:((?i:D-\s*\d+|D-Day)|만료)\s+)?(\d{1,3})%$""")
    /** 격자 칸 끝의 읽는 사람 수(`43`·`12,335`) */
    private val READERS = Regex("""\s*\d[\d,]*$""")
    /** 읽는 화면 쪽 `7/343` */
    private val PAGE = Regex("""^(\d+)\s*/\s*(\d+)$""")
}

private const val MAIN = "com.bookers.app.MainActivity"
/** 읽는 화면의 이름(요소 이름이 아니라 [Bookers2.showsViewer] 로 가른다) */
private const val VIEWER = "viewer"

/**
 * 부커스 2.0 의 판단: 책은 읽는 화면 메뉴의 제목으로만 넣는다. 서재 칸·상세 `바로읽기` 를 눌러 열어도 그 칸의 제목(도서관 상품 이름)으로 넣지 않고
 * 메뉴의 제목이 보일 때까지 책 모름으로 둔다(연 때부터의 시간은 메뉴 제목의 책에 붙는다). 메뉴 제목은 `(접근성)` 을 붙여 넣은 책에 맞춘다([Bookers2.listedTitle]).
 */
private class Bookers2Reader : ScreenReader(Bookers2) {
    /** 이 읽는 화면을 연 서재 칸·상세의 제목(누르지 않고 열렸으면 null). 메뉴 제목과 다르면 같은 책인지 묻는다 */
    private var openedFrom: String? = null

    override fun onEnterViewer(ctx: AppContext, from: String?, again: Boolean) {
        val cell = takeClicked(ctx)
        if (cell == null) {
            if (!again) openedFrom = null
            return
        }
        openedFrom = cell.title
        ctx.unknownOpenAt = ctx.now
        ctx.unknown(ctx.now)
    }

    override fun onShelf(ctx: AppContext) {
        loadAnswers(ctx)
        super.onShelf(ctx)
    }

    override fun onShelfLive(ctx: AppContext, force: Boolean) {
        loadAnswers(ctx)
        super.onShelfLive(ctx, force)
    }

    /** 서재를 읽기 전에 `같은 책` 이라 답한 짝을 불러 둔다(홈에서 답한 것이 다음 서재부터 먹게) */
    private fun loadAnswers(ctx: AppContext) = Bookers2.useAnswers(HomePrefs(ctx.context).sameBooksOf(ctx.pkg))

    override fun onViewer(ctx: AppContext) {
        val shown = readShown(ctx) ?: return
        val info = shown.title?.let { t -> Bookers2.listedTitle(ctx, t)?.let { ViewerInfo(it, shown.author, shown.progress) } } ?: shown
        logShown(ctx, info)
        retryProgress(ctx, info.progress)
        recordShown(ctx, info)
        info.title?.let { askSame(ctx, it) }
    }

    /**
     * 연 칸의 제목([openedFrom])이 메뉴 제목 [read] 와 다르면 같은 책인지 묻게 적는다(읽는 화면마다 한 번, 같은 짝은 한 번만).
     * 서재 제목이 메뉴 제목에 `(접근성)` 만 붙인 것이면(부커스의 접근성 전자책) 묻지 않고 같은 책으로 둔다
     */
    private fun askSame(ctx: AppContext, read: String) {
        val shelf = openedFrom ?: return
        openedFrom = null
        if (sameTitle(shelf, read)) return
        val prefs = HomePrefs(ctx.context)
        val pair = TitlePair(ctx.pkg, shelf, read)
        if (prefs.knowsPair(pair)) return
        if (shelf == Bookers2.ACCESSIBLE + read) {
            prefs.sameBooks = prefs.sameBooks + pair.key
            ctx.log(WatchLog.Kind.NOTE, "same: $shelf = $read")
        } else {
            prefs.sameBookAsk = prefs.sameBookAsk + pair.key
            ctx.log(WatchLog.Kind.NOTE, "same? $shelf ≠ $read")
        }
    }
}
