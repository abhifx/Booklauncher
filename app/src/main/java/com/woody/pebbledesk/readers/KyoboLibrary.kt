package com.woody.pebbledesk.readers

import android.view.accessibility.AccessibilityNodeInfo

/**
 * 교보도서관: 서재(목록·표지 보기) 칸에 제목·저자·진행률·반납(`반납 4일 남음`). 표지 보기 칸에는 제목이 없다(`4일`, `47%` 만).
 * 읽는 화면은 본문이 웹 화면이라 훑지 않고, 아래 진행률 줄(`bookIndicator` `2% (4/226p)`)과 메뉴의 제목만 이름으로 읽는다.
 * 받지 않은 칸은 누르면 받기만 하므로 읽는 화면이 열려야 편 것으로 본다([openOnViewer], 2026-10-24 페블에서 받기만 했는데 들어왔다).
 * 받는 중인 칸은 표지 오른쪽 위에 진행 원(가운데 멈춤 단추)을 겹쳐 그려 그런 칸의 표지는 자르지 않는다(2026-10-31 페블에서 원이 표지에 찍혔다).
 * 원은 칸 레이아웃 `kel_study_list_item` 의 받기 묶음 `view_download_progress`(2.2.6)에 들고, 같은 묶음의 받기 단추도 가린다(받지 않은 칸에 보이는지는 미확인).
 * 받은 칸은 묶음이 감춰진다(페블 화면 덤프). [COVER_OVERLAYS]
 */
object KyoboLibrary : ReaderApp(
    pkg = "kr.co.kyobobook.KEL", classPrefix = "com.kyobo",
    shelves = setOf("com.kyobo.ebook.kel.ui.main.MainActivity"),
    viewers = setOf(
        "com.kyobo.ebook.kel.viewer.epub.B2BViewerEpubMainActivity",
        "com.kyobo.ebook.kel.viewer.pdf.B2BViewerPdfMainActivity",
    ),
    titles = listOf("tvTitle"), thumbs = listOf("ivThumbnail"), coverOverlays = COVER_OVERLAYS, authors = listOf("tvAuthor"), progress = "tvReadPercentageTxt", due = "tvRemainDate",
    viewerProgress = listOf("bookIndicator"), viewerTitle = "viewer_top_booktitle", openOnViewer = true,
) {
    /** 받기만 한 칸을 누른 뒤 탭을 눌러 다른 책을 열면 그 칸이 들어왔다(2026-10-24 페블) */
    override val forgetsClickOnOtherTap get() = true

    /**
     * 보이지 않는 칸은 읽지 않는다: 서재 쪽 넘기기(`vpBookShelf`)가 옆 쪽을 화면 밖에 두어 같은 책 칸이 하나 더(자리가 화면 밖, 폭이 음수) 먼저 읽히고,
     * 그 칸의 표지 자리로 찍으려다 표지를 자르지 못했다(2026-10-31 페블).
     */
    override fun readCell(cell: AccessibilityNodeInfo): Cell? = if (!cell.isVisibleToUser) null else super.readCell(cell)
}

/** 표지 위에 겹치는 받기 묶음(받기 단추·진행 원·멈춤 단추) */
private val COVER_OVERLAYS = listOf("bookshelf_main_grid_item_download_layout")
