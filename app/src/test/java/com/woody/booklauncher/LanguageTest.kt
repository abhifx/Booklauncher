package com.woody.booklauncher

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 앱 언어(기기 언어 따르기·한국어·영어)의 처음 값과 가르기, 릴리스 설명의 언어 부분 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LanguageTest {
    private val sp get() = RuntimeEnvironment.getApplication().getSharedPreferences("language-test", Context.MODE_PRIVATE)

    @Before fun clear() {
        sp.edit().clear().commit()
    }

    @Test fun `새로 깔면 기기 언어를 따른다`() {
        assertEquals(AppLanguage.SYSTEM, HomePrefs.language(sp))
        assertEquals("system", sp.getString("language", null))
    }

    @Test fun `이미 쓰던 사람은 보던 한국어 그대로`() {
        sp.edit().putBoolean("onboarded", true).commit()
        assertEquals(AppLanguage.KOREAN, HomePrefs.language(sp))
    }

    @Test fun `새로 깔면 자주 쓰는 앱은 격자, 쓰던 사람은 목록 그대로`() {
        HomePrefs.language(sp)
        assertTrue(sp.getBoolean("fav_grid", false))
        sp.edit().clear().putBoolean("onboarded", true).commit()
        HomePrefs.language(sp)
        assertFalse(sp.contains("fav_grid"))
    }

    @Test fun `새로 깔면 홈은 목록, 쓰던 사람은 책장`() {
        HomePrefs.language(sp)
        assertEquals(HomeLayout.LIST, HomePrefs.homeLayout(sp))
        sp.edit().clear().putBoolean("onboarded", true).commit()
        HomePrefs.language(sp)
        assertEquals(HomeLayout.SHELF, HomePrefs.homeLayout(sp))
    }

    @Test fun `1_5_8 까지 영어로 바꿔 둔 사람은 영어, 옛 값은 지운다`() {
        sp.edit().putBoolean("onboarded", true).putBoolean("english", true).commit()
        assertEquals(AppLanguage.ENGLISH, HomePrefs.language(sp))
        assertFalse(sp.contains("english"))
    }

    @Test fun `한 번 정하면 그 뒤 설정 파일이 바뀌어도 그대로`() {
        assertEquals(AppLanguage.SYSTEM, HomePrefs.language(sp))
        sp.edit().putBoolean("onboarded", true).commit()
        assertEquals(AppLanguage.SYSTEM, HomePrefs.language(sp))
    }

    @Test fun `기기 언어 따르기는 한국어 기기만 한국어, 그 밖의 언어는 영어`() {
        assertFalse(AppLanguage.SYSTEM.isEnglish("ko"))
        assertTrue(AppLanguage.SYSTEM.isEnglish("en"))
        assertTrue(AppLanguage.SYSTEM.isEnglish("ja"))
        assertFalse(AppLanguage.KOREAN.isEnglish("en"))
        assertTrue(AppLanguage.ENGLISH.isEnglish("ko"))
    }

    private val notes = """
        ## 새로 생긴 것
        - 새 버전을 홈에서 알려 줍니다.

        ## English
        ### New
        - The home screen tells you when a new version is out.
    """.trimIndent()

    @Test fun `릴리스 설명은 앱 언어 부분만`() {
        assertEquals("## 새로 생긴 것\n- 새 버전을 홈에서 알려 줍니다.", UpdateSheets.notesFor(notes, english = false))
        assertEquals("### New\n- The home screen tells you when a new version is out.", UpdateSheets.notesFor(notes, english = true))
    }

    @Test fun `English 절이 없는 옛 설명은 언어와 상관없이 전체`() {
        val old = "## 고친 것\n- 표지가 바로 보입니다."
        assertEquals(old, UpdateSheets.notesFor(old, english = true))
        assertEquals(old, UpdateSheets.notesFor(old, english = false))
    }
}
