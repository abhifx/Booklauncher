package com.woody.pebbledesk

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.view.Gravity
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.woody.pebbledesk.BookRecord.State
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 책 상세: 홈에서 책을 길게 누를 때와 올해 읽은 책에서 책을 누를 때 같은 화면. 머리·기록 칸·별점·감상 칸([bookSheet]) 아래
 * 메뉴는 책 상태에 따라
 * - 읽고 있는 책(다시 읽는 책 포함): `완독` / `책 정보 고치기 ›` / `책 빼기`
 * - 나간 책·목록에 없던 책: `완독`(마지막 회차가 진행 중이면) / `다시 읽기` / `책 정보 고치기 ›` / `기록에서 지우기`
 * `완독`·`책 빼기`는 확인 대신 별점·감상 메뉴([reviewSheet], 지금 회차의 것을 채워 띄우고 비워 두고 눌러도 된다), `기록에서 지우기`는 한 번 더 묻는다.
 * `완독`은 늘 오늘로 남긴다(완독한 날은 전체 기록의 회차 메뉴에서 고친다, [editRound]). 무엇이든 바뀌면 [onChange].
 */
fun Context.showBookDetail(r: BookRecord, onChange: () -> Unit) {
    val sub = detailSub(r)
    val reopen = { showBookDetail(r.reload(this), onChange) }
    // 별점·감상 칸은 메뉴가 화면을 넘지 않는 만큼: 감상 5줄부터 줄여 보고(글자·줄 간격을 키우면 줄어든다), 별 줄도 안 들어가면 칸을 두지 않는다.
    val text = r.lastRound.review?.text?.isNotBlank() == true
    val fit = (if (text) (5 downTo 0) else listOf(5)).asSequence().map { detailSheet(r, sub, it, onChange, reopen) }
        .firstOrNull { it.height() <= screenHeight() }
    (fit ?: detailSheet(r, sub, -1, onChange, reopen)).show()
}

/** 상세 메뉴 하나(감상 [lines] 줄, -1 이면 별점·감상 칸 없이). 동작은 누르는 순간 책 id 로 저장소가 지금 상태를 보고 한다([BookShelf]). */
private fun Context.detailSheet(r: BookRecord, sub: String?, lines: Int, onChange: () -> Unit, reopen: () -> Unit): Sheet {
    val book = r.book
    val sheet = bookSheet(r, sub, lines, onChange, reopen)
    // 메뉴는 누르는 순간의 상태로: 상세를 열어 둔 사이 책이 바뀌었으면(이북 앱에서 다시 펴 되살아남·다른 화면에서 완독 등) 그 동작 대신
    // 지금 상태로 상세를 다시 띄운다(메뉴가 달라진다. 다른 화면에서 지웠으면 지운 책으로. 회차 수·마지막 회차의 별점·감상이 바뀌어도
    // — 완독 뒤 다시 읽기 등 — `완독` 메뉴에 옛 감상이 채워지지 않게)
    fun current(act: () -> Unit): () -> Unit = {
        val now = r.reload(this)
        val changed = now.state != r.state || now.hidden != r.hidden || now.rounds.size != r.rounds.size ||
            now.lastRound.finished != r.lastRound.finished || now.lastRound.review != r.lastRound.review
        if (changed) { onChange(); if (alive()) showBookDetail(now, onChange) }
        else act()
    }
    // 완독하면 지금(완독하지 않은) 회차의 별점·감상이 그 회차의 것이 된다.
    val open = r.lastRound.takeUnless { it.finished }?.review
    val finish = {
        reviewSheet(book, sub, getString(R.string.finish_book), open) { review ->
            val (id, done) = withBook(r) { id -> id to BookShelf.finish(this, id, review) }
            if (done) onChange()
            else {
                // 기기 날짜가 마지막 완독일보다 이르다(받지 않는다)
                val last = BookShelf.finishList(this)[id].orEmpty().maxOf { it.day }
                Sheet(this).header(getString(R.string.finish_book), getString(R.string.finish_date_back, last.format(DOTTED))).show()
            }
        }
    }
    if (r.state == State.READING) {
        sheet.item(getString(R.string.finish_book), action = current { finish() })
        sheet.item(getString(R.string.edit_book), action = current { showEditMenu(r, onChange) })
        sheet.item(getString(R.string.remove_book), action = current { removeBook(r, sub, onChange) })
        return sheet
    }
    // 나간 책도 마지막 회차가 진행 중이면(완독한 적 없거나 다시 읽다 뺀 책) 완독할 수 있다.
    if (!r.lastRound.finished) sheet.item(getString(R.string.finish_book), action = current { finish() })
    sheet.item(getString(R.string.read_again), action = current {
        withBook(r) { BookShelf.restore(this, it, newRound = r.lastRound.finished) }
        onChange()
    })
    sheet.item(getString(R.string.edit_book), action = current { showEditMenu(r, onChange) })
    sheet.item(getString(R.string.forget_book), action = current {
        confirm(getString(R.string.forget_confirm), book.title, getString(R.string.forget_book)) {
            val forgotten = withBook(r) { BookShelf.forget(this, it) }
            onChange()
            // 그사이 읽고 있는 책이 됐으면(이북 앱에서 다시 폄) 지우지 않고 지금 상태로 다시 띄운다
            if (!forgotten && alive()) reopen()
        }
    })
    return sheet
}

/**
 * 상세가 다루는 책 id(누르는 순간). 목록에 없던 책은 id 가 없어 그때 제목으로 정한다([BookShelf.claim]: 그사이 생긴 같은 제목의 책,
 * 없으면 나간 책으로 적어 둔다). 다른 책은 상세를 연 때의 id 그대로(책은 지우지 않으니 늘 있다).
 */
private fun Context.bookId(r: BookRecord): String =
    if (r.state == State.TITLE_ONLY) BookShelf.claim(this, r.book.title, r.book.app).id else r.book.id

/** [bookId] 로 정한 책에 [act]: 한 트랜잭션으로(목록에 없던 책을 적어 둔 뒤 [act] 가 실패하면 적어 둔 책도 되돌린다) */
private fun <T> Context.withBook(r: BookRecord, act: (String) -> T): T = BookShelf.together(this) { act(bookId(r)) }

/** 책 상세·전체 기록 머리의 부제(지금 기록의 책·상태로, [bookSubtitle]) */
private fun Context.detailSub(r: BookRecord): String? = bookSubtitle(r.book, r.state == State.READING)

