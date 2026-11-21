package com.woody.booklauncher.games

import kotlin.math.abs
import kotlin.random.Random

/**
 * Full chess rules for one game against the device: castling, en passant,
 * promotion, check, checkmate, stalemate, the fifty-move rule and the usual
 * insufficient-material draws. The board is 64 squares, a1 = 56 and h8 = 7;
 * White moves toward lower indices. White pieces are positive.
 *
 * The computer uses a small alpha-beta search over material and piece-square
 * tables. It is deliberately modest: a phone-class e-ink reader should answer
 * in well under a second, not play like an engine.
 */
internal class ChessGame(var human: Int = WHITE) {
    enum class Result { ONGOING, CHECKMATE, STALEMATE, DRAW }

    data class Move(val from: Int, val to: Int, val promotion: Int = 0, val castle: Boolean = false)

    private data class Snapshot(
        val board: IntArray,
        val turn: Int,
        val castling: Int,
        val ep: Int,
        val half: Int,
        val full: Int,
        val over: Boolean,
        val winner: Int,
        val result: Result,
    )

    var board = IntArray(64)
        private set
    var turn = WHITE
        private set
    var castling = ALL_CASTLING
        private set
    var ep = -1
        private set
    var half = 0
        private set
    var full = 1
        private set
    var over = false
        private set
    var winner = 0
        private set
    var result = Result.ONGOING
        private set
    private val history = mutableListOf<Snapshot>()

    init {
        newGame()
    }

    fun newGame() {
        board = IntArray(64)
        val back = intArrayOf(ROOK, KNIGHT, BISHOP, QUEEN, KING, BISHOP, KNIGHT, ROOK)
        for (i in 0 until 8) {
            board[i] = -back[i]
            board[8 + i] = -PAWN
            board[48 + i] = PAWN
            board[56 + i] = back[i]
        }
        turn = WHITE
        castling = ALL_CASTLING
        ep = -1
        half = 0
        full = 1
        over = false
        winner = 0
        result = Result.ONGOING
        history.clear()
    }

    fun pieceAt(square: Int): Int = board[square]

    /** Test/analysis hook: replace the position without touching the game history. */
    internal fun loadPosition(newBoard: IntArray, newTurn: Int, newCastling: Int = ALL_CASTLING, newEp: Int = -1) {
        require(newBoard.size == 64)
        board = newBoard.copyOf()
        turn = newTurn
        castling = newCastling
        ep = newEp
        half = 0
        full = 1
        over = false
        winner = 0
        result = Result.ONGOING
        history.clear()
    }

    fun isHumanTurn(): Boolean = !over && turn == human

    fun inCheck(player: Int = turn): Boolean {
        val king = board.indexOfFirst { it == player * KING }
        return king >= 0 && isAttacked(king, -player)
    }

    /** Every legal move for [player], including the four promotion choices. */
    fun legalMoves(player: Int = turn): List<Move> {
        val out = mutableListOf<Move>()
        for (from in 0 until 64) {
            if (sign(board[from]) != player) continue
            for (move in pseudoMoves(from, player)) {
                val snap = make(move)
                if (!inCheck(player)) out += move
                unmake(snap)
            }
        }
        return out
    }

    fun legalMovesFrom(from: Int): List<Move> = legalMoves(turn).filter { it.from == from }

    fun isPromotion(from: Int, to: Int): Boolean =
        abs(board[from]) == PAWN && (to / 8 == 0 || to / 8 == 7)

    /**
     * Plays a move for the side to move. When a pawn reaches the last rank and
     * [promotion] is 0, the move becomes a queen.
     */
    fun play(from: Int, to: Int, promotion: Int = 0): Boolean {
        if (over) return false
        val legal = legalMoves(turn)
        val want = if (promotion == 0) QUEEN else promotion
        val move = legal.firstOrNull {
            it.from == from && it.to == to &&
                (if (it.promotion == 0) promotion == 0 else it.promotion == want)
        } ?: return false
        val snap = make(move)
        history += snap
        val opponent = turn
        val opponentMoves = legalMoves(opponent)
        when {
            opponentMoves.isEmpty() -> {
                over = true
                if (inCheck(opponent)) {
                    result = Result.CHECKMATE
                    winner = -opponent
                } else {
                    result = Result.STALEMATE
                    winner = 0
                }
            }
            half >= 100 -> {
                over = true
                result = Result.DRAW
                winner = 0
            }
            insufficientMaterial() -> {
                over = true
                result = Result.DRAW
                winner = 0
            }
        }
        return true
    }

