package com.woody.pebbledesk.readers

import android.content.Context
import android.database.Cursor
import android.net.Uri
import com.woody.pebbledesk.R
import com.woody.pebbledesk.Seen
import com.woody.pebbledesk.ShelfBook
import com.woody.pebbledesk.Target
import com.woody.pebbledesk.OpenSteps
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * 네오리더(BOOX 기본 리더 `com.onyx.kreader`, 시스템 앱. 내 파일·BOOX 서점 책을 연다): 서재가 없고 실행 화면도 없다. 책은 BOOX 런처
 * (`com.onyx`, 앱 목록의 ONYX Launcher)의 서재나 파일 관리자에서 연다. 그래서 책의 앱은 파일을 받는 화면 `ui.ReaderHomeActivity`([fileEntry])로
 * 두고 홈에서 누르면 그 책의 파일을 네오리더로 바로 연다([open]). 파일을 모르는 책은 넣지 않는다. 판단과 기억은 [NeoReaderReader].
 * 읽는 화면은 탭 액티비티 `ui.ReaderTab1Activity`~`ReaderTab4Activity`(책 하나에 탭 하나, 탭마다 다른 프로세스)이고 본문은 직접 그리는
 * 화면(SurfaceView)이라 글자가 드러나지 않는다. 아래 상태 줄의 진행 칸 `page_name`(설정에 따라 `12/300`·`4.0%`, 쪽 이름이 있으면
 * `iv(12/300)`, 쪽을 세는 동안은 `...`)과, 메뉴를 띄웠을 때 아래 진행 칸의 제목 `tv_book_title`·쪽 `page_num` 을 읽는다.
 * 메뉴 제목은 BOOX 설정 `책 제목 사용` 이 켜져 있으면 책 정보의 제목, 아니면 파일 이름(확장자 포함)이다.
 * 어떤 책인지는 네오리더가 권한 없이 내놓은 데이터 창구 `Metadata`(연 책마다 한 줄: 제목·저자·파일 이름·경로. 제목은 파일 안의 제목이라
 * 파일 이름과 다를 수 있다)로 안다([dataBook]). 줄을 저장할 때마다 `updatedAt` 이 바뀌는데, 책을 열 때 저장하므로(새 책은 줄을 만들고, 아는 책은
 * 파일 안의 책 정보를 다시 저장한다) 메뉴 없이도 방금 연 책을 안다. `lastAccess` 는 아는 책을 다시 열어도 닫을 때에야 바뀌어 쓰지 않는다.
 * 책을 둘 이상 열면 메뉴 위에 탭 줄(`doc_bar`)도 보이지만 지금 탭이 어느 것인지 드러나지 않아 쓰지 않는다.
 * (BOOX Palma2 펌웨어 4.2(2026-08)의 네오리더 38994. 에뮬레이터에서 요소 이름과 자동 추가를 확인했고, 2026-10-17 BOOX 실기기(사용자, Page 또는 Go6)에서도 됐다.
 * `updatedAt` 은 Go6 펌웨어 4.2(2026-05)의 네오리더 코드와 에뮬레이터에서 확인(docs/READER_APPS.md))
 */
