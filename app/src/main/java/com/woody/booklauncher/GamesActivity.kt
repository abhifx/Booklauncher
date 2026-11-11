package com.woody.booklauncher

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 전자잉크에 맞춘 작은 게임(설정 없이 바로 하는 2048·지뢰찾기·스도쿠).
 * 애니메이션 없이 바뀐 판만 다시 그리고, 기록(최고 점수·최단 시간)은 `games` 설정에 남긴다.
 */
class GamesActivity : EinkActivity() {
    private enum class Page { HUB, G2048, MINES, SUDOKU }

    private lateinit var body: LinearLayout
    private lateinit var title: TextView
    private var page = Page.HUB

    private var game2048: Game2048? = null
    private var board2048: Board2048View? = null
    private var status2048: TextView? = null

    private var mines: MinesGame? = null
    private var minesView: MinesView? = null
    private var statusMines: TextView? = null

    private var sudoku: SudokuGame? = null
    private var sudokuView: SudokuView? = null
    private var statusSudoku: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restore(savedInstanceState)
        val root = vbox()
        root.addView(titleRow(getString(R.string.games)) { if (!back()) finish() }.apply {
            title = text("", 19f, color = Ui.GRAY)
            addView(title, lp(WRAP, WRAP).apply { marginStart = dp(14); topMargin = dp(6) })
        })
        root.addView(hline(2))
        body = vbox()
        root.addView(body, lp(MATCH, 0, 1f))
        root.addView(hline(1, inset = false))
        setContentView(root)
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("page", page.ordinal)
        game2048?.let {
            outState.putIntArray("b_cells", it.cells)
            outState.putInt("b_score", it.score)
            outState.putBoolean("b_won", it.won)
            outState.putBoolean("b_over", it.over)
        }
        mines?.let {
            outState.putBooleanArray("m_mine", it.mine)
            outState.putBooleanArray("m_open", it.revealed)
            outState.putBooleanArray("m_flag", it.flagged)
            outState.putBoolean("m_started", it.started)
            outState.putBoolean("m_lost", it.lost)
            outState.putBoolean("m_won", it.won)
            outState.putInt("m_flags", it.flags)
            outState.putInt("m_boom", it.exploded)
            outState.putLong("m_start", it.startMs)
            outState.putLong("m_elapsed", it.elapsedMs)
        }
        sudoku?.let {
            outState.putIntArray("s_cells", it.cells)
            outState.putIntArray("s_solution", it.solution)
            outState.putBooleanArray("s_given", it.given)
            outState.putInt("s_difficulty", it.difficulty)
            outState.putInt("s_selected", it.selected)
            outState.putLong("s_start", it.startMs)
            outState.putLong("s_elapsed", it.elapsedMs)
            outState.putBoolean("s_done", it.done)
        }
    }

    private fun restore(b: Bundle?) {
        if (b == null) return
        page = Page.entries.getOrNull(b.getInt("page", 0)) ?: Page.HUB
        b.getIntArray("b_cells")?.let {
            game2048 = Game2048().apply {
                cells = it.copyOf()
                score = b.getInt("b_score")
                won = b.getBoolean("b_won")
                over = b.getBoolean("b_over")
            }
        }
        b.getBooleanArray("m_mine")?.let { mine ->
            b.getBooleanArray("m_open")?.let { open ->
                b.getBooleanArray("m_flag")?.let { flag ->
                    mines = MinesGame().apply {
                        this.mine = mine.copyOf()
                        revealed = open.copyOf()
                        flagged = flag.copyOf()
                        started = b.getBoolean("m_started")
                        lost = b.getBoolean("m_lost")
                        won = b.getBoolean("m_won")
                        flags = b.getInt("m_flags")
                        exploded = b.getInt("m_boom", -1)
                        startMs = b.getLong("m_start")
                        elapsedMs = b.getLong("m_elapsed")
                    }
                }
            }
        }
        b.getIntArray("s_cells")?.let { cells ->
            b.getIntArray("s_solution")?.let { solution ->
                b.getBooleanArray("s_given")?.let { given ->
                    sudoku = SudokuGame().apply {
                        this.cells = cells.copyOf()
                        this.solution = solution.copyOf()
                        this.given = given.copyOf()
                        difficulty = b.getInt("s_difficulty", 1)
                        selected = b.getInt("s_selected", -1)
                        startMs = b.getLong("s_start")
                        elapsedMs = b.getLong("s_elapsed")
                        done = b.getBoolean("s_done")
                    }
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!back()) super.onBackPressed()
    }

    private fun back(): Boolean {
        if (page == Page.HUB) return false
        page = Page.HUB
        render()
        return true
    }

    /** 게임 판에서는 화면 밀기로 쪽을 넘기지 않는다(2048 은 그 밀기로 타일을 움직인다). */
    override fun onSwipe(toLeft: Boolean): Boolean = false

    private fun render() {
        body.removeAllViews()
        title.text = when (page) {
            Page.HUB -> ""
            Page.G2048 -> getString(R.string.game_2048)
            Page.MINES -> getString(R.string.game_minesweeper)
            Page.SUDOKU -> getString(R.string.game_sudoku)
        }
        when (page) {
            Page.HUB -> hub()
            Page.G2048 -> game2048()
            Page.MINES -> minesweeper()
            Page.SUDOKU -> sudoku()
        }
    }

    private fun hub() {
        listOf(
            Triple(R.string.game_2048, R.string.game_2048_desc, Page.G2048),
            Triple(R.string.game_minesweeper, R.string.game_minesweeper_desc, Page.MINES),
            Triple(R.string.game_sudoku, R.string.game_sudoku_desc, Page.SUDOKU),
        ).forEach { (label, desc, to) ->
            body.addView(hline(1, Ui.DIVIDER), lp(MATCH, dp(1)))
            body.addView(hbox().apply {
                setPadding(dp(Ui.MARGIN), 0, dp(Ui.MARGIN), 0)
                addView(vbox().apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(text(getString(label), 23f, Ui.medium))
                    addView(text(getString(desc), 13f, color = Ui.GRAY), lp(MATCH, WRAP).apply { topMargin = dp(2) })
                }, lp(0, WRAP, 1f))
                addView(text("›", 24f, color = Ui.LIGHT_GRAY).apply { gravity = Gravity.CENTER_VERTICAL })
                setOnClickListener {
                    page = to
                    when (to) {
                        Page.G2048 -> if (game2048 == null) new2048()
                        Page.MINES -> if (mines == null) newMines()
                        Page.SUDOKU -> if (sudoku == null) newSudoku(1)
                        else -> Unit
                    }
                    render()
                }
            }, lp(MATCH, rowDp(72)))
        }
    }

    // ---- 2048 ---------------------------------------------------------------

    private fun new2048() {
        game2048 = Game2048().apply { newGame() }
    }

    private fun game2048() {
        val g = game2048 ?: Game2048().apply { newGame() }.also { game2048 = it }
        body.setPadding(dp(Ui.MARGIN), dp(8), dp(Ui.MARGIN), dp(8))
        status2048 = text("", 15f, Ui.medium).apply { gravity = Gravity.CENTER }
        body.addView(status2048, lp(MATCH, WRAP).apply { bottomMargin = dp(6) })
        board2048 = Board2048View(this, g) { move2048(it) }
        body.addView(board2048, lp(MATCH, 0, 1f))
        body.addView(hbox().apply {
            listOf("←" to 3, "↑" to 0, "↓" to 2, "→" to 1).forEachIndexed { i, (label, dir) ->
                addView(GameKey(this@GamesActivity, label, false) { move2048(dir) }, lp(0, dp(52), 1f).apply {
                    if (i > 0) marginStart = dp(6)
                })
            }
        }, lp(MATCH, WRAP).apply { topMargin = dp(8) })
        body.addView(GameKey(this@GamesActivity, getString(R.string.game_new), true) {
            new2048()
            render()
        }, lp(MATCH, dp(52)).apply { topMargin = dp(8) })
        update2048()
    }

    private fun move2048(dir: Int) {
        val g = game2048 ?: return
        if (g.over) return
        if (g.move(dir)) {
            g.spawn()
            update2048()
            board2048?.invalidate()
        }
    }

    private fun update2048() {
        val g = game2048 ?: return
        val best = GameStore.best2048(this)
        if (g.score > best) GameStore.setBest2048(this, g.score)
        val tail = when {
            g.won -> "  ·  ${getString(R.string.game_won)}"
            g.over -> "  ·  ${getString(R.string.game_lost)}"
            else -> ""
        }
        status2048?.text = "${getString(R.string.game_score)} ${g.score}  ·  " +
            "${getString(R.string.game_best)} ${maxOf(best, g.score)}$tail"
    }

    // ---- minesweeper --------------------------------------------------------

    private fun newMines() {
        mines = MinesGame().apply { newGame() }
    }

    private fun minesweeper() {
        val g = mines ?: MinesGame().apply { newGame() }.also { mines = it }
        body.setPadding(dp(Ui.MARGIN), dp(8), dp(Ui.MARGIN), dp(8))
        statusMines = text("", 15f, Ui.medium).apply { gravity = Gravity.CENTER }
        body.addView(statusMines, lp(MATCH, WRAP).apply { bottomMargin = dp(6) })
        minesView = MinesView(this, g, onReveal = { revealMines(it) }, onFlag = { flagMines(it) })
        body.addView(minesView, lp(MATCH, 0, 1f))
        body.addView(text(getString(R.string.game_mines_help), 12.5f, color = Ui.GRAY).apply {
            gravity = Gravity.CENTER
        }, lp(MATCH, WRAP).apply { topMargin = dp(6) })
        body.addView(GameKey(this@GamesActivity, getString(R.string.game_new), true) {
            newMines()
            render()
        }, lp(MATCH, dp(52)).apply { topMargin = dp(8) })
        updateMines()
    }

    private fun revealMines(index: Int) {
        val g = mines ?: return
        if (g.reveal(index)) {
            if (g.won || g.lost) {
                val best = GameStore.bestMines(this)
                if (best == 0L || (g.elapsedMs in 1 until best)) GameStore.setBestMines(this, g.elapsedMs)
            }
            updateMines()
            minesView?.invalidate()
        }
    }

    private fun flagMines(index: Int) {
        val g = mines ?: return
        if (g.toggleFlag(index)) {
            updateMines()
            minesView?.invalidate()
        }
    }

    private fun updateMines() {
        val g = mines ?: return
        val elapsed = g.elapsed()
        val best = GameStore.bestMines(this)
        val tag = when {
            g.won -> "  ·  ${getString(R.string.game_mines_won)}"
            g.lost -> "  ·  ${getString(R.string.game_mines_lost)}"
            else -> ""
        }
        statusMines?.text = getString(R.string.game_mines_left, maxOf(0, g.mineCount - g.flags)) +
            "  ·  ${getString(R.string.game_time)} ${timeText(elapsed)}" +
            (if (best > 0) "  ·  ${getString(R.string.game_best)} ${timeText(best)}" else "") + tag
    }

    // ---- sudoku -------------------------------------------------------------

    private fun newSudoku(difficulty: Int) {
        sudoku = SudokuGame().apply { newGame(difficulty) }
    }

    private fun sudoku() {
        val g = sudoku ?: SudokuGame().apply { newGame(1) }.also { sudoku = it }
        body.setPadding(dp(Ui.MARGIN), dp(6), dp(Ui.MARGIN), dp(8))
        statusSudoku = text("", 15f, Ui.medium).apply { gravity = Gravity.CENTER }
        body.addView(statusSudoku, lp(MATCH, WRAP).apply { bottomMargin = dp(4) })
        sudokuView = SudokuView(this, g) { index ->
            g.selected = index
            sudokuView?.invalidate()
        }
        body.addView(sudokuView, lp(MATCH, 0, 1f))
        // 숫자판: 1~9, 지우기. 전자잉크라 누를 때마다 한 번만 다시 그린다.
        listOf(1..5, 6..9).forEachIndexed { row, range ->
            body.addView(hbox().apply {
                range.forEach { n ->
                    addView(GameKey(this@GamesActivity, n.toString(), false) { enterSudoku(n) }, lp(0, dp(46), 1f).apply {
                        if (n != range.first) marginStart = dp(5)
                    })
                }
                repeat(5 - range.count()) { addView(View(this@GamesActivity), lp(0, 1, 1f)) }
            }, lp(MATCH, WRAP).apply { if (row > 0) topMargin = dp(5) })
        }
        body.addView(hbox().apply {
            listOf(0 to getString(R.string.game_easy), 1 to getString(R.string.game_normal), 2 to getString(R.string.game_hard)).forEachIndexed { i, (level, label) ->
                addView(GameKey(this@GamesActivity, label, level == g.difficulty) {
                    newSudoku(level)
                    render()
                }, lp(0, dp(44), 1f).apply { if (i > 0) marginStart = dp(5) })
            }
            addView(GameKey(this@GamesActivity, getString(R.string.game_sudoku_erase), false) {
                enterSudoku(0)
            }, lp(0, dp(44), 1f).apply { marginStart = dp(5) })
        }, lp(MATCH, WRAP).apply { topMargin = dp(5) })
        body.addView(text(getString(R.string.game_sudoku_help), 12.5f, color = Ui.GRAY).apply {
            gravity = Gravity.CENTER
        }, lp(MATCH, WRAP).apply { topMargin = dp(4) })
        updateSudoku()
    }

    private fun enterSudoku(value: Int) {
        val g = sudoku ?: return
        if (g.done || g.selected < 0 || g.given.getOrElse(g.selected) { false }) return
        if (value != 0 && !g.canPlace(g.selected, value)) {
            statusSudoku?.text = getString(R.string.game_sudoku_wrong)
            return
        }
        if (g.startMs == 0L) g.startMs = System.currentTimeMillis()
        g.cells[g.selected] = value
        if (g.isComplete()) {
            g.done = true
            g.elapsedMs = g.elapsed()
            val best = GameStore.bestSudoku(this, g.difficulty)
            if (best == 0L || g.elapsedMs < best) GameStore.setBestSudoku(this, g.difficulty, g.elapsedMs)
        }
        sudokuView?.invalidate()
        updateSudoku()
    }

    private fun updateSudoku() {
        val g = sudoku ?: return
        val level = when (g.difficulty) {
            0 -> getString(R.string.game_easy)
            2 -> getString(R.string.game_hard)
            else -> getString(R.string.game_normal)
        }
        val best = GameStore.bestSudoku(this, g.difficulty)
        val tag = if (g.done) "  ·  ${getString(R.string.game_completed)}" else ""
        statusSudoku?.text = "$level  ·  ${getString(R.string.game_time)} ${timeText(g.elapsed())}" +
            (if (best > 0) "  ·  ${getString(R.string.game_best)} ${timeText(best)}" else "") + tag
    }

    private fun timeText(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val m = total / 60
        val s = total % 60
        return "%d:%02d".format(m, s)
    }

    companion object {
        fun intent(context: Context) = Intent(context, GamesActivity::class.java)
    }
}

