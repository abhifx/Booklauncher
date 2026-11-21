package com.woody.booklauncher.games

import kotlin.random.Random

/**
 * 배틀십, 10×10. 배 다섯(5·4·3·3·2)을 놓고 적 함대를 한 칸씩 친다.
 * 사람이 쏘면 기기가 바로 한 발 쏘고, 한쪽 배가 모두 가라앉으면 끝난다.
 */
internal class BattleshipGame {
    enum class Phase { PLACE, PLAY, OVER }
    enum class Shot { INVALID, MISS, HIT, SUNK, WIN }

    data class Ship(val size: Int, val cells: List<Int>, val horizontal: Boolean)

    val size = 10
    val fleet = intArrayOf(5, 4, 3, 3, 2)
    var playerShips = mutableListOf<Ship>()
        private set
    var aiShips = mutableListOf<Ship>()
        private set
    var playerShots = BooleanArray(100) // 사람이 적 함대에 쏜 칸
        private set
    var enemyShots = BooleanArray(100) // 기기가 내 함대에 쏜 칸
        private set
    var phase = Phase.PLACE
        private set
    var placement = 0
        private set
    var horizontal = true
        private set
    var winner = -1
        private set
    var lastPlayerShot = -1
        private set
    var lastEnemyShot = -1
        private set

    private val aiTargets = ArrayDeque<Int>()

    fun newGame() {
        playerShips = mutableListOf()
        aiShips = mutableListOf()
        playerShots = BooleanArray(100)
        enemyShots = BooleanArray(100)
        phase = Phase.PLACE
        placement = 0
        horizontal = true
        winner = -1
        lastPlayerShot = -1
        lastEnemyShot = -1
        aiTargets.clear()
    }

    fun rotate() { horizontal = !horizontal }

    /** 지금 놓을 배를 [row],[col] 에 놓는다. 규칙에 맞지 않으면 false. */
    fun placePlayer(row: Int, col: Int): Boolean {
        if (phase != Phase.PLACE) return false
        val s = fleet.getOrNull(placement) ?: return false
        val cells = cellsFor(row, col, s, horizontal) ?: return false
        if (cells.any { it in playerShips.flatMap { ship -> ship.cells } }) return false
        playerShips += Ship(s, cells, horizontal)
        placement++
        if (placement == fleet.size) start()
        return true
    }

    fun randomPlayerPlacement(rng: Random = Random.Default) {
        playerShips = randomFleet(rng)
        placement = fleet.size
        start()
    }

    fun autoPlaceBoth(rng: Random = Random.Default) {
        playerShips = randomFleet(rng)
        aiShips = randomFleet(rng)
        placement = fleet.size
        start()
    }

    private fun start() {
        if (aiShips.isEmpty()) aiShips = randomFleet(Random.Default)
        phase = Phase.PLAY
    }

    fun fire(row: Int, col: Int): Shot {
        if (phase != Phase.PLAY) return Shot.INVALID
        val i = row * size + col
        if (i !in 0 until 100 || playerShots[i]) return Shot.INVALID
        playerShots[i] = true
        lastPlayerShot = i
        val ship = shipAt(aiShips, i)
        val result = when {
            ship == null -> Shot.MISS
            ship.cells.all { playerShots[it] } -> Shot.SUNK
            else -> Shot.HIT
        }
        if (aiShips.all { s -> s.cells.all { playerShots[it] } }) {
            phase = Phase.OVER
            winner = HUMAN
            return Shot.WIN
        }
        aiFire()
        if (playerShips.all { s -> s.cells.all { enemyShots[it] } }) {
            phase = Phase.OVER
            winner = AI
        }
        return result
    }

    private fun aiFire() {
        if (phase != Phase.PLAY) return
        val i = nextAiTarget()
        enemyShots[i] = true
        lastEnemyShot = i
        val ship = shipAt(playerShips, i)
        if (ship != null) {
            if (ship.cells.all { enemyShots[it] }) {
                aiTargets.clear()
            } else {
                for (j in neighbours(i)) if (!enemyShots[j]) aiTargets.addLast(j)
            }
        }
    }

