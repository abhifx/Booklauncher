package com.woody.booklauncher.readers

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.woody.booklauncher.Seen
import kotlin.math.abs

/**
 * Kobo Books(11.1.2, 한국어가 없어 한국어 기기에서도 영어로 보인다): 서재·홈·책 상세는 `MainActivity` 안의 Compose 라 요소 이름이 없고 눌러도 알림이 없다.
 * 읽는 화면(`reader.ui.ReaderActivity`)은 React Native 라 요소 이름이 패키지 없는 testID(`epub-reader-rx-scrubber-bookLocation`)다.
 * 읽는 화면에는 제목이 어디에도 없어(위 줄은 단추뿐, 메뉴 아래는 장 이름·`13 of 41`, 목차에도 없다) 책은 책 상세나 My Books 의 순서로 안다([KoboReader]).
 * - My Books(eBooks 탭) 칸: 누를 수 있는 묶음의 첫 아이가 글자 없는 표지 자리, 그 뒤 제목·`By 저자`·상태(`PREVIEW`), 오른쪽 위 `Menu options` 단추([libraryCells]).
 *   누르면 바로 읽는 화면이 열린다. 정렬이 `Sort: Recent Activity` 면 읽고 돌아온 책이 맨 위에 온다([recentTop]). 홈의 스토어 줄 칸은 메뉴 단추가 없어 칸으로 보지 않는다.
 * - 책 상세(스토어 칸·검색 결과를 누르면 뜬다): 넘기는 묶음 안에 표지 자리·제목·부제·시리즈 링크·`by `·저자 링크·단추 줄(`Read Preview`·`Save Preview`·`Buy Now`)([detail]).
 *   `Save Preview`(My Books 에 넣고 연다)·`Read Preview` 로 미리보기를 연다(게스트도). 단추를 누르면 그 상세의 책을 편 것으로 본다([DetailReader]).
 * 진행률은 가운데를 눌러 메뉴를 띄우면 아래의 쪽(`13 of 41`, 화면 글자 크기에 따라 전체 쪽이 바뀐다). 아래 `1 of 1 in chapter` 는 장 안의 쪽이라 쓰지 않는다.
 * 서재 칸의 상태 줄에는 진행률이 없다(게스트 미리보기는 `PREVIEW`). 만화·PDF 읽는 화면(`MangaReaderActivity`·`PDFReaderActivity`)은 읽은 시간만 센다(진행률 미확인).
 */
