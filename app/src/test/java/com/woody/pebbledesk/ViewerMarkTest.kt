package com.woody.pebbledesk

import com.woody.pebbledesk.Db.rows
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 읽는 화면이 떴는지·닫혔는지 적기([ReadingLog.viewerShown]: Libby·KOReader). 지난 때로 적을 때의 순서 규칙. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ViewerMarkTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val ko = "org.koreader.launcher"
    private val libby = "com.overdrive.mobile.android.libby"
    private val now = System.currentTimeMillis()

    @After fun tearDown() {
        ReadingLog.clearCache()
        Db.closeForTest()
        Storage.of(app).getDatabasePath("pebble.db").parentFile?.deleteRecursively()
    }

    private fun marks(pkg: String) =
        Db.get(app).rows("SELECT at, shown FROM viewer_mark WHERE pkg = ? ORDER BY at, rowid", arrayOf(pkg)) { (it.getLong(0) - now) to (it.getInt(1) == 1) }

    /** DB 에 적힌 표시(읽은 시간을 셀 때처럼 때 순, 같은 때면 적은 순) */
    private fun stored() =
        Db.get(app).rows("SELECT at, pkg, shown FROM viewer_mark ORDER BY at, rowid") { ReadingLog.Mark(it.getLong(0), it.getString(1), it.getInt(2) == 1) }

    @Test fun `적힌 것이 없을 때 KOReader 는 열림, Libby 는 닫힘으로 보고 같은 것은 적지 않는다`() {
        ReadingLog.viewerShown(app, ko, shown = true, at = now)
        ReadingLog.viewerShown(app, libby, shown = false, at = now)
        assertEquals(emptyList<Pair<Long, Boolean>>(), marks(ko))
        assertEquals(emptyList<Pair<Long, Boolean>>(), marks(libby))
    }

    @Test fun `앞서 적은 것보다 앞의 때 - 지금 상태는 앞서 적은 때부터, 지난 일은 버린다`() {
        ReadingLog.viewerShown(app, ko, shown = false, at = now)
        ReadingLog.viewerShown(app, ko, shown = true, at = now + 10_000)
        // 지난 일(앞 책을 닫은 때)이 앞서 적은 것보다 앞이면 버린다
        ReadingLog.viewerShown(app, ko, shown = false, at = now + 5_000, current = false)
        assertEquals(listOf(0L to false, 10_000L to true), marks(ko))
        // 지금 상태(닫힘)는 앞서 적은 때(+10초)부터로
        ReadingLog.viewerShown(app, ko, shown = false, at = now + 5_000)
        assertEquals(listOf(0L to false, 10_000L to true, 10_000L to false), marks(ko))
    }

    @Test fun `같은 때 같은 상태는 다시 적지 않는다(같은 초를 다시 볼 때)`() {
        ReadingLog.viewerShown(app, ko, shown = false, at = now, current = false)
        ReadingLog.viewerShown(app, ko, shown = true, at = now)
        ReadingLog.viewerShown(app, ko, shown = false, at = now, current = false)
        ReadingLog.viewerShown(app, ko, shown = true, at = now)
        assertEquals(listOf(0L to false, 0L to true), marks(ko))
    }

    @Test fun `지난 일도 앞서 적은 것보다 뒤면 그 때로 적는다(책을 바꾼 사이)`() {
        ReadingLog.viewerShown(app, ko, shown = false, at = now)
        ReadingLog.viewerShown(app, ko, shown = true, at = now + 10_000, current = false)
        ReadingLog.viewerShown(app, ko, shown = false, at = now + 20_000, current = false)
        ReadingLog.viewerShown(app, ko, shown = true, at = now + 30_000)
        assertEquals(listOf(0L to false, 10_000L to true, 20_000L to false, 30_000L to true), marks(ko))
    }

    @Test fun `자동 추가가 꺼지면 그때부터 KOReader 는 열림, Libby 는 닫힘(끝나는 알림 없이 내려가 남은 표시를 덮는다)`() {
        // 마지막으로 맞출 때 KOReader 책은 닫혀 있었고 Libby 읽는 화면은 떠 있었다. 그 뒤 업데이트·앱 오류로 서비스가 알림 없이 내려갔다
        ReadingLog.viewerShown(app, ko, shown = false, at = now)
        ReadingLog.viewerShown(app, libby, shown = true, at = now)
        ReadingLog.watchStopped(app, at = now + 10_000)
        // 두 번 꺼져도 한 번만
        ReadingLog.watchStopped(app, at = now + 20_000)
        assertEquals(listOf(0L to false, 10_000L to true), marks(ko))
        assertEquals(listOf(0L to true, 10_000L to false), marks(libby))
        // 꺼진 동안 KOReader 가 앞에 있던 시간은 다 센다(닫힌 표시 때문에 하나도 세지 않던 것)
        ReadingLog.clearCache()
        val zone = java.time.ZoneId.systemDefault()
        val days = ReadingLog.totalsOf(
            listOf(ReadingLog.Span(ko, now + 30_000, now + 90_000, hosted = true), ReadingLog.Span(libby, now + 30_000, now + 90_000, hosted = true)),
            emptyList(), zone, stored(),
        )
        assertEquals(60_000L, days.values.sumOf { day -> day.filterKeys { it.pkg == ko }.values.sum() })
        assertEquals(0L, days.values.sumOf { day -> day.filterKeys { it.pkg == libby }.values.sum() })
    }

    @Test fun `적힌 것이 없으면 꺼져도 적지 않고, 표시로 자르지 않는 앱은 적지 않는다`() {
        ReadingLog.watchStopped(app, at = now)
        assertEquals(emptyList<String>(), Db.get(app).rows("SELECT pkg FROM viewer_mark") { it.getString(0) })
    }

    @Test fun `꺼진 때부터 열림으로 둔 뒤 다시 붙어 맞출 때 책이 그보다 앞에 닫혔으면 꺼진 때부터 닫힘`() {
        ReadingLog.viewerShown(app, ko, shown = false, at = now)
        ReadingLog.watchStopped(app, at = now + 10_000)
        // 다시 붙어 KOReader 를 맞춘다: 지금 책은 +5초에 닫혔다(지난 일은 버리고 지금 상태만 꺼진 때부터)
        ReadingLog.viewerShown(app, ko, shown = true, at = now + 2_000, current = false)
        ReadingLog.viewerShown(app, ko, shown = false, at = now + 5_000)
        assertEquals(listOf(0L to false, 10_000L to true, 10_000L to false), marks(ko))
        // 같은 때 열림 → 닫힘은 DB 에서 다시 읽어도 닫힘
        ReadingLog.clearCache()
        val zone = java.time.ZoneId.systemDefault()
        val days = ReadingLog.totalsOf(listOf(ReadingLog.Span(ko, now + 30_000, now + 90_000, hosted = true)), emptyList(), zone, stored())
        assertEquals(0L, days.values.sumOf { it.values.sum() })
    }

    @Test fun `기록 보기의 오늘 확인 - 앞에 있던 시간과 표시로 잘라 센 시간을 앱마다`() {
        org.robolectric.Shadows.shadowOf(app.getSystemService(android.app.AppOpsManager::class.java))
            .setMode(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), app.packageName, android.app.AppOpsManager.MODE_ALLOWED)
        val zone = java.time.ZoneId.systemDefault()
        val start = java.time.LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli() + 1000
        val end = minOf(now - 1000, start + 600_000)
        val usage = org.robolectric.Shadows.shadowOf(app.getSystemService(android.app.usage.UsageStatsManager::class.java))
        for ((type, at) in listOf(android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND to start, android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND to end))
            usage.addEvent(org.robolectric.shadows.ShadowUsageStatsManager.EventBuilder.buildEvent()
                .setPackage(ko).setClass("org.koreader.launcher.MainActivity").setEventType(type).setTimeStamp(at).build())
        // 앞에 온 뒤 1/10 에 책을 닫았다
        val closed = start + (end - start) / 10
        ReadingLog.viewerShown(app, ko, shown = false, at = closed)
        assertEquals(mapOf(ko to ((end - start) to (closed - start))), ReadingLog.todayCheck(app, setOf(ko)))
        // 홈처럼 자동 추가가 붙어 있지 않으면 내려간 때(여기서는 지금, 앞에 있던 구간 뒤)부터 KOReader 열림
        val stoppedAt = ReadingLog.marksFrom(app, start).last()
        assertEquals(ko to true, stoppedAt.pkg to stoppedAt.shown)
        assertTrue("${stoppedAt.at} >= $end", stoppedAt.at >= end)
        // 오늘 적은 것과 앱마다 그 앞의 마지막 것(오늘 시작 때의 상태)만
        ReadingLog.viewerShown(app, libby, shown = true, at = start - 7_200_000)
        ReadingLog.viewerShown(app, libby, shown = false, at = start - 3_600_000)
        assertEquals(listOf(start - 3_600_000 to libby, closed to ko, stoppedAt.at to ko), ReadingLog.marksFrom(app, start).map { it.at to it.pkg })
    }
}
