package com.woody.booklauncher.readers

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.time.LocalDate

/**
 * 서재 칸 한 권에서 읽은 것. [cell] 은 칸의 화면 위치, [thumb] 는 표지 그림 자리(모르면 null).
 * [stale] 은 방금 읽어 둔 서재 칸을 누른 글자로 맞춘 것(그 뒤 읽는 화면에서 진행률이 바뀌었을 수 있다).
 * [file] 은 파일 목록인 서재(이지뷰어)에서 칸의 파일: 화면에 보이는 위치 + 파일 이름(`#휴대폰/Books/a.epub`, 실제 경로는 [ReaderApp.bookFile]).
 */
data class Cell(
    val title: String, val author: String, val progress: Int, val due: Long, val thumb: Rect?, val cell: Rect,
    val stale: Boolean = false, val file: String? = null,
)

/**
 * 읽는 화면에서 읽은 것. [title]·[author] 는 메뉴에 제목이 보일 때만(모르면 null), [progress] 는 모르면 -1.
 * [file] 은 제목 대신 파일 이름만 보이는 읽는 화면(이지뷰어)의 파일 이름(그 파일로 넣은 책을 찾는다, 모르면 null).
 * [chapter] 는 진행률 대신 보이는 읽는 화(Mihon 메뉴의 화 이름, 모르면 "")
 */
class ViewerInfo(val title: String?, val author: String, val progress: Int, val file: String? = null, val chapter: String = "")

/**
 * 실행 화면이 없어 파일로만 여는 이북 앱(네오리더). [activity] 는 파일(`ACTION_VIEW`)을 받는 화면, [label] 은 책의 앱 이름.
 * 이 앱의 책은 파일을 알 때만 넣는다(홈에서 그 파일을 연다)
 */
class FileViewer(val activity: String, val label: Int)

/**
 * 이북 앱 데이터에서 찾은 책(네오리더·우주의 데이터, KOReader 기록). [progress] 는 데이터에 진행률이 있으면(KOReader, 모르면 -1).
 * 열고 닫은 때를 아는 앱(KOReader): [openedAt] 은 이 책을 연 때, [closedAt] 은 닫았으면 닫은 때(열려 있으면 0. ms, 모르면 0)
 */
class FileBook(
    val title: String, val author: String, val path: String, val progress: Int = -1,
    val openedAt: Long = 0, val closedAt: Long = 0,
)

/** 화면에 떠 있는 그 앱의 창들. [active] 는 맨 앞 창(다른 앱이면 null), [all] 은 위에 뜬 메뉴·알림 창이 먼저이고 화면 자체(읽는 화면·서재)가 끝 */
class AppWindows(val active: AccessibilityNodeInfo?, val all: List<AccessibilityNodeInfo>)

/** 책 칸을 찾을 때 위로 올라가는 단계, 칸 안을 훑는 깊이 */
internal const val CELL_UP = 8
internal const val WALK_DEPTH = 12
/** 창 전체를 훑는 깊이(요소 이름이 없는 서재, 누른 요소 찾기. React Native 는 칸이 60단계쯤 아래에 있다) */
internal const val DEEP_WALK_DEPTH = 100
/** 이보다 작게 보이는 표지 칸은 자르지 않는다(가려졌거나 화면 밖) */
private const val MIN_THUMB_PX = 40
/** 표지 칸의 세로 ÷ 가로가 이보다 작으면 일부만 보이는 칸으로 본다(표지는 1.4~1.5) */
private const val MIN_COVER_RATIO = 1.1f

/** 자를 만한 표지 칸: 너무 작지 않고(가려졌거나 화면 밖), 세로로 긴 표지 모양(화면 위·아래에 걸쳐 일부만 보이면 아님) */
fun usableThumb(r: Rect) =
    r.width() >= MIN_THUMB_PX && r.height() >= MIN_THUMB_PX && r.height() >= r.width() * MIN_COVER_RATIO

internal fun AccessibilityNodeInfo.bounds() = Rect().also { getBoundsInScreen(it) }

internal fun AccessibilityNodeInfo.isWebView() = className?.endsWith("WebView") == true