/**
 * `책 빼기`(홈 책 상세·읽고 있는 책 목록의 `빼기`): 올해 읽은 책에 남는 책([BookRecord.inYearBooks])이거나 지금 회차에 이미 별점·감상이 있으면
 * (상세의 별점 칸과 같은 조건) 확인 대신 별점·감상 메뉴(지금 회차의 것, 맨 아래 `취소` / `책 빼기`), 아니면(거의 읽지 않은 책. 남긴 별점을 볼 곳이 없다)
 * 확인만 `읽고 있는 책에서 뺄까요?`.
 */
fun Context.removeBook(shown: BookRecord, sub: CharSequence?, onChange: () -> Unit) {
    // 누르는 순간의 기록으로(상세를 열어 둔 사이 더 읽어 5분을 넘겼을 수 있다)
    val r = shown.reload(this)
    val book = r.book
    if (!r.canReview) {
        confirm(getString(R.string.remove_confirm), book.title, getString(R.string.remove_book)) { BookShelf.remove(this, book.id); onChange() }
        return
    }
    // 읽고 있는 책의 마지막 회차는 늘 완독하지 않은 회차다.
    reviewSheet(book, sub, getString(R.string.remove_book), r.lastRound.review) { review -> BookShelf.remove(this, book.id, review); onChange() }
}

/**
 * `책 정보 고치기`: 머리에 제목·저자, 읽고 있는 책이면 `읽고 있는 앱 바꾸기`, 그리고 `제목 고치기` / `저자 입력`(비어 있으면)·`저자 고치기` /
 * `표지 다시 찾기`. 목록에 없던 책은 고치는 순간 나간 책으로 적어 둔다(고친 것이 남게, [bookId]).
 */
private fun Context.showEditMenu(r: BookRecord, onChange: () -> Unit) {
    val book = r.book
    // 이북 앱이 저자를 보여 주지 않아(읽는 화면 메뉴로 들어온 책 등) 비어 있으면 `저자 입력`, 있으면 `저자 고치기`.
    val authorLabel = if (book.author.isBlank()) R.string.add_author else R.string.edit_author
    val sheet = Sheet(this).header(book.title, book.author.takeIf { it.isNotBlank() })
    if (r.state == State.READING) sheet.item(getString(R.string.change_reader)) {
        startActivity(AppListActivity.intent(this, AppListActivity.Mode.PICK_READER, book.id))
    }
    sheet.item(getString(R.string.edit_title)) {
        Sheet(this).header(getString(R.string.edit_title)).input(book.title) { t -> withBook(r) { BookShelf.setTitle(this, it, t) }; onChange() }.show()
    }
    sheet.item(getString(authorLabel)) {
        Sheet(this).header(getString(authorLabel)).input(book.author) { a -> withBook(r) { BookShelf.setAuthor(this, it, a) }; onChange() }.show()
    }
    sheet.item(getString(R.string.find_cover)) {
        val id = bookId(r)
        val target = BookShelf.list(this).firstOrNull { it.id == id } ?: BookShelf.history(this).first { it.book.id == id }.book
        startActivity(BookSearchActivity.intent(this, replace = target))
    }
    sheet.show()
}

/** 책 머리의 제목 아래: 저자(알면), 회색 줄 `교보도서관 · 47% 읽음 · 반납 4일 남음`(아는 것만, 진행률·반납일은 [reading] 일 때만) */
fun Context.bookSubtitle(book: ShelfBook, reading: Boolean): String? {
    val app = book.app?.let { key -> AppStore.find(this, key)?.label }
    val status = (listOfNotNull(app) + if (reading) bookStatus(book) else emptyList()).joinToString("  ·  ")
    return listOfNotNull(book.author.takeIf { it.isNotBlank() }, status.ifEmpty { null }).joinToString("\n").ifEmpty { null }
}

/**
 * 책 상세의 머리: 표지(완독 띠), 제목, [sub], 완독한 적이 있으면 오른쪽 위에 완독 도장. 그 아래 기록 칸([recordView], 보일 것이 없으면
 * 두지 않는다. 누르면 회차가 둘 이상이면 전체 기록 [showHistory], 회차 하나뿐이면 그 회차 메뉴 [editRound](별점·감상을 남길 수 없는 책은 읽기 시작한 날만). 닫으면 상세를 다시
 * 띄운다([reopen])), 그 아래 마지막 회차의
 * 별점·감상 칸([reviewBlock], 누르면 고친다. 5분 이상 읽지 않은 책은 흐리게, 누르면 안내), 그 아래에 부르는 쪽이 메뉴를 붙인다. 감상은 [lines] 줄까지(-1 이면 칸 없이, [showBookDetail] 이 화면에 맞춰 정한다). 컬러면 머리와 칸들을 책 상세 색면([Sheet.paper])에 둬 아래 메뉴와 나눈다.
 */
private fun Context.bookSheet(r: BookRecord, sub: String?, lines: Int, onChange: () -> Unit, reopen: () -> Unit): Sheet {
    val sheet = headSheet(r, sub)
    val last = r.lastRound
    // 별점·감상은 올해 읽은 책에 보이는 책(5분 이상 읽었거나 완독)이나 이미 남긴 것이 있을 때만 남긴다. 아니면 칸을 흐리게 두고
    // 누르면 안내(남긴 것을 볼 곳이 없어서). 안내를 닫으면 상세로.
    val open = r.canReview
    // 회차 하나뿐이면 그 회차 메뉴(읽기 시작한 날·완독한 날·별점·감상. 별점·감상을 남길 수 없는 책도 읽기 시작한 날은 고친다)
    val history = if (r.rounds.size >= 2) { { showHistory(r, sub, onChange, reopen) } } else { { editRound(r, last, onChange, reopen) } }
    val record = recordView(r)
    record?.let { sheet.note(it, history) }
    // 기록 칸도 없고(보일 기간·시간이 없음) 별점 칸도 둘 자리가 없으면, 별점·감상으로 가는 한 줄만 남긴다(남길 수 없으면 그 안내)
    if (record == null && lines < 0) {
        sheet.note(text("${getString(R.string.review_title)}  ›", 18f, color = Ui.GRAY), if (open) history else { { reviewLocked(r, sub, onChange, reopen) } })
    }
    if (lines >= 0) sheet.note(reviewBlock(last.review, lines, enabled = open)) {
        if (open) editLastReview(r, sub, onChange) else reviewLocked(r, sub, onChange, reopen)
    }
    return sheet
}

