package com.woody.booklauncher

import android.app.Activity
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.provider.Settings
import android.util.LruCache
import android.webkit.MimeTypeMap
import com.woody.booklauncher.readers.BookFiles
import com.woody.booklauncher.readers.Fallback
import com.woody.booklauncher.readers.HomeContext
import com.woody.booklauncher.readers.OpenResult
import com.woody.booklauncher.readers.ReaderApps
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Collator
import java.util.Locale

/** 런처에 보이는 앱 하나(실행 화면 단위). [key] 는 ComponentName 을 펼친 문자열이다. */
data class AppEntry(val component: ComponentName, val label: String) {
    val key: String get() = component.flattenToShortString()
    val pkg: String get() = component.packageName
}

/** 잠금 해제 전(부팅 직후)에도 모든 앱을 찾는다. 기본값은 잠금 전에 뜰 수 있는 앱만 돌려준다. */
private const val MATCH_ALL_BOOT_STATES = PackageManager.MATCH_DIRECT_BOOT_AWARE or PackageManager.MATCH_DIRECT_BOOT_UNAWARE

object AppStore {
    /** 이름 순서: 영문 → 한글 (ICU 기본 순서는 라틴 문자가 한글보다 앞) */
    val collator: Collator by lazy { Collator.getInstance(Locale.ROOT) }

    /** 이북 앱(읽고 있는 앱 고르기에서 먼저 보여 줄 앱) */
    val EBOOK_PACKAGES = ReaderApps.packages
    private val EBOOK_WORDS = listOf("ebook", "e-book", "eink", "e ink", "도서", "서재", "리디", "book")

    /** 설치된 앱 전체(이 런처 자신 제외)를 이름 순으로 */
    private var cachedApps: List<AppEntry>? = null
    private var cachedLocale: Locale? = null
    private var packageSequence = 0

