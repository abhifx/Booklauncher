package com.woody.pebbledesk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * ⑮ 월간 결산 그림(1080×1350, 인스타그램 피드 4:5). 위 카드(달 · 읽은 시간), 왼쪽 달력(책마다 읽은 구간을 막대로), 오른쪽 그달 읽은 책,
 * 아래 그해 잔디. [Palette] 로 흑백(표지·잔디도 회색)·컬러를 고른다(화면은 설정대로, 저장·보내기는 늘 컬러). 휴대폰으로 보낼 그림이라 크레마 화면 대비 보정은 하지 않는다.
 * 배치는 DESIGN.md ⑮, 시안은 design-review/monthly.
 */
object RecapImage {
    private const val W = 1080
    private const val H = 1350
    private const val M = 72f
    private const val CAL_W = 640f
    /** 위 카드가 시작하는 높이, 맨 아래 글줄(기준선)이 그림 아래 끝에서 떨어진 만큼, 크게 보기에서 내용 둘레에 남기는 여백 */
    private const val TOP = 40f
    private const val BOTTOM = 44f
    private const val CONTENT_PAD = 24f
    /** 아래 올해 잔디가 시작하는 높이 */
    private const val YEAR_Y = 1084f

    /**
     * 그달 표지에서 색을 못 고를 때(표지가 없거나 흑백 표지뿐, 빈 달) 쓰는 달마다 위 카드 색(옅은 그라데이션 두 색)과 강조색. 계절에 맞춰 1월부터:
     * 겨울 하늘 · 매화 · 새싹 · 벚꽃 · 신록 · 수국 · 바다 · 해바라기 · 가을 하늘 · 단풍 · 낙엽 · 겨울 붉은빛.
     */
    private val MONTHS = arrayOf(
        intArrayOf(0xFFE3ECF7.toInt(), 0xFFC9DAF0.toInt(), 0xFF2F5D9A.toInt()),
        intArrayOf(0xFFF6E4EA.toInt(), 0xFFEBC9D5.toInt(), 0xFFA33E66.toInt()),
        intArrayOf(0xFFE9F3DF.toInt(), 0xFFCFE6BC.toInt(), 0xFF4E8A2E.toInt()),
        intArrayOf(0xFFFBE6EE.toInt(), 0xFFF4CADB.toInt(), 0xFFC2477A.toInt()),
        intArrayOf(0xFFDFF2E4.toInt(), 0xFFBFE3C8.toInt(), 0xFF2E7D4F.toInt()),
        intArrayOf(0xFFE0EEF6.toInt(), 0xFFC4DDEE.toInt(), 0xFF2D6E9E.toInt()),
        intArrayOf(0xFFDDF1F3.toInt(), 0xFFBBE2E8.toInt(), 0xFF1F7A86.toInt()),
        intArrayOf(0xFFFFF1D6.toInt(), 0xFFFADFA6.toInt(), 0xFFB7791F.toInt()),
        intArrayOf(0xFFECE6F6.toInt(), 0xFFD8CCEE.toInt(), 0xFF6A4FA3.toInt()),
        intArrayOf(0xFFFBE7DA.toInt(), 0xFFF3CDB3.toInt(), 0xFFC2571A.toInt()),
        intArrayOf(0xFFF1E7DA.toInt(), 0xFFE2D0B8.toInt(), 0xFF8A5A2B.toInt()),
        intArrayOf(0xFFF8E4E4.toInt(), 0xFFEFC8C8.toInt(), 0xFFA83232.toInt()),
    )

    /** 표지 색으로 만든 카드 색: 색상 [hue](0~1), 채도 [sat] → 옅은 두 색과 진한 강조색 */
    internal fun theme(hue: Float, sat: Float): IntArray {
        val s = sat.coerceIn(0.25f, 0.65f)
        fun hsv(sv: Float, v: Float) = Color.HSVToColor(floatArrayOf(hue * 360f, sv.coerceIn(0f, 1f), v))
        return intArrayOf(hsv(s * 0.18f, 0.98f), hsv(s * 0.38f, 0.94f), hsv(maxOf(0.55f, s), 0.55f))
    }

