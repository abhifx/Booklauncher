package com.woody.pebbledesk

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Space

/**
 * 아래에서 올라오는 메뉴. 위쪽 굵은 선, 머리(아이콘·이름·설명), 항목들, 맨 아래 오른쪽 `닫기`.
 * 별점·감상 칸([review])이 있으면 항목 대신 별·감상 입력과 맨 아래 `취소` / 할 일 한 줄.
 * 박스 없이 선으로만 나눈다.
 * 가로 화면에서 항목이 있는 메뉴는 두 칸으로 나눈다: 왼쪽에 머리·칸, 오른쪽에 항목과 맨 아래 `닫기`(세로로 쌓으면 화면 높이를 넘는다).
 */
class Sheet(private val context: Context) {
    companion object {
        /** 떠 있는 메뉴와 띄운 화면·그때의 기록 판 번호([Db.changes]) */
        private class Shown(val dialog: Dialog, val owner: Context, val changes: Long)

        private val open = mutableListOf<Shown>()

        /** 닫힌 메뉴와 끝난 화면의 메뉴를 버린다(화면이 끝나면 메뉴는 닫힘 알림 없이 사라지고 isShowing 도 그대로라, 끝난 화면을 붙들지 않게) */
        private fun prune() = open.removeAll { !it.dialog.isShowing || (it.owner as? android.app.Activity)?.isDestroyed == true }

        /**
         * 화면([owner])에 돌아왔을 때(onPostResume, 날이 바뀌어 저장한 뒤): 그 화면에 떠 있던 메뉴 중 띄운 뒤 DB 가 바뀐 것(다른 화면에서 고침·이북 앱에서 편 책 자동 추가 등)을 닫는다
         * (오래된 기록으로 저장하지 않게). 닫힘 동작이 있는 메뉴(전체 기록·회차 메뉴)는 지금 기록으로 다시 뜨고, 별점·감상·입력 메뉴는 닫히기만 한다.
         * 바뀐 것이 없으면(화면만 껐다 켬) 그대로 둔다.
         * 같은 화면 안에서는 기록이 바뀌는 것이 그 메뉴들의 동작뿐이라(자동 추가는 이북 앱이 앞에 있을 때) 돌아올 때만 보면 된다.
         */
        fun dismissStale(owner: Context) {
            prune()
            val mine = open.filter { it.owner === owner }
            if (mine.isEmpty()) return
            val now = Db.changes()
            mine.filter { it.changes != now && it.dialog.isShowing }.forEach { it.dialog.dismiss() }
        }
    }

    private class Item(val label: String, val bold: Boolean, val right: String?, val action: () -> Unit)

    /** 체크 줄([check]): 누르면 [onClick] 하고 메뉴는 그대로(모든 체크 칸을 [on] 으로 다시 본다) */
    private class Check(val label: String, val sub: String?, val tag: String?, val right: String?, val on: () -> Boolean, val onClick: () -> Unit)
    private val checks = mutableListOf<Check>()

    /** 맨 아래 줄 왼쪽 글자와 누르면 할 일(닫고 한다). 없으면 오른쪽 `닫기`만. */
    private var footLeft: Pair<String, () -> Unit>? = null
    /** 맨 아래 줄 오른쪽을 `닫기` 대신 이 글자로, 누르면 닫고 할 일 */
    private var done: Pair<String, () -> Unit>? = null

    private var app: AppEntry? = null
    /** 앱 아이콘 대신 보여 줄 그림(책 표지). 폭 [SHEET_COVER_DP], 2:3 으로 그린다. */
    private var image: Bitmap? = null
    private var title: String = ""
    private var subtitle: CharSequence? = null
    /** 부제의 최대 줄 수(넘으면 끝에 줄임표) */
    private var subtitleLines = 16
    private val items = mutableListOf<Item>()
    /**
     * 글자 입력 줄들(지금 값, 비었을 때 회색 빈칸 글)과 다 쓰면 부를 일(줄마다 다듬은 값). 있으면 머리 아래에 두고 [saveLabel](없으면 `저장`)을
     * 맨 위 항목으로 둔다. 첫 줄은 비우면 저장하지 않고, 나머지 줄은 비워도 된다.
     */
    private var fields: List<Pair<String, String?>> = emptyList()
    private var onFields: ((List<String>) -> Unit)? = null
    private var saveLabel: String? = null
    private val hasInput get() = onFields != null
    private var numeric = false
    /** 입력이 날짜인가(날짜 자판) */
    private var date = false
    /** 입력 줄 아래 회색 안내(없으면 두지 않는다) */
    private var inputHint: String? = null
    /** 저장 전에 확인한다: 틀렸으면 알릴 글(안내 자리에 굵게, 저장하지 않고 메뉴를 둔다), 맞으면 null */
    private var validate: ((String) -> String?)? = null
    /** 아래 `닫기` 줄을 두는가. 확인 창은 `취소`를 항목으로 두고 닫기를 뺀다. */
    private var closeRow = true