/** 5분 이상 읽지 않아 별점·감상을 남길 수 없다는 안내(닫으면 상세로). 누르는 순간의 기록으로 다시 봐 그사이 넘겼으면 별점·감상 메뉴 */
private fun Context.reviewLocked(shown: BookRecord, sub: String?, onChange: () -> Unit, reopen: () -> Unit) {
    // 상세를 열어 둔 사이 5분을 넘겼으면(누르는 순간의 기록으로) 그대로 별점·감상 메뉴
    val r = shown.reload(this)
    if (r.canReview) { editLastReview(r, sub, onChange); return }
    Sheet(this).header(getString(R.string.review_title), getString(R.string.review_locked)).onDismiss { if (alive()) reopen() }.show()
}

/** 마지막 회차의 별점·감상 메뉴(상세의 별점 칸). 저장은 [saveRoundReview], 그 뒤는 [afterSave] */
private fun Context.editLastReview(r: BookRecord, sub: String?, onChange: () -> Unit) {
    val last = r.lastRound
    reviewSheet(r.book, sub, getString(R.string.save), last.review) { review -> afterSave(saveRoundReview(r, last, review), r, onChange) }
}

/** 화면이 끝나는 중이 아니어서 메뉴를 다시 띄울 수 있다(메뉴가 닫힌 뒤 다음 메뉴를 띄울 때) */
private fun Context.alive(): Boolean = (this as? Activity)?.let { !it.isFinishing && !it.isDestroyed } ?: true

/**
 * 회차 하나의 메뉴(전체 기록의 회차 줄, 회차 하나뿐인 책의 기록 칸): 머리에 `2회 · 2025년 4월 20일 – 5월 10일`, 그 아래
 * `읽기 시작한 날 · 2025년 4월 20일 ›`, 완독한 회차면 `완독한 날 · 2025년 5월 10일 ›`(누르면 날짜 입력, [dayRow]), 그 회차의 별점·감상,
 * 맨 아래 `취소` / `저장`. 진행 중 회차인데 별점·감상을 남길 수 없으면(5분 미만, [BookRecord.canReview]) 별점·감상 없이 날짜 줄과 `저장` / `취소`.
 * 닫히면(저장한 뒤에 온다) [then](고친 것을 다시 읽어 띄운다).
 */
private fun Context.editRound(r: BookRecord, round: BookRecord.Round, onChange: () -> Unit, then: () -> Unit) {
    val head = listOfNotNull(round.nth?.let { getString(R.string.finish_nth, it) }, period(round, withYear = true)).joinToString("  ·  ")
    val sub = "${r.book.title}  ·  $head"
    val finishId = round.finishId
    var day = round.to
    var start = round.from
    // 입력 아래 범위 안내는 누르는 순간의 기록과 오늘로(메뉴를 연 사이 다음 회차가 생겼거나 자정이 지났을 수 있다)
    fun hint(text: (BookRecord, BookRecord.Round, LocalDate) -> String): () -> String = {
        roundNow(r, round)?.let { (now, cur) -> text(now, cur, LocalDate.now()) }.orEmpty()
    }
    val rows = vbox().apply {
        // 시작한 날의 범위는 함께 고르는 완독한 날까지(완독한 날보다 뒤로 고르지 못하게)
        val startRow = dayRow(R.string.start_day, sub, start, hint { now, cur, today -> startDayRangeText(now, cur, today, day ?: cur.to) },
            { startDayError(r, round, it, day) }) { start = it }
        addView(startRow, lp(MATCH, WRAP))
        val to = day
        if (finishId != null && to != null) {
            // 두 줄 사이 틈은 줄 안쪽 여백으로(누르는 자리가 넓게)
            val half = dp(DAY_ROW_GAP_DP) / 2
            startRow.setPadding(0, 0, 0, half)
            // 완독한 날의 범위는 함께 고르는 읽기 시작한 날부터(두 날을 함께 앞으로 옮길 수 있게)
            addView(dayRow(R.string.finish_day, sub, to, hint { now, cur, today -> finishDayRangeText(now, cur, today, start) },
                { finishDayError(r, round, it, start) }) { day = it }.apply { setPadding(0, half, 0, 0) }, lp(MATCH, WRAP))
        }
    }
    // 저장에서 받지 못한 날이 있으면 메뉴가 닫힌 뒤 그 안내(머리 = 그 줄 이름)를 먼저 띄우고, 안내를 닫으면 [then](둘이 겹치지 않게 닫힐 때 한 곳에서)
    var rejected: Pair<Int, String>? = null
    val closed = {
        val error = rejected
        if (alive()) {
            if (error != null) Sheet(this).header(getString(error.first), error.second).onDismiss { if (alive()) then() }.show()
            else then()
        }
    }
    // 한 트랜잭션으로 묶지 않는다: 날 확인이 기록을 다시 읽으며 읽은 시간을 저장할 수 있어(ReadingLog), 되돌리면 그 메모리 사본과 DB 가 어긋난다
    fun save(review: Review?) { if (saveRound(r, round, review, day, start) { rejected = rejected ?: it }) onChange() }
    if (round.finished || r.canReview) {
        reviewSheet(r.book, head, getString(R.string.save), round.review, onDismiss = closed, top = rows) { save(it) }
    } else {
        Sheet(this).header(r.book.title, head, image = BookShelf.cover(this, r.book, dp(SHEET_COVER_DP))).note(rows)
            .item(getString(R.string.save), bold = true) { save(null) }.item(getString(R.string.cancel)) {}.noClose()
            .onDismiss(closed).show()
    }
}

/**
 * 회차 메뉴의 `저장`: [round] 의 별점·감상(null 이면 그대로)과 고른 완독한 날 [day]·읽기 시작한 날 [start](바뀐 것만). 날은 저장하는 순간의 기록으로 다시 확인하고,
 * 받지 못한 날은 그 줄 이름과 안내를 [reject] 에. 무엇이든 저장했으면 true.
 */
