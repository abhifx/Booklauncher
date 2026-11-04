package com.woody.pebbledesk.readers

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.view.accessibility.AccessibilityNodeInfo
import com.woody.pebbledesk.BookSearch
import com.woody.pebbledesk.HomeSignals
import com.woody.pebbledesk.ShelfBook
import com.woody.pebbledesk.Storage
import com.woody.pebbledesk.WatchCore
import com.woody.pebbledesk.normTitle
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Wattpad(11.42.0, 웹 소설. 한국 Play 스토어에는 없다): 책 한 권은 이야기 하나다(화(part)마다 따로 책을 만들지 않는다). 로그인해야 쓸 수 있다.
 * - 서재(`library.activities.LibraryActivity`, Current Reads 탭): 칸마다 제목 `story_title`·표지 `dimmable_cover_cover`, 목록 보기에는 저자 `story_author` 도.
 *   칸을 누르면 바로 읽는 화면이 열린다. 칸 안의 오프라인 단추(`offline_state`)·메뉴(`overflow_menu`)도 칸 클릭으로 오므로 읽는 화면이 열려야 편 것으로 본다([openOnViewer]).
 *   서재 아래 추천 줄(`title`)은 내 책이 아니라 칸으로 보지 않는다.
 * - 이야기 상세(`discover.storyinfo.activities.StoryInfoComposeActivity`, Compose 라 이름이 없다. 옆으로 넘겨 다른 이야기를 본다): 표지(설명이 제목) 뒤에
 *   제목 글자, 그 뒤 묶음 안에 저자(Wattpad 사용자 이름)([detail]). `Start reading`·`Continue reading` 으로 읽는 화면을 열면 그 상세의 이야기([WattpadReader]).
 * - 읽는 화면(`reader.ReaderActivity`): 화 처음부터 열면 위 툴바(`wattpad_activity_toolbar`)에 이야기 제목이 보인다(넘기면 툴바가 숨어 요소가 없어지고,
 *   읽던 화 중간에서 이어 열거나 앱을 다시 열면 툴바가 숨은 채 열린다). 그래서 제목은 서재 칸·상세로 알고, 툴바가 보이면 그것으로도 안다([tellsTitle]).
 *   화마다 맨 위에 화 이름(`reader_part_title`, 작가가 붙인 이름 그대로 `Chapter I`)이 있다. 진행률(%)은 없어(아래 막대 `progress_seek_bar` 는
 *   값을 내주지 않는다) 화 이름을 읽는 화로 둔다([currentPart]). 제목을 모르는 채 열린 읽는 화면은 그 앱에서 마지막으로 편 책으로 이어 간다.
 * 표지는 서재 칸·상세에서 자른다(읽는 화면 위의 그림은 표지를 크게 자른 것이라 쓰지 않는다).
 * 홈에서는 `wattpad://story/<이야기 번호>` 로 읽던 자리를 바로 연다([open]). 번호는 화면에 없어, 저자를 아는 책이 들어오면 Wattpad 공개 검색으로
 * 제목·저자(사용자 이름)가 같은 이야기를 한 번 찾아 둔다([WattpadReader.findStory]). 같은 제목이 많아 저자를 모르면 찾지 않는다.
 */
