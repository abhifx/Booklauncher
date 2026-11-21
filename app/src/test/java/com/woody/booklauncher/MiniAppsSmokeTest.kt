package com.woody.booklauncher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 미니 앱 허브에서 새 앱 다섯을 열어 그린다. 화면을 만드는 도중 나는 예외를 잡는다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MiniAppsSmokeTest {
    @After
    fun closeDatabase() {
        Db.closeForTest()
        ReadingLog.clearCache()
    }

    @Test
    fun `미니 앱을 모두 열 수 있다`() {
        val activity = Robolectric.buildActivity(MiniAppsActivity::class.java).setup().get()
        val names = listOf(
            R.string.mini_calc,
            R.string.mini_notes,
            R.string.mini_timer,
            R.string.mini_pet,
            R.string.mini_quest,
            R.string.mini_habits,
            R.string.mini_todo,
            R.string.mini_counter,
            R.string.mini_quotes,
            R.string.mini_clock,
        )
        for (id in names) {
            val cell = findCell(activity.window.decorView, activity.getString(id))
            assertNotNull("없는 미니 앱: ${activity.getString(id)}", cell)
            cell!!.performClick()
            shadowOf(Looper.getMainLooper()).idle()
            draw(activity)
            activity.onBackPressed()
            shadowOf(Looper.getMainLooper()).idle()
            draw(activity)
        }
    }

    private fun draw(activity: MiniAppsActivity) {
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

    private fun findCell(root: View, text: String): View? {
        if (root is TextView && root.text.toString() == text) {
            var p: View? = root
            while (p != null) {
                if (p.isClickable) return p
                p = p.parent as? View
            }
        }
        if (root is ViewGroup) for (i in 0 until root.childCount) {
            findCell(root.getChildAt(i), text)?.let { return it }
        }
        return null
    }
}
