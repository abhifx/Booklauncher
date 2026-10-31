package com.woody.pebbledesk.readers

import com.woody.pebbledesk.ReadingLog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 아이리더 기본 리더의 읽는 화면(클래스 앞부분을 몰라 마지막 이름으로)만 읽은 시간으로 센다 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IReaderTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val pkg = IReader.pkg

    @Test fun `읽는 화면은 앞부분과 상관없이 ActivityRead`() {
        assertTrue(ReadingLog.isReaderScreen(context, pkg, "$pkg.ActivityRead"))
        assertTrue(ReadingLog.isReaderScreen(context, pkg, "com.zhangyue.iReader.read.ui.ActivityRead"))
    }

    @Test fun `최근 작업 화면은 읽는 화면이 아니다`() {
        assertFalse(ReadingLog.isReaderScreen(context, pkg, "$pkg.RecentTaskListActivity"))
        assertFalse(ReadingLog.isReaderScreen(context, pkg, "$pkg.ActivityReadSetting"))
    }

    @Test fun `읽은 시간을 세는 앱이다`() {
        assertTrue(pkg in ReaderApps.packages)
    }
}
