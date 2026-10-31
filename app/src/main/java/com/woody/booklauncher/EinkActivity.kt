package com.woody.booklauncher

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 전자잉크용 화면 공통: 화면 전환 애니메이션을 끄고, 좌우로 밀면 [onSwipe] 를 부른다.
 * 밀기로 끝난 터치는 아래 뷰에 취소로 전달해 눌림(클릭)으로 처리되지 않게 한다.
 */
abstract class EinkActivity : Activity() {
    protected lateinit var prefs: HomePrefs
    private var downX = 0f
    private var downY = 0f
    /** 이 화면을 만들 때 쓴 언어. 설정에서 바뀌었으면 돌아올 때 다시 만든다. */
    private var createdEnglish = false
    /** 이 화면을 만들 때 쓴 글자 크기·줄 간격(%). 설정에서 바뀌었으면 돌아올 때 다시 만든다. */
    private var createdTextScale = 100
    private var createdRowScale = 100
    /** 이 화면을 만들 때 쓴 색. 설정에서 바뀌었으면 돌아올 때 다시 만든다. */
    private var createdPalette = Palette.MONO
    /** 이 화면이 쓰는 배율([Ui.textScale]·[Ui.rowScale]). 앱 전체 값이라 이 화면으로 돌아올 때마다 다시 맞춘다. */
    private var textScale = 1f
    private var rowScale = 1f

    /**
     * true 면 글자 · 간격 설정과 기기 글꼴 크기를 따르지 않고 늘 기본(100%) 크기로 그린다.
     * 사진으로 찍어 보내는 진단 화면(기기 정보·자동 추가 기록)이 한 화면에 다 들어가게.
     */
    protected open val fixedTextSize: Boolean get() = false

    /**
     * 설정의 언어(기기 언어 따르기/한국어/영어)로 화면 글자와 날짜 형식을 정하고, 화면 폭이 [Ui.DESIGN_WIDTH_DP] 가 되도록 밀도를 다시 정한다.
     * 모든 dp·sp 값이 페블 기준이라, 픽셀 수는 거의 같은데 밀도만 다르게 선언한 기기(메이북 E6: 320dpi → 536dp)에서도
     * 같은 물리 크기로 그려진다. 페블(1072px, 300dpi)은 그대로 300 이 나온다.
     */
    override fun attachBaseContext(newBase: Context) {
        createdEnglish = HomePrefs.isEnglish(newBase)
        createdTextScale = HomePrefs.textScalePercent(newBase)
        createdRowScale = HomePrefs.rowScalePercent(newBase)
        createdPalette = HomePrefs.palette(newBase)
        Palette.current = createdPalette
        textScale = if (fixedTextSize) 1f else createdTextScale / 100f
        rowScale = if (fixedTextSize) 1f else createdRowScale / 100f
        Ui.textScale = textScale
        Ui.rowScale = rowScale
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(if (createdEnglish) Locale.ENGLISH else Locale.KOREAN)
        if (fixedTextSize) config.fontScale = 1f
        val dpi = Ui.designDensityDpi(newBase)
        // 화면 크기(dp)도 같은 비율로 바꾼다. 밀도만 바꾸면 안드로이드 10 은 dp 크기에서 픽셀을 거꾸로 셈해 화면을 실제보다 넓게 안다
        // (1264px 300dpi → 354dpi 면 1491px). 아래 메뉴가 그 폭으로 높이를 재서 글이 실제로는 한 줄 더 늘어 `닫기` 가 잘렸다(BOOX Leaf).
        val from = config.densityDpi
        if (from > 0 && from != dpi) {
            fun scale(dp: Int) = if (dp <= 0) dp else (dp * from / dpi.toFloat()).roundToInt() // 0 은 모름
            config.screenWidthDp = scale(config.screenWidthDp)
            config.screenHeightDp = scale(config.screenHeightDp)
            config.smallestScreenWidthDp = scale(config.smallestScreenWidthDp)
        }
        config.densityDpi = dpi
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = HomePrefs(this)
        // 하위 화면은 매니페스트에서 연 때의 회전 그대로(locked) 열린다. 기기 정보의 회전 고정 시험을 켜지 않았으면 바로 풀어
        // 지금처럼 시스템이 방향을 정한다(Bigme B7 Pro: 거꾸로 들면 새 화면마다 잠깐 뒤집혀 보이는 것을 가르는 시험).
        if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LOCKED && !prefs.rotationLock)
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    override fun onPostResume() {
        super.onPostResume()
        // 떠나 있는 사이(그리고 돌아오며 화면마다 onResume 에서 한 저장까지) 기록이 바뀌었으면 떠 있던 메뉴를 닫는다
        Sheet.dismissStale(this)
    }