object Kobo : ReaderApp(
    pkg = "com.kobobooks.android", classPrefix = "com.kobobooks",
    shelves = setOf("com.kobobooks.android.MainActivity", "com.kobobooks.itemdetails.ItemDetailsActivity"),
    viewers = setOf(
        "com.kobobooks.reader.ui.ReaderActivity",
        "com.kobobooks.manga_reader.ui.MangaReaderActivity",
        "com.kobobooks.pdf_reader.ui.PDFReaderActivity",
    ),
    titles = emptyList(), thumbs = emptyList(), authors = emptyList(), progress = null, due = null,
    viewerProgress = emptyList(), openOnViewer = true,
) {
    override fun newAdapter(): ReaderAdapter = KoboReader(this)

    /** 칸·상세가 Compose 라 자리로 찾아, 찍은 뒤에도 같은 자리에 있을 때만 자른다 */
    override val cellsByPosition get() = true

    /** My Books 칸([libraryCells])과 책 상세([detail]: 표지를 잘라 둔다. 목록에 넣는 것은 단추로 읽는 화면이 열릴 때) */
    override fun shelfCells(root: AccessibilityNodeInfo): List<Cell> = libraryCells(root) + listOfNotNull(detail(root))

    /** My Books 칸들. 표지는 넘기는 목록의 위·아래 끝에 걸려 잘려 보이면 자르지 않는다 */
    internal fun libraryCells(root: AccessibilityNodeInfo): List<Cell> {
        val found = mutableListOf<Cell>()
        fun walk(n: AccessibilityNodeInfo, area: Rect?, depth: Int) {
            if (n.isWebView()) return
            libraryCell(n, area)?.let { found += it; return }
            val inner = if (n.isScrollable) n.bounds() else area
            if (depth < DEEP_WALK_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, inner, depth + 1) }
        }
        walk(root, null, 0)
        return found
    }

    /** 칸 하나: 첫 아이가 글자 없는 표지 자리, 그 뒤 글자가 제목·저자, 칸 안에 눌리는 메뉴 단추가 있다. 진행률은 읽는 화면의 것만 쓴다 */
    private fun libraryCell(n: AccessibilityNodeInfo, area: Rect?): Cell? {
        if (!n.isClickable) return null
        val kids = children(n)
        val cover = kids.firstOrNull()?.takeIf { it.text.isNullOrEmpty() && it.contentDescription.isNullOrEmpty() } ?: return null
        val at = cover.bounds().takeIf(::usableThumb) ?: return null
        if (kids.none { it.isClickable }) return null
        val texts = kids.filter(::isText).mapNotNull { it.text?.let(::plain)?.takeIf { t -> t.isNotEmpty() } }
        val title = texts.firstOrNull() ?: return null
        val author = texts.getOrNull(1)?.let(::byline).orEmpty()
        val clean = area == null || (at.top > area.top && at.bottom < area.bottom && at.left >= area.left && at.right <= area.right)
        return Cell(title, author, -1, -1, at.takeIf { clean }, n.bounds())
    }

    /**
     * My Books 가 eBooks 탭·최근 활동 순이고 맨 위까지 보일 때 맨 위 칸(가장 최근에 연 책). 아니면 null.
     * 탭·정렬 이름은 Kobo 의 문자열([labels], 기기 언어)로 본다: 고른 탭은 눌리지 않는 글자, 정렬 단추는 `Sort: Recent Activity`.
     * 정렬 단추는 목록과 함께 넘어가므로 그것이 보이면 목록 맨 위이고, 그 아래 첫 칸이 맨 위 칸이다.
     */
    internal fun recentTop(root: AccessibilityNodeInfo, labels: Labels): Cell? {
        var sort: Rect? = null
        var tab = false
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (n.isWebView()) return
            if (isText(n)) n.text?.let(::plain)?.let { t ->
                if (t == labels.sort) sort = n.bounds()
                if (t == labels.ebooks && n.parent?.isClickable == false) tab = true
            }
            if (depth < DEEP_WALK_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        val below = sort?.takeIf { tab }?.bottom ?: return null
        return libraryCells(root).filter { it.cell.top >= below }.minByOrNull { it.cell.top }
    }

    /** My Books 의 탭·정렬 이름 */
    internal class Labels(val ebooks: String, val sort: String)

    /** 기기 언어의 이름과 그 언어(바뀌면 다시 읽는다) */
    @Volatile private var labelCache: Pair<String, Labels>? = null

    /** Kobo 의 문자열 `tab_ebooks`·`core_ui_sort`(`Sort: %s`)·`core_ui_sort_recent` 를 기기 언어로. 못 읽으면 영어(기억하지 않고 다음에 다시 읽는다) */
    internal fun labels(context: Context): Labels {
        val config = Configuration(Resources.getSystem().configuration)
        val lang = config.locales.toLanguageTags()
        labelCache?.takeIf { it.first == lang }?.let { return it.second }
        val res = runCatching { context.createPackageContext(pkg, 0).createConfigurationContext(config).resources }.getOrNull()
        fun str(name: String): String? = res?.let { r ->
            @Suppress("DiscouragedApi")
            r.getIdentifier(name, "string", pkg).takeIf { it != 0 }?.let { runCatching { r.getString(it) }.getOrNull() }
        }
        val recent = str("core_ui_sort_recent") ?: RECENT
        val sort = str("core_ui_sort")?.let { runCatching { String.format(it, recent) }.getOrNull() } ?: String.format(SORT, recent)
        return Labels(plain(str("tab_ebooks") ?: EBOOKS), plain(sort)).also { if (res != null) labelCache = lang to it }
    }

    /**
     * 책 상세: 눌리지 않는 묶음의 아이가 가운데 놓인 글자 없는 표지 자리, 바로 뒤 제목 글자이고, 그 묶음에 단추(안에 버튼 요소)가 든 줄이 있다
     * (홈을 넘기면 왼쪽 위 빈 자리·`New Releases`·`View More` 단추가 같은 꼴이 된다. 그 자리는 왼쪽 끝에 붙어 있다).
     * 저자는 제목 뒤에서 눌리지 않는 글자(`by `) 바로 오른쪽, 같은 줄의 눌리는 글자(저자 링크. 시리즈 링크 `Book 6.0 - …` 는 혼자 한 줄이다)
     */
    fun detail(root: AccessibilityNodeInfo): Cell? {
        fun walk(n: AccessibilityNodeInfo, depth: Int): Cell? {
            if (n.isWebView()) return null
            return detailOf(n) ?: if (depth < DEEP_WALK_DEPTH) (0 until n.childCount).firstNotNullOfOrNull { i -> n.getChild(i)?.let { walk(it, depth + 1) } } else null
        }
        return walk(root, 0)
    }

    private fun detailOf(holder: AccessibilityNodeInfo): Cell? {
        if (holder.isClickable) return null
        val kids = children(holder)
        val box = holder.bounds()
        val at = kids.indexOfFirst { k ->
            k.text.isNullOrEmpty() && k.contentDescription.isNullOrEmpty() && !k.isClickable &&
                k.bounds().let { c -> usableThumb(c) && c.left > box.left && abs(c.centerX() - box.centerX()) <= box.width() / CENTER_SLACK }
        }
        if (at < 0) return null
        val title = kids.getOrNull(at + 1)?.takeIf(::isText)?.text?.let(::plain)?.takeIf { it.isNotEmpty() } ?: return null
        if (kids.none { k -> k.isClickable && children(k).any { it.className?.endsWith("Button") == true } }) return null
        val author = kids.drop(at + 2).zipWithNext().firstOrNull { (by, link) ->
            isText(by) && !by.isClickable && isText(link) && link.isClickable && by.bounds().let { b ->
                val l = link.bounds()
                b.right <= l.left + ROW_SLACK && b.centerY() in l.top..l.bottom
            }
        }?.second?.text?.let(::plain).orEmpty()
        return Cell(title, author, -1, -1, kids[at].bounds(), Rect())
    }

    /**
     * 읽는 화면 메뉴 아래의 쪽(`13 of 41`)을 진행률로. 요소 이름이 패키지 없는 testID 라 이름으로 찾기가 안 되어 훑는다(본문 웹 화면은 들어가지 않는다).
     * 제목은 없다
     */
    override fun readViewer(windows: AppWindows): ViewerInfo {
        var progress = -1
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (n.isWebView() || progress >= 0) return
            if (n.viewIdResourceName?.substringAfter(":id/") == LOCATION) n.text?.let { progress = pagesOf(it) ?: -1 }
            if (depth < DEEP_WALK_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        windows.all.forEach { walk(it, 0) }
        return ViewerInfo(null, "", progress)
    }

    /** `13 of 41` → 31(버림): 숫자 둘 가운데 작은 것이 지금 쪽(언어에 따라 순서가 바뀌어도). 숫자가 둘이 아니면 null */
    internal fun pagesOf(text: CharSequence): Int? {
        val nums = NUMBER.findAll(text).map { it.value }.toList().takeIf { it.size == 2 } ?: return null
        val (page, total) = nums.sortedBy { it.toLong() }
        return pagePercent(page, total, roundUp = false).takeIf { it >= 0 }
    }

    /** `By Michael Connelly` → `Michael Connelly`. 진행률·상태 글자(`PREVIEW`, 대문자뿐. 대소문자가 없는 한글·한자 이름은 상태가 아니다)가 아니면 */
    private fun byline(text: String): String? = when {
        percent(text) >= 0 -> null
        text.startsWith(BY) -> text.removePrefix(BY).trim()
        text.any { it.isUpperCase() } && text.none { it.isLowerCase() } -> null
        else -> text
    }

    private fun isText(n: AccessibilityNodeInfo) = n.className?.endsWith("TextView") == true

    private fun children(n: AccessibilityNodeInfo) = (0 until n.childCount).mapNotNull { n.getChild(it) }
}

/**
 * Kobo 의 판단과 기억: 책 상세의 단추로 연 책은 바탕([DetailReader], 누른 칸은 알림이 없어 모른다). 그 밖에 서재에서 읽는 화면으로 넘어오면 책 모름으로 적고
 * 그 때를 기억해 두었다가, 읽고 돌아와 처음 멈춘 서재가 최근 활동 순 My Books 의 맨 위까지 보이는 화면이면 맨 위 칸([Kobo.recentTop], 방금 읽은 책)을
 * 그때 편 것으로 적는다(읽는 화면이 열린 때부터의 시간이 그 책에 붙고, 그동안 메뉴로 읽은 진행률도 함께 적는다. 2026-10-27 에뮬레이터: 읽고 돌아오면 1초 안에 그 책이 맨 위로 온다).
 * 다른 서재 화면이었으면 잊는다. 멈추기 전이라도 맨 위 칸이 열기 전과 달라졌거나 돌아오고 1초가 지났으면 본다(곧바로 다음 칸·다른 탭을 눌러도 앞 책을 잃지 않게).
 */
class KoboReader(app: ReaderApp) : DetailReader(app, clickOnly = true, Kobo::detail) {
    /** 책 모름으로 연 읽는 화면이 열린 때 */
    private var unknownSince: Long? = null
    /** 그 읽는 화면에서 마지막으로 읽은 진행률(책 모름이라 적지 못했다. 책을 알게 되면 함께 적는다) */
    private var unknownProgress = -1
    /** 움직이는 서재에서 맨 위 칸을 마지막으로 본 때 */
    private var lastLiveTry = 0L
    /**
     * 책 모름이 아닐 때 마지막으로 본 My Books 맨 위 칸의 제목(읽는 화면을 열기 전의 순서). 우리가 맨 위 칸으로 정하지 않은 책을 열면
     * (책 상세로 연 책, 정하지 못한 책) 순서가 바뀌므로 비운다
     */
    private var topBefore: String? = null

    override fun onEnterViewer(ctx: AppContext, from: String?, again: Boolean) {
        super.onEnterViewer(ctx, from, again)
        if (again) return
        val lost = unknownSince != null
        unknownSince = ctx.now.takeIf { from in app.shelves && ctx.recorder.recordedBook(ctx.pkg) == null }
        unknownProgress = -1
        if (lost || unknownSince == null) topBefore = null
    }

    /** 바탕 그대로이고, 책 모름인 동안의 진행률은 기억해 둔다 */
    override fun onViewer(ctx: AppContext) {
        val shown = readShown(ctx) ?: return
        if (unknownSince != null && shown.progress >= 0) unknownProgress = shown.progress
        logShown(ctx, shown)
        retryProgress(ctx, shown.progress)
        recordShown(ctx, shown)
    }

    /**
     * 서재가 움직이는 중에도 맨 위 칸을 본다(멈추기 전에 다음 칸을 누르거나 다른 탭으로 가면 앞 책을 잃는다): 열기 전의 맨 위 칸([topBefore])과 다르면
     * 다시 정렬된 것이라 바로, 같으면(맨 위 책을 다시 읽었거나 아직 정렬 전) 화면이 바뀌고 [RESORT_MS] 가 지난 뒤. 못 찾으면 잊지 않고 멈춘 서재에 맡긴다.
     * 책 모름이 아닐 때는 열기 전의 맨 위 칸을 적어 둔다. 창 전체를 훑으므로 [THROTTLE_MS] 에 한 번, 책 모름인 동안은 [RESORT_MS] 의 반에 한 번(화면이 바뀔 때는 바로)
     */
    override fun onShelfLive(ctx: AppContext, force: Boolean) {
        super.onShelfLive(ctx, force)
        val now = ctx.now
        if (!force && now - lastLiveTry < (if (unknownSince != null) RESORT_MS / 2 else THROTTLE_MS)) return
        lastLiveTry = now
        val root = ctx.root ?: return
        val top = Kobo.recentTop(root, Kobo.labels(ctx.context))
        val since = unknownSince
        if (since == null) {
            top?.let { topBefore = it.title }
            return
        }
        if (top != null && ((topBefore != null && top.title != topBefore) || now - ctx.lastTransition >= RESORT_MS)) openTop(ctx, top, since)
    }

    override fun onShelf(ctx: AppContext) {
        // 읽고 돌아와 처음 멈춘 서재에서만 본다(책 상세로 돌아갔다가 My Books 에 가면 그 사이 연 다른 책·My Books 에 없는 미리보기일 수 있다)
        val root = ctx.root
        val top = root?.let { Kobo.recentTop(it, Kobo.labels(ctx.context)) }
        val since = unknownSince
        when {
            since == null -> top?.let { topBefore = it.title }
            top != null -> openTop(ctx, top, since)
            root != null -> { unknownSince = null; topBefore = null }
        }
        super.onShelf(ctx)
    }

    /** 책 모름으로 연 읽는 화면([since] 에 열림)의 책을 My Books 맨 위 칸 [top] 으로 적는다 */
    private fun openTop(ctx: AppContext, top: Cell, since: Long) {
        unknownSince = null
        topBefore = top.title
        ctx.opened(Seen(top.title, top.author, unknownProgress), since)
    }

    override fun beforeOpened(ctx: AppContext) {
        super.beforeOpened(ctx)
        unknownSince = null
    }
}

/** 맨 위 칸이 열기 전과 같을 때 다시 정렬을 기다리는 때(2026-10-27 에뮬레이터: 읽고 돌아오면 1초 안에 그 책이 맨 위로 온다) */
private const val RESORT_MS = 1000L
/** 읽는 화면 메뉴 아래의 쪽(testID) */
private const val LOCATION = "epub-reader-rx-scrubber-bookLocation"
private const val BY = "By "
/** Kobo 의 문자열을 못 읽을 때의 영어(11.1.2) */
private const val EBOOKS = "eBooks"
private const val SORT = "Sort: %s"
private const val RECENT = "Recent Activity"
/** 책 상세 표지의 가운데가 묶음 가운데에서 벗어날 수 있는 만큼(묶음 폭의 1/20) */
private const val CENTER_SLACK = 20
/** `by ` 와 저자 링크 사이로 볼 틈(px) */
private const val ROW_SLACK = 4
private val NUMBER = Regex("""\d+""")
