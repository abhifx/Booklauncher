package com.woody.booklauncher.readers

import android.content.ComponentName
import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.woody.booklauncher.Seen
import com.woody.booklauncher.ShelfBook
import com.woody.booklauncher.ShelfRecorder
import com.woody.booklauncher.Target
import com.woody.booklauncher.WatchLog
import com.woody.booklauncher.hasTitle
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 우주(우주뷰어, 이노스페이스원 `com.inno.woojoo`. 오픈소스 Librera Reader 를 고친 내 파일 리더: EPUB·PDF·만화 압축 파일 등): 서재는 `ui2.MainTabs2`
 * (전체·즐겨찾기·SD카드 탭, 쪽으로 넘긴다). 책 칸 `card_view` 에 제목 `title1`·표지 `browserItemIcon`·진행률 `idPercentText`, 목록 보기에는 저자 `title2` 도
 * 있다. 격자 보기는 저자가 칸의 설명(`도서 저자 제목 epub`)에만 있다. 목록 보기의 진행률 칸은 읽지 않은 책이면 감추기만 하고 글자는 다시 쓴 칸의 것이 남는다.
 * 읽는 화면은 `pdf.search.activity.HorizontalViewActivity`(책 넘김, 기본)와 `org.ebookdroid.ui.viewer.VerticalViewActivity`(스크롤)이고
 * 본문은 직접 그려 글자가 드러나지 않는다. 가운데를 눌러 메뉴를 띄우면 위에 제목 `title`(스크롤은 `bookName`), 아래에 쪽 `currentPage`·`maxPage`·`(1%)` `pagePercent`.
 * 메뉴가 없을 때는 우주 설정의 상태 표시줄을 켰을 때만 아래에 쪽 `pagesCountIndicator`(`13 / 96`)가 보인다(기본은 꺼짐). 그래서 읽는 동안의 진행률은
 * 우주가 쪽을 넘길 때마다 적는 `Woojoo/profile.<프로필>/device.<기기>/app-Progress.json`(파일 이름 → 비율 `p`)에서도 읽는다([dataProgress]).
 * 제목은 우주가 책 정보의 제목을 읽는 형식(EPUB·FB2·MOBI·TXT 등)만 책 정보의 제목이고, PDF·DjVu·만화 압축 파일 등([NO_TITLE])은 파일 이름(확장자 포함)이다.
 * 서재 칸은 그런 파일도 PDF 속 제목을 보여 읽는 화면과 제목이 달라, 그 칸은 책으로 보지 않는다(파일 이름으로 들어온다). 그 책의 표지는 서재에서 자를 수 없고
 * 책 파일에서도 꺼낼 수 없어(EPUB 만) 우주 기록의 표지 그림으로 붙인다([dataCover]).
 * 우주는 책을 열 때와 닫을 때 저장소의 `Woojoo/RecentBooks/RecentBooks.json` 에 그 책(메뉴의 제목·저자·진행률·파일 경로·때, 최근 10권)을 적는다
 * (`SpaceUtils.saveCurrentBookToRecentBooks`, 닫을 때는 `onStop`). 그래서 책 파일 읽기를 허용했으면 메뉴 없이도 방금 연 책과 그 파일·진행률(지난번에 닫은 곳)을 알고
 * ([dataBook]), 홈에서 그 파일을 바로 연다(실행 화면은 파일 보기를 받지 않아 `OpenerActivity` 로, [open]). 판단과 기억은 [WoojooReader]. 닫을 때 적는 진행률은 홈의 `onResume` 보다
 * 늦어 홈으로 돌아올 때 맞추지 않는다(서재 칸의 진행률·메뉴의 `%`·다음에 열 때 적힌 것으로 안다).
 * (우주 1.0.22, 2026-10-23 에뮬레이터에서 확인)
 */
