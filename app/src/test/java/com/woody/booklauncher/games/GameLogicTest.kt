package com.woody.booklauncher.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** 게임 규칙(화면 없이) 시험: 합치기·되돌리기, 지뢰 첫 칸, 스도쿠 메모, 노노그램, 사목, 체커, 솔리테어, 배틀십, 워들. */
class GameLogicTest {
    @Test
    fun `2048 합치기와 되돌리기`() {
        val g = Game2048()
        g.cells = intArrayOf(1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        assertTrue(g.move(3, Random(1)))
        assertEquals(2, g.cells[0])
        assertEquals(4, g.score)
        assertTrue(g.undo())
        assertEquals(1, g.cells[0])
        assertEquals(0, g.score)
    }

    @Test
    fun `2048 움직이지 않으면 기록하지 않는다`() {
        val g = Game2048()
        g.cells = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)
        assertFalse(g.move(3, Random(1)))
        assertFalse(g.canUndo)
    }

    @Test
    fun `지뢰찾기 첫 칸은 안전하고 깃발이 바뀐다`() {
        val g = Minesweeper(9, 10)
        g.newGame()
        assertTrue(g.reveal(0))
        assertTrue(g.started)
        assertFalse(g.lost)
        assertFalse(g.mine[0])
        for (n in g.neighbours(0)) assertFalse(g.mine[n])
        val hidden = g.revealed.indexOfFirst { !it }
        assertTrue(hidden >= 0)
        assertTrue(g.toggleFlag(hidden))
        assertEquals(1, g.flags)
        assertTrue(g.toggleFlag(hidden))
        assertEquals(0, g.flags)
    }

    @Test
    fun `지뢰찾기 지뢰 아닌 칸을 모두 열면 이긴다`() {
        val g = Minesweeper(5, 3)
        g.newGame()
        g.reveal(0)
        for (i in g.mine.indices) if (!g.mine[i]) g.reveal(i)
        assertTrue(g.won)
        assertFalse(g.lost)
    }

    @Test
    fun `스도쿠 메모와 되돌리기, 힌트`() {
        val g = SudokuGame()
        g.newGame(0, Random(2))
        val empty = g.cells.indexOfFirst { it == 0 }
        assertTrue(empty >= 0)
        g.selected = empty
        assertTrue(g.enter(1, true))
        assertTrue(g.notes[empty] != 0)
        assertTrue(g.undo())
        assertEquals(0, g.notes[empty])
        assertTrue(g.hint())
        assertEquals(g.solution[empty], g.cells[empty])
    }

    @Test
    fun `노노그램 힌트와 완성`() {
        val g = NonogramGame(0)
        g.newGame(0)
        assertEquals(10, g.size)
        assertTrue(g.puzzle.rowClues.isNotEmpty())
        for (i in g.marks.indices) g.set(i, if (g.puzzle.filled[i]) 1 else 2)
        assertTrue(g.solved)
        assertTrue(g.mistakes >= 0)
    }

    @Test
    fun `노노그램 힌트로 끝까지 채운다`() {
        val g = NonogramGame(0)
        g.newGame(0)
        var guard = 0
        while (!g.solved && guard++ < 200) assertTrue(g.hint() >= 0)
        assertTrue(g.solved)
    }

    @Test
    fun `사목 네 개를 이으면 끝난다`() {
        val g = ConnectFourGame()
        g.newGame()
        repeat(3) {
            g.drop(0, ConnectFourGame.HUMAN)
            g.drop(1, ConnectFourGame.AI)
        }
        g.drop(0, ConnectFourGame.HUMAN)
        assertEquals(ConnectFourGame.HUMAN, g.winner)
        assertTrue(g.over)
        assertEquals(4, g.winningCells().size)
    }

    @Test
    fun `사목 기기는 둘 수 있는 열만 고른다`() {
        val g = ConnectFourGame()
        g.newGame()
        repeat(6) { g.drop(3, if (it % 2 == 0) ConnectFourGame.HUMAN else ConnectFourGame.AI) }
        assertFalse(g.canDrop(3))
        val col = g.aiMove(1, Random(1))
        assertTrue(col in 0 until g.cols)
        assertTrue(g.canDrop(col))
    }

    @Test
    fun `체커는 잡을 수 있으면 잡는 수만 둔다`() {
        // 사람 말 (5,2), 기기 말 (4,3), 빈 칸 (3,4) — 잡기 하나만 있어야 한다.
        val board = CharArray(64) { '0' }
        board[5 * 8 + 2] = '1'
        board[4 * 8 + 3] = '3'
        val g = CheckersGame.decode(board.concatToString() + "|0|-1|0|0")!!
        val moves = g.movesFor(CheckersGame.HUMAN)
        assertEquals(1, moves.size)
        assertTrue(moves[0].captured >= 0)
        assertTrue(g.apply(moves[0]))
        assertEquals(0, g.board[4 * 8 + 3])
        assertEquals(CheckersGame.HUMAN_MAN, g.board[3 * 8 + 4])
    }