    /** 위 카드 색과 강조색 [m](옅은 색 두 개·강조색) */
    private class Colors(mono: Boolean, private val m: IntArray) {
        val ink = Color.BLACK
        val gray = Color.rgb(110, 110, 110)
        val dark = Color.rgb(55, 55, 55)
        val faint = Color.rgb(195, 195, 195)
        val line = Color.rgb(234, 234, 234)
        val head = Color.rgb(200, 200, 200)
        val coverLine = Color.rgb(215, 215, 215)
        val accent = if (mono) Color.BLACK else m[2]
        val done = if (mono) Color.rgb(85, 85, 85) else Palette.DONE
        val star = if (mono) Color.BLACK else Palette.STAR
        val starOff = Color.rgb(215, 215, 215)
        val band = if (mono) intArrayOf(Color.rgb(242, 242, 242), Color.rgb(228, 228, 228))
            else intArrayOf(m[0], m[1])
        val empty = if (mono) Color.rgb(236, 236, 236) else 0xFFEBEDF0.toInt()
        val future = if (mono) Color.rgb(246, 246, 246) else 0xFFF6F7F8.toInt()
        val levels = if (mono) intArrayOf(Color.rgb(200, 200, 200), Color.rgb(140, 140, 140), Color.rgb(80, 80, 80), Color.rgb(17, 17, 17))
            else intArrayOf(0xFF9BE9A8.toInt(), 0xFF40C463.toInt(), 0xFF30A14E.toInt(), 0xFF216E39.toInt())
    }

    /** [draw] 한 그림에서 바깥 흰 여백을 뺀 내용(크게 보기). 사방 [CONTENT_PAD] 만 남긴다. */
    fun content(full: Bitmap): Bitmap {
        val l = (M - CONTENT_PAD).toInt()
        val t = (TOP - CONTENT_PAD).toInt()
        val b = (H - BOTTOM + CONTENT_PAD).toInt()
        return Bitmap.createBitmap(full, l, t, W - 2 * l, b - t)
    }

    fun draw(context: Context, m: Recap.Month, palette: Palette): Bitmap {
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        Painter(context, Canvas(out), m, palette).draw()
        return out
    }

    private class Painter(val ctx: Context, val c: Canvas, val m: Recap.Month, palette: Palette) {
        val mono = !palette.color
        /** 색: 컬러면 그달 읽은 책 표지에서 가장 많이 보인 색(읽은 시간만큼 센다), 못 고르면 그달 계절 색 */
        val k by lazy {
            Colors(mono, if (mono) MONTHS[0] else coverTheme() ?: MONTHS[m.month.monthValue - 1])
        }

