package com.woody.booklauncher.readers

import android.content.ComponentName
import android.content.Context
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.woody.booklauncher.Seen
import com.woody.booklauncher.ShelfBook
import com.woody.booklauncher.Target
import com.woody.booklauncher.WatchLog
import com.woody.booklauncher.hasTitle
import com.woody.booklauncher.sameTitle
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Librera(오픈소스 내 파일 리더 foobnix/LibreraReader: EPUB·PDF·FB2·MOBI·만화 압축 파일 등). Play 무료판(광고) `com.foobnix.pdf.reader` 와
 * Pro 판 `com.foobnix.pro.pdf.reader`(Play 유료, GitHub 릴리스·F-Droid 판도 이 패키지)는 같은 코드라 화면·요소 이름·기록 파일이 같다.
 * 서재 `com.foobnix.ui2.MainTabs2`(라이브러리·폴더·최근·즐겨찾기·북마크 탭, 격자·목록 보기): 책 칸 `card_view`(설명 `도서 <저자> <제목> <확장자>`) 안에
 * 제목 `title1`·저자 `title2`·표지 `browserItemIcon`·진행률 `idPercentText`(읽지 않은 책은 빈 글자).
 * 읽는 화면은 `com.foobnix.pdf.search.activity.HorizontalViewActivity`(책 넘김, 기본)와 `org.ebookdroid.ui.viewer.VerticalViewActivity`(스크롤).
 * 본문은 직접 그린다(10.0 부터 EPUB 등은 화면 밖에 둔 웹 화면이 그린 그림. 웹 화면은 접근성에 드러나지 않는다). 가운데를 눌러 메뉴를 띄우면 위에 제목 `title`
 * (스크롤은 `bookName`, 설정에 따라 메뉴 없이도 보인다)·장 `chapter`, 아래에 쪽 `currentSeek`·`maxSeek`(`1`·`72`). 아래 상태 줄 `pagesCountIndicator`
 * (스크롤은 `currentPageIndex`)는 기본이 장 안의 쪽(`1장 – 2 ∕ 6`)이라 쓰지 않고, 사용자가 % 가 든 모양을 고르면(`12%  3 ∕ 72 (5)`) 그 % 를 쓴다.
 * 제목은 Librera 가 책 정보의 제목을 읽는 형식만 책 정보의 제목이고 PDF·XPS·TIFF·DjVu([NO_TITLE])는 파일 이름(확장자 포함)이다. 서재 칸은 그런 파일도
 * PDF 속 제목을 보여 읽는 화면과 제목이 다를 수 있어, 그 칸은 누른 칸으로 쓰지 않는다(파일 이름으로 들어온다). 서재가 멈췄을 때 그 칸이 이 앱의 책과 맞으면
 * (칸 제목이나 칸의 저자·제목이 책 제목과 같으면. 책 안 제목이 없으면 Librera 는 파일 이름을 손봐 보인다) 그 칸의 표지·진행률만 쓴다([fileTitleCells]).
 * 격자는 위 막대·아래 탭 줄 밑까지 이어져 그 막대에 걸린 칸은 표지를 자르지 않는다.
 * Librera 는 저장소의 `Librera/profile.<프로필>/device.<기기 모델>/`(모든 파일 액세스가 없으면 `Download/Librera/…`)에 책을 열 때 `app-Recent.json`
 * (연 파일 `internal-storage:/Download/a.epub` 과 때, 최근 탭을 감추면 적지 않는다)을, 쪽을 넘길 때마다 `app-Progress.json`(파일 이름 → 진행 비율 `p`)을 쓴다.
 * 그래서 책 파일 읽기를 허용했으면 메뉴 없이도 방금 연 책과 그 파일·진행률을 알고([dataBook]), 홈에서 그 파일을 바로 연다([open]). 판단과 기억은 [LibreraReader].
 * Play 무료판은 저장소 권한이 없어 기록을 `Download/Librera` 에 두지만, 책은 Librera 전용 폴더·폴더 허용(SAF)으로 읽어 기록의 경로를 BookLauncher 가 읽을 수 없다:
 * 그 책은 서재 칸·메뉴로만 안다([dataBook] 이 읽을 수 없는 파일은 거른다).
 * (Librera Pro 10.0.7·무료판 10.0.11, 2026-10-29 에뮬레이터에서 확인)
 */
