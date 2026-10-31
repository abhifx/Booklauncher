package com.woody.booklauncher.readers

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs

/**
 * 밀리의서재(크레마 e-ink 판 `kr.co.millie.eink`, 휴대폰 판 [MillieMobile] 과 다른 앱): 다운로드·전체도서 탭은 `BookshelfActivity`,
 * 책장 안(읽고있는 책 등)은 `BookShelfDetailActivity`. 칸에 제목·표지·진행률. 검색 탭(웹 화면) 책 상세의 `바로 읽기`·`이어 읽기` 로도 연다.
 * 읽는 화면은 아래 오른쪽 진행률(불러오는 동안의 `0%` 는 뺀다). 가운데를 누르면 뜨는 위 막대 `epub_navibar` 가운데에 제목(`팩트풀니스`)이
 * 이름 없는 글자로 있다(2026-10-16 페블에서 확인. 몇 초 뒤 저절로 닫힌다).
 * 전체도서 탭·책장 안의 받지 않은 책 칸은 누르면 내려받기만 해서, 누른 칸은 읽는 화면이 열릴 때 편 것으로 본다([openOnViewer]).
 * 받지 않은 칸은 표지 가운데에 받기 단추(받는 중에는 진행 원·취소 단추)를, 서비스가 끝난 책은 흰 막을 겹쳐 그려 그런 칸의 표지는 자르지 않는다
 * ([COVER_OVERLAYS], 칸 레이아웃 `view_bookshelf_cell`, 2026-10-21 페블에서 확인).
 */
