package com.woody.booklauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 표지 검색: 검색처 가르기(한글), Apple Books·Open Library 결과 읽기 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookSearchTest {
    @Test fun `한글이 있으면 한국어 책`() {
        assertTrue(BookSearch.hasHangul("채식주의자"))
        assertTrue(BookSearch.hasHangul("Project Hail Mary 한국어판"))
        assertFalse(BookSearch.hasHangul("Project Hail Mary"))
        assertFalse(BookSearch.hasHangul("ノルウェイの森"))
    }

    @Test fun `Apple 결과는 표지 주소의 크기를 바꾸고 표지 없는 것은 뺀다`() {
        val json = """{"resultCount":3,"results":[
            {"trackId":1526997052,"trackName":"Project Hail Mary","artistName":"Andy Weir",
             "artworkUrl100":"https://is1-ssl.mzstatic.com/image/thumb/Publication211/v4/13/9780593135211.d.jpg/100x100bb.jpg"},
            {"trackId":2,"trackName":"No cover","artistName":"X"},
            {"trackId":1526997052,"trackName":"Project Hail Mary","artistName":"Andy Weir",
             "artworkUrl100":"https://is1-ssl.mzstatic.com/image/thumb/a.jpg/100x100bb.jpg"}]}"""
        val books = BookSearch.parseApple(json)
        assertEquals(1, books.size)
        with(books[0]) {
            assertEquals("a1526997052", id)
            assertEquals("Project Hail Mary", title)
            assertEquals("Andy Weir", author)
            assertEquals("https://is1-ssl.mzstatic.com/image/thumb/Publication211/v4/13/9780593135211.d.jpg/400x400bb.jpg", thumbUrl)
            assertEquals("https://is1-ssl.mzstatic.com/image/thumb/Publication211/v4/13/9780593135211.d.jpg/1000x1000bb.jpg", coverUrl)
        }
    }

    @Test fun `Open Library 결과는 표지 번호로 주소를 만들고 저자를 잇는다`() {
        val json = """{"numFound":3,"docs":[
            {"author_name":["Andy Weir"],"cover_i":11200092,"title":"Project Hail Mary"},
            {"author_name":["Andy Weir"],"title":"The Martian / Artemis / Project Hail Mary"},
            {"author_name":["A","B"],"cover_i":7,"title":"Two"},
            {"cover_i":8,"title":"No author"}]}"""
        val books = BookSearch.parseOpenLibrary(json, "")
        assertEquals(listOf("o11200092", "o7", "o8"), books.map { it.id })
        assertEquals("https://covers.openlibrary.org/b/id/11200092-L.jpg", books[0].thumbUrl)
        assertEquals("https://covers.openlibrary.org/b/id/11200092-L.jpg", books[0].coverUrl)
        assertEquals("A, B", books[1].author)
        assertEquals("", books[2].author)
    }

    @Test fun `Open Library 는 검색어 낱말이 제목·저자에 모두 든 것만`() {
        val json = """{"docs":[
            {"author_name":["Andy Weir"],"cover_i":1,"title":"Project Hail Mary"},
            {"author_name":["SuperSummary"],"cover_i":2,"title":"Study Guide -- The Other Wes Moore"},
            {"author_name":["William Shakespeare"],"cover_i":3,"title":"Othello"}]}"""
        assertEquals(listOf("o1"), BookSearch.parseOpenLibrary(json, "Project Hail Mary").map { it.id })
        assertEquals(listOf("o1"), BookSearch.parseOpenLibrary(json, "project hail-mary  Andy Weir").map { it.id })
        assertEquals(listOf("o3"), BookSearch.parseOpenLibrary(json, "Othello, Shakespeare").map { it.id })
    }

    @Test fun `결과 칸이 없으면 빈 목록`() {
        assertEquals(emptyList<BookResult>(), BookSearch.parseApple("""{"resultCount":0}"""))
        assertEquals(emptyList<BookResult>(), BookSearch.parseOpenLibrary("""{"numFound":0}""", "x"))
    }
}
