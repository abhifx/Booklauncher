package com.woody.booklauncher.readers

import android.content.ComponentName
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.woody.booklauncher.sameTitle

/**
 * Google Play 북(2026.9.18): 읽는 화면(`ebook.activity.ReadingActivity`)의 창 제목이 책 제목이라(메뉴 위 줄의 제목과 같다) 메뉴 없이도 책을 안다.
 * 책을 불러오는 처음 몇 초는 창 제목이 읽는 화면 이름(`Play 북`, 앱 이름은 `Google Play 북`)이라, 본문(`book_view`)에 쪽 설명이 붙은 뒤의 제목만 쓰고
 * 그 이름들은 거른다([PlayBooksReader]).
 * 진행률은 메뉴의 쪽(`position_label` `12/511`), 메뉴가 없으면 본문의 설명(`Secrets by Specification, 511페이지 중 12페이지`)의 쪽을 올림한다
 * (앱이 아래 오른쪽에 그리는 % 는 쪽 비율과 딱 맞지 않아 1 차이가 날 수 있다: 1/511쪽 1%, 12/511쪽 3%, 6/110쪽 5%).
 * 표지·저자는 라이브러리(`HomeActivity` 의 `books_view_pager`, Compose 칸 [libraryCells])와 스토어 책 상세(`navigation.activity.ArbitraryFragmentActivity`,
 * 표지 `thumbnail` 옆에 제목·저자·출시일 줄 `multiline_description_widget_description_line`)에서 온다. 올린 책(내 EPUB·PDF)은 스토어 상세가 없어 라이브러리에서만.
 * 상세의 제목은 부제까지 길지만(`CSS Secrets: Better Solutions to …`) 부제를 떼면 읽는 화면 제목과 같다. 무료 샘플은 라이브러리에 들어가지 않는다.
 */
