package com.woody.pebbledesk

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate

/** 런처 설정과 앱 목록 상태(자주 쓰는 앱·숨긴 앱) */
class HomePrefs(context: Context) {
    private val sp = Storage.of(context).getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 예전(책 한 권만 저장하던) 버전의 읽고 있는 앱. 그 책을 새 목록으로 옮길 때만 읽는다. */
    val legacyReadingApp: String? get() = sp.getString("default_app", null)

    /**
     * 자주 쓰는 앱(직접 정한 순서). 예전에는 순서 없는 묶음(favorites)으로 저장했으므로,
     * 아직 목록이 없으면 그 묶음을 늘어놓아 시작한다.
     */
    var favoriteList: List<String>
        get() {
            sp.getString("favorite_list", null)?.let { json ->
                return runCatching { JSONArray(json).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList())
            }
            return sp.getStringSet("favorites", emptySet())!!.sorted()
        }
        set(v) {
            val list = v.distinct()
            // 빠진 앱에 붙인 이름도 함께 지운다(다시 넣으면 원래 이름).
            sp.edit().putString("favorite_list", JSONArray(list).toString()).remove("favorites")
                .putString(KEY_FAV_NAMES, JSONObject(favoriteNames.filterKeys { it in list }).toString()).apply()
        }

    /** 자주 쓰는 앱에 사용자가 붙인 이름(앱 키 → 이름). 홈·자주 쓰는 앱 관리에서만 쓰고, 모든 앱에는 원래 이름이 보인다. */
    var favoriteNames: Map<String, String>
        get() = runCatching {
            val o = JSONObject(sp.getString(KEY_FAV_NAMES, null) ?: "{}")
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())
        set(v) = sp.edit().putString(KEY_FAV_NAMES, JSONObject(v).toString()).apply()

    /** [key] 에 이름 [name] 을 붙인다. null 이면 원래 이름으로. */
    fun setFavoriteName(key: String, name: String?) {
        favoriteNames = if (name == null) favoriteNames - key else favoriteNames + (key to name)
    }

    /** 들어 있는지 볼 때와 넣고 뺄 때. 새로 넣는 앱은 목록 맨 뒤에 붙는다. */
    var favorites: Set<String>
        get() = favoriteList.toSet()
        set(v) {
            val old = favoriteList
            favoriteList = old.filter { it in v } + v.filter { it !in old }
        }

    /** [key] 를 [delta] 칸 옮긴다(-1 이면 위로). */
    fun moveFavorite(key: String, delta: Int) {
        val list = favoriteList.toMutableList()
        val from = list.indexOf(key)
        val to = from + delta
        if (from < 0 || to !in list.indices) return
        list.add(to, list.removeAt(from))
        favoriteList = list
    }

