package com.woody.booklauncher

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View

/**
 * 업데이트 창들(설정 › 업데이트와 홈의 새 판 알림이 같이 쓴다): 바뀐 점과 `설치`, 설치 허용 안내, 받는 중 창, 설치 실패 알림.
 * 화면마다 하나씩 두고, 그 화면이 앞에 있는 동안 [Update.onChange] 에서 [onChange] 를 부른다.
 */
class UpdateSheets(private val activity: Activity) {
    /** 받는 중 창(받기가 끝나거나 실패하면 닫는다) */
    private var progressDialog: Dialog? = null

    /** 상태가 바뀌었을 때·화면에 돌아왔을 때: 받기가 끝났으면 받는 중 창을 닫고, 설치에 실패했으면 알린다 */
    fun onChange() {
        closeProgress()
        showInstallFailure()
    }

    fun dismiss() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    /** 설정 › 업데이트: 새 판의 바뀐 점(릴리스 설명, 화면에서 [NOTES_LINES] 줄까지)과 `설치` */
    fun offer(release: Update.Release) {
        Sheet(activity).header("BookLauncher ${release.version}", releaseNotes(activity, release.notes), lines = NOTES_LINES)
            .item(activity.getString(R.string.update_install), bold = true) { install(release) }
            .show()
    }

    /**
     * 홈: 새 판이 나왔다는 창(화면 가운데). 머리 `새 버전이 나왔습니다` · `BookLauncher 1.5.9 · 지금 1.5.8`, 반 줄 띄워 바뀐 점,
     * `설치`(굵게) · `이 버전은 알리지 않기`([onSkip]) · `닫기`. 글자를 키워 화면을 넘으면 바뀐 점을 줄여 들어가게 한다.
     * 닫히면(어느 항목이든) [onClosed]: `설치` 가 설치 허용 안내로 갔으면 true(허용하고 돌아오면 다시 띄우게).
     * 화면이 다시 만들어져(회전) 창이 사라질 때는 부르지 않는다.
     */
    fun notice(release: Update.Release, maxHeight: Int, onSkip: () -> Unit, onClosed: (askedAllow: Boolean) -> Unit): Dialog {
        val versions = activity.getString(R.string.update_new_versions, release.version, activity.appVersion())
        val notes = releaseNotes(activity, release.notes)
        val desc = SpannableStringBuilder(versions)
        if (notes != null) desc.append("\n").append(" \n", RelativeSizeSpan(0.5f), 0).append(notes)
        // 닫힘 알림은 항목 동작 뒤에 온다(Dialog 가 따로 보낸다)
        var askedAllow = false
        fun sheet(lines: Int) = Sheet(activity).header(activity.getString(R.string.update_new_title), desc, lines = lines)
            .centered().keepOnChange()
            .item(activity.getString(R.string.update_install), bold = true) { install(release) { askedAllow = true } }
            .item(activity.getString(R.string.update_skip)) { onSkip() }
            .onDismiss { onClosed(askedAllow) }
        // 판 줄 + 반 줄 + 바뀐 점. 넘치면 넷씩 줄인다(판 줄은 남긴다).
        var lines = NOTES_LINES + 2
        var s = sheet(lines)
        while (lines > 1 && s.height() > maxHeight) {
            lines = maxOf(1, lines - 4)
            s = sheet(lines)
        }
        return s.show()
    }

    /** 이 앱에 '알 수 없는 앱 설치'가 허용돼 있으면 받아서 설치하고(이 판의 설치 창을 띄워 둔 적이 있으면 그 창을), 아니면 허용 화면 안내([onNotAllowed]) */
    private fun install(release: Update.Release, onNotAllowed: () -> Unit = {}) {
        if (Update.resumeInstall(activity, release.version)) return
        if (activity.packageManager.canRequestPackageInstalls()) {
            Update.install(activity, release)
            showProgress(release)
            return
        }
        onNotAllowed()
        Sheet(activity).header(activity.getString(R.string.update_allow_title), activity.getString(R.string.update_allow_desc))
            .item(activity.getString(R.string.update_allow_open), bold = true) {
                runCatching {
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
                }
            }.show()
    }

    /**
     * 받는 중 창: 머리 `BookLauncher 1.5.1` · `받는 중`, 막대. 전자잉크라 막대는 25% 씩 네 번만 바꾸고, 금방 받아도
     * [Update.MIN_SHOW_MS](5초)에 걸쳐 채운다(받은 만큼보다 앞서 가지 않는다). 받기가 끝나면 닫히고 설치 창이 뜬다.
     */
    private fun showProgress(release: Update.Release) {
        val bar = ProgressLine(activity)
        val dialog = Sheet(activity).header("BookLauncher ${release.version}", activity.getString(R.string.update_downloading))
            .note(bar).locked().show()
        progressDialog = dialog
        val step = Update.MIN_SHOW_MS / 4
        var ticks = 0
        val tick = object : Runnable {
            override fun run() {
                if (!dialog.isShowing) return
                ticks++
                val shown = minOf(ticks * 25, Update.downloaded / 25 * 25)
                if (shown != bar.percent) bar.percent = shown
                bar.postDelayed(this, step)
            }
        }
        bar.postDelayed(tick, step)
    }