object Woojoo : ReaderApp(
    // 읽는 화면이 두 패키지 아래에 있어 이름 앞부분으로 가리지 않는다(액티비티인지는 따로 본다)
    pkg = "com.inno.woojoo", classPrefix = "",
    shelves = setOf("com.inno.ui2.MainTabs2"),
    viewers = setOf("com.inno.pdf.search.activity.HorizontalViewActivity", "org.ebookdroid.ui.viewer.VerticalViewActivity"),
    titles = listOf("title1"), thumbs = listOf("browserItemIcon"), authors = listOf("title2"), progress = "idPercentText", due = null,
    viewerProgress = listOf("pagePercent", "pagesCountIndicator"), viewerTitle = "title", viewerPages = "currentPage" to "maxPage",
    needsBookFiles = true,
) {
    override fun newAdapter(): ReaderAdapter = WoojooReader()

    /** 만화(CBZ·CBR, CB7 은 열지 못함)는 우주 기록의 그림 대신 그 파일의 첫 그림을 표지로 */
    override val fileCovers get() = BookFiles.WITH_COMICS

    /** 홈에서 그 책의 파일을 연다(실행 화면은 파일 보기를 받지 않아 `OpenerActivity` 로). 파일을 모르거나 지워졌으면 앱을 연다 */
    override fun open(ctx: HomeContext, book: ShelfBook): OpenResult =
        ctx.bookFile(book)?.let { OpenResult.File(ComponentName(pkg, OPENER), it) } ?: OpenResult.Launch

    override val viewerTitleIds = listOf("title", "bookName")

    /** PDF 등은 메뉴 제목이 파일 이름이라 확장자를 뗀다 */
    override fun splitTitle(text: String): Pair<String, String> = withoutBookFile(text) to ""

    /**
     * 우주가 책 정보의 제목을 읽는 형식의 칸만(칸 설명 끝의 확장자로 가린다). 진행률은 그 칸이 보일 때만, 격자 보기의 저자는 칸 설명에서.
     * 폴더 칸은 이름이 `text1` 이라 제목이 없어 책이 아니다.
     */
    override fun readCell(cell: AccessibilityNodeInfo): Cell? {
        // 목록 보기의 칸은 `card_view` 와 그 옆 별·진행률을 묶은 것이라 설명은 안의 `card_view` 에 있다
        val desc = find(cell, CARD)?.contentDescription?.let(::plain).orEmpty()
        if (desc.substringAfterLast(' ').lowercase() in NO_TITLE) return null
        val c = super.readCell(cell) ?: return null
        val shown = progress?.let { find(cell, it) }?.isVisibleToUser == true
        return c.copy(author = c.author.ifEmpty { descAuthor(desc, c.title) }, progress = if (shown) c.progress else -1)
    }

    /** [cell] 과 그 안에서 이름이 [id] 인 첫 요소(누른 칸에서는 이름으로 찾기가 안 되어 직접 훑는다) */
    private fun find(cell: AccessibilityNodeInfo, id: String): AccessibilityNodeInfo? {
        val full = "$pkg:id/$id"
        fun walk(n: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
            if (n.viewIdResourceName == full) return n
            if (depth < WALK_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }?.let { return it }
            return null
        }
        return walk(cell, 0)
    }

    /**
     * 누른 글자로만 칸을 만들지 않는다(진행률이 같이 오는 칸 글자에는 PDF 칸도 있다). 방금 읽은 서재 칸의 제목과 같은 글자는 그 칸으로.
     */
    override fun clickedCell(source: AccessibilityNodeInfo?, text: List<CharSequence>, windows: AppWindows, last: List<Cell>?): Cell? =
        super.clickedCell(source, text.filter { percent(it) < 0 }, windows, last)

    /**
     * RecentBooks.json 에서: 메뉴 제목이 보이면 그 제목의 책(가장 최근 것), 안 보이면 이 읽는 화면이 열린 뒤([since])에 적힌 가장 최근 책.
     * 메뉴 없이는 [notBefore](앞서 책을 편 것으로 적은 때) 전에 적힌 것도 믿지 않는다(우주는 책을 닫을 때도 적어, 닫고 곧 다른 칸을 누르면
     * 맨 앞이 닫은 책이다). 책 파일 읽기를 허용하지 않았으면 null
     */
    fun dataBook(context: Context, info: ViewerInfo, since: Long, notBefore: Long = 0): FileBook? {
        if (!BookFiles.canRead(context)) return null
        val list = WoojooData.recent(recentFile()) ?: return null
        val e = if (info.title != null) list.firstOrNull { withoutBookFile(it.title) == info.title }
            else list.firstOrNull()?.takeIf { it.time >= since - OPEN_SLACK_MS && it.time >= notBefore }
        return e?.let { FileBook(withoutBookFile(it.title).ifBlank { withoutBookFile(File(it.path).name) }, it.author, it.path, it.progress) }
    }

    /** 읽는 동안 쪽을 넘길 때마다 적는 진행 비율(서재 칸처럼 반올림한 %) */
    fun dataProgress(context: Context, path: String): Int {
        if (!BookFiles.canRead(context)) return -1
        return WoojooData.progress(progressFile() ?: return -1, File(path).name)
    }

    /**
     * 우주 기록의 그 책 표지 그림(`thumb_path`, 우주가 첫 쪽을 300×450 으로 그려 둔 PNG. 책을 열 때마다 새로 그리고 앞 그림은 지운다).
     * PDF·만화처럼 책 파일에서 표지를 꺼낼 수 없는 책에 쓴다
     */
    fun dataCover(context: Context, path: String): ByteArray? {
        if (!BookFiles.canRead(context)) return null
        val thumb = WoojooData.recent(recentFile())?.firstOrNull { it.path == path }?.thumb?.takeIf { it.isNotEmpty() } ?: return null
        return File(thumb).takeIf { it.isFile }?.let(BookFiles::imageCover)
    }

    /** 우주의 저장소 폴더(우주 설정에서 바꿀 수 있지만 기본 `Woojoo` 만 본다) */
    private fun recentFile() = File(BookFiles.storageRoot, "Woojoo/RecentBooks/RecentBooks.json")

    /**
     * `Woojoo/profile.<프로필>/device.<기기 모델>/app-Progress.json`: 프로필·기기 폴더가 여럿이면(프로필을 바꿨거나 다른 기기에서 옮겨 온 폴더) 가장 최근에 쓴 것
     * (지금 프로필·이 기기의 것이 읽는 동안 바뀐다). 없으면 null
     */
    private fun progressFile(): File? =
        File(BookFiles.storageRoot, "Woojoo").listFiles { f -> f.isDirectory && f.name.startsWith("profile.") }.orEmpty()
            .flatMap { it.listFiles { f -> f.isDirectory && f.name.startsWith("device.") }.orEmpty().toList() }
            .map { File(it, "app-Progress.json") }.filter { it.isFile }.maxByOrNull { it.lastModified() }

    /** 격자 칸 설명 `도서 저자 제목 epub` 에서 저자(제목 앞, 첫 낱말 뒤). 모르면 빈 글자 */
    internal fun descAuthor(desc: String, title: String): String {
        val body = desc.substringBeforeLast(' ', "").takeIf { it.endsWith(" $title") }?.removeSuffix(title) ?: return ""
        return body.substringAfter(' ', "").trim()
    }

    /** 파일을 받는 화면(실행 화면 `MainTabs2` 는 파일 보기를 받지 않는다) */
    private const val OPENER = "com.inno.OpenerActivity"
    /** 책 칸(설명 `도서 저자 제목 확장자` 가 붙는 요소) */
    private const val CARD = "card_view"
    /** 우주가 책 정보의 제목을 읽지 않고 파일 이름을 제목으로 쓰는 형식(Librera `BookType.hasTitle` 이 거짓) */
    private val NO_TITLE = setOf("pdf", "xps", "tiff", "tif", "cbz", "cbr", "djvu")
    /** 읽는 화면이 열렸다는 알림보다 적힌 때가 조금 앞설 수 있어 봐준다 */
    private const val OPEN_SLACK_MS = 3_000L
}