private fun Context.saveRound(
    r: BookRecord, round: BookRecord.Round, review: Review?, day: LocalDate?, start: LocalDate?, reject: (Pair<Int, String>) -> Unit,
): Boolean {
    var changed = false
    var to = round.to
    var stale = false
    val finishId = round.finishId
    val began = start.takeIf { it != round.from }
    // 완독한 회차는 그 완독 줄(id)에: 완독한 날은 고쳤을 때만, 저장하는 순간의 기록으로 다시 확인해서(메뉴를 연 사이 다음 회차가 시작했을 수 있다)
    if (finishId != null) {
        val moved = day.takeIf { it != round.to }
        // 완독한 날은 함께 고른 읽기 시작한 날부터 받는다. 그 시작한 날이 받을 수 없는 날이면 지금 시작한 날부터(완독한 날이 시작한 날 앞으로 가지 않게)
        val from = began?.takeIf { moved != null && startDayError(r, round, it, moved) == null } ?: round.from
        val error = moved?.let { finishDayError(r, round, it, from) }
        if (error != null) reject(R.string.finish_day to error)
        else if (moved != null) to = moved
        BookShelf.setFinish(this, r.book.id, finishId, review ?: round.review ?: Review(), moved.takeIf { error == null })
        changed = true
    } else if (review == null) stale = staleTitleOnly(r)
    else when (saveRoundReview(r, round, review)) {
        Saved.YES -> changed = true
        Saved.NO -> Unit
        // 목록에 없던 책인데 같은 제목의 책이 생겼다: 시작한 날도 저장하지 않는다(닫힌 뒤 회차 메뉴의 then() 이 지금 상태로 다시 띄운다)
        Saved.STALE -> stale = true
    }
    // 읽기 시작한 날은 고쳤을 때만, 저장한 완독한 날까지의 범위로 다시 확인해서
    began?.takeIf { !stale }?.let { b ->
        val error = startDayError(r, round, b, to)
        if (error != null) reject(R.string.start_day to error)
        else { saveBegan(r, round, b); changed = true }
    }
    return changed
}

private const val DAY_ROW_GAP_DP = 14

/**
 * 회차 메뉴를 연 뒤의 지금 기록과 그 안의 그 회차: 완독한 회차는 그 완독 줄로(없어졌으면 null), 진행 중 회차는 같은 순번([BookRecord.roundIn]).
 * 누르는·저장하는 순간에 다시 본다(메뉴를 연 사이 다음 회차가 생겼거나 다른 화면에서 완독했을 수 있다).
 */
private fun Context.roundNow(r: BookRecord, round: BookRecord.Round): Pair<BookRecord, BookRecord.Round>? {
    val now = r.reload(this)
    val id = round.finishId
    val cur = if (id == null) r.roundIn(now, round) else now.rounds.firstOrNull { it.finishId == id }
    return cur?.let { now to it }
}

/**
 * 회차 [round] 의 완독한 날을 [day] 로 바꿀 수 있는가(읽기 시작한 날 [from] 부터): 지금 기록과 오늘로 다시 본다(메뉴를 연 사이 다음 회차가
 * 생겼거나 자정이 지났을 수 있다). 바꿀 수 있으면 null, 아니면 지금 범위를 담은 안내.
 */
private fun Context.finishDayError(r: BookRecord, round: BookRecord.Round, day: LocalDate?, from: LocalDate?): String? {
    val (now, cur) = roundNow(r, round) ?: return getString(R.string.date_invalid, "")
    val today = LocalDate.now()
    return if (day != null && now.canFinishOn(cur, day, today, from)) null else getString(R.string.date_invalid, finishDayRangeText(now, cur, today, from))
}

/** 회차 [round] 의 읽기 시작한 날을 [day] 로 바꿀 수 있는가(완독한 날 [to] 까지, [finishDayError] 처럼 지금 기록과 오늘로). 되면 null, 아니면 안내. */
private fun Context.startDayError(r: BookRecord, round: BookRecord.Round, day: LocalDate?, to: LocalDate?): String? {
    val (now, cur) = roundNow(r, round) ?: return getString(R.string.date_invalid, "")
    val today = LocalDate.now()
    val end = to ?: cur.to
    return if (day != null && now.canStartOn(cur, day, today, end)) null else getString(R.string.date_invalid, startDayRangeText(now, cur, today, end))
}

/** 회차 [round] 의 읽기 시작한 날을 [day] 로 저장한다(그 회차가 그사이 완독됐으면 그 완독 줄에, 목록에 없던 책은 나간 책으로 적어 두고) */
private fun Context.saveBegan(r: BookRecord, round: BookRecord.Round, day: LocalDate) {
    val finishId = roundNow(r, round)?.second?.finishId
    withBook(r) { BookShelf.setBegan(this, it, finishId, day) }
}

/**
 * 회차 메뉴의 날짜 줄 `읽기 시작한 날 · 2025년 4월 20일 ›`(모르면 `모름 ›`). 누르면 날짜 한 줄 입력(머리 [label] / [sub], 지금 날짜를 채워,
 * 날짜 자판)이 위에 뜨고, 고른 날을 [onPick] 에. 입력 아래 안내는 [hint](고를 수 있는 범위), 받지 못하면 [error] 의 안내.
 * 실제로 바꾸는 것은 회차 메뉴의 `저장`(취소하면 그대로).
 */
private fun Context.dayRow(
    label: Int, sub: String, initial: LocalDate?, hint: () -> String, error: (LocalDate?) -> String?, onPick: (LocalDate) -> Unit,
): View {
    val value = text("", 21f)
    fun show(d: LocalDate?) { value.text = "${d?.format(dateFormat(R.string.date_year_fmt)) ?: getString(R.string.day_unknown)}  ›" }
    show(initial)
    var picked = initial
    return hbox().apply {
        addView(text(getString(label), 18f, color = Ui.GRAY), lp(0, WRAP, 1f))
        addView(value)
        setOnClickListener {
            Sheet(this@dayRow).header(getString(label), sub)
                .dateInput(picked?.format(DOTTED).orEmpty(), getString(R.string.date_hint, hint()), check = { v -> error(parseDay(v)) }) { v ->
                    parseDay(v)?.let { picked = it; show(it); onPick(it) }
                }
                .show()
        }
    }
}

/** 완독한 날을 고칠 수 있는 범위 글자 `2026.07.16 – 2026.10.14 사이`(읽은 기록이 없는 첫 완독은 [BookRecord.EARLIEST_DAY] 부터) */
internal fun Context.finishDayRangeText(r: BookRecord, round: BookRecord.Round, today: LocalDate, from: LocalDate? = round.from): String {
    val (earliest, latest) = r.finishDayRange(round, today, from)
    return getString(R.string.date_range, (earliest ?: BookRecord.EARLIEST_DAY).format(DOTTED), latest.format(DOTTED))
}

