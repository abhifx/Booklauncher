package com.woody.booklauncher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.woody.booklauncher.games.BattleshipGame
import com.woody.booklauncher.games.BattleshipView
import com.woody.booklauncher.games.Board2048View
import com.woody.booklauncher.games.CheckersGame
import com.woody.booklauncher.games.CheckersView
import com.woody.booklauncher.games.ChessGame
import com.woody.booklauncher.games.ChessView
import com.woody.booklauncher.games.ConnectFourGame
import com.woody.booklauncher.games.ConnectFourView
import com.woody.booklauncher.games.Game2048
import com.woody.booklauncher.games.GameKey
import com.woody.booklauncher.games.GameStore
import com.woody.booklauncher.games.MinesView
import com.woody.booklauncher.games.Minesweeper
import com.woody.booklauncher.games.NonogramGame
import com.woody.booklauncher.games.NonogramView
import com.woody.booklauncher.games.OthelloGame
import com.woody.booklauncher.games.OthelloView
import com.woody.booklauncher.games.HeartsGame
import com.woody.booklauncher.games.HeartsView
import com.woody.booklauncher.games.InterceptStory
import com.woody.booklauncher.games.CarnivoreGame
import com.woody.booklauncher.games.MurdleGame
import com.woody.booklauncher.games.MurdlePuzzle
import com.woody.booklauncher.games.CarnivoreCase
import com.woody.booklauncher.games.Nonograms
import com.woody.booklauncher.games.SolitaireGame
import com.woody.booklauncher.games.SolitaireView
import com.woody.booklauncher.games.SudokuGame
import com.woody.booklauncher.games.SudokuView
import com.woody.booklauncher.games.WordleGame
import com.woody.booklauncher.games.WordleKeyboardView
import com.woody.booklauncher.games.WordleView
import com.woody.booklauncher.games.WordleWords

/**
 * 전자잉크에 맞춘 게임 아홉. 애니메이션 없이 바뀐 판만 다시 그리고, 기록과 하던 판은 `games` 설정에 남긴다.
 * 판 그리기는 games/GameViews.kt, 규칙은 games/ 의 파일마다.
 */
class GamesActivity : EinkActivity() {
    private enum class Page(val id: String, val titleRes: Int, val descRes: Int) {
        HUB("hub", 0, 0),
        G2048("2048", R.string.game_2048, R.string.game_2048_desc),
        MINES(GameStore.MINES, R.string.game_minesweeper, R.string.game_minesweeper_desc),
        SUDOKU(GameStore.SUDOKU, R.string.game_sudoku, R.string.game_sudoku_desc),
        NONOGRAM(GameStore.NONOGRAM, R.string.game_nonogram, R.string.game_nonogram_desc),
        CONNECT4(GameStore.CONNECT4, R.string.game_connect4, R.string.game_connect4_desc),
        CHECKERS(GameStore.CHECKERS, R.string.game_checkers, R.string.game_checkers_desc),
        CHESS(GameStore.CHESS, R.string.game_chess, R.string.game_chess_desc),
        OTHELLO(GameStore.OTHELLO, R.string.game_othello, R.string.game_othello_desc),
        SOLITAIRE(GameStore.SOLITAIRE, R.string.game_solitaire, R.string.game_solitaire_desc),
        HEARTS(GameStore.HEARTS, R.string.game_hearts, R.string.game_hearts_desc),
        INTERCEPT(GameStore.INTERCEPT, R.string.game_intercept, R.string.game_intercept_desc),
        CARNIVORE(GameStore.CARNIVORE, R.string.game_carnivore, R.string.game_carnivore_desc),
        MURDLE(GameStore.MURDLE, R.string.game_murdle, R.string.game_murdle_desc),
        BATTLESHIP(GameStore.BATTLESHIP, R.string.game_battleship, R.string.game_battleship_desc),
        WORDLE(GameStore.WORDLE, R.string.game_wordle, R.string.game_wordle_desc),
    }

    private lateinit var body: LinearLayout
    private lateinit var title: TextView
    private var page = Page.HUB

    private var status: TextView? = null
    private var refreshStatus: (() -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())
    private var ticker: Runnable? = null

    private var game2048: Game2048? = null
    private var board2048: Board2048View? = null

    private var mines: Minesweeper? = null
    private var minesView: MinesView? = null
    private var minesLevel = 0

    private var sudoku: SudokuGame? = null
    private var sudokuView: SudokuView? = null
    private var sudokuNotes = false

    private var nonogram: NonogramGame? = null
    private var nonogramView: NonogramView? = null
    private var nonogramCross = false

    private var connect4: ConnectFourGame? = null
    private var connect4View: ConnectFourView? = null
    private var connect4Level = 1

    private var checkers: CheckersGame? = null
    private var checkersView: CheckersView? = null
    private var checkersLevel = 1

    private var chess: ChessGame? = null
    private var chessView: ChessView? = null
    private var chessLevel = 1

    private var othello: OthelloGame? = null
    private var othelloView: OthelloView? = null
    private var othelloLevel = 1

    private var hearts: HeartsGame? = null
    private var heartsView: HeartsView? = null
    private var heartsLevel = 1
    private var heartsSelected: Int? = null

    private var intercept: InterceptStory? = null
    private var carnivore: CarnivoreGame? = null
    private var murdle: MurdleGame? = null
    private var hubPage = 0

    private var solitaire: SolitaireGame? = null
    private var solitaireView: SolitaireView? = null
    private var solitaireSelected: SolitaireView.SolitaireHit? = null

    private var battleship: BattleshipGame? = null
    private var battleshipView: BattleshipView? = null
    private var battleshipOwn = false

