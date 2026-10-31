package com.woody.booklauncher.readers

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Kotatsu(만화 앱, 9.4.1. 2025-11 저장소가 보관되어 GitHub 릴리스·F-Droid 에서 내려갔다)와 그 포크 Futon(9.9): 책 한 권은 만화 한 작품이다(화마다 따로 책을 만들지 않는다).
 * Futon 은 Kotatsu 의 코드 이름공간(`org.koitharu.kotatsu` → `io.github.landwarderer.futon`)과 패키지만 바꿔, 화면 이름·요소 이름이 같다
 * (두 소스의 레이아웃을 맞춰 봤다. Futon 은 작품 상세에 흐린 배경 `imageView_blur_background` 가 더 있다).
 * Mihon 과 달리 XML 화면이라 요소 이름이 있어 바탕 규칙으로 읽는다:
 * - 서재 칸(내장 메모리·즐겨찾기·검색 목록의 격자 `item_manga_grid`·목록 `item_manga_list`, 최근에 본 만화 탭): 표지 `imageView_cover`, 제목 `textView_title`.
 *   탐색 탭의 소스 칸은 그림이 `imageView_icon` 이라 책 칸이 아니다. 격자 칸은 표지 위에 진행 고리 `progressView`·읽지 않은 화 수 `badge` 를 그려 자르지 않는다
 *   (깨끗한 표지는 작품 상세에서).
 * - 작품 상세(`details.ui.DetailsActivity`, [detail]): 같은 표지·제목과 표(`card_details`)의 저자 `textView_author`(로컬 만화는 없다)·작품 진행률
 *   `textView_progress`(`33%`, 앱이 읽은 화·쪽으로 셈). 서재 칸을 누르면 이 상세가 뜨고(읽는 화면이 아니라 [openOnViewer]), 상세의 `지금 읽기`·`계속`(`button_read`)이나
 *   화 줄을 누르면 읽는 화면이 열린다: 그 작품([DetailReader]. 그 단추는 화 목록의 `textView_title` 들과 함께 있어 누른 칸으로는 잡히지 않는다, 2026-10-28 에뮬레이터).
 *   최근에 본 만화 탭의 `계속` 단추는 상세 없이 바로 열어 책 모름이었다가 메뉴의 작품 이름으로 안다.
 * - 읽는 화면(`reader.ui.ReaderActivity`): 가운데를 눌러 메뉴를 띄우면 위 막대에 작품 이름과 화 이름([readViewer]). 진행률은 읽는 화면에 없다
 *   (아래 정보 줄 `infoBar` 는 그림으로 그리고, 쪽 막대는 그 화의 쪽뿐) → 작품 상세의 진행률.
 * Futon 의 소설·PDF 읽는 화면([extraViewers])은 읽은 시간을 센다(위 막대가 같은 모양이라 제목도 같은 길로 읽는다, 미확인).
 */
