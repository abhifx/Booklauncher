package com.woody.booklauncher.readers

/**
 * 교보eBook(크레마 e-ink 판 `com.kyobo.ebook.eink`, 휴대폰 판 [KyoboEbookMobile] 과 다른 앱): 서재(Compose)에는 요소 이름이 없고 격자 보기에는 제목도 없어 서재는 읽지 않는다.
 * 교보도서관과 같은 읽는 화면이라 진행률 줄과 메뉴의 제목으로 안다(서재·내 책장 어디서 열어도).
 */
object KyoboEbook : ReaderApp(
    pkg = "com.kyobo.ebook.eink", classPrefix = "com.kyobo",
    shelves = emptySet(),
    viewers = setOf(
        "com.kyobo.ebook.common.b2c.viewer.epub.ViewerEpubMainActivity",
        "com.kyobo.ebook.common.b2c.viewer.pdf.ViewerPdfMainActivity",
        "com.kyobo.ebook.common.b2c.viewer.comic.ViewerComicMainActivity",
    ),
    // 서재(shelves)를 읽지 않으므로 칸 요소 이름(titles·thumbs)은 쓰이지 않는다.
    titles = emptyList(), thumbs = emptyList(), authors = emptyList(), progress = null, due = null,
    viewerProgress = listOf("bookIndicator"), viewerTitle = "viewer_top_booktitle",
)
