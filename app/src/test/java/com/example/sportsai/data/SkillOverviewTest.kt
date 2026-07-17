package com.example.sportsai.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillOverviewTest {

    @Test
    fun shortOverviewIsCompletedToThreeOrFourSentences() {
        val overview = normalizeSkillOverview("Strong setup.")

        assertTrue(splitOverviewSentences(overview).size in 3..4)
    }

    @Test
    fun longOverviewIsCappedAtFourSentences() {
        val overview = normalizeSkillOverview("One. Two! Three? Four. Five. Six.")

        assertEquals(4, splitOverviewSentences(overview).size)
        assertTrue("Five." !in overview)
    }
}
