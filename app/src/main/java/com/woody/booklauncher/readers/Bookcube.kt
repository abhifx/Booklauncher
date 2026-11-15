package com.woody.booklauncher.readers

import android.graphics.BitmapFactory
import android.view.accessibility.AccessibilityNodeInfo
import com.woody.booklauncher.BookSearch
import com.woody.booklauncher.Seen
import com.woody.booklauncher.ShelfBook
import com.woody.booklauncher.normTitle
import com.woody.booklauncher.sameTitle
import java.net.URLEncoder

/**
 * 북큐브(전자책 서점·웹소설·웹툰, 공공·대학 전자도서관 대출도 이 앱의 `도서관` 탭으로 받는다): 내서재(`BookShelfActivity`, 목록·표지형 두 보기).
 * 목록 칸에 제목 `row_text`·저자 `row_text2`(`메이브 빈치 | 문학동네`, 저자 | 출판사)·표지 `list_img`·진행 원 안의 읽은 % `down_progress_text`·
 * 대여 기간 `row_text3`(`2026-11-20 23:59 만료`, 받기 전 대여는 `다운로드 시점으로부터 7일`)·도서관 이름 `row_text4`.
 * 표지형 칸은 표지 그림뿐이고 제목 글자가 없어(`grid_click_layout`) 그렇게 연 책은 읽는 화면 메뉴의 제목으로만 안다.
 * 받지 않은 칸·기간이 지난 칸은 표지 위에 어둡게 한 막(`down_back_color`)과 받기·만료 그림(`list_img_down_icon`)을 겹쳐 자르지 않는다([COVER_OVERLAYS]).
 * 받지 않은 칸을 누르면 받는 화면(`ViewNextBookActivity`)을 거쳐 읽는 화면이 열리고, 기간이 지난 칸은 지울지 묻는 창만 떠 읽는 화면이 열려야 편 것으로 본다.
 * 읽는 화면(EPUB `Epub30ViewerActivity`·PDF `PdfViewerActivityGL`, 옛 PDF 뷰어 `PdfViewerActivity`)은 화면 캡처를 막는다. EPUB 은 아래 줄 왼쪽·오른쪽(`addInfoLeft`·`addInfoRight`)에
 * 사용자가 고른 장 이름·쪽 정보(`7/37(19%)`)·시각을 보이고, PDF 읽는 화면에는 글자가 없다(`pdf_view` 하나라 진행률은 메뉴와 서재 칸의 %로). 메뉴는 따로 액티비티(`EpubMenuActivity`·`PdfMenuActivityGL`)라 읽는 화면으로 보고, 위에 제목 `bookTitle`, 아래 `progressRatio`(`7/37(19%)`, PDF `2/124(2%)`).
 * 본문 그림을 크게 보는 화면(`ImageViewerActivity`)도 읽는 중이다. 오디오북(`AudioActivity`)은 읽는 화면이 아니다.
 * 웹툰은 내서재로 받지 않고 서점(내서재와 같은 액티비티 안의 웹 화면)에서 바로 보며 뷰어에 화(`1화`)만 있어 넣지 않는다(사용자가 정함).
 * (3.0.43, 2026-11-14 CremaPebble_A11_Play 에뮬레이터에서 무료 EPUB·도서관 대출 EPUB·PDF·웹소설로 확인)
 */
