package com.woody.booklauncher.readers

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
import android.media.browse.MediaBrowser
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import android.webkit.MimeTypeMap
import com.woody.booklauncher.ShelfBook
import com.woody.booklauncher.Storage
import com.woody.booklauncher.WatchCore
import com.woody.booklauncher.WatchLog
import com.woody.booklauncher.Seen
import com.woody.booklauncher.hasTitle

/**
 * LibreraX(Librera 개발자가 새로 만든 내 파일 리더, 1.2.87·1.2.90. [Librera] 와 패키지·화면·기록이 모두 달라 코드를 나누지 않는다).
 * 액티비티가 `ComposeMainActivity` 하나이고(다른 앱이 책을 열 때의 `reader.ReaderActivity` 는 그 별칭) 서재·읽는 화면이 모두 Compose 라 요소 이름이 없다.
 * 책을 열고 닫아도 화면 바뀜 알림은 없고 내용 바뀜만 온다(Libby·부커스 2.0 과 같다). 탭·단추 글자는 앱 언어마다 달라 글자가 아니라 모양으로 가린다.
 * 서재(리스트·2열·그리드 보기)의 칸: 누르기·길게 누르기가 되는 묶음 바로 아래에 표지 묶음, 제목, 저자(책 정보가 없으면 폴더 `[Download]`),
 * 정보 줄(`EPUB`, 리스트 보기는 `3%  EPUB  139 kB (81)  26. 10. 21.`)이 차례로 있다. 표지 보기 칸·위 막대의 최근 책·`무료 도서` 줄은 글자가 없어
 * 칸으로 보지 않는다(그렇게 연 책은 메뉴 제목으로 안다). 서재 칸에서는 저자·진행률만 읽고 표지는 자르지 않는다.
 * 표지는 LibreraX 가 내준다: 자동차·시계용으로 내보낸 미디어 서비스 `media.BookMediaService` 의 `__recent__`(최근에 연 책, 방금 연 책이 맨 앞)에
 * 책마다 제목·표지 주소 `content://com.librerax.covers/<책 경로의 hashCode>` 가 있고, 그 주소는 내보낸 `CoverProvider` 가 JPEG(600×900px)로 내준다
 * ([recent]. 표지 그림이 없는 책은 LibreraX 가 그린 그림). 서재 칸의 표지도 같은 캐시 파일이라, 주소가 없는 책은 서재에도 LibreraX 가 글자로 그린 표지뿐이다:
 * 서재 칸에서 잘라 올 것이 없어 자르지 않는다.
 * 읽는 화면: 책을 열면 메뉴가 떠 있다. 메뉴는 맨 위에 제목·저자(누를 수 있는 묶음 하나, PDF 는 파일 이름만), 아래에 서재 줄(책 칸들: 글자 없는 표지뿐이라 칸이 아니다)과
 * 쪽 `1` · 막대(`SeekBar`) · `81`. 메뉴를 닫으면 아래 상태 줄(`1` ` / 81`, 설정에서 끌 수 있다)만 남고 누를 수 있는 것이 하나도 없다.
 * 본문(웹 화면·PDF 그림)은 창 뿌리에서 닿지 않는다. 그래서 읽는 화면은 쪽 막대가 있는 메뉴가 보이거나, 화면에 누를 수 있는 것이 없는 동안이다([showsViewer]).
 * LibreraX 는 저장소 권한이 없어 책을 사용자가 허용한 폴더의 문서 주소나 자기 폴더에 베낀 사본으로 읽는다(미디어 서비스의 책 경로가 그것).
 * Pebble Desk 가 읽을 수 있는 파일은 아니지만 LibreraX 에 그 경로를 그대로 보내면 그 책을 읽던 곳에서 연다: 어댑터가 최근 책에서 그 경로를 적어 두고([saveBookPath])
 * 홈에서 그 경로로 연다([open]. 사용자의 저장소 경로 `file://` 는 LibreraX 가 읽지 못해 열지 못한다).
 */
