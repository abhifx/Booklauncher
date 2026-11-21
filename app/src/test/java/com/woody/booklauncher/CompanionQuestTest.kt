package com.woody.booklauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CompanionQuestTest {
    private val day1 = LocalDate.of(2026, 11, 20)
    private val day2 = LocalDate.of(2026, 11, 21)

    @Test
    fun `pet stages follow the reading thresholds`() {
        assertEquals(PetStage.EGG, PetStage.of(0))
        assertEquals(PetStage.BABY, PetStage.of(60))
        assertEquals(PetStage.BABY, PetStage.of(299))
        assertEquals(PetStage.CHILD, PetStage.of(300))
        assertEquals(PetStage.ELDER, PetStage.of(99_999))
    }

    @Test
    fun `reading lifts mood and energy and days away lower them`() {
        val p = PetState("Momo", 100, 100, 50, 50, day1.toString(), "", "", "")
        val read = p.touch(180, day1)
        assertTrue(read.mood > 50)
        assertTrue(read.energy > 50)
        assertEquals(80L, read.xp(180))
        val later = read.touch(180, day2)
        assertTrue(later.mood < read.mood)
        assertTrue(later.energy < read.energy)
    }

    @Test
    fun `feed play and rest happen once a day`() {
        val p = PetState("Momo", 0, 0, 50, 50, day1.toString(), "", "", "")
        val fed = p.feed(day2.toString())
        assertEquals(70, fed.mood)
        assertEquals(fed, fed.feed(day2.toString()))
        val played = fed.play(day2.toString())
        assertEquals(85, played.mood)
        assertEquals(40, played.energy)
        assertEquals(played, played.play(day2.toString()))
        val rested = played.rest(day2.toString())
        assertEquals(65, rested.energy)
        assertEquals(rested, rested.rest(day2.toString()))
    }

    @Test
    fun `quest chapters rise in required minutes and end by the strongest trait`() {
        val chapters = ReadingQuest.chapters
        assertTrue(chapters.size >= 8)
        assertEquals(0L, chapters.first().requires)
        chapters.zipWithNext().forEach { (a, b) -> assertTrue(b.requires > a.requires) }
        chapters.forEach { assertTrue(it.choices.size >= 2) }

        val courage = chapters.mapIndexed { i, c -> c.choices.indexOfFirst { it.trait == QuestTrait.COURAGE }.let { if (it >= 0) it else 0 } }
        assertEquals("The Lantern Road", ReadingQuest.ending(courage).second)
        assertTrue(ReadingQuest.endingText(courage).second.isNotEmpty())

        val balanced = listOf(0, 0, 1, 1, 0, 2, 0, 1, 2)
        assertEquals("The Long Night", ReadingQuest.ending(balanced).second)
    }

    @Test
    fun `quest progress counts minutes since it began`() {
        val q = QuestState(500, 2, listOf(0, 1))
        assertEquals(0L, q.xp(400))
        assertEquals(120L, q.xp(620))
    }
}