/** 두 판 모두 코드 이름공간이 패키지 이름과 같아([pkg]) 화면 이름의 앞이 된다 */
open class KotatsuApp(pkg: String, extraViewers: Set<String> = emptySet()) : ReaderApp(
    pkg = pkg, classPrefix = pkg,
    shelves = setOf(
        "$pkg.main.ui.MainActivity",
        "$pkg.details.ui.DetailsActivity",
        "$pkg.search.ui.MangaListActivity",
        "$pkg.search.ui.multi.SearchActivity",
        "$pkg.favourites.ui.FavouritesActivity",
        "$pkg.history.ui.HistoryActivity",
    ),
    viewers = setOf("$pkg.reader.ui.ReaderActivity") + extraViewers,
    titles = listOf("textView_title"), thumbs = listOf("imageView_cover"), coverOverlays = listOf("progressView", "badge"),
    authors = listOf("textView_author"), progress = "textView_progress", due = null,
    viewerProgress = emptyList(), openOnViewer = true,
) {
    override fun newAdapter(): ReaderAdapter = DetailReader(this, clickOnly = false, ::detail)

    /**
     * 서재 칸: 작품 상세 화면이면 상세 하나([detail]: 넘겨서 위 막대 밑에 가린 표지는 뺀다. 바탕 규칙으로 읽으면 같은 작품이 가린 표지째 칸이 된다),
     * 아니면 바탕(이름으로 찾는 격자·목록 칸)
     */
    override fun shelfCells(root: AccessibilityNodeInfo): List<Cell> =
        if (find(root, DETAILS_TABLE, 0) != null) listOfNotNull(detail(root)) else super.shelfCells(root)

    /** 메뉴를 띄우면 위 막대에 작품 이름이 보인다 */
    override val tellsTitle get() = true

    /**
     * 메뉴의 위 막대(`appbar_top` 안의 `toolbar`): 요소 이름 없는 글자 둘이 작품 이름·화 이름이다. 작품을 불러오는 동안은 `불러오는 중…` 하나뿐이라
     * 화 이름까지 있을 때만 쓴다. 진행률은 없다(-1). 막대는 위쪽에 있어 그림 칸(`pager`)은 들어가지 않고 훑는다
     */
    override fun readViewer(windows: AppWindows): ViewerInfo {
        val texts = windows.all.firstNotNullOfOrNull { root -> find(root, BAR, 0)?.let { find(it, TOOLBAR, 0) }?.let(::barTexts)?.takeIf { it.size >= 2 } }
            ?: return ViewerInfo(null, "", -1)
        return ViewerInfo(texts[0], "", -1, chapter = texts[1])
    }

    /**
     * 작품 상세: 상세 표(`card_details`)가 있는 화면에서 넘기는 칸(`scrollView`) 안의 제목·표지와 표의 저자·작품 진행률. 표지가 위 막대 밑으로
     * 넘어가 덜 보이면 표지는 자르지 않는다. 상세가 아니면 null
     */
    fun detail(root: AccessibilityNodeInfo): Cell? {
        val table = find(root, DETAILS_TABLE, 0) ?: return null
        val scroll = find(root, DETAILS_SCROLL, 0) ?: return null
        val title = find(scroll, "textView_title", 0)?.text?.let(::plain)?.takeIf { it.isNotEmpty() } ?: return null
        val area = scroll.bounds()
        val cover = find(scroll, "imageView_cover", 0)?.bounds()?.takeIf { usableThumb(it) && area.contains(it) }
        val author = find(table, "textView_author", 0)?.text?.let(::plain).orEmpty()
        val progress = find(table, "textView_progress", 0)?.text?.let(::percent) ?: -1
        return Cell(title, author, progress, -1, cover, Rect())
    }

    /** [n] 아래에서 요소 이름이 [id] 인 첫 요소(읽는 화면의 그림 칸은 들어가지 않는다) */
    private fun find(n: AccessibilityNodeInfo, id: String, depth: Int): AccessibilityNodeInfo? {
        if (n.viewIdResourceName == "$pkg:id/$id") return n
        if (n.viewIdResourceName == "$pkg:id/$PAGES" || depth >= DEEP_WALK_DEPTH) return null
        return (0 until n.childCount).firstNotNullOfOrNull { i -> n.getChild(i)?.let { find(it, id, depth + 1) } }
    }

    /** 위 막대 바로 아래의 글자들(뒤로 단추는 글자가 없다) */
    private fun barTexts(toolbar: AccessibilityNodeInfo): List<String> = (0 until toolbar.childCount)
        .mapNotNull { toolbar.getChild(it) }
        .filter { it.className?.endsWith("TextView") == true }
        .mapNotNull { it.text?.let(::plain)?.takeIf { t -> t.isNotEmpty() } }
}

object Futon : KotatsuApp(
    "io.github.landwarderer.futon",
    extraViewers = setOf(
        "io.github.landwarderer.futon.novel.ui.reader.NovelReaderActivity",
        "io.github.landwarderer.futon.novel.ui.reader.pdf.PdfReaderActivity",
    ),
)

object Kotatsu : KotatsuApp("org.koitharu.kotatsu")

/** 읽는 화면 메뉴의 위 막대와 그 안의 툴바, 그림 칸 */
private const val BAR = "appbar_top"
private const val TOOLBAR = "toolbar"
private const val PAGES = "pager"
/** 작품 상세의 표와 넘기는 칸 */
private const val DETAILS_TABLE = "card_details"
private const val DETAILS_SCROLL = "scrollView"
