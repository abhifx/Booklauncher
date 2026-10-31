package com.woody.booklauncher.readers

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Readest 데이터 폴더 읽기([ReadestData])·진행률([roundPercent])·서재 칸의 표지 자리([Readest.coverOf]). JSON·Rect 때문에 Robolectric */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadestTest {
    @get:Rule val tmp = TemporaryFolder()

    private val hash = "fce81ec8c4c0a558017d6eb0aa556958"

    /** 에뮬레이터의 Readest 0.12.16 이 쓴 모양 그대로 */
    private fun books(progress: String = "[21,81]", deleted: String = "null"): File {
        val dir = File(tmp.newFolder(), "Readest/Books").apply { mkdirs() }
        File(dir, "library.json").writeText(
            """[{"hash":"$hash","format":"EPUB","title":"페블 시험 책 : 이지뷰어 편","author":"홍길동","pageCount":81,""" +
                """"createdAt":1792649734759,"deletedAt":$deleted,"updatedAt":1792650248884,"progress":$progress}]"""
        )
        File(dir, hash).apply { mkdirs() }.let { book ->
            File(book, "페블 시험 책 _ 이지뷰어 편.epub").writeText("epub")
            File(book, "cover.png").writeText("png")
            File(book, "nav.json").writeText("{}")
        }
        return dir
    }

    @Test fun `진행률은 Readest 처럼 반올림`() {
        assertEquals(30, roundPercent(24, 81))
        assertEquals(26, roundPercent(21, 81))
        assertEquals(4, roundPercent(3, 81))
        assertEquals(100, roundPercent(81, 81))
        assertEquals(-1, roundPercent(3, 0))
    }

    @Test fun `해시로 제목·저자·진행률`() {
        val dir = books()
        val book = ReadestData(dir).book(hash)!!
        assertEquals("페블 시험 책 : 이지뷰어 편", book.title)
        assertEquals("홍길동", book.author)
        assertEquals(26, book.progress)
        assertEquals(File(dir, "$hash/cover.png"), ReadestData(dir).cover(hash))
    }

    @Test fun `쪽을 넘길 때마다 쓰는 config_json 의 진행률이 먼저`() {
        val dir = books()
        val data = ReadestData(dir)
        assertEquals(26, data.book(hash)!!.progress)
        File(dir, "$hash/config.json").writeText("""{"schemaVersion":3,"progress":[24,81],"location":"epubcfi(/6/8!/4,/2,/6/1:32)"}""")
        assertEquals(30, data.book(hash)!!.progress)
    }

    @Test fun `지운 책·모르는 해시는 없다`() {
        assertNull(ReadestData(books(deleted = "1792650248884")).book(hash))
        assertNull(ReadestData(books()).book("0123456789abcdef0123456789abcdef"))
    }

    @Test fun `진행률이 없으면 -1`() {
        assertEquals(-1, ReadestData(books(progress = "null")).book(hash)!!.progress)
    }

    @Test fun `찍은 뒤 자리 확인은 Readest 만 같은 책 칸 둘 중 하나면 된다`() {
        val top = Rect(30, 218, 348, 682)
        val grid = Rect(30, 816, 348, 1280)
        val now = listOf(Cell("책", "", -1, -1, top, top), Cell("책", "", -1, -1, grid, grid))
        // 책장 줄(위)과 격자(아래)에 같은 책: 위 칸 자리도 그대로로 본다
        assertEquals(true, Readest.coverStill(now, "책", top))
        assertEquals(true, Readest.coverStill(now, "책", grid))
        assertEquals(false, Readest.coverStill(now, "책", Rect(0, 0, 10, 10)))
        // 다른 앱은 예전 그대로(제목마다 마지막 칸)
        assertEquals(false, KyoboLibrary.coverStill(now, "책", top))
        assertEquals(true, KyoboLibrary.coverStill(now, "책", grid))
    }

    @Test fun `서재 칸의 표지 자리`() {
        // 격자 보기(크레마 페블, 3열): 칸 [30,140][348,718], 표지 [30,158][347,621]. 위 여백까지 잡고 표지 아래 끝에서 멈춘다(제목 글자는 640 부터).
        assertEquals(Rect(30, 140, 348, 620), Readest.coverOf(Rect(30, 140, 348, 718)))
        // 목록 보기: 칸 [30,140][1042,395], 표지 [30,158][180,377](세로 가운데, 제목 글자는 211 부터)
        assertEquals(Rect(30, 157, 180, 378), Readest.coverOf(Rect(30, 140, 1042, 395)))
    }
}
