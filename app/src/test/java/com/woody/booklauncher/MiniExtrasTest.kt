package com.woody.booklauncher

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 미니 앱 자료(습관·할 일·계수기·문장)가 설정 파일에 그대로 남는지. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MiniExtrasTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Test
    fun `습관을 저장하고 다시 읽는다`() {
        MiniExtrasStore.saveHabits(app, emptyList())
        val h = Habit(1, "물 마시기", setOf("2026-11-21", "2026-11-20"))
        MiniExtrasStore.saveHabits(app, listOf(h))
        val read = MiniExtrasStore.habits(app)
        assertEquals(1, read.size)
        assertEquals("물 마시기", read[0].name)
        assertEquals(setOf("2026-11-21", "2026-11-20"), read[0].days)
    }

    @Test
    fun `할 일은 끝난 것이 뒤로 간다`() {
        MiniExtrasStore.saveTodos(app, emptyList())
        MiniExtrasStore.saveTodos(app, listOf(
            Todo(1, "먼저", true, 10),
            Todo(2, "나중", false, 20),
        ))
        val read = MiniExtrasStore.todos(app)
        assertEquals(listOf("나중", "먼저"), read.map { it.text })
        assertEquals(false, read[0].done)
    }

    @Test
    fun `계수기와 문장을 저장하고 다시 읽는다`() {
        MiniExtrasStore.saveCounters(app, emptyList())
        MiniExtrasStore.saveCounters(app, listOf(CounterItem(1, "커피", 3, 1)))
        assertEquals(3, MiniExtrasStore.counters(app).single().value)

        MiniExtrasStore.saveQuotes(app, emptyList())
        MiniExtrasStore.saveQuotes(app, listOf(QuoteItem(1, "천천히 읽어도 된다.", "나")))
        val quote = MiniExtrasStore.quotes(app).single()
        assertEquals("천천히 읽어도 된다.", quote.text)
        assertEquals("나", quote.author)
    }

    @Test
    fun `companion and quest survive a restart`() {
        MiniExtrasStore.savePet(app, PetState("Momo", 10, 40, 80, 60, "2026-11-21", "2026-11-21", "", ""))
        val pet = MiniExtrasStore.pet(app)!!
        assertEquals("Momo", pet.name)
        assertEquals(80, pet.mood)
        assertEquals(60, pet.energy)
        assertEquals("2026-11-21", pet.fedDay)

        MiniExtrasStore.saveQuest(app, QuestState(100, 3, listOf(0, 1, 2)))
        val quest = MiniExtrasStore.quest(app)!!
        assertEquals(3, quest.chapter)
        assertEquals(listOf(0, 1, 2), quest.choices)
        assertEquals(50L, quest.xp(150))
    }
}
