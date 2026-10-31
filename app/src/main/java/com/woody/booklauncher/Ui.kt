package com.woody.booklauncher

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.StaticLayout
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/** 화면 크기·색·글꼴. 시안(폭 402) 기준 값을 페블 화면(폭 572dp)에 맞춰 1.42배 한 값이다. 다른 기기도 이 폭이 되도록 밀도를 맞춘다([designDensityDpi]). */
object Ui {
    /** 모든 dp 값의 기준 화면 폭(크레마 페블 1072px / 1.875). 세로 모양 화면의 짧은 변이 이 폭이 되게 밀도를 정한다. */
    const val DESIGN_WIDTH_DP = 572

    /** 화면 짧은 변이 [DESIGN_WIDTH_DP] 가 되는 밀도(dpi). 페블 300, 메이북 E6·M6C(1072px) 300, 1404px 7.8인치면 393. */
    fun designDensityDpi(context: Context): Int {
        val dm = context.resources.displayMetrics
        densityBasis = "${dm.widthPixels}×${dm.heightPixels}"
        val shortPx = minOf(dm.widthPixels, dm.heightPixels)
        tallScreen = maxOf(dm.widthPixels, dm.heightPixels) >= shortPx * TALL_RATIO
        return (shortPx * 160f / DESIGN_WIDTH_DP).roundToInt().also { designDpi = it }
    }

    /**
     * 마지막으로 [designDensityDpi] 가 정한 밀도. 화면이 실제로 다른 밀도로 그려지면 기기가 앱의 밀도를 덮어쓴 것이다
     * (BOOX 앱 최적화의 앱별 DPI. Poke3 에서 300 대신 350 → 490×662dp). 홈이 알리고 기기 정보가 보여 준다.
     */
    @Volatile var designDpi = 0
        private set

    /** [context] 가 앱이 맞춘 밀도([designDpi])로 그려지는가. 아니면 기기가 덮어쓴 것이다(BOOX 앱 최적화의 앱별 DPI 등). */
    fun densityApplied(context: Context): Boolean = designDpi <= 0 || context.resources.displayMetrics.densityDpi == designDpi

    /**
     * 글자 배율(설정 › 글자 · 간격, [HomePrefs.textScalePercent]). 모든 글자는 [setSp]·[spf] 로 이 배율을 곱해 그린다.
     * 기기의 글꼴 크기는 그대로 따르고 그 위에 곱한다(sp 로 넘겨 안드로이드 14+ 의 큰 글자 덜 키우기도 같이 걸린다). 화면을 만들 때(EinkActivity) 정한다.
     */
    @Volatile var textScale = 1f

    /**
     * 글자가 드는 고정 자리(목록 줄·격자 글자 칸·달력 날짜 칸 등)에 곱하는 배율: 글자를 키운 만큼 넓히고, 줄일 때는 그대로 둔다
     * (고정 크기 표지·링이 잘리지 않게).
     */
    val textGrow: Float get() = maxOf(1f, textScale)

    /** 줄 간격 배율(설정 › 글자 · 간격, [HomePrefs.rowScalePercent]). 목록·메뉴 줄 높이를 [rowDp] 로 이 배율만큼 늘리고 줄인다. */
    @Volatile var rowScale = 1f

    /**
     * 켜져 있으면 [setSp]·[spf] 가 글자 배율·기기 글꼴 크기 없이 기준 크기(sp 값 그대로 dp)로 만든다.
     * 홈처럼 틀 높이를 글자 설정과 상관없이 고정하는 곳에서 그 높이를 잴 때만 켠다([atBaseText]).
     */
    @Volatile var baseText = false
        private set

    /** [block] 동안 [baseText] 를 켠다(그 안에서 만든 글자는 기준 크기) */
    fun <T> atBaseText(block: () -> T): T {
        val was = baseText
        baseText = true
        try {
            return block()
        } finally {
            baseText = was
        }
    }

    /**
     * 바형(휴대폰 모양) 화면인가: 긴 변 ÷ 짧은 변이 [TALL_RATIO] 이상. 리더는 1.33~1.36, 바형은 1.67(Mudita Kompakt)~2.0(Tango·Palma 등).
     * 세로가 남아 홈 책 자리를 크게 쓴다. [designDensityDpi] 가 정한다.
     */
    @Volatile var tallScreen = false
        private set
    private const val TALL_RATIO = 1.5f

    /** 마지막으로 [designDensityDpi] 가 본 화면 크기(px). 기기 정보에서 밀도가 왜 그렇게 정해졌는지 본다(Moaan MIX7S 는 1264px 인데 300dpi 로 잡혔다). */
    @Volatile var densityBasis: String? = null
        private set