object Bookcube : ReaderApp(
    pkg = "com.bookcube.bookplayer", classPrefix = "com.bookcube",
    shelves = setOf("com.bookcube.ui.BookShelfActivity"),
    viewers = setOf(
        "com.bookcube.ui.Epub30ViewerActivity",
        "com.bookcube.ui.EpubMenuActivity",
        "com.bookcube.ui.PdfViewerActivity",
        "com.bookcube.ui.PdfViewerActivityGL",
        "com.bookcube.ui.PdfMenuActivity",
        "com.bookcube.ui.PdfMenuActivityGL",
        "com.bookcube.ui.ImageViewerActivity",
    ),
    titles = listOf("row_text"), thumbs = listOf("list_img"), coverOverlays = COVER_OVERLAYS, authors = listOf("row_text2"),
    progress = "down_progress_text", due = "row_text3",
    viewerProgress = listOf("addInfoRight", "addInfoLeft", "progressRatio"), viewerTitle = "bookTitle", openOnViewer = true,
) {
    /**
     * 목록 칸: 저자 칸은 `저자 | 출판사` 라 앞만, 미리보기 책은 서재에만 제목 앞에 `[미리보기]` 를 붙여(메뉴 제목에는 없다) 뗀다.
     * 표지 왼쪽 위에는 늘 딱지(파일 종류 `EPUB`·`PDF`, 받고 읽지 않은 책은 `NEW`, `list_icon`)가 겹친다. 딱지와 표지(`list_img`)는 다른 요소지만
     * 화면을 찍어 자르므로 딱지째 찍힌다. 그래서 책이 들어오면 북큐브 공개 검색의 원본 표지로 바꾸고([BookcubeReader.afterOpened]), 서재에서 자른 표지는
     * 인터넷이 안 될 때 남는 것이라 딱지째 둔다(사용자가 정함).
     * 연재(웹소설·웹툰)는 작품 칸(`포식으로 레벨업하는 군주님`, `아이박슨 | 연필`, 진행 원 대신 `총 1화 ›`)을 누르면 같은 화면 안에 화 칸
     * (`포식으로 레벨업하는 군주님 - 1화`, 둘째 줄은 저자 대신 `1화`, 진행 원은 그 화의 %)이 뜬다. 화마다 한 권(사용자가 정함, 진행률은 그 화의 %)이고
     * 제목은 메뉴 제목과 같은 모양(`포식으로 레벨업하는 군주님 1화`)으로 둔다. 저자는 칸에 없어 앞서 본 작품 칸에서([BookcubeReader]).
     */
    override fun readCell(cell: AccessibilityNodeInfo): Cell? {
        val c = super.readCell(cell) ?: return null
        CELL_EPISODE.matchEntire(c.title.trim())?.let { m ->
            return c.copy(title = "${m.groupValues[1].trim()} ${m.groupValues[2]}화", author = "")
        }
        val title = c.title.removePrefix(PREVIEW).trim().ifEmpty { c.title }
        val author = c.author.substringBefore('|').trim()
        return c.copy(title = title, author = author)
    }

    override fun newAdapter(): ReaderAdapter = BookcubeReader(this)

    /** 연재 한 화의 제목(`작품 1화`)이면 작품 제목, 아니면 null */
    internal fun workOf(title: String): String? = EPISODE.matchEntire(title.trim())?.groupValues?.get(1)?.trim()

    /** 서재의 화 칸 제목 `작품 - 1화` */
    private val CELL_EPISODE = Regex("""(.+?)\s+-\s+(\d+)\s*화""")
    /** 한 화의 제목 `작품 1화`(메뉴, 서재의 화 칸을 고친 것) */
    private val EPISODE = Regex("""(.+?)\s+(\d+)\s*화""")

    /**
     * 북큐브 공개 검색 주소(로그인 없음, 제목만 보낸다). 연재의 한 화(`작품 1화`)는 웹소설에서 작품 제목으로, 그 밖은 서점(책)에서 찾는다
     */
    internal fun searchUrl(title: String): String {
        val work = workOf(title)
        val query = URLEncoder.encode(work ?: title, "UTF-8")
        return "$SEARCH?site=${if (work != null) "novel" else "book"}&searchStr=$query"
    }

    /**
     * 검색 결과 HTML 에서 [title]·[author] 의 표지 원본 주소(딱지 없는 270px). 결과 칸마다 표지 그림(대체 글이 제목)과 글자 줄(제목·저자·출판사,
     * 웹소설은 `list-author`)이 있다. 제목이 같고([sameTitle]) 저자를 알면 그 저자가 든 칸만, 하나도 없으면 null
     */
    internal fun coverUrl(html: String, title: String, author: String): String? {
        val want = workOf(title) ?: title
        val who = author.substringBefore(',').filter { it.isLetterOrDigit() }
        for (item in html.split("<li").drop(1)) {
            val m = RESULT_COVER.find(item) ?: continue
            val (url, alt) = m.destructured
            val name = unescape(alt)
            if (!sameTitle(name, want)) continue
            // 저자는 제목 줄을 뺀 글자 줄에서(제목에 저자 이름이 들어 있어도 맞는 것으로 보지 않게)
            val texts = RESULT_TEXT.findAll(item).map { unescape(it.groupValues[1]) }.filter { it != name }.map { it.filter { c -> c.isLetterOrDigit() } }
            if (who.isNotEmpty() && texts.none { who in it }) continue
            return url.replace(SMALL_SIZE, ".com/270/")
        }
        return null
    }

    private fun unescape(s: String) =
        s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&").trim()

    private const val SEARCH = "https://www.bookcube.com/book/m/search.asp"
    /** 결과 칸의 표지 그림: 서점 `bookimg.bookcube.com/270/2409/240900763.jpg`, 웹소설 `serialimg.bookcube.com/200/sf6719.jpg` */
    private val RESULT_COVER = Regex("""<img\s+src="(https://(?:book|serial)img\.bookcube\.com/[^"]+\.jpg)"\s+alt="([^"]*)"""")
    private val RESULT_TEXT = Regex("""<(?:p|h4)[^>]*>([^<]+)</(?:p|h4)>""")
    /** 작은 그림 크기(웹소설 결과는 200) → 270 */
    private val SMALL_SIZE = Regex("""\.com/(150|200)/""")

    private const val PREVIEW = "[미리보기]"
}

/**
 * 북큐브의 판단: 서재 칸을 거치지 않고 읽는 화면이 열리면(서점에서 받아 바로 보기, 메뉴의 `다음 권`·`다음 화` 등) 다른 책이라 메뉴 제목을 볼 때까지
 * 책 모름으로 둔다(앞서 편 책에 시간이 붙지 않게. 메뉴 제목을 읽으면 열린 때부터 그 책으로).
 * 연재의 한 화는 칸에 저자가 없어, 서재에서 본 작품 칸의 저자를 붙인다(서비스가 붙어 있는 동안 기억).
 */
private class BookcubeReader(app: ReaderApp) : ScreenReader(app) {
    /** 서재에서 본 작품마다 저자(정리한 제목 → 저자) */
    private val authors = HashMap<String, String>()

    override fun onShelf(ctx: AppContext) {
        super.onShelf(ctx)
        val root = ctx.root ?: return
        for (c in readShelf(root)) if (c.author.isNotEmpty() && authors.size < MAX_AUTHORS) authors[normTitle(c.title)] = c.author
    }

    override fun onEnterViewer(ctx: AppContext, from: String?, again: Boolean) {
        val cell = takeClicked(ctx)
        when {
            cell != null -> openCell(ctx, cell)
            // 제목 없는 칸을 눌러 바탕 흐름이 이미 책 모름으로 적었으면 다시 적지 않는다
            !again && from in OPENERS && ctx.unknownOpenAt == null -> {
                ctx.unknownOpenAt = ctx.now
                ctx.unknown(ctx.now)
            }
        }
    }

    override fun openCell(ctx: AppContext, cell: Cell, at: Long?) {
        ctx.opened(Seen(cell.title, cell.author.ifEmpty { workAuthor(cell.title) }, cell.progress, cell.due), at)
    }

    override fun onViewer(ctx: AppContext) {
        val shown = readShown(ctx) ?: return
        logShown(ctx, shown)
        retryProgress(ctx, shown.progress)
        val title = shown.title
        recordShown(ctx, if (title == null || shown.author.isNotEmpty()) shown else ViewerInfo(title, workAuthor(title), shown.progress))
    }

    /** 이번 연결에서 표지를 찾았거나 찾는 중인 책(같은 책을 겹쳐 찾지 않게. 실패하면 빼서 다음에 열 때 다시) */
    private val searched = HashSet<String>()

    /**
     * 책이 처음 들어오면 뒤에서 한 번 북큐브 공개 검색으로 딱지 없는 표지 원본을 받아 서재에서 잘라 둔 표지와 바꾼다([Bookcube.coverUrl]).
     * 받았거나 검색에 없으면 다시 찾지 않는다(사용자가 고른 표지를 덮지 않게). 인터넷이 안 되면 잘라 둔 표지 그대로 두고 다음에 열 때 다시 찾는다
     */
    override fun afterOpened(ctx: AppContext, book: ShelfBook) {
        val pref = COVER_PREF + book.id
        if (ctx.prefs.contains(pref) || !searched.add(book.id)) return
        val title = book.seenTitle.ifBlank { book.title }
        val author = book.author
        ctx.inBackground({
            val url = Bookcube.coverUrl(String(BookSearch.download(Bookcube.searchUrl(title))), title, author)
            // 그림이 아닌 응답(오류 쪽 등)으로 표지를 덮지 않게 크기를 읽을 수 있는 것만
            url?.let { BookSearch.download(it) }?.takeIf { isImage(it) }
        }) { result ->
            // 인터넷이 안 되는 등 실패하면 다음에 그 책을 열 때 다시 찾는다(그동안은 잘라 둔 표지)
            if (result.isFailure) { searched.remove(book.id); return@inBackground }
            result.getOrNull()?.let { ctx.recorder.replaceCover(book.id, it) }
            ctx.prefs.edit().putBoolean(pref, true).apply()
        }
    }

    private fun isImage(bytes: ByteArray): Boolean {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        return o.outWidth > 0 && o.outHeight > 0
    }

    /** 한 화의 제목이면 서재에서 본 그 작품의 저자, 모르면 "" */
    private fun workAuthor(title: String): String = Bookcube.workOf(title)?.let { authors[normTitle(it)] }.orEmpty()

    companion object {
        /** 서재 칸 없이 책을 여는 화면(받기·불러오기) */
        private val OPENERS = setOf(
            "com.bookcube.ui.ViewNextBookActivity", "com.bookcube.ui.DownloadActivity", "com.bookcube.ui.LoadActivity",
        )
        private const val MAX_AUTHORS = 500
        /** 표지 원본을 찾아 본 책(prefs, 뒤에 책 id) */
        private const val COVER_PREF = "bookcube_cover_"
    }
}

/** 표지 위에 겹치는 받기·만료 표시(어둡게 한 막과 그림) */
private val COVER_OVERLAYS = listOf("down_back_color", "list_img_down_icon")
