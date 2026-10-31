package com.woody.booklauncher.readers

import com.woody.booklauncher.readers.NeoReader.Meta
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 네오리더 책 정보에서 메뉴 제목·책 제목으로 책 찾기([NeoReader.matchingRows]). 창구 주소(Uri) 때문에 Robolectric */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NeoReaderTest {
    private val photo = "/storage/emulated/0/Download/246-319.pdf"
    private val first = "/storage/emulated/0/Download/1-245까지.pdf"
    private val rows = listOf(Meta("", "", "1-245까지.pdf", first, 10_000), Meta("사진", "", "246-319.pdf", photo, 5_000))

    private fun paths(rows: List<Meta>, vararg titles: String) = NeoReader.matchingRows(rows, titles.toList()).map { it.path }

    @Test fun `파일 안의 제목이나 파일 이름(확장자 뗌)으로`() {
        assertEquals(listOf(photo), paths(rows, "사진"))
        assertEquals(listOf(photo), paths(rows, "246-319"))
        assertEquals(listOf(first), paths(rows, "1-245까지"))
        assertEquals(listOf(first), paths(rows, "없는 책", "1-245까지"))
    }

    @Test fun `제목이 없는 책이 빈 제목으로 맞지 않게`() {
        assertEquals(emptyList<String>(), paths(rows, ""))
    }

    @Test fun `같은 이름이 여럿이면 받은 순서(가장 최근에 저장한 책부터)`() {
        val other = "/storage/emulated/0/Books/246-319.pdf"
        val two = listOf(Meta("", "", "246-319.pdf", other, 9_000)) + rows
        assertEquals(listOf(other, photo), paths(two, "246-319"))
        assertEquals(listOf(first, photo), paths(two, "사진", "1-245까지"))
    }
}
