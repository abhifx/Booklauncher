package com.woody.booklauncher.games

import kotlin.random.Random

/**
 * 9×9 스도쿠. 완성판을 만든 뒤 답이 하나뿐인 칸만 지워 문제를 만든다.
 * 메모(칸마다 1~9 후보), 되돌리기, 힌트, 충돌 표시를 갖는다.
 */
internal class SudokuGame {
    var cells = IntArray(81)
    var solution = IntArray(81)
    var given = BooleanArray(81)
    /** 칸마다 후보 숫자 비트(1 shl (n-1)) */
    var notes = IntArray(81)
    var difficulty = 1
    var selected = -1
    var startMs = 0L
        private set
    var elapsedMs = 0L
        private set
    var done = false
        private set
    var mistakes = 0
        private set

    private val history = ArrayDeque<Snapshot>()

    private data class Snapshot(
        val cells: IntArray,
        val notes: IntArray,
        val selected: Int,
        val mistakes: Int,
        val done: Boolean,
        val startMs: Long,
        val elapsedMs: Long,
    )

    fun newGame(level: Int, rng: Random = Random.Default) {
        difficulty = level
        solution = completeGrid(rng)
        cells = solution.copyOf()
        given.fill(true)
        notes.fill(0)
        selected = -1
        startMs = 0L
        elapsedMs = 0L
        done = false
        mistakes = 0
        history.clear()
        val target = when (level) {
            0 -> 40
            2 -> 54
            else -> 48
        }
        var removed = 0
        for (i in (0 until 81).shuffled(rng)) {
            if (removed >= target) break
            val save = cells[i]
            cells[i] = 0
            if (countSolutions(cells, 2) == 1) {
                given[i] = false
                removed++
            } else {
                cells[i] = save
            }
        }
    }

    fun elapsed(now: Long = System.currentTimeMillis()): Long =
        if (elapsedMs > 0) elapsedMs else if (startMs > 0) (now - startMs).coerceAtLeast(0) else 0L

    /** [notesMode] 면 후보를 넣고 뺀다. 아니면 숫자를 넣는다. 넣었으면 true. */
    fun enter(value: Int, notesMode: Boolean): Boolean {
        val i = selected
        if (i !in 0 until 81 || done || given[i]) return false
        if (notesMode && value != 0) {
            val bit = 1 shl (value - 1)
            push()
            notes[i] = notes[i] xor bit
            start()
            return true
        }
        if (value != 0 && !canPlace(i, value)) {
            mistakes++
            return false
        }
        push()
        start()
        cells[i] = value
        notes[i] = 0
        if (value != 0) clearPeerNotes(i, value)
        checkDone()
        return true
    }

    /** 지금 고른 칸(없으면 첫 빈 칸)에 답을 넣는다. */
    fun hint(): Boolean {
        if (done) return false
        val i = selected.takeIf { it in 0 until 81 && cells[it] == 0 }
            ?: cells.indices.firstOrNull { cells[it] == 0 } ?: return false
        push()
        start()
        cells[i] = solution[i]
        notes[i] = 0
        selected = i
        clearPeerNotes(i, solution[i])
        checkDone()
        return true
    }

    fun erase(): Boolean {
        val i = selected
        if (i !in 0 until 81 || done || given[i]) return false
        if (cells[i] == 0 && notes[i] == 0) return false
        push()
        cells[i] = 0
        notes[i] = 0
        return true
    }

    fun undo(): Boolean {
        val s = history.removeLastOrNull() ?: return false
        cells = s.cells.copyOf()
        notes = s.notes.copyOf()
        selected = s.selected
        mistakes = s.mistakes
        done = s.done
        startMs = s.startMs
        elapsedMs = s.elapsedMs
        return true
    }

    fun canUndo(): Boolean = history.isNotEmpty()

    fun canPlace(index: Int, value: Int): Boolean {
        val r = index / 9
        val c = index % 9
        for (i in 0 until 9) {
            if (i != c && cells[r * 9 + i] == value) return false
            if (i != r && cells[i * 9 + c] == value) return false
            val br = (r / 3) * 3 + i / 3
            val bc = (c / 3) * 3 + i % 3
            if ((br != r || bc != c) && cells[br * 9 + bc] == value) return false
        }
        return true
    }

    /** 줄·칸·상자에 같은 숫자가 겹친 칸. */
    fun conflicts(): BooleanArray {
        val out = BooleanArray(81)
        for (i in 0 until 81) {
            val v = cells[i]
            if (v == 0) continue
            val r = i / 9
            val c = i % 9
            for (j in 0 until 81) {
                if (j == i || cells[j] != v) continue
                val r2 = j / 9
                val c2 = j % 9
                if (r2 == r || c2 == c || (r2 / 3 == r / 3 && c2 / 3 == c / 3)) {
                    out[i] = true
                    out[j] = true
                }
            }
        }
        return out
    }