    fun noClose() = apply { closeRow = false }

    /** 닫기 줄 없이, 뒤로 가기·바깥 누르기로도 닫히지 않는다(받는 중 창. 부르는 쪽이 [show] 가 돌려준 창을 닫는다). */
    private var locked = false
    /** 기록이 바뀌어도 닫지 않는다([dismissStale]. 기록을 저장하지 않는 안내 창) */
    private var keep = false

    fun keepOnChange() = apply { keep = true }

    fun locked() = apply { locked = true; closeRow = false }

    /** 아래에 붙이지 않고 화면 가운데에 띄운다(테두리를 두르고 좌우를 띄운다). 놓치면 안 되는 안내(자동 추가가 꺼짐). */
    private var centered = false

    fun centered() = apply { centered = true }

    /** 머리와 항목 사이에 두는 칸(책 상세의 회차별 기록·별점 칸). 칸마다 위에 옅은 선, 화면 폭으로(좌우 여백 안). [onClick] 이 있으면 칸 전체를 누를 수 있다. */
    private class Note(val view: View, val onClick: (() -> Unit)?)
    private val notes = mutableListOf<Note>()

    fun note(view: View, onClick: (() -> Unit)? = null) = apply { notes += Note(view, onClick) }

    /** 페이지 버튼([PageKeys])으로 넘길 메뉴 안의 목록(전체 기록). 없으면 메뉴가 떠 있는 동안 페이지 버튼은 아무것도 넘기지 않는다. */
    private var pages: Paged? = null

    fun pages(rows: Paged) = apply { pages = rows }

    /** 별점·감상 칸(지금 값, 아래 오른쪽 굵은 글자, 누르면 부를 일). 있으면 머리 아래에 두고 맨 아래 `취소` / 할 일 한 줄. */
    private var review: Triple<Review, String, (Review) -> Unit>? = null

    fun review(initial: Review, action: String, onDone: (Review) -> Unit) = apply {
        review = Triple(initial, action, onDone); closeRow = false
    }

    /** 머리 오른쪽 위에 완독 도장(위 끝은 메뉴 위 선에서 잘리고 아래는 기록 칸에 조금 걸친다) */
    private var stamped = false

    fun stamp() = apply { stamped = true }

    /** 머리와 칸들(책 상세의 정보)을 책 상세 색면([Palette.Area.DETAIL]) 바탕에 둬 아래 메뉴(흰색)와 나눈다. 컬러에서만. */
    private var paper: Int? = null

    fun paper() = apply { paper = Palette.current.field(Palette.Area.DETAIL) }

    fun header(title: String, subtitle: CharSequence? = null, app: AppEntry? = null, image: Bitmap? = null, lines: Int = 16) = apply {
        this.title = title; this.subtitle = subtitle; this.app = app; this.image = image; subtitleLines = lines
    }

    /** 항목 한 줄. [right] 가 있으면 오른쪽에 회색으로(지금 값 `소설 ›`). */
    fun item(label: String, bold: Boolean = false, right: String? = null, action: () -> Unit) = apply { items += Item(label, bold, right, action) }

    /**
     * 체크 줄(왼쪽 체크 칸 · 이름([sub] 는 그 아래 작게, [tag] 는 뒤에 작게) · 오른쪽 회색 [right]). 누르면 [onClick] 하고 닫지 않는다.
     * 한 줄을 누르면 다른 줄의 칸도 바뀔 수 있어(`전체`) 모든 칸을 다시 본다. 맨 아래 [done] 으로 정한다.
     */
    fun check(label: String, sub: String? = null, tag: String? = null, right: String? = null, on: () -> Boolean, onClick: () -> Unit) =
        apply { checks += Check(label, sub, tag, right, on, onClick) }

    /** 맨 아래 줄 왼쪽 글자(`카테고리 관리 ›`): 누르면 닫고 [action] */
    fun footer(label: String, action: () -> Unit) = apply { footLeft = label to action }

    /** 맨 아래 줄 오른쪽을 `닫기` 대신 [label](굵게): 누르면 닫고 [action] */
    fun done(label: String, action: () -> Unit) = apply { done = label to action }

