package com.woody.pebbledesk.readers

import android.content.Context

/**
 * 읽는 책 자동 추가가 받는 이북 앱들(앱마다 파일 하나). 앱을 더하면 여기와
 * `res/xml/reader_watch.xml` 의 packageNames(시스템이 이 앱들의 화면만 보낸다), 설정 › 독서 › 자동 추가 안내의 앱(`ReaderGuide.APPS`,
 * 빠지면 단위 테스트가 알린다)을 함께 고친다. 한 패키지가 판에 따라 다른 앱이면(북커스 2.0.0 · 1.x) 새 판의 앱 파일을 앞에 둔다([ReaderApp.handles]).
 */
object ReaderApps {
    val ALL = listOf(
        KyoboLibrary, KyoboEbook, KyoboEbookMobile, Millie, MillieMobile, Aladin, Bookers2, Bookers, Ridi, RidiOneStore, Yes24Library, Yes24LibraryMobile, Yes24Ebook, MyYes, MoonReader, MoonReaderPro, EasyView, EasyViewFree, CherieViewer, Kindle, Libby, NeoReader, KOReader, KOReaderFdroid, Readest, BookFusion, Woojoo, Mihon, Kahon, PlayBooks, Kobo, Futon, Kotatsu, Librera, LibreraPro,
    )

    /** 패키지마다 앱 파일들(판에 따라 다른 앱이면 여럿) */
    private val byPackage = ALL.groupBy { it.pkg }

    /** [pkg] 의 깔린 판을 맡는 앱 파일([installed]). 이북 앱이 아니면 null */
    fun forPackage(context: Context, pkg: String): ReaderApp? = byPackage[pkg]?.installed(context)

    /**
     * 책 파일 읽기가 필요한 앱([ReaderApp.needsBookFiles]). 이 가운데 깔린 앱이 있으면 시작하기에 책 파일 읽기 쪽을 더한다
     * (홈의 안내 창은 자동 추가 서비스가 꺼진 채 그 앱의 읽는 화면을 볼 때 적는다, `ReaderWatchService.wantFiles`).
     */
    val fileApps = ALL.filter { it.needsBookFiles }

    /** 이북 앱 패키지(읽고 있는 앱 고르기에서 먼저 보이고, 오늘 읽은 시간을 센다) */
    val packages = byPackage.keys

    /** [pkg] 앱의 [cls](액티비티 전체 이름)가 읽는 화면인가([ReaderApp.isViewer]. 판마다 앱 파일이 다르면 어느 판의 읽는 화면이든: 화면 이름이 겹치지 않는다) */
    fun isViewer(context: Context, pkg: String, cls: String) = byPackage[pkg]?.any { it.isViewer(context, cls) } == true

    /** 표시(열림·닫힘)로 읽은 시간을 자르는 액티비티들([ReaderApp.markedScreens]: Libby·Readest 의 서재 액티비티, KOReader 의 읽는 화면) */
    private val hosts = ALL.flatMap { it.markedScreens }.toSet()

    /** [cls] 가 읽는 화면이 보인 동안만 세는 액티비티인가([hosts]) */
    fun hostsViewer(cls: String) = cls in hosts

    /** 읽는 화면이 떴는지 적힌 것이 없으면 떠 있는 것으로 보는 앱([ViewerTime.MARKS_OPEN], KOReader: 데이터를 못 읽으면 앞에 있던 동안을 다 센다). 아니면 안 뜬 것으로(Libby) */
    private val shownByDefault = ALL.filter { it.viewerTime == ViewerTime.MARKS_OPEN }.map { it.pkg }.toSet()

    fun shownByDefault(pkg: String) = pkg in shownByDefault
}
