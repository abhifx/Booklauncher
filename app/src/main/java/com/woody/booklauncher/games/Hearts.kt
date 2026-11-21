package com.woody.booklauncher.games

import kotlin.random.Random

/**
 * Hearts(하트). 4명, 13장씩. 하트 1점, 스페이드 퀸 13점, 가장 낮은 점수가 이긴다.
 * 2♣를 가진 사람이 첫 트릭을 열고, 같은 무늬가 있으면 따라야 한다.
 * 하트는 한 번 나온 뒤에만 열 수 있고, 첫 트릭에는 점수 카드를 내지 않는다(낼 수밖에 없을 때만).
 */
internal class HeartsGame {
    enum class Phase { PASS, PLAY, OVER }
    enum class Pass(val ko: String, val en: String) { LEFT("왼쪽", "left"), RIGHT("오른쪽", "right"), ACROSS("건너", "across"), NONE("없음", "none") }

    var hands = Array(4) { mutableListOf<Int>() }
        private set
    var scores = IntArray(4)
        private set
    private var handPoints = IntArray(4)
    var phase = Phase.PASS
        private set
    var pass = Pass.LEFT
        private set
    var handNumber = 1
        private set
    var trick = mutableListOf<Pair<Int, Int>>()
        private set
    var turn = 0
        private set
    var leader = 0
        private set
    var lastWinner = -1
        private set
    var lastPoints = 0
        private set
    var heartsBroken = false
        private set
    private var firstTrick = true
    private val humanPass = mutableListOf<Int>()
    private val passSets = Array(4) { mutableListOf<Int>() }
    var messageKo = ""
    var messageEn = ""

    fun newGame(rng: Random = Random.Default) {
        scores = IntArray(4)
        handNumber = 1
        pass = Pass.LEFT
        deal(rng)
    }

    private fun deal(rng: Random) {
        val deck = (0 until 52).shuffled(rng)
        hands.forEach { it.clear() }
        deck.forEachIndexed { i, c -> hands[i % 4] += c }
        hands.forEach { it.sortWith(CARD_ORDER) }
        scores = scores.copyOf()
        handPoints = IntArray(4)
        phase = if (pass == Pass.NONE) Phase.PLAY else Phase.PASS
        passSets.forEach { it.clear() }
        humanPass.clear()
        trick.clear()
        turn = 0
        leader = hands.indexOfFirst { 40 in it }.coerceAtLeast(0) // 2♣ = club(3)*13 + 1
        turn = leader
        heartsBroken = false
        firstTrick = true
        lastWinner = -1
        lastPoints = 0
        messageKo = ""
        messageEn = ""
    }

    fun humanHand(): List<Int> = hands[0]

    fun selectPass(card: Int): Boolean {
        if (phase != Phase.PASS || card !in hands[0]) return false
        if (card in humanPass) humanPass.remove(card) else if (humanPass.size < 3) humanPass += card else return false
        return true
    }

    fun passSelected(): List<Int> = humanPass.toList()

    /** 사람이 고른 세 장과 기기의 세 장을 동시에 바꾼다. */
    fun confirmPass(level: Int, rng: Random = Random.Default): Boolean {
        if (phase != Phase.PASS || humanPass.size != 3) return false
        passSets[0].addAll(humanPass)
        for (p in 1 until 4) passSets[p].addAll(aiPass(p, level, rng))
        val targets = IntArray(4) { target(it) }
        val moved = Array(4) { mutableListOf<Int>() }
        for (p in 0 until 4) for (c in passSets[p]) { hands[p].remove(c); moved[targets[p]] += c }
        for (p in 0 until 4) hands[p] += moved[p]
        hands.forEach { it.sortWith(CARD_ORDER) }
        humanPass.clear()
        phase = Phase.PLAY
        leader = hands.indexOfFirst { TWO_CLUBS in it }.coerceAtLeast(0)
        turn = leader
        firstTrick = true
        return true
    }

    private fun target(player: Int): Int = when (pass) {
        Pass.LEFT -> (player + 1) % 4
        Pass.RIGHT -> (player + 3) % 4
        Pass.ACROSS -> (player + 2) % 4
        Pass.NONE -> player
    }

    fun isHumanTurn(): Boolean = phase == Phase.PLAY && turn == 0

    fun legalCards(player: Int = turn): List<Int> {
        val hand = hands[player]
        if (hand.isEmpty()) return emptyList()
        if (trick.isEmpty()) {
            if (firstTrick && TWO_CLUBS in hand) return listOf(TWO_CLUBS)
            var cards: List<Int> = hand
            if (firstTrick) {
                val safe = cards.filter { !isPenalty(it) }
                if (safe.isNotEmpty()) cards = safe
            }
            if (!heartsBroken) {
                val noHeart = cards.filter { suit(it) != HEARTS }
                if (noHeart.isNotEmpty()) cards = noHeart
            }
            return cards.sortedWith(CARD_ORDER)
        }
        val led = suit(trick.first().second)
        val follow = hand.filter { suit(it) == led }
        return (if (follow.isNotEmpty()) follow else hand).sortedWith(CARD_ORDER)
    }

