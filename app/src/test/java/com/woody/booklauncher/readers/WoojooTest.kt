package com.woody.booklauncher.readers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 우주의 RecentBooks.json 과 격자 칸 설명의 저자. org.json 때문에 Robolectric */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WoojooTest {
    /** 에뮬레이터에서 EPUB 둘·PDF 하나를 차례로 연 뒤의 파일 그대로(우주 1.0.22, 최근 것부터) */
    private val json = """[{"title":"우주 사용가이드.pdf","author":"","progress":"1%","file_path":"/storage/emulated/0/Woojoo/Document/우주 사용가이드.pdf","thumb_path":"/storage/emulated/0/Woojoo/RecentBooks/1792757676425.png","time":1792757676425},""" +
        """{"title":"퓨전 둘째 책, 이야기","author":"김철수","progress":"1%","file_path":"/storage/emulated/0/Download/bf-second.epub","thumb_path":"/storage/emulated/0/Woojoo/RecentBooks/1792757587489.png","time":1792757587489},""" +
        """{"title":"퓨전 첫 책","author":"홍길동","progress":"37%","file_path":"/storage/emulated/0/Download/bf-first.epub","thumb_path":"/storage/emulated/0/Woojoo/RecentBooks/1792757405829.png","time":1792757405829}]"""

    @Test fun `최근 책은 최근 것부터, 진행률은 숫자로`() {
        val list = WoojooData.parse(json)!!
        assertEquals(listOf("우주 사용가이드.pdf", "퓨전 둘째 책, 이야기", "퓨전 첫 책"), list.map { it.title })
        assertEquals("김철수", list[1].author)
        assertEquals("/storage/emulated/0/Download/bf-first.epub", list[2].path)
        assertEquals(37, list[2].progress)
        assertEquals("/storage/emulated/0/Woojoo/RecentBooks/1792757676425.png", list[0].thumb)
    }

    @Test fun `파일에 적힌 순서와 상관없이 때로 줄 세운다`() {
        val list = WoojooData.parse("""[{"title":"a","file_path":"/a.epub","time":1},{"title":"b","file_path":"/b.epub","time":3},{"title":"c","file_path":"/c.epub","time":2}]""")!!
        assertEquals(listOf("b", "c", "a"), list.map { it.title })
        assertEquals(-1, list[0].progress)
    }

    @Test fun `경로가 없는 줄은 빼고, 깨진 파일은 null`() {
        assertEquals(listOf("b"), WoojooData.parse("""[{"title":"a","time":1},{"title":"b","file_path":"/b.epub","time":2}]""")!!.map { it.title })
        assertNull(WoojooData.parse("{"))
    }

    @Test fun `읽는 동안의 진행 비율은 파일 이름으로, 반올림한 %`() {
        // 에뮬레이터에서 쪽을 넘긴 뒤의 app-Progress.json 그대로(13/96쪽 = 0.135, 우주 메뉴는 14%)
        val all = WoojooData.parseProgress("""{"wj-two.epub":{"cp":false,"d":0,"lk":1,"p":0.13541667,"s":120,"t":1792758314153,"z":100},""" +
            """"우주 사용가이드.pdf":{"lk":2,"p":0.057142857,"t":1792759231240},"x.epub":{"lk":1}}""")!!
        assertEquals(14, all["wj-two.epub"])
        assertEquals(6, all["우주 사용가이드.pdf"])
        // 비율이 없는 책은 모른다
        assertNull(all["x.epub"])
        assertNull(WoojooData.parseProgress("["))
    }

    @Test fun `격자 칸 설명에서 저자`() {
        assertEquals("최지우", Woojoo.descAuthor("도서 최지우 퓨전 다섯째 책 epub", "퓨전 다섯째 책"))
        assertEquals("김철수", Woojoo.descAuthor("도서 김철수 퓨전 둘째 책, 이야기 epub", "퓨전 둘째 책, 이야기"))
        // 저자가 없는 책은 설명에 빈칸만 남는다
        assertEquals("", Woojoo.descAuthor("도서  우주 사용가이드_v3.0 pdf", "우주 사용가이드_v3.0"))
        // 제목이 설명과 다르면 모른다
        assertEquals("", Woojoo.descAuthor("도서 홍길동 다른 책 epub", "퓨전 첫 책"))
    }
}
