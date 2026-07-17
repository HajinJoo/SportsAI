package com.example.sportsai

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sportsai.data.PoseAnalyzer
import com.example.sportsai.model.AthleteTrackingMode
import com.example.sportsai.model.Sport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Optional real-motion gate; skipped unless the attributed test clip is placed on the device. */
class BatterVideoRegressionInstrumentedTest {

    @Test
    fun realGameAngleKeepsTheSwingingBatterInsteadOfCatcherOrUmpire() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val video = File(context.getExternalFilesDir(null), TEST_VIDEO_NAME)
        assumeTrue("Optional real-game batting clip is not installed", video.exists())

        val analyzer = PoseAnalyzer(context)
        val result = try {
            analyzer.analyze(Uri.fromFile(video), Sport.BASEBALL_BAT, targetFps = 10)
        } finally {
            analyzer.close()
        }
        try {
            assertEquals(
                "Batter Lock evidence: ${result.athleteTracking}",
                AthleteTrackingMode.BATTER_LOCKED,
                result.athleteTracking.mode
            )
            assertTrue("Expected a game-angle multi-person scene", result.athleteTracking.maxPeopleDetected >= 2)
            assertTrue("Expected a continuous selected batter track", result.timeline.size >= 20)
            assertTrue("Expected enough batter visibility", result.detectionRate >= 0.35f)
            LABELED_BATTER_TIMESTAMPS_MS.forEach { timestampMs ->
                val pose = result.timeline.minBy { kotlin.math.abs(it.timestampMs - timestampMs) }
                assertTrue(
                    "No selected pose close to the labeled batter frame at ${timestampMs}ms",
                    kotlin.math.abs(pose.timestampMs - timestampMs) <= 200L
                )
                val torso = pose.landmarks.filter { it.type in TORSO_LANDMARKS }
                assertTrue("Missing labeled batter torso near ${timestampMs}ms", torso.size == 4)
                val normalizedX = torso.map { it.x }.average() / TEST_VIDEO_WIDTH
                val normalizedY = torso.map { it.y }.average() / TEST_VIDEO_HEIGHT
                assertTrue(
                    "Selected track left the manually labeled batter region near ${timestampMs}ms: " +
                        "center=($normalizedX,$normalizedY), evidence=${result.athleteTracking}",
                    normalizedX in BATTER_MIN_X..BATTER_MAX_X &&
                        normalizedY in BATTER_MIN_Y..BATTER_MAX_Y
                )
            }
        } finally {
            result.keyFrame?.recycle()
            result.animationFrames.forEach { frame -> frame.bitmap.recycle() }
        }
    }

    private companion object {
        const val TEST_VIDEO_NAME = "real-game-batting-9s.mp4"
        const val TEST_VIDEO_WIDTH = 1_280.0
        const val TEST_VIDEO_HEIGHT = 720.0
        const val BATTER_MIN_X = 0.36
        const val BATTER_MAX_X = 0.47
        const val BATTER_MIN_Y = 0.38
        const val BATTER_MAX_Y = 0.55
        val LABELED_BATTER_TIMESTAMPS_MS = listOf(500L, 5_000L, 8_500L)
        val TORSO_LANDMARKS = setOf(11, 12, 23, 24)
    }
}
