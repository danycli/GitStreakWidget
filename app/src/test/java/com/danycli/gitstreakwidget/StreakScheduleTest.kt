package com.danycli.gitstreakwidget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class StreakScheduleTest {

    private val morning = LocalDateTime.of(2026, 11, 17, 8, 0)
    private val afternoon = LocalDateTime.of(2026, 11, 17, 14, 0)
    private val night = LocalDateTime.of(2026, 11, 17, 20, 0)

    @Test
    fun `before the first check all three slots of today are armed`() {
        val slots = nextCheckSlots(LocalDateTime.of(2026, 11, 17, 6, 30))
        assertEquals(listOf(morning, afternoon, night), slots)
    }

    @Test
    fun `after the morning check the remaining slots of today are armed`() {
        val slots = nextCheckSlots(LocalDateTime.of(2026, 11, 17, 9, 15))
        assertEquals(listOf(afternoon, night), slots)
    }

    @Test
    fun `when today is exhausted the next day is armed`() {
        val slots = nextCheckSlots(LocalDateTime.of(2026, 11, 17, 21, 5))
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 11, 18, 8, 0),
                LocalDateTime.of(2026, 11, 18, 14, 0),
                LocalDateTime.of(2026, 11, 18, 20, 0)
            ),
            slots
        )
    }

    @Test
    fun `streak already counted skips the rest of the day`() {
        val slots = nextCheckSlots(LocalDateTime.of(2026, 11, 17, 9, 15), skipRestOfToday = true)
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 11, 18, 8, 0),
                LocalDateTime.of(2026, 11, 18, 14, 0),
                LocalDateTime.of(2026, 11, 18, 20, 0)
            ),
            slots
        )
    }

    @Test
    fun `a check slot is never armed for the moment it is already running in`() {
        val slots = nextCheckSlots(morning)
        assertEquals(listOf(afternoon, night), slots)
    }
}
