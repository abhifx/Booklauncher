package com.woody.booklauncher.readers

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.woody.booklauncher.BookRecorder
import com.woody.booklauncher.OpenSteps
import com.woody.booklauncher.Seen
import com.woody.booklauncher.ShelfBook
import com.woody.booklauncher.Target
import com.woody.booklauncher.WatchLog

/**
 * 이북 앱 하나의 판단과 기억(서비스가 붙을 때 [ReaderApp.newAdapter] 로 새로 만든다, docs/reader-adapter-plan.md `훅`).
 * 공통 흐름([com.woody.booklauncher.WatchCore])이 알림마다 아래 훅을 부르고, 어댑터는 [AppContext] 로 기록한다.
 * 모든 어댑터는 바탕 [ScreenReader] 를 상속하고, 다른 길로 책을 아는 앱만 그 앱 파일에서 훅을 덮는다.
 */
interface ReaderAdapter {
    /**
     * 그 앱의 화면이 [name] 으로 바뀌었다. [again]: 같은 화면 알림이 한 번 더 온 것, [byWindow]: 창 바뀜 알림으로 들어옴.
     * [ScreenReader] 가 공통 전이를 하고 읽는 화면이면 [onEnterViewer] 를 부른다(덮지 않는다)
     */
    fun onScreen(ctx: AppContext, name: String, from: String?, again: Boolean, byWindow: Boolean)
    /** 읽는 화면에 들어왔다(공통 전이 뒤): 그 앱의 읽는 화면 기억 비우기와 무엇으로 책을 기록하나(바탕: 누른 칸) */
    fun onEnterViewer(ctx: AppContext, from: String?, again: Boolean) {}
    /** 액티비티가 아닌 창이 떴다(대화상자). [cls] 는 그 창의 클래스 이름(모르면 null) */
    fun onWindow(ctx: AppContext, cls: String?) {}
    /**
     * 화면 이름([ReaderApp.screenInsideActivity] 앱만 부른다): [activity] 안에 읽는 화면이 떠 있으면 `activity#요소`, 아니면 activity.
     * 판단할 수 없으면(맨 앞 창이 그 앱이 아님) null
     */
    fun screenName(ctx: AppContext, activity: String?): String? = activity
    /** 서재가 보이는 동안 바로(멈추기 전). [force] 가 아니면 1초에 한 번만 할 일은 조각마다 거른다 */
    fun onShelfLive(ctx: AppContext, force: Boolean) {}
    /** 서재가 멈췄다 */
    fun onShelf(ctx: AppContext) {}
    /** 무엇인가를 눌렀다(서재가 아니어도) */
    fun onClick(ctx: AppContext, click: Click) {}
    /**
     * 그 앱의 내용이 바뀌었다(화면을 읽기 전에, [ReaderApp.readsContent] 앱만). [source] 를 부르면 바뀐 요소(없으면 null).
     * 창 뿌리에서 훑어 닿지 않는 요소(Compose 화면 안의 웹 화면)를 알림으로 붙잡아 두는 앱만 쓴다. 바탕: 아무것도 안 함(요소를 얻지 않는다)
     */
    fun onContent(ctx: AppContext, source: () -> AccessibilityNodeInfo?) {}
    /** 읽는 화면이 잠잠해졌다(그때 그 앱 화면이 읽는 화면일 때만) */
    fun onViewer(ctx: AppContext) {}
    /** 홈으로 돌아왔다 */
    fun onHome(ctx: AppContext) {}
    /** [AppContext.opened] 가 기록하기 전: 그 앱의 판단 기억을 비운다 */
    fun beforeOpened(ctx: AppContext) {}
    /** [AppContext.opened] 의 기록기가 예외 없이 돌아온 뒤 */
    fun afterOpened(ctx: AppContext, book: ShelfBook) {}
    /** 화면에서 표지를 잘라도 되나 */
    fun capturesCovers(ctx: AppContext): Boolean = true
    /** 화면을 찍은 직후, 찍기 전에 정한 자리 가운데 아직 그 자리에 있는 것만 */
    fun stillSpots(ctx: AppContext, spots: List<CoverSpot>): List<CoverSpot> = spots
}

