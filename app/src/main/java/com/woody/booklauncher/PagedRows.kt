package com.woody.booklauncher

import android.annotation.SuppressLint
import android.content.Context
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import android.widget.TextView

/** 쪽으로 넘기는 것(쪽 표시 [pageLabel]·[pagesClick] 가 쓴다). [next]·[prev] 는 끝에서 멈춘다. */
interface Paged {
    val page: Int
    val pageCount: Int
    fun next()
    fun prev()
}

/** 한 쪽 넘긴다(페이지 버튼). 한 쪽뿐이면 넘길 것이 없다(false). */
fun Paged.turn(next: Boolean): Boolean {
    if (pageCount <= 1) return false
    if (next) next() else prev()
    return true
}

/**
 * 스크롤 없이 페이지 단위로 넘기는 목록. 주어진 높이에 들어가는 만큼만 줄을 그리고, 줄 사이에 가는 회색 선을 둔다.
 * 전자잉크라 넘길 때 애니메이션을 쓰지 않는다.
 * [fixedRows] 가 0보다 크면 높이에 맞추지 않고 늘 그 줄 수(격자면 그 높이 안에 gridRows 줄)를 쓴다. 높이는 WRAP 으로 둔다. 나중에 [setShape] 로 바꿀 수 있다.
 * [insetDividers] 가 false 면 줄 사이 선을 좌우 여백 없이 끝까지 긋는다(이미 여백 안에 놓인 좁은 칸).
 * [dividers] 가 false 면 줄 사이 선을 긋지 않는다(표지 격자처럼 줄마다 칸이 나뉘어 보이는 목록). 높이 계산은 같다.
 */