    private fun nextAiTarget(): Int {
        while (aiTargets.isNotEmpty()) {
            val i = aiTargets.removeFirst()
            if (!enemyShots[i]) return i
        }
        val open = (0 until 100).filter { !enemyShots[it] }
        return open.random()
    }

    fun shipAt(ships: List<Ship>, index: Int): Ship? = ships.firstOrNull { index in it.cells }

    fun isSunk(ship: Ship, shots: BooleanArray): Boolean = ship.cells.all { shots[it] }

    fun neighbours(index: Int): List<Int> {
        val r = index / size
        val c = index % size
        return buildList {
            if (r > 0) add((r - 1) * size + c)
            if (r < size - 1) add((r + 1) * size + c)
            if (c > 0) add(r * size + c - 1)
            if (c < size - 1) add(r * size + c + 1)
        }
    }

    private fun cellsFor(row: Int, col: Int, length: Int, horizontal: Boolean): List<Int>? {
        val cells = (0 until length).map {
            val r = if (horizontal) row else row + it
            val c = if (horizontal) col + it else col
            if (r !in 0 until size || c !in 0 until size) return null
            r * size + c
        }
        return cells
    }

    private fun randomFleet(rng: Random): MutableList<Ship> {
        val used = mutableSetOf<Int>()
        val out = mutableListOf<Ship>()

        fun place(index: Int): Boolean {
            if (index == fleet.size) return true
            val size = fleet[index]
            val candidates = mutableListOf<Ship>()
            for (r in 0 until this.size) for (c in 0 until this.size) {
                for (h in listOf(true, false)) {
                    val cells = cellsFor(r, c, size, h) ?: continue
                    if (cells.none { it in used }) candidates += Ship(size, cells, h)
                }
            }
            candidates.shuffle(rng)
            for (ship in candidates) {
                ship.cells.forEach { used += it }
                out += ship
                if (place(index + 1)) return true
                out.removeAt(out.lastIndex)
                ship.cells.forEach { used -= it }
            }
            return false
        }

        if (place(0)) return out
        // 함대가 들어가지 않는 아주 드문 경우: 빈 판에 한 줄씩 놓는다.
        out.clear()
        used.clear()
        var row = 0
        for (s in fleet) {
            val cells = (0 until s).map { row * size + it }
            out += Ship(s, cells, true)
            used += cells
            row += 2
        }
        return out
    }

    fun encode(): String = listOf(
        encodeShips(playerShips), encodeShips(aiShips),
        bits(playerShots), bits(enemyShots), phase.ordinal, placement,
        if (horizontal) 1 else 0, winner, lastPlayerShot, lastEnemyShot,
    ).joinToString("|")

    companion object {
        const val HUMAN = 0
        const val AI = 1

        private fun bits(a: BooleanArray) = a.joinToString("") { if (it) "1" else "0" }
        private fun encodeShips(ships: List<Ship>) = ships.joinToString(";") { s -> "${s.size}:${s.cells.joinToString(",")}:${if (s.horizontal) 1 else 0}" }

        fun decode(s: String): BattleshipGame? = runCatching {
            val p = s.split('|')
            BattleshipGame().apply {
                playerShips = mutableListOf<Ship>().also { list ->
                    if (p[0].isNotEmpty()) p[0].split(';').forEach { line ->
                        val q = line.split(':')
                        list += Ship(q[0].toInt(), q[1].split(',').map { it.toInt() }, q[2] == "1")
                    }
                }
                aiShips = mutableListOf<Ship>().also { list ->
                    if (p[1].isNotEmpty()) p[1].split(';').forEach { line ->
                        val q = line.split(':')
                        list += Ship(q[0].toInt(), q[1].split(',').map { it.toInt() }, q[2] == "1")
                    }
                }
                playerShots = BooleanArray(100) { p[2][it] == '1' }
                enemyShots = BooleanArray(100) { p[3][it] == '1' }
                phase = Phase.entries[p[4].toInt()]
                placement = p[5].toInt()
                horizontal = p[6] == "1"
                winner = p[7].toInt()
                lastPlayerShot = p[8].toInt()
                lastEnemyShot = p[9].toInt()
            }
        }.getOrNull()
    }
}
