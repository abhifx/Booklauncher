package com.woody.booklauncher

import android.app.Activity
import com.woody.booklauncher.Db.tx
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 떠나 있는 사이 기록이 바뀌면 돌아올 때 떠 있던 메뉴를 닫는다([Sheet.dismissStale]) */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SheetStaleTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @After fun tearDown() {
        Db.closeForTest()
        Storage.of(app).getDatabasePath("pebble.db").parentFile?.deleteRecursively()
    }

    private fun activity() = Robolectric.buildActivity(Activity::class.java).setup().get().apply { setTheme(R.style.Theme_BookLauncher) }

    @Test fun `기록이 바뀌지 않았으면 그대로, 바뀌었으면 닫히고 닫힘 동작이 불린다`() {
        val a = activity()
        var closed = 0
        val dialog = Sheet(a).header("데미안").onDismiss { closed++ }.show()
        Sheet.dismissStale(a)
        assertTrue(dialog.isShowing)
        Db.get(a).tx { execSQL("INSERT INTO round_start VALUES ('b-1', NULL)") }
        Sheet.dismissStale(a)
        assertFalse(dialog.isShowing)
        // 닫힘 알림은 메인 루퍼로 온다
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(closed == 1)
    }

    @Test fun `다른 화면의 메뉴와 닫지 못하게 한 메뉴는 두고, 다시 띄운 메뉴는 지금 기록으로 잰다`() {
        val a = activity()
        val b = activity()
        val other = Sheet(b).header("다른 화면").show()
        val progress = Sheet(a).header("받는 중").locked().show()
        val notice = Sheet(a).header("자동 추가 꺼짐 안내").centered().keepOnChange().show()
        Db.get(a).tx { execSQL("INSERT INTO round_start VALUES ('b-1', NULL)") }
        Sheet.dismissStale(a)
        assertTrue(other.isShowing)
        assertTrue(progress.isShowing)
        assertTrue(notice.isShowing)
        val again = Sheet(a).header("다시").show()
        Sheet.dismissStale(a)
        assertTrue(again.isShowing)
    }

    @Test fun `되돌린 트랜잭션은 기록이 바뀐 것으로 세지 않는다`() {
        val a = activity()
        val dialog = Sheet(a).header("데미안").show()
        val before = Db.changes()
        runCatching {
            Db.get(a).tx { execSQL("INSERT INTO round_start VALUES ('b-1', NULL)"); error("저장 실패") }
        }
        org.junit.Assert.assertEquals(before, Db.changes())
        Sheet.dismissStale(a)
        assertTrue(dialog.isShowing)
        // 들어간 쓰기는 센다
        Db.get(a).tx { execSQL("INSERT INTO round_start VALUES ('b-2', NULL)") }
        Sheet.dismissStale(a)
        assertFalse(dialog.isShowing)
    }
}
