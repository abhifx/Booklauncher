package com.woody.booklauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** 완독한 날 입력([parseDay]): 구분자는 무엇이든, 숫자 여덟 자 */
class ParseDayTest {
    private val day = LocalDate.of(2026, 10, 12)

    @Test fun `점·줄표·붙여 쓰기를 받는다`() {
        assertEquals(day, parseDay("2026.10.12"))
        assertEquals(day, parseDay("2026-10-12"))
        assertEquals(day, parseDay("20261012"))
        assertEquals(day, parseDay(" 2026.10.12 "))
        assertEquals(LocalDate.of(2026, 1, 5), parseDay("2026.1.5"))
    }

    @Test fun `정한 꼴이 아니면 null`() {
        assertNull(parseDay("2026/10/12"))
        assertNull(parseDay("완독 2026.10.12"))
        assertNull(parseDay("2026년10월12일"))
        assertNull(parseDay("2026 10 12"))
        assertNull(parseDay("2026.10-12"))
        assertNull(parseDay("2026-10.12"))
    }

    @Test fun `날짜가 아니면 null`() {
        assertNull(parseDay(""))
        assertNull(parseDay("2026.10"))
        assertNull(parseDay("2026.13.01"))
        assertNull(parseDay("2026.02.30"))
        assertNull(parseDay("2026.10.123"))
    }
}
