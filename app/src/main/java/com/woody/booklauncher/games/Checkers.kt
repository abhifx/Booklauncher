package com.woody.booklauncher.games

import kotlin.random.Random

/**
 * 영국식 체커(English draughts), 8×8. 어두운 칸만 쓴다. 잡을 수 있으면 반드시 잡고,
 * 여러 개를 연달아 잡는 동안은 같은 차례가 이어진다. 사람은 위로, 기기는 아래로 간다.
 * 값: 0 빈 칸, 1 사람 말, 2 사람 킹, 3 기기 말, 4 기기 킹.
 */
internal class CheckersGame {
    var board = IntArray(64)
        private set
    var turn = HUMAN
        private set
    var winner = -1
        private set
    var over = false
        private set
    var selected = -1
    var chain = false
        private set
    var movesWithoutCapture = 0
        private set

    private val turnStarts = ArrayDeque<Snapshot>()

    private data class Snapshot(val board: IntArray, val turn: Int, val winner: Int, val over: Boolean, val moves: Int)

    data class Move(val from: Int, val to: Int, val captured: Int = -1)

    init {
        newGame()
    }

    fun newGame() {
        board = IntArray(64)
        for (r in 0 until 3) for (c in 0 until 8) if ((r + c) % 2 == 1) board[r * 8 + c] = AI_MAN
        for (r in 5 until 8) for (c in 0 until 8) if ((r + c) % 2 == 1) board[r * 8 + c] = HUMAN_MAN
        turn = HUMAN
        winner = -1
        over = false
        selected = -1
        chain = false
        movesWithoutCapture = 0
        turnStarts.clear()
        turnStarts.addLast(snapshot())
    }

    fun canUndo(): Boolean = turnStarts.size > 1

    /** 지난 사람 차례의 시작으로 돌아간다(기기 수까지 함께). */
    fun undoRound(): Boolean {
        if (turnStarts.size < 2) return false
        turnStarts.removeLast()
        restore(turnStarts.last())
        selected = -1
        chain = false
        return true
    }

    /** [player] 가 둘 수 있는 수. 잡을 수 있으면 잡는 수만. */
    fun movesFor(player: Int): List<Move> {
        val captures = mutableListOf<Move>()
        val simple = mutableListOf<Move>()
        for (i in 0 until 64) {
            val p = board[i]
            if (p == 0 || owner(p) != player) continue
            val (caps, steps) = movesFrom(i, p)
            captures += caps
            simple += steps
        }
        return if (captures.isNotEmpty()) captures else simple
    }

    fun movesFrom(index: Int): List<Move> {
        val p = board.getOrNull(index) ?: return emptyList()
        if (p == 0) return emptyList()
        val player = owner(p)
        return movesFor(player).filter { it.from == index }
    }

    /** 고른 칸을 바꾼다. 그 차례의 말이면 true. */
    fun select(index: Int): Boolean {
        if (over || index !in 0 until 64 || owner(board[index]) != turn) return false
        if (chain && index != selected) return false
        selected = index
        return true
    }

    /** [move] 를 둔다. 규칙에 맞지 않으면 false. */
    fun apply(move: Move): Boolean {
        if (over) return false
        val legal = movesFor(turn)
        if (move !in legal) return false
        val p = board[move.from]
        board[move.from] = 0
        board[move.to] = promote(p, move.to)
        if (move.captured >= 0) {
            board[move.captured] = 0
            movesWithoutCapture = 0
        } else {
            movesWithoutCapture++
        }
        val promoted = board[move.to] != p
        val more = if (move.captured >= 0 && !promoted) movesFrom(move.to).filter { it.captured >= 0 } else emptyList()
        if (more.isNotEmpty()) {
            selected = move.to
            chain = true
        } else {
            chain = false
            selected = -1
            turn = if (turn == HUMAN) AI else HUMAN
            if (turn == HUMAN) turnStarts.addLast(snapshot())
            finishOrDraw()
        }
        return true
    }

