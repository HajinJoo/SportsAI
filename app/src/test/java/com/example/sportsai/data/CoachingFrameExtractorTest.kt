package com.example.sportsai.data

import com.example.sportsai.model.FramePose
import com.example.sportsai.model.HighlightClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachingFrameExtractorTest {

    @Test
    fun selectionFocusesOnDetectedActionAndCoversItsSequence() {
        val timeline = (0L..3_000L step 200L).map { FramePose(it, emptyList()) }
        val action = HighlightClip(
            id = 1L,
            label = "Best swing",
            startMs = 800L,
            endMs = 2_200L
        )

        val selected = selectCoachingPoses(timeline, action, maxFrames = 8)

        assertEquals(8, selected.size)
        assertTrue(selected.all { it.timestampMs in action.startMs..action.endMs })
        assertEquals(800L, selected.first().timestampMs)
        assertEquals(2_200L, selected.last().timestampMs)
    }

    @Test
    fun selectionFallsBackToWholeTimelineWhenNoHighlightExists() {
        val timeline = (0L..2_000L step 200L).map { FramePose(it, emptyList()) }

        val selected = selectCoachingPoses(timeline, actionWindow = null, maxFrames = 4)

        assertEquals(4, selected.size)
        assertEquals(0L, selected.first().timestampMs)
        assertEquals(2_000L, selected.last().timestampMs)
    }
}