class PagedRows(
    context: Context,
    private val listRowPx: Int,
    private var fixedRows: Int = 0,
    private val insetDividers: Boolean = true,
    private val dividers: Boolean = true,
    private val dividerMarginDp: Int = Ui.MARGIN,
    /** 남는 높이를 줄 수로 나눠 줄을 늘린다(홈 함께 읽는 책: 표지가 커져 줄 수가 줄어도 아래를 비우지 않게). */
    private val stretch: Boolean = false,
) : LinearLayout(context), Paged {
    private var rows: List<() -> View> = emptyList()
    private val dividerPx = context.dp(1)

    /**
     * 격자 모드: 목록일 때 들어갈 높이를 그대로 쓰고 그 안을 [gridRows] 줄로 나눈다(줄 사이 선 없음).
     * 덜 차도 그 높이를 비워 두어 머리(제목·선) 위치가 목록일 때와 같다. 0 이면 목록, [GRID_AUTO] 면 높이에 맞춰 목록 줄 수의 2/3.
     */
    private var gridRows = 0

    /** 격자 한 줄의 높이를 정해 둔 값(0 이면 목록 높이를 줄 수로 나눈다, [setShape]) */
    private var gridRowPx = 0

    /** 지금 한 줄의 높이(격자면 목록 높이를 나눈 값) */
    var rowHeightPx: Int = listRowPx
        private set

    /**
     * 격자 줄 수 [gridRows](0 이면 목록)와 고정 줄 수 [fixedRows] 를 함께 바꾼다. [gridRowPx] 가 0보다 크면 격자 한 줄을 그 높이로
     * (목록 높이를 나누지 않는다. 홈 자주 쓰는 앱 격자 1줄은 2줄일 때의 한 줄 높이 그대로).
     */
    fun setShape(gridRows: Int, fixedRows: Int, gridRowPx: Int = 0) {
        if (gridRows == this.gridRows && fixedRows == this.fixedRows && gridRowPx == this.gridRowPx) return
        this.gridRows = gridRows
        this.fixedRows = fixedRows
        this.gridRowPx = gridRowPx
        perPage = 0
        if (!fitRows(height)) render()
    }

    var perPage = 0
        private set
    override var page = 0
        private set
    override val pageCount: Int get() = if (perPage <= 0) 1 else maxOf(1, (rows.size + perPage - 1) / perPage)

    /** 목록 위에 함께 붙는 머리(제목·선). 들어갈 줄 수를 셀 때 그 높이를 뺀다. */
    var header: List<View> = emptyList()
        set(v) {
            if (v == field) return
            field = v
            if (!fitRows(height)) render()
        }

    /** 페이지나 페이지 수가 바뀌면 불린다(페이지 점·쪽 번호 갱신용). */
    var onPageChanged: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        if (fixedRows > 0) fitRows(0)
    }

    fun setRows(rows: List<() -> View>) {
        this.rows = rows
        render()
    }

    fun showPage(p: Int) {
        val target = p.coerceIn(0, pageCount - 1)
        if (target == page) return
        page = target
        render()
    }

    /** 다음·앞 쪽(끝에서 멈춘다. 쪽 표시의 `‹ ›` 와 맞게 처음·끝을 돌지 않는다) */
    override fun next() = showPage(page + 1)
    override fun prev() = showPage(page - 1)

    /** [index] 번째 줄이 있는 페이지 */
    fun pageOf(index: Int) = if (perPage <= 0) 0 else index / perPage

    private var revealIndex: Int? = null

    /** [index] 번째 줄이 있는 페이지를 보여 준다. 아직 줄 수를 모르면 정해진 뒤에 보여 준다. */
    fun reveal(index: Int) {
        if (perPage > 0) showPage(pageOf(index)) else revealIndex = index
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitRows(h)
    }

    /**
     * 높이 [h] 에 들어가는 줄 수를 다시 정한다. 레이아웃 중에는 자식을 바꾸지 않고, 화면에 그리기 직전에 채운다.
     * (다음 차례로 미루면 빈 목록을 한 번 그린 뒤 다시 그리게 되어 전자잉크가 깜빡인다.) 줄 수가 바뀌었으면 true.
     */
    private fun fitRows(h: Int): Boolean {
        if (fixedRows == 0 && h <= 0) return false
        val listFit = if (fixedRows > 0) fixedRows else maxOf(1, (h - headerHeight() + dividerPx) / (listRowPx + dividerPx))
        val fit: Int
        val rowPx: Int
        if (gridRows != 0) {
            fit = if (gridRows == GRID_AUTO) maxOf(1, listFit * 2 / 3) else minOf(gridRows, listFit)
            rowPx = if (gridRowPx > 0) gridRowPx else listHeightPx(listFit) / fit
        } else {
            fit = listFit
            rowPx = if (stretch && fixedRows == 0) {
                val rowsH = (h - headerHeight()).coerceAtLeast(0)
                ((rowsH - (fit - 1) * dividerPx).coerceAtLeast(0) / fit).coerceAtLeast(listRowPx)
            } else listRowPx
        }
        if (fit == perPage && rowPx == rowHeightPx) return false
        perPage = fit
        rowHeightPx = rowPx
        renderBeforeDraw()
        return true
    }

    /** 목록 [rows] 줄(줄 사이 선 포함)의 높이 */
    fun listHeightPx(rows: Int) = rows * (listRowPx + dividerPx) - dividerPx

    /** 머리 뷰 높이 합([measuredHeightIn]) */
    private fun headerHeight(): Int = header.sumOf { measuredHeightIn(it) }

    private var renderPending = false

    /** 이번 그리기를 건너뛰고 줄을 채운 뒤 다시 레이아웃해서 그린다. */
    private fun renderBeforeDraw() {
        if (renderPending) return
        renderPending = true
        beforeNextDraw { renderPending = false; render() }
    }

    private fun render() {
        removeAllViews()
        if (perPage <= 0) return
        revealIndex?.let { page = pageOf(it); revealIndex = null }
        header.forEach { addView(it) }
        page = page.coerceIn(0, pageCount - 1)
        val from = page * perPage
        val to = minOf(rows.size, from + perPage)
        for (i in from until to) {
            if (i > from && gridRows == 0) {
                // 선을 긋지 않아도 같은 높이를 비워 둔다(들어가는 줄 수 계산이 같도록).
                if (dividers) addView(context.hline(1, Ui.DIVIDER, insetDividers, dividerMarginDp)) else addView(View(context), lp(MATCH, dividerPx))
            }
            addView(rows[i](), lp(MATCH, rowHeightPx))
        }
        // 덜 찬 페이지도 같은 높이를 쓴다(아래를 비워 제목·선 위치가 움직이지 않게).
        // 목록은 페이지가 여러 개일 때만, 격자는 늘(목록과 같은 높이를 유지).
        val missing = if (pageCount > 1 || gridRows != 0 || fixedRows > 0) perPage - (to - from) else 0
        val gap = if (gridRows != 0) 0 else dividerPx
        if (missing > 0) addView(View(context), lp(MATCH, missing * (rowHeightPx + gap)))
        onPageChanged?.invoke()
    }

    companion object {
        /** [setShape]: 격자 줄 수를 높이에 맞춰 목록 줄 수의 2/3 로(홈 격자가 목록 세 줄 높이에 두 줄인 것과 같은 비율. 가로 화면의 자주 쓰는 앱) */
        const val GRID_AUTO = -1
    }
}