    const val BLACK = Color.BLACK
    const val GRAY = 0xFF555555.toInt()
    const val LIGHT_GRAY = 0xFF888888.toInt()
    const val DIVIDER = 0xFFCCCCCC.toInt()

    /** 좌우 여백 */
    const val MARGIN = 40

    /** 화면 맨 아래 줄(모든 앱 · 설정 · 페이지 점 등) 높이. 누르는 자리라 48dp 아래로 줄이지 않는다. */
    const val FOOTER_DP = 56

    /** 설정 줄 높이(dp, 줄 간격을 곱한다). 자주 쓰는 앱 화면의 `보기` 줄도 같은 모양이다. */
    const val SETTING_ROW_DP = 45

    /** 목록 한 줄 규격(홈·모든 앱·고르기·숨긴 앱 공통): 줄 높이 dp, 아이콘 dp, 글자 sp */
    const val ROW_DP = 62
    const val ROW_ICON_DP = 27
    const val ROW_SP = 25f

    /** 흑백(채도 0)으로 그리는 페인트: 앱 아이콘과 책 표지 */
    fun grayPaint() = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
    }

    val heavy: Typeface = Typeface.create(Typeface.DEFAULT, 800, false)
    val bold: Typeface = Typeface.create(Typeface.DEFAULT, 700, false)
    val medium: Typeface = Typeface.create(Typeface.DEFAULT, 500, false)
    val regular: Typeface = Typeface.create(Typeface.DEFAULT, 400, false)
}

/**
 * 가로로 놓였는가(폭이 높이보다 크다). 화면은 돌릴 때마다 새로 만들어지니 만들 때 한 번 본다.
 * 가로면 세로 화면의 칸 모양은 그대로 두고 남는 폭에 칸을 나란히 놓는다(홈·독서 기록·설정·아래 메뉴. DESIGN.md `가로 화면`).
 */
fun Context.landscape(): Boolean = resources.displayMetrics.let { it.widthPixels > it.heightPixels }

/** 앱 버전 이름(설정 바닥글·기기 정보). 못 읽으면 빈 글자. */
fun Context.appVersion(): String =
    runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()

fun Context.dp(v: Number): Int = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
).toInt()

fun Context.dpf(v: Number): Float = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
)

/** 목록·메뉴 줄 높이: [v]dp × 줄 간격([Ui.rowScale]) */
fun Context.rowDp(v: Number): Int = dp(v.toFloat() * Ui.rowScale)

/** 글자 크기 [sp](페블 기준 값) × [Ui.textScale] 를 기기 글꼴 크기대로 바꾼 크기(px). 직접 그리는 글자에 쓴다. */
fun Context.spf(sp: Float): Float =
    if (Ui.baseText) dpf(sp) else TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp * Ui.textScale, resources.displayMetrics)

/** 글자 크기를 [sp](페블 기준 값) × [Ui.textScale] 로(기기 글꼴 크기는 안드로이드가 곱한다). [Ui.baseText] 면 기준 크기. */
fun TextView.setSp(sp: Float) =
    if (Ui.baseText) setTextSize(TypedValue.COMPLEX_UNIT_DIP, sp) else setTextSize(TypedValue.COMPLEX_UNIT_SP, sp * Ui.textScale)

/**
 * 낱말 안에서는 줄을 나누지 않는다: 한글은 글자마다 줄을 나눌 수 있어 낱말의 글자 사이에 단어 이음표 U+2060(두 칸 글자(이모지 등)는
 * 쪼개지 않게 코드 포인트마다). 줄은 띄어쓴 곳에서만 나뉜다(한 줄보다 긴 낱말은 그래도 나뉜다).
 */
fun keepWords(text: String): String =
    text.split(' ').joinToString(" ") { word -> word.codePoints().toArray().joinToString("\u2060") { String(Character.toChars(it)) } }

/** [text] 를 줄바꿈 없이(띄어쓴 곳도 붙인다, [keepWords]) */
fun noBreak(text: String) = keepWords(text.replace(' ', '\u00A0'))

/**
 * 키운 글자([Ui.textScale])가 폭 [widthPx] 에서 [TextView.getMaxLines] 줄을 넘으면 1dp 씩 줄인다. 배율 100% 크기 아래로는 줄이지 않는다
 * (그래도 넘치면 줄임표). 홈 큰 제목처럼 줄 수를 늘릴 자리가 없는 글자에 쓴다.
 */