        /** 표지의 색 있는 픽셀을 색상 칸에 나눠 읽은 시간만큼 센 가장 많은 칸([Recap.dominantHue])의 색 */
        fun coverTheme(): IntArray? {
            val samples = m.books.mapNotNull { b ->
                if (b.ms <= 0) null else coverPixels(b.book) to b.ms
            }
            val (hue, sat) = Recap.dominantHue(samples) ?: return null
            return theme(hue, sat)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val locale = ctx.resources.configuration.locales[0]
        fun fmt(res: Int): DateTimeFormatter = DateTimeFormatter.ofPattern(ctx.getString(res), locale)

        // ---------- 글자 ----------
        enum class V { TOP, MID, BASE }

        fun pen(size: Float, face: Typeface, color: Int) = paint.apply {
            reset(); isAntiAlias = true; textSize = size; typeface = face; this.color = color; style = Paint.Style.FILL
        }

        fun width(s: String, size: Float, face: Typeface) = pen(size, face, 0).measureText(s)

        /** [x] 는 [align] 쪽 끝, [y] 는 [v] 기준(위 = 글꼴 윗선, 가운데, 기준선) */
        fun text(s: String, x: Float, y: Float, size: Float, face: Typeface, color: Int,
                 align: Paint.Align = Paint.Align.LEFT, v: V = V.TOP) {
            val p = pen(size, face, color).apply { textAlign = align }
            val fm = p.fontMetrics
            val base = when (v) {
                V.TOP -> y - fm.ascent
                V.MID -> y - (fm.ascent + fm.descent) / 2
                V.BASE -> y
            }
            c.drawText(s, x, base, p)
        }

        /** 띄어쓰기로 나눠 [maxW] 에 맞춘 줄들(한 낱말이 넘치면 글자로 자른다). [lines] 를 넘으면 마지막 줄 끝을 `…` 로. */
        fun wrap(s: String, size: Float, face: Typeface, maxW: Float, lines: Int): List<String> {
            val out = mutableListOf<String>()
            var cur = ""
            fun push(word: String) {
                val t = if (cur.isEmpty()) word else "$cur $word"
                if (width(t, size, face) <= maxW) { cur = t; return }
                if (cur.isNotEmpty()) { out += cur; cur = "" }
                var w = word
                while (width(w, size, face) > maxW && w.length > 1) {
                    var cut = w.length - 1
                    while (cut > 1 && width(w.substring(0, cut), size, face) > maxW) cut--
                    out += w.substring(0, cut)
                    w = w.substring(cut)
                }
                cur = w
            }
            s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { push(it) }
            if (cur.isNotEmpty()) out += cur
            if (out.size <= lines) return out
            val kept = out.take(lines).toMutableList()
            kept[lines - 1] = ellipsize(kept[lines - 1] + " " + out[lines], size, face, maxW)
            return kept
        }

        fun ellipsize(s: String, size: Float, face: Typeface, maxW: Float): String {
            if (width(s, size, face) <= maxW) return s
            var t = s
            while (t.isNotEmpty() && width("$t…", size, face) > maxW) t = t.dropLast(1)
            return t.trimEnd() + "…"
        }

        // ---------- 도형 ----------
        fun fill(color: Int) = paint.apply { reset(); isAntiAlias = true; this.color = color; style = Paint.Style.FILL }
        fun stroke(color: Int, w: Float) = paint.apply { reset(); isAntiAlias = true; this.color = color; style = Paint.Style.STROKE; strokeWidth = w }

        fun gradientBox(r: RectF, radius: Float, from: Int, to: Int) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(r.left, 0f, r.right, 0f, from, to, Shader.TileMode.CLAMP) }
            c.drawRoundRect(r, radius, radius, p)
        }

