package com.woody.booklauncher

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 색 테마: 1.7.9 까지의 흑백·컬러 설정 옮기기, 테마의 색 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PaletteTest {
    private val sp get() = RuntimeEnvironment.getApplication().getSharedPreferences("palette-test", Context.MODE_PRIVATE)

    @Before fun clear() {
        sp.edit().clear().commit()
    }

    @Test fun `새로 깔면 흑백, 아무것도 적지 않는다`() {
        assertEquals(Palette.MONO, HomePrefs.palette(sp))
        assertFalse(sp.contains("theme"))
    }

    @Test fun `1_7_9 까지 컬러를 쓰던 사람은 등불 아래(amber)로 옮기고 옛 값은 지운다`() {
        sp.edit().putString("palette", "color").commit()
        assertEquals(Palette.AMBER, HomePrefs.palette(sp))
        assertEquals("amber", sp.getString("theme", null))
        assertFalse(sp.contains("palette"))
        assertNull("옛 값 color 는 테마 키가 아니다", Palette.of("color"))
    }

    @Test fun `1_7_9 까지 흑백을 고른 사람은 흑백`() {
        sp.edit().putString("palette", "mono").commit()
        assertEquals(Palette.MONO, HomePrefs.palette(sp))
        assertEquals("mono", sp.getString("theme", null))
        assertFalse(sp.contains("palette"))
    }

    @Test fun `옮긴 뒤에는 고른 테마`() {
        sp.edit().putString("palette", "color").commit()
        HomePrefs.palette(sp)
        sp.edit().putString("theme", "camellia").commit()
        assertEquals(Palette.CAMELLIA, HomePrefs.palette(sp))
    }

    @Test fun `모르는 테마(더 새 판)는 흑백으로 보고 값은 그대로`() {
        sp.edit().putString("theme", "someday").commit()
        assertEquals(Palette.MONO, HomePrefs.palette(sp))
        assertEquals("someday", sp.getString("theme", null))
    }

    @Test fun `저장 값은 테마마다 다르다`() {
        assertEquals(Palette.entries.size, Palette.entries.map { it.key }.toSet().size)
    }

    @Test fun `컬러 테마의 색면·제목 색은 고르기 창의 색 칸 그대로, 설정의 제목은 검정`() {
        val t = Palette.MOSS
        assertEquals(t.swatch[0], t.field)
        assertEquals(Ui.BLACK, t.title(Palette.Area.SETTINGS))
        assertEquals(t.swatch[1], t.title(Palette.Area.HOME))
        assertEquals(t.swatch[1], t.goal)
    }

    @Test fun `흑백은 칠하지 않고 제목은 검정`() {
        assertNull(Palette.MONO.field)
        Palette.Area.entries.forEach { assertEquals(Ui.BLACK, Palette.MONO.title(it)) }
    }
}
