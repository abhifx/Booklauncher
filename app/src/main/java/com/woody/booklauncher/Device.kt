package com.woody.booklauncher

import android.content.ComponentName
import android.content.ContentProviderClient
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import java.util.concurrent.Executors

/**
 * 기기 종류와 기기마다 다른 동작(빠른 설정, 조명). 크레마 전용 화면 보정은 [Crema].
 * 기기별 조사 내용은 docs/DEVICES.md.
 */
object Device {
    enum class Kind { CREMA, MEEBOOK, OTHER }

    /** 메이북(Haoqing 펌웨어)은 기종마다 모델명이 달라(M6·M6C·E6) 회사 속성 `ro.haoqing.brand` 로 가른다. */
    val kind: Kind by lazy {
        when {
            Crema.isCrema -> Kind.CREMA
            prop("ro.haoqing.brand").isNotEmpty() -> Kind.MEEBOOK
            else -> Kind.OTHER
        }
    }

    /**
     * 조명 상태가 들어 있는 `Settings.System` 키. 바뀌면 상태 줄을 다시 그린다.
     * 그 밖의 기기는 조명 단계 `EinkLightStep`(iReader SmartOS: Ocean·Neo 에서 확인)을 본다. 제조사로 가르지 않는다:
     * 같은 회사라도 기종·펌웨어마다 없을 수 있고, 없으면 0 으로 읽혀 꺼짐이다.
     */
    val lightKeys: List<String>
        get() = when (kind) {
            Kind.CREMA -> listOf("screen_brightness", "warm_light")
            Kind.MEEBOOK -> listOf("isLightOn")
            Kind.OTHER -> listOf("EinkLightStep")
        }

    /**
     * 조명 켜짐. 크레마는 백색(screen_brightness)·온색(warm_light) 중 하나라도 0보다 크면 켜짐.
     * 메이북은 꺼도 밝기 값이 남아 SystemUI 가 쓰는 `isLightOn`("true"/"false")을 본다.
     * `EinkLightStep` 은 끄면 0 이다(iReader 의 `screen_brightness` 는 조명과 따로 논다).
     */
    fun lightOn(context: Context): Boolean {
        val cr = context.contentResolver
        return when (kind) {
            Kind.CREMA, Kind.OTHER -> lightKeys.any { key -> runCatching { Settings.System.getInt(cr, key, 0) }.getOrDefault(0) > 0 }
            Kind.MEEBOOK -> runCatching { Settings.System.getString(cr, lightKeys.single()) }.getOrNull() == "true"
        }
    }

    /**
     * 기기의 빠른 설정 창(와이파이·BT·조명 등)을 연다. 크레마는 `com.epd.drop_down`, 메이북은 SystemUI 가 받는
     * `com.haoqing.action.QUICK_SETTINGS`. 받는 쪽이 없는 방송은 아무 일도 하지 않으므로 기기를 가리지 않고 둘 다 보낸다.
     */
    fun openQuickSettings(context: Context) {
        // 크레마: SystemUI 가 받아 크레마 빠른 설정 앱(`com.inno.quicksetting`)을 띄운다.
        context.sendBroadcast(Intent("com.epd.drop_down"))
        // 메이북: Haoqing SystemUI 가 받아 알림창(빠른 설정)을 펼친다.
        context.sendBroadcast(Intent("com.haoqing.action.QUICK_SETTINGS").setPackage("com.android.systemui"))
    }

    /**
     * BOOX 런처(`com.onyx`)가 있다. BOOX 는 앱 얼리기로 다른 회사 앱을 쓰지 않을 때 꺼 두고(DISABLED_USER),
     * 얼린 앱은 이 런처가 풀고 연다(docs/DEVICES.md › Onyx BOOX).
     */
    fun hasBooxLauncher(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(BOOX_LAUNCHER, 0) }.isSuccess

    /**
     * BOOX 가 얼려 둔 앱인가(앱 단위로 꺼짐). BOOX 인지는 [hasBooxLauncher] 로 따로 본다.
     * BOOX 가 얼리지 않는 Onyx·시스템 앱(펌웨어 `ActivityManagerHelper.isOnyxOrSystemApp`)과, 같은 방법으로 끄는
     * Google Play 끄기(`ApplicationFreezeHelper.disableGooglePlay`)의 앱은 빼 둔다(누르면 사용자가 끈 Play 가 켜진다).
     */
    fun isFrozen(pm: PackageManager, pkg: String): Boolean {
        if ("com.onyx" in pkg || pkg in GOOGLE_PLAY) return false
        return runCatching {
            val info = pm.getApplicationInfo(pkg, PackageManager.MATCH_DISABLED_COMPONENTS)
            info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0 &&
                pm.getApplicationEnabledSetting(pkg) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
        }.getOrDefault(false)
    }

    /** 얼린 앱을 BOOX 런처에 풀고 열어 달라고 한다(BOOX 플로팅 버튼도 같은 방법을 쓴다). 런처가 떠 있어야 받는다. */
    fun sendOpenFrozenApp(context: Context, component: ComponentName) {
        context.sendBroadcast(Intent("onyx.action.open_frozen_app").setPackage(BOOX_LAUNCHER)
            .putExtra("args_pkg", component.packageName)
            .putExtra("args_class", component.className)
            // UserHandle.hashCode 가 곧 사용자 번호다(getIdentifier 는 숨은 함수).
            .putExtra("args_user_id", Process.myUserHandle().hashCode()))
    }