object Millie : ReaderApp(
    pkg = "kr.co.millie.eink", classPrefix = "kr.co.millie",
    shelves = setOf("kr.co.millie.eink.bookshelf.BookshelfActivity", "kr.co.millie.eink.bookshelf.BookShelfDetailActivity"),
    viewers = setOf(
        "kr.co.millie.eink.epub.EPubViewActivity",
        "kr.co.millie.eink.pdf.PDFViewActivity",
        "kr.co.millie.eink.epub.StoryViewActivity",
    ),
    titles = listOf("tv_book_title"), thumbs = listOf("iv_thumbnail"), coverOverlays = COVER_OVERLAYS,
    authors = emptyList(), progress = "bookshelf_cell_reading", due = null,
    viewerProgress = listOf("tv_viewer_add_on_right"), viewerLoading = "epub_loading_message",
    openOnViewer = true,
) {
    override fun newAdapter(): ReaderAdapter = MillieReader()

    /** 읽는 화면 메뉴(위 막대)에 제목이 보인다(이름 없는 글자라 [viewerTitle] 대신 [readViewer] 에서 읽는다) */
    override val tellsTitle: Boolean get() = true

    /** 읽는 화면: 기본 읽기(진행률)에 더해, 메뉴가 떠 있으면 위 막대 안의 글자 하나를 제목으로(뒤로·책갈피 단추는 글자가 없다) */
    override fun readViewer(windows: AppWindows): ViewerInfo {
        val info = super.readViewer(windows)
        val title = windows.all.flatMap { it.byId(pkg, NAVI_BAR) }.flatMap { barTexts(it, 0) }.distinct().singleOrNull() ?: return info
        return ViewerInfo(title, "", info.progress)
    }

    /** 위 막대 안의 글자들(몇 단계만) */
    private fun barTexts(n: AccessibilityNodeInfo, depth: Int): List<String> =
        listOfNotNull(n.text?.let(::plain)?.takeIf { it.isNotEmpty() }) +
            if (depth < 3) (0 until n.childCount).flatMap { i -> n.getChild(i)?.let { barTexts(it, depth + 1) }.orEmpty() } else emptyList()

    private const val NAVI_BAR = "epub_navibar"

    /** 검색 책 상세의 읽기 단추 글자 */
    private val READ_NOW = setOf("바로 읽기", "이어 읽기")
    /** 웹 화면(검색 책 상세)을 훑는 깊이, 같은 줄·같은 왼쪽으로 보는 차이(px) */
    private const val WEB_DEPTH = 40
    private const val ALIGN_PX = 8

    /**
     * 검색(웹 화면)의 책 상세: `바로 읽기` 단추와 같은 줄 맨 왼쪽 단추에 왼쪽을 맞춘, 단추 위의 글자들이 제목·저자다.
     * 표지는 그 왼쪽의 그림. 요소 이름이 없는 웹 화면이라 위치로 찾는다(`바로 읽기` 클릭에는 글자가 없다).
     */
    fun webBook(root: AccessibilityNodeInfo): Cell? {
        val texts = mutableListOf<Pair<String, Rect>>()
        val images = mutableListOf<Rect>()
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            val b = n.bounds()
            n.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { texts += it to b }
            if (n.className?.endsWith("Image") == true) images += b
            if (depth < WEB_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        val button = texts.firstOrNull { it.first in READ_NOW }?.second ?: return null
        val rowLeft = texts.filter { abs(it.second.top - button.top) < ALIGN_PX }.minOf { it.second.left }
        val above = texts.filter { abs(it.second.left - rowLeft) < ALIGN_PX && it.second.bottom <= button.top }.sortedBy { it.second.top }
        val (title, titleAt) = above.firstOrNull() ?: return null
        val author = authorName(above.getOrNull(1)?.first.orEmpty())
        val cover = images.firstOrNull { it.right <= rowLeft && it.top <= titleAt.bottom && it.bottom >= button.top }
        return Cell(title, author, -1, -1, cover, Rect())
    }
}

/**
 * 표지 위에 겹치는 요소: 받기 단추, 받는 중 진행 원(앱의 철자 그대로)·취소 단추, 서비스 종료 막·배지, 공개 예정 막, 편집 중 고르기 표시.
 * 칸에 이름 없는 배지도 있지만 이름이 없어 가리지 못한다.
 */
private val COVER_OVERLAYS = listOf(
    "download_start", "dowload_progressBar", "download_cancel", "container_not_service", "v_badge_not_service",
    "container_coming_soon", "bookshelf_cell_cover_dim_none", "bookshelf_cell_cover_dim_selected",
)

/**
 * 밀리(e-ink)의 판단과 기억: 책 상세는 [DetailReader] 그대로이고, 받아야 하는 책은 `바로 읽기`를 누르면 받기 창(`SearchDownloadDialog`)이
 * 상세 위에 떠 받는 동안 상세가 안 보인다. 상세가 그 창이 뜰 무렵([COVER_SLACK_MS] 안) 사라졌으면 창이 뜬 뒤 [DOWNLOAD_KEEP_MS] 까지는 상세를 잊지 않는다(그 사이 상세가 다시 보이면 받기를 취소한 것)
 * (받는 데 3초를 넘기면 상세를 잊어 저자 없이 메뉴 제목으로 들어왔다, 2026-10-24 페블). 받기를 취소하고 상세에서 뒤로 나가 사라진 것은 바탕대로 잊는다.
 * 그 사이 서재 칸을 누르면 그 칸이 먼저다([DetailReader]).
 */
private class MillieReader : DetailReader(Millie, clickOnly = false, Millie::webBook) {
    /** 기억한 상세 위에 받기 창이 뜬 때 */
    private var coveredAt: Long? = null

    override fun keepsHidden(ctx: AppContext, gone: Long): Boolean =
        coveredAt?.let { abs(gone - it) <= COVER_SLACK_MS && ctx.now - it < DOWNLOAD_KEEP_MS } == true

    override fun onWindow(ctx: AppContext, cls: String?) {
        super.onWindow(ctx, cls)
        if (remembers && cls?.endsWith(DOWNLOAD_DIALOG) == true) coveredAt = ctx.now
    }

    override fun onEnterViewer(ctx: AppContext, from: String?, again: Boolean) {
        super.onEnterViewer(ctx, from, again)
        coveredAt = null
    }

    /** 받기 창이 뜬 뒤 상세가 다시 보이면(받기를 취소함) 그 창은 지나갔다: 그 뒤 상세가 사라지면 바탕대로 잊는다 */
    override fun onShelfLive(ctx: AppContext, force: Boolean) {
        super.onShelfLive(ctx, force)
        coveredAt?.takeIf { detailShown && ctx.now - it > COVER_SLACK_MS }?.let { coveredAt = null }
    }

    override fun beforeOpened(ctx: AppContext) {
        super.beforeOpened(ctx)
        coveredAt = null
    }

    companion object {
        /** 받기 창이 뜬 뒤 상세를 기억해 두는 때(받는 데 걸리는 때, 누른 칸을 기다리는 때와 같게) */
        private const val DOWNLOAD_KEEP_MS = ScreenReader.CLICK_OPEN_MS
        /** 받기 창이 뜬 때와 상세가 사라진 때(1초마다 읽어 늦게 적힌다)의 차이가 이만큼 안이면 그 창에 가려진 것 */
        private const val COVER_SLACK_MS = 2_000L
        /** 검색 상세의 `바로 읽기` 로 받을 때 뜨는 창 */
        private const val DOWNLOAD_DIALOG = "SearchDownloadDialog"
    }
}
