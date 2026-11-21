package com.woody.booklauncher.games

/** 노노그램 한 문제. [rows] 는 '#'(칠함)과 '.'(빈 칸) 10줄. */
internal class NonogramPuzzle(val name: String, rows: List<String>) {
    val size = rows.size
    val filled = BooleanArray(size * size) { i -> rows[i / size][i % size] == '#' }

    val rowClues: List<List<Int>> = (0 until size).map { r ->
        runs((0 until size).map { c -> filled[r * size + c] })
    }
    val colClues: List<List<Int>> = (0 until size).map { c ->
        runs((0 until size).map { r -> filled[r * size + c] })
    }

    private fun runs(line: List<Boolean>): List<Int> {
        val out = mutableListOf<Int>()
        var n = 0
        for (v in line) {
            if (v) n++ else if (n > 0) { out += n; n = 0 }
        }
        if (n > 0) out += n
        return out.ifEmpty { listOf(0) }
    }
}

internal object Nonograms {
    /** 그림 하나가 한 문제. 모두 10×10 이고 손으로 만든 것만 둔다. */
    val ALL: List<NonogramPuzzle> = listOf(
        NonogramPuzzle(
            "하트",
            listOf(
                "..##...##.",
                ".####.####",
                "##########",
                "##########",
                ".########.",
                "..######..",
                "...####...",
                "....##....",
                "..........",
                "..........",
            ),
        ),
        NonogramPuzzle(
            "별",
            listOf(
                "....##....",
                "...####...",
                ".########.",
                "##########",
                ".########.",
                "..######..",
                "..##..##..",
                ".##....##.",
                "..........",
                "..........",
            ),
        ),
        NonogramPuzzle(
            "집",
            listOf(
                "....##....",
                "...####...",
                "..######..",
                ".########.",
                "##########",
                "##......##",
                "##.##...##",
                "##.##...##",
                "##.##...##",
                "##########",
            ),
        ),
        NonogramPuzzle(
            "고양이",
            listOf(
                "##.....##.",
                "##.....##.",
                "##########",
                "##########",
                "##.####.##",
                "##.####.##",
                "##########",
                ".########.",
                "..######..",
                "...####...",
            ),
        ),
        NonogramPuzzle(
            "나무",
            listOf(
                "....##....",
                "...####...",
                "..######..",
                ".########.",
                "##########",
                "...####...",
                "...####...",
                "..######..",
                ".########.",
                "..........",
            ),
        ),
        NonogramPuzzle(
            "물고기",
            listOf(
                "..........",
                "..####....",
                ".######.#.",
                "##########",
                ".########.",
                "..######..",
                "...####...",
                "..........",
                "..........",
                "..........",
            ),
        ),
        NonogramPuzzle(
            "화살",
            listOf(
                "....##....",
                "...####...",
                "..######..",
                ".########.",
                "##########",
                "....##....",
                "....##....",
                "....##....",
                "....##....",
                "....##....",
            ),
        ),
        NonogramPuzzle(
            "컵",
            listOf(
                "..........",
                "##......##",
                "##......##",
                "##......##",
                "##########",
                ".########.",
                "..######..",
                "..........",
                "..........",
                "..........",
            ),
        ),
        NonogramPuzzle(
            "미소",
            listOf(
                "..######..",
                ".########.",
                "##.####.##",
                "##.####.##",
                "##########",
                "##......##",
                ".#.#..#.#.",
                "..######..",
                "..........",
                "..........",
            ),
        ),
        NonogramPuzzle(
            "로켓",
            listOf(
                "....##....",
                "...####...",
                "..######..",
                "..######..",
                "..######..",
                ".########.",
                "..######..",
                ".########.",
                "..##..##..",
                ".##....##.",
            ),
        ),
    )
}

/**
 * 노노그램 판. 칸 표시는 0 모름, 1 칠함, 2 X. 가로·세로 힌트는 문제에서 계산한다.
 * 칠한 칸이 틀리면 [mistakes] 가 는다(바로 되돌릴 수 있다).
 */
internal class NonogramGame(var puzzleIndex: Int = 0) {
    var marks = IntArray(Nonograms.ALL[puzzleIndex.coerceIn(Nonograms.ALL.indices)].size * Nonograms.ALL[puzzleIndex.coerceIn(Nonograms.ALL.indices)].size)
    var mistakes = 0
        private set
    var startMs = 0L
        private set
    var elapsedMs = 0L
        private set

    private val history = ArrayDeque<IntArray>()

    val puzzle: NonogramPuzzle get() = Nonograms.ALL[puzzleIndex.coerceIn(Nonograms.ALL.indices)]
    val size: Int get() = puzzle.size
    val solved: Boolean get() = marks.indices.all { i -> marks[i] != 0 && (marks[i] == 1) == puzzle.filled[i] }

    fun newGame(index: Int) {
        puzzleIndex = index.coerceIn(Nonograms.ALL.indices)
        marks = IntArray(size * size)
        mistakes = 0
        startMs = 0L
        elapsedMs = 0L
        history.clear()
    }

    fun set(index: Int, mark: Int): Boolean {
        if (index !in marks.indices || mark !in 0..2) return false
        if (marks[index] == mark) return false
        if (startMs == 0L) startMs = System.currentTimeMillis()
        push()
        marks[index] = mark
        if (mark == 1 && !puzzle.filled[index]) mistakes++
        if (mark == 2 && puzzle.filled[index]) mistakes++
        if (solved) elapsedMs = elapsed()
        return true
    }

    /** 지금 칸을 정답으로 바꾼다(힌트). */
    fun hint(): Int {
        val i = marks.indices.firstOrNull { i ->
            (puzzle.filled[i] && marks[i] != 1) || (!puzzle.filled[i] && marks[i] != 2)
        } ?: return -1
        if (startMs == 0L) startMs = System.currentTimeMillis()
        push()
        marks[i] = if (puzzle.filled[i]) 1 else 2
        if (solved) elapsedMs = elapsed()
        return i
    }

    fun undo(): Boolean {
        val s = history.removeLastOrNull() ?: return false
        marks = s.copyOf()
        mistakes = marks.indices.count { i -> (marks[i] == 1 && !puzzle.filled[i]) || (marks[i] == 2 && puzzle.filled[i]) }
        elapsedMs = 0L
        return true
    }

    fun canUndo(): Boolean = history.isNotEmpty()

    fun elapsed(now: Long = System.currentTimeMillis()): Long =
        if (elapsedMs > 0) elapsedMs else if (startMs > 0) (now - startMs).coerceAtLeast(0) else 0L

    private fun push() {
        history.addLast(marks.copyOf())
        while (history.size > 60) history.removeFirst()
    }

    fun encode(): String = listOf(
        puzzleIndex, marks.joinToString(""), mistakes, startMs, elapsedMs,
    ).joinToString("|")

    companion object {
        fun decode(s: String): NonogramGame? = runCatching {
            val p = s.split('|')
            val g = NonogramGame(p[0].toInt())
            g.marks = IntArray(p[1].length) { p[1][it] - '0' }
            g.mistakes = p[2].toInt()
            g.startMs = p[3].toLong()
            g.elapsedMs = p[4].toLong()
            require(g.marks.size == g.size * g.size)
            g
        }.getOrNull()
    }
}
