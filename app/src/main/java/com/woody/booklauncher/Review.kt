package com.woody.booklauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** 책 한 권의 별점(반 개 단위 [halves] 0~10, 0 = 없음)과 감상. 둘 다 비면 남기지 않는다([BookShelf.setReview]). */
data class Review(val halves: Int = 0, val text: String = "") {
    val isEmpty get() = halves == 0 && text.isBlank()
}

/** 별점 글자 `4.5`·`4` */
fun starsLabel(halves: Int): String = if (halves % 2 == 0) "${halves / 2}" else "${halves / 2}.5"

/**
 * 별 다섯(반 개 단위). [editable] 이면 별을 누르거나 좌우로 밀어 고른다: 손가락이 별 왼쪽 반에 있으면 반 개, 오른쪽 반이면 한 개까지 채우고,
 * 손을 뗀 자리가 값이다. 지금 값을 다시 누르면(밀지 않고) 지운다. 값이 바뀔 때만 다시 그린다(밀어도 0.5 단위로만 깜빡인다).
 * 칸이 별보다 넓으면 가운데에 그린다. 빈 별은 [empty] 테두리, 채운 별은 [filled], 반 개는 빈 별 위에 왼쪽 반만 칠한다.
 */
class StarBar(
    context: Context, sizeDp: Int, gapDp: Int, private val filled: Int = Palette.current.star, private val empty: Int = Ui.LIGHT_GRAY,
) : View(context) {
    var halves = 0
        set(v) {
            if (field == v) return
            field = v
            invalidate()
            onChange?.invoke(v)
        }
    var onChange: ((Int) -> Unit)? = null
    var editable = false

    private val size = context.dpf(sizeDp)
    private val gap = context.dpf(gapDp)
    private val starsW = 5 * size + 4 * gap
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeWidth = maxOf(context.dpf(1.2f), size / 16)
    }
    private val path = Path()

    private val left get() = paddingLeft + (width - paddingLeft - paddingRight - starsW) / 2

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize((starsW + paddingLeft + paddingRight).toInt() + 1, widthMeasureSpec),
            resolveSize((size + paddingTop + paddingBottom).toInt() + 1, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        val r = size / 2
        // 별은 위 꼭짓점이 바깥 원에 닿고 아래는 덜 내려오므로(0.81r) 그만큼 내려 칸 가운데에 맞춘다.
        val cy = paddingTop + (height - paddingTop - paddingBottom) / 2f + r * 0.095f
        for (i in 0 until 5) {
            val x = left + i * (size + gap)
            star(x + r, cy, r)
            when {
                halves >= 2 * (i + 1) -> canvas.drawPath(path, fill.apply { color = filled })
                halves == 2 * i + 1 -> {
                    canvas.drawPath(path, stroke.apply { color = empty })
                    canvas.save()
                    canvas.clipRect(x - stroke.strokeWidth, 0f, x + r, height.toFloat())
                    canvas.drawPath(path, fill.apply { color = filled })
                    canvas.restore()
                }
                else -> canvas.drawPath(path, stroke.apply { color = empty })
            }
        }
    }

    /** 가운데 ([cx], [cy]), 바깥 반지름 [r] 의 별(안쪽 꼭짓점 0.45r, 작게 그려도 뭉개지지 않게 통통하게) */
    private fun star(cx: Float, cy: Float, r: Float) {
        path.reset()
        for (k in 0 until 10) {
            val a = -Math.PI / 2 + k * Math.PI / 5
            val rr = if (k % 2 == 0) r else r * 0.45f
            val x = cx + (rr * cos(a)).toFloat()
            val y = cy + (rr * sin(a)).toFloat()
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }

    /** 손가락 x 의 값: 별과 그 양옆 틈의 반이 그 별의 자리, 별 왼쪽 반이면 반 개. 별 줄 왼쪽 밖은 0, 오른쪽 밖은 5개. */
    private fun valueAt(x: Float): Int {
        val rel = x - left
        if (rel < -gap / 2) return 0
        if (rel > starsW + gap / 2) return 10
        val i = floor((rel + gap / 2) / (size + gap)).toInt().coerceIn(0, 4)
        val local = rel - i * (size + gap)
        return if (local < size / 2) 2 * i + 1 else 2 * i + 2
    }

    private var downSame = false
    private var moved = false

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!editable) return false
        val v = valueAt(e.x)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // 별 줄 밖(양옆 빈 칸)을 누른 것은 받지 않는다.
                if (e.x < left - gap || e.x > left + starsW + gap) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                downSame = v == halves
                moved = false
                halves = v
            }
            MotionEvent.ACTION_MOVE -> follow(v)
            MotionEvent.ACTION_UP -> {
                // 손을 뗀 자리도 값에 넣는다(마지막으로 민 자리와 다를 수 있다).
                follow(v)
                if (downSame && !moved) halves = 0
                performClick()
            }
        }
        return true
    }

    /** 밀어서 손가락 자리의 값으로 */
    private fun follow(v: Int) {
        if (v != halves) { moved = true; halves = v }
    }

    override fun performClick(): Boolean = super.performClick()
}