    /** Takes back the last human move and the computer's answer, when there is one. */
    fun undo(): Boolean {
        if (history.isEmpty()) return false
        unmake(history.removeAt(history.lastIndex))
        while (history.isNotEmpty() && turn != human) {
            unmake(history.removeAt(history.lastIndex))
        }
        over = false
        winner = 0
        result = Result.ONGOING
        return true
    }

    fun aiMove(level: Int, rng: Random = Random.Default): Move? {
        val moves = legalMoves(turn)
        if (moves.isEmpty()) return null
        if (level <= 0) return moves[rng.nextInt(moves.size)]
        if (level == 1) {
            var best = moves.first()
            var bestScore = Int.MIN_VALUE
            for (move in moves) {
                val snap = make(move)
                val score = -evaluate(turn) + rng.nextInt(4)
                unmake(snap)
                if (score > bestScore) {
                    bestScore = score
                    best = move
                }
            }
            return best
        }
        var best = moves.first()
        var bestScore = Int.MIN_VALUE
        var alpha = -MATE
        for (move in ordered(moves)) {
            val snap = make(move)
            val score = -search(2, -MATE, -alpha, turn)
            unmake(snap)
            if (score > bestScore) {
                bestScore = score
                best = move
            }
            if (score > alpha) alpha = score
        }
        return best
    }

    /** A perft node count, used by the unit tests to prove the move generator. */
    internal fun perft(depth: Int): Long {
        if (depth == 0) return 1
        var nodes = 0L
        for (move in legalMoves(turn)) {
            val snap = make(move)
            nodes += perft(depth - 1)
            unmake(snap)
        }
        return nodes
    }

    fun encode(): String = listOf(
        board.joinToString(","), turn, castling, ep, half, full, human,
        result.ordinal, winner, if (over) 1 else 0,
    ).joinToString("|")

    // ---- move generation ---------------------------------------------------

    private fun pseudoMoves(from: Int, player: Int): List<Move> {
        val piece = board[from]
        if (sign(piece) != player) return emptyList()
        val type = abs(piece)
        val row = from / 8
        val col = from % 8
        val out = mutableListOf<Move>()
        when (type) {
            PAWN -> pawnMoves(from, row, col, player, out)
            KNIGHT -> for ((dr, dc) in KNIGHT_STEPS) {
                val r = row + dr
                val c = col + dc
                if (r in 0..7 && c in 0..7) addIfFreeOrEnemy(from, r * 8 + c, player, out)
            }
            BISHOP -> slide(from, row, col, player, out, DIAGONAL)
            ROOK -> slide(from, row, col, player, out, STRAIGHT)
            QUEEN -> {
                slide(from, row, col, player, out, DIAGONAL)
                slide(from, row, col, player, out, STRAIGHT)
            }
            KING -> {
                for ((dr, dc) in KING_STEPS) {
                    val r = row + dr
                    val c = col + dc
                    if (r in 0..7 && c in 0..7) addIfFreeOrEnemy(from, r * 8 + c, player, out)
                }
                castleMoves(from, player, out)
            }
        }
        return out
    }

    private fun pawnMoves(from: Int, row: Int, col: Int, player: Int, out: MutableList<Move>) {
        val dir = if (player == WHITE) -1 else 1
        val startRow = if (player == WHITE) 6 else 1
        val lastRow = if (player == WHITE) 0 else 7
        val one = row + dir
        if (one in 0..7 && board[one * 8 + col] == 0) {
            addPawn(from, one * 8 + col, one == lastRow, out)
            val two = row + 2 * dir
            if (row == startRow && board[two * 8 + col] == 0) {
                out += Move(from, two * 8 + col)
            }
        }
        for (dc in intArrayOf(-1, 1)) {
            val c = col + dc
            if (c !in 0..7 || one !in 0..7) continue
            val to = one * 8 + c
            if (sign(board[to]) == -player || to == ep) addPawn(from, to, one == lastRow, out)
        }
    }

    private fun addPawn(from: Int, to: Int, promotion: Boolean, out: MutableList<Move>) {
        if (promotion) {
            for (p in intArrayOf(QUEEN, ROOK, BISHOP, KNIGHT)) out += Move(from, to, p)
        } else {
            out += Move(from, to)
        }
    }

