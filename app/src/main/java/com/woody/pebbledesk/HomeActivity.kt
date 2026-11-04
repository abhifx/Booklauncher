package com.woody.pebbledesk

import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.hardware.input.InputManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.woody.pebbledesk.readers.BookFiles
import com.woody.pebbledesk.readers.ReaderApps
import java.time.LocalDate

/** ① 홈: 맨 위 두 줄 · 지금 읽는 책과 읽고 있는 앱 · 자주 쓰는 앱 · 하단 줄 */
class HomeActivity : EinkActivity() {
    private lateinit var topBar: TopBarView
    /**
     * 홈 틀(상단·책 자리·함께 읽는 책 줄·자주 쓰는 앱)을 글자 · 간격 설정과 상관없이 고정하는가. 기기가 앱의 밀도를 덮어써
     * 화면이 작아졌으면(BOOX 앱 최적화의 앱별 DPI) 고정하지 않고 글자 · 간격대로 잡는다(글자를 줄여 함께 읽는 책 줄을 맞출 수 있게).
     */
    private val fixedFrame by lazy { Ui.densityApplied(this) }

    /** 틀 높이를 잴 때: 고정이면 기준 크기 글자로, 아니면 지금 글자대로 */
    private fun <T> frameText(block: () -> T): T = if (fixedFrame) Ui.atBaseText(block) else block()

    /**
     * 가로 화면: 상단은 세로와 같고, 그 아래 왼쪽 칸에 책 자리(세로 화면 폭 [Ui.DESIGN_WIDTH_DP] 까지), 오른쪽 칸에 자주 쓰는 앱을 높이만큼 둔다.
     * 칸마다 세로 화면과 같은 좌우 여백이고 두 칸 사이에 옅은 세로 선.
     */
    private val wide by lazy { landscape() }

    /** 바형 화면의 큰 함께 읽는 책(표지 아래 글자). 가로면 책 자리가 낮아 보통 화면처럼 둔다. */
    private val tall get() = Ui.tallScreen && !wide

    /** 자주 쓰는 앱 격자 칸 수: 가로 페블처럼 오른쪽 칸이 좁으면 3칸 */
    private val gridColumns by lazy {
        if (wide && resources.displayMetrics.widthPixels - heroPaneWidth() - 2 * dp(Ui.MARGIN) < dp(GRID_NARROW_DP)) 3 else GRID_COLUMNS
    }

    /** 가로 화면 왼쪽(책) 칸 폭: 화면의 [HERO_PANE_RATIO], 세로 화면 폭까지 */
    private fun heroPaneWidth() = minOf(dp(Ui.DESIGN_WIDTH_DP), (resources.displayMetrics.widthPixels * HERO_PANE_RATIO).toInt())

    /** 글자 칸 묶음: 틀을 고정하면 다 들지 않는 줄을 그리지 않는다([FitColumn]). 고정하지 않으면 1.5.3 까지와 같다. */
    private fun column(): LinearLayout = if (fixedFrame) FitColumn(this) else vbox()
    /** 읽고 있는 책 자리. 자주 쓰는 앱 높이가 고정이라 남는 높이를 모두 차지하고, 책이 한 권이면 표지가 그만큼 커진다. */
    private lateinit var hero: FrameLayout
    private var heroHeight = 0
    /** 자주 쓰는 앱 제목 줄: 왼쪽 제목, 오른쪽 끝에 페이지(여러 쪽일 때만) */
    private lateinit var favPage: TextView
    private lateinit var favLine: View
    private lateinit var favRows: PagedRows
    /** 함께 읽는 책 쪽: 지금 쪽, 쪽 수, 그 책들(바뀌면 1쪽부터) */
    private var alsoPage = 0
    private var alsoCount = 0
    private var alsoIds: List<String> = emptyList()
    /** 그린 함께 읽는 책 줄(밀기를 여기서 시작했는지 본다)과, 쪽을 바꿔 그 줄의 칸만 다시 채우는 함수. 줄이 없으면 null. */
    private var alsoView: View? = null
    private var fillAlso: ((Int) -> Unit)? = null
    private val alsoPaged = object : Paged {
        override val page get() = alsoPage
        override val pageCount get() = alsoCount
        override fun next() = showAlso(alsoPage + 1)
        override fun prev() = showAlso(alsoPage - 1)
    }
    /** 이번 손가락이 함께 읽는 책 줄에서 시작했는가(그 줄을 밀면 책을, 다른 곳을 밀면 자주 쓰는 앱을 넘긴다) */
    private var touchOnAlso = false

    private fun showAlso(page: Int) {
        if (page !in 0 until alsoCount || page == alsoPage) return
        alsoPage = page
        fillAlso?.invoke(page)
    }

    /** 하단 가운데: 오늘 읽은 시간 · 연속 독서일(켜 두었고 권한이 있을 때만) */
    private lateinit var readingText: TextView
    /** 하단 왼쪽 `모든 앱 ›`(가운데 글자가 닿는지 잰다) */
    private lateinit var allAppsLink: TextView