/**
 * 우주의 판단과 기억: 서재는 바탕 그대로([Woojoo.readCell]), 읽는 화면은 우주 기록(RecentBooks)으로 안 책이 먼저 — 그 파일로 넣은 책,
 * 없으면 파일 없이 넣은 같은 제목의 책, 둘 다 없으면 새 책(파일 안의 제목이 같은 다른 파일과 섞지 않게) — 없으면 메뉴 제목.
 * 화면에 진행률이 없으면 우주 기록의 진행률, 표지가 없으면 우주 기록의 표지 그림.
 */
class WoojooReader : ScreenReader(Woojoo) {
    /**
     * 이 읽는 화면이 열릴 때 앞서 책을 편 것으로 적은 때(서재에서 누른 칸이면 누른 때)나 제목을 못 읽은 칸(PDF 등)을 누른 때 가운데 늦은 것:
     * 그 전에 적힌 우주 기록은 앞 책의 것이다(알림이 늦게 오는 만큼 [NOT_BEFORE_SLACK_MS] 봐준다)
     */
    private var notBefore = 0L
    /** 서재에서 책 칸으로 알아보지 못한 것을 누른 때 */
    private var noCellClickAt = 0L
    private val ask = DataAsk { context, info, since -> Woojoo.dataBook(context, info, since, notBefore) }
    /** 표지 그림을 뒤에서 꺼내는 중인 책 파일들(같은 파일을 겹쳐 꺼내지 않게) */
    private val dataCovers = mutableSetOf<String>()

