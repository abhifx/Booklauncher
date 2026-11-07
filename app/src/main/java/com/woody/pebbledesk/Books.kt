package com.woody.pebbledesk

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.util.LruCache
import com.woody.pebbledesk.Db.rows
import com.woody.pebbledesk.Db.tx
import com.woody.pebbledesk.readers.BookFiles
import com.woody.pebbledesk.readers.ReaderApps
import com.woody.pebbledesk.readers.isImageFile
import com.woody.pebbledesk.readers.withoutBookFile
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.io.IOException
import java.util.UUID
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/** 표지 검색 결과 한 권. [id] 는 검색처 안에서 같은 책을 가르는 것(YES24 상품 번호, Apple `a<번호>`, Open Library `o<표지 번호>`) */
data class BookResult(
    val id: String,
    val title: String,
    val author: String,
    val thumbUrl: String,
    val coverUrl: String,
)

/**
 * 책 표지 검색. 한국어 책(앱 언어가 한국어이거나 검색어에 한글이 있음)은 YES24 검색 페이지에서 찾는다(크레마와 같은 YES24 표지라 eBook 표지와도
 * 대부분 일치한다). 그 밖은 Apple Books(기기 지역의 스토어, 결과가 한 쪽이 안 되면 미국 스토어도)에서 찾고, 그래도 한 쪽이 안 되면
 * Open Library 를 뒤에 붙인다(원서는 YES24 에 한국어판·음반이 섞이고 덜 알려진 책은 없다). Google Books 는 키 없이 부르면 하루 한도로
 * 실패해 쓰지 않는다. 앱 언어가 한국어인지는 부르는 쪽이 준다([search] 의 `korean`).
 */
object BookSearch {
    // YES24 는 오래된 Chrome UA 를 메인 페이지로 리다이렉트하므로 Safari UA 를 쓴다.
    private const val USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"
    private val EXCLUDED_TYPES = setOf("[중고도서]", "[Blu-ray]", "[DVD]", "[음반]", "[LP]", "[GIFT]")
    private val GOODS_ID = Regex("""/goods/(\d+)""")
    // 검색 결과 한 권(`<div class="itemUnit">`)에서 읽는 것. HTML 라이브러리(jsoup)는 앱 크기의 절반이라 쓰지 않는다.
    private val TYPE = Regex("""<span class="gd_res">(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
    private val NAME = Regex("""(<a\b[^>]*\bclass="gd_name"[^>]*>)(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
    private val HREF = Regex("""href="([^"]*)"""")
    private val AUTHORS = Regex("""<span[^>]*\binfo_auth\b[^>]*>(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
    private val LINK = Regex("""<a\b[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
    /** Apple 표지 주소의 크기(`100x100bb`, 비율을 지키는 상자) */
    private val APPLE_SIZE = Regex("""\d+x\d+bb""")
    /** 이보다 적으면(검색 화면 한 쪽, 4열 × 2줄) 다음 검색처를 뒤에 붙인다 */
    private const val FEW = 8

    /** [korean] 은 앱 언어가 한국어인가. 네트워크를 쓰므로 백그라운드에서 부른다. */
    @Throws(IOException::class)
    fun search(query: String, korean: Boolean): List<BookResult> {
        if (korean || hasHangul(query)) return yes24(query)
        val found = mutableListOf<BookResult>()
        var error: Throwable? = null
        fun add(from: () -> List<BookResult>) {
            if (found.size >= FEW) return
            runCatching(from).onSuccess { found += it }.onFailure { if (error == null) error = it }
        }
        // Apple Books 가 없는 나라(한국 등)의 스토어는 eBook 이 0권이라 미국 스토어를 뒤에 본다. 같은 서버라 연결 문제로 실패했으면 건너뛴다
        // (잘못된 지역 코드의 HTTP 400 이면 본다).
        val country = country()
        add { apple(query, country) }
        if (country != "us" && !isConnectionError(error)) add { apple(query, "us") }
        add { openLibrary(query) }
        if (found.isEmpty()) error?.let { throw it }
        return found.distinctBy { it.id }
    }

    /** 연결 문제(주소 못 찾음·연결 실패·시간 초과)인가. 검색 화면의 와이파이 확인 안내도 이것으로 가른다 */
    internal fun isConnectionError(e: Throwable?) = e is UnknownHostException || e is ConnectException || e is SocketTimeoutException

    internal fun hasHangul(text: String) = text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HANGUL }

    /** 기기 지역(앱이 화면마다 덮어쓴 언어가 아니라 기기 설정의 것). 모르면 미국 */
    private fun country(): String =
        android.content.res.Resources.getSystem().configuration.locales[0].country.lowercase().ifEmpty { "us" }

    @Throws(IOException::class)
    private fun yes24(query: String): List<BookResult> {
        val url = "https://www.yes24.com/Product/Search?domain=ALL&query=" + URLEncoder.encode(query, "UTF-8")
        val html = String(get(url, readTimeout = 15_000) { conn ->
            // 검색 페이지가 아닌 곳(메인)으로 넘겨졌으면 결과가 아니다.
            if (!conn.url.toString().contains("/Search", ignoreCase = true)) throw IOException("redirected")
        }, Charsets.UTF_8)
        return html.split("class=\"itemUnit\"").drop(1).mapNotNull { item ->
            val type = TYPE.find(item)?.groupValues?.get(1)?.let(::plain)
            if (type != null && type in EXCLUDED_TYPES) return@mapNotNull null
            val name = NAME.find(item) ?: return@mapNotNull null
            val href = HREF.find(name.groupValues[1])?.groupValues?.get(1) ?: return@mapNotNull null
            val id = GOODS_ID.find(href)?.groupValues?.get(1) ?: return@mapNotNull null
            val authors = AUTHORS.find(item)?.groupValues?.get(1).orEmpty()
            BookResult(
                id = id,
                title = plain(name.groupValues[2]),
                author = LINK.findAll(authors).joinToString(", ") { plain(it.groupValues[1]) },
                thumbUrl = "https://image.yes24.com/goods/$id/L",
                coverUrl = "https://image.yes24.com/goods/$id/XL",
            )
        }.distinctBy { it.id }
    }

    /** iTunes Search API 의 eBook(키 없음, 분당 약 20번). 표지 주소의 크기를 바꿔 큰 표지(긴 변 1000)를 받는다. */
    @Throws(IOException::class)
    private fun apple(query: String, country: String): List<BookResult> {
        val url = "https://itunes.apple.com/search?media=ebook&entity=ebook&limit=24&country=$country&term=" + URLEncoder.encode(query, "UTF-8")
        return parseApple(String(get(url, readTimeout = 15_000), Charsets.UTF_8))
    }

    internal fun parseApple(json: String): List<BookResult> {
        val items = JSONObject(json).optJSONArray("results") ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val item = items.getJSONObject(i)
            val art = item.optString("artworkUrl100").takeIf { APPLE_SIZE.containsMatchIn(it) } ?: return@mapNotNull null
            val title = item.optString("trackName").trim().ifEmpty { return@mapNotNull null }
            BookResult(
                id = "a" + (item.optLong("trackId").takeIf { it > 0 } ?: return@mapNotNull null),
                title = title,
                author = item.optString("artistName").trim(),
                thumbUrl = art.replace(APPLE_SIZE, "400x400bb"),
                coverUrl = art.replace(APPLE_SIZE, "1000x1000bb"),
            )
        }.distinctBy { it.id }
    }

    /** Open Library 검색(키 없음). 표지 없는 결과는 뺀다. 큰 표지(`-L`, 검색 칸에도 이것. `-M` 은 폭 180 이라 칸보다 작다)도 폭이 300 남짓이라 Apple 뒤에 붙인다. */
    @Throws(IOException::class)
    private fun openLibrary(query: String): List<BookResult> {
        val url = "https://openlibrary.org/search.json?fields=title,author_name,cover_i&limit=24&q=" + URLEncoder.encode(query, "UTF-8")
        return parseOpenLibrary(String(get(url, readTimeout = 20_000), Charsets.UTF_8), query)
    }

    /**
     * Open Library 의 검색어(`q`)는 느슨하게 맞춰 상관없는 책(셰익스피어 등)이 뒤에 붙으므로, 검색어의 낱말이 제목·저자에 모두 든 것만 둔다
     * (제목 검색 `title` 은 `표지 다시 찾기` 처럼 저자를 붙인 검색어를 못 찾는다).
     */
    internal fun parseOpenLibrary(json: String, query: String): List<BookResult> {
        val words = query.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }
        val docs = JSONObject(json).optJSONArray("docs") ?: return emptyList()
        return (0 until docs.length()).mapNotNull { i ->
            val doc = docs.getJSONObject(i)
            val cover = doc.optLong("cover_i").takeIf { it > 0 } ?: return@mapNotNull null
            val title = doc.optString("title").trim().ifEmpty { return@mapNotNull null }
            val authors = doc.optJSONArray("author_name")
            val author = (0 until (authors?.length() ?: 0)).joinToString(", ") { authors!!.getString(it) }
            val text = "$title $author".lowercase()
            if (words.any { it !in text }) return@mapNotNull null
            BookResult(
                id = "o$cover",
                title = title,
                author = author,
                thumbUrl = "https://covers.openlibrary.org/b/id/$cover-L.jpg",
                coverUrl = "https://covers.openlibrary.org/b/id/$cover-L.jpg",
            )
        }.distinctBy { it.id }
    }

    /** HTML 조각의 글자만(태그를 빼고 `&#39;` 같은 것을 풀어서) */
    private fun plain(html: String) = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim()

    /** 네트워크를 쓰므로 백그라운드에서 부른다. */
    @Throws(IOException::class)
    fun download(url: String): ByteArray =
        get(url, readTimeout = 20_000, referer = "https://www.yes24.com/".takeIf { URL(url).host.endsWith("yes24.com") })

    /** [url] 을 받아 온다(200 이 아니거나 [verify] 가 던지면 IOException). */
    @Throws(IOException::class)
    private fun get(url: String, readTimeout: Int, referer: String? = null, verify: (HttpURLConnection) -> Unit = {}): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = readTimeout
        conn.setRequestProperty("User-Agent", USER_AGENT)
        referer?.let { conn.setRequestProperty("Referer", it) }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
            verify(conn)
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    /** 폭 [widthPx] 에 맞춰 줄이고 [gray] 면 흑백으로 바꾼다. 디코딩하지 못하면 null. */
    fun scaledCover(bytes: ByteArray, widthPx: Int, gray: Boolean): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= widthPx) sample *= 2
        val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val h = (src.height.toLong() * widthPx / src.width).toInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, null, Rect(0, 0, widthPx, h), if (gray) Ui.grayPaint() else Paint(Paint.FILTER_BITMAP_FLAG))
        src.recycle()
        return out
    }
}