    /**
     * 설치된 앱 전체. 앱 조회와 이름 읽기가 비싸서(돌아올 때 20~30ms, 런처가 새로 뜰 때 300ms) 앱이
     * 설치·삭제·업데이트됐거나 언어가 바뀌었을 때만 다시 읽고, 그 밖에는 지난번 목록을 쓴다.
     * 런처가 새로 떠도 재부팅 전이면 파일에 저장해 둔 목록을 쓴다(앱 변경 순번은 부팅마다 새로 매겨진다).
     */
    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val locale = context.resources.configuration.locales[0]
        if (cachedApps == null) readSaved(context, locale)
        val changed = pm.getChangedPackages(packageSequence)
        cachedApps?.let { if (changed == null && locale == cachedLocale) return it }
        changed?.let { packageSequence = it.sequenceNumber }
        cachedLocale = locale
        cachedFileReaders = null
        return queryApps(context).also { cachedApps = it; save(context, it, locale) }
    }

    private fun savedFile(context: Context) = File(Storage.of(context).filesDir, "apps.json")
    private fun bootCount(context: Context) =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    /** 저장해 둔 목록이 이번 부팅·언어의 것이면 쓴다. 없거나 깨졌으면 그냥 새로 읽게 둔다. */
    private fun readSaved(context: Context, locale: Locale) = runCatching {
        val json = JSONObject(savedFile(context).readText())
        if (json.optInt("boot", -2) != bootCount(context) || json.optString("locale") != locale.toLanguageTag()) return@runCatching
        val arr = json.getJSONArray("apps")
        val apps = (0 until arr.length()).map { arr.getJSONArray(it) }
            .map { AppEntry(ComponentName(it.getString(0), it.getString(1)), it.getString(2)) }
        packageSequence = json.getInt("seq")
        cachedApps = apps
        cachedLocale = locale
    }

    private fun save(context: Context, apps: List<AppEntry>, locale: Locale) {
        val arr = JSONArray()
        apps.forEach { arr.put(JSONArray().put(it.component.packageName).put(it.component.className).put(it.label)) }
        val json = JSONObject().put("boot", bootCount(context)).put("seq", packageSequence)
            .put("locale", locale.toLanguageTag()).put("apps", arr)
        runCatching { savedFile(context).writeText(json.toString()) }
    }

    private fun queryApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val found = pm.queryIntentActivities(intent, MATCH_ALL_BOOT_STATES)
        val frozen = if (Device.hasBooxLauncher(context)) frozenActivities(pm, intent) else emptyList()
        return (found + frozen)
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                AppEntry(
                    ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                    it.loadLabel(pm).toString().trim(),
                )
            }
            .distinctBy { it.key }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    /**
     * BOOX 가 얼려 둔 앱의 실행 화면. BOOX 는 앱을 꺼 두는 것(DISABLED_USER)으로 얼려 보통 조회에는 빠진다.
     * 앱이 스스로 끈 실행 화면(아이콘 바꾸기 별칭 등)은 넣지 않는다. 누르면 BOOX 런처가 풀고 연다([launch]).
     */
    private fun frozenActivities(pm: PackageManager, intent: Intent): List<ResolveInfo> =
        pm.queryIntentActivities(intent, MATCH_ALL_BOOT_STATES or PackageManager.MATCH_DISABLED_COMPONENTS)
            // 켜진 앱의 화면은 보통 조회에 이미 있다(꺼진 화면이면 앱이 스스로 끈 것).
            .filter { !it.activityInfo.applicationInfo.enabled && Device.isFrozen(pm, it.activityInfo.packageName) }
            .filter {
                val a = it.activityInfo
                when (runCatching { pm.getComponentEnabledSetting(ComponentName(a.packageName, a.name)) }.getOrNull()) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> a.enabled
                    else -> false
                }
            }

    /** 지난번에 정리한 앱 목록([loadAndPrune]. 목록이 그대로면 다시 정리하지 않는다) */
    private var prunedApps: List<AppEntry>? = null

    /** 앱 목록을 읽고, 지워진 앱을 설정과 읽고 있는 책에서 정리한다(앱 목록이 바뀌었을 때만). */
    fun loadAndPrune(context: Context, prefs: HomePrefs): List<AppEntry> {
        val apps = load(context)
        // 잠금 해제 전에는 앱 정보가 덜 보일 수 있어, 지워진 앱 정리는 잠금이 풀린 뒤에만 한다.
        if (!Storage.isUnlocked(context) || apps === prunedApps) return apps
        // 숨긴(hidden) 앱도 설치된 것으로 본다. 기본 조회로는 지워진 앱처럼 못 찾는다.
        // 파일로만 여는 이북 앱(네오리더)의 항목도 있는 앱으로 본다(앱 목록에 없어 패키지를 묻지 않게).
        val fix = keyFixer(apps + fileReaders(context)) { pkg ->
            runCatching { context.packageManager.getPackageInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES) }.isSuccess
        }
        prefs.prune(fix)
        BookShelf.pruneApps(context, fix)
        prunedApps = apps
        return apps
    }

    /**
     * 저장해 둔 앱 키를 지금 목록에 맞춘다. 앱을 업데이트하는 동안에는 잠깐 목록에서 빠지므로,
     * 패키지가 정말 지워졌을 때만 null(빼기), 실행 화면 이름만 바뀌었으면 같은 패키지의 새 화면 키를 돌려준다.
     */
    private fun keyFixer(apps: List<AppEntry>, isInstalled: (String) -> Boolean): (String) -> String? {
        val keys = apps.map { it.key }.toSet()
        return fix@{ key ->
            if (key in keys) return@fix key
            val pkg = key.substringBefore('/')
            apps.firstOrNull { it.pkg == pkg }?.let { return@fix it.key }
            if (isInstalled(pkg)) key else null
        }
    }

    fun isEbook(app: AppEntry): Boolean {
        if (app.pkg in EBOOK_PACKAGES) return true
        val l = app.label.lowercase(Locale.ROOT)
        return EBOOK_WORDS.any { it in l }
    }

    fun launch(context: Context, app: AppEntry) {
        if (Device.hasBooxLauncher(context) && Device.isFrozen(context.packageManager, app.pkg)) return openFrozen(context, app)
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            // 전자잉크라 화면 전환 애니메이션을 쓰지 않는다.
            context.startActivity(intent, ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle())
        } catch (_: Exception) {
            // 지워졌거나 열 수 없는 앱. 다음에 홈으로 돌아올 때 목록에서 빠진다.
        }
    }

    /**
     * 실행 화면이 없어 런처 조회에 나오지 않고 파일로만 여는 이북 앱(네오리더, [com.woody.booklauncher.readers.ReaderApp.fileEntry])의 항목.
     * 깔려 있고 켜져 있을 때만. 자동 추가로 그 앱의 책에 붙고, 책의 앱 이름과 그 책 열기에만 쓴다(모든 앱·자주 쓰는 앱·읽고 있는 앱 고르기에는
     * 넣지 않는다. 그냥 띄우면 아무것도 하지 않고, 고를 파일이 없다).
     */
    private fun fileReaders(context: Context): List<AppEntry> = cachedFileReaders ?: ReaderApps.ALL.mapNotNull { spec ->
        val viewer = spec.fileEntry ?: return@mapNotNull null
        val enabled = runCatching { context.packageManager.getApplicationInfo(spec.pkg, 0).enabled }.getOrDefault(false)
        if (enabled) AppEntry(ComponentName(spec.pkg, viewer.activity), context.getString(viewer.label)) else null
    }.also { cachedFileReaders = it }

    /** [fileReaders] 를 앱 목록을 다시 읽을 때까지 담아 둔다(홈의 책 칸마다 앱 정보를 묻지 않게). 언어가 바뀌면 이름도 바뀌어 같이 비운다. */
    @Volatile private var cachedFileReaders: List<AppEntry>? = null

    /** 책의 앱 [key]. 앱 목록([apps])에 없으면 파일로만 여는 이북 앱([fileReaders])에서 찾는다. */
    fun find(context: Context, key: String, apps: List<AppEntry> = load(context)): AppEntry? =
        apps.find { it.key == key } ?: fileReaders(context).find { it.key == key }

    /** 이북 앱 [pkg] 에서 편 책에 붙일 앱: 파일로만 여는 앱은 그 항목, 아니면 그 패키지의 실행 화면 */
    fun readerKey(context: Context, pkg: String): String? =
        fileReaders(context).firstOrNull { it.pkg == pkg }?.key ?: load(context).firstOrNull { it.pkg == pkg }?.key

    /** 이북 앱 [pkg] 의 이름: 앱 목록의 이름([readerKey]), 목록에 없으면 시스템의 앱 이름. 깔려 있지 않거나 꺼져 있으면 null */
    fun readerLabel(context: Context, pkg: String): String? {
        val pm = context.packageManager
        val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()?.takeIf { it.enabled } ?: return null
        return readerKey(context, pkg)?.let { find(context, it)?.label } ?: pm.getApplicationLabel(info).toString()
    }

    /**
     * 책의 앱을 연다. 이북 앱이면 그 앱이 정한 대로([ReaderApp.open]: 파일·링크·앱). 파일로만 여는 이북 앱의 책인데 그 파일을 모르거나 없으면
     * false: 부르는 쪽이 뒤에서 다시 찾아([findBookFile]) 연다([openFoundFile]).
     */
    fun openBook(context: Context, book: ShelfBook, app: AppEntry): Boolean {
        val reader = ReaderApps.forPackage(context, app.pkg) ?: run { launch(context, app); return true }
        fun fail(onFail: Fallback) = if (onFail == Fallback.LAUNCH) { launch(context, app); true } else false
        return when (val r = reader.open(HomeContext(context, app.component), book)) {
            OpenResult.Launch -> { launch(context, app); true }
            is OpenResult.Start -> if (start(context, r.intent)) { r.started?.invoke(); true } else fail(r.onFail)
            is OpenResult.File -> if (openFile(context, r.component, r.path)) { r.started?.invoke(); true } else fail(r.onFail)
            OpenResult.FindFile -> false
        }
    }

    /**
     * 파일로만 여는 이북 앱 [app] 의 책 [book] 인데 그 파일을 모를 때 그 앱 데이터([com.woody.booklauncher.readers.ReaderApp.bookFiles])에서 다시 찾는다.
     * 찾을 이름은 제목과 이북 앱이 보여 준 제목(파일 이름이거나 파일 안의 제목). 같은 이름의 파일이 여럿이면(파일 안의 제목이 같은 PDF 등) 다른 책으로
     * 넣지 않은 파일이 먼저. 못 찾으면 null. 저장소 전체는 훑지 않는다(1.7.2 까지는 그다음 같은 파일 이름의 첫 책 파일을 찾았는데, 폴더마다 같은 이름이
     * 있으면(만화 `01.cbz`) 다른 책이 열리고 SD 카드는 찾지 못해 뺐다). 다른 앱을 깨우므로 화면 스레드에서 부르지 않는다.
     */
    fun findBookFile(context: Context, book: ShelfBook, app: AppEntry): String? {
        val titles = listOf(book.title, book.seenTitle).filter { it.isNotEmpty() }.distinct()
        val found = ReaderApps.forPackage(context, app.pkg)?.bookFiles(context, titles).orEmpty()
        return found.firstOrNull { BookShelf.idByFile(context, it).let { id -> id == null || id == book.id } } ?: found.firstOrNull()
    }

    /**
     * 다시 찾은 파일 [path] 를 [book] 의 파일로 적고 [app] 으로 연다. 그 파일로 넣은 다른 책이 있으면(같은 파일이 두 권으로 들어간 1.5.11 까지의 책)
     * 적지 않고 열기만 한다(한 파일에는 한 권만: 읽으면 그 책으로 들어간다).
     */
    fun openFoundFile(context: Context, book: ShelfBook, app: AppEntry, path: String): Boolean {
        if (BookShelf.idByFile(context, path).let { it == null || it == book.id }) BookShelf.setFilePath(context, book.id, path)
        return openFile(context, app.component, path)
    }

    /** [intent] 로 연다(새 작업, 화면 전환 애니메이션 없이). 열 수 없으면 false */
    private fun start(context: Context, intent: Intent): Boolean = runCatching {
        context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle())
    }.isSuccess

    /**
     * [path] 를 [component] 로 연다(`file://`). 받는 앱(네오리더·KOReader)은 저장소 전체를 읽는 앱이라 경로를 그대로 넘긴다.
     * 안드로이드 7+ 은 다른 앱에 `file://` 를 넘기면 막으므로(FileUriExposedException) 여는 동안만 그 검사를 끈다.
     * 파일이 지워졌으면 받는 앱이 알린다(책 파일 읽기를 허용하지 않았으면 이 앱은 미리 볼 수 없다. 허용했으면 KOReader 는 [openBook] 이 미리 본다).
     */
    private fun openFile(context: Context, component: ComponentName, path: String): Boolean {
        val file = File(path)
        val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase(Locale.ROOT))
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.fromFile(file), type).setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val policy = StrictMode.getVmPolicy()
        return try {
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
            context.startActivity(intent, ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle())
            true
        } catch (_: Exception) {
            false
        } finally {
            StrictMode.setVmPolicy(policy)
        }
    }

    /**
     * BOOX 가 얼린 앱을 BOOX 런처에 풀고 열어 달라고 한다. 런처가 죽어 있었으면 먼저 다시 띄우는데, 막 뜬 런처는
     * 받는 곳을 등록하기 전에 온 방송을 놓칠 수 있다. 그래서 조금 뒤에도 이 화면이 그대로면 한 번 더 보내고,
     * 그래도 안 열리면 얼리기에서 빼라고 안내한다. 다시 누르면 앞의 재시도는 그만둔다.
     */
    private fun openFrozen(context: Context, app: AppEntry) {
        val activity = context as? Activity
        // 앱이 열리거나 사용자가 다른 데로 가면 이 화면이 초점을 잃는다.
        val left = { activity != null && (activity.isFinishing || !activity.hasWindowFocus()) }
        main.removeCallbacksAndMessages(FROZEN_RETRY)
        // 런처를 띄우는 동안 다시 누르면 앞의 것은 그만둔다(재시도·안내가 겹치지 않게).
        val tap = ++frozenTaps
        Device.keepBooxLauncher(context) {
            // 그 사이 다른 데로 갔으면 열지 않는다.
            if (tap != frozenTaps || left()) return@keepBooxLauncher
            Device.sendOpenFrozenApp(context, app.component)
            main.postDelayed({
                if (left()) return@postDelayed
                // BOOX 런처가 풀기는 했는데 열지 못했으면 직접 연다.
                if (!Device.isFrozen(context.packageManager, app.pkg)) return@postDelayed launch(context, app)
                Device.sendOpenFrozenApp(context, app.component)
                main.postDelayed({ if (!left() && activity != null) showFrozenHelp(activity, app) }, FROZEN_RETRY, FROZEN_WAIT_MS)
            }, FROZEN_RETRY, FROZEN_WAIT_MS)
        }
    }

    private fun showFrozenHelp(activity: Activity, app: AppEntry) {
        Sheet(activity).header(app.label, activity.getString(R.string.boox_frozen_help), app = app)
            .item(activity.getString(R.string.boox_freeze_settings), bold = true) { Device.openBooxFreezeSettings(activity) }
            .item(activity.getString(R.string.app_info)) { openAppInfo(activity, app) }
            .show()
    }

    private val main = Handler(Looper.getMainLooper())
    /** 얼린 앱 열기 재시도의 표(다시 누르면 지운다) */
    private val FROZEN_RETRY = Any()
    /** 얼린 앱을 누른 횟수. 런처를 띄운 뒤 이어 할 일이 마지막 누름의 것인지 본다. */
    private var frozenTaps = 0
    /** 얼린 앱을 열어 달라고 한 뒤 열렸는지 볼 때까지(BOOX 런처가 풀고 여는 시간을 넉넉히) */
    private const val FROZEN_WAIT_MS = 2000L

    fun openAppInfo(context: Context, app: AppEntry) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** 첫 글자 색인: 영문은 대문자, 한글은 초성(된소리는 예사소리로), 그 밖은 # */
    fun initial(label: String): String {
        val c = label.firstOrNull() ?: return "#"
        if (c in '가'..'힣') {
            val cho = (c - '가') / 588
            return "ㄱㄱㄴㄷㄷㄹㅁㅂㅂㅅㅅㅇㅈㅈㅊㅋㅌㅍㅎ"[cho].toString()
        }
        if (c.isLetter() && c.code < 128) return c.uppercaseChar().toString()
        return "#"
    }
}

