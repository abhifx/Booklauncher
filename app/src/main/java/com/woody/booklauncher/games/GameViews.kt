package com.woody.booklauncher.games

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import com.woody.booklauncher.Ui
import com.woody.booklauncher.dp
import com.woody.booklauncher.dpf
import com.woody.booklauncher.spf
import kotlin.math.abs
import kotlin.math.min

/** 2048 판. 밀기로 [onMove] 를 부른다. */
internal class Board2048View(
    context: Context,
    private val game: Game2048,
    private val onMove: (Int) -> Unit,
) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()
    private var downX = 0f
    private var downY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; return true }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (maxOf(abs(dx), abs(dy)) > context.dp(24)) {
                    val dir = if (abs(dx) > abs(dy)) (if (dx > 0) 1 else 3) else (if (dy > 0) 2 else 0)
                    onMove(dir)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        val side = min(width, height).toFloat()
        val left = (width - side) / 2
        val top = (height - side) / 2
        val gap = context.dpf(5)
        val cell = (side - gap * 5) / 4
        text.textSize = cell * 0.30f
        for (i in 0 until 16) {
            val r = i / 4
            val c = i % 4
            val x = left + gap + c * (cell + gap)
            val y = top + gap + r * (cell + gap)
            val e = game.cells[i]
            val shade = if (e == 0) 246 else (238 - e * 16).coerceIn(38, 238)
            fill.color = Color.rgb(shade, shade, shade)
            rect.set(x, y, x + cell, y + cell)
            canvas.drawRoundRect(rect, cell * 0.08f, cell * 0.08f, fill)
            if (e > 0) {
                text.color = if (shade < 128) Color.WHITE else Ui.BLACK
                text.typeface = if (e >= 7) Ui.heavy else Ui.bold
                val s = (1 shl e).toString()
                val ty = y + cell / 2f - (text.descent() + text.ascent()) / 2f
                canvas.drawText(s, x + cell / 2f, ty, text)
            }
        }
    }
}

/** 지뢰찾기 판. 눌러 열고, 길게 눌러 깃발을 꽂는다. */
internal class MinesView(
    context: Context,
    private val game: Minesweeper,
    private val onReveal: (Int) -> Unit,
    private val onFlag: (Int) -> Unit,
) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = context.dpf(1); color = Ui.DIVIDER
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Ui.bold }
    private val handler = Handler(Looper.getMainLooper())
    private var pressed = -1
    private var longPressed = false
    private val longPress = Runnable {
        if (pressed >= 0) { longPressed = true; onFlag(pressed) }
    }
    private val rect = RectF()

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(longPress)
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val index = indexAt(event.x, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = index; longPressed = false
                if (index >= 0) handler.postDelayed(longPress, 450)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (index != pressed) { handler.removeCallbacks(longPress); pressed = -1 }
                return true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPress)
                if (!longPressed && pressed >= 0) onReveal(pressed)
                pressed = -1
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPress); pressed = -1; return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun indexAt(x: Float, y: Float): Int {
        val side = min(width, height).toFloat()
        val left = (width - side) / 2
        val top = (height - side) / 2
        if (x < left || y < top || x >= left + side || y >= top + side) return -1
        val cell = side / game.size
        val c = ((x - left) / cell).toInt().coerceIn(0, game.size - 1)
        val r = ((y - top) / cell).toInt().coerceIn(0, game.size - 1)
        return r * game.size + c
    }

    override fun onDraw(canvas: Canvas) {
        val side = min(width, height).toFloat()
        val left = (width - side) / 2
        val top = (height - side) / 2
        val cell = side / game.size
        text.textSize = cell * 0.42f
        for (i in 0 until game.size * game.size) {
            val r = i / game.size
            val c = i % game.size
            val x = left + c * cell
            val y = top + r * cell
            rect.set(x, y, x + cell, y + cell)
            val open = game.revealed[i]
            fill.color = when {
                i == game.exploded -> Ui.BLACK
                open -> Color.WHITE
                else -> 0xFFE8E8E8.toInt()
            }
            canvas.drawRect(rect, fill)
            canvas.drawRect(rect, line)
            val cx = x + cell / 2f
            val cy = y + cell / 2f
            when {
                game.flagged[i] && !open -> {
                    val pole = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE; strokeWidth = context.dpf(1.4f); color = Ui.BLACK
                    }
                    canvas.drawLine(cx, cy - cell * 0.22f, cx, cy + cell * 0.22f, pole)
                    val flag = Path().apply {
                        moveTo(cx, cy - cell * 0.22f)
                        lineTo(cx + cell * 0.22f, cy - cell * 0.10f)
                        lineTo(cx, cy + cell * 0.02f)
                        close()
                    }
                    canvas.drawPath(flag, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.BLACK })
                }
                i == game.exploded -> {
                    fill.color = Color.WHITE
                    canvas.drawCircle(cx, cy, cell * 0.22f, fill)
                }
                open && game.mine[i] -> {
                    fill.color = Ui.BLACK
                    canvas.drawCircle(cx, cy, cell * 0.22f, fill)
                }
                open && game.count(i) > 0 -> {
                    text.color = Ui.BLACK
                    val ty = cy - (text.descent() + text.ascent()) / 2f
                    canvas.drawText(game.count(i).toString(), cx, ty, text)
                }
            }
        }
    }
}