    var hidden: Set<String>
        get() = sp.getStringSet("hidden", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("hidden", v).apply()

    /** 홈 시계: true 면 24시간(18:24), false 면 12시간(오후 6:24) */
    var clock24h: Boolean
        get() = sp.getBoolean("clock_24h", true)
        set(v) = sp.edit().putBoolean("clock_24h", v).apply()

    /** 글자 크기(%): [TEXT_SCALES] 중 하나. 앱 전체 글자에 곱한다([Ui.textScale]). */
    var textScalePercent: Int
        get() = percent(sp, KEY_TEXT_SCALE, TEXT_SCALES)
        set(v) = sp.edit().putInt(KEY_TEXT_SCALE, v).apply()

    /** 줄 간격(%): [ROW_SCALES] 중 하나. 목록·메뉴 줄 높이에 곱한다([Ui.rowScale]). */
    var rowScalePercent: Int
        get() = percent(sp, KEY_ROW_SCALE, ROW_SCALES)
        set(v) = sp.edit().putInt(KEY_ROW_SCALE, v).apply()

    /** 앱 언어(시계·날짜 포함): 기기 언어를 따르기([AppLanguage.SYSTEM]) · 한국어 · 영어. 처음 값은 [language] */
    var language: AppLanguage
        get() = language(sp)
        set(v) = sp.edit().putString(KEY_LANGUAGE, v.key).apply()

    /** '화면이 크게 보입니다' 창에서 `다시 보지 않기` 를 누른 때의 밀도(`지금/맞출 값`, 예: `350/300`). 값이 바뀌면 다시 알린다. */
    var densityWarnSkip: String?
        get() = sp.getString("density_warn_skip", null)
        set(v) = sp.edit().putString("density_warn_skip", v).apply()

    /**
     * 책 파일 읽기가 꺼진 채 읽는 화면이 뜬, 책 파일 읽기가 필요한 이북 앱(패키지, [com.woody.pebbledesk.readers.ReaderApp.needsBookFiles]). 홈으로 돌아오면 그 앱마다
     * 안내 창을 띄우고 지운다([HomeActivity.warnFileAccess]). 그 앱을 쓸 때만 적히므로 깔아만 둔 앱은 묻지 않는다.
     */
    var fileNoticeWanted: Set<String>
        get() = sp.getStringSet("file_notice_wanted", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("file_notice_wanted", v).apply()

    /** 책 파일 읽기 안내 창에서 `다시 보지 않기` 를 누른 이북 앱(패키지). 다른 앱은 따로 묻는다 */
    var fileNoticeSkip: Set<String>
        get() = sp.getStringSet("file_notice_skip", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("file_notice_skip", v).apply()

    /**
     * 자동 추가 '꺼져 있습니다'·'멈춰 있습니다' 창에서 `다시 보지 않기` 를 눌렀을 때, 그 창을 띄운 때 서비스가 마지막으로 붙은 때([ReaderWatchService.lifeLog] 의 연결 때,
     * 붙은 적이 없으면 0). 지금 연결 때가 이 값과 같으면(그 뒤로 한 번도 붙지 않았으면) 창을 띄우지 않는다. 서비스가 다시 붙으면 연결 때가 바뀌어
     * 그 뒤에 꺼지거나 멈추면 다시 알린다. 누른 적이 없으면 -1(연결 때가 될 수 없는 값)
     */
    var autoNoticeSkip: Long
        get() = sp.getLong("auto_notice_skip", -1)
        set(v) = sp.edit().putLong("auto_notice_skip", v).apply()

    /**
     * 같은 책인지 물을 짝([TitlePair.key]): 서재의 제목과 읽는 화면의 제목이 달라 이북 앱 파일이 적는다. 홈으로 돌아오면 하나씩 묻고([HomeActivity.askSameBook])
     * 답하면 [sameBooks]·[otherBooks] 로 옮긴다. 답하지 않고 닫으면 남아 다음에 다시 묻는다
     */
    var sameBookAsk: Set<String>
        get() = sp.getStringSet("same_book_ask", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("same_book_ask", v).apply()

    /** `같은 책` 이라고 답한 짝([TitlePair.key]). 그 서재 제목을 읽는 화면 제목의 책으로 본다(그 앱 파일이 쓴다) */
    var sameBooks: Set<String>
        get() = sp.getStringSet("same_books", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("same_books", v).apply()

    /** `다른 책` 이라고 답한 짝([TitlePair.key]). 다시 묻지 않는다 */
    var otherBooks: Set<String>
        get() = sp.getStringSet("other_books", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("other_books", v).apply()

    /** [pair] 를 물은 적이 있거나 묻는 중인가(답했거나 아직 기다리는 중) */
    fun knowsPair(pair: TitlePair): Boolean = pair.key.let { it in sameBookAsk || it in sameBooks || it in otherBooks }

    /** [pkg] 앱에서 `같은 책` 이라고 답한 짝들 */
    fun sameBooksOf(pkg: String): List<TitlePair> = sameBooks.mapNotNull(TitlePair::of).filter { it.pkg == pkg }

    /** 홈에서 `이 버전은 알리지 않기` 를 누른 판 */
    var updateSkip: String?
        get() = sp.getString("update_skip", null)
        set(v) = sp.edit().putString("update_skip", v).apply()

    /** 홈의 새 판 창을 마지막으로 닫은 판과 날(`1.5.9@2026-10-20`). 같은 날에는 다시 띄우지 않는다. 설치 허용 안내로 가면 지운다. */
    var updateShown: String?
        get() = sp.getString("update_shown", null)
        set(v) = sp.edit().putString("update_shown", v).apply()

    /**
     * 자주 쓰는 앱을 격자(아이콘 아래 이름, 한 줄에 4개)로. false 면 목록. 1.5.12 까지는 목록이 기본이라 고른 적 없이 쓰던 사람은 목록 그대로,
     * 새로 깐 사람은 처음 [language] 를 정할 때 격자로 적어 둔다.
     */
    var favGrid: Boolean
        get() = sp.getBoolean(KEY_FAV_GRID, false)
        set(v) = sp.edit().putBoolean(KEY_FAV_GRID, v).apply()

    /**
     * 세로 홈의 자주 쓰는 앱을 낮게(목록 2줄 · 격자 1줄). false 면 지금 높이(목록 3줄 · 격자 2줄).
     * 낮게면 남는 높이를 책 자리에 주고 함께 읽는 책을 큰 표지로 그린다. 가로 홈에는 쓰지 않는다.
     */
    var favShort: Boolean
        get() = sp.getBoolean("fav_short", false)
        set(v) = sp.edit().putBoolean("fav_short", v).apply()

    /** 독서 기록의 4주를 날마다 막대그래프로. false 면 링 달력 */
    var readingGraph: Boolean
        get() = sp.getBoolean("reading_graph", false)
        set(v) = sp.edit().putBoolean("reading_graph", v).apply()

    /** 올해 읽은 책을 표지 격자(한 줄에 4권)로. false 면 목록 */
    var yearGrid: Boolean
        get() = sp.getBoolean("year_grid", false)
        set(v) = sp.edit().putBoolean("year_grid", v).apply()

    /** 올해 읽은 책에서 그해 완독한 책만. false 면 전체 */
    var yearFinishedOnly: Boolean
        get() = sp.getBoolean("year_finished_only", false)
        set(v) = sp.edit().putBoolean("year_finished_only", v).apply()

    /** 통계 화면마다(독서 기록·올해 읽은 책·결산) 보기에서 고른 카테고리([Categories.NONE] = 분류 없음). 비면 `전체`. */
    enum class StatsScreen(val key: String) { READING("filter_reading"), YEAR("filter_year"), RECAP("filter_recap") }

    fun statsPicked(screen: StatsScreen): Set<Long> =
        sp.getStringSet(screen.key, emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()

    fun setStatsPicked(screen: StatsScreen, picked: Set<Long>) =
        sp.edit().putStringSet(screen.key, picked.map { it.toString() }.toSet()).apply()

    // 읽는 책 자동 추가·오늘 읽은 시간은 따로 켜짐 값을 두지 않는다. 시스템의 접근성(ReaderWatchService.isEnabled)·
    // 사용 기록 액세스(ReadingLog.hasAccess)가 곧 켜짐이다(1.3.1 까지의 `auto_books`·`reading_time` 은 읽지 않는다).

    /** 시작하기(온보딩)를 한 번 띄웠는가(둘 다 이미 켜져 있어 건너뛴 경우도) */
    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()

    /**
     * 하루 목표(분). 0 이면 목표 없이 읽은 날만 표시. 바꾸면 오늘부터의 목표로 `reading_goal_history` 에 남겨
     * 지난날은 그날의 목표로 그린다([readingGoals]). 처음 바꿀 때 그 전 목표를 '처음부터'로 함께 남긴다.
     */
    var readingGoalMin: Int
        get() = sp.getInt("reading_goal_min", 30)
        set(v) {
            if (v == readingGoalMin) return
            val today = LocalDate.now().toEpochDay()
            // 같은 날 여러 번 바꾸면 마지막 값만
            val history = goalHistory().ifEmpty { listOf(Long.MIN_VALUE to readingGoalMin) }.filter { it.first < today } + (today to v)
            sp.edit().putInt("reading_goal_min", v)
                .putString("reading_goal_history", history.joinToString(",") { "${it.first}:${it.second}" }).apply()
        }

    /** (이날부터, 목표 분) 바꾼 순서대로. 바꾼 적이 없으면(이 기능 전 설치 포함) 비어 있다. */
    private fun goalHistory(): List<Pair<Long, Int>> =
        sp.getString("reading_goal_history", null).orEmpty().split(',').mapNotNull { e ->
            val (day, min) = e.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            day.toLongOrNull()?.let { d -> min.toIntOrNull()?.let { d to it } }
        }

    /** 날짜별 하루 목표(분): 그날까지 마지막으로 정한 목표. 기록이 없으면 지금 목표. 한 번 읽어 여러 날에 쓴다. */
    fun readingGoals(): (LocalDate) -> Int {
        val history = goalHistory()
        val now = readingGoalMin
        return { day -> history.lastOrNull { it.first <= day.toEpochDay() }?.second ?: now }
    }

    /** 독서 기록 달력·이번 주의 시작 요일: 월요일(기본) 또는 일요일 */
    var firstDayOfWeek: DayOfWeek
        get() = if (sp.getBoolean("week_starts_sunday", false)) DayOfWeek.SUNDAY else DayOfWeek.MONDAY
        set(v) = sp.edit().putBoolean("week_starts_sunday", v == DayOfWeek.SUNDAY).apply()

    /** 색 테마(설정 › 화면 › 색, [Palette]). 처음은 흑백. */
    var palette: Palette
        get() = palette(sp)
        set(v) = sp.edit().putString(KEY_THEME, v.key).remove(KEY_LEGACY_PALETTE).apply()

    var showIcons: Boolean
        get() = sp.getBoolean("show_icons", true)
        set(v) = sp.edit().putBoolean("show_icons", v).apply()

    /** 앱 아이콘을 가져올 아이콘 팩의 패키지(설정 › 화면 › 아이콘 모양, [IconPacks]). 없으면 기본(각 앱의 아이콘). */
    var iconPack: String?
        get() = sp.getString(KEY_ICON_PACK, null)
        set(v) = sp.edit().putString(KEY_ICON_PACK, v).apply()

    /** 앱마다 고른 아이콘(앱 키 → `<아이콘 팩>/<그림 이름>`, 길게 누르기 › 아이콘 바꾸기). [iconPack] 보다 먼저 쓴다. */
    var iconOverrides: Map<String, String>
        get() = iconOverrides(sp)
        set(v) = sp.edit().putString(KEY_ICON_OVERRIDES, JSONObject(v).toString()).apply()

    /** [key] 의 아이콘을 [icon](`<아이콘 팩>/<그림 이름>`)으로. null 이면 원래 아이콘으로. */
    fun setIconOverride(key: String, icon: String?) {
        iconOverrides = if (icon == null) iconOverrides - key else iconOverrides + (key to icon)
    }

    /** 홈에서도 기기 상단 바를 보인다. 처음은 꺼짐(홈은 상단 바를 숨기고 자체 상단을 그린다). */
    var showStatusBar: Boolean
        get() = sp.getBoolean("show_status_bar", false)
        set(v) = sp.edit().putBoolean("show_status_bar", v).apply()

    /** 앞 쪽·다음 쪽 페이지 버튼의 키 코드([PageKeys]). 0 이면 정하지 않음(처음은 둘 다 없음). */
    var pageKeyPrev: Int
        get() = sp.getInt("page_key_prev", 0)
        set(v) = sp.edit().putInt("page_key_prev", v).apply()

    var pageKeyNext: Int
        get() = sp.getInt("page_key_next", 0)
        set(v) = sp.edit().putInt("page_key_next", v).apply()

    /** 홈에서 페이지 버튼이 넘길 줄: true 면 함께 읽는 책, false 면 자주 쓰는 앱(처음) */
    var pageKeysAlso: Boolean
        get() = sp.getBoolean("page_keys_also", false)
        set(v) = sp.edit().putBoolean("page_keys_also", v).apply()

    /** 기기 정보의 회전 고정 시험: 하위 화면이 연 때의 회전을 그대로 두고 다시 정하지 않는다([EinkActivity]). 처음은 꺼짐. */
    var rotationLock: Boolean
        get() = sp.getBoolean("rotation_lock", false)
        set(v) = sp.edit().putBoolean("rotation_lock", v).apply()

    fun toggleFavorite(key: String) {
        favorites = if (key in favorites) favorites - key else favorites + key
    }

    fun hide(key: String) {
        hidden = hidden + key
        favorites = favorites - key
    }

    fun unhide(key: String) {
        hidden = hidden - key
    }

    /** 지워졌거나 실행 화면 이름이 바뀐 앱을 정리한다([fix] 는 AppStore.keyFixer). */
    fun prune(fix: (String) -> String?) {
        // 이름을 먼저 옮긴다(목록을 저장할 때 목록에 없는 이름은 지운다).
        favoriteNames.let { names -> names.mapNotNull { (k, v) -> fix(k)?.let { it to v } }.toMap().let { if (it != names) favoriteNames = it } }
        favoriteList.let { list -> list.mapNotNull(fix).distinct().let { if (it != list) favoriteList = it } }
        hidden.let { set -> set.mapNotNull(fix).toSet().let { if (it != set) hidden = it } }
        iconOverrides.let { icons -> icons.mapNotNull { (k, v) -> fix(k)?.let { it to v } }.toMap().let { if (it != icons) iconOverrides = it } }
    }

    companion object {
        const val FILE = "home"
        /** 1.5.8 까지의 언어(영어면 true). 처음 [language] 를 정할 때만 읽고 지운다 */
        private const val KEY_ENGLISH = "english"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_FAV_NAMES = "favorite_names"
        private const val KEY_FAV_GRID = "fav_grid"
        private const val KEY_TEXT_SCALE = "text_scale"
        private const val KEY_ROW_SCALE = "row_scale"
        private const val KEY_THEME = "theme"
        /** 1.7.9 까지의 색(`mono` / `color`). 처음 [palette] 를 읽을 때 [KEY_THEME] 으로 옮기고 지운다 */
        private const val KEY_LEGACY_PALETTE = "palette"
        private const val KEY_ICON_PACK = "icon_pack"
        private const val KEY_ICON_OVERRIDES = "icon_overrides"

        /** 설정 › 글자 · 간격의 단계(%): 글자 크기는 70~150(기기 글꼴 크기에 곱한다), 줄 간격은 80~150, 10% 씩 */
        val TEXT_SCALES = (70..150 step 10).toList()
        val ROW_SCALES = (80..150 step 10).toList()

        private fun percent(sp: android.content.SharedPreferences, key: String, steps: List<Int>) =
            sp.getInt(key, 100).takeIf { it in steps } ?: 100

        private fun prefs(context: Context) = Storage.of(context).getSharedPreferences(FILE, Context.MODE_PRIVATE)

        /** 화면을 만들기 전에(attachBaseContext) 글자 크기·줄 간격을 정하려고 쓴다. */
        fun textScalePercent(context: Context) = percent(prefs(context), KEY_TEXT_SCALE, TEXT_SCALES)
        fun rowScalePercent(context: Context) = percent(prefs(context), KEY_ROW_SCALE, ROW_SCALES)

        /** 화면을 만들기 전에(attachBaseContext) 색을 정하려고 쓴다. */
        fun palette(context: Context) = palette(prefs(context))

        /**
         * 색 테마. 1.7.9 까지는 `palette` 에 흑백·컬러 둘 중 하나만 있었다: 처음 읽을 때 컬러(`color`)는 그때 홈 색인 [Palette.AMBER](등불 아래)로,
         * 그 밖은 흑백으로 [KEY_THEME] 에 적고 옛 값을 지운다. 모르는 값(판을 낮춘 뒤 더 새 판의 테마)은 흑백으로 보고 그대로 둔다.
         */
        internal fun palette(sp: android.content.SharedPreferences): Palette {
            sp.getString(KEY_THEME, null)?.let { return Palette.of(it) ?: Palette.MONO }
            val legacy = sp.getString(KEY_LEGACY_PALETTE, null) ?: return Palette.MONO
            val moved = if (legacy == "color") Palette.AMBER else Palette.MONO
            sp.edit().putString(KEY_THEME, moved.key).remove(KEY_LEGACY_PALETTE).apply()
            return moved
        }

        /** 아이콘을 그릴 때마다 설정 객체를 만들지 않으려고 쓴다. */
        fun iconPack(context: Context): String? = prefs(context).getString(KEY_ICON_PACK, null)

        /** 앱마다 고른 아이콘([iconOverrides]). 아이콘을 그릴 때마다 읽으므로 같은 값이면 읽어 둔 표를 쓴다. */
        fun iconOverrides(context: Context): Map<String, String> = iconOverrides(prefs(context))

        private var overridesRaw: String? = null
        private var overridesMap: Map<String, String> = emptyMap()

        @Synchronized
        private fun iconOverrides(sp: android.content.SharedPreferences): Map<String, String> {
            val raw = sp.getString(KEY_ICON_OVERRIDES, null) ?: return emptyMap()
            if (raw != overridesRaw) {
                overridesMap = runCatching { JSONObject(raw).let { o -> o.keys().asSequence().associateWith { o.getString(it) } } }.getOrDefault(emptyMap())
                overridesRaw = raw
            }
            return overridesMap
        }

        /** 화면을 만들기 전에(attachBaseContext) 언어를 정하려고 쓴다. 기기 언어를 따르면 지금 기기 언어로 가른다. */
        fun isEnglish(context: Context) = language(prefs(context)).isEnglish(systemLanguage())

        /** 기기(시스템) 언어. 앱이 화면마다 덮어쓴 언어가 아니라 기기 설정의 것 */
        fun systemLanguage(): String = android.content.res.Resources.getSystem().configuration.locales[0].language

        /**
         * 앱 언어. 처음 읽을 때 한 번 정해 저장한다: 1.5.8 까지 영어로 바꿔 둔 사람은 영어, 그 밖에 이미 쓰던 사람(설정 파일에 무엇이든 있다)은
         * 보던 그대로 한국어, 새로 깐 사람은 기기 언어를 따른다(업데이트한 뒤 기기 언어가 영어인 한국 사용자의 화면이 갑자기 바뀌지 않게).
         * 새로 깐 사람은 여기서 자주 쓰는 앱 격자([favGrid])도 적어 둔다(앱이 처음 읽는 설정이라 새로 깐 것을 가를 수 있는 곳).
         */
        internal fun language(sp: android.content.SharedPreferences): AppLanguage {
            AppLanguage.of(sp.getString(KEY_LANGUAGE, null))?.let { return it }
            val first = when {
                sp.getBoolean(KEY_ENGLISH, false) -> AppLanguage.ENGLISH
                sp.all.isNotEmpty() -> AppLanguage.KOREAN
                else -> AppLanguage.SYSTEM
            }
            val edit = sp.edit().putString(KEY_LANGUAGE, first.key).remove(KEY_ENGLISH)
            if (first == AppLanguage.SYSTEM) edit.putBoolean(KEY_FAV_GRID, true)
            edit.apply()
            return first
        }
    }
}

/**
 * 같은 책인지 묻는 짝: 이북 앱 [pkg] 의 서재(목록·상세)에 보인 제목 [shelf] 와 읽는 화면에 보인 제목 [read](자동 추가는 이 제목으로 넣는다).
 * 서재 제목은 도서관에 등록된 이름(판·형태 덧붙임)이고 읽는 화면 제목은 책 파일의 제목이라 같은 책이어도 다를 수 있다
 */
data class TitlePair(val pkg: String, val shelf: String, val read: String) {
    /** 설정에 적는 한 줄(`패키지\t서재 제목\t읽는 화면 제목`). 제목 안의 탭은 공백으로(나눌 수 있게. 제목 비교는 공백을 보지 않는다) */
    val key: String get() = listOf(pkg, shelf, read).joinToString("\t") { it.replace('\t', ' ') }

    companion object {
        fun of(key: String): TitlePair? = key.split('\t').takeIf { it.size == 3 && it.none(String::isBlank) }?.let { TitlePair(it[0], it[1], it[2]) }
    }
}

/** 앱 언어 설정. 앱 글은 한국어·영어 두 가지라, 기기 언어를 따르면 한국어 기기만 한국어이고 그 밖의 언어는 모두 영어다. */
enum class AppLanguage(val key: String) {
    SYSTEM("system"), KOREAN("ko"), ENGLISH("en");

    /** 기기 언어가 [systemLanguage](예: `ko`, `en`, `ja`)일 때 영어로 보이는가 */
    fun isEnglish(systemLanguage: String) = when (this) {
        SYSTEM -> systemLanguage != "ko"
        KOREAN -> false
        ENGLISH -> true
    }

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key }
    }
}