object LibreraX : ReaderApp(
    pkg = "com.librerax", classPrefix = "com.librerax",
    shelves = setOf(MAIN),
    viewers = setOf("$MAIN#$VIEWER"),
    titles = emptyList(), thumbs = emptyList(), authors = emptyList(), progress = null, due = null,
    viewerProgress = emptyList(), viewerHolder = VIEWER,
    openOnViewer = true,
) {
    override fun newAdapter(): ReaderAdapter = LibreraXReader(this)

    /**
     * 홈에서: 적어 둔 LibreraX 의 책 경로([bookPath])로 연다. 문서 주소는 그대로, LibreraX 폴더의 사본은 파일로. 경로를 모르거나
     * BOOX 가 얼린 앱이면(런처로 풀어야 한다) 앱을 연다. 그 책이 지워졌으면 LibreraX 가 `이 책을 더 이상 열 수 없습니다` 를 보인다
     */
    override fun open(ctx: HomeContext, book: ShelfBook): OpenResult {
        val known = bookPath(ctx.context, book.id)
        val frozen = ctx.frozen
        // 홈에서 책이 아니라 앱이 열린다는 기기에서 어느 길이었는지 자동 추가 기록(기기 정보 › 기록 보기)으로 본다
        WatchLog.add(ctx.context, WatchLog.Kind.NOTE, pkg, "open " + (if (frozen) "frozen" else known?.let(::pathKind) ?: "no path"))
        val path = known?.takeIf { !frozen } ?: return OpenResult.Launch
        val reader = ComponentName(pkg, ALIAS)
        if (path.startsWith("/")) return OpenResult.File(reader, path)
        val uri = Uri.parse(path)
        val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(Uri.decode(path).substringAfterLast('/').substringAfterLast('.', "").lowercase())
        return OpenResult.Start(Intent(Intent.ACTION_VIEW).setComponent(reader).setDataAndType(uri, type))
    }

    /** 책 [id] 를 LibreraX 가 여는 경로(문서 주소 `content://…` 나 LibreraX 폴더의 사본 `/storage/…`). 모르면 null */
    internal fun bookPath(context: Context, id: String): String? = prefs(context).getString(pathPref(id), null)

    /** 책 [id] 의 LibreraX 경로를 적는다(같으면 쓰지 않는다). 문서 주소·절대 경로가 아니면(클라우드 책 `gdrive://…` 등) 적지 않고 false */
    internal fun saveBookPath(prefs: SharedPreferences, id: String, path: String): Boolean {
        if (!path.startsWith(CONTENT) && !path.startsWith("/")) return false
        if (prefs.getString(pathPref(id), null) != path) prefs.edit().putString(pathPref(id), path).apply()
        return true
    }

    /** 기록에 남길 경로의 모양(파일 이름 없이): 문서 주소는 주소를 내주는 곳, 절대 경로는 `file`, 그 밖은 앞의 이름(`gdrive`) */
    internal fun pathKind(path: String): String = when {
        path.startsWith(CONTENT) -> "content " + Uri.parse(path).authority.orEmpty()
        path.startsWith("/") -> "file"
        else -> path.substringBefore(':', "?")
    }

    private fun prefs(context: Context) = Storage.of(context).getSharedPreferences(WatchCore.WATCH_PREFS, Context.MODE_PRIVATE)
    private fun pathPref(id: String) = "book_path_${pkg}_$id"

    /** 책을 열면 메뉴가 떠 있어 제목을 안다 */
    override val tellsTitle get() = true

    /**
     * 다른 앱에서 책을 열어 LibreraX 가 새로 뜨면 액티비티 기록은 별칭 이름이다. 안드로이드 11 의 사용 기록에는 본래 이름으로 남았지만,
     * 별칭 이름으로 남는 판이 있으면 그 이름이 읽는 화면 규칙에 맞아 앱에 있던 동안이 모두 읽은 시간이 되므로 그 이름도 읽는 화면이 보인 동안만 센다
     */
    override val markedScreens: Set<String> get() = setOf(MAIN, ALIAS)

    /** 창 하나가 읽는 화면인가: 쪽 막대가 있는 메뉴가 보이거나 누를 수 있는 것이 하나도 없다 */
    override fun showsViewer(root: AccessibilityNodeInfo): Boolean = menu(root) != null || bare(root)

    /**
     * 그 앱의 창들에 읽는 화면이 떠 있는가: 어느 창에든 메뉴가 보이거나(읽기 설정 창이 위에 떠 있어도), 화면 자체(끝 창)에 누를 수 있는 것이 없다.
     * 위에 뜬 창만 누를 것이 없는 것(안내 글자)은 읽는 화면이 아니다
     */
    internal fun viewerIn(windows: AppWindows): Boolean =
        windows.all.any { menu(it) != null } || windows.all.lastOrNull()?.let(::bare) == true

    /** 서재(리스트·2열·그리드 보기)의 칸들. 읽는 화면 메뉴의 서재 줄은 읽지 않는다 */
    override fun shelfCells(root: AccessibilityNodeInfo): List<Cell> {
        if (menu(root) != null) return emptyList()
        val found = mutableListOf<Cell>()
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            cell(n)?.let { found += it; return }
            if (depth < DEEP_WALK_DEPTH) for (k in n.kids()) walk(k, depth + 1)
        }
        walk(root, 0)
        return found
    }

    /**
     * 누른 칸: 누른 요소가 칸이면 그 칸, 아니면 지금 서재에서 누른 요소와 자리가 똑같은 칸(알림의 요소에서 아래를 못 읽을 때).
     * 아래 탭 줄은 칸 위에 떠 있어 누른 곳이 든 칸으로 찾지 않는다
     */
    override fun clickedCell(source: AccessibilityNodeInfo?, text: List<CharSequence>, windows: AppWindows, last: List<Cell>?): Cell? {
        val s = source ?: return null
        cell(s)?.let { return it }
        val at = s.bounds()
        return windows.active?.let(::shelfCells)?.firstOrNull { it.cell == at }
    }

    /**
     * 읽는 화면: 메뉴가 보이면 제목·저자와 쪽 막대 양옆의 쪽, 없으면 상태 줄의 쪽(`1` ` / 81`).
     * 제목은 메뉴가 있는 창에서만 찾고(위에 뜬 TTS 창의 `엔진`(`Engine`) 줄을 책 제목으로 읽었다), 쪽 막대가 있는 창이 여럿이면 제목이 있는 창,
     * 없으면 쪽이 숫자인 창을 쓴다
     */
    override fun readViewer(windows: AppWindows): ViewerInfo {
        val menus = windows.all.mapNotNull { w -> menu(w)?.let { m -> m to menuTitle(w, m.bar) } }
        (menus.firstOrNull { it.second != null } ?: menus.firstOrNull { it.first.page != null } ?: menus.firstOrNull())?.let { (m, found) ->
            val (title, author) = found ?: (null to "")
            return ViewerInfo(title, author, pagePercent(m.page, m.total, roundUp = false))
        }
        return ViewerInfo(null, "", windows.all.firstNotNullOfOrNull { statusPages(it).takeIf { p -> p >= 0 } } ?: -1)
    }

    /** 읽는 화면 메뉴의 쪽 막대 자리와 양옆의 지금 쪽·전체 쪽(책을 여는 중이라 아직 숫자가 아니면 null) */
    private class Menu(val bar: Rect, val page: String?, val total: String?)

    /**
     * 읽는 화면 메뉴: 막대(`SeekBar`) 바로 앞·뒤 형제가 글자 요소(지금 쪽·전체 쪽)인 것. 없으면 null.
     * 책을 여는 동안은 쪽 글자가 아직 숫자가 아니어도 메뉴로 본다(그 사이를 서재로 보면 읽는 화면이 닫혔다 다시 열린 것으로 적힌다)
     */
    private fun menu(root: AccessibilityNodeInfo): Menu? {
        fun walk(n: AccessibilityNodeInfo, depth: Int): Menu? {
            val kids = n.kids()
            for (i in 1 until kids.size - 1) {
                if (kids[i].className?.endsWith("SeekBar") != true) continue
                if (!kids[i - 1].isText() || !kids[i + 1].isText()) continue
                return Menu(kids[i].bounds(), kids[i - 1].label()?.takeIf(NUMBER::matches), kids[i + 1].label()?.takeIf(NUMBER::matches))
            }
            if (depth < DEEP_WALK_DEPTH) for (k in kids) walk(k, depth + 1)?.let { return it }
            return null
        }
        return walk(root, 0)
    }

    /** 누를 수 있는 것이 하나도 없는 창(메뉴를 닫은 읽는 화면·책을 불러오는 중) */
    private fun bare(root: AccessibilityNodeInfo): Boolean {
        fun clickable(n: AccessibilityNodeInfo, depth: Int): Boolean =
            n.isClickable || (depth < DEEP_WALK_DEPTH && n.kids().any { clickable(it, depth + 1) })
        return !clickable(root, 0)
    }

    /**
     * 메뉴 맨 위의 제목·저자: 메뉴가 있는 창 [root] 에서 누를 수 있고 아래가 글자 하나(제목)나 둘(제목·저자)뿐인 묶음 가운데 쪽 막대 [bar] 보다 위에서 가장 위의 것.
     * 넘기는 줄 안(서재 줄의 탭, 설정·TTS 창의 `엔진`·`음성` 같은 이름 · 값 줄)은 뺀다. 저자 자리의 폴더(`[Download]`)는 저자가 아니다
     */
    private fun menuTitle(root: AccessibilityNodeInfo, bar: Rect): Pair<String, String>? {
        var best: AccessibilityNodeInfo? = null
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (n.className?.endsWith("ScrollView") == true) return
            val kids = n.kids()
            if (n.isClickable && kids.size in 1..2 && kids.all { it.label() != null } && n.bounds().bottom <= bar.top &&
                (best == null || n.bounds().top < best!!.bounds().top)) best = n
            if (depth < DEEP_WALK_DEPTH) for (k in kids) walk(k, depth + 1)
        }
        walk(root, 0)
        val texts = best?.kids()?.mapNotNull { it.label() } ?: return null
        return texts[0] to texts.getOrNull(1)?.takeUnless(FOLDER::matches).orEmpty()
    }

    /** 상태 줄의 쪽 → %(버림): 전체 쪽 글자(` / 81`) 바로 앞 형제가 지금 쪽. 없으면 -1 */
    private fun statusPages(root: AccessibilityNodeInfo): Int {
        fun walk(n: AccessibilityNodeInfo, depth: Int): Int {
            val kids = n.kids()
            for (i in 1 until kids.size) {
                val total = kids[i].label()?.let { TOTAL.find(it)?.groupValues?.get(1) } ?: continue
                val page = kids[i - 1].label()?.takeIf(NUMBER::matches) ?: continue
                pagePercent(page, total, roundUp = false).takeIf { it >= 0 }?.let { return it }
            }
            if (depth < DEEP_WALK_DEPTH) for (k in kids) walk(k, depth + 1).takeIf { it >= 0 }?.let { return it }
            return -1
        }
        return walk(root, 0)
    }

    /**
     * 칸: 누르기·길게 누르기가 되는 묶음, 첫 요소가 글자가 아닌 표지 묶음이고 그 뒤 바로 아래 글자가 둘 이상(제목 … 정보 줄).
     * 글자가 셋 이상이면 둘째가 저자(폴더 `[Download]` 는 아니다). 진행률은 정보 줄에 `%` 가 있을 때만(리스트 보기).
     * 표지는 LibreraX 가 내주는 것을 쓰므로 표지 자리는 두지 않는다
     */
    private fun cell(n: AccessibilityNodeInfo): Cell? {
        if (!n.isClickable || !n.isLongClickable) return null
        val kids = n.kids()
        if (kids.firstOrNull()?.let { it.label() == null && !it.isText() } != true) return null
        val texts = kids.drop(1).filter { it.isText() }.mapNotNull { it.label() }
        if (texts.size < 2) return null
        val author = texts.takeIf { it.size >= 3 }?.get(1)?.takeUnless(FOLDER::matches).orEmpty()
        return Cell(texts[0], author, percent(texts.last()), -1, null, n.bounds())
    }

    /** LibreraX 미디어 서비스의 최근 책 한 줄: LibreraX 의 책 경로(허용한 폴더의 문서 주소나 자기 폴더의 사본), 제목, 표지 주소(표지를 만들지 않은 책은 null) */
    internal class Recent(val path: String, val title: String, val cover: Uri?)

    /** 미디어 항목 → 최근 책. 책 항목은 미디어 id 가 `book:<LibreraX 의 책 경로>` 다. 책이 아닌 항목·제목 없는 항목은 null */
    internal fun recentItem(mediaId: String?, title: CharSequence?, icon: Uri?): Recent? {
        val path = mediaId?.takeIf { it.startsWith(BOOK_ID) }?.substring(BOOK_ID.length)?.takeIf { it.isNotEmpty() } ?: return null
        val name = title?.let(::plain)?.takeIf { it.isNotEmpty() } ?: return null
        return Recent(path, name, icon)
    }

    /**
     * [items](방금 연 책이 맨 앞) 가운데 [book] 의 줄: 제목이 그 책의 것인 첫 줄, 없으면 경로의 파일 이름(확장자 뺌)이 그 책의 제목인 첫 줄.
     * PDF 는 메뉴에 파일 이름이 보여 그 이름으로 넣는데 최근 책의 제목은 파일 안의 제목이다(`SPIEGEL_2026_46` · `DER SPIEGEL …`)
     */
    internal fun recentFor(items: List<Recent>, book: ShelfBook): Recent? =
        items.firstOrNull { book.hasTitle(it.title) } ?: items.firstOrNull { fileTitle(it.path).let { f -> f.isNotBlank() && book.hasTitle(f) } }

    /** 책 경로의 파일 이름에서 확장자를 뺀 것(문서 주소는 풀어서: `…%2FSPIEGEL_2026_46.pdf` → `SPIEGEL_2026_46`) */
    internal fun fileTitle(path: String): String {
        val name = (if (path.startsWith(CONTENT)) Uri.decode(path) else path).substringAfterLast('/').substringAfterLast(':')
        return name.substringBeforeLast('.')
    }

    /**
     * LibreraX 미디어 서비스에 붙어 최근에 연 책들을 받아 [done] 에(화면 스레드, 한 번만). 붙지 못하거나 [MEDIA_TIMEOUT_MS] 안에 답이 없으면 빈 목록.
     * 화면 스레드에서 부른다(LibreraX 가 떠 있을 때: 서비스에 붙는 것이라 떠 있지 않으면 LibreraX 를 깨운다)
     */
    internal fun recent(context: Context, done: (List<Recent>) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        var browser: MediaBrowser? = null
        var finished = false
        lateinit var timeout: Runnable
        fun finish(items: List<Recent>) {
            if (finished) return
            finished = true
            main.removeCallbacks(timeout)
            runCatching { browser?.disconnect() }
            done(items)
        }
        timeout = Runnable { finish(emptyList()) }
        val callback = object : MediaBrowser.ConnectionCallback() {
            override fun onConnected() {
                runCatching {
                    browser!!.subscribe(RECENT, object : MediaBrowser.SubscriptionCallback() {
                        override fun onChildrenLoaded(parent: String, children: MutableList<MediaBrowser.MediaItem>) =
                            finish(children.mapNotNull { k -> k.description.let { d -> recentItem(k.mediaId, d.title, d.iconUri) } })
                        override fun onError(parent: String) = finish(emptyList())
                    })
                }.onFailure { finish(emptyList()) }
            }
            override fun onConnectionFailed() = finish(emptyList())
            override fun onConnectionSuspended() = finish(emptyList())
        }
        runCatching {
            browser = MediaBrowser(context, ComponentName(pkg, MEDIA_SERVICE), callback, null)
            main.postDelayed(timeout, MEDIA_TIMEOUT_MS)
            browser!!.connect()
        }.onFailure { finish(emptyList()) }
    }

    private fun AccessibilityNodeInfo.kids(): List<AccessibilityNodeInfo> = (0 until childCount).mapNotNull { getChild(it) }
    private fun AccessibilityNodeInfo.isText() = className?.endsWith("TextView") == true
    /** 요소의 글자(빈 글자는 null) */
    private fun AccessibilityNodeInfo.label(): String? = text?.let(::plain)?.takeIf { it.isNotEmpty() }

    private val NUMBER = Regex("""\d{1,6}""")
    /** 상태 줄의 전체 쪽 ` / 81` */
    private val TOTAL = Regex("""^/\s*(\d{1,6})$""")
    /** 책 정보에 저자가 없을 때 그 자리에 보이는 폴더 이름 `[Download]` */
    private val FOLDER = Regex("""^\[.*]$""")
    /** 내보낸 미디어 서비스(`android.media.browse.MediaBrowserService`)와 그 안의 최근 책 묶음, 책 항목의 미디어 id 앞 */
    private const val MEDIA_SERVICE = "com.librerax.media.BookMediaService"
    private const val RECENT = "__recent__"
    private const val BOOK_ID = "book:"
    private const val MEDIA_TIMEOUT_MS = 5000L
    private const val CONTENT = "content://"
}