/** 여러 칸을 담는 목록(RecyclerView·ListView·GridView, Compose 목록은 목록 정보로) */
internal fun AccessibilityNodeInfo.isList() = collectionInfo != null || className?.let { LIST_CLASS.containsMatchIn(it) } == true

private val LIST_CLASS = Regex("""(RecyclerView|ListView|GridView)$""")

internal fun AccessibilityNodeInfo.byId(pkg: String, id: String): List<AccessibilityNodeInfo> =
    findAccessibilityNodeInfosByViewId("$pkg:id/$id")

/** 아래에 이름이 [id] 인 요소가 몇 개인가. 이벤트로 받은 요소에서는 이름으로 찾기가 안 되어 직접 훑는다. */
internal fun AccessibilityNodeInfo.count(pkg: String, id: String): Int = count(pkg, listOf(id))

/**
 * 아래에 이름이 [ids] 가운데 하나인 요소가 몇 개인가(이름마다 센 수의 합. 한 번만 훑는다).
 * [stopAt] 개를 넘으면 더 세지 않고 그때까지 센 수를 돌려준다(있는지·하나뿐인지만 볼 때).
 */
internal fun AccessibilityNodeInfo.count(pkg: String, ids: List<String>, stopAt: Int = Int.MAX_VALUE): Int {
    val full = ids.groupingBy { "$pkg:id/$it" }.eachCount()
    var c = 0
    fun walk(n: AccessibilityNodeInfo, depth: Int) {
        n.viewIdResourceName?.let { c += full[it] ?: 0 }
        if (depth < WALK_DEPTH) for (i in 0 until n.childCount) {
            if (c > stopAt) return
            n.getChild(i)?.let { walk(it, depth + 1) }
        }
    }
    walk(this, 0)
    return c
}

/** 화면 글자를 보통 글자로(YES24 eBook 서재 제목은 띄어쓰기가 줄바꿈 없는 공백 U+00A0 이다) */
internal fun plain(text: CharSequence): String = text.toString().replace(' ', ' ').trim()

/** 제목이 아니라 상태 글자(`4일`, `반납 4일 남음`, `D-3`, `만료`) — 교보도서관 표지 보기 칸은 제목 없이 이것만 있다 */
internal val STATUS_ONLY = Regex("""^(반납\s*)?(\d+\s*일(\s*남음)?|D-?\d+|만료|오늘 반납)$""")

/** `한강 지음`, `한스 로슬링 지음 / 이창신 옮김`, `<정지원,염선형 저>/ 미래의창` → 지은이만 */
internal fun authorName(text: String): String {
    val t = text.trim().removePrefix("<").substringBefore(">")
    return (AUTHOR_ROLE.find(t)?.let { t.substring(0, it.range.first) } ?: t).trim()
}

/** 지은이 뒤의 `지음`·`저`(뒤에 옮긴이·출판사가 이어질 수 있다) */
private val AUTHOR_ROLE = Regex("""\s+(지음|저)(?=\s*($|/|,))""")

/** 파일로 연 책의 확장자(읽는 화면 제목이 파일 이름일 때 뗀다. 문리더·네오리더. 이지뷰어는 파일 목록 칸 중 이것만 책으로 본다) */
private val BOOK_FILE = Regex("""\.(txt|html?|xhtml|mht|mhtml|md|rtf|docx?|odt|fb2(\.zip)?|chm|epub|mobi|prc|azw3?|pdf|djvu|cbz|cbr|zip|rar|cb7|7z|caj)$""", RegexOption.IGNORE_CASE)

/** 그림 파일(이지뷰어는 그림 폴더를 한 권으로 본다) */
private val IMAGE_FILE = Regex("""\.(jpe?g|png|gif|webp|bmp|heic|avif)$""", RegexOption.IGNORE_CASE)

internal fun isImageFile(name: String) = IMAGE_FILE.containsMatchIn(name)

/** `이름.epub` → `이름`(책 파일 확장자만 뗀다) */
internal fun withoutBookFile(text: String): String = text.replace(BOOK_FILE, "")

/** 책 파일 이름인가(확장자가 [BOOK_FILE]) */
internal fun isBookFile(name: String) = BOOK_FILE.containsMatchIn(name)

