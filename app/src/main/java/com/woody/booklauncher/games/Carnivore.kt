package com.woody.booklauncher.games

import kotlin.random.Random

/**
 * Carnivore: 동물 용의자들의 진술로 범인을 찾는 추리 게임.
 * 각 용의자는 한 문장을 하고, 문제에 적힌 수만큼의 거짓 진술이 있다. 범인도 진실을 말할 수 있고
 * 죄 없는 동물도 거짓을 말할 수 있다. 문장은 죄·식성·키를 가리키며, 정확히 한 가지 답이 있게 만든다.
 */
internal data class CarnivoreCase(
    val number: Int,
    val suspects: List<Suspect>,
    val culpritCount: Int,
    val liars: Int,
    val answer: Set<Int>,
) {
    data class Suspect(val name: String, val nameEn: String, val diet: Diet, val height: Int, val statement: Statement)

    enum class Diet(val ko: String, val en: String) { CARNIVORE("육식", "carnivore"), HERBIVORE("초식", "herbivore"), OMNIVORE("잡식", "omnivore") }

    sealed interface Statement {
        fun text(suspects: List<Suspect>, en: Boolean): String
        fun eval(culprits: Set<Int>, suspects: List<Suspect>): Boolean
    }

    data class Innocent(val who: Int) : Statement {
        override fun text(suspects: List<Suspect>, en: Boolean) =
            if (en) "${sName(suspects, who, true)} is innocent." else "${suspects[who].name}은(는) 죄가 없다."
        override fun eval(culprits: Set<Int>, suspects: List<Suspect>) = who !in culprits
    }

    data class NotCulprit(val who: Int) : Statement {
        override fun text(suspects: List<Suspect>, en: Boolean) =
            if (en) "${sName(suspects, who, true)} is not a culprit." else "${suspects[who].name}은(는) 범인이 아니다."
        override fun eval(culprits: Set<Int>, suspects: List<Suspect>) = who !in culprits
    }

    data class IsCulprit(val who: Int) : Statement {
        override fun text(suspects: List<Suspect>, en: Boolean) =
            if (en) "${sName(suspects, who, true)} is a culprit." else "${suspects[who].name}이(가) 범인이다."
        override fun eval(culprits: Set<Int>, suspects: List<Suspect>) = who in culprits
    }

    data class DietIs(val who: Int, val diet: Diet) : Statement {
        override fun text(suspects: List<Suspect>, en: Boolean) =
            if (en) "${sName(suspects, who, true)} is ${aDiet(diet)}."
            else "${suspects[who].name}은(는) ${diet.ko} 동물이다."
        override fun eval(culprits: Set<Int>, suspects: List<Suspect>) = suspects[who].diet == diet
    }

    data class Taller(val a: Int, val b: Int) : Statement {
        override fun text(suspects: List<Suspect>, en: Boolean) =
            if (en) "${sName(suspects, a, true)} is taller than ${sName(suspects, b, true)}."
            else "${suspects[a].name}은(는) ${suspects[b].name}보다 크다."
        override fun eval(culprits: Set<Int>, suspects: List<Suspect>) = suspects[a].height > suspects[b].height
    }

    data class ExactlyOne(val a: Int, val b: Int) : Statement {
        override fun text(suspects: List<Suspect>, en: Boolean) =
            if (en) "Exactly one of ${sName(suspects, a, true)} and ${sName(suspects, b, true)} is a culprit."
            else "${suspects[a].name}과 ${suspects[b].name} 중 정확히 하나가 범인이다."
        override fun eval(culprits: Set<Int>, suspects: List<Suspect>) = (a in culprits) xor (b in culprits)
    }

    data class AtLeastOne(val a: Int, val b: Int) : Statement {
        override fun text(suspects: List<Suspect>, en: Boolean) =
            if (en) "${sName(suspects, a, true)} or ${sName(suspects, b, true)} is a culprit."
            else "${suspects[a].name} 또는 ${suspects[b].name}이(가) 범인이다."
        override fun eval(culprits: Set<Int>, suspects: List<Suspect>) = a in culprits || b in culprits
    }

    data class CulpritDiet(val diet: Diet) : Statement {
        override fun text(suspects: List<Suspect>, en: Boolean) =
            if (en) "A culprit is ${aDiet(diet)}." else "범인은 ${diet.ko} 동물이다."
        override fun eval(culprits: Set<Int>, suspects: List<Suspect>) = culprits.any { suspects[it].diet == diet }
    }

    data class BothCulprits(val a: Int, val b: Int) : Statement {
        override fun text(suspects: List<Suspect>, en: Boolean) =
            if (en) "Both ${sName(suspects, a, true)} and ${sName(suspects, b, true)} are culprits."
            else "${suspects[a].name}과 ${suspects[b].name}이(가) 둘 다 범인이다."
        override fun eval(culprits: Set<Int>, suspects: List<Suspect>) = a in culprits && b in culprits
    }

    fun statementTruth(index: Int, culprits: Set<Int> = answer): Boolean =
        suspects[index].statement.eval(culprits, suspects)

    fun falseCount(culprits: Set<Int>): Int =
        suspects.indices.count { !statementTruth(it, culprits) }

    /** 주어진 범인 수·거짓 수를 만족하는 범인 조합이 하나뿐인가. */
    fun solutions(): List<Set<Int>> {
        val out = mutableListOf<Set<Int>>()
        combinations(suspects.indices.toList(), culpritCount).forEach { set ->
            if (falseCount(set) == liars) out += set
        }
        return out
    }

    fun choose(index: Int): String = if (index in answer) "정답" else "빗나갔다"

    companion object {
        private val NAMES = listOf("표범", "공작", "상어", "여우", "곰", "사슴", "말")
        private val NAMES_EN = listOf("Panther", "Peacock", "Shark", "Fox", "Bear", "Stag", "Horse")

        fun combinations(all: List<Int>, k: Int): List<Set<Int>> {
            if (k == 0) return listOf(emptySet())
            if (all.size < k) return emptyList()
            val out = mutableListOf<Set<Int>>()
            fun go(start: Int, picked: MutableList<Int>) {
                if (picked.size == k) { out += picked.toSet(); return }
                for (i in start until all.size) { picked += all[i]; go(i + 1, picked); picked.removeAt(picked.lastIndex) }
            }
            go(0, mutableListOf())
            return out
        }
    }

    /** 문제 생성: 같은 씨앗이면 같은 문제. 정답이 하나인 것만 쓴다. */
    object Cases {
        private const val COUNT = 12
        val all: List<CarnivoreCase> by lazy { (1..COUNT).mapNotNull { generate(it) } }

        fun generate(seed: Int): CarnivoreCase? {
            val rng = Random(seed)
            val cast = (0 until 5).shuffled(rng).take(4)
            val answer = (0 until 4).shuffled(rng).take(if (seed % 3 == 0) 2 else 1).toSet()
            val suspects = cast.mapIndexed { i, animal ->
                val diet = DIETS[animal]
                Suspect(NAMES[animal], NAMES_EN[animal], diet, HEIGHTS[animal], placeholder(i))
            }
            // 문장을 하나씩 바꿔 가며 거짓 수가 맞고 답이 하나인 조합을 찾는다.
            val options = cast.indices.flatMap { i -> statementsFor(i, cast) }
            val chosen = arrayOfNulls<Statement>(cast.size)
            fun pick(i: Int): Boolean {
                if (i == cast.size) {
                    val list = suspects.mapIndexed { j, s -> s.copy(statement = chosen[j]!!) }
                    val candidate = CarnivoreCase(seed, list, answer.size, 0, answer)
                    val falseCount = candidate.falseCount(answer)
                    if (falseCount !in 1..(cast.size - 1)) return false
                    val withLiars = candidate.copy(liars = falseCount)
                    return withLiars.solutions().size == 1
                }
                options.filter { it.first == i }.shuffled(rng).forEach { (_, st) ->
                    chosen[i] = st
                    if (pick(i + 1)) return true
                }
                chosen[i] = null
                return false
            }
            if (!pick(0)) return null
            val list = suspects.mapIndexed { j, s -> s.copy(statement = chosen[j]!!) }
            val probe = CarnivoreCase(seed, list, answer.size, 0, answer)
            return probe.copy(liars = probe.falseCount(answer))
        }

        private fun placeholder(i: Int): Statement = Innocent(i)

        private fun statementsFor(i: Int, cast: List<Int>): List<Pair<Int, Statement>> {
            val others = cast.indices.filter { it != i }
            val out = mutableListOf<Pair<Int, Statement>>()
            out += i to Innocent(i)
            out += i to IsCulprit(i)
            out += i to NotCulprit(i)
            cast.indices.forEach { a -> out += i to DietIs(a, DIETS[cast[a]]) }
            others.forEach { b -> out += i to Taller(i, b) }
            others.forEach { b -> out += i to Taller(b, i) }
            others.forEach { b -> out += i to ExactlyOne(i, b) }
            others.forEach { b -> out += i to AtLeastOne(i, b) }
            others.forEach { b -> out += i to BothCulprits(i, b) }
            out += i to CulpritDiet(Diet.CARNIVORE)
            out += i to CulpritDiet(Diet.HERBIVORE)
            out += i to CulpritDiet(Diet.OMNIVORE)
            return out
        }

        private val DIETS = listOf(Diet.CARNIVORE, Diet.OMNIVORE, Diet.CARNIVORE, Diet.OMNIVORE, Diet.OMNIVORE, Diet.HERBIVORE, Diet.HERBIVORE)
        private val HEIGHTS = listOf(70, 110, 200, 60, 180, 150, 160)
    }
}

