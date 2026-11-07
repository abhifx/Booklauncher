package com.woody.pebbledesk.readers

import android.view.accessibility.AccessibilityNodeInfo

/**
 * 이씨오 범용뷰어 3.0(도서관 전자책 대출, 3.0.44): 서재(`MyWorkspaceActivity`, 대출한 책)와 읽는 화면(`ViewerActivity`)이 모두 Compose 라 요소 이름이 없다.
 * 서재는 목록·격자 보기 칸 모양이 같다: 누를 수 있는 칸 안 한 묶음에 표지 묶음(표지 그림 `책 표지 이미지` + 진행 막대), 제목, `대출일`·`반납예정일` 과
 * 두 날짜(`2026. 11. 14.`), `반납`·`연장` 단추. 진행 막대는 서버의 읽은 % 인데 읽어도 0 그대로라(2026-11-07 7/12 장까지 읽고 새로고침) 쓰지 않는다.
 * Compose 는 칸을 눌러도 누름 알림을 보내지 않아 누른 칸을 모른다. 그래서 편 책은 읽는 화면이 알려 준다([tellsTitle]).
 * 읽는 화면은 웹 화면(epub.eco.co.kr 의 웹 뷰어, 책 파일을 받지 않는다)이 Compose 안에 들어 있어 창 뿌리에서 훑으면 닿지 않고(Compose 가 내보내지 않는다),
 * 웹 화면이 보내는 내용 바뀜 알림의 요소로만 닿는다([ReaderAdapter.onContent]). 화면 캡처는 막혀 있다(표지는 서재에서 자른다).
 * 웹 화면 안에는 메뉴를 닫아도 옆 메뉴의 표지 단추 `표지 이미지 <제목> <저자>` 와 아래 메뉴의 장 순서(`7` `/` `12`, 진행 막대 위 `7 / 12`: 목차의 몇 번째 장)가 있다.
 * 뒤로를 2초 안에 두 번 눌러야 닫힌다. Play 라이선스 확인이 있어 Play 스토어 없는 기기·에뮬레이터에서는 켜자마자 닫힌다.
 */