object Wattpad : ReaderApp(
    pkg = "wp.wattpad", classPrefix = "wp.wattpad",
    shelves = setOf(
        "wp.wattpad.library.activities.LibraryActivity",
        "wp.wattpad.discover.storyinfo.activities.StoryInfoComposeActivity",
    ),
    viewers = setOf("wp.wattpad.reader.ReaderActivity"),
    titles = listOf("story_title"), thumbs = listOf("dimmable_cover_cover"), authors = listOf("story_author"), progress = null, due = null,
    viewerProgress = emptyList(), openOnViewer = true,
) {
    override fun newAdapter(): ReaderAdapter = WattpadReader(this)

    /**
     * 홈에서: 이야기 번호를 알면 그 이야기를 읽던 자리에서 연다(상세를 거치지 않고 툴바가 숨은 읽는 화면이 열려, 연 책을 자동 추가에 알린다).
     * 모르거나 BOOX 가 얼린 앱이면 앱을 연다
     */
    override fun open(ctx: HomeContext, book: ShelfBook): OpenResult {
        val id = storyId(ctx.context, book.id)?.takeIf { !ctx.frozen } ?: return OpenResult.Launch
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("wattpad://story/$id")).setPackage(pkg)
        return OpenResult.Start(intent, started = { HomeSignals.bookOpened(pkg, book) })
    }

    /** 책 [bookId] 의 이야기 번호(찾아 둔 것). 모르면 null */
    private fun storyId(context: Context, bookId: String): String? =
        Storage.of(context).getSharedPreferences(WatchCore.WATCH_PREFS, Context.MODE_PRIVATE).getString(storyPref(bookId), null)

    internal fun storyPref(bookId: String) = "wattpad_story_$bookId"

    /** 공개 검색 주소: 제목 [title] 로 찾은 이야기들의 번호·제목·사용자 이름 */
    internal fun searchUrl(title: String): String =
        "https://www.wattpad.com/api/v3/stories?query=${enc(title)}&limit=$SEARCH_LIMIT&fields=${enc("stories(id,title,user(name))")}"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /**
     * 검색 결과 [json] 가운데 사용자 이름(대소문자 무시)이 같고 제목이 같은 이야기가 하나뿐이면 그 번호. 아니면 null.
     * 제목은 글자 그대로(대소문자·앞뒤 공백 무시) 같은 것을 먼저 보고, 없으면 정리한 제목([normTitle], 부제를 뗀다)으로 본다
     * (같은 작가의 `Book 1: The Beginning`·`Book 1: Rebirth` 는 정리하면 같아진다)
     */
    internal fun matchStory(json: String, title: String, author: String): String? {
        val stories = runCatching { JSONObject(json).optJSONArray("stories") }.getOrNull() ?: return null
        val mine = (0 until stories.length()).mapNotNull { stories.optJSONObject(it) }
            .filter { it.optJSONObject("user")?.optString("name").equals(author, ignoreCase = true) }
        fun only(same: (String) -> Boolean) = mine.filter { same(it.optString("title")) }.map { it.optString("id") }.distinct()
        val exact = only { it.trim().equals(title.trim(), ignoreCase = true) }
        val key = normTitle(title)
        return (exact.ifEmpty { only { normTitle(it) == key } })
            .singleOrNull()?.takeIf { id -> id.isNotEmpty() && id.all(Char::isDigit) }
    }

    /** 화 처음부터 연 읽는 화면은 툴바에 이야기 제목이 보인다(서재 칸·상세 없이 연 이야기도 알 수 있다) */
    override val tellsTitle get() = true

    /** 읽는 화면: 툴바가 보이면 이야기 제목, 화면 위쪽 반에 화 머리가 보이면 그 화 이름. 진행률은 없다 */
    override fun readViewer(windows: AppWindows): ViewerInfo {
        val title = windows.all.flatMap { it.byId(pkg, TOOLBAR) }.firstNotNullOfOrNull(::toolbarTitle)
        val chapter = windows.all.firstNotNullOfOrNull { root -> currentPart(root.byId(pkg, PART_TITLE), root.bounds()) }
        return ViewerInfo(title, "", -1, chapter = chapter.orEmpty())
    }

    /** 툴바 [toolbar] 바로 아래의 글자(이야기 제목. 뒤로 단추·받기·글자 설정·목차 단추에는 글자가 없다). 툴바가 숨었으면 null */
    internal fun toolbarTitle(toolbar: AccessibilityNodeInfo): String? {
        if (!toolbar.isVisibleToUser) return null
        return (0 until toolbar.childCount).firstNotNullOfOrNull { i ->
            toolbar.getChild(i)?.takeIf { it.className?.endsWith("TextView") == true && it.isVisibleToUser }?.text?.let(::plain)?.takeIf { it.isNotEmpty() }
        }
    }

    /**
     * 화 머리들 [parts] 가운데 지금 읽는 화의 이름: 창 [area] 의 위쪽 반에 있는 것 중 가장 아래(다음 화의 머리가 아래 반에 막 보이면 아직 앞 화를 읽는다).
     * 위쪽 반에 없으면 null(화 중간을 읽는 중: 앞서 적은 화 그대로 둔다)
     */
    internal fun currentPart(parts: List<AccessibilityNodeInfo>, area: Rect): String? {
        val middle = area.top + area.height() / 2
        return parts.filter { it.isVisibleToUser }
            .mapNotNull { p -> p.text?.let(::plain)?.takeIf { it.isNotEmpty() }?.let { it to p.bounds() } }
            .filter { it.second.top < middle }
            .maxByOrNull { it.second.top }?.first
    }

    /**
     * 이야기 상세(Compose): 어느 묶음의 자식 가운데 표지 묶음(안에 설명이 붙은 그림) 바로 뒤에 그 설명과 같은 글자가 오면 그것이 제목, 그 뒤 묶음 안의 첫 글자가 저자.
     * 옆 이야기로 넘기는 중이라 표지가 창 밖에 걸치면 표지는 자르지 않는다(넘기는 중에 찍은 것은 찍은 뒤 상세가 그 자리에 그대로인지 다시 보아 버린다, [DetailReader.stillSpots]). 없으면 null
     */
    fun detail(root: AccessibilityNodeInfo): Cell? {
        val screen = root.bounds()
        // 아이들은 한 번만 가져와 머리 찾기와 아래로 내려가기에 같이 쓴다(요소를 가져올 때마다 시스템을 거친다)
        fun walk(n: AccessibilityNodeInfo, depth: Int): Cell? {
            val kids = children(n)
            return header(kids, screen) ?: if (depth < DEEP_WALK_DEPTH) kids.firstNotNullOfOrNull { walk(it, depth + 1) } else null
        }
        return walk(root, 0)
    }

    /** 한 묶음의 아이들 [kids] 가운데 표지 묶음·제목·저자 묶음이 이어지면 그 이야기 */
    private fun header(kids: List<AccessibilityNodeInfo>, screen: Rect): Cell? {
        for ((i, k) in kids.withIndex()) {
            val cover = coverImage(k) ?: continue
            val name = cover.contentDescription?.let(::plain)?.takeIf { it.isNotEmpty() } ?: continue
            val title = kids.getOrNull(i + 1)?.takeIf { it.className?.endsWith("TextView") == true && it.text?.let(::plain) == name } ?: continue
            val author = kids.getOrNull(i + 2)?.let { r -> children(r).firstNotNullOfOrNull { it.text?.let(::plain)?.takeIf { t -> t.isNotEmpty() } } }
            val at = cover.bounds().takeIf { usableThumb(it) && screen.contains(it) }
            return Cell(plain(title.text), author.orEmpty(), -1, -1, at, Rect())
        }
        return null
    }

    /** [n] 이 글자 없는 묶음이고 그 안(몇 단계 아래)에 설명이 붙은 그림이 있으면 그 그림 */
    private fun coverImage(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (!n.text.isNullOrEmpty()) return null
        fun walk(c: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
            if (c.className?.endsWith("ImageView") == true && !c.contentDescription.isNullOrBlank()) return c
            if (depth >= COVER_DEPTH || c.childCount != 1) return null
            return c.getChild(0)?.let { walk(it, depth + 1) }
        }
        return walk(n, 0)
    }

    private fun children(n: AccessibilityNodeInfo) = (0 until n.childCount).mapNotNull { n.getChild(it) }
}

