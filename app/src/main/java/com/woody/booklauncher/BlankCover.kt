package com.woody.booklauncher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import java.io.File
import java.util.Locale

/**
 * 표지 그림이 없는 책(자동으로 넣었는데 이북 앱에서 표지를 못 잘라 옴)의 빈 표지. 천 양장본처럼
 * 연회색 바탕에 이중 테두리, 가운데 명조 제목(부제는 작게), 짧은 선, 저자, 아래에 앱 이름.
 * 작은 칸(내 책장·메뉴 머리)에서는 글자가 너무 작아지므로 테두리와 제목만 그린다.
 */
object BlankCover {
    private const val BG = 0xFFF7F7F7.toInt() // 패널이 연회색을 어둡게 그려 디자인 값(#e9)보다 밝게
    private const val INK = 0xFF333333.toInt()
    private const val TITLE = 0xFF111111.toInt()
    private const val SUB = 0xFF444444.toInt()
    private const val APP = 0xFF777777.toInt()
    /** 이보다 좁으면 제목만 */
    private const val SMALL_PX = 200

    /**
     * 한글 명조. 크레마는 한글을 자체 고딕 글꼴로 먼저 그려 Typeface.SERIF 로는 명조가 나오지 않으므로
     * 시스템의 Noto Serif CJK 에서 한국어 글꼴(ttc 1번)을 직접 연다. 없으면 기본 serif.
     */
    private val serif: Typeface by lazy {
        runCatching { Typeface.Builder(File("/system/fonts/NotoSerifCJK-Regular.ttc")).setTtcIndex(1).build() }.getOrNull()
            ?: Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    }
    private val sans = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun draw(title: String, author: String, app: String?, widthPx: Int): Bitmap {
        val w = widthPx.coerceAtLeast(1)
        val h = w * 3 / 2
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(BG)
        val s = w / 400f // 시안(400×600) 기준 배율
        val small = w < SMALL_PX
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; style = Paint.Style.STROKE }
        val outer = if (small) w * 0.055f else 22 * s
        val inner = if (small) w * 0.09f else 32 * s
        line.strokeWidth = (3 * s).coerceAtLeast(1.5f)
        c.drawRect(outer, outer, w - outer, h - outer, line)
        line.strokeWidth = s.coerceAtLeast(1f)
        c.drawRect(inner, inner, w - inner, h - inner, line)

        val main = title.substringBefore(':').trim().ifEmpty { title.trim() }
        val sub = title.substringAfter(':', "").trim()
        val cx = w / 2f
        if (small) {
            val (p, lines) = fit(main, w * 0.74f, 3, w * 0.16f, w * 0.11f, serif, TITLE)
            drawLines(c, lines, p, cx, h * 0.45f - lines.size * p.textSize * 1.3f / 2, 1.3f)
            return out
        }
        val (p, lines) = fit(main, w - 110 * s, 4, 56 * s, 30 * s, serif, TITLE)
        var y = drawLines(c, lines, p, cx, h * 0.40f - lines.size * p.textSize * 1.3f / 2, 1.3f)
        if (sub.isNotEmpty()) {
            val (sp, subLines) = fit(sub, w - 120 * s, 2, 22 * s, 18 * s, serif, SUB)
            y = drawLines(c, subLines, sp, cx, y + 6 * s, 1.35f)
        }
        y += 24 * s
        line.strokeWidth = 2 * s
        c.drawLine(cx - 30 * s, y, cx + 30 * s, y, line)
        y += 26 * s
        if (author.isNotBlank()) {
            val ap = paint(serif, 24 * s, INK)
            drawLines(c, listOf(ellipsize(author.trim(), ap, w - 110 * s)), ap, cx, y, 1f)
        }
        if (!app.isNullOrBlank()) {
            val pp = paint(sans, 17 * s, APP)
            drawLines(c, listOf(ellipsize(app, pp, w - 110 * s)), pp, cx, h - 58 * s, 1f)
        }
        return out
    }

    private fun paint(face: Typeface, size: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face
        textSize = size
        this.color = color
        textAlign = Paint.Align.CENTER
        textLocale = Locale.KOREAN // 한자 모양이 한국 글꼴로 나오게
    }

    /** 위 끝이 [top] 인 줄들을 가운데 맞춰 그리고 다음 줄의 위치를 돌려준다. */
    private fun drawLines(c: Canvas, lines: List<String>, p: Paint, cx: Float, top: Float, spacing: Float): Float {
        var y = top
        for (l in lines) {
            c.drawText(l, cx, y - p.ascent(), p)
            y += p.textSize * spacing
        }
        return y
    }

    /** [maxLines] 줄에 들어가는 가장 큰 글자 크기. 가장 작게 해도 넘치면 마지막 줄을 '…' 로 줄인다. */
    private fun fit(text: String, maxW: Float, maxLines: Int, start: Float, min: Float, face: Typeface, color: Int): Pair<Paint, List<String>> {
        val p = paint(face, start, color)
        val step = (start - min) / 5
        while (p.textSize > min) {
            val lines = wrap(text, p, maxW)
            if (lines.size <= maxLines) return p to lines
            p.textSize = (p.textSize - step).coerceAtLeast(min)
        }
        val lines = wrap(text, p, maxW)
        if (lines.size <= maxLines) return p to lines
        return p to lines.take(maxLines - 1) + ellipsize(lines.drop(maxLines - 1).joinToString(" ") + "…", p, maxW)
    }

    /** 폭에 맞춰 줄을 나눈다. 될 수 있으면 띄어쓰기에서 끊는다. */
    private fun wrap(text: String, p: Paint, maxW: Float): List<String> {
        val lines = mutableListOf<String>()
        var cur = StringBuilder()
        for (ch in text) {
            if (cur.isNotEmpty() && p.measureText(cur.toString() + ch) > maxW) {
                val space = cur.lastIndexOf(" ")
                if (space > 0) {
                    lines += cur.substring(0, space)
                    cur = StringBuilder(cur.substring(space + 1))
                } else {
                    lines += cur.toString()
                    cur = StringBuilder()
                }
                if (ch == ' ' && cur.isEmpty()) continue
            }
            cur.append(ch)
        }
        if (cur.isNotBlank()) lines += cur.toString()
        return lines.map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun ellipsize(text: String, p: Paint, maxW: Float): String {
        if (p.measureText(text) <= maxW) return text
        var t = text.removeSuffix("…")
        while (t.isNotEmpty() && p.measureText("$t…") > maxW) t = t.dropLast(1)
        return "${t.trimEnd()}…"
    }
}
