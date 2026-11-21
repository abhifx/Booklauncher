package com.woody.booklauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 습관: 날짜(ISO)마다 한 번씩 표시한다. */
data class Habit(val id: Long, val name: String, val days: Set<String>)

/** 할 일: 끝냈는지만 기억한다. */
data class Todo(val id: Long, val text: String, val done: Boolean, val created: Long)

/** 계수기: 이름·현재 값·한 번에 더하는 값. */
data class CounterItem(val id: Long, val name: String, val value: Int, val step: Int)

/** 문장: 글과 지은이. */
data class QuoteItem(val id: Long, val text: String, val author: String)

/** 성장 단계. [atMinutes] 는 입양한 뒤 읽은 시간(분). */
enum class PetStage(val ko: String, val en: String, val atMinutes: Long) {
    EGG("알", "Egg", 0),
    BABY("아기", "Baby", 60),
    CHILD("어린이", "Child", 300),
    TEEN("청소년", "Teen", 900),
    ADULT("어른", "Adult", 1_800),
    ELDER("장로", "Elder", 3_600);

    companion object {
        fun of(minutes: Long): PetStage = entries.last { minutes >= it.atMinutes }
    }
}

/** 읽은 시간으로 자라는 동반자. 모든 시간은 분 단위. */
data class PetState(
    val name: String,
    val startMinutes: Long,
    val lastMinutes: Long,
    val mood: Int,
    val energy: Int,
    val lastDay: String,
    val fedDay: String,
    val playedDay: String,
    val restedDay: String,
) {
    fun xp(totalMinutes: Long): Long = (totalMinutes - startMinutes).coerceAtLeast(0)
    fun stage(totalMinutes: Long): PetStage = PetStage.of(xp(totalMinutes))
    fun nextStage(totalMinutes: Long): PetStage? =
        PetStage.entries.firstOrNull { it.atMinutes > xp(totalMinutes) }
    fun minutesToNext(totalMinutes: Long): Long? =
        nextStage(totalMinutes)?.let { (it.atMinutes - xp(totalMinutes)).coerceAtLeast(0) }

    /** Days away lower mood and energy; new reading lifts both a little. */
    fun touch(totalMinutes: Long, today: java.time.LocalDate): PetState {
        var m = mood
        var e = energy
        if (lastDay.isNotEmpty()) {
            val days = runCatching {
                java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(lastDay), today)
            }.getOrDefault(0).coerceAtLeast(0)
            m = (m - (days * 8).toInt()).coerceIn(0, 100)
            e = (e - (days * 6).toInt()).coerceIn(0, 100)
        }
        val read = (totalMinutes - lastMinutes).coerceAtLeast(0)
        if (read > 0) {
            m = (m + (read / 8).coerceIn(1, 20).toInt()).coerceIn(0, 100)
            e = (e + (read / 12).coerceIn(1, 12).toInt()).coerceIn(0, 100)
        }
        return copy(mood = m, energy = e, lastMinutes = totalMinutes, lastDay = today.toString())
    }

    fun feed(day: String): PetState =
        if (fedDay == day) this else copy(mood = (mood + 20).coerceAtMost(100), fedDay = day)

    fun play(day: String): PetState =
        if (playedDay == day || energy < 10) this
        else copy(mood = (mood + 15).coerceAtMost(100), energy = (energy - 10).coerceAtLeast(0), playedDay = day)

    fun rest(day: String): PetState =
        if (restedDay == day) this else copy(energy = (energy + 25).coerceAtMost(100), restedDay = day)
}

/** Reading quest progress: minutes at the start, next chapter, and choice per finished chapter. */
data class QuestState(
    val startMinutes: Long,
    val chapter: Int,
    val choices: List<Int>,
) {
    fun xp(totalMinutes: Long): Long = (totalMinutes - startMinutes).coerceAtLeast(0)
}


/**
 * 미니 앱 다섯(습관·할 일·계수기·문장·시계)의 자료. 모두 `shared_prefs/mini.xml` 에 JSON 으로 둔다.
 * 자료가 적어 DB 를 늘리지 않는다.
 */
object MiniExtrasStore {
    private const val PREFS = "mini"
    private const val HABITS = "habits"
    private const val TODOS = "todos"
    private const val COUNTERS = "counters"
    private const val QUOTES = "quotes"
    private const val PET = "pet"
    private const val QUEST = "quest"