object NeoReader : ReaderApp(
    pkg = "com.onyx.kreader", classPrefix = "com.onyx.kreader",
    shelves = emptySet(),
    viewers = (1..4).map { "com.onyx.kreader.ui.ReaderTab${it}Activity" }.toSet(),
    titles = emptyList(), thumbs = emptyList(), authors = emptyList(), progress = null, due = null,
    viewerProgress = listOf("page_name", "page_num"), viewerTitle = "tv_book_title",
    needsBookFiles = true,
) {
    /** 실행 화면이 없어 앱 목록 항목을 따로 둔다: 파일을 받는 화면 */
    override val fileEntry = FileViewer("com.onyx.kreader.ui.ReaderHomeActivity", R.string.app_neoreader)

    /** 같은 모르는 탭에 다시 오면(설정 화면에 갔다 옴 등) 처음 열린 때를 둔다(탭별 판단은 [NeoReaderReader]) */
    override val keepsUnknownOnReenter get() = true

    /** 만화(CBZ·CBR)도 연다(CB7 은 열지 못함): 표지는 그 파일의 첫 그림 */
    override val fileCovers get() = BookFiles.WITH_COMICS

    override fun newAdapter(): ReaderAdapter = NeoReaderReader()

    /** 그 책의 파일을 연다. 모르거나 없으면, 또는 열기에 실패하면 앱을 열지 않고(실행 화면이 없다) 뒤에서 다시 찾는다 */
    override fun open(ctx: HomeContext, book: ShelfBook): OpenResult {
        if (ctx.component.className != fileEntry.activity) return OpenResult.Launch
        return ctx.bookFile(book)?.let { OpenResult.File(ctx.component, it, onFail = Fallback.FIND_FILE) } ?: OpenResult.FindFile
    }

    override fun splitTitle(text: String): Pair<String, String> = withoutBookFile(text) to ""

    /**
     * 메뉴 제목이 보이면 그것과 제목이나 파일 이름(확장자 뗌)이 같은 책(가장 최근에 저장한 것), 안 보이면 가장 최근에 저장한 책이
     * 이 읽는 화면이 열린 뒤([since])에 저장됐을 때만(그 전에 저장한 것은 앞 탭의 책일 수 있다. 탭을 앞으로 가져와도 저장해 그 탭의 책이 나온다). 제목이 없는 책(PDF 등)은 파일 이름으로.
     */
    fun dataBook(context: Context, info: ViewerInfo, since: Long): FileBook? {
        val menu = info.title
        val row = if (menu != null) matchingRows(withTitles(context, listOf(menu)), listOf(menu)).firstOrNull()
            else query(context, "updatedAt IS NOT NULL", emptyList(), limit = 1)?.firstOrNull()?.takeIf { it.updatedAt >= since - OPEN_SLACK_MS }
        return row?.let { FileBook(it.title.ifBlank { withoutBookFile(it.name) }, it.authors, it.path) }
    }

    /** 파일이 없는 것(책 파일 읽기가 켜져 있을 때만 안다)은 뺀다 */
    override fun bookFiles(context: Context, titles: Collection<String>): List<String> {
        val canRead = BookFiles.canRead(context)
        return matchingRows(withTitles(context, titles), titles).map { it.path }.distinct().filter { !canRead || File(it).isFile }
    }

    /** 책 정보 한 줄 */
    internal class Meta(val title: String, val authors: String, val name: String, val path: String, val updatedAt: Long)

    /** [rows] 가운데 제목이나 파일 이름(확장자 뗌)이 [titles] 중 하나인 줄들(순서 그대로) */
    internal fun matchingRows(rows: List<Meta>, titles: Collection<String>): List<Meta> =
        rows.filter { it.title.isNotEmpty() && it.title in titles || withoutBookFile(it.name) in titles }

    /** 제목이 [titles] 중 하나이거나 파일 이름이 그것으로 시작하는 줄들(저장한 때 늦은 것부터. 정확히 맞는지는 [matchingRows]). 읽지 못하면 빈 목록 */
    private fun withTitles(context: Context, titles: Collection<String>): List<Meta> {
        val wanted = titles.filter { it.isNotEmpty() }.distinct()
        if (wanted.isEmpty()) return emptyList()
        val where = wanted.joinToString(" OR ") { "(title = ? OR name LIKE ? ESCAPE '\\')" }
        val args = wanted.flatMap { listOf(it, it.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + ".%") }
        return query(context, where, args).orEmpty()
    }

    /** 저장한 때가 늦은 것부터 [limit]줄. 창구가 없거나 읽지 못하면 null */
    private fun query(context: Context, where: String, args: List<String>, limit: Int = Int.MAX_VALUE): List<Meta>? = runCatching {
        context.contentResolver.query(METADATA, META_COLUMNS, where, args.toTypedArray(), "updatedAt DESC")?.use { c ->
            ArrayList<Meta>().apply { while (size < limit && c.moveToNext()) meta(c)?.let(::add) }
        }
    }.getOrNull()

    private fun meta(c: Cursor): Meta? {
        val path = c.getString(3) ?: return null
        return Meta(c.getString(0).orEmpty().trim(), c.getString(1).orEmpty().trim(), c.getString(2).orEmpty(), path, c.getLong(4))
    }

    private val METADATA = Uri.parse("content://com.onyx.content.database.ContentProvider/Metadata")
    private val META_COLUMNS = arrayOf("title", "authors", "name", "nativeAbsolutePath", "updatedAt")
    /** 읽는 화면이 열렸다는 알림보다 저장한 때가 조금 앞설 수 있어 봐준다 */
    private const val OPEN_SLACK_MS = 3_000L
}

