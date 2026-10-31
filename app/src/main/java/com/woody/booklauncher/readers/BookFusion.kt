package com.woody.booklauncher.readers

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.woody.booklauncher.Storage

/**
 * BookFusion(2.24.1): 앱 코드와 요소 이름이 난독화돼 있다. 요소 이름은 서재 칸의 제목 `title_text_view`·표지 `cover_image_layout` 몇 개만 남고
 * 나머지는 이름이 비어 있다(`패키지:id/`). 읽는 화면 액티비티도 이름이 `o.getLayoutResId`(EPUB)·`o.writePhotoListTofacebook_common_release`(PDF)
 * 처럼 판마다 바뀌는 난독화 이름이라(2.24.0 은 `o.onSuccesslambda10`·`o.getCaption`, 2.23.15 는 `o.startlambda1`·`o.CameraEffectTextures`.
 * 남은 요소 이름·`activities.` 화면 이름은 세 판이 같다) 이름이 아니라 깔린 판의 매니페스트 속성으로 읽는 화면을 정한다([isViewer]).
 * 서재는 `activities.HomeActivity` 하나(Bookshelf).
 * 읽는 화면(본문 웹 화면)에는 제목이 어디에도 없어 책은 서재에서 누른 칸으로만 안다([openOnViewer]: 받지 않은 책은 누르면 받기부터 한다).
 * 서재 칸: 격자 보기·'You're currently reading' 칸은 안드로이드 뷰(저자·제목 글자, 저자는 이름 없음)라 누르면 알림이 온다(줄 칸은 바로 읽는 화면을,
 * 격자 칸은 책 상세를 연다). 목록 보기는 Compose 라 칸에 이름이 없고 눌러도 알림이 없어 읽지 않는다. 대신 칸을 누르면 책 상세(Compose)가 먼저 떠
 * 거기서 `Read` 로 연 책을 안다([webBook]. 표지·저자도 상세에서). 그 밖에 누른 칸을 모른 채 서재에서 읽는 화면으로 넘어오면 책 모름으로 적는다([DetailReader]).
 * '지금 읽는 책' 줄의 순서는 읽은 순서가 아니라 그것으로는 알 수 없다. 서재에 진행률은 없다(그 줄에 진행 고리만).
 * 진행률은 읽는 화면 가운데를 눌러 메뉴를 띄우면 아래 줄 오른쪽 끝(`14.5%`).
 * 계정 책 가운데 이 기기에 받지 않은 책은 표지에 어두운 막과 받기 아이콘(표지 오른쪽 위 칸 안의 그림, 받는 중·올리는 중에는 진행 표시)이 붙어
 * 그 칸의 표지는 자르지 않는다. 그런 책은 '지금 읽는 책' 줄에서 눌러도 책 상세(`Download (157KB)`)가 먼저 뜨고, 받으면 `Read` 로 바뀐다(표지는 상세에서 자른다).
 * 책 바로 열기는 없다(읽는 화면은 앱 안에서만 열리고 받는 링크는 로그인 `bookfusion://auth` 뿐).
 */
