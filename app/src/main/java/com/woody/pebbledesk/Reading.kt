package com.woody.pebbledesk

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.woody.pebbledesk.Db.rows
import com.woody.pebbledesk.Db.tx
import com.woody.pebbledesk.readers.ReaderApps
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 오늘 읽은 시간과 연속 독서일(오늘까지, 오늘 아직 안 읽었으면 어제까지) */
data class ReadingSummary(val todayMs: Long, val streak: Int)

/**
 * 읽은 시간 한 칸: 이북 앱([pkg]), 그 앱에서 편 책의 제목([title], 그 앱에서 편 책을 몰랐으면 ""), 그 시간이 붙은 책 id([book],
 * 목록에 없던 책이거나 아직 모르면 ""). 책 id 는 한 번 정하면 다시 정하지 않는다(같은 제목의 새 책이 생기거나 제목을 고쳐도 그대로).
 */
data class ReadKey(val pkg: String, val title: String = "", val book: String = "")

/** 날마다 칸([ReadKey])별 읽은 시간(ms) */
typealias ReadingDays = Map<LocalDate, Map<ReadKey, Long>>

fun ReadingDays.total(day: LocalDate): Long = this[day]?.values?.sum() ?: 0

/** 읽은 시간 표시: `1시간 5분` · `42분` · `86시간`(열 시간이 넘으면 시간만) */
fun Context.duration(ms: Long): String {
    val min = ms / 60_000
    return when {
        min < 60 -> getString(R.string.dur_m, min)
        min % 60 == 0L || min >= 600 -> getString(R.string.dur_h, min / 60)
        else -> getString(R.string.dur_hm, min / 60, min % 60)
    }
}

/**
 * 오늘 읽은 시간·연속 독서일. 안드로이드 사용 기록(사용 기록 액세스 권한)에서 이북 앱의 '읽는 화면'이
 * 앞에 떠 있던 시간을 더한다. 어떤 책인지는 접근성 서비스가 그 앱에서 마지막으로 편 책([bookOpened])으로 나누고,
 * 그 앱에서 편 책을 한 번도 몰랐으면 앱으로만 센다(서비스가 꺼진 동안 읽은 시간은 책 없이 시간만, [watchStopped]).
 * 읽는 화면이 따로 액티비티가 아닌 앱(Libby)은 그 액티비티가 앞에 있던 시간 가운데 접근성 서비스가 읽는 화면을 본 동안([viewerShown])만 센다
 * (서비스를 끈 동안은 세지 않는다). 파일 목록과 읽는 화면이 한 액티비티인 KOReader 는 책이 열려 있던 동안만(KOReader 가 남긴 파일로 안다.
 * 모르면 앞에 있던 동안을 다).
 * 사용 기록은 며칠만 남으므로 끝난 날의 합계는 DB([Db]) `reading_day` 에 남겨 둔다(지우지 않는다. 지난해 읽은 책을 보려고).
 */
object ReadingLog {
    /**
     * 읽는 화면(클래스의 마지막 이름). 서재·스토어 화면과 구분한다.
     * 밀리 EPubViewActivity · 교보 ViewerEpubMainActivity · 알라딘 ViewerActivity, ReadONBookRenderActivity ·
     * 리디 EPubReaderActivity · YES24 CremaEPUBActivity · 부커스 EpubActivity 등. 리디의 WebViewActivity 같은 웹 화면은 뺀다.
     * 자동 추가가 받는 앱은 앱 파일에 적은 읽는 화면([ReaderApps.isViewer], 문리더 ActivityTxt 처럼 이 규칙에 안 맞는 것도)으로 센다.
     */
    private val READER = Regex(
        "^(?!.*WebView).*(ViewActivity|ViewerActivity|ReaderActivity|RenderActivity|" +
            "Viewer(Epub|Pdf|Comic)MainActivity|(EPUB|Epub|PDF|CPUB|TXT)Activity)$"
    )

    /** [pkg] 앱의 [cls](액티비티 전체 이름)가 이북 앱의 읽는 화면인가 */
    fun isReaderScreen(context: Context, pkg: String, cls: String) = ReaderApps.isViewer(context, pkg, cls) || READER.matches(cls.substringAfterLast('.'))

    /**
     * 읽은 날로 셀 하루 최소 시간(연속일·목표 없는 날의 링·올해 읽은 책에 넣을지·회차). 이북 앱을 잠깐 열어 확인한 것은 읽은 것으로 보지 않는다.
     * 1.5.3 까지는 1분이었다(바꾼 뒤에는 지난날도 5분으로 다시 센다). 자동 추가(책을 펴면 읽고 있는 책에 넣기)는 시간과 상관없다.
     */
    const val MIN_DAY_MS = 5 * 60_000L

    /** 사용 기록에서 다시 계산할 지난 날 수(그보다 오래된 날은 저장해 둔 값을 쓴다) */
    private const val BACKFILL_DAYS = 7L