/** 스도쿠 판. 고른 칸과 같은 줄·상자, 메모, 충돌을 그린다. */
internal class SudokuView(
    context: Context,
    private val game: SudokuGame,
    private val onSelect: (Int) -> Unit,
) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = context.dpf(0.8f); color = Ui.DIVIDER
    }
    private val thick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = context.dpf(2); color = Ui.BLACK
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP || game.done) return true
        val side = min(width, height).toFloat()
        val left = (width - side) / 2
        val top = (height - side) / 2
        if (event.x < left || event.y < top || event.x >= left + side || event.y >= top + side) return true
        val cell = side / 9
        val c = ((event.x - left) / cell).toInt().coerceIn(0, 8)
        val r = ((event.y - top) / cell).toInt().coerceIn(0, 8)
        onSelect(r * 9 + c)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val side = min(width, height).toFloat()
        val left = (width - side) / 2
        val top = (height - side) / 2
        val cell = side / 9
        val conflicts = game.conflicts()
        val sr = game.selected / 9
        val sc = game.selected % 9
        for (i in 0 until 81) {
            val r = i / 9
            val c = i % 9
            val x = left + c * cell
            val y = top + r * cell
            if (game.selected >= 0 && (r == sr || c == sc || (r / 3 == sr / 3 && c / 3 == sc / 3))) {
                fill.color = 0xFFF1F1F1.toInt()
                rect.set(x, y, x + cell, y + cell)
                canvas.drawRect(rect, fill)
            }
            if (i == game.selected) {
                fill.color = 0xFFDDDDDD.toInt()
                rect.set(x, y, x + cell, y + cell)
                canvas.drawRect(rect, fill)
            }
            val v = game.cells[i]
            if (v > 0) {
                text.typeface = if (game.given[i]) Ui.bold else Ui.regular
                text.color = if (conflicts[i]) Ui.GRAY else Ui.BLACK
                text.textSize = cell * 0.58f
                val ty = y + cell / 2f - (text.descent() + text.ascent()) / 2f
                canvas.drawText(v.toString(), x + cell / 2f, ty, text)
            } else if (game.notes[i] != 0) {
                text.typeface = Ui.regular
                text.color = Ui.GRAY
                text.textSize = cell * 0.17f
                for (n in 1..9) {
                    if (game.notes[i] and (1 shl (n - 1)) == 0) continue
                    val nr = (n - 1) / 3
                    val nc = (n - 1) % 3
                    val tx = x + cell * (0.22f + nc * 0.28f)
                    val ty = y + cell * (0.34f + nr * 0.28f) - (text.descent() + text.ascent()) / 2f
                    canvas.drawText(n.toString(), tx, ty, text)
                }
            }
        }
        for (i in 0..9) {
            val p = i * cell
            val paint = if (i % 3 == 0) thick else thin
            canvas.drawLine(left + p, top, left + p, top + side, paint)
            canvas.drawLine(left, top + p, left + side, top + p, paint)
        }
    }
}

