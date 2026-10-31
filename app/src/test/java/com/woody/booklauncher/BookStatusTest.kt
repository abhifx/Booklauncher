package com.woody.booklauncher

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

/** 홈·목록·상세의 상태 줄([bookStatus]): 읽는 화를 알면 % 대신 그것만(Futon 은 둘 다 들어온다), 모르면 %, 반납일은 늘 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookStatusTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val today = LocalDate.of(2026, 10, 28)
    private fun book(progress: Int, chapter: String = "", due: Long = -1) = ShelfBook("b-1", "Sea Song", "", null, 0, progress, due, chapter = chapter)
    private val pct get() = context.getString(R.string.progress_pct, 33)

    @Test fun `읽는 화를 알면 % 는 빼고 화 이름만`() = assertEquals(listOf("Unnamed chapter"), context.bookStatus(book(33, "Unnamed chapter"), today))

    @Test fun `읽는 화를 모르면 %`() = assertEquals(listOf(pct), context.bookStatus(book(33), today))

    @Test fun `반납일은 읽는 화와 함께`() =
        assertEquals(listOf("Ch. 12", context.getString(R.string.due_days, 4)), context.bookStatus(book(33, "Ch. 12", today.toEpochDay() + 4), today))
}