    /**
     * 한 줄 입력(박스 없이 아래 굵은 선). 빈 글자는 저장하지 않는다. [numeric] 이면 숫자 키보드.
     * [check] 가 있으면 저장 전에 본다(틀렸으면 입력 줄 아래에 그 글을 굵게 알리고 저장하지 않는다. 처음에는 빈 줄).
     */
    fun input(initial: String, numeric: Boolean = false, check: ((String) -> String?)? = null, onDone: (String) -> Unit) = apply {
        fields = listOf(initial to null); onFields = { onDone(it[0]) }; this.numeric = numeric
        if (check != null) { validate = check; inputHint = inputHint ?: "" }
    }

    /**
     * 여러 줄 입력([fields]: 줄마다 지금 값·빈칸 글). 첫 줄은 비우면 저장하지 않고 나머지는 비워도 된다. 자판의 다음 키로 아래 줄로,
     * 마지막 줄의 완료 키나 [save] 줄로 저장한다([onDone] 에 줄마다 다듬은 값).
     */
    fun inputs(fields: List<Pair<String, String>>, save: String, onDone: (List<String>) -> Unit) = apply {
        this.fields = fields; onFields = onDone; saveLabel = save
    }

    /** 날짜 한 줄 입력(날짜 자판): 아래에 [hint], 저장 전에 [check](틀렸으면 그 글을 굵게 알리고 저장하지 않는다). */
    fun dateInput(initial: String, hint: String, check: (String) -> String?, onDone: (String) -> Unit) = apply {
        fields = listOf(initial to null); onFields = { onDone(it[0]) }; date = true; inputHint = hint; validate = check
    }

    /** 가로 화면에서 두 칸으로 나누는가: 아래에 붙는 메뉴이고 항목이 있으며 별점·감상·입력 칸이 없을 때(키보드와 함께 쓰는 메뉴는 한 칸) */
    private val split get() = !centered && review == null && !hasInput && (items.isNotEmpty() || checks.isNotEmpty()) && context.landscape()

    /** 두 칸 메뉴의 오른쪽(항목) 칸 폭 */
    private fun menuWidth() = maxOf(context.dp(SPLIT_MENU_DP), (context.resources.displayMetrics.widthPixels * SPLIT_MENU_RATIO).toInt())

    /** 가운데 창 폭: 화면 폭에서 좌우 여백을 뺀 만큼. 가로 화면은 [CENTERED_MAX_DP] 까지(낮은 화면에 글이 덜 접히게, 너무 넓지는 않게) */
    private fun centeredWidth(): Int =
        minOf(context.resources.displayMetrics.widthPixels - context.dp(Ui.MARGIN) * 2, context.dp(CENTERED_MAX_DP))

    /** 머리: 앱 아이콘 또는 표지, 제목·설명, 도장이 있으면 그 자리를 비운다. 두 칸이면 왼쪽 칸이 좁아 도장을 줄인다. */
    private fun head(): LinearLayout {
        val c = context
        val small = split
        val stampDp = if (small) SPLIT_STAMP_DP else STAMP_DP
        val stampEnd = if (small) SPLIT_STAMP_END_DP else STAMP_END_DP
        val head = (if (stamped) StampedRow(c, stampDp, stampEnd, if (small) SPLIT_STAMP_TOP_DP else STAMP_TOP_DP) else c.hbox())
            .apply { setPadding(c.dp(Ui.MARGIN), c.dp(22), c.dp(Ui.MARGIN), c.dp(20)) }
        app?.let {
            head.addView(c.picture(AppIcons.icon(c, it, c.dp(45))),
                lp(c.dp(45), c.dp(45)).apply { marginEnd = c.dp(16) })
        }
        image?.let {
            // 표지는 제목 윗변에 맞춘다(글자 줄이 많아도 표지가 내려가지 않게).
            head.addView(c.coverImage(it), lp(c.dp(SHEET_COVER_DP), c.dp(SHEET_COVER_DP) * 3 / 2).apply {
                marginEnd = c.dp(16); gravity = Gravity.TOP; topMargin = c.dp(6)
            })
        }
        val names = c.vbox()
        // 가운데 안내 창은 제목이 곧 알림이라 세 줄까지(글자를 키워도 '…꺼져 있습니다'가, 긴 앱 이름이 든 책 파일 읽기 안내가 잘리지 않게). 아래 메뉴는 한 줄.
        names.addView(c.text(title, 29f, Ui.heavy, lines = if (centered) 3 else 1))
        subtitle?.let { names.addView(c.text(it, 18f, color = Ui.GRAY, lines = subtitleLines), lp(WRAP, WRAP).apply { topMargin = c.dp(7) }) }
        head.addView(names, lp(0, WRAP, 1f).apply {
            if (image != null) gravity = Gravity.TOP
            // 제목이 도장에 덮이지 않게 도장 왼쪽 끝까지 비운다.
            if (stamped) marginEnd = c.dp(stampEnd + stampDp / 2 - Ui.MARGIN)
        })
        return head
    }