fun TextView.fitLines(sp: Float, widthPx: Int) {
    if (widthPx <= 0) return
    fun lineCount() = StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx)
        .setBreakStrategy(breakStrategy).setHyphenationFrequency(hyphenationFrequency).setIncludePad(includeFontPadding)
        .build().lineCount
    val min = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics)
    while (textSize > min && lineCount() > maxLines) setTextSize(TypedValue.COMPLEX_UNIT_PX, maxOf(min, textSize - context.dpf(1)))
}

fun Context.text(
    value: CharSequence,
    sp: Float,
    face: Typeface = Ui.regular,
    color: Int = Ui.BLACK,
    lines: Int = 1,
): TextView = TextView(this).apply {
    text = value
    setSp(sp)
    typeface = face
    setTextColor(color)
    includeFontPadding = false
    maxLines = lines
    ellipsize = TextUtils.TruncateAt.END
}

/** 가로 선. [inset] 이 true 면 좌우 여백만큼 들여 그린다. */
fun Context.hline(heightDp: Number, color: Int = Ui.BLACK, inset: Boolean = true): View = View(this).apply {
    setBackgroundColor(color)
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp)).apply {
        if (inset) {
            marginStart = dp(Ui.MARGIN); marginEnd = dp(Ui.MARGIN)
        }
    }
}

/**
 * 나란히 놓은 두 칸 사이의 옅은 세로 선(가로 화면). 위아래를 [insetDp] 씩 띄운다. [heightPx] 는 높이(보통 칸 높이에 맞춰 MATCH,
 * 높이를 따로 맞추는 줄 안에서는 0 으로 재게 둔다).
 */
fun Context.vline(insetDp: Int, heightPx: Int = MATCH): View = View(this).apply {
    setBackgroundColor(Ui.DIVIDER)
    layoutParams = LinearLayout.LayoutParams(dp(1), heightPx).apply { topMargin = dp(insetDp); bottomMargin = dp(insetDp) }
}

/**
 * 표지 격자(올해 읽은 책·책 추가): 표지 폭은 세로 화면(짧은 변)에서 [columns]권이 [gapDp] 사이로 들어가는 폭으로 고정하고,
 * 가로 화면은 그 폭으로 한 줄에 들어가는 만큼 권수를 늘려 남는 폭을 칸 사이에 나눈다(세로는 그대로 [columns]권).
 */
class CoverGrid(val cellW: Int, val columns: Int, val gap: Int)

fun Context.coverGrid(columns: Int, gapDp: Int): CoverGrid {
    val dm = resources.displayMetrics
    val inner = dm.widthPixels - 2 * dp(Ui.MARGIN)
    val cellW = (minOf(dm.widthPixels, dm.heightPixels) - 2 * dp(Ui.MARGIN) - (columns - 1) * dp(gapDp)) / columns
    val n = maxOf(columns, (inner + dp(gapDp)) / (cellW + dp(gapDp)))
    return CoverGrid(cellW, n, (inner - n * cellW) / (n - 1))
}

/**
 * 하위 화면 제목 줄 `‹ 제목`. 누르면 [onBack]. 제목 뒤에 덧붙일 것(개수 등)은 돌려받은 줄에 더한다.
 * 컬러면 화면 묶음 [area] 의 제목 색으로 쓰고, 제목 줄을 칠하는 묶음([Palette.Area.titleBand], 책장·설정)은 그 색면으로 칠한다.
 */
fun Context.titleRow(title: String, area: Palette.Area = Palette.Area.SETTINGS, onBack: () -> Unit): LinearLayout = hbox().apply {
    layoutParams = lp(MATCH, dp(80))
    setPadding(dp(Ui.MARGIN - 8), 0, dp(Ui.MARGIN), 0)
    if (area.titleBand) Palette.current.field(area)?.let { setBackgroundColor(it) }
    val color = Palette.current.title(area)
    addView(text("‹", 34f, color = color), lp(WRAP, WRAP).apply { marginEnd = dp(10) })
    addView(text(title, 33f, Ui.heavy, color))
    setOnClickListener { onBack() }
}

/**
 * 제목 줄 [row] 가 화면 폭에 다 들지 않으면(기기가 밀도를 덮어써 폭이 좁은 화면·키운 글자) 오른쪽 끝이 잘리지 않게 [link] 글자를 줄인다.
 * 원래 크기의 [LINK_MIN_RATIO] 아래로는 줄이지 않는다(독서 기록 `올해 읽은 책 ›`, 올해 읽은 책 `10월 결산 ›`).
 */
