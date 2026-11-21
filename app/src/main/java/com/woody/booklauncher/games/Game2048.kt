package com.woody.booklauncher.games

import kotlin.random.Random

/**
 * 2048 판. 칸 값은 지수(1 = 2, 11 = 2048)로 둔다. 밀기 전 판을 [undo] 에 남겨 한 수 되돌린다.
 * 규칙만 두고 그리기는 하지 않는다(GamesActivity 의 Board2048View).
 */
internal class Game2048 {
    var cells = IntArray(16)
    var score = 0
        private set
    var won = false
        private set
    var over = false
        private set

    private val history = ArrayDeque<Snapshot>()

    private data class Snapshot(val cells: IntArray, val score: Int, val won: Boolean, val over: Boolean)

    val canUndo: Boolean get() = history.isNotEmpty()

    fun newGame(rng: Random = Random.Default) {
        cells.fill(0)
        score = 0
        won = false
        over = false
        history.clear()
        spawn(rng)
        spawn(rng)
    }

    /** [dir] 0 위, 1 오른쪽, 2 아래, 3 왼쪽. 움직였으면 true. */
    fun move(dir: Int, rng: Random = Random.Default): Boolean {
        if (over) return false
        val before = snapshot()
        var moved = false
        var gained = 0
        var reached = false
        val vertical = dir == 0 || dir == 2
        val reverse = dir == 1 || dir == 2
        for (line in 0 until 4) {
            val values = (0 until 4).map { i ->
                val k = if (vertical) i * 4 + line else line * 4 + i
                cells[k]
            }.filter { it != 0 }.toMutableList()
            if (reverse) values.reverse()
            val merged = mutableListOf<Int>()
            var i = 0
            while (i < values.size) {
                if (i + 1 < values.size && values[i] == values[i + 1]) {
                    val v = values[i] + 1
                    merged += v
                    gained += 1 shl v
                    if (v >= 11) reached = true
                    i += 2
                } else {
                    merged += values[i]
                    i++
                }
            }
            if (reverse) merged.reverse()
            repeat(4) { i ->
                val v = merged.getOrElse(i) { 0 }
                val k = if (vertical) i * 4 + line else line * 4 + i
                if (cells[k] != v) moved = true
                cells[k] = v
            }
        }
        if (!moved) return false
        history.addLast(before)
        while (history.size > MAX_UNDO) history.removeFirst()
        score += gained
        if (reached) won = true
        spawn(rng)
        over = !canMove()
        return true
    }

    fun undo(): Boolean {
        val s = history.removeLastOrNull() ?: return false
        cells = s.cells.copyOf()
        score = s.score
        won = s.won
        over = s.over
        return true
    }

    fun spawn(rng: Random = Random.Default) {
        val empty = cells.indices.filter { cells[it] == 0 }
        if (empty.isEmpty()) return
        val at = empty[rng.nextInt(empty.size)]
        cells[at] = if (rng.nextInt(10) < 9) 1 else 2
    }

    fun canMove(): Boolean {
        if (cells.any { it == 0 }) return true
        for (r in 0 until 4) for (c in 0 until 4) {
            val v = cells[r * 4 + c]
            if (c < 3 && cells[r * 4 + c + 1] == v) return true
            if (r < 3 && cells[(r + 1) * 4 + c] == v) return true
        }
        return false
    }

    private fun snapshot() = Snapshot(cells.copyOf(), score, won, over)

    fun encode(): String = listOf(cells.joinToString(","), score, if (won) 1 else 0, if (over) 1 else 0).joinToString("|")

    companion object {
        private const val MAX_UNDO = 20

        fun decode(s: String): Game2048? = runCatching {
            val p = s.split('|')
            val c = p[0].split(',').map { it.toInt() }.toIntArray()
            require(c.size == 16)
            Game2048().apply {
                cells = c
                score = p[1].toInt()
                won = p[2] == "1"
                over = p[3] == "1"
            }
        }.getOrNull()
    }
}
