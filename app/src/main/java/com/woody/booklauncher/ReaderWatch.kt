package com.woody.booklauncher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.woody.booklauncher.readers.AppWindows
import com.woody.booklauncher.readers.Click
import com.woody.booklauncher.readers.ReaderApps

/**
 * 읽고 있는 책 자동 추가(접근성 서비스). 이북 앱([ReaderApps], 앱마다 화면 이름·요소 이름과 판단은 `readers/` 의 앱 파일)의 **서재 화면**에 보이는 제목·저자·진행률·반납일과
 * 사용자가 누른 책 칸만 읽는다. 다른 앱은 시스템이 아예 보내지 않고(`res/xml/reader_watch.xml` 의 packageNames),
 * 읽는 화면은 본문(웹 화면)을 훑지 않고 진행률 줄·메뉴 제목만 이름으로 읽는다. 읽은 내용은 책 목록에만 적고 밖으로 보내지 않는다.
 * 이 서비스는 안드로이드와 잇는 일(알림 받기·창·화면 캡처·서비스 생명)만 하고, 흐름은 [WatchCore], 기록은 [BookRecorder] 가 한다.
 */
class ReaderWatchService : AccessibilityService(), WatchHost {
    private val handler = Handler(Looper.getMainLooper())
    private val clock = object : WatchClock {
        override fun now() = System.currentTimeMillis()
        override fun post(r: Runnable, delayMs: Long) { handler.postDelayed(r, delayMs) }
        override fun remove(r: Runnable) = handler.removeCallbacks(r)
    }
    private val core by lazy { WatchCore(this, clock, ShelfRecorder(this), ReaderApps.ALL) }

