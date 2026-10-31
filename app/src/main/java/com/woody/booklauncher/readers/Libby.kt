package com.woody.booklauncher.readers

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs

/**
 * Libby(OverDrive, 휴대폰 판 하나): 액티비티가 `nautilus.ui.Activity_Main` 하나뿐이고 화면은 모두 웹 화면이다. 서재·검색은
 * `clientHolder` 안의 웹 화면, 책을 열면 그 자리에 읽는 화면 웹 화면 `bifocalHolder` 가 뜬다(닫으면 다시 `clientHolder`).
 * 그래서 읽는 화면은 액티비티가 아니라 이 요소가 보이는 동안이다([viewerHolder]).
 * 읽는 화면에는 늘 숨은 안내 글자 `페이지 모드를 사용 중입니다. 제목, 저자 .`(메뉴를 띄우면 `탐색 모드를…`)가 있어 메뉴 없이도 제목을 안다.
 * 메뉴를 띄우면 아래에 쪽 `페이지 1`(단추)·`총 59 페이지`(같은 줄 오른쪽)가 보인다. 웹 화면의 요소 이름은 HTML id(앞에 패키지 없음, 대부분 없거나 매번 바뀐다)라 쓰지 않는다.
 * 서재(`서가` 탭)의 대출한 책 칸에서 진행률·반납일·표지를 읽는다([shelfCells]). 누른 칸은 대개 알 수 없어(칸 안의 `책 열기`·`대출 관리`를
 * 가리려면 앱 언어의 글자를 봐야 한다. 제목 글자를 누른 것만 칸으로 맞춘다) 편 책은 읽는 화면의 제목으로 안다.
 */