/** 노노그램 판. 왼쪽·위에 힌트, 가운데 10×10 격자. */
internal class NonogramView(
    context: Context,
    private val game: NonogramGame,
    private val onTap: (Int, Boolean) -> Unit,
) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = context.dpf(0.8f); color = Ui.DIVIDER
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Ui.BLACK }
    private val handler = Handler(Looper.getMainLooper())
    private var down = -1
    private var longPressed = false
    private val long = Runnable { if (down >= 0) { longPressed = true; onTap(down, !crossMode) } }
    private val rect = RectF()
    var crossMode = false

    private fun metrics(): FloatArray {
        val n = game.size
        val band = min(context.dp(56).toFloat(), width * 0.20f)
        val cell = min((width - band) / n, (height - band) / n)
        val gw = cell * n
        val left = band + (width - band - gw) / 2
        val top = band + (height - band - gw) / 2
        return floatArrayOf(cell, left, top)
    }

    private fun indexAt(x: Float, y: Float): Int {
        val (cell, left, top) = metrics()
        if (x < left || y < top) return -1
        val c = ((x - left) / cell).toInt()
        val r = ((y - top) / cell).toInt()
        if (r !in 0 until game.size || c !in 0 until game.size) return -1
        return r * game.size + c
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(long)
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                down = indexAt(event.x, event.y)
                longPressed = false
                if (down >= 0) handler.postDelayed(long, 450)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (indexAt(event.x, event.y) != down) { handler.removeCallbacks(long); down = -1 }
                return true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(long)
                if (!longPressed && down >= 0) onTap(down, crossMode)
                down = -1
                return true
            }
            MotionEvent.ACTION_CANCEL -> { handler.removeCallbacks(long); down = -1; return true }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        val (cell, left, top) = metrics()
        val n = game.size
        text.textSize = cell * 0.30f
        // 가로 힌트(왼쪽)
        for (r in 0 until n) {
            val clues = game.puzzle.rowClues[r]
            clues.forEachIndexed { i, v ->
                val x = left - (clues.size - i) * cell * 0.46f
                val y = top + r * cell + cell / 2f - (text.descent() + text.ascent()) / 2f
                canvas.drawText(v.toString(), x, y, text)
            }
        }
        // 세로 힌트(위)
        for (c in 0 until n) {
            val clues = game.puzzle.colClues[c]
            clues.forEachIndexed { i, v ->
                val x = left + c * cell + cell / 2f
                val y = top - (clues.size - i) * cell * 0.46f - text.descent()
                canvas.drawText(v.toString(), x, y, text)
            }
        }
        for (i in 0 until n * n) {
            val r = i / n
            val c = i % n
            val x = left + c * cell
            val y = top + r * cell
            rect.set(x, y, x + cell, y + cell)
            fill.color = if (game.marks[i] == 1) Ui.BLACK else Color.WHITE
            canvas.drawRect(rect, fill)
            canvas.drawRect(rect, line)
            if (game.marks[i] == 2) {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = context.dpf(1.3f); color = Ui.GRAY
                }
                canvas.drawLine(x + cell * 0.25f, y + cell * 0.25f, x + cell * 0.75f, y + cell * 0.75f, p)
                canvas.drawLine(x + cell * 0.75f, y + cell * 0.25f, x + cell * 0.25f, y + cell * 0.75f, p)
            }
        }
    }
}

/** Connect Four 판. 한 열을 누르면 [onDrop]. */
internal class ConnectFourView(
    context: Context,
    private val game: ConnectFourGame,
    private val onDrop: (Int) -> Unit,
) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = context.dpf(1.2f); color = Ui.BLACK
    }
    private val win = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = context.dpf(3); color = Ui.BLACK
    }
    private val rect = RectF()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true
        val cw = width.toFloat() / game.cols
        val c = (event.x / cw).toInt()
        if (c in 0 until game.cols) onDrop(c)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val cw = width.toFloat() / game.cols
        val ch = height.toFloat() / game.rows
        val r = min(cw, ch) * 0.38f
        val wins = game.winningCells().toSet()
        for (i in 0 until game.rows * game.cols) {
            val row = i / game.cols
            val col = i % game.cols
            val cx = col * cw + cw / 2
            val cy = row * ch + ch / 2
            when (game.board[i]) {
                ConnectFourGame.HUMAN -> { fill.color = Ui.BLACK; canvas.drawCircle(cx, cy, r, fill) }
                ConnectFourGame.AI -> {
                    fill.color = Color.WHITE; canvas.drawCircle(cx, cy, r, fill)
                    canvas.drawCircle(cx, cy, r, line)
                }
                else -> {
                    fill.color = Ui.TILE; canvas.drawCircle(cx, cy, r, fill)
                }
            }
            if (i in wins) canvas.drawCircle(cx, cy, r + context.dpf(2), win)
        }
    }
}

