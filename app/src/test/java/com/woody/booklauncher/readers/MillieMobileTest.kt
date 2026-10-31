package com.woody.booklauncher.readers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 휴대폰 밀리 내서재 표지 단추 글자 → 제목·저자([MillieMobile.coverText]) */
class MillieMobileTest {
    @Test fun `저자 표시 앞의 슬래시에서 나눈다`() {
        assertEquals("팩트풀니스" to "한스 로슬링 지음 / 이창신 옮김", MillieMobile.coverText("팩트풀니스/한스 로슬링 지음 / 이창신 옮김 썸네일"))
        assertEquals("A/B" to "홍길동 지음", MillieMobile.coverText("A/B/홍길동 지음 썸네일"))
        assertEquals("1/2 저 너머" to "홍길동 지음", MillieMobile.coverText("1/2 저 너머/홍길동 지음 썸네일"))
        assertEquals("작은 책" to "홍길동 저", MillieMobile.coverText("작은 책/홍길동 저 썸네일"))
        assertEquals("제목" to "홍길동 지음 / 김철수 지음", MillieMobile.coverText("제목/홍길동 지음 / 김철수 지음 썸네일"))
        assertEquals("제목" to "홍길동 지음 / 김철수 저", MillieMobile.coverText("제목/홍길동 지음 / 김철수 저 썸네일"))
    }

    @Test fun `저자 표시가 없으면 첫 슬래시, 배지는 뗀다`() {
        assertEquals("제목" to "홍길동", MillieMobile.coverText("제목/홍길동 썸네일"))
        assertEquals("제목" to "홍길동 지음", MillieMobile.coverText("오디오북 제목/홍길동 지음 썸네일"))
    }

    @Test fun `표지 단추가 아니면 null`() {
        assertNull(MillieMobile.coverText("제목/홍길동 지음"))
        assertNull(MillieMobile.coverText("제목 썸네일"))
    }
}
