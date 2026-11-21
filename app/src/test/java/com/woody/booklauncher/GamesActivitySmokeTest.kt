package com.woody.booklauncher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 게임 화면이 모두 만들어지는지(그리기 전에 죽지 않는지) 보는 연기 시험.
 * 판 규칙은 games/GameLogicTest 가 보고, 여기서는 허브에서 각 게임을 열었다 닫는다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GamesActivitySmokeTest {
    @Test
    fun `허브에서 모든 게임을 열 수 있다`() {
        val activity = Robolectric.buildActivity(GamesActivity::class.java).setup().get()
        val names = listOf(
            R.string.game_2048,
            R.string.game_minesweeper,
            R.string.game_sudoku,
            R.string.game_nonogram,
            R.string.game_connect4,
            R.string.game_checkers,
            R.string.game_chess,
            R.string.game_othello,
            R.string.game_hearts,
            R.string.game_intercept,
            R.string.game_carnivore,
            R.string.game_murdle,
            R.string.game_solitaire,
            R.string.game_battleship,
            R.string.game_wordle,
        )
        for (id in names) {
            var cell = findCell(activity.window.decorView, activity.getString(id))
            var guard = 0
            while (cell == null && guard++ < 3) {
                nextHubPage(activity)
                cell = findCell(activity.window.decorView, activity.getString(id))
            }
            assertNotNull("게임 칸: ${activity.getString(id)}", cell)
            cell!!.performClick()
            shadowOf(Looper.getMainLooper()).idle()
            draw(activity)
            activity.onBackPressed()
            shadowOf(Looper.getMainLooper()).idle()
            draw(activity)
        }
    }

    /** 허브가 여러 쪽이면 다음 쪽으로 넘긴다. */
    private fun nextHubPage(activity: GamesActivity) {
        val m = GamesActivity::class.java.getDeclaredMethod("hubTurn", Boolean::class.javaPrimitiveType)
        m.isAccessible = true
        m.invoke(activity, true)
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** 실제로 한 번 그려 본다(판 그리기에서 나는 오류를 잡는다). */
    private fun draw(activity: GamesActivity) {
        val root = activity.window.decorView
        val w = 1072
        val h = 1456
        root.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, w, h)
        root.draw(Canvas(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)))
    }

    /** [text] 를 가진 TextView 를 품은, 누를 수 있는 가장 가까운 위 칸. */
    private fun findCell(root: View, text: String): View? {
        if (root is TextView && root.text.toString() == text) {
            var p: View? = root
            while (p != null) {
                if (p.isClickable && p.hasOnClickListener()) return p
                p = p.parent as? View
            }
        }
        if (root is ViewGroup) for (i in 0 until root.childCount) {
            findCell(root.getChildAt(i), text)?.let { return it }
        }
        return null
    }

    private fun View.hasOnClickListener(): Boolean {
        // Android has no public getter; the cell is clickable and has a listener from the hub.
        return isClickable
    }
}