/** 별 아래 안내: 고르기 전 `누르거나 밀어서 고릅니다 · 비워 둬도 됩니다`, 고른 뒤 `4.5점 · 다시 누르면 지웁니다` */
fun Context.reviewHint(halves: Int): String =
    if (halves == 0) getString(R.string.review_stars_hint) else getString(R.string.review_stars_set, starsLabel(halves))

/**
 * 별점·감상 메뉴(완독·책 빼기·상세의 별점 칸·전체 기록의 회차 줄이 같이 쓴다). 머리(표지·제목·[sub]) 아래 별 다섯, 여러 줄 감상,
 * 맨 아래 `취소` / [action](굵게). 그 회차에 남긴 것([initial])을 채워 띄운다. [top] 이 있으면 머리 아래에 둔다. [action] 을 누르면 고른 것을 [onDone] 에(비어 있어도.
 * 별점 없이 완독·책 빼기만 해도 된다). 어떻게든 닫히면 [onDismiss](저장한 뒤에 온다).
 */
fun Context.reviewSheet(
    book: ShelfBook, sub: CharSequence?, action: String, initial: Review?, onDismiss: (() -> Unit)? = null, top: View? = null,
    onDone: (Review) -> Unit,
) {
    val sheet = Sheet(this).header(book.title, sub, image = BookShelf.cover(this, book, dp(SHEET_COVER_DP)))
        .review(initial ?: Review(), action, onDone)
    // 머리와 별 사이에 둘 줄(전체 기록의 회차 메뉴: 완독한 날)
    top?.let { sheet.note(it) }
    onDismiss?.let { sheet.onDismiss(it) }
    sheet.show()
}

/**
 * 책 상세의 별점·감상 칸(마지막 회차의 것): 별(반 개)·점수(회색)·오른쪽 `고치기 ›`, 그 아래 감상([lines] 줄까지, 0 이면 두지 않는다. 메뉴가 화면을 넘지 않게
 * 부르는 쪽이 정한다. 전체는 `고치기`에서). 남긴 것이 없으면 빈 별과 `별점·감상 남기기 ›` 한 줄. 누르면 [reviewSheet](부르는 쪽이 단다).
 */
fun Context.reviewBlock(review: Review?, lines: Int, enabled: Boolean): View = vbox().apply {
    addView(hbox().apply {
        val halves = review?.halves ?: 0
        // 남길 수 없으면(5분 이상 읽지 않은 책) 별과 글자를 흐리게
        addView(StarBar(context, 28, 5, empty = if (enabled) Ui.LIGHT_GRAY else Ui.DIVIDER).apply { this.halves = halves }, lp(WRAP, WRAP))
        if (halves > 0) addView(text(starsLabel(halves), 18f, color = Ui.GRAY), lp(WRAP, WRAP).apply { marginStart = dp(12) })
        addView(View(context), lp(0, 0, 1f))
        addView(text(getString(if (review == null) R.string.review_add else R.string.review_edit), 18f, color = if (enabled) Ui.GRAY else Ui.DIVIDER))
    }, lp(MATCH, WRAP))
    review?.text?.takeIf { it.isNotBlank() && lines > 0 }?.let {
        addView(text(it, 20f, lines = lines).apply { setLineSpacing(0f, 1.15f) }, lp(MATCH, WRAP).apply { topMargin = dp(REVIEW_TEXT_TOP_DP) })
    }
}

/** 별점·감상 칸에서 별 줄과 감상 사이 */
private const val REVIEW_TEXT_TOP_DP = 10