    /**
     * 띄웠을 때의 높이(화면 폭에서 실제로 만들어 잰다). 칸을 화면에 들어가는 만큼만 담을 때 쓴다(상세의 감상 줄 수, 전체 기록의 쪽 나누기).
     * 잰 뒤에는 칸의 뷰를 떼어 둔다(띄울 때 다시 붙인다).
     */
    fun height(): Int {
        val screen = context.resources.displayMetrics.widthPixels
        val h = layout(Dialog(context, R.style.Sheet)).heightAt(if (centered) centeredWidth() else screen)
        notes.forEach { (it.view.parent as? ViewGroup)?.removeView(it.view) }
        return h
    }

    /** 줄(항목·체크 줄)이 많아 메뉴가 화면을 넘으면 한 쪽에 이만큼씩([fitRows]). null 이면 다 보인다. */
    private var perPage: Int? = null

    /**
     * 메뉴가 화면(이 화면의 내용 칸) 높이를 넘으면 넘는 만큼 줄을 덜어 쪽으로 나눈다(카테고리가 많은 보기·고르기 메뉴). 넘지 않으면 그대로.
     * 별점·입력이 있는 메뉴는 나누지 않는다. 칸([note])이 있는 메뉴(책 상세)는 부르는 쪽이 칸을 먼저 줄여 맞추고, 그래도 넘치면 줄을 나눈다.
     */
    private fun fitRows() {
        val count = items.size + checks.size
        if (perPage != null || count < 2 || centered || review != null || hasInput) return
        val avail = (context as? android.app.Activity)?.findViewById<View>(android.R.id.content)?.height?.takeIf { it > 0 }
            ?: context.resources.displayMetrics.heightPixels
        val over = height() - avail
        if (over <= 0) return
        val rowH = context.rowDp(Ui.ROW_DP) + context.dp(1)
        perPage = (count - (over + rowH - 1) / rowH).coerceAtLeast(1)
    }

    /** 닫히면(닫기·뒤로 가기·바깥 누르기, 항목을 눌러 닫힐 때도) 부를 일 */
    private var onDismiss: (() -> Unit)? = null

    fun onDismiss(action: () -> Unit) = apply { onDismiss = action }

