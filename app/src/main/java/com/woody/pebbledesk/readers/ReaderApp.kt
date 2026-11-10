package com.woody.pebbledesk.readers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.woody.pebbledesk.ShelfBook

/**
 * 이북 앱 하나(읽는 책 자동 추가 [com.woody.pebbledesk.ReaderWatchService] 가 쓴다). 화면 이름·요소 이름과, 서재 칸·누른 칸·
 * 읽는 화면을 읽는 동작을 가진다. 기본 동작은 요소 이름으로 찾는 것이고, 다르게 읽어야 하는 앱은 그 앱 파일에서 메서드를 바꾼다
 * (리디·휴대폰 밀리·e-ink 밀리·문리더·킨들). 앱이 업데이트돼 이름이 바뀌면 그 앱 파일만 고친다. 앱 목록은 [ReaderApps].
 */
open class ReaderApp(
    val pkg: String,
    val classPrefix: String,
    /** 책 칸이 보이는 화면들(서재·탭·책장 안) */
    val shelves: Set<String>,
    /** 읽는 화면들. 오늘 읽은 시간에서도 읽는 화면으로 센다([isViewer]). */
    val viewers: Set<String>,
    /** 서재 칸의 제목(보기 방식마다 이름이 다르면 여럿) */
    val titles: List<String>,
    /** 서재 칸의 표지 그림(보기 방식·화면마다 이름이 다르면 여럿) */
    val thumbs: List<String>,
    /**
     * 서재 칸의 표지 위에 겹쳐 그리는 요소(받기 단추·받는 중 표시·서비스 종료 막 등). 이것이 보이는 칸은 표지를 자르지 않는다
     * (자르면 그 표시가 표지에 박히고, 표지는 한 번 붙으면 다시 찍지 않는다).
     */
    val coverOverlays: List<String> = emptyList(),
    /** 서재 칸의 저자(앞에서부터 먼저 찾은 것) */
    val authors: List<String>,
    /** 서재 칸의 진행률. null 이면 서재의 진행률을 쓰지 않는다(누른 칸의 글자에 있어도). */
    val progress: String?,
    val due: String?,
    /** 읽는 화면의 진행률 줄(앞에서부터 먼저 찾은 것) */
    val viewerProgress: List<String>,
    /**
     * 읽는 화면이 책을 불러오는 중에 보이는 요소. 이것이 보이는 동안의 `0%` 는 진행률로 보지 않는다
     * (밀리는 불러오는 처음 2초쯤 `0%` 를 보이다가 진짜 값으로 바꾸고, 불러오기 문구는 그 뒤로도 20초쯤 남는다).
     */
    val viewerLoading: String? = null,
    /** 읽는 화면 메뉴의 책 제목(메뉴를 띄웠을 때만 보인다) */
    val viewerTitle: String? = null,
    /** 쪽 번호가 지금 쪽·전체 쪽 두 요소로 나뉜 읽는 화면(부커스 메뉴) */
    val viewerPages: Pair<String, String>? = null,
    /** 쪽으로 셈한 진행률을 올림한다(서재에 보이는 % 와 맞추려고. 부커스는 1/368쪽을 1% 로 보인다) */
    val pagesRoundUp: Boolean = false,
    /**
     * 서재를 잠깐만 보고 책으로 넘어가는 앱(알라딘·부커스): 멈추기를 기다리지 않고 표지를 잘라 둔다(멈췄을 때는 모든 앱이 자른다).
     * 밀리는 앱을 다시 열 때 잠깐 칸을 다른 책으로 채우므로 쓰지 않는다.
     */
    val eagerCovers: Boolean = false,
    /**
     * 칸을 눌러도 읽는 화면이 열려야 편 것으로 본다(my YES: 누르면 내려받기·기간 만료 안내가 먼저 뜬다. 밀리·교보도서관·부커스: 받지 않은 책은 누르면 받기만 한다.
     * YES24 도서관·YES24 eBook: 칸 안의 받기·반납 단추도 칸 클릭으로 온다). 그 사이 서재 화면에서 책 칸이 아닌 것을 누르면 그 칸은 버린다([ScreenReader.onClick])
     */
    val openOnViewer: Boolean = false,
    /**
     * 누른 요소가 든 칸을 못 찾았을 때 누른 곳 자리의 칸으로 본다. 끄는 앱: my YES(구매목록 창이 책장 위에 겹쳐 뒤 창의 칸을 고를 수 있다),
     * Libby(칸 안의 `책 열기`·`대출 관리` 단추도 칸 자리에 든다)
     */
    val clickByPlace: Boolean = true,
    /** 앱 화면 전체가 화면 캡처를 막아(검게 찍힌다) 표지를 자르지 않는다(my YES) */
    val secure: Boolean = false,
    /** 서재 편집(지우기·옮기기) 중에만 보이는 요소. 그동안은 칸을 눌러도 책을 고르는 것이라 서재를 읽지 않는다(알라딘) */
    val shelfEditing: String? = null,
    /**
     * 읽는 화면이 따로 액티비티가 아니라 서재 액티비티 안에 뜨는 앱(Libby): 그 묶음의 요소 이름. 이것이 보이는 동안을
     * 읽는 화면 `<서재 액티비티>#<이름>`([viewers] 에 적는다)으로 보고, 읽은 시간도 그 동안만 센다([com.woody.pebbledesk.ReadingLog.viewerShown]).
     * 요소 이름이 아니라 웹 화면 안의 HTML id 로 가르는 앱(Readest, [showsViewer] 를 바꾼다)은 읽는 화면의 이름일 뿐이다.
     */
    val viewerHolder: String? = null,
    /**
     * 책 파일 읽기가 필요하다([BookFiles.canRead]): 꺼져 있으면 책(KOReader)·표지(문리더·네오리더)·표지와 책 정보(이지뷰어·채리뷰어)를 모른다.
     * 깔려 있으면 시작하기에 책 파일 읽기 쪽을 더하고([ReaderApps.fileApps]), 꺼진 채 그 앱의 읽는 화면이 뜨면 홈에서 묻는다. 앱을 더할 때 여기서 정한다.
     */
    val needsBookFiles: Boolean = false,
) {
    /**
     * [cls](액티비티 전체 이름, 서재 안에 뜨는 읽는 화면은 `<서재 액티비티>#<이름>`)가 읽는 화면인가. 오늘 읽은 시간도 이것으로 센다([ReaderApps.isViewer]).
     * 기본: [viewers] 에 있다. 액티비티 이름이 판마다 바뀌는(난독화) 앱(BookFusion)은 깔린 판의 매니페스트(액티비티 속성)로 정한다([context] 는 그때 쓴다).
     */
    open fun isViewer(context: Context, cls: String): Boolean = cls in viewers

    /** 서재에 보이는 책 칸들. 기본: 제목 요소마다 그것이 든 칸을 찾아 읽는다. */
    open fun shelfCells(root: AccessibilityNodeInfo): List<Cell> =
        titles.flatMap { root.byId(pkg, it) }.mapNotNull { title -> cellOf(title)?.let(::readCell) }

    /**
     * 누른 책 칸([source] 는 누른 요소, 누르자마자 화면이 넘어가면 null. [text] 는 알림의 글자, [last] 는 방금 읽어 둔 서재 칸).
     * 기본: 누른 요소가 든 칸 → 알림의 글자(제목·`%`) → 알림의 글자 가운데 방금 본 서재 칸 제목.
     */
    open fun clickedCell(source: AccessibilityNodeInfo?, text: List<CharSequence>, windows: AppWindows, last: List<Cell>?): Cell? =
        source?.let { clickedNode(it, windows) } ?: fromClickText(text) ?: shelfCellByText(text, last)

    /** 요소 이름 없이 자리로 칸을 찾는 서재(화면이 움직이는 중이면 어긋나, 표지를 찍은 뒤에도 칸이 그 자리에 있을 때만 자른다) */
    open val cellsByPosition: Boolean get() = false

    /**
     * 찍은 뒤 다시 읽어 표지 자리가 그대로인 칸만 자르는가([coverStill]). 이름으로 칸을 찾아도 서재가 움직이는 중에 찍힐 수 있는 앱이 켠다
     * (칸을 읽은 뒤 찍기 전에 서재가 움직여 표지 자리에 옆 글자가 찍혔다). 기본: 자리로 칸을 찾는 서재([cellsByPosition])만
     */
    open val recheckCovers: Boolean get() = cellsByPosition

    /**
     * 바로 자르기([eagerCovers])에서 앞서 읽었을 때(1초 넘게 전, [ScreenReader.THROTTLE_MS])와 표지 자리가 같은 칸만 자르는가.
     * 서재가 옆으로 넘어가며 멈추는 동안에는 접근성 요소의 자리가 화면에 그려진 것보다 먼저 멈춘 자리에 가 있어, 찍은 뒤 다시 읽어도([recheckCovers])
     * 같은데 표지 자리에 옆 글자가 찍혔다. 넘긴 직후의 표지는 멈춘 서재에서 자른다([ReaderAdapter.onShelf]). 기본: 보이는 대로 자른다
     */
    open val eagerWhenStill: Boolean get() = false

    /**
     * 찍은 뒤 다시 읽은 서재 칸들 [now](표지 자리가 있는 것만)에 [title] 의 표지 자리가 아직 [rect] 그대로인가([recheckCovers]·[coverOverlays] 인 앱).
     * 기본: 그 제목의 (마지막) 칸이 그 자리다
     */
    open fun coverStill(now: List<Cell>, title: String, rect: Rect): Boolean = now.associate { it.title to it.thumb }[title] == rect

    /** 메뉴를 띄웠을 때 읽는 화면에 제목이 보이는가(보이면 표지만 눌러 연 책도 알 수 있다) */
    open val tellsTitle: Boolean get() = viewerTitle != null

    /**
     * 서재 화면 자체에서 책 칸이 아닌 것(탭 등)을 누르면 앞서 누른 칸을 잊는가([openOnViewer] 앱에서 받기만 한 칸이 그 뒤 연 다른 책 대신 들어가지 않게).
     * 기본: 잊지 않는다(위에 뜬 안내 창의 단추를 서재 화면으로 잘못 볼 수 있는 앱도 있어, 문제를 본 앱만 켠다)
     */
    open val forgetsClickOnOtherTap: Boolean get() = false

    /**
     * 읽는 화면: 본문(웹 화면)은 훑지 않고 이름으로 찾은 요소만 읽는다. 진행률은 아래 줄([viewerProgress])에서,
     * 없으면 메뉴의 쪽([viewerPages])으로. 제목([viewerTitleIds])은 메뉴를 띄웠을 때만 보이고, 같은 이름이 여럿이면(목차 같은 목록) 믿지 않는다.
     * 메뉴·알림 창이 위에 떠 있어도 그 아래 읽는 화면의 진행률 줄을 읽도록 그 앱의 창을 모두 본다.
     */
    open fun readViewer(windows: AppWindows): ViewerInfo {
        val roots = windows.all
        fun texts(id: String, inside: List<AccessibilityNodeInfo> = roots) =
            inside.flatMap { it.byId(pkg, id) }.mapNotNull { textOf(it)?.let(::plain) }.filter { it.isNotEmpty() }
        // 진행률 줄이 여럿이면 앱이 보여 주는 % 를 쪽으로 셈한 것보다 먼저 쓴다(리디 아래 좌·우는 사용자가 고른 대로 쪽·% 가 놓인다).
        // 책을 막 열었을 때의 `페이지 계산중 - 40%` 는 진행률이 아니다(my YES). 책을 불러오는 동안 먼저 보이는 `0%` 도 아니다(밀리).
        val loading by lazy { viewerLoading?.let { texts(it).isNotEmpty() } == true }
        val lines = viewerProgress.flatMap { texts(it) }.filterNot { "계산" in it || (percent(it) == 0 && loading) }
        val progress = lines.firstNotNullOfOrNull { percent(it).takeIf { p -> p >= 0 } }
            ?: lines.firstNotNullOfOrNull { viewerPercent(it, pagesRoundUp).takeIf { p -> p >= 0 } }
            ?: viewerPages?.let { (cur, total) -> pagePercent(texts(cur).firstOrNull(), texts(total).firstOrNull(), pagesRoundUp) }
            ?: -1
        val titleRoots = titleRoots(windows)
        val title = viewerTitleIds.firstNotNullOfOrNull { id -> texts(id, titleRoots).distinct().singleOrNull() }
            ?: return ViewerInfo(null, "", progress)
        val (name, author) = splitTitle(title)
        return ViewerInfo(name, author, progress)
    }

    /**
     * 파일로만 여는 앱([fileEntry])의 책 파일을 그 앱 데이터에서 다시 찾는다: 제목이나 파일 이름(확장자 뗌)이 [titles] 중 하나인 책 파일들의 경로
     * (그 앱이 최근에 다룬 것부터. 파일이 옮겨졌거나 예전 판에서 파일 없이 넣은 책을 홈에서 누를 때). 모르면 빈 목록. 기본: 빈 목록
     */
    open fun bookFiles(context: Context, titles: Collection<String>): List<String> = emptyList()

    /**
     * 서재 칸에서 자른 그림 [crop] 이 이북 앱이 표지 대신 그리는 기본 그림(파일 종류 아이콘 등)인가. 그러면 표지로 두지 않는다
     * (표지는 한 번 붙으면 다시 찍지 않으므로, 진짜 표지가 보일 때 자르게). 화면 스레드가 아닌 곳에서 부른다. 기본: 아니다
     */
    open fun placeholderCover(context: Context, crop: Bitmap): Boolean = false

    /** 읽는 화면 제목을 찾을 요소 이름들(앞에서부터, 글자가 있는 것). 기본: [viewerTitle] 하나 */
    protected open val viewerTitleIds: List<String> get() = listOfNotNull(viewerTitle)

    /** 읽는 화면 제목을 찾을 묶음들. 기본: 그 앱의 창 전체 */
    protected open fun titleRoots(windows: AppWindows): List<AccessibilityNodeInfo> = windows.all

    /** 읽는 화면 제목 글자 → 제목·저자. 기본: 글자 그대로가 제목 */
    protected open fun splitTitle(text: String): Pair<String, String> = text to ""

    /**
     * 서재 액티비티 안에 읽는 화면([viewerHolder])이 떠 있는가. 기본: 그 이름의 요소가 있다(안드로이드 요소 이름으로만 찾는다.
     * 웹 화면 안의 HTML id 로 가르는 앱(Readest)은 바꾼다)
     */
    open fun showsViewer(root: AccessibilityNodeInfo): Boolean = viewerHolder?.let { root.byId(pkg, it).isNotEmpty() } == true

    /** 서비스가 붙을 때마다 새로 만드는 이 앱의 어댑터(판단과 기억). 기본은 화면만 읽는 바탕 [ScreenReader], 다른 길로 책을 아는 앱은 그 앱 파일의 어댑터 */
    open fun newAdapter(): ReaderAdapter = ScreenReader(this)

    /**
     * 깔린 판을 이 앱 파일이 맡는가. 한 패키지가 판에 따라 전혀 다른 앱이 되면(부커스 2.0.0) 판마다 앱 파일을 따로 두고,
     * [ReaderApps.ALL] 에 적은 순서로 이것을 물어 고른다([installed]). 기본: 맡는다
     */
    open fun handles(context: Context): Boolean = true

    /** 읽은 시간을 세는 방식: 기본은 읽는 화면이 앞에 있던 동안. 서재 안에 뜨는 읽는 화면([viewerHolder])은 표시로 자르고 표시가 없으면 안 셈 */
    open val viewerTime: ViewerTime get() = if (viewerHolder != null) ViewerTime.MARKS_CLOSED else ViewerTime.SCREEN

    /** 표시로 읽은 시간을 자르는 액티비티(서재 안에 읽는 화면이 뜨는 앱은 서재 액티비티, 데이터로 아는 앱은 읽는 화면 액티비티) */
    open val markedScreens: Set<String> get() = when (viewerTime) {
        ViewerTime.MARKS_CLOSED -> shelves
        ViewerTime.MARKS_OPEN -> viewers
        ViewerTime.SCREEN -> emptySet()
    }

    /** 내용 바뀜 알림으로 화면을 읽는다(앞에 올 때만 데이터로 맞추는 앱은 아니다) */
    open val readsContent: Boolean get() = true

    /** 읽는 화면이 서재 액티비티 안의 요소라 화면 이름이 알림 없이 바뀐다(내용 바뀜마다 화면 이름을 다시 정한다) */
    open val screenInsideActivity: Boolean get() = viewerHolder != null

    /** 읽는 화면에 다시 들어와도 책 모름이 된 때를 지우지 않는다(탭마다 책이 다른 앱: 같은 모르는 탭에 다시 오면 처음 열린 때를 둔다) */
    open val keepsUnknownOnReenter: Boolean get() = false

    /** 실행 화면이 없어 앱 목록 항목을 따로 두는 앱(네오리더): 파일을 받는 화면과 이름 */
    open val fileEntry: FileViewer? get() = null

    /**
     * 이 앱이 연 책 파일에서 표지를 꺼내는 길([BookFiles.Covers]): 꺼낼 수 있는 책은 화면에서 표지를 자르지 않는다.
     * 기본은 EPUB·그림 폴더([BookFiles.BOOKS]), 만화를 여는 앱은 그 앱 파일에서 [BookFiles.WITH_COMICS] 를 고른다.
     */
    open val fileCovers: BookFiles.Covers get() = BookFiles.BOOKS

    /** 시작하기의 책 파일 읽기 앱 순서(켜야 책이 들어오는 앱(KOReader)이 앞에) */
    open val fileOrder: Int get() = 1

    /** 홈에서 이 책을 연다. 기본: 앱을 연다(책 파일·링크로 바로 여는 앱은 그 앱 파일에서 바꾼다) */
    open fun open(ctx: HomeContext, book: ShelfBook): OpenResult = OpenResult.Launch

    /** 서재 편집 중인가([shelfEditing]) */
    fun editing(root: AccessibilityNodeInfo): Boolean = shelfEditing?.let { root.byId(pkg, it).isNotEmpty() } == true

    /**
     * 누른 책 칸. 이벤트로 받은 요소는 위(부모)로 올라갈 수 없어서, 그 앱의 창(맨 앞 창부터)을 한 번 훑어 누른 요소를 찾고
     * 거기서 위로 올라가며 제목이 하나 든 가장 가까운 묶음을 칸으로 본다(표지 그림만 눌린 것으로 오기도 한다).
     * 창이 겹치는 앱(my YES 구매목록 창 뒤의 책장)에서 위치로 고르면 뒤 창의 칸을 고를 수 있고, 누르자마자 창이 닫히기도 해서 빨리 찾는다.
     * 못 찾으면 누른 곳의 가운데가 들어 있는 칸([clickByPlace] 인 앱), 그래도 없으면 누른 요소 안에서 읽는다.
     */
    private fun clickedNode(source: AccessibilityNodeInfo, windows: AppWindows): Cell? {
        val at = source.bounds()
        for (r in (listOfNotNull(windows.active) + windows.all).distinct()) {
            val path = pathTo(r, source, at) ?: continue
            // 누른 요소에서 위로: 제목이 처음 보이는 묶음이 그 칸(제목이 둘 이상이면 여러 권을 담은 목록이라 칸이 아니다).
            for (n in path.asReversed()) {
                val titleCount = n.count(pkg, titles, stopAt = 1)
                if (titleCount == 0) continue
                if (titleCount == 1 && n.count(pkg, thumbs, stopAt = 0) > 0 && !titleInOtherList(n, at)) readCell(n)?.let { return it }
                break
            }
        }
        if (clickByPlace) {
            windows.active?.let(::shelfCells)?.firstOrNull { it.cell.contains(at.centerX(), at.centerY()) }?.let { return it }
        }
        return cellOf(source)?.let(::readCell)
    }

    /**
     * [root] 에서 [target] 까지의 요소들(root 부터). 이벤트로 받은 요소는 창 번호가 실제 창과 다르게 오기도 해서
     * (my YES 구매목록 창의 단추가 뒤 창 번호로 온다) 요소 이름·종류·위치([at])가 모두 같으면 같은 요소로 본다.
     */
    private fun pathTo(root: AccessibilityNodeInfo, target: AccessibilityNodeInfo, at: Rect): List<AccessibilityNodeInfo>? {
        val path = ArrayList<AccessibilityNodeInfo>()
        val r = Rect()
        fun walk(n: AccessibilityNodeInfo, depth: Int): Boolean {
            n.getBoundsInScreen(r)
            // 누른 요소를 품지 않는 묶음은 들어가지 않는다.
            if (!r.contains(at)) return false
            path += n
            if (n.viewIdResourceName == target.viewIdResourceName && n.className == target.className && r == at) return true
            if (depth < DEEP_WALK_DEPTH) for (i in 0 until n.childCount) if (n.getChild(i)?.let { walk(it, depth + 1) } == true) return true
            path.removeAt(path.size - 1)
            return false
        }
        return if (walk(root, 0)) path else null
    }

    /**
     * [node] 가 들어 있는 책 칸: 위로 올라가며 제목이 하나뿐인 가장 큰 묶음(그 위는 여러 권을 담은 목록, 목록 자체는 칸이 아니다).
     * 표지 그림이 없는 묶음은 책 칸이 아니다(알라딘은 `분야`·`필터` 단추도 같은 이름을 쓴다).
     */
    private fun cellOf(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val at = node.bounds()
        var cell: AccessibilityNodeInfo? = null
        var n: AccessibilityNodeInfo? = node
        for (i in 0 until CELL_UP) {
            // 칸은 목록 안의 한 칸까지(책이 한 권이면 목록 밖까지 제목이 하나뿐이라 화면 전체가 칸이 되었다, 밀리 e-ink)
            val c = n?.takeUnless { it.isList() } ?: break
            val titleCount = c.count(pkg, titles, stopAt = 1)
            if (titleCount > 1 || (titleCount == 1 && titleInOtherList(c, at))) break
            if (titleCount == 1) cell = c
            n = c.parent
        }
        return cell?.takeIf { c -> c.count(pkg, thumbs, stopAt = 0) > 0 }
    }

    /**
     * [n] 안의 제목이 누른 곳([at])을 품지 않는 목록 속에 있는가: 그러면 [n] 은 책 칸이 아니라 목록 밖까지 품은 묶음이다
     * (서재에 책이 한 권뿐일 때 위 탭을 누르면 화면 전체가 그 한 권의 칸으로 보였다, 부커스 내서재).
     */
    private fun titleInOtherList(n: AccessibilityNodeInfo, at: Rect): Boolean {
        fun walk(c: AccessibilityNodeInfo, depth: Int): Boolean {
            if (depth > 0 && c.isList() && !c.bounds().contains(at) && c.count(pkg, titles, stopAt = 0) > 0) return true
            if (depth < WALK_DEPTH) for (i in 0 until c.childCount) if (c.getChild(i)?.let { walk(it, depth + 1) } == true) return true
            return false
        }
        return walk(n, 0)
    }

    /** 책 칸 하나: 칸 아래 요소들을 직접 훑어 이름별 글자를 모은다(누른 칸에서는 이름으로 찾기가 안 된다). */
    protected open fun readCell(cell: AccessibilityNodeInfo): Cell? {
        val texts = mutableMapOf<String, String>()
        var thumb: Rect? = null
        var covered = false
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            val id = n.viewIdResourceName?.substringAfter(":id/")
            val t = n.text?.let(::plain)
            if (id != null && !t.isNullOrEmpty()) texts.putIfAbsent(id, t)
            if (id in thumbs && thumb == null) thumb = n.bounds()
            if (id in coverOverlays && n.isVisibleToUser) covered = true
            if (depth < WALK_DEPTH) for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(cell, 0)
        fun text(id: String?) = id?.let { texts[it] }.orEmpty()
        val title = titles.map(::text).firstOrNull { it.isNotEmpty() } ?: return null
        val author = authors.map(::text).firstOrNull { it.isNotEmpty() }.orEmpty()
        return Cell(title, authorName(author), percent(text(progress)), dueDate(text(due)), thumb.takeUnless { covered }, cell.bounds())
    }

    /**
     * 누른 칸을 못 읽었을 때: 클릭 이벤트의 글자 목록(제목이 맨 앞, `%`·반납 문구는 모양으로 가린다).
     * 탭·단추(`다운로드`, `책장` 등)를 책으로 넣지 않도록 진행률(`%`)이 함께 있을 때만 책 칸으로 본다.
     */
    private fun fromClickText(list: List<CharSequence>): Cell? {
        val items = list.map(::plain).filter { it.isNotEmpty() }
        val clickProgress = items.firstNotNullOfOrNull { percent(it).takeIf { p -> p >= 0 } } ?: return null
        val title = items.firstOrNull()?.takeIf { percent(it) < 0 && !STATUS_ONLY.matches(it) } ?: return null
        // 반납 문구: `반납 4일 남음`, 부커스 격자 보기 칸은 저자 없이 `9일 남음`
        fun isDue(t: String) = "반납" in t || "만료" in t || STATUS_ONLY.matches(t)
        val dueDay = if (due != null) items.firstOrNull(::isDue)?.let(::dueDate) ?: -1 else -1
        val author = if (authors.isNotEmpty()) authorName(items.getOrNull(1)?.takeIf { "%" !in it && !isDue(it) }.orEmpty()) else ""
        // 서재 진행률을 쓰지 않는 앱(읽는 화면보다 늦게 바뀐다)은 누른 글자의 진행률도 쓰지 않는다.
        return Cell(title, author, if (progress != null) clickProgress else -1, dueDay, null, Rect())
    }

    /**
     * 누른 순간 화면이 이미 읽는 화면으로 넘어가 칸을 못 읽었을 때(부커스·YES24 도서관): 누른 글자 가운데 하나가 방금 본 서재 칸의
     * 제목과 똑같으면 그 칸(YES24 도서관은 도서관 이름이 제목보다 앞에 온다). 탭·단추 글자는 책 제목과 같을 일이 없어 책으로 들어가지 않는다.
     */
    private fun shelfCellByText(list: List<CharSequence>, last: List<Cell>?): Cell? {
        val texts = list.map(::plain).filter { it.isNotEmpty() }.toSet()
        return last?.firstOrNull { it.title in texts }?.copy(stale = true)
    }

    /** 글자가 없는 묶음이면 바로 아래 글자들을 이어 붙인다(부커스 아래 줄: `8` ` / ` `371` 세 조각). */
    private fun textOf(n: AccessibilityNodeInfo): String? = n.text?.toString()
        ?: (0 until n.childCount).mapNotNull { n.getChild(it)?.text?.toString() }.joinToString("").ifEmpty { null }
}

/** 한 패키지의 앱 파일들([ReaderApps.ALL] 순서) 가운데 깔린 판을 맡는 것([ReaderApp.handles]). 맡는 것이 없으면 마지막 것 */
internal fun List<ReaderApp>.installed(context: Context): ReaderApp = singleOrNull() ?: firstOrNull { it.handles(context) } ?: last()