/** 읽기 시작한 날을 고칠 수 있는 범위 글자(완독한 날 [to] 까지): 앞 회차가 있으면 `2025.05.10 – 2026.11.01 사이`, 첫 회차면 `2026.11.01 까지` */
internal fun Context.startDayRangeText(r: BookRecord, round: BookRecord.Round, today: LocalDate, to: LocalDate?): String {
    val (earliest, latest) = r.startDayRange(round, today, to)
    return if (earliest == null) getString(R.string.date_until, latest.format(DOTTED))
    else getString(R.string.date_range, earliest.format(DOTTED), latest.format(DOTTED))
}

/** 완독한 날 입력·안내의 날짜 꼴 `2026.10.12` */
private val DOTTED: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")

/** 완독한 날 입력: `2026.10.12`·`2026-10-12`(월·일은 한 자리도)·`20261012` 꼴만 날짜로. 아니면(없는 날 포함) null. */
internal fun parseDay(s: String): LocalDate? {
    val t = s.trim()
    val m = DAY_DOTS.matchEntire(t) ?: DAY_DASHES.matchEntire(t) ?: DAY_DIGITS.matchEntire(t) ?: return null
    val (y, mo, d) = m.destructured
    return runCatching { LocalDate.of(y.toInt(), mo.toInt(), d.toInt()) }.getOrNull()
}