/**
 * LibreraX 의 판단: 읽는 화면이 떠 있는지를 그 앱의 창을 모두 보고 정한다(읽기 설정 같은 창이 위에 떠 있는 동안에도 읽는 화면으로).
 * 책은 메뉴 제목으로 안다: 칸을 누른 알림은 화면이 읽는 화면으로 넘어간 뒤에 와서(그때는 서재가 아니다) 누른 칸으로는 알 수 없다.
 * 메뉴는 책이 다 열린 뒤에 보이므로, 읽는 화면에 들어와 처음 읽은 제목의 책은 들어온 때부터 편 것으로 적는다.
 * 표지는 화면에서 자르지 않고 LibreraX 가 내주는 그림을 받는다([LibreraX.recent]): 표지가 없는 책이면, 편 책은 폈을 때·읽는 화면이 잠잠해질 때, 서재에 보이는 책은 서재가 멈출 때.
 * 같은 목록에서 홈에서 열 책의 경로도 적는다: 책을 폈을 때는 늘(다른 파일로 같은 제목을 열었을 수 있다), 읽는 화면·서재에서는 경로를 모르는 책만
 * (폈을 때 최근 책에 아직 없었거나 서비스가 꺼진 동안 연 책. 경로를 모르면 홈에서 책이 아니라 앱이 열린다)
 */
