package com.example.sportsai.data

import com.example.sportsai.model.FramePose
import com.example.sportsai.model.LandmarkPoint
import com.example.sportsai.model.AnalysisResult
import com.example.sportsai.model.Sport
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class BattingMotionTest {

    @Test
    fun coordinatedBurstWinsOverOneFrameOutAndBackJitter() {
        val handTravel = listOf(0f, 0f, 0f, 0f, 20f, 40f, 60f, 60f, 260f, 60f, 60f)
        val timeline = handTravel.mapIndexed { index, travel ->
            pose(timestampMs = index * 100L, handTravel = travel)
        }

        assertTrue(coordinatedBattingPeakIndex(timeline) in 4..6)
    }

    @Test
    fun cameraTranslationZoomAndRollDoNotBecomeASwing() {
        val timeline = (0..8).map { index ->
            pose(
                timestampMs = index * 100L,
                handTravel = 0f,
                scale = 1.0 + index * 0.04,
                rotationRadians = index * 0.035,
                translateX = index * 14.0,
                translateY = index * -7.0
            )
        }

        assertNull(coordinatedBattingPeakIndex(timeline))
        val result = AnalysisResult(
            framesSampled = timeline.size,
            framesWithPose = timeline.size,
            timeline = timeline,
            durationMs = timeline.last().timestampMs
        )
        assertTrue(HighlightExtractor().extract(result, Sport.BASEBALL_BAT).isEmpty())
    }

    private fun pose(
        timestampMs: Long,
        handTravel: Float,
        scale: Double = 1.0,
        rotationRadians: Double = 0.0,
        translateX: Double = 0.0,
        translateY: Double = 0.0
    ): FramePose {
        val base = listOf(
            point(11, 250f, 200f), point(12, 350f, 200f),
            point(15, 345f + handTravel, 250f), point(16, 365f + handTravel, 255f),
            point(23, 265f, 360f), point(24, 335f, 360f)
        )
        val transformed = base.map { landmark ->
            val x = landmark.x - 300.0
            val y = landmark.y - 300.0
            landmark.copy(
                x = (x * scale * cos(rotationRadians) - y * scale * sin(rotationRadians) +
                    300.0 + translateX).toFloat(),
                y = (x * scale * sin(rotationRadians) + y * scale * cos(rotationRadians) +
                    300.0 + translateY).toFloat()
            )
        }
        return FramePose(timestampMs, transformed)
    }

    private fun point(type: Int, x: Float, y: Float) =
        LandmarkPoint(type, x, y, inFrameLikelihood = 0.99f)
}