/** 같은 파일 이름인가(대소문자 무시, 한쪽이 확장자를 숨긴 이름이어도. 이지뷰어 목록의 '첫글자를 대문자로'·읽는 화면의 확장자 숨김 설정) */
internal fun sameFileName(a: String, b: String) =
    a.equals(b, ignoreCase = true) || withoutBookFile(a).equals(withoutBookFile(b), ignoreCase = true)

/** `47%` → 47, 소수(문리더 `30.9%`)는 버림. 없으면 -1 */
internal fun percent(text: CharSequence): Int =
    PERCENT.find(text)?.groupValues?.get(1)?.toInt()?.coerceIn(0, 100) ?: -1

/** 읽는 화면의 진행률: `2% (4/226p)` 처럼 % 가 있으면 그것, 없으면 쪽 `6 / 368` 로 계산(알라딘·부커스). 모르면 -1 */
internal fun viewerPercent(text: CharSequence, roundUp: Boolean): Int {
    percent(text).takeIf { it >= 0 }?.let { return it }
    val m = PAGES.find(text) ?: return -1
    return pagePercent(m.groupValues[1], m.groupValues[2], roundUp)
}

/** 지금 쪽 ÷ 전체 쪽(버림, [roundUp] 이면 올림). 숫자가 아니면 -1 */
internal fun pagePercent(page: String?, total: String?, roundUp: Boolean): Int {
    val p = page?.trim()?.toLongOrNull() ?: return -1
    val t = total?.trim()?.toLongOrNull()?.takeIf { it > 0 } ?: return -1
    val pct = if (roundUp) (p * 100 + t - 1) / t else p * 100 / t
    return pct.toInt().coerceIn(0, 100)
}

/**
 * `2026.10.20` → 그날, `반납 4일 남음`·`D-4`·`Due In 4 Days`(Libby 영어) → 오늘 + 4일, `오늘 반납`·`5시간`·`3분`·`Due In 5 Hours` → 오늘,
 * `만료`·`Expired`·`Due Now` → 어제(지남). 모르면 -1. Libby 는 `만료일: 14일`·`만료일: 2시간`·`만료일: 3분`(영어 `Due In 1 Day`·`Due In 2 Hours`·
 * `Due In 3 Minutes`) 처럼 남은 기간 앞에 '만료'를 붙여 기간을 먼저 보고, 반납 시각이 지나면 `지금 만료`(`Due Now`)다(2026-10-16 에뮬레이터 날짜를 옮겨 확인).
 */
internal fun dueDate(text: String): Long {
    if (text.isEmpty()) return -1
    DUE_DATE.find(text)?.let { m ->
        val (y, mo, d) = m.destructured
        runCatching { return LocalDate.of(y.toInt(), mo.toInt(), d.toInt()).toEpochDay() }
    }
    val today = LocalDate.now().toEpochDay()
    DUE_D.find(text)?.let { return today + it.groupValues[1].toLong() }
    if (text.equals("D-Day", ignoreCase = true)) return today
    DUE_DAYS.find(text)?.let { return today + it.groupValues[1].toLong() }
    if (DUE_HOURS.containsMatchIn(text)) return today
    // '오늘'을 '만료'보다 먼저 본다(`오늘 만료` 처럼 둘 다 있으면 오늘).
    if ("오늘" in text || "today" in text.lowercase()) return today
    if ("만료" in text || DUE_PAST.containsMatchIn(text)) return today - 1
    return -1
}

// 서재 칸마다 부르므로 한 번만 만든다([percent]·[viewerPercent]·[dueDate])
private val PERCENT = Regex("""(\d{1,3})(?:[.,]\d+)?\s*%""")
private val PAGES = Regex("""(\d+)\s*/\s*(\d+)""")
private val DUE_DATE = Regex("""(\d{4})\s*[.\-/년]\s*(\d{1,2})\s*[.\-/월]\s*(\d{1,2})""")
private val DUE_D = Regex("""D-\s*(\d+)""", RegexOption.IGNORE_CASE)
private val DUE_DAYS = Regex("""(\d+)\s*(일|days?\b)""", RegexOption.IGNORE_CASE)
private val DUE_HOURS = Regex("""\d+\s*(시간|분|(hours?|minutes?)\b)""", RegexOption.IGNORE_CASE)
private val DUE_PAST = Regex("""\b(expired|due now)\b""", RegexOption.IGNORE_CASE)