private class LibreraXReader(app: ReaderApp) : ScreenReader(app) {
    /** 읽는 화면에 새로 들어온 때. 그 뒤 처음 제목을 읽으면 지운다(읽는 화면 안에서 책을 바꾼 것은 제목이 바뀐 때부터) */
    private var enteredAt: Long? = null
    /** 책(id)마다 표지를 받아 본 횟수(LibreraX 가 표지를 아직 만들지 않았거나 최근 책에 없으면 다음에 다시, [TRIES] 번까지)와 받는 중인가 */
    private val coverTries = mutableMapOf<String, Int>()
    private var coverBusy = false
    /** 받는 중에 편 책: 끝나면 그 책의 경로를 받으러 한 번 더 묻는다 */
    private var pendingOpened: ShelfBook? = null
    /** 책(id)마다 경로를 받아 본 횟수(경로를 모르는 책이 최근 책에 없을 때 [TRIES] 번까지) */
    private val pathTries = mutableMapOf<String, Int>()

    override fun screenName(ctx: AppContext, activity: String?): String? {
        // 별칭 이름으로 와도 같은 액티비티다. 액티비티를 모르면(서비스가 그 앱이 떠 있는 동안 다시 시작됨) 하나뿐인 그 액티비티로 본다
        val act = activity?.takeUnless { it == ALIAS } ?: MAIN
        if (act != MAIN) return act
        // 맨 앞 창이 그 앱이 아니면(알림 창 등) 알 수 없다
        if (ctx.root == null) return null
        return if (LibreraX.viewerIn(ctx.windows())) "$MAIN#$VIEWER" else MAIN
    }

