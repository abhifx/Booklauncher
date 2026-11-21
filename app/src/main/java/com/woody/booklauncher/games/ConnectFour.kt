package com.woody.booklauncher.games

import kotlin.random.Random

/**
 * Connect Four: 7열 × 6줄. 사람(0)과 기기(1)가 번갈아 한 열에 떨어뜨린다.
 * [aiMove] 는 난이도 0(무작위)·1(이기기/막기/가운데)·2(네 수 앞을 보는 미니맥스).
 */
internal class ConnectFourGame {
    val rows = 6
    val cols = 7
    var board = IntArray(rows * cols) { -1 }
        private set
    var turn = HUMAN
        private set
    /** 0 사람, 1 기기, -1 아직 없음 */
    var winner = -1
        private set
    var over = false
        private set
    var lastDrop = -1
        private set

    private val history = ArrayDeque<Snapshot>()

    private data class Snapshot(val board: IntArray, val turn: Int, val winner: Int, val over: Boolean, val last: Int)

    val canUndo: Boolean get() = history.isNotEmpty()

    fun newGame() {
        board = IntArray(rows * cols) { -1 }
        turn = HUMAN
        winner = -1
        over = false
        lastDrop = -1
        history.clear()
    }

    /** [col] 에 [player] 의 돌을 떨어뜨린다. 놓은 줄(0 위)을 돌려주고, 못 놓으면 -1. */
    fun drop(col: Int, player: Int): Int {
        if (over || col !in 0 until cols) return -1
        val row = dropRow(board, col) ?: return -1
        history.addLast(Snapshot(board.copyOf(), turn, winner, over, lastDrop))
        while (history.size > 30) history.removeFirst()
        board[row * cols + col] = player
        lastDrop = row * cols + col
        if (hasWon(board, player)) {
            winner = player
            over = true
        } else if (board.none { it < 0 }) {
            over = true
        } else {
            turn = if (player == HUMAN) AI else HUMAN
        }
        return row
    }

    fun undo(): Boolean {
        val s = history.removeLastOrNull() ?: return false
        board = s.board.copyOf()
        turn = s.turn
        winner = s.winner
        over = s.over
        lastDrop = s.last
        return true
    }

    fun canDrop(col: Int): Boolean = col in 0 until cols && dropRow(board, col) != null && !over

    /** 이긴 네 칸. 없으면 빈 목록. */
    fun winningCells(player: Int = winner): List<Int> = if (player < 0) emptyList() else winLine(board, player)

    fun aiMove(level: Int, rng: Random = Random.Default): Int {
        if (over) return -1
        val legal = (0 until cols).filter { dropRow(board, it) != null }
        if (legal.isEmpty()) return -1
        return when (level) {
            0 -> legal[rng.nextInt(legal.size)]
            1 -> normalMove(legal)
            else -> minimaxMove(legal)
        }
    }

    private fun normalMove(legal: List<Int>): Int {
        for (c in legal) if (wins(board, c, AI)) return c
        for (c in legal) if (wins(board, c, HUMAN)) return c
        val order = listOf(3, 2, 4, 1, 5, 0, 6)
        return order.firstOrNull { it in legal } ?: legal.first()
    }

    private fun minimaxMove(legal: List<Int>): Int {
        var best = legal.first()
        var bestScore = Int.MIN_VALUE
        for (c in legal) {
            val b = board.copyOf()
            val row = dropRow(b, c) ?: continue
            b[row * cols + c] = AI
            val s = minimax(b, 4, HUMAN)
            if (s > bestScore) { bestScore = s; best = c }
        }
        return best
    }

    private fun minimax(b: IntArray, depth: Int, player: Int): Int {
        if (hasWon(b, AI)) return 1_000_000 + depth
        if (hasWon(b, HUMAN)) return -1_000_000 - depth
        if (b.none { it < 0 } || depth == 0) return evaluate(b)
        val legal = (0 until cols).filter { dropRow(b, it) != null }
        var best = if (player == AI) Int.MIN_VALUE else Int.MAX_VALUE
        for (c in legal) {
            val next = b.copyOf()
            val row = dropRow(next, c) ?: continue
            next[row * cols + c] = player
            val s = minimax(next, depth - 1, if (player == AI) HUMAN else AI)
            best = if (player == AI) maxOf(best, s) else minOf(best, s)
        }
        return best
    }

    /** 네 칸 묶음마다 기기 돌과 사람 돌의 차이. 가운데를 조금 더 준다. */
    private fun evaluate(b: IntArray): Int {
        var score = 0
        for (r in 0 until rows) for (c in 0 until cols) {
            val v = b[r * cols + c]
            if (v == AI) score += if (c == 3) 3 else 1
            if (v == HUMAN) score -= if (c == 3) 3 else 1
        }
        for (line in lines()) {
            val ai = line.count { b[it] == AI }
            val hu = line.count { b[it] == HUMAN }
            if (ai > 0 && hu == 0) score += ai * ai * 4
            if (hu > 0 && ai == 0) score -= hu * hu * 4
        }
        return score
    }

    private fun wins(b: IntArray, col: Int, player: Int): Boolean {
        val row = dropRow(b, col) ?: return false
        val copy = b.copyOf()
        copy[row * cols + col] = player
        return hasWon(copy, player)
    }

    companion object {
        const val HUMAN = 0
        const val AI = 1

        fun dropRow(b: IntArray, col: Int): Int? {
            for (r in 5 downTo 0) if (b[r * 7 + col] < 0) return r
            return null
        }

        fun hasWon(b: IntArray, player: Int): Boolean = winLine(b, player).isNotEmpty()

        fun winLine(b: IntArray, player: Int): List<Int> {
            if (player < 0) return emptyList()
            for (r in 0 until 6) for (c in 0 until 7) {
                if (b[r * 7 + c] != player) continue
                for ((dr, dc) in listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)) {
                    val cells = (0 until 4).map { i -> (r + dr * i) to (c + dc * i) }
                    if (cells.all { (rr, cc) -> rr in 0 until 6 && cc in 0 until 7 && b[rr * 7 + cc] == player })
                        return cells.map { (rr, cc) -> rr * 7 + cc }
                }
            }
            return emptyList()
        }

        private fun lines(): List<List<Int>> {
            val out = mutableListOf<List<Int>>()
            for (r in 0 until 6) for (c in 0 until 7) {
                for ((dr, dc) in listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)) {
                    val cells = (0 until 4).map { i -> (r + dr * i) to (c + dc * i) }
                    if (cells.all { (rr, cc) -> rr in 0 until 6 && cc in 0 until 7 })
                        out += cells.map { (rr, cc) -> rr * 7 + cc }
                }
            }
            return out
        }

        fun decode(s: String): ConnectFourGame? = runCatching {
            val p = s.split('|')
            ConnectFourGame().apply {
                board = p[0].split(',').map { it.toInt() }.toIntArray()
                turn = p[1].toInt()
                winner = p[2].toInt()
                over = p[3] == "1"
                lastDrop = p[4].toInt()
                require(board.size == rows * cols)
            }
        }.getOrNull()
    }

    fun encode(): String = listOf(
        board.joinToString(","), turn, winner, if (over) 1 else 0, lastDrop,
    ).joinToString("|")
}
