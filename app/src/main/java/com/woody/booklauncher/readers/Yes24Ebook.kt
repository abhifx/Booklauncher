package com.woody.booklauncher.readers

/**
 * YES24 eBook(휴대폰·태블릿용, 크레마의 my YES 와 다른 앱. YES24 도서관·북커스와 같은 회사 코드): 내서재(`PurchaseActivity`)의
 * 읽고 있는 책·책장·구매한 책 칸과 시리즈 책 목록(`PurchaseMetaActivity`)에 제목 `tv_title`(띄어쓰기가 NBSP)·표지 `iv_cover`·저자 `tv_author`. 칸을 누르면 `[제목, 저자, 0%]`.
 * 칸의 진행률(`tv_read_percent`)은 읽는 화면보다 늦게 바뀌어(읽는 화면 1% 인데 0%) 쓰지 않는다. 읽는 화면(도서관과 같은 뷰어)의
 * 하단 정보는 기본이 '모두 표시 안함'이라, 켜면 아래 줄 `ll_page_area`(`1%` 또는 쪽)·`tv_toc_title`. 메뉴에 제목 `tv_title`·쪽 `ll_menu_page_area`(`1 / 519`).
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
)