    override fun onEnterViewer(ctx: AppContext, from: String?, again: Boolean) {
        if (!again) enteredAt = ctx.now
        super.onEnterViewer(ctx, from, again)
    }

    override fun onViewer(ctx: AppContext) {
        val shown = readShown(ctx) ?: return
        logShown(ctx, shown)
        retryProgress(ctx, shown.progress)
        val title = shown.title
        val since = enteredAt
        if (title != null) enteredAt = null
        // 들어와 처음 읽은 제목이 앞서 편 책이 아니면 들어온 때부터 그 책(제목 못 읽은 칸으로 책 모름이 된 때가 있으면 바탕대로 그때부터)
        if (title != null && since != null && ctx.unknownOpenAt == null && ctx.recorder.recordedBook(ctx.pkg)?.hasTitle(title) != true) {
            ctx.opened(Seen(title, shown.author, shown.progress), since)
            return
        }
        recordShown(ctx, shown)
        askRecent(ctx, listOfNotNull(ctx.recorder.recordedBook(ctx.pkg)), null)
    }

    /** 서재가 멈췄다: 바탕대로 읽고, 보이는 칸 가운데 이 앱으로 넣은 책에 표지나 경로가 없으면 받는다 */
    override fun onShelf(ctx: AppContext) {
        super.onShelf(ctx)
        val lookup = ctx.recorder.lookup
        val shown = ctx.root?.let(app::shelfCells).orEmpty()
            .mapNotNull { c -> lookup.byTitle(c.title)?.takeIf { it.app?.substringBefore('/') == ctx.pkg } }
        askRecent(ctx, shown + listOfNotNull(ctx.recorder.recordedBook(ctx.pkg)), null)
    }

