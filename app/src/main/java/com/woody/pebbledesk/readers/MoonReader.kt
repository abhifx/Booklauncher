package com.woody.pebbledesk.readers

import android.view.accessibility.AccessibilityNodeInfo

/**
 * 문리더(내 파일을 여는 리더): 서재(최근 목록·책장, 목록·격자 보기 같은 이름) 칸에 제목 `myBookName`·표지 `myBookImage`·
 * 진행률 `progresTv`, 목록 보기에 저자 `myBookAuthor`(격자 보기는 저자순 정렬일 때만 `myAuthor`). 칸을 누르면 알림이 온다.
 * 책장의 묶음 칸은 진행률 줄을 감춘다. 홈 탭의 '최근 목록' 줄과 책장 보기(`shelfBookList`, 나무 선반)는 칸에 표지(`myBookImage`)만 있어(누르면 빈 알림)
 * 읽는 화면 메뉴의 제목(`txtTitle`)으로 안다. 그 칸들의 표지는 제목을 몰라 자르지 못한다(책장 보기는 나무 바탕·⋮ 점도 같이 잘린다): 표지는
 * 최근 목록 화면·책장 목록(목록·격자 보기)에서 연 책만. 문리더는 연 파일을 어디에도 내놓지 않아(10.8 코드) 책 파일로는 표지를 꺼내지도, 홈에서 그 책을 바로 열지도 않는다
 * (1.7.2 까지는 메뉴 제목으로 저장소 전체에서 EPUB 을 찾아 표지를 꺼냈는데, 같은 책이 여러 폴더에 있으면 맞는 파일인지 알 수 없어 뺐다).
 * 읽는 화면(본문은 직접 그리는 뷰라 글자가 드러나지 않는다) 아래 상태 줄 오른쪽 `statusRight` 에 진행률(`30.9%`, 설정에 따라
 * `12/300`), 메뉴에 `txtTextViewPercent`(`30%`). 상태 줄 가운데(`statusMiddle`, `2장 (5/8)`)는 장 안의 쪽이라 쓰지 않는다.
 * 유료판(`com.flyersoft.moonreaderp`)은 같은 코드로 만든 앱이라 요소 이름은 같고, 화면 클래스도 그 패키지 아래에 있다
 * (`com.flyersoft.moonreaderp.ActivityMain`·`ActivityTxt`, 10.7).
 */
open class MoonReaderApp(pkg: String) : ReaderApp(
    pkg = pkg, classPrefix = pkg,
    shelves = setOf("$pkg.ActivityMain"),
    viewers = setOf("$pkg.ActivityTxt"),
    titles = listOf("myBookName"), thumbs = listOf("myBookImage"), authors = listOf("myBookAuthor", "myAuthor"), progress = "progresTv", due = null,
    viewerProgress = listOf("statusRight", "txtTextViewPercent"), viewerTitle = "txtTitle",
    eagerCovers = true,
) {
    /**
     * 진행률이 없는 칸은 책이 아니다(책장의 묶음 칸은 진행률 줄을 감춘다). 보이지 않는 칸도 아니다: 한 번 연 '최근 목록' 화면이 책장 보기 뒤에
     * 남아 있어, 책장 보기에서 누른 자리가 그 화면의 다른 책 칸으로 잡힌다.
     */
    override fun readCell(cell: AccessibilityNodeInfo): Cell? =
        if (!cell.isVisibleToUser) null else super.readCell(cell)?.takeIf { it.progress >= 0 }

    /**
     * 메뉴 제목 → 제목·저자. EPUB 등은 `제목 - 저자`(저자가 없으면 제목만)라 마지막 ` - ` 에서 나눈다.
     * TXT·HTML 등은 파일 이름(`이름.txt`)이라 확장자만 뗀다(`저자 - 제목.txt` 같은 파일 이름은 나누지 않는다).
     */
    override fun splitTitle(text: String): Pair<String, String> {
        withoutBookFile(text).takeIf { it != text }?.let { return it to "" }
        val i = text.lastIndexOf(" - ")
        return if (i > 0) text.substring(0, i).trim() to text.substring(i + 3).trim() else text to ""
    }
}

/** 무료판 */
object MoonReader : MoonReaderApp("com.flyersoft.moonreader")

/** 유료판(Moon+ Reader Pro) */
object MoonReaderPro : MoonReaderApp("com.flyersoft.moonreaderp")