    override fun onEnterViewer(ctx: AppContext, from: String?, again: Boolean) {
        ask.reset()
        super.onEnterViewer(ctx, from, again)
        notBefore = (maxOf(ctx.lastOpenedAt ?: 0L, noCellClickAt) - NOT_BEFORE_SLACK_MS).coerceAtLeast(0L)
    }

    override fun onClickNoCell(ctx: AppContext) {
        noCellClickAt = ctx.now
    }

    /**
     * 읽는 화면: 우주 기록으로 안 책이면 그 파일로 책을 가르고, 메뉴 없이 기록으로 방금 연 책을 알았으면 읽는 화면이 열린 때부터 그 책으로 센다
     * (메뉴를 띄운 뒤에야 알아본 책은 그때부터. 제목 못 읽은 칸으로 들어왔으면 그 때부터). 편 책 기록의 책이 그 책이면 다시 적지 않는다.
     * 그 파일은 편 책 기록의 책에 적는다. 진행률은 화면의 것, 없으면 우주 기록의 것(메뉴·상태 줄 없이 읽는 동안).
     */
    override fun onViewer(ctx: AppContext) {
        val shown = readShown(ctx) ?: return
        val recorder = ctx.recorder
        val lookup = recorder.lookup
        val file = ask.ask(ctx, shown)
        val info = file?.let { ViewerInfo(lookup.knownTitle(it.path, it.title), it.author, shown.progress.takeIf { p -> p >= 0 } ?: it.progress) } ?: shown
        val progress = info.progress
        logShown(ctx, info)
        retryProgress(ctx, progress)
        val bookId = file?.let { f -> lookup.byFile(f.path) ?: lookup.withoutFile(info.title.orEmpty()) }
        info.title?.let { title ->
            val recorded = recorder.recordedBook(ctx.pkg)
            val opened = recorded?.let { if (file != null) it.id == bookId else it.hasTitle(title) } != true
            // 데이터로 안 책의 파일은 그 책에(편 책 기록에서 다시 찾으면 같은 때 적힌 다른 줄이 나올 수 있다)
            val book = if (opened) {
                val at = ctx.unknownOpenAt ?: ctx.viewerOpenedAt?.takeIf { file != null && shown.title == null }
                ctx.opened(Seen(title, info.author, progress), at, if (file != null) Target.Exact(bookId) else Target.ByTitle)
            } else recorded
            if (file != null && book != null) recorder.setFile(book.id, file.path)
            if (opened) return
        }
        val book = recorder.recordedBook(ctx.pkg) ?: return
        val path = lookup.filePath(book.id)
        path?.let { dataCover(ctx, book.id, it) }
        if (progress >= 0) return recorder.status(book.id, progress)
        val p = path?.let { Woojoo.dataProgress(ctx.context, it) }?.takeIf { it >= 0 } ?: return
        ctx.log(WatchLog.Kind.PROGRESS, "$p", book.title)
        recorder.status(book.id, p)
    }