/** 자주 쓰는 앱을 직접 정한 순서대로(숨긴 앱·지워진 앱 제외). 이름을 바꾼 앱은 [AppEntry.label] 이 바꾼 이름이다. */
fun favoriteApps(apps: List<AppEntry>, prefs: HomePrefs): List<AppEntry> {
    val hidden = prefs.hidden
    val names = prefs.favoriteNames
    val byKey = apps.associateBy { it.key }
    return prefs.favoriteList.mapNotNull { byKey[it] }.filter { it.key !in hidden }
        .map { app -> names[app.key]?.let { app.copy(label = it) } ?: app }
}

/**
 * 앱 아이콘 캐시. 흑백으로 바꿔 그리고, 컬러 [Palette] 면 원래 색 그대로.
 * 다른 앱의 아이콘을 불러오는 것은 느리므로(그 앱의 리소스를 연다) 그린 아이콘을 `files/icons/` 에도 두고, 앱이 새로 뜰 때는 그것을 읽는다.
 * 파일 이름에 앱을 업데이트한 때를 넣어 앱이 바뀌면 다시 그린다.
 */
object AppIcons {
    private val cache = LruCache<String, Bitmap>(200)
    private val grayPaint = Ui.grayPaint()
    private var packageSequence = 0

    private fun dir(context: Context) = File(Storage.of(context).filesDir, "icons")
    /** 파일 이름에 쓰지 않는 글자(실행 화면 키의 `/`·`$` 등) */
    private val UNSAFE_NAME = Regex("[^A-Za-z0-9._]")