/** 체커 판. 칸을 누르면 [onTap]. */
internal class CheckersView(
    context: Context,
    private val game: CheckersGame,
    private val onTap: (Int) -> Unit,
) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = context.dpf(1); color = Ui.BLACK
    }
    private val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = context.dpf(3); color = Ui.BLACK
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true
        val side = min(width, height).toFloat()
        val left = (width - side) / 2
        val top = (height - side) / 2
        val cell = side / 8
        if (event.x < left || event.y < top || event.x >= left + side || event.y >= top + side) return true
        val c = ((event.x - left) / cell).toInt().coerceIn(0, 7)
        val r = ((event.y - top) / cell).toInt().coerceIn(0, 7)
        onTap(r * 8 + c)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val side = min(width, height).toFloat()
        val left = (width - side) / 2
        val top = (height - side) / 2
        val cell = side / 8
        val destinations = if (game.selected >= 0) game.destinations(game.selected) else emptyList()
        for (i in 0 until 64) {
            val r = i / 8
            val c = i % 8
            val x = left + c * cell
            val y = top + r * cell
            fill.color = if ((r + c) % 2 == 1) Ui.TILE else Color.WHITE
            canvas.drawRect(x, y, x + cell, y + cell, fill)
            val p = game.board[i]
            val cx = x + cell / 2
            val cy = y + cell / 2
            if (p != 0) {
                val human = p == CheckersGame.HUMAN_MAN || p == CheckersGame.HUMAN_KING
                fill.color = if (human) Ui.BLACK else Color.WHITE
                canvas.drawCircle(cx, cy, cell * 0.33f, fill)
                if (!human) canvas.drawCircle(cx, cy, cell * 0.33f, line)
                if (p == CheckersGame.HUMAN_KING || p == CheckersGame.AI_KING) {
                    text.color = if (human) Color.WHITE else Ui.BLACK
                    text.textSize = cell * 0.30f
                    text.typeface = Ui.bold
                    val ty = cy - (text.descent() + text.ascent()) / 2f
                    canvas.drawText("K", cx, ty, text)
                }
            }
            if (i == game.selected) {
                val inset = cell * 0.04f
                canvas.drawRect(x + inset, y + inset, x + cell - inset, y + cell - inset, mark)
            }
            if (i in destinations) {
                fill.color = Ui.BLACK
                canvas.drawCircle(cx, cy, cell * 0.10f, fill)
            }
        }
    }
}

