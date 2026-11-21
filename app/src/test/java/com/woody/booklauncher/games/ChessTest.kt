package com.woody.booklauncher.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChessTest {
    @Test
    fun `initial position has twenty moves and the known perft counts`() {
        val g = ChessGame()
        assertEquals(20, g.legalMoves().size)
        assertEquals(20L, g.perft(1))
        assertEquals(400L, g.perft(2))
        assertEquals(8902L, g.perft(3))
    }

    @Test
    fun `king side castling moves the rook too`() {
        val g = ChessGame()
        val b = IntArray(64)
        b[60] = ChessGame.KING
        b[63] = ChessGame.ROOK
        b[4] = -ChessGame.KING
        g.loadPosition(b, ChessGame.WHITE, 1)
        assertTrue(g.play(60, 62))
        assertEquals(ChessGame.KING, g.pieceAt(62))
        assertEquals(ChessGame.ROOK, g.pieceAt(61))
        assertEquals(0, g.pieceAt(63))
        assertEquals(0, g.pieceAt(60))
    }

    @Test
    fun `en passant removes the passed pawn`() {
        val g = ChessGame()
        val b = IntArray(64)
        b[28] = ChessGame.PAWN   // e5
        b[27] = -ChessGame.PAWN  // d5
        b[60] = ChessGame.KING
        b[4] = -ChessGame.KING
        g.loadPosition(b, ChessGame.WHITE, 0, 19) // d6
        assertTrue(g.play(28, 19))
        assertEquals(ChessGame.PAWN, g.pieceAt(19))
        assertEquals(0, g.pieceAt(28))
        assertEquals(0, g.pieceAt(27))
    }

    @Test
    fun `pawn promotion can choose a rook`() {
        val g = ChessGame()
        val b = IntArray(64)
        b[8] = ChessGame.PAWN // a7
        b[60] = ChessGame.KING
        b[4] = -ChessGame.KING
        g.loadPosition(b, ChessGame.WHITE, 0)
        assertTrue(g.play(8, 0, ChessGame.ROOK))
        assertEquals(ChessGame.ROOK, g.pieceAt(0))
    }

    @Test
    fun `fools mate is checkmate and a black win`() {
        val g = ChessGame(human = ChessGame.WHITE)
        assertTrue(g.play(53, 45)) // f2-f3
        assertTrue(g.play(12, 28)) // e7-e5
        assertTrue(g.play(54, 38)) // g2-g4
        assertTrue(g.play(3, 39))  // Qd8-h4
        assertTrue(g.over)
        assertEquals(ChessGame.Result.CHECKMATE, g.result)
        assertEquals(ChessGame.BLACK, g.winner)
    }

    @Test
    fun `undo takes back a human move and the answer`() {
        val g = ChessGame(ChessGame.WHITE)
        assertTrue(g.play(52, 36)) // e2-e4
        val answer = g.aiMove(1, Random(1))!!
        assertTrue(g.play(answer.from, answer.to, answer.promotion))
        assertTrue(g.undo())
        assertEquals(ChessGame.WHITE, g.turn)
        assertEquals(ChessGame.WHITE, g.pieceAt(52))
        assertEquals(0, g.pieceAt(36))
    }

    @Test
    fun `computer returns a legal move and saved games round trip`() {
        val g = ChessGame(ChessGame.BLACK)
        val move = g.aiMove(2, Random(2))
        assertNotNull(move)
        assertTrue(g.legalMoves().any { it == move })
        val copy = ChessGame.decode(g.encode())
        assertNotNull(copy)
        assertEquals(g.board.toList(), copy!!.board.toList())
        assertEquals(g.human, copy.human)
        assertEquals(g.turn, copy.turn)
    }

    @Test
    fun `stalemate is not a win`() {
        val g = ChessGame()
        val b = IntArray(64)
        b[0] = -ChessGame.KING  // a8
        b[17] = ChessGame.QUEEN // b6
        b[26] = ChessGame.KING  // c5
        g.loadPosition(b, ChessGame.BLACK, 0)
        assertFalse(g.inCheck(ChessGame.BLACK))
        assertEquals(0, g.legalMoves().size)
    }
}
