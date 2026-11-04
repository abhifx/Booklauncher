package com.woody.booklauncher.readers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.woody.booklauncher.ShelfBook
import com.woody.booklauncher.WatchLog
import com.woody.booklauncher.hasTitle

/**
 * iReader 기기 내장판 4.1.0. 서재는 ShelfView(뷰 하나)가 직접 그리고 읽는 화면 메뉴에도 제목이 없어 화면으로는 책을 알 수 없다.
 * 기기 펌웨어(ReaderRecordApi)가 지금 책 식별자를 알려 주면 공개된 최근 책 Provider 의 줄과 맞춰 본다.
 * 알려 주지 않는 기기(Ocean 5 Pro 4.1.0.215.0 등)는 최근 책 맨 위 줄을 지금 책으로 본다: iReader 는 책 열기가 끝나면 그 책의 읽은 때를
 * 그때로 적어(ActivityRead.onBookOpenSuccess) 맨 위로 올리고, 잠금 화면의 지금 책 표지도 같은 줄을 쓴다(bookWallpaperInfo).
 * 표지는 getBookCoverWallpaper 가 내주는 그림(읽기 허락이 같이 온다. 작은 표지는 iReader 의 기본 표지).
 * 홈에서는 그 책의 파일(최근 책 줄의 경로)을 ActivityAliasOpenBook 으로 열고, 모르면 서재를 연다.
 * 따로 까는 판 com.zhangyue.read.iReader.eink 와는 다른 앱이다.
 */