/** 솔리테어 판. 카드 더미·빈 칸·줄을 그리고 누른 곳을 알린다. */
internal class SolitaireView(
    context: Context,
    private val game: SolitaireGame,
    private val onTap: (SolitaireHit) -> Unit,
) : View(context) {
    enum class Kind { STOCK, WASTE, FOUNDATION, TABLEAU }

    data class SolitaireHit(val kind: Kind, val pile: Int, val index: Int = -1)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = context.dpf(1); color = Ui.BLACK
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.BLACK }
    private val rect = RectF()
    var selected: SolitaireHit? = null

    private fun cardW() = (width - 6 * gap()) / 7f
    private fun cardH() = cardW() * 1.42f
    private fun gap() = context.dpf(3)

    private fun topY() = context.dpf(2)
    private fun tableauY() = topY() + cardH() + context.dpf(8)

    private fun pileYs(pile: Int): List<Float> {
        val cards = game.tableau[pile]
        if (cards.isEmpty()) return emptyList()
        val avail = height - tableauY() - context.dpf(2) - cardH()
        val step = if (cards.size <= 1) 0f else min(cardH() * 0.34f, (avail / (cards.size - 1)).coerceAtLeast(context.dpf(13)))
        return cards.indices.map { tableauY() + it * step }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true
        onTap(hit(event.x, event.y))
        return true
    }

    private fun hit(x: Float, y: Float): SolitaireHit {
        val w = cardW()
        val h = cardH()
        val g = gap()
        if (y in topY()..(topY() + h)) {
            val col = (x / (w + g)).toInt().coerceIn(0, 6)
            return when (col) {
                0 -> SolitaireHit(Kind.STOCK, 0)
                1 -> SolitaireHit(Kind.WASTE, 0)
                else -> SolitaireHit(Kind.FOUNDATION, col - 2)
            }
        }
        val pile = (x / (w + g)).toInt().coerceIn(0, 6)
        val ys = pileYs(pile)
        val index = ys.indexOfLast { y >= it }.coerceAtLeast(0)
        return SolitaireHit(Kind.TABLEAU, pile, index)
    }

    override fun onDraw(canvas: Canvas) {
        val w = cardW()
        val h = cardH()
        val g = gap()
        // 위 줄: 재고, 버린 패, 재단 넷
        if (game.stock.isNotEmpty()) drawBack(canvas, 0f, topY(), w, h, true)
        else drawEmpty(canvas, 0f, topY(), w, h, if (game.waste.isNotEmpty()) "↺" else "")
        game.waste.lastOrNull()?.let { drawCard(canvas, w + g, topY(), w, h, it, selected?.kind == Kind.WASTE) }
            ?: drawEmpty(canvas, w + g, topY(), w, h, "")
        for (i in 0 until 4) {
            val x = (3 + i) * (w + g)
            val card = game.foundations[i]
            if (card >= 0) drawCard(canvas, x, topY(), w, h, card, false)
            else drawEmpty(canvas, x, topY(), w, h, "♠♥♦♣"[i].toString())
        }
        // 줄
        val sel = selected
        for (p in 0 until 7) {
            val x = p * (w + g)
            val ys = pileYs(p)
            if (ys.isEmpty()) { drawEmpty(canvas, x, tableauY(), w, h, ""); continue }
            game.tableau[p].forEachIndexed { i, card ->
                val y = ys[i]
                if (game.faceUp[p][i]) {
                    drawCard(canvas, x, y, w, h, card, sel?.kind == Kind.TABLEAU && sel?.pile == p && sel?.index == i)
                } else {
                    drawBack(canvas, x, y, w, h, true)
                }
            }
        }
    }

    private fun drawBack(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, filled: Boolean) {
        rect.set(x, y, x + w, y + h)
        fill.color = Color.WHITE
        canvas.drawRoundRect(rect, context.dpf(3), context.dpf(3), fill)
        canvas.drawRoundRect(rect, context.dpf(3), context.dpf(3), line)
        if (!filled) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = context.dpf(1); color = Ui.GRAY
        }
        var k = context.dpf(5)
        while (k < h) {
            canvas.drawLine(x + context.dpf(3), y + k, x + w - context.dpf(3), y + k, p)
            k += context.dpf(6)
        }
    }

    private fun drawEmpty(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, label: String) {
        rect.set(x, y, x + w, y + h)
        line.color = Ui.LIGHT_GRAY
        canvas.drawRoundRect(rect, context.dpf(3), context.dpf(3), line)
        line.color = Ui.BLACK
        if (label.isNotEmpty()) {
            text.textSize = h * 0.30f
            text.textAlign = Paint.Align.CENTER
            val ty = y + h / 2 - (text.descent() + text.ascent()) / 2
            canvas.drawText(label, x + w / 2, ty, text)
        }
    }

    private fun drawCard(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, card: Int, selected: Boolean) {
        rect.set(x, y, x + w, y + h)
        fill.color = Color.WHITE
        canvas.drawRoundRect(rect, context.dpf(3), context.dpf(3), fill)
        line.strokeWidth = context.dpf(if (selected) 2.5f else 1f)
        canvas.drawRoundRect(rect, context.dpf(3), context.dpf(3), line)
        line.strokeWidth = context.dpf(1)
        val rank = when (val r = SolitaireGame.rank(card)) {
            0 -> "A"; 10 -> "J"; 11 -> "Q"; 12 -> "K"; else -> (r + 1).toString()
        }
        val suit = "♠♥♦♣"[SolitaireGame.suit(card)]
        text.textSize = w * 0.34f
        text.textAlign = Paint.Align.LEFT
        text.typeface = Ui.bold
        canvas.drawText(rank, x + w * 0.12f, y + h * 0.30f, text)
        canvas.drawText(suit.toString(), x + w * 0.12f, y + h * 0.62f, text)
        text.typeface = Ui.regular
    }
}