/** 2048 판: 4×4, 빈 칸은 회색, 숫자가 커질수록 어두워진다. 밀기로 움직인다. */
private class Game2048 {
    var cells = IntArray(16)
    var score = 0
    var won = false
    var over = false

    fun newGame() {
        cells.fill(0)
        score = 0
        won = false
        over = false
        spawn()
        spawn()
    }

    /** [dir] 0 위, 1 오른쪽, 2 아래, 3 왼쪽. 움직였으면 true */
    fun move(dir: Int): Boolean {
        if (over) return false
        var moved = false
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
                    score += 1 shl v
                    if (v >= 11) won = true
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
        if (moved) over = !canMove()
        return moved
    }

    fun spawn() {
        val empty = cells.indices.filter { cells[it] == 0 }
        if (empty.isEmpty()) return
        val at = empty.random()
        cells[at] = if ((0..9).random() < 9) 1 else 2
    }

    private fun canMove(): Boolean {
        if (cells.any { it == 0 }) return true
        for (r in 0 until 4) for (c in 0 until 4) {
            val v = cells[r * 4 + c]
            if (c < 3 && cells[r * 4 + c + 1] == v) return true
            if (r < 3 && cells[(r + 1) * 4 + c] == v) return true
        }
        return false
    }
}

/** 9×9 지뢰찾기. 첫 칸을 열 때 그 칸과 이웃을 피해 지뢰를 놓는다. */
private class MinesGame(val size: Int = 9, val mineCount: Int = 10) {
    var mine = BooleanArray(size * size)
    var revealed = BooleanArray(size * size)
    var flagged = BooleanArray(size * size)
    var started = false
    var lost = false
    var won = false
    var flags = 0
    var exploded = -1
    var startMs = 0L
    var elapsedMs = 0L