/**
 * 높이가 제각각인 묶음(단락·제목·항목)을 스크롤 없이 쪽으로 나눠 보여 준다. 묶음은 쪼개지 않고 들어가는 만큼 한 쪽에 담으며,
 * [Block.newPage] 인 묶음은 새 쪽에서 시작한다. 높이를 알게 되면 그리기 직전에 나눈다([PagedRows] 와 같이 빈 화면을 한 번 그리지 않게).
 */
class PagedBlocks(context: Context) : LinearLayout(context), Paged {
    class Block(val views: List<View>, val newPage: Boolean = false)

    private var blocks: List<Block> = emptyList()
    /** 쪽마다 담을 묶음. 높이를 모르면 비어 있다. */
    private var pages: List<List<Block>> = emptyList()
    private var splitPending = false

    override var page = 0
        private set
    override val pageCount: Int get() = maxOf(1, pages.size)

    /** 쪽이나 쪽 수가 바뀌면 불린다(쪽 번호 갱신용). */
    var onPageChanged: (() -> Unit)? = null

    init {
        orientation = VERTICAL
    }

    /** 묶음을 바꾼다(보던 쪽은 남는 데까지 그대로) */
    fun setBlocks(blocks: List<Block>) {
        this.blocks = blocks
        if (height > 0) split(height)
    }

    private fun showPage(p: Int) {
        val target = p.coerceIn(0, pageCount - 1)
        if (target == page) return
        page = target
        render()
    }

    override fun next() = showPage(page + 1)
    override fun prev() = showPage(page - 1)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 레이아웃 중에는 자식을 바꾸지 않고 그리기 직전에 나눈다.
        if (h > 0 && !splitPending) {
            splitPending = true
            beforeNextDraw { splitPending = false; split(height) }
        }
    }

    /** 높이 [h] 에 맞춰 쪽을 나누고 지금 쪽을 그린다 */
    private fun split(h: Int) {
        val out = mutableListOf<MutableList<Block>>()
        var used = 0
        for (b in blocks) {
            val bh = b.views.sumOf { measuredHeightIn(it) }
            val cur = out.lastOrNull()
            if (cur == null || (cur.isNotEmpty() && (b.newPage || used + bh > h))) {
                out += mutableListOf(b)
                used = bh
            } else {
                cur += b
                used += bh
            }
        }
        pages = out
        page = page.coerceIn(0, pageCount - 1)
        render()
    }

    private fun render() {
        removeAllViews()
        pages.getOrNull(page)?.forEach { b -> b.views.forEach(::addView) }
        onPageChanged?.invoke()
    }
}

/**
 * [v] 를 이 묶음에 넣었을 때의 높이(위아래 여백 포함). 실제 배치와 같은 조건(정해진 높이·좌우 여백)으로 잰다.
 * 레이아웃 중에 다른 조건으로 재면 그 값이 그대로 남아 선(높이 2dp)이 0 으로 그려진다.
 */