private val DAY_DOTS = Regex("""(\d{4})\.(\d{1,2})\.(\d{1,2})""")
private val DAY_DASHES = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""")
private val DAY_DIGITS = Regex("""(\d{4})(\d{2})(\d{2})""")

/** 메뉴를 띄울 수 있는 높이(화면의 내용 칸, 상단바가 보이는 화면이면 그만큼 뺀 높이) */
private fun Context.screenHeight(): Int =
    (this as? Activity)?.findViewById<View>(android.R.id.content)?.height?.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels

/**
 * 회차·합계의 읽은 시간을 보이는 최소(1분. 그보다 짧으면 `0분`이라 쓰지 않는다). 회차가 생기는지는 [ReadingLog.MIN_DAY_MS](5분)로 따로 정하고,
 * 이미 있는 회차(완독한 회차 등)는 5분이 안 돼도 시간을 보인다.
 */
private const val SHOWN_MS = 60_000L

/** 별점·감상 저장 결과: 저장함 / 바뀐 것 없음 / 열어 둔 사이 책이 바뀌어 저장하지 않음(지금 상태로 다시 띄운다) */
internal enum class Saved { YES, NO, STALE }

/**
 * 회차 [round] 의 별점·감상을 [review] 로: 완독한 회차는 그 완독 줄(id)에, 아니면 완독하지 않은 회차의 것으로(목록에 없던 책은 남기는 순간
 * 나간 책으로 적어 둔다, [bookId]). 진행 중이던 회차를 열어 둔 사이 다른 화면에서 완독했으면 그 완독한 회차에(같은 순번, 새로 생긴 다음 회차가 아니라).
 * 목록에 없던 책인데 열어 둔 사이 같은 제목의 책이 생겼으면(자동 추가·되살림) 어느 회차에 붙일지 정할 수 없어 저장하지 않는다([Saved.STALE]).
 */
internal fun Context.saveRoundReview(r: BookRecord, round: BookRecord.Round, review: Review): Saved {
    round.finishId?.let { BookShelf.setFinish(this, r.book.id, it, review); return Saved.YES }
    if (r.state == State.TITLE_ONLY) {
        if (staleTitleOnly(r)) return Saved.STALE
        if (review.isEmpty) return Saved.NO
    } else {
        r.roundIn(r.reload(this), round)?.finishId?.let { BookShelf.setFinish(this, r.book.id, it, review); return Saved.YES }
    }
    withBook(r) { BookShelf.setReview(this, it, review) }
    return Saved.YES
}

/** 목록에 없던 책인데 열어 둔 사이 같은 제목의 책이 생겼다(자동 추가·되살림): 어느 책·회차에 붙일지 정할 수 없어 저장하지 않는다 */
private fun Context.staleTitleOnly(r: BookRecord): Boolean =
    r.state == State.TITLE_ONLY && (BookShelf.findByTitle(this, r.book.title) != null || BookShelf.findLivePast(this, r.book.title) != null)

/** 상세의 별점·감상 칸에서 저장한 뒤: 저장했으면 [onChange], 열어 둔 사이 책이 바뀌었으면 지금 상태로 상세를 다시 */
private fun Context.afterSave(saved: Saved, r: BookRecord, onChange: () -> Unit) {
    when (saved) {
        Saved.YES -> onChange()
        Saved.STALE -> { onChange(); if (alive()) showBookDetail(r.reload(this), onChange) }
        Saved.NO -> Unit
    }
}

/** 책 상세의 머리(표지·제목·[sub]·완독 도장)만 든 메뉴. 상세와 전체 기록이 같이 쓴다. */
private fun Context.headSheet(r: BookRecord, sub: String?): Sheet {
    // 부제(저자·앱·진행률)는 두 줄까지(긴 저자가 접혀도 메뉴가 화면을 넘지 않게)
    val sheet = Sheet(this).header(r.book.title, sub, image = BookShelf.cover(this, r.book, dp(SHEET_COVER_DP)), lines = 2).paper()
    if (r.finished) sheet.stamp()
    return sheet
}

/**
 * 기록 칸: 마지막 회차 한 줄 `2회`(회색, 회차가 둘 이상일 때만) · 기간(검정, `8월 20일 – 9월 6일`, 진행 중이면 `10월 8일부터`,
 * 올해가 아니면 해를 붙인다) · 읽은 시간(회색, 오른쪽 정렬). 완독한 적이 있으면 그 아래 회색 `완독 4회 · 마지막 10월 8일`과
 * 오른쪽에 회차가 둘 이상이면 `전체 기록 ›`, 완독한 회차 하나뿐이면 `고치기 ›`. 완독한 적이 없으면 그 줄이 없으니 시간 뒤에 `›`
 * (누르면 회차 메뉴). `완독` 글자는 쓰지 않는다(표지의 띠가 말한다). 보일 것이 없으면 null.
 */
private fun Context.recordView(r: BookRecord): View? {
    val last = r.lastRound
    val hasRow = last.from != null || last.to != null || last.ms >= SHOWN_MS
    if (!hasRow && !r.finished) return null
    val today = LocalDate.now()
    return vbox().apply {
        if (hasRow) addView(hbox().apply {
            last.nth?.let { addView(text(getString(R.string.finish_nth, it), 18f, color = Ui.GRAY), lp(WRAP, WRAP).apply { marginEnd = dp(14) }) }
            addView(text(recordPeriod(last, today), 18f), lp(0, WRAP, 1f))
            val time = listOfNotNull(roundTime(last.ms), "›".takeUnless { r.finished }).joinToString("  ")
            addView(text(time, 18f, color = Ui.GRAY).apply { gravity = Gravity.END }, lp(WRAP, WRAP).apply { marginStart = dp(12) })
        }, lp(MATCH, WRAP).apply { topMargin = dp(6) })
        finishSummary(r, today)?.let { summary ->
            addView(hbox().apply {
                addView(text(summary, 16f, color = Ui.GRAY), lp(0, WRAP, 1f))
                addView(text(recordMore(r).orEmpty(), 16f, color = Ui.GRAY), lp(WRAP, WRAP).apply { marginStart = dp(12) })
            }, lp(MATCH, WRAP).apply { topMargin = dp(if (hasRow) 10 else 6) })
        }
    }
}

/** 기록 칸의 기간: 올해가 아니면 해를 붙인다 */
internal fun Context.recordPeriod(round: BookRecord.Round, today: LocalDate): String =
    period(round, withYear = (round.from ?: round.to)?.year?.let { it != today.year } == true)

/** 회차·합계의 읽은 시간 글자(1분 미만이면 null, [SHOWN_MS]) */
internal fun Context.roundTime(ms: Long): String? = duration(ms).takeIf { ms >= SHOWN_MS }

/** 기록 칸 아래 `완독 4회 · 마지막 10월 8일`(마지막 완독이 올해가 아니면 해를 붙인다). 완독한 적이 없으면 null. */
internal fun Context.finishSummary(r: BookRecord, today: LocalDate): String? {
    val lastDay = r.finishes.lastOrNull() ?: return null
    val day = lastDay.format(dateFormat(if (lastDay.year != today.year) R.string.date_year_fmt else R.string.date_short_fmt))
    return getString(R.string.finish_summary, r.finishes.size, day)
}

/** 기록 칸 오른쪽: 회차가 둘 이상이면 `전체 기록 ›`, 완독한 회차 하나뿐이면 `고치기 ›`, 아니면 null(누를 것 없음) */
internal fun Context.recordMore(r: BookRecord): String? = when {
    r.rounds.size >= 2 -> getString(R.string.all_history)
    r.lastRound.finished -> getString(R.string.review_edit)
    else -> null
}

/**
 * 전체 기록 요약 줄의 굵은 `완독 5회` 뒤: `  ·  모두 21시간 7분  ·  2023년부터`. 모두 = 회차 시간의 합(어느 회차에도 들지 않은 잠깐 연
 * 시간은 뺀다, 1분 미만이면 뺀다), 시작한 해는 여러 해에 걸쳐 읽었을 때만(마지막 회차가 진행 중이면 [today] 의 해까지 읽은 것으로).
 */
internal fun Context.historyRest(r: BookRecord, today: LocalDate): String {
    val rounds = r.rounds
    val firstYear = rounds.first().let { (it.from ?: it.to)?.year }
    val lastYear = rounds.last().takeIf { it.finished }?.to?.year ?: today.year
    return listOfNotNull(
        roundTime(rounds.sumOf { it.ms })?.let { getString(R.string.total_time, it) },
        firstYear?.takeIf { it < lastYear }?.let { getString(R.string.since_year, it) },
    ).joinToString("") { "  ·  $it" }
}

/** 전체 기록 한 줄의 둘째 줄 회색 글자: `2회 · 진행 중 · 3시간 54분`(완독한 회차는 `진행 중` 없이, 1분 미만이면 시간 없이) */
internal fun Context.roundDetail(round: BookRecord.Round): String =
    listOfNotNull(getString(R.string.finish_nth, round.nth ?: 0), getString(R.string.in_progress).takeUnless { round.finished }, roundTime(round.ms))
        .joinToString("  ·  ")

private fun Context.dateFormat(pattern: Int) = DateTimeFormatter.ofPattern(getString(pattern), resources.configuration.locales[0])

/**
 * 한 회차의 기간: `8월 20일 – 9월 6일`(해를 넘기면 끝 날에 해), 진행 중이면 `10월 8일부터`(시작일을 모르면 `시작일 모름`), 하루면 그날.
 * [withYear] 면 시작 날(하루면 그날)에도 해를 붙인다(`2026년 9월 20일 – 10월 8일`).
 */
internal fun Context.period(s: BookRecord.Round, withYear: Boolean): String {
    val fmt = dateFormat(R.string.date_short_fmt)
    val yearFmt = dateFormat(R.string.date_year_fmt)
    val startFmt = if (withYear) yearFmt else fmt
    val from = s.from
    val to = s.to
    return when {
        // 진행 중인데 시작일을 모르면(기록을 켜기 전·v2 에서 다시 읽던 책) `시작일 모름`
        to == null -> from?.let { getString(R.string.since_date, it.format(startFmt)) } ?: getString(R.string.start_unknown)
        from == null || from >= to -> to.format(startFmt)
        else -> "${from.format(startFmt)} – ${to.format(if (from.year != to.year) yearFmt else fmt)}"
    }
}

/**
 * 전체 기록(회차가 둘 이상, 상세의 기록 칸을 누르면): 머리는 상세와 같고([headSheet]), 그 아래 요약 한 줄
 * 굵은 `완독 5회` + 회색 `· 모두 21시간 7분 · 2023년부터`(회차 시간의 합, 시작한 해는 여러 해에 걸쳐 읽었을 때만),
 * 그 아래 최근 회차부터 세로 타임라인([Timeline]): 줄마다 해를 붙인 기간 / 회색 `5회 · 3시간 12분`(진행 중이면 `2회 · 진행 중 · …`)과
 * 그 회차의 작은 별 / 그 회차의 감상 한 줄(넘치면 `…`), 남긴 것이 없으면 옅은 회색 `별점·감상 남기기 ›`. 완독한 회차는 채운 점, 진행 중은 테두리만.
 * 줄을 누르면 그 회차 메뉴([editRound]: 읽기 시작한 날·완독한 날·별점·감상)를 띄우고 닫으면 다시 전체 기록으로. 첫 줄 오른쪽 끝의 휴지통([TrashIcon])은
 * 그 회차만 지울지 묻는다([forgetRound], 닫으면 전체 기록, 회차가 하나가 됐으면 상세로).
 * 화면에 다 들어가지 않으면 쪽으로 나눠 요약 줄 오른쪽에 `‹ 1 / 2 ›`(눌러서 넘김). 닫으면(닫기·뒤로 가기·바깥) 상세로 돌아간다([back]).
 */
private fun Context.showHistory(r: BookRecord, sub: String?, onChange: () -> Unit, back: () -> Unit) {
    val rounds = r.rounds.asReversed()
    val sheet = headSheet(r, sub)
    val rest = historyRest(r, LocalDate.now())
    val pageText = text("", 18f, color = Ui.GRAY)
    val summary = hbox().apply {
        addView(text(getString(R.string.finish_count, r.finishes.size), 18f, Ui.bold))
        addView(text(rest, 18f, color = Ui.GRAY), lp(0, WRAP, 1f))
        addView(pageText, lp(WRAP, WRAP).apply { marginStart = dp(12) })
    }
    // 줄(회차 메뉴)·휴지통(회차 지우기)을 눌러 다른 메뉴로 가면 닫혀도 상세로 돌아가지 않는다([leaveFor]).
    var editing = false
    lateinit var dialog: android.app.Dialog
    // 전체 기록을 닫고 [go] 의 메뉴로(닫히면 지금 기록으로 전체 기록을 다시, 회차가 하나가 됐으면 상세로). 머리의 부제(진행률·앱)도 그사이 바뀌었을 수 있다.
    fun leaveFor(go: (then: () -> Unit) -> Unit) {
        if (!dialog.isShowing) return
        editing = true
        dialog.dismiss()
        go { r.reload(this).let { now -> if (now.rounds.size >= 2) showHistory(now, detailSub(now), onChange, back) else back() } }
    }
    val rows = rounds.map { round ->
        vbox().apply {
            tag = round.finished
            // 첫 줄: 기간과 오른쪽 끝 휴지통(누르면 이 회차만 지울지 묻는다, [forgetRound]). 휴지통 칸은 둘째 줄까지 눌린다(줄 전체는 회차 메뉴).
            val trash = TrashIcon(context)
            addView(hbox().apply {
                addView(text(period(round, withYear = true), 18f), lp(0, WRAP, 1f))
                addView(trash, lp(dp(TrashIcon.WIDTH_DP), MATCH))
            })
            trash.setOnClickListener { leaveFor { then -> forgetRound(r, round, onChange, then) } }
            trash.extendTouch(this)
            addView(hbox().apply {
                addView(text(roundDetail(round), 16f, color = Ui.GRAY))
                round.review?.halves?.takeIf { it > 0 }?.let { halves ->
                    addView(StarBar(context, 14, 2, filled = Ui.GRAY).apply { this.halves = halves }, lp(WRAP, WRAP).apply { marginStart = dp(12) })
                }
            }, lp(WRAP, WRAP).apply { topMargin = dp(6) })
            val review = round.review
            when {
                review == null || review.isEmpty -> addView(text(getString(R.string.review_add), 16f, color = Ui.LIGHT_GRAY), lp(MATCH, WRAP).apply { topMargin = dp(6) })
                review.text.isNotBlank() -> addView(text(review.text, 16f), lp(MATCH, WRAP).apply { topMargin = dp(6) })
            }
            setOnClickListener { leaveFor { then -> editRound(r, round, onChange, then) } }
        }
    }
    // 쪽 나누기: 화면 높이에서 타임라인 없이 띄운 메뉴(머리·요약 줄·닫기)의 높이를 뺀 높이에 들어가는 만큼씩(줄 높이는 감상이 있으면 한 줄 더).
    val width = resources.displayMetrics.widthPixels - dp(Ui.MARGIN) * 2
    val gap = dp(Timeline.GAP_DP)
    fun paginate(): List<MutableList<View>> {
        val avail = screenHeight() - headSheet(r, sub).note(summary).height() - dp(TIMELINE_TOP_DP)
        val pages = mutableListOf<MutableList<View>>()
        var used = 0
        rows.forEach { row ->
            val h = row.heightAt(width - dp(Timeline.GUTTER_DP))
            val cur = pages.lastOrNull()
            if (cur == null || used + gap + h > avail) { pages += mutableListOf(row); used = h } else { cur += row; used += gap + h }
        }
        return pages
    }
    // 여러 쪽이면 요약 줄 오른쪽의 쪽 표시(`‹ 1 / 3 ›`)가 요약을 접어 높일 수 있으니, 표시를 넣고 다시 나눈다(쪽 수가 바뀌면 한 번 더).
    // 가장 넓은 표시(마지막 쪽 `10 / 10`)로 잰다(쪽을 넘겨 자리 수가 늘어도 요약이 더 접히지 않게)
    var pages = paginate()
    repeat(2) {
        if (pages.size > 1) {
            pageText.text = pageLabel(object : Paged {
                override val page = pages.size - 1
                override val pageCount = pages.size
                override fun next() {}
                override fun prev() {}
            })
            pages = paginate()
        }
    }
    val pageH = pages.maxOf { page -> page.sumOf { it.measuredHeight } + (page.size - 1) * gap }
    val timeline = Timeline(this, pages, pageH)
    timeline.onPageChanged = { pageText.text = pageLabel(timeline) }
    pageText.text = pageLabel(timeline)
    pageText.pagesClick(timeline)
    sheet.pages(timeline)
    sheet.note(vbox().apply {
        addView(summary)
        addView(timeline, lp(MATCH, WRAP).apply { topMargin = dp(TIMELINE_TOP_DP) })
    })
    dialog = sheet.onDismiss { if (!editing && alive()) back() }.show()
}

private const val TIMELINE_TOP_DP = 16

/**
 * 전체 기록의 휴지통: 회차 [round] 하나만 지울지 묻는다(`2회 기록을 지울까요?` / `책 · 기간`, 설명 글 없이 `이 회차 지우기` / `취소`). 지우면 그 회차의 완독·별점·감상과
 * 그 회차에 읽은 시간이 이 책에서 빠진다([BookShelf.forgetRound]. 하루 읽은 시간·연속일은 그대로). 읽고 있는 책의 진행 중 회차를 지우면 읽고 있는
 * 책에서도 빠진다. 누르는 순간의 기록으로 다시 보고, 그사이 그 회차가 없어졌거나 회차가 하나가 됐으면 지우지 않는다. 닫히면 [then].
 */
private fun Context.forgetRound(r: BookRecord, round: BookRecord.Round, onChange: () -> Unit, then: () -> Unit) {
    val nth = getString(R.string.finish_nth, round.nth ?: 0)
    Sheet(this).header(getString(R.string.forget_round_confirm, nth), "${r.book.title}  ·  ${period(round, withYear = true)}")
        .item(getString(R.string.forget_round), bold = true) {
            val (now, cur) = roundNow(r, round) ?: return@item
            if (now.rounds.size < 2) return@item
            if (BookShelf.forgetRound(this, now.book.id, cur.finishId, now.dropOf(cur, LocalDate.now()))) onChange()
        }
        .item(getString(R.string.cancel)) {}.noClose().onDismiss { if (alive()) then() }.show()
}

/** 전체 기록 회차 줄의 휴지통(회색 선 그림, 글자를 키우면 함께 커진다 [Ui.textGrow]). 칸 폭 [WIDTH_DP], 그림은 오른쪽 끝에 맞춘다. */
private class TrashIcon(context: Context) : View(context) {
    companion object {
        const val WIDTH_DP = 48
        private const val SIZE_DP = 22f
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.GRAY; style = Paint.Style.STROKE; strokeWidth = context.dpf(1.8f * Ui.textGrow); strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()

    init { contentDescription = context.getString(R.string.forget_round) }

    /** 누르는 자리를 [row] 의 둘째 줄까지 넓힌다(첫 줄 높이만으로는 손가락에 좁다) */
    fun extendTouch(row: ViewGroup) {
        row.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val parent = parent as? View ?: return@addOnLayoutChangeListener
            val below = maxOf(parent.top + bottom, row.getChildAt(1)?.bottom ?: 0)
            val hit = Rect(parent.left + left - context.dp(8), parent.top + top - context.dp(8), row.width, below)
            row.touchDelegate = TouchDelegate(hit, this)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val s = context.dpf(SIZE_DP * Ui.textGrow)
        val cx = width - s / 2
        val cy = height / 2f
        path.reset()
        // 뚜껑·손잡이
        path.moveTo(cx - s * 0.45f, cy - s * 0.32f); path.lineTo(cx + s * 0.45f, cy - s * 0.32f)
        path.moveTo(cx - s * 0.14f, cy - s * 0.32f); path.lineTo(cx - s * 0.14f, cy - s * 0.46f)
        path.lineTo(cx + s * 0.14f, cy - s * 0.46f); path.lineTo(cx + s * 0.14f, cy - s * 0.32f)
        // 통과 안의 두 줄
        path.moveTo(cx - s * 0.34f, cy - s * 0.32f); path.lineTo(cx - s * 0.28f, cy + s * 0.46f)
        path.lineTo(cx + s * 0.28f, cy + s * 0.46f); path.lineTo(cx + s * 0.34f, cy - s * 0.32f)
        for (x in floatArrayOf(-0.1f, 0.1f)) { path.moveTo(cx + s * x, cy - s * 0.14f); path.lineTo(cx + s * x, cy + s * 0.3f) }
        canvas.drawPath(path, paint)
    }
}

/**
 * 전체 기록의 세로 타임라인: 줄마다 왼쪽에 점(첫 줄의 가운데 높이, 완독한 회차(줄의 tag 가 true)는 채운 점, 아니면 테두리만), 점 사이를 회색 세로선으로 잇는다.
 * 쪽마다 [pages] 의 줄들. 앞뒤 쪽이 있으면 선을 위·아래 끝까지 이어 그린다. 여러 쪽이면 덜 찬 쪽도 가장 긴 쪽 높이([pageH])로(메뉴 높이가 그대로이게).
 */
private class Timeline(context: Context, private val pages: List<List<View>>, pageH: Int) : LinearLayout(context), Paged {
    companion object {
        const val GUTTER_DP = 28
        const val GAP_DP = 16
    }

    override var page = 0
        private set
    override val pageCount = pages.size

    var onPageChanged: (() -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = context.dpf(1.5f) }

    init {
        orientation = VERTICAL
        setWillNotDraw(false)
        setPadding(context.dp(GUTTER_DP), 0, 0, 0)
        if (pageCount > 1) minimumHeight = pageH
        render()
    }

    override fun next() = showPage(page + 1)
    override fun prev() = showPage(page - 1)

    private fun showPage(p: Int) {
        val target = p.coerceIn(0, pageCount - 1)
        if (target == page) return
        page = target
        render()
        onPageChanged?.invoke()
    }

    private fun render() {
        removeAllViews()
        pages[page].forEachIndexed { i, v ->
            addView(v, lp(MATCH, WRAP).apply { if (i > 0) topMargin = context.dp(GAP_DP) })
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (childCount == 0) return
        val x = context.dpf(7)
        val r = context.dpf(6)
        // 점 높이: 줄의 첫 글자 줄 가운데
        val ys = (0 until childCount).map { i ->
            val row = getChildAt(i) as ViewGroup
            val first = row.getChildAt(0)
            row.top + first.top + first.height / 2f
        }
        paint.color = Ui.LIGHT_GRAY
        if (page > 0) canvas.drawLine(x, 0f, x, ys.first() - r, paint)
        ys.zipWithNext { a, b -> canvas.drawLine(x, a + r, x, b - r, paint) }
        if (page < pageCount - 1) canvas.drawLine(x, ys.last() + r, x, height.toFloat(), paint)
        paint.color = Ui.BLACK
        ys.forEachIndexed { i, y ->
            paint.style = if (getChildAt(i).tag == true) Paint.Style.FILL else Paint.Style.STROKE
            canvas.drawCircle(x, y, if (paint.style == Paint.Style.STROKE) r - paint.strokeWidth / 2 else r, paint)
        }
        paint.style = Paint.Style.STROKE
    }
}
