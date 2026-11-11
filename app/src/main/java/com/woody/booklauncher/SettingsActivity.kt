package com.woody.booklauncher

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.Formatter
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import com.woody.booklauncher.readers.ArchiveLib
import com.woody.booklauncher.readers.BookFiles
import java.text.Collator
import java.time.DayOfWeek
import java.time.format.TextStyle
import kotlin.math.roundToInt

/** ⑧ 설정. 회색 소제목으로 묶고 값은 오른쪽에 같은 굵기로 쓴다. 켜짐은 검정, 꺼짐은 진회색, `›` 는 다음 화면. */
class SettingsActivity : EinkActivity() {
    private lateinit var list: LinearLayout
    /** 줄을 넣는 곳: 보통 [list], 가로 화면의 설정 첫 화면은 왼쪽·오른쪽 칸 중 하나 */
    private lateinit var col: LinearLayout
    /** 업데이트 줄 자리(상태가 바뀌면 이 줄만 다시 그린다) */
    private var updateSlot: LinearLayout? = null
    /** 최신 판 줄을 눌러 다시 확인하는 중(끝나서 여전히 최신이면 알린다) */
    private var announceLatest = false
    /** 바뀐 점·설치·받는 중 창 */
    private val updates = UpdateSheets(this)
    /** 자동 추가 안내·앱 안내의 쪽들(그 화면에서만) */
    private lateinit var guide: PagedBlocks
    /**
     * 어느 화면인가: 설정(null), 독서([PAGE_READING]), 자동 추가 안내([PAGE_GUIDE]), 그 앱 하나의 안내([PAGE_GUIDE_APP]),
     * 화면([PAGE_DISPLAY]), 글자 · 간격([PAGE_TEXT]), 페이지 버튼([PAGE_KEYS])
     */
    private val page by lazy { intent.getStringExtra(EXTRA_PAGE) }
    /** 앱 안내 화면의 앱 */
    private val guideApp by lazy { ReaderGuide.APPS.getOrNull(intent.getIntExtra(EXTRA_APP, -1)) }
    private val paged get() = page == PAGE_GUIDE || page == PAGE_GUIDE_APP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (page == PAGE_GUIDE_APP && guideApp == null) return finish()
        val root = vbox()
        val title = when (page) {
            PAGE_GUIDE -> getString(R.string.auto_books_guide)
            PAGE_GUIDE_APP -> guideApp!!.label(this)
            PAGE_READING -> getString(R.string.reading_settings)
            PAGE_DISPLAY -> getString(R.string.section_display)
            PAGE_TEXT -> getString(R.string.text_spacing)
            PAGE_KEYS -> getString(R.string.page_keys)
            PAGE_WIDGETS -> getString(R.string.section_widgets)
            else -> getString(R.string.settings)
        }
        val titleRow = titleRow(title) { finish() }
        root.addView(titleRow)
        root.addView(hline(2))
        if (paged) {
            // 한 화면에 다 들어가지 않아 쪽으로 나눈다. 쪽 표시 `‹ 1 / 2 ›` 는 제목 줄 오른쪽 끝(아래 줄이 없는 화면).
            // 제목 줄은 누르면 뒤로 가므로, 쪽 표시가 줄 높이·오른쪽 끝까지 받아 그 근처를 눌러도 뒤로 가지 않게 한다(한 쪽뿐이면 숨긴다).
            guide = PagedBlocks(this)
            val guidePage = text("", 21f).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(24), 0, dp(Ui.MARGIN), 0)
                pagesClick(guide)
            }
            titleRow.setPadding(titleRow.paddingLeft, 0, 0, 0)
            titleRow.addView(View(this), lp(0, 1, 1f))
            titleRow.addView(guidePage, lp(WRAP, MATCH))
            guide.onPageChanged = {
                guidePage.text = pageLabel(guide)
                guidePage.visibility = if (guide.pageCount > 1) View.VISIBLE else View.GONE
            }
            root.addView(guide, lp(MATCH, 0, 1f))
        } else {
            list = vbox()
            col = list
            root.addView(list, lp(MATCH, 0, 1f))
        }
        // 맨 아래 지금 판, 늘 보인다(업데이트 줄은 새 판이 있으면 새 판 번호를 보여 지금 판이 안 보인다)
        if (page == null) root.addView(text("BookLauncher ${appVersion()}", 15f, color = Ui.LIGHT_GRAY).apply {
            gravity = Gravity.CENTER
        }, lp(MATCH, dp(44)))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        when (page) {
            PAGE_GUIDE -> renderGuide()
            PAGE_GUIDE_APP -> renderGuideApp()
            PAGE_READING -> renderReading()
            PAGE_DISPLAY -> renderDisplay()
            PAGE_TEXT -> renderText()
            PAGE_KEYS -> renderPageKeys()
            PAGE_WIDGETS -> renderWidgets()
            else -> {
                render()
                Update.onChange = { renderUpdate(); updates.onChange(); showLatest() }
                updates.onChange()
                Update.checkIfStale(this)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (page == null) Update.onChange = null
    }

    override fun onDestroy() {
        updates.dismiss()
        super.onDestroy()
    }

    private fun render() {
        list.removeAllViews()
        AppStore.loadAndPrune(this, prefs)
        // 가로 화면이면 묶음째 두 칸으로: 왼쪽 독서·화면·언어와 정보, 오른쪽 앱 관리(묶음은 쪼개지 않는다)
        val right = splitColumns()
        val left = col

        // 맨 위 소제목 없이 독서(읽고 있는 책·자동 추가·오늘 읽은 시간·하루 목표·한 주 시작·독서 기록). 줄이 많아 한 화면에 모은다.
        row(getString(R.string.reading_settings), null, next = true) { open(PAGE_READING) }

        // 화면(글자 · 간격 · 색 · 앱 아이콘 · 시계)은 하위 화면에 모아 첫 화면을 짧게 둔다(줄 간격을 키워도 잘리지 않게).
        row(getString(R.string.section_display), null, next = true) { open(PAGE_DISPLAY) }
        // 언어는 첫 화면에 둔다(다른 언어 화면에서도 바로 찾게). 언어 이름은 바꾸지 않는다(어느 언어에서도 알아볼 수 있게).
        // 기기 언어 따르기는 지금 보이는 언어를 괄호로(`시스템(한국어)`). 바꾸면 이 화면부터 다시 만든다(onResume).
        val choices = listOf(AppLanguage.SYSTEM, AppLanguage.KOREAN, AppLanguage.ENGLISH)
        fun languageName(l: AppLanguage): String = when (l) {
            AppLanguage.SYSTEM ->
                getString(R.string.language_system, languageName(if (l.isEnglish(HomePrefs.systemLanguage())) AppLanguage.ENGLISH else AppLanguage.KOREAN))
            AppLanguage.KOREAN -> "한국어"
            AppLanguage.ENGLISH -> "English"
        }
        row(getString(R.string.language), languageName(prefs.language), next = true) {
            choose(getString(R.string.language), choices.map(::languageName), choices.indexOf(prefs.language)) {
                if (choices[it] != prefs.language) {
                    prefs.language = choices[it]
                    recreate()
                }
            }
        }

        col = right
        row(getString(R.string.section_widgets), null, next = true) { open(PAGE_WIDGETS) }

        section(getString(R.string.section_apps))
        row(getString(R.string.favorites), prefs.favoriteList.size.toString(), next = true) {
            startActivity(FavoritesActivity.intent(this))
        }
        row(getString(R.string.hidden_apps), prefs.hidden.size.toString(), next = true) {
            startActivity(AppListActivity.intent(this, AppListActivity.Mode.HIDDEN))
        }
        row(getString(R.string.set_default_home), null, next = true) { openHomeSettings() }
        row(getString(R.string.device_settings), null, next = true) {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }

        // 정보
        col = left
        section(getString(R.string.section_info))
        row(getString(R.string.device_info), Build.MODEL, next = true) { startActivity(DeviceInfoActivity.intent(this)) }
        updateSlot = vbox().also { col.addView(it) }
        renderUpdate()
    }

    /**
     * 업데이트 줄: 판 번호만. 최신이면 지금 판(회색), 새 판이 있으면 `1.5.1 ›`(받는 동안은 `›` 없이),
     * 모르면 `확인 중` · `인터넷 없음`(누르면 연결 확인 안내) · `서버 응답 없음`(회색, 누르면 잠시 뒤 다시 해 보라는 창). 새 판을 누르면 바뀐 점과 `설치` 창, 그 밖에는 누르면 다시 확인한다
     * (최신 판을 눌러 다시 확인해도 최신이면 `최신 버전이 설치되어 있습니다` 창).
     */
    private fun renderUpdate() {
        val slot = updateSlot ?: return
        slot.removeAllViews()
        val label = getString(R.string.update)
        when (val s = Update.state) {
            is Update.State.Available ->
                row(label, s.release.version, next = true, last = true, into = slot) { updates.offer(s.release) }
            is Update.State.Downloading -> row(label, s.release.version, last = true, into = slot) {}
            // 설치 창을 띄운 뒤: 창을 그냥 벗어났을 수 있어 다시 누르면 그 창을 다시 띄운다.
            is Update.State.Installing -> row(label, s.release.version, next = true, last = true, into = slot) {
                if (!Update.resumeInstall(this, s.release.version)) updates.offer(s.release)
            }
            Update.State.Latest -> row(label, appVersion(), on = false, last = true, into = slot) {
                announceLatest = true
                Update.check(this)
            }
            Update.State.Unknown, Update.State.Checking ->
                row(label, getString(R.string.update_checking), on = false, last = true, into = slot) {}
            // 누르면 아직 연결이 없으면 연결을 확인하라고 알리고, 연결됐으면 다시 확인한다.
            Update.State.Offline -> row(label, getString(R.string.update_offline_short), on = false, last = true, into = slot) {
                if (Update.online(this)) Update.check(this)
                else Sheet(this).header(getString(R.string.update_offline), getString(R.string.update_offline_desc)).show()
            }
            // 누르면 잠시 뒤 다시 해 보라고 알리고, 뒤에서 다시 확인해 둔다.
            Update.State.Failed -> row(label, getString(R.string.update_no_response), on = false, last = true, into = slot) {
                Sheet(this).header(getString(R.string.update_server_title), getString(R.string.update_server_desc)).show()
                Update.check(this)
            }
        }
    }

    /** 최신 판 줄을 눌러 다시 확인했는데 여전히 최신이면 알린다(새 판이 나왔으면 줄이 그 판으로 바뀐다) */
    private fun showLatest() {
        val s = Update.state
        if (!announceLatest || s == Update.State.Checking) return
        announceLatest = false
        if (s == Update.State.Latest) Sheet(this).header("BookLauncher ${appVersion()}", getString(R.string.update_is_latest)).show()
    }

    private fun open(page: String) = startActivity(Intent(this, SettingsActivity::class.java).putExtra(EXTRA_PAGE, page))

    /** 화면: 글자 · 간격, 색, 앱 아이콘, 아이콘 모양, 시계, 기기 상단 바 */
    private fun renderDisplay() {
        list.removeAllViews()
        // 글자 크기·줄 간격은 한 화면에서 슬라이더로 보며 고른다.
        row(getString(R.string.text_spacing), "${prefs.textScalePercent}% · ${prefs.rowScalePercent}%", next = true) { open(PAGE_TEXT) }
        // 홈 화면: 서가(책 중심) / 달력(달력 + 자주 쓰는 앱 격자). 바꾸면 홈이 돌아올 때 다시 만들어진다(HomeActivity.onResume).
        val styles = HomeStyle.entries
        val styleNames = styles.map { getString(it.labelRes) }
        row(getString(R.string.home_style), styleNames[styles.indexOf(prefs.homeStyle)], next = true) {
            choose(getString(R.string.home_style), styleNames, styles.indexOf(prefs.homeStyle)) {
                if (styles[it] != prefs.homeStyle) {
                    prefs.homeStyle = styles[it]
                    renderDisplay()
                }
            }
        }
        // 홈 모양: 목록 / 두 칸 / 책장. 바꾸면 홈이 돌아올 때 새 모양으로 다시 만들어진다(HomeActivity.onResume).
        val layouts = HomeLayout.entries
        val layoutNames = layouts.map { getString(it.labelRes) }
        row(getString(R.string.home_layout), layoutNames[layouts.indexOf(prefs.homeLayout)], next = true) {
            choose(getString(R.string.home_layout), layoutNames, layouts.indexOf(prefs.homeLayout)) {
                if (layouts[it] != prefs.homeLayout) {
                    prefs.homeLayout = layouts[it]
                    renderDisplay()
                }
            }
        }
        // 색: 흑백 / 컬러 테마. 줄마다 그 테마의 색 칸(색면 · 제목 색). 바꾸면 이 화면부터 다시 만든다(색은 화면을 만들 때 정해진다).
        val themes = Palette.entries
        row(getString(R.string.color), getString(prefs.palette.label), next = true) {
            choose(getString(R.string.color), themes.map { getString(it.label) }, themes.indexOf(prefs.palette), themes.map { it.swatch }) {
                if (themes[it] != prefs.palette) { prefs.palette = themes[it]; recreate() }
            }
        }
        toggle(getString(R.string.app_icons), prefs.showIcons, hint = getString(R.string.app_icons_hint), redraw = ::renderDisplay) { prefs.showIcons = it }
        iconStyleRow()
        val clocks = listOf(getString(R.string.clock24), getString(R.string.clock12))
        row(getString(R.string.clock), clocks[if (prefs.clock24h) 0 else 1], next = true) {
            choose(getString(R.string.clock), clocks, if (prefs.clock24h) 0 else 1) {
                prefs.clock24h = it == 0; renderDisplay()
            }
        }
        toggle(getString(R.string.status_bar), prefs.showStatusBar, hint = getString(R.string.status_bar_hint), redraw = ::renderDisplay) { prefs.showStatusBar = it }
        // 페이지 버튼: 앞 쪽 · 다음 쪽 키(`음량 위 · 음량 아래`, 한쪽만 정했으면 다른 쪽은 `없음`), 둘 다 없으면 `없음`
        val keys = listOf(prefs.pageKeyPrev, prefs.pageKeyNext)
        val anyKey = keys.any { it != 0 }
        val keysText = if (!anyKey) getString(R.string.none) else keys.joinToString(" · ", transform = ::keyText)
        row(getString(R.string.page_keys), keysText, on = anyKey, next = true) { open(PAGE_KEYS) }
    }

    /**
     * 페이지 버튼 화면: 앞 쪽·다음 쪽 버튼(누르면 [askKey]), 홈에서 넘길 곳, 모두 지우기, 아래에 설명.
     * 버튼을 하나도 정하지 않았으면 아래 두 줄은 흐리게(누를 수는 있다).
     */
    private fun renderPageKeys() {
        list.removeAllViews()
        col = list
        val prev = prefs.pageKeyPrev
        val next = prefs.pageKeyNext
        row(getString(R.string.page_key_prev), keyText(prev), on = prev != 0, next = true) { askKey(false) }
        row(getString(R.string.page_key_next), keyText(next), on = next != 0, next = true) { askKey(true) }
        val any = prev != 0 || next != 0
        val homeRows = listOf(getString(R.string.favorites), getString(R.string.also_reading))
        val home = if (prefs.pageKeysAlso) 1 else 0
        row(getString(R.string.page_keys_home), homeRows[home], on = any, next = true) {
            choose(getString(R.string.page_keys_home), homeRows, home) { prefs.pageKeysAlso = it == 1; renderPageKeys() }
        }
        row(getString(R.string.page_keys_clear), null, on = any) {
            prefs.pageKeyPrev = 0; prefs.pageKeyNext = 0; renderPageKeys()
        }
        col.addView(text(getString(R.string.page_keys_desc), 16f, color = Ui.GRAY, lines = GUIDE_LINES).apply {
            setPadding(dp(Ui.MARGIN), dp(16), dp(Ui.MARGIN), 0)
        })
    }

    /** 페이지 버튼 키 이름, 정하지 않았으면(0) `없음` */
    private fun keyText(code: Int) = if (code == 0) getString(R.string.none) else PageKeys.name(this, code)

    /**
     * 버튼 받기 창: 다음에 누른 키를 [next] 쪽(앞 쪽이면 false) 버튼으로 정하고 닫는다. 뒤로 키는 창을 닫고, 홈·전원 등은 받지 않는다.
     * 다른 쪽에 이미 정한 키면 그쪽은 지운다(한 키가 두 쪽을 넘기지 않게).
     */
    private fun askKey(next: Boolean) {
        val title = getString(if (next) R.string.page_key_next else R.string.page_key_prev)
        val dialog = Sheet(this).header(title, getString(if (next) R.string.page_key_ask_next else R.string.page_key_ask_prev))
            .centered().keepOnChange().show()
        dialog.setOnKeyListener { _, _, event ->
            val code = event.keyCode
            if (!PageKeys.assignable(code)) return@setOnKeyListener false
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && dialog.isShowing) {
                if (next) {
                    prefs.pageKeyNext = code
                    if (prefs.pageKeyPrev == code) prefs.pageKeyPrev = 0
                } else {
                    prefs.pageKeyPrev = code
                    if (prefs.pageKeyNext == code) prefs.pageKeyNext = 0
                }
                dialog.dismiss()
                renderPageKeys()
            }
            true
        }
    }

    /**
     * 아이콘 모양: 기본(각 앱의 아이콘) / 깔린 아이콘 팩([IconPacks]). 격자에는 늘 아이콘이 보여 `앱 아이콘` 이 꺼져도 보인다.
     * 깔린 팩이 없으면 팩을 까는 곳을 알린다(이 앱이 받지 않는다).
     */
    private fun iconStyleRow() {
        val packs = IconPacks.installed(this)
        val chosen = packs.indexOfFirst { it.pkg == prefs.iconPack }
        val title = getString(R.string.icon_style)
        row(title, packs.getOrNull(chosen)?.label ?: getString(R.string.icon_style_default), next = true) {
            if (packs.isEmpty()) {
                Sheet(this).header(title, getString(R.string.icon_pack_none)).show()
                return@row
            }
            choose(title, listOf(getString(R.string.icon_style_default)) + packs.map { it.label }, chosen + 1) {
                val pkg = packs.getOrNull(it - 1)?.pkg
                if (pkg != prefs.iconPack) {
                    prefs.iconPack = pkg
                    pkg?.let { p -> Bg.run({ IconPacks.warm(applicationContext, p) }) {} }
                    renderDisplay()
                }
            }
        }
    }

    /** 독서 화면: 읽고 있는 책, 읽는 책 자동 추가, 오늘 읽은 시간, 하루 목표, 한 주 시작, 독서 기록 */
    private fun renderReading() {
        list.removeAllViews()
        // 가로 화면이면 두 칸으로: 왼쪽 책·자동 추가, 오른쪽 읽은 시간·목표·독서 기록(일곱 줄이 낮은 화면을 넘지 않게)
        val right = splitColumns()
        // 읽고 있는 앱은 책을 정할 때 함께 고른다(책 없이 바꾸려면 앱을 길게 눌러 '읽고 있는 앱으로 설정').
        // 맨 앞 책 제목이 길면 줄임표로 자르고 `외 3권` 은 늘 보인다.
        val books = BookShelf.list(this)
        // 홈에서 숨긴 책은 제목을 앞에 보이지 않는다(홈 맨 앞 책과 같게, 모두 숨겼으면 맨 앞 책)
        val booksText = (books.firstOrNull { !it.homeHidden } ?: books.firstOrNull())?.title ?: getString(R.string.none)
        val booksTail = if (books.size > 1) getString(R.string.books_more, books.size - 1) else null
        row(getString(R.string.now_reading), booksText, on = books.isNotEmpty(), next = true, tail = booksTail) {
            startActivity(BooksActivity.intent(this))
        }
        // 읽는 책 자동 추가 = 안드로이드 접근성에서 이 앱이 켜져 있음(앱이 대신 켜고 끌 수 없다).
        // 켜져 있든 꺼져 있든 무엇을 읽는지 안내 창(켜고 끄기는 그 창의 `접근성 설정 열기`).
        val watching = ReaderWatchService.isEnabled(this)
        row(
            getString(R.string.auto_books), getString(if (watching) R.string.on else R.string.off),
            on = watching, next = true,
        ) { autoBooksSheet(watching) }
        // 무엇을 어떻게 읽는지(서재 화면에 보이는 것만, 앱마다 되는 화면) 안내
        row(getString(R.string.auto_books_guide), null, next = true) {
            startActivity(Intent(this, SettingsActivity::class.java).putExtra(EXTRA_PAGE, PAGE_GUIDE))
        }
        // 책 파일 읽기 = 안드로이드 '모든 파일 액세스'(KOReader 의 기록, 내 파일 리더(KOReader·네오리더·문리더)의 책 파일과 표지). 늘 보인다.
        val files = BookFiles.canRead(this)
        row(getString(R.string.book_files), getString(if (files) R.string.on else R.string.off), on = files, next = true) { bookFilesSheet() }
        // 만화 표지 압축 해제 = 받아 쓰는 libarchive(CBR·CB7 등, 필요한 사람만 받는다). 이 기기에서 쓸 수 없으면(x86) 두지 않는다.
        if (ArchiveLib.supported()) {
            val ready = ArchiveLib.installed(this)
            val value = when {
                ArchiveLib.downloading -> R.string.comic_archives_downloading
                ready -> R.string.comic_archives_ready
                else -> R.string.comic_archives_get
            }
            row(getString(R.string.comic_archives), getString(value), on = ready, next = !ArchiveLib.downloading, fitValue = true) { comicArchivesSheet() }
        }
        // 오늘 읽은 시간 = 사용 기록 액세스 허용. 켜고 끄기 모두 그 시스템 화면에서 한다.
        col = right
        val access = ReadingLog.hasAccess(this)
        row(
            getString(R.string.reading_time), getString(if (access) R.string.on else R.string.off),
            on = access, next = true, hint = getString(R.string.reading_time_hint),
        ) { ReadingLog.openAccessSettings(this) }
        if (access) {
            // 정해 둔 값 다음에 `직접 입력`. 직접 넣은 값이면 그 줄에 값을 함께 보이고 ✓. 시간은 독서 기록과 같은 꼴(`1시간 30분`)
            val goals = listOf(0, 15, 20, 30, 45, 60)
            val goalText = { m: Int -> if (m == 0) getString(R.string.none) else duration(m * 60_000L) }
            val goal = prefs.readingGoalMin
            row(getString(R.string.daily_goal), goalText(goal), next = true) {
                val custom = goal !in goals
                val options = goals.map(goalText) + getString(R.string.custom_goal).let {
                    if (custom) "$it · ${goalText(goal)}" else it
                }
                choose(getString(R.string.daily_goal), options, if (custom) goals.size else goals.indexOf(goal)) {
                    if (it < goals.size) { prefs.readingGoalMin = goals[it]; renderReading() } else askGoal()
                }
            }
            // 독서 기록 달력과 '이번 주'의 시작 요일
            val days = listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY)
            val dayNames = days.map { it.getDisplayName(TextStyle.FULL, resources.configuration.locales[0]) }
            val first = days.indexOf(prefs.firstDayOfWeek)
            row(getString(R.string.week_starts), dayNames[first], next = true) {
                choose(getString(R.string.week_starts), dayNames, first) { prefs.firstDayOfWeek = days[it]; renderReading() }
            }
        }
        // 카테고리: 책에 붙여 독서 기록·올해 읽은 책·결산을 거르고, 통계에서 뺄 카테고리를 정한다
        val categories = Categories.list(this).size
        row(getString(R.string.categories_title), if (categories == 0) null else getString(R.string.category_count, categories), next = true) {
            startActivity(CategoriesActivity.intent(this))
        }
        row(getString(R.string.reading_stats), null, next = true, last = true) { startActivity(ReadingActivity.intent(this)) }
    }

    /**
     * 글자 · 간격 화면: 글자 크기(70~150%, 기기 글꼴 크기에 곱한다)와 줄 간격(80~150%)을 10% 단위 슬라이더로 고르고, 아래 미리 보기(자주 쓰는 앱 목록 몇 줄)가
     * 손을 뗄 때 바로 바뀐다. 다른 화면은 돌아갈 때 새 값으로 다시 만든다(EinkActivity). 이 화면의 제목·슬라이더는 다시 들어올 때 바뀐다.
     */
    private fun renderText() {
        list.removeAllViews()
        // 가로 화면이면 왼쪽에 슬라이더, 오른쪽에 미리 보기(세로로 쌓으면 미리 보기가 화면 아래로 넘친다)
        val right = splitColumns()
        val preview = vbox()
        fun showPreview() {
            preview.removeAllViews()
            val apps = AppStore.load(this).let { all -> favoriteApps(all, prefs).ifEmpty { all } }.take(PREVIEW_ROWS)
            apps.forEachIndexed { i, app ->
                if (i > 0) preview.addView(hline(1, Ui.DIVIDER))
                preview.addView(appRow(app, app.label, Ui.ROW_ICON_DP, Ui.ROW_SP, showIcon = prefs.showIcons), lp(MATCH, rowDp(Ui.ROW_DP)))
            }
        }
        fun slider(label: Int, steps: List<Int>, value: Int, apply: (Int) -> Unit) {
            val valueText = text("$value%", 20f, Ui.medium)
            col.addView(hbox().apply {
                setPadding(dp(Ui.MARGIN), dp(18), dp(Ui.MARGIN), 0)
                addView(text(getString(label), ROW_LABEL_SP), lp(0, WRAP, 1f))
                addView(valueText)
            })
            col.addView(StepSlider(this, steps, value, onStep = { valueText.text = "$it%" }) { apply(it); showPreview() })
        }
        slider(R.string.text_size, HomePrefs.TEXT_SCALES, prefs.textScalePercent) {
            prefs.textScalePercent = it; Ui.textScale = it / 100f
        }
        // 기기 글꼴 크기를 바꿔 두었으면 그 위에 곱한다는 것을 알린다.
        val deviceFont = (resources.configuration.fontScale * 100).roundToInt()
        if (deviceFont != 100) col.addView(text(getString(R.string.text_size_device, deviceFont), 15f, color = Ui.GRAY).apply {
            setPadding(dp(Ui.MARGIN), 0, dp(Ui.MARGIN), 0)
        })
        slider(R.string.line_spacing, HomePrefs.ROW_SCALES, prefs.rowScalePercent) {
            prefs.rowScalePercent = it; Ui.rowScale = it / 100f
        }
        col = right
        section(getString(R.string.preview))
        col.addView(hline(1))
        col.addView(preview)
        showPreview()
    }

    /** 하루 목표 직접 입력(분). 범위 밖이면 가장 가까운 값으로 */
    private fun askGoal() {
        val initial = prefs.readingGoalMin.takeIf { it > 0 }?.toString().orEmpty()
        Sheet(this).header(getString(R.string.daily_goal), getString(R.string.custom_goal_hint, GOAL_MIN, GOAL_MAX))
            .input(initial, numeric = true) { v ->
                v.toLongOrNull()?.let { prefs.readingGoalMin = it.coerceIn(GOAL_MIN, GOAL_MAX).toInt(); renderReading() }
            }.show()
    }

    /**
     * 자동 추가 안내: 맨 위 회색 한 줄(앱을 누르면 자세한 안내가 나온다는 것), 그 아래 `지원하는 앱` 을 한 줄씩(안내가 같은 판은 한 줄에 `리디 · 리디(원스토어)`, 지금 언어의 이름 순.
     * 깔린 앱으로 나누지 않는다: 기기마다 순서가 달라지지 않게). 줄을 누르면 그 앱의 안내([renderGuideApp]). 넘치면 쪽으로 나눈다.
     */
    private fun renderGuide() {
        val collator = Collator.getInstance(resources.configuration.locales[0])
        val apps = ReaderGuide.APPS.mapIndexed { i, app -> i to app.label(this) }.sortedWith(compareBy(collator) { it.second })
        val blocks = mutableListOf(PagedBlocks.Block(listOf(guidePara(getString(R.string.guide_tap_hint), Ui.GRAY).apply {
            setPadding(paddingLeft, dp(12), paddingRight, dp(2))
        })))
        // 소제목은 첫 줄과 한 묶음
        apps.forEachIndexed { i, (index, label) ->
            val box = vbox()
            if (i == 0) { box.addView(sectionView(getString(R.string.guide_apps))); box.addView(hline(1, Ui.DIVIDER)) }
            row(label, null, next = true, last = i == apps.lastIndex, into = box) {
                startActivity(Intent(this, SettingsActivity::class.java).putExtra(EXTRA_PAGE, PAGE_GUIDE_APP).putExtra(EXTRA_APP, index))
            }
            blocks += PagedBlocks.Block(listOf(box))
        }
        guide.setBlocks(blocks)
    }

    /**
     * 앱 하나의 안내: 필요한 권한(지금 켜짐/꺼짐, 누르면 그 설정. 책 파일 읽기는 그 앱이 쓸 때만), 앱에서 할 것(점 목록, 없으면 한 줄),
     * 자동으로 들어오는 책 정보 다섯 가지(표지·제목·저자·진행률·반납일: 들어오는지만, 조건·이유는 아래 작은 회색), 알아 둘 것(있을 때만). 넘치면 쪽으로 나눈다.
     * 권한을 켜고 돌아오면 다시 그린다(onResume).
     */
    private fun renderGuideApp() {
        val info = guideApp?.info ?: return
        val blocks = mutableListOf<PagedBlocks.Block>()
        fun onOff(on: Boolean) = getString(if (on) R.string.on else R.string.off)

        val perms = vbox().apply { addView(sectionView(getString(R.string.guide_permissions))); addView(hline(1, Ui.DIVIDER)) }
        val watching = ReaderWatchService.isEnabled(this)
        row(getString(R.string.auto_books), onOff(watching), on = watching, next = true, into = perms) { autoBooksSheet(watching) }
        info.bookFiles?.let { hint ->
            val files = BookFiles.canRead(this)
            row(getString(R.string.book_files), onOff(files), on = files, next = true, hint = getString(hint), into = perms) { bookFilesSheet() }
        }
        val access = ReadingLog.hasAccess(this)
        row(getString(R.string.reading_time), onOff(access), on = access, next = true, hint = getString(R.string.guide_usage_hint), into = perms) {
            ReadingLog.openAccessSettings(this)
        }
        blocks += PagedBlocks.Block(listOf(perms))

        // 할 것: 점 하나에 한 묶음, 소제목은 첫 점과 함께. 없으면 묶음을 두지 않는다
        info.todo.forEachIndexed { i, res ->
            val box = vbox()
            if (i == 0) box.addView(sectionView(getString(R.string.guide_todo)))
            box.addView(bullet(getString(res)))
            blocks += PagedBlocks.Block(listOf(box))
        }

        info.fields.forEachIndexed { i, (label, field) ->
            val box = vbox()
            if (i == 0) { box.addView(sectionView(getString(R.string.guide_fields))); box.addView(hline(1, Ui.DIVIDER)) }
            box.addView(fieldRow(getString(label), field))
            box.addView(hline(1, Ui.DIVIDER))
            blocks += PagedBlocks.Block(listOf(box))
        }

        info.note?.let { note ->
            blocks += PagedBlocks.Block(listOf(vbox().apply {
                addView(sectionView(getString(R.string.guide_notes)))
                addView(guidePara(getString(note), Ui.GRAY))
            }))
        }
        guide.setBlocks(blocks)
    }

    /** 안내의 한 단락(17sp). 줄 수는 글자를 키운 영어 안내도 다 들어가게 넉넉히(쪽은 잰 높이로 나눈다) */
    private fun guidePara(value: String, color: Int) = text(value, 17f, color = color, lines = GUIDE_LINES).apply {
        setLineSpacing(0f, 1.2f)
        setPadding(dp(Ui.MARGIN), dp(4), dp(Ui.MARGIN), dp(6))
    }

    /** 앱에서 할 것 한 줄: 왼쪽 `·`, 오른쪽 여러 줄 글 */
    private fun bullet(value: String) = hbox().apply {
        gravity = Gravity.TOP
        setPadding(dp(Ui.MARGIN), dp(4), dp(Ui.MARGIN), dp(4))
        addView(text("·", 17f, Ui.bold), lp(dp(18), WRAP))
        addView(text(value, 17f, lines = GUIDE_LINES).apply { setLineSpacing(0f, 1.2f) }, lp(0, WRAP, 1f))
    }

    /** 책 정보 한 줄: 왼쪽 굵은 이름(폭 고정, 글자 크기를 따라 넓힌다), 오른쪽 `들어옵니다`(안 들어오면 흐리게)와 그 아래 작은 회색 조건·이유 */
    private fun fieldRow(label: String, field: ReaderGuide.Field) = hbox().apply {
        gravity = Gravity.TOP
        setPadding(dp(Ui.MARGIN), dp(9), dp(Ui.MARGIN), dp(9))
        addView(text(label, 18f, Ui.bold), lp(dp(FIELD_LABEL_DP * Ui.textScale), WRAP))
        addView(vbox().apply {
            addView(text(getString(field.main), 18f, color = if (field.added) Ui.BLACK else Ui.LIGHT_GRAY, lines = FIELD_LINES))
            field.note?.let { note ->
                addView(text(getString(note), 15f, color = Ui.GRAY, lines = FIELD_LINES).apply { setPadding(0, dp(3), 0, 0) })
            }
        }, lp(0, WRAP, 1f))
    }

    /** 책 파일 읽기 창: 무엇을 읽는지 + `모든 파일에 액세스 열기` */
    private fun bookFilesSheet() {
        Sheet(this).header(getString(R.string.book_files), getString(R.string.book_files_desc))
            .item(getString(R.string.open_file_access), bold = true) { BookFiles.request(this) }.show()
    }

    /** 만화 표지 압축 해제 창: 받지 않았으면 무엇인지 + `받기 (1.1 MB)`, 받았으면 `지우기`. 받는 중이면 띄우지 않는다 */
    private fun comicArchivesSheet() {
        if (ArchiveLib.downloading) return
        val title = getString(R.string.comic_archives)
        if (ArchiveLib.installed(this)) {
            Sheet(this).header(title, getString(R.string.comic_archives_ready_desc))
                .item(getString(R.string.comic_archives_remove)) { ArchiveLib.remove(this); renderReading() }.show()
        } else {
            val size = Formatter.formatShortFileSize(this, ArchiveLib.downloadSize())
            Sheet(this).header(title, getString(R.string.comic_archives_desc))
                .item(getString(R.string.comic_archives_get_size, size), bold = true) { getComicArchives() }.show()
        }
    }

    /** libarchive 를 받는다(줄은 `받는 중`). 받으면 이미 넣은 책들의 표지를 뒤에서 채우고, 못 받으면 알린다 */
    private fun getComicArchives() {
        if (!Update.online(this)) {
            Sheet(this).header(getString(R.string.update_offline), getString(R.string.update_offline_desc)).show()
            return
        }
        ArchiveLib.download(this) { r ->
            r.onSuccess { BookShelf.archiveLibChanged(applicationContext) }
            if (isDestroyed) return@download
            renderReading()
            r.onFailure { Sheet(this).header(getString(R.string.comic_archives), getString(R.string.comic_archives_failed)).show() }
        }
        renderReading()
    }

    override fun onSwipe(toLeft: Boolean): Boolean {
        if (!paged || guide.pageCount <= 1) return false
        if (toLeft) guide.next() else guide.prev()
        return true
    }

    /**
     * 자동 추가 창: 무엇을 읽는지 + `접근성 설정 열기`. 안드로이드 13+ 이면 `앱 정보 열기` 도 두고,
     * 꺼져 있으면([watching] 이 false) 허용이 안 보일 때 안내(BOOX Leaf2)와 13+ 의 제한된 설정 안내를 설명 아래 단락으로 붙인다.
     * 서비스가 한 번이라도 연결된 적이 있으면(이미 켜 본 것. 제한된 설정도 풀려 있다) 두 안내와 `앱 정보 열기` 는 두지 않는다.
     * BOOX 면 켜져 있어도 백그라운드 `무제한` 안내와 `E-Ink 센터 열기` 를 둔다(켠 뒤 5분 강제 종료로 꺼지므로).
     */
    private fun autoBooksSheet(watching: Boolean) {
        val turnedOnBefore = ReaderWatchService.lifeLog(this).first > 0
        val restricted = ReaderWatchService.mayBeRestricted && !turnedOnBefore
        val hints = if (watching || turnedOnBefore) emptyList() else listOfNotNull(R.string.allow_hint, R.string.restricted_hint.takeIf { restricted })
        val boox = Device.hasBooxLauncher(this)
        val desc = (listOfNotNull(R.string.auto_books_desc, R.string.boox_background_hint.takeIf { boox }) + hints)
            .joinToString("\n\n") { getString(it) }
        val sheet = Sheet(this).header(getString(R.string.auto_books), desc)
        if (boox) sheet.item(getString(R.string.boox_eink_center), bold = true) { Device.openBooxOptimization(this, componentName) }
        sheet.item(getString(R.string.open_accessibility), bold = true) { ReaderWatchService.openSettings(this) }
        if (restricted) sheet.item(getString(R.string.open_app_info)) { ReaderWatchService.openAppInfo(this) }
        sheet.show()
    }

    /**
     * 가로 화면이면 [list] 를 두 칸(사이에 옅은 세로 선)으로 나눠 [col] 을 왼쪽 칸으로 두고 오른쪽 칸을 돌려준다.
     * 세로 화면이면 [col] 도 오른쪽도 [list] 그대로(한 칸).
     */
    private fun splitColumns(): LinearLayout {
        col = list
        if (!wideLayout()) return list
        list.orientation = LinearLayout.HORIZONTAL
        val left = vbox()
        val right = vbox()
        list.addView(left, lp(0, WRAP, 1f))
        list.addView(vline(12))
        list.addView(right, lp(0, WRAP, 1f))
        col = left
        return right
    }

    private fun renderWidgets() {
        list.removeAllViews()
        col = list
        row(getString(R.string.weather_city_title), Weather.city(this) ?: getString(R.string.none), next = true) { askCity() }
        row(getString(R.string.stock_symbol_title), Stocks.symbol(this) ?: getString(R.string.none), next = true) { askSymbol() }
        row(
            getString(R.string.notification_access),
            getString(if (MediaNow.hasAccess(this)) R.string.notification_access_on else R.string.notification_access_off),
            on = MediaNow.hasAccess(this), next = true,
        ) { MediaNow.requestAccess(this) }
        row(getString(R.string.mini_apps), null, next = true) { startActivity(MiniAppsActivity.intent(this)) }
    }

    private fun askCity() {
        Sheet(this).header(getString(R.string.weather_city_title), getString(R.string.weather_city_hint))
            .input(Weather.city(this).orEmpty()) { city ->
                Weather.setCity(this, city)
                if (page == PAGE_WIDGETS) renderWidgets() else render()
            }
            .show()
    }

    private fun askSymbol() {
        Sheet(this).header(getString(R.string.stock_symbol_title), getString(R.string.stock_symbol_hint))
            .input(Stocks.symbol(this).orEmpty()) { symbol ->
                Stocks.setSymbol(this, symbol)
                if (page == PAGE_WIDGETS) renderWidgets() else render()
            }
            .show()
    }

    private fun section(title: String) = col.addView(sectionView(title))

    private fun sectionView(title: String) = text(title, 16f, color = Ui.LIGHT_GRAY).apply {
        setPadding(dp(Ui.MARGIN), dp(14), dp(Ui.MARGIN), dp(2))
    }

    /**
     * 설정 한 줄. [hint] 는 이름 뒤에 작은 회색 글자로 붙는 보조 설명. 값은 같은 굵기로, 꺼짐은 진회색.
     * 값은 이름 뒤 남는 폭만큼 오른쪽에 붙여 보이고 넘치면 줄임표로 자른다. [tail](`외 3권`)과 `›` 는 자르지 않는다.
     */
    private fun row(
        label: String, value: String?, on: Boolean = true, next: Boolean = false, last: Boolean = false,
        hint: String? = null, tail: String? = null, into: LinearLayout = col, fitValue: Boolean = false, action: () -> Unit,
    ) {
        val color = if (on) Ui.BLACK else Ui.GRAY
        val row = hbox().apply {
            setPadding(dp(Ui.MARGIN), 0, dp(Ui.MARGIN), 0)
            // 보통은 이름을 먼저 다 보이고 값을 줄인다(`읽고 있는 책` 의 긴 제목). [fitValue] 면 거꾸로 짧은 값을 다 보이고 긴 이름을 줄인다
            // (글자를 키워도 `받기 ›` 가 보이게)
            addView(text(label, ROW_LABEL_SP), if (fitValue) lp(0, WRAP, 1f) else lp(WRAP, WRAP))
            if (hint != null) addView(text(hint, 15f, color = Ui.LIGHT_GRAY), lp(WRAP, WRAP).apply { marginStart = dp(10) })
            addView(text(value.orEmpty(), 20f, Ui.medium, color).apply { gravity = Gravity.END },
                (if (fitValue) lp(WRAP, WRAP) else lp(0, WRAP, 1f)).apply { marginStart = dp(16) })
            // 값과의 사이는 전과 같은 글자 간격(`외 3권` 앞 한 칸, `›` 앞 두 칸)
            val end = buildString {
                tail?.let { append(if (value.isNullOrEmpty()) it else " $it") }
                if (next) append(if (isEmpty() && value.isNullOrEmpty()) "›" else "  ›")
            }
            if (end.isNotEmpty()) addView(text(end, 20f, Ui.medium, color))
            setOnClickListener { action() }
        }
        into.addView(row, lp(MATCH, rowDp(Ui.SETTING_ROW_DP)))
        if (!last) into.addView(hline(1, Ui.DIVIDER))
    }

    /** 켜짐/꺼짐 줄. 누르면 바꾸고 [redraw] 로 그 화면을 다시 그린다. */
    private fun toggle(label: String, value: Boolean, hint: String? = null, redraw: () -> Unit, set: (Boolean) -> Unit) =
        row(label, getString(if (value) R.string.on else R.string.off), on = value, hint = hint) { set(!value); redraw() }

    /** 안드로이드 홈 앱 선택 화면. 기기마다 있는 화면이 달라 차례로 시도한다. */
    private fun openHomeSettings() {
        val candidates = listOf(
            Intent(Settings.ACTION_HOME_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (intent in candidates) {
            if (runCatching { startActivity(intent) }.isSuccess) return
        }
    }

    companion object {
        private const val EXTRA_PAGE = "page"
        private const val PAGE_TEXT = "text"
        private const val PAGE_DISPLAY = "display"
        private const val PAGE_KEYS = "page_keys"
        private const val PAGE_WIDGETS = "widgets"
        /** 설정 줄 이름 글자(sp) */
        private const val ROW_LABEL_SP = 22f
        private const val PAGE_READING = "reading"
        private const val PAGE_GUIDE = "guide"
        private const val PAGE_GUIDE_APP = "guide_app"
        /** 자동 추가 안내의 앱([ReaderGuide.APPS] 의 몇 번째) */
        private const val EXTRA_APP = "app"
        /** 책 정보의 이름 칸 폭(dp, 글자 크기 100% 기준) */
        private const val FIELD_LABEL_DP = 96
        /** 안내 단락·할 것 한 줄, 책 정보의 글·조건이 넘을 수 없는 줄 수(글자 150% 영어도 들어가게) */
        private const val GUIDE_LINES = 10
        private const val FIELD_LINES = 5
        private const val GOAL_MIN = 1L
        private const val GOAL_MAX = 300L
        /** 글자 · 간격 미리 보기 줄 수 */
        private const val PREVIEW_ROWS = 4
    }
}

/**
 * 단계 슬라이더(글자 · 간격): 가로 선 위 단계마다 점, 고른 값까지 검정, 동그란 손잡이, 아래에 단계 값.
 * 누르거나 밀면 가장 가까운 단계로 옮기고([onStep]), 손을 떼면 [onCommit]. 단계가 바뀔 때만 다시 그린다(전자잉크).
 */
private class StepSlider(
    context: Context,
    private val steps: List<Int>,
    value: Int,
    private val onStep: (Int) -> Unit,
    private val onCommit: (Int) -> Unit,
) : View(context) {
    private var index = steps.indexOf(value).coerceAtLeast(0)
    private var committed = index
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = context.dpf(14); textAlign = Paint.Align.CENTER; typeface = Ui.regular
    }
    private val pad = context.dpf(Ui.MARGIN + 6)

    private fun x(i: Int) = pad + (width - 2 * pad) * i / (steps.size - 1)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), context.dp(HEIGHT_DP))

    override fun onDraw(canvas: Canvas) {
        val y = context.dpf(TRACK_Y_DP)
        paint.strokeWidth = context.dpf(2)
        paint.color = Ui.DIVIDER
        canvas.drawLine(x(0), y, x(steps.size - 1), y, paint)
        paint.color = Ui.BLACK
        paint.strokeWidth = context.dpf(3)
        canvas.drawLine(x(0), y, x(index), y, paint)
        steps.forEachIndexed { i, v ->
            paint.color = if (i <= index) Ui.BLACK else Ui.LIGHT_GRAY
            canvas.drawCircle(x(i), y, context.dpf(4), paint)
            labelPaint.color = if (i == index) Ui.BLACK else Ui.LIGHT_GRAY
            labelPaint.typeface = if (i == index) Ui.bold else Ui.regular
            canvas.drawText(v.toString(), x(i), y + context.dpf(38), labelPaint)
        }
        paint.color = Ui.BLACK
        canvas.drawCircle(x(index), y, context.dpf(12), paint)
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE -> {
                parent.requestDisallowInterceptTouchEvent(true)
                val i = ((event.x - pad) / ((width - 2 * pad) / (steps.size - 1))).let { Math.round(it) }.coerceIn(0, steps.size - 1)
                if (i != index) { index = i; invalidate(); onStep(steps[i]) }
            }
            // 손을 떼거나 터치가 취소되면 보이는 값으로 확정한다(손잡이와 저장 값이 어긋나지 않게).
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL ->
                if (index != committed) { committed = index; onCommit(steps[index]) }
        }
        return true
    }

    companion object {
        const val HEIGHT_DP = 72
        const val TRACK_Y_DP = 26
    }
}
