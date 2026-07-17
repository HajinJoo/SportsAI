package com.example.sportsai

import android.Manifest
import android.net.Uri
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sportsai.data.HighlightExtractor
import com.example.sportsai.data.PoseAnalyzer
import com.example.sportsai.data.SwingAnalysisJsonCodec
import com.example.sportsai.data.TechniqueAnalyzer
import com.example.sportsai.model.AthleteTrackingMode
import com.example.sportsai.model.Sport
import com.example.sportsai.model.SwingCameraView
import com.example.sportsai.model.SwingPhase
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
            assertTrue(
                "Expected the object detector to process decoded batting frames",
                result.objectDetectionFrames > 0
            )
            val report = TechniqueAnalyzer().analyze(result, Sport.BASEBALL_BAT)
            val swingAnalysis = requireNotNull(report.swingAnalysis)
            assertEquals(
                listOf(
                    SwingPhase.STANCE,
                    SwingPhase.STRIDE,
                    SwingPhase.IMPACT_ZONE,
                    SwingPhase.FOLLOW_THROUGH
                ),
                swingAnalysis.phases.map { it.phase }
            )
            assertTrue("Expected numeric swing measurements", swingAnalysis.measurements.isNotEmpty())
            assertTrue(
                "Expected a confirmed camera view: ${swingAnalysis.cameraView.evidence}",
                swingAnalysis.cameraView.view != SwingCameraView.UNKNOWN
            )
            val measurementKeys = swingAnalysis.measurements.map { it.key }.toSet()
            val routedKeys = when (swingAnalysis.cameraView.view) {
                SwingCameraView.SIDE -> setOf(
                    "front_knee_angle_impact",
                    "trail_knee_angle_impact",
                    "hands_travel_with_stride"
                )
                SwingCameraView.REAR -> setOf(
                    "stance_spine_angle",
                    "spine_angle_change",
                    "head_movement_range",
                    "shoulder_rotation_at_stride"
                )
                SwingCameraView.UNKNOWN -> emptySet()
            }
            assertTrue(
                "Expected ${swingAnalysis.cameraView.view} measurements in $measurementKeys",
                measurementKeys.any { it in routedKeys }
            )
            assertTrue("Expected bat/ball detector summaries", swingAnalysis.equipment.size == 2)
            val analysisJson = SwingAnalysisJsonCodec.encodeReport(report)
                .getJSONObject("swingAnalysis")
            assertEquals(2, analysisJson.getInt("schemaVersion"))
            assertEquals(
                swingAnalysis.cameraView.view.name,
                analysisJson.getJSONObject("cameraView").getString("view")
            )
            assertEquals(4, analysisJson.getJSONArray("phases").length())
            assertEquals(
                swingAnalysis.issues.map { it.code },
                (0 until analysisJson.getJSONArray("issues").length()).map { index ->
                    analysisJson.getJSONArray("issues").getJSONObject(index).getString("code")
                }
            )
            val elapsedMs = SystemClock.elapsedRealtime() - startedAt
            assertTrue("Acceptance video took too long: ${elapsedMs}ms", elapsedMs < 180_000L)
        } finally {
            result.keyFrame?.recycle()
            result.animationFrames.forEach { frame -> frame.bitmap.recycle() }
        }
    }
}