    fun icon(context: Context, app: AppEntry, px: Int): Bitmap {
        val gray = !Palette.current.color
        val packPkg = HomePrefs.iconPack(context)
        // 이 앱에 따로 고른 아이콘(`<아이콘 팩>/<그림 이름>`)
        val own = HomePrefs.iconOverrides(context)[app.key]
        val key = "${app.key}@$px@$gray@$packPkg@$own"
        cache.get(key)?.let { return it }
        val pm = context.packageManager
        val updated = runCatching { pm.getPackageInfo(app.pkg, MATCH_ALL_BOOT_STATES).lastUpdateTime }.getOrNull()
        // 따로 고른 아이콘의 팩, 고른 아이콘 팩(지웠으면 null: 다음 것으로)
        val ownPkg = own?.substringBefore('/')
        val ownName = own?.substringAfter('/')
        val ownStamp = IconPacks.stamp(context, ownPkg)
        val pack = IconPacks.stamp(context, packPkg)
        // 같은 앱·크기·색의 파일 이름 머리(뒤에 앱을 업데이트한 때와 아이콘 팩 또는 따로 고른 아이콘)
        val prefix = "${app.key.replace(UNSAFE_NAME, "_")}@$px${if (gray) "g" else "c"}@"
        val source = if (ownStamp != null) "@$ownStamp.$ownName" else pack?.let { "@$it" }.orEmpty()
        val file = updated?.let { File(dir(context), "$prefix$it${source.replace(UNSAFE_NAME, "_")}.png") }
        file?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }?.let { cache.put(key, it); return it }
        // 따로 고른 아이콘 → 팩의 아이콘 → 그 앱의 아이콘. 못 찾으면 기본 아이콘을 쓰되 기억하지 않는다(다음에 다시 찾는다).
        val found = (if (ownPkg != null && ownName != null && ownStamp != null) IconPacks.drawable(context, ownPkg, ownName) else null)
            ?: (if (packPkg != null && pack != null) IconPacks.icon(context, packPkg, pack, app.component) else null)
            ?: runCatching {
                pm.getActivityInfo(app.component, MATCH_ALL_BOOT_STATES or PackageManager.MATCH_DISABLED_COMPONENTS).loadIcon(pm)
            }.getOrNull()
        val out = draw(found ?: pm.defaultActivityIcon, px, gray)
        if (found != null) {
            cache.put(key, out)
            if (file != null) Bg.run({ save(file, prefix, out) }) {}
        }
        return out
    }

    /** 아이콘 팩의 그림 [name] 을 [px] 크기로(아이콘 바꾸기의 칸, 기억하지 않는다). 없으면 null. */
    fun packIcon(context: Context, pkg: String, name: String, px: Int): Bitmap? =
        IconPacks.drawable(context, pkg, name)?.let { draw(it, px, !Palette.current.color) }

    /** [drawable] 을 [px] 크기 그림으로. [gray] 면 흑백으로 바꾼다. */
    private fun draw(drawable: Drawable, px: Int, gray: Boolean): Bitmap {
        val color = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        Canvas(color).let { drawable.setBounds(0, 0, px, px); drawable.draw(it) }
        return if (!gray) color else Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawBitmap(color, 0f, 0f, grayPaint)
            color.recycle()
        }
    }

    /** 그린 아이콘을 [file] 로 두고 같은 앱·크기·색의 예전 파일([prefix], 앱을 업데이트하기 전)은 지운다. */
    private fun save(file: File, prefix: String, bitmap: Bitmap) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (!tmp.renameTo(file)) tmp.delete()
        file.parentFile?.listFiles { f -> f != file && f.name.startsWith(prefix) }?.forEach { it.delete() }
    }

    /**
     * 그사이 설치·업데이트·삭제된 앱의 아이콘만 캐시에서 비우고(아이콘이 바뀌었을 수 있으므로) 그 패키지들을 돌려준다(없으면 빈 집합).
     * 다른 앱이 뒤에서 업데이트될 때마다 모든 아이콘을 다시 읽고 홈 전체를 다시 그리지 않게, 부르는 쪽은 보이는 앱이 들었는지만 본다.
     */
    fun clearChangedPackages(context: Context): Set<String> {
        val changed = context.packageManager.getChangedPackages(packageSequence) ?: return emptySet()
        packageSequence = changed.sequenceNumber
        val pkgs = changed.packageNames.toSet()
        // 쓰는 아이콘 팩(고른 팩·앱마다 고른 아이콘의 팩)이 바뀌었으면(업데이트·삭제) 모든 아이콘을 비운다.
        // 홈은 팩의 표시([IconPacks.stamp])로 다시 그린다.
        if (usedPacks(context).any { it in pkgs }) cache.evictAll()
        // 키는 `<패키지>/<화면>@크기@흑백@아이콘 팩@따로 고른 아이콘`
        else cache.snapshot().keys.filter { it.substringBefore('/') in pkgs }.forEach { cache.remove(it) }
        return pkgs
    }

    /** 아이콘을 가져오는 팩들: 설정에서 고른 팩과 앱마다 고른 아이콘의 팩 */
    fun usedPacks(context: Context): Set<String> =
        HomePrefs.iconOverrides(context).values.map { it.substringBefore('/') }.toSet() + listOfNotNull(HomePrefs.iconPack(context))
}
