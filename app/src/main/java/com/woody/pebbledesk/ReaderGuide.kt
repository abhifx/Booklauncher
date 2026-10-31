package com.woody.pebbledesk

import android.content.Context
import android.os.Build
import com.woody.pebbledesk.readers.Aladin
import com.woody.pebbledesk.readers.BookFusion
import com.woody.pebbledesk.readers.Bookers
import com.woody.pebbledesk.readers.Bookers2
import com.woody.pebbledesk.readers.CherieViewer
import com.woody.pebbledesk.readers.EasyView
import com.woody.pebbledesk.readers.EasyViewFree
import com.woody.pebbledesk.readers.Futon
import com.woody.pebbledesk.readers.Kahon
import com.woody.pebbledesk.readers.KOReader
import com.woody.pebbledesk.readers.KOReaderFdroid
import com.woody.pebbledesk.readers.Kindle
import com.woody.pebbledesk.readers.Kobo
import com.woody.pebbledesk.readers.Kotatsu
import com.woody.pebbledesk.readers.KyoboEbook
import com.woody.pebbledesk.readers.KyoboEbookMobile
import com.woody.pebbledesk.readers.KyoboLibrary
import com.woody.pebbledesk.readers.Libby
import com.woody.pebbledesk.readers.Librera
import com.woody.pebbledesk.readers.LibreraPro
import com.woody.pebbledesk.readers.Mihon
import com.woody.pebbledesk.readers.Millie
import com.woody.pebbledesk.readers.MillieMobile
import com.woody.pebbledesk.readers.MoonReader
import com.woody.pebbledesk.readers.MoonReaderPro
import com.woody.pebbledesk.readers.MyYes
import com.woody.pebbledesk.readers.NeoReader
import com.woody.pebbledesk.readers.PlayBooks
import com.woody.pebbledesk.readers.ReaderApps
import com.woody.pebbledesk.readers.ReaderApp
import com.woody.pebbledesk.readers.Readest
import com.woody.pebbledesk.readers.Ridi
import com.woody.pebbledesk.readers.RidiOneStore
import com.woody.pebbledesk.readers.Woojoo
import com.woody.pebbledesk.readers.Yes24Ebook
import com.woody.pebbledesk.readers.Yes24Library
import com.woody.pebbledesk.readers.Yes24LibraryMobile

/**
 * 자동 추가 안내(설정 › 독서 › 자동 추가 안내)의 앱별 내용. 앱마다 필요한 권한, 그 앱에서 할 것, 자동으로 들어오는 책 정보 다섯 가지(표지·제목·저자·진행률·반납일),
 * 알아 둘 것. 책 정보는 어디서 오는지가 아니라 들어오는지만(조건이 있으면 조건만) 적는다. 근거·자세한 것은 docs/READER_APPS.md `자동 추가: 앱별로 가져오는 것`.
 */
internal object ReaderGuide {
    /**
     * 책 정보 한 칸: [main] 은 `들어옵니다`·`메뉴를 띄우면 들어옵니다` 같은 한 줄, [note] 는 그 아래 작은 회색 글(들어오면 조건,
     * 안 들어오면 이유: 앱이 막고 있음·구독·내 파일). [added] 가 false 면 흐리게
     */
    class Field(val main: Int, val note: Int? = null, val added: Boolean = true)

    /**
     * 앱 하나의 안내. [bookFiles] 는 책 파일 읽기 줄의 보조 글(꼭 필요·표지 등, null 이면 그 줄을 두지 않는다), [todo] 는 앱에서 할 것(비면 `따로 바꿀 것은 없습니다`),
     * [note] 는 알아 둘 것(없으면 그 묶음을 두지 않는다).
     * 표지는 안드로이드 9·10 이면 [coverBefore11]: 서재 화면에서 자르는 표지는 화면 캡처가 안드로이드 11 부터라 들어오지 않는다
     * (기본. 책 파일 읽기 줄이 있는 앱은 표지를 책 파일·앱 데이터에서 꺼내 그대로이고, 그 길만 남는 앱만 따로 준다).
     */
    class Info(
        val todo: List<Int>,
        cover: Field, val title: Field, val author: Field, val progress: Field, val due: Field,
        val bookFiles: Int? = null,
        val note: Int? = null,
        coverBefore11: Field = if (cover.added && bookFiles == null) BEFORE_11 else cover,
    ) {
        val cover = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) coverBefore11 else cover