open class LibreraApp(pkg: String) : ReaderApp(
    // 읽는 화면이 두 이름공간 아래에 있고 패키지와도 달라 이름 앞부분으로 가리지 않는다
    pkg = pkg, classPrefix = "",
    shelves = setOf("com.foobnix.ui2.MainTabs2"),
    viewers = setOf("com.foobnix.pdf.search.activity.HorizontalViewActivity", "org.ebookdroid.ui.viewer.VerticalViewActivity"),
    titles = listOf("title1"), thumbs = listOf("browserItemIcon"), authors = listOf("title2"), progress = "idPercentText", due = null,
    viewerProgress = listOf("pagesCountIndicator", "currentPageIndex"), viewerTitle = "title", viewerPages = "currentSeek" to "maxSeek",
    needsBookFiles = true,
) {
    override fun newAdapter(): ReaderAdapter = LibreraReader(this)

    /** 만화 압축 파일(CBZ·CBR)은 그 파일의 첫 그림, PDF 는 첫 쪽을 표지로(PDF 는 Librera 기록에 표지 그림이 없고 서재 칸은 제목이 다를 수 있다) */
    override val fileCovers get() = COVERS

    /** 홈에서 그 책의 파일을 연다(실행 화면은 파일 보기를 받지 않아 `OpenerActivity` 로). 파일을 모르거나 지워졌으면 앱을 연다 */
    override fun open(ctx: HomeContext, book: ShelfBook): OpenResult =
        ctx.bookFile(book)?.let { OpenResult.File(ComponentName(pkg, OPENER), it) } ?: OpenResult.Launch

    override val viewerTitleIds = listOf("title", "bookName")

    /** PDF 등은 메뉴 제목이 파일 이름이라 확장자를 뗀다 */
    override fun splitTitle(text: String): Pair<String, String> = withoutBookFile(text) to ""

    /**
     * Librera 가 책 정보의 제목을 읽는 형식의 칸만(칸 설명 끝의 확장자로 가린다). 진행률은 그 칸이 보일 때만.
     * 폴더 칸은 제목 이름이 달라(`text1`) 책이 아니다.
     */
    override fun readCell(cell: AccessibilityNodeInfo): Cell? = if (fileTitle(cell)) null else read(cell)

    /** 서재 칸들. 위 막대·아래 탭 줄에 걸린 칸은 표지를 자르지 않는다([unbarred]) */
    override fun shelfCells(root: AccessibilityNodeInfo): List<Cell> = unbarred(root, super.shelfCells(root))

    /**
     * 격자는 위 막대(`filterLayout`)·아래 탭 줄(`slidingTabs2`) 밑까지 이어져, 넘긴 서재의 첫 줄·끝 줄 칸은 표지가 막대에 가린다
     * (자르면 막대가 표지에 찍힌다): 그 칸들은 표지 자리를 비운다
     */
    private fun unbarred(root: AccessibilityNodeInfo, cells: List<Cell>): List<Cell> {
        val bars = BARS.flatMap { root.byId(pkg, it) }.map { it.bounds() }
        return cells.map { c -> if (c.thumb != null && bars.any { Rect.intersects(it, c.thumb) }) c.copy(thumb = null) else c }
    }

    /**
     * 서재에 보이는 파일 이름이 제목인 형식(PDF 등, [NO_TITLE])의 칸들. 누른 칸으로는 쓰지 않고, 칸 제목이 책 제목과 같을 때 표지·진행률만 쓴다
     * ([LibreraReader.onShelf]). 목록 보기에서 칸 옆에 있는 진행률은 `card_view` 밖이라 이 칸들에서는 모른다
     */
    fun fileTitleCells(root: AccessibilityNodeInfo): List<Cell> = unbarred(root, root.byId(pkg, CARD).filter(::fileTitle).mapNotNull(::read))

    /** 칸 설명 끝의 확장자가 파일 이름을 제목으로 쓰는 형식인가. 목록 보기의 칸은 `card_view` 와 그 옆 별·진행률을 묶은 것일 수 있어 설명은 안의 `card_view` 에서 찾는다 */
    private fun fileTitle(cell: AccessibilityNodeInfo): Boolean =
        find(cell, CARD)?.contentDescription?.let(::plain).orEmpty().substringAfterLast(' ').lowercase() in NO_TITLE

    /** 칸 하나. 진행률은 그 칸이 보일 때만(목록 보기는 읽지 않은 책이면 감추기만 한다) */
    private fun read(cell: AccessibilityNodeInfo): Cell? {
        val c = super.readCell(cell) ?: return null
        val shown = progress?.let { find(cell, it) }?.isVisibleToUser == true
        return c.copy(progress = if (shown) c.progress else -1)
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
     * Librera 기록에서 읽는 화면의 책: 메뉴 제목이 보이면 그 제목의 책(최근 것부터 [TITLE_LOOKS] 권), 안 보이면 이 읽는 화면이 열린 뒤([since])에 연 가장 최근 책.
     * 메뉴 없이는 [notBefore](앞서 책을 편 것으로 적은 때) 전에 연 것도 믿지 않는다(앞 책을 닫고 곧 다른 칸을 누르면 새 책을 적기 전까지는 맨 앞이 앞 책이다).
     * 제목·저자는 EPUB 은 책 파일의 책 정보, 그 밖은 파일 이름(확장자 뗌). 책 파일 읽기를 허용하지 않았으면 null
     */
    fun dataBook(context: Context, info: ViewerInfo, since: Long, notBefore: Long = 0): FileBook? {
        if (!BookFiles.canRead(context)) return null
        // 읽을 수 없는 파일(무료판의 Librera 전용 폴더·지운 파일)은 거른다. 파일 확인·책 정보 읽기는 화면 스레드라 필요한 만큼만
        val list = (LibreraData.recent(profileFile(LibreraData.RECENT) ?: return null) ?: return null).asSequence().filter { File(it.path).isFile }
        val title = info.title
        val e = if (title != null) list.take(TITLE_LOOKS).firstOrNull { e -> sameTitle(fileInfo(e.path).first, title) || sameTitle(stem(e.path), title) }
            else list.firstOrNull()?.takeIf { it.time >= since - OPEN_SLACK_MS && it.time >= notBefore }
        e ?: return null
        val (name, author) = fileInfo(e.path)
        return FileBook(name, author, e.path, dataProgress(context, e.path))
    }

    /** 읽는 동안 쪽을 넘길 때마다 적는 진행 비율(반올림한 %). 모르면 -1 */
    fun dataProgress(context: Context, path: String): Int {
        if (!BookFiles.canRead(context)) return -1
        return LibreraData.progress(profileFile(LibreraData.PROGRESS) ?: return -1, File(path).name)
    }

    /**
     * 책 파일의 제목·저자: EPUB 은 책 정보(Librera 도 그것을 제목으로 보인다), 그 밖은 파일 이름(확장자 뗌)과 빈 저자.
     * EPUB 은 파일마다 한 번 읽어 기억한다(메뉴 제목이 기록에 없으면 읽는 화면 알림마다 묻는다)
     */
    private fun fileInfo(path: String): Pair<String, String> {
        val f = File(path)
        return LibreraData.epubInfo.getOrPut("${f.path}@${f.lastModified()}@${f.length()}") {
            BookFiles.epub(path)?.takeIf { it.title.isNotBlank() }?.let { it.title to it.author } ?: (stem(path) to "")
        }
    }

    private fun stem(path: String) = withoutBookFile(File(path).name)

    /**
     * 프로필의 [name] 파일: 모든 파일 액세스가 있으면 `Librera/`, 없으면 `Download/Librera/` 아래 `profile.<프로필>/device.<기기 모델>/`.
     * 프로필·기기 폴더가 여럿이면(프로필을 바꿨거나 다른 기기에서 옮겨 온 폴더, 무료판·Pro 판이 함께 쓴다) 가장 최근에 쓴 것. 없으면 null
     */
    private fun profileFile(name: String): File? =
        listOf(File(BookFiles.storageRoot, "Librera"), File(BookFiles.storageRoot, "Download/Librera"))
            .flatMap { it.listFiles { f -> f.isDirectory && f.name.startsWith("profile.") }.orEmpty().toList() }
            .flatMap { it.listFiles { f -> f.isDirectory && f.name.startsWith("device.") }.orEmpty().toList() }
            .map { File(it, name) }.filter { it.isFile }.maxByOrNull { it.lastModified() }

    companion object {
        private val COVERS = BookFiles.withPdf(BookFiles.WITH_COMICS)
        /** 파일을 받는 화면(실행 화면 `MainTabs2` 는 파일 보기를 받지 않는다) */
        private const val OPENER = "com.foobnix.OpenerActivity"
        /** 책 칸(설명 `도서 저자 제목 확장자` 가 붙는 요소) */
        private const val CARD = "card_view"
        /** 서재 격자 위에 겹치는 위 막대(검색·정렬)·아래 탭 줄 */
        private val BARS = listOf("filterLayout", "slidingTabs2")
        /** Librera 가 책 정보의 제목을 읽지 않고 파일 이름을 제목으로 쓰는 형식(`BookType.hasTitle` 이 거짓. 10.0 부터 CBZ·CBR 은 참) */
        private val NO_TITLE = setOf("pdf", "xps", "tiff", "tif", "djvu")
        /** 읽는 화면이 열렸다는 알림보다 연 때가 조금 앞설 수 있어 봐준다 */
        private const val OPEN_SLACK_MS = 3_000L
        /** 메뉴 제목으로 찾을 때 볼 최근 책 수(EPUB 은 책 정보를 열어 본다) */
        private const val TITLE_LOOKS = 10
    }
}

/** Play 무료판(광고) */
object Librera : LibreraApp("com.foobnix.pdf.reader")

/** Pro 판(Play 유료, GitHub·F-Droid 판도 같은 패키지) */
object LibreraPro : LibreraApp("com.foobnix.pro.pdf.reader")

/**
 * Librera 의 판단과 기억: 서재는 바탕에 파일 이름이 제목인 형식의 칸을 더하고([onShelf]), 읽는 화면은 Librera 기록으로 안 책이 먼저 — 그 파일로 넣은 책,
 * 없으면 파일 없이 넣은 같은 제목의 책, 둘 다 없으면 새 책(책 정보의 제목이 같은 다른 파일과 섞지 않게) — 없으면 메뉴 제목.
 * 화면에 진행률이 없으면 Librera 기록의 진행률.
 */
class LibreraReader(private val librera: LibreraApp) : ScreenReader(librera) {
    /**
     * 이 읽는 화면이 열릴 때 앞서 책을 편 것으로 적은 때(서재에서 누른 칸이면 누른 때)나 제목을 못 읽은 칸(PDF 등)을 누른 때 가운데 늦은 것:
     * 그 전에 연 Librera 기록은 앞 책의 것이다(알림이 늦게 오는 만큼 [NOT_BEFORE_SLACK_MS] 봐준다)
     */
    private var notBefore = 0L
    /** 서재에서 책 칸으로 알아보지 못한 것을 누른 때 */
    private var noCellClickAt = 0L
    private val ask = DataAsk { context, info, since -> librera.dataBook(context, info, since, notBefore) }

    override fun onEnterViewer(ctx: AppContext, from: String?, again: Boolean) {
        ask.reset()
        super.onEnterViewer(ctx, from, again)
        notBefore = (maxOf(ctx.lastOpenedAt ?: 0L, noCellClickAt) - NOT_BEFORE_SLACK_MS).coerceAtLeast(0L)
    }

    override fun onClickNoCell(ctx: AppContext) {
        noCellClickAt = ctx.now
    }

    /**
     * 서재가 멈췄다: 바탕처럼 칸들의 진행률·반납일·표지를 적되, 파일 이름이 제목인 형식(PDF 등)의 칸도 이 앱의 책과 맞으면 더한다
     * (그 책은 책 파일에서 표지를 꺼낼 수 없어 이것이 표지다. 맞지 않는 칸·목록에 없는 책은 잘라 두지 않는다: PDF 속 제목으로 새 책이 생기지 않게).
     * 맞추기는 [fileTitleBook].
     * 표지는 한 번에 찍어야 해서(찍기 간격) 바탕을 부르지 않고 같은 일을 한다(서재 창 기억은 [ReaderApp.forgetsClickOnOtherTap] 용이라 쓰지 않는다).
     */
    override fun onShelf(ctx: AppContext) {
        val root = ctx.root ?: return ctx.noWindow()
        val seen = readShelf(root)
        ctx.log(WatchLog.Kind.SHELF, "${seen.size}")
        val named = librera.fileTitleCells(root).mapNotNull { c -> fileTitleBook(ctx, c)?.let { c.copy(title = it.title) } }
        val spots = mutableListOf<CoverSpot>()
        shelfStatuses(ctx, seen + named, spots)
        ctx.capture(spots)
    }

    /**
     * 읽는 화면: Librera 기록으로 안 책이면 그 파일로 책을 가르고, 메뉴 없이 기록으로 방금 연 책을 알았으면 읽는 화면이 열린 때부터 그 책으로 센다
     * (메뉴를 띄운 뒤에야 알아본 책은 그때부터. 제목 못 읽은 칸으로 들어왔으면 그 때부터). 편 책 기록의 책이 그 책이면 다시 적지 않는다.
     * 그 파일은 편 책 기록의 책에 적는다. 진행률은 화면의 것, 없으면 Librera 기록의 것(메뉴 없이 읽는 동안).
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
        if (progress >= 0) return recorder.status(book.id, progress)
        val p = lookup.filePath(book.id)?.let { librera.dataProgress(ctx.context, it) }?.takeIf { it >= 0 } ?: return
        ctx.log(WatchLog.Kind.PROGRESS, "$p", book.title)
        recorder.status(book.id, p)
    }

    /**
     * 파일 이름이 제목인 형식의 칸 [c] 에 맞는 이 앱의 책: 칸 제목, 없으면 칸의 저자·제목을 이은 것이 책 제목(파일 이름)과 같은 책(정리한 제목으로).
     * Librera 는 책 안 제목이 없으면 파일 이름을 손봐 칸에 보여(첫 글자 대문자, `-`·`_` 는 빈칸) 정리한 제목은 같고, `저자 - 제목` 꼴 이름은
     * 저자·제목으로 나누고(`Kim Author - Czech Pages` → `Czech Pages`·`Kim Author`), 끝의 `(저자)` 도 저자로 뗀다(그래서 제목·저자 순서로도 본다)
     */
    private fun fileTitleBook(ctx: AppContext, c: Cell): ShelfBook? {
        val lookup = ctx.recorder.lookup
        return listOf(c.title, "${c.author} ${c.title}", "${c.title} ${c.author}").distinct()
            .firstNotNullOfOrNull { t -> lookup.byTitle(t)?.takeIf { it.app?.substringBefore('/') == ctx.pkg } }
    }

    companion object {
        /** 앞서 책을 편 때보다 이만큼 앞에 연 기록까지는 믿는다(누름 알림이 늦게 온다) */
        private const val NOT_BEFORE_SLACK_MS = 300L
    }
}

/** Librera 의 `app-Recent.json`·`app-Progress.json`. 같은 파일은 바뀌었을 때만 다시 읽는다 */
internal object LibreraData {
    const val RECENT = "app-Recent.json"
    const val PROGRESS = "app-Progress.json"

    /** 최근에 연 책 한 권: 파일 경로(실제 경로로 바꾼 것)와 연 때(ms) */
    class Recent(val path: String, val time: Long)

    /** [file] 의 책들, 최근 것부터. 못 읽으면 null */
    fun recent(file: File): List<Recent>? {
        val stamp = "${file.path}@${file.lastModified()}@${file.length()}"
        cache?.takeIf { it.first == stamp }?.let { return it.second }
        return parse(runCatching { file.readText() }.getOrNull() ?: return null, BookFiles.storageRoot.path)?.also { cache = stamp to it }
    }

    /**
     * `[{"path":"internal-storage:/Download/a.epub","time":…}, …]`(Librera 는 다시 연 책을 끝으로 옮긴다) → 최근 것부터.
     * `internal-storage:` 는 저장소 [root] 로 바꾸고, 파일이 아닌 것(추가한 저장소의 `content:` 주소)은 뺀다. 못 읽으면 null
     */
    fun parse(json: String, root: String): List<Recent>? = runCatching {
        val a = JSONArray(json)
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val path = o.optString("path").replace(INTERNAL, root).takeIf { it.startsWith("/") } ?: return@mapNotNull null
            Recent(path, o.optLong("time"))
        }.sortedByDescending { it.time }
    }.getOrNull()

    @Volatile private var cache: Pair<String, List<Recent>>? = null

    /** `app-Progress.json`([file])에서 파일 이름 [name] 의 진행률(비율 `p` 를 반올림한 %). 없거나 못 읽으면 -1 */
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

    /** 읽어 본 책 파일(`경로@고친 때@길이`)의 제목·저자([LibreraApp] 의 `fileInfo`) */
    val epubInfo = ConcurrentHashMap<String, Pair<String, String>>()

    /** Librera 가 저장소 뿌리 대신 적는 앞부분(`MyPath.INTERNAL_PREFIX`) */
    private const val INTERNAL = "internal-storage:"
}