/** 배틀십 판. [own] 면 내 함대, 아니면 적 함대의 피격만. */
internal class BattleshipView(
    context: Context,
    private val game: BattleshipGame,
    private val onTap: (Int, Int) -> Unit,
) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = context.dpf(0.8f); color = Ui.DIVIDER
    }
    private val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = context.dpf(2); color = Ui.BLACK
    }
    var own = true

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true
        val side = min(width, height).toFloat()
        val left = (width - side) / 2
        val top = (height - side) / 2
        val cell = side / game.size
        if (event.x < left || event.y < top || event.x >= left + side || event.y >= top + side) return true
        val c = ((event.x - left) / cell).toInt().coerceIn(0, game.size - 1)
        val r = ((event.y - top) / cell).toInt().coerceIn(0, game.size - 1)
        onTap(r, c)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val side = min(width, height).toFloat()
        val left = (width - side) / 2
        val top = (height - side) / 2
        val cell = side / game.size
        val ships = if (own) game.playerShips else game.aiShips
        val shots = if (own) game.enemyShots else game.playerShots
        for (i in 0 until game.size * game.size) {
            val r = i / game.size
            val c = i % game.size
            val x = left + c * cell
            val y = top + r * cell
            fill.color = Color.WHITE
            canvas.drawRect(x, y, x + cell, y + cell, fill)
            canvas.drawRect(x, y, x + cell, y + cell, line)
            val ship = game.shipAt(ships, i)
            val sunk = ship != null && game.isSunk(ship, shots)
            if (own && ship != null) {
                fill.color = if (sunk) Ui.BLACK else Ui.LIGHT_GRAY
                canvas.drawRect(x + 2, y + 2, x + cell - 2, y + cell - 2, fill)
            }
            if (shots[i]) {
                val cx = x + cell / 2
                val cy = y + cell / 2
                if (ship != null) {
                    canvas.drawLine(cx - cell * 0.22f, cy - cell * 0.22f, cx + cell * 0.22f, cy + cell * 0.22f, mark)
                    canvas.drawLine(cx + cell * 0.22f, cy - cell * 0.22f, cx - cell * 0.22f, cy + cell * 0.22f, mark)
                } else {
                    fill.color = Ui.BLACK
                    canvas.drawCircle(cx, cy, cell * 0.12f, fill)
                }
            }
        }
    }
}

/** Wordle 격자. [game] 의 지난 줄과 지금 줄을 그린다. */
internal class WordleView(context: Context, private val game: WordleGame) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = context.dpf(1.2f); color = Ui.BLACK
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Ui.bold }

    override fun onDraw(canvas: Canvas) {
        val gap = context.dpf(5)
        val cell = min((width - 4 * gap) / 5f, (height - 5 * gap) / 6f)
        val gw = cell * 5 + gap * 4
        val gh = cell * 6 + gap * 5
        val left = (width - gw) / 2
        val top = (height - gh) / 2
        for (row in 0 until 6) {
            val guess = game.guesses.getOrNull(row)
            val letters = guess ?: if (row == game.guesses.size && !game.over) game.current else ""
            val score = guess?.let { game.score(it) }
            for (col in 0 until 5) {
                val x = left + col * (cell + gap)
                val y = top + row * (cell + gap)
                val ch = letters.getOrNull(col)
                val s = score?.getOrNull(col) ?: 0
                fill.color = when (s) {
                    2 -> Ui.BLACK
                    1 -> Ui.LIGHT_GRAY
                    else -> Color.WHITE
                }
                val rect = RectF(x, y, x + cell, y + cell)
                canvas.drawRoundRect(rect, cell * 0.08f, cell * 0.08f, fill)
                if (s == 0) canvas.drawRoundRect(rect, cell * 0.08f, cell * 0.08f, line)
                if (ch != null) {
                    text.textSize = cell * 0.52f
                    text.color = if (s == 2) Color.WHITE else Ui.BLACK
                    val ty = y + cell / 2 - (text.descent() + text.ascent()) / 2
                    canvas.drawText(ch.uppercaseChar().toString(), x + cell / 2, ty, text)
                }
            }
        }
    }
}

