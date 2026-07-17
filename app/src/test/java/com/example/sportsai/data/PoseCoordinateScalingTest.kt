package com.example.sportsai.data

import com.example.sportsai.model.LandmarkPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PoseCoordinateScalingTest {

    @Test
    fun mapsDetectorCoordinatesBackToTheSourceFrameForCoachingAndReplay() {
        val detected = listOf(LandmarkPoint(type = 15, x = 480f, y = 270f, inFrameLikelihood = 0.9f, z = -24f))

        val source = scaleLandmarksToFrame(
            landmarks = detected,
            decodedWidth = 960,
            decodedHeight = 540,
            sourceWidth = 3_840,
            sourceHeight = 2_160
        ).single()

        assertEquals(1_920f, source.x, 0.001f)
        assertEquals(1_080f, source.y, 0.001f)
        assertEquals(-96f, source.z, 0.001f)
    }

    @Test
    fun leavesAlreadySourceSizedLandmarksUntouched() {
        val detected = listOf(LandmarkPoint(11, 100f, 200f, 0.9f))

        val result = scaleLandmarksToFrame(detected, 1_280, 720, 1_280, 720)

        assertSame(detected, result)
    }

    @Test
    fun mapsNonUniformDecodeBoundsWithoutMixingAxes() {
        val detected = listOf(LandmarkPoint(15, 100f, 100f, 0.9f, z = 10f))

        val result = scaleLandmarksToFrame(detected, 400, 200, 800, 600).single()

        assertEquals(200f, result.x, 0.001f)
        assertEquals(300f, result.y, 0.001f)
        assertEquals(20f, result.z, 0.001f)
    }

    @Test
    fun swapsMetadataDimensionsForQuarterTurnVideo() {
        assertEquals(720 to 1_280, orientedVideoFrameSize(1_280, 720, 90))
        assertEquals(720 to 1_280, orientedVideoFrameSize(1_280, 720, 270))
        assertEquals(1_280 to 720, orientedVideoFrameSize(1_280, 720, 0))
    }
}