    fun isComplete(): Boolean = cells.indices.all { cells[it] == solution[it] }

    private fun start() {
        if (startMs == 0L) startMs = System.currentTimeMillis()
    }

    private fun checkDone() {
        if (!isComplete()) return
        done = true
        elapsedMs = elapsed()
    }

    /** 같은 줄·칸·상자의 메모에서 [value] 를 지운다. */
    private fun clearPeerNotes(index: Int, value: Int) {
        val bit = 1 shl (value - 1)
        val r = index / 9
        val c = index % 9
        for (i in 0 until 81) {
            val r2 = i / 9
            val c2 = i % 9
            if (r2 == r || c2 == c || (r2 / 3 == r / 3 && c2 / 3 == c / 3)) notes[i] = notes[i] and bit.inv()
        }
    }

    private fun push() {
        history.addLast(Snapshot(cells.copyOf(), notes.copyOf(), selected, mistakes, done, startMs, elapsedMs))
        while (history.size > 50) history.removeFirst()
    }

    private fun completeGrid(rng: Random): IntArray {
        val rows = (0 until 9).shuffled(rng)
        val cols = (0 until 9).shuffled(rng)
        val nums = (1..9).shuffled(rng)
        return IntArray(81) { i ->
            val r = rows[i / 9]
            val c = cols[i % 9]
            nums[(r * 3 + r / 3 + c) % 9]
        }
    }

    /** 답이 몇 가지인지 [limit] 까지만 센다(문제가 하나뿐인지 볼 때 2). */
    private fun countSolutions(start: IntArray, limit: Int): Int {
        val grid = start.copyOf()
        val rowMask = IntArray(9)
        val colMask = IntArray(9)
        val boxMask = IntArray(9)
        for (i in 0 until 81) {
            val v = grid[i]
            if (v == 0) continue
            val bit = 1 shl (v - 1)
            val r = i / 9
            val c = i % 9
            val b = (r / 3) * 3 + c / 3
            rowMask[r] = rowMask[r] or bit
            colMask[c] = colMask[c] or bit
            boxMask[b] = boxMask[b] or bit
        }
        var found = 0
        fun dfs() {
            if (found >= limit) return
            var best = -1
            var bestMask = 0
            var bestCount = 10
            for (i in 0 until 81) {
                if (grid[i] != 0) continue
                val r = i / 9
                val c = i % 9
                val b = (r / 3) * 3 + c / 3
                val free = FULL and (rowMask[r] or colMask[c] or boxMask[b]).inv()
                val n = Integer.bitCount(free)
                if (n == 0) return
                if (n < bestCount) {
                    bestCount = n
                    best = i
                    bestMask = free
                    if (n == 1) break
                }
            }
            if (best < 0) {
                found++
                return
            }
            val r = best / 9
            val c = best % 9
            val b = (r / 3) * 3 + c / 3
            for (v in 1..9) {
                val bit = 1 shl (v - 1)
                if (bestMask and bit == 0) continue
                grid[best] = v
                rowMask[r] = rowMask[r] or bit
                colMask[c] = colMask[c] or bit
                boxMask[b] = boxMask[b] or bit
                dfs()
                grid[best] = 0
                rowMask[r] = rowMask[r] and bit.inv()
                colMask[c] = colMask[c] and bit.inv()
                boxMask[b] = boxMask[b] and bit.inv()
                if (found >= limit) return
            }
        }
        dfs()
        return found
    }

    fun encode(): String = listOf(
        cells.joinToString(","), solution.joinToString(","),
        given.joinToString("") { if (it) "1" else "0" },
        notes.joinToString(","), difficulty, selected, startMs, elapsedMs,
        if (done) 1 else 0, mistakes,
    ).joinToString("|")

    companion object {
        private const val FULL = 0x1FF

        fun decode(s: String): SudokuGame? = runCatching {
            val p = s.split('|')
            SudokuGame().apply {
                cells = ints(p[0])
                solution = ints(p[1])
                given = BooleanArray(81) { p[2][it] == '1' }
                notes = ints(p[3])
                difficulty = p[4].toInt()
                selected = p[5].toInt()
                startMs = p[6].toLong()
                elapsedMs = p[7].toLong()
                done = p[8] == "1"
                mistakes = p[9].toInt()
                require(cells.size == 81 && solution.size == 81 && notes.size == 81)
            }
        }.getOrNull()

        private fun ints(s: String) = s.split(',').map { it.toInt() }.toIntArray()
    }
}