    /**
     * BOOX 가 이 앱을 자동 얼리기 대상으로 두었는가. 펌웨어의 공개 함수 `android.onyx.optimization.EInkHelper.getAutoFreezeApps()`
     * (숨은 API 표시가 SDK 라 일반 앱도 부를 수 있고, 시스템 서비스 `oec_service` 는 권한을 묻지 않는다. Palma 4.0.2 에서 확인)가
     * 돌려주는 목록에 이 앱이 있으면 true. 대상 앱은 다른 앱으로 넘어가 30초가 지나면 작업이 지워지고 꺼져, 접근성이
     * '멈춘 서비스'로 남는다(docs/DEVICES.md › 앱 얼리기). BOOX 펌웨어가 아니라 물을 수 없으면 null.
     */
    fun booxAutoFreeze(context: Context): Boolean? {
        val apps = runCatching { getAutoFreezeApps?.invoke(null) as? List<*> }.getOrNull() ?: return null
        return context.packageName in apps
    }

    private val getAutoFreezeApps by lazy {
        runCatching { Class.forName("android.onyx.optimization.EInkHelper").getMethod("getAutoFreezeApps") }.getOrNull()
    }

    /** BOOX 런처의 앱 얼리기 설정 화면을 열어 달라고 한다(BOOX 플로팅 버튼의 얼리기 바로가기와 같다). */
    fun openBooxFreezeSettings(context: Context) {
        context.sendBroadcast(Intent("com.onyx.app.freeze.page").setPackage(BOOX_LAUNCHER))
    }

    /**
     * BOOX SystemUI 의 E-Ink 센터(앱 최적화)를 [screen] 화면(이 앱의 화면)으로 열어 달라고 한다. 사용자가 `기타` 탭의
     * `백그라운드에서 활성화 상태` 를 `무제한` 으로 바꾸게 하려고 연다: 처음 값(5분)이면 BOOX 가 다른 앱으로 넘어가고 그 시간이
     * 지난 앱을 강제 종료하고(얼리기 대상이 아니어도), 그러면 안드로이드가 접근성 켜짐을 지운다(docs/DEVICES.md › 백그라운드 강제 종료).
     * 받는 곳은 SystemUI `EACActionReceiver`(권한 없이 등록, Go6 4.2 펌웨어로 확인. 실기기 **(미확인)**).
     */
    fun openBooxOptimization(context: Context, screen: ComponentName) {
        context.sendBroadcast(Intent("com.onyx.SHOW_EAC_SETTINGS_VIEW_REQUEST").setPackage("com.android.systemui")
            .putExtra("args_component", screen.flattenToString()))
    }

    /**
     * BOOX 런처를 띄워 둔다. 얼린 앱 열기 방송은 런처가 떠 있을 때만 받는데(실행 중에 등록하는 받는 곳),
     * 런처는 부팅 때 스스로 뜨지 않아 BookLauncher 가 기본 홈이면 꺼져 있을 수 있다.
     * 런처의 공개 데이터 창구에 연결만 해 두면(읽고 쓰지 않음) 안드로이드가 런처 프로세스를 띄우고 이 앱만큼 살려 둔다.
     * 홈이 보일 때마다 다시 잡는다(런처가 죽었으면 새로 띄운다). 불안정(unstable) 연결이라 런처가 죽어도 이 앱은 괜찮다.
     * 다 잡으면 [then] 을 화면 스레드에서 부른다.
     */
    fun keepBooxLauncher(context: Context, then: (() -> Unit)? = null) {
        // 잠금이 풀리기 전에는 런처(잠금 전에 뜨지 않는 앱)를 띄울 수 없다.
        if (!Storage.isUnlocked(context) || !hasBooxLauncher(context)) {
            then?.invoke()
            return
        }
        val resolver = context.applicationContext.contentResolver
        booxLinker.execute {
            // 런처가 꺼져 있으면 뜰 때까지 기다린다. 못 잡으면 전 연결을 그대로 둔다.
            runCatching { resolver.acquireUnstableContentProviderClient(BOOX_PROVIDER) }.getOrNull()?.let { client ->
                booxLink?.close()
                booxLink = client
            }
            then?.let { Handler(Looper.getMainLooper()).post(it) }
        }
    }

    /** 연결은 한 줄로 차례대로 잡는다(겹친 요청이 순서를 바꿔 새 연결을 덮지 않게). [booxLink] 도 여기서만 만진다. */
    private val booxLinker = Executors.newSingleThreadExecutor()
    private var booxLink: ContentProviderClient? = null
    private const val BOOX_LAUNCHER = "com.onyx"
    private const val BOOX_PROVIDER = "com.onyx.system.database.ContentProvider"
    private val GOOGLE_PLAY = setOf("com.android.vending", "com.google.android.gms", "com.google.android.gsf")

    /** SystemProperties.get 은 숨은 함수라 한 번만 찾아 둔다(배터리 신호마다 불리므로). */
    private val getProperty by lazy {
        runCatching { Class.forName("android.os.SystemProperties").getMethod("get", String::class.java, String::class.java) }.getOrNull()
    }

    /** 시스템 속성. 일반 앱도 읽을 수 있다. 없으면 빈 글자. */
    fun prop(name: String): String =
        runCatching { getProperty?.invoke(null, name, "") as? String }.getOrNull().orEmpty().trim()
}
