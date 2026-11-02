package com.woody.pebbledesk

import android.app.AppOpsManager
import android.app.usage.UsageEvents.Event
import android.app.usage.UsageStatsManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowUsageStatsManager
import java.time.LocalDate
import java.time.ZoneId

/** 화면이 꺼져도 읽는 화면이 앞에 그대로 있는 기기(Bigme, 이슈 #13): 다시 켜지면 이어 센다. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadingDozeTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val ko = "org.koreader.launcher"
    private val viewer = "org.koreader.launcher.MainActivity"
    private val min = 60_000L
    /** 오늘 안에서 40분 전(자정 직후면 건너뛴다) */
    private val base = System.currentTimeMillis() - 40 * min

    @Before fun setUp() {
        val zone = ZoneId.systemDefault()
        assumeTrue(base >= LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli())
        shadowOf(app.getSystemService(AppOpsManager::class.java))
            .setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), app.packageName, AppOpsManager.MODE_ALLOWED)
    }

    @After fun tearDown() {
        ReadingLog.clearCache()
        Db.closeForTest()
        Storage.of(app).getDatabasePath("pebble.db").parentFile?.deleteRecursively()
    }

    private fun events(vararg list: Pair<Int, Long>) {
        val usage = shadowOf(app.getSystemService(UsageStatsManager::class.java))
        for ((type, at) in list) {
            val b = ShadowUsageStatsManager.EventBuilder.buildEvent().setEventType(type).setTimeStamp(base + at)
            if (type == Event.MOVE_TO_FOREGROUND || type == Event.MOVE_TO_BACKGROUND) b.setPackage(ko).setClass(viewer) else b.setPackage("android")
            usage.addEvent(b.build())
        }
    }

    private fun front() = ReadingLog.todayCheck(app, setOf(ko))?.get(ko)?.first ?: 0L

    @Test fun `앞에 그대로 있던 채 켜지면 꺼진 동안도, 오래 꺼져 있었으면 켜진 때부터`() {
        events(
            Event.MOVE_TO_FOREGROUND to 0,
            Event.SCREEN_NON_INTERACTIVE to 1 * min, Event.SCREEN_INTERACTIVE to 3 * min,
            Event.SCREEN_NON_INTERACTIVE to 4 * min, Event.SCREEN_INTERACTIVE to 20 * min,
            Event.MOVE_TO_BACKGROUND to 22 * min,
        )
        assertEquals(4 * min + 2 * min, front())
    }

    @Test fun `잠금 화면이 떴으면 꺼진 동안은 빼고 잠금을 푼 때부터`() {
        events(
            Event.MOVE_TO_FOREGROUND to 0,
            Event.SCREEN_NON_INTERACTIVE to 1 * min, Event.KEYGUARD_SHOWN to 1 * min,
            Event.SCREEN_INTERACTIVE to 2 * min, Event.KEYGUARD_HIDDEN to 3 * min,
            Event.MOVE_TO_BACKGROUND to 5 * min,
        )
        assertEquals(1 * min + 2 * min, front())
    }

    @Test fun `꺼진 동안 읽는 화면이 내려갔으면 켜져도 잇지 않는다(보통 기기)`() {
        events(
            Event.MOVE_TO_FOREGROUND to 0,
            Event.SCREEN_NON_INTERACTIVE to 1 * min, Event.MOVE_TO_BACKGROUND to 1 * min,
            Event.SCREEN_INTERACTIVE to 2 * min,
        )
        assertEquals(1 * min, front())
    }
}
