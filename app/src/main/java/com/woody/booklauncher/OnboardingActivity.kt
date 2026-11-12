package com.woody.booklauncher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.woody.booklauncher.readers.BookFiles
import com.woody.booklauncher.readers.ReaderApps

/**
 * ⑭ 시작하기(온보딩). 처음 한 번, 기본으로 꺼져 있는 두 기능을 한 쪽에 하나씩 설명하고 켜게 한다.
 * 1쪽 읽는 책 자동 추가, 2쪽 오늘 읽은 시간. 둘 다 이 앱의 기본 기능이라 켜져야 넘어간다(`다음 ›`, 마지막 쪽은 `시작하기 ›`.
 * 그 전에는 흐리게, 누르지 않는다). 책 파일 읽기가 있어야 하는 이북 앱([ReaderApps.fileApps], KOReader·문리더·네오리더·이지뷰어)이 깔려 있으면
 * 3쪽 책 파일 읽기를 더한다(깔린 앱 이름을 늘어놓고 그 책 정보를 가져오는 데 필요하다고만. 그 앱을 안 쓸 수도 있어 켜지 않아도 `시작하기 ›`). 켤 수 없는 기기(시스템 화면이 막힌 기기 등)에서도 나갈 수 있게 하단 바 바로 위에 `나중에 하기`. 항목마다 설명 · 켜면 홈에 생기는 모습(작게) · `켜는 방법` 단계(켠 뒤에도
 * 그대로 둔다. 단계 옆 버튼이 그 시스템 화면을 연다. 돌아오면 이름 옆에 `켜짐 ✓`). 닫으면 다시 나오지 않는다.
 * BOOX 가 이 앱을 자동 얼리기 대상으로 두었으면 자동 추가 단계 맨 앞에 얼리기에서 빼는 단계를 더한다([booxFreeze]).
 * 스크롤하지 않으므로 쓸 수 있는 높이(기기·시스템 줄마다 다르다)에 넘치면 쪽 안의 글자를 줄여 그린다([fitPage]).
 */