    @Test
    fun `체커 연달아 잡기`() {
        val board = CharArray(64) { '0' }
        board[5 * 8 + 2] = '1'
        board[4 * 8 + 3] = '3'
        board[2 * 8 + 3] = '3'
        val g = CheckersGame.decode(board.concatToString() + "|0|-1|0|0")!!
        val first = g.movesFor(CheckersGame.HUMAN).first { it.to == 3 * 8 + 4 }
        assertTrue(g.apply(first))
        assertTrue(g.chain)
        val second = g.movesFrom(g.selected).firstOrNull { it.captured >= 0 }
        assertNotNull(second)
        assertTrue(g.apply(second!!))
        assertEquals(0, g.board[2 * 8 + 3])
        assertEquals(CheckersGame.HUMAN_MAN, g.board[1 * 8 + 2])
    }

    @Test
    fun `체커 한 판 되돌리기`() {
        val g = CheckersGame()
        g.newGame()
        val move = g.movesFor(CheckersGame.HUMAN).first()
        assertTrue(g.select(move.from))
        assertTrue(g.apply(move))
        assertTrue(g.aiMove(1, Random(2)) != null)
        assertTrue(g.undoRound())
        assertEquals(CheckersGame.HUMAN, g.turn)
    }

    @Test
    fun `솔리테어 나눠 주기와 재고 넘기기`() {
        val g = SolitaireGame()
        g.newGame(Random(3))
        assertEquals(7, g.tableau.size)
        assertEquals(28, g.tableau.sumOf { it.size })
        assertEquals(24, g.stock.size)
        val first = g.waste.size
        assertTrue(g.drawStock())
        assertEquals(first + 1, g.waste.size)
    }

    @Test
    fun `솔리테어 빈 줄에는 킹만`() {
        val g = SolitaireGame()
        g.newGame(Random(4))
        g.tableau[0].clear()
        g.faceUp[0].clear()
        val king = (0 until 52).first { SolitaireGame.rank(it) == 12 }
        val other = (0 until 52).first { SolitaireGame.rank(it) != 12 }
        assertTrue(g.canPlaceOnTableau(king, 0))
        assertFalse(g.canPlaceOnTableau(other, 0))
    }

    @Test
    fun `배틀십 배치와 한쪽 함대 격침`() {
        val g = BattleshipGame()
        g.newGame()
        g.autoPlaceBoth(Random(5))
        assertEquals(5, g.playerShips.size)
        assertEquals(5, g.aiShips.size)
        assertEquals(BattleshipGame.Phase.PLAY, g.phase)
        for (cell in g.aiShips.flatMap { it.cells }) {
            val r = cell / 10
            val c = cell % 10
            g.fire(r, c)
        }
        assertEquals(BattleshipGame.Phase.OVER, g.phase)
        assertEquals(BattleshipGame.HUMAN, g.winner)
    }

    @Test
    fun `배틀십 자동 배치는 늘 배 다섯을 놓는다`() {
        repeat(30) { seed ->
            val g = BattleshipGame()
            g.newGame()
            g.autoPlaceBoth(Random(seed))
            assertEquals(5, g.playerShips.size)
            assertEquals(5, g.aiShips.size)
            assertEquals(17, g.playerShips.sumOf { it.size })
            assertEquals(17, g.aiShips.sumOf { it.size })
        }
    }

    @Test
    fun `워들 중복 글자 점수와 승리`() {
        val allowed = setOf("apple", "happy", "allee")
        val g = WordleGame("apple", allowed)
        val s = g.score("allee")
        assertEquals(2, s[0]) // a
        assertEquals(1, s[1]) // l (apple has one l left)
        assertEquals(0, s[2]) // second l
        assertEquals(0, s[3]) // e was already used by the final correct e
        assertEquals(2, s[4]) // final e
        "apple".forEach { g.type(it) }
        assertEquals(WordleGame.Submit.OK, g.submit())
        assertTrue(g.won)
        assertTrue(g.over)
    }

    @Test
    fun `워들 여섯 번 틀리면 끝난다`() {
        val allowed = setOf("apple", "happy", "allee", "brick", "crane", "drove", "flute")
        val g = WordleGame("apple", allowed)
        listOf("happy", "allee", "brick", "crane", "drove", "flute").forEach { word ->
            word.forEach { c -> g.type(c) }
            assertEquals(WordleGame.Submit.OK, g.submit())
        }
        assertTrue(g.over)
        assertFalse(g.won)
        assertEquals(6, g.guesses.size)
    }

