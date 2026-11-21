package com.woody.booklauncher.games

import kotlin.random.Random

/**
 * 클론다이크 솔itaire. 카드 id 0~51, 무늬 = id/13(♠♥♦♣), 숫자 = id%13(A=0 … K=12).
 * 재고는 한 장씩 뒤집고, 비면 버린 패를 뒤집어 다시 쌓는다. 되돌리기 한 수.
 */
internal class SolitaireGame {
    val tableau = Array(7) { mutableListOf<Int>() }
    val faceUp = Array(7) { mutableListOf<Boolean>() }
    val foundations = IntArray(4) { -1 }
    val stock = ArrayDeque<Int>()
    val waste = ArrayDeque<Int>()
    var score = 0
        private set
    var moves = 0
        private set
    var won = false
        private set

    private val history = ArrayDeque<Snapshot>()

    private data class Snapshot(
        val tableau: Array<MutableList<Int>>,
        val faceUp: Array<MutableList<Boolean>>,
        val foundations: IntArray,
        val stock: ArrayDeque<Int>,
        val waste: ArrayDeque<Int>,
        val score: Int,
        val moves: Int,
        val won: Boolean,
    )

    val canUndo: Boolean get() = history.isNotEmpty()

    fun newGame(rng: Random = Random.Default) {
        val deck = (0 until 52).shuffled(rng)
        tableau.forEach { it.clear() }
        faceUp.forEach { it.clear() }
        stock.clear()
        waste.clear()
        foundations.fill(-1)
        var k = 0
        for (p in 0 until 7) {
            repeat(p + 1) { i ->
                tableau[p] += deck[k++]
                faceUp[p] += i == p
            }
        }
        while (k < deck.size) stock.addLast(deck[k++])
        score = 0
        moves = 0
        won = false
        history.clear()
    }

    fun drawStock(): Boolean {
        if (won) return false
        push()
        if (stock.isEmpty()) {
            if (waste.isEmpty()) { popNoChange(); return false }
            while (waste.isNotEmpty()) stock.addLast(waste.removeLast())
        } else {
            waste.addLast(stock.removeLast())
        }
        moves++
        return true
    }

    fun canPlaceOnTableau(card: Int, pile: Int): Boolean {
        val top = tableau[pile].lastOrNull()
        return if (top == null) rank(card) == 12 else rank(top) == rank(card) + 1 && isRed(top) != isRed(card)
    }

    fun canPlaceOnFoundation(card: Int): Boolean {
        val top = foundations[suit(card)]
        return if (top < 0) rank(card) == 0 else suit(top) == suit(card) && rank(top) == rank(card) - 1
    }

    /** [fromPile] 의 [fromIndex] 부터 끝까지 [toPile] 로 옮긴다. */
    fun moveTableau(fromPile: Int, fromIndex: Int, toPile: Int): Boolean {
        if (won || fromPile == toPile) return false
        val cards = tableau[fromPile]
        if (fromIndex !in cards.indices || !faceUp[fromPile][fromIndex]) return false
        if (!faceUp[fromPile].drop(fromIndex).all { it }) return false
        val seq = cards.drop(fromIndex)
        if (!canPlaceOnTableau(seq.first(), toPile)) return false
        // 옮기는 동안의 카드가 모두 내림차순 교대색인지
        for (i in 0 until seq.size - 1) {
            if (rank(seq[i]) != rank(seq[i + 1]) + 1 || isRed(seq[i]) == isRed(seq[i + 1])) return false
        }
        push()
        repeat(seq.size) {
            tableau[fromPile].removeAt(tableau[fromPile].lastIndex)
            faceUp[fromPile].removeAt(faceUp[fromPile].lastIndex)
        }
        seq.forEach { tableau[toPile] += it; faceUp[toPile] += true }
        if (tableau[fromPile].isNotEmpty() && !faceUp[fromPile].last()) faceUp[fromPile][faceUp[fromPile].lastIndex] = true
        moves++
        checkWin()
        return true
    }

    fun moveWaste(toPile: Int): Boolean {
        val card = waste.lastOrNull() ?: return false
        if (won || !canPlaceOnTableau(card, toPile)) return false
        push()
        waste.removeLast()
        tableau[toPile] += card
        faceUp[toPile] += true
        moves++
        return true
    }