/** Wordle 자판. 누른 키를 [onKey] 로 알린다(ENTER·BACK 또는 알파벳). */
internal class WordleKeyboardView(
    context: Context,
    private val game: WordleGame,
    private val onKey: (String) -> Unit,
) : View(context) {
    private val rows = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = context.dpf(1); color = Ui.BLACK
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Ui.bold }
    private val hit = mutableListOf<Pair<RectF, String>>()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true
        hit.firstOrNull { it.first.contains(event.x, event.y) }?.let { onKey(it.second) }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        hit.clear()
        val gap = context.dpf(3)
        val keyH = (height - gap * 2) / 3f
        for (r in rows.indices) {
            val row = rows[r]
            val extra = if (r == 2) 2 else 0
            val keyW = (width - gap * (row.length + extra - 1)) / (row.length + extra)
            val total = row.length * keyW + (row.length - 1) * gap
            var x = (width - total) / 2
            if (r == 2) {
                drawKey(canvas, RectF(context.dpf(0), r * (keyH + gap), x - gap, (r + 1) * keyH + r * gap), "ENTER")
            }
            for (c in row.indices) {
                val rect = RectF(x, r * (keyH + gap), x + keyW, (r + 1) * keyH + r * gap)
                drawKey(canvas, rect, row[c].toString())
                x += keyW + gap
            }
            if (r == 2) {
                val left = x
                drawKey(canvas, RectF(left, r * (keyH + gap), width.toFloat(), (r + 1) * keyH + r * gap), "BACK")
            }
        }
    }

    private fun drawKey(canvas: Canvas, rect: RectF, label: String) {
        val shown = when (label) {
            "ENTER" -> "↵"
            "BACK" -> "⌫"
            else -> label
        }
        val status = if (label.length == 1) game.keyboard[label[0].lowercaseChar() - 'a'] else 0
        fill.color = when (status) {
            3 -> Ui.BLACK
            2 -> Ui.LIGHT_GRAY
            1 -> Ui.TILE
            else -> Color.WHITE
        }
        canvas.drawRoundRect(rect, context.dpf(4), context.dpf(4), fill)
        if (status == 0) canvas.drawRoundRect(rect, context.dpf(4), context.dpf(4), line)
        text.textSize = if (label.length == 1) rect.height() * 0.42f else rect.height() * 0.40f
        text.color = if (status == 3) Color.WHITE else Ui.BLACK
        val ty = rect.centerY() - (text.descent() + text.ascent()) / 2
        canvas.drawText(shown, rect.centerX(), ty, text)
        hit += rect to label
    }
}

/** 게임 단추: 흰 바탕에 얇은 테두리, 강조는 검은 바탕에 흰 글자. */
internal class GameKey(context: Context, private val label: String, private val primary: Boolean, onClick: () -> Unit) : View(context) {
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = context.dpf(1.2f); color = Ui.BLACK
    }
    private val baseSize = context.spf(20f)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = baseSize
        textAlign = Paint.Align.CENTER
        typeface = if (primary) Ui.bold else Ui.medium
        color = if (primary) Color.WHITE else Ui.BLACK
    }
    private val rect = RectF()

    init {
        contentDescription = label
        isClickable = true
        setOnClickListener { onClick() }
    }

    override fun onDraw(canvas: Canvas) {
        val i = border.strokeWidth / 2
        rect.set(i, i, width - i, height - i)
        if (primary) canvas.drawRoundRect(rect, context.dpf(8f), context.dpf(8f), Paint().apply { color = Ui.BLACK })
        else canvas.drawRoundRect(rect, context.dpf(8f), context.dpf(8f), border)
        text.textSize = baseSize
        val avail = width - context.dpf(8)
        val measured = text.measureText(label)
        if (measured > avail && measured > 0) text.textSize = (baseSize * avail / measured).coerceAtLeast(context.dpf(9))
        val y = height / 2f - (text.descent() + text.ascent()) / 2f
        canvas.drawText(label, width / 2f, y, text)
    }
}

/** 오셀로 판. 사람 차례의 둘 수 있는 칸에 점을 찍는다. */
internal class OthelloView(
    context: Context,
    private val game: OthelloGame,
    private val onTap: (Int) -> Unit,
) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = context.dpf(1); color = Ui.BLACK
    }
    private val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = context.dpf(2.5f); color = Ui.BLACK
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP || game.over || game.turn != OthelloGame.HUMAN) return true
        val side = min(width, height).toFloat()
        val left = (width - side) / 2
        val top = (height - side) / 2
        val cell = side / 8
        if (event.x < left || event.y < top || event.x >= left + side || event.y >= top + side) return true
        val c = ((event.x - left) / cell).toInt().coerceIn(0, 7)
        val r = ((event.y - top) / cell).toInt().coerceIn(0, 7)
        onTap(r * 8 + c)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val side = min(width, height).toFloat()
        val left = (width - side) / 2
        val top = (height - side) / 2
        val cell = side / 8
        val legal = if (game.turn == OthelloGame.HUMAN && !game.over) game.legalMoves().toSet() else emptySet()
        for (i in 0 until 64) {
            val r = i / 8
            val c = i % 8
            val x = left + c * cell
            val y = top + r * cell
            fill.color = if ((r + c) % 2 == 0) 0xFFE9E9E6.toInt() else 0xFFCFCFCA.toInt()
            canvas.drawRect(x, y, x + cell, y + cell, fill)
            canvas.drawRect(x, y, x + cell, y + cell, line)
            val p = game.board[i]
            if (p != 0) {
                fill.color = if (p == OthelloGame.HUMAN) Ui.BLACK else Color.WHITE
                canvas.drawCircle(x + cell / 2, y + cell / 2, cell * 0.38f, fill)
                if (p == OthelloGame.AI) canvas.drawCircle(x + cell / 2, y + cell / 2, cell * 0.38f, line)
            } else if (i in legal) {
                fill.color = Ui.GRAY
                canvas.drawCircle(x + cell / 2, y + cell / 2, cell * 0.11f, fill)
            }
            if (i in game.lastFlips) canvas.drawRect(x + 2, y + 2, x + cell - 2, y + cell - 2, mark)
        }
    }
}