/**
 * Wattpad 어댑터: 상세에서 읽는 화면으로 가면 그 상세의 이야기([DetailReader], 표지·저자도 상세에서), 서재 칸은 바탕 규칙.
 * 상세를 기억하는 것은 상세에서 바로 읽는 화면으로 올 때만이다: 홈 탭·검색은 서재 화면이 아니라 상세가 사라진 것을 보지 못해, 상세를 보고 홈 탭에서 연
 * 다른 이야기(툴바가 숨은 이어 읽기)에 붙지 않게 서재 화면 밖에서 왔으면 누른 칸·상세 기억을 지운다.
 * 서재에 있는 이야기의 상세를 보면 빈 저자를 채운다(격자 보기 서재에는 저자가 없다).
 */
class WattpadReader(private val wattpad: Wattpad) : DetailReader(wattpad, clickOnly = false, wattpad::detail) {
    /** 이 서비스에서 이야기 번호를 찾아본 책(실패해도 서비스가 다시 붙을 때까지 다시 묻지 않는다) */
    private val searched = mutableSetOf<String>()

    /**
     * 홈에서 링크로 연 책이면 그 책(기록하기 전에 [beforeOpened] 로 상세·누른 칸 기억이 비워진다), 아니면 상세·누른 칸
     * (서재 화면 밖에서 왔으면 그 기억은 지운다)
     */
    override fun onEnterViewer(ctx: AppContext, from: String?, again: Boolean) {
        val home = HomeSignals.take(ctx.pkg)?.takeIf { ctx.now - it.second < HOME_OPEN_MS }?.first
        if (!again && from !in wattpad.shelves) beforeOpened(ctx)
        if (home != null) openCell(ctx, home) else super.onEnterViewer(ctx, from, again)
    }

