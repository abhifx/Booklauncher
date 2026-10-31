package com.woody.booklauncher.readers

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.graphics.Rect
import com.woody.booklauncher.ReadingLog
import com.woody.booklauncher.Storage
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** BookFusion 의 읽는 화면(난독화된 이름 대신 매니페스트 속성)과 같은 책 칸 둘의 표지 자리. 패키지 정보·Rect 때문에 Robolectric */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookFusionTest {
    private val pkg = "com.bookfusion.android.reader"
    private val context: Context = RuntimeEnvironment.getApplication()

    /** 매니페스트에서 본 속성 그대로(launchMode, configChanges) */
    private val home = Triple("$pkg.activities.HomeActivity", ActivityInfo.LAUNCH_SINGLE_INSTANCE, 0xd80)
    private val splash = Triple("$pkg.activities.SplashActivity", ActivityInfo.LAUNCH_MULTIPLE, 0)
    private fun reader(name: String) = Triple(name, ActivityInfo.LAUNCH_SINGLE_TOP, 0x1fb4)
    private fun login(name: String) = Triple(name, ActivityInfo.LAUNCH_MULTIPLE, 0x4b4)
    private fun other(name: String) = Triple(name, ActivityInfo.LAUNCH_MULTIPLE, 0x5a0)

    /** 그 판을 깐다(업데이트한 때 [updated]) */
    private fun install(updated: Long, vararg acts: Triple<String, Int, Int>) {
        val app = ApplicationInfo().apply { packageName = pkg }
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = pkg
            lastUpdateTime = updated
            applicationInfo = app
            activities = acts.map { (name, mode, config) ->
                ActivityInfo().apply { this.name = name; packageName = pkg; applicationInfo = app; launchMode = mode; configChanges = config }
            }.toTypedArray()
        })
    }

    private fun viewer(cls: String) = ReaderApps.isViewer(context, pkg, cls)

    /** 예전 판의 읽는 화면 이름은 테스트마다 비운다(저장소 컨텍스트는 테스트 사이에 남는다) */
    @Before fun clearPast() {
        Storage.of(context).getSharedPreferences("bookfusion", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun `판마다 이름이 바뀌어도 매니페스트 속성으로 읽는 화면`() {
        // 2.23.15 → 2.24.0 → 2.24.1 로 업데이트
        install(1, home, splash, reader("o.startlambda1"), reader("o.CameraEffectTextures"), login("o.accesssetAnonymousAppDeviceGUIDcp"), other("o.MenuHostHelper"))
        assertEquals(true, viewer("o.startlambda1"))
        assertEquals(true, viewer("o.CameraEffectTextures"))
        assertEquals(false, viewer("o.accesssetAnonymousAppDeviceGUIDcp"))
        assertEquals(false, viewer("o.MenuHostHelper"))
        assertEquals(false, viewer(home.first))
        install(2, home, splash, reader("o.onSuccesslambda10"), reader("o.getCaption"), login("o.createInstance"), other("o.onMenuClosed"))
        assertEquals(true, viewer("o.onSuccesslambda10"))
        assertEquals(false, viewer("o.onMenuClosed"))
        // 업데이트 뒤 이름이 같은 다른 화면(2.24.0 의 다른 화면 이름이 2.24.1 에서 읽는 화면이 됨)도 새 판으로 본다
        install(3, home, splash, reader("o.getLayoutResId"), reader("o.onMenuClosed"), login("o.createInstancedefault"))
        assertEquals(true, viewer("o.getLayoutResId"))
        assertEquals(true, viewer("o.onMenuClosed"))
    }

    @Test fun `업데이트 전에 읽은 화면은 업데이트 뒤에도 읽는 화면(사용 기록을 늦게 셀 때)`() {
        install(20, home, reader("o.startlambda1"), other("o.MenuHostHelper"))
        assertEquals(true, viewer("o.startlambda1"))
        // 새 판에는 없는 예전 판의 읽는 화면: 업데이트 전에 읽은 시간을 업데이트 뒤에 세도 읽는 화면
        install(21, home, reader("o.onSuccesslambda10"), other("o.dispatchUnhandledKeyEventBeforeHierarchy"))
        assertEquals(true, viewer("o.startlambda1"))
        assertEquals(true, viewer("o.onSuccesslambda10"))
        // 예전 판에서 읽는 화면이 아니던 것은 그대로 아니다
        assertEquals(false, viewer("o.MenuHostHelper"))
        // 예전 판의 읽는 화면 이름이 새 판에서 다른 화면이 되면 새 판을 따른다
        install(22, home, reader("o.getLayoutResId"), other("o.startlambda1"))
        assertEquals(false, viewer("o.startlambda1"))
        assertEquals(true, viewer("o.onSuccesslambda10"))
    }

    @Test fun `판 정보를 못 읽으면 기억하지 않고 예전 판의 읽는 화면만 센다`() {
        install(30, home, reader("o.getLayoutResId"), other("o.onMenuClosed"))
        assertEquals(true, viewer("o.getLayoutResId"))
        // 지워져 판 정보를 못 읽는다: 예전 판의 읽는 화면만(지우기 전에 읽은 시간을 셀 때)
        shadowOf(context.packageManager).removePackage(pkg)
        assertEquals(false, viewer("o.onMenuClosed"))
        assertEquals(true, viewer("o.getLayoutResId"))
        // 다시 깔면 그 판으로 다시 읽는다(못 읽은 것을 기억해 두지 않았다)
        install(31, home, reader("o.onMenuClosed"), other("o.getLayoutResId"))
        assertEquals(true, viewer("o.onMenuClosed"))
        assertEquals(false, viewer("o.getLayoutResId"))
    }

    @Test fun `안드로이드가 더한 바뀜이 있어도 속성이 다 들어 있으면 읽는 화면`() {
        val info = { mode: Int, config: Int -> ActivityInfo().apply { name = "o.a"; launchMode = mode; configChanges = config } }
        val mccMnc = ActivityInfo.CONFIG_MCC or ActivityInfo.CONFIG_MNC
        assertEquals(setOf("o.a"), BookFusion.readerActivities(listOf(info(ActivityInfo.LAUNCH_SINGLE_TOP, 0x1fb4 or mccMnc))))
        assertEquals(emptySet<String>(), BookFusion.readerActivities(listOf(info(ActivityInfo.LAUNCH_MULTIPLE, 0x1fb4))))
        assertEquals(emptySet<String>(), BookFusion.readerActivities(listOf(info(ActivityInfo.LAUNCH_SINGLE_TOP, 0x4b4))))
    }

    @Test fun `읽은 시간도 그 앱에서만 그 규칙으로 센다`() {
        install(10, home, reader("o.getLayoutResId"))
        assertEquals(true, ReadingLog.isReaderScreen(context, pkg, "o.getLayoutResId"))
        assertEquals(false, ReadingLog.isReaderScreen(context, "com.example.other", "o.getLayoutResId"))
        // 다른 앱은 예전 그대로(적어 둔 읽는 화면, 이름 규칙)
        assertEquals(true, ReadingLog.isReaderScreen(context, "com.initialcoms.ridi", "com.ridi.books.viewer.reader.epub.EPubReaderActivity"))
        assertEquals(true, ReadingLog.isReaderScreen(context, "com.flyersoft.moonreader", "com.flyersoft.moonreader.ActivityTxt"))
        assertEquals(false, ReadingLog.isReaderScreen(context, "com.initialcoms.ridi", "com.ridi.books.viewer.main.activity.MainActivity"))
    }

    @Test fun `찍은 뒤 자리 확인은 같은 책 칸 둘 중 하나면 된다`() {
        val reading = Rect(557, 277, 737, 532)
        val row = Rect(30, 1041, 150, 1221)
        val now = listOf(Cell("책", "", -1, -1, reading, reading), Cell("책", "", -1, -1, row, row))
        // '지금 읽는 책' 줄(위)과 격자 보기(아래)에 같은 책
        assertEquals(true, BookFusion.coverStill(now, "책", reading))
        assertEquals(true, BookFusion.coverStill(now, "책", row))
        assertEquals(false, BookFusion.coverStill(now, "책", Rect(0, 0, 10, 10)))
    }
}
