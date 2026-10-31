package com.woody.pebbledesk.readers

import android.content.Context

/**
 * 아이리더(掌阅 iReader SmartOS 기기: Tango·Ocean 5C 에서 확인) 기본 리더 `com.zhangyue.iReader.Eink`. 읽는 화면은 `ActivityRead`
 * (두 기기 사진의 기기 정보 `최근에 뜬 다른 화면`, 2026-10-31. 화웨이용 같은 앱 1.8.3 은 `Eink.Activity.ActivityRead` 인데 기기 판은 앞부분을 몰라 마지막 이름으로 가린다).
 * 그 1.8.3 은 서재를 뷰 하나에 직접 그리고 읽는 화면 메뉴에 제목이 없으며 기록은 `Android/data` 에 두어, 화면으로도 파일로도 책을 알 수 없다.
 * 그래서 책은 알아보지 않고 읽은 시간만 센다
 * (책 없이 앱으로: 오늘 읽은 시간·연속일에만 들어간다). 자동 추가 서비스에는 넣어 두어 `기록 보기` 에 이 앱의 화면 이름이 남는다.
 */
object IReader : ReaderApp(
    pkg = "com.zhangyue.iReader.Eink", classPrefix = "",
    shelves = emptySet(), viewers = emptySet(),
    titles = emptyList(), thumbs = emptyList(), authors = emptyList(), progress = null, due = null,
    viewerProgress = emptyList(),
) {
    override fun isViewer(context: Context, cls: String) = cls.substringAfterLast('.') == VIEWER
}

private const val VIEWER = "ActivityRead"
