package com.woody.booklauncher

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Small built-in tools to match Licorice's "mini apps": a calculator, a notes
 * pad and a countdown timer. Everything is local and e-ink friendly: no
 * animation, no scrolling where it can be avoided.
 */
class MiniAppsActivity : EinkActivity() {
    private enum class Page { HUB, CALC, NOTES, TIMER }

    private lateinit var body: LinearLayout
    private lateinit var title: TextView
    private var page = Page.HUB

    // Calculator
    private var display = "0"
    private var acc: Double? = null
    private var pending: Char? = null
    private var fresh = true

    // Notes
    private var notes = emptyList<Note>()
    private var editing: Note? = null
    private var draft: EditText? = null

    // Timer
    private var timerLeftMs = 25 * 60_000L
    private var timerRunning = false
    private var timerTotalMs = 25 * 60_000L
    private var timerDone = false
    private var timerText: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            timerLeftMs -= now - lastTick
            lastTick = now
            if (timerLeftMs <= 0) {
                timerLeftMs = 0
                timerRunning = false
                timerDone = true
                runCatching {
                    ToneGenerator(AudioManager.STREAM_ALARM, 80).startTone(ToneGenerator.TONE_PROP_BEEP, 800)
                }
                render()
                return
            }
            timerText?.text = timeText(timerLeftMs)
            if (timerRunning) handler.postDelayed(this, 1000)
        }
    }
    private var lastTick = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = vbox()
        root.addView(titleRow(getString(R.string.mini_apps)) { if (!back()) finish() }.apply {
            title = text("", 19f, color = Ui.GRAY)
            addView(title, lp(WRAP, WRAP).apply { marginStart = dp(14); topMargin = dp(6) })
        })
        body = vbox()
        root.addView(softCard(0).apply { addView(body, lp(MATCH, MATCH)) }, cardLp(weight = 1f, topDp = 8))
        root.addView(hline(1, inset = false))
        setContentView(root)
        notes = MiniStore.notes(this)
        render()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!back()) super.onBackPressed()
    }

    /** Returns true when the screen handled the back press. */
    private fun back(): Boolean {
        when {
            editing != null -> {
                editing = null
                render()
            }
            page != Page.HUB -> {
                page = Page.HUB
                render()
            }
            else -> return false
        }
        return true
    }

    private fun render() {
        body.removeAllViews()
        body.setPadding(0, 0, 0, 0)
        title.text = when (page) {
            Page.HUB -> ""
            Page.CALC -> getString(R.string.mini_calc)
            Page.NOTES -> if (editing == null) getString(R.string.mini_notes) else getString(R.string.note_hint)
            Page.TIMER -> getString(R.string.mini_timer)
        }
        when (page) {
            Page.HUB -> hub()
            Page.CALC -> calculator()
            Page.NOTES -> if (editing == null) notesList() else noteEditor()
            Page.TIMER -> timer()
        }
    }

    // ---- hub ----------------------------------------------------------------

    private fun hub() {
        val items: List<Triple<Int, Int, () -> Unit>> = listOf(
            Triple(R.string.mini_calc, R.string.mini_calc_desc) { page = Page.CALC; render() },
            Triple(R.string.mini_notes, R.string.mini_notes_desc) {
                notes = MiniStore.notes(this)
                page = Page.NOTES
                render()
            },
            Triple(R.string.mini_timer, R.string.mini_timer_desc) { page = Page.TIMER; render() },
            Triple(R.string.games, R.string.games_desc) {
                startActivity(GamesActivity.intent(this@MiniAppsActivity))
            },
        )
        items.forEach { (label, desc, action) ->
            body.addView(hline(1, Ui.DIVIDER), lp(MATCH, dp(1)))
            body.addView(hbox().apply {
                setPadding(dp(Ui.MARGIN), 0, dp(Ui.MARGIN), 0)
                addView(vbox().apply {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(text(getString(label), 23f, Ui.medium))
                    addView(text(getString(desc), 13f, color = Ui.GRAY), lp(MATCH, WRAP).apply { topMargin = dp(2) })
                }, lp(0, WRAP, 1f))
                addView(text("›", 24f, color = Ui.LIGHT_GRAY).apply { gravity = Gravity.CENTER_VERTICAL })
                setOnClickListener { action() }
            }, lp(MATCH, rowDp(72)))
        }
    }

    // ---- calculator ---------------------------------------------------------

    private fun calculator() {
        body.setPadding(dp(Ui.MARGIN), dp(12), dp(Ui.MARGIN), dp(8))
        body.addView(
            text(display, 44f, Ui.heavy).apply {
                gravity = Gravity.END
                setPadding(0, dp(4), dp(4), dp(8))
            },
            lp(MATCH, 0, 1f),
        )
        val keys = listOf(
            listOf("C", "±", "%", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "−"),
            listOf("1", "2", "3", "+"),
            listOf("0", ".", "⌫", "="),
        )
        keys.forEachIndexed { r, row ->
            body.addView(hbox().apply {
                row.forEachIndexed { c, key ->
                    val button = CalcKey(this@MiniAppsActivity, key, key == "=") { press(key) }
                    addView(button, lp(0, MATCH, 1f).apply {
                        if (c > 0) marginStart = dp(6)
                    })
                }
            }, lp(MATCH, 0, 1f).apply { if (r > 0) topMargin = dp(6) })
        }
    }

    private fun press(key: String) {
        when (key) {
            "C" -> {
                display = "0"
                acc = null
                pending = null
                fresh = true
            }
            "⌫" -> display = display.dropLast(1).ifEmpty { "0" }.let { if (it == "-") "0" else it }
            "±" -> display = if (display.startsWith("-")) display.drop(1) else "-$display"
            "%" -> display = (display.toDoubleOrNull()?.div(100.0) ?: 0.0).fmt()
            "=" -> {
                pending?.let { op -> acc = calc(acc ?: 0.0, display.toDoubleOrNull() ?: 0.0, op) }
                display = (acc ?: display.toDoubleOrNull() ?: 0.0).fmt()
                pending = null
                acc = null
                fresh = true
                render()
                return
            }
            "÷", "×", "−", "+" -> {
                val cur = display.toDoubleOrNull() ?: 0.0
                acc = pending?.let { calc(acc ?: cur, cur, it) } ?: cur
                display = (acc ?: cur).fmt()
                pending = key[0]
                fresh = true
            }
            "." -> if (!display.contains('.')) {
                display = if (fresh) "0." else "$display."
                fresh = false
            }
            else -> {
                display = if (fresh || display == "0") key else display + key
                fresh = false
            }
        }
        render()
    }

    private fun calc(a: Double, b: Double, op: Char): Double? = when (op) {
        '÷' -> if (b == 0.0) null else a / b
        '×' -> a * b
        '−' -> a - b
        '+' -> a + b
        else -> b
    }

    private fun Double.fmt(): String {
        val whole = this
        val r = whole.roundToInt()
        return if (kotlin.math.abs(whole - r) < 0.0000001) r.toString() else String.format(Locale.US, "%.6f", whole).trimEnd('0').trimEnd('.')
    }

    // ---- notes --------------------------------------------------------------

    private fun notesList() {
        if (notes.isEmpty()) {
            body.addView(
                text(getString(R.string.note_empty), 17f, color = Ui.GRAY).apply {
                    setPadding(dp(Ui.MARGIN), dp(24), dp(Ui.MARGIN), 0)
                },
                lp(MATCH, 0, 1f),
            )
        } else {
            val rows = PagedRows(this, rowDp(72))
            rows.setRows(notes.map { note -> { noteRow(note) } })
            body.addView(rows, lp(MATCH, 0, 1f))
            val label = text("", 15f, Ui.medium, Ui.GRAY).apply {
                gravity = Gravity.CENTER
                pagesClick(rows)
            }
            rows.onPageChanged = { label.text = if (rows.pageCount > 1) pageLabel(rows) else "" }
            body.addView(label, lp(MATCH, dp(28)))
            rows.onPageChanged?.invoke()
        }
        body.addView(hline(1, inset = false))
        body.addView(text(getString(R.string.note_new), 21f, Ui.bold).apply {
            gravity = Gravity.CENTER
            setPadding(dp(Ui.MARGIN), 0, dp(Ui.MARGIN), 0)
            setOnClickListener {
                editing = Note(0, "", "", System.currentTimeMillis())
                render()
            }
        }, lp(MATCH, dp(Ui.FOOTER_DP)))
    }

    private fun noteRow(note: Note): View = hbox().apply {
        setPadding(dp(Ui.MARGIN), 0, dp(Ui.MARGIN), 0)
        addView(vbox().apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(text(note.title.ifBlank { getString(R.string.note_hint) }, 19f, Ui.medium))
            addView(
                text(note.body.replace('\n', ' ').take(120), 12f, color = Ui.GRAY, lines = 1),
                lp(MATCH, WRAP).apply { topMargin = dp(2) },
            )
        }, lp(0, WRAP, 1f))
        addView(text(dateText(note.updated), 11f, color = Ui.LIGHT_GRAY).apply { gravity = Gravity.CENTER_VERTICAL })
        setOnClickListener {
            editing = note
            render()
        }
        setOnLongClickListener {
            Sheet(this@MiniAppsActivity).header(note.title.ifBlank { getString(R.string.note_hint) })
                .item(getString(R.string.note_delete), bold = true) {
                    MiniStore.delete(this@MiniAppsActivity, note.id)
                    notes = MiniStore.notes(this@MiniAppsActivity)
                    render()
                }.show()
            true
        }
    }

    private fun noteEditor() {
        val e = EditText(this).apply {
            setText(editing?.body.orEmpty())
            hint = getString(R.string.note_hint)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 19f * Ui.textScale)
            setTextColor(Ui.BLACK)
            setHintTextColor(Ui.LIGHT_GRAY)
            typeface = Ui.regular
            background = null
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(false)
            setPadding(dp(Ui.MARGIN), dp(12), dp(Ui.MARGIN), dp(12))
        }
        draft = e
        body.addView(e, lp(MATCH, 0, 1f))
        e.requestFocus()
        e.post {
            runCatching {
                (getSystemService(android.view.inputmethod.InputMethodManager::class.java))
                    .showSoftInput(e, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }
        body.addView(hline(1, inset = false))
        val footer = hbox().apply {
            addView(text(getString(R.string.cancel), 20f, color = Ui.GRAY).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(Ui.MARGIN), 0, dp(24), 0)
                setOnClickListener {
                    editing = null
                    render()
                }
            }, lp(0, MATCH, 1f))
            addView(text(getString(R.string.save), 20f, Ui.bold).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(24), 0, dp(Ui.MARGIN), 0)
                setOnClickListener {
                    saveNote()
                    editing = null
                    notes = MiniStore.notes(this@MiniAppsActivity)
                    render()
                }
            }, lp(WRAP, MATCH))
        }
        body.addView(footer, lp(MATCH, dp(Ui.FOOTER_DP)))
    }

    private fun saveNote() {
        val text = draft?.text?.toString().orEmpty().trim()
        if (text.isEmpty()) return
        val title = text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(60)
        val old = editing ?: return
        MiniStore.save(this, Note(old.id, title, text, System.currentTimeMillis()))
    }

    // ---- timer --------------------------------------------------------------

    private fun timer() {
        body.setPadding(dp(Ui.MARGIN), dp(16), dp(Ui.MARGIN), dp(12))
        timerText = text(timeText(timerLeftMs), 64f, Ui.heavy).apply { gravity = Gravity.CENTER }
        body.addView(timerText, lp(MATCH, 0, 1f))
        if (timerDone) {
            body.addView(text(getString(R.string.timer_done), 17f, Ui.bold, Palette.current.goal).apply { gravity = Gravity.CENTER })
        }
        body.addView(hbox().apply {
            listOf(5, 10, 15, 25, 30, 60).forEachIndexed { i, minutes ->
                addView(CalcKey(this@MiniAppsActivity, "$minutes", minutes * 60_000L == timerTotalMs) {
                    timerTotalMs = minutes * 60_000L
                    timerLeftMs = timerTotalMs
                    timerRunning = false
                    timerDone = false
                    handler.removeCallbacks(tick)
                    render()
                }, lp(0, dp(48), 1f).apply { if (i > 0) marginStart = dp(6) })
            }
        }, lp(MATCH, WRAP).apply { topMargin = dp(6) })
        body.addView(hbox().apply {
            addView(CalcKey(this@MiniAppsActivity, getString(if (timerRunning) R.string.timer_pause else R.string.timer_start), true) {
                toggleTimer()
            }, lp(0, dp(64), 2f))
            addView(CalcKey(this@MiniAppsActivity, getString(R.string.timer_reset), false) {
                timerRunning = false
                timerDone = false
                timerLeftMs = timerTotalMs
                handler.removeCallbacks(tick)
                render()
            }, lp(0, dp(64), 1f).apply { marginStart = dp(10) })
        }, lp(MATCH, WRAP).apply { topMargin = dp(8) })
    }

    private fun toggleTimer() {
        if (timerDone) {
            timerDone = false
            timerLeftMs = timerTotalMs
        }
        timerRunning = !timerRunning
        if (timerRunning) {
            lastTick = System.currentTimeMillis()
            handler.postDelayed(tick, 1000)
        } else handler.removeCallbacks(tick)
        render()
    }

    private fun timeText(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        return "%02d:%02d".format(total / 60, total % 60)
    }

    private fun dateText(ms: Long): String =
        Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
            .format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))

    companion object {
        fun intent(context: Context) = Intent(context, MiniAppsActivity::class.java)
    }
}

