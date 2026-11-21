package com.woody.booklauncher.games

import android.content.Context
import com.woody.booklauncher.Storage

/** 게임 상태·기록. `games` 설정 한 곳에 두어 앱을 다시 띄워도 이어서 한다. */
internal object GameStore {
    const val G2048 = "2048"
    const val MINES = "mines"
    const val SUDOKU = "sudoku"
    const val NONOGRAM = "nonogram"
    const val CONNECT4 = "connect4"
    const val CHECKERS = "checkers"
    const val CHESS = "chess"
    const val OTHELLO = "othello"
    const val SOLITAIRE = "solitaire"
    const val HEARTS = "hearts"
    const val INTERCEPT = "intercept"
    const val CARNIVORE = "carnivore"
    const val MURDLE = "murdle"
    const val BATTLESHIP = "battleship"
    const val WORDLE = "wordle"

    private const val PREFS = "games"
    private const val LAST_PAGE = "last_page"

    private fun prefs(context: Context) =
        Storage.of(context).getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun state(context: Context, id: String): String? = prefs(context).getString("state_$id", null)

    fun saveState(context: Context, id: String, state: String) =
        prefs(context).edit().putString("state_$id", state).apply()

    fun clearState(context: Context, id: String) =
        prefs(context).edit().remove("state_$id").putBoolean("active_$id", false).apply()

    fun active(context: Context, id: String): Boolean = prefs(context).getBoolean("active_$id", false)
    fun setActive(context: Context, id: String, active: Boolean) =
        prefs(context).edit().putBoolean("active_$id", active).apply()

    fun lastPage(context: Context): String? = prefs(context).getString(LAST_PAGE, null)
    fun setLastPage(context: Context, page: String) = prefs(context).edit().putString(LAST_PAGE, page).apply()

    fun best(context: Context, key: String): Long =
        (prefs(context).all["best_$key"] as? Number)?.toLong() ?: 0L
    fun setBest(context: Context, key: String, value: Long) {
        if (value > best(context, key)) prefs(context).edit().putLong("best_$key", value).apply()
    }
    /** 시간 기록처럼 작을수록 좋은 값. 0 은 모름. */
    fun setBestLow(context: Context, key: String, value: Long) {
        val old = best(context, key)
        if (value > 0 && (old == 0L || value < old)) prefs(context).edit().putLong("best_$key", value).apply()
    }

    fun wins(context: Context, id: String): Int = prefs(context).getInt("win_$id", 0)
    fun losses(context: Context, id: String): Int = prefs(context).getInt("loss_$id", 0)
    fun addWin(context: Context, id: String) = prefs(context).edit().putInt("win_$id", wins(context, id) + 1).apply()
    fun addLoss(context: Context, id: String) = prefs(context).edit().putInt("loss_$id", losses(context, id) + 1).apply()

    fun count(context: Context, key: String): Int = prefs(context).getInt("count_$key", 0)
    fun addCount(context: Context, key: String, by: Int = 1) =
        prefs(context).edit().putInt("count_$key", count(context, key) + by).apply()
    fun setCount(context: Context, key: String, value: Int) =
        prefs(context).edit().putInt("count_$key", value).apply()
}