        val fields get() = listOf(
            R.string.guide_cover to cover, R.string.guide_title to title, R.string.guide_author to author,
            R.string.guide_progress to progress, R.string.guide_due to due,
        )
    }

    /**
     * 목록의 앱 한 줄: 판 이름들(e-ink 판에만 `(e-ink)`, 문리더 유료판은 `문리더 Pro`, 리디 원스토어판은 `(원스토어)`, 이지뷰어 유료판은 채리뷰어)과
     * 그 이북 앱들. 같은 코드라 안내가 같은 판은 한 줄로 묶고 이름을 ` · ` 로 잇는다(밀리의서재 e-ink·휴대폰 판은 다른 앱이라 따로).
     * 이지뷰어 무료판 둘과 KOReader 의 GitHub·F-Droid 판은 사용자가 고르는 차이가 아니라 이름 하나로.
     */
    class App(val names: List<Int>, val specs: List<ReaderApp>, val info: Info) {
        fun label(context: Context) = names.joinToString(" · ") { context.getString(it) }
    }

    private val YES = Field(R.string.guide_added)
    private val NO = Field(R.string.guide_not_added, added = false)
    private fun yes(note: Int) = Field(R.string.guide_added, note)
    private fun no(reason: Int) = Field(R.string.guide_not_added, reason, added = false)
    /** 교보eBook 서재는 읽지 않아(Compose, 요소 이름이 없고 격자 보기는 제목도 없다) 서재에서만 보이는 것을 모른다 */
    private val KYOBO_SHELF = no(R.string.guide_r_kyobo_shelf)
    private val SUBSCRIPTION = no(R.string.guide_r_subscription)
    private val OWN_FILE = no(R.string.guide_r_own_file)
    private val MENU = Field(R.string.guide_added_menu)
    private val READING_MENU = yes(R.string.guide_n_reading_menu)
    /** 안드로이드 9·10 에서 서재 화면에서 자르는 표지(`ReaderWatchService.captureCovers`) */
    private val BEFORE_11 = no(R.string.guide_r_android11)

    /** 자동 추가 안내의 앱들. [ReaderApps.ALL] 을 빠짐없이 덮어야 한다(단위 테스트, 앱을 더하면 여기도) */
    val APPS = listOf(
        App(listOf(R.string.guide_kel), listOf(KyoboLibrary), Info(
            listOf(R.string.guide_t_kyobo_bottom, R.string.guide_t_kel_list),
            cover = Field(R.string.guide_added_list_view), title = yes(R.string.guide_n_kel_title),
            author = Field(R.string.guide_added_list_view), progress = YES, due = YES,
        )),
        App(listOf(R.string.guide_kyobo_mobile, R.string.guide_kyobo), listOf(KyoboEbookMobile, KyoboEbook), Info(
            listOf(R.string.guide_t_kyobo_menu, R.string.guide_t_kyobo_bottom),
            cover = KYOBO_SHELF, title = MENU, author = KYOBO_SHELF, progress = YES, due = KYOBO_SHELF,
        )),
        App(listOf(R.string.guide_millie), listOf(Millie), Info(
            emptyList(),
            cover = YES, title = YES, author = Field(R.string.guide_added_search_open), progress = YES, due = SUBSCRIPTION,
        )),
        App(listOf(R.string.guide_millie_mobile), listOf(MillieMobile), Info(
            listOf(R.string.guide_t_millie_cover, R.string.guide_t_progress_menu),
            cover = YES, title = YES, author = YES, progress = MENU, due = SUBSCRIPTION,
        )),
        App(listOf(R.string.guide_aladin), listOf(Aladin), Info(
            listOf(R.string.guide_t_aladin_list),
            cover = yes(R.string.guide_n_resume_cover), title = yes(R.string.guide_n_aladin_title),
            author = Field(R.string.guide_added_list_view), progress = YES, due = Field(R.string.guide_added_list_view),
        )),
        App(listOf(R.string.guide_bookers2), listOf(Bookers2), Info(
            listOf(R.string.guide_t_libby_time),
            cover = yes(R.string.guide_n_bookers2_cover), title = YES, author = YES, progress = YES, due = YES,
        )),
        App(listOf(R.string.guide_bookers), listOf(Bookers), Info(
            listOf(R.string.guide_t_bookers_bottom),
            cover = YES, title = YES, author = Field(R.string.guide_added_bookers_list), progress = YES, due = YES,
        )),
        App(listOf(R.string.guide_ridi, R.string.guide_ridi_onestore), listOf(Ridi, RidiOneStore), Info(
            listOf(R.string.guide_t_ridi_bottom, R.string.guide_t_ridi_menu),
            cover = yes(R.string.guide_n_ridi_cover), title = MENU, author = no(R.string.guide_r_ridi_author), progress = YES, due = NO,
        )),
        App(listOf(R.string.guide_yes24lib_mobile, R.string.guide_yes24lib), listOf(Yes24LibraryMobile, Yes24Library), Info(
            emptyList(),
            cover = YES, title = yes(R.string.guide_n_yes24lib_title), author = YES, progress = YES, due = YES,
        )),
        App(listOf(R.string.guide_myyes), listOf(MyYes), Info(
            emptyList(),
            cover = no(R.string.guide_r_myyes_capture), title = YES, author = YES, progress = YES, due = Field(R.string.guide_added_expired),
        )),
        App(listOf(R.string.guide_yes24ebook), listOf(Yes24Ebook), Info(
            listOf(R.string.guide_t_yes24_bottom),
            cover = YES, title = YES, author = YES, progress = YES, due = NO,
        )),
        App(listOf(R.string.guide_moon, R.string.guide_moon_pro), listOf(MoonReader, MoonReaderPro), Info(
            listOf(R.string.guide_t_moon_open),
            cover = yes(R.string.guide_n_moon_cover), title = yes(R.string.guide_n_moon_title), author = YES, progress = YES, due = OWN_FILE,
            bookFiles = R.string.guide_files_cover,
            coverBefore11 = Field(R.string.guide_added_book_files, R.string.guide_n_moon_cover_before11),
        )),
        App(listOf(R.string.guide_easyview, R.string.guide_cherie), listOf(EasyView, EasyViewFree, CherieViewer), Info(
            listOf(R.string.guide_t_ez_open, R.string.guide_t_ez_bar),
            cover = YES, title = YES, author = Field(R.string.guide_added_epub), progress = YES, due = OWN_FILE,
            bookFiles = R.string.guide_files_cover_info, note = R.string.guide_note_ez,
            coverBefore11 = Field(R.string.guide_added_book_files, R.string.guide_n_ez_files),
        )),
        App(listOf(R.string.guide_kindle), listOf(Kindle), Info(
            listOf(R.string.guide_t_kindle_menu),
            cover = yes(R.string.guide_n_kindle_cover), title = yes(R.string.guide_n_kindle_title), author = YES, progress = READING_MENU, due = NO,
        )),
        App(listOf(R.string.guide_libby), listOf(Libby), Info(
            listOf(R.string.guide_t_progress_menu, R.string.guide_t_libby_time),
            cover = YES, title = YES, author = YES, progress = READING_MENU, due = YES,
        )),
        App(listOf(R.string.guide_neo), listOf(NeoReader), Info(
            listOf(R.string.guide_t_neo_open),
            cover = Field(R.string.guide_added_book_files, R.string.guide_n_epub), title = YES, author = YES, progress = YES, due = OWN_FILE,
            bookFiles = R.string.guide_files_cover, note = R.string.guide_note_neo,
        )),
        App(listOf(R.string.guide_ko), listOf(KOReader, KOReaderFdroid), Info(
            listOf(R.string.guide_t_ko_files),
            cover = Field(R.string.guide_added_epub_comics, R.string.guide_n_comic_archives), title = YES, author = YES, progress = YES, due = OWN_FILE,
            bookFiles = R.string.guide_files_required, note = R.string.guide_note_ko,
        )),
        App(listOf(R.string.guide_readest), listOf(Readest), Info(
            listOf(R.string.guide_t_readest_data, R.string.guide_t_readest_shelf),
            cover = yes(R.string.guide_n_readest_cover), title = yes(R.string.guide_n_readest_title), author = yes(R.string.guide_n_readest_author),
            progress = yes(R.string.guide_n_readest_progress), due = OWN_FILE,
            bookFiles = R.string.guide_files_readest, note = R.string.guide_note_readest,
            coverBefore11 = Field(R.string.guide_added_readest_data),
        )),
        App(listOf(R.string.guide_bookfusion), listOf(BookFusion), Info(
            listOf(R.string.guide_t_bookfusion_shelf, R.string.guide_t_progress_menu),
            cover = YES, title = yes(R.string.guide_n_bookfusion_title), author = YES, progress = READING_MENU, due = NO,
        )),
        App(listOf(R.string.guide_woojoo), listOf(Woojoo), Info(
            listOf(R.string.guide_t_woojoo_open),
            cover = yes(R.string.guide_n_woojoo_cover), title = YES, author = YES, progress = yes(R.string.guide_n_woojoo_progress), due = OWN_FILE,
            bookFiles = R.string.guide_files_woojoo, note = R.string.guide_note_woojoo,
            coverBefore11 = Field(R.string.guide_added_book_files, R.string.guide_n_epub),
        )),
        App(listOf(R.string.guide_playbooks), listOf(PlayBooks), Info(
            emptyList(),
            cover = yes(R.string.guide_n_playbooks_library), title = YES, author = yes(R.string.guide_n_playbooks_library), progress = YES, due = NO,
        )),
        App(listOf(R.string.guide_mihon, R.string.guide_kahon), listOf(Mihon, Kahon), Info(
            listOf(R.string.guide_t_mihon_open),
            cover = yes(R.string.guide_n_mihon_cover), title = MENU, author = yes(R.string.guide_n_mihon_detail),
            progress = Field(R.string.guide_added_menu, R.string.guide_n_mihon_chapter), due = NO, bookFiles = R.string.guide_files_cover_info, note = R.string.guide_note_mihon,
            coverBefore11 = Field(R.string.guide_added_book_files, R.string.guide_n_mihon_local),
        )),
        App(listOf(R.string.guide_kobo), listOf(Kobo), Info(
            listOf(R.string.guide_t_kobo_open, R.string.guide_t_progress_menu),
            cover = YES, title = yes(R.string.guide_n_kobo_title), author = YES, progress = READING_MENU, due = NO,
        )),
        App(listOf(R.string.guide_futon, R.string.guide_kotatsu), listOf(Futon, Kotatsu), Info(
            listOf(R.string.guide_t_kotatsu_open),
            cover = YES, title = YES, author = yes(R.string.guide_n_kotatsu_author),
            progress = yes(R.string.guide_n_kotatsu_progress), due = NO, note = R.string.guide_note_kotatsu,
        )),
        App(listOf(R.string.guide_librera, R.string.guide_librera_pro), listOf(Librera, LibreraPro), Info(
            listOf(R.string.guide_t_librera_open),
            cover = YES, title = YES, author = YES, progress = READING_MENU, due = OWN_FILE,
            bookFiles = R.string.guide_files_librera, note = R.string.guide_note_librera,
            coverBefore11 = Field(R.string.guide_added_book_files, R.string.guide_n_librera_cover_files),
        )),
    )
}