/** 네트워크·파일 작업용 백그라운드 스레드와 화면 스레드로 돌려주기 */
object Bg {
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    fun <T> run(work: () -> T, done: (Result<T>) -> Unit) {
        pool.execute {
            val result = runCatching(work)
            main.post { done(result) }
        }
    }
}

/**
 * 읽고 있는 책 한 권: 표지는 앱 전용 저장소의 `books/<id>.jpg`, [app] 은 그 책을 읽는 앱(없으면 null).
 * [id] 는 넣을 때 런처가 만든다(`b-` + UUID, `책 추가` 로 직접 넣은 책은 `m-` + UUID, [manual]). 같은 책을 두 번 넣어도 따로 다룬다(합치지 않는다).
 */
data class ShelfBook(
    val id: String,
    val title: String,
    val author: String,
    val app: String?,
    val updatedAt: Long,
    /** 이북 앱 서재에서 읽어 온 진행률(%). 모르면 -1 */
    val progress: Int = -1,
    /** 교보도서관 반납일(epochDay). 모르면 -1 */
    val due: Long = -1,
    /** 자동으로 넣을 때 이북 앱이 보여 준 제목. 사용자가 제목을 고쳐도 다음에 펼 때 이 책으로 찾는다. 직접 넣은 책은 "" */
    val seenTitle: String = "",
    /** 이북 앱이 보여 준 읽는 화(Mihon 의 화 이름 `Ch. 12` 등, 앱이 쓴 그대로). 모르면 "". 나간 책에는 남기지 않는다 */
    val chapter: String = "",
)

/**
 * 읽고 있는 책 목록에서 나간 책(완독했거나 뺀 책). 올해 읽은 책 목록에 보인다. 완독은 따로 [BookShelf.finishList].
 * 표지는 읽던 때 그대로 `books/<id>.jpg`. [hidden] 은 올해 읽은 책에서 지운 책(읽은 시간이 남아 있어도 다시 보이지 않게),
 * [timeDropped] 는 지울 때 읽은 시간까지 지운 책(그 책의 시간을 하루 합계·연속일에서도 뺀다, [ReadingLog.days]).
 */
data class PastBook(val book: ShelfBook, val leftAt: Long, val hidden: Boolean = false, val timeDropped: Boolean = false)

/**
 * 홈·목록·상세에 붙이는 읽는 화 또는 진행률, 반납일 `[Ch. 12, 반납 4일 남음]`·`[47% 읽음]`. 읽는 화(만화 앱 메뉴의 화 이름)를 알면 % 대신 그것만
 * (Futon 은 작품 화면의 % 도 들어오지만 읽는 화가 더 알기 쉽다, 사용자가 정함). 모르는 값은 뺀다(홈은 한 줄씩, 목록은 이어서).
 * [withPercent] 가 false 면 진행률은 빼고(홈 맨 앞 책은 막대와 함께 따로 그린다, [percentShown]) 읽는 화는 그대로.
 */
fun Context.bookStatus(book: ShelfBook, today: LocalDate = LocalDate.now(), withPercent: Boolean = true): List<String> {
    val parts = mutableListOf<String>()
    if (book.chapter.isNotEmpty()) parts += book.chapter
    else if (withPercent && book.progress >= 0) parts += getString(R.string.progress_pct, book.progress)
    if (book.due >= 0) {
        val left = book.due - today.toEpochDay()
        parts += when {
            left > 0 -> getString(R.string.due_days, left.toInt())
            left == 0L -> getString(R.string.due_today)
            else -> getString(R.string.due_over)
        }
    }
    return parts
}

/** [bookStatus] 가 진행률(%)을 보이는가: 진행률을 알고 읽는 화가 없을 때 */
val ShelfBook.percentShown: Boolean get() = chapter.isEmpty() && progress >= 0

/**
 * 제목 맞추기용: 부제(`:` 뒤)·공백·기호를 빼고 소문자로. 이북 앱 서재는 부제를 빼고 보여 주기도 한다.
 * `+`·`#` 은 제목을 가르니 남기고(『C++』≠『C#』), 기호만 있는 제목은 그대로(앞뒤 공백만 빼고) 비교한다. `:` 앞이 비어 있으면 부제로 나누지 않는다.
 */
fun normTitle(title: String): String {
    // `:` 앞이 비어 있으면(`: 부제`, `::`) 부제로 나누지 않는다
    val head = title.substringBefore(':').ifBlank { title }
    return head.filter { it.isLetterOrDigit() || it == '+' || it == '#' }.lowercase().ifEmpty { head.trim() }
}

/**
 * 같은 책인가: 정리한 제목([normTitle], 부제·공백·기호·대소문자 무시)이 전부 같다. 앞부분만 같은 제목(『데미안』과 『데미안 2』)은 다른 책
 * (1.5.3 까지는 앞부분이 같아도 같은 책으로 봤다. 읽은 시간의 책은 한 번 정하면 바뀌지 않아 속편이 앞 책에 붙지 않게).
 * 부제는 한쪽에만 있으면 무시하고(서재가 부제를 빼고 보여 주는 앱), 둘 다 있는데 다르면 다른 책이다(『해리포터: 1권』과 『해리포터: 2권』. 1.7.8 까지는 같은 책).
 */
fun sameTitle(a: String, b: String): Boolean {
    if (normTitle(a) != normTitle(b)) return false
    val sa = subtitleKey(a)
    val sb = subtitleKey(b)
    return sa.isEmpty() || sb.isEmpty() || sa == sb
}

/** 부제(`:` 뒤)를 [normTitle] 처럼 정리한 것. 부제가 없으면(`:` 앞이 비어 부제로 나누지 않는 제목도) "" */
private fun subtitleKey(title: String): String {
    if (title.substringBefore(':').isBlank()) return ""
    return title.substringAfter(':', "").filter { it.isLetterOrDigit() || it == '+' || it == '#' }.lowercase()
}

/**
 * `책 추가` 로 직접 넣은 책(지원하지 않는 이북 앱용). 이북 앱이 보여 주는 제목으로 찾지 않는다([hasTitle]): 지원하는 앱에서 펴면 자동 추가가 따로 넣고,
 * 읽은 시간·진행률도 그 책에 붙는다(1.5.3 까지 넣은 책은 `b-` 라 예전처럼 제목으로 찾는다).
 */
val ShelfBook.manual: Boolean get() = id.startsWith(BookShelf.MANUAL_PREFIX)

/**
 * 이 책을 [title] 로 찾을 수 있는가: 지금 제목이 [sameTitle] 이거나, 앱이 보여 준 제목([ShelfBook.seenTitle])이 같다(제목을 고친 책).
 * 직접 넣은 책([manual])은 제목으로 찾지 않는다(자동 추가·읽은 시간의 책 정하기·목록에 없던 책 모두).
 */
fun ShelfBook.hasTitle(title: String): Boolean =
    !manual && (sameTitle(this.title, title) || (seenTitle.isNotEmpty() && sameTitle(seenTitle, title)))

