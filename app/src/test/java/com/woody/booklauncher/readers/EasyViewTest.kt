package com.woody.booklauncher.readers

import org.junit.Assert.assertEquals
import org.junit.Test

/** 이지뷰어·채리뷰어 읽는 화면 위 상태 줄의 쪽 → 진행률([EasyViewSpec.pageInfoPercent]), 제목 자리 → 파일 이름([EasyViewSpec.viewerFileName]) */
class EasyViewTest {
    @Test fun `EPUB 은 장까지 셈한다`() {
        assertEquals(20, EasyViewSpec.pageInfoPercent("2/5[3/12]"))
        assertEquals(1, EasyViewSpec.pageInfoPercent("1/5[1/12]"))
        assertEquals(100, EasyViewSpec.pageInfoPercent("5/5[12/12]"))
    }

    @Test fun `그 밖은 쪽 나누기 전체 쪽`() {
        assertEquals(5, EasyViewSpec.pageInfoPercent("6/101"))
        assertEquals(37, EasyViewSpec.pageInfoPercent("3/8"))
    }

    @Test fun `모르면 -1`() {
        assertEquals(-1, EasyViewSpec.pageInfoPercent(""))
        assertEquals(-1, EasyViewSpec.pageInfoPercent("1/0[0/0]"))
    }

    @Test fun `읽는 화면 위 줄의 파일 이름`() {
        assertEquals("ezview_test1.epub", EasyViewSpec.viewerFileName("ezview_test1.epub#3 3장"))
        assertEquals("ezview_test1.epub", EasyViewSpec.viewerFileName("ezview_test1.epub#1\n1장"))
        assertEquals("ezview_note.txt", EasyViewSpec.viewerFileName("ezview_note.txt"))
        assertEquals("만화.zip", EasyViewSpec.viewerFileName("Books/만화.zip/p01.jpg"))
        assertEquals("ezview_test1", EasyViewSpec.viewerFileName("ezview_test1#2 2장"))
        assertEquals("a#b.epub", EasyViewSpec.viewerFileName("a#b.epub#2 2장"))
        assertEquals("Vol#3 part 2.epub", EasyViewSpec.viewerFileName("Vol#3 part 2.epub#1 표지"))
        assertEquals("Issue#3 Final.cbz", EasyViewSpec.viewerFileName("Issue#3 Final.cbz"))
    }

}
