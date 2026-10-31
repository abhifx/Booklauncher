package com.woody.booklauncher.readers

import android.view.accessibility.AccessibilityNodeInfo
import com.woody.booklauncher.WatchLog

/**
 * 책 상세에서 읽는 화면으로 가는 앱의 판단과 기억(쓰는 앱: BookFusion·밀리 e-ink·Mihon·Kahon·Kobo·Futon·Kotatsu. 바꾸면 모두 시험한다).
 * 서재 화면 안의 책 상세([detail]: BookFusion 책 상세, 밀리 검색 상세, Mihon 작품 상세)에서 `Read`·`바로 읽기`를 누르면 그 책을 편 것으로 본다(그 단추에는 글자가 없다).
 * 상세는 멈추기를 기다리지 않고 1초마다 기억하고, 단추를 누르면 상세가 먼저 사라지기도 해(BookFusion) 사라진 뒤 [KEEP_MS] 동안은 둔다.
 * 상세를 마지막으로 본 뒤 다른 칸을 눌렀으면 그 칸이 먼저다(사라진 때는 1초마다 읽어 늦게 적힐 수 있어 본 때로 가른다). [clickOnly]: 읽는 화면에 제목이 없어 누른 칸으로만 책을 아는 앱(BookFusion) — 누른 칸도 상세도 모른 채
 * 서재에서 넘어오면 책 모름(앞서 편 책에 붙이지 않게).
 */
open class DetailReader(app: ReaderApp, private val clickOnly: Boolean, private val detail: (AccessibilityNodeInfo) -> Cell?) : ScreenReader(app) {
    /** 보고 있는 책 상세 */
    private var book: Cell? = null
    /** 그 상세가 화면에서 사라진 때 */
    private var gone: Long? = null
    /** 그 상세를 마지막으로 본 때 */
    private var seenAt = 0L
    private var lastRead = 0L

    /** 기억한 책 상세가 있는가(앱 어댑터가 [keepsHidden] 을 정할 때) */
    protected val remembers: Boolean get() = book != null
    /** 기억한 상세가 지금 보이는가(사라졌다고 적지 않았다) */
    protected val detailShown: Boolean get() = book != null && gone == null

    /**
     * [gone] 에 사라진 상세를 안 보여도 기억해 둘 때인가([KEEP_MS] 가 지나도 잊지 않는다. 그 뒤 누른 칸이 먼저인 것은 그대로). 기본: 아니다
     */
    protected open fun keepsHidden(ctx: AppContext, gone: Long): Boolean = false

    /**
     * 기억한 상세(보이거나 사라진 지 [KEEP_MS] 안·[keepsHidden], 마지막으로 본 뒤 다른 칸을 누르지 않았을 때)면 그 책, 없으면 바탕(누른 칸).
     * 둘 다 없고 [clickOnly] 면 서재에서 넘어왔을 때 책 모름
     */
    override fun onEnterViewer(ctx: AppContext, from: String?, again: Boolean) {
        val now = ctx.now
        val gone = gone.also { gone = null }
        val clickedLater = (clicked?.second ?: 0L) > seenAt
        val web = book.also { book = null }
            ?.takeIf { !clickedLater && (gone == null || now - gone < KEEP_MS || keepsHidden(ctx, gone)) }
            ?.also { openCell(ctx, it) }
        val clickedCell = takeClicked(ctx)
        clickedCell?.let { openCell(ctx, it) }
        if (clickOnly && web == null && clickedCell == null && from in app.shelves) ctx.unknown(now)
    }

    /**
     * 책 칸이 아닌 것을 눌렀다: `바로 읽기`일 수 있으니 지금 보이는 책 상세를 기억하고 표지를 찍어 둔다
     * (웹 화면은 다 그려진 뒤 알림이 없을 때가 있어, 1초마다 기억해 둔 것이 비어 있을 수 있다).
     */
    override fun onClickNoCell(ctx: AppContext) {
        val root = ctx.root ?: return
        val web = detail(root) ?: return
        book = web
        gone = null
        seenAt = ctx.now
        val found = ctx.recorder.lookup.byTitle(web.title)
        val need = if (found == null) !ctx.recorder.hasPendingTitle(web.title) else !ctx.recorder.lookup.hasCover(found.id)
        if (web.thumb != null && need) ctx.capture(listOf(CoverSpot(web.title, web.thumb)))
    }

    override fun beforeOpened(ctx: AppContext) {
        super.beforeOpened(ctx)
        book = null
        gone = null
    }

    /** 책 상세는 `바로 읽기`를 누르자마자 사라지므로 멈추기를 기다리지 않고 1초마다 기억해 둔다 */
    override fun onShelfLive(ctx: AppContext, force: Boolean) {
        val now = ctx.now
        if (force || now - lastRead >= THROTTLE_MS) {
            lastRead = now
            ctx.root?.let { root ->
                val web = detail(root)
                if (web != null) { book = web; gone = null; seenAt = now }
                else if (book != null) {
                    val since = gone ?: now.also { gone = it }
                    if (now - since >= KEEP_MS && !keepsHidden(ctx, since)) { book = null; gone = null }
                }
            }
        }
        super.onShelfLive(ctx, force)
    }

    /**
     * 서재가 멈췄다: 책 상세를 다시 기억하고, 그 표지(목록에 없거나 표지가 없으면)를 칸 표지와 한 번에 찍는다. 목록에 없는 책은 잘라 두었다가
     * 들어올 때 붙인다(`바로 읽기`를 누를 때 찍으면 읽는 화면이 먼저 떠 검게 찍히기도 한다, 2026-10-24 페블 밀리).
     */
    override fun onShelf(ctx: AppContext) {
        val root = ctx.root ?: return ctx.noWindow()
        val seen = readShelf(root)
        ctx.log(WatchLog.Kind.SHELF, "${seen.size}")
        val spots = mutableListOf<CoverSpot>()
        val web = detail(root)
        // 상세가 없으면 사라진 때를 적는다(1초마다 읽기가 아직 돌지 않았어도)
        if (web == null) {
            if (book != null && gone == null) gone = ctx.now
        } else {
            book = web
            gone = null
            seenAt = ctx.now
            val found = ctx.recorder.lookup.byTitle(web.title)
            val need = if (found == null) !ctx.recorder.hasPendingTitle(web.title) else !ctx.recorder.lookup.hasCover(found.id)
            if (web.thumb != null && need) spots += CoverSpot(web.title, web.thumb)
        }
        shelfStatuses(ctx, seen, spots)
        if (!app.secure) ctx.capture(spots)
    }

    /** 찍은 뒤: 책 상세의 표지는 그 상세가 그 자리에 그대로 있을 때만, 서재 칸 표지는 바탕 규칙으로(서재 칸끼리만 맞춰 보면 상세 표지가 늘 버려졌다) */
    override fun stillSpots(ctx: AppContext, spots: List<CoverSpot>): List<CoverSpot> {
        val web = ctx.root?.let(detail)
        val (shown, rest) = spots.partition { web != null && it.title == web.title && it.rect == web.thumb }
        return shown + super.stillSpots(ctx, rest)
    }

    companion object {
        /** 책 상세가 사라진 뒤 그 책을 기억해 두는 때 */
        private const val KEEP_MS = 3000L
    }
}