/**
 * Carnivore 한 판: 용의자를 범인/무죄로 표시하고 `Accuse`. 목숨은 두 번.
 */
internal class CarnivoreGame(val caseIndex: Int = 0) {
    val case: CarnivoreCase = CarnivoreCase.Cases.all[caseIndex.coerceIn(CarnivoreCase.Cases.all.indices)]
    var marks = IntArray(case.suspects.size) // 0 모름, 1 범인, 2 무죄
    var lives = 2
    var solved = false
    var lost = false
    var message = Msg.NONE

    enum class Msg { NONE, PICK, CORRECT, WRONG, DEAD }

    fun cycle(index: Int) {
        if (solved || lost || index !in marks.indices) return
        marks[index] = (marks[index] + 1) % 3
    }

    fun accuse(): Boolean {
        if (solved || lost) return false
        val picked = marks.indices.filter { marks[it] == 1 }.toSet()
        if (picked.size != case.culpritCount) {
            message = Msg.PICK
            return false
        }
        if (picked == case.answer) {
            solved = true
            message = Msg.CORRECT
            return true
        }
        lives--
        message = if (lives <= 0) Msg.DEAD else Msg.WRONG
        if (lives <= 0) lost = true
        return false
    }

    fun restart() {
        marks = IntArray(case.suspects.size)
        lives = 2
        solved = false
        lost = false
        message = Msg.NONE
    }

    fun encode(): String = listOf(caseIndex, marks.joinToString(""), lives, if (solved) 1 else 0, if (lost) 1 else 0, message.ordinal).joinToString("|")

    companion object {
        fun decode(s: String): CarnivoreGame? = runCatching {
            val p = s.split('|')
            CarnivoreGame(p[0].toInt()).apply {
                marks = IntArray(p[1].length) { p[1][it] - '0' }
                lives = p[2].toInt()
                solved = p[3] == "1"
                lost = p[4] == "1"
                message = Msg.entries[p.getOrNull(5)?.toIntOrNull()?.coerceIn(0, Msg.entries.lastIndex) ?: 0]
            }
        }.getOrNull()
    }
}

private fun sName(suspects: List<CarnivoreCase.Suspect>, i: Int, en: Boolean) =
    if (en) suspects[i].nameEn else suspects[i].name

private fun aDiet(diet: CarnivoreCase.Diet): String =
    (if (diet == CarnivoreCase.Diet.OMNIVORE) "an " else "a ") + diet.en