private fun LinearLayout.measuredHeightIn(v: View): Int {
    val m = v.layoutParams as? ViewGroup.MarginLayoutParams
    val lpW = v.layoutParams?.width ?: MATCH
    val lpH = v.layoutParams?.height ?: WRAP
    val wSpec = ViewGroup.getChildMeasureSpec(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        paddingLeft + paddingRight + (m?.leftMargin ?: 0) + (m?.rightMargin ?: 0), lpW,
    )
    val hSpec = if (lpH >= 0) View.MeasureSpec.makeMeasureSpec(lpH, View.MeasureSpec.EXACTLY)
    else View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    v.measure(wSpec, hSpec)
    return v.measuredHeight + (m?.topMargin ?: 0) + (m?.bottomMargin ?: 0)
}

/**
 * 다음 그리기를 건너뛰고 [action] 을 한 뒤 다시 레이아웃해서 그린다.
 * 그 사이 이 목록이 화면에서 떨어졌으면(화면을 새로 만들었으면) 할 일 없이 그리게 둔다. 떨어진 뷰의 viewTreeObserver 는
 * 화면 것이 아니어서 거기서 지우면 리스너가 남아 그리기를 계속 건너뛴다(독서 기록이 검게 멈췄다). 그래서 건 관찰자에서 지운다.
 */
private fun View.beforeNextDraw(action: () -> Unit) {
    val observer = viewTreeObserver
    observer.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            if (observer.isAlive) observer.removeOnPreDrawListener(this)
            if (viewTreeObserver.isAlive) viewTreeObserver.removeOnPreDrawListener(this)
            if (!isAttachedToWindow) return true
            action()
            return false
        }
    })
}

/**
 * 쪽 표시 `‹   2 / 3   ›`: 갈 수 있는 쪽 화살표는 글자색 그대로, 갈 수 없는 쪽(첫 쪽의 `‹`, 끝 쪽의 `›`)은 흐리게
 * (독서 기록 달력의 `‹ ›` 처럼). 숫자 자리는 쪽마다 그대로다. 한 쪽뿐이면 빈 글자.
 */
fun pageLabel(rows: Paged): CharSequence {
    if (rows.pageCount <= 1) return ""
    fun arrow(s: String, on: Boolean) = SpannableString(s).apply {
        if (!on) setSpan(ForegroundColorSpan(Ui.DIVIDER), 0, s.length, 0)
    }
    return SpannableStringBuilder()
        .append(arrow("‹   ", rows.page > 0))
        .append("${rows.page + 1} / ${rows.pageCount}")
        .append(arrow("   ›", rows.page < rows.pageCount - 1))
}

/**
 * 쪽 표시를 누르면: 왼쪽 절반(`‹`)은 앞 쪽, 오른쪽 절반(`›`)은 다음 쪽. 그쪽으로 갈 수 없으면(흐린 화살표) 그대로.
 * 손가락 위치 없이 눌리면(다른 곳을 눌러 대신 누를 때) 다음 쪽, 끝이면 앞 쪽.
 */
@SuppressLint("ClickableViewAccessibility")
fun TextView.pagesClick(rows: Paged) = pagesClick { rows }

/** [pagesClick] 를 누를 때마다 [current] 로 지금 보이는 목록에(목록·격자를 바꾸는 화면) */
@SuppressLint("ClickableViewAccessibility")
fun TextView.pagesClick(current: () -> Paged) {
    var downX = -1f
    // 밀기로 취소되면 위치를 지운다(남아 있으면 제목 줄로 대신 누를 때 그 위치로 방향을 정한다).
    setOnTouchListener { _, e ->
        when (e.action) {
            MotionEvent.ACTION_DOWN -> downX = e.x
            MotionEvent.ACTION_CANCEL -> downX = -1f
        }
        false
    }
    setOnClickListener {
        val rows = current()
        val x = downX
        downX = -1f
        when {
            x < 0 -> if (rows.page < rows.pageCount - 1) rows.next() else rows.prev()
            x < width / 2f -> rows.prev()
            else -> rows.next()
        }
    }
}