    fun show(): Dialog {
        fitRows()
        val dialog = Dialog(context, R.style.Sheet)
        val root = layout(dialog)
        val shown = Shown(dialog, context, Db.changes())
        prune()
        // 닫지 못하게 한 메뉴(받는 중 진행 막대)와 기록을 저장하지 않는 안내 창은 기록이 바뀌어도 닫지 않는다
        if (!locked && !keep) open += shown
        dialog.setOnDismissListener { open -= shown; onDismiss?.invoke() }
        dialog.setContentView(root)
        dialog.window?.apply {
            setGravity(if (centered) Gravity.CENTER else Gravity.BOTTOM)
            val width = if (centered) centeredWidth() else ViewGroup.LayoutParams.MATCH_PARENT
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            decorView.setPadding(0, 0, 0, 0)
            // 입력 줄이 있으면 키보드를 띄우고, 메뉴가 키보드 위로 올라오게 한다.
            if (hasInput) setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            if (review != null) setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        // 감상을 쓰다 바깥을 잘못 눌러 글이 날아가지 않게(뒤로 가기·`취소`로 닫는다).
        if (review != null) dialog.setCanceledOnTouchOutside(false)
        if (locked) dialog.setCancelable(false)
        pages?.let { rows ->
            val keys = PageKeyHandler(context) { rows.turn(it) }
            dialog.setOnKeyListener { _, _, event -> keys.onKey(event) }
        }
        dialog.show()
        return dialog
    }

    /** 메뉴의 내용(머리·칸·별점 감상·입력·항목·닫기). 누르면 [dialog] 를 닫는다. */
    private fun layout(dialog: Dialog): LinearLayout {
        val c = context
        // 닫고 할 일. 닫히기 전에 두 번 눌려도 한 번만(같은 책을 두 번 다시 읽기·적어 두기 하지 않게).
        fun dismissThen(action: () -> Unit) {
            if (!dialog.isShowing) return
            dialog.dismiss(); action()
        }
        val root = c.vbox()
        if (centered) {
            // 가운데에 뜨면 아래가 가려지지 않는다. 위에 굵은 선 대신 둘레에 테두리.
            root.background = GradientDrawable().apply { setColor(android.graphics.Color.WHITE); setStroke(c.dp(2), Ui.BLACK) }
        } else {
            Crema.reserveHiddenBottom(root)
            root.addView(c.hline(2, inset = false))
        }
        // 머리와 칸들을 한 묶음에 넣는다(정보 바탕을 칠할 때 도장이 넘친 자리까지 같은 바탕이게).
        val info = c.vbox()
        paper?.let { info.setBackgroundColor(it) }
        // 항목과 `닫기`를 넣을 곳: 두 칸이면 오른쪽 칸(옅은 세로 선 뒤), 아니면 머리 아래
        val split = split
        val menu: LinearLayout
        if (split) {
            // 아래 여백을 두고, 오른쪽 칸은 위에도 여백을 둔다(왼쪽은 머리의 위 여백이 있고, 위에 두면 도장이 메뉴 위 선을 덮는다)
            val columns = EqualHeightRow(c).apply { setPadding(0, 0, 0, c.dp(SPLIT_PAD_DP)) }
            root.addView(columns, lp(MATCH, WRAP))
            columns.addView(info, lp(0, WRAP, 1f))
            // 선은 높이 0 으로 재고([EqualHeightRow] 가 칸 높이로 늘린다. WRAP 이면 빈 뷰가 화면 높이를 다 차지한다)
            columns.addView(c.vline(16, heightPx = 0))
            menu = c.vbox().apply { setPadding(0, c.dp(SPLIT_PAD_DP), 0, 0) }
            columns.addView(menu, lp(menuWidth(), WRAP))
            if (stamped) columns.clipChildren = false
        } else {
            root.addView(info, lp(MATCH, WRAP))
            menu = root
        }
        // 도장은 머리 아래로 조금 넘쳐 기록 칸 위에 걸친다(뒤에 그리는 선·글자가 위에 온다).
        if (stamped) { root.clipChildren = false; info.clipChildren = false }

        val head = head()
        info.addView(head)
        notes.forEach { n ->
            info.addView(c.hline(1, Ui.DIVIDER))
            info.addView(FrameLayout(c).apply {
                setPadding(c.dp(Ui.MARGIN), c.dp(NOTE_PAD_TOP_DP), c.dp(Ui.MARGIN), c.dp(NOTE_PAD_BOTTOM_DP))
                addView(n.view)
                n.onClick?.let { action -> setOnClickListener { dismissThen(action) } }
            }, lp(MATCH, WRAP))
        }

        review?.let { (initial, action, onDone) ->
            root.addView(c.hline(1, Ui.DIVIDER))
            val hint = c.text(c.reviewHint(initial.halves), 15f, color = Ui.LIGHT_GRAY).apply { gravity = Gravity.CENTER_HORIZONTAL }
            val stars = StarBar(c, 54, 18).apply {
                halves = initial.halves
                editable = true
                onChange = { hint.text = c.reviewHint(it) }
            }
            root.addView(stars, lp(MATCH, WRAP).apply { topMargin = c.dp(18) })
            root.addView(hint, lp(MATCH, WRAP).apply { topMargin = c.dp(8); bottomMargin = c.dp(14) })
            root.addView(c.hline(1, Ui.DIVIDER))
            val field = EditText(c).apply {
                setText(initial.text)
                setSp(22f)
                setTextColor(Ui.BLACK)
                setHint(R.string.review_text_hint)
                setHintTextColor(Ui.LIGHT_GRAY)
                background = null
                gravity = Gravity.TOP or Gravity.START
                setPadding(0, c.dp(14), 0, c.dp(14))
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                // 가로 화면에서도 전체 화면 입력 창으로 바꾸지 않는다(별점이 가려진다)
                imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
                minLines = 4
                maxLines = 4
                filters = arrayOf(InputFilter.LengthFilter(2000))
                // 키보드가 올라오면 메뉴가 다 들어가지 않아 머리(표지·제목)를 감춘다.
                setOnFocusChangeListener { _, focused -> if (focused) head.visibility = View.GONE }
            }
            root.addView(field, lp(MATCH, WRAP).apply { marginStart = c.dp(Ui.MARGIN); marginEnd = c.dp(Ui.MARGIN) })
            root.addView(c.hline(2))
            root.addView(c.hbox().apply {
                setPadding(c.dp(Ui.MARGIN), 0, c.dp(Ui.MARGIN), 0)
                addView(c.text(c.getString(R.string.cancel), 24f).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 0, c.dp(24), 0)
                    setOnClickListener { dialog.dismiss() }
                }, lp(WRAP, MATCH))
                addView(View(c), lp(0, 0, 1f))
                addView(c.text(action, 24f, Ui.bold).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(c.dp(24), 0, 0, 0)
                    setOnClickListener { dismissThen { onDone(Review(stars.halves, field.text.toString())) } }
                }, lp(WRAP, MATCH))
            }, lp(MATCH, c.rowDp(Ui.ROW_DP)))
            // 처음에는 감상 칸에 초점을 주지 않는다(키보드가 바로 뜨지 않게. 별만 고르는 일이 많다).
            root.isFocusableInTouchMode = true
        }

        onFields?.let { onDone ->
            val edits = fields.mapIndexed { i, (initial, placeholder) ->
                EditText(c).apply {
                    setText(initial)
                    setSelection(text.length)
                    setSp(24f)
                    setTextColor(Ui.BLACK)
                    placeholder?.let { hint = it; setHintTextColor(Ui.LIGHT_GRAY) }
                    background = null
                    setPadding(0, c.dp(8), 0, c.dp(8))
                    isSingleLine = true
                    inputType = when {
                        date -> InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_DATE
                        numeric -> InputType.TYPE_CLASS_NUMBER
                        else -> InputType.TYPE_CLASS_TEXT
                    }
                    // 마지막 줄이 아니면 자판의 다음 키로 아래 줄로(안드로이드가 다음 입력 줄로 옮긴다)
                    imeOptions = (if (i == fields.lastIndex) EditorInfo.IME_ACTION_DONE else EditorInfo.IME_ACTION_NEXT) or EditorInfo.IME_FLAG_NO_EXTRACT_UI
                }
            }
            val hint = inputHint?.let { c.text(it, 15f, color = Ui.LIGHT_GRAY, lines = 2) }
            val save = {
                val values = edits.map { it.text.toString().trim() }
                val error = validate?.invoke(values[0])
                if (error != null) hint?.apply { text = error; typeface = Ui.bold; setTextColor(Ui.BLACK) }
                else if (values[0].isNotEmpty()) {
                    // 키보드를 내리고 닫는다(아래 메뉴 위에 떠서 열었을 때 키보드가 남아 그 메뉴의 아래 줄을 가리지 않게).
                    c.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(edits[0].windowToken, 0)
                    dismissThen { onDone(values) }
                }
            }
            edits.last().setOnEditorActionListener { _, action, _ -> if (action == EditorInfo.IME_ACTION_DONE) { save(); true } else false }
            edits.forEach { field ->
                root.addView(field, lp(MATCH, WRAP).apply { marginStart = c.dp(Ui.MARGIN); marginEnd = c.dp(Ui.MARGIN) })
                root.addView(c.hline(2))
            }
            hint?.let { root.addView(it, lp(MATCH, WRAP).apply { marginStart = c.dp(Ui.MARGIN); marginEnd = c.dp(Ui.MARGIN); topMargin = c.dp(8); bottomMargin = c.dp(10) }) }
            root.addView(c.hbox().apply {
                setPadding(c.dp(Ui.MARGIN), 0, c.dp(Ui.MARGIN), 0)
                addView(c.text(saveLabel ?: c.getString(R.string.save), 24f, Ui.bold), lp(MATCH, WRAP))
                setOnClickListener { save() }
            }, lp(MATCH, c.rowDp(Ui.ROW_DP)))
            edits[0].requestFocus()
        }

        // 항목·체크 줄(줄마다 위에 옅은 선). 두 칸이면 첫 줄 위 선은 두지 않는다(메뉴 위 굵은 선 바로 아래라)
        val rows = mutableListOf<View>()
        fun row(i: Int, content: View) = c.vbox().apply {
            if (!split || i > 0) addView(c.hline(1, Ui.DIVIDER))
            addView(content, lp(MATCH, c.rowDp(Ui.ROW_DP)))
        }.also { rows += it }
        // 고르는 줄(체크)이 할 일(항목) 위에: 고른 뒤 아래 항목으로 정한다
        val marks = mutableListOf<Pair<CheckMark, Check>>()
        checks.forEach { check ->
            val mark = CheckMark(c).apply { on = check.on() }
            marks += mark to check
            row(rows.size, c.hbox().apply {
                setPadding(c.dp(Ui.MARGIN), 0, c.dp(Ui.MARGIN), 0)
                addView(mark, lp(c.dp(CHECK_DP), c.dp(CHECK_DP)).apply { marginEnd = c.dp(16) })
                addView(c.vbox().apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(c.hbox().apply {
                        gravity = Gravity.BOTTOM
                        addView(c.text(check.label, 24f))
                        check.tag?.let { addView(c.text(it, 15f, color = Ui.GRAY), lp(WRAP, WRAP).apply { marginStart = c.dp(10); bottomMargin = c.dp(3) }) }
                    })
                    check.sub?.let { addView(c.text(it, 15f, color = Ui.GRAY)) }
                }, lp(0, WRAP, 1f))
                check.right?.let { addView(c.text(it, 21f, color = Ui.GRAY), lp(WRAP, WRAP).apply { marginStart = c.dp(16) }) }
                setOnClickListener {
                    check.onClick()
                    // 바뀐 칸만 다시 그린다
                    marks.forEach { (m, ch) -> ch.on().let { v -> if (m.on != v) m.on = v } }
                }
            })
        }
        items.forEach { item ->
            row(rows.size, c.hbox().apply {
                setPadding(c.dp(Ui.MARGIN), 0, c.dp(Ui.MARGIN), 0)
                addView(c.text(item.label, 24f, if (item.bold) Ui.bold else Ui.regular), if (item.right == null) lp(MATCH, WRAP) else lp(WRAP, WRAP))
                item.right?.let { addView(c.text(it, 21f, color = Ui.GRAY).apply { gravity = Gravity.END }, lp(0, WRAP, 1f).apply { marginStart = c.dp(16) }) }
                setOnClickListener { dismissThen(item.action) }
            })
        }
        val per = perPage
        if (per == null || rows.size <= per) rows.forEach { menu.addView(it, lp(MATCH, WRAP)) }
        else {
            // 화면을 넘는 메뉴: 한 쪽에 [per] 줄씩, 머리 오른쪽에 쪽 표시(누르거나 페이지 버튼으로 넘긴다). 덜 찬 쪽도 높이는 같다.
            val box = c.vbox().apply { minimumHeight = per * (c.rowDp(Ui.ROW_DP) + c.dp(1)) }
            menu.addView(box, lp(MATCH, WRAP))
            val label = c.text("", 18f, Ui.bold, Ui.GRAY).apply { setPadding(c.dp(16), c.dp(8), 0, c.dp(8)) }
            head.addView(label, lp(WRAP, WRAP).apply { gravity = Gravity.TOP })
            val pager = RowPager(box, rows, per) { label.text = pageLabel(it) }
            label.pagesClick(pager)
            if (pages == null) pages = pager
        }
        if (review == null) menu.addView(c.hline(1, Ui.DIVIDER))
        // 두 칸이면 `닫기`는 오른쪽 칸 맨 아래(왼쪽 칸이 더 높아도)
        // (빈 뷰는 높이를 모를 때 화면 높이를 다 차지한다. Space 는 0 이다가 [EqualHeightRow] 가 높이를 정하면 남는 만큼 늘어난다)
        if (split) menu.addView(Space(c), lp(MATCH, 0, 1f))
        if (closeRow) menu.addView(c.hbox().apply {
            // 왼쪽 글자(있으면) · 오른쪽 `닫기`(또는 [done] 의 글자)
            footLeft?.let { (label, action) ->
                addView(c.text(label, 21f).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(c.dp(Ui.MARGIN), 0, c.dp(24), 0)
                    setOnClickListener { dismissThen(action) }
                }, lp(WRAP, MATCH))
            }
            addView(View(c), lp(0, 0, 1f))
            addView(c.text(done?.first ?: c.getString(R.string.close), 21f, Ui.bold).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(c.dp(24), 0, c.dp(Ui.MARGIN), 0)
                setOnClickListener { done?.second?.let { dismissThen(it) } ?: dialog.dismiss() }
            }, lp(WRAP, MATCH))
        }, lp(MATCH, c.dp(CLOSE_ROW_DP)))

        // 입력이 없는 메뉴는 초점을 받지 않는다(받을 수 있으면 기기 버튼을 눌렀을 때 누를 수 있는 글자에 회색 초점 표시가 남는다).
        if (!hasInput && review == null) root.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        return root
    }
}