    override fun onResume() {
        super.onResume()
        // 기본 크기로 그리는 화면(기기 정보)에서 돌아왔으면 이 화면의 배율로 되돌린다(새로 그리는 줄·메뉴가 이 값을 쓴다).
        Ui.textScale = textScale
        Ui.rowScale = rowScale
        Palette.current = createdPalette
        // 언어는 기기 언어를 따르면 떠나 있는 사이 기기 언어가 바뀌었을 수도 있어 지금 값으로 다시 가른다
        if (HomePrefs.isEnglish(this) != createdEnglish || prefs.textScalePercent != createdTextScale ||
            prefs.rowScalePercent != createdRowScale || prefs.palette != createdPalette) {
            recreating = true
            recreate()
        }
    }

    /** 설정(언어·글자·간격·색)이 바뀌어 [onResume] 에서 이 화면을 다시 만드는 중. 이어지는 일(다시 세기·그리기)은 새 화면이 한다. */
    protected var recreating = false
        private set

    override fun setContentView(view: View) {
        Crema.reserveHiddenBottom(view)
        super.setContentView(view)
    }

    /** [toLeft] 가 true 면 왼쪽으로 민 것(다음 페이지). 처리했으면 true. */
    protected open fun onSwipe(toLeft: Boolean): Boolean = false

    /**
     * 페이지 버튼([PageKeys])을 눌렀을 때. [next] 면 다음 쪽. 넘길 목록이 있으면 true(키를 먹는다).
     * 기본은 밀기와 같은 목록. 넘기는 목록이 둘인 화면(홈)은 설정에서 고른 하나로.
     */
    protected open fun onPageKey(next: Boolean): Boolean = onSwipe(next)

    private val pageKeys = PageKeyHandler(this) { onPageKey(it) }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        pageKeys.onKey(event) || super.dispatchKeyEvent(event)

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = ev.x; downY = ev.y }
            MotionEvent.ACTION_UP -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (abs(dx) > dp(70) && abs(dx) > 2 * abs(dy) && onSwipe(dx < 0)) {
                    val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                    super.dispatchTouchEvent(cancel)
                    cancel.recycle()
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun startActivity(intent: Intent?, options: Bundle?) {
        super.startActivity(intent, options)
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }

    /**
     * 앱 길게 누르기 메뉴(④). 무엇이 바뀌면 [changed] 를 부른다. [rename] 이면(홈 자주 쓰는 앱) 맨 위에 `이름 바꾸기`.
     * 아이콘 팩이 깔려 있으면 `아이콘 바꾸기`(그 앱에만 팩의 다른 그림, [IconPickActivity]. 돌아오면 화면이 다시 그린다).
     */
    protected fun showAppMenu(app: AppEntry, rename: Boolean = false, changed: () -> Unit) {
        // 읽고 있는 앱은 책에 딸린 설정이라 책 메뉴·책 목록에서만 바꾼다.
        val fav = app.key in prefs.favorites
        val sheet = Sheet(this).header(app.label, app = app)
        if (rename && fav) sheet.item(getString(R.string.rename)) { renameFavorite(app, changed) }
        if (IconPacks.anyInstalled(this)) sheet.item(getString(R.string.change_icon)) { startActivity(IconPickActivity.intent(this, app)) }
        sheet.item(getString(if (fav) R.string.fav_remove else R.string.fav_add)) { prefs.toggleFavorite(app.key); changed() }
            .item(getString(R.string.hide)) { prefs.hide(app.key); changed() }
            .item(getString(R.string.app_info)) { AppStore.openAppInfo(this, app) }
            .show()
    }

    /** 자주 쓰는 앱 이름 바꾸기. 원래 이름과 같게 쓰거나 `원래 이름으로` 를 누르면 붙인 이름을 지운다. */
    protected fun renameFavorite(app: AppEntry, changed: () -> Unit) {
        val original = AppStore.load(this).firstOrNull { it.key == app.key }?.label ?: app.label
        val custom = prefs.favoriteNames[app.key]
        val sheet = Sheet(this).header(getString(R.string.rename), app = app)
            .input(custom ?: original) { prefs.setFavoriteName(app.key, it.takeIf { name -> name != original }); changed() }
        if (custom != null) sheet.item(getString(R.string.rename_reset, original)) { prefs.setFavoriteName(app.key, null); changed() }
        sheet.show()
    }
}
