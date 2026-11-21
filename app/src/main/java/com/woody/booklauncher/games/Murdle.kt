package com.woody.booklauncher.games

import kotlin.random.Random

/**
 * Murdle식 논리 표: 용의자 넷의 무기와 장소를 단서로 맞힌다.
 * 단서는 모두 참이고, 답이 하나뿐인 문제만 만든다(전수 계산).
 */
internal data class MurdlePuzzle(
    val number: Int,
    val suspects: List<String>,
    val suspectsEn: List<String>,
    val weapons: List<String>,
    val weaponsEn: List<String>,
    val rooms: List<String>,
    val roomsEn: List<String>,
    val weaponAnswer: IntArray,
    val roomAnswer: IntArray,
    val clues: List<Clue>,
) {
    sealed interface Clue {
        fun text(suspects: List<String>, weapons: List<String>, rooms: List<String>, en: Boolean): String
        fun matches(w: IntArray, r: IntArray): Boolean
    }

    data class WeaponIs(val s: Int, val w: Int) : Clue {
        override fun text(suspects: List<String>, weapons: List<String>, rooms: List<String>, en: Boolean) =
            if (en) "${suspects[s]}'s weapon is the ${weapons[w]}." else "${suspects[s]}의 무기는 ${weapons[w]}."
        override fun matches(w: IntArray, r: IntArray) = w[s] == this.w
    }

    data class RoomIs(val s: Int, val r: Int) : Clue {
        override fun text(suspects: List<String>, weapons: List<String>, rooms: List<String>, en: Boolean) =
            if (en) "${suspects[s]} was in the ${rooms[r]}." else "${suspects[s]}은(는) ${rooms[r]}에 있었다."
        override fun matches(w: IntArray, r: IntArray) = r[s] == this.r
    }

    data class WeaponNot(val s: Int, val w: Int) : Clue {
        override fun text(suspects: List<String>, weapons: List<String>, rooms: List<String>, en: Boolean) =
            if (en) "${suspects[s]}'s weapon is not the ${weapons[w]}." else "${suspects[s]}의 무기는 ${weapons[w]}이(가) 아니다."
        override fun matches(w: IntArray, r: IntArray) = w[s] != this.w
    }

    data class RoomNot(val s: Int, val r: Int) : Clue {
        override fun text(suspects: List<String>, weapons: List<String>, rooms: List<String>, en: Boolean) =
            if (en) "${suspects[s]} was not in the ${rooms[r]}." else "${suspects[s]}은(는) ${rooms[r]}에 있지 않았다."
        override fun matches(w: IntArray, r: IntArray) = r[s] != this.r
    }

    data class WeaponBefore(val a: Int, val b: Int) : Clue {
        override fun text(suspects: List<String>, weapons: List<String>, rooms: List<String>, en: Boolean) =
            if (en) "${suspects[a]}'s weapon comes before ${suspects[b]}'s in the list."
            else "${suspects[a]}의 무기는 ${suspects[b]}의 무기보다 목록에서 앞선다."
        override fun matches(w: IntArray, r: IntArray) = w[a] < w[b]
    }

    data class RoomBefore(val a: Int, val b: Int) : Clue {
        override fun text(suspects: List<String>, weapons: List<String>, rooms: List<String>, en: Boolean) =
            if (en) "${suspects[a]} was in a room left of ${suspects[b]}."
            else "${suspects[a]}은(는) ${suspects[b]}보다 왼쪽 방에 있었다."
        override fun matches(w: IntArray, r: IntArray) = r[a] < r[b]
    }

    data class RoomNextTo(val a: Int, val b: Int) : Clue {
        override fun text(suspects: List<String>, weapons: List<String>, rooms: List<String>, en: Boolean) =
            if (en) "${suspects[a]} and ${suspects[b]} were in neighbouring rooms."
            else "${suspects[a]}과 ${suspects[b]}의 방은 이웃했다."
        override fun matches(w: IntArray, r: IntArray) = kotlin.math.abs(r[a] - r[b]) == 1
    }

    fun matches(w: IntArray, r: IntArray): Boolean = clues.all { it.matches(w, r) }

    companion object {
        private val SUSPECTS = listOf("마라", "줄스", "루크", "베라")
        private val SUSPECTS_EN = listOf("Mara", "Jules", "Luke", "Vera")
        private val WEAPONS = listOf("단검", "밧줄", "독약", "촛대")
        private val WEAPONS_EN = listOf("dagger", "rope", "poison", "candlestick")
        private val ROOMS = listOf("서재", "온실", "주방", "차고")
        private val ROOMS_EN = listOf("study", "conservatory", "kitchen", "garage")

        /** 열두 문제를 한 번만 만든다. */
        val Cases: List<MurdlePuzzle> by lazy { (1..12).mapNotNull { generate(it) } }

        fun generate(seed: Int): MurdlePuzzle? {
            val rng = Random(seed)
            val w = (0 until 4).shuffled(rng).toIntArray()
            val r = (0 until 4).shuffled(rng).toIntArray()
            val all = mutableListOf<Clue>()
            // 참인 단서 후보. 너무 직접적인 단서만 나오지 않게 섞는다.
            for (s in 0 until 4) {
                all += WeaponIs(s, w[s])
                all += RoomIs(s, r[s])
                all += WeaponNot(s, (0 until 4).filter { it != w[s] }.random(rng))
                all += RoomNot(s, (0 until 4).filter { it != r[s] }.random(rng))
            }
            for (a in 0 until 4) for (b in a + 1 until 4) {
                if (w[a] < w[b]) all += WeaponBefore(a, b)
                if (r[a] < r[b]) all += RoomBefore(a, b)
                if (kotlin.math.abs(r[a] - r[b]) == 1) all += RoomNextTo(a, b)
            }
            all.shuffle(rng)
            val clues = mutableListOf<Clue>()
            for (c in all) {
                clues += c
                if (clues.size >= 3 && countSolutions(clues, limit = 2) == 1) break
            }
            if (countSolutions(clues, limit = 2) != 1) return null
            return MurdlePuzzle(seed, SUSPECTS, SUSPECTS_EN, WEAPONS, WEAPONS_EN, ROOMS, ROOMS_EN, w, r, clues)
        }

        private fun countSolutions(clues: List<Clue>, limit: Int): Int {
            var found = 0
            val perm = permute(listOf(0, 1, 2, 3))
            for (w in perm) for (r in perm) {
                if (clues.all { it.matches(w, r) }) {
                    found++
                    if (found >= limit) return found
                }
            }
            return found
        }

        private fun permute(list: List<Int>): List<IntArray> {
            val out = mutableListOf<IntArray>()
            fun go(start: Int, a: IntArray) {
                if (start == a.size) { out += a.copyOf(); return }
                for (i in start until a.size) {
                    val t = a[start]; a[start] = a[i]; a[i] = t
                    go(start + 1, a)
                    val u = a[start]; a[start] = a[i]; a[i] = u
                }
            }
            go(0, list.toIntArray())
            return out
        }
    }
}