    override val context: Context get() = this

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString() ?: return
        val click = if (e.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) Click(e.source, e.text) else null
        // 알림의 요소는 그것을 쓰는 앱만 얻는다(얻을 때마다 그 앱에 묻는다)
        core.onEvent(pkg, e.eventType, e.className?.toString(), click) { e.source }
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        // 지난번 서비스가 내려가 있던 동안(끊긴 때부터, 끊김 알림 없이 끝났으면 살아 있던 마지막 때부터 — 강제 종료·메모리 부족·업데이트·재부팅)
        // 편 책을 모른다. 그 사이 읽은 시간이 그 전에 편 책에 붙지 않게 '책 모름'을 그때로 적는다(어느 책인지 모르면 버린다).
        downSince(this)?.let { ReadingLog.watchStopped(this, it) }
        recordLife(this, connected = true)
        bound = true
        instance = this
        WatchLog.connected(this)
        core.attach()
        // 설정 › 업데이트로 새 판이 깔린 뒤 다시 붙은 것이면 홈을 띄운다(깔린 직후의 알림에서는 막힐 때가 있다).
        Update.reopenHomeAfterUpdate(this, fromService = true)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        bound = false
        instance = null
        recordLife(this, connected = false)
        // 사용자가 접근성에서 끈 것이면(설정에서도 꺼짐) 그 뒤 읽은 시간은 어느 책인지 모른다(꺼지기 전에 마지막으로 편 책에 붙이지 않게).
        // 업데이트·재부팅처럼 설정은 켜진 채 잠깐 떨어진 것이면 다시 붙을 때 끊긴 때로 적는다([downSince]).
        // 끊긴 때(방금 적은 것)와 같은 때로 적어, 셀 때·다시 붙을 때 다시 적지 않게 한다.
        if (!isEnabled(this)) ReadingLog.watchStopped(this, lifeLog(this).second)
        WatchLog.add(this, WatchLog.Kind.UNBOUND)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        bound = false
        instance = null
        core.detach()
        WatchLog.add(this, WatchLog.Kind.DESTROYED)
        WatchLog.flush()
        super.onDestroy()
    }

    /** 지금 앞에 있는 그 앱의 화면(다른 앱·시스템 창이면 null) */
    override fun root(pkg: String): AccessibilityNodeInfo? = rootInActiveWindow?.takeIf { it.packageName == pkg }

    /**
     * 화면에 떠 있는 그 앱의 창들(위에 뜬 메뉴·알림이 먼저, 화면 자체가 끝). 창 목록을 못 얻으면 맨 앞 창만.
     * 이북 앱의 것일 수 없는 창(키보드·접근성 덧창·화면 나눔 막대)은 내용을 받아 오지 않는다(키보드가 떠 있으면 그 트리가 크다).
     */
    override fun windows(pkg: String): AppWindows {
        val active = root(pkg)
        val all = runCatching {
            windows.filter { it.type !in OTHER_WINDOWS }.sortedByDescending { it.layer }.mapNotNull { it.root }.filter { it.packageName == pkg }
        }.getOrDefault(emptyList())
        return AppWindows(active, all.ifEmpty { listOfNotNull(active) })
    }

    override val noActiveWindow: Boolean get() = rootInActiveWindow == null

    override val canScreenshot: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    override fun screenshot(done: (Bitmap) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val shot = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                    ?.copy(Bitmap.Config.ARGB_8888, false)
                result.hardwareBuffer.close()
                shot?.let(done)
            }

            override fun onFailure(errorCode: Int) {
                WatchLog.add(context, WatchLog.Kind.ERROR, "screenshot", "code $errorCode")
            }
        })
    }

    /**
     * [cls] 가 [pkg] 의 액티비티인가. 대화상자가 닫힐 때는 화면 바뀜 알림이 다시 오지 않아, 대화상자를 화면으로 기억하면
     * 그 아래 읽는 화면을 읽지 않게 된다. 액티비티 목록은 크므로(킨들·교보는 수백 개) 앱마다 담아 두고 앱이 업데이트됐을 때만 다시 읽는다.
     * 앱 정보를 못 읽으면 액티비티로 본다.
     */
    override fun isActivity(pkg: String, cls: String): Boolean {
        val updated = runCatching { packageManager.getPackageInfo(pkg, 0).lastUpdateTime }.getOrNull() ?: return true
        val known = activities[pkg]?.takeIf { it.first == updated }?.second
            ?: runCatching { packageManager.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES).activities }.getOrNull()
                ?.mapTo(HashSet()) { it.name }?.also { activities[pkg] = updated to it }
            ?: return true
        return cls in known
    }

    /** 이북 앱마다 (업데이트한 때, 액티비티 이름들)([isActivity]) */
    private val activities = HashMap<String, Pair<Long, Set<String>>>()

    companion object {
        /** 이북 앱의 것일 수 없는 창([windows] 에서 내용을 받아 오지 않는다) */
        private val OTHER_WINDOWS = setOf(
            AccessibilityWindowInfo.TYPE_INPUT_METHOD,
            AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY,
            AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER,
        )

        /**
         * 서비스가 연결·끊긴 때와 끊긴 횟수(기기 정보에서 본다). 켜 둔 서비스가 저절로 꺼지는 기기(Moaan MIX7S)에서
         * 언제 끊겼는지 알려고. 이 앱 프로세스가 왜 끝났는지는 기기 정보가 안드로이드에서 따로 읽는다.
         */
        private fun recordLife(context: Context, connected: Boolean) {
            val prefs = lifePrefs(context)
            val now = System.currentTimeMillis()
            prefs.edit().apply {
                if (connected) putLong(LIFE_CONNECTED, now)
                else putLong(LIFE_DISCONNECTED, now).putInt(LIFE_DISCONNECTS, prefs.getInt(LIFE_DISCONNECTS, 0) + 1)
            }.apply()
        }

        /**
         * (지금 붙어 있지 않은) 서비스가 내려가 그 뒤로 편 책을 모르게 된 때: 정상적으로 끊겼으면 끊긴 때, 끊김 알림(onUnbind) 없이 끝났으면
         * 살아 있던 것이 확실한 마지막 때(연결된 때·이북 앱 알림을 마지막으로 받은 때·편 책을 마지막으로 적은 때 중 늦은 것). 연결된 적이 없으면 null.
         * 다시 붙을 때([onServiceConnected])와 꺼져 있는 채 셀 때([stoppedAt]) '책 모름'을 이때로 적는다.
         */
        internal fun downSince(context: Context): Long? {
            val (connected, disconnected, _) = lifeLog(context)
            val at = when {
                connected == 0L && disconnected == 0L -> return null
                disconnected >= connected -> disconnected
                // 편 책 기록도 살아 있던 서비스가 적은 것(알림 수는 몇 초 늦게 저장되고, 같은 알림을 처리하며 편 책을 적는 것이 그 뒤라 그보다 앞이면
                // '책 모름'이 편 책 줄 앞에 놓여 쓰이지 않는다)
                else -> maxOf(connected, WatchLog.lastEventAt(context), ReadingLog.lastOpenAt(context))
            }
            return minOf(at, System.currentTimeMillis())
        }

        /**
         * 이 프로세스에서 서비스가 붙어 있다(홈과 같은 프로세스). 설정에서 켜져 있어도 붙어 있지 않으면(시스템이 떼어 놓음·저절로 꺼지는 기기·
         * 프로세스가 막 떠 아직 안 붙음) 편 책을 모른다([ReadingLog.days]).
         */
        @Volatile var bound = false
            private set

        /** 붙어 있는 서비스([syncFileApps]) */
        @Volatile private var instance: ReaderWatchService? = null

        /**
         * 책이 열려 있는지를 데이터로 아는 앱(KOReader)을 맞춘다: 홈으로 돌아올 때(읽은 시간·책을 그리기 전에) 부른다. 그 앱을 지켜보지 않으므로
         * 앱 안에서 책을 바꾸거나 닫은 것은 이때 데이터에 적힌 때로 적힌다. 책 열쇠로 가르는 앱(Readest)의 마지막 책 진행률도 그 앱 데이터로.
         * 자동 추가 서비스가 붙어 있을 때만.
         */
        fun syncFileApps() {
            instance?.core?.home()
        }

        /** 꺼져 있는 서비스가 편 책을 모르게 된 때([downSince], 기록이 없으면 지금). 그 뒤 시간이 꺼지기 전에 편 책에 붙지 않게([ReadingLog.days]). */
        fun stoppedAt(context: Context): Long = downSince(context) ?: System.currentTimeMillis()

        /** 마지막 연결 때·마지막 끊김 때(없으면 0)·끊긴 횟수 */
        fun lifeLog(context: Context): Triple<Long, Long, Int> = lifePrefs(context).let {
            Triple(it.getLong(LIFE_CONNECTED, 0), it.getLong(LIFE_DISCONNECTED, 0), it.getInt(LIFE_DISCONNECTS, 0))
        }

        private fun lifePrefs(context: Context) = Storage.of(context).getSharedPreferences("reader_watch_life", Context.MODE_PRIVATE)
        private const val LIFE_CONNECTED = "connected"
        private const val LIFE_DISCONNECTED = "disconnected"
        private const val LIFE_DISCONNECTS = "disconnects"

        /** 시스템 접근성 설정에서 이 서비스가 켜져 있는가 */
        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, ReaderWatchService::class.java)
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        /**
         * 안드로이드 13+ 의 '제한된 설정': 파일(APK)로 직접 깐 앱은 접근성에서 흐리게 막힌다. 앱 정보 › ⋮ ›
         * `제한된 설정 허용` 으로 풀어야 한다(그 창을 한 번 본 뒤에야 메뉴가 생긴다). 막혔는지는 알 수 없어 13+ 이면 안내한다.
         */
        val mayBeRestricted: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        /** 시스템 접근성 설정 */
        fun openSettings(context: Context) {
            runCatching { context.startActivity(settingsIntent()) }
        }

        fun settingsIntent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

        /**
         * 이 앱의 앱 정보(제한된 설정 허용). 기기 설정 아이콘을 숨긴 기기(iReader)에서도 직접 열린다.
         * 안드로이드 13 설정 앱은 ⋮ 메뉴를 만들 때 앱 uid 를 앱 목록이 넘기는 인자나 extra `uId` 에서만 읽어,
         * 없으면 -1 로 묻다가 실패해 `제한된 설정 허용` 이 빠진다(에뮬레이터 API 33 에서 확인). 그래서 uid 를 함께 넘긴다.
         */
        fun openAppInfo(context: Context) {
            runCatching { context.startActivity(appInfoIntent(context)) }
        }

        fun appInfoIntent(context: Context): Intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).putExtra("uId", Process.myUid())
    }
}
