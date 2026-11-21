package com.woody.booklauncher.games

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.woody.booklauncher.Ui
import com.woody.booklauncher.dpf
import kotlin.math.abs

/**
 * Chess board. White pieces are white discs with a black letter, black pieces
 * are black discs with a white letter, so no special font or bitmap is needed
 * and the board stays readable on a 16-step e-ink panel.
 */
internal class ChessView(
    context: Context,
    private val game: ChessGame,
    private val onMove: (Int, Int) -> Unit,
) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpf(1)
        color = Ui.BLACK
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Ui.bold
    }
    private var selected = -1
    var lastMove: ChessGame.Move? = null

    private fun side() = minOf(width, height).toFloat()
    private fun left() = (width - side()) / 2f
    private fun top() = (height - side()) / 2f
    private fun cell() = side() / 8f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true
        if (!game.isHumanTurn()) return true
        val x = event.x - left()
        val y = event.y - top()
        if (x < 0 || y < 0 || x >= side() || y >= side()) return true
        val col = (x / cell()).toInt().coerceIn(0, 7)
        val row = (y / cell()).toInt().coerceIn(0, 7)
        val square = row * 8 + col
        if (selected >= 0) {
            if (square == selected) {
                selected = -1
                invalidate()
                return true
            }
            if (game.legalMovesFrom(selected).any { it.to == square }) {
                onMove(selected, square)
                selected = -1
                return true
            }
        }
        if (sign(game.pieceAt(square)) == game.human) {
            selected = square
            invalidate()
        }
        return true
    }

    fun clearSelection() {
        selected = -1
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val side = side()
        val left = left()
        val top = top()
        val cell = cell()
        val targets = if (selected >= 0) game.legalMovesFrom(selected).map { it.to }.toSet() else emptySet()
        val checkKing = if (game.inCheck(game.turn)) game.board.indexOfFirst { it == game.turn * ChessGame.KING } else -1
        for (square in 0 until 64) {
            val row = square / 8
            val col = square % 8
            val x = left + col * cell
            val y = top + row * cell
            fill.color = if ((row + col) % 2 == 0) 0xFFF2F2EF.toInt() else 0xFFC9C9C4.toInt()
            canvas.drawRect(x, y, x + cell, y + cell, fill)
            val last = lastMove
            if (last != null && (square == last.from || square == last.to)) {
                fill.color = 0x22000000
                canvas.drawRect(x, y, x + cell, y + cell, fill)
            }
            if (square == selected) {
                line.strokeWidth = context.dpf(3)
                canvas.drawRect(x + 2, y + 2, x + cell - 2, y + cell - 2, line)
                line.strokeWidth = context.dpf(1)
            }
            if (square == checkKing) {
                line.strokeWidth = context.dpf(3)
                canvas.drawRect(x + 5, y + 5, x + cell - 5, y + cell - 5, line)
                line.strokeWidth = context.dpf(1)
            }
            val p = game.pieceAt(square)
            if (p != 0) {
                val cx = x + cell / 2
                val cy = y + cell / 2
                fill.color = if (p > 0) Color.WHITE else Ui.BLACK
                canvas.drawCircle(cx, cy, cell * 0.36f, fill)
                line.strokeWidth = context.dpf(1.6f)
                canvas.drawCircle(cx, cy, cell * 0.36f, line)
                line.strokeWidth = context.dpf(1)
                text.color = if (p > 0) Ui.BLACK else Color.WHITE
                text.textSize = cell * 0.42f
                val baseline = cy - (text.descent() + text.ascent()) / 2f
                canvas.drawText(letter(abs(p)), cx, baseline, text)
            } else if (square in targets) {
                fill.color = Ui.GRAY
                canvas.drawCircle(x + cell / 2, y + cell / 2, cell * 0.10f, fill)
                line.strokeWidth = context.dpf(1.5f)
                canvas.drawCircle(x + cell / 2, y + cell / 2, cell * 0.22f, line)
                line.strokeWidth = context.dpf(1)
            }
            // A small coordinate in the corner keeps the board readable.
            text.color = Ui.GRAY
            text.textSize = cell * 0.15f
            text.typeface = Ui.regular
            if (row == 7) canvas.drawText("abcdefgh"[col].toString(), x + cell * 0.14f, y + cell * 0.88f, text)
            if (col == 0) canvas.drawText((8 - row).toString(), x + cell * 0.14f, y + cell * 0.25f, text)
            text.typeface = Ui.bold
        }
        line.strokeWidth = context.dpf(1)
        canvas.drawRect(left, top, left + side, top + side, line)
    }

    private fun letter(piece: Int): String = when (piece) {
        ChessGame.PAWN -> "P"
        ChessGame.KNIGHT -> "N"
        ChessGame.BISHOP -> "B"
        ChessGame.ROOK -> "R"
        ChessGame.QUEEN -> "Q"
        else -> "K"
    }

    private fun sign(p: Int) = if (p > 0) 1 else if (p < 0) -1 else 0
}
