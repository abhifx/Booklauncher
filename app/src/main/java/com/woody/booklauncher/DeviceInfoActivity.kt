package com.woody.booklauncher

import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 기기 정보(설정 › 기기 정보). 다른 기기를 쓰는 사람이 이 화면을 사진으로 찍어 보내면 기기마다 다른 동작을 맞출 수 있게
 * 한 화면에 모은다: 기기·시스템 속성·설정 값·권한, 자동 추가 서비스 기록, 누른 버튼의 키 번호, 최근에 뜬 다른 화면,
 * 이 앱이 여는 시스템 화면 시험. 기기별 조사는 docs/DEVICES.md.
 */
class DeviceInfoActivity : EinkActivity() {
    /** 사진으로 찍어 보내는 진단 화면이라 글자 · 간격 설정과 기기 글꼴 크기를 따르지 않는다. */
    override val fixedTextSize get() = true
    private lateinit var keyValue: TextView
    private lateinit var recentValue: TextView
    private lateinit var permsValue: TextView
    private lateinit var radiosValue: TextView

    /** 시스템 화면 시험에서 연 화면: 결과를 쓸 곳, 화면 이름, 연 때. 돌아오면([onResume]) 걸린 시간을 쓴다. */
    private var opened: Triple<TextView, String, Long>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = vbox()
        root.addView(titleRow(getString(R.string.device_info)) { finish() })
        root.addView(hline(2))
        val list = vbox().apply { setPadding(dp(Ui.MARGIN), 0, dp(Ui.MARGIN), 0) }
        root.addView(list, lp(MATCH, 0, 1f))

        list.addView(text(getString(R.string.device_photo_hint), 15f, color = Ui.GRAY),
            lp(MATCH, WRAP).apply { topMargin = dp(8) })

        // 화면: 기기가 선언한 밀도·dp(앱 밀도를 바꾸지 않은 applicationContext)와 앱이 그리는 밀도·dp(EinkActivity 가 맞춘 것)
        val real = DisplayMetrics()
        @Suppress("DEPRECATION") windowManager.defaultDisplay.getRealMetrics(real)
        val sys = applicationContext.resources.displayMetrics
        val dm = resources.displayMetrics
        // 앱 밀도를 정할 때(Ui.designDensityDpi) 본 크기. 실제 화면과 다르면 앱 글자가 기기에 맞지 않는다.
        val basis = Ui.densityBasis ?: "-"
        fun dpSize(density: Float) = "${(real.widthPixels / density).roundToInt()}×${(real.heightPixels / density).roundToInt()}dp"
        val kind = getString(when (Device.kind) {
            Device.Kind.CREMA -> R.string.device_kind_crema
            Device.Kind.MEEBOOK -> R.string.device_kind_meebook
            Device.Kind.OTHER -> R.string.device_kind_other
        })
        // 값만 봐도 무엇인지 알 수 있어 이름 없이 이어 쓴다: 모델(코드명)·제조사·브랜드·안드로이드·기기 종류·앱 버전·화면·빌드.
        // 화면은 앱이 맞춘 밀도가 다르거나 밀도를 정할 때 본 크기가 실제와 다르면 `→ 앱 기준 …` 을 붙인다. 길면 잘리는 빌드를 맨 뒤에.
        section(list, R.string.device_section_device)
        val size = "${real.widthPixels}×${real.heightPixels}"
        val screen = "${getString(R.string.device_screen)} $size · ${sys.densityDpi}dpi · ${dpSize(sys.density)}" +
            (if (dm.densityDpi != sys.densityDpi || basis != size)
                " → ${getString(R.string.device_screen_app)} ${dm.densityDpi}dpi · ${dpSize(dm.density)} · ${getString(R.string.device_screen_basis, basis)}"
            else "") +
            // 앱이 맞춘 밀도가 먹히지 않았다(기기가 덮어씀, BOOX 앱 최적화의 앱별 DPI 등)
            if (dm.densityDpi != Ui.designDpi) " · ${getString(R.string.device_screen_wanted, Ui.designDpi)}" else ""
        list.addView(text(listOf(
            "${Build.MODEL} (${Build.DEVICE})", Build.MANUFACTURER, Build.BRAND,
            "${getString(R.string.device_android)} ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            kind, "BookLauncher ${appVersion()}", screen, Build.DISPLAY,
        ).joinToString(" · "), 14f, lines = 4).apply { setLineSpacing(0f, 1.15f) })

