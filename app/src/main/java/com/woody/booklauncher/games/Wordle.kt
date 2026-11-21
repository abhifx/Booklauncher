package com.woody.booklauncher.games

import kotlin.random.Random

/**
 * Wordle. 다섯 글자, 여섯 번. 정답과 허용 단어는 앱의 raw 자원에서 읽어 넣는다.
 * 점수: 0 없음, 1 다른 자리에 있음, 2 제자리.
 */
internal class WordleGame(val answer: String, private val allowed: Set<String>) {
    enum class Submit { OK, TOO_SHORT, NOT_WORD, FINISHED }

    val guesses = mutableListOf<String>()
    var current = ""
        private set
    var won = false
        private set
    var over = false
        private set
    /** 알파벳마다 지금까지 안 가장 좋은 결과(0 모름, 1 없음, 2 다른 자리, 3 제자리) */
    val keyboard = IntArray(26)

    fun type(c: Char): Boolean {
        if (over || current.length >= 5 || !c.isLetter()) return false
        current += c.lowercaseChar()
        return true
    }

    fun backspace(): Boolean {
        if (over || current.isEmpty()) return false
        current = current.dropLast(1)
        return true
    }

    fun submit(): Submit {
        if (over) return Submit.FINISHED
        if (current.length < 5) return Submit.TOO_SHORT
        val word = current.lowercase()
        if (word !in allowed) return Submit.NOT_WORD
        val s = score(word)
        guesses += word
        for (i in 0 until 5) {
            val k = word[i] - 'a'
            if (k in 0 until 26) keyboard[k] = maxOf(keyboard[k], s[i])
        }
        current = ""
        if (word == answer) {
            won = true
            over = true
        } else if (guesses.size >= MAX_TRIES) {
            over = true
        }
        return Submit.OK
    }

    fun score(guess: String): IntArray {
        val g = guess.lowercase().toCharArray()
        val a = answer.lowercase().toCharArray()
        val out = IntArray(5)
        val used = BooleanArray(5)
        for (i in 0 until minOf(5, g.size, a.size)) {
            if (g[i] == a[i]) { out[i] = 2; used[i] = true }
        }
        for (i in 0 until minOf(5, g.size)) {
            if (out[i] != 0) continue
            val j = (0 until minOf(5, a.size)).firstOrNull { !used[it] && a[it] == g[i] } ?: continue
            out[i] = 1
            used[j] = true
        }
        return out
    }

    fun encode(): String = listOf(
        answer, guesses.joinToString(","), current, if (won) 1 else 0, if (over) 1 else 0,
        keyboard.joinToString(""),
    ).joinToString("|")

    companion object {
        const val MAX_TRIES = 6

        fun randomAnswer(answers: List<String>, rng: Random = Random.Default): String =
            answers[rng.nextInt(answers.size)].lowercase()

        fun decode(s: String, allowed: Set<String>): WordleGame? = runCatching {
            val p = s.split('|')
            WordleGame(p[0], allowed).apply {
                if (p[1].isNotEmpty()) guesses += p[1].split(',')
                current = p[2]
                won = p[3] == "1"
                over = p[4] == "1"
                p[5].forEachIndexed { i, c -> if (i < 26) keyboard[i] = c - '0' }
            }
        }.getOrNull()
    }
}