object IReader : ReaderApp(
    pkg = "com.zhangyue.iReader.Eink", classPrefix = "com.zhangyue",
    shelves = emptySet(),
    viewers = setOf("com.zhangyue.iReader.Eink.Activity.ActivityRead", "com.zhangyue.iReader.Eink.Activity.ActivityReadP1"),
    titles = emptyList(), thumbs = emptyList(), authors = emptyList(), progress = null, due = null,
    viewerProgress = listOf("Id_read_menu_progress_ui", "Id_reader_menu_pdf_progress_percent"),
) {
    internal val DATA = Uri.parse("content://com.szzy.ireader.eink.ireaderdata")

    override fun newAdapter(): ReaderAdapter = IReaderReader()

    /** 홈에서 책을 못 열 때 여는 서재(이 패키지의 실행 화면은 기기에 따라 서점 별칭이라 앱을 열면 서점이 뜬다) */
    internal const val SHELF = "com.zhangyue.iReader.Eink.Activity.alias.ActivityAliasBookShelf"
    /** 책 파일 경로(`file`)만 주면 그 파일로 책을 여는 화면(바로가기·런처가 쓴다. ActivityAliasOpenBook.onCreate → 경로로 열기) */
    internal const val OPEN_BOOK = "com.zhangyue.iReader.Eink.Activity.alias.ActivityAliasOpenBook"

    /** 네오리더처럼 책을 정할 때 적어 둔 그 책의 파일로 연다. 파일을 모르거나 없으면 서재 */
    override fun open(ctx: HomeContext, book: ShelfBook): OpenResult = OpenResult.Start(
        ctx.bookFile(book)?.let { Intent().setClassName(pkg, OPEN_BOOK).putExtra("file", it) } ?: Intent().setClassName(pkg, SHELF)
    )

    internal const val RECORD_ACTION = "com.szzy.ireader.ink.reader.process.READER_PROCESS_INFO_RECORDER"
    internal val RECENT = Uri.parse("content://com.szzy.ireader.provider.notebookshelf/bookshelf/recent?limit=2")
    /** 최근 책에서 묻는 칸. 진행률(readpercent)은 없는 판이 있을 수 있어 따로([COLUMNS] 가 실패하면 [BASE_COLUMNS] 로 다시) */
    private val BASE_COLUMNS = arrayOf("id", "name", "author", "path", "bookid", "readlasttime", "is_encrypt", "isnotebook", "type")
    internal val COLUMNS = BASE_COLUMNS + "readpercent"
    /** 따로 도는 프로세스(:read1)의 두 번째 읽는 화면. 최근 책은 프로세스를 가르지 않아 맨 위 줄로 정하지 않는다 */
    internal const val SECOND_VIEWER = "com.zhangyue.iReader.Eink.Activity.ActivityReadP1"

    /**
     * [readAt] 은 iReader 가 위치를 저장할 때마다 바뀌어 같은 책인지([same])는 보지 않는다. [percent] 는 iReader 가 저장한 진행률(%, 모르면 -1):
     * 읽는 화면을 떠날 때(onPause·닫기) 위치와 함께 저장한다(readpercent 0~1, 끝까지 읽으면 1).
     */
    internal data class Book(val title: String, val author: String, val readAt: Long, val identifier: String = "", val percent: Int = -1) {
        fun same(other: Book) = identifier == other.identifier && title == other.title && author == other.author
    }

    /** 최근 책 한 줄(경로 [key], 펌웨어 식별자와 맞춰 볼 id·경로·책 id [ids]). [book] 은 책으로 쓸 수 있는 줄만: 사용자가 잠근 책(is_encrypt)·노트·type 26/55·제목 없음은 null */
    internal data class Row(val key: String, val ids: Set<String>, val readAt: Long, val book: Book?)

    /** 한 번 물은 답. [firmware]: 펌웨어 기록 Provider 가 있어 [book] 은 식별자로 맞춘 책(없으면 null). 없으면 [rows] 로 정한다 */
    internal class Look(val firmware: Boolean, val book: Book?, val rows: List<Row>)

    /** 펌웨어의 ReaderRecordApi 가 쓰는 Provider 들 */
    private fun recorders(context: Context): List<String> =
        context.packageManager.queryIntentContentProviders(Intent(RECORD_ACTION), PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { it.providerInfo?.takeIf { p -> p.exported }?.authority }.distinct()

    /** 읽는 화면이 도는 프로세스 */
    private fun process(screen: String) = if (screen.endsWith("ActivityReadP1")) "$pkg:read1" else pkg

    /** 현재 액티비티·프로세스의 책 식별자. 하나일 때만 쓴다. */
    private fun identifier(context: Context, screen: String, authorities: List<String>): String? {
        val process = process(screen)
        val ids = authorities.flatMap { authority ->
            context.contentResolver.query(Uri.parse("content://$authority"), null, null, null, null)?.use { cursor ->
                buildList {
                    val book = cursor.getColumnIndexOrThrow("k_bi")
                    val activity = cursor.getColumnIndexOrThrow("k_an")
                    val proc = cursor.getColumnIndexOrThrow("k_pn")
                    while (cursor.moveToNext()) {
                        if (cursor.getString(activity) == screen && cursor.getString(proc) == process) {
                            cursor.getString(book)?.takeIf { it.isNotEmpty() }?.let(::add)
                        }
                    }
                }
            }.orEmpty()
        }
        return ids.distinct().singleOrNull()
    }

    internal fun book(context: Context, screen: String): Book? = runCatching {
        if (screen !in viewers) return null
        book(context, screen, recorders(context))
    }.getOrNull()

    private fun book(context: Context, screen: String, authorities: List<String>): Book? {
        val id = identifier(context, screen, authorities) ?: return null
        val matches = recent(context).orEmpty().filter { id in it.ids }
        // 묻는 동안 책이 바뀌었거나 같은 식별자가 여러 줄에 맞으면 적지 않는다.
        return matches.singleOrNull()?.book?.takeIf { identifier(context, screen, authorities) == id }
    }

    /**
     * 최근 책(읽은 때가 늦은 순으로 두 줄). 못 읽으면 null. 화면 스레드에서 부르지 않는다.
     * Provider 는 받은 칸을 그대로 쿼리에 쓰므로 진행률 칸(readpercent)이 없는 판이면 그 칸을 빼고 다시 묻는다(진행률만 모름).
     */
    internal fun recent(context: Context): List<Row>? =
        recent(context, COLUMNS) ?: recent(context, BASE_COLUMNS)

    private fun recent(context: Context, columns: Array<String>): List<Row>? = runCatching {
        context.contentResolver.query(RECENT, columns, null, null, null)?.use { cursor ->
            buildList {
                fun text(column: String) = cursor.getString(cursor.getColumnIndexOrThrow(column)).orEmpty()
                val percentColumn = cursor.getColumnIndex("readpercent")
                while (cursor.moveToNext()) {
                    val title = plain(text("name"))
                    val percent = if (percentColumn < 0 || cursor.isNull(percentColumn)) -1 else (cursor.getFloat(percentColumn) * 100).toInt().coerceIn(0, 100)
                    val at = text("readlasttime").toLongOrNull() ?: 0L
                    val usable = title.isNotEmpty() && at > 0 && text("is_encrypt") == "0" && text("isnotebook") == "0" && text("type") !in setOf("26", "55")
                    val key = text("path").ifEmpty { "id:" + text("id") }
                    val ids = setOf(text("id"), text("path"), text("bookid")) - ""
                    add(Row(key, ids, at, if (usable) Book(title, plain(text("author")), at, key, percent) else null))
                }
            }
        }
    }.getOrNull()

    /** 지금 책을 한 번 묻는다(펌웨어 기록 Provider 가 있으면 그것으로, 없으면 최근 책). 못 물으면 null. 화면 스레드에서 부르지 않는다. */
    internal fun look(context: Context, screen: String): Look? = runCatching {
        if (screen !in viewers) return null
        val authorities = recorders(context)
        if (authorities.isNotEmpty()) Look(true, book(context, screen, authorities), emptyList())
        else Look(false, null, recent(context) ?: return null)
    }.getOrNull()

    /**
     * [book] 이 책을 알아내지 못했을 때 기록 보기에 남길 단계별 진단(펌웨어 기록 Provider·최근 책 Provider 에서 얻은 것).
     * 실기기에서 어느 단계가 막히는지 사진으로 보려고 둔다. 기록 보기 한 줄에 들게 짧게, 제목은 있는지만. 화면 스레드에서 부르지 않는다.
     */
    internal fun diagnose(context: Context, screen: String, since: Long): List<String> = buildList {
        fun tail(s: String?, n: Int = 16) = s.orEmpty().let { if (it.length > n) "…" + it.takeLast(n) else it.ifEmpty { "-" } }
        fun fail(e: Throwable) = e.javaClass.simpleName + (e.message?.let { " " + tail(it, 60) } ?: "")
        fun Cursor.text(column: String) = getColumnIndex(column).takeIf { it >= 0 }?.let { getString(it) } ?: "?"
        fun short(process: String) = process.removePrefix(pkg).ifEmpty { "main" }
        val authorities = runCatching { recorders(context) }.getOrElse { add("rec ${fail(it)}"); emptyList() }
        add("rec ${authorities.size} ${authorities.joinToString(",") { tail(it, 28) }}")
        for (authority in authorities) runCatching {
            context.contentResolver.query(Uri.parse("content://$authority"), null, null, null, null)?.use { c ->
                add("rec rows ${c.count} cols ${c.columnNames.joinToString(",")}")
                while (c.moveToNext() && c.position < 3) {
                    add("rec an=${c.text("k_an").substringAfterLast('.')} pn=${short(c.text("k_pn"))} bi=${tail(c.text("k_bi"))}")
                }
            } ?: add("rec null")
        }.onFailure { add("rec ${fail(it)}") }
        add("want an=${screen.substringAfterLast('.')} pn=${short(process(screen))}")
        runCatching {
            context.contentResolver.query(RECENT, BASE_COLUMNS, null, null, null)?.use { c ->
                add("recent rows ${c.count}")
                while (c.moveToNext() && c.position < 2) {
                    val at = c.text("readlasttime").toLongOrNull()?.let { "%+ds".format((it - since) / 1000) } ?: "?"
                    add("recent id=${c.text("id")} enc=${c.text("is_encrypt")} note=${c.text("isnotebook")} type=${c.text("type")} at=$at name=${if (c.text("name").isBlank()) "-" else "O"}")
                    add("recent path=${tail(c.text("path"))} bid=${tail(c.text("bookid"), 8)}")
                }
            } ?: add("recent null")
        }.onFailure { add("recent ${fail(it)}") }
    }

    internal fun metadata(data: Bundle?): Book? {
        val title = data?.getString("EXTRA_BOOK_NAME")?.let(::plain)?.takeIf { it.isNotEmpty() } ?: return null
        return Book(title, data.getString("EXTRA_BOOK_AUTHOR")?.let(::plain).orEmpty(), data.getLong("EXTRA_BOOK_READ_TIME", 0))
    }

    /**
     * API가 최근 책을 다시 고르므로 표지 요청 전후에도 같은 책일 때만 쓴다: 지금 책([current], 펌웨어 식별자나 최근 책 맨 위 줄로 다시 물음)이 [expected] 이고,
     * 표지 API 의 책이 같은 때 읽은 최근 책 행과 같다(시각까지). 파일 경로는 직접 읽지 않는다.
     */
    internal fun cover(context: Context, expected: Book, current: () -> Book?): ByteArray? = coverResult(context, expected, current).first

    /** [cover] 와 그 까닭(기록 보기 진단): ok · differ(지금 책이나 표지 API 의 책이 다름) · none(그림을 주지 않음) · changed(받는 사이 책이 바뀜) · 오류 이름 */
    internal fun coverResult(context: Context, expected: Book, current: () -> Book?): Pair<ByteArray?, String> = runCatching<Pair<ByteArray?, String>> {
        fun sameBook(): Boolean {
            val now = current()?.takeIf { it.same(expected) } ?: return false
            return metadata(context.contentResolver.call(DATA, "bookWallpaperInfo", null, null)) == now.copy(identifier = "")
        }
        if (!sameBook()) return null to "differ"
        val result = context.contentResolver.call(DATA, "getBookCoverWallpaper", null, null) ?: return null to "none"
        val uri = result.getString("EXTRA_BOOK_COVER_URI")?.let(Uri::parse)?.takeIf {
            it.scheme == "content" && it.authority == "$pkg.SHARE_FILE"
        } ?: return null to "none"
        val jpeg = BookFiles.streamCover { context.contentResolver.openInputStream(uri) ?: error("No cover") } ?: return null to "none"
        if (sameBook()) jpeg to "ok" else null to "changed"
    }.getOrElse { null to it.javaClass.simpleName }
}

/**
 * Provider 호출과 표지 읽기는 뒤에서. 다른 화면으로 넘어간 뒤 온 결과는 적용하지 않는다.
 * 들어온 때부터 어떤 책인지는 정한 뒤에 적는다: 앞서 적은 책이면 그대로(편 책 기록을 늘리지 않는다), 다른 책이면 들어온 때부터 그 책,
 * 끝내 정하지 못하면(재시도가 끝남·홈으로 감·다음에 들어옴) 들어온 때부터 책 모름.
 * 펌웨어 기록이 없는 기기에서 최근 책으로 정하는 법은 [choose].
 */
internal class IReaderReader : ScreenReader(IReader) {
    private var busy = false
    private var pending = false
    private var lastAsk = Long.MIN_VALUE
    private val coverAttempts = mutableMapOf<String, Int>()
    private var coverBusy = false
    private var enteredAt: Long? = null
    /** 이번에 들어온 읽는 화면의 책을 정했다(정했거나 책 모름으로 적음) */
    private var settled = true
    /** 이번에 들어온 읽는 화면에서 정한 책([IReader.Book.readAt] 은 정한 때의 읽은 때) */
    private var confirmed: IReader.Book? = null
    /** 이번에 들어온 읽는 화면에서 갈래마다 마지막으로 남긴 기록 줄(같으면 다시 남기지 않는다) */
    private val logged = mutableMapOf<String, String>()

    /** [choose] 의 답: 그 책으로 · 조금 뒤 다시 · 정하지 못함. [why] 는 기록 보기에 남기는 까닭 */
    private sealed class Choice(val why: String) {
        class Take(val book: IReader.Book, why: String) : Choice(why)
        class Wait(val ms: Long, why: String) : Choice(why)
        class None(why: String) : Choice(why)
    }

    /** 기록 보기에 [line] 을 한 줄(그 갈래 [kind] 의 앞 줄과 같으면 남기지 않는다). 실기기에서 어느 규칙으로 정했는지 사진으로 보려고 둔다 */
    private fun note(ctx: AppContext, kind: String, line: String) {
        if (logged.put(kind, line) != line) ctx.log(WatchLog.Kind.NOTE, line, "")
    }

    /** 최근 책 줄의 짧은 모양: 경로 끝·들어온 때와 읽은 때의 차·쓸 수 없는 줄(잠금·노트 등)은 x */
    private fun rowsLine(rows: List<IReader.Row>, entered: Long) = "rows " + rows.joinToString(" ") { r ->
        val key = r.key.substringAfterLast('/').let { if (it.length > 10) "…" + it.takeLast(10) else it }
        "$key@${"%+d".format((r.readAt - entered) / 1000)}s" + if (r.book == null) "x" else ""
    }.ifEmpty { "-" }

    override fun onEnterViewer(ctx: AppContext, from: String?, again: Boolean) {
        if (!again || enteredAt == null) {
            giveUp(ctx)
            enteredAt = ctx.now
            settled = false
            confirmed = null
            lastAsk = Long.MIN_VALUE
            coverAttempts.clear()
            logged.clear()
            ctx.unknownOpenAt = ctx.now
            note(ctx, "enter", "enter new")
        } else note(ctx, "enter", "enter again")
    }

    override fun onHome(ctx: AppContext) {
        val read = confirmed?.takeIf { it.identifier.isNotEmpty() }
        giveUp(ctx)
        enteredAt = null
        confirmed = null
        pending = false
        // iReader 는 읽는 화면을 떠날 때 진행률을 저장하므로 홈에 오면 그 책의 줄을 한 번 더 읽는다(아직 안 썼으면 다음에 들어올 때)
        if (read != null) ctx.inBackground({ IReader.recent(ctx.context)?.firstOrNull { it.key == read.identifier }?.book?.percent }) { r ->
            val percent = r.getOrNull()?.takeIf { it >= 0 } ?: return@inBackground
            ctx.recorder.recordedBook(ctx.pkg)?.takeIf { it.hasTitle(read.title) }?.let { ctx.recorder.status(it.id, percent) }
        }
    }

    /** 들어온 읽는 화면의 책을 정하지 못했다: 들어온 때부터 책 모름(앞서 적은 책에 붙이지 않게) */
    private fun giveUp(ctx: AppContext) {
        val entered = enteredAt
        if (settled || entered == null) return
        settled = true
        ctx.unknown(entered)
    }

    /**
     * 펌웨어 기록이 없는 기기에서 최근 책([rows], 읽은 때가 늦은 순)으로 지금 책을 정한다.
     * - 두 번째 읽는 화면(:read1)은 정하지 않는다.
     * - 처음 정할 때: 맨 위 줄이 들어온 뒤 읽은 때가 바뀌었으면(책을 열었다) 바로 그 책. 그대로면 이어 읽기이고,
     *   새 책이 아직 열리는 중일 수 있어 들어오고 [RESUME_MS] 까지 기다린 뒤 그 책.
     * - 정한 뒤: 맨 위가 다른 줄로 바뀌었어도, 정한 책의 줄도 정한 뒤 읽은 때가 바뀌었을 때만(닫고 다른 책을 연 흔적) 그 책으로 바꾼다.
     *   읽는 동안 가져오기·저장소 훑기로 다른 책이 맨 위로 올라와도 바꾸지 않기 위해서다.
     *   다만 이어 읽기로 정한 책(이번에 열렸는지 모른다)이면, 들어오고 [LATE_OPEN_MS] 안에 연 다른 책이 맨 위로 오면 바꾼다([RESUME_MS] 보다 늦게 열린 책.
     *   그보다 뒤에 맨 위로 온 줄은 이어 읽는 동안 가져온 책일 수 있어 바꾸지 않는다).
     */
    private fun choose(now: Long, screen: String, entered: Long, rows: List<IReader.Row>): Choice {
        if (screen == IReader.SECOND_VIEWER) return Choice.None("skip P1")
        val top = rows.firstOrNull() ?: return Choice.None("skip no rows")
        val current = confirmed
        if (current == null) {
            val book = top.book ?: return Choice.None("skip top unusable")
            if (top.readAt >= entered) return Choice.Take(book, "pick open")
            val left = RESUME_MS - (now - entered)
            return if (left > 0) Choice.Wait(left, "wait resume") else Choice.Take(book, "pick resume")
        }
        if (top.key == current.identifier) return Choice.Take(current, "same")
        val resumed = current.readAt < entered
        val closed = rows.any { it.key == current.identifier && it.readAt > current.readAt }
        val late = resumed && top.readAt - entered <= LATE_OPEN_MS
        val opened = top.book?.takeIf { (late || closed) && top.readAt >= entered }
        return if (opened != null) Choice.Take(opened, if (closed) "switch closed" else "switch late open") else Choice.Take(current, "keep (top changed, not closed)")
    }

    override fun onViewer(ctx: AppContext) {
        // 빠른 설정 등 다른 창이 잠깐 앞에 있다: 들어온 때는 그대로 두고(늦게 온 응답은 stillHere 가 버린다) 다음 알림을 기다린다
        if (ctx.root == null) return
        val entered = enteredAt ?: return
        // iReader가 새 책을 여는 동안에는 Provider에 앞 책이 남아 있을 수 있다.
        val wait = 1000L - (ctx.now - entered)
        if (wait > 0) { ctx.viewerAgain(wait); return }
        if (busy) { pending = true; return }
        val throttle = if (lastAsk == Long.MIN_VALUE) 0L else 1000L - (ctx.now - lastAsk)
        if (throttle > 0) { ctx.viewerAgain(throttle); return }
        val screen = ctx.screen ?: return
        val transition = ctx.lastTransition
        fun stillHere() = ctx.root != null && ctx.screen == screen && enteredAt == entered && ctx.lastTransition == transition
        busy = true
        pending = false
        lastAsk = ctx.now
        ctx.inBackground({
            val look = IReader.look(ctx.context, screen)
            val failed = look == null || (if (look.firmware) look.book == null else look.rows.firstOrNull()?.book == null || screen == IReader.SECOND_VIEWER)
            look to if (failed) IReader.diagnose(ctx.context, screen, entered) else null
        }) { result ->
            busy = false
            val reread = pending
            pending = false
            if (!stillHere()) {
                if (reread) ctx.viewerAgain(0L)
                return@inBackground
            }
            if (reread) {
                // 도중의 화면 변경은 다른 책일 수도 있다. 이전 응답과 새 화면의 진행률을 섞지 않는다.
                ctx.viewerAgain((1000L - (ctx.now - lastAsk)).coerceAtLeast(0L))
                return@inBackground
            }
            val (look, diag) = result.getOrNull() ?: (null to null)
            val windows = ctx.windows()
            val choice = when {
                look == null -> Choice.None("skip no answer")
                look.firmware -> look.book?.let { Choice.Take(it, "pick firmware") } ?: Choice.None("skip firmware")
                else -> choose(ctx.now, screen, entered, look.rows)
            }
            if (look != null && !look.firmware && choice.why != "same" && logged["choice"] != choice.why) {
                note(ctx, "rows", rowsLine(look.rows, entered))
            }
            if (choice.why != "same") note(ctx, "choice", choice.why)
            if (choice is Choice.Wait) { ctx.viewerAgain(choice.ms); return@inBackground }
            val info = (choice as? Choice.Take)?.book
            if (info == null) {
                if (look?.firmware == true) confirmed = null
                if (diag != null && logged.put("diag", diag.joinToString("\n")) != diag.joinToString("\n")) {
                    // 기록 보기는 새 줄이 위라 거꾸로 넣어 위에서부터 읽히게
                    diag.asReversed().forEach { ctx.log(WatchLog.Kind.NOTE, it, "") }
                }
                if (ctx.viewerRetry()) ctx.viewerAgain(VIEWER_RETRY_MS) else giveUp(ctx)
                return@inBackground
            }
            if (confirmed?.same(info) != true) confirmed = info
            settled = true
            // 진행률은 메뉴(진행률 하위 메뉴)에 보이면 그것, 아니면 iReader 가 저장한 것
            val progress = IReader.readViewer(windows).progress.takeIf { it >= 0 } ?: info.percent
            val current = ViewerInfo(info.title, info.author, progress)
            logShown(ctx, current)
            recordShown(ctx, current)
            // 앞서 적은 책이라 다시 적지 않았으면 들어온 때를 지운다(읽는 화면 안에서 다른 책을 펴면 그때부터)
            ctx.unknownOpenAt = null
            retryProgress(ctx, progress)
            val book = ctx.recorder.recordedBook(ctx.pkg) ?: return@inBackground
            // 홈에서 그 책의 파일로 열려고 경로를 적어 둔다(경로가 없는 줄은 id 라 적지 않는다)
            if (info.identifier.startsWith("/")) ctx.recorder.setFile(book.id, info.identifier)
            if (ctx.recorder.lookup.hasCover(book.id)) return@inBackground
            if (coverBusy) { ctx.viewerAgain(1000L); return@inBackground }
            val attempts = coverAttempts[book.id] ?: 0
            if (attempts >= 3) return@inBackground
            coverAttempts[book.id] = attempts + 1
            coverBusy = true
            val firmware = look?.firmware == true
            ctx.inBackground({
                IReader.coverResult(ctx.context, info) { if (firmware) IReader.book(ctx.context, screen) else IReader.recent(ctx.context)?.firstOrNull()?.book }
            }) { cover ->
                coverBusy = false
                val (jpeg, why) = cover.getOrNull() ?: (null to "fail")
                note(ctx, "cover", "cover $why")
                if (stillHere() && confirmed?.same(info) == true) {
                    if (jpeg != null) ctx.recorder.cover(book.id, jpeg)
                    else if (attempts + 1 < 3) ctx.viewerAgain(1000L)
                }
            }
        }
    }

    private companion object {
        /** 이어 읽기로 보기 전에 새 책이 열리기를 기다리는 때(들어온 때부터) */
        const val RESUME_MS = 3000L
        /** 이어 읽기로 정한 뒤에도 늦게 열린 책으로 볼 때(들어온 때부터. 그 뒤로 맨 위에 온 줄은 가져온 책일 수 있다) */
        const val LATE_OPEN_MS = 60_000L
    }
}
