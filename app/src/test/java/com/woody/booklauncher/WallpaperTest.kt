package com.woody.booklauncher

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 홈 배경 사진([Wallpaper]): 화면을 채우는 가운데 자르기와 흰색과 섞기 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WallpaperTest {
    @Test fun cropKeepsScreenRatioInTheMiddle() {
        // 세로 사진을 세로 화면(1072×1456)에: 위아래를 조금 자른다
        assertEquals(Rect(0, 57, 1280, 57 + 1739), Wallpaper.crop(1280, 1854, 1072, 1456))
        // 가로 사진을 세로 화면에: 양옆을 자른다
        val r = Wallpaper.crop(2000, 1000, 1072, 1456)
        assertEquals(1000, r.height())
        assertEquals(736, r.width())
        assertEquals(2000 - r.right, r.left)
        // 비율이 같으면 그대로
        assertEquals(Rect(0, 0, 536, 728), Wallpaper.crop(536, 728, 1072, 1456))
    }

    /** 섞은 뒤 밝기(빨강 줄): r·g·b 를 넣어 */
    private fun washed(strength: Int, color: Boolean, r: Float, g: Float, b: Float): Float {
        val m = Wallpaper.wash(strength, color).array
        return m[0] * r + m[1] * g + m[2] * b + m[4]
    }

    @Test fun washBlendsWithWhite() {
        // 0% 는 흰색, 100% 는 사진 그대로, 그 사이는 흰색 쪽으로
        assertEquals(255f, washed(0, true, 0f, 0f, 0f), 0.01f)
        assertEquals(40f, washed(100, true, 40f, 90f, 200f), 0.01f)
        assertEquals(255 - 0.3f * 255, washed(30, true, 0f, 0f, 0f), 0.01f)
        // 흑백은 회색으로: 순수 초록 → 0.587 밝기
        assertEquals(0.587f * 255, washed(100, false, 0f, 255f, 0f), 0.01f)
        assertEquals(255f, washed(50, false, 255f, 255f, 255f), 0.01f)
    }
}
