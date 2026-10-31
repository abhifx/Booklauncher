package com.woody.booklauncher.readers

import android.content.Context

/**
 * 이북 앱 데이터로 책을 아는 앱에 묻기(쓰는 앱: 우주·네오리더·Librera. 바꾸면 모두 시험한다).
 * 쪽을 넘길 때마다 그 앱 데이터를 다시 읽지 않도록 메뉴 제목이 새로 보일 때(결과는 기억)와 읽는 화면이 앞에 온 뒤
 * 메뉴 없이 [TRIES]번까지만 묻는다(방금 연 책을 찾으면 그만 묻는다).
 * [find] 는 그 앱 데이터에서 책 찾기: 읽는 화면에서 읽은 것(메뉴 제목이 보이면 그 책을), 이 읽는 화면이 열린 때(제목을 모르면 그 뒤에 연 책만 믿는다, 제목이 보이면 0)
 */
class DataAsk(private val find: (Context, ViewerInfo, Long) -> FileBook?) {
    /** 메뉴 없이 물어볼 남은 횟수(읽는 화면이 앞에 올 때마다 다시 센다) */
    private var tries = 0
    /** 마지막으로 메뉴 제목으로 찾은 책(메뉴를 띄워 둔 동안 다시 묻지 않게. 못 찾으면 두지 않고 다시 묻는다) */
    private var byTitle: Pair<String, FileBook>? = null

    /** 읽는 화면이 앞에 왔다: 다시 센다 */
    fun reset() {
        tries = TRIES
        byTitle = null
    }

    fun ask(ctx: AppContext, shown: ViewerInfo): FileBook? {
        val title = shown.title
        if (title != null) {
            byTitle?.let { (t, book) -> if (t == title) return book }
            return find(ctx.context, shown, 0)?.also { byTitle = title to it }
        }
        if (tries <= 0) return null
        tries--
        val since = ctx.viewerOpenedAt ?: return null
        return find(ctx.context, shown, since)?.also { tries = 0 }
    }

    companion object {
        /** 읽는 화면이 앞에 온 뒤 메뉴 없이 물어볼 횟수(책을 여는 동안 연 때가 늦게 적힐 수 있다) */
        const val TRIES = 6
    }
}
