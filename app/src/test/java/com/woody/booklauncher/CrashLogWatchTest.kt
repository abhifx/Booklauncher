package com.woody.booklauncher

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** 지난번에 죽은 오류는 다음에 뜰 때 자동 추가 기록(기록 보기)에 죽은 때로 한 번만 남는다([CrashLog.install]) */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrashLogWatchTest {
    @Test fun `지난 오류를 기록 보기에 한 줄로, 다시 떠도 또 남기지 않는다`() {
        val app = RuntimeEnvironment.getApplication()
        val at = 1_793_000_000_000L
        File(app.createDeviceProtectedStorageContext().filesDir, "last_crash.txt").apply {
            writeText("10/26 09:48 · 1.6.2 · main · IllegalStateException: x\nh4.b:41 ← bg.c:88")
            setLastModified(at)
        }
        CrashLog.install(app)
        CrashLog.install(app)
        val crashes = WatchLog.entries(app).filter { it.kind == WatchLog.Kind.CRASH && it.at == at }
        assertEquals(1, crashes.size)
        assertEquals("IllegalStateException: x", crashes[0].arg)
        assertEquals("h4.b:41 ← bg.c:88", crashes[0].detail)
    }
}