    override fun onShelf(ctx: AppContext) {
        super.onShelf(ctx)
        val web = ctx.root?.let(wattpad::detail)?.takeIf { it.author.isNotEmpty() } ?: return
        val book = ctx.recorder.lookup.byTitle(web.title) ?: return
        if (book.author.isBlank()) {
            ctx.recorder.status(book.id, -1, author = web.author)
            findStory(ctx, book.copy(author = web.author))
        }
    }

    override fun afterOpened(ctx: AppContext, book: ShelfBook) = findStory(ctx, book)

    /**
     * 홈에서 바로 열 이야기 번호를 뒤에서 한 번 찾아 둔다: 이미 있거나 저자를 모르면(같은 제목이 많다) 하지 않는다.
     * 앱이 보여 준 제목으로 공개 검색해 제목·사용자 이름이 같은 이야기가 하나일 때만([Wattpad.matchStory])
     */
    private fun findStory(ctx: AppContext, book: ShelfBook) {
        val author = book.author.trim()
        val pref = wattpad.storyPref(book.id)
        if (author.isEmpty() || ctx.prefs.contains(pref) || !searched.add(book.id)) return
        val title = book.seenTitle.ifBlank { book.title }
        ctx.inBackground({ wattpad.matchStory(String(BookSearch.download(wattpad.searchUrl(title))), title, author) }) { result ->
            result.getOrNull()?.let { id -> ctx.prefs.edit().putString(pref, id).apply() }
        }
    }
}

/** 홈에서 링크로 연 뒤 이 안에 읽는 화면이 열리면 그 책으로 본다 */
private const val HOME_OPEN_MS = 30_000L
/** 공개 검색에서 받는 이야기 수(제목이 같은 이야기가 많아도 저자로 고른다) */
private const val SEARCH_LIMIT = 30

private const val TOOLBAR = "wattpad_activity_toolbar"
private const val PART_TITLE = "reader_part_title"
/** 상세의 표지 묶음에서 그림까지 내려가는 단계(묶음 셋 아래에 그림) */
private const val COVER_DEPTH = 4
