package com.woody.pebbledesk

/**
 * 색 테마(설정 › 화면 › 색): 흑백(처음 값) 또는 컬러 테마 하나. 기기를 보고 바꾸지 않는다(사용자가 고른다).
 * 컬러 테마는 컬러 전자잉크(Kaleido 등)용: 표지·앱 아이콘을 원래 색으로 그리고, 화면마다 머리에 옅은 색면 하나([field])와
 * 큰 제목 색([title], [Area.accentTitle] 인 화면만)을 쓴다. 모든 화면이 같은 두 색이다.
 * 어디서나 같은 완독 색([DONE], 완독 띠·도장)과 큰 별 색([STAR])은 테마와 상관없다. 작은 글자·가는 선·작은 별은 무채색 그대로.
 * 컬러 전자잉크는 색이 탁하고 반 해상도라, 색은 넓은 면과 굵은 표시에만 쓴다.
 *
 * [key] 는 설정 파일에 저장하는 값이라 바꾸지 않는다. 1.7.9 까지의 `palette`(mono / color)는 [HomePrefs.palette] 가 한 번 옮긴다.
 */
enum class Palette(val key: String, val label: Int, private val fieldColor: Int, private val accentColor: Int) {
    MONO("mono", R.string.palette_mono, android.graphics.Color.WHITE, Ui.BLACK),
    /** 등불 아래: 1.7.9 까지 컬러의 홈 색. 그때 컬러를 쓰던 사람은 이 테마로 옮긴다. */
    AMBER("amber", R.string.palette_amber, 0xFFF0DEB6.toInt(), 0xFFA95B00.toInt()),
    INK("ink", R.string.palette_ink, 0xFFE4E0D6.toInt(), 0xFF3A3732.toInt()),
    MOSS("moss", R.string.palette_moss, 0xFFDDE6CB.toInt(), 0xFF4B6B2B.toInt()),
    CAMELLIA("camellia", R.string.palette_camellia, 0xFFF0D6DA.toInt(), 0xFF9A3048.toInt()),
    CANOLA("canola", R.string.palette_canola, 0xFFF4EC96.toInt(), 0xFF7A6800.toInt()),
    INDIGO("indigo", R.string.palette_indigo, 0xFFD9E1EA.toInt(), 0xFF34506B.toInt());

    /** 표지·앱 아이콘을 원래 색으로 그리고 색면·역할 색을 쓰는가 */
    val color: Boolean get() = this != MONO

    /** 고르기 창의 색 칸 둘: 색면 · 큰 제목 색(흑백은 흰색 · 검정) */
    val swatch: List<Int> get() = listOf(fieldColor, accentColor)

    /** 완독 띠 */
    val ribbon: Int get() = if (color) DONE else Ui.GRAY

    /** 완독 도장 */
    val stamp: Int get() = if (color) DONE else Ui.LIGHT_GRAY

    /** 독서 기록의 목표 링·목표를 채운 날의 막대·연속일 숫자 */
    val goal: Int get() = title(Area.READING)

    /** 큰 별점(책 상세·별점 메뉴)의 채운 별. 작은 별(목록·격자)은 늘 회색. */
    val star: Int get() = if (color) STAR else Ui.BLACK

    /** 색면(바탕): [Area] 마다 그 화면의 머리에 칠한다(모든 화면이 같은 색). 흑백이면 null(칠하지 않는다). */
    val field: Int? get() = if (color) fieldColor else null

    /** [area] 의 큰 제목 글자색. 흑백이거나 제목을 검정으로 두는 화면이면 검정. */
    fun title(area: Area): Int = if (color && area.accentTitle) accentColor else Ui.BLACK

    /**
     * 큰 제목이 있는 화면 묶음. 색면은 그 화면의 머리만 칠한다: [titleBand] 면 제목 줄, 아니면 그 화면의 맨 위 칸
     * (책 상세 메뉴는 묶음 없이 [field] 를 바로 쓴다, [Sheet.paper]).
     * [accentTitle] 이면 큰 제목(과 독서 기록의 링·막대)을 테마의 제목 색으로, 아니면 검정.
     */
    enum class Area(val accentTitle: Boolean, val titleBand: Boolean = false) {
        /** 홈: 지금 읽는 책 자리, 책 제목 */
        HOME(true),
        /** 독서 기록: 연속일·오늘 링 칸 */
        READING(true),
        /** 책장(올해 읽은 책·읽고 있는 책·책 추가): 제목 줄 */
        LIBRARY(true, titleBand = true),
        /** 설정·앱 목록·기기 정보: 제목 줄 */
        SETTINGS(false, titleBand = true),
    }

    companion object {
        /** 지금 화면이 쓰는 색. 화면을 만들 때(EinkActivity) 설정에서 정한다. */
        @Volatile var current = MONO

        /** 완독(띠·도장): 어느 테마·화면에서나 같은 주홍 */
        const val DONE = 0xFFC84B28.toInt()

        /** 큰 별점 */
        const val STAR = 0xFFCC7200.toInt()

        /** 저장한 값의 테마. 모르는 값(더 새 판에서 고른 테마)이면 null */
        fun of(key: String?): Palette? = entries.firstOrNull { it.key == key }
    }
}
