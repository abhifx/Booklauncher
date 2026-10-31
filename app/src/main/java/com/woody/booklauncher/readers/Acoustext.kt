package com.woody.booklauncher.readers

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.view.accessibility.AccessibilityNodeInfo
import com.woody.booklauncher.Seen
import com.woody.booklauncher.ShelfBook
import com.woody.booklauncher.Storage
import com.woody.booklauncher.WatchCore

/**
 * AcousText (`com.opsdevabhi.acoustext`): Jetpack Compose 기반 e-북/TTS 리더 앱.
 * 서재 화면(Library)과 읽는 화면이 `com.example.acoustext.MainActivity` 하나에서 표시된다.
 * 읽는 화면은 `reader_root_container` 요소가 포함된 경우이다([viewerHolder]).
 * 서재(Library)에서 책 제목과 표지를 읽고, 읽는 화면에서는 위 막대의 제목과 상태 줄의 진행률·화를 읽는다.
 * 홈에서 책을 누르면 `acoustext://book?title=<제목>` 으로 그 책을 바로 연다(AcousText 가 받는다, [open]).
 */
object Acoustext : ReaderApp(
    pkg = "com.opsdevabhi.acoustext",
    classPrefix = "com.example.acoustext",
    shelves = setOf(MAIN),
    viewers = setOf("$MAIN#$ROOT"),
    titles = emptyList(),
    thumbs = emptyList(),
    authors = emptyList(),
    progress = null,
    due = null,
    viewerProgress = emptyList(),
    viewerHolder = ROOT,
    eagerCovers = true,
    openOnViewer = true,
) {
    override fun newAdapter(): ReaderAdapter = AcoustextReader()

    /** 서재 칸은 요소 이름 없이 자리로 찾으므로 찍은 뒤에도 같은 자리에 있을 때만 자른다 */
    override val cellsByPosition get() = true

    /** 홈에서 그 책을 AcousText 의 책 링크로 바로 연다. 책 제목을 모르거나 BOOX 가 얼린 앱이면 앱을 연다 */
    override fun open(ctx: HomeContext, book: ShelfBook): OpenResult {
        if (ctx.frozen) return OpenResult.Launch
        // AcousText 가 보여 준 제목(사용자가 BookLauncher 에서 제목을 고쳐도 그 책을 찾게)
        val title = book.seenTitle.ifEmpty { book.title }.trim()
        if (title.isEmpty()) return OpenResult.Launch
        val uri = Uri.parse("$SCHEME://$HOST").buildUpon().appendQueryParameter("title", title)
        // 책 파일을 알면 경로도 보낸다(제목을 바꾼 책·같은 제목의 책을 더 정확히)
        ctx.bookFile(book)?.let { uri.appendQueryParameter("path", it) }
        // 홈에서 연 책은 AcousText 화면에 누름 알림이 오지 않아, 읽는 화면이 열리면 이 제목으로 센다
        return OpenResult.Start(
            Intent(Intent.ACTION_VIEW, uri.build())
                .setClassName(pkg, MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            started = { rememberPending(ctx.context, title) },
        )
    }

    /** 홈에서 연 책의 제목을 서비스가 읽을 수 있게 적어 둔다(읽는 화면이 열리면 [AcoustextReader] 가 꺼낸다) */
    private fun rememberPending(context: Context, title: String) {
        prefs(context).edit().putString(PENDING_TITLE, title).putLong(PENDING_AT, System.currentTimeMillis()).apply()
    }

    /** 적어 둔 책(아직 안 꺼냈으면). [takePending] 은 꺼내며 지운다 */
    fun pendingBook(context: Context): LinkedBook? {
        val prefs = prefs(context)
        val at = prefs.getLong(PENDING_AT, 0)
        if (at != 0L && System.currentTimeMillis() - at > PENDING_MS) {
            clearPending(context)
            return null
        }
        return prefs.getString(PENDING_TITLE, null)?.takeIf { it.isNotEmpty() }?.let { LinkedBook(it, at) }
    }

    /** 홈에서 연 책을 꺼내고 지운다(한 번만) */
    fun takePending(context: Context): LinkedBook? {
        val book = pendingBook(context) ?: return null
        clearPending(context)
        return book
    }

    fun clearPending(context: Context) {
        prefs(context).edit().remove(PENDING_TITLE).remove(PENDING_AT).apply()
    }

    /** 홈에서 연 책(제목과 누른 때) */
    class LinkedBook(val title: String, val at: Long)

    private fun prefs(context: Context) =
        Storage.of(context).getSharedPreferences(WatchCore.WATCH_PREFS, Context.MODE_PRIVATE)

    /** 같은 책이 Continue reading 머리와 Recent·격자에 함께 보여도 그중 하나라도 그 자리면 된다 */
    override fun coverStill(now: List<Cell>, title: String, rect: Rect): Boolean =
        now.any { it.title == title && it.thumb == rect }

    /**
     * 서재(Library)의 책 카드를 찾아 제목과 표지 영역, 진행률을 가져온다.
     * Compose 카드는 요소 이름이 없어, 누를 수 있는 묶음 안의 글자 없는 세로로 긴 자리를 표지로 본다.
     * Continue reading 머리 카드에는 `CONTINUE READING`·`5% completed` 같은 안내 글자가 함께 오므로 제목에서 거른다.
     */
    override fun shelfCells(root: AccessibilityNodeInfo): List<Cell> {
        if (showsViewer(root)) return emptyList()
        val found = mutableListOf<Cell>()
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            bookCell(n)?.let { found += it; return }
            if (depth < DEEP_WALK_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        return found
    }

    /** 누를 수 있는 책 카드 하나. 표지가 없으면(안내·탭·단추) 책이 아니다 */
    private fun bookCell(n: AccessibilityNodeInfo): Cell? {
        if (!n.isClickable) return null
        val at = n.bounds()
        if (at.isEmpty) return null
        var thumb: Rect? = null
        val texts = mutableListOf<String>()

        fun collect(c: AccessibilityNodeInfo, depth: Int) {
            val t = c.text?.let(::plain).orEmpty()
            if (t.isNotEmpty()) {
                texts += t
            } else {
                val r = c.bounds()
                if (r != at && usableThumb(r)) {
                    val best = thumb
                    if (best == null || r.width().toLong() * r.height() > best.width().toLong() * best.height()) thumb = r
                }
            }
            if (depth < DEEP_WALK_DEPTH) for (i in 0 until c.childCount) c.getChild(i)?.let { collect(it, depth + 1) }
        }

        collect(n, 0)
        val cover = thumb ?: return null
        val title = texts.firstOrNull(::isTitle) ?: return null
        val progress = texts.firstOrNull { PROGRESS_BADGE.matches(it) }?.let(::percent) ?: -1
        return Cell(title, "", progress, -1, cover, at)
    }

    /** 서재 카드의 안내 글자·파일 종류 표시·진행률 표시가 아닌 글자가 책 제목이다 */
    private fun isTitle(text: String): Boolean {
        val t = text.trim()
        return t.isNotEmpty() &&
            !PROGRESS_BADGE.matches(t) &&
            t.uppercase() !in FILE_TYPES &&
            t.lowercase() !in LABELS &&
            !t.all { it.isDigit() }
    }

    /**
     * 읽는 화면: 상태 줄의 진행률(`Chapter 1 • 5%`)·화와 위 막대의 책 제목. 제목을 읽으면 서재에서 누르지 않았어도
     * (앱을 다시 열어 이어진 읽는 화면·다른 길로 연 책) 그 책으로 적는다. 본문(Readium WebView)은 훑지 않는다.
     */
    override fun readViewer(windows: AppWindows): ViewerInfo {
        var progress = -1
        var pages = -1
        var chapter = ""

        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (n.isWebView() || n.viewIdResourceName == "$pkg:id/$NAV") return
            val text = n.text?.let(::plain).orEmpty()
            if (text.isNotEmpty()) {
                if (progress < 0 && isProgressText(text)) {
                    progress = percent(text)
                    chapter = chapterOf(text)
                }
                if (pages < 0) {
                    PAGE_OF.find(text)?.let { m ->
                        pages = pagePercent(m.groupValues[1], m.groupValues[2], roundUp = false)
                    }
                }
            }
            if (depth < DEEP_WALK_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }

        val roots = windows.all.flatMap { it.byId(pkg, ROOT).ifEmpty { listOf(it) } }
        roots.forEach { walk(it, 0) }
        val title = windows.all.flatMap { it.byId(pkg, ROOT) }.ifEmpty { windows.all }
            .firstNotNullOfOrNull(::toolbarTitle)
        return ViewerInfo(title, "", progress.takeIf { it >= 0 } ?: pages, chapter = chapter)
    }

    /**
     * 위 막대의 책 제목: `Back`(뒤로) 바로 뒤의 글자. 읽는 화면 위 막대가 숨었으면 없다
     * (제목이 없으면 서재에서 누른 칸·홈에서 연 책으로만 안다).
     */
    private fun toolbarTitle(root: AccessibilityNodeInfo): String? {
        var afterBack = false
        var title: String? = null
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (title != null) return
            val desc = n.contentDescription?.let(::plain).orEmpty()
            if (!afterBack && BACK.containsMatchIn(desc)) {
                afterBack = true
            } else if (afterBack && n.className?.endsWith("TextView") == true) {
                val t = n.text?.let(::plain).orEmpty()
                if (t.isNotEmpty() && t.lowercase() !in LABELS && !t.contains('%') && !CLOCK.matches(t)) title = t
            }
            if (depth < DEEP_WALK_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        return title
    }

    /**
     * 상태 줄에서 진행률인 글자인가. `%` 하나뿐인 글자는 배터리일 수 있고, 시계만 앞에 있는 `14:23 • 86%` 도 아니다
     * (진행률은 `Chapter 1 • 5%`·`Page 3 of 81 • 5%` 처럼 `%` 앞에 화·쪽이 있다).
     */
    private fun isProgressText(text: String): Boolean {
        val at = PERCENT.find(text)?.range?.first ?: return false
        val head = text.substring(0, at).trim().trimEnd('•', '·', ' ', '\u00a0').trim()
        val last = head.substringAfterLast('•').trim()
        return last.isNotEmpty() && !CLOCK.matches(last)
    }

    /** 상태 줄 글자 앞의 화(`Chapter 1 • 5%` → `Chapter 1`). 시계·쪽만 있으면 화가 아니다 */
    private fun chapterOf(text: String): String {
        val cut = PERCENT.find(text)?.range?.first ?: text.length
        val head = text.substring(0, cut).trim().trimEnd('•', '·', ' ', '\u00a0').trim()
        return head.substringAfterLast('•').trim().takeUnless { it.isEmpty() || CLOCK.matches(it) || PAGES_ONLY.matches(it) }.orEmpty()
    }

    private val PERCENT = Regex("""\d{1,3}(?:[.,]\d+)?\s*%""")

    /** 서재 카드의 진행률 표시(`5%`·`5% completed`) */
    private val PROGRESS_BADGE = Regex("""^\d{1,3}(?:[.,]\d+)?\s*%(?:\s*completed)?$""", RegexOption.IGNORE_CASE)

    private val CLOCK = Regex("""^\d{1,2}:\d{2}$""")
    private val BACK = Regex("""^(Back|뒤로)$""", RegexOption.IGNORE_CASE)
    private val PAGES_ONLY = Regex("""^(Page|페이지)\s*\d+(\s*(of|/)\s*\d+)?$""", RegexOption.IGNORE_CASE)

    /** 읽는 화면 아래 쪽 표시(`Page 48 of 894`) */
    private val PAGE_OF = Regex("""\bPage\s+(\d+)\s+of\s+(\d+)\b""", RegexOption.IGNORE_CASE)

    private val FILE_TYPES = setOf("EPUB", "PDF", "MOBI", "AZW", "AZW3", "TXT", "CBZ", "CBR", "CB7", "FB2", "ZIP", "RAR", "MP3", "M4A", "M4B", "AAC", "OGG")
    private val LABELS = setOf(
        "continue reading", "recent", "pick up where you left off", "collection premium",
        "library", "get books", "discover", "audio", "reading insights", "dashboard",
        "settings", "about", "refresh library", "full refresh", "search your collection...",
        "all", "reading", "finished", "favorites", "author", "series",
    )
}

/**
 * 서재에서 누른 칸이 있으면 그 책(바탕과 같다). 홈에서 링크로 연 책은 AcousText 화면에 누름 알림이 오지 않아
 * [Acoustext.open] 이 적어 둔 제목으로 편다(그래야 그때부터 읽은 시간과 진행률이 그 책에 붙는다).
 */
private class AcoustextReader : ScreenReader(Acoustext) {
    override fun onEnterViewer(ctx: AppContext, from: String?, again: Boolean) {
        val click = takeClicked(ctx)
        if (click != null) {
            Acoustext.clearPending(ctx.context)
            openCell(ctx, click)
            return
        }
        val pending = Acoustext.takePending(ctx.context)
        if (pending != null) {
            ctx.opened(Seen(pending.title), pending.at)
            return
        }
        super.onEnterViewer(ctx, from, again)
    }

    /** 같은 읽는 화면 안에서 다른 책으로 바뀌어(액티비티·요소가 그대로) onEnterViewer 가 없을 때 */
    override fun onViewer(ctx: AppContext) {
        Acoustext.takePending(ctx.context)?.let { pending ->
            ctx.opened(Seen(pending.title), pending.at)
        }
        super.onViewer(ctx)
    }

    /** 홈에서 연 책을 못 찾아 서재에 머문 뒤 다른 책을 누르면 그 책으로(적어 둔 제목은 버린다) */
    override fun onClick(ctx: AppContext, click: Click) {
        if (ctx.screen in app.shelves) Acoustext.clearPending(ctx.context)
        super.onClick(ctx, click)
    }
}

private const val MAIN = "com.example.acoustext.MainActivity"
private const val ROOT = "reader_root_container"
private const val NAV = "readium_nav_container"
private const val SCHEME = "acoustext"
private const val HOST = "book"

/** 홈에서 연 책의 제목(읽는 화면이 늦게 열려도 이만큼은 쓴다) */
private const val PENDING_TITLE = "acoustext_pending_title"
private const val PENDING_AT = "acoustext_pending_at"
private const val PENDING_MS = 60_000L