object PlayBooks : ReaderApp(
    pkg = "com.google.android.apps.books", classPrefix = "com.google.android.apps.play.books",
    shelves = setOf(
        "com.google.android.apps.play.books.home.HomeActivity",
        "com.google.android.apps.play.books.navigation.activity.ArbitraryFragmentActivity",
    ),
    viewers = setOf("com.google.android.apps.play.books.ebook.activity.ReadingActivity"),
    titles = emptyList(), thumbs = emptyList(), authors = emptyList(), progress = null, due = null,
    viewerProgress = listOf("position_label"), pagesRoundUp = true,
) {
    override fun newAdapter(): ReaderAdapter = PlayBooksReader(this)

    /** 읽는 화면의 창 제목이 책 제목이다 */
    override val tellsTitle get() = true

    /** 라이브러리 칸이 Compose 라 자리로 찾아, 찍은 뒤에도 같은 자리에 있을 때만 자른다 */
    override val cellsByPosition get() = true

    /** 라이브러리 칸([libraryCells])과 스토어 책 상세의 머리(표지와 제목·저자 줄, [header]) */
    override fun shelfCells(root: AccessibilityNodeInfo): List<Cell> =
        root.byId(pkg, LIBRARY).flatMap(::libraryCells) + root.byId(pkg, THUMB).mapNotNull(::header)

    /**
     * 라이브러리(`내 도서`)의 칸들(Compose, 이름 없음): 누를 수 있는 묶음의 첫 아이가 글자 없는 세로로 긴 표지 자리, 그 뒤 글자가 제목·저자
     * (목록 보기는 그 아래 `24% 완료`, 격자 보기는 제목·저자만). 표지 자리 안에 설명이 붙은 표시(격자 보기에서 받지 않은 책의 받기 아이콘 `다운로드되지 않음`.
     * 받은 책의 `다운로드됨` 은 2px 로 보이지 않는다)가 보이거나, 표지가 목록 위·아래 끝에 걸려 잘려 보이거나 좌우 밖(옆 탭의 쪽)이면 자르지 않는다.
     * 진행률은 읽는 화면의 것만 쓴다.
     */
    internal fun libraryCells(list: AccessibilityNodeInfo): List<Cell> {
        val area = list.bounds()
        val found = mutableListOf<Cell>()
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            libraryCell(n, area)?.let { found += it; return }
            if (depth < DEEP_WALK_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(list, 0)
        return found
    }

    private fun libraryCell(n: AccessibilityNodeInfo, area: Rect): Cell? {
        if (!n.isClickable) return null
        val kids = (0 until n.childCount).mapNotNull { n.getChild(it) }
        val cover = kids.firstOrNull()?.takeIf { it.text.isNullOrEmpty() } ?: return null
        val at = cover.bounds().takeIf(::usableThumb) ?: return null
        val texts = kids.drop(1).filter { it.className?.endsWith("TextView") == true }.mapNotNull { it.text?.let(::plain)?.takeIf { t -> t.isNotEmpty() } }
        val title = texts.firstOrNull() ?: return null
        val author = texts.getOrNull(1)?.takeIf { percent(it) < 0 }.orEmpty()
        // 위·아래 끝에 걸리면 잘려 보이는 것, 좌우 밖이면 옆 탭(`서가` 등)의 쪽에 있는 칸
        val clean = at.top > area.top && at.bottom < area.bottom && at.left >= area.left && at.right <= area.right && !badged(cover)
        return Cell(title, author, -1, -1, at.takeIf { clean }, n.bounds())
    }

    /** 표지 자리 안에 설명이 붙은 표시가 보이는가(받기 아이콘) */
    private fun badged(cover: AccessibilityNodeInfo): Boolean {
        fun walk(n: AccessibilityNodeInfo, depth: Int): Boolean =
            (!n.contentDescription.isNullOrEmpty() && n.bounds().width() >= BADGE_PX) ||
                (depth < WALK_DEPTH && (0 until n.childCount).any { i -> n.getChild(i)?.let { walk(it, depth + 1) } == true })
        return (0 until cover.childCount).any { i -> cover.getChild(i)?.let { walk(it, 1) } == true }
    }

    /** 표지 [thumb] 와 같은 줄의 제목·저자 줄: 첫째가 제목, 둘째가 저자(셋째는 출시일) */
    internal fun header(thumb: AccessibilityNodeInfo): Cell? {
        val row = thumb.parent ?: return null
        val lines = mutableListOf<String>()
        var cover: Rect? = null
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            when (n.viewIdResourceName) {
                "$pkg:id/$LINE" -> n.text?.let(::plain)?.takeIf { it.isNotEmpty() }?.let { lines += it }
                "$pkg:id/$IMAGE" -> if (cover == null) cover = n.bounds()
            }
            if (depth < WALK_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(row, 0)
        val title = lines.firstOrNull() ?: return null
        return Cell(title, lines.getOrNull(1).orEmpty(), -1, -1, cover?.takeIf(::usableThumb), row.bounds())
    }

    /**
     * 읽는 화면: 본문(`book_view`)에 쪽 설명이 붙은 창의 제목이 책 제목(본문을 그리기 전에는 설명이 없고 창 제목도 앱 이름일 수 있다).
     * 진행률은 메뉴의 쪽(바탕 규칙), 없으면 본문 설명의 쪽([pagesOf]). 본문 웹 화면은 훑지 않고 이름으로만 찾는다.
     */
    override fun readViewer(windows: AppWindows): ViewerInfo {
        val base = super.readViewer(windows)
        val (root, desc) = windows.all.firstNotNullOfOrNull { r ->
            r.byId(pkg, BOOK).firstNotNullOfOrNull { it.contentDescription?.takeIf { d -> d.isNotBlank() } }?.let { r to it }
        } ?: return base
        val title = root.window?.title?.let(::plain)?.takeIf { it.isNotEmpty() }
        return ViewerInfo(title, "", base.progress.takeIf { it >= 0 } ?: pagesOf(desc) ?: -1)
    }

    /**
     * 본문 설명 `<장 제목>, 511페이지 중 12페이지`(영어 `<장 제목>, Page 12 of 511`)의 진행률: 마지막 쉼표 뒤의 숫자 둘 가운데 작은 것이 지금 쪽(올림).
     * 숫자가 둘이 아니면 null
     */
    internal fun pagesOf(desc: CharSequence): Int? {
        val tail = desc.toString().substringAfterLast(", ")
        val nums = NUMBER.findAll(tail).map { it.value }.toList().takeIf { it.size == 2 } ?: return null
        val (page, total) = nums.sortedBy { it.toLong() }
        return pagePercent(page, total, roundUp = true).takeIf { it >= 0 }
    }
}

/**
 * 창 제목이 앱 이름·읽는 화면 이름(`Play 북`)이면 책을 불러오는 중이라 제목도 진행률도 쓰지 않는다(그 쪽이 앞서 편 책에 적히지 않게).
 * 읽는 화면에는 저자가 없어, 마지막으로 본 라이브러리·스토어 상세의 칸 가운데 같은 책(부제를 떼고 같은 제목)이 있으면 그 저자를 붙인다. 그 밖은 바탕 그대로
 */
class PlayBooksReader(app: ReaderApp) : ScreenReader(app) {
    /** 마지막으로 본 라이브러리 칸·스토어 책 상세 */
    private var seen: List<Cell> = emptyList()
    /** 앱 이름·읽는 화면 이름(처음 읽을 때 한 번, 어댑터는 서비스가 붙을 때마다 새로 만든다) */
    private var labels: Set<String>? = null

    override fun onShelf(ctx: AppContext) {
        super.onShelf(ctx)
        ctx.root?.let(app::shelfCells)?.takeIf { it.isNotEmpty() }?.let { seen = it }
    }

    override fun onViewer(ctx: AppContext) {
        val shown = readShown(ctx) ?: return
        val info = when {
            shown.title != null && shown.title in labels(ctx) -> ViewerInfo(null, "", -1)
            else -> ViewerInfo(shown.title, shown.title?.let { t -> seen.firstOrNull { sameTitle(it.title, t) && it.author.isNotEmpty() }?.author }.orEmpty(), shown.progress)
        }
        logShown(ctx, info)
        retryProgress(ctx, info.progress)
        recordShown(ctx, info)
    }

    private fun labels(ctx: AppContext): Set<String> = labels ?: run {
        val pm = ctx.context.packageManager
        app.viewers.mapNotNull { runCatching { pm.getActivityInfo(ComponentName(ctx.pkg, it), 0).loadLabel(pm).toString() }.getOrNull() }.toSet() +
            listOfNotNull(runCatching { pm.getApplicationLabel(pm.getApplicationInfo(ctx.pkg, 0)).toString() }.getOrNull())
    }.also { labels = it }
}

private const val BOOK = "book_view"
/** 라이브러리 탭(`내 도서`·`서가`…)의 칸들을 담는 쪽 넘김 */
private const val LIBRARY = "books_view_pager"
private const val THUMB = "thumbnail"
private const val IMAGE = "book_card_image"
private const val LINE = "multiline_description_widget_description_line"

private val NUMBER = Regex("""\d+""")

/** 표지 위 표시로 보는 최소 폭(px. 받은 책의 `다운로드됨` 표시는 2px) */
private const val BADGE_PX = 8
