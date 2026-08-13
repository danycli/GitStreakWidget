package com.danycli.gitstreakwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class StreakDataTest {

    @Test
    fun `test StreakData toJson and fromJson`() {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val history = mapOf(
            today to true,
            yesterday to false
        )

        val originalData = StreakData(
            streakCount = 5,
            committedToday = true,
            history = history
        )

        val jsonString = originalData.toJson()
        val parsedData = StreakData.fromJson(jsonString)

        assertNotNull(parsedData)
        assertEquals(originalData.streakCount, parsedData?.streakCount)
        assertEquals(originalData.committedToday, parsedData?.committedToday)
        assertEquals(originalData.history.size, parsedData?.history?.size)
        assertTrue(parsedData?.history?.get(today) == true)
        assertTrue(parsedData?.history?.get(yesterday) == false)
    }
}