    /** 받기가 끝났으면(설치 창을 띄웠거나 실패) 받는 중 창을 닫는다 */
    private fun closeProgress() {
        if (Update.state is Update.State.Downloading) return
        dismiss()
    }

    /** `설치` 뒤 받기·확인·설치에 실패했으면 알린다(그 화면을 떠나 있었으면 돌아올 때) */
    private fun showInstallFailure() {
        if (!Update.takeInstallFailure()) return
        Sheet(activity).header(activity.getString(R.string.update_install_failed), activity.getString(R.string.update_install_failed_desc)).show()
    }

    companion object {
        /** 바뀐 점 창에 보이는 릴리스 설명 줄 수(크레마 페블에서 한 줄 한국어 약 28자). 릴리스 설명은 이 안에 맞춰 쓴다. */
        const val NOTES_LINES = 18
        private const val NOTES_SCAN_CHARS = 2_000

        /**
         * 릴리스 설명에서 앱 언어의 부분만: 한국어 아래 `## English` 제목 줄이 있으면 영어는 그 아래, 한국어는 그 위만(CLAUDE.md 릴리스 절).
         * 그 줄이 없으면(1.5.8 까지의 한국어만 쓴 설명) 언어와 상관없이 전체.
         */
        internal fun notesFor(markdown: String, english: Boolean): String {
            val lines = markdown.lines()
            val split = lines.indexOfFirst { ENGLISH_HEADING.matches(it.trim()) }
            if (split < 0) return markdown
            return (if (english) lines.drop(split + 1) else lines.take(split)).joinToString("\n").trim()
        }

        private val ENGLISH_HEADING = Regex("#{1,6}\\s*English", RegexOption.IGNORE_CASE)

        private fun releaseNotes(context: Context, markdown: String): CharSequence? =
            releaseNotes(notesFor(markdown, context.resources.configuration.locales[0].language == java.util.Locale.ENGLISH.language))

        /**
         * 릴리스 설명(마크다운)을 창에 넣을 글로: 절 제목(`## 새로 생긴 것`)은 굵은 검정, 항목(`- `)은 `· ` 로 시작하는 회색,
         * 두 번째 절부터는 위에 반 줄을 띄운다. `**`·`` ` `` 는 던다. 줄 수는 창이 [NOTES_LINES] 로 자른다(넘으면 끝에 줄임표).
         * 릴리스 설명은 이 줄 수에 맞춰 쓴다(CLAUDE.md 릴리스 절).
         */
        private fun releaseNotes(markdown: String): CharSequence? {
            val out = SpannableStringBuilder()
            for (raw in markdown.lines()) {
                val line = raw.replace("**", "").replace("`", "").trim()
                if (line.isEmpty()) continue
                val heading = line.startsWith("#")
                val text = when {
                    heading -> line.trimStart('#', ' ')
                    line.startsWith("- ") || line.startsWith("* ") -> "· " + line.drop(2)
                    else -> line
                }
                if (out.isNotEmpty()) {
                    out.append("\n")
                    if (heading) out.append(" \n", RelativeSizeSpan(0.5f), 0)
                }
                if (heading) out.append(text, StyleSpan(Typeface.BOLD), 0).also {
                    it.setSpan(ForegroundColorSpan(Ui.BLACK), it.length - text.length, it.length, 0)
                } else out.append(text)
                // 어차피 [NOTES_LINES] 줄만 보이므로 아주 긴 설명은 앞부분만 다룬다.
                if (out.length > NOTES_SCAN_CHARS) break
            }
            return out.takeIf { it.isNotEmpty() }
        }
    }
}

/** 받는 중 막대: 연회색 줄 위에 받은 만큼 검정(박스 없이, 높이 [HEIGHT_DP]) */
private class ProgressLine(context: Context) : View(context) {
    var percent = 0
        set(v) { field = v; invalidate() }
    private val paint = Paint()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), context.dp(HEIGHT_DP))

    override fun onDraw(canvas: Canvas) {
        paint.color = Ui.DIVIDER
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.color = Ui.BLACK
        canvas.drawRect(0f, 0f, width * percent / 100f, height.toFloat(), paint)
    }

    companion object {
        const val HEIGHT_DP = 8
    }
}