    /** 자정 직전에 펼친 책도 세도록 하루 시작보다 조금 앞에서부터 기록을 읽는다. */
    private const val LOOKBACK_MS = 6 * 60 * 60 * 1000L

    /**
     * 화면이 꺼져도 읽는 화면이 앞에 그대로 있다가(멈추지 않고) 이만큼 안에 다시 켜지면 꺼진 동안도 읽은 것으로 센다.
     * Bigme 는 읽는 중에도 몇십 초마다 화면을 끄고(그림은 남는다) 쪽을 넘기면 켠다(이슈 #13).
     */
    private const val DOZE_MS = 5 * 60_000L

    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
        } else mode == AppOpsManager.MODE_ALLOWED
    }

    /** 셀 앱: 알려진 이북 앱과 책에 연결된 앱 */
    fun readerPackages(context: Context): Set<String> =
        AppStore.EBOOK_PACKAGES + BookShelf.list(context).mapNotNull { it.app?.substringBefore('/') }

    /**
     * 시스템의 '사용 기록 액세스' 화면. 목록에서 찾지 않게 이 앱만 보이는 화면(바로 허용)을 열고, 열지 못하면(그 화면이 없는 기기) 목록.
     * Moaan MIX7S 는 목록 화면에서 시스템 설정 앱이 멈추고 이 앱만 보이는 화면은 열린다(사용자 확인).
     */
    fun openAccessSettings(context: Context) {
        runCatching { context.startActivity(appAccessSettingsIntent(context)) }
            .recoverCatching { context.startActivity(accessSettingsIntent()) }
    }

    /** 사용 기록 액세스 목록 화면 */
    fun accessSettingsIntent() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /** 이 앱만 보이는 사용 기록 액세스 화면(같은 동작 + `package:` 주소) */
    fun appAccessSettingsIntent(context: Context): Intent = accessSettingsIntent().setData(Uri.parse("package:${context.packageName}"))

    /**
     * [packages] (이북 앱) 의 날마다 칸([ReadKey])별 읽은 시간. 권한이 없으면 null.
     * 끝난 날은 한 번만 계산해 저장하고, 오늘은 부를 때마다 다시 센다. 책 id 를 모르는 칸은 그때의 목록으로 정해([bookFor]) 저장한다.
     */
    fun days(context: Context, packages: Set<String>): ReadingDays? {
        // 잠금 해제 전에는 사용 기록이 비어 보인다. 그대로 세면 어제를 0분으로 확정해 버리므로 기다린다.
        if (!Storage.isUnlocked(context) || !hasAccess(context)) return null
        noteWatchDown(context)
        // 책을 정할 목록은 잠금 밖에서 읽는다(잠금 순서는 늘 BookShelf → ReadingLog. [adopt] 는 BookShelf 의 저장 안에서 불린다).
        val shelf = BookShelf.list(context)
        val past = BookShelf.history(context)
        return count(context, packages, shelf, past)
    }

    /**
     * 자동 추가가 붙어 있지 않으면(설정에서 꺼짐, 켜져 있어도 시스템이 떼어 놓음, 꺼질 때 알림이 오지 않은 경우도) 내려간 때부터 책 모름
     * (지금이 아니라: 그 사이 시간이 앞 책에 붙지 않게). 다시 붙으면 같은 때로 적으니 겹치지 않는다. 셀 때마다 먼저([days]·[todayCheck]).
     */
    private fun noteWatchDown(context: Context) {
        if (!ReaderWatchService.bound) watchStopped(context, ReaderWatchService.stoppedAt(context))
    }

    @Synchronized
    private fun count(context: Context, packages: Set<String>, shelf: List<ShelfBook>, past: List<PastBook>): ReadingDays {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val loaded = load(context)
        // 기기 날짜가 뒤로 가 오늘이 저장한 마지막 날(through)과 같거나 그 전이면: 오늘·미래가 된 저장 날은 보이지 않게(DB 에는 남는다) 하고,
        // 오늘만 세며 아무것도 저장하지 않는다(저장한 날을 다시 세어 덮으면 기록이 지워진다). 날짜가 돌아오면 그대로 이어진다.
        val back = loaded.through != null && loaded.through >= today
        val saved = if (back) Saved(loaded.through, loaded.since, loaded.days.filterKeys { it < today }) else loaded
        val from = if (back) today else maxOf(saved.through?.plusDays(1) ?: today.minusDays(BACKFILL_DAYS), today.minusDays(BACKFILL_DAYS))
        // 같은 범위를 세던 중이면 지난번 이후의 사용 기록만 이어서 센다(홈으로 돌아올 때마다 하루치를 다시 읽지 않게).
        val c = counter?.takeIf { it.from == from && it.packages == packages && it.zone == zone }
            ?: Counter(from, packages, zone).also { counter = it }
        c.feed(context)
        val raw = c.totals(opens(context), marks(context))
        // 그 책 id 가 처음 붙은 날(저장한 날들과 지금 센 날들에서): 그 전 날의 모르는 시간은 그 책에 붙이지 않는다
        // 저장한 날들 쪽은 저장이 바뀔 때만 다시 센다(홈으로 돌아올 때마다 전체 기록을 훑지 않게). 센 날은 저장한 날 뒤라 겹치지 않는다.
        val firstRead = savedFirstRead(saved.days).toMutableMap()
        raw.firstReadDays().forEach { (book, day) -> firstRead.merge(book, day, ::minOf) }
        val counted = withBooks(raw, shelf, past, firstRead)
        // 기록이 시작된 날(그 전은 '안 읽음'이 아니라 '기록 없음')
        val since = saved.since ?: (saved.days.keys + from).min()
        val days = saved.days + counted
        // 새로 끝난 날(센 날 중 오늘 빼고)만 저장한다.
        if (!back && (saved.through != today.minusDays(1) || saved.since == null)) save(context, Saved(today.minusDays(1), since, days - today), counted - today)
        return days
    }

    /** 저장한 날들([days])의 [firstReadDays] 와 그 날들(같은 사본이면 다시 세지 않는다) */
    private var firstReadCache: Pair<ReadingDays, Map<String, LocalDate>>? = null

    private fun savedFirstRead(days: ReadingDays): Map<String, LocalDate> =
        firstReadCache?.takeIf { it.first === days }?.second ?: days.firstReadDays().also { firstReadCache = days to it }

    /**
     * 책 id 를 모르는 칸([ReadKey.book] "", 제목이 있는 칸만)을 [bookFor] 로 정한다. 같은 칸이 되면 더한다.
     * 정하지 못한 칸(목록에 없던 책)은 비워 두고, 그 책이 생기면 [adopt] 가 채운다.
     */
    internal fun withBooks(days: ReadingDays, shelf: List<ShelfBook>, past: List<PastBook>, firstRead: Map<String, LocalDate> = emptyMap()): ReadingDays =
        days.mapValues { (day, keys) ->
            val out = mutableMapOf<ReadKey, Long>()
            keys.forEach { (k, ms) ->
                // 지운 책은 지운 날까지만 그 책: 지운 뒤에도 편 책 기록에 남은 그 책 id(자동 추가를 끈 채 같은 앱에서 읽음)는 모르는 것으로 다시 정한다
                val gone = past.firstOrNull { it.hidden && it.book.id == k.book && day > leftOn(it) } != null
                val id = if ((k.book.isEmpty() || gone) && k.title.isNotEmpty()) bookFor(k.title, day, shelf, past, firstRead) else null
                val key = if (gone) k.copy(book = id.orEmpty()) else if (id != null) k.copy(book = id) else k
                out[key] = (out[key] ?: 0) + ms
            }
            out
        }

    /**
     * 목록에 없던 책이 책([book])이 됐다(나간 책으로 적어 둠([BookShelf.keep])·자동 추가의 새 책. `책 추가` 로 직접 넣은 책은 아니다, [manual]): 그 제목([title], 앱이 보여 준 제목 [seenTitle])으로
     * 읽었는데 책을 몰라 비워 둔 지난날 칸을 그 책으로 채운다(오늘 칸과 편 책은 셀 때 [bookFor] 가 이 책을 찾는다).
     * BookShelf 의 저장 안에서 부른다(같은 트랜잭션이면 함께 되돌려진다). 메모리 사본은 비워 다음에 DB 에서 다시 읽는다.
     */
    @Synchronized
    fun adopt(context: Context, title: String, seenTitle: String, book: String) {
        val probe = ShelfBook(book, title, "", null, 0, seenTitle = seenTitle)
        val db = Db.get(context)
        val rows = db.rows("SELECT day, pkg, title, ms FROM reading_day WHERE book_id = '' AND title != ''") {
            ReadRow(it.getLong(0), it.getString(1), it.getString(2), it.getLong(3))
        }.filter { probe.hasTitle(it.title) }
        if (rows.isEmpty()) return
        cached = null
        db.tx {
            for (r in rows) {
                delete("reading_day", "day = ? AND pkg = ? AND title = ? AND book_id = ''", arrayOf(r.day.toString(), r.pkg, r.title))
                // 그 책 칸이 이미 있으면 더한다(UPSERT 는 안드로이드 9 의 SQLite 3.22 에 없다)
                val had = rows("SELECT ms FROM reading_day WHERE day = ? AND pkg = ? AND title = ? AND book_id = ?",
                    arrayOf(r.day.toString(), r.pkg, r.title, book)) { it.getLong(0) }.firstOrNull()
                if (had != null) update("reading_day", Db.values("ms" to had + r.ms), "day = ? AND pkg = ? AND title = ? AND book_id = ?",
                    arrayOf(r.day.toString(), r.pkg, r.title, book))
                else insertOrThrow("reading_day", null, Db.values("day" to r.day, "pkg" to r.pkg, "title" to r.title, "book_id" to book, "ms" to r.ms))
            }
        }
    }

    private class ReadRow(val day: Long, val pkg: String, val title: String, val ms: Long)

    /** 메모리 사본을 비워 다음에 DB 에서 다시 읽는다(책 id 가 바뀐 뒤 [BookShelf] 가 DB 를 함께 고쳤을 때, 테스트) */
    @Synchronized
    internal fun clearCache() {
        cached = null; opens = null; marks = null; counter = null
    }

    /** 접근성 서비스가 알아낸 '[at] 에 [pkg] 에서 [title] 을 폈다'(그 책 id [book], 넣지 못한 책이면 ""). 읽는 화면 시간을 그때 편 책으로 센다. */
    internal class Open(val at: Long, val pkg: String, val title: String, val book: String = "")

    /** [pkg] 의 읽는 화면이 [a]~[b](ms)에 앞에 있었다. [hosted] 면 읽는 화면이 보인 동안만 세는 액티비티(Libby 서재, KOReader)라 그 화면이 떠 있던 동안만 센다. */
    internal class Span(val pkg: String, val a: Long, val b: Long, val hosted: Boolean = false)

    /**
     * 읽는 화면이 앞에 있던 구간들([spans])을 날마다 칸([ReadKey])별 시간으로. 구간 안에서 그 앱의 다른 책을 폈으면(편 때 [Open.at]) 거기서 나눠
     * 앞은 그 전에 편 책, 뒤는 새로 편 책으로 센다. 마지막 조각은 구간이 닫힐 때까지 마지막으로 편 책(서재로 돌아와서야 책을 아는 앱도
     * 읽는 화면을 연 때로 적으니 맞게 들어간다). 서재 안에 읽는 화면이 뜨는 앱의 구간([Span.hosted])은 그 화면이 떠 있던 동안([marks])만.
     */
    internal fun totalsOf(spans: List<Span>, opens: List<Open>, zone: ZoneId, marks: List<Mark> = emptyList()): ReadingDays {
        val totals = mutableMapOf<LocalDate, MutableMap<ReadKey, Long>>()
        fun count(pkg: String, a: Long, b: Long, open: Open?) {
            val key = ReadKey(pkg, open?.title.orEmpty(), open?.book.orEmpty())
            var t = a
            while (t < b) {
                val day = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
                val end = minOf(b, day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
                val keys = totals.getOrPut(day) { mutableMapOf() }
                keys[key] = (keys[key] ?: 0) + (end - t)
                t = end
            }
        }
        for (s in spans) for ((a, b) in if (s.hosted) marks.shownWithin(s.pkg, s.a, s.b) else listOf(s.a to s.b)) {
            // [opens] 는 편 때 순(같은 때면 적은 순)이라 조건에 맞는 마지막 줄이 그때의 책(같은 때 '책 모름' 뒤에 적은 책이 이긴다)
            val mine = opens.filter { it.pkg == s.pkg }
            var from = a
            for (cut in mine.map { it.at }.filter { it > a && it < b }.distinct().sorted()) {
                count(s.pkg, from, cut, mine.lastOrNull { it.at < cut })
                from = cut
            }
            // 구간은 [시작, 끝): 끝과 같은 때 편 책은 다음 구간의 것
            count(s.pkg, from, b, mine.lastOrNull { it.at < b })
        }
        return totals
    }

    @Volatile private var opens: List<Open>? = null

    /**
     * DB `book_open`(편 때 순, 같은 때면 적은 순). 비어 있으면(이 기능 전) 접근성 서비스가 기억해 둔 앱마다 마지막에 편 책부터 시작한다.
     * [bookOpened] 와 같은 잠금으로 읽는다(처음 읽는 동안 새로 편 책이 옛 목록에 덮이지 않게).
     */
    @Synchronized
    private fun opens(context: Context): List<Open> = opens ?: Db.get(context).rows("SELECT at, pkg, title, book_id FROM book_open ORDER BY at, rowid") {
        Open(it.getLong(0), it.getString(1), it.getString(2), it.getString(3))
    }.ifEmpty {
        Storage.of(context).getSharedPreferences("reader_watch", Context.MODE_PRIVATE).all.mapNotNull { (k, v) ->
            if (k.startsWith("opened_") && v is String) Open(0, k.removePrefix("opened_"), v, "") else null
        }
    }.also { opens = it }

    /**
     * [pkg] 에서 [title] 을 폈다([at] 부터 읽은 시간은 이 책. 그 책 id [book], 목록에 넣지 못했으면 ""). 다시 셀 수 있는 날([BACKFILL_DAYS]) 남짓만 남기고,
     * 그보다 오래됐어도 앱마다 마지막 책은 남긴다(이북 앱은 마지막 책을 바로 다시 연다).
     */
    @Synchronized
    fun bookOpened(context: Context, pkg: String, title: String, book: String, at: Long = System.currentTimeMillis()) {
        val list = opens(context)
        // [at] 에 쓰이는 줄과 같으면 적지 않는다(기기 날짜가 뒤로 가 더 뒤 때의 줄이 있어도 그 줄은 아직 쓰이지 않는다)
        val last = list.lastOrNull { it.pkg == pkg && it.at <= at }
        if (last?.title == title && last.book == book) return
        keepOpens(context, list + Open(at, pkg, title, book), at)
    }

    /** 편 책 기록의 마지막 때(없으면 0). 자동 추가 서비스가 살아 있던 때다([ReaderWatchService.downSince]). */
    @Synchronized
    fun lastOpenAt(context: Context): Long = opens(context).maxOfOrNull { it.at } ?: 0L

    /**
     * [pkg] 에서 지금 읽는 책으로 적힌 책 id: 편 책 기록에서 지금 쓰이는 줄의 책. '책 모름' 줄(꺼짐·강제 종료·제목 없는 칸)이 적힌 뒤나 책 id 를 모르는
     * 옛 줄(1.5.3 까지)이면 null. 자동 추가는 메뉴 제목이 이 책의 것이면 그대로 두고(같은 제목의 책이 여럿이어도), 아니면 다시 적어 그 뒤 시간이 그 책에 붙게 한다.
     */
    @Synchronized
    fun openBook(context: Context, pkg: String, at: Long = System.currentTimeMillis()): String? =
        opens(context).lastOrNull { it.pkg == pkg && it.at <= at }?.takeIf { it.title.isNotEmpty() }?.book?.ifEmpty { null }

    /**
     * 읽는 책 자동 추가(접근성 서비스)가 꺼졌다(꺼질 때 [ReaderWatchService] 가, 알림 없이 꺼졌으면 셀 때 [days] 가 부른다):
     * 이 뒤로 이북 앱에서 읽은 시간은 어느 책인지 모른다(꺼진 동안 편 책을 알 수 없다).
     * 앱마다 '책 모름'(제목 없는 편 책)을 적어 두어 그 시간은 하루 합계·연속일에만 들어가고 책에는 붙지 않게 한다
     * (그러지 않으면 꺼지기 전에 마지막으로 편 책에 붙는다). 다시 켜고 책을 펴면 그 책이 적혀 그때부터 다시 책에 붙는다.
     * 표시로 읽은 시간을 자르는 앱도 그때부터 지켜보지 않을 때의 상태로 둔다([ReaderApps.unwatchedShown]: 서비스가 끝날 때의 [WatchCore] 와 같게.
     * 끝나는 알림 없이 내려갔으면(업데이트·앱 오류) 마지막 표시가 남아, KOReader 의 책을 닫은 표시가 남으면 그 뒤 읽은 시간을 하나도 세지 않았다).
     */
    @Synchronized
    fun watchStopped(context: Context, at: Long = System.currentTimeMillis()) {
        val list = opens(context)
        val unknown = list.groupBy { it.pkg }.mapNotNull { (pkg, mine) ->
            if (mine.lastOrNull { it.at <= at }?.title?.isEmpty() == true) null else Open(at, pkg, "")
        }
        if (unknown.isNotEmpty()) keepOpens(context, list + unknown, at)
        ReaderApps.unwatchedShown.forEach { (pkg, shown) -> viewerShown(context, pkg, shown, at) }
    }

    /** 편 책 기록을 저장한다: 다시 셀 수 있는 날 남짓만 남기고(앱마다 마지막 것은 남긴다) DB 에 들어간 뒤에만 메모리 사본을 바꾼다. */
    private fun keepOpens(context: Context, all: List<Open>, at: Long) {
        val old = at - (BACKFILL_DAYS + 3) * 24 * 60 * 60 * 1000L
        // 편 때 순으로(같은 때면 적은 순 그대로, sortedBy 는 안정 정렬) 다시 적는다
        val kept = all.sortedBy { it.at }.groupBy { it.pkg }.values.flatMap { mine ->
            val last = mine.last()
            mine.filter { it.at >= old || it === last }
        }.sortedBy { it.at }
        // DB 에 들어간 뒤에만 메모리 사본을 바꾼다(실패하면 비워 다음에 DB 에서 다시 읽는다. 앱을 다시 띄워도 같은 책으로 세게)
        opens = runCatching {
            Db.get(context).tx {
                delete("book_open", null, null)
                kept.forEach { insertOrThrow("book_open", null, Db.values("at" to it.at, "pkg" to it.pkg, "title" to it.title, "book_id" to it.book)) }
            }
            kept
        }.getOrNull()
    }


    /**
     * 저장해 둔 날마다 기록(끝난 날, 사용 기록 권한이 없거나 잠금 해제 전이어도). 권한을 끈 동안에도 지난 기록으로 회차를 판단하고
     * 책 기록을 보이게([BookRecord.all]).
     */
    @Synchronized
    fun savedDays(context: Context): ReadingDays {
        // 끝난 날만(오늘은 사용 기록으로 세는 날이라 저장하지 않는다. 기기 날짜가 뒤로 가 오늘·미래가 된 줄도 빼서 다시 보이지 않게)
        val today = LocalDate.now()
        return load(context).days.filterKeys { it < today }
    }

    /**
     * 기록 보기의 오늘 읽은 시간 확인: 이북 앱마다 (오늘 읽는 화면이 앞에 있던 시간, 그 가운데 읽은 시간으로 센 것)(ms, 앞에 있던 시간이 있는 앱만).
     * 홈과 같은 셈([Counter]·[totalsOf])을 오늘만 새로 한다(표시로 자르는 앱은 둘이 다르다). 권한이 없거나 잠금 해제 전이면 null.
     */
    fun todayCheck(context: Context, packages: Set<String>): Map<String, Pair<Long, Long>>? {
        if (!Storage.isUnlocked(context) || !hasAccess(context)) return null
        noteWatchDown(context)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val c = Counter(today, packages, zone).also { it.feed(context) }
        val counted = c.totals(opens(context), marks(context))[today].orEmpty()
        return c.front(today).mapValues { (pkg, ms) -> ms to counted.filterKeys { it.pkg == pkg }.values.sum() }
    }

    /** [since] 뒤에 적은 읽는 화면 열림·닫힘([viewerShown])과 앱마다 그 앞의 마지막 것([since] 때의 상태), 때 순(기록 보기) */
    internal fun marksFrom(context: Context, since: Long): List<Mark> {
        val all = marks(context)
        val before = all.filter { it.at < since }.groupBy { it.pkg }.values.map { it.last() }
        return (before + all.filter { it.at >= since }).sortedBy { it.at }
    }

    /** 접근성 서비스가 본 '[at] 에 [pkg] 의 서재 안에 읽는 화면이 떴다([shown])·닫혔다' */
    internal class Mark(val at: Long, val pkg: String, val shown: Boolean)

    @Volatile private var marks: List<Mark>? = null

    /** DB `viewer_mark`(때 순, 같은 때면 적은 순: 같은 때 열림 → 닫힘이면 닫힘) */
    @Synchronized
    private fun marks(context: Context): List<Mark> = marks ?: Db.get(context).rows("SELECT at, pkg, shown FROM viewer_mark ORDER BY at, rowid") {
        Mark(it.getLong(0), it.getString(1), it.getInt(2) != 0)
    }.also { marks = it }

    /**
     * 읽는 화면이 보인 동안만 세는 앱([ReaderApps.hostsViewer]: Libby 서재 안의 읽는 화면, KOReader 의 책)에서 그 화면이 [at] 에 떴다([shown])·닫혔다.
     * 그 액티비티가 앞에 있던 시간 가운데 떠 있던 동안만 읽은 시간으로 센다. 앞서 적은 것과 같으면 적지 않는다.
     * 앞서 적은 것보다 앞의 때면: 지금 상태([current])는 앞서 적은 때부터로 적고, 지난 일(KOReader 의 앞 책을 닫은 때 등)은 적지 않는다.
     * [bookOpened] 처럼 다시 셀 수 있는 날 남짓만 남기고, 앱마다 마지막 것은 남긴다.
     */
    @Synchronized
    fun viewerShown(context: Context, pkg: String, shown: Boolean, at: Long = System.currentTimeMillis(), current: Boolean = true) {
        val list = marks(context)
        val last = list.lastOrNull { it.pkg == pkg }
        if ((last?.shown ?: ReaderApps.shownByDefault(pkg)) == shown) return
        val time = if (last != null && at < last.at) { if (!current) return; last.at } else at
        // 같은 때·같은 상태를 이미 적었으면(KOReader 를 맞출 때 지난번의 마지막 초를 다시 본다) 다시 적지 않는다
        if (list.any { it.pkg == pkg && it.at == time && it.shown == shown }) return
        val kept = keepRecent(list + Mark(time, pkg, shown), time, { it.pkg }, { it.at })
        marks = kept
        runCatching {
            Db.get(context).tx {
                delete("viewer_mark", null, null)
                kept.forEach { insertOrThrow("viewer_mark", null, Db.values("at" to it.at, "pkg" to it.pkg, "shown" to it.shown)) }
            }
        }
    }

    /** [now] 기준 다시 셀 수 있는 날([BACKFILL_DAYS]) 남짓 것만 남기되 앱마다 마지막 것은 남긴다(때 순) */
    private fun <T> keepRecent(list: List<T>, now: Long, pkg: (T) -> String, at: (T) -> Long): List<T> {
        val old = now - (BACKFILL_DAYS + 3) * 24 * 60 * 60 * 1000L
        return list.groupBy(pkg).values.flatMap { mine ->
            val last = mine.maxBy(at)
            mine.filter { at(it) >= old || it === last }
        }.sortedBy(at)
    }

    /** [pkg] 의 [a]~[b] 가운데 읽는 화면이 떠 있던 구간들([viewerShown]) */
    internal fun List<Mark>.shownWithin(pkg: String, a: Long, b: Long): List<Pair<Long, Long>> {
        val mine = filter { it.pkg == pkg }
        var on = mine.lastOrNull { it.at <= a }?.shown ?: ReaderApps.shownByDefault(pkg)
        var from = a
        val out = mutableListOf<Pair<Long, Long>>()
        for (m in mine) {
            if (m.at <= a || m.at >= b || m.shown == on) continue
            if (on) out += from to m.at else from = m.at
            on = m.shown
        }
        if (on) out += from to b
        return out
    }

    /** 기록이 시작된 날. 그 전 날은 읽었는지 알 수 없다. 아직 기록이 없으면 null. */
    fun since(context: Context): LocalDate? = load(context).since?.let { minOf(it, LocalDate.now()) }  // 기기 날짜가 뒤로 가면 오늘까지(오늘이 범위 밖이 되지 않게)

    /** 홈 하단: 오늘 읽은 시간·연속일. 통계에서 뺀 카테고리의 책은 빼고 센다(독서 기록의 `전체`와 같게, [StatsFilter]). */
    fun summary(context: Context, packages: Set<String>): ReadingSummary? =
        days(context, packages)?.let(StatsFilter.of(context)::days)?.let { ReadingSummary(it.total(LocalDate.now()), streak(it)) }

    /** 하루 [MIN_DAY_MS] 이상 읽은 날이 이어진 수. 오늘 아직 안 읽었으면 어제까지. */
    fun streak(days: ReadingDays, today: LocalDate = LocalDate.now()): Int {
        var streak = 0
        var day = if (days.total(today) >= MIN_DAY_MS) today else today.minusDays(1)
        while (days.total(day) >= MIN_DAY_MS) { streak++; day = day.minusDays(1) }
        return streak
    }

    /** 가장 길었던 연속일 */
    fun longestStreak(days: ReadingDays): Int {
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (day in days.keys.filter { days.total(it) >= MIN_DAY_MS }.sorted()) {
            run = if (prev != null && day == prev.plusDays(1)) run + 1 else 1
            best = maxOf(best, run)
            prev = day
        }
        return best
    }

    @Volatile private var counter: Counter? = null

    /**
     * [from] 부터 읽는 화면이 앞에 떠 있던 구간. [feed] 를 부를 때마다 지난번 이후의 사용 기록만 읽어
     * 이어서 센다(읽는 화면이 열려 있던 상태도 이어 간다). 어느 책인지는 [totals] 에서 그때까지 알아낸 편 책으로 나눈다
     * (알라딘처럼 서재로 돌아와서야 책을 아는 앱도 나중에 맞게 세도록).
     */
    private class Counter(val from: LocalDate, val packages: Set<String>, val zone: ZoneId) {
        private val start = from.atStartOfDay(zone).toInstant().toEpochMilli()
        private val spans = mutableListOf<Span>()
        /** 여기까지 읽었다. 처음에는 자정 직전에 펼친 책도 세도록 조금 앞에서부터. */
        private var until = start - LOOKBACK_MS
        private var openAt = -1L
        private var openPkg = ""
        private var openClass: String? = null
        private var openHosted = false
        /** 화면이 꺼지거나 잠겨 멈춘 읽는 화면([openClass])이 아직 앞에 있으면 멈춘 때, 아니면 -1 */
        private var dozeAt = -1L
        /** 멈춘 동안도 셀지(잠금 화면이 뜨지 않았을 때만) */
        private var dozeFill = false
        private var screenOn = true
        private var locked = false

        private fun add(pkg: String, a: Long, b: Long) {
            if (b > start) spans += Span(pkg, maxOf(a, start), b, openHosted)
        }

        /** 화면이 꺼지거나 잠기면 세기를 멈추고, 읽는 화면이 앞에 있던 채 다시 켜지면([wake]) 잇는다. */
        private fun doze(at: Long, fill: Boolean) {
            if (openAt >= 0) { add(openPkg, openAt, at); openAt = -1; dozeAt = at; dozeFill = true }
            if (!fill) dozeFill = false
        }

        private fun wake(at: Long) {
            if (dozeAt < 0 || !screenOn || locked) return
            openAt = if (dozeFill && at - dozeAt <= DOZE_MS) dozeAt else at
            dozeAt = -1
        }

        /** 날마다 칸([ReadKey])별 시간([totalsOf]) */
        @Synchronized
        fun totals(opens: List<Open>, marks: List<Mark>): ReadingDays = totalsOf(spans, opens, zone, marks)

        /** [day] 에 앱마다 읽는 화면이 앞에 있던 시간(표시로 자르지 않고, [todayCheck]) */
        @Synchronized
        fun front(day: LocalDate): Map<String, Long> {
            val a = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val b = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            return spans.groupBy { it.pkg }.mapValues { (_, mine) -> mine.sumOf { maxOf(0L, minOf(it.b, b) - maxOf(it.a, a)) } }.filterValues { it > 0 }
        }

        @Synchronized
        fun feed(context: Context) {
            val now = System.currentTimeMillis()
            val events = context.getSystemService(UsageStatsManager::class.java)?.queryEvents(until, now) ?: return
            until = now
            val e = UsageEvents.Event()
            while (events.getNextEvent(e)) {
                when (e.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        if (openAt >= 0) add(openPkg, openAt, e.timeStamp)
                        dozeAt = -1
                        val cls = e.className
                        val hosted = cls != null && ReaderApps.hostsViewer(cls)
                        val reading = e.packageName in packages && cls != null && (isReaderScreen(context, e.packageName, cls) || hosted)
                        openAt = if (reading) e.timeStamp else -1
                        openPkg = e.packageName
                        openClass = if (reading) cls else null
                        openHosted = reading && hosted
                    }
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> if (openClass != null && e.className == openClass) {
                        if (openAt >= 0) add(openPkg, openAt, e.timeStamp)
                        openAt = -1; dozeAt = -1; openClass = null
                    }
                    // 화면이 꺼지면 읽기도 멈춘 것으로 본다. 읽는 화면이 그대로 앞에 있던 채 켜지면 이어 센다(보통은 꺼질 때 화면이 내려간다).
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> { screenOn = false; doze(e.timeStamp, fill = true) }
                    UsageEvents.Event.KEYGUARD_SHOWN -> { locked = true; doze(e.timeStamp, fill = false) }
                    UsageEvents.Event.SCREEN_INTERACTIVE -> { screenOn = true; wake(e.timeStamp) }
                    UsageEvents.Event.KEYGUARD_HIDDEN -> { locked = false; wake(e.timeStamp) }
                }
            }
            // 아직 열려 있는 읽는 화면은 닫힐 때 센다(홈에서 부르므로 보통은 이미 닫혀 있다).
        }
    }

    private class Saved(val through: LocalDate?, val since: LocalDate?, val days: ReadingDays)

    /** 메모리 사본(돌아올 때마다 DB를 읽지 않도록). 쓸 때 함께 바꾼다. */
    @Volatile private var cached: Saved? = null

    private fun load(context: Context): Saved = cached ?: read(context).also { cached = it }

    /** DB `reading_day`(날·책·ms)와 `reading_meta`(through·since) */
    private fun read(context: Context): Saved {
        val db = Db.get(context)
        val days = mutableMapOf<LocalDate, MutableMap<ReadKey, Long>>()
        db.rows("SELECT day, pkg, title, book_id, ms FROM reading_day") { c ->
            days.getOrPut(LocalDate.ofEpochDay(c.getLong(0))) { mutableMapOf() }[ReadKey(c.getString(1), c.getString(2), c.getString(3))] = c.getLong(4)
        }
        val meta = db.rows("SELECT name, value FROM reading_meta") { it.getString(0) to it.getString(1) }.toMap()
        fun date(name: String) = meta[name]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return Saved(date("through"), date("since"), days)
    }

    /**
     * [changed] 날들(그날의 칸별 시간을 통째로)과 through·since 를 한 트랜잭션으로 저장하고, 저장했으면 [saved] 를 메모리 사본으로 둔다
     * (실패하면 사본을 비워 다음에 DB 에서 다시 읽는다. 저장하지 못한 날은 그때 다시 센다). 날마다 기록은 지우지 않는다.
     */
    private fun save(context: Context, saved: Saved, changed: ReadingDays) {
        cached = runCatching {
            Db.get(context).tx {
                changed.forEach { (day, keys) ->
                    val d = day.toEpochDay()
                    delete("reading_day", "day = ?", arrayOf(d.toString()))
                    keys.forEach { (k, ms) ->
                        insertOrThrow("reading_day", null, Db.values("day" to d, "pkg" to k.pkg, "title" to k.title, "book_id" to k.book, "ms" to ms))
                    }
                }
                for ((name, value) in listOf("through" to saved.through, "since" to saved.since)) {
                    replaceOrThrow("reading_meta", null, Db.values("name" to name, "value" to value?.toString()))
                }
            }
            saved
        }.getOrNull()
    }
}