    fun aiMove(level: Int, rng: Random = Random.Default): Move? {
        if (over || turn != AI) return null
        val moves = movesFor(AI)
        if (moves.isEmpty()) { winner = HUMAN; over = true; return null }
        val move = if (level == 0) moves[rng.nextInt(moves.size)] else {
            val best = moves.maxOf { if (it.captured >= 0) 10 else 0 }
            val candidates = moves.filter { (if (it.captured >= 0) 10 else 0) == best }
            candidates.maxByOrNull { (it.to / 8) - (it.from / 8) } ?: candidates.first()
        }
        apply(move)
        return move
    }

    /** 고른 칸에서 갈 수 있는 칸(표시용). */
    fun destinations(index: Int): List<Int> = if (index !in 0 until 64) emptyList() else movesFrom(index).map { it.to }

    fun winnerAfterNoMoves() {
        if (over) return
        if (movesFor(turn).isEmpty()) {
            winner = if (turn == HUMAN) AI else HUMAN
            over = true
        }
    }

    private fun finishOrDraw() {
        if (movesFor(turn).isEmpty()) {
            winner = if (turn == HUMAN) AI else HUMAN
            over = true
            return
        }
        if (movesWithoutCapture >= 40) {
            winner = -1
            over = true
        }
    }

    private fun movesFrom(index: Int, p: Int): Pair<List<Move>, List<Move>> {
        val r = index / 8
        val c = index % 8
        val king = p == HUMAN_KING || p == AI_KING
        val dirs = if (king) listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
        else if (owner(p) == HUMAN) listOf(-1 to -1, -1 to 1)
        else listOf(1 to -1, 1 to 1)
        val caps = mutableListOf<Move>()
        val steps = mutableListOf<Move>()
        for ((dr, dc) in dirs) {
            val r1 = r + dr
            val c1 = c + dc
            if (r1 !in 0 until 8 || c1 !in 0 until 8) continue
            val j = r1 * 8 + c1
            if (board[j] == 0) {
                steps += Move(index, j)
            } else if (owner(board[j]) != owner(p)) {
                val r2 = r + dr * 2
                val c2 = c + dc * 2
                if (r2 in 0 until 8 && c2 in 0 until 8) {
                    val k = r2 * 8 + c2
                    if (board[k] == 0) caps += Move(index, k, j)
                }
            }
        }
        return caps to steps
    }

    private fun promote(p: Int, to: Int): Int {
        val row = to / 8
        return when (p) {
            HUMAN_MAN -> if (row == 0) HUMAN_KING else p
            AI_MAN -> if (row == 7) AI_KING else p
            else -> p
        }
    }

    private fun owner(p: Int): Int = when (p) {
        HUMAN_MAN, HUMAN_KING -> HUMAN
        AI_MAN, AI_KING -> AI
        else -> -1
    }

    private fun snapshot() = Snapshot(board.copyOf(), turn, winner, over, movesWithoutCapture)

    private fun restore(s: Snapshot) {
        board = s.board.copyOf()
        turn = s.turn
        winner = s.winner
        over = s.over
        movesWithoutCapture = s.moves
    }

    fun encode(): String = listOf(
        board.joinToString(""), turn, winner, if (over) 1 else 0, movesWithoutCapture,
    ).joinToString("|")

    companion object {
        const val HUMAN = 0
        const val AI = 1
        const val HUMAN_MAN = 1
        const val HUMAN_KING = 2
        const val AI_MAN = 3
        const val AI_KING = 4

        fun decode(s: String): CheckersGame? = runCatching {
            val p = s.split('|')
            CheckersGame().apply {
                board = IntArray(64) { p[0][it] - '0' }
                turn = p[1].toInt()
                winner = p[2].toInt()
                over = p[3] == "1"
                movesWithoutCapture = p[4].toInt()
                selected = -1
                chain = false
                turnStarts.clear()
                turnStarts.addLast(snapshot())
                require(board.size == 64)
            }
        }.getOrNull()
    }
}
