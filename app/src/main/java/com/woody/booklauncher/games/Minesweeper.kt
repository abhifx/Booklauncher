package com.woody.booklauncher.games

/**
 * 지뢰찾기. 첫 칸을 열 때 그 칸과 이웃을 피해 지뢰를 놓는다.
 * [chord] 는 열린 숫자 칸을 눌러 주위 깃발 수가 숫자와 같을 때 나머지를 연다(지뢰를 잘못 꽂았으면 진다).
 */
internal class Minesweeper(val size: Int, val mineCount: Int) {
    var mine = BooleanArray(size * size)
    var revealed = BooleanArray(size * size)
    var flagged = BooleanArray(size * size)
    var started = false
        private set
    var lost = false
        private set
    var won = false
        private set
    var flags = 0
        private set
    var exploded = -1
        private set
    var startMs = 0L
        private set
    var elapsedMs = 0L
        private set

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

    fun elapsed(now: Long = System.currentTimeMillis()): Long =
        if (elapsedMs > 0) elapsedMs else if (startMs > 0) (now - startMs).coerceAtLeast(0) else 0L

    /** 칸을 연다. 바뀐 것이 있으면 true. */
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
        finishIfWon()
        return true
    }

    /** 숫자 칸을 눌러 주위 깃발이 숫자와 같으면 나머지를 연다. 바뀐 것이 있으면 true. */
    fun chord(index: Int): Boolean {
        if (index !in mine.indices || lost || won || !revealed[index] || mine[index]) return false
        val n = count(index)
        if (n == 0) return false
        val around = neighbours(index)
        if (around.count { flagged[it] } != n) return false
        var changed = false
        for (i in around) {
            if (revealed[i] || flagged[i]) continue
            if (mine[i]) {
                lost = true
                exploded = i
                elapsedMs = elapsed()
                for (j in mine.indices) if (mine[j]) revealed[j] = true
                return true
            }
            open(i)
            changed = true
        }
        finishIfWon()
        return changed
    }

    fun toggleFlag(index: Int): Boolean {
        if (index !in mine.indices || lost || won || revealed[index]) return false
        flagged[index] = !flagged[index]
        flags += if (flagged[index]) 1 else -1
        return true
    }

    private fun finishIfWon() {
        if (lost || won) return
        if (revealed.count { !it } == mineCount) {
            won = true
            elapsedMs = elapsed()
            for (i in mine.indices) if (mine[i]) flagged[i] = true
            flags = mineCount
        }
    }

    private fun placeMines(avoid: Int) {
        val safe = mutableSetOf(avoid)
        safe += neighbours(avoid)
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

    fun neighbours(index: Int): List<Int> {
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

    fun encode(): String = listOf(
        size, mineCount,
        mine.joinToString("") { if (it) "1" else "0" },
        revealed.joinToString("") { if (it) "1" else "0" },
        flagged.joinToString("") { if (it) "1" else "0" },
        if (started) 1 else 0, if (lost) 1 else 0, if (won) 1 else 0,
        flags, exploded, startMs, elapsedMs,
    ).joinToString("|")

    companion object {
        fun decode(s: String): Minesweeper? = runCatching {
            val p = s.split('|')
            val size = p[0].toInt()
            val game = Minesweeper(size, p[1].toInt())
            game.mine = bits(p[2])
            game.revealed = bits(p[3])
            game.flagged = bits(p[4])
            game.started = p[5] == "1"
            game.lost = p[6] == "1"
            game.won = p[7] == "1"
            game.flags = p[8].toInt()
            game.exploded = p[9].toInt()
            game.startMs = p[10].toLong()
            game.elapsedMs = p[11].toLong()
            require(game.mine.size == size * size)
            game
        }.getOrNull()

        private fun bits(s: String) = BooleanArray(s.length) { s[it] == '1' }
    }
}
