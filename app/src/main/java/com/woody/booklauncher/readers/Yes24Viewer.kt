package com.woody.booklauncher.readers

import android.view.accessibility.AccessibilityNodeInfo

/**
 * YES24 뷰어(`com.yes24.ebook.fourth.ui.viewer…`)의 읽는 화면 제목(쓰는 앱: YES24 eBook·YES24 도서관 두 판. 바꾸면 모두 시험한다).
 * 메뉴(EPUB·PDF·만화)의 제목 `tv_title` 은 위 막대 `cl_top_area` 안에 있다. 읽는 화면에 뜨는 볼륨키 안내 창
 * (`볼륨키로 페이지를 넘겨보세요.`, 기기에 따라. 에뮬에서는 뜨지 않았다)의 제목도 `tv_title` 이라 위 막대 안의 것만 제목으로 본다
 * (안내 창 제목이 책으로 들어갔다, 2026-11-10 크레마 페블 YES24 eBook. 레이아웃은 YES24 도서관 e-ink 판 APK 에서 확인).
 */
internal object Yes24Viewer {
    fun titleRoots(pkg: String, windows: AppWindows): List<AccessibilityNodeInfo> = windows.all.flatMap { it.byId(pkg, "cl_top_area") }
}