object Libby : ReaderApp(
    pkg = "com.overdrive.mobile.android.libby", classPrefix = "com.overdrive",
    shelves = setOf(MAIN),
    viewers = setOf("$MAIN#$HOLDER"),
    titles = emptyList(), thumbs = emptyList(), authors = emptyList(), progress = null, due = null,
    viewerProgress = emptyList(), viewerHolder = HOLDER,
    eagerCovers = true, openOnViewer = true, clickByPlace = false,
) {
    /** 읽는 화면은 메뉴 없이도 안내 글자로 제목을 알려 준다 */
    override val tellsTitle get() = true

    /** 서재 칸은 요소 이름 없이 자리로 찾고 표지 자리도 셈한 것이라, 찍은 뒤에도 같은 자리에 있을 때만 자른다 */
    override val cellsByPosition get() = true

    /**
     * 서재(`서가` 첫 화면의 '최근 대출 자료', `대출` 목록)의 대출한 책 칸. 한 목록 아래 형제로 칸마다
     * 이름표 `책: 제목, 저자별`(안에 제목 글자) · 상태 단추(`도서 상태: 대출됨, 100% 다운로드 완료`) · 할 일 묶음(`책 열기`·`대출 관리`·
     * `만료일: 14일`·`0%를 읽음`, 영어는 `Due In 14 Days`·`You Have Read 2 Percent`)이 이어진다. 이름표가 제목 글자를 품고 그 뒤에 `, ` 가 오면 칸의 시작이고, 그 아래 할 일 묶음에서 진행률·반납일을 읽는다.
     * 검색 결과 칸도 모양이 같지만 진행률이 없어(`대출`·`샘플 읽기`) 빌린 책이 아니므로 뺀다.
     * 표지는 요소로 드러나지 않고(배경 그림) 할 일 묶음 왼쪽, 이름표 왼쪽 끝부터 할 일 묶음 왼쪽까지·할 일 묶음 높이에 그려진다.
     * 아래 탭 줄(HTML id `footer-nav-…`)과 그 위 '지금 읽고 있는 자료' 띠(탭 줄 바로 위에서 끝나는 단추들)가 목록 위에 떠 있다.
     */
    override fun shelfCells(root: AccessibilityNodeInfo): List<Cell> {
        val found = mutableListOf<Cell>()
        val footer = mutableListOf<Rect>()
        val buttons = mutableListOf<Rect>()
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (n.viewIdResourceName == "$pkg:id/$HOLDER") return
            if (n.viewIdResourceName?.startsWith(FOOTER) == true) footer += n.bounds()
            if (n.isClickable) buttons += n.bounds()
            val kids = (0 until n.childCount).mapNotNull { n.getChild(it) }
            kids.forEachIndexed { i, k ->
                val (title, author) = label(k) ?: return@forEachIndexed
                val top = k.bounds()
                val actions = kids.drop(i + 1).takeWhile { label(it) == null }.firstOrNull { it.childCount > 0 && it.bounds().top >= top.bottom - ROW_PX }
                    ?: return@forEachIndexed
                val texts = mutableListOf<String>()
                fun collect(c: AccessibilityNodeInfo, d: Int) {
                    c.text?.let(::plain)?.takeIf { it.isNotEmpty() }?.let { texts += it }
                    if (d < WALK_DEPTH) for (j in 0 until c.childCount) c.getChild(j)?.let { collect(it, d + 1) }
                }
                collect(actions, 0)
                val progress = texts.firstNotNullOfOrNull { readPercent(it).takeIf { p -> p >= 0 } } ?: return@forEachIndexed
                // `책 열기`·`대출 관리` 같은 글자는 반납일로 읽히지 않는다(-1). 숫자 없는 `Due Today` 도 읽도록 거르지 않는다.
                val due = texts.filter { readPercent(it) < 0 }.firstNotNullOfOrNull { dueDate(it).takeIf { d -> d >= 0 } } ?: -1
                val a = actions.bounds()
                // 표지 오른쪽·아래에 그림자(폭의 3% 남짓)가 붙어 그만큼 덜어 낸다.
                val shadow = (a.left - top.left) * SHADOW_PCT / 100
                val cover = Rect(top.left, a.top, a.left - shadow, a.bottom - shadow)
                found += Cell(title, author, progress, due, cover, Rect(top.left, top.top, a.right, a.bottom))
            }
            if (depth < DEEP_WALK_DEPTH) for (k in kids) walk(k, depth + 1)
        }
        walk(root, 0)
        // 화면 아래의 탭 줄과 그 바로 위에 붙은 '지금 읽고 있는 자료' 띠가 칸 위에 떠 있어, 그 밑으로 들어간 표지는 자르지 않는다.
        val tabs = footer.minByOrNull { it.top } ?: return found
        val bar = buttons.filter { it.top < tabs.top && it.bottom in tabs.top - tabs.height() / 2..tabs.top }.minOfOrNull { it.top }
        val covered = minOf(tabs.top, bar ?: tabs.top)
        return found.map { c -> if ((c.thumb?.bottom ?: 0) > covered) c.copy(thumb = null) else c }
    }

    /** 이름표(`책: 제목, 저자별`)면 제목·저자. 이름표 아래(두 단계 안)의 글자가 제목이고 이름표 글자에서 그 뒤 `, ` 다음이 저자다. */
    private fun label(n: AccessibilityNodeInfo): Pair<String, String>? {
        val text = n.text?.let(::plain)?.takeIf { it.isNotEmpty() } ?: return null
        var title: String? = null
        fun find(c: AccessibilityNodeInfo, d: Int) {
            for (j in 0 until c.childCount) {
                val k = c.getChild(j) ?: continue
                k.text?.let(::plain)?.takeIf { it.isNotEmpty() && "$it, " in text }?.let { if (title == null) title = it }
                if (d < 2) find(k, d + 1)
            }
        }
        find(n, 0)
        val t = title ?: return null
        return t to labelAuthor(text.substringAfter("$t, "))
    }

    /** 서재 칸의 진행률: `2%를 읽음`, 영어는 `You Have Read 2 Percent`. 아니면 -1 */
    private fun readPercent(text: String): Int =
        percent(text).takeIf { it >= 0 } ?: PERCENT_WORD.find(text)?.groupValues?.get(1)?.toInt()?.coerceIn(0, 100) ?: -1

    private val PERCENT_WORD = Regex("""(\d{1,3})\s*percent\b""", RegexOption.IGNORE_CASE)

    /** 이름표의 저자 부분 `Man-Sik Chae Kyung-Ja Chun별`(영어 `Book: 제목, by Man-Sik Chae`) → 저자 */
    private fun labelAuthor(text: String): String = text.trim().removeSuffix("별").removePrefix("by ").trim()

    /** 같은 줄로 보는 위쪽 차이(px) */
    private const val ROW_PX = 8
    /** 서재 표지 자리에서 덜어 낼 그림자(표지 폭의 %) */
    private const val SHADOW_PCT = 4
    /** 화면 안으로 보는 넘침(px. 웹 화면은 읽는 화면보다 2px 크다) */
    private const val EDGE_PX = 8

    /** 안내 글자 `<모드 문장>. <제목>, <저자> .` */
    private val ANNOUNCE = Regex("""^[^.]+\.\s+(.+)\s+\.$""")

    /** 서재 화면의 문서 제목 앞(`Libby - 검색 결과`). 책을 불러오는 동안의 읽는 화면도 이렇게 보일 수 있어 책 제목으로 보지 않는다. */
    private const val APP_NAME = "Libby"

    /** 숫자 하나뿐인 글자(`페이지 1`, `총 59 페이지`) */
    private val ONE_NUMBER = Regex("""^\D*(\d+)\D*$""")

    /**
     * 읽는 화면의 글자들. 웹 화면이 책 본문도 내놓으므로(쪽들을 옆으로 이어 붙인 화면보다 훨씬 넓은 띠, 예: 가로 -5364~32175)
     * 화면 밖으로 나가는 묶음에는 들어가지 않는다. 메뉴·안내 글자는 화면 안에 있다(안내 글자는 크기 0, 왼쪽 -3).
     * 옛 웹 화면(WebView 91, 에뮬레이터)은 메뉴를 몇 번 열고 닫으면 안쪽 요소를 내놓지 않는다. 그래도 웹 화면 요소의 글자(문서 제목 = 책 제목)는
     * 남아 있어 안내 글자가 없으면 그것을 제목으로 쓴다(새 웹 화면은 이 글자가 비어 있다).
     */
    override fun readViewer(windows: AppWindows): ViewerInfo {
        val texts = mutableListOf<Pair<String, Rect>>()
        var docTitle: String? = null
        fun walk(n: AccessibilityNodeInfo, screen: Rect, depth: Int) {
            val b = n.bounds()
            if (b.left < screen.left - EDGE_PX || b.right > screen.right + EDGE_PX || b.top < screen.top - EDGE_PX || b.bottom > screen.bottom + EDGE_PX) return
            val t = n.text?.let(::plain)?.takeIf { it.isNotEmpty() }
            if (n.isWebView()) { if (docTitle == null) docTitle = t?.takeUnless { it.startsWith(APP_NAME) } }
            else if (t != null) texts += t to b
            if (depth < DEEP_WALK_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, screen, depth + 1) }
        }
        windows.all.flatMap { it.byId(pkg, HOLDER) }.forEach { walk(it, it.bounds(), 0) }
        val (title, author) = texts.firstNotNullOfOrNull { (t, _) -> ANNOUNCE.find(t)?.groupValues?.get(1)?.let(::splitAnnounce) }
            ?: (docTitle to "")
        return ViewerInfo(title, author, pages(texts))
    }

    /** `Pride and Prejudice, Jane Austen` → 제목·저자(마지막 쉼표에서 나눈다. 제목에 쉼표가 있어도 저자는 끝에 온다) */
    private fun splitAnnounce(text: String): Pair<String, String>? {
        val i = text.lastIndexOf(", ")
        val title = (if (i > 0) text.substring(0, i) else text).trim().ifEmpty { return null }
        return title to (if (i > 0) text.substring(i + 2).trim() else "")
    }

    /**
     * 메뉴의 쪽: 숫자 하나뿐인 글자 둘이 같은 줄에 있으면 왼쪽이 지금 쪽, 오른쪽이 전체 쪽(`페이지 1` · `총 59 페이지`).
     * 화면 밖(크기 0)의 글자는 보지 않는다. 메뉴가 없으면 -1.
     */
    private fun pages(texts: List<Pair<String, Rect>>): Int {
        val numbers = texts.filter { !it.second.isEmpty }.mapNotNull { (t, r) -> ONE_NUMBER.find(t)?.groupValues?.get(1)?.let { it to r } }
        for ((cur, a) in numbers) {
            val (total, _) = numbers.firstOrNull { (_, b) -> b !== a && abs(b.top - a.top) < ROW_PX && b.left >= a.right } ?: continue
            return pagePercent(cur, total, roundUp = false)
        }
        return -1
    }
}

private const val MAIN = "com.overdrive.mobile.android.nautilus.ui.Activity_Main"
private const val HOLDER = "bifocalHolder"
/** 아래 탭 줄의 탭들(`footer-nav-search`·`footer-nav-shelf` 등, 웹 화면의 HTML id 라 앞에 패키지가 없다) */
private const val FOOTER = "footer-nav-"