    fun habits(context: Context): List<Habit> = read(context, HABITS) { o ->
        val days = mutableSetOf<String>()
        o.optJSONArray("days")?.let { a -> for (i in 0 until a.length()) days += a.getString(i) }
        Habit(o.getLong("id"), o.optString("name"), days)
    }

    fun todos(context: Context): List<Todo> = read(context, TODOS) { o ->
        Todo(o.getLong("id"), o.optString("text"), o.optBoolean("done"), o.optLong("created"))
    }.sortedWith(compareBy({ it.done }, { -it.created }))

    fun counters(context: Context): List<CounterItem> = read(context, COUNTERS) { o ->
        CounterItem(o.getLong("id"), o.optString("name"), o.optInt("value"), o.optInt("step", 1).coerceAtLeast(1))
    }

    fun quotes(context: Context): List<QuoteItem> = read(context, QUOTES) { o ->
        QuoteItem(o.getLong("id"), o.optString("text"), o.optString("author"))
    }

    fun saveHabits(context: Context, list: List<Habit>) = write(context, HABITS, list) { h ->
        JSONObject().apply {
            put("id", h.id); put("name", h.name)
            put("days", JSONArray(h.days.sorted()))
        }
    }

    fun saveTodos(context: Context, list: List<Todo>) = write(context, TODOS, list) { t ->
        JSONObject().apply {
            put("id", t.id); put("text", t.text); put("done", t.done); put("created", t.created)
        }
    }

    fun saveCounters(context: Context, list: List<CounterItem>) = write(context, COUNTERS, list) { c ->
        JSONObject().apply {
            put("id", c.id); put("name", c.name); put("value", c.value); put("step", c.step)
        }
    }

    fun saveQuotes(context: Context, list: List<QuoteItem>) = write(context, QUOTES, list) { q ->
        JSONObject().apply { put("id", q.id); put("text", q.text); put("author", q.author) }
    }

    fun pet(context: Context): PetState? {
        val raw = prefs(context).getString(PET, null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            PetState(
                o.optString("name", "Momo"),
                o.optLong("start"),
                o.optLong("last"),
                o.optInt("mood", 70).coerceIn(0, 100),
                o.optInt("energy", 70).coerceIn(0, 100),
                o.optString("day"),
                o.optString("fed"),
                o.optString("played"),
                o.optString("rested"),
            )
        }.getOrNull()
    }

    fun savePet(context: Context, pet: PetState) {
        prefs(context).edit().putString(PET, JSONObject().apply {
            put("name", pet.name)
            put("start", pet.startMinutes)
            put("last", pet.lastMinutes)
            put("mood", pet.mood)
            put("energy", pet.energy)
            put("day", pet.lastDay)
            put("fed", pet.fedDay)
            put("played", pet.playedDay)
            put("rested", pet.restedDay)
        }.toString()).apply()
    }

    fun quest(context: Context): QuestState? {
        val raw = prefs(context).getString(QUEST, null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            val choices = mutableListOf<Int>()
            o.optJSONArray("choices")?.let { a -> for (i in 0 until a.length()) choices += a.getInt(i) }
            QuestState(o.optLong("start"), o.optInt("chapter", 0).coerceAtLeast(0), choices)
        }.getOrNull()
    }

    fun saveQuest(context: Context, quest: QuestState) {
        prefs(context).edit().putString(QUEST, JSONObject().apply {
            put("start", quest.startMinutes)
            put("chapter", quest.chapter)
            put("choices", JSONArray(quest.choices))
        }.toString()).apply()
    }

    private fun <T> read(context: Context, key: String, parse: (JSONObject) -> T): List<T> {
        val raw = prefs(context).getString(key, null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).mapNotNull { i -> runCatching { parse(a.getJSONObject(i)) }.getOrNull() }
        }.getOrDefault(emptyList())
    }

    private fun <T> write(context: Context, key: String, list: List<T>, build: (T) -> JSONObject) {
        val a = JSONArray()
        list.forEach { a.put(build(it)) }
        prefs(context).edit().putString(key, a.toString()).apply()
    }

    private fun prefs(context: Context) =
        Storage.of(context).getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Total reading time in minutes from the stored reading log. */
internal fun readingMinutes(context: Context): Long =
    ReadingLog.savedDays(context).values.sumOf { day -> day.values.sum() } / 60_000
