package com.woody.booklauncher

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import java.io.File
import java.io.OutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * ⑮ 월간 결산(올해 읽은 책 제목 오른쪽 `10월 결산 ›`). 그달의 독서를 한 장으로 그린 그림([RecapImage])을 보여 주고,
 * 아래 줄 `‹ 9월` · `공유` · `11월 ›`(밀기·페이지 버튼도)로 달을 넘긴다. 기록을 시작한 달보다 앞, 이번 달보다 뒤로는 가지 않는다.
 * 그림을 누르면 화면 가득 크게(상단바·제목·아래 줄·그림 바깥 여백 없이), 다시 누르거나 뒤로 가면 돌아온다. 크게 본 채로도 밀어서 달을 넘긴다.
 * `공유`는 아래 메뉴: 파일로 저장(위치 고르기) · 다른 앱으로 보내기. 저장·보내는 그림은 휴대폰에서 볼 그림이라 화면이 흑백이어도 늘 컬러.
 */
class RecapActivity : EinkActivity() {
    private var month: YearMonth = YearMonth.now()
    private lateinit var titleText: TextView
    /** 제목 옆 보기 거르기 `전체 ▾`(카테고리가 있을 때만) */
    private lateinit var filterText: TextView
    private lateinit var preview: PictureView
    /** 그림 크게 보기(화면 전체를 덮는다, 그림의 바깥 여백을 잘라 [RecapImage.content]) */
    private lateinit var full: PictureView
    private lateinit var prev: TextView
    private lateinit var next: TextView
    /** 지금 그달의 데이터(저장·보내기 그림을 컬러로 다시 그릴 때) */
    private var data: Recap.Month? = null
    /** 저장·보내기에 쓰는 컬러 그림(휴대폰에서 볼 그림이라 화면이 흑백이어도 늘 컬러, 처음 쓸 때 그린다) */
    private var shareImage: Bitmap? = null
    /** 보낼 PNG 를 뒤에서 쓰는 중(두 번 눌러 같은 파일을 함께 쓰지 않게) */
    private var writing = false
    /** 그려 둔 달과 기록 상태(돌아왔을 때 같으면 다시 그리지 않는다) */
    private var shown: List<Any?>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        month = (savedInstanceState?.getString(EXTRA_MONTH) ?: intent.getStringExtra(EXTRA_MONTH))
            ?.let { runCatching { YearMonth.parse(it) }.getOrNull() } ?: YearMonth.now()
        val root = vbox()
        root.addView(titleRow("", Palette.Area.LIBRARY) { finish() }.apply {
            titleText = getChildAt(1) as TextView
            filterText = text("", 21f, color = Ui.GRAY).apply { setPadding(dp(14), dp(20), dp(8), dp(20)) }
            addView(filterText, lp(WRAP, WRAP).apply { topMargin = dp(6) })
        })
        root.addView(hline(2))
        preview = PictureView(this).apply {
            setPadding(dp(Ui.MARGIN), dp(14), dp(Ui.MARGIN), dp(14))
            setOnClickListener { showFull(true) }
        }
        root.addView(preview, lp(MATCH, 0, 1f))
        root.addView(hline(1, inset = false))
        val footer = FrameLayout(this)
        prev = text("", 21f, Ui.bold).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Ui.MARGIN), 0, dp(24), 0)
            setOnClickListener { move(-1) }
        }
        footer.addView(prev, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.START))
        footer.addView(text(getString(R.string.recap_share), 21f, Ui.bold).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), 0, dp(24), 0)
            setOnClickListener { share() }
        }, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.CENTER_HORIZONTAL))
        next = text("", 21f, Ui.bold).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), 0, dp(Ui.MARGIN), 0)
            setOnClickListener { move(1) }
        }
        footer.addView(next, FrameLayout.LayoutParams(WRAP, MATCH, Gravity.END))
        root.addView(footer, lp(MATCH, dp(Ui.FOOTER_DP)))
        full = PictureView(this).apply {
            setBackgroundColor(Color.WHITE)
            visibility = View.GONE
            setOnClickListener { showFull(false) }
        }
        render()
        setContentView(FrameLayout(this).apply {
            addView(root, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(full, FrameLayout.LayoutParams(MATCH, MATCH))
        })
    }

    /** 크게 보기: 상단바를 숨기고 그림의 바깥 흰 여백을 잘라 화면 폭·높이를 채운다(그림은 4:5, 여백을 빼면 페블 화면과 거의 같은 비율) */
    private fun showFull(on: Boolean) {
        full.visibility = if (on) View.VISIBLE else View.GONE
        setStatusBarVisible(!on)
    }

    /** 뒤로 가기: 크게 보는 중이면 먼저 닫는다 */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (full.visibility == View.VISIBLE) showFull(false) else @Suppress("DEPRECATION") super.onBackPressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(EXTRA_MONTH, month.toString())
    }

    override fun onResume() {
        super.onResume()
        if (!recreating && state() != shown) render()
    }

    override fun onSwipe(toLeft: Boolean): Boolean = move(if (toLeft) 1 else -1)

    /** 앞쪽 끝: 기록을 시작한 달(모르면 이번 달) */
    private fun earliest(): YearMonth = ReadingLog.since(this)?.let { YearMonth.from(it) }?.coerceAtMost(YearMonth.now()) ?: YearMonth.now()

    private fun move(by: Long): Boolean {
        val to = month.plusMonths(by)
        if (to < earliest() || to > YearMonth.now()) return false
        month = to
        render()
        return true
    }

    private fun state(): List<Any?> = listOf(month, Db.changes(), YearMonth.now(),
        ReadingLog.days(this, ReadingLog.readerPackages(this))?.get(LocalDate.now()), prefs.statsPicked(HomePrefs.StatsScreen.RECAP))

    private fun render() {
        // 보기 거르기: 고른 카테고리의 책과 그 시간만(`전체`면 통계에서 뺀 카테고리의 책만 빼고). 고른 이름은 그림 머리에도.
        val filter = StatsFilter.of(this, prefs.statsPicked(HomePrefs.StatsScreen.RECAP))
        showFilter(filter)
        val data = Recap.load(this, month, prefs, filter, filterName(filter).takeUnless { filter.all })
        this.data = data
        shown = state()
        // 화면에는 화면 색 설정대로. 컬러면 그 그림을 그대로 저장·보내기에 쓴다.
        val bmp = RecapImage.draw(this, data, Palette.current)
        shareImage = if (Palette.current.color) bmp else null
        // 화면에는 크레마 화면 대비만큼 밝힌 사본(그림 뷰는 크레마가 다시 밝히지 않는다). 저장·보내기는 컬러 원본([sharedImage]).
        val shownBmp = bmp.copy(Bitmap.Config.ARGB_8888, true).also { Crema.undoContrast(it, Crema.contrastTone(this)) }
        preview.bitmap = shownBmp
        full.bitmap = RecapImage.content(shownBmp)
        titleText.text = title(this, month)
        val monthFmt = DateTimeFormatter.ofPattern(getString(R.string.month_fmt), resources.configuration.locales[0])
        val canPrev = month > earliest()
        val canNext = month < YearMonth.now()
        prev.text = getString(R.string.recap_prev, month.minusMonths(1).format(monthFmt))
        next.text = getString(R.string.recap_next, month.plusMonths(1).format(monthFmt))
        prev.setTextColor(if (canPrev) Ui.BLACK else Ui.DIVIDER)
        next.setTextColor(if (canNext) Ui.BLACK else Ui.DIVIDER)
    }

    /** 제목 옆 `전체 ▾`(카테고리가 없으면 두지 않는다). 누르면 보기 메뉴(줄마다 그것만 골랐을 때 그달 읽은 시간). */
    private fun showFilter(filter: StatsFilter) {
        filterText.visibility = if (filter.categories.isEmpty()) View.GONE else View.VISIBLE
        filterText.text = "${filterName(filter)} ▾"
        filterText.setOnClickListener {
            val raw = Recap.days(this)
            val shown = month
            val monthName = shown.format(DateTimeFormatter.ofPattern(getString(R.string.month_fmt), resources.configuration.locales[0]))
            showStatsFilter(filter, getString(R.string.filter_recap_sub, monthName), { f ->
                val d = f.days(raw)
                duration((1..shown.lengthOfMonth()).sumOf { d.total(shown.atDay(it)) })
            }) { prefs.setStatsPicked(HomePrefs.StatsScreen.RECAP, it); render() }
        }
    }

    /** 파일 이름 `BookLauncher-2026-10.png` */
    private fun fileName() = "BookLauncher-$month.png"

    /** 저장·보내기 그림: 늘 컬러(화면이 흑백이면 컬러로 한 번 더 그린다) */
    private fun sharedImage(): Bitmap? = shareImage ?: data?.let { RecapImage.draw(this, it, Palette.COLOR) }?.also { shareImage = it }

    private fun share() {
        val bmp = sharedImage() ?: return
        val sheet = Sheet(this).header(titleText.text.toString())
        sheet.item(getString(R.string.recap_save_file)) { saveAsFile() }
            .item(getString(R.string.recap_send)) { send(bmp) }
            .show()
    }

    private fun done(ok: Boolean) {
        if (isFinishing || isDestroyed) return
        if (ok) Sheet(this).header(getString(R.string.recap_saved)).show()
        else Sheet(this).header(getString(R.string.recap_save_failed)).show()
    }

    /** 저장할 위치·이름을 고르게 한다(시스템 파일 고르기) */
    private fun saveAsFile() {
        val pick = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/png"
            putExtra(Intent.EXTRA_TITLE, fileName())
        }
        runCatching { startActivityForResult(pick, PICK_FILE) }.onFailure { done(false) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_FILE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val bmp = sharedImage() ?: return
        // PNG 압축은 느린 기기에서 화면을 멈추게 하니 뒤에서. 고르기 창이 이미 파일을 만들었으니 늘 쓴다(건너뛰면 빈 파일이 남는다).
        Bg.run({ contentResolver.openOutputStream(uri, "wt")?.use { writePng(bmp, it) } ?: error("no stream") }) { done(it.isSuccess) }
    }

    /** 안드로이드 공유 창으로 다른 앱에 보낸다(그림은 이 앱만 내주는 임시 파일, [RecapFiles]) */
    private fun send(bmp: Bitmap) {
        if (writing) return
        writing = true
        val name = fileName()
        // PNG 는 뒤에서 쓰고(느린 기기에서 화면이 멈추지 않게), 다 쓰면 공유 창을 띄운다
        Bg.run({
            val file = RecapFiles.file(this, name)
            file.parentFile?.listFiles()?.filter { it != file }?.forEach { it.delete() }
            file.outputStream().use { writePng(bmp, it) }
            file
        }) { result ->
            writing = false
            // 그사이 화면을 닫았거나 다시 만들었으면(회전·설정) 조용히 끝낸다
            if (isFinishing || isDestroyed) return@run
            val ok = result.isSuccess && runCatching {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, RecapFiles.uri(this@RecapActivity, name))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(send, getString(R.string.recap_send)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }.isSuccess
            if (!ok) done(false)
        }
    }

    private fun writePng(bmp: Bitmap, out: OutputStream) {
        if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, out)) error("compress")
    }

    companion object {
        private const val EXTRA_MONTH = "month"
        private const val PICK_FILE = 1

        fun intent(context: Context, month: YearMonth = YearMonth.now()) =
            Intent(context, RecapActivity::class.java).putExtra(EXTRA_MONTH, month.toString())

        /** 화면 제목 `10월 결산`(올해가 아니면 `2025년 12월 결산`) */
        fun title(context: Context, month: YearMonth): String {
            val res = if (month.year == YearMonth.now().year) R.string.recap_month_fmt else R.string.recap_month_year_fmt
            val f = DateTimeFormatter.ofPattern(context.getString(res), context.resources.configuration.locales[0])
            return context.getString(R.string.recap_title, month.format(f))
        }

        /** 올해 읽은 책 제목 줄의 `10월 결산 ›` */
        fun link(context: Context, month: YearMonth): String {
            val f = DateTimeFormatter.ofPattern(context.getString(R.string.month_fmt), context.resources.configuration.locales[0])
            return context.getString(R.string.recap_link, month.format(f))
        }
    }
}