        fun star(cx: Float, cy: Float, r: Float, frac: Float, on: Int, off: Int) {
            val path = Path()
            for (i in 0 until 10) {
                val a = -PI / 2 + i * PI / 5
                val rr = if (i % 2 == 0) r else r * 0.45f
                val x = cx + rr * cos(a).toFloat()
                val y = cy + rr * sin(a).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            c.drawPath(path, fill(off))
            if (frac <= 0f) return
            c.save()
            c.clipRect(cx - r, cy - r, cx - r + 2 * r * frac.coerceAtMost(1f), cy + r)
            c.drawPath(path, fill(on))
            c.restore()
        }

        fun stars(x: Float, cy: Float, r: Float, step: Float, halves: Int) {
            for (i in 0 until 5) star(x + r + i * step, cy, r, ((halves / 2f) - i).coerceIn(0f, 1f), k.star, k.starOff)
        }

        /** 표지(2:3 으로 가운데를 잘라)와 옅은 테두리 */
        fun cover(b: ShelfBook, x: Float, y: Float, w: Float) {
            val h = w * 1.5f
            val bmp = coverBitmap(b, w.toInt().coerceAtLeast(1))
            val bw = bmp.width.toFloat()
            val bh = bmp.height.toFloat()
            val scale = maxOf(w / bw, h / bh)
            val sw = w / scale
            val sh = h / scale
            val src = Rect(((bw - sw) / 2).toInt(), ((bh - sh) / 2).toInt(), ((bw + sw) / 2).toInt(), ((bh + sh) / 2).toInt())
            c.drawBitmap(bmp, src, RectF(x, y, x + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
            c.drawRect(x + 0.5f, y + 0.5f, x + w - 0.5f, y + h - 0.5f, stroke(k.coverLine, 1.2f))
        }

        val covers = HashMap<String, Bitmap>()
        fun coverBitmap(b: ShelfBook, w: Int): Bitmap = covers.getOrPut("${b.id}@$w") {
            BookShelf.shareCover(ctx, b, w * 2, gray = mono)
                ?: BlankCover.draw(b.title, b.author, b.app?.let { AppStore.find(ctx, it)?.label }, w * 2)
        }

        /** 표지 색을 볼 작은 그림(24×36)의 픽셀: 막대 색·카드 색 */
        val pixels = HashMap<String, IntArray>()
        fun coverPixels(b: ShelfBook): IntArray = pixels.getOrPut(b.id) {
            val small = Bitmap.createScaledBitmap(coverBitmap(b, 60), 24, 36, true)
            IntArray(small.width * small.height).also { small.getPixels(it, 0, small.width, 0, 0, small.width, small.height) }
        }

        /** 막대 색(컬러만): 표지에서 채도 있는 픽셀의 평균(없으면 전체 평균), 너무 밝으면 어둡게 */
        val barColors = HashMap<String, Int>()
        fun barColor(b: ShelfBook): Int = barColors.getOrPut(b.id) {
            var n = 0; var r = 0L; var g = 0L; var bl = 0L
            var an = 0; var ar = 0L; var ag = 0L; var ab = 0L
            val hsv = FloatArray(3)
            for (px in coverPixels(b)) {
                ar += Color.red(px); ag += Color.green(px); ab += Color.blue(px); an++
                Color.colorToHSV(px, hsv)
                if (hsv[1] > 0.28f && hsv[2] in 0.18f..0.95f) { r += Color.red(px); g += Color.green(px); bl += Color.blue(px); n++ }
            }
            val (cr, cg, cb) = if (n > an * 0.08) Triple(r / n, g / n, bl / n) else Triple(ar / an, ag / an, ab / an)
            var col = Color.rgb(cr.toInt(), cg.toInt(), cb.toInt())
            val lum = 0.299 * cr + 0.587 * cg + 0.114 * cb
            if (lum > 170) col = mix(col, Color.BLACK, ((lum - 150) / 255).toFloat())
            col
        }

        fun mix(a: Int, b: Int, t: Float) = Color.rgb(
            (Color.red(a) * (1 - t) + Color.red(b) * t).toInt(),
            (Color.green(a) * (1 - t) + Color.green(b) * t).toInt(),
            (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt(),
        )

        // ---------- 그리기 ----------
        val month: YearMonth = m.month
        val yearStart: LocalDate = LocalDate.of(month.year, 1, 1).with(TemporalAdjusters.previousOrSame(m.firstDayOfWeek))
        val yearWeeks = ((LocalDate.of(month.year, 12, 31).toEpochDay() - yearStart.toEpochDay()) / 7 + 1).toInt()
        val cell = (W - 2 * M + 3) / yearWeeks - 3
        /** 위 카드 높이 = 아래 올해 잔디 묶음 높이(제목 66 + 일곱 칸) */
        val cardH = 66 + 7 * (cell + 3) - 3

        fun draw() {
            c.drawColor(Color.WHITE)
            card()
            val gy = TOP + cardH + 46
            val rh = (YEAR_Y - 34 - gy) / Recap.weeks(month, m.firstDayOfWeek)
            // 칸 높이에 들어가는 만큼만 막대 줄(아래 `+n권 더` 한 줄 자리를 남긴다). 여섯 주인 달은 세 줄.
            val lanes = ((rh - LANE_TOP - MORE_H) / LANE).toInt().coerceIn(1, Recap.LANES)
            val cal = Recap.calendar(month, m.firstDayOfWeek, m.books, lanes)
            calendar(cal, gy, rh, lanes)
            side(gy - 26, gy + cal.weeks * rh)
            yearStrip(YEAR_Y)
            text(ctx.getString(R.string.app_name), M, H - BOTTOM, 20f, Ui.bold, k.gray, v = V.BASE)
            text(month.format(fmt(R.string.recap_footer_fmt)), W - M, H - BOTTOM, 20f, Ui.medium, k.gray, Paint.Align.RIGHT, V.BASE)
        }

        /** 위 카드: 해 · 이름, `10월 독서 달력`, 권수 줄, 오른쪽 읽은 시간 */
        fun card() {
            gradientBox(RectF(M, TOP, W - M, TOP + cardH), 18f, k.band[0], k.band[1])
            text("${month.year} · PEBBLE DESK", M + 34, 70f, 19f, Ui.bold, k.gray)
            text(ctx.getString(R.string.recap_image_title, month.format(fmt(R.string.recap_month_fmt))), M + 34, 100f, 52f, Ui.heavy, k.ink)
            val base = 40 + cardH - 36
            val n = m.books.size
            val summary = listOf(
                if (n == 1) ctx.getString(R.string.recap_books_one) else ctx.getString(R.string.recap_books, n),
                ctx.getString(R.string.recap_finished_count, m.finished),
                count(m.readDays, R.string.recap_read_days_one, R.string.recap_read_days),
            ).joinToString(" · ")
            text(summary, M + 34, base, 21f, Ui.medium, k.dark, v = V.BASE)
            text(ctx.getString(R.string.recap_time_label), W - M - 34, 74f, 18f, Ui.bold, k.gray, Paint.Align.RIGHT)
            bigTime(W - M - 34, base, m.totalMs)
        }

        /** `23시간 40분`: 숫자는 크게 강조색, 단위는 작게. [right] 에 오른쪽 끝을 맞춘다. */
        fun bigTime(right: Float, base: Float, ms: Long) {
            val min = ms / 60_000
            val h = min / 60
            val mm = min % 60
            val parts = buildList {
                if (h > 0) { add("$h" to true); add(ctx.getString(R.string.recap_unit_h) to false) }
                if (mm > 0 || h == 0L) { add("$mm" to true); add(ctx.getString(R.string.recap_unit_m) to false) }
            }
            fun w(s: String, num: Boolean) = if (num) width(s, 80f, Ui.heavy) + 4 else width(s, 30f, Ui.bold) + 18
            var x = right - parts.sumOf { (s, num) -> w(s, num).toDouble() }.toFloat() + 18
            for ((s, num) in parts) {
                if (num) text(s, x, base, 80f, Ui.heavy, k.accent, v = V.BASE) else text(s, x, base, 30f, Ui.bold, k.ink, v = V.BASE)
                x += w(s, num)
            }
        }

        /** 왼쪽 달력: 요일 머리, 날짜(읽은 날은 진하게, 가장 많이 읽은 날은 별), 책 막대(옅은 표지 색 + 제목, 완독하면 끝에 ✓), 넘친 책 `+n권 더` */
        fun calendar(cal: Recap.Calendar, gy: Float, rh: Float, lanes: Int) {
            val cw = CAL_W / 7
            for (i in 0 until 7) {
                val dow = m.firstDayOfWeek.plus(i.toLong())
                text(dow.getDisplayName(TextStyle.NARROW, locale), M + i * cw + cw / 2, gy - 18, 16f, Ui.medium, k.gray, Paint.Align.CENTER, V.MID)
            }
            for (w in 0 until cal.weeks) for (i in 0 until 7) {
                val day = cal.start.plusDays((7 * w + i).toLong())
                if (YearMonth.from(day) != month) continue
                val x = M + i * cw
                val y = gy + w * rh
                val read = m.dayMs[day.dayOfMonth - 1] >= ReadingLog.MIN_DAY_MS
                val label = day.dayOfMonth.toString()
                text(label, x + 10, y + 8, 16f, if (read) Ui.bold else Ui.medium, if (read) k.ink else k.faint)
                if (day.dayOfMonth == m.bestDay) star(x + 10 + width(label, 16f, Ui.bold) + 12, y + 18, 8f, 1f, k.star, k.star)
                val hidden = cal.hidden[day.dayOfMonth - 1]
                if (hidden > 0) text(ctx.getString(R.string.recap_more_day, hidden), x + 10, y + LANE_TOP + lanes * LANE + BAR_H / 2,
                    14f, Ui.bold, k.gray, v = V.MID)
            }
            for (w in 1..cal.weeks) c.drawLine(M, gy + w * rh, M + CAL_W, gy + w * rh, stroke(k.line, 1.2f))
            c.drawLine(M, gy, M + CAL_W, gy, stroke(k.head, 1.5f))
            for (p in cal.pieces) {
                val b = m.books[p.book]
                val base = if (mono) null else barColor(b.book)
                val bg = base?.let { mix(it, Color.WHITE, 0.8f) } ?: Color.rgb(234, 234, 234)
                val fg = base?.let { mix(it, Color.BLACK, 0.45f) } ?: Color.rgb(40, 40, 40)
                val x0 = M + p.from * cw + if (p.starts) 4 else 0
                val x1 = M + (p.to + 1) * cw - if (p.ends) 4 else 0
                val y0 = gy + p.week * rh + LANE_TOP + p.lane * LANE
                val r = RectF(x0, y0, x1, y0 + BAR_H)
                c.drawRoundRect(r, BAR_H / 2, BAR_H / 2, fill(bg))
                if (!p.starts) c.drawRect(x0, y0, x0 + BAR_H, y0 + BAR_H, fill(bg))
                if (!p.ends) c.drawRect(x1 - BAR_H, y0, x1, y0 + BAR_H, fill(bg))
                // 완독한 날이 든 구간의 끝(완독 뒤 며칠 더 읽었어도 그 구간 끝에)
                val doneHere = p.ends && b.finishDay != null && b.finishDay in p.segment
                val room = x1 - x0 - 16 - if (doneHere) 24 else 0
                if (room > 20) text(ellipsize(b.book.title, 13f, Ui.bold, room), x0 + 9, y0 + BAR_H / 2, 13f, Ui.bold, fg, v = V.MID)
                if (doneHere) check(x1 - BAR_H / 2, y0 + BAR_H / 2, BAR_H / 2 - 1)
            }
        }

        /** 완독 ✓(주홍 동그라미 안 흰 체크) */
        fun check(cx: Float, cy: Float, r: Float) {
            c.drawCircle(cx, cy, r, fill(k.done))
            val p = Path().apply { moveTo(cx - 4.5f, cy + 0.5f); lineTo(cx - 1.2f, cy + 3.8f); lineTo(cx + 4.8f, cy - 3.5f) }
            c.drawPath(p, stroke(Color.WHITE, 2f).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND })
        }

        /** 오른쪽 칸(책 수에 따라 [Recap.side]) */
        fun side(top: Float, bottom: Float) {
            val x0 = M + CAL_W + 26
            val w = W - M - x0
            val h = bottom - top
            val books = m.books
            when (Recap.side(books.size)) {
                Recap.Side.NONE -> text(ctx.getString(R.string.recap_no_books), x0, top + 8, 20f, Ui.medium, k.gray)
                Recap.Side.ONE -> story(books[0], x0, top, w, w * 0.78f, big = true)
                Recap.Side.TWO -> {
                    val hb = (h - 24) / 2
                    books.forEachIndexed { i, b ->
                        story(b, x0, top + i * (hb + 24), w, minOf(w * 0.5f, (hb - 170) / 1.5f), big = false)
                    }
                    c.drawLine(x0, top + hb + 12, x0 + w, top + hb + 12, stroke(k.line, 1.2f))
                }
                Recap.Side.ROWS -> {
                    val n = books.size
                    val hb = (h - (n - 1) * 22) / n
                    books.forEachIndexed { i, b ->
                        val y = top + i * (hb + 22)
                        row(b, x0, y, w, hb)
                        if (i < n - 1) c.drawLine(x0, y + hb + 11, x0 + w, y + hb + 11, stroke(k.line, 1.2f))
                    }
                }
                Recap.Side.GRID -> {
                    val gap = 10f
                    val g = Recap.grid(books, w, h, gap, 44f)
                    val ch = g.coverW * 1.5f
                    val gridW = g.cols * g.coverW + (g.cols - 1) * gap
                    val ox = x0 + (w - gridW) / 2
                    val slots = g.shown.size + if (g.more > 0) 1 else 0
                    for (i in 0 until slots) {
                        val x = ox + (i % g.cols) * (g.coverW + gap)
                        val y = top + (i / g.cols) * (ch + gap)
                        if (i < g.shown.size) cover(books[g.shown[i]].book, x, y, g.coverW)
                        else {
                            c.drawRect(x, y, x + g.coverW, y + ch, fill(Color.rgb(242, 242, 242)))
                            text(ctx.getString(R.string.recap_more, g.more), x + g.coverW / 2, y + ch / 2, if (g.coverW > 70) 22f else 17f,
                                Ui.heavy, k.gray, Paint.Align.CENTER, V.MID)
                        }
                    }
                }
            }
        }

        /** 하나면 [one](`1일`·`1 day`), 아니면 [many] */
        fun count(n: Int, one: Int, many: Int): String = if (n == 1) ctx.getString(one) else ctx.getString(many, n)

        /** `11일 · 10월 2일 – 20일`(짧게 `11일 · 2–20일`). 하루뿐이면 그날만(`1일 · 10월 26일`), 그달 읽지 않고 완독만 했으면 날짜만. */
        fun dayRange(b: Recap.Book, short: Boolean): String {
            val count = if (b.readDays == 0) null else count(b.readDays, R.string.recap_days_one, R.string.recap_days_count)
            val from = month.atDay(b.firstDay)
            val range = when {
                b.firstDay == b.lastDay -> from.format(fmt(R.string.date_short_fmt))
                short -> ctx.getString(R.string.recap_range_short, b.firstDay, b.lastDay)
                else -> ctx.getString(R.string.recap_range, from.format(fmt(R.string.date_short_fmt)), month.atDay(b.lastDay).format(fmt(R.string.recap_day_fmt)))
            }
            return if (count == null) range else "$count · $range"
        }

        /** 1·2권: 표지 아래 제목·저자·시간·읽은 날·완독(과 별점), 1권이면 감상까지 */
        fun story(b: Recap.Book, x: Float, y0: Float, w: Float, coverW: Float, big: Boolean) {
            var y = y0
            cover(b.book, x, y, coverW)
            y += coverW * 1.5f + if (big) 22 else 14
            val ts = if (big) 26f else 22f
            for (ln in wrap(b.book.title, ts, Ui.heavy, w, 2)) { text(ln, x, y, ts, Ui.heavy, k.ink); y += if (big) 34 else 29 }
            if (b.book.author.isNotBlank()) {
                text(ellipsize(b.book.author, if (big) 18f else 16f, Ui.medium, w), x, y + 2, if (big) 18f else 16f, Ui.medium, k.gray)
                y += if (big) 36 else 30
            } else y += 6
            // 그달 기간 안에 읽은 시간이 없으면(고친 기간에 완독만 있음) `0분` 대신 기간 줄만
            if (b.ms > 0) {
                text(ctx.duration(b.ms), x, y, if (big) 24f else 21f, Ui.bold, k.accent)
                y += if (big) 34 else 29
            }
            text(dayRange(b, short = false), x, y, if (big) 18f else 16f, Ui.medium, k.dark)
            y += if (big) 36 else 31
            var sx = x
            b.finishDay?.let { d ->
                val s = ctx.getString(R.string.recap_finished_on, month.atDay(d).format(fmt(R.string.date_short_fmt)))
                val ps = if (big) 17f else 15f
                val ph = if (big) 30f else 26f
                val lw = width(s, ps, Ui.bold) + 22
                c.drawRoundRect(RectF(x, y, x + lw, y + ph), ph / 2, ph / 2, fill(k.done))
                text(s, x + lw / 2, y + ph / 2, ps, Ui.bold, Color.WHITE, Paint.Align.CENTER, V.MID)
                sx = x + lw + 12
                if (big) { y += 44; sx = x }
            }
            if (b.halves > 0) {
                val r = if (big) 12f else 10f
                stars(sx, y + if (big) 12 else 13, r, if (big) 28f else 24f, b.halves)
                if (big) y += 36
            }
            if (big && b.review.isNotBlank()) {
                y += 4
                for (ln in wrap("“${b.review.trim()}”", 18f, Ui.regular, w, 4)) { text(ln, x, y, 18f, Ui.regular, k.dark); y += 27 }
            }
        }

        /** 3~5권: 왼쪽 표지, 오른쪽 제목(칸이 낮으면 한 줄, 저자 빼고)·시간·읽은 날·완독 */
        fun row(b: Recap.Book, x: Float, y: Float, w: Float, h: Float) {
            val coverW = minOf(h / 1.5f, w * 0.4f)
            cover(b.book, x, y, coverW)
            val tx = x + coverW + 14
            val tw = w - coverW - 14
            var yy = y + 2
            val compact = h < 160
            val ts = if (compact) 19f else 20f
            for (ln in wrap(b.book.title, ts, Ui.heavy, tw, if (compact) 1 else 2)) { text(ln, tx, yy, ts, Ui.heavy, k.ink); yy += 26 }
            if (!compact && b.book.author.isNotBlank()) {
                text(ellipsize(b.book.author, 15f, Ui.medium, tw), tx, yy + 2, 15f, Ui.medium, k.gray)
                yy += 28
            } else yy += 4
            if (b.ms > 0) {
                text(ctx.duration(b.ms), tx, yy, 19f, Ui.bold, k.accent)
                yy += 26
            }
            text(dayRange(b, short = true), tx, yy, 15f, Ui.medium, k.dark)
            yy += 28
            b.finishDay?.let { d ->
                val s = ctx.getString(R.string.recap_finished_short, month.monthValue, d)
                val lw = width(s, 14f, Ui.bold) + 18
                c.drawRoundRect(RectF(tx, yy, tx + lw, yy + 24), 12f, 12f, fill(k.done))
                text(s, tx + lw / 2, yy + 12, 14f, Ui.bold, Color.WHITE, Paint.Align.CENTER, V.MID)
            }
        }

        /** 아래 올해 잔디(GitHub 기여 그래프처럼): 그달 밖은 옅게, 그달 뒤의 날은 빈 칸, 그달 테두리 */
        fun yearStrip(ys: Float) {
            val monthEnd = month.atEndOfMonth()
            val shown = m.yearDayMs.filterKeys { it <= monthEnd }
            val ydays = shown.values.count { it >= ReadingLog.MIN_DAY_MS }
            val yHours = shown.values.sum() / 3_600_000
            text(ctx.getString(R.string.recap_year_title, month.year), M, ys, 24f, Ui.bold, k.ink)
            text(count(ydays, R.string.recap_days_one, R.string.recap_days_count) + " · " + ctx.getString(R.string.recap_year_hours, yHours),
                W - M, ys + 2, 21f, Ui.bold, k.gray, Paint.Align.RIGHT)
            val top = ys + 66
            var day = LocalDate.of(month.year, 1, 1)
            val last = LocalDate.of(month.year, 12, 31)
            val monthFmt = DateTimeFormatter.ofPattern(ctx.getString(R.string.month_fmt), locale)
            while (day <= last) {
                val idx = (day.toEpochDay() - yearStart.toEpochDay()).toInt()
                val w = idx / 7
                val r = idx % 7
                val x = M + w * (cell + 3)
                val y = top + r * (cell + 3)
                val color = if (day > monthEnd) k.future else {
                    val lv = Recap.level(m.yearDayMs[day] ?: 0L, m.goalMin)
                    val base = if (lv == 0) k.empty else k.levels[lv - 1]
                    if (YearMonth.from(day) != month) mix(base, Color.WHITE, 0.35f) else base
                }
                c.drawRoundRect(RectF(x, y, x + cell, y + cell), 2.5f, 2.5f, fill(color))
                if (day.dayOfMonth == 1) {
                    val cur = day.monthValue == month.monthValue
                    text(day.format(monthFmt), x, top - 10, 15f, if (cur) Ui.bold else Ui.medium, if (cur) k.ink else k.gray, v = V.BASE)
                }
                day = day.plusDays(1)
            }
            val w0 = ((month.atDay(1).toEpochDay() - yearStart.toEpochDay()) / 7).toInt()
            val w1 = ((monthEnd.toEpochDay() - yearStart.toEpochDay()) / 7).toInt()
            val r = RectF(M + w0 * (cell + 3) - 4, top - 4, M + (w1 + 1) * (cell + 3) - 3 + 4, top + 7 * (cell + 3) - 3 + 4)
            c.drawRoundRect(r, 5f, 5f, stroke(k.ink, 2f))
        }
    }

    /** 달력 막대: 칸 위에서 첫 줄까지, 줄 사이, 막대 높이 */
    private const val LANE_TOP = 32f
    private const val LANE = 21f
    private const val BAR_H = 17f
    /** 막대 줄 아래 `+n권 더` 글줄이 차지하는 높이(다음 주 날짜와 겹치지 않게) */
    private const val MORE_H = 19f
}
