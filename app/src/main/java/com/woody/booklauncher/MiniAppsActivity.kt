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
import com.woody.booklauncher.games.GameKey
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
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
    private enum class Page { HUB, CALC, NOTES, TIMER, PET, QUEST, HABITS, TODO, COUNTER, QUOTES, CLOCK }

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

    // Habits · to do · counters · quotes
    private var habits = emptyList<Habit>()
    private var todos = emptyList<Todo>()
    private var counters = emptyList<CounterItem>()
    private var quotes = emptyList<QuoteItem>()
    private var quoteIndex = 0
    private var clockText: TextView? = null
    private val clockTick = object : Runnable {
        override fun run() {
            clockText?.text = clockTime()
            handler.postDelayed(this, 30_000)
        }
    }

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
        habits = MiniExtrasStore.habits(this)
        todos = MiniExtrasStore.todos(this)
        counters = MiniExtrasStore.counters(this)
        quotes = MiniExtrasStore.quotes(this)
        render()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        handler.removeCallbacks(clockTick)
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
        if (page != Page.CLOCK) handler.removeCallbacks(clockTick)
        body.removeAllViews()
        body.setPadding(0, 0, 0, 0)
        title.text = when (page) {
            Page.HUB -> ""
            Page.CALC -> getString(R.string.mini_calc)
            Page.NOTES -> if (editing == null) getString(R.string.mini_notes) else getString(R.string.note_hint)
            Page.TIMER -> getString(R.string.mini_timer)
            Page.PET -> getString(R.string.mini_pet)
            Page.QUEST -> getString(R.string.mini_quest)
            Page.HABITS -> getString(R.string.mini_habits)
            Page.TODO -> getString(R.string.mini_todo)
            Page.COUNTER -> getString(R.string.mini_counter)
            Page.QUOTES -> getString(R.string.mini_quotes)
            Page.CLOCK -> getString(R.string.mini_clock)
        }
        when (page) {
            Page.HUB -> hub()
            Page.CALC -> calculator()
            Page.NOTES -> if (editing == null) notesList() else noteEditor()
            Page.TIMER -> timer()
            Page.PET -> petPage()
            Page.QUEST -> questPage()
            Page.HABITS -> habitsPage()
            Page.TODO -> todoPage()
            Page.COUNTER -> counterPage()
            Page.QUOTES -> quotesPage()
            Page.CLOCK -> clockPage()
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
            Triple(R.string.mini_pet, R.string.mini_pet_desc) { page = Page.PET; render() },
            Triple(R.string.mini_quest, R.string.mini_quest_desc) { page = Page.QUEST; render() },
            Triple(R.string.mini_habits, R.string.mini_habits_desc) { page = Page.HABITS; render() },
            Triple(R.string.mini_todo, R.string.mini_todo_desc) { page = Page.TODO; render() },
            Triple(R.string.mini_counter, R.string.mini_counter_desc) { page = Page.COUNTER; render() },
            Triple(R.string.mini_quotes, R.string.mini_quotes_desc) { page = Page.QUOTES; render() },
            Triple(R.string.mini_clock, R.string.mini_clock_desc) { page = Page.CLOCK; render() },
            Triple(R.string.games, R.string.games_desc) {
                startActivity(GamesActivity.intent(this@MiniAppsActivity))
            },
        )
        val rows = PagedRows(this, rowDp(72))
        rows.setRows(items.map { (label, desc, action) -> { hubRow(label, desc, action) } })
        body.addView(rows, lp(MATCH, 0, 1f))
        val pageText = text("", 15f, Ui.medium, Ui.GRAY).apply {
            gravity = Gravity.CENTER
            pagesClick(rows)
        }
        rows.onPageChanged = { pageText.text = if (rows.pageCount > 1) pageLabel(rows) else "" }
        body.addView(pageText, lp(MATCH, dp(28)))
        rows.onPageChanged?.invoke()
    }

    private fun hubRow(label: Int, desc: Int, action: () -> Unit): View = hbox().apply {
        setPadding(dp(Ui.MARGIN), 0, dp(Ui.MARGIN), 0)
        addView(vbox().apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(text(getString(label), 23f, Ui.medium))
            addView(text(getString(desc), 13f, color = Ui.GRAY), lp(MATCH, WRAP).apply { topMargin = dp(2) })
        }, lp(0, WRAP, 1f))
        addView(text("›", 24f, color = Ui.LIGHT_GRAY).apply { gravity = Gravity.CENTER_VERTICAL })
        setOnClickListener { action() }
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

    // ---- companion ----------------------------------------------------------

    private fun petPage() {
        val total = readingMinutes(this)
        val today = LocalDate.now()
        var p = MiniExtrasStore.pet(this) ?: PetState(
            name = "Momo",
            startMinutes = total,
            lastMinutes = total,
            mood = 70,
            energy = 70,
            lastDay = today.toString(),
            fedDay = "",
            playedDay = "",
            restedDay = "",
        ).also { MiniExtrasStore.savePet(this, it) }
        val touched = p.touch(total, today)
        if (touched != p) {
            p = touched
            MiniExtrasStore.savePet(this, p)
        }
        val en = HomePrefs.isEnglish(this)
        val petStage = p.stage(total)
        body.setPadding(dp(Ui.MARGIN), dp(4), dp(Ui.MARGIN), dp(8))
        body.addView(hbox().apply {
            addView(text(p.name, 26f, Ui.heavy), lp(0, WRAP, 1f))
            addView(text(getString(R.string.pet_rename), 13f, color = Ui.GRAY).apply {
                setPadding(dp(8), 0, 0, 0)
                setOnClickListener {
                    Sheet(this@MiniAppsActivity).input(
                        p.name,
                        check = { if (it.isBlank()) getString(R.string.pet_name_empty) else null },
                    ) { name ->
                        p = p.copy(name = name.trim())
                        MiniExtrasStore.savePet(this@MiniAppsActivity, p)
                        render()
                    }.show()
                }
            }, lp(WRAP, WRAP))
        }, lp(MATCH, WRAP))
        body.addView(text(
            "${if (en) petStage.en else petStage.ko}  ·  ${getString(R.string.pet_read_together, duration(total * 60_000))}",
            13f, color = Ui.GRAY,
        ), lp(MATCH, WRAP).apply { topMargin = dp(1) })
        body.addView(PetView(this).apply {
            this.stage = petStage
            mood = p.mood
            energy = p.energy
        }, lp(MATCH, 0, 1f).apply { topMargin = dp(4) })
        body.addView(text("${getString(R.string.pet_mood)}  ${bars(p.mood)}", 16f, Ui.medium), lp(MATCH, WRAP))
        body.addView(text("${getString(R.string.pet_energy)}  ${bars(p.energy)}", 16f, Ui.medium), lp(MATCH, WRAP).apply { topMargin = dp(2) })
        val next = p.nextStage(total)
        if (next != null) {
            val need = p.minutesToNext(total) ?: 0
            body.addView(text(
                getString(R.string.pet_next, if (en) next.en else next.ko, need),
                12f, color = Ui.GRAY,
            ), lp(MATCH, WRAP).apply { topMargin = dp(5) })
            val current = petStage.atMinutes
            val span = (next.atMinutes - current).coerceAtLeast(1)
            body.addView(ProgressLine(this, 8).apply {
                percent = (((p.xp(total) - current).toFloat() / span) * 100f).toInt().coerceIn(0, 100)
            }, lp(MATCH, dp(8)).apply { topMargin = dp(3) })
        } else {
            body.addView(text(getString(R.string.pet_fully), 12f, color = Ui.GRAY), lp(MATCH, WRAP).apply { topMargin = dp(5) })
        }
        val day = today.toString()
        body.addView(hbox().apply {
            addView(CalcKey(this@MiniAppsActivity, getString(R.string.pet_feed) + if (p.fedDay == day) " ✓" else "", p.fedDay != day) {
                p = p.feed(day)
                MiniExtrasStore.savePet(this@MiniAppsActivity, p)
                render()
            }, lp(0, dp(48), 1f))
            addView(CalcKey(this@MiniAppsActivity, getString(R.string.pet_play) + if (p.playedDay == day) " ✓" else "", false) {
                p = p.play(day)
                MiniExtrasStore.savePet(this@MiniAppsActivity, p)
                render()
            }, lp(0, dp(48), 1f).apply { marginStart = dp(6) })
            addView(CalcKey(this@MiniAppsActivity, getString(R.string.pet_rest) + if (p.restedDay == day) " ✓" else "", false) {
                p = p.rest(day)
                MiniExtrasStore.savePet(this@MiniAppsActivity, p)
                render()
            }, lp(0, dp(48), 1f).apply { marginStart = dp(6) })
        }, lp(MATCH, WRAP).apply { topMargin = dp(8) })
    }

    private fun bars(value: Int): String {
        val n = (value / 20).coerceIn(0, 5)
        return "■".repeat(n) + "□".repeat(5 - n)
    }

    // ---- reading quest ------------------------------------------------------

    private fun questPage() {
        val total = readingMinutes(this)
        var q = MiniExtrasStore.quest(this) ?: QuestState(total, 0, emptyList()).also {
            MiniExtrasStore.saveQuest(this, it)
        }
        val en = HomePrefs.isEnglish(this)
        val xp = q.xp(total)
        body.setPadding(dp(Ui.MARGIN), dp(4), dp(Ui.MARGIN), dp(8))
        val chapters = ReadingQuest.chapters
        if (q.chapter >= chapters.size) {
            val title = ReadingQuest.ending(q.choices)
            val text = ReadingQuest.endingText(q.choices)
            body.addView(text(getString(R.string.quest_ending), 12f, color = Ui.GRAY).apply { gravity = Gravity.CENTER }, lp(MATCH, WRAP))
            body.addView(text(if (en) title.second else title.first, 26f, Ui.heavy).apply { gravity = Gravity.CENTER }, lp(MATCH, WRAP).apply { topMargin = dp(2) })
            body.addView(text(if (en) text.second else text.first, 17f, lines = 14).apply { setLineSpacing(dp(3).toFloat(), 1f) }, lp(MATCH, 0, 1f).apply { topMargin = dp(10) })
            body.addView(text(traits(q.choices), 13f, color = Ui.GRAY).apply { gravity = Gravity.CENTER }, lp(MATCH, WRAP))
            body.addView(CalcKey(this, getString(R.string.quest_restart), true) {
                confirmQuestRestart(total)
            }, lp(MATCH, dp(46)).apply { topMargin = dp(8) })
            return
        }
        val chapter = chapters[q.chapter]
        val previous = if (q.chapter == 0) 0 else chapters[q.chapter - 1].requires
        val need = (chapter.requires - xp).coerceAtLeast(0)
        body.addView(text(
            getString(R.string.quest_chapter, q.chapter + 1, chapters.size),
            12f, color = Ui.GRAY,
        ).apply { gravity = Gravity.CENTER }, lp(MATCH, WRAP))
        body.addView(text(if (en) chapter.titleEn else chapter.titleKo, 24f, Ui.heavy), lp(MATCH, WRAP).apply { topMargin = dp(3) })
        if (need > 0) {
            body.addView(text(getString(R.string.quest_more, need), 16f, color = Ui.GRAY, lines = 2), lp(MATCH, WRAP).apply { topMargin = dp(8) })
            val span = (chapter.requires - previous).coerceAtLeast(1)
            body.addView(ProgressLine(this, 10).apply {
                percent = (((xp - previous).toFloat() / span) * 100f).toInt().coerceIn(0, 100)
            }, lp(MATCH, dp(10)).apply { topMargin = dp(5) })
            body.addView(text(getString(R.string.quest_read_so_far, duration(xp * 60_000)), 13f, color = Ui.GRAY), lp(MATCH, WRAP).apply { topMargin = dp(6) })
            body.addView(text(getString(R.string.quest_hint), 13f, color = Ui.GRAY, lines = 2), lp(MATCH, WRAP).apply { topMargin = dp(4) })
            body.addView(View(this), lp(MATCH, 0, 1f))
            body.addView(text(getString(R.string.quest_restart), 12f, color = Ui.GRAY).apply {
                gravity = Gravity.CENTER
                setOnClickListener { confirmQuestRestart(total) }
            }, lp(MATCH, dp(28)))
            return
        }
        body.addView(text(if (en) chapter.textEn else chapter.textKo, 17f, lines = 12).apply {
            setLineSpacing(dp(3).toFloat(), 1f)
        }, lp(MATCH, 0, 1f).apply { topMargin = dp(10) })
        body.addView(text(getString(R.string.quest_choose), 12f, color = Ui.GRAY), lp(MATCH, WRAP))
        chapter.choices.forEachIndexed { i, choice ->
            body.addView(GameKey(this, if (en) choice.en else choice.ko, i == 0) {
                q = q.copy(chapter = q.chapter + 1, choices = q.choices + i)
                MiniExtrasStore.saveQuest(this, q)
                render()
            }, lp(MATCH, dp(46)).apply { topMargin = dp(4) })
        }
        body.addView(text(getString(R.string.quest_read_so_far, duration(xp * 60_000)), 12f, color = Ui.GRAY).apply {
            gravity = Gravity.CENTER
        }, lp(MATCH, WRAP).apply { topMargin = dp(5) })
    }

    private fun traits(choices: List<Int>): String {
        val counts = IntArray(QuestTrait.entries.size)
        choices.forEachIndexed { chapter, choice ->
            ReadingQuest.chapters.getOrNull(chapter)?.choices?.getOrNull(choice)?.let {
                counts[it.trait.ordinal]++
            }
        }
        return getString(R.string.quest_traits, counts[0], counts[1], counts[2])
    }

    private fun confirmQuestRestart(total: Long) {
        Sheet(this).header(getString(R.string.quest_restart)).item(getString(R.string.quest_restart), bold = true) {
            MiniExtrasStore.saveQuest(this, QuestState(total, 0, emptyList()))
            render()
        }.show()
    }

    // ---- habits -------------------------------------------------------------

    private fun habitsPage() {
        body.setPadding(0, dp(2), 0, 0)
        val today = LocalDate.now()
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val days = (0..6).map { monday.plusDays(it.toLong()) }
        val labels = getString(R.string.habit_days).split(",")
        body.addView(hbox().apply {
            setPadding(dp(Ui.MARGIN), 0, dp(14), 0)
            addView(View(this@MiniAppsActivity), lp(0, dp(22), 1f))
            days.forEachIndexed { i, d ->
                addView(text(labels.getOrElse(i) { "" }, 12f,
                    if (d == today) Ui.bold else Ui.regular,
                    if (d == today) Ui.BLACK else Ui.GRAY).apply { gravity = Gravity.CENTER },
                    lp(dp(30), dp(22)))
            }
        }, lp(MATCH, WRAP))
        if (habits.isEmpty()) {
            body.addView(text(getString(R.string.habit_empty), 16f, color = Ui.GRAY).apply {
                setPadding(dp(Ui.MARGIN), dp(22), dp(Ui.MARGIN), 0)
            }, lp(MATCH, 0, 1f))
        } else {
            val rows = PagedRows(this, rowDp(54))
            rows.setRows(habits.map { h -> { habitRow(h, days, today) } })
            body.addView(rows, lp(MATCH, 0, 1f))
            val label = text("", 14f, Ui.medium, Ui.GRAY).apply { gravity = Gravity.CENTER; pagesClick(rows) }
            rows.onPageChanged = { label.text = if (rows.pageCount > 1) pageLabel(rows) else "" }
            body.addView(label, lp(MATCH, dp(24)))
            rows.onPageChanged?.invoke()
        }
        body.addView(hline(1, inset = false))
        body.addView(inputRow(R.string.habit_add_hint) { name ->
            habits = habits + Habit(System.currentTimeMillis(), name, emptySet())
            MiniExtrasStore.saveHabits(this, habits)
            render()
        }, lp(MATCH, dp(Ui.FOOTER_DP)))
    }

    private fun habitRow(h: Habit, days: List<LocalDate>, today: LocalDate): View = hbox().apply {
        setPadding(dp(Ui.MARGIN), 0, dp(14), 0)
        addView(text(h.name, 17f, Ui.medium, lines = 1).apply { gravity = Gravity.CENTER_VERTICAL }, lp(0, MATCH, 1f))
        days.forEach { d ->
            val key = d.toString()
            val done = key in h.days
            addView(text(if (done) "●" else "○", 19f,
                if (d == today) Ui.bold else Ui.regular,
                if (done) Ui.BLACK else Ui.LIGHT_GRAY).apply {
                gravity = Gravity.CENTER
                setOnClickListener {
                    val set = h.days.toMutableSet()
                    if (!set.add(key)) set.remove(key)
                    habits = habits.map { if (it.id == h.id) it.copy(days = set) else it }
                    MiniExtrasStore.saveHabits(this@MiniAppsActivity, habits)
                    render()
                }
            }, lp(dp(30), MATCH))
        }
        setOnLongClickListener {
            Sheet(this@MiniAppsActivity).header(h.name)
                .item(getString(R.string.remove), bold = true) {
                    habits = habits.filterNot { it.id == h.id }
                    MiniExtrasStore.saveHabits(this@MiniAppsActivity, habits)
                    render()
                }.show()
            true
        }
    }

    // ---- to do --------------------------------------------------------------

    private fun todoPage() {
        body.setPadding(0, dp(2), 0, 0)
        if (todos.isEmpty()) {
            body.addView(text(getString(R.string.todo_empty), 16f, color = Ui.GRAY).apply {
                setPadding(dp(Ui.MARGIN), dp(22), dp(Ui.MARGIN), 0)
            }, lp(MATCH, 0, 1f))
        } else {
            val rows = PagedRows(this, rowDp(52))
            rows.setRows(todos.map { t -> { todoRow(t) } })
            body.addView(rows, lp(MATCH, 0, 1f))
            val label = text("", 14f, Ui.medium, Ui.GRAY).apply { gravity = Gravity.CENTER; pagesClick(rows) }
            rows.onPageChanged = { label.text = if (rows.pageCount > 1) pageLabel(rows) else "" }
            body.addView(label, lp(MATCH, dp(24)))
            rows.onPageChanged?.invoke()
        }
        body.addView(hline(1, inset = false))
        body.addView(inputRow(R.string.todo_add_hint) { text ->
            todos = todos + Todo(System.currentTimeMillis(), text, false, System.currentTimeMillis())
            MiniExtrasStore.saveTodos(this, todos)
            render()
        }, lp(MATCH, dp(Ui.FOOTER_DP)))
    }

    private fun todoRow(t: Todo): View = hbox().apply {
        setPadding(dp(Ui.MARGIN), 0, dp(Ui.MARGIN), 0)
        addView(text(if (t.done) "■" else "□", 20f, color = if (t.done) Ui.BLACK else Ui.LIGHT_GRAY).apply {
            gravity = Gravity.CENTER_VERTICAL
        }, lp(dp(30), MATCH))
        addView(text(t.text, 17f, if (t.done) Ui.regular else Ui.medium, if (t.done) Ui.GRAY else Ui.BLACK, lines = 2).apply {
            gravity = Gravity.CENTER_VERTICAL
            if (t.done) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        }, lp(0, MATCH, 1f))
        setOnClickListener {
            todos = todos.map { if (it.id == t.id) it.copy(done = !it.done) else it }
            todos = todos.sortedWith(compareBy({ it.done }, { -it.created }))
            MiniExtrasStore.saveTodos(this@MiniAppsActivity, todos)
            render()
        }
        setOnLongClickListener {
            Sheet(this@MiniAppsActivity).header(t.text)
                .item(getString(R.string.remove), bold = true) {
                    todos = todos.filterNot { it.id == t.id }
                    MiniExtrasStore.saveTodos(this@MiniAppsActivity, todos)
                    render()
                }.show()
            true
        }
    }

    // ---- counter ------------------------------------------------------------

    private fun counterPage() {
        body.setPadding(0, dp(2), 0, 0)
        if (counters.isEmpty()) {
            body.addView(text(getString(R.string.counter_empty), 16f, color = Ui.GRAY).apply {
                setPadding(dp(Ui.MARGIN), dp(22), dp(Ui.MARGIN), 0)
            }, lp(MATCH, 0, 1f))
        } else {
            val rows = PagedRows(this, rowDp(72))
            rows.setRows(counters.map { c -> { counterRow(c) } })
            body.addView(rows, lp(MATCH, 0, 1f))
            val label = text("", 14f, Ui.medium, Ui.GRAY).apply { gravity = Gravity.CENTER; pagesClick(rows) }
            rows.onPageChanged = { label.text = if (rows.pageCount > 1) pageLabel(rows) else "" }
            body.addView(label, lp(MATCH, dp(24)))
            rows.onPageChanged?.invoke()
        }
        body.addView(hline(1, inset = false))
        body.addView(inputRow(R.string.counter_add_hint) { name ->
            counters = counters + CounterItem(System.currentTimeMillis(), name, 0, 1)
            MiniExtrasStore.saveCounters(this, counters)
            render()
        }, lp(MATCH, dp(Ui.FOOTER_DP)))
    }

    private fun counterRow(c: CounterItem): View = hbox().apply {
        setPadding(dp(Ui.MARGIN), 0, dp(Ui.MARGIN), 0)
        addView(text(c.name, 16f, Ui.medium, lines = 1).apply { gravity = Gravity.CENTER_VERTICAL }, lp(0, MATCH, 1f))
        addView(text(c.value.toString(), 26f, Ui.heavy).apply { gravity = Gravity.CENTER }, lp(dp(56), MATCH))
        addView(CalcKey(this@MiniAppsActivity, "−", false) { bumpCounter(c, -c.step) }, lp(dp(44), dp(48)).apply { gravity = Gravity.CENTER_VERTICAL })
        addView(CalcKey(this@MiniAppsActivity, "+", true) { bumpCounter(c, c.step) }, lp(dp(44), dp(48)).apply {
            gravity = Gravity.CENTER_VERTICAL; marginStart = dp(6)
        })
        setOnLongClickListener {
            Sheet(this@MiniAppsActivity).header(c.name)
                .item(getString(R.string.counter_reset)) { bumpCounter(c, -c.value) }
                .item(getString(R.string.remove), bold = true) {
                    counters = counters.filterNot { it.id == c.id }
                    MiniExtrasStore.saveCounters(this@MiniAppsActivity, counters)
                    render()
                }.show()
            true
        }
    }

    private fun bumpCounter(c: CounterItem, delta: Int) {
        counters = counters.map { if (it.id == c.id) it.copy(value = (it.value + delta).coerceAtLeast(0)) else it }
        MiniExtrasStore.saveCounters(this, counters)
        render()
    }

    // ---- quotes -------------------------------------------------------------

    private fun quotesPage() {
        body.setPadding(dp(Ui.MARGIN), dp(6), dp(Ui.MARGIN), 0)
        if (quotes.isEmpty()) {
            body.addView(text(getString(R.string.quote_empty), 16f, color = Ui.GRAY).apply { gravity = Gravity.CENTER }, lp(MATCH, 0, 1f))
        } else {
            quoteIndex = quoteIndex.coerceIn(0, quotes.lastIndex)
            val q = quotes[quoteIndex]
            body.addView(softCard(0).apply {
                addView(vbox().apply {
                    setPadding(dp(18), dp(18), dp(18), dp(16))
                    addView(text("“", 34f, Ui.heavy, Ui.LIGHT_GRAY))
                    addView(text(q.text, 20f, Ui.medium, lines = 6).apply { setLineSpacing(dp(4).toFloat(), 1f) }, lp(MATCH, WRAP).apply { topMargin = dp(2) })
                    if (q.author.isNotBlank()) addView(text("— ${q.author}", 13f, color = Ui.GRAY).apply { gravity = Gravity.END }, lp(MATCH, WRAP).apply { topMargin = dp(8) })
                }, lp(MATCH, MATCH))
                setOnClickListener { quoteIndex = (quoteIndex + 1) % quotes.size; render() }
                setOnLongClickListener {
                    Sheet(this@MiniAppsActivity).header(getString(R.string.remove))
                        .item(getString(R.string.remove), bold = true) {
                            quotes = quotes.filterNot { it.id == q.id }
                            MiniExtrasStore.saveQuotes(this@MiniAppsActivity, quotes)
                            quoteIndex = 0
                            render()
                        }.show()
                    true
                }
            }, lp(MATCH, 0, 1f))
            body.addView(hbox().apply {
                addView(text("‹", 26f, Ui.medium).apply {
                    gravity = Gravity.CENTER
                    setOnClickListener { quoteIndex = (quoteIndex + quotes.size - 1) % quotes.size; render() }
                }, lp(0, dp(36), 1f))
                addView(text("${quoteIndex + 1} / ${quotes.size}", 14f, color = Ui.GRAY).apply { gravity = Gravity.CENTER }, lp(0, dp(36), 2f))
                addView(text("›", 26f, Ui.medium).apply {
                    gravity = Gravity.CENTER
                    setOnClickListener { quoteIndex = (quoteIndex + 1) % quotes.size; render() }
                }, lp(0, dp(36), 1f))
            }, lp(MATCH, WRAP).apply { topMargin = dp(4) })
        }
        body.addView(hline(1, inset = false))
        body.addView(inputRow(R.string.quote_add_hint) { text ->
            quotes = quotes + QuoteItem(System.currentTimeMillis(), text, "")
            MiniExtrasStore.saveQuotes(this, quotes)
            quoteIndex = quotes.lastIndex
            render()
        }, lp(MATCH, dp(Ui.FOOTER_DP)))
    }

    // ---- clock --------------------------------------------------------------

    private fun clockPage() {
        body.setPadding(dp(Ui.MARGIN), 0, dp(Ui.MARGIN), 0)
        clockText = text(clockTime(), 60f, Ui.heavy).apply { gravity = Gravity.CENTER }
        body.addView(clockText, lp(MATCH, WRAP).apply { topMargin = dp(14) })
        body.addView(text(clockDate(), 15f, color = Ui.GRAY).apply { gravity = Gravity.CENTER }, lp(MATCH, WRAP).apply { topMargin = dp(2) })
        body.addView(MonthView(this, LocalDate.now()), lp(MATCH, 0, 1f).apply { topMargin = dp(10) })
        handler.removeCallbacks(clockTick)
        handler.postDelayed(clockTick, 30_000)
    }

    private fun clockTime(): String {
        val pattern = if (android.text.format.DateFormat.is24HourFormat(this)) "HH:mm" else "h:mm a"
        return java.time.LocalTime.now().format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }

    private fun clockDate(): String {
        val today = LocalDate.now()
        return if (HomePrefs.isEnglish(this)) {
            today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH))
        } else {
            today.format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN))
        }
    }

    /** Adds a one-line text field and an add button at the bottom of a page. */
    private fun inputRow(hintRes: Int, onAdd: (String) -> Unit): View {
        val field = EditText(this).apply {
            hint = getString(hintRes)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f * Ui.textScale)
            setTextColor(Ui.BLACK)
            setHintTextColor(Ui.LIGHT_GRAY)
            typeface = Ui.regular
            background = null
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(dp(Ui.MARGIN), 0, dp(8), 0)
        }
        val add = text(getString(R.string.add), 18f, Ui.bold).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), 0)
            setOnClickListener {
                val value = field.text.toString().trim()
                if (value.isNotEmpty()) {
                    onAdd(value)
                    field.setText("")
                }
            }
        }
        return hbox().apply {
            addView(field, lp(0, MATCH, 1f))
            addView(add, lp(WRAP, MATCH))
        }
    }

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

