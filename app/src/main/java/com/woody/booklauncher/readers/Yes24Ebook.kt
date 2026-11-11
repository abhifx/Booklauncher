package com.woody.booklauncher.readers

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * YES24 eBook(휴대폰·태블릿용, 크레마의 my YES 와 다른 앱. YES24 도서관·부커스와 같은 회사 코드): 내서재(`PurchaseActivity`)의
 * 읽고 있는 책·책장·구매한 책 칸과 시리즈 책 목록(`PurchaseMetaActivity`)에 제목 `tv_title`(띄어쓰기가 NBSP)·표지 `iv_cover`·저자 `tv_author`. 칸을 누르면 `[제목, 저자, 0%]`.
 * 칸의 진행률(`tv_read_percent`)은 읽는 화면보다 늦게 바뀌어(읽는 화면 1% 인데 0%) 쓰지 않는다. 읽는 화면(도서관과 같은 뷰어)의
 * 하단 정보는 기본이 '모두 표시 안함'이라, 켜면 아래 줄 `ll_page_area`(`1%` 또는 쪽)·`tv_toc_title`. 메뉴에 제목 `tv_title`·쪽 `ll_menu_page_area`(`1 / 519`).
 * 메뉴 제목은 위 막대 안의 것만 읽는다(볼륨키 안내 창 제목도 `tv_title`, [Yes24Viewer]).
 */
object Yes24Ebook : ReaderApp(
    pkg = "com.yes24.ebook.fourth", classPrefix = "com.yes24",
    // 묶음 칸(`총 13권`)을 누르면 뜨는 시리즈 책 목록(`PurchaseMetaActivity`, 칸의 요소 이름이 같다)도 서재: 그 안에서 누른 권이 그 책이다
    // (서재로 보지 않으면 묶음 칸이 누른 칸으로 남아 시리즈 이름으로 들어왔다, 2026-10-24 갤럭시)
    shelves = setOf("com.yes24.ebook.fourth.ui.purchase.PurchaseActivity", "com.yes24.ebook.fourth.ui.purchase.meta.PurchaseMetaActivity"),
    viewers = setOf(
        "com.yes24.ebook.fourth.ui.viewer.epub.EpubActivity",
        "com.yes24.ebook.fourth.ui.viewer.pdf.PDFActivity",
        "com.yes24.ebook.fourth.ui.viewer.comic.ComicActivity",
    ),
    titles = listOf("tv_title"), thumbs = listOf("iv_cover"), authors = listOf("tv_author"), progress = null, due = null,
    viewerProgress = listOf("ll_page_area", "ll_menu_page_area"), viewerTitle = "tv_title", pagesRoundUp = true, eagerCovers = true,
    // 받지 않은 칸의 `다운로드`·묶음 칸(`총 13권`)도 칸 안이라 누르면 그 칸으로 온다: 읽는 화면이 열려야 편 것으로(같은 회사의 YES24 도서관처럼)
    openOnViewer = true,
) {
    override fun titleRoots(windows: AppWindows): List<AccessibilityNodeInfo> = Yes24Viewer.titleRoots(pkg, windows)

    /**
     * 내서재는 보이는 대로 바로 자르는데, 위 탭(읽고 있는 책·책장·구매한 책)을 밀어 넘기는 중에 찍으면 표지 자리에 옆 글자(크레마클럽 띠·제목·저자·
     * `다운로드`)나 옆 탭이 찍혔다(2026-11-10 크레마 페블 사용자 사진, 에뮬에서 탭을 오가며 재현). 앞서 읽었을 때와 표지 자리가 같은 칸만 바로 자르고
     * (넘기기가 끝나 갈 때는 요소 자리가 먼저 멈춰 찍은 뒤 다시 읽기로는 못 가린다), 찍은 뒤에도 그 자리가 그대로인 칸만 쓴다.
     */
    override val eagerWhenStill get() = true

    override val recheckCovers get() = true

    /** 같은 책 칸이 둘이면(받은 책은 `책장` 탭의 `다운로드 책` 책장과 `구매한 책` 탭 모두에 있어, 넘기는 동안 둘이 함께 보인다) 어느 하나라도 그 자리면 된다 */
    override fun coverStill(now: List<Cell>, title: String, rect: Rect): Boolean = now.any { it.title == title && it.thumb == rect }
}