    fun play(player: Int, card: Int): Boolean {
        if (phase != Phase.PLAY || player != turn || card !in legalCards(player)) return false
        hands[player].remove(card)
        trick += player to card
        if (suit(card) == HEARTS) heartsBroken = true
        if (trick.size == 4) {
            val led = suit(trick.first().second)
            val winner = trick.filter { suit(it.second) == led }.maxByOrNull { trickRank(it.second) }!!.first
            val points = trick.sumOf { cardPoints(it.second) }
            handPoints[winner] += points
            lastWinner = winner
            lastPoints = points
            trick.clear()
            firstTrick = false
            if (hands.all { it.isEmpty() }) finishHand() else {
                leader = winner
                turn = winner
            }
        } else turn = (turn + 1) % 4
        return true
    }

    private fun finishHand() {
        val shooter = handPoints.indexOfFirst { it == 26 }
        if (shooter >= 0) {
            for (p in 0 until 4) if (p != shooter) scores[p] += 26
            messageKo = "달 사냥!"
            messageEn = "Shot the moon!"
        } else {
            for (p in 0 until 4) scores[p] += handPoints[p]
        }
        if (scores.any { it >= 100 }) {
            phase = Phase.OVER
            return
        }
        handNumber++
        pass = Pass.entries[(pass.ordinal + 1) % Pass.entries.size]
        deal(Random.Default)
    }

    /** 기기가 넘길 세 장: 높은 카드와 위험한 카드를 먼저 버린다. */
    private fun aiPass(player: Int, level: Int, rng: Random): List<Int> {
        val hand = hands[player].toMutableList()
        if (hand.size < 3) return hand
        return if (level == 0) hand.shuffled(rng).take(3)
        else hand.sortedWith(CARD_ORDER).takeLast(3)
    }

    fun aiPlay(player: Int, level: Int, rng: Random = Random.Default): Int {
        val legal = legalCards(player)
        if (legal.isEmpty()) return -1
        if (level == 0) return legal.first()
        val led = trick.firstOrNull()?.let { suit(it.second) }
        if (led == null) {
            // 리드: 점수 카드가 아닌 낮은 카드
            val safe = legal.filter { !isPenalty(it) }
            return (safe.ifEmpty { legal }).first()
        }
        val following = legal.filter { suit(it) == led }
        val pointsInTrick = trick.any { isPenalty(it.second) }
        if (following.isNotEmpty()) {
            // 이길 수 있으면 점수 트릭을 가져오지 않게 낮게, 점수 트릭이면 이길 수 있게 높게
            val ledCards = trick.filter { suit(it.second) == led }
            val winning = following.filter { c -> ledCards.all { trickRank(c) > trickRank(it.second) } }
            return if (pointsInTrick && winning.isNotEmpty()) winning.minByOrNull { trickRank(it) } ?: following.first()
            else following.first()
        }
        val safe = legal.filter { !isPenalty(it) }
        return (safe.ifEmpty { legal }).lastOrNull() ?: legal.first()
    }

    fun encode(): String {
        fun h(a: List<Int>) = a.joinToString(",")
        return listOf(
            hands.joinToString(";") { h(it) }, scores.joinToString(","), handPoints.joinToString(","), phase.ordinal, pass.ordinal,
            handNumber, trick.joinToString(";") { "${it.first}:${it.second}" }, turn, leader,
            if (heartsBroken) 1 else 0, if (firstTrick) 1 else 0, humanPass.joinToString(","),
            lastWinner, lastPoints, messageKo, messageEn,
        ).joinToString("|")
    }

    companion object {
        const val HEARTS = 1
        const val SPADES = 0
        const val TWO_CLUBS = 40
        val CARD_ORDER = Comparator<Int> { a, b -> if (suit(a) != suit(b)) suit(a) - suit(b) else trickRank(a) - trickRank(b) }

        fun suit(card: Int) = card / 13
        fun rank(card: Int) = card % 13
        /** 하트에서는 A 가 가장 높다. */
        fun trickRank(card: Int) = if (rank(card) == 0) 13 else rank(card)
        fun isPenalty(card: Int) = suit(card) == HEARTS || card == 11 // Q♠ = spades(0)*13 + 11
        fun cardPoints(card: Int) = when {
            suit(card) == HEARTS -> 1
            card == 11 -> 13
            else -> 0
        }

        fun decode(s: String): HeartsGame? = runCatching {
            val p = s.split('|')
            HeartsGame().apply {
                hands = Array(4) { i -> p[0].split(';')[i].split(',').filter { it.isNotEmpty() }.map { it.toInt() }.toMutableList() }
                scores = p[1].split(',').map { it.toInt() }.toIntArray()
                handPoints = p[2].split(',').map { it.toInt() }.toIntArray()
                phase = Phase.entries[p[3].toInt()]
                pass = Pass.entries[p[4].toInt()]
                handNumber = p[5].toInt()
                trick = if (p[6].isEmpty()) mutableListOf() else p[6].split(';').map { val q = it.split(':'); q[0].toInt() to q[1].toInt() }.toMutableList()
                turn = p[7].toInt()
                leader = p[8].toInt()
                heartsBroken = p[9] == "1"
                firstTrick = p[10] == "1"
                if (p[11].isNotEmpty()) humanPass.addAll(p[11].split(',').map { it.toInt() })
                lastWinner = p[12].toInt()
                lastPoints = p[13].toInt()
                messageKo = p.getOrElse(14) { "" }
                messageEn = p.getOrElse(15) { "" }
            }
        }.getOrNull()
    }
}