    /**
     * 표지가 없는 책 [id] 에 우주가 남긴 책 파일 [path] 의 표지 그림을 붙인다(PDF·표지 없는 EPUB 등). 표지를 꺼낼 수 있는 EPUB·그림, 만화 묶음([fileCovers])은 그릴 때 책 파일에서 꺼내므로 하지 않는다.
     * 아직 없으면(우주가 책을 연 뒤 그림을 늦게 남긴다) 다음에 읽는 화면을 읽을 때 다시 본다.
     */
    private fun dataCover(ctx: AppContext, id: String, path: String) {
        if (ctx.recorder.lookup.hasCover(id) || ShelfRecorder.coverFromFile(ctx.context, path, app.fileCovers) || !dataCovers.add(path)) return
        ctx.inBackground({ Woojoo.dataCover(ctx.context, path) }) done@{ result ->
            dataCovers -= path
            ctx.recorder.cover(id, result.getOrNull() ?: return@done)
        }
    }

    companion object {
        /** 앞서 책을 편 때보다 이만큼 앞에 적힌 우주 기록까지는 믿는다(누름 알림이 늦게 온다. 닫은 책의 기록은 보통 1초 넘게 앞이다) */
        private const val NOT_BEFORE_SLACK_MS = 300L
    }
}

/** 우주의 `RecentBooks.json`·`app-Progress.json`. 같은 파일은 바뀌었을 때만 다시 읽는다 */
internal object WoojooData {
    /** 최근 책 한 권: 메뉴의 제목(PDF 등은 파일 이름)·저자·진행률(%, 모르면 -1)·파일 경로·적은 때(ms)·표지 그림 경로 */
    class Recent(val title: String, val author: String, val progress: Int, val path: String, val time: Long, val thumb: String)

    /** [file] 의 책들, 최근 것부터. 파일이 없으면 빈 목록, 못 읽으면 null */
    fun recent(file: File): List<Recent>? {
        if (!file.isFile) return emptyList()
        val stamp = "${file.path}@${file.lastModified()}@${file.length()}"
        cache?.takeIf { it.first == stamp }?.let { return it.second }
        return parse(runCatching { file.readText() }.getOrNull() ?: return null)?.also { cache = stamp to it }
    }

    fun parse(json: String): List<Recent>? = runCatching {
        val a = JSONArray(json)
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val path = o.optString("file_path").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            Recent(o.optString("title").trim(), o.optString("author").trim(), percent(o.optString("progress")), path, o.optLong("time"), o.optString("thumb_path"))
        }.sortedByDescending { it.time }
    }.getOrNull()

    @Volatile private var cache: Pair<String, List<Recent>>? = null

    /** `app-Progress.json`([file])에서 파일 이름 [name] 의 진행률(비율 `p` 를 반올림한 %, 우주 서재 칸과 같게). 없거나 못 읽으면 -1 */
    fun progress(file: File, name: String): Int {
        val stamp = "${file.path}@${file.lastModified()}@${file.length()}"
        val all = progressCache?.takeIf { it.first == stamp }?.second
            ?: (parseProgress(runCatching { file.readText() }.getOrNull() ?: return -1) ?: return -1).also { progressCache = stamp to it }
        return all[name] ?: -1
    }

    /** 파일 이름 → 진행률(%). 못 읽으면 null */
    fun parseProgress(json: String): Map<String, Int>? = runCatching {
        val o = JSONObject(json)
        o.keys().asSequence().mapNotNull { k ->
            val p = o.optJSONObject(k)?.optDouble("p", Double.NaN)?.takeIf { it in 0.0..1.0 } ?: return@mapNotNull null
            k to Math.round(p * 100).toInt()
        }.toMap()
    }.getOrNull()

    @Volatile private var progressCache: Pair<String, Map<String, Int>>? = null
}
