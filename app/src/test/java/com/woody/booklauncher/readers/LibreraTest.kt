package com.woody.booklauncher.readers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Librera 의 app-Recent.json·app-Progress.json. org.json 때문에 Robolectric */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibreraTest {
    private val root = "/storage/emulated/0"

    @Test fun `연 책은 최근 것부터, 저장소 앞부분은 실제 경로로`() {
        // Librera 는 다시 연 책을 끝으로 옮긴다(에뮬레이터에서 둘을 차례로 연 뒤 첫 책을 다시 연 모양)
        val list = LibreraData.parse("""[{"path":"internal-storage:/Download/b.epub","time":1793252300000},""" +
            """{"path":"internal-storage:/Download/mn-p.epub","time":1793252275414},{"path":"internal-storage:/Books/a.pdf","time":1793252400000}]""", root)!!
        assertEquals(listOf("$root/Books/a.pdf", "$root/Download/b.epub", "$root/Download/mn-p.epub"), list.map { it.path })
        assertEquals(1793252400000, list[0].time)
    }

    @Test fun `파일이 아닌 줄(추가한 저장소의 content 주소·경로 없음)은 빼고, 깨진 파일은 null`() {
        val list = LibreraData.parse("""[{"path":"content://com.android.externalstorage.documents/document/1234%3Ab.epub","time":2},""" +
            """{"time":3},{"path":"/storage/1234-5678/c.epub","time":1}]""", root)!!
        assertEquals(listOf("/storage/1234-5678/c.epub"), list.map { it.path })
        assertNull(LibreraData.parse("{", root))
    }

    @Test fun `읽는 동안의 진행 비율은 파일 이름으로, 반올림한 %`() {
        // 에뮬레이터에서 72쪽 책의 둘째 쪽으로 넘긴 뒤의 app-Progress.json 그대로(Librera Pro 10.0.7)
        val all = LibreraData.parseProgress("""{"mn-p.epub":{"cp":false,"d":0,"dc":false,"dp":false,"lk":1,"p":0.027777778,""" +
            """"pt":"을 넘기며 진행률을 확인합니다.","rtl":false,"s":120,"sp":false,"t":1793252308215,"x":0,"y":0,"z":100},"x.pdf":{"lk":2}}""")!!
        assertEquals(3, all["mn-p.epub"])
        // 비율이 없는 책은 모른다
        assertNull(all["x.pdf"])
        assertNull(LibreraData.parseProgress("["))
    }
}