/** 하트 판: 점수·현재 트릭·내 패. 카드를 누르면 [onTap]. */
internal class HeartsView(
    context: Context,
    private val game: HeartsGame,
    private val seatNames: List<String>,
    private val onTap: (Int) -> Unit,
) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = context.dpf(1); color = Ui.BLACK
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.BLACK }
    var selectedCards: Set<Int> = emptySet()

    private fun cardW() = min(context.dp(58).toFloat(), width / 7.5f)
    private fun cardH() = cardW() * 1.42f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true
        val w = cardW()
        val h = cardH()
        val y = height - h - context.dpf(2)
        if (event.y < y) return true
        val hand = game.humanHand()
        if (hand.isEmpty()) return true
        val step = if (hand.size <= 1) 0f else (width - w) / (hand.size - 1)
        val i = ((event.x - 0) / step).toInt().coerceIn(0, hand.size - 1)
        onTap(hand[i])
        return true
    }

    override fun onDraw(canvas: Canvas) {
        // 점수 한 줄
        text.textSize = context.spf(12f)
        text.textAlign = Paint.Align.LEFT
        text.typeface = Ui.medium
        val scoreLine = seatNames.mapIndexed { i, n -> "$n ${game.scores[i]}" }.joinToString("    ")
        canvas.drawText(scoreLine, context.dpf(8), context.dpf(16), text)
        // 트릭
        val w = cardW()
        val h = cardH()
        val cx = width / 2f
        val cy = height * 0.42f
        val offsets = listOf(0f to h * 0.55f, -w * 0.65f to 0f, 0f to -h * 0.55f, w * 0.65f to 0f)
        game.trick.forEachIndexed { i, (_, card) ->
            val (dx, dy) = offsets[i % 4]
            drawCard(canvas, cx + dx - w / 2, cy + dy - h / 2, w, h, card, false)
        }
        // 내 패
        val hand = game.humanHand()
        if (hand.isEmpty()) return
        val step = if (hand.size <= 1) 0f else (width - w) / (hand.size - 1)
        val y = height - h - context.dpf(2)
        hand.forEachIndexed { i, card ->
            val sel = card in selectedCards
            drawCard(canvas, i * step, if (sel) y - context.dpf(7) else y, w, h, card, sel)
        }
    }

    private fun drawCard(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, card: Int, sel: Boolean) {
        fill.color = if (sel) 0xFFE2E2DE.toInt() else Color.WHITE
        val r = RectF(x, y, x + w, y + h)
        canvas.drawRoundRect(r, context.dpf(3), context.dpf(3), fill)
        line.strokeWidth = context.dpf(if (sel) 3f else 1f)
        canvas.drawRoundRect(r, context.dpf(3), context.dpf(3), line)
        line.strokeWidth = context.dpf(1)
        val rank = when (HeartsGame.rank(card)) {
            0 -> "A"; 10 -> "J"; 11 -> "Q"; 12 -> "K"; else -> (HeartsGame.rank(card) + 1).toString()
        }
        val suit = "♠♥♦♣"[HeartsGame.suit(card)]
        text.textSize = w * 0.36f
        text.textAlign = Paint.Align.LEFT
        text.typeface = Ui.bold
        canvas.drawText(rank, x + w * 0.12f, y + h * 0.31f, text)
        canvas.drawText(suit.toString(), x + w * 0.12f, y + h * 0.62f, text)
        text.typeface = Ui.regular
    }
}