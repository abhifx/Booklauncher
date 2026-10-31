package com.woody.booklauncher

/**
 * 색(설정 › 화면 › 색): 흑백(처음 값) 또는 컬러. 기기를 보고 바꾸지 않는다(사용자가 고른다).
 * 컬러는 컬러 전자잉크(Kaleido 등)용 '색면의 서가': 표지·앱 아이콘을 원래 색으로 그리고, 화면마다 넓은 색면 하나와 큰 제목 색([Area]),
 * 어디서나 같은 완독 색([DONE], 완독 띠·도장)과 큰 별 색([STAR])을 쓴다. 작은 글자·가는 선·작은 별은 무채색 그대로.
 * 컬러 전자잉크는 색이 탁하고 반 해상도라, 색은 넓은 면과 굵은 표시에만 쓴다.
 */
enum class Palette(val key: String) {
    MONO("mono"),
    COLOR("color");

    /** 표지·앱 아이콘을 원래 색으로 그리고 색면·역할 색을 쓰는가 */
    val color: Boolean get() = this == COLOR

    /** 완독 띠 */
    val ribbon: Int get() = if (color) DONE else Ui.GRAY

    /** 완독 도장 */
    val stamp: Int get() = if (color) DONE else Ui.LIGHT_GRAY

    /** 독서 기록의 목표 링·목표를 채운 날의 막대·연속일 숫자 */
    val goal: Int get() = title(Area.READING)

    /** 큰 별점(책 상세·별점 메뉴)의 채운 별. 작은 별(목록·격자)은 늘 회색. */
    val star: Int get() = if (color) STAR else Ui.BLACK

    /** [area] 의 색면(바탕). 흑백이면 null(칠하지 않는다). */
    fun field(area: Area): Int? = if (color) area.field else null

    /** [area] 의 큰 제목 글자색. 흑백이면 검정. */
    fun title(area: Area): Int = if (color) area.title else Ui.BLACK

    /** 화면 묶음마다 색면과 큰 제목 색. 색면은 그 화면의 머리만 칠한다: [titleBand] 면 제목 줄, 아니면 그 화면의 맨 위 칸. */
    enum class Area(val field: Int, val title: Int, val titleBand: Boolean = false) {
        /** 홈: 지금 읽는 책 자리(황토) */
        HOME(0xFFF0DEB6.toInt(), 0xFFA95B00.toInt()),
        /** 독서 기록: 연속일·오늘 링 칸(파랑) */
        READING(0xFFD7E4EF.toInt(), 0xFF1565C0.toInt()),
        /** 책장(올해 읽은 책·읽고 있는 책·책 추가): 제목 줄(초록) */
        LIBRARY(0xFFDCE7D4.toInt(), 0xFF2E7D32.toInt(), titleBand = true),
        /** 책 상세: 머리·기록·별점 칸(살구) */
        DETAIL(0xFFEDD3BD.toInt(), Ui.BLACK),
        /** 설정·앱 목록·기기 정보: 제목 줄(회색 종이) */
        SETTINGS(0xFFE7E3DA.toInt(), Ui.BLACK, titleBand = true),
    }

    companion object {
        /** 지금 화면이 쓰는 색. 화면을 만들 때(EinkActivity) 설정에서 정한다. */
        @Volatile var current = MONO

        /** 완독(띠·도장): 어느 화면에서나 같은 주홍 */
        const val DONE = 0xFFC84B28.toInt()

        /** 큰 별점 */
        const val STAR = 0xFFCC7200.toInt()

        fun of(key: String?): Palette = entries.firstOrNull { it.key == key } ?: MONO
    }
}
