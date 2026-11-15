package com.woody.booklauncher

import android.app.Activity
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 홈 자주 쓰는 앱 칸(아이콘 타일 + 이름). 누르기는 칸이 받는다: 아이콘 타일이 누르기를 먹으면
 * 타일을 눌렀을 때(칸의 대부분) 아무 일도 일어나지 않는다(3.0.0 의 자리).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FavoriteTileClickTest {
    @Test
    fun `아이콘 타일을 눌러도 칸의 누르기가 온다`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val cell = activity.vbox()
        var clicks = 0
        cell.setOnClickListener { clicks++ }
        cell.addView(IconTile(activity), LinearLayout.LayoutParams(100, 100))
        activity.setContentView(cell)
        cell.measure(
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
        )
        cell.layout(0, 0, 100, 100)
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 50f, 50f, 0)
        val up = MotionEvent.obtain(0, 16, MotionEvent.ACTION_UP, 50f, 50f, 0)
        cell.dispatchTouchEvent(down)
        cell.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, clicks)
    }
}