    fun newGame() {
        mine.fill(false)
        revealed.fill(false)
        flagged.fill(false)
        started = false
        lost = false
        won = false
        flags = 0
        exploded = -1
        startMs = 0L
        elapsedMs = 0L
    }

    fun elapsed(): Long = if (elapsedMs > 0) elapsedMs else if (startMs > 0) System.currentTimeMillis() - startMs else 0L

    fun reveal(index: Int): Boolean {
        if (index !in mine.indices || lost || won || revealed[index] || flagged[index]) return false
        if (!started) {
            started = true
            startMs = System.currentTimeMillis()
            placeMines(index)
        }
        if (mine[index]) {
            lost = true
            exploded = index
            elapsedMs = elapsed()
            for (i in mine.indices) if (mine[i]) revealed[i] = true
            return true
        }
        open(index)
        if (revealed.count { !it } == mineCount) {
            won = true
            elapsedMs = elapsed()
            for (i in mine.indices) if (mine[i]) flagged[i] = true
            flags = mineCount
        }
        return true
    }

    fun toggleFlag(index: Int): Boolean {
        if (index !in mine.indices || lost || won || revealed[index]) return false
        flagged[index] = !flagged[index]
        flags += if (flagged[index]) 1 else -1
        return true
    }

    private fun placeMines(avoid: Int) {
        val safe = mutableSetOf(avoid)
        for (j in neighbours(avoid)) safe += j
        val pool = (0 until size * size).filter { it !in safe }.toMutableList()
        if (pool.size < mineCount) pool.addAll((0 until size * size).filter { it != avoid && it !in pool })
        pool.shuffle()
        pool.take(mineCount).forEach { mine[it] = true }
    }