/**
 * 칸들을 나란히 두고 모두 가장 높은 칸의 높이로 맞추는 줄(두 칸 메뉴: 오른쪽 칸의 `닫기`를 맨 아래에, 가운데 세로 선을 끝까지).
 * 높이가 WRAP 인 줄 안의 MATCH 칸은 다른 칸 높이를 따라가 더 높은 칸이 잘려서 직접 맞춘다.
 */
private class EqualHeightRow(context: Context) : LinearLayout(context) {
    init {
        orientation = HORIZONTAL
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val h = measuredHeight - paddingTop - paddingBottom
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val m = child.layoutParams as MarginLayoutParams
            val target = h - m.topMargin - m.bottomMargin
            if (child.measuredHeight != target) child.measure(
                MeasureSpec.makeMeasureSpec(child.measuredWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(maxOf(0, target), MeasureSpec.EXACTLY),
            )
        }
    }
}

/** 화면을 넘는 메뉴의 줄을 쪽으로([Sheet.perPage]): [box] 에 지금 쪽의 줄들, 바뀌면 [onPage] */
private class RowPager(private val box: LinearLayout, private val rows: List<View>, private val per: Int, private val onPage: (Paged) -> Unit) : Paged {
    override var page = 0
        private set
    override val pageCount = (rows.size + per - 1) / per

