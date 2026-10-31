package com.woody.booklauncher.readers

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.woody.booklauncher.WatchLog
import com.woody.booklauncher.sameTitle

/**
 * 알라딘: 격자 보기 칸에는 제목·표지만 있고 눌러도 알림이 없다(서재 아래 '최근 읽은 책'으로 안다. 그 줄은 늦게 바뀐다).
 * 목록 보기 칸에는 제목·저자·진행률·대여 기한이 있고 누르면 알림이 온다. 읽는 화면은 메뉴의 제목으로 알고,
 * 아래 쪽 표시(`6 / 368　 저자소개`, 쪽·장 이름)로 진행률을 셈한다. PDF·만화(CPUB)는 다른 읽는 화면(`cpviewer.ViewerActivity`)인데
 * 메뉴의 제목 이름은 같고 쪽 표시만 `viewermenu_text_page_info`(`6 / 368`)다.
 * 서재 편집(`편집` → 전체·이동·삭제)에서는 격자 보기 칸을 눌러도 알림이 오고 칸에 고르기 동그라미가 그려진다. 판단과 기억은 [AladinReader].
 */
object Aladin : ReaderApp(
    pkg = "kr.co.aladin.ebook", classPrefix = "kr.co.aladin",
    shelves = setOf("kr.co.aladin.ebook.MainActivity"),
    viewers = setOf(
        "kr.co.aladin.epubreader.readonbook.bookrender.ReadONBookRenderActivity",
        "kr.co.aladin.ebook.cpviewer.ViewerActivity",
    ),
    titles = listOf("txt_title", "text_title"), thumbs = listOf("img_cover"), authors = listOf("text_author"), progress = "txt_read_percent",
    due = "text_rent_date", viewerProgress = listOf("bookrender_txt_page_onepage", "viewermenu_text_pageinfo", "viewermenu_text_page_info"),
    viewerTitle = "viewer_header_title",
    eagerCovers = true, shelfEditing = "edit_cl_btn_container",
) {
    override fun newAdapter(): ReaderAdapter = AladinReader()

    /** 서재 아래 '최근 읽은 책'의 제목(없으면 null) */
    fun recentTitle(root: AccessibilityNodeInfo): String? =
        root.byId(pkg, RECENT).firstOrNull()?.text?.let(::plain)?.takeIf { it.isNotEmpty() }

    private const val RECENT = "reading_book_tv_book_title"
}

/**
 * 알라딘의 판단과 기억: 격자 보기 칸은 눌러도 알림이 없어, 서재를 떠나 읽고 돌아오면 서재 아래 '최근 읽은 책' 줄로 방금 읽은 책을 안다
 * (읽으러 간 때부터 그 책으로). 줄이 읽으러 가기 전 그대로면(알라딘은 줄을 늦게 바꾸고, 내 파일은 줄에 오르지 않는다) 믿지 않고 바뀌기를 기다린다.
 * 서재를 떠난 뒤 메뉴로 처음 안 책은 앞서 편 책과 같아도 편 것으로 보고(같은 책을 다시 읽으면 줄이 그대로라 돌아와서는 알 수 없다), 그러면 돌아와 줄로 다시 판단하지 않는다.
 */
class AladinReader : ScreenReader(Aladin) {
    /** 서재에서 읽는 화면으로 갔다 왔다 */
    private var readSince = false
    /** 서재를 떠나 읽는 화면이 처음 열린 때 */
    private var readerAt: Long? = null
    /** 읽는 화면 메뉴로 이미 책을 알았다 */
    private var readKnown = false
    /** 서재로 돌아왔는데 '최근 읽은 책' 줄이 아직 읽으러 가기 전 그대로다(바뀌기를 기다린다) */
    private var recentWait = false
    private var lastRecentRead = 0L