        // 기기마다 다른 동작을 가르는 시스템 속성·설정 값: 이미 아는 키(크레마·메이북)와 이름으로 찾은 기기 고유 값 중 있는 것만.
        section(list, R.string.device_section_values)
        listOf(vendorProps(), vendorSettings()).forEach {
            list.addView(text(it.ifEmpty { "-" }, 14f, lines = 2).apply { setLineSpacing(0f, 1.15f) })
        }

        // 권한과 상태 줄의 와이파이·블루투스 지금 값(빠른 설정에서 바꾸고 돌아오면 다시 읽는다)
        section(list, R.string.device_section_perms)
        permsValue = text("", 16f, lines = 2)
        list.addView(permsValue)
        radiosValue = text("", 14f)
        list.addView(radiosValue)

        // 자동 추가(접근성 서비스)가 저절로 꺼지는 기기에서 언제·왜 끊겼는지: 서비스 연결·끊김, 이 앱이 끝난 까닭(안드로이드 11+),
        // 백그라운드에서 앱을 끄는 설정들. 서비스가 받은 알림·한 일은 따로 된 화면(`기록 보기 ›`, 이 화면에 더 넣을 자리가 없다).
        section(list, R.string.device_section_service, getString(R.string.watch_log_open)) { startActivity(WatchLogActivity.intent(this)) }
        serviceLog().forEachIndexed { i, line ->
            list.addView(text(line, 14f, lines = if (i >= 2) LOG_LINES[i - 2] else 1).apply { setLineSpacing(0f, 1.15f) })
        }

        // 버튼 시험: 페이지 버튼·화면 아래 버튼이 무슨 키를 보내는지. 뒤로 키는 화면을 닫으므로 다시 열면 보인다.
        section(list, R.string.device_section_keys)
        keyValue = text(lastKey ?: getString(R.string.device_key_hint), 17f, Ui.bold, lines = 2)
        list.addView(keyValue)

        // 최근에 앞에 뜬 다른 앱 화면(사용 기록). 기본 런처·상단 바에서 빠른 설정을 연 뒤 돌아오면, 빠른 설정이
        // 크레마처럼 따로 된 화면이면 여기 남는다. 시스템 알림창은 화면(Activity)이 아니라 남지 않는다.
        section(list, R.string.device_section_recent)
        recentValue = text("", 14f, lines = 3).apply { setLineSpacing(0f, 1.15f) }
        list.addView(recentValue)

