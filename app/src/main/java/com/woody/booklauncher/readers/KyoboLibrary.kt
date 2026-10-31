package com.woody.booklauncher.readers

/**
 * 교보도서관: 서재(목록·표지 보기) 칸에 제목·저자·진행률·반납(`반납 4일 남음`). 표지 보기 칸에는 제목이 없다(`4일`, `47%` 만).
 * 읽는 화면은 본문이 웹 화면이라 훑지 않고, 아래 진행률 줄(`bookIndicator` `2% (4/226p)`)과 메뉴의 제목만 이름으로 읽는다.
 * 받지 않은 칸은 누르면 받기만 하므로 읽는 화면이 열려야 편 것으로 본다([openOnViewer], 2026-10-24 페블에서 받기만 했는데 들어왔다).
 */
object KyoboLibrary : ReaderApp(
    pkg = "kr.co.kyobobook.KEL", classPrefix = "com.kyobo",
    shelves = setOf("com.kyobo.ebook.kel.ui.main.MainActivity"),
    viewers = setOf(
        "com.kyobo.ebook.kel.viewer.epub.B2BViewerEpubMainActivity",
        "com.kyobo.ebook.kel.viewer.pdf.B2BViewerPdfMainActivity",
    ),
    titles = listOf("tvTitle"), thumbs = listOf("ivThumbnail"), authors = listOf("tvAuthor"), progress = "tvReadPercentageTxt", due = "tvRemainDate",
    viewerProgress = listOf("bookIndicator"), viewerTitle = "viewer_top_booktitle", openOnViewer = true,
) {
    /** 받기만 한 칸을 누른 뒤 탭을 눌러 다른 책을 열면 그 칸이 들어왔다(2026-10-24 페블) */
    override val forgetsClickOnOtherTap get() = true
}
