package com.woody.booklauncher.readers

/**
 * 교보eBook 휴대폰·태블릿 판(`com.kyobo.ebook.common.b2c`, 크레마의 [KyoboEbook] 과 다른 앱. 같은 공통 코드라 지금은 이름이 같지만 따로 업데이트되므로 따로 적는다):
 * 서재(`MainV3Activity`, Compose)에는 요소 이름이 없어 읽지 않고, 읽는 화면(크레마 판과 같은 화면 이름)의
 * 진행률 줄과 메뉴의 제목으로 안다.
 */
object KyoboEbookMobile : ReaderApp(
    pkg = "com.kyobo.ebook.common.b2c", classPrefix = "com.kyobo",
    shelves = emptySet(),
    viewers = setOf(
        "com.kyobo.ebook.common.b2c.viewer.epub.ViewerEpubMainActivity",
        "com.kyobo.ebook.common.b2c.viewer.pdf.ViewerPdfMainActivity",
        "com.kyobo.ebook.common.b2c.viewer.comic.ViewerComicMainActivity",
    ),
    titles = emptyList(), thumbs = emptyList(), authors = emptyList(), progress = null, due = null,
    viewerProgress = listOf("bookIndicator"), viewerTitle = "viewer_top_booktitle",
)