    override fun afterOpened(ctx: AppContext, book: ShelfBook) = askRecent(ctx, listOf(book), book)

    /** 표지는 LibreraX 가 내주는 그림을 쓴다(서재 칸은 같은 그림을 작게 보이거나, 표지가 없으면 LibreraX 가 글자로 그린 것이다) */
    override fun capturesCovers(ctx: AppContext): Boolean = false

    /**
     * LibreraX 의 최근 책을 받아 [books] 가운데 표지가 없는 책의 표지를 붙이고, 경로를 모르는 책과 [opened](편 책) 의 경로를 적는다. 한 번에 하나씩 묻는다:
     * 받는 중이면 편 책만 기억해 두었다가 끝난 뒤 다시 묻는다
     */
    private fun askRecent(ctx: AppContext, books: List<ShelfBook>, opened: ShelfBook?) {
        if (coverBusy) {
            if (opened != null) pendingOpened = opened
            return
        }
        val lookup = ctx.recorder.lookup
        val all = books.distinctBy { it.id }
        val need = all.filter { !lookup.hasCover(it.id) && (coverTries[it.id] ?: 0) < TRIES }
        val needPath = all.filter { it.id != opened?.id && LibreraX.bookPath(ctx.context, it.id) == null && (pathTries[it.id] ?: 0) < TRIES }
        if (need.isEmpty() && needPath.isEmpty() && opened == null) return
        need.forEach { coverTries[it.id] = (coverTries[it.id] ?: 0) + 1 }
        needPath.forEach { pathTries[it.id] = (pathTries[it.id] ?: 0) + 1 }
        coverBusy = true
        LibreraX.recent(ctx.context) { items ->
            val paths = (listOfNotNull(opened) + needPath).map { b ->
                val item = LibreraX.recentFor(items, b)
                val saved = item?.let { LibreraX.saveBookPath(ctx.prefs, b.id, it.path) } == true
                when {
                    items.isEmpty() -> "no list"
                    item == null -> "none"
                    else -> LibreraX.pathKind(item.path) + if (saved) "" else " skip"
                }
            }
            // 홈에서 그 책이 아니라 앱이 열리면 경로를 받았는지·어떤 모양인지 기록 보기로 본다(한 번 물을 때 한 줄)
            if (paths.isNotEmpty()) ctx.log(WatchLog.Kind.NOTE, "path " + paths.distinct().joinToString(", "))
            val found = need.mapNotNull { b -> LibreraX.recentFor(items, b)?.cover?.let { b to it } }
            if (found.isEmpty()) {
                if (need.isNotEmpty()) ctx.log(WatchLog.Kind.NOTE, "cover " + if (items.isEmpty()) "no list" else "none")
                done(ctx)
                return@recent
            }
            val resolver = ctx.context.contentResolver
            ctx.inBackground({ found.map { (b, uri) -> b to BookFiles.streamCover { resolver.openInputStream(uri) ?: error("No cover") } } }) { read ->
                val covers = read.getOrNull().orEmpty().mapNotNull { (b, jpeg) -> jpeg?.let { b to it } }
                ctx.log(WatchLog.Kind.NOTE, "cover ${covers.size}/${found.size}")
                // 받는 사이 사용자가 표지를 붙였으면 덮지 않는다
                for ((b, jpeg) in covers) if (!lookup.hasCover(b.id)) ctx.recorder.cover(b.id, jpeg)
                done(ctx)
            }
        }
    }

    /** 받기를 마쳤다: 그사이 편 책이 있으면 그 책으로 다시 묻는다 */
    private fun done(ctx: AppContext) {
        coverBusy = false
        pendingOpened?.let { pendingOpened = null; askRecent(ctx, listOf(it), it) }
    }

    private companion object {
        /** 책 한 권의 표지·경로를 받아 보는 횟수(서비스가 붙어 있는 동안) */
        const val TRIES = 5
    }
}

private const val MAIN = "com.librerax.ComposeMainActivity"
/** 다른 앱이 책을 열 때 쓰는 [MAIN] 의 별칭 */
private const val ALIAS = "com.librerax.reader.ReaderActivity"
/** 읽는 화면의 이름(요소 이름이 아니라 [LibreraX.showsViewer] 로 가른다) */
private const val VIEWER = "reader"
