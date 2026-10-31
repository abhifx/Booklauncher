package com.woody.booklauncher.readers

/**
 * my YES(YES24 서점, 크레마 기본 앱): 첫 화면 기본책장(격자) 칸에 제목 `textView_title`·표지 `grid_book_cover`·저자 `textView_author`
 * (`<김호연> 저`), 그 위에 뜨는 구매목록 창 칸(`ll_list_book`)에 제목 `tv_buylist_title`·작은 표지 `aiv_buylist_thumbnail`·저자.
 * 진행률·반납일은 서재에 없다(기간이 지난 대여 책은 표지 위에 `기간만료`). 읽는 화면(본문 웹 화면) 아래 `bottom_page_no`(`28%`),
 * 메뉴에 제목 `tv_book_title`·`text_space_page`(`28%`). 앱 화면 전체가 화면 캡처를 막아(검게 찍힘, 창 FLAG_SECURE) 표지는 자르지 않는다.
 * USB 디버깅이 켜져 있으면 앱이 스스로 닫힌다.
 */
object MyYes : ReaderApp(
    pkg = "com.yes24.ebook.einkstore", classPrefix = "com.keph",
    shelves = setOf("com.keph.crema.lunar.ui.MainActivity"),
    viewers = setOf(
        "com.keph.crema.lunar.ui.viewer.epub.CremaEPUBActivity",
        "com.keph.crema.lunar.ui.viewer.pdf.CremaPDFActivity",
        "com.keph.crema.lunar.ui.viewer.cpub.CremaCPUBActivity",
        "com.keph.crema.lunar.ui.viewer.txt.CremaTXTActivity",
    ),
    titles = listOf("textView_title", "tv_buylist_title"), thumbs = listOf("grid_book_cover", "aiv_buylist_thumbnail"),
    authors = listOf("textView_author", "tv_buylist_author"), progress = null, due = "tv_book_disable_message",
    viewerProgress = listOf("bottom_page_no", "text_space_page"), viewerTitle = "tv_book_title",
    openOnViewer = true, secure = true, clickByPlace = false,
)