    init { render() }

    override fun next() = show(page + 1)
    override fun prev() = show(page - 1)

    private fun show(p: Int) {
        val target = p.coerceIn(0, pageCount - 1)
        if (target == page) return
        page = target
        render()
    }

    private fun render() {
        box.removeAllViews()
        rows.drop(page * per).take(per).forEach { box.addView(it, lp(MATCH, WRAP)) }
        onPage(this)
    }
}

/** 체크 칸([Sheet.check]): 꺼지면 검은 테두리, 켜지면 검게 칠하고 흰 ✓ */
private class CheckMark(context: Context) : View(context) {
    var on = false
        set(v) { field = v; invalidate() }
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: android.graphics.Canvas) {
        val s = minOf(width, height).toFloat()
        val stroke = s * 0.09f
        paint.color = Ui.BLACK
        if (on) {
            paint.style = android.graphics.Paint.Style.FILL
            canvas.drawRect(0f, 0f, s, s, paint)
            paint.color = android.graphics.Color.WHITE
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = s * 0.13f
            paint.strokeCap = android.graphics.Paint.Cap.SQUARE
            val path = android.graphics.Path().apply { moveTo(s * 0.22f, s * 0.52f); lineTo(s * 0.42f, s * 0.72f); lineTo(s * 0.8f, s * 0.28f) }
            canvas.drawPath(path, paint)
        } else {
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = stroke
            canvas.drawRect(stroke / 2, stroke / 2, s - stroke / 2, s - stroke / 2, paint)
        }
    }
}