/**
 * 네오리더의 판단과 기억: 탭마다 책이 다르다. 탭이 앞에 오면 그 탭으로 마지막으로 안 책(편 책 기록과 다르면 편 것으로), 모르는 탭이면 책 모름.
 * 읽는 화면에서는 `Metadata`(메뉴 제목마다·탭이 앞에 온 뒤 메뉴 없이 6번, [DataAsk])로 안 책을 그 파일로 가른다 — 그 파일로 넣은 책,
 * 없으면 파일 없이 넣은 같은 제목의 책(1.5.11 까지 메뉴 제목만으로 넣은 책), 둘 다 없으면 새 책. 파일을 모르면 메뉴 제목으로는 넣지 않고
 * (홈에서 열 수 없다) 진행률만 편 책 기록의 책에 적고 조금 뒤 다시 본다. 책을 편 것으로 적을 때 지금 탭 ↔ 그 책을 적어 둔다.
 */
class NeoReaderReader : ScreenReader(NeoReader) {
    /** 메뉴 없이 데이터에 보인 떠난 탭의 책은 찾은 것으로 치지 않는다(이 탭의 책을 더 묻는다) */
    private val ask = DataAsk { context, info, since ->
        NeoReader.dataBook(context, info, since)?.takeUnless { info.title == null && leavingPath != null && it.path == leavingPath }
    }
    /** 읽는 화면에서 그 책의 파일을 아직 모를 때 다시 읽어 볼 남은 횟수(탭이 앞에 올 때마다 다시 센다) */
    private var fileRetries = 0
    /** 책 모름 시각을 적은 탭 */
    private var unknownTab: String? = null
    /** 지금 탭으로 마지막으로 안 책이 읽고 있는 책 목록에 없을 때(뺀 책·완독한 책) 그 id: 그 탭을 앞으로 가져와 데이터에 다시 보여도 되살리지 않는다 */
    private var leftTabBook: String? = null
    /** 탭이 앞에 오기 전에 편 책(떠난 탭의 책)의 파일: 메뉴 없이 데이터에 보이면 떠나며 저장한 것이라 이 탭의 책으로 보지 않는다 */
    private var leavingPath: String? = null

    override fun onEnterViewer(ctx: AppContext, from: String?, again: Boolean) {
        ask.reset()
        fileRetries = FILE_RETRIES
        super.onEnterViewer(ctx, from, again)
        ctx.screen?.takeIf { app.isViewer(ctx.context, it) }?.let { enterTab(ctx, it, ctx.now, again) }
        // 파일을 받는 화면을 거쳐 왔으면 그 탭을 앞으로 가져온 것이 아니라 파일을 연 것이다(완독한 책·방금 떠난 책을 다시 열면 그 책)
        if (from == NeoReader.fileEntry.activity) {
            leftTabBook = null
            leavingPath = null
        }
    }

    /**
     * 탭 [cls] 가 앞에 왔다. 그 탭으로 마지막으로 안 책이 지금 편 책과 다르면 그 책을 편 것으로 본다(탭을 오가며 읽으면 진행률·읽은 시간이
     * 앞 탭의 책에 붙지 않게). 모르는 탭이면 앞서 편 책과 다를 수 있어 '책 모름'을 적고, 메뉴나 데이터로 알면 탭이 열린 때부터 그 책으로 센다.
     */
    private fun enterTab(ctx: AppContext, cls: String, now: Long, again: Boolean) {
        // 그 탭의 책이 읽고 있는 책 목록에 있을 때만(빼거나 지운 책을 되살리지 않게)
        val remembered = tabBook(ctx, cls)
        val book = remembered?.let { ctx.recorder.lookup.get(it) }
        leftTabBook = remembered.takeIf { book == null }
        // 같은 읽는 화면의 알림이 한 번 더 온 것이면(그사이 이 탭의 책을 적었다) 떠난 탭의 책을 그대로 둔다
        if (!again) leavingPath = ctx.recorder.openedId(ctx.pkg)?.takeIf { it != book?.id }?.let(ctx.recorder.lookup::filePath)
        if (book == null) {
            // 같은 모르는 탭에 다시 오면(같은 날) 처음 열린 때를 둔다(다음 날 같은 탭에 연 책에 어제 시각이 붙지 않게)
            if (unknownTab != cls || ctx.unknownOpenAt?.let { dayOf(it) == dayOf(now) } != true) {
                ctx.unknownOpenAt = now
                unknownTab = cls
                ctx.unknown(now)
            }
        } else if (ctx.recorder.recordedBook(ctx.pkg)?.id != book.id) {
            // 읽는 화면이 열린 때로(데이터로 새 책을 알면 같은 때로 적어, 늦게 적은 탭 책이 새 책을 덮지 않게)
            ctx.opened(Seen(book.title), ctx.viewerOpenedAt ?: now, Target.Exact(book.id), steps = tabSteps(ctx))
        }
    }