        // 이 앱이 여는 시스템 화면이 열리는지(설정 앱이 멈추는 기기, Moaan MIX7S 의 사용 기록 화면). 연 화면 이름과 돌아오기까지 걸린 시간을 쓴다.
        // 제목 줄 오른쪽은 회전 고정 시험(누르면 켜고 끈다. 다음에 여는 화면부터, [EinkActivity]): 줄을 늘리지 않으려고 여기에 둔다.
        section(list, R.string.device_section_system, rotationLockText()) {
            prefs.rotationLock = !prefs.rotationLock
            it.text = rotationLockText()
        }
        list.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            SYSTEM_SCREENS.forEach { (label, intentOf) ->
                val result = text("", 13f, color = Ui.GRAY, lines = 2)
                addView(vbox().apply {
                    addView(text(getString(label) + " ›", 17f, Ui.bold))
                    addView(result)
                    setPadding(0, dp(6), dp(4), dp(6))
                    setOnClickListener { openSystem(intentOf(this@DeviceInfoActivity), result) }
                }, lp(0, WRAP, 1f))
            }
        })
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        recentValue.text = recentScreens()
        permsValue.text = permsText()
        radiosValue.text = radiosText()
        opened?.let { (result, name, at) ->
            result.text = getString(R.string.device_system_back, name, ((SystemClock.elapsedRealtime() - at) / 1000).toInt())
            opened = null
        }
    }

    /** [intent] 를 열고 연 화면 이름(`Settings$AppUsageAccessSettingsActivity` → `AppUsageAccessSettings`)을 기억한다. 받는 화면이 없거나 열지 못하면 바로 쓴다. */
    private fun openSystem(intent: Intent, result: TextView) {
        val name = packageManager.resolveActivity(intent, 0)?.activityInfo?.name
            ?.substringAfterLast('.')?.substringAfterLast('$')?.removeSuffix("Activity")
            // 좁은 칸에서 낱말 사이(대문자 앞)에서 줄이 바뀌게
            ?.replace(WORD_START, "\u200B")
        if (name == null) {
            result.text = getString(R.string.device_system_none)
            return
        }
        runCatching { startActivity(intent) }
            .onSuccess { opened = Triple(result, name, SystemClock.elapsedRealtime()) }
            .onFailure { result.text = getString(R.string.device_system_failed, name) }
    }

    /**
     * 네 줄: 서비스 연결·끊김 때와 끊긴 횟수 / 이 앱이 최근에 끝난 때와 까닭(안드로이드 11+) / 마지막 앱 오류([CrashLog]) /
     * 배터리 최적화 제외·대기 그룹·백그라운드 제한과 이 앱 이름이 든 기기 설정 값(제조사의 허용 목록일 수 있다).
     */
    private fun serviceLog(): List<String> {
        val time = SimpleDateFormat("MM/dd HH:mm", Locale.US)
        fun at(ms: Long) = if (ms == 0L) "-" else time.format(ms)
        val (connected, disconnected, count) = ReaderWatchService.lifeLog(this)
        val life = getString(R.string.device_service_life, at(connected), at(disconnected), count)

        val exits = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                // 한 줄에 들게, 앞 것과 같은 날이면 시각만
                val day = SimpleDateFormat("MM/dd", Locale.US)
                val clock = SimpleDateFormat("HH:mm", Locale.US)
                var lastDay: String? = null
                getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(packageName, 0, EXIT_COUNT)
                    .joinToString(" · ") {
                        val d = day.format(it.timestamp)
                        val stamp = (if (d == lastDay) "" else "$d ") + clock.format(it.timestamp)
                        lastDay = d
                        "$stamp ${exitReason(it.reason)}"
                    }
            }.getOrNull()
        } else null
        val exit = getString(R.string.device_service_exits, exits?.ifEmpty { "-" } ?: "-")
        // 이 앱이 마지막으로 죽은 오류(CRASH 의 내용, [CrashLog])
        val crash = getString(R.string.device_service_crash, CrashLog.last(this) ?: "-")

        val power = getSystemService(PowerManager::class.java)
        val bucket = getSystemService(UsageStatsManager::class.java)?.appStandbyBucket?.let { BUCKETS[it] ?: "$it" } ?: "-"
        val restricted = getSystemService(ActivityManager::class.java).isBackgroundRestricted
        val lists = settingsNaming(packageName)
        val kill = listOf(
            getString(R.string.device_service_battery) + " " + onOff(power?.isIgnoringBatteryOptimizations(packageName) == true),
            getString(R.string.device_service_bucket) + " " + bucket,
            getString(R.string.device_service_restricted) + " " + onOff(restricted),
        ).joinToString(" · ") + if (lists.isEmpty()) "" else " · " + lists.joinToString(" · ")
        return listOf(life, exit, crash, kill)
    }

    /** `Settings.System`·`Secure`·`Global` 가운데 값에 [pkg] 가 든 기기 고유 키(접근성 켜짐 같은 안드로이드 기본 키는 뺀다). */
    private fun settingsNaming(pkg: String): List<String> =
        listOf(Settings.System.CONTENT_URI, Settings.Secure.CONTENT_URI, Settings.Global.CONTENT_URI).flatMap { uri ->
            runCatching {
                contentResolver.query(uri, arrayOf("name", "value"), null, null, null)?.use { c ->
                    buildList { while (c.moveToNext()) if (c.getString(1)?.contains(pkg) == true) c.getString(0)?.let { add(it) } }
                }
            }.getOrNull().orEmpty()
        }.filter { !AOSP_PACKAGE_SETTING.matches(it) }.distinct().map { short(it) }

    private fun exitReason(reason: Int) = EXIT_REASONS[reason] ?: "$reason"

    /** 최근 [RECENT_MS] 안에 앞에 뜬 다른 앱 화면 [RECENT_COUNT] 개(시각 · 패키지 / 화면 클래스 이름). 한 줄에 들게 클래스는 마지막 이름만. 사용 기록 권한이 없으면 안내. */
    private fun recentScreens(): String {
        if (!ReadingLog.hasAccess(this)) return getString(R.string.device_recent_no_access)
        val now = System.currentTimeMillis()
        val events = getSystemService(UsageStatsManager::class.java)?.queryEvents(now - RECENT_MS, now) ?: return "-"
        val e = UsageEvents.Event()
        val time = SimpleDateFormat("HH:mm:ss", Locale.US)
        val recent = ArrayDeque<String>()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            @Suppress("DEPRECATION")
            if (e.eventType != UsageEvents.Event.MOVE_TO_FOREGROUND || e.packageName == packageName) continue
            recent.addLast("${time.format(e.timeStamp)}  ${e.packageName} / ${e.className.orEmpty().substringAfterLast('.')}")
            if (recent.size > RECENT_COUNT) recent.removeFirst()
        }
        return recent.reversed().joinToString("\n").ifEmpty { "-" }
    }

    /** 버튼을 누르면 키 번호·이름·스캔 코드를 보여 준다(쪽 넘기기는 하지 않는다). 뒤로 키는 기록만 하고 닫는다. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        lastKey = "$keyCode  ${KeyEvent.keyCodeToString(keyCode)}  · scan ${event.scanCode}"
        keyValue.text = lastKey
        return if (keyCode == KeyEvent.KEYCODE_BACK) super.onKeyDown(keyCode, event) else true
    }

    /** 시스템 속성 중 아는 키와 이름이 [VENDOR_KEY] 에 맞는 것(`getprop` 전체 목록에서). 읽지 못하면 아는 키만. */
    private fun vendorProps(): String {
        val all = runCatching {
            ProcessBuilder("getprop").redirectErrorStream(true).start().inputStream.bufferedReader().useLines { lines ->
                lines.mapNotNull { PROP_LINE.matchEntire(it)?.destructured?.let { (k, v) -> k to v } }.toMap()
            }
        }.getOrDefault(emptyMap())
        val keys = PROPS + all.keys.filter { vendorKey(it) }.sorted()
        return keys.distinct().mapNotNull { key -> (all[key] ?: Device.prop(key)).ifEmpty { null }?.let { "$key=${short(it)}" } }
            .joinToString(" · ")
    }

    /** `Settings.System` 중 아는 키와 이름이 [VENDOR_KEY]·[SETTING_KEY] 에 맞는 것(전체 목록에서). 목록을 못 읽으면 아는 키만. */
    private fun vendorSettings(): String {
        val all = runCatching {
            contentResolver.query(Settings.System.CONTENT_URI, arrayOf("name", "value"), null, null, null)?.use { c ->
                buildMap { while (c.moveToNext()) c.getString(0)?.let { put(it, c.getString(1).orEmpty()) } }
            }
        }.getOrNull().orEmpty()
        val keys = SETTINGS + all.keys.filter { (vendorKey(it) || SETTING_KEY.containsMatchIn(it)) && !AOSP_SETTING.matches(it) }.sorted()
        return keys.distinct().mapNotNull { key ->
            (all[key] ?: runCatching { Settings.System.getString(contentResolver, key) }.getOrNull())?.let { "$key=${short(it)}" }
        }.joinToString(" · ")
    }

    /** 기기 고유 값으로 보이는 이름: 전자잉크·조명 낱말이나 제조사·브랜드 이름이 들어 있다. */
    private fun vendorKey(key: String): Boolean {
        val k = key.lowercase()
        return VENDOR_KEY.containsMatchIn(k) || makerWords.any { it in k }
    }

    private val makerWords by lazy {
        listOf(Build.MANUFACTURER, Build.BRAND).flatMap { it.lowercase().split(' ', '-', '_') }
            .filter { it.length >= 4 && it !in COMMON_WORDS }.distinct()
    }

    private fun short(value: String) = if (value.length > VALUE_MAX) value.take(VALUE_MAX) + "…" else value

    /** 자동 추가·오늘 읽은 시간·기본 홈(BOOX 면 자동 얼리기, 물을 수 없으면 `?`) */
    private fun permsText(): String {
        val home = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            ?.activityInfo?.packageName == packageName
        // BOOX 면 이 앱이 자동 얼리기 대상인지(켜짐이면 다른 앱으로 넘어간 30초 뒤 꺼져 자동 추가가 멈춘다)
        val freeze = if (Device.hasBooxLauncher(this))
            getString(R.string.device_perm_boox_freeze) to (Device.booxAutoFreeze(this)?.let { onOff(it) } ?: "?")
        else null
        return listOfNotNull(
            getString(R.string.auto_books_short) to onOff(ReaderWatchService.isEnabled(this)),
            getString(R.string.reading_time) to onOff(ReadingLog.hasAccess(this)),
            getString(R.string.device_perm_home) to onOff(home),
            freeze,
        ).joinToString("  ·  ") { (label, value) -> "$label $value" }
    }

    /** `와이파이 켜짐·연결 없음 · 블루투스 켜짐 A2DP0 HFP0 HID?`(프로필 0 끊김·2 연결됨, ? 는 물을 수 없음(안드로이드 12+ 권한)) */
    private fun radiosText(): String {
        val r = DeviceStatus.radios(this)
        val wifi = if (!r.wifiOn) onOff(false)
            else getString(if (r.wifiLinked) R.string.device_linked else R.string.device_unlinked, onOff(true))
        val bt = if (!r.btOn) onOff(false) else onOff(true) +
            r.profiles.joinToString("") { (name, v) -> " $name${if (v < 0) "?" else v}" }
        return "${getString(R.string.device_wifi)} $wifi · ${getString(R.string.device_bt)} $bt"
    }

    private fun onOff(on: Boolean) = getString(if (on) R.string.on else R.string.off)

    private fun rotationLockText() = getString(R.string.device_rotation_lock, onOff(prefs.rotationLock))

    /** 절 제목. [moreText] 가 있으면 같은 줄 오른쪽 끝에 굵게 쓰고(줄을 늘리지 않는다) 줄을 누르면 그 글자 뷰로 [more] 를 부른다. */
    private fun section(list: LinearLayout, title: Int, moreText: String? = null, more: (TextView) -> Unit = {}) {
        val head = text(getString(title), 15f, color = Ui.LIGHT_GRAY)
        val row = if (moreText == null) head else hbox().apply {
            addView(head, lp(0, WRAP, 1f))
            val right = text(moreText, 15f, Ui.bold)
            addView(right)
            setOnClickListener { more(right) }
        }
        list.addView(row, lp(MATCH, WRAP).apply { topMargin = dp(9); bottomMargin = dp(2) })
    }

    companion object {
        /** 이 프로세스에서 마지막으로 누른 키(뒤로 키로 닫혀도 다시 열면 보이게) */
        private var lastKey: String? = null

        private const val RECENT_MS = 10 * 60 * 1000L
        private const val RECENT_COUNT = 3

        /**
         * 이미 아는 기기의 키(크레마·메이북·Bigme). 없는 기기에서는 보이지 않는다. `ro.build.versionCode` 는 Bigme OTA 서버가 묻는 기기 코드
         * (HiBreak Pro BW `B651 EEA_M2`, 펌웨어를 받아 보려고. docs/DEVICES.md Bigme B7 Pro).
         */
        private val PROPS = listOf("ro.build.versionCode", "ro.haoqing.brand", "persist.haoqing.updatemodel", "persist.haoqing.pagekeys", "persist.vendor.fullmode_cnt")
        private val SETTINGS = listOf("screen_brightness", "warm_light", "isLightOn", "light_mode", "haoqing_warm_light", "hq_contrast")

        /** 처음 보는 기기의 고유 값을 이름으로 찾는다: 전자잉크·화면 갱신·조명·버튼 낱말, 알려진 펌웨어 회사 이름. */
        private val VENDOR_KEY = Regex("eink|epd|ebc|fullmode|pagekey|backlight|frontlight|warm(?!_?(reset|boot))|contrast|haoqing|wisky|viwoods|crema|wetao|onyx|boox|bigme")

        /** 제조사·브랜드 이름에 흔한 낱말. 안드로이드 기본 키에도 들어 있어 잡음만 나온다(Minimal Phone 의 `phone` → `gsm.current.phone-type`·`volume_music_headphone`). */
        private val COMMON_WORDS = setOf("phone", "mobile", "tech", "technology", "electronics", "digital", "company", "group", "android", "device", "inc.")

        /** `Settings.System` 에서 더 보는 낱말(조명·버튼·제스처·화면 갱신). 시스템 속성에는 이름이 비슷한 안드로이드 기본 값이 많아 쓰지 않는다. */
        private val SETTING_KEY = Regex("light|bright|key|gesture|refresh|ghost", RegexOption.IGNORE_CASE)

        /** 이름은 맞지만 안드로이드 기본에 있는 설정 */
        private val AOSP_SETTING = Regex("screen_brightness_(mode|float|for_vr.*)|notification_light_pulse|show_key_presses|.*vibration.*|.*haptic.*")

        private val PROP_LINE = Regex("""\[(.+?)]: \[(.*)]""")
        private const val VALUE_MAX = 32

        /**
         * 이 앱이 여는 시스템 화면들(앱이 여는 것과 같은 인텐트). 사용 기록은 앱이 여는 이 앱만 보이는 화면과, 그 화면이 없는
         * 기기에서 대신 여는 목록 화면을 따로 시험한다.
         */
        private val SYSTEM_SCREENS: List<Pair<Int, (Context) -> Intent>> = listOf(
            R.string.device_system_a11y to { _ -> ReaderWatchService.settingsIntent() },
            R.string.device_system_usage to { _ -> ReadingLog.accessSettingsIntent() },
            R.string.device_system_usage_app to ReadingLog::appAccessSettingsIntent,
            R.string.device_system_app_info to ReaderWatchService::appInfoIntent,
        )

        private const val EXIT_COUNT = 3
        /** 서비스 기록의 셋째 줄부터 넘칠 수 있는 줄 수: 마지막 앱 오류(때·예외 / 자리·원인, 메시지가 길면 각각 두 줄), 배터리·대기 그룹·기기 설정 키 */
        private val LOG_LINES = intArrayOf(4, 2)
        private val WORD_START = Regex("(?<=[a-z])(?=[A-Z])")

        /** `ApplicationExitInfo` 의 까닭(세 개가 한 줄에 들게 줄인 이름). 상수는 안드로이드 11+ 에만 있어 숫자로 둔다. */
        private val EXIT_REASONS = mapOf(
            1 to "EXIT_SELF", 2 to "SIGNALED", 3 to "LOW_MEMORY", 4 to "CRASH", 5 to "CRASH_NATIVE", 6 to "ANR",
            7 to "INIT_FAIL", 8 to "PERMISSION", 9 to "RESOURCE", 10 to "USER_REQ", 11 to "USER_STOP",
            12 to "DEP_DIED", 13 to "OTHER", 14 to "FREEZER", 15 to "PKG_STATE", 16 to "PKG_UPDATED",
        )

        /** 앱 대기 그룹(`UsageStatsManager.STANDBY_BUCKET_*`) */
        private val BUCKETS = mapOf(5 to "EXEMPTED", 10 to "ACTIVE", 20 to "WORKING_SET", 30 to "FREQUENT", 40 to "RARE", 45 to "RESTRICTED", 50 to "NEVER")

        /** 값에 이 앱 이름이 드는 안드로이드 기본 설정(접근성 켜짐·바로가기) */
        private val AOSP_PACKAGE_SETTING = Regex("enabled_accessibility_services|touch_exploration_granted_accessible_services|accessibility_.*")

        fun intent(context: Context) = Intent(context, DeviceInfoActivity::class.java)
    }
}