/** 이번 달 달력. 오늘만 검은 원으로 표시한다. */
private class MonthView(context: Context, private val month: LocalDate) : View(context) {
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Ui.BLACK }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.BLACK }

    override fun onDraw(canvas: Canvas) {
        val first = month.withDayOfMonth(1)
        val offset = first.dayOfWeek.value - 1
        val len = month.lengthOfMonth()
        val rows = (offset + len + 6) / 7
        val headerH = context.dpf(24)
        val cellW = width / 7f
        val cellH = minOf((height - headerH) / rows, context.dpf(58))
        val top = ((height - headerH - cellH * rows) / 2f).coerceAtLeast(0f)
        val labels = context.getString(R.string.habit_days).split(",")
        text.textSize = context.spf(11f)
        text.typeface = Ui.regular
        text.color = Ui.GRAY
        for (i in 0..6) {
            canvas.drawText(labels.getOrElse(i) { "" }, cellW * (i + 0.5f), top + headerH * 0.7f, text)
        }
        val today = LocalDate.now()
        for (day in 1..len) {
            val idx = offset + day - 1
            val col = idx % 7
            val row = idx / 7
            val cx = cellW * (col + 0.5f)
            val cy = top + headerH + cellH * (row + 0.5f)
            val d = first.plusDays((day - 1).toLong())
            val isToday = d == today
            if (isToday) {
                fill.color = Ui.BLACK
                canvas.drawCircle(cx, cy, minOf(cellW, cellH) * 0.34f, fill)
            }
            text.color = when {
                isToday -> android.graphics.Color.WHITE
                col == 6 -> Ui.GRAY
                else -> Ui.BLACK
            }
            text.textSize = context.spf(if (isToday) 14f else 13f)
            text.typeface = if (isToday) Ui.bold else Ui.regular
            val baseline = cy - (text.descent() + text.ascent()) / 2f
            canvas.drawText(day.toString(), cx, baseline, text)
        }
    }
}

data class Note(val id: Long, val title: String, val body: String, val updated: Long)/** Notes are small; a JSON array in the app's private prefs is enough. */
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