/** 체크 칸 크기 */
private const val CHECK_DP = 24

/** 머리의 표지 폭(책 상세. 띠 글자가 읽히게 크게) */
const val SHEET_COVER_DP = 76

/** 칸([Sheet.note])의 위·아래 여백, 맨 아래 `닫기` 줄 높이 */
private const val NOTE_PAD_TOP_DP = 10
private const val NOTE_PAD_BOTTOM_DP = 14
private const val CLOSE_ROW_DP = 62

/** 머리의 완독 도장: 지름, 가운데가 오른쪽 끝에서 안쪽으로, 위에서 아래로(지름의 절반보다 작아 위가 잘리고, 아래는 머리 밖으로 조금 넘친다) */
private const val STAMP_DP = 220
private const val STAMP_END_DP = 110
private const val STAMP_TOP_DP = 64

/** 가로 화면 두 칸 메뉴: 오른쪽 항목 칸 폭(최소 dp, 화면 폭에 대한 비율 중 큰 것), 왼쪽 칸이 좁아 줄인 도장(지름·오른쪽에서·위에서) */
private const val SPLIT_MENU_DP = 340
private const val SPLIT_MENU_RATIO = 0.36f
private const val SPLIT_STAMP_DP = 170
private const val SPLIT_STAMP_END_DP = 85
private const val SPLIT_STAMP_TOP_DP = 50
/** 두 칸 메뉴의 아래 여백과 오른쪽(항목) 칸 위 여백 */
private const val SPLIT_PAD_DP = 14
/** 가운데 창의 가장 넓은 폭(가로 화면) */
private const val CENTERED_MAX_DP = 720

/**
 * 되돌리기 어려운 일 확인: 머리 [title]·[subtitle], 아래에 두 줄 `[action]`(굵게, 누르면 [onConfirm]) / `취소`.
 * (`닫기`보다 할 일과 그만두기가 맞서 보여 고르기 쉽다)
 */
fun Context.confirm(title: String, subtitle: String?, action: String, onConfirm: () -> Unit) {
    Sheet(this).header(title, subtitle).item(action, bold = true) { onConfirm() }.item(getString(R.string.cancel)) {}.noClose().show()
}

/** 고르기 메뉴: 지금 값은 굵게 + ✓ */
fun Context.choose(title: String, options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    val sheet = Sheet(this).header(title)
    options.forEachIndexed { i, label ->
        sheet.item(if (i == selected) "$label  ✓" else label, bold = i == selected) { onPick(i) }
    }
    sheet.show()
}
