package com.example.sportsai

import android.Manifest
import android.net.Uri
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sportsai.data.HighlightExtractor
import com.example.sportsai.data.PoseAnalyzer
import com.example.sportsai.model.AthleteTrackingMode
import com.example.sportsai.model.Sport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Optional connected-device acceptance test for a real MP4 without committing personal footage.
 * Supply `-e batterVideoUri content://...` to exercise decode, tracking, replay, and highlights.
 */
class BatterPoseVideoInstrumentedTest {

    @Test
    fun suppliedRealVideoProducesOneCappedBatterTrack() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val uriText = InstrumentationRegistry.getArguments().getString("batterVideoUri")
        assumeTrue("No batterVideoUri supplied; optional video acceptance test skipped", !uriText.isNullOrBlank())

        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.READ_MEDIA_VIDEO)
        val suppliedUri = Uri.parse(uriText)
        val copiedIntoCache = suppliedUri.scheme != "file"
        val localVideo = if (copiedIntoCache) {
            File(instrumentation.targetContext.cacheDir, "connected-batter-test.mp4")
        } else {
            File(requireNotNull(suppliedUri.path))
        }
        if (copiedIntoCache) {
            instrumentation.context.contentResolver.openInputStream(suppliedUri).use { input ->
                requireNotNull(input) { "Could not open the supplied batterVideoUri" }
                localVideo.outputStream().use(input::copyTo)
            }
        }
        val analyzer = PoseAnalyzer(instrumentation.targetContext)
        val startedAt = SystemClock.elapsedRealtime()
        val result = try {
            analyzer.analyze(Uri.fromFile(localVideo), Sport.BASEBALL_BAT)
        } finally {
            analyzer.close()
            instrumentation.uiAutomation.dropShellPermissionIdentity()
            if (copiedIntoCache) localVideo.delete()
        }

        try {
            assertEquals(AthleteTrackingMode.BATTER_LOCKED, result.athleteTracking.mode)
            assertTrue("Expected a continuous batter timeline", result.timeline.size >= 5)
            assertTrue("Replay must stay memory-bounded", result.animationFrames.size <= 24)
            if (result.animationFrames.size >= 8 && result.keyFramePose != null) {
                assertTrue(
                    "Replay should stay centered on the selected swing burst",
                    result.animationFrames.all { frame ->
                        kotlin.math.abs(frame.pose.timestampMs - result.keyFramePose.timestampMs) <= 1_500L
                    }
                )
            }
            assertTrue(
                "Expected a pose-timed swing highlight",
                HighlightExtractor().extract(result, Sport.BASEBALL_BAT).isNotEmpty()
            )
            val elapsedMs = SystemClock.elapsedRealtime() - startedAt
            assertTrue("Acceptance video took too long: ${elapsedMs}ms", elapsedMs < 180_000L)
        } finally {
            result.keyFrame?.recycle()
            result.animationFrames.forEach { frame -> frame.bitmap.recycle() }
        }
    }
}
