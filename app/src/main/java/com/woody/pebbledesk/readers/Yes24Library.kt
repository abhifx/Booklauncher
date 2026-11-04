package com.woody.pebbledesk.readers

/**
 * YES24 도서관: 내 서재 칸(격자·목록 보기 같은 이름)에 제목·표지·저자(`<지은이 저>`)·반납(`D-15`). 진행 막대(`pb_read_percent`)는
 * 읽는 화면과 맞지 않아(읽는 화면 8% 인데 0) 쓰지 않는다. 읽는 화면(본문 웹 화면, 부커스와 같은 뷰어)은 아래에 진행률(`tv_percent`),
 * 뷰어 설정에서 하단 정보를 켜면 그 자리에 쪽(`ll_page_area`, `14` `/` `283` 세 조각)이 보인다. 메뉴에 제목(`tv_title`)·쪽(`ll_menu_page_area`).
 * 휴대폰·태블릿판(`com.yes24.library`, '예스24 전자도서관 (리뉴얼)')은 패키지만 다르고 화면·요소 이름은 같다(1.0.61, 에뮬에서 확인).
 * 칸 안의 내려받기·`반납`·`연장` 단추를 눌러도 그 칸의 클릭(제목)이 와서, 읽는 화면이 열릴 때 편 것으로 본다([openOnViewer]).
 */
open class Yes24LibrarySpec(pkg: String) : ReaderApp(
    pkg = pkg, classPrefix = "com.yes24",
    shelves = setOf("com.yes24.library.shelf.LibShelfActivity"),
    viewers = setOf(
        "com.yes24.ebook.fourth.ui.viewer.epub.EpubActivity",
        "com.yes24.ebook.fourth.ui.viewer.pdf.PDFActivity",
        "com.yes24.ebook.fourth.ui.viewer.comic.ComicActivity",
    ),
    titles = listOf("tv_title"), thumbs = listOf("iv_cover"), authors = listOf("tv_author"), progress = null, due = "tv_d_day",
    viewerProgress = listOf("tv_percent", "ll_page_area", "ll_menu_page_area"), viewerTitle = "tv_title", pagesRoundUp = true, eagerCovers = true,
    openOnViewer = true,
)

/** e-ink 판(크레마 등) */
object Yes24Library : Yes24LibrarySpec("com.yes24.library.eink")

/** 휴대폰·태블릿판(iReader Ocean 5 Pro 등 Play 스토어 기기) */
object Yes24LibraryMobile : Yes24LibrarySpec("com.yes24.library")