fun Context.fitTitleLink(row: View, link: TextView) {
    row.measure(0, 0)
    val over = row.measuredWidth - resources.displayMetrics.widthPixels
    if (over <= 0) return
    val w = link.paint.measureText(link.text.toString())
    link.setTextSize(TypedValue.COMPLEX_UNIT_PX, link.textSize * maxOf((w - over - dp(1)) / w, LINK_MIN_RATIO))
}

private const val LINK_MIN_RATIO = 0.7f

/** 안드로이드 상단바를 보이거나 숨긴다(숨기면 위에서 아래로 쓸 때 잠깐 나온다). 홈과 월간 결산 크게 보기. */
fun Activity.setStatusBarVisible(show: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.insetsController?.let {
            if (show) it.show(WindowInsets.Type.statusBars()) else it.hide(WindowInsets.Type.statusBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    } else {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            if (show) 0 else View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
}

/** 순서 바꾸기 버튼(↑/↓). 끝에 있어 못 옮기면 흐리게 두고 누르지 않게 한다. 누르는 폭은 48dp. */
fun Context.orderArrow(symbol: String, enabled: Boolean, onClick: () -> Unit): TextView =
    text(symbol, 24f, Ui.bold, if (enabled) Ui.BLACK else Ui.DIVIDER).apply {
        gravity = Gravity.CENTER
        minWidth = dp(48)
        setPadding(dp(4), dp(10), dp(4), dp(10))
        if (enabled) setOnClickListener { onClick() }
        // 흐린 버튼도 누름을 받아 둔다(줄 누르기 — 앱 열기·이름 바꾸기 — 로 새지 않게).
        else isClickable = true
    }

/**
 * 그림 한 장(비트맵 또는 드로어블)을 패딩 안쪽에 그리는 뷰. [crop] 이면 칸을 채워 가운데를 자르고, 아니면 비율을 지켜 가운데에 맞춘다.
 * ImageView 를 쓰지 않는 이유: 크레마는 앱이 바뀔 때마다 화면의 모든 ImageView 에 명암 필터를 다시 걸고 다시 그려
 * 전자잉크가 한 번 더 깜빡인다(홈으로 돌아올 때마다). 그냥 View 는 건드리지 않는다.
 */
class PictureView(context: Context, private val crop: Boolean = false) : View(context) {
    var bitmap: Bitmap? = null
        set(v) { field = v; invalidate() }
    var drawable: Drawable? = null
        set(v) { field = v; invalidate() }
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val src = Rect()
    private val dst = RectF()

    override fun onDraw(canvas: Canvas) {
        val w = width - paddingLeft - paddingRight
        val h = height - paddingTop - paddingBottom
        if (w <= 0 || h <= 0) return
        bitmap?.let { b ->
            if (crop) {
                // 칸 비율에 맞춰 원본 가운데를 잘라 칸을 꽉 채운다.
                val scale = maxOf(w.toFloat() / b.width, h.toFloat() / b.height)
                val sw = (w / scale).toInt()
                val sh = (h / scale).toInt()
                src.set((b.width - sw) / 2, (b.height - sh) / 2, (b.width + sw) / 2, (b.height + sh) / 2)
                dst.set(paddingLeft.toFloat(), paddingTop.toFloat(), (paddingLeft + w).toFloat(), (paddingTop + h).toFloat())
            } else {
                val scale = minOf(w.toFloat() / b.width, h.toFloat() / b.height)
                src.set(0, 0, b.width, b.height)
                fitCenter(b.width * scale, b.height * scale, w, h)
            }
            canvas.drawBitmap(b, src, dst, paint)
        }
        drawable?.let { d ->
            val iw = d.intrinsicWidth.takeIf { it > 0 } ?: w
            val ih = d.intrinsicHeight.takeIf { it > 0 } ?: h
            val scale = minOf(w.toFloat() / iw, h.toFloat() / ih)
            fitCenter(iw * scale, ih * scale, w, h)
            d.setBounds(dst.left.toInt(), dst.top.toInt(), dst.right.toInt(), dst.bottom.toInt())
            d.draw(canvas)
        }
    }

    private fun fitCenter(fw: Float, fh: Float, w: Int, h: Int) {
        val l = paddingLeft + (w - fw) / 2
        val t = paddingTop + (h - fh) / 2
        dst.set(l, t, l + fw, t + fh)
    }
}

/**
 * 오른쪽에 완독 도장(`drawable-nodpi/stamp_completed.png`, 회색 [Ui.LIGHT_GRAY], 컬러면 완독 색 [Palette.stamp] 으로 칠한다)을 찍은 가로 줄. 도장은 글자보다
 * 먼저(아래에) 그리고 줄 밖으로 나간 부분은 잘린다(종이 끝에 걸쳐 찍은 것처럼. 부모가 clipChildren=false 면 넘쳐 그린다). 지름 [sizeDp], 가운데는 오른쪽 끝에서 [endDp] 안쪽,
 * 위에서 [topDp] 아래(null 이면 줄 가운데). 글자가 도장에 덮이지 않게 부르는 쪽이 글자 칸 오른쪽을 비운다.
 */
class StampedRow(context: Context, private val sizeDp: Int, private val endDp: Int, private val topDp: Int? = null) : LinearLayout(context) {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = PorterDuffColorFilter(Palette.current.stamp, PorterDuff.Mode.SRC_IN)
    }
    private val dst = RectF()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    override fun dispatchDraw(canvas: Canvas) {
        val r = context.dp(sizeDp) / 2f
        val cx = width - context.dp(endDp).toFloat()
        val cy = topDp?.let { context.dp(it).toFloat() } ?: (height / 2f)
        dst.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawBitmap(stamp(context), null, dst, paint)
        super.dispatchDraw(canvas)
    }

    companion object {
        @Volatile private var bitmap: Bitmap? = null

        /** 도장 그림(420px, 한 번 읽어 둔다) */
        private fun stamp(context: Context): Bitmap = bitmap ?: BitmapFactory.decodeResource(
            context.resources, R.drawable.stamp_completed, BitmapFactory.Options().apply { inScaled = false },
        ).also { bitmap = it }
    }
}

/**
 * 세로로 쌓되 칸 높이 안에 다 들어가는 자식만 그린다(위에서부터. 아래 끝에 걸치거나 넘친 자식은 그리지 않는다).
 * 홈처럼 틀 높이를 고정하고 글자만 키우는 곳에서 반쯤 잘린 줄이 보이지 않게 한다.
 * 자식은 높이 제한 없이 재서(남은 높이로 줄여 재면 글자가 칸 끝에서 잘린 채 그려진다) 다 들어가는지 본다.
 */
class FitColumn(context: Context, private val unbounded: Boolean = true) : LinearLayout(context) {
    init {
        orientation = VERTICAL
    }

    /** [unbounded] 가 false 면 보통 묶음처럼 잰다(남는 높이를 나눠 받는 자식이 있을 때. 가로 화면 독서 기록 왼쪽 칸) */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (!unbounded) return super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        setMeasuredDimension(measuredWidth, resolveSize(measuredHeight, heightMeasureSpec))
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean =
        child.bottom <= height - paddingBottom && super.drawChild(canvas, child, drawingTime)
}

/** 그림 한 장(앱 아이콘 등) */
fun Context.picture(bitmap: Bitmap?): PictureView = PictureView(this).apply { this.bitmap = bitmap }

/** 책 표지: 가운데를 채워 자르고 1dp 회색 테두리(흰 표지 윗변이 구분선처럼 보이지 않게) */
fun Context.coverImage(bitmap: Bitmap): PictureView = PictureView(this, crop = true).apply {
    this.bitmap = bitmap
    setBackgroundColor(Ui.LIGHT_GRAY)
    setPadding(dp(1), dp(1), dp(1), dp(1))
}

fun Context.vbox(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

fun Context.hbox(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
}

/** 폭 [widthPx] 에서 잰 높이(높이 제한 없이) */
fun View.heightAt(widthPx: Int): Int {
    measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
    return measuredHeight
}

fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * 앱 한 줄: [아이콘] 이름 ......... [오른쪽 표시]
 * 아이콘 설정이 꺼져 있으면 아이콘 없이 이름을 왼쪽 여백에 붙인다.
 */
fun Context.appRow(
    app: AppEntry?,
    label: CharSequence,
    iconDp: Int,
    textSp: Float,
    face: Typeface = Ui.regular,
    color: Int = Ui.BLACK,
    showIcon: Boolean,
    right: View? = null,
): LinearLayout = hbox().apply {
    setPadding(dp(Ui.MARGIN), 0, dp(Ui.MARGIN), 0)
    if (showIcon && app != null) {
        addView(picture(AppIcons.icon(context, app, dp(iconDp))), lp(dp(iconDp), dp(iconDp)).apply { marginEnd = dp(16) })
    }
    addView(text(label, textSp, face, color), lp(0, WRAP, 1f))
    if (right != null) addView(right, lp(WRAP, WRAP).apply { marginStart = dp(12) })
}
