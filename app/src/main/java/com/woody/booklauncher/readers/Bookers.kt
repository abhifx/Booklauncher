package com.woody.booklauncher.readers

/**
 * 북커스: 내서재 칸에 제목·표지·진행률·대여 기한(다운로드 탭), 리스트 보기에는 저자. 칸을 누르면 알림이 온다(격자 보기는 `[제목, 9일 남음, 1%]`).
 * 읽는 화면(본문 웹 화면)은 하단 정보를 켜면 아래 줄(`ll_page_area`, 쪽 또는 %)이 늘 보이고, 꺼 두면 메뉴의 제목과 쪽(지금 쪽 / 전체 쪽)으로 안다.
 * 받지 않은 칸의 받기 단추를 눌러도 칸 누름으로 와서, 읽는 화면이 열려야 편 것으로 본다([openOnViewer], 2026-10-24 1.0.33 페블에서 받기만 했는데 들어왔다).
 */
object Bookers : ReaderApp(
    pkg = "com.bookers.ebook", classPrefix = "com.bookers",
    shelves = setOf("com.bookers.ebook.ui.purchase.PurchaseActivity"),
    viewers = setOf("com.bookers.ebook.ui.viewer.epub.EpubActivity"),
    titles = listOf("tv_title"), thumbs = listOf("iv_cover"), authors = listOf("tv_author"), progress = "tv_percent", due = "tv_end_date",
    viewerProgress = listOf("ll_page_area"), viewerTitle = "tv_title", viewerPages = "tv_current_page" to "tv_total_page",
    pagesRoundUp = true, eagerCovers = true, openOnViewer = true,
)
