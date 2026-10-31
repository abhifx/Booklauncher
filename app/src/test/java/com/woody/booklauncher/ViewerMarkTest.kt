package com.woody.booklauncher

import com.woody.booklauncher.Db.rows
import org.junit.After
import org.junit.Assert.assertEquals
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
        Db.get(app).rows("SELECT at, shown FROM viewer_mark WHERE pkg = ? ORDER BY at", arrayOf(pkg)) { (it.getLong(0) - now) to (it.getInt(1) == 1) }

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
}