    private var wordle: WordleGame? = null
    private var wordleView: WordleView? = null
    private var wordleKeyboard: WordleKeyboardView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = Page.entries.firstOrNull { it.id == GameStore.lastPage(this) } ?: Page.HUB
        val root = vbox()
        root.addView(titleRow(getString(R.string.games)) { if (!back()) finish() }.apply {
            title = text("", 19f, color = Ui.GRAY)
            addView(title, lp(WRAP, WRAP).apply { marginStart = dp(14); topMargin = dp(6) })
        })
        body = vbox()
        root.addView(softCard(0).apply { addView(body, lp(MATCH, MATCH)) }, cardLp(weight = 1f, topDp = 8))
        root.addView(hline(1, inset = false))
        setContentView(root)
        render()
    }

    override fun onResume() {
        super.onResume()
        startTicker()
    }

    override fun onPause() {
        saveCurrent()
        stopTicker()
        super.onPause()
    }

    override fun onDestroy() {
        stopTicker()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!back()) super.onBackPressed()
    }

    private fun back(): Boolean {
        if (page == Page.HUB) return false
        saveCurrent()
        page = Page.HUB
        GameStore.setLastPage(this, Page.HUB.id)
        render()
        return true
    }

    /** 판에서 화면 밀기로 쪽을 넘기지 않는다(2048·체커·솔리테어가 밀기/누르기를 쓴다). 허브에서는 게임 쪽을 넘긴다. */
    override fun onSwipe(toLeft: Boolean): Boolean = if (page == Page.HUB) hubTurn(toLeft) else false

    private fun render() {
        stopTicker()
        body.removeAllViews()
        status = null
        refreshStatus = null
        title.text = if (page == Page.HUB) "" else getString(page.titleRes)
        if (page == Page.HUB) hub() else when (page) {
            Page.G2048 -> page2048()
            Page.MINES -> pageMines()
            Page.SUDOKU -> pageSudoku()
            Page.NONOGRAM -> pageNonogram()
            Page.CONNECT4 -> pageConnect4()
            Page.CHECKERS -> pageCheckers()
            Page.CHESS -> pageChess()
            Page.OTHELLO -> pageOthello()
            Page.SOLITAIRE -> pageSolitaire()
            Page.HEARTS -> pageHearts()
            Page.INTERCEPT -> pageIntercept()
            Page.CARNIVORE -> pageCarnivore()
            Page.MURDLE -> pageMurdle()
            Page.BATTLESHIP -> pageBattleship()
            Page.WORDLE -> pageWordle()
            Page.HUB -> Unit
        }
        if (page != Page.HUB) startTicker()
    }

    private fun open(to: Page) {
        page = to
        GameStore.setLastPage(this, to.id)
        render()
    }

    // ---- 허브 ---------------------------------------------------------------

    private fun hub() {
        body.setPadding(dp(Ui.MARGIN), dp(4), dp(Ui.MARGIN), dp(8))
        val games = Page.entries.filter { it != Page.HUB }
        val perPage = 10
        val pages = (games.size + perPage - 1) / perPage
        hubPage = hubPage.coerceIn(0, maxOf(0, pages - 1))
        val shown = games.drop(hubPage * perPage).take(perPage)
        shown.chunked(2).forEachIndexed { rowIndex, pair ->
            if (rowIndex > 0) body.addView(hline(1, Ui.DIVIDER))
            body.addView(hbox().apply {
                pair.forEachIndexed { i, p ->
                    if (i > 0) addView(vline(1))
                    addView(vbox().apply {
                        setPadding(dp(10), dp(7), dp(10), dp(7))
                        addView(text(getString(p.titleRes), 20f, Ui.medium))
                        addView(text(getString(p.descRes), 12f, color = Ui.GRAY, lines = 2), lp(MATCH, WRAP).apply { topMargin = dp(1) })
                        addView(text(hubStatus(p), 11.5f, color = if (GameStore.active(this@GamesActivity, p.id)) Ui.BLACK else Ui.GRAY, lines = 1), lp(MATCH, WRAP).apply { topMargin = dp(2) })
                        setOnClickListener { open(p) }
                    }, lp(0, MATCH, 1f))
                }
                if (pair.size == 1) addView(View(this@GamesActivity), lp(0, 1, 1f))
            }, lp(MATCH, dp(112)))
        }
        if (pages > 1) {
            body.addView(hbox().apply {
                addView(GameKey(this@GamesActivity, "‹", false) { hubPage = (hubPage + pages - 1) % pages; render() }, lp(0, dp(44), 1f))
                addView(text("${hubPage + 1} / $pages", 16f, Ui.medium).apply { gravity = Gravity.CENTER }, lp(0, dp(44), 2f))
                addView(GameKey(this@GamesActivity, "›", false) { hubPage = (hubPage + 1) % pages; render() }, lp(0, dp(44), 1f))
            }, lp(MATCH, WRAP).apply { topMargin = dp(4) })
        }
    }

    private fun hubTurn(toLeft: Boolean): Boolean {
        val games = Page.entries.count { it != Page.HUB }
        val pages = (games + 9) / 10
        if (pages <= 1) return false
        hubPage = (hubPage + if (toLeft) 1 else pages - 1) % pages
        render()
        return true
    }

    private fun hubStatus(p: Page): String = when (p) {
        Page.G2048 -> listOfNotNull(
            GameStore.best(this, GameStore.G2048).takeIf { it > 0 }?.let { "${getString(R.string.game_best)} ${it.toInt()}" },
            getString(R.string.game_continue).takeIf { GameStore.active(this, GameStore.G2048) },
        ).joinToString(" · ").ifEmpty { getString(R.string.game_tap_to_start) }
        Page.MINES -> listOfNotNull(
            bestMines()?.let { "${getString(R.string.game_best)} ${timeText(it)}" },
            getString(R.string.game_continue).takeIf { GameStore.active(this, GameStore.MINES) },
        ).joinToString(" · ").ifEmpty { getString(R.string.game_tap_to_start) }
        Page.SUDOKU -> listOfNotNull(
            GameStore.best(this, "sudoku_1").takeIf { it > 0 }?.let { "${getString(R.string.game_best)} ${timeText(it)}" },
            getString(R.string.game_continue).takeIf { GameStore.active(this, GameStore.SUDOKU) },
        ).joinToString(" · ").ifEmpty { getString(R.string.game_tap_to_start) }
        Page.NONOGRAM -> listOfNotNull(
            GameStore.count(this, "nonogram_solved").takeIf { it > 0 }?.let { "${getString(R.string.game_solved)} $it" },
            getString(R.string.game_continue).takeIf { GameStore.active(this, GameStore.NONOGRAM) },
        ).joinToString(" · ").ifEmpty { getString(R.string.game_tap_to_start) }
        Page.CONNECT4 -> scoreLine(GameStore.CONNECT4)
        Page.CHECKERS -> scoreLine(GameStore.CHECKERS)
        Page.CHESS -> scoreLine(GameStore.CHESS)
        Page.OTHELLO -> scoreLine(GameStore.OTHELLO)
        Page.HEARTS -> scoreLine(GameStore.HEARTS)
        Page.INTERCEPT -> {
            val active = getString(R.string.game_continue).takeIf { GameStore.active(this, GameStore.INTERCEPT) }
            active ?: getString(R.string.game_tap_to_start)
        }
        Page.CARNIVORE -> {
            val solved = GameStore.count(this, "carnivore_solved")
            val active = getString(R.string.game_continue).takeIf { GameStore.active(this, GameStore.CARNIVORE) }
            listOfNotNull(if (solved > 0) "${getString(R.string.game_solved)} $solved" else null, active).joinToString(" · ").ifEmpty { getString(R.string.game_tap_to_start) }
        }
        Page.MURDLE -> {
            val solved = GameStore.count(this, "murdle_solved")
            val active = getString(R.string.game_continue).takeIf { GameStore.active(this, GameStore.MURDLE) }
            listOfNotNull(if (solved > 0) "${getString(R.string.game_solved)} $solved" else null, active).joinToString(" · ").ifEmpty { getString(R.string.game_tap_to_start) }
        }
        Page.SOLITAIRE -> {
            val w = GameStore.wins(this, GameStore.SOLITAIRE)
            val played = GameStore.count(this, "solitaire_played")
            val active = getString(R.string.game_continue).takeIf { GameStore.active(this, GameStore.SOLITAIRE) }
            listOfNotNull("$w / $played", active).joinToString(" · ")
        }
        Page.BATTLESHIP -> scoreLine(GameStore.BATTLESHIP)
        Page.WORDLE -> {
            val played = GameStore.count(this, "wordle_played")
            val wins = GameStore.count(this, "wordle_won")
            val streak = GameStore.count(this, "wordle_streak")
            if (played == 0) getString(R.string.game_tap_to_start)
            else "$wins / $played · ${getString(R.string.game_wordle_streak)} $streak"
        }
        Page.HUB -> ""
    }

    private fun scoreLine(id: String): String {
        val w = GameStore.wins(this, id)
        val l = GameStore.losses(this, id)
        val active = getString(R.string.game_continue).takeIf { GameStore.active(this, id) }
        return listOfNotNull("$w${getString(R.string.game_wins_short)} $l${getString(R.string.game_losses_short)}", active).joinToString(" · ")
    }

    // ---- 2048 ---------------------------------------------------------------

    private fun page2048() {
        val g = game2048 ?: load(GameStore.G2048, Game2048::decode)?.also { game2048 = it } ?: new2048()
        body.setPadding(dp(Ui.MARGIN), dp(8), dp(Ui.MARGIN), dp(8))
        val s = statusText(); status = s
        board2048 = Board2048View(this, g) { move2048(it) }
        body.addView(s, lp(MATCH, WRAP).apply { bottomMargin = dp(5) })
        body.addView(board2048!!, lp(MATCH, 0, 1f))
        body.addView(keyRow(listOf("←" to { move2048(3) }, "↑" to { move2048(0) }, "↓" to { move2048(2) }, "→" to { move2048(1) }), 50), lp(MATCH, WRAP).apply { topMargin = dp(6) })
        body.addView(keyRow(listOf(
            getString(R.string.game_undo) to { if (g.undo()) { GameStore.setActive(this, GameStore.G2048, true); save2048(); board2048?.invalidate(); update2048() } },
            getString(R.string.game_new) to { new2048(); render() },
        ), 48), lp(MATCH, WRAP).apply { topMargin = dp(5) })
        refreshStatus = { update2048() }
        update2048()
    }

    private fun new2048(): Game2048 {
        val g = Game2048().apply { newGame() }
        game2048 = g
        GameStore.setActive(this, GameStore.G2048, true)
        save2048()
        return g
    }

    private fun save2048() {
        game2048?.let { GameStore.saveState(this, GameStore.G2048, it.encode()) }
    }

    private fun move2048(dir: Int) {
        val g = game2048 ?: return
        if (!g.move(dir)) return
        GameStore.setBest(this, GameStore.G2048, g.score.toLong())
        if (g.over) GameStore.setActive(this, GameStore.G2048, false)
        save2048()
        board2048?.invalidate()
        update2048()
    }

    private fun update2048() {
        val g = game2048 ?: return
        val best = maxOf(GameStore.best(this, GameStore.G2048), g.score.toLong())
        val tail = when {
            g.won -> "  ·  ${getString(R.string.game_2048_won)}"
            g.over -> "  ·  ${getString(R.string.game_lost)}"
            else -> ""
        }
        status?.text = "${getString(R.string.game_score)} ${g.score}  ·  ${getString(R.string.game_best)} $best$tail"
    }

    // ---- 지뢰찾기 -----------------------------------------------------------

    private val mineLevels = listOf(Triple(9, 10, R.string.game_easy), Triple(12, 24, R.string.game_normal), Triple(16, 50, R.string.game_hard))

    private fun pageMines() {
        val g = mines ?: load(GameStore.MINES, Minesweeper::decode)?.also {
            mines = it
            minesLevel = mineLevels.indexOfFirst { l -> l.first == it.size }.coerceAtLeast(0)
        } ?: newMines(0)
        body.setPadding(dp(Ui.MARGIN), dp(6), dp(Ui.MARGIN), dp(8))
        val s = statusText(); status = s
        body.addView(s, lp(MATCH, WRAP).apply { bottomMargin = dp(4) })
        minesView = MinesView(this, g, onReveal = { revealMine(it) }, onFlag = { flagMine(it) })
        body.addView(minesView!!, lp(MATCH, 0, 1f))
        body.addView(difficultyRow(mineLevels.mapIndexed { i, l -> getString(l.third) to { newMines(i); render() } }, minesLevel), lp(MATCH, WRAP).apply { topMargin = dp(5) })
        body.addView(GameKey(this, getString(R.string.game_new), true) { newMines(minesLevel); render() }, lp(MATCH, dp(44)).apply { topMargin = dp(4) })
        body.addView(text(getString(R.string.game_mines_help), 12f, color = Ui.GRAY).apply { gravity = Gravity.CENTER }, lp(MATCH, WRAP).apply { topMargin = dp(3) })
        refreshStatus = { updateMines() }
        updateMines()
    }

    private fun newMines(level: Int): Minesweeper {
        minesLevel = level.coerceIn(mineLevels.indices)
        val l = mineLevels[minesLevel]
        val g = Minesweeper(l.first, l.second).apply { newGame() }
        mines = g
        GameStore.setActive(this, GameStore.MINES, true)
        saveMines()
        return g
    }

    private fun saveMines() {
        mines?.let { GameStore.saveState(this, GameStore.MINES, it.encode()) }
    }

    private fun revealMine(index: Int) {
        val g = mines ?: return
        val changed = if (g.revealed[index]) g.chord(index) else g.reveal(index)
        if (!changed) return
        if (g.won || g.lost) {
            if (g.won) GameStore.setBestLow(this, minesBestKey(), g.elapsed())
            GameStore.setActive(this, GameStore.MINES, false)
        }
        saveMines()
        minesView?.invalidate()
        updateMines()
    }

    private fun flagMine(index: Int) {
        val g = mines ?: return
        if (!g.toggleFlag(index)) return
        saveMines()
        minesView?.invalidate()
        updateMines()
    }

    private fun updateMines() {
        val g = mines ?: return
        val best = GameStore.best(this, minesBestKey())
        val tag = when {
            g.won -> "  ·  ${getString(R.string.game_mines_won)}"
            g.lost -> "  ·  ${getString(R.string.game_mines_lost)}"
            else -> ""
        }
        status?.text = getString(R.string.game_mines_left, maxOf(0, g.mineCount - g.flags)) +
            "  ·  ${getString(R.string.game_time)} ${timeText(g.elapsed())}" +
            (if (best > 0) "  ·  ${getString(R.string.game_best)} ${timeText(best)}" else "") + tag
        if (g.won || g.lost) stopTicker()
    }

    private fun minesBestKey(): String = "mines_${mineLevels[minesLevel].first}_${mineLevels[minesLevel].second}"
    private fun bestMines(): Long? = mineLevels.map { GameStore.best(this, "mines_${it.first}_${it.second}") }.filter { it > 0 }.maxOrNull()

    // ---- 스도쿠 -------------------------------------------------------------

    private fun pageSudoku() {
        val g = sudoku ?: load(GameStore.SUDOKU, SudokuGame::decode)?.also { sudoku = it } ?: newSudoku(1)
        body.setPadding(dp(Ui.MARGIN), dp(5), dp(Ui.MARGIN), dp(7))
        val s = statusText(); status = s
        body.addView(s, lp(MATCH, WRAP).apply { bottomMargin = dp(3) })
        sudokuView = SudokuView(this, g) { index -> g.selected = index; sudokuView?.invalidate() }
        body.addView(sudokuView!!, lp(MATCH, 0, 1f))
        listOf(1..5, 6..9).forEachIndexed { row, range ->
            body.addView(hbox().apply {
                range.forEach { n ->
                    addView(GameKey(this@GamesActivity, n.toString(), false) { enterSudoku(n) }, lp(0, dp(42), 1f).apply { if (n != range.first) marginStart = dp(4) })
                }
                repeat(5 - range.count()) { addView(View(this@GamesActivity), lp(0, 1, 1f)) }
            }, lp(MATCH, WRAP).apply { if (row > 0) topMargin = dp(4) })
        }
        body.addView(keyRow(listOf(
            getString(R.string.game_notes) to { sudokuNotes = !sudokuNotes; updateSudoku() },
            getString(R.string.game_sudoku_erase) to { if (g.erase()) { saveSudoku(); invalidateSudoku() } },
            getString(R.string.game_hint) to { if (g.hint()) { saveSudoku(); invalidateSudoku() } },
            getString(R.string.game_undo) to { if (g.undo()) { GameStore.setActive(this, GameStore.SUDOKU, true); saveSudoku(); invalidateSudoku() } },
        ), 42), lp(MATCH, WRAP).apply { topMargin = dp(4) })
        body.addView(difficultyRow(listOf(
            getString(R.string.game_easy) to { newSudoku(0); render() },
            getString(R.string.game_normal) to { newSudoku(1); render() },
            getString(R.string.game_hard) to { newSudoku(2); render() },
        ), g.difficulty), lp(MATCH, WRAP).apply { topMargin = dp(4) })
        refreshStatus = { updateSudoku() }
        updateSudoku()
    }

    private fun newSudoku(level: Int): SudokuGame {
        val g = SudokuGame().apply { newGame(level) }
        sudoku = g
        sudokuNotes = false
        GameStore.setActive(this, GameStore.SUDOKU, true)
        saveSudoku()
        return g
    }

    private fun saveSudoku() {
        sudoku?.let { GameStore.saveState(this, GameStore.SUDOKU, it.encode()) }
    }

    private fun enterSudoku(value: Int) {
        val g = sudoku ?: return
        if (g.enter(value, sudokuNotes)) {
            if (g.done) {
                GameStore.setBestLow(this, "sudoku_${g.difficulty}", g.elapsed())
                GameStore.setActive(this, GameStore.SUDOKU, false)
            }
            saveSudoku()
            invalidateSudoku()
        } else if (value != 0) {
            status?.text = getString(R.string.game_sudoku_wrong)
        }
    }

    private fun invalidateSudoku() {
        sudokuView?.invalidate()
        updateSudoku()
    }

    private fun updateSudoku() {
        val g = sudoku ?: return
        val level = getString(when (g.difficulty) { 0 -> R.string.game_easy; 2 -> R.string.game_hard; else -> R.string.game_normal })
        val best = GameStore.best(this, "sudoku_${g.difficulty}")
        val tag = when {
            g.done -> "  ·  ${getString(R.string.game_completed)}"
            g.mistakes > 0 -> "  ·  ${getString(R.string.game_mistakes)} ${g.mistakes}"
            sudokuNotes -> "  ·  ${getString(R.string.game_notes)}"
            else -> ""
        }
        status?.text = "$level  ·  ${getString(R.string.game_time)} ${timeText(g.elapsed())}" +
            (if (best > 0) "  ·  ${getString(R.string.game_best)} ${timeText(best)}" else "") + tag
        if (g.done) stopTicker()
    }

    // ---- 노노그램 -----------------------------------------------------------

    private fun pageNonogram() {
        val g = nonogram ?: load(GameStore.NONOGRAM, NonogramGame::decode)?.also { nonogram = it } ?: newNonogram(0)
        body.setPadding(dp(Ui.MARGIN), dp(4), dp(Ui.MARGIN), dp(5))
        val s = statusText(); status = s
        body.addView(s, lp(MATCH, WRAP).apply { bottomMargin = dp(2) })
        body.addView(hbox().apply {
            addView(GameKey(this@GamesActivity, "‹", false) { switchNonogram(-1) }, lp(dp(42), dp(38)))
            addView(text(g.puzzle.name, 17f, Ui.medium).apply { gravity = Gravity.CENTER }, lp(0, dp(38), 1f))
            addView(GameKey(this@GamesActivity, "›", false) { switchNonogram(1) }, lp(dp(42), dp(38)))
        }, lp(MATCH, WRAP))
        nonogramView = NonogramView(this, g) { i, cross -> tapNonogram(i, cross) }
        nonogramView?.crossMode = nonogramCross
        body.addView(nonogramView!!, lp(MATCH, 0, 1f))
        body.addView(keyRow(listOf(
            getString(if (nonogramCross) R.string.game_cross else R.string.game_fill) to {
                nonogramCross = !nonogramCross; nonogramView?.crossMode = nonogramCross; updateNonogram()
            },
            getString(R.string.game_hint) to { if (g.hint() >= 0) { saveNonogram(); invalidateNonogram() } },
            getString(R.string.game_undo) to { if (g.undo()) { GameStore.setActive(this, GameStore.NONOGRAM, true); saveNonogram(); invalidateNonogram() } },
            getString(R.string.game_new) to { newNonogram(g.puzzleIndex); render() },
        ), 40), lp(MATCH, WRAP).apply { topMargin = dp(3) })
        refreshStatus = { updateNonogram() }
        updateNonogram()
    }

    private fun newNonogram(index: Int): NonogramGame {
        val g = NonogramGame(index.coerceIn(Nonograms.ALL.indices)).apply { newGame(puzzleIndex) }
        nonogram = g
        nonogramCross = false
        GameStore.setActive(this, GameStore.NONOGRAM, true)
        saveNonogram()
        return g
    }

    private fun switchNonogram(delta: Int) {
        val g = nonogram ?: return
        newNonogram((g.puzzleIndex + delta + Nonograms.ALL.size) % Nonograms.ALL.size)
        render()
    }

    private fun saveNonogram() {
        nonogram?.let { GameStore.saveState(this, GameStore.NONOGRAM, it.encode()) }
    }

    private fun tapNonogram(index: Int, cross: Boolean) {
        val g = nonogram ?: return
        val mark = if (cross) 2 else 1
        if (!g.set(index, if (g.marks[index] == mark) 0 else mark)) return
        if (g.solved) {
            GameStore.addCount(this, "nonogram_solved")
            GameStore.setBestLow(this, "nonogram", g.elapsed())
            GameStore.setActive(this, GameStore.NONOGRAM, false)
        }
        saveNonogram()
        invalidateNonogram()
    }

    private fun invalidateNonogram() {
        nonogramView?.invalidate()
        updateNonogram()
    }

    private fun updateNonogram() {
        val g = nonogram ?: return
        val best = GameStore.best(this, "nonogram")
        val tag = when {
            g.solved -> "  ·  ${getString(R.string.game_completed)}"
            g.mistakes > 0 -> "  ·  ${getString(R.string.game_mistakes)} ${g.mistakes}"
            else -> ""
        }
        status?.text = "${getString(R.string.game_time)} ${timeText(g.elapsed())}" +
            (if (best > 0) "  ·  ${getString(R.string.game_best)} ${timeText(best)}" else "") + tag
        if (g.solved) stopTicker()
    }

    // ---- Connect Four -------------------------------------------------------

    private fun pageConnect4() {
        val g = connect4 ?: load(GameStore.CONNECT4, ConnectFourGame::decode)?.also { connect4 = it } ?: newConnect4()
        body.setPadding(dp(Ui.MARGIN), dp(6), dp(Ui.MARGIN), dp(8))
        val s = statusText(); status = s
        body.addView(s, lp(MATCH, WRAP).apply { bottomMargin = dp(4) })
        connect4View = ConnectFourView(this, g) { dropConnect4(it) }
        body.addView(connect4View!!, lp(MATCH, 0, 1f))
        body.addView(difficultyRow(listOf(
            getString(R.string.game_easy) to { connect4Level = 0; updateConnect4() },
            getString(R.string.game_normal) to { connect4Level = 1; updateConnect4() },
            getString(R.string.game_hard) to { connect4Level = 2; updateConnect4() },
        ), connect4Level), lp(MATCH, WRAP).apply { topMargin = dp(5) })
        body.addView(keyRow(listOf(
            getString(R.string.game_undo) to { undoConnect4() },
            getString(R.string.game_new) to { newConnect4(); render() },
        ), 44), lp(MATCH, WRAP).apply { topMargin = dp(4) })
        refreshStatus = { updateConnect4() }
        updateConnect4()
    }

    private fun newConnect4(): ConnectFourGame {
        val g = ConnectFourGame().apply { newGame() }
        connect4 = g
        GameStore.setActive(this, GameStore.CONNECT4, true)
        saveConnect4()
        return g
    }

    private fun saveConnect4() {
        connect4?.let { GameStore.saveState(this, GameStore.CONNECT4, it.encode()) }
    }

    private fun dropConnect4(col: Int) {
        val g = connect4 ?: return
        if (g.over || g.turn != ConnectFourGame.HUMAN || g.drop(col, ConnectFourGame.HUMAN) < 0) return
        if (!g.over) g.aiMove(connect4Level)
        if (g.over) recordWinLoss(GameStore.CONNECT4, g.winner == ConnectFourGame.HUMAN)
        saveConnect4()
        connect4View?.invalidate()
        updateConnect4()
    }

    private fun undoConnect4() {
        val g = connect4 ?: return
        if (!g.undo()) return
        if (g.turn != ConnectFourGame.HUMAN) g.undo()
        GameStore.setActive(this, GameStore.CONNECT4, true)
        saveConnect4()
        connect4View?.invalidate()
        updateConnect4()
    }

    private fun updateConnect4() {
        val g = connect4 ?: return
        val turn = when {
            g.over && g.winner == ConnectFourGame.HUMAN -> getString(R.string.game_you_won)
            g.over && g.winner == ConnectFourGame.AI -> getString(R.string.game_ai_won)
            g.over -> getString(R.string.game_draw)
            g.turn == ConnectFourGame.HUMAN -> getString(R.string.game_your_turn)
            else -> getString(R.string.game_ai_turn)
        }
        status?.text = "$turn  ·  ${GameStore.wins(this, GameStore.CONNECT4)}${getString(R.string.game_wins_short)} " +
            "${GameStore.losses(this, GameStore.CONNECT4)}${getString(R.string.game_losses_short)}"
    }

    // ---- 체커 ---------------------------------------------------------------

    private fun pageCheckers() {
        val g = checkers ?: load(GameStore.CHECKERS, CheckersGame::decode)?.also { checkers = it } ?: newCheckers()
        body.setPadding(dp(Ui.MARGIN), dp(6), dp(Ui.MARGIN), dp(8))
        val s = statusText(); status = s
        body.addView(s, lp(MATCH, WRAP).apply { bottomMargin = dp(4) })
        checkersView = CheckersView(this, g) { tapCheckers(it) }
        body.addView(checkersView!!, lp(MATCH, 0, 1f))
        body.addView(difficultyRow(listOf(
            getString(R.string.game_easy) to { checkersLevel = 0; updateCheckers() },
            getString(R.string.game_normal) to { checkersLevel = 1; updateCheckers() },
        ), checkersLevel), lp(MATCH, WRAP).apply { topMargin = dp(5) })
        body.addView(keyRow(listOf(
            getString(R.string.game_undo) to { if (g.undoRound()) { GameStore.setActive(this, GameStore.CHECKERS, true); saveCheckers(); checkersView?.invalidate(); updateCheckers() } },
            getString(R.string.game_new) to { newCheckers(); render() },
        ), 44), lp(MATCH, WRAP).apply { topMargin = dp(4) })
        refreshStatus = { updateCheckers() }
        updateCheckers()
    }

    private fun newCheckers(): CheckersGame {
        val g = CheckersGame().apply { newGame() }
        checkers = g
        GameStore.setActive(this, GameStore.CHECKERS, true)
        saveCheckers()
        return g
    }

    private fun saveCheckers() {
        checkers?.let { GameStore.saveState(this, GameStore.CHECKERS, it.encode()) }
    }

    private fun tapCheckers(index: Int) {
        val g = checkers ?: return
        if (g.over) return
        if (g.selected >= 0) {
            val move = g.movesFrom(g.selected).firstOrNull { it.to == index }
            if (move != null) {
                if (g.apply(move)) {
                    aiCheckers()
                    if (g.over) recordWinLoss(GameStore.CHECKERS, g.winner == CheckersGame.HUMAN)
                    saveCheckers()
                    checkersView?.invalidate()
                    updateCheckers()
                }
                return
            }
        }
        g.select(index)
        checkersView?.invalidate()
    }

    /** 기기는 연달아 잡을 수 있으면 그 차례를 끝까지 둔다. */
    private fun aiCheckers() {
        val g = checkers ?: return
        var guard = 0
        while (!g.over && g.turn == CheckersGame.AI && guard++ < 10) {
            if (g.aiMove(checkersLevel) == null) break
        }
    }

    private fun updateCheckers() {
        val g = checkers ?: return
        val turn = when {
            g.over && g.winner == CheckersGame.HUMAN -> getString(R.string.game_you_won)
            g.over && g.winner == CheckersGame.AI -> getString(R.string.game_ai_won)
            g.over -> getString(R.string.game_draw)
            g.turn == CheckersGame.HUMAN -> getString(R.string.game_your_turn)
            else -> getString(R.string.game_ai_turn)
        }
        val mine = g.board.count { it == CheckersGame.HUMAN_MAN || it == CheckersGame.HUMAN_KING }
        val theirs = g.board.count { it == CheckersGame.AI_MAN || it == CheckersGame.AI_KING }
        status?.text = "$turn  ·  $mine : $theirs  ·  ${GameStore.wins(this, GameStore.CHECKERS)}${getString(R.string.game_wins_short)} " +
            "${GameStore.losses(this, GameStore.CHECKERS)}${getString(R.string.game_losses_short)}"
    }

    // ---- 체스 ---------------------------------------------------------------

    private fun pageChess() {
        val g = chess ?: load(GameStore.CHESS, ChessGame::decode)?.also { chess = it } ?: newChess(ChessGame.WHITE)
        body.setPadding(dp(Ui.MARGIN), dp(4), dp(Ui.MARGIN), dp(6))
        val s = statusText(); status = s
        body.addView(s, lp(MATCH, WRAP).apply { bottomMargin = dp(2) })
        chessView = ChessView(this, g) { from, to -> tapChess(from, to) }
        body.addView(chessView!!, lp(MATCH, 0, 1f))
        body.addView(difficultyRow(listOf(
            getString(R.string.game_easy) to { chessLevel = 0; updateChess() },
            getString(R.string.game_normal) to { chessLevel = 1; updateChess() },
            getString(R.string.game_hard) to { chessLevel = 2; updateChess() },
        ), chessLevel), lp(MATCH, WRAP))
        body.addView(hbox().apply {
            addView(GameKey(this@GamesActivity, getString(R.string.chess_white), g.human == ChessGame.WHITE) {
                newChess(ChessGame.WHITE); render()
            }, lp(0, dp(38), 1f))
            addView(GameKey(this@GamesActivity, getString(R.string.chess_black), g.human == ChessGame.BLACK) {
                newChess(ChessGame.BLACK); render()
            }, lp(0, dp(38), 1f).apply { marginStart = dp(4) })
        }, lp(MATCH, WRAP).apply { topMargin = dp(3) })
        body.addView(hbox().apply {
            addView(GameKey(this@GamesActivity, getString(R.string.game_undo), false) {
                if (g.undo()) {
                    chessView?.lastMove = null
                    saveChess()
                    chessView?.invalidate()
                    updateChess()
                }
            }, lp(0, dp(40), 1f))
            addView(GameKey(this@GamesActivity, getString(R.string.game_new), true) {
                newChess(g.human); render()
            }, lp(0, dp(40), 1f).apply { marginStart = dp(4) })
        }, lp(MATCH, WRAP).apply { topMargin = dp(3) })
        refreshStatus = { updateChess() }
        updateChess()
        runAiChess()
    }

    private fun newChess(human: Int): ChessGame {
        val g = ChessGame(human)
        chess = g
        chessView?.lastMove = null
        GameStore.setActive(this, GameStore.CHESS, true)
        saveChess()
        return g
    }

    private fun saveChess() {
        chess?.let { GameStore.saveState(this, GameStore.CHESS, it.encode()) }
    }

    private fun tapChess(from: Int, to: Int) {
        val g = chess ?: return
        if (!g.isHumanTurn()) return
        if (g.isPromotion(from, to)) {
            val options = listOf(
                ChessGame.QUEEN to R.string.chess_queen,
                ChessGame.ROOK to R.string.chess_rook,
                ChessGame.BISHOP to R.string.chess_bishop,
                ChessGame.KNIGHT to R.string.chess_knight,
            )
            Sheet(this).header(getString(R.string.chess_promote)).apply {
                options.forEachIndexed { i, (piece, name) ->
                    item(getString(name), bold = i == 0) {
                        doChessMove(from, to, piece)
                    }
                }
            }.show()
            return
        }
        doChessMove(from, to, 0)
    }

    private fun doChessMove(from: Int, to: Int, promotion: Int) {
        val g = chess ?: return
        val move = g.legalMoves(g.turn).firstOrNull {
            it.from == from && it.to == to &&
                (if (it.promotion == 0) promotion == 0 else it.promotion == (if (promotion == 0) ChessGame.QUEEN else promotion))
        } ?: return
        if (!g.play(from, to, promotion)) return
        chessView?.lastMove = move
        afterChessMove()
    }

    private fun afterChessMove() {
        val g = chess ?: return
        if (g.over) recordWinLoss(GameStore.CHESS, g.winner == g.human)
        saveChess()
        chessView?.invalidate()
        updateChess()
        runAiChess()
    }

    private fun runAiChess() {
        val g = chess ?: return
        if (g.over || g.turn == g.human) return
        val move = g.aiMove(chessLevel) ?: return
        if (g.play(move.from, move.to, move.promotion)) {
            chessView?.lastMove = move
            if (g.over) recordWinLoss(GameStore.CHESS, g.winner == g.human)
            saveChess()
            chessView?.invalidate()
            updateChess()
        }
    }

    private fun updateChess() {
        val g = chess ?: return
        val state = when {
            g.over && g.result == ChessGame.Result.CHECKMATE && g.winner == g.human -> getString(R.string.game_you_won)
            g.over && g.result == ChessGame.Result.CHECKMATE -> getString(R.string.game_ai_won)
            g.over && g.result == ChessGame.Result.STALEMATE -> getString(R.string.chess_stalemate)
            g.over -> getString(R.string.game_draw)
            g.turn == g.human -> getString(R.string.game_your_turn)
            else -> getString(R.string.game_ai_turn)
        }
        val check = if (!g.over && g.inCheck(g.turn)) "  ·  ${getString(R.string.chess_check)}" else ""
        status?.text = "$state$check  ·  ${GameStore.wins(this, GameStore.CHESS)}${getString(R.string.game_wins_short)} " +
            "${GameStore.losses(this, GameStore.CHESS)}${getString(R.string.game_losses_short)}"
    }

    // ---- 오셀로 -------------------------------------------------------------

    private fun pageOthello() {
        val g = othello ?: load(GameStore.OTHELLO, OthelloGame::decode)?.also { othello = it } ?: newOthello()
        body.setPadding(dp(Ui.MARGIN), dp(6), dp(Ui.MARGIN), dp(8))
        val s = statusText(); status = s
        body.addView(s, lp(MATCH, WRAP).apply { bottomMargin = dp(4) })
        othelloView = OthelloView(this, g) { index ->
            if (g.play(index)) {
                if (!g.over && g.turn == OthelloGame.AI) {
                    val m = g.aiMove(othelloLevel)
                    if (m >= 0) g.play(m)
                }
                if (g.over) recordWinLoss(GameStore.OTHELLO, g.winner == OthelloGame.HUMAN)
                saveOthello()
                othelloView?.invalidate()
                updateOthello()
            }
        }
        body.addView(othelloView!!, lp(MATCH, 0, 1f))
        body.addView(difficultyRow(listOf(
            getString(R.string.game_easy) to { othelloLevel = 0; updateOthello() },
            getString(R.string.game_normal) to { othelloLevel = 1; updateOthello() },
            getString(R.string.game_hard) to { othelloLevel = 2; updateOthello() },
        ), othelloLevel), lp(MATCH, WRAP).apply { topMargin = dp(4) })
        body.addView(GameKey(this, getString(R.string.game_new), true) { newOthello(); render() }, lp(MATCH, dp(44)).apply { topMargin = dp(4) })
        refreshStatus = { updateOthello() }
        updateOthello()
        runAiOthello()
    }

    /** 기기 차례인 채로 저장된 판을 다시 열면 기기가 이어서 둔다. */
    private fun runAiOthello() {
        val g = othello ?: return
        if (g.over || g.turn != OthelloGame.AI) return
        val m = g.aiMove(othelloLevel)
        if (m >= 0 && g.play(m)) {
            if (g.over) recordWinLoss(GameStore.OTHELLO, g.winner == OthelloGame.HUMAN)
            saveOthello()
            othelloView?.invalidate()
            updateOthello()
        }
    }

    private fun newOthello(): OthelloGame {
        val g = OthelloGame().apply { newGame() }
        othello = g
        GameStore.setActive(this, GameStore.OTHELLO, true)
        saveOthello()
        return g
    }

    private fun saveOthello() {
        othello?.let { GameStore.saveState(this, GameStore.OTHELLO, it.encode()) }
    }

    private fun updateOthello() {
        val g = othello ?: return
        val (h, a) = g.score()
        val turn = when {
            g.over && g.winner == OthelloGame.HUMAN -> getString(R.string.game_you_won)
            g.over && g.winner == OthelloGame.AI -> getString(R.string.game_ai_won)
            g.over -> getString(R.string.game_draw)
            g.turn == OthelloGame.HUMAN -> getString(R.string.game_your_turn)
            else -> getString(R.string.game_ai_turn)
        }
        status?.text = "$turn  ·  ● $h  ○ $a  ·  ${GameStore.wins(this, GameStore.OTHELLO)}${getString(R.string.game_wins_short)} " +
            "${GameStore.losses(this, GameStore.OTHELLO)}${getString(R.string.game_losses_short)}"
    }

    // ---- 하트 ---------------------------------------------------------------

    private fun pageHearts() {
        val g = hearts ?: load(GameStore.HEARTS, HeartsGame::decode)?.also { hearts = it } ?: newHearts()
        body.setPadding(dp(Ui.MARGIN), dp(5), dp(Ui.MARGIN), dp(6))
        val s = statusText(); status = s
        body.addView(s, lp(MATCH, WRAP).apply { bottomMargin = dp(3) })
        heartsView = HeartsView(this, g, listOf(
            getString(R.string.hearts_you),
            getString(R.string.hearts_left),
            getString(R.string.hearts_top),
            getString(R.string.hearts_right),
        )) { card -> tapHearts(card) }
        heartsView?.selectedCards = g.passSelected().toSet()
        body.addView(heartsView!!, lp(MATCH, 0, 1f))
        when (g.phase) {
            HeartsGame.Phase.PASS -> {
                body.addView(text(getString(R.string.game_hearts_pass, if (HomePrefs.isEnglish(this)) g.pass.en else g.pass.ko), 12f, color = Ui.GRAY).apply { gravity = Gravity.CENTER }, lp(MATCH, WRAP).apply { topMargin = dp(2) })
                body.addView(GameKey(this, getString(R.string.game_confirm_pass), true) {
                    if (g.confirmPass(heartsLevel)) { saveHearts(); render() }
                }, lp(MATCH, dp(44)).apply { topMargin = dp(3) })
            }
            HeartsGame.Phase.PLAY -> body.addView(difficultyRow(listOf(
                getString(R.string.game_easy) to { heartsLevel = 0; render() },
                getString(R.string.game_normal) to { heartsLevel = 1; render() },
            ), heartsLevel), lp(MATCH, WRAP).apply { topMargin = dp(3) })
            HeartsGame.Phase.OVER -> Unit
        }
        body.addView(GameKey(this, getString(R.string.game_new), true) { newHearts(); render() }, lp(MATCH, dp(42)).apply { topMargin = dp(3) })
        refreshStatus = { updateHearts() }
        updateHearts()
        if (g.phase == HeartsGame.Phase.PLAY) runAiHearts()
    }

    private fun newHearts(): HeartsGame {
        val g = HeartsGame().apply { newGame() }
        hearts = g
        heartsSelected = null
        GameStore.setActive(this, GameStore.HEARTS, true)
        saveHearts()
        return g
    }

    private fun saveHearts() {
        hearts?.let { GameStore.saveState(this, GameStore.HEARTS, it.encode()) }
    }

    private fun tapHearts(card: Int) {
        val g = hearts ?: return
        when (g.phase) {
            HeartsGame.Phase.PASS -> {
                g.selectPass(card)
                heartsView?.selectedCards = g.passSelected().toSet()
                heartsView?.invalidate()
                updateHearts()
            }
            HeartsGame.Phase.PLAY -> if (g.turn == 0 && g.play(0, card)) {
                heartsSelected = null
                runAiHearts()
                if (g.phase == HeartsGame.Phase.PASS || g.phase == HeartsGame.Phase.OVER) {
                    saveHearts()
                    if (g.phase == HeartsGame.Phase.OVER) render()
                    return
                }
                saveHearts()
                heartsView?.selectedCards = emptySet()
                heartsView?.invalidate()
                updateHearts()
            }
            HeartsGame.Phase.OVER -> Unit
        }
    }

    private fun runAiHearts() {
        val g = hearts ?: return
        var guard = 0
        while (g.phase == HeartsGame.Phase.PLAY && g.turn != 0 && guard++ < 60) {
            val card = g.aiPlay(g.turn, heartsLevel)
            if (card < 0 || !g.play(g.turn, card)) break
        }
        if (g.phase == HeartsGame.Phase.OVER) recordHearts()
        saveHearts()
        if (g.phase == HeartsGame.Phase.PASS || g.phase == HeartsGame.Phase.OVER) {
            if (g.phase == HeartsGame.Phase.PASS) render() else {
                heartsView?.invalidate()
                updateHearts()
            }
        } else {
            heartsView?.invalidate()
            updateHearts()
        }
    }

    private fun recordHearts() {
        val g = hearts ?: return
        if (g.phase != HeartsGame.Phase.OVER || !GameStore.active(this, GameStore.HEARTS)) return
        val best = g.scores.indices.minByOrNull { g.scores[it] } ?: 0
        recordWinLoss(GameStore.HEARTS, best == 0)
    }

    private fun updateHearts() {
        val g = hearts ?: return
        val phase = when (g.phase) {
            HeartsGame.Phase.PASS -> getString(R.string.game_hearts_passing, if (HomePrefs.isEnglish(this)) g.pass.en else g.pass.ko) + "  ·  ${g.passSelected().size}/3"
            HeartsGame.Phase.PLAY -> getString(if (g.turn == 0) R.string.game_your_turn else R.string.game_ai_turn)
            HeartsGame.Phase.OVER -> getString(R.string.game_completed)
        }
        val msg = if (HomePrefs.isEnglish(this)) g.messageEn else g.messageKo
        status?.text = phase + (if (msg.isNotEmpty()) "  ·  $msg" else "")
    }

    // ---- The Intercept ------------------------------------------------------

    private fun pageIntercept() {
        val story = intercept ?: load(GameStore.INTERCEPT, InterceptStory::decode)?.also { intercept = it } ?: InterceptStory().also {
            intercept = it
            GameStore.setActive(this, GameStore.INTERCEPT, true)
        }
        body.setPadding(dp(Ui.MARGIN), dp(10), dp(Ui.MARGIN), dp(10))
        val node = story.current()
        val en = HomePrefs.isEnglish(this)
        body.addView(text(if (en) node.titleEn else node.titleKo, 24f, Ui.heavy))
        body.addView(text(if (en) node.en else node.ko, 17f, lines = 12).apply { setLineSpacing(dp(3).toFloat(), 1f) }, lp(MATCH, 0, 1f).apply { topMargin = dp(10) })
        if (story.over) {
            body.addView(GameKey(this, getString(R.string.game_restart_story), true) {
                story.restart(); GameStore.setActive(this, GameStore.INTERCEPT, true); saveIntercept(); render()
            }, lp(MATCH, dp(46)).apply { topMargin = dp(8) })
            GameStore.setActive(this, GameStore.INTERCEPT, false)
        } else {
            node.choices.forEachIndexed { i, c ->
                body.addView(GameKey(this, if (en) c.en else c.ko, i == 0) {
                    story.choose(i); saveIntercept(); if (story.over) GameStore.setActive(this, GameStore.INTERCEPT, false); render()
                }, lp(MATCH, dp(44)).apply { topMargin = dp(5) })
            }
        }
        saveIntercept()
    }

    private fun saveIntercept() {
        intercept?.let { GameStore.saveState(this, GameStore.INTERCEPT, it.encode()) }
    }

    // ---- Carnivore ----------------------------------------------------------

    private fun pageCarnivore() {
        val g = carnivore ?: load(GameStore.CARNIVORE, CarnivoreGame::decode)?.also { carnivore = it } ?: newCarnivore(0)
        body.setPadding(dp(Ui.MARGIN), dp(5), dp(Ui.MARGIN), dp(6))
        val s = statusText(); status = s
        body.addView(s, lp(MATCH, WRAP).apply { bottomMargin = dp(3) })
        val c = g.case
        val en = HomePrefs.isEnglish(this)
        body.addView(text(getString(R.string.game_case, c.number) + "  ·  ${getString(R.string.game_culprits)} ${c.culpritCount}  ·  ${getString(R.string.game_liars)} ${c.liars}", 13f, color = Ui.GRAY).apply { gravity = Gravity.CENTER }, lp(MATCH, WRAP))
        c.suspects.forEachIndexed { i, suspect ->
            body.addView(hbox().apply {
                setPadding(dp(4), 0, dp(4), 0)
                addView(text(if (en) suspect.nameEn else suspect.name, 18f, Ui.medium), lp(0, WRAP, 1f))
                addView(text("${if (en) suspect.diet.en else suspect.diet.ko} · ${suspect.height}", 12f, color = Ui.GRAY), lp(WRAP, WRAP).apply { marginEnd = dp(8) })
                addView(text(getString(when (g.marks[i]) { 1 -> R.string.game_suspect; 2 -> R.string.game_innocent; else -> R.string.game_unknown }), 11.5f, Ui.medium), lp(dp(84), WRAP).apply { gravity = Gravity.END })
                setOnClickListener { g.cycle(i); updateCarnivore(); render() }
            }, lp(MATCH, dp(30)))
            body.addView(text("“${suspect.statement.text(c.suspects, en)}”", 14f, color = Ui.BLACK, lines = 2).apply { setPadding(dp(18), 0, dp(4), dp(2)) }, lp(MATCH, WRAP))
        }
        body.addView(hbox().apply {
            addView(GameKey(this@GamesActivity, "‹", false) { newCarnivore(c.number - 2); render() }, lp(0, dp(40), 1f))
            addView(GameKey(this@GamesActivity, getString(R.string.game_accuse), true) {
                g.accuse(); if (g.solved) { GameStore.addCount(this@GamesActivity, "carnivore_solved"); GameStore.setActive(this@GamesActivity, GameStore.CARNIVORE, false) }
                if (g.lost) GameStore.setActive(this@GamesActivity, GameStore.CARNIVORE, false)
                saveCarnivore(); render()
            }, lp(0, dp(40), 2f).apply { marginStart = dp(4) })
            addView(GameKey(this@GamesActivity, "›", false) { newCarnivore(c.number); render() }, lp(0, dp(40), 1f).apply { marginStart = dp(4) })
        }, lp(MATCH, WRAP).apply { topMargin = dp(4) })
        if (g.message != CarnivoreGame.Msg.NONE) {
            val msg = when (g.message) {
                CarnivoreGame.Msg.PICK -> getString(R.string.carnivore_pick, c.culpritCount)
                CarnivoreGame.Msg.CORRECT -> getString(R.string.game_correct)
                CarnivoreGame.Msg.WRONG -> getString(R.string.carnivore_wrong, g.lives)
                CarnivoreGame.Msg.DEAD -> getString(R.string.carnivore_dead)
                CarnivoreGame.Msg.NONE -> ""
            }
            body.addView(text(msg, 13f, color = Ui.GRAY).apply { gravity = Gravity.CENTER }, lp(MATCH, WRAP))
        }
        refreshStatus = { updateCarnivore() }
        updateCarnivore()
    }

    private fun newCarnivore(number: Int): CarnivoreGame {
        val list = CarnivoreCase.Cases.all
        val index = (number.coerceAtLeast(1) - 1) % list.size
        val g = CarnivoreGame(index)
        carnivore = g
        GameStore.setActive(this, GameStore.CARNIVORE, true)
        saveCarnivore()
        return g
    }

    private fun saveCarnivore() {
        carnivore?.let { GameStore.saveState(this, GameStore.CARNIVORE, it.encode()) }
    }

    private fun updateCarnivore() {
        val g = carnivore ?: return
        val tag = when {
            g.solved -> getString(R.string.game_correct)
            g.lost -> getString(R.string.game_lost)
            else -> getString(R.string.game_lives) + " " + g.lives
        }
        status?.text = tag
    }

    // ---- Murdle -------------------------------------------------------------

    private fun pageMurdle() {
        val g = murdle ?: load(GameStore.MURDLE, MurdleGame::decode)?.also { murdle = it } ?: newMurdle(0)
        body.setPadding(dp(Ui.MARGIN), dp(5), dp(Ui.MARGIN), dp(6))
        val s = statusText(); status = s
        body.addView(s, lp(MATCH, WRAP).apply { bottomMargin = dp(3) })
        val p = g.puzzle
        val en = HomePrefs.isEnglish(this)
        val sn = if (en) p.suspectsEn else p.suspects
        val wn = if (en) p.weaponsEn else p.weapons
        val rn = if (en) p.roomsEn else p.rooms
        body.addView(text(getString(R.string.game_case, p.number) + "  ·  ${getString(R.string.game_clues)}", 13f, color = Ui.GRAY).apply { gravity = Gravity.CENTER }, lp(MATCH, WRAP))
        p.clues.forEach { clue ->
            body.addView(text("· ${clue.text(sn, wn, rn, en)}", 12.5f, lines = 2).apply { setPadding(dp(4), dp(1), dp(4), dp(1)) }, lp(MATCH, WRAP))
        }
        p.suspects.forEachIndexed { i, name ->
            body.addView(hbox().apply {
                setPadding(dp(4), 0, dp(4), 0)
                addView(text(sn[i], 17f, Ui.medium), lp(0, WRAP, 1f))
                addView(GameKey(this@GamesActivity, if (g.weapons[i] >= 0) wn[g.weapons[i]] else getString(R.string.game_weapon), false) { g.cycleWeapon(i); saveMurdle(); render() }, lp(dp(128), dp(36)))
                addView(GameKey(this@GamesActivity, if (g.rooms[i] >= 0) rn[g.rooms[i]] else getString(R.string.game_room), false) { g.cycleRoom(i); saveMurdle(); render() }, lp(0, dp(36), 1f).apply { marginStart = dp(4) })
            }, lp(MATCH, dp(42)))
        }
        body.addView(hbox().apply {
            addView(GameKey(this@GamesActivity, getString(R.string.game_check), true) {
                g.check(); if (g.solved) { GameStore.addCount(this@GamesActivity, "murdle_solved"); GameStore.setActive(this@GamesActivity, GameStore.MURDLE, false) }
                saveMurdle(); render()
            }, lp(0, dp(42), 1f))
            addView(GameKey(this@GamesActivity, getString(R.string.game_new), false) { newMurdle(p.number); render() }, lp(0, dp(42), 1f).apply { marginStart = dp(4) })
        }, lp(MATCH, WRAP).apply { topMargin = dp(4) })
        refreshStatus = { updateMurdle() }
        updateMurdle()
    }

    private fun newMurdle(number: Int): MurdleGame {
        val list = MurdlePuzzle.Cases
        val index = (number.coerceAtLeast(1) - 1) % list.size
        val g = MurdleGame(index)
        murdle = g
        GameStore.setActive(this, GameStore.MURDLE, true)
        saveMurdle()
        return g
    }

    private fun saveMurdle() {
        murdle?.let { GameStore.saveState(this, GameStore.MURDLE, it.encode()) }
    }

    private fun updateMurdle() {
        val g = murdle ?: return
        status?.text = when {
            g.solved -> getString(R.string.game_completed)
            g.checked -> getString(R.string.game_wrong)
            else -> getString(R.string.game_murdle_help)
        }
    }

    // ---- 솔리테어 -----------------------------------------------------------

    private fun pageSolitaire() {
        val g = solitaire ?: load(GameStore.SOLITAIRE, SolitaireGame::decode)?.also { solitaire = it } ?: newSolitaire()
        body.setPadding(dp(Ui.MARGIN), dp(6), dp(Ui.MARGIN), dp(8))
        val s = statusText(); status = s
        body.addView(s, lp(MATCH, WRAP).apply { bottomMargin = dp(3) })
        solitaireView = SolitaireView(this, g) { tapSolitaire(it) }
        solitaireView?.selected = solitaireSelected
        body.addView(solitaireView!!, lp(MATCH, 0, 1f))
        body.addView(keyRow(listOf(
            getString(R.string.game_auto) to {
                if (g.autoFoundation()) {
                    recordSolitaireWin()
                    saveSolitaire(); solitaireView?.invalidate(); updateSolitaire()
                }
            },
            getString(R.string.game_undo) to { if (g.undo()) { GameStore.setActive(this, GameStore.SOLITAIRE, true); solitaireSelected = null; solitaireView?.selected = null; saveSolitaire(); solitaireView?.invalidate(); updateSolitaire() } },
            getString(R.string.game_new) to { newSolitaire(); render() },
        ), 44), lp(MATCH, WRAP).apply { topMargin = dp(4) })
        body.addView(text(getString(R.string.game_solitaire_help), 11.5f, color = Ui.GRAY, lines = 2).apply { gravity = Gravity.CENTER }, lp(MATCH, WRAP).apply { topMargin = dp(2) })
        refreshStatus = { updateSolitaire() }
        updateSolitaire()
    }

    private fun newSolitaire(): SolitaireGame {
        val g = SolitaireGame().apply { newGame() }
        solitaire = g
        solitaireSelected = null
        GameStore.setActive(this, GameStore.SOLITAIRE, true)
        GameStore.addCount(this, "solitaire_played")
        saveSolitaire()
        return g
    }

    private fun saveSolitaire() {
        solitaire?.let { GameStore.saveState(this, GameStore.SOLITAIRE, it.encode()) }
    }

    private fun tapSolitaire(hit: SolitaireView.SolitaireHit) {
        val g = solitaire ?: return
        when (hit.kind) {
            SolitaireView.Kind.STOCK -> { g.drawStock(); solitaireSelected = null }
            SolitaireView.Kind.WASTE -> {
                if (solitaireSelected != null) solitaireSelected = null
                else solitaireSelected = hit
            }
            SolitaireView.Kind.FOUNDATION -> {
                val sel = solitaireSelected
                val moved = when (sel?.kind) {
                    SolitaireView.Kind.WASTE -> g.moveWasteToFoundation()
                    SolitaireView.Kind.TABLEAU -> g.moveTableauToFoundation(sel.pile, sel.index)
                    else -> false
                }
                if (moved) solitaireSelected = null
            }
            SolitaireView.Kind.TABLEAU -> {
                val sel = solitaireSelected
                if (sel == null) {
                    if (g.faceUp[hit.pile].getOrElse(hit.index) { false }) solitaireSelected = hit
                } else if (sel.kind == SolitaireView.Kind.TABLEAU && sel.pile == hit.pile && sel.index == hit.index) {
                    solitaireSelected = null
                } else {
                    val moved = when (sel.kind) {
                        SolitaireView.Kind.WASTE -> g.moveWaste(hit.pile)
                        SolitaireView.Kind.TABLEAU -> g.moveTableau(sel.pile, sel.index, hit.pile)
                        else -> false
                    }
                    if (moved) solitaireSelected = null
                }
            }
        }
        if (g.won) recordSolitaireWin()
        saveSolitaire()
        solitaireView?.selected = solitaireSelected
        solitaireView?.invalidate()
        updateSolitaire()
    }

    private fun recordSolitaireWin() {
        val g = solitaire ?: return
        if (!g.won || !GameStore.active(this, GameStore.SOLITAIRE)) return
        GameStore.addWin(this, GameStore.SOLITAIRE)
        GameStore.setActive(this, GameStore.SOLITAIRE, false)
    }

    private fun updateSolitaire() {
        val g = solitaire ?: return
        val tag = if (g.won) "  ·  ${getString(R.string.game_completed)}" else ""
        status?.text = "${getString(R.string.game_score)} ${g.score}  ·  ${getString(R.string.game_moves)} ${g.moves}$tag"
    }

    // ---- 배틀십 -------------------------------------------------------------

    private fun pageBattleship() {
        val g = battleship ?: load(GameStore.BATTLESHIP, BattleshipGame::decode)?.also { battleship = it } ?: newBattleship()
        body.setPadding(dp(Ui.MARGIN), dp(4), dp(Ui.MARGIN), dp(6))
        val s = statusText(); status = s
        body.addView(s, lp(MATCH, WRAP).apply { bottomMargin = dp(3) })
        battleshipView = BattleshipView(this, g) { r, c -> tapBattleship(r, c) }
        battleshipView?.own = if (g.phase == BattleshipGame.Phase.PLACE) true else battleshipOwn
        body.addView(battleshipView!!, lp(MATCH, 0, 1f))
        if (g.phase == BattleshipGame.Phase.PLACE) {
            body.addView(keyRow(listOf(
                getString(R.string.game_rotate) to { g.rotate(); updateBattleship() },
                getString(R.string.game_auto_place) to { g.autoPlaceBoth(); saveBattleship(); battleshipView?.invalidate(); updateBattleship() },
                getString(R.string.game_new) to { newBattleship(); render() },
            ), 42), lp(MATCH, WRAP).apply { topMargin = dp(4) })
        } else {
            body.addView(keyRow(listOf(
                getString(R.string.game_my_fleet) to { battleshipOwn = true; battleshipView?.own = true; battleshipView?.invalidate() },
                getString(R.string.game_enemy_fleet) to { battleshipOwn = false; battleshipView?.own = false; battleshipView?.invalidate() },
                getString(R.string.game_new) to { newBattleship(); render() },
            ), 42), lp(MATCH, WRAP).apply { topMargin = dp(4) })
        }
        refreshStatus = { updateBattleship() }
        updateBattleship()
    }

    private fun newBattleship(): BattleshipGame {
        val g = BattleshipGame().apply { newGame() }
        battleship = g
        battleshipOwn = false
        GameStore.setActive(this, GameStore.BATTLESHIP, true)
        saveBattleship()
        return g
    }

    private fun saveBattleship() {
        battleship?.let { GameStore.saveState(this, GameStore.BATTLESHIP, it.encode()) }
    }

    private fun tapBattleship(row: Int, col: Int) {
        val g = battleship ?: return
        if (g.phase == BattleshipGame.Phase.PLACE) {
            if (g.placePlayer(row, col)) {
                if (g.phase == BattleshipGame.Phase.PLAY) { battleshipOwn = false; battleshipView?.own = false }
                saveBattleship()
                battleshipView?.invalidate()
                updateBattleship()
            }
            return
        }
        if (g.phase != BattleshipGame.Phase.PLAY || battleshipOwn) return
        if (g.fire(row, col) == BattleshipGame.Shot.INVALID) return
        if (g.phase == BattleshipGame.Phase.OVER) recordWinLoss(GameStore.BATTLESHIP, g.winner == BattleshipGame.HUMAN)
        saveBattleship()
        battleshipView?.invalidate()
        updateBattleship()
    }

    private fun updateBattleship() {
        val g = battleship ?: return
        if (g.phase == BattleshipGame.Phase.PLACE) {
            val size = g.fleet.getOrNull(g.placement) ?: 0
            status?.text = getString(R.string.game_place_help, size) + "  ·  ${getString(R.string.game_rotate)}: ${if (g.horizontal) "↔" else "↕"}"
            return
        }
        val remaining = g.aiShips.sumOf { s -> s.cells.count { !g.playerShots[it] } }
        val turn = when {
            g.phase == BattleshipGame.Phase.OVER && g.winner == BattleshipGame.HUMAN -> getString(R.string.game_you_won)
            g.phase == BattleshipGame.Phase.OVER -> getString(R.string.game_ai_won)
            else -> getString(R.string.game_your_turn)
        }
        status?.text = "$turn  ·  ${getString(R.string.game_battleship_remaining)} $remaining  ·  ${GameStore.wins(this, GameStore.BATTLESHIP)}${getString(R.string.game_wins_short)} " +
            "${GameStore.losses(this, GameStore.BATTLESHIP)}${getString(R.string.game_losses_short)}"
    }

    // ---- Wordle -------------------------------------------------------------

    private fun pageWordle() {
        val g = wordle ?: loadWordle()?.also { wordle = it } ?: newWordle()
        body.setPadding(dp(Ui.MARGIN), dp(4), dp(Ui.MARGIN), dp(6))
        val s = statusText(); status = s
        body.addView(s, lp(MATCH, WRAP).apply { bottomMargin = dp(3) })
        wordleView = WordleView(this, g)
        body.addView(wordleView!!, lp(MATCH, 0, 1f))
        wordleKeyboard = WordleKeyboardView(this, g) { key -> wordleKey(key) }
        body.addView(wordleKeyboard!!, lp(MATCH, dp(150)).apply { topMargin = dp(4) })
        body.addView(GameKey(this, getString(R.string.game_new), true) { newWordle(); render() }, lp(MATCH, dp(40)).apply { topMargin = dp(4) })
        refreshStatus = { updateWordle() }
        updateWordle()
    }

    private fun newWordle(): WordleGame {
        val answers = WordleWords.answers(this)
        val allowed = WordleWords.allowed(this)
        val g = WordleGame(WordleGame.randomAnswer(answers), allowed)
        wordle = g
        GameStore.setActive(this, GameStore.WORDLE, true)
        saveWordle()
        return g
    }

    private fun loadWordle(): WordleGame? = GameStore.state(this, GameStore.WORDLE)?.let { WordleGame.decode(it, WordleWords.allowed(this)) }

    private fun saveWordle() {
        wordle?.let { GameStore.saveState(this, GameStore.WORDLE, it.encode()) }
    }

    private fun wordleKey(key: String) {
        val g = wordle ?: return
        when (key) {
            "ENTER" -> {
                when (g.submit()) {
                    WordleGame.Submit.TOO_SHORT -> status?.text = getString(R.string.game_wordle_short)
                    WordleGame.Submit.NOT_WORD -> status?.text = getString(R.string.game_wordle_not_word)
                    WordleGame.Submit.FINISHED -> Unit
                    WordleGame.Submit.OK -> {
                        if (g.over) {
                            GameStore.addCount(this, "wordle_played")
                            if (g.won) {
                                GameStore.addCount(this, "wordle_won")
                                GameStore.addCount(this, "wordle_streak")
                            } else {
                                GameStore.setCount(this, "wordle_streak", 0)
                            }
                            GameStore.setActive(this, GameStore.WORDLE, false)
                        }
                        saveWordle()
                    }
                }
            }
            "BACK" -> { g.backspace(); saveWordle() }
            else -> { g.type(key[0]); saveWordle() }
        }
        wordleView?.invalidate()
        wordleKeyboard?.invalidate()
        updateWordle()
    }

    private fun updateWordle() {
        val g = wordle ?: return
        val played = GameStore.count(this, "wordle_played")
        val wins = GameStore.count(this, "wordle_won")
        val streak = GameStore.count(this, "wordle_streak")
        val tag = when {
            g.won -> "  ·  ${getString(R.string.game_completed)}"
            g.over -> "  ·  ${getString(R.string.game_wordle_answer)} ${g.answer.uppercase()}"
            else -> ""
        }
        status?.text = "${g.guesses.size}/6  ·  $wins/$played  ·  ${getString(R.string.game_wordle_streak)} $streak$tag"
    }

    // ---- 공통 ---------------------------------------------------------------

    private fun statusText(): TextView = text("", 14f, Ui.medium).apply { gravity = Gravity.CENTER }

    private fun keyRow(keys: List<Pair<String, () -> Unit>>, heightDp: Int): View = hbox().apply {
        keys.forEachIndexed { i, (label, action) ->
            addView(GameKey(this@GamesActivity, label, false) { action() }, lp(0, dp(heightDp), 1f).apply { if (i > 0) marginStart = dp(4) })
        }
    }

    private fun difficultyRow(keys: List<Pair<String, () -> Unit>>, selected: Int): View = hbox().apply {
        keys.forEachIndexed { i, (label, action) ->
            addView(GameKey(this@GamesActivity, label, i == selected) { action() }, lp(0, dp(40), 1f).apply { if (i > 0) marginStart = dp(4) })
        }
    }

    private fun recordWinLoss(id: String, won: Boolean) {
        if (!GameStore.active(this, id)) return
        if (won) GameStore.addWin(this, id) else GameStore.addLoss(this, id)
        GameStore.setActive(this, id, false)
    }

    private fun timeText(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(total / 60, total % 60)
    }

    private fun startTicker() {
        if (page != Page.MINES && page != Page.SUDOKU && page != Page.NONOGRAM) return
        if (ticker != null) return
        val r = object : Runnable {
            override fun run() {
                refreshStatus?.invoke()
                handler.postDelayed(this, 1000)
            }
        }
        ticker = r
        handler.postDelayed(r, 1000)
    }

    private fun stopTicker() {
        ticker?.let { handler.removeCallbacks(it) }
        ticker = null
    }

    private fun saveCurrent() {
        when (page) {
            Page.G2048 -> save2048()
            Page.MINES -> saveMines()
            Page.SUDOKU -> saveSudoku()
            Page.NONOGRAM -> saveNonogram()
            Page.CONNECT4 -> saveConnect4()
            Page.CHECKERS -> saveCheckers()
            Page.CHESS -> saveChess()
            Page.OTHELLO -> saveOthello()
            Page.HEARTS -> saveHearts()
            Page.INTERCEPT -> saveIntercept()
            Page.CARNIVORE -> saveCarnivore()
            Page.MURDLE -> saveMurdle()
            Page.SOLITAIRE -> saveSolitaire()
            Page.BATTLESHIP -> saveBattleship()
            Page.WORDLE -> saveWordle()
            Page.HUB -> Unit
        }
    }

    private fun <T> load(id: String, decode: (String) -> T?): T? = GameStore.state(this, id)?.let(decode)

    companion object {
        fun intent(context: Context) = Intent(context, GamesActivity::class.java)
    }
}
