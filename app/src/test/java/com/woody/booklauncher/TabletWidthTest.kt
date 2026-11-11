package com.woody.booklauncher

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 태블릿 폭 규칙(`Ui.designWidthDp`): 6~7인치 리더는 지금처럼 572dp, 10인치 태블릿은 표준 dp 의 1.32배까지만
 * 키운 폭(실제 짧은 변 / 1.32)으로 그린다. BOOX 가 앱별 DPI 를 덮어써도 실제 크기(xdpi)로 가른다.
 */
class TabletWidthTest {
    private fun width(shortPx: Int, densityDpi: Int, xdpi: Float, ydpi: Float) =
        designWidthDp(shortPx, densityDpi, xdpi, ydpi)

    @Test
    fun `6인치 리더는 572dp 그대로`() {
        assertEquals(572f, width(1072, 300, 300f, 300f), 0.5f)
        // 메이북 E6 처럼 밀도만 320 으로 선언한 기기도 같다
        assertEquals(572f, width(1072, 320, 300f, 300f), 0.5f)
    }

    @Test
    fun `10인치 227ppi 태블릿은 751dp`() {
        // BOOX Note3: 1404×1872, 226.4 x 227.1 dpi
        assertEquals(751f, width(1404, 240, 226.4f, 227.1f), 1f)
    }

    @Test
    fun `BOOX 가 앱별 DPI 를 덮어써도 10인치로 본다`() {
        // 선언 밀도만 350 으로 바뀌어도 물리 밀도는 그대로다
        assertEquals(751f, width(1404, 350, 226.4f, 227.1f), 1f)
    }

    @Test
    fun `물리 밀도를 못 믿으면 선언 밀도로 센다`() {
        // xdpi 가 160 으로 오는 기기: 표준 dp(572)와 너무 달라 물리 밀도를 버린다
        assertEquals(572f, width(1072, 300, 160f, 160f), 0.5f)
    }

    @Test
    fun `7_8인치(768dp)는 아직 572dp`() {
        assertEquals(572f, width(1440, 300, 300f, 300f), 0.5f)
    }

    @Test
    fun `13인치가 넘어도 폭은 1000dp 까지`() {
        // 2200×1650, 227ppi → 실제 짧은 변 1163dp. 1163/1.32 = 881 이라 그대로
        assertEquals(881f, width(1650, 240, 227f, 227f), 1f)
    }
}