    private fun dayOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

    override fun onViewer(ctx: AppContext) {
        val shown = readShown(ctx) ?: return
        val recorder = ctx.recorder
        val lookup = recorder.lookup
        val file = ask.ask(ctx, shown)
        // 파일을 모르는 메뉴 제목은 쓰지 않는다(그 책으로 넣으면 홈에서 열 수 없다)
        val info = file?.let { ViewerInfo(lookup.knownTitle(it.path, it.title), it.author, shown.progress) } ?: ViewerInfo(null, "", shown.progress)
        val progress = info.progress
        logShown(ctx, info)
        // 데이터에 아직 안 적혔을 수 있어(책을 여는 동안 적는다) 조금 뒤 다시 본다
        val retryFile = file == null && fileRetries.also { fileRetries = it - 1 } > 0
        if ((progress < 0 && ctx.viewerRetry()) || retryFile) ctx.viewerAgain(VIEWER_RETRY_MS)
        if (file != null && info.title != null) {
            val bookId = lookup.byFile(file.path) ?: lookup.withoutFile(info.title)
            // 뺀 책·완독한 책의 탭을 앞으로 가져왔을 뿐이면(메뉴 없이 데이터로만 안 책) 되살리지 않는다
            if (shown.title == null && bookId != null && bookId == leftTabBook && lookup.get(bookId) == null) return
            // 편 책 기록의 책이 그 책이면 다시 적지 않는다. 메뉴 없이 데이터로 방금 연 책을 알았으면 탭이 열린 때부터
            if (recorder.recordedBook(ctx.pkg)?.let { it.id == bookId } != true) {
                val at = ctx.unknownOpenAt ?: ctx.viewerOpenedAt?.takeIf { shown.title == null }
                val book = ctx.opened(Seen(info.title, info.author, progress), at, Target.Exact(bookId), steps = tabSteps(ctx))
                recorder.setFile(book.id, file.path)
                return
            }
            recorder.recordedBook(ctx.pkg)?.let { recorder.setFile(it.id, file.path) }
        }
        status(ctx, progress)
    }

    /** 진행률은 편 책 기록의 책에('책 모름'이면 적지 않는다) */
    private fun status(ctx: AppContext, progress: Int) {
        if (progress < 0) return
        val book = ctx.recorder.recordedBook(ctx.pkg) ?: return
        ctx.recorder.status(book.id, progress)
    }

    /** 편 책 기록 뒤(표지 전): 지금 화면이 읽는 화면 탭이면 그 탭 ↔ 책 */
    private fun tabSteps(ctx: AppContext) = object : OpenSteps {
        override fun logged(book: ShelfBook) {
            ctx.screen?.takeIf { app.isViewer(ctx.context, it) }?.let { setTabBook(ctx, it, book.id) }
        }
    }

    /**
     * 읽는 화면 [cls](탭)로 마지막으로 편 책의 id(서비스가 다시 시작돼도 남긴다).
     * 1.5.11 까지는 제목(`tab_<탭>`)을 두어 파일 안의 제목이 같은 다른 책과 헷갈렸다. 옛 값은 쓰지 않는다(그 탭은 처음 보는 탭으로).
     */
    private fun tabBook(ctx: AppContext, cls: String) = ctx.prefs.getString("tab_book_$cls", null)

    private fun setTabBook(ctx: AppContext, cls: String, id: String) {
        if (tabBook(ctx, cls) != id) ctx.prefs.edit().putString("tab_book_$cls", id).remove("tab_$cls").apply()
    }

    companion object {
        /** 책의 파일을 모를 때 다시 읽어 볼 횟수 */
        private const val FILE_RETRIES = 5
    }
}