/** White key with a thin border; the equals key is inverted for emphasis. */
private class CalcKey(context: Context, private val label: String, private val primary: Boolean, onClick: () -> Unit) : View(context) {
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpf(1.2f)
        color = Ui.BLACK
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = context.spf(24f)
        textAlign = Paint.Align.CENTER
        typeface = if (primary) Ui.bold else Ui.medium
        color = if (primary) android.graphics.Color.WHITE else Ui.BLACK
    }
    private val rect = RectF()

    init {
        contentDescription = label
        isClickable = true
        setOnClickListener { onClick() }
    }

    override fun onDraw(canvas: Canvas) {
        val i = border.strokeWidth / 2
        rect.set(i, i, width - i, height - i)
        if (primary) canvas.drawRoundRect(rect, context.dpf(8f), context.dpf(8f), Paint().apply { color = Ui.BLACK })
        else canvas.drawRoundRect(rect, context.dpf(8f), context.dpf(8f), border)
        val y = height / 2f - (text.descent() + text.ascent()) / 2f
        canvas.drawText(label, width / 2f, y, text)
    }
}

data class Note(val id: Long, val title: String, val body: String, val updated: Long)

/** Notes are small; a JSON array in the app's private prefs is enough. */
object MiniStore {
    private const val PREFS = "mini"
    private const val KEY = "notes"

    fun notes(context: Context): List<Note> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).mapNotNull { i ->
                runCatching {
                    val o = a.getJSONObject(i)
                    Note(o.getLong("id"), o.optString("title"), o.optString("body"), o.optLong("updated"))
                }.getOrNull()
            }.sortedByDescending { it.updated }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, note: Note) {
        val list = notes(context).toMutableList()
        val id = note.id.takeIf { it != 0L } ?: System.currentTimeMillis()
        val row = Note(id, note.title, note.body, System.currentTimeMillis())
        val at = list.indexOfFirst { it.id == id }
        if (at >= 0) list[at] = row else list += row
        write(context, list)
    }

    fun delete(context: Context, id: Long) {
        write(context, notes(context).filterNot { it.id == id })
    }

    private fun write(context: Context, list: List<Note>) {
        val a = JSONArray()
        list.forEach { n ->
            a.put(JSONObject().apply {
                put("id", n.id); put("title", n.title); put("body", n.body); put("updated", n.updated)
            })
        }
        prefs(context).edit().putString(KEY, a.toString()).apply()
    }

    private fun prefs(context: Context) =
        Storage.of(context).getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
