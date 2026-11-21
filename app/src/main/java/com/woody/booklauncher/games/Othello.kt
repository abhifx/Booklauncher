package com.woody.booklauncher.games

import kotlin.random.Random

/**
 * 오셀로(리버시). 8×8, 사람이 흑(1)이고 기기가 백(2)이다. 상대 돌을 사이에 두고 뒤집는 곳에만 둘 수 있고,
 * 둘 곳이 없으면 차례를 넘긴다. 둘 다 둘 곳이 없으면 끝나고 돌이 많은 쪽이 이긴다.
 */
internal class OthelloGame {
    var board = IntArray(64)
        private set
    var turn = HUMAN
        private set
    var over = false
        private set
    var winner = 0
        private set
    /** 마지막에 뒤집힌 칸(그리기 강조) */
    var lastFlips = emptyList<Int>()
        private set
    var last = -1
        private set
    private var passes = 0

    fun newGame() {
        board = IntArray(64)
        board[27] = AI      // d4
        board[28] = HUMAN   // e4
        board[35] = HUMAN   // d5
        board[36] = AI      // e5
        turn = HUMAN
        over = false
        winner = 0
        lastFlips = emptyList()
        last = -1
        passes = 0
    }

    fun score(): Pair<Int, Int> = board.count { it == HUMAN } to board.count { it == AI }

    fun legalMoves(player: Int = turn): List<Int> = board.indices.filter { flipsFor(it, player).isNotEmpty() }

    fun play(index: Int, player: Int = turn): Boolean {
        if (over || player != turn) return false
        val flips = flipsFor(index, player)
        if (flips.isEmpty()) return false
        board[index] = player
        flips.forEach { board[it] = player }
        last = index
        lastFlips = flips
        passes = 0
        val other = if (player == HUMAN) AI else HUMAN
        when {
            legalMoves(other).isNotEmpty() -> turn = other
            legalMoves(player).isNotEmpty() -> { turn = player; passes++ }
            else -> finish()
        }
        return true
    }

    /** 기기가 둘 곳을 고른다. 난이도 0 무작위, 1 모서리·뒤집기, 2 한 수 더 보기. */
    fun aiMove(level: Int, rng: Random = Random.Default): Int {
        if (over || turn != AI) return -1
        val legal = legalMoves(AI)
        if (legal.isEmpty()) return -1
        if (level == 0) return legal[rng.nextInt(legal.size)]
        var best = legal.first()
        var bestScore = Int.MIN_VALUE
        for (i in legal) {
            val flips = flipsFor(i, AI).size
            val corner = if (i in CORNERS) 30 else 0
            val edge = if (i / 8 == 0 || i / 8 == 7 || i % 8 == 0 || i % 8 == 7) 6 else 0
            val s = flips + corner + edge
            if (s > bestScore) { bestScore = s; best = i }
        }
        if (level < 2) return best
        // 두 수 앞: 내가 둔 뒤 상대가 얻는 최선을 빼고 고른다.
        var bestNet = Int.MIN_VALUE
        for (i in legal) {
            val copy = board.copyOf()
            val flips = flipsFor(i, AI)
            copy[i] = AI
            flips.forEach { copy[it] = AI }
            val opp = bestFlips(copy, HUMAN)
            val net = flips.size - opp
            if (net > bestNet) { bestNet = net; best = i }
        }
        return best
    }

    private fun bestFlips(b: IntArray, player: Int): Int {
        var best = 0
        for (i in b.indices) {
            if (b[i] == 0) best = maxOf(best, flipsOn(b, i, player).size)
        }
        return best
    }

    private fun flipsFor(index: Int, player: Int): List<Int> = flipsOn(board, index, player)

    private fun flipsOn(b: IntArray, index: Int, player: Int): List<Int> {
        if (index !in b.indices || b[index] != 0) return emptyList()
        val other = if (player == HUMAN) AI else HUMAN
        val r = index / 8
        val c = index % 8
        val out = mutableListOf<Int>()
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            var rr = r + dr
            var cc = c + dc
            val line = mutableListOf<Int>()
            while (rr in 0 until 8 && cc in 0 until 8 && b[rr * 8 + cc] == other) {
                line += rr * 8 + cc
                rr += dr
                cc += dc
            }
            if (line.isNotEmpty() && rr in 0 until 8 && cc in 0 until 8 && b[rr * 8 + cc] == player) out += line
        }
        return out
    }

    private fun finish() {
        over = true
        val (h, a) = score()
        winner = when {
            h > a -> HUMAN
            a > h -> AI
            else -> 0
        }
    }

    fun encode(): String = listOf(
        board.joinToString(","), turn, if (over) 1 else 0, winner, last, lastFlips.joinToString(","),
    ).joinToString("|")

    companion object {
        const val HUMAN = 1
        const val AI = 2
        private val CORNERS = setOf(0, 7, 56, 63)

        fun decode(s: String): OthelloGame? = runCatching {
            val p = s.split('|')
            OthelloGame().apply {
                board = p[0].split(',').map { it.toInt() }.toIntArray()
                turn = p[1].toInt()
                over = p[2] == "1"
                winner = p[3].toInt()
                last = p[4].toInt()
                lastFlips = if (p[5].isEmpty()) emptyList() else p[5].split(',').map { it.toInt() }
                require(board.size == 64)
            }
        }.getOrNull()
    }
}
