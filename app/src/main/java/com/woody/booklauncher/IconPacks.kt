package com.woody.booklauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * 런처 아이콘 팩(설정 › 화면 › 아이콘 모양): 깔린 팩 찾기와 팩에서 앱 아이콘 찾기.
 * 팩은 사용자가 Play 스토어·F-Droid 에서 따로 까는 앱이다(이 앱이 받지 않는다). 팩에 없는 앱은 그 앱의 아이콘 그대로.
 * 팩은 어느 런처의 것이든 같은 꼴이다: 런처 테마 동작에 응답하고, `appfilter.xml` 에 실행 화면 → 그림 이름을 적어 둔다.
 */
object IconPacks {
    /** 아이콘 팩이 응답하는 런처 테마 동작(팩마다 이 중 몇 가지를 단다) */
    private val THEME_ACTIONS = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.teslacoilsw.launcher.THEME",
        "com.gau.go.launcherex.theme",
        "com.fede.launcher.THEME_ICONPACK",
        "com.anddoes.launcher.THEME",
        "com.dlto.atom.launcher.THEME",
    )
    private const val MATCH_ALL_BOOT_STATES = PackageManager.MATCH_DIRECT_BOOT_AWARE or PackageManager.MATCH_DIRECT_BOOT_UNAWARE

    data class Pack(val pkg: String, val label: String)

    /** 아이콘 팩이 하나라도 깔렸는지(앱 길게 누르기 메뉴의 `아이콘 바꾸기`. 이름은 읽지 않는다) */
    fun anyInstalled(context: Context): Boolean = THEME_ACTIONS.any { action ->
        runCatching { context.packageManager.queryIntentActivities(Intent(action), MATCH_ALL_BOOT_STATES).isNotEmpty() }.getOrDefault(false)
    }

    /** 깔린 아이콘 팩(이름 순서) */
    fun installed(context: Context): List<Pack> {
        val pm = context.packageManager
        return THEME_ACTIONS.flatMap { action ->
            runCatching { pm.queryIntentActivities(Intent(action), MATCH_ALL_BOOT_STATES) }.getOrDefault(emptyList())
        }
            .distinctBy { it.activityInfo.packageName }
            .map { Pack(it.activityInfo.packageName, it.activityInfo.applicationInfo.loadLabel(pm).toString()) }
            .sortedWith(compareBy(AppStore.collator) { it.label })
    }

    /**
     * 팩 [pkg] 의 표시(패키지·업데이트한 때, 설정의 팩과 앱마다 고른 그림의 팩). 아이콘 캐시 파일 이름과 홈의 다시 그리기 판단에 쓴다
     * (팩을 바꾸거나 업데이트하면 달라진다). [pkg] 가 null 이거나 팩을 지웠으면 null(다음 아이콘으로).
     */
    fun stamp(context: Context, pkg: String?): String? = pkg?.let {
        runCatching { "$it.${context.packageManager.getPackageInfo(it, MATCH_ALL_BOOT_STATES).lastUpdateTime}" }.getOrNull()
    }

    /** 팩의 실행 화면 → 그림 이름. 실행 화면이 맞는 줄이 없으면 같은 패키지의 첫 줄을 쓴다(앱이 실행 화면 이름을 바꾼 판). */
    class Filter(private val byActivity: Map<String, String>, private val byPackage: Map<String, String>) {
        fun drawableFor(app: ComponentName): String? = byActivity[app.flattenToString()] ?: byPackage[app.packageName]
        /** 팩이 그림 목록(`drawable.xml`)을 두지 않았을 때 고를 그림들 */
        val drawables: List<String> get() = byActivity.values.distinct()
    }

    /** 마지막으로 읽은 팩([stamp])의 표. 팩마다 수천 줄이라 한 번 읽어 둔다. */
    private var loaded: Pair<String, Filter>? = null

    /** 팩 [pkg]([stamp] 는 그 표시)에서 [app] 의 아이콘. 팩에 없거나 읽지 못하면 null. */
    fun icon(context: Context, pkg: String, stamp: String, app: ComponentName): Drawable? =
        runCatching { filter(context, pkg, stamp).drawableFor(app) }.getOrNull()?.let { drawable(context, pkg, it) }

    /** 팩 [pkg] 의 그림 [name]. 없거나 읽지 못하면 null. */
    fun drawable(context: Context, pkg: String, name: String): Drawable? = runCatching {
        val res = context.packageManager.getResourcesForApplication(pkg)
        val id = res.getIdentifier(name, "drawable", pkg).takeIf { it != 0 }
            ?: res.getIdentifier(name, "mipmap", pkg).takeIf { it != 0 }
            ?: return null
        res.getDrawable(id, null)
    }.getOrNull()

    /** 마지막으로 읽은 팩([stamp])의 모든 그림 이름 */
    private var names: Pair<String, List<String>>? = null

    /**
     * 팩 [pkg] 의 모든 그림 이름(팩이 늘어놓은 순서, 겹친 이름은 한 번). 아이콘 바꾸기에서 고른다.
     * 팩의 `drawable.xml`(`<item drawable="…"/>`, 묶음 제목 `<category>` 는 거른다)이 없으면 appfilter 의 그림들.
     */
    @Synchronized
    fun names(context: Context, pkg: String): List<String> {
        val stamp = stamp(context, pkg) ?: return emptyList()
        names?.let { (s, n) -> if (s == stamp) return n }
        val list = runCatching { readXml(context, pkg, "drawable", ::parseNames) }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: filter(context, pkg, stamp).drawables
        names = stamp to list
        return list
    }

    internal fun parseNames(parser: XmlPullParser): List<String> {
        val out = LinkedHashSet<String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG || parser.name != "item") continue
            parser.getAttributeValue(null, "drawable")?.takeIf { it.isNotBlank() }?.let { out += it }
        }
        return out.toList()
    }

    /**
     * 그림 이름 찾기: 띄어 쓴 말은 `_` 로 잇고 대소문자를 가리지 않는다. [query] 로 시작하는 이름을 먼저, 그다음 들어 있는 이름(각각 가나다순).
     * 비어 있으면 [all] 그대로.
     */
    internal fun search(all: List<String>, query: String): List<String> {
        val q = query.trim().lowercase().replace(Regex("\\s+"), "_")
        if (q.isEmpty()) return all
        val (starts, contains) = all.filter { q in it.lowercase() }.partition { it.lowercase().startsWith(q) }
        return starts.sortedBy { it.lowercase() } + contains.sortedBy { it.lowercase() }
    }

    /** 고른 팩 [pkg] 의 표를 미리 읽어 둔다(뒤에서). 팩마다 수 MB 라 홈이 처음 그릴 때 멈추지 않게. */
    fun warm(context: Context, pkg: String) {
        val stamp = stamp(context, pkg) ?: return
        runCatching { filter(context, pkg, stamp) }
    }

    @Synchronized
    private fun filter(context: Context, pkg: String, stamp: String): Filter {
        loaded?.let { (s, f) -> if (s == stamp) return f }
        val filter = readXml(context, pkg, "appfilter", ::parse)
        loaded = stamp to filter
        return filter
    }

    /** 팩의 XML [name] 을 [parse] 로 읽는다. 대부분 `res/xml/<name>.xml`, 옛 팩(Arcticons 도)은 `assets/<name>.xml`. */
    private fun <T> readXml(context: Context, pkg: String, name: String, parse: (XmlPullParser) -> T): T {
        val res = context.packageManager.getResourcesForApplication(pkg)
        val id = res.getIdentifier(name, "xml", pkg)
        return if (id != 0) res.getXml(id).use { parse(it) }
        else res.assets.open("$name.xml").use { input ->
            parse(XmlPullParserFactory.newInstance().newPullParser().apply { setInput(input, null) })
        }
    }

    /** `<item component="ComponentInfo{패키지/실행 화면}" drawable="그림"/>` 줄들을 읽는다. 실행 화면이 `.` 으로 시작하면 패키지 안의 이름. */
    internal fun parse(parser: XmlPullParser): Filter {
        val byActivity = HashMap<String, String>()
        val byPackage = HashMap<String, String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG || parser.name != "item") continue
            val drawable = parser.getAttributeValue(null, "drawable")?.takeIf { it.isNotBlank() } ?: continue
            val inside = parser.getAttributeValue(null, "component")
                ?.substringAfter("ComponentInfo{", "")?.substringBefore('}')?.takeIf { '/' in it } ?: continue
            val pkg = inside.substringBefore('/').trim()
            val cls = inside.substringAfter('/').trim().let { if (it.startsWith(".")) pkg + it else it }
            if (pkg.isEmpty() || cls.isEmpty()) continue
            byActivity.putIfAbsent("$pkg/$cls", drawable)
            byPackage.putIfAbsent(pkg, drawable)
        }
        return Filter(byActivity, byPackage)
    }
}