    private fun open(index: Int) {
        if (index !in mine.indices || revealed[index] || flagged[index] || mine[index]) return
        revealed[index] = true
        if (count(index) == 0) neighbours(index).forEach { open(it) }
    }

    private fun neighbours(index: Int): List<Int> {
        val r = index / size
        val c = index % size
        val out = mutableListOf<Int>()
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val nr = r + dr
            val nc = c + dc
            if (nr in 0 until size && nc in 0 until size) out += nr * size + nc
        }
        return out
    }

    fun count(index: Int): Int = neighbours(index).count { mine[it] }
}

/** 9×9 스도쿠: 완성판을 만든 뒤 답이 하나뿐인 칸만 지워 문제를 만든다. */
private class SudokuGame {
    var cells = IntArray(81)
    var solution = IntArray(81)
    var given = BooleanArray(81)
    var difficulty = 1
    var selected = -1
    var startMs = 0L
    var elapsedMs = 0L
    var done = false

    fun newGame(level: Int) {
        difficulty = level
        solution = completeGrid()
        cells = solution.copyOf()
        given.fill(true)
        selected = -1
        startMs = 0L
        elapsedMs = 0L
        done = false
        val target = when (level) {
            0 -> 40
            2 -> 54
            else -> 48
        }
        var removed = 0
        for (i in (0 until 81).shuffled()) {
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

    fun elapsed(): Long = if (elapsedMs > 0) elapsedMs else if (startMs > 0) System.currentTimeMillis() - startMs else 0L

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

    fun isComplete(): Boolean = cells.none { it == 0 }

    private fun completeGrid(): IntArray {
        val rows = (0 until 9).shuffled()
        val cols = (0 until 9).shuffled()
        val nums = (1..9).shuffled()
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

    companion object {
        private const val FULL = 0x1FF
    }
}

/** 2048 판 그리기. 밀기(또는 아래 화살표)로 [onMove] 를 부른다. */
private class Board2048View(
    context: Context,
    private val game: Game2048,
    private val onMove: (Int) -> Unit,
) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Ui.heavy
    }
    private val rect = RectF()
    private var downX = 0f
    private var downY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                val min = context.dp(28)
                if (maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) > min) {
                    val dir = if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                        if (dx > 0) 1 else 3
                    } else {
                        if (dy > 0) 2 else 0
                    }
                    onMove(dir)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        val side = minOf(width, height).toFloat()
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

/** 지뢰찾기 판 그리기. 눌러 열고, 길게 눌러 깃발을 꽂는다. */
private class MinesView(
    context: Context,
    private val game: MinesGame,
    private val onReveal: (Int) -> Unit,
    private val onFlag: (Int) -> Unit,
) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpf(1)
        color = Ui.DIVIDER
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Ui.bold
    }
    private val handler = Handler(Looper.getMainLooper())
    private var pressed = -1
    private var longPressed = false
    private val longPress = Runnable {
        if (pressed >= 0) {
            longPressed = true
            onFlag(pressed)
        }
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
                pressed = index
                longPressed = false
                if (index >= 0) handler.postDelayed(longPress, 450)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (index != pressed) {
                    handler.removeCallbacks(longPress)
                    pressed = -1
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPress)
                if (!longPressed && pressed >= 0) onReveal(pressed)
                pressed = -1
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPress)
                pressed = -1
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun indexAt(x: Float, y: Float): Int {
        val side = minOf(width, height).toFloat()
        val left = (width - side) / 2
        val top = (height - side) / 2
        if (x < left || y < top || x >= left + side || y >= top + side) return -1
        val cell = side / game.size
        val c = ((x - left) / cell).toInt().coerceIn(0, game.size - 1)
        val r = ((y - top) / cell).toInt().coerceIn(0, game.size - 1)
        return r * game.size + c
    }

    override fun onDraw(canvas: Canvas) {
        val side = minOf(width, height).toFloat()
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
                    // 깃발: 작은 세모와 깃대
                    val pole = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = context.dpf(1.4f)
                        color = Ui.BLACK
                    }
                    canvas.drawLine(cx, cy - cell * 0.22f, cx, cy + cell * 0.22f, pole)
                    val flag = android.graphics.Path().apply {
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

/** 스도쿠 판 그리기. 칸을 누르면 [onSelect] 로 알린다. */
private class SudokuView(
    context: Context,
    private val game: SudokuGame,
    private val onSelect: (Int) -> Unit,
) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpf(0.8f)
        color = Ui.DIVIDER
    }
    private val thick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpf(2)
        color = Ui.BLACK
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP || game.done) return true
        val side = minOf(width, height).toFloat()
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
        val side = minOf(width, height).toFloat()
        val left = (width - side) / 2
        val top = (height - side) / 2
        val cell = side / 9
        text.textSize = cell * 0.58f
        for (i in 0 until 81) {
            val r = i / 9
            val c = i % 9
            val x = left + c * cell
            val y = top + r * cell
            if (i == game.selected) {
                fill.color = 0xFFE0E0E0.toInt()
                rect.set(x, y, x + cell, y + cell)
                canvas.drawRect(rect, fill)
            }
            val v = game.cells[i]
            if (v > 0) {
                text.typeface = if (game.given[i]) Ui.bold else Ui.regular
                text.color = Ui.BLACK
                val ty = y + cell / 2f - (text.descent() + text.ascent()) / 2f
                canvas.drawText(v.toString(), x + cell / 2f, ty, text)
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

/** 게임 단추: 흰 바탕에 얇은 테두리, 강조는 검은 바탕에 흰 글자. */
private class GameKey(context: Context, private val label: String, private val primary: Boolean, onClick: () -> Unit) : View(context) {
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpf(1.2f)
        color = Ui.BLACK
    }
    private val baseSize = context.spf(22f)
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
        // 긴 이름(영어 Normal·Erase)이 단추 밖으로 나가지 않게 폭에 맞춰 줄인다.
        text.textSize = baseSize
        val avail = width - context.dpf(10)
        val measured = text.measureText(label)
        if (measured > avail && measured > 0) text.textSize = (baseSize * avail / measured).coerceAtLeast(context.dpf(10))
        val y = height / 2f - (text.descent() + text.ascent()) / 2f
        canvas.drawText(label, width / 2f, y, text)
    }
}

/** 게임 기록(최고 점수·최단 시간)은 앱의 `games` 설정에 남긴다. */
private object GameStore {
    private const val PREFS = "games"

    fun best2048(context: Context): Int = prefs(context).getInt("best_2048", 0)
    fun setBest2048(context: Context, score: Int) = prefs(context).edit().putInt("best_2048", score).apply()

    fun bestMines(context: Context): Long = prefs(context).getLong("best_mines", 0L)
    fun setBestMines(context: Context, ms: Long) = prefs(context).edit().putLong("best_mines", ms).apply()

    fun bestSudoku(context: Context, difficulty: Int): Long =
        prefs(context).getLong("best_sudoku_$difficulty", 0L)

    fun setBestSudoku(context: Context, difficulty: Int, ms: Long) =
        prefs(context).edit().putLong("best_sudoku_$difficulty", ms).apply()

    private fun prefs(context: Context) =
        Storage.of(context).getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