    @Test
    fun `게임 상태 저장 문자열이 왕복한다`() {
        val g = Game2048()
        g.newGame(Random(6))
        val copy = Game2048.decode(g.encode())
        assertNotNull(copy)
        assertEquals(g.cells.toList(), copy!!.cells.toList())
        assertEquals(g.score, copy.score)
    }

    @Test
    fun `오셀로는 네 돌에서 시작하고 저장한 판을 다시 읽는다`() {
        val g = OthelloGame()
        g.newGame()
        assertEquals(2, g.score().first)
        assertEquals(2, g.score().second)
        assertTrue(g.legalMoves().isNotEmpty())
        assertTrue(g.play(g.legalMoves().first()))
        val copy = OthelloGame.decode(g.encode())
        assertNotNull(copy)
        assertEquals(g.board.toList(), copy!!.board.toList())
        assertEquals(g.turn, copy.turn)
    }

    @Test
    fun `하트는 세 장을 넘기고 2클럽으로 시작한다`() {
        val g = HeartsGame()
        g.newGame(Random(4))
        assertEquals(52, g.hands.sumOf { it.size })
        g.humanHand().take(3).forEach { assertTrue(g.selectPass(it)) }
        assertEquals(3, g.passSelected().size)
        assertTrue(g.confirmPass(1, Random(4)))
        assertEquals(HeartsGame.Phase.PLAY, g.phase)
        assertEquals(52, g.hands.sumOf { it.size })
        val leader = g.turn
        assertTrue(HeartsGame.TWO_CLUBS in g.hands[leader])
        assertEquals(listOf(HeartsGame.TWO_CLUBS), g.legalCards(leader))
        assertEquals(13, HeartsGame.cardPoints(11)) // Q♠
        assertEquals(0, HeartsGame.cardPoints(10)) // J♠
        assertTrue(HeartsGame.isPenalty(11))
        assertFalse(HeartsGame.isPenalty(10))
        val copy = HeartsGame.decode(g.encode())
        assertNotNull(copy)
        assertEquals(g.hands.sumOf { it.size }, copy!!.hands.sumOf { it.size })
    }

    @Test
    fun `인터셉트는 선택으로 끝까지 가고 처음으로 되돌린다`() {
        val story = InterceptStory()
        var guard = 0
        while (!story.over && guard++ < 50) assertTrue(story.choose(0))
        assertTrue(story.over)
        assertTrue(story.ending.isNotEmpty())
        val copy = InterceptStory.decode(story.encode())
        assertNotNull(copy)
        assertEquals(story.node, copy!!.node)
        story.restart()
        assertFalse(story.over)
        assertEquals("start", story.node)
    }

    @Test
    fun `카니보어 열두 사건은 모두 답이 하나다`() {
        assertTrue(CarnivoreCase.Cases.all.isNotEmpty())
        for (case in CarnivoreCase.Cases.all) {
            assertEquals(1, case.solutions().size)
            assertEquals(case.answer, case.solutions().first())
            assertTrue(case.culpritCount in 1..3)
            assertTrue(case.liars in 0..case.suspects.size)
            case.suspects.forEach { assertTrue(it.statement.text(case.suspects, false).isNotEmpty()) }
        }
        val game = CarnivoreGame(0)
        assertEquals(2, game.lives)
        game.marks.indices.forEach { game.marks[it] = 0 }
        game.case.answer.forEach { game.marks[it] = 1 }
        assertTrue(game.accuse())
        assertTrue(game.solved)
    }

    @Test
    fun `머들 문제는 단서를 모두 만족하고 답이 하나다`() {
        val puzzles = MurdlePuzzle.Cases
        assertTrue(puzzles.size >= 6)
        for (p in puzzles) {
            assertTrue(p.matches(p.weaponAnswer, p.roomAnswer))
            assertTrue(p.clues.size >= 3)
            p.clues.forEach { assertTrue(it.text(p.suspects, p.weapons, p.rooms, false).isNotEmpty()) }
        }
        val game = MurdleGame(0)
        assertFalse(game.check())
        game.weapons = game.puzzle.weaponAnswer.copyOf()
        game.rooms = game.puzzle.roomAnswer.copyOf()
        assertTrue(game.check())
        assertTrue(game.solved)
        val copy = MurdleGame.decode(game.encode())
        assertNotNull(copy)
        assertEquals(game.solved, copy!!.solved)
    }
}