internal class MurdleGame(val puzzleIndex: Int = 0) {
    val puzzle: MurdlePuzzle = MurdlePuzzle.Cases[puzzleIndex.coerceIn(MurdlePuzzle.Cases.indices)]
    var weapons = IntArray(4) { -1 }
    var rooms = IntArray(4) { -1 }
    var solved = false
    var checked = false

    fun cycleWeapon(s: Int) {
        if (solved) return
        weapons[s] = (weapons[s] + 2) % 5 - 1 // -1 -> 0 -> 1 -> 2 -> 3 -> -1
    }

    fun cycleRoom(s: Int) {
        if (solved) return
        rooms[s] = (rooms[s] + 2) % 5 - 1
    }

    fun check(): Boolean {
        checked = true
        solved = weapons.contentEquals(puzzle.weaponAnswer) && rooms.contentEquals(puzzle.roomAnswer)
        return solved
    }

    fun restart() {
        weapons = IntArray(4) { -1 }
        rooms = IntArray(4) { -1 }
        solved = false
        checked = false
    }

    fun encode(): String = listOf(
        puzzleIndex, weapons.joinToString(","), rooms.joinToString(","), if (solved) 1 else 0,
    ).joinToString("|")

    companion object {
        fun decode(s: String): MurdleGame? = runCatching {
            val p = s.split('|')
            MurdleGame(p[0].toInt()).apply {
                weapons = p[1].split(',').map { it.toInt() }.toIntArray()
                rooms = p[2].split(',').map { it.toInt() }.toIntArray()
                solved = p[3] == "1"
            }
        }.getOrNull()
    }
}