    private var apps: List<AppEntry> = emptyList()
    /**
     * 지난번에 그린 책 자리와 자주 쓰는 앱의 내용. 같으면 다시 만들지 않는다(빠른 설정을 닫거나 앱에서 돌아올 때 불필요한 전자잉크 갱신을 막는다).
     * 둘을 따로 보아 바뀐 쪽만 다시 만든다(책 자리 높이가 정해지거나 책이 바뀌어도 자주 쓰는 앱은 그대로).
     */
    private var shownHero: List<Any?>? = null
    private var shownFavs: List<Any?>? = null

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateStatus()
            // 날이 바뀌면 오늘 읽은 시간을 0 부터. 부팅 직후 잠금이 풀리면 그때부터 읽은 시간을 셀 수 있다.
            if (intent.action == Intent.ACTION_DATE_CHANGED || intent.action == Intent.ACTION_USER_UNLOCKED) {
                updateReading()
                // 반납일 글자(`반납 1일 남음` → `오늘 반납`)는 날에 따라 바뀐다(바뀐 책 자리만 다시 그린다)
                refresh()
                // 끝난 날을 저장했으면 떠 있던 메뉴는 옛 기록이다
                Sheet.dismissStale(this@HomeActivity)
            }
            // BOOX 런처는 잠금이 풀려야 띄울 수 있고, 화면이 꺼진 동안 죽었을 수 있다.
            if (intent.action == Intent.ACTION_USER_UNLOCKED || intent.action == Intent.ACTION_SCREEN_ON) Device.keepBooxLauncher(context)
        }
    }
    private val lightObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = updateStatus()
    }
    /**
     * 끊긴 와이파이. 시스템은 끊김(onLost)을 알린 뒤에 네트워크 목록에서 빼서, 알림을 받자마자 읽으면 끊긴 와이파이가
     * 아직 남아 있을 수 있다(안드로이드 14 기기에서 끊어도 아이콘이 남았다가 다른 화면에 다녀오면 지워졌다). 다시 연결될 때까지 뺀다.
     */
    private val lostWifi = mutableSetOf<Network>()
    /** 와이파이 연결·끊김(연결 방송은 안드로이드 11 에뮬에서 오지 않았다) */
    private val wifiCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            lostWifi.remove(network)
            updateStatus()
        }
        override fun onLost(network: Network) {
            lostWifi.add(network)
            updateStatus()
        }
    }
    /**
     * 블루투스 이어폰·리모컨이 붙고 떨어질 때. 안드로이드 12+ 는 연결 방송(`ACTION_CONNECTION_STATE_CHANGED`)이 '근처 기기' 권한
     * 없이는 오지 않아, 상태 줄이 연결을 짐작하는 소리 출력·외부 입력 장치의 바뀜으로 다시 읽는다([DeviceStatus]).
     */
    private val audioCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = updateStatus()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = updateStatus()
    }
    private val inputListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = updateStatus()
        override fun onInputDeviceRemoved(deviceId: Int) = updateStatus()
        override fun onInputDeviceChanged(deviceId: Int) = Unit
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildViews()
    }

    override fun onResume() {
        super.onResume()
        if (recreating) return
        applyStatusBar()
        // KOReader 처럼 지켜보지 않고 데이터로 아는 앱에서 그사이 열고 닫은 책을 먼저 맞춘다(책 자리·읽은 시간을 그리기 전에)
        if (Storage.isUnlocked(this)) ReaderWatchService.syncFileApps()
        refresh()
        // 읽은 시간은 다른 앱에서 돌아올 때만 바뀐다(레이아웃 때의 refresh 에서는 다시 세지 않는다).
        updateReading()
        // 시작하기를 띄웠으면 그것이 자동 추가를 설명한다. 화면 크기 안내 · 자동 추가 안내 · 책 파일 읽기 안내 순(앞의 것이 뜨면 뒤의 것은 다음에 돌아올 때. 가운데 창이 겹치지 않게)
        // 새 판 창이 떠 있으면 다른 안내는 다음에 돌아올 때(가운데 창이 겹치지 않게)
        if (!OnboardingActivity.showOnce(this, prefs) && updateDialog?.isShowing != true) {
            warnDensity()
            if (densityDialog?.isShowing != true) warnAutoOff()
            if (densityDialog?.isShowing != true && autoOffDialog?.isShowing != true) warnFileAccess()
            if (densityDialog?.isShowing != true && autoOffDialog?.isShowing != true && fileDialog?.isShowing != true) askSameBook()
        }
        Update.onChange = { updates.onChange(); offerUpdate() }
        updates.onChange()
        offerUpdate()
        Update.checkFromHome(this)
        Device.keepBooxLauncher(this)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(stalledLater)
        Update.onChange = null
    }

    override fun onDestroy() {
        updates.dismiss()
        super.onDestroy()
    }

    /** 새 판 창·받는 중 창 */
    private val updates = UpdateSheets(this)
    /** 떠 있는 새 판 창 */
    private var updateDialog: Dialog? = null

    /**
     * 새 판이 나왔으면 화면 가운데에 알린다(바뀐 점과 `설치`, [UpdateSheets.notice]). 판마다 하루 한 번까지: `닫기` 면 다음 날 다시,
     * `이 버전은 알리지 않기` 면 그 판은 다시 띄우지 않는다(설정 › 업데이트에는 그대로 보인다). 다른 가운데 창이 떠 있거나 시작하기 전이면 다음에.
     */
    private fun offerUpdate() {
        val release = (Update.state as? Update.State.Available)?.release ?: return
        fun today() = "${release.version}@${LocalDate.now()}"
        if (!prefs.onboarded || prefs.updateSkip == release.version || prefs.updateShown == today()) return
        if (listOf(updateDialog, densityDialog, autoOffDialog, fileDialog).any { it?.isShowing == true }) return
        val maxHeight = window.decorView.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        // 닫는 날을 띄운 날로 적는다(회전으로 사라지면 다시 띄운다). 설치 허용 안내로 갔으면 허용하고 돌아왔을 때(시스템이 앱을 다시 시작한다) 다시.
        updateDialog = updates.notice(release, maxHeight, onSkip = { prefs.updateSkip = release.version }) { askedAllow ->
            prefs.updateShown = if (askedAllow) null else today()
        }
    }

    /** 떠 있는 '자동 추가가 꺼져 있습니다'·'멈춰 있습니다' 창(홈에 돌아올 때마다 하나만) */
    private var autoOffDialog: android.app.Dialog? = null
    /** 떠 있는 창이 '멈춰 있습니다' 인가(아니면 '꺼져 있습니다') */
    private var autoOffStalled = false
    private val handler = Handler(Looper.getMainLooper())
    /** 켜져 있는데 붙지 않은 채 [STALLED_WAIT_MS] 가 지났으면 '멈춰 있습니다' 창(홈이 앞에 있을 때만, onPause 에서 거둔다) */
    private val stalledLater = Runnable {
        if (ReaderWatchService.isEnabled(this) && !ReaderWatchService.bound && !(autoOffDialog?.isShowing == true && autoOffStalled) &&
            densityDialog?.isShowing != true && updateDialog?.isShowing != true && fileDialog?.isShowing != true) warnStalled()
    }

    /**
     * 읽는 책 자동 추가(접근성)가 꺼져 있으면 홈에 돌아올 때마다 화면 가운데에 알린다: 편 책이 읽고 있는 책에 들어오지 않고 읽은 시간도
     * 어느 책인지 모르게 된다(업데이트·시스템이 저절로 끄기도 한다). 켜져 있는데 서비스가 붙지 않은 채면 조금 기다렸다 '멈춰 있습니다'([warnStalled]).
     * 켜져 붙어 있으면 뜨지 않는다(떠 있던 창도 닫는다).
     */
    private fun warnAutoOff() {
        handler.removeCallbacks(stalledLater)
        if (!prefs.onboarded || !Storage.isUnlocked(this)) return
        val on = ReaderWatchService.isEnabled(this)
        val shown = autoOffDialog?.takeIf { it.isShowing }
        // 켜져 붙어 있으면 그사이 켠 것이다: 떠 있던 안내는 닫는다
        if (on && ReaderWatchService.bound) { shown?.dismiss(); return }
        // 떠 있는 창이 지금 상태와 다르면(꺼짐 창이 떠 있는데 켰으나 멈춰 있음 등) 닫고 맞는 창을
        if (shown != null) { if (autoOffStalled == on) return; shown.dismiss() }
        // 켜져 있는데 아직 안 붙었다: 앱이 막 떠 붙는 중일 수 있어 조금 기다렸다 본다([warnStalled])
        if (on) { handler.postDelayed(stalledLater, STALLED_WAIT_MS); return }
        val restricted = ReaderWatchService.mayBeRestricted
        fun sheet(hints: Boolean): Sheet {
            val desc = listOfNotNull(R.string.auto_off_desc, R.string.allow_hint.takeIf { hints }, R.string.restricted_hint.takeIf { hints && restricted })
                .joinToString("\n\n") { getString(it) }
            val sheet = autoNotice(R.string.auto_off_title, desc)
            if (restricted) sheet.item(getString(R.string.open_app_info)) { ReaderWatchService.openAppInfo(this) }
            return sheet
        }
        // 글자를 키워 화면을 넘으면 켜는 법 안내(허용·제한된 설정)를 빼고 첫 설명과 버튼만(설정 › 독서의 같은 안내에 다 있다)
        val full = sheet(hints = true)
        autoOffDialog = (if (full.height() <= window.decorView.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels) full else sheet(hints = false)).show()
        autoOffStalled = false
    }

    /** 자동 추가 안내 창(화면 가운데, 기록이 바뀌어도 닫지 않는다)과 `접근성 설정 열기`. [freeze] 면 그 위에 `얼리기 설정 열기` */
    private fun autoNotice(title: Int, desc: String, freeze: Boolean = false): Sheet =
        Sheet(this).header(getString(title), desc).centered().keepOnChange().apply {
            if (freeze) item(getString(R.string.boox_freeze_settings), bold = true) { Device.openBooxFreezeSettings(this@HomeActivity) }
        }.item(getString(R.string.open_accessibility), bold = true) { ReaderWatchService.openSettings(this) }

    /**
     * 접근성에서는 켜져 있는데 서비스가 붙어 있지 않다(켜진 채 강제 중지되거나 메모리 정리로 죽은 뒤 다시 켜면 시스템이 '멈춘 서비스'로 두고 붙이지 않는다.
     * 껐다 켜거나 앱을 업데이트해야 붙는다. 2026-10-16 에뮬레이터(안드로이드 11)에서 확인): 그동안은 편 책을 모르니 꺼짐과 같이 화면 가운데에 알린다.
     * BOOX 가 얼렸다 푼 뒤에도 이렇게 된다(2026-10-20 에뮬레이터에서 `pm disable-user`·`enable` 로 확인). 이 앱이 BOOX 얼리기
     * 대상이면([Device.booxAutoFreeze]) 얼리기에서 빼라는 말과 `얼리기 설정 열기` 를 더한다(빼지 않으면 다시 켜도 또 멈춘다).
     */
    private fun warnStalled() {
        autoOffDialog?.takeIf { it.isShowing }?.dismiss()
        val freeze = Device.booxAutoFreeze(this) == true
        val desc = getString(R.string.auto_stalled_desc) + "\n\n" + getString(if (freeze) R.string.auto_stalled_boox else R.string.auto_stalled_fix)
        autoOffDialog = autoNotice(R.string.auto_stalled_title, desc, freeze).show()
        autoOffStalled = true
    }

    /** 떠 있는 '책 파일 읽기를 켜 주세요' 창 */
    private var fileDialog: Dialog? = null

    /**
     * 책 파일 읽기 안내: 책 파일 읽기가 필요한 앱(KOReader·문리더·네오리더·이지뷰어)으로 꺼진 채 읽었으면
     * 홈으로 돌아올 때 그 앱마다 화면 가운데에 묻는다(`모든 파일에 액세스 열기` / `다시 보지 않기`). 그 앱을 쓸 때만 적히므로
     * 깔아만 둔 앱은 묻지 않는다([HomePrefs.fileNoticeWanted]). 띄우면 그 앱은 지우고, 다음에 그 앱으로 읽고 돌아오면 또 묻는다(`다시 보지 않기` 를 누를 때까지).
     */
    private fun warnFileAccess() {
        if (fileDialog?.isShowing == true) return
        val wanted = prefs.fileNoticeWanted
        if (wanted.isEmpty()) return
        if (BookFiles.canRead(this)) { prefs.fileNoticeWanted = emptySet(); return }
        val pkg = wanted.first()
        prefs.fileNoticeWanted = wanted - pkg
        if (pkg in prefs.fileNoticeSkip) return
        // 그 사이 업데이트로 책 파일 읽기를 쓰지 않게 된 앱(1.7.2 까지의 문리더)은 묻지 않는다
        if (ReaderApps.forPackage(this, pkg)?.needsBookFiles != true) return
        val name = AppStore.readerLabel(this, pkg) ?: return
        // 앱 이름이 길면(1.7.2 까지 묻던 Moon+ Reader Pro) 낱말 가운데서 줄이 나뉘어('가 / 져오려면') 띄어쓴 곳에서만 나누게
        fileDialog = Sheet(this).header(keepWords(getString(R.string.file_notice_title, name)), getString(R.string.file_notice_desc)).centered().keepOnChange()
            .item(getString(R.string.open_file_access), bold = true) { BookFiles.request(this) }
            .item(getString(R.string.dont_show_again)) { prefs.fileNoticeSkip = prefs.fileNoticeSkip + pkg }
            .show()
    }

    /** 떠 있는 '같은 책인가요?' 창 */
    private var sameBookDialog: Dialog? = null

    /**
     * 같은 책인지 묻기: 이북 앱의 서재 제목과 읽는 화면 제목이 달랐던 짝([HomePrefs.sameBookAsk])을 홈으로 돌아올 때 하나씩 묻는다(아래 메뉴, `같은 책` / `다른 책`).
     * 답하면 [HomePrefs.sameBooks]·[HomePrefs.otherBooks] 로 옮기고, 답하지 않고 닫으면 다음에 다시 묻는다. 읽는 화면 제목의 책이 없으면(지움) 묻지 않고 버린다
     */
    private fun askSameBook() {
        if (sameBookDialog?.isShowing == true) return
        val keys = prefs.sameBookAsk
        val key = keys.firstOrNull() ?: return
        val pair = TitlePair.of(key)
        if (pair == null || (BookShelf.findByTitle(this, pair.read) == null && BookShelf.findLivePast(this, pair.read) == null)) {
            prefs.sameBookAsk = keys - key
            return
        }
        val app = AppStore.readerLabel(this, pair.pkg) ?: pair.pkg
        fun answer(same: Boolean) {
            prefs.sameBookAsk = prefs.sameBookAsk - key
            if (same) prefs.sameBooks = prefs.sameBooks + key else prefs.otherBooks = prefs.otherBooks + key
        }
        val titles = vbox().apply {
            fun row(label: Int, title: String, top: Int) {
                addView(text(getString(label), 15f, color = Ui.LIGHT_GRAY), lp(MATCH, WRAP).apply { topMargin = dp(top) })
                addView(text(title, 19f, Ui.bold, lines = 2), lp(MATCH, WRAP).apply { topMargin = dp(4) })
            }
            row(R.string.same_book_read, pair.read, 0)
            row(R.string.same_book_shelf, pair.shelf, 14)
            addView(text(getString(R.string.same_book_note), 14f, color = Ui.LIGHT_GRAY, lines = 3), lp(MATCH, WRAP).apply { topMargin = dp(16) })
        }
        sameBookDialog = Sheet(this).header(getString(R.string.same_book_title), getString(R.string.same_book_desc, app)).noClose().keepOnChange()
            .note(titles)
            .item(getString(R.string.same_book_yes), bold = true) { answer(true) }
            .item(getString(R.string.same_book_no)) { answer(false) }
            .show()
    }

    /** 떠 있는 '화면이 크게 보입니다' 창(홈에 돌아올 때마다 하나만) */
    private var densityDialog: Dialog? = null

    /**
     * 앱이 맞춘 밀도([Ui.designDpi])가 먹히지 않고 기기가 정한 밀도로 그려지면(BOOX 앱 최적화의 앱별 DPI 등) 홈에 돌아올 때마다 알린다.
     * 화면 전체가 크게 보이고 홈의 함께 읽는 책 줄이 들지 않을 수 있다. 바꾸는 곳이 기기마다 달라 안내만 한다(BOOX 는 그 방법을 적는다).
     * `다시 보지 않기` 를 누르면 그 값(`지금/맞출 값`)으로는 다시 알리지 않는다.
     */
    private fun warnDensity() {
        val actual = resources.displayMetrics.densityDpi
        val pair = "$actual/${Ui.designDpi}"
        if (fixedFrame || prefs.densityWarnSkip == pair || densityDialog?.isShowing == true) return
        val desc = getString(
            if (Device.hasBooxLauncher(this)) R.string.density_changed_desc_boox else R.string.density_changed_desc,
            actual, Ui.designDpi,
        )
        densityDialog = Sheet(this).header(getString(R.string.density_changed_title), desc).centered().keepOnChange()
            .item(getString(R.string.dont_show_again)) { prefs.densityWarnSkip = pair }
            .show()
    }

    /**
     * 상태 줄은 홈이 보이는 동안 계속 갱신한다. 빠른 설정 창이 위에 떠 있으면 홈은 멈춤(pause) 상태지만
     * 뒤에 보이므로, 거기서 와이파이·조명 등을 바꾸면 바로 반영되어야 한다.
     */
    override fun onStart() {
        super.onStart()
        topBar.use24Hour = prefs.clock24h
        registerReceiver(statusReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_USER_UNLOCKED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        })
        val main = Handler(Looper.getMainLooper())
        runCatching {
            getSystemService(ConnectivityManager::class.java).registerNetworkCallback(
                NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
                wifiCallback, main,
            )
        }
        runCatching { getSystemService(AudioManager::class.java).registerAudioDeviceCallback(audioCallback, main) }
        runCatching { getSystemService(InputManager::class.java).registerInputDeviceListener(inputListener, main) }
        Device.lightKeys.forEach { contentResolver.registerContentObserver(Settings.System.getUriFor(it), false, lightObserver) }
        updateStatus()
    }

    override fun onStop() {
        runCatching { unregisterReceiver(statusReceiver) }
        runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(wifiCallback) }
        lostWifi.clear()
        runCatching { getSystemService(AudioManager::class.java).unregisterAudioDeviceCallback(audioCallback) }
        runCatching { getSystemService(InputManager::class.java).unregisterInputDeviceListener(inputListener) }
        contentResolver.unregisterContentObserver(lightObserver)
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyStatusBar()
    }

    /** 홈 버튼을 다시 누르면 첫 페이지로(자주 쓰는 앱·함께 읽는 책 모두) */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (::favRows.isInitialized) favRows.showPage(0)
        showAlso(0)
    }

    /** 홈에서 뒤로 가기는 아무것도 하지 않는다. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = Unit

    /** 함께 읽는 책 줄에서 시작한 밀기는 책을, 그 밖은 자주 쓰는 앱을 넘긴다. 책이 한 쪽뿐이면 자주 쓰는 앱. */
    override fun onSwipe(toLeft: Boolean): Boolean {
        val rows: Paged = if (touchOnAlso && alsoCount > 1) alsoPaged else favRows
        if (rows.pageCount <= 1) return false
        if (toLeft) rows.next() else rows.prev()
        return true
    }

    /**
     * 페이지 버튼은 설정 `홈에서 넘길 곳` 의 줄만 넘긴다(마지막으로 만진 줄을 따라가지 않는다).
     * 그 줄이 한 쪽뿐이거나 그려져 있지 않으면(칸에 들지 않음) 키를 먹지 않는다.
     */
    override fun onPageKey(next: Boolean): Boolean = when {
        prefs.pageKeysAlso -> alsoView?.let(::drawn) == true && alsoPaged.turn(next)
        ::favRows.isInitialized -> favRows.turn(next)
        else -> false
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) touchOnAlso = alsoView?.let { touches(it, ev) } == true
        return super.dispatchTouchEvent(ev)
    }

    /** [view] 가 그려져 있는가(칸에 다 들지 않아 그리지 않은 줄([FitColumn])은 보이게 둬도 빼고) */
    private fun drawn(view: View): Boolean {
        val parent = view.parent as? View ?: return false
        return view.isShown && view.bottom <= parent.height
    }

    /** [view] 가 그려져 있고 손가락이 그 안에서 눌렸는가 */
    private fun touches(view: View, ev: MotionEvent): Boolean {
        if (!drawn(view)) return false
        val at = IntArray(2).also { view.getLocationInWindow(it) }
        return ev.x >= at[0] && ev.x < at[0] + view.width && ev.y >= at[1] && ev.y < at[1] + view.height
    }

    /**
     * 홈에서는 안드로이드 상단바를 숨긴다. 위에서 아래로 쓸면 잠깐 나온다.
     * 설정 `기기 상단 바 · 홈에서도 보이기`를 켜면 보인다(교보 sam 7 처럼 빠른 설정이 상단 바 버튼뿐인 기기).
     * 그때는 시각·배터리 등이 겹치지 않게 홈 상단(로고·시계·상태 줄)을 빼고, 그 자리에 빈 띠를 두어 상단 바와 띄운다
     * (컬러면 색면 위에. 색면 안 표지 위아래 여백은 그대로 같다).
     */
    private fun applyStatusBar() {
        val show = prefs.showStatusBar
        topBar.visibility = if (show) View.GONE else View.VISIBLE
        (topBar.parent as View).setPadding(0, if (show) dp(STATUS_BAR_GAP_DP) else 0, 0, 0)
        setStatusBarVisible(show)
    }

    /** 화면이 꺼져 있을 때는 그리지 않는다(슬립화면 위에 덧그리지 않도록). 켜지면 SCREEN_ON 으로 다시 부른다. */
    private fun updateStatus() {
        if (!::topBar.isInitialized || !getSystemService(PowerManager::class.java).isInteractive) return
        topBar.update(DeviceStatus.read(this, lostWifi))
    }

    private fun buildViews() {
        // 흰 바탕은 배경(background)으로 두지 않고 직접 칠한다. 크레마는 앱이 바뀔 때마다 배경이 있는 뷰 묶음(창의 맨 바깥 포함)에
        // 명암 필터를 다시 걸고 다시 그려, 홈으로 돌아올 때 전자잉크가 한 번 더 깜빡인다. 창 배경도 그래서 비운다.
        val root = object : LinearLayout(this) {
            override fun dispatchDraw(canvas: Canvas) {
                canvas.drawColor(Color.WHITE)
                super.dispatchDraw(canvas)
            }
        }.apply { orientation = LinearLayout.VERTICAL }
        topBar = TopBarView(this, fixedFrame).apply { setOnClickListener { Device.openQuickSettings(this@HomeActivity) } }
        root.addView(topBar, lp(MATCH, WRAP))

        hero = FrameLayout(this)
        // 가로면 책 자리와 자주 쓰는 앱을 나란히(오른쪽 칸은 아래에서 채운다)
        val body = if (wide) hbox().apply { gravity = Gravity.TOP } else root
        if (wide) {
            root.addView(body, lp(MATCH, 0, 1f))
            body.addView(hero, lp(heroPaneWidth(), MATCH))
            body.addView(vline(PANE_LINE_INSET_DP))
        } else root.addView(hero, lp(MATCH, 0, 1f))
        // 높이가 정해지면(보통 처음 한 번) 이번 그리기를 건너뛰고 그 높이에 맞춰 책 자리를 채운 뒤 그린다.
        // (다음 차례로 미루면 빈 자리를 한 번 그린 뒤 다시 그리게 되어 전자잉크가 깜빡인다.)
        hero.viewTreeObserver.addOnPreDrawListener {
            if (hero.height == heroHeight) true else { shownHero = null; refresh(); false }
        }

        // 자주 쓰는 앱: 책 수와 상관없이 늘 목록 세 줄(격자는 그 높이 안에 두 줄) 높이
        // 홈의 틀(상단·책 자리·함께 읽는 책 줄·자주 쓰는 앱·하단 줄)은 글자 · 간격 설정과 상관없이 높이가 같다. 줄 간격은 홈에 쓰지 않고 글자만 키운다.
        // 가로면 상단 바로 아래라 조금 더 내린다. 쪽 표시는 목록을 만든 뒤 연결한다.
        val (favHeader, page) = pagedHeader(getString(R.string.favorites), if (wide) FAV_TITLE_TOP_WIDE_DP else 18) { favRows }
        favPage = page
        favLine = hline(1)
        // 가로면 오른쪽 칸 높이에 들어가는 만큼(책 자리와 따로라 줄 수를 고정하지 않아도 책 자리가 움직이지 않는다)
        favRows = PagedRows(this, if (fixedFrame) dp(Ui.ROW_DP) else rowDp(Ui.ROW_DP), fixedRows = if (wide) 0 else FAV_ROWS).apply { header = listOf(favHeader, favLine) }
        if (wide) body.addView(favRows, lp(0, MATCH, 1f)) else root.addView(favRows, lp(MATCH, WRAP))

        root.addView(hline(1, inset = false))
        val footer = FrameLayout(this)
        allAppsLink = text(getString(R.string.all_apps_link), 24f, Ui.bold).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Ui.MARGIN), 0, dp(32), 0)
            setOnClickListener { startActivity(AppListActivity.intent(this@HomeActivity, AppListActivity.Mode.ALL)) }
        }
        footer.addView(allAppsLink, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.START))
        readingText = text("", 15f, color = Ui.GRAY).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), 0)
            setOnClickListener { startActivity(ReadingActivity.intent(this@HomeActivity)) }
        }
        footer.addView(readingText, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.CENTER))
        footer.addView(PictureView(this).apply {
            drawable = getDrawable(R.drawable.ic_settings)
            contentDescription = getString(R.string.settings)
            setPadding(dp(24), dp((Ui.FOOTER_DP - 28) / 2), dp(Ui.MARGIN), dp((Ui.FOOTER_DP - 28) / 2))
            setOnClickListener { startActivity(Intent(this@HomeActivity, SettingsActivity::class.java)) }
        }, FrameLayout.LayoutParams(dp(24 + 32 + Ui.MARGIN), MATCH, Gravity.END))
        root.addView(footer, lp(MATCH, dp(Ui.FOOTER_DP)))

        favRows.onPageChanged = {
            favPage.text = pageLabel(favRows)
        }
        setContentView(root)
        window.setBackgroundDrawable(null)
    }

    /** 앱 목록과 설정을 다시 읽어 그린다. 홈으로 돌아올 때마다 부른다. */
    private fun refresh() {
        val changedPkgs = AppIcons.clearChangedPackages(this)
        apps = AppStore.loadAndPrune(this, prefs)
        // 맨 앞 책은 크게, 나머지는 함께 읽는 책 줄에서 쪽으로 넘긴다.
        val books = BookShelf.list(this)
        // 맨 앞 책의 앱이 곧 읽고 있는 앱이다.
        val reading = books.firstOrNull()?.app?.let(::findApp)
        // 자주 쓰는 앱은 직접 넣고 뺀 그대로 보인다(위의 책·앱과 상관없다).
        val favs = favoriteApps(apps, prefs)
        val icons = prefs.showIcons
        // 설정에서 고른 팩과 아이콘을 가져오는 팩들(바꾸거나 업데이트하면 달라진다), 앱마다 고른 아이콘(그 앱이 보이는 자리만 다시 그린다).
        // 고른 팩을 따로 넣는다: 앱마다 고른 아이콘이 같은 팩이면 `기본` 으로 바꿔도 쓰는 팩들은 그대로다.
        val packs = prefs.iconPack to AppIcons.usedPacks(this).associateWith { IconPacks.stamp(this, it) }
        val ownIcons = prefs.iconOverrides
        val grid = prefs.favGrid

        heroHeight = hero.height
        // 책이 없으면 자리 표시의 안내 줄이 자동 추가 켜짐에 따라 달라진다
        val watching = books.isEmpty() && ReaderWatchService.isEnabled(this)
        // 진행률·반납일 글자(반납일은 날이 바뀌면 달라진다)
        val status = books.map { bookStatus(it) }
        // 표지가 생겼는지(책 파일 표지는 뒤에서 꺼내 둔다: 압축 만화·만화 표지 도구를 받은 뒤. 책 목록이 그대로여도 다시 그리게)
        val covers = books.map { BookShelf.hasCover(this, it.id) }
        val heroState = listOf(books, reading, icons, packs, ownIcons[reading?.key], heroHeight, watching, status, covers)
        // 아이콘이 바뀌었을 수 있는 앱(그사이 업데이트된 앱)이 보이는 자리만 다시 그린다(다른 앱 업데이트로 홈 전체가 깜빡이지 않게).
        if (reading?.pkg in changedPkgs || heroState != shownHero) {
            shownHero = heroState
            buildHero(books, reading)
        }
        val favState = listOf(favs, icons, packs, favs.map { ownIcons[it.key] }, grid)
        if (favs.none { it.pkg in changedPkgs } && favState == shownFavs) return
        shownFavs = favState
        favRows.setGrid(if (!grid) 0 else if (wide) PagedRows.GRID_AUTO else GRID_ROWS)
        favRows.setRows(
            if (favs.isEmpty()) listOf {
                text(getString(R.string.favorites_empty), 18f, color = Ui.LIGHT_GRAY, lines = 2).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(Ui.MARGIN), 0, dp(Ui.MARGIN), 0)
                }
            } else if (grid) favs.chunked(gridColumns).let { lines ->
                // 한 쪽의 몇째 줄인지와, 그 쪽이 한 줄뿐인지(위 줄 모양으로 둔다). 한 쪽 줄 수는 그릴 때 정해져 있다(가로면 높이에 따라).
                lines.mapIndexed { i, line ->
                    {
                        val per = maxOf(1, favRows.perPage)
                        gridRow(line, i % per, per, lastAlone = i == lines.lastIndex && i % per == 0)
                    }
                }
            }
            else favs.map { app ->
                {
                    appRow(app, app.label, Ui.ROW_ICON_DP, Ui.ROW_SP, showIcon = icons).apply {
                        setOnClickListener { AppStore.launch(this@HomeActivity, app) }
                        setOnLongClickListener { showAppMenu(app, rename = true) { refresh() }; true }
                    }
                }
            }
        )
    }

    /**
     * 격자 한 줄([pos]: 한 쪽 [per] 줄 중 몇째 줄): 아이콘(흑백, 컬러 [Palette] 면 원래 색) 아래 이름 한 줄.
     * 줄들이 한 묶음으로 보이게 바깥(위 선 → 첫 줄, 끝 줄 → 하단 선)은 [GRID_OUTER_DP], 줄 사이는 [GRID_INNER_DP] 로 좁게 둔다.
     * 줄 높이(목록 높이를 줄 수로 나눈 값)에서 여백·이름 한 줄을 뺀 만큼 아이콘을 키우고(최대 [GRID_ICON_MAX_DP]), 그래도 남는 높이는 바깥으로 보낸다
     * (첫 줄은 아래에, 끝 줄은 위에, 가운데 줄(가로 화면)은 가운데에 붙인다). 한 줄뿐인 쪽([lastAlone])은 첫 줄 모양으로 위에 붙인다.
     * 격자에서는 '앱 아이콘' 설정과 상관없이 아이콘을 그린다.
     */
    private fun gridRow(line: List<AppEntry>, pos: Int, per: Int, lastAlone: Boolean): View = hbox().apply {
        // 함께 읽는 책 줄과 같은 칸(좌우 여백 + 칸 사이 간격)으로 나누고, 칸마다 아이콘과 이름을 가운데에 맞춘다.
        val first = pos == 0
        val last = pos == per - 1
        gravity = when {
            first && !lastAlone -> Gravity.BOTTOM
            first || last -> Gravity.TOP
            else -> Gravity.CENTER_VERTICAL
        }
        val inner = GRID_INNER_DP / 2
        setPadding(dp(Ui.MARGIN), dp(if (first) GRID_OUTER_DP else inner), dp(Ui.MARGIN), dp(if (last) GRID_OUTER_DP else inner))
        // 이름은 한 줄(길면 `…`)이라 글자를 키워도 이름 칸은 한 줄만큼만 커진다. 남는 높이를 아이콘에 준다.
        val labelH = text("가", 13f).run { measure(0, 0); measuredHeight }
        val icon = (favRows.rowHeightPx - dp(GRID_OUTER_DP + inner + GRID_LABEL_GAP_DP) - labelH).coerceIn(dp(32), dp(GRID_ICON_MAX_DP))
        line.forEachIndexed { i, app ->
            addView(vbox().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                addView(picture(AppIcons.icon(context, app, icon)), lp(icon, icon))
                addView(text(app.label, 13f).apply { gravity = Gravity.CENTER; ellipsize = TextUtils.TruncateAt.END }, lp(MATCH, WRAP).apply { topMargin = dp(GRID_LABEL_GAP_DP) })
                setOnClickListener { AppStore.launch(this@HomeActivity, app) }
                setOnLongClickListener { showAppMenu(app, rename = true) { refresh() }; true }
            }, lp(0, WRAP, 1f).apply { if (i > 0) marginStart = dp(GAP_DP) })
        }
        // 덜 찬 줄도 칸 폭이 같도록 빈 칸을 채운다.
        repeat(gridColumns - line.size) { addView(View(context), lp(0, 1, 1f).apply { marginStart = dp(GAP_DP) }) }
    }

    /**
     * 구역 제목(함께 읽는 책 · 자주 쓰는 앱): 작은 회색 굵은 글자, 아래에 가는 선. 높이는 기준 크기 글자일 때로 고정하고
     * 글자를 아래(선 쪽)에 붙여, 키운 글자는 위 여백([topDp])으로 자란다.
     */
    private fun sectionTitle(title: String, topDp: Int) = text(title, 13f, Ui.bold, Ui.GRAY).apply {
        val baseH = frameText { text(title, 13f, Ui.bold).run { measure(0, 0); measuredHeight } }
        layoutParams = lp(MATCH, dp(topDp) + baseH + dp(8))
        gravity = Gravity.BOTTOM
        setPadding(dp(Ui.MARGIN), 0, dp(Ui.MARGIN), dp(8))
    }

    /**
     * 쪽으로 넘기는 구역(함께 읽는 책 · 자주 쓰는 앱)의 제목 줄: 왼쪽 [sectionTitle], 오른쪽 끝에 쪽 표시 `‹ 1 / 2 ›`(제목과 같은 글자·여백,
     * 글자는 부르는 쪽이 채운다). 쪽 표시의 왼쪽 절반은 앞 쪽, 오른쪽 절반은 다음 쪽([rows]).
     */
    private fun pagedHeader(title: String, topDp: Int, rows: () -> Paged): Pair<LinearLayout, TextView> {
        val titleView = sectionTitle(title, topDp)
        val page = text("", 13f, Ui.bold, Ui.GRAY).apply {
            gravity = Gravity.BOTTOM
            setPadding(dp(24), 0, dp(Ui.MARGIN), dp(8))
            pagesClick(rows)
        }
        val header = hbox().apply {
            layoutParams = lp(MATCH, titleView.layoutParams.height)
            addView(titleView, lp(0, MATCH, 1f))
            addView(page, lp(WRAP, MATCH))
        }
        return header to page
    }

    private fun findApp(key: String): AppEntry? = AppStore.find(this, key, apps)

    /** 하단 가운데의 `오늘 42분 · 5일째`(바형 화면은 `오늘` 없이). 사용 기록 액세스가 없으면(= 오늘 읽은 시간 꺼짐) 비운다. */
    private fun updateReading() {
        // 하루 합계(통계에서 뺀 카테고리의 책은 빼고). 권한이 없으면 null.
        val summary = ReadingLog.summary(this, ReadingLog.readerPackages(this))
        // 긴 것부터: `오늘 42분 · 5일째` → `42분 · 5일째` → `42분`(연속일은 이틀째부터). 바형은 `42분 · 5일째` 부터
        val lines = summary?.let {
            val time = duration(it.todayMs)
            fun withStreak(s: String) = if (it.streak >= 2) s + "  ·  " + getString(R.string.reading_streak, it.streak) else s
            val full = if (Ui.tallScreen) null else withStreak(getString(R.string.reading_today, time))
            listOfNotNull(full, withStreak(time), time).distinct()
        } ?: listOf("")
        fitReading(lines)
    }

    /**
     * 하단 가운데 글자는 화면 가운데에 두되, `모든 앱 ›`·설정 아이콘에 닿으면 둘 사이 가운데로 옮기고 그래도 들지 않으면 짧은 글로 바꾼다
     * (기기가 앱의 밀도를 덮어써 폭이 좁은 화면·키운 글자. HiBreak 에서 `모든 앱 ›` 에 겹쳤다). [lines] 는 긴 것부터.
     */
    private fun fitReading(lines: List<String>) {
        val width = resources.displayMetrics.widthPixels
        // 글자 폭만 잰다(뷰를 다시 measure 하면 TextView 가 세로 가운데를 그 높이로 잡아 `모든 앱 ›` 이 위로 붙는다)
        val left = allAppsLink.paddingStart + allAppsLink.paint.measureText(allAppsLink.text.toString()).toInt() + dp(FOOTER_GAP_DP)
        // 설정 아이콘 칸(24 + 32 + 여백)에서 왼쪽 24dp 는 누르는 자리
        val right = width - dp(32 + Ui.MARGIN) - dp(FOOTER_GAP_DP)
        val paint = readingText.paint
        val line = lines.firstOrNull { paint.measureText(it) <= right - left } ?: lines.last()
        val half = paint.measureText(line) / 2
        val center = width / 2f
        readingText.translationX = if (center - half >= left && center + half <= right) 0f else (left + right) / 2f - center
        if (readingText.text.toString() != line) readingText.text = line
    }

    /** 파일을 다시 찾고 있는 책([openBook]. 그동안 같은 책을 또 누르면 무시한다) */
    private var findingFile: String? = null

    /**
     * 그 책의 앱을 연다(파일로만 여는 이북 앱은 그 책의 파일). 앱을 아직 안 정했거나 지워졌으면 고르러 간다.
     * 파일로만 여는 앱의 책인데 그 파일을 모르거나 없으면 뒤에서 다시 찾아 열고([AppStore.findBookFile]), 그래도 없으면(지워짐·옮겨짐)
     * 그 앱에서 한 번 열어 달라고 알린다.
     */
    private fun openBook(book: ShelfBook) {
        val app = book.app?.let(::findApp)
        if (app == null) return startActivity(AppListActivity.intent(this, AppListActivity.Mode.PICK_READER, book.id))
        if (AppStore.openBook(this, book, app) || findingFile == book.id) return
        findingFile = book.id
        Bg.run({ AppStore.findBookFile(this, book, app) }) { result ->
            findingFile = null
            if (isDestroyed) return@run
            val path = result.getOrNull()
            if (path == null || !AppStore.openFoundFile(this, book, app, path)) fileMissing(app)
        }
    }

    /** 파일로만 여는 이북 앱 [app] 의 책인데 그 파일을 찾지 못했다 */
    private fun fileMissing(app: AppEntry) {
        Sheet(this).header(getString(R.string.book_file_missing_title), getString(R.string.book_file_missing_desc, app.label)).centered().show()
    }

    /** 함께 읽는 책 한 쪽의 잰 줄 높이: (표지 폭, 책 자리 폭, 그 쪽 책마다 제목·앱 이름·진행률 글자) → 높이([buildHero]) */
    private val alsoHeights = HashMap<List<Any?>, Int>()

    /**
     * 읽고 있는 책 자리. 없으면 같은 모양의 스켈레톤, 한 권이면 남는 높이를 채우는 큰 표지,
     * 여러 권이면 남는 높이에 맞춘 맨 앞 책 + 아래 '함께 읽는 책' 줄.
     * 칸 높이는 글자 · 간격 설정과 상관없이 기준 크기 글자로 정하고, 키운 글자가 칸에 다 들지 않는 줄은 그리지 않는다([FitColumn]).
     */
    private fun buildHero(books: List<ShelfBook>, reading: AppEntry?) {
        hero.removeAllViews()
        alsoView = null
        fillAlso = null
        // 함께 읽는 책(맨 앞 책 다음)을 [ALSO_PER_PAGE] 권씩 쪽으로. 책이 바뀌었으면(누른 책이 맨 앞으로 올라감 등) 1쪽부터.
        val rest = books.drop(1)
        val pages = rest.chunked(ALSO_PER_PAGE)
        val ids = rest.map { it.id }
        if (ids != alsoIds) alsoPage = 0
        alsoIds = ids
        alsoCount = pages.size
        alsoPage = alsoPage.coerceIn(0, maxOf(0, alsoCount - 1))
        if (heroHeight <= 0) return
        val view: View = when (books.size) {
            0 -> skeleton(coverWidth())
            1 -> mainBook(books.first(), reading, coverWidth())
            else -> {
                /**
                 * [measureOnly] 면 표지 그림 대신 같은 크기의 빈 칸(높이만 잴 때. 표지는 폭마다 줄인 파일을 남긴다).
                 * 잴 때는 칸을 내용 높이로, 그릴 때는 잰 높이를 채우게 둔다(높이를 모를 때 채우는 칸은 높이 계산에서 빠진다).
                 */
                fun LinearLayout.alsoCells(coverW: Int, page: Int, measureOnly: Boolean) {
                    val shown = pages[page]
                    shown.forEachIndexed { i, book ->
                        addView(smallBook(book, coverW, measureOnly), lp(0, if (measureOnly || !fixedFrame) WRAP else MATCH, 1f).apply { if (i > 0) marginStart = dp(GAP_DP) })
                    }
                    // 덜 찬 쪽도 표지 폭·자리가 같도록 빈 칸을 채운다.
                    repeat(ALSO_PER_PAGE - shown.size) { addView(View(context), lp(0, 1, 1f).apply { marginStart = dp(GAP_DP) }) }
                }
                fun alsoRow(coverW: Int, page: Int, measureOnly: Boolean = false) = vbox().apply {
                    // 컬러면 맨 앞 책 색면이 그만큼 아래로 내려와 있어 위 여백을 줄인다(줄 높이는 같게).
                    val (header, pageText) = pagedHeader(getString(R.string.also_reading), 28 - fieldBottom) { alsoPaged }
                    if (!measureOnly) pageText.text = pageLabel(alsoPaged)
                    addView(header)
                    addView(hline(1))
                    val cells = hbox().apply {
                        gravity = Gravity.TOP
                        setPadding(dp(Ui.MARGIN), dp(14), dp(Ui.MARGIN), 0)
                        alsoCells(coverW, page, measureOnly)
                    }
                    addView(cells, lp(MATCH, 0, 1f))
                    if (!measureOnly) {
                        alsoView = this
                        // 쪽을 넘기면 칸과 쪽 표시만 바꾼다(맨 앞 책은 다시 그리지 않는다).
                        fillAlso = { p ->
                            cells.removeAllViews()
                            cells.alsoCells(coverW, p, false)
                            pageText.text = pageLabel(alsoPaged)
                        }
                    }
                }
                fun View.measuredIn(): Int {
                    measure(
                        View.MeasureSpec.makeMeasureSpec(hero.width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    )
                    return measuredHeight
                }
                // 쪽마다 줄 높이(제목 줄 수·진행률)가 다를 수 있어 가장 높은 쪽으로 잰다(넘겨도 맨 앞 책 표지 크기가 그대로).
                // 쪽마다 잰 높이는 그 쪽에 보이는 글자로 기억해 둔다(진행률 하나가 바뀌어도 모든 쪽을 다시 만들어 재지 않게).
                fun alsoHeight(coverW: Int) = frameText {
                    if (alsoHeights.size > ALSO_HEIGHTS_MAX) alsoHeights.clear()
                    pages.indices.maxOf { p ->
                        val key = listOf(coverW, hero.width, pages[p].map { listOf(it.title, it.app?.let(::findApp)?.label, bookStatus(it)) })
                        alsoHeights.getOrPut(key) { alsoRow(coverW, p, measureOnly = true).measuredIn() }
                    }
                }
                // 함께 읽는 책 줄 높이를 먼저 재고(기준 크기 글자로), 남는 높이를 맨 앞 책 표지에 준다.
                var textPart = 0
                val alsoW = if (tall) {
                    // 바형은 세로가 남아 함께 읽는 책 표지를 키우되, 큰 표지와 크기 차이가 나게 그 [SMALL_COVER_RATIO] 배로 둔다
                    // (100dp ~ 칸 폭). 표지 말고 줄 높이(제목·앱·진행률)는 표지 크기와 상관없으니, 남는 높이를 두 표지에 그 비율로 나눈다.
                    textPart = alsoHeight(dp(SMALL_COVER_DP * 2)) - (dp(SMALL_COVER_DP * 2) * 3 / 2 + dp(2))
                    val room = heroHeight - dp(heroPad) - dp(HERO_PAD_BOTTOM_DP) - textPart - dp(4)
                    val mainW = minOf(dp(COVER_MAX_DP), (room / (1.5f * (1 + SMALL_COVER_RATIO))).toInt())
                    val cellW = (hero.width - 2 * dp(Ui.MARGIN) - (ALSO_PER_PAGE - 1) * dp(GAP_DP)) / ALSO_PER_PAGE - dp(2)
                    minOf(maxOf((mainW * SMALL_COVER_RATIO).toInt(), dp(SMALL_COVER_DP * 2)), cellW)
                } else dp(SMALL_COVER_DP)
                // 바형은 표지 말고 줄 높이가 표지 크기와 상관없어 위에서 잰 것에 표지 높이만 더한다(모든 쪽을 다시 재지 않게)
                val alsoH = if (tall) textPart + alsoW * 3 / 2 + dp(2) else alsoHeight(alsoW)
                val main = mainBook(books.first(), reading, coverWidth(reserved = alsoH, min = COVER_SMALLER_DP))
                // 책 자리에 남는 높이(아래 여백, 바형은 표지가 가장 커도 남는 높이)도 함께 읽는 책 줄에 준다(키운 글자가 더 든다. 틀 위치는 같다).
                // 화면이 낮아 맨 앞 책 표지가 가장 작아도 함께 읽는 책 줄이 다 들지 않으면 그 줄을 그리지 않는다.
                // 틀을 고정하지 않으면(기기가 밀도를 덮어씀) 1.5.3 까지처럼 그대로 쌓는다(넘치면 책 자리 끝에서 잘린다).
                if (fixedFrame) FitColumn(this@HomeActivity).apply {
                    addView(main)
                    addView(alsoRow(alsoW, alsoPage), lp(MATCH, maxOf(alsoH, heroHeight - main.measuredIn())))
                } else vbox().apply {
                    addView(main)
                    addView(alsoRow(alsoW, alsoPage))
                }
            }
        }
        hero.addView(view, FrameLayout.LayoutParams(MATCH, WRAP))
    }

    /**
     * 맨 앞 책 표지 폭: 책 자리 높이에서 위아래 여백과 그 아래에 놓일 높이([reserved], 함께 읽는 책 줄)를 뺀 만큼.
     * 너무 작거나 커지지 않게 [min]dp ~ COVER_MAX_DP.
     */
    private fun coverWidth(reserved: Int = 0, min: Int = COVER_DP): Int {
        val coverH = heroHeight - dp(heroPad) - dp(HERO_PAD_BOTTOM_DP) - reserved
        // 가로면 칸이 좁아 표지가 칸 폭의 [WIDE_COVER_RATIO] 를 넘지 않게(옆 제목 칸을 남긴다)
        val max = if (wide) minOf(dp(COVER_MAX_DP), ((hero.width - 2 * dp(Ui.MARGIN)) * WIDE_COVER_RATIO).toInt()) else dp(COVER_MAX_DP)
        return (coverH * 2 / 3).coerceIn(minOf(dp(min), max), max)
    }

    /**
     * 맨 앞 책 표지 위 여백(dp, 컬러면 홈 색면 안에서 표지 위아래 여백). 상단 줄 바로 아래라 좁게 두고,
     * 세로가 남는 바형 화면([tall])은 조금 더 띄운다.
     */
    private val heroPad get() = if (tall) HERO_PAD_TALL_DP else HERO_PAD_TOP_DP

    /** 컬러면 맨 앞 책 자리를 칠하는 색면의 아래 여백(dp). 흑백이면 0. */
    private val fieldBottom get() = if (Palette.current.color) heroPad else 0

    /** 맨 앞 책 자리(표지·제목): 컬러면 홈 색면([Palette.Area.HOME])을 칠하고 아래로 [fieldBottom] 만큼 넓힌다. */
    private fun View.heroField() {
        setPadding(dp(Ui.MARGIN), dp(heroPad), dp(Ui.MARGIN), dp(fieldBottom))
        Palette.current.field(Palette.Area.HOME)?.let { setBackgroundColor(it) }
    }

    /**
     * 책이 없을 때: 표지 자리에 `+` 상자, 제목·저자·앱 자리에 회색 막대, 자동 추가가 켜져 있으면 그 아래 회색 안내
     * `이북 앱에서 책을 펴면 여기에 들어옵니다`. 누르면 책을 넣는 법 안내([showAddGuide]).
     */
    private fun skeleton(coverW: Int): View = hbox().apply {
        gravity = Gravity.TOP
        heroField()
        val coverH = coverW * 3 / 2
        addView(DashedBox(context), lp(coverW + dp(2), coverH + dp(2)))
        val info = vbox()
        // 책이 들어오면 채워질 자리: 제목 · 저자 · (아래) 읽고 있는 앱. 폭은 비율로(그리기 전에 정해지게 weight 로).
        fun bar(widthFrac: Float, heightDp: Int) = hbox().apply {
            addView(View(context).apply { setBackgroundColor(Ui.DIVIDER) }, lp(0, dp(heightDp), widthFrac))
            addView(View(context), lp(0, dp(heightDp), 1f - widthFrac))
        }
        info.addView(bar(0.7f, 26), lp(MATCH, WRAP).apply { topMargin = dp(14) })
        info.addView(bar(0.45f, 14), lp(MATCH, WRAP).apply { topMargin = dp(14) })
        info.addView(bar(0.4f, 18), lp(MATCH, WRAP).apply { topMargin = dp(30) })
        if (ReaderWatchService.isEnabled(context)) {
            info.addView(text(getString(R.string.home_empty_hint), 18f, color = Ui.GRAY, lines = 3), lp(MATCH, WRAP).apply { topMargin = dp(30) })
        }
        addView(info, lp(0, coverH + dp(2), 1f).apply { marginStart = dp(24) })
        setOnClickListener { showAddGuide() }
    }

    /**
     * 책을 넣는 법(빈 홈의 책 자리를 누르면): 책은 이북 앱에서 펴면 자동 추가가 넣는다. 자동 추가가 꺼져 있으면 `자동 추가 켜기 ›`(시스템 접근성 설정,
     * 꺼짐 안내 창의 `접근성 설정 열기` 와 같다)를 먼저, 그 아래 `직접 추가 ›`(지원하지 않는 이북 앱용, 책 추가 ⑨). 읽고 있는 책 목록의 `책 추가 ›` 는 이 안내 없이 바로 ⑨.
     */
    private fun showAddGuide() {
        val on = ReaderWatchService.isEnabled(this)
        val sheet = Sheet(this).header(getString(R.string.add_guide_title), getString(if (on) R.string.add_guide_on else R.string.add_guide_off))
        if (!on) sheet.item(getString(R.string.add_guide_turn_on), bold = true) { ReaderWatchService.openSettings(this) }
        sheet.item(getString(R.string.add_guide_manual)) { startActivity(BookSearchActivity.intent(this)) }.show()
    }

    /** 표지 자리: 연한 회색으로 채우고 점선 테두리, 가운데에 + */
    private class DashedBox(context: Context) : View(context) {
        private val fill = Paint().apply { color = Ui.DIVIDER }
        private val plus = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Ui.GRAY
            strokeWidth = context.dpf(3)
            strokeCap = Paint.Cap.ROUND
        }
        private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Ui.LIGHT_GRAY
            strokeWidth = context.dpf(1.5f)
            pathEffect = DashPathEffect(floatArrayOf(context.dpf(6), context.dpf(5)), 0f)
        }
        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
            val inset = border.strokeWidth / 2
            canvas.drawRect(inset, inset, width - inset, height - inset, border)
            val cx = width / 2f
            val cy = height / 2f
            val arm = width * 0.11f
            canvas.drawLine(cx - arm, cy, cx + arm, cy, plus)
            canvas.drawLine(cx, cy - arm, cx, cy + arm, plus)
        }
    }

    /**
     * 맨 앞 책: 왼쪽 표지, 오른쪽 제목·저자와 읽고 있는 앱. 진행률은 글자 칸 아래 끝(표지 아래 끝)에 `55% 읽음` 과 그 오른쪽 막대 한 줄,
     * 글자를 키워 그 자리가 모자라면 그 줄은 그리지 않는다(글자 · 간격을 줄이는 것은 사용자가).
     */
    private fun mainBook(book: ShelfBook, reading: AppEntry?, coverW: Int): View = hbox().apply {
        gravity = Gravity.TOP
        heroField()
        val coverH = coverW * 3 / 2
        addView(coverView(book, coverW), lp(coverW + dp(2), coverH + dp(2)))
        // 칸 높이는 표지 높이. 키운 글자가 다 들지 않으면 아래 줄부터 그리지 않는다.
        val info = column()
        // 글자를 키웠을 때 긴 제목이 두 줄에서 잘리지 않게 줄인다(옆 칸 폭: 화면 − 좌우 여백 − 표지 − 사이).
        val infoW = hero.width - 2 * dp(Ui.MARGIN) - (coverW + dp(2)) - dp(24)
        info.addView(text(book.title, 34f, Ui.heavy, Palette.current.title(Palette.Area.HOME), lines = 2).apply { fitLines(34f, infoW) }, lp(MATCH, WRAP).apply { topMargin = dp(10) })
        if (book.author.isNotBlank()) {
            info.addView(text(book.author, 16f, color = Ui.GRAY), lp(MATCH, WRAP).apply { topMargin = dp(10) })
        }
        // 이북 앱 서재에서 읽어 온 읽는 화·반납일(읽는 책 자동 추가를 켰을 때), 한 줄씩. 진행률은 아래 끝에 막대와 함께.
        bookStatus(book, withPercent = false).forEachIndexed { i, s ->
            info.addView(text(s, 15f, color = Ui.GRAY), lp(MATCH, WRAP).apply { topMargin = dp(if (i == 0) 6 else 2) })
        }
        info.addView(
            if (reading != null) hbox().apply {
                if (prefs.showIcons) addView(picture(AppIcons.icon(context, reading, dp(26))), lp(dp(26), dp(26)).apply { marginEnd = dp(10) })
                addView(text("${reading.label}  ›", 20f, Ui.bold))
            } else text(getString(R.string.pick_reader_link), 20f, Ui.bold, Ui.LIGHT_GRAY),
            // 제목·저자 바로 아래에 붙여 한 덩어리로 읽히게 한다.
            lp(WRAP, WRAP).apply { topMargin = dp(26) }
        )
        val infoH = coverH + dp(2)
        // `55% 읽음 ▬▬▬───` 한 줄: 글자 오른쪽에 칸 끝까지 막대(글자 줄 가운데 높이)
        val progress = if (!book.percentShown) null else hbox().apply {
            addView(text(getString(R.string.progress_pct, book.progress), 15f, color = Ui.GRAY))
            addView(ProgressLine(context, PROGRESS_DP).apply { percent = book.progress }, lp(0, WRAP, 1f).apply { marginStart = dp(12) })
        }
        // 진행률 줄은 가장 아래 줄이라 글자 줄 아래에 [PROGRESS_GAP_DP] 띄워 다 들어갈 때만 둔다(아래 줄부터 그리지 않는다)
        val column = if (progress == null || info.heightAt(infoW) + dp(PROGRESS_GAP_DP) + progress.heightAt(infoW) > infoH) info
        else FrameLayout(context).apply {
            addView(info, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(progress, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))
        }
        addView(column, lp(0, infoH, 1f).apply { marginStart = dp(24) })
        setOnClickListener { openBook(book) }
        setOnLongClickListener { showBookMenu(book); true }
    }

    /**
     * 함께 읽는 책: 작은 표지 옆에 제목과 그 책의 앱. 누르면 맨 앞으로 올리고 그 앱을 연다.
     * 표지 폭은 [w](px), [measureOnly] 면 표지 그림 없이 자리만(높이를 잴 때). 바형 세로 화면([tall])은 세로가 남아 표지를 키우고(맨 앞 책 표지의 0.5배, 100dp ~ 칸 폭), 옆에 자리가 없어 글자는 표지 아래에 둔다(표지·글자 모두 칸 가운데, 올해 읽은 책 격자처럼).
     */
    private fun smallBook(book: ShelfBook, w: Int, measureOnly: Boolean = false): View = (if (tall) vbox() else hbox()).apply {
        gravity = if (tall) Gravity.CENTER_HORIZONTAL else Gravity.TOP
        addView(if (measureOnly) View(context) else coverView(book, w), lp(w + dp(2), w * 3 / 2 + dp(2)))
        val info = column()
        info.addView(text(book.title, 16f, Ui.bold, lines = 2))
        // 글자 칸은 칸 폭으로(글자 폭에 딱 맞추면 글자 크기가 소수점일 때 마지막 글자 끝이 잘렸다: 90% 에서 `15% read` 의 d)
        book.app?.let(::findApp)?.let {
            info.addView(text(it.label, 13f, color = Ui.GRAY), lp(MATCH, WRAP).apply { topMargin = dp(4) })
        }
        bookStatus(book).forEach { info.addView(text(it, 12f, color = Ui.GRAY), lp(MATCH, WRAP).apply { topMargin = dp(2) }) }
        if (tall) addView(info.apply {
            gravity = Gravity.CENTER_HORIZONTAL
            for (i in 0 until childCount) (getChildAt(i) as? TextView)?.gravity = Gravity.CENTER_HORIZONTAL
        }, (if (measureOnly || !fixedFrame) lp(MATCH, WRAP) else lp(MATCH, 0, 1f)).apply { topMargin = dp(10) })
        else addView(info, lp(0, if (measureOnly || !fixedFrame) WRAP else MATCH, 1f).apply { marginStart = dp(10) })
        setOnClickListener {
            BookShelf.moveToFront(this@HomeActivity, book.id)
            openBook(book)
        }
        setOnLongClickListener { showBookMenu(book); true }
    }

    private fun coverView(book: ShelfBook, widthPx: Int): View = coverImage(BookShelf.cover(this, book, widthPx))

    /** 책을 길게 누르면 올해 읽은 책과 같은 책 상세([showBookDetail]) */
    private fun showBookMenu(book: ShelfBook) = showBookDetail(BookRecord.of(this, book)) { refresh() }

    companion object {
        /** 하단 가운데 글자와 양쪽(`모든 앱 ›`·설정 아이콘) 사이 최소 간격 */
        private const val FOOTER_GAP_DP = 12
        /** 자주 쓰는 앱 고정 줄 수 */
        private const val FAV_ROWS = 3
        /** 기억해 두는 함께 읽는 책 쪽 높이 수(넘으면 비운다) */
        private const val ALSO_HEIGHTS_MAX = 200
        /** 켜져 있는데 안 붙은 서비스를 '멈춤'으로 보기까지 기다리는 때(앱이 막 뜨면 붙는 데 1~2초 걸린다) */
        private const val STALLED_WAIT_MS = 5_000L
        /** 맨 앞 책 표지는 남는 높이에 맞춰 COVER_DP~COVER_MAX_DP(여러 권이면 COVER_SMALLER_DP 부터) */
        private const val COVER_DP = 160
        private const val COVER_MAX_DP = 230
        private const val COVER_SMALLER_DP = 136
        /** 맨 앞 책 진행률 막대 높이, 그 위 글자(읽고 있는 앱 줄)와 떨어질 최소 간격 */
        private const val PROGRESS_DP = 6
        private const val PROGRESS_GAP_DP = 12
        /** 맨 앞 책 표지 위 여백(바형은 [HERO_PAD_TALL_DP]). 컬러면 홈 색면 안에서 표지 아래 여백도 같다. */
        private const val HERO_PAD_TOP_DP = 8
        private const val HERO_PAD_TALL_DP = 14
        private const val HERO_PAD_BOTTOM_DP = 22
        /** 기기 상단 바를 보일 때(홈 상단을 뺄 때) 상단 바 아래 빈 띠 */
        private const val STATUS_BAR_GAP_DP = 16
        /** 함께 읽는 책 한 쪽의 칸 수(맨 앞 책과 함께 [BookShelf.HOME]) */
        private const val ALSO_PER_PAGE = BookShelf.HOME - 1
        /** 함께 읽는 책 표지 폭 */
        private const val SMALL_COVER_DP = 50
        /** 바형 화면에서 함께 읽는 책 표지 폭 ÷ 맨 앞 책 표지 폭 */
        private const val SMALL_COVER_RATIO = 0.5f

        /** 자주 쓰는 앱 격자: 4칸 × 2줄(목록과 같은 높이 안에), 아이콘 최대 크기 */
        private const val GRID_COLUMNS = 4
        /** 함께 읽는 책·격자 칸 사이 간격(두 줄의 칸을 맞춘다) */
        private const val GAP_DP = 12
        private const val GRID_ROWS = 2
        private const val GRID_ICON_MAX_DP = 56
        /** 격자: 바깥(위 선 → 첫 줄 아이콘, 둘째 줄 이름 → 하단 선), 줄 사이(첫 줄 이름 → 둘째 줄 아이콘), 아이콘과 이름 사이 */
        private const val GRID_OUTER_DP = 11
        private const val GRID_INNER_DP = 12
        private const val GRID_LABEL_GAP_DP = 6
        /** 가로 화면: 책 칸 폭(화면 폭에 대한 비율, 세로 화면 폭까지), 두 칸 사이 세로 선의 위아래 여백, 표지 폭 상한(칸 폭에 대한 비율) */
        private const val HERO_PANE_RATIO = 0.6f
        private const val PANE_LINE_INSET_DP = 16
        private const val WIDE_COVER_RATIO = 0.42f
        /** 오른쪽 칸(여백 안)이 이보다 좁으면 자주 쓰는 앱 격자를 3칸으로 */
        private const val GRID_NARROW_DP = 360
        /** 가로 화면 자주 쓰는 앱 제목 위 여백(세로는 18dp) */
        private const val FAV_TITLE_TOP_WIDE_DP = 34

    }
}