    override fun onEnterViewer(ctx: AppContext, from: String?, again: Boolean) {
        super.onEnterViewer(ctx, from, again)
        // 서재를 떠나 처음 연 읽는 화면의 때만 적는다(읽는 중 목차 같은 다른 화면에 갔다 와도 처음부터 그 책으로 세게).
        // 줄이 바뀌기를 기다리다 서재에서 다시 읽으러 갔으면 이번 책의 때로 다시 적는다.
        if ((!readSince).also { readSince = true } || recentWait.also { recentWait = false }) readerAt = ctx.now
    }

    override fun onShelfLive(ctx: AppContext, force: Boolean) {
        // 서재를 잠깐만 보고 책으로 넘어가도 읽으러 가기 전의 '최근 읽은 책' 줄을 알도록 멈추기를 기다리지 않는다(1초에 한 번).
        val now = ctx.now
        if (force || now - lastRecentRead >= THROTTLE_MS) {
            lastRecentRead = now
            ctx.root?.let { rememberRecent(ctx, it) }
        }
        super.onShelfLive(ctx, force)
    }

    /** 서재가 멈췄다: 읽고 돌아왔으면 '최근 읽은 책' 줄로 방금 읽은 책을 안 뒤(칸 진행률·표지보다 먼저) 바탕과 같이 */
    override fun onShelf(ctx: AppContext) {
        val root = ctx.root ?: return ctx.noWindow()
        val seen = readShelf(root)
        ctx.log(WatchLog.Kind.SHELF, "${seen.size}")
        val recent = Aladin.recentTitle(root)
        if (readSince) {
            if (readKnown) {
                readKnown = false
                readSince = false
            } else if (recent != null && recentBefore(ctx).let { it == null || it == recent }) {
                // 몰랐으면 처음 본 줄을 읽으러 가기 전의 줄로 적어 둔다(그래야 바뀌면 알아본다).
                setRecentBefore(ctx, recent)
                if (!recentWait) {
                    recentWait = true
                    ctx.log(WatchLog.Kind.RECENT_WAIT, recent)
                }
            } else recent?.let { title ->
                readSince = false
                recentWait = false
                openCell(ctx, seen.firstOrNull { sameTitle(it.title, title) } ?: Cell(title, "", -1, -1, null, Rect()), readerAt)
            }
        }
        rememberRecent(ctx, root)
        val spots = mutableListOf<CoverSpot>()
        shelfStatuses(ctx, seen, spots)
        ctx.capture(spots)
    }

    /** 메뉴 제목: 서재를 떠난 뒤 메뉴로 처음 안 책이면 앞서 편 책과 같아도 편 것으로(맨 앞으로) */
    override fun onViewer(ctx: AppContext) {
        val shown = readShown(ctx) ?: return
        logShown(ctx, shown)
        retryProgress(ctx, shown.progress)
        val first = shown.title != null && readSince && (!readKnown).also { readKnown = true }
        recordShown(ctx, shown, force = first)
    }

    /** 읽으러 가기 전에 본 서재의 '최근 읽은 책' */
    private fun recentBefore(ctx: AppContext) = ctx.prefs.getString("recent_${ctx.pkg}", null)

    /** [title] 이 null 이면 지운다(모른다) */
    private fun setRecentBefore(ctx: AppContext, title: String?) {
        if (recentBefore(ctx) != title) ctx.prefs.edit().putString("recent_${ctx.pkg}", title).apply()
    }

    /**
     * 읽으러 가기 전의 '최근 읽은 책' 줄을 적어 둔다(돌아와 아직 기다리는 동안은 고치지 않는다).
     * 줄이 안 보이면(아직 안 그려짐) 지운다: 앞서 적은 줄과 비교하면 그사이 바뀐 줄을 방금 편 책으로 잘못 본다.
     */
    private fun rememberRecent(ctx: AppContext, root: AccessibilityNodeInfo) {
        if (readSince) return
        setRecentBefore(ctx, Aladin.recentTitle(root))
    }

}