    private fun slide(from: Int, row: Int, col: Int, player: Int, out: MutableList<Move>, dirs: List<Pair<Int, Int>>) {
        for ((dr, dc) in dirs) {
            var r = row + dr
            var c = col + dc
            while (r in 0..7 && c in 0..7) {
                val to = r * 8 + c
                val target = board[to]
                if (target == 0) {
                    out += Move(from, to)
                } else {
                    if (sign(target) != player) out += Move(from, to)
                    break
                }
                r += dr
                c += dc
            }
        }
    }

    private fun addIfFreeOrEnemy(from: Int, to: Int, player: Int, out: MutableList<Move>) {
        if (sign(board[to]) != player) out += Move(from, to)
    }

    private fun castleMoves(from: Int, player: Int, out: MutableList<Move>) {
        if (player == WHITE && from == 60) {
            if (castling and WK != 0 && board[61] == 0 && board[62] == 0 &&
                !isAttacked(60, BLACK) && !isAttacked(61, BLACK) && !isAttacked(62, BLACK)
            ) out += Move(60, 62, castle = true)
            if (castling and WQ != 0 && board[59] == 0 && board[58] == 0 && board[57] == 0 &&
                !isAttacked(60, BLACK) && !isAttacked(59, BLACK) && !isAttacked(58, BLACK)
            ) out += Move(60, 58, castle = true)
        } else if (player == BLACK && from == 4) {
            if (castling and BK != 0 && board[5] == 0 && board[6] == 0 &&
                !isAttacked(4, WHITE) && !isAttacked(5, WHITE) && !isAttacked(6, WHITE)
            ) out += Move(4, 6, castle = true)
            if (castling and BQ != 0 && board[3] == 0 && board[2] == 0 && board[1] == 0 &&
                !isAttacked(4, WHITE) && !isAttacked(3, WHITE) && !isAttacked(2, WHITE)
            ) out += Move(4, 2, castle = true)
        }
    }

    // ---- making and unmaking ----------------------------------------------

    private fun make(move: Move): Snapshot {
        val snap = Snapshot(board.copyOf(), turn, castling, ep, half, full, over, winner, result)
        val moving = board[move.from]
        val color = sign(moving)
        val type = abs(moving)
        val captured = board[move.to]
        val wasEp = type == PAWN && move.to == ep && move.from % 8 != move.to % 8
        if (wasEp) {
            board[move.to + if (color == WHITE) 8 else -8] = 0
        }
        board[move.to] = if (move.promotion != 0) color * move.promotion else moving
        board[move.from] = 0
        if (type == KING && abs(move.to - move.from) == 2) {
            if (move.to > move.from) {
                board[move.from + 1] = board[move.from + 3]
                board[move.from + 3] = 0
            } else {
                board[move.from - 1] = board[move.from - 4]
                board[move.from - 4] = 0
            }
        }
        if (type == KING) castling = castling and (if (color == WHITE) (BK or BQ) else (WK or WQ))
        if (type == ROOK) castling = clearRook(castling, move.from)
        if (abs(captured) == ROOK) castling = clearRook(castling, move.to)
        ep = if (type == PAWN && abs(move.to - move.from) == 16) (move.from + move.to) / 2 else -1
        half = if (type == PAWN || captured != 0 || wasEp) 0 else half + 1
        turn = -turn
        if (turn == WHITE) full++
        return snap
    }

    private fun unmake(snap: Snapshot) {
        snap.board.copyInto(board)
        turn = snap.turn
        castling = snap.castling
        ep = snap.ep
        half = snap.half
        full = snap.full
        over = snap.over
        winner = snap.winner
        result = snap.result
    }

    private fun clearRook(rights: Int, square: Int): Int = when (square) {
        56 -> rights and WQ.inv()
        63 -> rights and WK.inv()
        0 -> rights and BQ.inv()
        7 -> rights and BK.inv()
        else -> rights
    }

    // ---- attacks and evaluation -------------------------------------------