/** 누름 알림: 누른 요소(누르자마자 화면이 넘어가면 null)와 알림의 글자 */
class Click(val source: AccessibilityNodeInfo?, val text: List<CharSequence>)

/**
 * 잘라 올 표지 자리. [key] 가 없으면 제목 자리(찍은 뒤 그 제목의 책에, 없으면 정리한 제목으로 잘라 둔다),
 * 있으면 그 열쇠의 자리([id] 의 책에, 없으면 [key] 로 잘라 둔다 — 이지뷰어 파일 칸)
 */
class CoverSpot(val title: String, val rect: Rect, val id: String? = null, val key: String? = null)

/** 읽은 시간을 세는 방식([ReaderApp.viewerTime]) */
enum class ViewerTime {
    /** 읽는 화면이 앞에 있던 동안 */
    SCREEN,
    /** 표시(열림·닫힘)로 자르고 표시가 없으면 세지 않는다(Libby·Readest: 읽는 화면이 서재 액티비티 안) */
    MARKS_CLOSED,
    /** 표시로 자르고 표시가 없으면 다 센다(KOReader: 데이터를 못 읽으면 앞에 있던 동안) */
    MARKS_OPEN,
}

/** 어댑터가 받는 것: 그 앱 하나에 묶인 공통 흐름의 기억·기록·예약 */
interface AppContext {
    val context: Context
    val app: ReaderApp
    val pkg: String get() = app.pkg
    /** 편 책 기록을 바꾸지 않는 기록(진행률·파일·표지 등)은 바로 */
    val recorder: BookRecorder
    /** 책을 폈다: 공통 기억과 [ReaderAdapter.beforeOpened] 로 비운 뒤 기록하고, 예외 없이 돌아오면 [ReaderAdapter.afterOpened] */
    fun opened(seen: Seen, at: Long? = null, target: Target = Target.ByTitle, coverKey: String? = null, steps: OpenSteps? = null): ShelfBook
    /** 여기부터 어떤 책인지 모른다(기억은 비우지 않는다) */
    fun unknown(at: Long)
    /** 제목 못 읽은 칸·모르는 탭으로 들어와 책 모름이 된 때 */
    var unknownOpenAt: Long?
    val now: Long
    /** 지금 화면(액티비티 이름, 서재 안 읽는 화면은 `액티비티#요소`) */
    val screen: String?
    /** 읽는 화면이 마지막으로 앞에 온 때 */
    val viewerOpenedAt: Long?
    /** 화면이 마지막으로 바뀐 때(어느 앱이든) */
    val lastTransition: Long
    /** 이 앱에서 책을 편 것으로 마지막으로 적은 때 */
    val lastOpenedAt: Long?
    /** 맨 앞 창(다른 앱이면 null) */
    val root: AccessibilityNodeInfo?
    /** 그 앱의 창들 */
    fun windows(): AppWindows
    /** 서비스가 남기는 값(`reader_watch`) */
    val prefs: SharedPreferences
    fun log(kind: WatchLog.Kind, arg: String = "", detail: String = "")
    /** 그 앱의 화면을 못 얻었다(맨 앞 창을 아예 못 얻을 때만 적는다) */
    fun noWindow()
    /** 화면을 찍을 수 있다(마지막으로 찍은 지 1.1초) */
    val canShoot: Boolean
    /** 표지 자리를 찍어 잘라 달라. 너무 이르면 [retry] 일 때 서재 멈춤을 다시 예약한다 */
    fun capture(spots: List<CoverSpot>, retry: Boolean = true)
    /** 이 앱의 [ReaderAdapter.onViewer] 를 [ms] 뒤 다시(읽는 화면 예약은 전체에 하나) */
    fun viewerAgain(ms: Long)
    /** 진행률 재시도가 남았으면 하나 쓰고 true(전체에 하나, 잠잠 예약을 새로 걸 때마다 3) */
    fun viewerRetry(): Boolean
    /** 무거운 일(뒤 스레드), [done] 은 메인 스레드. 서비스가 끝나도 적용한다 */
    fun <T> inBackground(work: () -> T, done: (Result<T>) -> Unit)
}