    fun moveTableauToFoundation(pile: Int, index: Int): Boolean {
        val cards = tableau[pile]
        if (won || index != cards.lastIndex || !faceUp[pile][index]) return false
        val card = cards[index]
        if (!canPlaceOnFoundation(card)) return false
        push()
        tableau[pile].removeAt(index)
        faceUp[pile].removeAt(index)
        foundations[suit(card)] = card
        score += 10
        if (tableau[pile].isNotEmpty() && !faceUp[pile].last()) faceUp[pile][faceUp[pile].lastIndex] = true
        moves++
        checkWin()
        return true
    }

    fun moveWasteToFoundation(): Boolean {
        val card = waste.lastOrNull() ?: return false
        if (won || !canPlaceOnFoundation(card)) return false
        push()
        waste.removeLast()
        foundations[suit(card)] = card
        score += 10
        moves++
        checkWin()
        return true
    }

    /** 버린 패와 각 줄 맨 위에서 올릴 수 있는 것을 재단으로 올린다. */
    fun autoFoundation(): Boolean {
        var any = false
        var guard = 0
        while (guard++ < 20) {
            if (moveWasteToFoundation()) { any = true; continue }
            var moved = false
            for (p in 0 until 7) {
                if (tableau[p].isNotEmpty() && moveTableauToFoundation(p, tableau[p].lastIndex)) { moved = true; any = true; break }
            }
            if (!moved) break
        }
        return any
    }

    fun undo(): Boolean {
        val s = history.removeLastOrNull() ?: return false
        for (i in 0 until 7) {
            tableau[i] = s.tableau[i].toMutableList()
            faceUp[i] = s.faceUp[i].toMutableList()
        }
        s.foundations.copyInto(foundations)
        stock.clear(); stock.addAll(s.stock)
        waste.clear(); waste.addAll(s.waste)
        score = s.score
        moves = s.moves
        won = s.won
        return true
    }

    private fun checkWin() {
        if (foundations.all { it >= 0 && rank(it) == 12 }) won = true
    }

    private fun push() {
        history.addLast(
            Snapshot(
                Array(7) { tableau[it].toMutableList() },
                Array(7) { faceUp[it].toMutableList() },
                foundations.copyOf(),
                ArrayDeque(stock),
                ArrayDeque(waste),
                score, moves, won,
            )
        )
        while (history.size > 40) history.removeFirst()
    }

    private fun popNoChange() { if (history.isNotEmpty()) history.removeLast() }

    fun encode(): String {
        fun pile(a: List<Int>, b: List<Boolean>) = a.indices.joinToString(",") { "${a[it]}:${if (b[it]) 1 else 0}" }
        return listOf(
            (0 until 7).joinToString(";") { pile(tableau[it], faceUp[it]) },
            foundations.joinToString(","),
            stock.joinToString(","),
            waste.joinToString(","),
            score, moves, if (won) 1 else 0,
        ).joinToString("|")
    }

    companion object {
        fun rank(card: Int) = card % 13
        fun suit(card: Int) = card / 13
        fun isRed(card: Int) = suit(card) == 1 || suit(card) == 2

        fun decode(s: String): SolitaireGame? = runCatching {
            val p = s.split('|')
            SolitaireGame().apply {
                p[0].split(';').forEachIndexed { i, pile ->
                    if (pile.isEmpty()) return@forEachIndexed
                    pile.split(',').forEach { line ->
                        val q = line.split(':')
                        tableau[i] += q[0].toInt()
                        faceUp[i] += q[1] == "1"
                    }
                }
                p[1].split(',').forEachIndexed { i, v -> foundations[i] = v.toInt() }
                if (p[2].isNotEmpty()) p[2].split(',').forEach { stock.addLast(it.toInt()) }
                if (p[3].isNotEmpty()) p[3].split(',').forEach { waste.addLast(it.toInt()) }
                score = p[4].toInt()
                moves = p[5].toInt()
                won = p[6] == "1"
            }
        }.getOrNull()
    }
}