/** 나간 책이 나간 날(지운 책은 지운 날) */
fun leftOn(p: PastBook, zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(p.leftAt).atZone(zone).toLocalDate()

/**
 * [day] 에 [title] 을 읽은 시간을 붙일 책 id([ReadKey.book]). 정할 때(그날을 저장할 때, 마이그레이션) 한 번만 쓰고, 정한 id 는 다시 정하지 않는다.
 * 그 제목([hasTitle])이고 그날 이미 있던 책(그 책 id 가 붙은 시간이 그날보다 뒤에야 처음 나오는 책은 아직 없던 책, [firstRead]) 중
 * ① 나간 책(지운 책 포함) 중 그날이 나간 날(지운 책은 지운 날) 이전인 책, 여럿이면 먼저 나간 책
 * (같은 제목의 새 책이 생겨도 나가기 전 시간은 그 책에) ② 읽고 있는 책(목록 앞쪽) ③ 지우지 않은 나간 책 중 가장 최근(목록에서 뒤쪽)
 * ④ 없으면 null(목록에 없던 책. 그 책이 생기면 [ReadingLog.adopt]).
 */
fun bookFor(
    title: String, day: LocalDate, shelf: List<ShelfBook>, past: List<PastBook>, firstRead: Map<String, LocalDate> = emptyMap(),
    zone: ZoneId = ZoneId.systemDefault(),
): String? {
    fun existed(b: ShelfBook) = b.hasTitle(title) && firstRead[b.id]?.let { it <= day } != false
    past.filter { existed(it.book) && day <= leftOn(it, zone) }.minByOrNull { it.leftAt }?.let { return it.book.id }
    shelf.firstOrNull { existed(it) }?.let { return it.id }
    return past.lastOrNull { !it.hidden && existed(it.book) }?.book?.id
}

/** 책 id 마다 그 id 가 붙은 읽은 시간이 처음 나오는 날([bookFor] 이 그 전 날의 시간을 그 책에 붙이지 않게) */
fun ReadingDays.firstReadDays(): Map<String, LocalDate> {
    val first = mutableMapOf<String, LocalDate>()
    forEach { (day, keys) -> keys.keys.forEach { k -> if (k.book.isNotEmpty() && first[k.book]?.let { it > day } != false) first[k.book] = day } }
    return first
}

/**
 * 읽고 있는 책 목록(권수 제한 없음, 1.5.3 까지는 10권). 앞 [HOME]권이 홈에 바로 보이고(맨 앞 책은 크게, 나머지는 함께 읽는 책 다음 쪽), 맨 앞 책의 앱이 '읽고 있는 앱'이다.
 * 목록은 DB([Db]) `shelf`, 나간 책은 `past`, 표지는 books/<id>.jpg.
 */
object BookShelf {
    /** 홈 책 자리 한 쪽의 책 수: 맨 앞 책 + 함께 읽는 책 3칸(나머지는 함께 읽는 책 다음 쪽) */
    const val HOME = 4
    /** 줄인 표지(바이트로 재서 앱 메모리의 1/8 까지. 권수로 재면 큰 홈 표지가 작은 목록 표지 몇 장에 밀려난다) */
    private val covers = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    /** 뒤에서 쓰는 중인 줄인 표지 파일([cover]) */
    private val writingThumbs: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    private fun dir(context: Context) = File(Storage.of(context).filesDir, "books").apply { mkdirs() }
    private fun coverFile(context: Context, id: String) = File(dir(context), "$id.jpg")

    /**
     * 이 런처만 쓰는 데이터라 쓸 때 함께 바꾸는 메모리 사본(돌아올 때마다 DB를 읽지 않도록).
     * 화면(메인 스레드)과 접근성 서비스(표지 잘라 넣기는 다른 스레드)가 함께 고치므로 고치는 함수는 모두 @Synchronized.
     */
    @Volatile private var cached: List<ShelfBook>? = null

    fun list(context: Context): List<ShelfBook> = cached ?: load(context)

    @Volatile private var cachedHistory: List<PastBook>? = null

    /** 나간 책(완독한 책·뺀 책·지운 책), 나간 순서대로 */
    fun history(context: Context): List<PastBook> = cachedHistory ?: loadHistory(context)

    @Synchronized private fun loadHistory(context: Context): List<PastBook> {
        cachedHistory?.let { return it }
        return Db.get(context).rows(
            "SELECT id, title, author, app, updated_at, progress, seen_title, left_at, hidden, time_dropped FROM past ORDER BY pos"
        ) { c ->
            PastBook(
                ShelfBook(c.getString(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(3)?.ifEmpty { null },
                    c.getLong(4), c.getInt(5), seenTitle = c.getString(6).orEmpty()),
                c.getLong(7), c.getInt(8) != 0, c.getInt(9) != 0,
            )
        }.also { cachedHistory = it }
    }

    /**
     * 완독 한 번(DB `finish` 한 줄): 줄 id(같은 날 두 번 완독해도 따로), 완독한 날, 완독을 누른 때(ms, 같은 날 두 완독의 순서. 버전 3 전의 완독은 null),
     * 그 회차를 `다시 읽기`로 시작한 날(없으면 null), 그 회차의 별점·감상(없으면 null), 사용자가 고친 그 회차의 읽기 시작한 날(없으면 null)
     */
    class Finish(val id: Long, val day: LocalDate, val at: Long?, val started: LocalDate?, val review: Review?, val began: LocalDate? = null)

    /** 완독 순서: 날, 같은 날이면 누른 때(기기를 옮겨도 그대로), 그다음 줄 id */
    private val finishOrder = compareBy<Finish>({ it.day }, { it.at ?: 0 }, { it.id })

    @Volatile private var cachedFinishes: Map<String, List<Finish>>? = null

    /** 책(id)마다 완독([finishOrder] 순, 오래된 순). 다시 읽어도 남는다. */
    fun finishList(context: Context): Map<String, List<Finish>> = cachedFinishes ?: loadFinishes(context)

    @Synchronized private fun loadFinishes(context: Context): Map<String, List<Finish>> {
        cachedFinishes?.let { return it }
        return Db.get(context).rows("SELECT book_id, id, day, at, started, stars, text, began FROM finish") { c ->
            c.getString(0) to Finish(
                c.getLong(1), LocalDate.ofEpochDay(c.getLong(2)), if (c.isNull(3)) null else c.getLong(3),
                if (c.isNull(4)) null else LocalDate.ofEpochDay(c.getLong(4)), if (c.isNull(5)) null else Review(c.getInt(5), c.getString(6).orEmpty()),
                if (c.isNull(7)) null else LocalDate.ofEpochDay(c.getLong(7)),
            )
        }.groupBy({ it.first }, { it.second }).mapValues { (_, l) -> l.sortedWith(finishOrder) }.also { cachedFinishes = it }
    }

    /**
     * [id] 책을 [on] 에 완독했다(같은 날 두 번이면 두 번). 그때 남긴 [review] 는 이 회차의 것이 된다(비었으면 없음).
     * 진행 중이던 회차의 별점·감상([reviews])은 지우고, `다시 읽기`한 날([roundStarts])·고친 읽기 시작한 날([roundBegans])은 이 완독 줄로
     * 옮긴다(다음 회차는 빈 채로 시작).
     */
    @Synchronized private fun addFinish(context: Context, id: String, on: LocalDate, review: Review) {
        val r = review.trimmed().takeUnless { it.isEmpty }
        val started = roundStarts(context)[id]
        val began = roundBegans(context)[id]
        // 넣기 전에 읽어 둔다(캐시가 비어 있으면 넣은 뒤 DB 에서 읽은 목록에 같은 완독을 한 번 더 붙이게 된다).
        val before = finishList(context)
        // 누른 때. 기기 시각이 뒤로 고쳐져도 같은 책의 앞 완독보다 늦게(같은 날 회차 순서가 뒤바뀌지 않게)
        val at = maxOf(System.currentTimeMillis(), before[id].orEmpty().maxOfOrNull { (it.at ?: 0) + 1 } ?: 0)
        val rowId = Db.get(context).tx {
            deleteOpenRound(id)
            insertOrThrow("finish", null, Db.values(
                "uid" to UUID.randomUUID().toString().replace("-", ""), "book_id" to id, "day" to on.toEpochDay(), "at" to at,
                "started" to started?.toEpochDay(), "stars" to r?.halves, "text" to r?.text, "began" to began?.toEpochDay(),
            ))
        }
        cachedFinishes = before + (id to (before[id].orEmpty() + Finish(rowId, on, at, started, r, began)).sortedWith(finishOrder))
        forgetOpenRoundCache(context, id)
    }

    /** 진행 중 회차의 표시(별점·감상·`다시 읽기`한 날·고친 읽기 시작한 날)를 DB 에서 지운다(완독·회차 지우기). 캐시는 [forgetOpenRoundCache] */
    private fun SQLiteDatabase.deleteOpenRound(id: String) {
        delete("review", "book_id = ?", arrayOf(id))
        delete("round_start", "book_id = ?", arrayOf(id))
        delete("round_began", "book_id = ?", arrayOf(id))
    }

    private fun forgetOpenRoundCache(context: Context, id: String) {
        cachedReviews = reviews(context) - id
        cachedRoundStarts = roundStarts(context) - id
        cachedRoundBegans = roundBegans(context) - id
    }

    /**
     * 완독한 회차([id] 책의 [finishId] 완독)의 별점·감상을 [review] 로(둘 다 비면 없앤다), [day] 가 있으면 완독한 날도 바꾼다. 회차는 날 순서로 다시 센다.
     * 그 완독 줄이 [id] 책의 것이 아니면 예외(다른 책의 줄을 고치면 DB 와 캐시가 어긋난다).
     */
    @Synchronized fun setFinish(context: Context, id: String, finishId: Long, review: Review, day: LocalDate? = null) {
        val r = review.trimmed().takeUnless { it.isEmpty }
        val values = Db.values("stars" to r?.halves, "text" to r?.text).apply { day?.let { put("day", it.toEpochDay()) } }
        val n = Db.get(context).tx { update("finish", values, "id = ? AND book_id = ?", arrayOf(finishId.toString(), id)) }
        check(n == 1) { "finish $finishId is not a finish of $id" }
        val all = finishList(context)
        cachedFinishes = all + (id to all[id].orEmpty().map { if (it.id == finishId) Finish(it.id, day ?: it.day, it.at, it.started, r, it.began) else it }
            .sortedWith(finishOrder))
    }

    /**
     * [id] 책 한 회차의 읽기 시작한 날을 [day] 로 고친다: 완독한 회차([finishId])는 그 완독 줄에, 아니면(null) 진행 중 회차의 것([roundBegans]).
     * 그 완독 줄이 [id] 책의 것이 아니면 예외. 범위([BookRecord.startDayRange])는 부르는 쪽이 본다.
     */
    @Synchronized fun setBegan(context: Context, id: String, finishId: Long?, day: LocalDate) {
        if (finishId != null) {
            val n = Db.get(context).tx { update("finish", Db.values("began" to day.toEpochDay()), "id = ? AND book_id = ?", arrayOf(finishId.toString(), id)) }
            check(n == 1) { "finish $finishId is not a finish of $id" }
            val all = finishList(context)
            cachedFinishes = all + (id to all[id].orEmpty().map { if (it.id == finishId) Finish(it.id, it.day, it.at, it.started, it.review, day) else it })
        } else {
            Db.get(context).tx { replaceOrThrow("round_began", null, Db.values("book_id" to id, "day" to day.toEpochDay())) }
            cachedRoundBegans = roundBegans(context) + (id to day)
        }
    }

    @Volatile private var cachedRoundBegans: Map<String, LocalDate>? = null

    /** 책(id)마다 진행 중 회차의 고친 읽기 시작한 날([setBegan]). 완독하면 그 완독 줄로 옮기고, 새 회차가 시작하면 지운다([startRound]). */
    fun roundBegans(context: Context): Map<String, LocalDate> = cachedRoundBegans ?: loadRoundBegans(context)

    @Synchronized private fun loadRoundBegans(context: Context): Map<String, LocalDate> {
        cachedRoundBegans?.let { return it }
        return Db.get(context).rows("SELECT book_id, day FROM round_began") { it.getString(0) to LocalDate.ofEpochDay(it.getLong(1)) }
            .toMap().also { cachedRoundBegans = it }
    }

    @Volatile private var cachedRoundStarts: Map<String, LocalDate?>? = null

    /**
     * 책(id)마다 진행 중인 회차가 있다는 표시와 그 회차를 `다시 읽기`로 시작한 날(그 뒤 처음 읽은 날이 있으면 그날이 시작). 완독하면 지운다.
     * 날이 null 이면 진행 중인데 시작한 날을 모른다(1.5 판에서 다시 읽던 책. 책을 빼도 진행 중 회차가 남게 표시는 둔다).
     */
    fun roundStarts(context: Context): Map<String, LocalDate?> = cachedRoundStarts ?: loadRoundStarts(context)

    @Synchronized private fun loadRoundStarts(context: Context): Map<String, LocalDate?> {
        cachedRoundStarts?.let { return it }
        return Db.get(context).rows("SELECT book_id, day FROM round_start") { it.getString(0) to if (it.isNull(1)) null else LocalDate.ofEpochDay(it.getLong(1)) }
            .toMap().also { cachedRoundStarts = it }
    }

    private fun Review.trimmed() = copy(text = text.trim())

    @Volatile private var cachedReviews: Map<String, Review>? = null

    /** 책(id)마다 진행 중인 마지막 회차(처음 읽는 중·다시 읽는 중·다시 읽다 뺀 회차)의 별점·감상. 완독한 회차의 것은 [finishList]. */
    fun reviews(context: Context): Map<String, Review> = cachedReviews ?: loadReviews(context)

    @Synchronized private fun loadReviews(context: Context): Map<String, Review> {
        cachedReviews?.let { return it }
        return Db.get(context).rows("SELECT book_id, stars, text FROM review") { it.getString(0) to Review(it.getInt(1), it.getString(2).orEmpty()) }
            .toMap().also { cachedReviews = it }
    }

    /** [id] 책의 진행 중인 마지막 회차의 별점·감상을 바꾼다. 둘 다 비면 지운다. */
    @Synchronized fun setReview(context: Context, id: String, review: Review) {
        val db = Db.get(context)
        val r = review.trimmed()
        val all = reviews(context)
        cachedReviews = if (r.isEmpty) {
            db.tx { delete("review", "book_id = ?", arrayOf(id)) }
            all - id
        } else {
            // 실패하면 예외(캐시는 DB 에 들어간 뒤에만 바꾼다)
            db.tx {
                replaceOrThrow("review", null, Db.values(
                    "book_id" to id, "stars" to r.halves, "text" to r.text, "updated_at" to System.currentTimeMillis(),
                ))
            }
            all + (id to r)
        }
    }

    /** 나간 책 목록을 쓴다(지우지 않는다. 지운 책도 읽은 시간이 남아 있어 다시 보이지 않게 남긴다). 한 트랜잭션으로 통째로 바꾼다. */
    private fun writeHistory(context: Context, past: List<PastBook>) {
        Db.get(context).tx {
            delete("past", null, null)
            past.forEachIndexed { i, p ->
                val b = p.book
                insertOrThrow("past", null, Db.values(
                    "pos" to i, "id" to b.id, "title" to b.title, "author" to b.author, "app" to b.app.orEmpty(),
                    "updated_at" to b.updatedAt, "progress" to b.progress, "seen_title" to b.seenTitle,
                    "left_at" to p.leftAt, "hidden" to p.hidden, "time_dropped" to p.timeDropped,
                ))
            }
        }
        // 나간 책은 반납일·읽는 화를 저장하지 않는다(DB 에 칸이 없다): 메모리 사본도 같게(다시 띄운 뒤와 다르지 않게. 되살리면 이북 앱에서 다시 읽는다)
        cachedHistory = past.map { if (it.book.due < 0 && it.book.chapter.isEmpty()) it else it.copy(book = it.book.copy(due = -1, chapter = "")) }
    }

    /**
     * 여러 표를 한 트랜잭션으로 바꾼다(중간에 끊기면 아무것도 바뀌지 않는다). 안의 쓰기는 캐시를 바로 고치므로, 실패하면 캐시를 모두 비워
     * 되돌린 DB 에서 다시 읽게 한다.
     */
    private inline fun <T> atomic(context: Context, block: () -> T): T =
        try {
            Db.get(context).tx { block() }
        } catch (e: Exception) {
            clearCaches()
            throw e
        }

    /**
     * 부르는 쪽의 여러 저장을 한 트랜잭션으로(목록에 없던 책을 적어 두고([claim]) 바로 완독·별점·감상·책 정보: 뒤가 실패하면 적어 둔 책도
     * 되돌린다). 실패하면 캐시를 비운다.
     */
    @Synchronized fun <T> together(context: Context, block: () -> T): T = atomic(context, block)

    /** [id] 책의 새 회차가 [today] 에 시작한다(다시 읽기, 완독한 책을 펴 자동으로 되살림. 펴서 되살리면 편 때의 날) */
    private fun startRound(context: Context, id: String, today: LocalDate = LocalDate.now()) {
        // 기기 날짜가 그 책의 마지막 완독일보다 이르면 회차는 열되 시작일은 모름으로 둔다(완독보다 앞선 시작이 된다. 날짜를 바꿔 적는 추측은 안 한다).
        // 그때 회차는 마지막 완독 뒤 처음 읽은 날부터 센다.
        val day = today.takeIf { t -> finishList(context)[id].orEmpty().none { it.day > t } }
        val row = Db.get(context).tx {
            insertWithOnConflict("round_start", null, Db.values("book_id" to id, "day" to day?.toEpochDay()), SQLiteDatabase.CONFLICT_IGNORE)
                .also { if (it != -1L) delete("round_began", "book_id = ?", arrayOf(id)) }
        }
        // 이미 있으면(진행 중이던 회차) 그날을 그대로 둔다(DB 는 넣지 않았으니 캐시도). 새 회차면 앞 회차에서 끝나지 않고 남은 고친 시작일을 버린다.
        if (row != -1L) {
            cachedRoundStarts = roundStarts(context) + (id to day)
            cachedRoundBegans = roundBegans(context) - id
        }
    }

    /** 캐시를 모두 비워 다음에 DB 에서 다시 읽게 한다(트랜잭션을 되돌렸을 때, 테스트) */
    internal fun clearCaches() {
        cached = null; cachedHistory = null; cachedFinishes = null; cachedRoundStarts = null; cachedReviews = null; cachedRoundBegans = null
        cachedRoundDrops = null
        BookRecord.clearCache()
    }

    /** 읽고 있는 책을 목록에서 내보낸다(나간 책으로 남긴다). 표지는 남긴다. 나간 책에 넣기와 빼기를 한 번에(끊기면 같은 책이 양쪽에 남는다). */
    @Synchronized private fun leave(context: Context, id: String) {
        val books = list(context)
        val book = books.find { it.id == id } ?: return
        atomic(context) {
            // 1.3.0~1.5.3 은 두 표를 따로 저장해 끊기면 같은 책이 나간 책에도 남아 있을 수 있다: 그 줄은 이번 것으로 바꾼다(기본 키가 겹쳐 실패하지 않게)
            writeHistory(context, history(context).filter { it.book.id != id } + PastBook(book, System.currentTimeMillis()))
            write(context, books.filter { it.id != id })
        }
    }

    /**
     * 완독: [id] 책(읽고 있는 책·나간 책)에 오늘 완독한 날을 더하고(그때 남긴 [review] 는 이 회차의 것), 읽고 있는 책이면 뺀다(다시 읽던 책이면
     * 두 번째 완독). 완독한 날은 회차 메뉴에서 고친다([setFinish]).
     * 한 트랜잭션으로(완독만 남고 책이 읽고 있는 책에 남으면 바로 새 회차가 생긴 것처럼 보인다). 기기 날짜가 마지막 완독일보다 이르면 하지 않고 false.
     */
    @Synchronized fun finish(context: Context, id: String, review: Review): Boolean {
        // 기기 날짜가 마지막 완독일보다 이르면 받지 않는다(그날로 적으면 회차 순서가 뒤바뀐다. 날짜를 바꿔 적는 것은 추측이라 하지 않는다)
        val today = LocalDate.now()
        if (finishList(context)[id].orEmpty().any { it.day > today }) return false
        atomic(context) {
            addFinish(context, id, today, review)
            leave(context, id)
        }
        return true
    }

    /**
     * 다시 읽기: 나간 책 [id] 를 읽고 있는 책 맨 앞으로(표지·앱·완독한 날 그대로). 이미 읽고 있는 책이면 그대로 true.
     * [newRound] 면(마지막 회차가 완독으로 끝난 책. 부르는 쪽이 [BookRecord.rounds] 로 판단한다) 새 회차가 이날 시작한다(아직 읽지 않았어도).
     * 진행 중이던 회차면 그대로 이어진다. 지운 책은 되살리지 않는다(오래 열어 둔 상세에서 누른 경우).
     */
    @Synchronized fun restore(context: Context, id: String, newRound: Boolean) {
        if (list(context).any { it.id == id }) return
        val past = history(context)
        val book = past.firstOrNull { it.book.id == id && !it.hidden }?.book ?: return
        atomic(context) {
            write(context, listOf(book.copy(updatedAt = System.currentTimeMillis())) + list(context))
            writeHistory(context, past.filter { it.book.id != id })
            if (newRound) startRound(context, id)
        }
    }

    /**
     * 올해 읽은 책에서 지운다(다시 보이지 않게 나간 책에 지운 표시). [dropTime] 이면 그 책의 읽은 시간도 하루 합계·연속일에서 빼고([PastBook.timeDropped],
     * DB 의 읽은 시간 줄은 남긴다), 아니면 합계에 그대로. 표지와 책 파일 경로는 버린다. 읽고 있는 책이면 지우지 않고 false.
     */
    @Synchronized fun forget(context: Context, id: String, dropTime: Boolean = false): Boolean {
        // 1.3.0~1.5.3 의 끊긴 저장으로 나간 책에도 같은 id 가 남아 있을 수 있다: 읽고 있는 책이면 그래도 지우지 않는다(표지를 버리지 않게)
        if (list(context).any { it.id == id }) return false
        val past = history(context)
        if (past.none { it.book.id == id }) return false
        // 이미 지운 책이면 그대로(지운 날을 덮지 않는다. 두 화면에서 지운 경우)
        if (past.any { it.book.id == id && it.hidden }) return true
        val now = System.currentTimeMillis()
        writeHistory(context, past.map { if (it.book.id == id) it.copy(hidden = true, leftAt = now, timeDropped = dropTime) else it })
        dropFiles(context, id)
        Db.get(context).tx { delete("book_file", "book_id = ?", arrayOf(id)) }
        return true
    }

    /** 지운 회차 하나: [after] 다음 날(null 이면 처음)부터 [until] 까지 읽은 시간은 그 책의 기록에 넣지 않는다([BookRecord.all]) */
    class Drop(val after: LocalDate?, val until: LocalDate) {
        fun covers(day: LocalDate) = (after == null || day > after) && day <= until
    }

    @Volatile private var cachedRoundDrops: Map<String, List<Drop>>? = null

    /** 책(id)마다 지운 회차([forgetRound]) */
    fun roundDrops(context: Context): Map<String, List<Drop>> = cachedRoundDrops ?: loadRoundDrops(context)

    @Synchronized private fun loadRoundDrops(context: Context): Map<String, List<Drop>> {
        cachedRoundDrops?.let { return it }
        return Db.get(context).rows("SELECT book_id, after_day, until_day FROM round_drop ORDER BY id") { c ->
            c.getString(0) to Drop(if (c.isNull(1)) null else LocalDate.ofEpochDay(c.getLong(1)), LocalDate.ofEpochDay(c.getLong(2)))
        }.groupBy({ it.first }, { it.second }).also { cachedRoundDrops = it }
    }

    /**
     * 회차가 둘 이상인 책 [id] 에서 회차 하나만 지운다(전체 기록의 휴지통). 완독한 회차면 그 완독 줄([finishId])을, 진행 중 회차(null)면 그 회차의
     * 별점·감상·`다시 읽기`한 날·고친 읽기 시작한 날을 지우고(읽고 있는 책이면 읽고 있는 책에서도 뺀다: 읽고 있는 책에는 진행 중 회차가 늘 있다),
     * 그 회차에 읽은 시간([drop], [BookRecord.dropOf])은 그 책의 기록에서 뺀다(하루 합계·연속일에는 그대로. 남은 회차가 그 시간을 가져가 늘어나지 않게).
     * 읽고 있는 책도 나간 책(지운 책은 빼고)도 아니거나 그 완독 줄이 이 책의 것이 아니면(그사이 지워짐) false. 한 트랜잭션으로.
     */
    @Synchronized fun forgetRound(context: Context, id: String, finishId: Long?, drop: Drop): Boolean {
        val reading = list(context).any { it.id == id }
        if (!reading && history(context).none { it.book.id == id && !it.hidden }) return false
        // 넣기 전에 읽어 둔다(캐시가 비어 있으면 넣은 뒤 DB 에서 읽은 목록에 같은 줄을 한 번 더 붙이게 된다)
        val finishes = finishList(context)
        val drops = roundDrops(context)
        if (finishId != null && finishes[id].orEmpty().none { it.id == finishId }) return false
        atomic(context) {
            Db.get(context).tx {
                if (finishId != null) delete("finish", "id = ? AND book_id = ?", arrayOf(finishId.toString(), id))
                else deleteOpenRound(id)
                insertOrThrow("round_drop", null, Db.values("book_id" to id, "after_day" to drop.after?.toEpochDay(), "until_day" to drop.until.toEpochDay()))
            }
            if (finishId != null) {
                val left = finishes[id].orEmpty().filter { it.id != finishId }
                cachedFinishes = if (left.isEmpty()) finishes - id else finishes + (id to left)
            } else {
                forgetOpenRoundCache(context, id)
                if (reading) leave(context, id)
            }
            cachedRoundDrops = drops + (id to drops[id].orEmpty() + drop)
        }
        return true
    }

    /**
     * 목록에 없던 책(읽은 시간에만 있는 제목, id 가 없다)을 고치는 순간의 그 책: 그사이 같은 제목의 책이 생겼으면(자동 추가 등) 그 책
     * (읽고 있는 책 목록 앞쪽 → 지우지 않은 나간 책 중 가장 최근), 없으면 나간 책으로 적어 둔다([keep]. 별점·감상·완독·책 정보를 그 책에 남기게).
     */
    @Synchronized fun claim(context: Context, title: String, app: String?): ShelfBook =
        findByTitle(context, title) ?: findLivePast(context, title)?.book ?: keep(context, title, app)

    /** 목록에 없던 책(읽은 시간에만 있는 제목)을 책으로. 앱이 보여 준 제목을 [ShelfBook.seenTitle] 로 둬 제목을 고쳐도 자동 추가가 찾는다. */
    private fun titleOnly(title: String, app: String?) = ShelfBook(newId(), title, "", app, System.currentTimeMillis(), seenTitle = title)

    /** 목록에 없던 책을 나간 책으로 적어 두고 돌려준다(제목·저자·표지를 고칠 수 있게). 그 제목으로 읽었는데 책을 몰라 비워 둔 시간을 이 책으로. */
    @Synchronized fun keep(context: Context, title: String, app: String?): ShelfBook = titleOnly(title, app).also { book ->
        atomic(context) {
            writeHistory(context, history(context) + PastBook(book, System.currentTimeMillis()))
            ReadingLog.adopt(context, book.title, book.seenTitle, book.id)
        }
    }

    /** 나간 책 중 제목이 같은, 지우지 않은 책(가장 최근 것). 같은 제목의 지운 책이 더 최근이어도 그 앞의 책을 찾는다. */
    fun findLivePast(context: Context, title: String): PastBook? = history(context).lastOrNull { !it.hidden && it.book.hasTitle(title) }

    @Synchronized private fun load(context: Context): List<ShelfBook> {
        cached?.let { return it }
        migrateSingleBook(context)
        return migrateIds(context, readIndex(context)).also { cached = it }
    }

    private const val ID_PREFIX = "b-"
    /** 표지가 빈 그림인지 볼 때 줄여 푸는 폭([dropCoverIf]) */
    private const val TEST_PX = 256
    /** `책 추가` 로 직접 넣은 책의 id 머리([manual]) */
    const val MANUAL_PREFIX = "m-"
    /** 책 id(`book_id`)가 든 표: 책 id 를 바꾸면 모두 함께 바꾼다([migrateIds]) */
    private val BOOK_ID_TABLES = listOf("finish", "review", "round_start", "round_began", "round_drop", "reading_day", "book_open", "book_file")
    private fun newId() = ID_PREFIX + UUID.randomUUID()

    private fun readIndex(context: Context): List<ShelfBook> =
        Db.get(context).rows("SELECT id, title, author, app, updated_at, progress, due, seen_title, chapter FROM shelf ORDER BY pos") { c ->
            ShelfBook(c.getString(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(3)?.ifEmpty { null },
                c.getLong(4), c.getInt(5), c.getLong(6), c.getString(7).orEmpty(), c.getString(8).orEmpty())
        }

    /** 줄인 표지(흑백, 컬러 [Palette] 면 원래 색). 표지 그림이 없거나 읽지 못하면 제목·저자·앱 이름으로 그린 빈 표지([BlankCover]). */
    fun cover(context: Context, book: ShelfBook, widthPx: Int): Bitmap {
        // 빈 표지에는 앱 이름이 들어가므로 앱이 바뀌어도, 표지는 화면 대비에 맞춰 밝히므로 대비가 바뀌어도 다시 그린다.
        // 한 번이라도 완독한 책은 어디에 놓이든(홈·목록·메뉴·올해 읽은 책) `완독` 띠를 그려 넣는다.
        val tone = Crema.contrastTone(context)
        // 띠 글자는 앱 언어를 따르므로 키에 글자를 넣는다(빈 글자 = 띠 없음).
        val ribbon = if (finishList(context)[book.id].isNullOrEmpty()) "" else context.getString(R.string.finish_book)
        val palette = Palette.current
        val original = coverFile(context, book.id)
        // 표지 그림이 없으면 책 파일에서 꺼낼 수 있는지가 키에 든다(빈 표지를 그린 뒤 책 파일 읽기를 허용하거나, 문리더처럼 책 파일을 나중에 찾으면 다시 그리게)
        val source = if (original.length() > 0) "o" else if (BookFiles.canRead(context) && filePath(context, book.id) != null) "f" else "-"
        val key = "${book.id}@$widthPx@${book.updatedAt}@${book.app}@$tone@$ribbon@${palette.key}@$source"
        covers.get(key)?.let { return it }
        if (source == "f") fileCover(context, book.id)
        // 원본(큰 JPEG)을 매번 줄이지 않도록, 화면 폭으로 줄인 표지를 파일로 둔다. 원본이 바뀌면 다시 만든다.
        // 화면 대비만큼 미리 밝힌 그림이라 대비 값을, 컬러로 줄인 것은 `c` 를 이름에 넣는다.
        val thumb = File(dir(context), "${book.id}_${widthPx}t${(tone * 100).toInt()}${if (palette.color) "c" else ""}.png")
        // 줄인 표지의 시각은 줄일 때 읽은 원본의 시각(뒤에서 쓰는 사이 원본이 바뀌면 다음에 다시 만든다)
        val made = original.lastModified()
        val bmp = thumb.takeIf { it.lastModified() >= made }?.let { BitmapFactory.decodeFile(it.path) }
            ?: runCatching { BookSearch.scaledCover(original.readBytes(), widthPx, gray = !palette.color)?.also { Crema.undoContrast(it, tone) } }.getOrNull()?.also { scaled ->
                // 파일로 두기는 화면을 기다리게 하지 않게 뒤에서(PNG 압축은 느린 기기에서 100ms 넘게 걸린다). 같은 파일을 쓰는 중이면 두 번 쓰지 않는다.
                if (writingThumbs.add(thumb.path)) Bg.run({
                    try {
                        runCatching {
                            writeAtomically(thumb) { f -> f.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                            thumb.setLastModified(made)
                        }
                        // 같은 폭의 예전 줄인 표지(대비·색이 다르거나 예전 이름)는 지운다.
                        val stale = Regex("""${Regex.escape("${book.id}_$widthPx")}(g|t\d+c?)?\.png""")
                        dir(context).listFiles { f -> f != thumb && stale.matches(f.name) }?.forEach { it.delete() }
                    } finally {
                        writingThumbs.remove(thumb.path)
                    }
                }) {}
            }
            ?: BlankCover.draw(book.title, book.author, book.app?.let { app -> AppStore.find(context, app)?.label }, widthPx)
        val out = if (ribbon.isEmpty()) bmp else withRibbon(bmp, ribbon, palette.ribbon)
        covers.put(key, out)
        return out
    }

    /**
     * 표지 오른쪽 위 모서리를 45도로 가로지르는 [band](진한 회색, 컬러면 완독 색 [Palette.ribbon]) [text] 띠를 그린 사본. 띠 굵기·글자는 표지 폭에 비례해(굵기 21%, 글자는 띠의 58%)
     * 큰 표지와 작은 표지가 같은 모양이다. 띠 가운데는 모서리에서 폭의 1/4 안쪽, 위아래 흰 줄로 어두운 표지에서도 띠가 보인다.
     * 표지 칸은 모두 2:3 이고 그림은 가운데를 잘라 보이므로, 먼저 2:3 으로 잘라 두고 그린다(아니면 띠가 잘려 한쪽으로 쏠린다).
     */
    private fun withRibbon(cover: Bitmap, text: String, band: Int): Bitmap {
        val vw = minOf(cover.width, cover.height * 2 / 3)
        val vh = minOf(cover.height, cover.width * 3 / 2)
        val out = Bitmap.createBitmap(vw, vh, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(cover, -(cover.width - vw) / 2f, -(cover.height - vh) / 2f, null)
        val w = out.width.toFloat()
        val bandH = w * 0.21f
        val k = w * 0.25f
        val half = w * 0.55f
        val inset = bandH * 0.09f
        Canvas(out).apply {
            translate(w - k, k)
            rotate(45f)
            drawRect(-half, -bandH / 2, half, bandH / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = band })
            val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = maxOf(1f, bandH * 0.045f) }
            drawLine(-half, -bandH / 2 + inset, half, -bandH / 2 + inset, edge)
            drawLine(-half, bandH / 2 - inset, half, bandH / 2 - inset, edge)
            val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Ui.bold; textSize = bandH * 0.58f }
            // 글꼴 높이(ascent·descent)가 아니라 실제 글자 모양으로 띠 가운데에 둔다(한글 글꼴은 위아래 여백이 커서 한쪽으로 쏠린다).
            val bounds = Rect().also { label.getTextBounds(text, 0, text.length, it) }
            drawText(text, -bounds.exactCenterX(), -bounds.exactCenterY(), label)
        }
        return out
    }

    /**
     * 사용자가 적은 제목·저자로 새 책([manual], 지원하지 않는 이북 앱용)을 맨 앞에 넣는다. 인터넷을 쓰지 않아 표지 그림은 없고(제목으로 그린 빈 표지),
     * 표지는 나중에 책 정보 고치기의 `표지 다시 찾기`([replace])로 붙인다.
     * 책은 사용자가 직접 뺄 때만 빠진다(자동으로 밀어내지 않는다).
     * 같은 제목의 책이 있어도 늘 새 책이고, 제목으로 다른 책·읽은 시간과 잇지 않는다(그 제목으로 읽은 어느 책에도 붙지 않은 시간도 가져오지 않는다.
     * 그 시간은 지원하는 앱에서 읽은 것이라 자동 추가가 넣은 책의 것이다).
     * 읽은 시간이 붙지 않으니 넣은 날 첫 회차가 시작한다([roundStarts]. 독서 기록·회차·올해 읽은 책에 넣은 날부터 보인다, [BookRecord.readYears]).
     */
    @Synchronized fun add(context: Context, title: String, author: String): ShelfBook {
        val added = ShelfBook(MANUAL_PREFIX + UUID.randomUUID(), title, author, null, System.currentTimeMillis())
        atomic(context) {
            write(context, listOf(added) + list(context))
            startRound(context, added.id)
        }
        return added
    }

    /**
     * 나간 책 [id] 를 이북 앱에서 다시 펴 되살릴 때 새 회차가 시작하는가: 완독한 적이 있고 진행 중 회차 표시가 없고
     * 마지막 회차가 완독으로 끝났다(이미 진행 중이던 회차 — 다시 읽기·감상·완독 뒤 5분 넘게 읽음 — 면 이어진다).
     */
    private fun startsRoundOnReturn(context: Context, id: String): Boolean =
        finishList(context)[id].orEmpty().isNotEmpty() && id !in roundStarts(context) &&
            BookRecord.all(context).firstOrNull { it.book.id == id }?.reopenStartsRound != false

    /**
     * 이북 앱에서 편 책을 맨 앞에 표지 그림 없이 넣는다(읽고 있는 책 자동 추가, 빈 표지로 보인다).
     * 전에 뺀 책(나간 책 가운데 [revive] 인 것, 기본은 같은 제목. 지운 책은 빼고)이면 새로 만들지 않고 그 책을 같은 id 로 되살린다(별점·감상·표지·고친 제목이 이어지게).
     * 완독한 책이면 `다시 읽기`처럼 새 회차가 그날 시작한다([roundStarts]. 이미 진행 중이던 회차면 그대로 이어진다).
     * 목록에 넣기와 나간 책에서 빼기는 한 트랜잭션으로. 권수 제한은 없다(빼는 것은 사용자가 목록에서).
     */
    @Synchronized fun addOpened(
        context: Context, title: String, author: String, app: String?, progress: Int, due: Long, at: Long = System.currentTimeMillis(),
        chapter: String = "", revive: (ShelfBook) -> Boolean = { it.hasTitle(title) },
    ): ShelfBook {
        val now = System.currentTimeMillis()
        val past = history(context).lastOrNull { !it.hidden && revive(it.book) }?.book
        // 화면에서 모르는 진행률(-1)은 예전 값 그대로([updateStatus] 와 같게). 반납일·읽는 화는 나간 책에 남기지 않으니(writeHistory) 본 것으로
        val added = past?.copy(
            author = past.author.ifEmpty { author }, app = app ?: past.app, updatedAt = now,
            progress = if (progress >= 0) progress else past.progress, due = due, chapter = chapter,
        )
            ?: ShelfBook(newId(), title, author, app, now, progress, due, seenTitle = title, chapter = chapter)
        // 마지막 회차가 완독으로 끝난 책이면 새 회차를 연다. 이미 진행 중이던 회차(다시 읽기·감상·완독 뒤 5분 넘게 읽음)면 이어진다.
        val newRound = past != null && startsRoundOnReturn(context, past.id)
        atomic(context) {
            write(context, listOf(added) + list(context))
            if (past != null) writeHistory(context, history(context).filter { it.book.id != past.id })
            // 편 때(서재로 돌아와서야 알면 읽는 화면을 연 때)의 날. 자정을 넘겨 알아내도 그 회차는 편 날 시작
            if (newRound) startRound(context, added.id, Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate())
            // 새 책: 그 제목으로 읽었는데 책을 몰라 비워 둔 시간을 이 책으로
            if (past == null) ReadingLog.adopt(context, added.title, added.seenTitle, added.id)
        }
        return added
    }

    /**
     * 표지를 다시 고른 책(읽고 있는 책·나간 책): 자리·앱·진행률은 두고 표지·제목·저자만 바꾼다(줄인 표지는 원본이 새로워 다시 만든다).
     */
    @Throws(IOException::class)
    @Synchronized fun replace(context: Context, id: String, book: BookResult, coverBytes: ByteArray) {
        if (list(context).none { it.id == id } && history(context).none { it.book.id == id }) return
        withCover(context, id, coverBytes) { edit(context, id) { it.copy(title = book.title, author = book.author) } }
    }

    /**
     * 표지 파일 [bytes] 와 책 정보 저장([save])을 함께: 저장이 실패하면 표지를 예전 것으로 되돌린다(없었으면 지운다. 표지가 있는 것으로 보여
     * 다시 잘라 오지 않거나, 바뀐 표지가 다시 그려지지 않은 채 남지 않게).
     */
    private fun withCover(context: Context, id: String, bytes: ByteArray, save: () -> Unit) {
        val cover = coverFile(context, id)
        val backup = File(cover.parentFile, "${cover.name}.bak").also { it.delete() }
        if (cover.exists()) cover.copyTo(backup, overwrite = true)
        try {
            saveCover(context, id, bytes)
            save()
        } catch (e: Exception) {
            if (backup.exists()) backup.copyTo(cover, overwrite = true) else cover.delete()
            clearCaches()
            throw e
        } finally {
            backup.delete()
        }
    }

    /** 이북 앱 서재에서 본 책 [id] 의 진행률·반납일(모르면 -1)·저자·읽는 화(모르면 "")([updateStatuses]) */
    data class Status(val id: String, val progress: Int, val due: Long, val author: String = "", val chapter: String = "")

    /** 이북 앱 서재에서 본 진행률·반납일·읽는 화(저자는 비어 있을 때만)를 적는다(순서는 그대로). 바뀐 게 없으면 쓰지 않는다. */
    fun updateStatus(context: Context, id: String, progress: Int, due: Long, author: String = "", chapter: String = "") =
        updateStatuses(context, listOf(Status(id, progress, due, author, chapter)))

    /** 서재 한 화면에서 본 여러 책의 [updateStatus] 를 한 번에 쓴다. */
    @Synchronized fun updateStatuses(context: Context, statuses: List<Status>) {
        val now = System.currentTimeMillis()
        fun ShelfBook.with(s: Status): ShelfBook {
            val p = if (s.progress >= 0) s.progress else progress
            val d = if (s.due >= 0) s.due else due
            // 저자는 비어 있을 때만 채운다(읽는 화면 메뉴의 제목으로 먼저 들어온 책).
            val a = author.ifBlank { s.author }
            val ch = s.chapter.ifEmpty { chapter }
            if (p == progress && d == due && a == author && ch == chapter) return this
            // 저자가 채워지면 빈 표지를 다시 그리도록 updatedAt 도 새로.
            return copy(progress = p, due = d, author = a, chapter = ch, updatedAt = if (a != author) now else updatedAt)
        }
        // 같은 책이 여러 칸이면 차례로(앞 칸에서 본 값을 뒤 칸의 모르는 값(-1)이 지우지 않게)
        write(context, statuses.fold(list(context)) { books, s -> books.map { if (it.id == s.id) it.with(s) else it } })
    }

    fun hasCover(context: Context, id: String) = coverFile(context, id).length() > 0

    /**
     * 기기 밖으로 보내는 그림(월간 결산 [RecapImage])에 넣는 표지: 원본을 폭 [widthPx] 로 줄인 것. 화면 대비 보정·완독 띠 없이, [gray] 면 흑백.
     * 표지 그림이 없으면(책 파일에서 꺼낼 수 있으면 꺼내 보고) null.
     */
    fun shareCover(context: Context, book: ShelfBook, widthPx: Int, gray: Boolean): Bitmap? {
        val file = coverFile(context, book.id)
        if (file.length() <= 0 && BookFiles.canRead(context) && filePath(context, book.id) != null) fileCover(context, book.id)
        if (file.length() <= 0) return null
        return runCatching { BookSearch.scaledCover(file.readBytes(), widthPx, gray) }.getOrNull()
    }

    /** 표지만 바꾼다(이북 앱 화면에서 잘라 온 표지). 바뀐 표지가 다시 그려지도록 updatedAt 도 새로. */
    @Synchronized fun setCover(context: Context, id: String, bytes: ByteArray) {
        val books = list(context)
        if (books.none { it.id == id }) return
        val now = System.currentTimeMillis()
        withCover(context, id, bytes) { write(context, books.map { if (it.id == id) it.copy(updatedAt = now) else it }) }
    }

    /**
     * 책 [id] 의 표지 그림이 [test](이북 앱이 표지 대신 그리는 빈 그림)에 맞으면 지운다(그러면 진짜 표지가 보일 때 다시 잘라 온다). 지웠으면 true.
     * 표지 없는 모습으로 다시 그려지도록 updatedAt 도 새로. 화면 스레드에서 부르지 않는다.
     * 큰 표지(책 파일에서 꺼낸 그림)도 폭 [TEST_PX] 쯤으로 줄여 풀어 본다(잠금을 오래 쥐지 않게).
     */
    @Synchronized fun dropCoverIf(context: Context, id: String, test: (Bitmap) -> Boolean): Boolean {
        val books = list(context)
        if (books.none { it.id == id }) return false
        val file = coverFile(context, id).takeIf { it.length() > 0 } ?: return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= TEST_PX) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return false
        val matches = try { test(bmp) } finally { bmp.recycle() }
        if (!matches) return false
        dropFiles(context, id)
        val now = System.currentTimeMillis()
        write(context, books.map { if (it.id == id) it.copy(updatedAt = now) else it })
        return true
    }

    /**
     * 제목이 같은 책([sameTitle]: 공백 무시, 부제는 한쪽에만 있으면 무시). 사용자가 제목을 고친 자동 추가 책은 앱이 보여 준 제목([ShelfBook.seenTitle])으로도 찾는다
     * (그러지 않으면 다음에 펼 때 새 책으로 들어간다).
     */
    fun findByTitle(context: Context, title: String): ShelfBook? = list(context).firstOrNull { it.hasTitle(title) }

    /**
     * 표지 그림이 없는 책: 이북 앱이 연 책 파일([filePath], 연 파일을 아는 앱: KOReader·네오리더·이지뷰어·우주·문리더)에서 그 책을 넣은 앱의 길([fileCovers])로 표지를 꺼내 이 책의 표지로 둔다.
     * 파일을 적을 때 뒤에서 꺼내 두고([prefetchFileCover]), 그때 못 했으면(그 전에 넣은 책·책 파일 읽기를 나중에 허용) 그릴 때([cover]) 꺼낸다.
     * 같은 책은 한 번만 꺼낸다(뒤에서 꺼내는 중에 그리면 끝나기를 기다린다). 한 번 둔 표지는 책 파일을 지우거나 권한을 꺼도 남는다.
     * 표지가 없는 파일은 그 파일이 바뀔 때까지 다시 열지 않는다.
     */
    private fun fileCover(context: Context, id: String) {
        val path = filePath(context, id) ?: return
        // 그 책을 넣은 앱이 고른 길로만 꺼낸다. 오래 걸릴 수 있는 파일(압축 만화)은 화면 스레드에서 꺼내지 않고 뒤에서(다음에 그릴 때 보인다)
        val covers = fileCovers(context, id)
        if (Looper.myLooper() == Looper.getMainLooper() && covers.slow(path)) return prefetchFileCover(context, id)
        synchronized(fileCoverLocks.getOrPut(id) { Any() }) {
            if (hasCover(context, id)) return
            val tried = "$path@${File(path).lastModified()}"
            if (tried in noFileCover) return
            val bytes = covers.cover(context, path) ?: run { noFileCover += tried; return }
            runCatching { saveCover(context, id, bytes) }
        }
    }

    /** 책 [id](읽고 있는 책, 없으면 나간 책)를 넣은 이북 앱이 책 파일에서 표지를 꺼내는 길([com.woody.pebbledesk.readers.ReaderApp.fileCovers]). 모르는 앱이면 기본 */
    fun fileCovers(context: Context, id: String): BookFiles.Covers =
        bookOf(context, setOf(id))?.app?.substringBefore('/')?.let { ReaderApps.forPackage(context, it) }?.fileCovers ?: BookFiles.BOOKS

    /**
     * 압축 만화를 푸는 libarchive 를 받았다([com.woody.pebbledesk.readers.ArchiveLib]): 받기 전에 표지가 없다고 기억한 파일을 잊고, 표지 그림이 없는
     * 읽고 있는 책들의 파일 표지를 뒤에서 꺼내 둔다(받기 전에 넣은 CBR·CB7 책도 홈에 돌아오면 보이게).
     */
    fun archiveLibChanged(context: Context) {
        noFileCover.clear()
        list(context).forEach { prefetchFileCover(context, it.id) }
    }

    /**
     * 표지 그림이 없는 책 [id] 의 책 파일 표지를 뒤에서 꺼내 둔다([fileCover]): 큰 그림·큰 EPUB 을 홈이 그릴 때 화면 스레드에서 꺼내지 않게.
     * 책 파일 읽기가 꺼져 있으면 하지 않는다(못 읽은 파일을 표지 없는 파일로 기억하지 않게).
     */
    private fun prefetchFileCover(context: Context, id: String) {
        if (hasCover(context, id) || !BookFiles.canRead(context)) return
        val app = context.applicationContext
        Bg.run({ fileCover(app, id) }) {}
    }

    /** 표지를 꺼내 봤지만 없던 책 파일(경로@수정 시각, [fileCover]) */
    private val noFileCover: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    /** 책마다 표지 꺼내기를 한 번에 하나만([fileCover]) */
    private val fileCoverLocks = java.util.concurrent.ConcurrentHashMap<String, Any>()

    @Throws(IOException::class)
    private fun saveCover(context: Context, id: String, bytes: ByteArray) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) throw IOException("not an image")
        writeAtomically(coverFile(context, id)) { it.writeBytes(bytes) }
    }

    private fun dropFiles(context: Context, id: String) {
        coverFile(context, id).delete()
        dir(context).listFiles { f -> f.name.startsWith("${id}_") }?.forEach { it.delete() }
    }

    @Synchronized fun moveToFront(context: Context, id: String) {
        val books = list(context)
        if (books.firstOrNull()?.id == id) return
        val book = books.find { it.id == id } ?: return
        write(context, listOf(book) + books.filter { it.id != id })
    }

    /** [id] 책을 [delta] 칸 옮긴다(-1 이면 위로). 맨 위 책이 홈에 크게 보인다. */
    @Synchronized fun move(context: Context, id: String, delta: Int) {
        val books = list(context).toMutableList()
        val from = books.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to !in books.indices) return
        books.add(to, books.removeAt(from))
        write(context, books)
    }

    /** 책마다 저장한 앱 키를 정리한다(지워진 앱은 null, 실행 화면 이름이 바뀐 앱은 새 키로). */
    @Synchronized fun pruneApps(context: Context, fix: (String) -> String?) {
        val books = list(context)
        val fixed = books.map { b -> b.app?.let { b.copy(app = fix(it)) } ?: b }
        if (fixed != books) write(context, fixed)
    }

    /** 제목을 바꾼다(제목이 잘렸거나 없는 책). 읽고 있는 책·나간 책 모두. */
    fun setTitle(context: Context, id: String, title: String) = edit(context, id) { it.copy(title = title) }

    /** 저자를 직접 넣거나 고친다(이북 앱이 저자를 보여 주지 않은 책). 읽고 있는 책·나간 책 모두. */
    fun setAuthor(context: Context, id: String, author: String) = edit(context, id) { it.copy(author = author) }

    /**
     * 읽고 있는 책 또는 나간 책의 제목·저자를 고친다. 표지가 다시 그려지도록 updatedAt 도 새로.
     * 처음 고칠 때 원래 제목을 [ShelfBook.seenTitle] 로 남겨 이북 앱이 보여 주는 제목으로 계속 찾는다(읽은 시간·자동 추가).
     */
    @Synchronized private fun edit(context: Context, id: String, change: (ShelfBook) -> ShelfBook) {
        val now = System.currentTimeMillis()
        fun ShelfBook.changed() = change(this).copy(updatedAt = now, seenTitle = seenTitle.ifEmpty { title })
        val books = list(context)
        val past = history(context)
        if (books.any { it.id == id }) write(context, books.map { if (it.id == id) it.changed() else it })
        // 어디에도 없으면 쓰지 않는다(기록 판 번호가 올라 떠 있는 메뉴가 닫히지 않게)
        else if (past.any { it.book.id == id }) writeHistory(context, past.map { if (it.book.id == id) it.copy(book = it.book.changed()) else it })
    }

    @Synchronized fun setApp(context: Context, id: String, app: String?) =
        write(context, list(context).map { if (it.id == id) it.copy(app = app) else it })

    /** 이북 앱이 이 책으로 연 파일의 경로(네오리더처럼 파일로만 여는 앱). 모르면 null */
    fun filePath(context: Context, id: String): String? =
        Db.get(context).rows("SELECT path FROM book_file WHERE book_id = ?", arrayOf(id)) { it.getString(0) }.firstOrNull()

    /** [path] 파일로 넣은 책(읽고 있는 책, 없으면 나간 책)의 제목. 없으면 null */
    fun titleByFile(context: Context, path: String): String? = bookOf(context, idsByFile(context, path))?.title

    /** [path] 파일로 넣은 책(읽고 있는 책, 없으면 나간 책)의 id. 없으면 null */
    fun idByFile(context: Context, path: String): String? = bookOf(context, idsByFile(context, path))?.id

    // 공유 저장소는 대소문자를 가리지 않아 같은 파일이 표기만 다를 수 있다(이지뷰어 목록의 '첫글자를 대문자로' 설정)
    private fun idsByFile(context: Context, path: String): Set<String> =
        Db.get(context).rows("SELECT book_id FROM book_file WHERE path = ? COLLATE NOCASE", arrayOf(path)) { it.getString(0) }.toSet()

    /**
     * 파일 이름이 [name] 인 책 파일로 넣은 책들의 id(다른 폴더의 같은 이름이면 여럿, 지운 책도 들어 있을 수 있다 [bookOf]).
     * 제목 대신 파일 이름만 보이는 읽는 화면(이지뷰어)에서 어떤 책인지 찾는다. 확장자를 뺀 이름이 같아도 같은 파일로 보고(이지뷰어 설정으로
     * 확장자를 숨길 수 있다), 대소문자는 무시한다(이지뷰어 목록의 '첫글자를 대문자로' 설정)
     */
    fun idsByFileName(context: Context, name: String): Set<String> {
        val stem = withoutBookFile(name)
        val rows = Db.get(context).rows("SELECT book_id, path FROM book_file", null) { it.getString(0) to File(it.getString(1)).name }
        return (rows.filter { it.second.equals(name, ignoreCase = true) }.ifEmpty { rows.filter { withoutBookFile(it.second).equals(stem, ignoreCase = true) } })
            .map { it.first }.toSet()
    }

    /** [folder] 안의 그림 파일로 넣은 책(그림 폴더가 한 권, 이지뷰어. 읽고 있는 책, 없으면 나간 책)의 id. 없으면 null */
    fun idByFolder(context: Context, folder: String): String? = bookOf(context,
        Db.get(context).rows("SELECT book_id, path FROM book_file", null) { it.getString(0) to File(it.getString(1)) }
            .filter { (_, f) -> isImageFile(f.name) && f.parent.equals(folder, ignoreCase = true) }.map { it.first }.toSet())?.id

    /** [ids] 가운데 읽고 있는 책, 없으면 나간 책(마지막). 없으면 null */
    fun bookOf(context: Context, ids: Set<String>): ShelfBook? {
        if (ids.isEmpty()) return null
        return list(context).firstOrNull { it.id in ids } ?: history(context).lastOrNull { it.book.id in ids }?.book
    }

    /** 읽고 있는 책 [id]. 없으면 null */
    fun get(context: Context, id: String): ShelfBook? = list(context).firstOrNull { it.id == id }

    /** [title] 의 책(읽고 있는 책, 없으면 지우지 않은 나간 책의 마지막) 가운데 [accept] 인 것의 id. 없으면 null */
    fun idByTitle(context: Context, title: String, accept: (ShelfBook) -> Boolean): String? =
        list(context).firstOrNull { it.hasTitle(title) && accept(it) }?.id
            ?: history(context).lastOrNull { !it.hidden && it.book.hasTitle(title) && accept(it.book) }?.book?.id

    /** 책이 나간 책이 되어도 남긴다(다시 읽을 때 그 파일을 연다). 표지 그림이 없으면 그 파일의 표지를 뒤에서 꺼내 둔다([prefetchFileCover]). */
    fun setFilePath(context: Context, id: String, path: String) {
        if (filePath(context, id) == path) return
        Db.get(context).tx { insertWithOnConflict("book_file", null, Db.values("book_id" to id, "path" to path), SQLiteDatabase.CONFLICT_REPLACE) }
        prefetchFileCover(context, id)
    }

    /** 홈에서 빼기: 읽고 있는 책에서 빼서 나간 책으로 남긴다. [review] 가 있으면(빼기 메뉴에서 고친 별점·감상) 진행 중 회차의 것으로 같이 저장한다. */
    @Synchronized fun remove(context: Context, id: String, review: Review? = null) = atomic(context) {
        // 읽고 있는 책일 때만(그사이 다른 화면에서 완독·빼기를 했으면 감상을 지금 회차에 적지 않는다)
        if (list(context).none { it.id == id }) return@atomic
        leave(context, id)
        review?.let { setReview(context, id, it) }
    }

    /**
     * 읽고 있는 책 목록을 한 트랜잭션으로 바꾼다(순서 = pos). 바뀐 게 없으면 쓰지 않고(기록 판 번호가 올라 떠 있는 메뉴가 닫히지 않게),
     * 책과 순서가 그대로면 바뀐 줄만 고친다(서재에서 진행률을 볼 때마다 표 전체를 다시 쓰지 않게).
     */
    private fun write(context: Context, books: List<ShelfBook>) {
        // list() 가 아니라(처음 읽는 중 옮기기에서도 부른다) 지금 DB 의 목록
        val before = cached ?: readIndex(context)
        if (books == before) return
        fun ShelfBook.values(pos: Int) = Db.values(
            "pos" to pos, "id" to id, "title" to title, "author" to author, "app" to app.orEmpty(),
            "updated_at" to updatedAt, "progress" to progress, "due" to due, "seen_title" to seenTitle, "chapter" to chapter,
        )
        Db.get(context).tx {
            if (books.map { it.id } == before.map { it.id }) {
                books.forEachIndexed { i, b -> if (b != before[i]) update("shelf", b.values(i), "id = ?", arrayOf(b.id)) }
            } else {
                delete("shelf", null, null)
                books.forEachIndexed { i, b -> insertOrThrow("shelf", null, b.values(i)) }
            }
        }
        cached = books
    }

    /** 예전(한 권만 저장하던) now_reading/ 을 목록의 첫 책으로 옮긴다. 그때의 읽고 있는 앱을 그 책의 앱으로. */
    private fun migrateSingleBook(context: Context) {
        val oldDir = File(context.filesDir, "now_reading")
        val info = File(oldDir, "info.json")
        val cover = File(oldDir, "cover.jpg")
        if (!info.exists() || readIndex(context).isNotEmpty()) return
        val json = runCatching { JSONObject(info.readText()) }.getOrNull()
        if (json != null && cover.length() > 0) {
            val id = newId()
            cover.copyTo(coverFile(context, id), overwrite = true)
            val app = HomePrefs(context).legacyReadingApp
            write(context, listOf(ShelfBook(id, json.optString("title"), json.optString("author"), app, json.optLong("updated_at"))))
        }
        oldDir.deleteRecursively()
    }

    /**
     * 예전 id(YES24 상품 번호, 자동 추가는 `auto-<정리한 제목>`, 옛 한 권은 `legacy`)를 런처가 만든 id 로 바꾼다(1.2.1 까지의 데이터).
     * `auto-` 책은 id 의 제목을 [ShelfBook.seenTitle] 로 옮겨, 제목을 고친 책도 계속 찾는다.
     * 중간에 꺼져도 책·표지를 잃지 않게: 표지를 새 이름으로 **복사** → 목록 저장(책 id 가 든 다른 표도 같은 트랜잭션으로 새 id 로) → 예전 파일 지우기.
     * 표지 복사나 목록 저장이 실패하면 예전 id 그대로 쓰고(id 는 이름일 뿐이라 그대로도 동작한다) 다음에 읽고 있는 책 목록을 읽을 때 다시 한다.
     * 그 사이 예전 id 로 남긴 완독·별점·감상·회차 시작·읽은 시간도 그때 함께 새 id 로 옮겨 끊기지 않는다. 그 사이 나간 책은 예전 id 그대로 남는다
     * (읽고 있는 책만 바꾼다. 예전 id 로도 기록이 다 이어진다).
     */
    private fun migrateIds(context: Context, books: List<ShelfBook>): List<ShelfBook> {
        fun current(id: String) = id.startsWith(ID_PREFIX) || id.startsWith(MANUAL_PREFIX)
        if (books.all { current(it.id) }) return books
        val moved = books.map { b ->
            if (current(b.id)) return@map b
            val seen = b.seenTitle.ifEmpty { if (b.id.startsWith("auto-")) b.id.removePrefix("auto-") else "" }
            b.copy(id = newId(), seenTitle = seen)
        }
        val renamed = books.zip(moved).filter { (old, new) -> old.id != new.id }
        val copied = runCatching {
            renamed.forEach { (old, new) ->
                val cover = coverFile(context, old.id)
                if (cover.exists()) cover.copyTo(coverFile(context, new.id), overwrite = true)
            }
            Db.get(context).tx {
                write(context, moved)
                for ((old, new) in renamed) for (table in BOOK_ID_TABLES) {
                    update(table, Db.values("book_id" to new.id), "book_id = ?", arrayOf(old.id))
                }
            }
        }
        if (copied.isFailure) {
            moved.filter { m -> books.none { it.id == m.id } }.forEach { coverFile(context, it.id).delete() }
            return books
        }
        // 완독·감상·회차 시작·지운 회차·읽은 시간의 메모리 사본은 옛 id 로 읽었을 수 있다
        cachedFinishes = null; cachedReviews = null; cachedRoundStarts = null; cachedRoundBegans = null; cachedRoundDrops = null
        ReadingLog.clearCache()
        // 목록에 없는 표지·줄인 표지(예전 id 의 것, 앞서 실패한 복사본)를 지운다. 나간 책의 표지는 남긴다.
        val keep = moved.map { it.id } + history(context).map { it.book.id }
        dir(context).listFiles { f ->
            (f.name.endsWith(".jpg") || f.name.endsWith(".png")) && keep.none { f.name.startsWith("$it.") || f.name.startsWith("${it}_") }
        }?.forEach { it.delete() }
        return moved
    }

    private fun writeAtomically(target: File, write: (File) -> Unit) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        write(tmp)
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw IOException("cannot write ${target.name}")
        }
    }
}