/** 월간 결산 그림을 다른 앱에 보낼 때만 내주는 파일([RecapActivity]). 캐시 폴더 `recap/` 의 PNG 하나만 읽기로 연다. */
class RecapFiles : android.content.ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri) = "image/png"

    override fun openFile(uri: Uri, mode: String): android.os.ParcelFileDescriptor {
        val f = file(context ?: throw java.io.FileNotFoundException(), uri.lastPathSegment ?: throw java.io.FileNotFoundException())
        if (!f.isFile) throw java.io.FileNotFoundException(uri.toString())
        return android.os.ParcelFileDescriptor.open(f, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
    }

    /** 받는 앱이 이름·크기를 물어 오면(파일 이름으로 저장하는 앱) */
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): android.database.Cursor {
        val f = file(context ?: throw java.io.FileNotFoundException(), uri.lastPathSegment.orEmpty())
        val cols = projection ?: arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE)
        return android.database.MatrixCursor(cols).apply {
            addRow(cols.map { c ->
                when (c) {
                    android.provider.OpenableColumns.DISPLAY_NAME -> f.name
                    android.provider.OpenableColumns.SIZE -> f.length()
                    else -> null
                }
            }.toTypedArray())
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        private fun authority(context: Context) = "${context.packageName}.recap"

        /** `recap/` 안의 [name](경로를 벗어나지 못하게 이름만) */
        fun file(context: Context, name: String): File =
            File(File(Storage.of(context).cacheDir, "recap").apply { mkdirs() }, File(name).name)

        fun uri(context: Context, name: String): Uri =
            Uri.Builder().scheme("content").authority(authority(context)).appendPath(name).build()
    }
}