class OnboardingActivity : EinkActivity() {
    /** 쪽 내용이 들어가는 칸. 남는 높이를 모두 차지한다([available]). */
    private lateinit var content: FrameLayout
    private lateinit var prev: TextView
    private lateinit var pageText: TextView
    private lateinit var next: TextView
    /** 0 = 자동 추가, 1 = 오늘 읽은 시간, 2 = 책 파일 읽기([fileApps] 가 있을 때만) */
    private var page = 0
    /** 깔려 있는, 책 파일 읽기가 있어야 하는 이북 앱 이름(없으면 2쪽까지) */
    private val fileApps by lazy { fileAppNames(this) }
    private val pages get() = if (fileApps.isEmpty()) 2 else 3
    /**
     * BOOX 얼리기 단계를 보이는가. 이 앱이 얼리기 대상이면 켜고, 사용자가 빼고 돌아와도·화면을 돌려도 끄지 않는다(다른 단계처럼 그대로 둔다).
     * 맨 앞에 두는 것은 얼리기 대상이면 접근성을 켜러 간 사이(30초 뒤) BOOX 가 이 화면을 지우고 앱을 꺼 버리기 때문이다.
     */
    private var booxFreeze = false
    /** 지난번에 그린 상태(쪽 · 자동 추가 켜짐 · 읽은 시간 켜짐 · 얼리기 단계). 같으면 다시 그리지 않는다(전자잉크 갱신을 줄인다). */
    private var shownState: List<Any>? = null
    /** [content] 의 높이. 알기 전(처음 레이아웃 전)에는 0 이라 그리지 않는다. */
    private var available = 0
    private val cover by lazy { sampleCover(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = minOf(savedInstanceState?.getInt(KEY_PAGE) ?: 0, pages - 1)
        booxFreeze = savedInstanceState?.getBoolean(KEY_BOOX_FREEZE) ?: false
        val root = vbox()
        root.addView(text(getString(R.string.onboarding_title), 34f, Ui.heavy).apply {
            setPadding(dp(Ui.MARGIN), dp(24), dp(Ui.MARGIN), 0)
        })
        root.addView(text(getString(if (pages == 3) R.string.onboarding_intro3 else R.string.onboarding_intro), 17f, color = Ui.GRAY, lines = 2).apply {
            setLineSpacing(0f, 1.2f)
            setPadding(dp(Ui.MARGIN), dp(8), dp(Ui.MARGIN), dp(14))
        })
        content = FrameLayout(this)
        root.addView(softCard(0).apply { addView(content, lp(MATCH, MATCH)) }, cardLp(weight = 1f, topDp = 8))
        // 높이가 정해지면(보통 처음 한 번) 이번 그리기를 건너뛰고 그 높이에 맞춰 채운 뒤 그린다(홈 책 자리와 같다. 빈 화면을 한 번 그리지 않는다).
        content.viewTreeObserver.addOnPreDrawListener {
            if (content.height == available) true else { available = content.height; shownState = null; render(); false }
        }

        // 하단 바 바로 위: `나중에 하기`(두 쪽 모두. 켜지 않고 닫는다. 설정 › 독서에서 언제든 켤 수 있다)
        root.addView(text(getString(R.string.onboarding_later), 17f, color = Ui.GRAY).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Ui.MARGIN), 0, dp(24), 0)
            setOnClickListener { finish() }
        }, lp(WRAP, dp(LATER_DP)))

        // 하단: 왼쪽 `‹ 이전`(2쪽부터), 가운데 쪽 번호, 오른쪽 `다음 ›` / `시작하기 ›`(마지막 쪽)
        root.addView(hline(1, inset = false))
        val footer = FrameLayout(this)
        prev = text(getString(R.string.onboarding_prev), 22f).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Ui.MARGIN), 0, dp(24), 0)
            setOnClickListener { showPage(page - 1) }
        }
        footer.addView(prev, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.START))
        pageText = text("", 16f, color = Ui.LIGHT_GRAY).apply { gravity = Gravity.CENTER }
        footer.addView(pageText, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.CENTER))
        next = text("", 22f, Ui.bold).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), 0, dp(Ui.MARGIN), 0)
        }
        footer.addView(next, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.END))
        root.addView(footer, lp(MATCH, dp(Ui.FOOTER_DP)))
        setContentView(root)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_PAGE, page)
        outState.putBoolean(KEY_BOOX_FREEZE, booxFreeze)
    }

    /** 시스템 설정에서 돌아올 때마다 켜졌는지 다시 본다. */
    override fun onResume() {
        super.onResume()
        render()
    }

    /** 밀기: 다음 쪽은 지금 쪽의 기능이 켜져 있을 때만([done]) */
    override fun onSwipe(toLeft: Boolean): Boolean {
        val to = if (toLeft) page + 1 else page - 1
        if (to !in 0 until pages || (to > page && !done(page))) return false
        showPage(to)
        return true
    }

    /** [p] 쪽을 넘어갈 수 있는가: 앞 두 쪽은 그 기능이 켜졌을 때, 책 파일 읽기 쪽은 늘 */
    private fun done(p: Int): Boolean = when (p) {
        0 -> ReaderWatchService.isEnabled(this)
        1 -> ReadingLog.hasAccess(this)
        else -> true
    }

    private fun showPage(to: Int) {
        page = to
        render()
    }

    private fun render() {
        if (available <= 0) return
        val books = ReaderWatchService.isEnabled(this)
        val time = ReadingLog.hasAccess(this)
        val files = BookFiles.canRead(this)
        if (page == 0 && !booxFreeze) booxFreeze = Device.booxAutoFreeze(this) == true
        val state = listOf(page, books, time, files, booxFreeze, available)
        if (state == shownState) return
        shownState = state
        content.removeAllViews()
        content.addView(fitPage(books, time, files), FrameLayout.LayoutParams(MATCH, WRAP))

        pageText.text = "${page + 1} / $pages"
        prev.visibility = if (page > 0) View.VISIBLE else View.INVISIBLE
        // 그 쪽의 기능이 켜져야 넘어간다(다음 쪽 / 닫기). 책 파일 읽기 쪽은 켜지 않아도 닫는다.
        val done = done(page)
        val last = page == pages - 1
        next.text = getString(if (last) R.string.onboarding_start else R.string.onboarding_next)
        next.setTextColor(if (done) Ui.BLACK else Ui.DIVIDER)
        next.setOnClickListener(if (!done) null else if (last) View.OnClickListener { finish() } else View.OnClickListener { showPage(page + 1) })
        next.isClickable = done
    }

    /**
     * 지금 쪽을 [available] 높이에 들어가게 만든다. 붙이기 전에 재 보고 넘치면 쪽 안의 글자를 [FIT_STEP] 씩
     * ([MAX_FIT_STEPS] 번, 70% 까지) 줄여 다시 만든다. 내용은 빼지 않는다.
     */
    private fun fitPage(books: Boolean, time: Boolean, files: Boolean): View {
        var step = 0
        while (true) {
            val scale = 1f - step * FIT_STEP
            val view = buildPage(books, time, files, scale)
            view.measure(
                View.MeasureSpec.makeMeasureSpec(content.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            if (view.measuredHeight <= available || step == MAX_FIT_STEPS) return view
            step++
        }
    }

    /** 한 쪽. 글자 크기는 모두 [scale] 배 */
    private fun buildPage(books: Boolean, time: Boolean, files: Boolean, scale: Float): View = vbox().apply {
        if (page == 2) {
            addView(feature(
                getString(R.string.book_files), files, getString(R.string.onboarding_files_desc, fileApps.joinToString("·")),
                getString(R.string.onboarding_files_note), bookPreview(scale, fileApps.first()), scale,
            ))
            val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            addView(steps(listOf(
                getString(if (modern) R.string.onboarding_files_s1 else R.string.onboarding_files_old_s1) to
                    Step(R.string.onboarding_open) { BookFiles.request(this@OnboardingActivity) },
                getString(if (modern) R.string.onboarding_files_s2 else R.string.onboarding_files_old_s2) to null,
            ), scale))
            addView(text(getString(R.string.onboarding_settings_hint), 14f * scale, color = Ui.LIGHT_GRAY, lines = 2).apply {
                setPadding(dp(Ui.MARGIN), dp(18), dp(Ui.MARGIN), 0)
            })
        } else if (page == 0) {
            addView(feature(
                getString(R.string.auto_books), books,
                getString(R.string.onboarding_books_desc), getString(R.string.onboarding_books_note), bookPreview(scale), scale,
            ))
            addView(steps(booksSteps(), scale))
        } else {
            addView(feature(
                getString(R.string.reading_time), time,
                getString(R.string.onboarding_time_desc), getString(R.string.onboarding_time_note), footerPreview(scale), scale,
            ))
            addView(steps(listOf(
                getString(R.string.onboarding_time_s1) to Step(R.string.onboarding_open) { ReadingLog.openAccessSettings(this@OnboardingActivity) },
                getString(R.string.onboarding_time_s2) to null,
            ), scale))
            addView(text(getString(R.string.onboarding_settings_hint), 14f * scale, color = Ui.LIGHT_GRAY, lines = 2).apply {
                setPadding(dp(Ui.MARGIN), dp(18), dp(Ui.MARGIN), 0)
            })
        }
    }

    /**
     * 자동 추가 켜는 방법. 안드로이드 13+ 은 파일로 깐 앱을 '제한된 설정'으로 막을 수 있어(막혔는지는 알 수 없다) 푸는 단계까지.
     * `⋮` 의 `제한된 설정 허용` 은 접근성에서 막힌 창을 한 번 본 뒤에야 생긴다. [booxFreeze] 면 맨 앞에 BOOX 얼리기에서 빼기,
     * BOOX 면(얼리기와 상관없이) 그다음에 백그라운드 `무제한`(그대로 두면 5분 뒤 강제 종료돼 접근성이 꺼진다, [Device.openBooxOptimization]).
     */
    private fun booksSteps(): List<Pair<String, Step?>> {
        val freeze = if (booxFreeze) listOf(
            getString(R.string.onboarding_books_boox) to Step(R.string.onboarding_open_freeze) { Device.openBooxFreezeSettings(this) },
        ) else emptyList()
        val background = if (Device.hasBooxLauncher(this)) listOf(
            getString(R.string.onboarding_books_boox_background) to
                Step(R.string.onboarding_open_eink) { Device.openBooxOptimization(this, componentName) },
        ) else emptyList()
        val accessibility = Step(R.string.onboarding_open_accessibility) { ReaderWatchService.openSettings(this) }
        return freeze + background + if (ReaderWatchService.mayBeRestricted) listOf(
            getString(R.string.onboarding_books_r1) to accessibility,
            getString(R.string.onboarding_books_r2) to null,
            getString(R.string.onboarding_books_r3) to Step(R.string.onboarding_open_app_info) { ReaderWatchService.openAppInfo(this) },
            getString(R.string.onboarding_books_r4) to accessibility,
        ) else listOf(
            getString(R.string.onboarding_books_s1) to accessibility,
            getString(R.string.onboarding_books_s2) to null,
        )
    }

    /** 단계 옆 버튼: 글자와 누르면 할 일 */
    private class Step(val label: Int, val action: () -> Unit)

    /** 기능 머리: 이름 ··· `켜짐 ✓`(켜졌을 때), 아래 왼쪽에 설명(검정)과 덧붙임(회색), 오른쪽에 [preview]. 글자는 [scale] 배. */
    private fun feature(name: String, on: Boolean, desc: String, note: String, preview: View, scale: Float): View = vbox().apply {
        setPadding(dp(Ui.MARGIN), dp(18), dp(Ui.MARGIN), dp(12))
        addView(hbox().apply {
            addView(text(name, 27f * scale, Ui.bold), lp(0, WRAP, 1f))
            if (on) addView(text(getString(R.string.onboarding_on), 22f * scale, color = Ui.GRAY))
        })
        addView(hbox().apply {
            gravity = Gravity.TOP
            addView(vbox().apply {
                addView(text(desc, 17f * scale, lines = 5).apply { setLineSpacing(0f, 1.2f) })
                addView(text(note, 15f * scale, color = Ui.GRAY, lines = 3).apply { setLineSpacing(0f, 1.15f) },
                    lp(MATCH, WRAP).apply { topMargin = dp(6) })
            }, lp(0, WRAP, 1f))
            addView(preview, lp(dp(PREVIEW_DP), WRAP).apply { marginStart = dp(22); topMargin = dp(4) })
        }, lp(MATCH, WRAP).apply { topMargin = dp(10) })
    }

    /** `켜는 방법`: 옅은 선 아래 회색 소제목, 줄마다 ①② · 설명 · 오른쪽 버튼(그 시스템 화면을 연다) */
    private fun steps(items: List<Pair<String, Step?>>, scale: Float): View = vbox().apply {
        addView(hline(1, Ui.DIVIDER))
        addView(text(getString(R.string.onboarding_steps), 14f * scale, Ui.bold, Ui.GRAY).apply {
            setPadding(dp(Ui.MARGIN), dp(10), dp(Ui.MARGIN), dp(2))
        })
        items.forEachIndexed { i, (desc, step) ->
            addView(hbox().apply {
                gravity = Gravity.TOP
                setPadding(dp(Ui.MARGIN), dp(6), dp(Ui.MARGIN), dp(4))
                addView(text(CIRCLED[i], 20f * scale), lp(dp(32), WRAP))
                addView(text(desc, 17f * scale, lines = 4).apply { setLineSpacing(0f, 1.1f) }, lp(0, WRAP, 1f))
                // 누르는 자리는 글자보다 넓게(왼쪽·아래 여백, 최소 44dp). 위 여백은 두지 않아 글자 윗줄이 설명 첫 줄과 맞는다.
                if (step != null) addView(text(getString(step.label), 18f * scale, Ui.bold).apply {
                    setPadding(dp(16), 0, 0, dp(8))
                    minHeight = dp(44)
                    setOnClickListener { step.action() }
                })
            })
        }
    }

    /** 자동 추가를 켜면 홈에 생기는 것: 맨 앞 책(작게, 앱 이름 [app]). 예시 책 표지는 퍼블릭 도메인(`tools/sample_cover.py`). */
    private fun bookPreview(scale: Float, app: String = getString(R.string.sample_app)): View = hbox().apply {
        gravity = Gravity.TOP
        val w = dp(SAMPLE_COVER_DP)
        addView(coverImage(cover), lp(w + dp(2), w * 3 / 2 + dp(2)))
        addView(vbox().apply {
            addView(text(getString(R.string.sample_title), 16f * scale, Ui.heavy, lines = 2))
            addView(text("${getString(R.string.sample_author)} · ${getString(R.string.progress_pct, 38)}", 12f * scale, color = Ui.GRAY),
                lp(WRAP, WRAP).apply { topMargin = dp(6) })
            addView(text("$app  ›", 12f * scale, Ui.bold), lp(WRAP, WRAP).apply { topMargin = dp(8) })
        }, lp(0, WRAP, 1f).apply { marginStart = dp(12) })
    }

    /** 오늘 읽은 시간을 켜면 홈 하단 줄 가운데에 생기는 것 */
    private fun footerPreview(scale: Float): View = vbox().apply {
        addView(hline(1, inset = false), lp(MATCH, dp(1)).apply { topMargin = dp(12) })
        addView(hbox().apply {
            // 홈 하단 왼쪽 모든 앱 아이콘(사각형 넷). 홈 글자 24sp 를 13sp 로 줄인 만큼 홈 아이콘 28dp 도 줄인다
            val icon = dp(28 * 13 / 24f * scale)
            addView(PictureView(context).apply { drawable = context.getDrawable(R.drawable.ic_all_apps) }, lp(icon, icon))
            addView(View(context), lp(0, 1, 1f))
            // 바형 화면은 홈처럼 `오늘` 없이
            val time = duration(42 * 60_000L)
            addView(text((if (Ui.tallScreen) time else getString(R.string.reading_today, time)) + "  ·  " +
                getString(R.string.reading_streak, 5), 12f * scale, color = Ui.GRAY))
        }, lp(MATCH, dp(44)))
    }

    companion object {
        /** `나중에 하기` 줄의 높이(누르는 자리 최소 44dp) */
        private const val LATER_DP = 44
        /** 오른쪽 미리 보기 칸 폭 */
        private const val PREVIEW_DP = 176
        private const val SAMPLE_COVER_DP = 52
        private const val KEY_PAGE = "page"
        private const val KEY_BOOX_FREEZE = "boox_freeze"
        /** 단계 번호: 가장 많을 때 BOOX 얼리기·백그라운드 + 제한된 설정 넷 */
        private val CIRCLED = listOf("①", "②", "③", "④", "⑤", "⑥")
        /** [fitPage]: 넘칠 때 글자를 한 번에 줄이는 비율과 가장 작게 줄이는 비율 */
        private const val FIT_STEP = 0.05f
        private const val MAX_FIT_STEPS = 6

        /**
         * 홈에서 부른다. 아직 띄운 적이 없고(잠금이 풀린 뒤) 두 기능 중 하나라도 시스템에서 꺼져 있으면(책 파일 읽기 쪽이 있으면 그것까지) 한 번 띄운다.
         * 모두 이미 켜져 있으면 띄우지 않고 띄운 것으로 친다(나중에 꺼도 다시 나오지 않는다). 띄우는 순간 적어 두 번 뜨지 않게 하고,
         * 시스템 설정에 갔다가 홈 버튼으로 나가도 다시 띄우지 않는다(설정 › 독서에 같은 것이 있다). 띄웠으면 true.
         */
        fun showOnce(activity: Activity, prefs: HomePrefs): Boolean {
            if (prefs.onboarded || !Storage.isUnlocked(activity)) return false
            prefs.onboarded = true
            if (ReaderWatchService.isEnabled(activity) && ReadingLog.hasAccess(activity) &&
                (BookFiles.canRead(activity) || fileAppNames(activity).isEmpty())) return false
            activity.startActivity(Intent(activity, OnboardingActivity::class.java))
            return true
        }

        /**
         * 깔려 있고 켜져 있는, 책 파일 읽기가 있어야 하는 이북 앱([ReaderApps.fileApps])의 이름. 켜야 책이 들어오는 앱(KOReader)이 앞에
         * (미리 보기의 앱). 판만 다른 앱(KOReader F-Droid 판 등)은 이름이 같으면 하나로.
         */
        private fun fileAppNames(context: Context): List<String> =
            ReaderApps.fileApps.sortedBy { it.fileOrder }.mapNotNull { AppStore.readerLabel(context, it.pkg) }.distinct()

        /** 예시 책 표지. 직접 그리는 그림이라 크레마 화면 대비만큼 미리 밝힌다(홈 표지와 같다). */
        private fun sampleCover(context: Context): Bitmap =
            BitmapFactory.decodeResource(context.resources, R.drawable.sample_cover, BitmapFactory.Options().apply {
                inScaled = false; inMutable = true
            }).also { Crema.undoContrast(it, Crema.contrastTone(context)) }
    }
}