object EcoViewer : ReaderApp(
    pkg = "com.mackerly.eco.ecobookviewer3", classPrefix = "com.mackerly",
    shelves = setOf("com.mackerly.eco.ecobookviewer3.ui.MyWorkspaceActivity"),
    viewers = setOf("com.mackerly.eco.viewer.ViewerActivity"),
    titles = emptyList(), thumbs = emptyList(), authors = emptyList(), progress = null, due = null,
    viewerProgress = emptyList(), eagerCovers = true,
) {
    /** 읽는 화면은 메뉴 없이도 표지 단추로 제목을 알려 준다 */
    override val tellsTitle get() = true

    override fun newAdapter(): ReaderAdapter = EcoReader(this)

    /** 서재(목록·격자 보기)의 칸들: 누를 수 있는 요소 가운데 칸 모양인 것([cell]) */
    override fun shelfCells(root: AccessibilityNodeInfo): List<Cell> {
        val found = mutableListOf<Cell>()
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (n.isClickable) cell(n)?.let { found += it; return }
            if (depth < DEEP_WALK_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        return found
    }

    /**
     * 칸: 누를 수 있는 요소 아래 묶음 하나, 그 첫 요소가 표지 묶음(설명 있는 표지 그림과 진행 막대), 그 뒤 첫 글자가 제목.
     * 반납 예정일은 날짜 글자 가운데 늦은 것(대출일·반납예정일 순서가 보기마다 같지만 날짜로 가린다)
     */
    private fun cell(n: AccessibilityNodeInfo): Cell? {
        val box = n.takeIf { it.childCount == 1 }?.getChild(0) ?: return null
        val kids = (0 until box.childCount).mapNotNull { box.getChild(it) }
        val coverBox = kids.firstOrNull() ?: return null
        val parts = (0 until coverBox.childCount).mapNotNull { coverBox.getChild(it) }
        if (parts.none { it.className?.endsWith("ProgressBar") == true }) return null
        val cover = parts.firstOrNull { !it.contentDescription.isNullOrEmpty() } ?: return null
        val title = kids.drop(1).firstOrNull { it.className?.endsWith("TextView") == true }?.text?.let(::plain)?.takeIf { it.isNotEmpty() } ?: return null
        val due = kids.mapNotNull { k -> k.text?.let(::plain)?.takeIf { DATE.matches(it) }?.let(::dueDate)?.takeIf { it >= 0 } }.maxOrNull() ?: -1
        return Cell(title, "", -1, due, cover.bounds(), n.bounds())
    }

    /**
     * 읽는 화면의 웹 화면 [web] 에서 제목·저자(표지 단추)와 장 순서로 셈한 진행률. 제목과 저자는 한 글자로 붙어 있어,
     * 앞에서부터 아는 제목([known]: 서재에서 본 제목·읽고 있는 책)인 부분을 제목으로 하고 나머지를 저자로 본다. 모르면 제목 없음.
     * 본문 글자는 모으지 않는다(짧은 글자와 표지 단추만 본다).
     */
    internal fun readWeb(web: AccessibilityNodeInfo, known: (String) -> Boolean): ViewerInfo {
        val short = mutableListOf<String>()
        var cover: String? = null
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            n.text?.let(::plain)?.takeIf { it.isNotEmpty() }?.let { t ->
                if (cover == null && t.startsWith(COVER)) cover = t.removePrefix(COVER).trim()
                else if (t.length <= SHORT_TEXT) short += t
            }
            if (depth < DEEP_WALK_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(web, 0)
        val (title, author) = cover?.let { splitCover(it, known) } ?: (null to "")
        return ViewerInfo(title, author, chapterPercent(short))
    }

    /** `레 미제라블 3(한글판) 빅토르 위고` → 아는 제목인 가장 긴 앞부분과 나머지(저자). 아는 제목이 없으면 null */
    internal fun splitCover(text: String, known: (String) -> Boolean): Pair<String, String>? {
        if (text.isEmpty()) return null
        if (known(text)) return text to ""
        var i = text.lastIndexOf(' ')
        while (i > 0) {
            val head = text.substring(0, i).trim()
            if (head.isNotEmpty() && known(head)) return head to text.substring(i + 1).trim()
            i = text.lastIndexOf(' ', i - 1)
        }
        return null
    }

    /**
     * 아래 메뉴의 장 순서 → %(버림): 숫자·`/`·숫자 세 글자가 잇달아 있으면 그것, 없으면 `7 / 12` 한 글자. 지금 장이 전체보다 크면 버린다. 없으면 -1
     */
    internal fun chapterPercent(texts: List<String>): Int {
        for (i in 0 until texts.size - 2) {
            if (texts[i + 1] == "/" && NUMBER.matches(texts[i]) && NUMBER.matches(texts[i + 2])) return part(texts[i], texts[i + 2])
        }
        return texts.firstNotNullOfOrNull { CHAPTERS.find(it) }?.let { part(it.groupValues[1], it.groupValues[2]) } ?: -1
    }

    /**
     * 표지 단추를 나눌 때 제목을 맞추는 열쇠: 글자·숫자만 남긴다. [normTitle] 은 `:` 뒤 부제를 잘라
     * `사피엔스: 부제 저자` 전체가 `사피엔스: 부제` 와 같아져 저자까지 제목이 되므로 쓰지 않는다
     */
    internal fun titleKey(title: String): String = title.filter { it.isLetterOrDigit() }.lowercase()

    private fun part(cur: String, total: String): Int {
        val c = cur.toLongOrNull() ?: return -1
        val t = total.toLongOrNull() ?: return -1
        return if (c > t) -1 else pagePercent(cur, total, roundUp = false)
    }

    /** 옆 메뉴 표지 단추 글자의 앞(그림 설명). 앱이 한국어뿐이다 */
    private const val COVER = "표지 이미지 "
    /** 메뉴 글자로 볼 길이(본문 문단은 이보다 길다) */
    private const val SHORT_TEXT = 12
    private val NUMBER = Regex("""\d{1,5}""")
    private val CHAPTERS = Regex("""^(\d{1,5})\s*/\s*(\d{1,5})$""")
    /** 서재 칸의 날짜 `2026. 11. 14.` */
    private val DATE = Regex("""^\d{4}\.\s*\d{1,2}\.\s*\d{1,2}\.?$""")
}

/**
 * 이씨오 범용뷰어 3.0 의 판단: 읽는 화면의 웹 화면을 내용 바뀜 알림으로 붙잡아 두고, 읽는 화면이 잠잠해지면 그 안에서 제목·저자·장 순서를 읽는다.
 * 제목을 나누려고 서재에서 본 제목을 기억한다(누른 칸은 알림이 없어 모른다)
 */
private class EcoReader(app: ReaderApp) : ScreenReader(app) {
    /** 읽는 화면의 웹 화면(바깥 WebView 요소). 읽는 화면에 새로 들어오면 다시 붙잡는다 */
    private var web: AccessibilityNodeInfo? = null
    /** 서재에서 본 제목([EcoViewer.titleKey]) */
    private val shelfTitles = mutableSetOf<String>()

    override fun onEnterViewer(ctx: AppContext, from: String?, again: Boolean) {
        if (!again) web = null
        super.onEnterViewer(ctx, from, again)
    }

    override fun onContent(ctx: AppContext, source: () -> AccessibilityNodeInfo?) {
        if (web != null) return
        val screen = ctx.screen ?: return
        if (!app.isViewer(ctx.context, screen)) return
        web = source()?.let(::webHost)
    }

    override fun onShelf(ctx: AppContext) {
        super.onShelf(ctx)
        ctx.root?.let { r -> app.shelfCells(r).forEach { shelfTitles += EcoViewer.titleKey(it.title) } }
    }

    override fun onViewer(ctx: AppContext) {
        val node = web?.takeIf { it.refresh() }
        if (node == null) {
            // 웹 화면을 아직 못 붙잡았거나 사라졌다(다시 만든 읽는 화면): 다음 내용 바뀜 알림에서 다시 붙잡는다
            web = null
            retryProgress(ctx, -1)
            return
        }
        val lookup = ctx.recorder.lookup
        val shown = EcoViewer.readWeb(node) { t ->
            val key = EcoViewer.titleKey(t)
            // 목록의 책도 부제까지 같은지 다시 본다(찾기는 부제를 떼고 맞춘다)
            key in shelfTitles || lookup.byTitle(t)?.let { b -> EcoViewer.titleKey(b.title) == key || EcoViewer.titleKey(b.seenTitle) == key } == true
        }
        logShown(ctx, shown)
        retryProgress(ctx, shown.progress)
        // 제목을 못 나눴으면 앞서 편 책이 아니다(그 책은 목록에 있어 늘 나뉜다): 그 진행률을 앞 책에 적지 않는다
        if (shown.title != null) recordShown(ctx, shown)
    }

    /** 알림의 요소 [n] 에서 위로 올라가며 가장 바깥 WebView(웹 화면 자체). 웹 화면을 품은 묶음의 알림이면 그 아래 WebView */
    private fun webHost(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var host: AccessibilityNodeInfo? = null
        var cur: AccessibilityNodeInfo? = n
        var steps = 0
        while (cur != null && steps++ < CLIMB) {
            if (cur.isWebView()) host = cur
            cur = cur.parent
        }
        return host ?: (0 until n.childCount).firstNotNullOfOrNull { i -> n.getChild(i)?.takeIf { it.isWebView() } }
    }

    private companion object {
        /** 웹 화면을 찾아 올라가는 단계(본문 깊은 곳의 알림에서도 닿게) */
        const val CLIMB = 60
    }
}