object BookFusion : ReaderApp(
    pkg = "com.bookfusion.android.reader", classPrefix = "",
    shelves = setOf("com.bookfusion.android.reader.activities.HomeActivity"),
    viewers = emptySet(),
    titles = listOf("title_text_view"), thumbs = listOf(COVER), authors = emptyList(), progress = null, due = null,
    viewerProgress = emptyList(), openOnViewer = true,
) {
    override fun newAdapter(): ReaderAdapter = DetailReader(this, clickOnly = true, ::webBook)

    /**
     * 읽는 화면(EPUB·PDF): 깔린 판의 매니페스트에서 [readerActivities] 인 액티비티. 이름은 판마다 바뀌지만 이 속성은 세 판이 같고 앱에서 읽는 화면 둘만 갖는다.
     * 예전 판의 읽는 화면도 지금 판에 없는 이름이면 읽는 화면으로 본다([pastReaders]: 업데이트 전에 읽은 시간을 업데이트 뒤에 사용 기록으로 셀 때).
     * 그 판의 업데이트한 때로 기억해 둔다. 같은 화면을 거듭 물으면(내용이 바뀔 때마다) [RECHECK_MS] 동안은 다시 보지 않고, 다른 화면이면 업데이트됐는지 본다
     * (업데이트 뒤 같은 이름이 다른 화면이 될 수 있다).
     */
    override fun isViewer(context: Context, cls: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        val m = manifest?.takeIf { it.asked == cls && now - it.checkedAt < RECHECK_MS } ?: readManifest(context, cls, now)
        return cls in m.readers || (cls in m.past && cls !in m.activities)
    }

    /** 깔린 판(업데이트한 때)의 액티비티 이름들·읽는 화면들, 예전 판까지의 읽는 화면들, 마지막으로 물은 화면·확인한 때(부팅 뒤 ms) */
    private class Manifest(
        val updated: Long, val activities: Set<String>, val readers: Set<String>, val past: Set<String>, val asked: String, val checkedAt: Long,
    )

    @Volatile private var manifest: Manifest? = null

    private fun readManifest(context: Context, cls: String, now: Long): Manifest {
        val pm = context.packageManager
        val updated = runCatching { pm.getPackageInfo(pkg, 0).lastUpdateTime }.getOrNull()
        manifest?.takeIf { updated != null && it.updated == updated }?.let { old ->
            return Manifest(old.updated, old.activities, old.readers, old.past, cls, now).also { manifest = it }
        }
        // 못 읽었으면(지워졌거나 잠깐 실패) 기억하지 않고 다음에 다시 본다. 예전 판의 읽는 화면은 그대로 센다
        val infos = updated?.let { runCatching { pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES).activities }.getOrNull() }
            ?: return Manifest(0, emptySet(), emptySet(), pastReaders(context, emptySet()), cls, now)
        val readers = readerActivities(infos.toList())
        return Manifest(updated, infos.mapTo(HashSet()) { it.name }, readers, pastReaders(context, readers), cls, now).also { manifest = it }
    }

    /** 지금까지 본 판들의 읽는 화면 이름(최근 [PAST_MAX]개, 오래된 것부터). [readers] 를 더해 저장한다 */
    private fun pastReaders(context: Context, readers: Set<String>): Set<String> {
        val prefs = Storage.of(context).getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = prefs.getString(PAST_KEY, null)?.split('\n')?.filter { it.isNotEmpty() }.orEmpty()
        val now = ((old - readers) + readers.sorted()).takeLast(PAST_MAX)
        if (now != old) prefs.edit().putString(PAST_KEY, now.joinToString("\n")).apply()
        return now.toSet()
    }

    /**
     * 읽는 화면의 매니페스트 속성: `launchMode=singleTop` 이고 화면 바뀜(언어·키보드·방향·화면 크기·밀도 등, [READER_CONFIG])을 스스로 처리한다.
     * 2.23.15·2.24.0·2.24.1 모두 이것을 가진 액티비티는 EPUB·PDF 읽는 화면 둘뿐이다(서재 `HomeActivity` 는 singleInstance, 로그인 등은 standard).
     * 안드로이드가 읽을 때 더하는 바뀜(MCC·MNC)이 있어 같은지가 아니라 다 들어 있는지 본다.
     */
    internal fun readerActivities(list: List<ActivityInfo>): Set<String> =
        list.filter { it.launchMode == ActivityInfo.LAUNCH_SINGLE_TOP && it.configChanges and READER_CONFIG == READER_CONFIG }.mapTo(HashSet()) { it.name }

    /** 책 상세 칸은 이름 없이 자리로 찾아, 찍은 뒤에도 같은 자리에 있을 때만 자른다 */
    override val cellsByPosition get() = true

    /** 같은 책이 '지금 읽는 책'과 아래 서재에 함께 보이므로 그 제목의 칸 가운데 하나가 그 자리면 된다 */
    override fun coverStill(now: List<Cell>, title: String, rect: Rect) = now.any { it.title == title && it.thumb == rect }

    /** 격자 보기·'지금 읽는 책' 칸(제목 이름으로)과 책 상세([webBook]: 표지를 잘라 둔다. 목록에 넣는 것은 `Read` 로 읽는 화면이 열릴 때) */
    override fun shelfCells(root: AccessibilityNodeInfo): List<Cell> = super.shelfCells(root) + listOfNotNull(webBook(root))

    /**
     * 격자 보기·'지금 읽는 책' 칸: 제목 `title_text_view` 와 그 앞의 이름 없는 글자(저자. 줄 칸은 제목 뒤에 `Continue` 가 있다), 표지 `cover_image_layout`.
     * 표지 오른쪽 위에 겹친 칸(받지 않은 책의 받기 아이콘, 받는 중·올리는 중 진행 표시)에 무엇이 들어 있거나, 표지가 화면 끝에 걸려 일부만 보이면 자르지 않는다.
     */
    override fun readCell(cell: AccessibilityNodeInfo): Cell? {
        var title: String? = null
        var author: String? = null
        var thumb: Rect? = null
        var coverSeen = false
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            val id = n.viewIdResourceName?.substringAfter(":id/")
            val t = n.text?.let(::plain)?.takeIf { it.isNotEmpty() }
            if (id == "title_text_view") { if (t != null && title == null) title = t }
            // 저자는 제목 앞의 이름 없는 글자만(제목 뒤의 `Continue` 를 저자 없는 책의 저자로 잡지 않게)
            else if (t != null && author == null && title == null && id.isNullOrEmpty()) author = t
            if (id == COVER && !coverSeen) { coverSeen = true; thumb = n.bounds().takeIf { wholeOnScreen(n, it) } }
            if (depth < WALK_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(cell, 0)
        val t = title ?: return null
        val cover = thumb?.takeUnless { r ->
            children(cell).any { k ->
                k.viewIdResourceName?.endsWith(":id/$COVER") != true && k.childCount > 0 && k.isVisibleToUser && Rect.intersects(k.bounds(), r)
            }
        }
        return Cell(t, author.orEmpty(), -1, -1, cover, cell.bounds())
    }

    /**
     * 책 상세(격자·목록 보기 칸, 받지 않은 책은 '지금 읽는 책' 칸도 누르면 뜬다, Compose. 여기서 `Read` 로 읽는 화면을 연다. 받지 않은 책은 `Download` 뒤 `Read`): 글자들과 눌리는 단추(안에 버튼 요소, `Read`)를 가진 정보 칸.
     * 단추 위 글자 가운데 가장 큰 것이 제목, 그 바로 위가 저자(`Language`·`Format` 같은 항목은 제목 아래). 표지는 정보 칸과 같은 묶음의 글자 없는 그림 자리.
     */
    fun webBook(root: AccessibilityNodeInfo): Cell? {
        fun walk(n: AccessibilityNodeInfo, depth: Int): Cell? =
            detail(n) ?: if (depth < DEEP_WALK_DEPTH) (0 until n.childCount).firstNotNullOfOrNull { i -> n.getChild(i)?.let { walk(it, depth + 1) } } else null
        return walk(root, 0)
    }

    /** [holder] 의 자식 가운데 책 상세 정보 칸이 있으면 그 책 */
    private fun detail(holder: AccessibilityNodeInfo): Cell? {
        val kids = children(holder)
        for (card in kids) {
            val parts = children(card)
            val button = parts.singleOrNull { p -> p.isClickable && children(p).any { it.className?.endsWith("Button") == true } }?.bounds() ?: continue
            val texts = parts.filter { it.className?.endsWith("TextView") == true }
                .mapNotNull { p -> p.text?.let(::plain)?.takeIf { it.isNotEmpty() }?.let { it to p.bounds() } }
                .filter { it.second.bottom <= button.top }
            val i = texts.indices.maxByOrNull { texts[it].second.height() } ?: continue
            val (title, at) = texts[i]
            val author = texts.getOrNull(i - 1)?.takeIf { it.second.bottom <= at.top }?.first.orEmpty()
            val cover = kids.firstOrNull { k -> k !== card && k.text.isNullOrEmpty() && children(k).none { !it.text.isNullOrEmpty() } && usableThumb(k.bounds()) }
            return Cell(title, author, -1, -1, cover?.bounds(), Rect())
        }
        return null
    }

    private fun children(n: AccessibilityNodeInfo) = (0 until n.childCount).mapNotNull { n.getChild(it) }

    /**
     * 안드로이드 뷰 [n] 이 화면에 다 보이는가: 화면 자리 [shown] 은 보이는 부분만(화면 끝에 걸리면 잘린다), 부모 안 자리는 뷰 크기 그대로라
     * 둘의 크기가 같아야 한다(격자 칸 표지 230×345 가 아래 끝에 걸리면 화면 자리는 230×208)
     */
    private fun wholeOnScreen(n: AccessibilityNodeInfo, shown: Rect): Boolean {
        val full = Rect().also { @Suppress("DEPRECATION") n.getBoundsInParent(it) }
        return shown.width() >= full.width() - EDGE_PX && shown.height() >= full.height() - EDGE_PX
    }

    /** 메뉴 아래 줄 오른쪽 끝의 진행률(`14.5%`, 이름 없는 글자). 본문 웹 화면은 훑지 않는다. 제목은 없다. */
    override fun readViewer(windows: AppWindows): ViewerInfo {
        var progress = -1
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (n.isWebView() || progress >= 0) return
            n.text?.let(::plain)?.takeIf { PROGRESS.matches(it) }?.let { progress = percent(it) }
            if (depth < DEEP_WALK_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        windows.all.forEach { walk(it, 0) }
        return ViewerInfo(null, "", progress)
    }
}

private const val COVER = "cover_image_layout"

/** 읽는 화면이 스스로 처리하는 화면 바뀜(매니페스트 `configChanges` 0x1fb4: locale·keyboard·keyboardHidden·orientation·screenLayout·uiMode·screenSize·smallestScreenSize·density) */
private const val READER_CONFIG = ActivityInfo.CONFIG_LOCALE or ActivityInfo.CONFIG_KEYBOARD or ActivityInfo.CONFIG_KEYBOARD_HIDDEN or
    ActivityInfo.CONFIG_ORIENTATION or ActivityInfo.CONFIG_SCREEN_LAYOUT or ActivityInfo.CONFIG_UI_MODE or ActivityInfo.CONFIG_SCREEN_SIZE or
    ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE or ActivityInfo.CONFIG_DENSITY

/** 화면 자리와 뷰 크기의 차이를 같은 것으로 보는 만큼(px, 반올림) */
private const val EDGE_PX = 2

/** 예전 판의 읽는 화면 이름을 적어 두는 곳([BookFusion.isViewer]) */
private const val PREFS = "bookfusion"
private const val PAST_KEY = "past_readers"
/** 예전 판의 읽는 화면 이름을 몇 개까지(한 판에 둘. 사용 기록은 일주일 남짓 다시 센다) */
private const val PAST_MAX = 20

/** 같은 화면을 거듭 물을 때 깔린 판을 다시 보지 않는 때 */
private const val RECHECK_MS = 10 * 60_000L

/** 진행률 글자 하나(`14.5%`, `0.0%`) */
private val PROGRESS = Regex("""\d{1,3}(?:[.,]\d+)?\s*%""")