    private fun isAttacked(square: Int, by: Int): Boolean {
        val row = square / 8
        val col = square % 8
        // Pawns
        val pawnRow = row + if (by == WHITE) 1 else -1
        if (pawnRow in 0..7) {
            for (dc in intArrayOf(-1, 1)) {
                val c = col + dc
                if (c in 0..7 && board[pawnRow * 8 + c] == by * PAWN) return true
            }
        }
        // Knights
        for ((dr, dc) in KNIGHT_STEPS) {
            val r = row + dr
            val c = col + dc
            if (r in 0..7 && c in 0..7 && board[r * 8 + c] == by * KNIGHT) return true
        }
        // King
        for ((dr, dc) in KING_STEPS) {
            val r = row + dr
            val c = col + dc
            if (r in 0..7 && c in 0..7 && board[r * 8 + c] == by * KING) return true
        }
        // Sliding pieces
        for ((dr, dc) in DIAGONAL) {
            var r = row + dr
            var c = col + dc
            while (r in 0..7 && c in 0..7) {
                val p = board[r * 8 + c]
                if (p != 0) {
                    if (sign(p) == by && (abs(p) == BISHOP || abs(p) == QUEEN)) return true
                    break
                }
                r += dr
                c += dc
            }
        }
        for ((dr, dc) in STRAIGHT) {
            var r = row + dr
            var c = col + dc
            while (r in 0..7 && c in 0..7) {
                val p = board[r * 8 + c]
                if (p != 0) {
                    if (sign(p) == by && (abs(p) == ROOK || abs(p) == QUEEN)) return true
                    break
                }
                r += dr
                c += dc
            }
        }
        return false
    }

    private fun evaluate(player: Int): Int {
        var white = 0
        var pieces = 0
        for (square in 0 until 64) {
            val p = board[square]
            if (p == 0) continue
            if (abs(p) != PAWN) pieces++
            val value = VALUES[abs(p)]
            val table = TABLES[abs(p)] ?: KING_TABLE
            val index = if (p > 0) square else 63 - square
            val score = value + table[index]
            if (p > 0) white += score else white -= score
        }
        if (pieces <= 6) {
            for (square in 0 until 64) {
                val p = board[square]
                if (abs(p) == KING) {
                    val index = if (p > 0) square else 63 - square
                    white += if (p > 0) KING_END[index] else -KING_END[index]
                }
            }
        }
        return if (player == WHITE) white else -white
    }

    private fun search(depth: Int, alphaStart: Int, betaStart: Int, player: Int): Int {
        if (depth == 0) return evaluate(player)
        val moves = legalMoves(player)
        if (moves.isEmpty()) return if (inCheck(player)) -MATE + (3 - depth) else 0
        var alpha = alphaStart
        var best = -MATE
        for (move in ordered(moves)) {
            val snap = make(move)
            val score = -search(depth - 1, -betaStart, -alpha, -player)
            unmake(snap)
            if (score > best) best = score
            if (score > alpha) alpha = score
            if (alpha >= betaStart) break
        }
        return best
    }

    private fun ordered(moves: List<Move>): List<Move> = moves.sortedByDescending { move ->
        var score = 0
        val target = board[move.to]
        if (target != 0) score += 10 * VALUES[abs(target)] - VALUES[abs(board[move.from])]
        if (move.promotion != 0) score += VALUES[move.promotion]
        if (move.castle) score += 30
        score
    }

    private fun insufficientMaterial(): Boolean {
        var knights = 0
        var whiteBishops = 0
        var blackBishops = 0
        var others = 0
        val bishopSquares = mutableListOf<Int>()
        for (square in 0 until 64) {
            val p = board[square]
            when (abs(p)) {
                KNIGHT -> knights++
                BISHOP -> {
                    if (p > 0) whiteBishops++ else blackBishops++
                    bishopSquares += square
                }
                PAWN, ROOK, QUEEN -> others++
            }
        }
        if (others > 0) return false
        val bishops = whiteBishops + blackBishops
        if (knights == 0 && bishops == 0) return true
        if (knights + bishops == 1) return true
        if (knights == 0 && bishops == 2 && whiteBishops == 1 && blackBishops == 1) {
            val a = bishopSquares[0]
            val b = bishopSquares[1]
            return (a / 8 + a % 8) % 2 == (b / 8 + b % 8) % 2
        }
        return false
    }

    companion object {
        const val EMPTY = 0
        const val PAWN = 1
        const val KNIGHT = 2
        const val BISHOP = 3
        const val ROOK = 4
        const val QUEEN = 5
        const val KING = 6
        const val WHITE = 1
        const val BLACK = -1

        private const val WK = 1
        private const val WQ = 2
        private const val BK = 4
        private const val BQ = 8
        private const val ALL_CASTLING = WK or WQ or BK or BQ
        private const val MATE = 100_000

        private val KNIGHT_STEPS = listOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1)
        private val KING_STEPS = listOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1)
        private val DIAGONAL = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
        private val STRAIGHT = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

        private val VALUES = intArrayOf(0, 100, 320, 330, 500, 900, 20_000)

        private val PAWN_TABLE = intArrayOf(
            0, 0, 0, 0, 0, 0, 0, 0,
            50, 50, 50, 50, 50, 50, 50, 50,
            10, 10, 20, 30, 30, 20, 10, 10,
            5, 5, 10, 25, 25, 10, 5, 5,
            0, 0, 0, 20, 20, 0, 0, 0,
            5, -5, -10, 0, 0, -10, -5, 5,
            5, 10, 10, -20, -20, 10, 10, 5,
            0, 0, 0, 0, 0, 0, 0, 0,
        )
        private val KNIGHT_TABLE = intArrayOf(
            -50, -40, -30, -30, -30, -30, -40, -50,
            -40, -20, 0, 0, 0, 0, -20, -40,
            -30, 0, 10, 15, 15, 10, 0, -30,
            -30, 5, 15, 20, 20, 15, 5, -30,
            -30, 0, 15, 20, 20, 15, 0, -30,
            -30, 5, 10, 15, 15, 10, 5, -30,
            -40, -20, 0, 5, 5, 0, -20, -40,
            -50, -40, -30, -30, -30, -30, -40, -50,
        )
        private val BISHOP_TABLE = intArrayOf(
            -20, -10, -10, -10, -10, -10, -10, -20,
            -10, 0, 0, 0, 0, 0, 0, -10,
            -10, 0, 5, 10, 10, 5, 0, -10,
            -10, 5, 5, 10, 10, 5, 5, -10,
            -10, 0, 10, 10, 10, 10, 0, -10,
            -10, 10, 10, 10, 10, 10, 10, -10,
            -10, 5, 0, 0, 0, 0, 5, -10,
            -20, -10, -10, -10, -10, -10, -10, -20,
        )
        private val ROOK_TABLE = intArrayOf(
            0, 0, 0, 0, 0, 0, 0, 0,
            5, 10, 10, 10, 10, 10, 10, 5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            0, 0, 0, 5, 5, 0, 0, 0,
        )
        private val QUEEN_TABLE = intArrayOf(
            -20, -10, -10, -5, -5, -10, -10, -20,
            -10, 0, 0, 0, 0, 0, 0, -10,
            -10, 0, 5, 5, 5, 5, 0, -10,
            -5, 0, 5, 5, 5, 5, 0, -5,
            0, 0, 5, 5, 5, 5, 0, -5,
            -10, 5, 5, 5, 5, 5, 0, -10,
            -10, 0, 5, 0, 0, 0, 0, -10,
            -20, -10, -10, -5, -5, -10, -10, -20,
        )
        private val KING_TABLE = intArrayOf(
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -20, -30, -30, -40, -40, -30, -30, -20,
            -10, -20, -20, -20, -20, -20, -20, -10,
            20, 20, 0, 0, 0, 0, 20, 20,
            20, 30, 10, 0, 0, 10, 30, 20,
        )
        private val KING_END = intArrayOf(
            -50, -40, -30, -20, -20, -30, -40, -50,
            -30, -20, -10, 0, 0, -10, -20, -30,
            -30, -10, 20, 30, 30, 20, -10, -30,
            -30, -10, 30, 40, 40, 30, -10, -30,
            -30, -10, 30, 40, 40, 30, -10, -30,
            -30, -10, 20, 30, 30, 20, -10, -30,
            -30, -30, 0, 0, 0, 0, -30, -30,
            -50, -30, -30, -30, -30, -30, -30, -50,
        )
        private val TABLES = arrayOf(
            intArrayOf(), PAWN_TABLE, KNIGHT_TABLE, BISHOP_TABLE, ROOK_TABLE, QUEEN_TABLE, KING_TABLE,
        )

        private fun sign(p: Int): Int = if (p > 0) WHITE else if (p < 0) BLACK else 0

        fun decode(s: String): ChessGame? = runCatching {
            val p = s.split('|')
            ChessGame(p[6].toInt()).apply {
                val b = p[0].split(',').map { it.toInt() }.toIntArray()
                require(b.size == 64)
                board = b
                turn = p[1].toInt()
                castling = p[2].toInt()
                ep = p[3].toInt()
                half = p[4].toInt()
                full = p[5].toInt()
                result = Result.entries[p[7].toInt()]
                winner = p[8].toInt()
                over = p[9] == "1"
                history.clear()
            }
        }.getOrNull()
    }
}
