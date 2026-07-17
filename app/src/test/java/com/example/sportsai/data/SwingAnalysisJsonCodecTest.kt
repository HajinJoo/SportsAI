package com.example.sportsai.data

import com.example.sportsai.model.BiomechanicsMeasurement
import com.example.sportsai.model.CameraViewAssessment
import com.example.sportsai.model.EquipmentTrackingStatus
import com.example.sportsai.model.EquipmentTrackingSummary
import com.example.sportsai.model.MechanicsIssue
import com.example.sportsai.model.MechanicsIssueSeverity
import com.example.sportsai.model.SwingAnalysisSummary
import com.example.sportsai.model.SwingCameraView
import com.example.sportsai.model.SwingPhase
import com.example.sportsai.model.SwingPhaseSegment
import com.example.sportsai.model.TechniqueReport
import com.example.sportsai.model.TrackedObjectType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwingAnalysisJsonCodecTest {

    @Test
    fun reportJsonCarriesAuthoritativeNumericAnalysis() {
        val analysis = summary()
        val report = TechniqueReport(
            sport = "Baseball Batting",
            overallScore = 78,
            summary = "Local result",
            findings = emptyList(),
            detectionRate = 0.92f,
            metricScores = mapOf("Hip Rotation" to 81),
            analysisProfile = "offline-batter-lock-v3",
            swingAnalysis = analysis
        )

        val json = SwingAnalysisJsonCodec.encodeReport(report)

        assertEquals(78, json.getInt("overallScore"))
        assertEquals(81, json.getJSONObject("metricScores").getInt("Hip Rotation"))
        assertEquals(
            "HEAD_DROP",
            json.getJSONObject("swingAnalysis").getJSONArray("issues")
                .getJSONObject(0).getString("code")
        )
        assertEquals(
            "SIDE",
            json.getJSONObject("swingAnalysis").getJSONObject("cameraView").getString("view")
        )
    }

    @Test
    fun structuredSummaryRoundTripsWithoutLosingLabels() {
        val original = summary()
        val encoded = SwingAnalysisJsonCodec.encodeSummary(original) as JSONObject

        val decoded = SwingAnalysisJsonCodec.decodeSummary(encoded)

        assertEquals(original, decoded)
        assertTrue(decoded!!.phases.zipWithNext().all { (first, second) ->
            first.endMs <= second.startMs
        })
    }

    @Test
    fun legacySummaryWithoutCameraViewDecodesAsUnknown() {
        val legacy = SwingAnalysisJsonCodec.encodeSummary(summary()) as JSONObject
        legacy.put("schemaVersion", 1).remove("cameraView")

        val decoded = SwingAnalysisJsonCodec.decodeSummary(legacy)

        assertEquals(1, decoded!!.schemaVersion)
        assertEquals(SwingCameraView.UNKNOWN, decoded.cameraView.view)
    }

    private fun summary() = SwingAnalysisSummary(
        cameraView = CameraViewAssessment(
            view = SwingCameraView.SIDE,
            confidence = 0.84f,
            usableFrames = 10,
            evidence = "Projected geometry supports a side view."
        ),
        phases = listOf(
            SwingPhaseSegment(SwingPhase.STANCE, 0L, 200L, 0.9f, "setup"),
            SwingPhaseSegment(SwingPhase.STRIDE, 200L, 400L, 0.86f, "ankle travel"),
            SwingPhaseSegment(SwingPhase.IMPACT_ZONE, 400L, 600L, 0.91f, "hand peak"),
            SwingPhaseSegment(SwingPhase.FOLLOW_THROUGH, 600L, 900L, 0.88f, "post peak")
        ),
        measurements = listOf(
            BiomechanicsMeasurement(
                key = "head_vertical_change",
                label = "Head vertical change",
                value = 0.2,
                unit = "body-lengths",
                phase = SwingPhase.IMPACT_ZONE,
                evidence = "head relative to pelvis"
            )
        ),
        issues = listOf(
            MechanicsIssue(
                code = "HEAD_DROP",
                label = "Head drop",
                severity = MechanicsIssueSeverity.WATCH,
                confidence = 0.72f,
                phase = SwingPhase.IMPACT_ZONE,
                evidence = "0.2 body-lengths",
                coachingCue = "Keep the head centered."
            )
        ),
        equipment = listOf(
            EquipmentTrackingSummary(
                type = TrackedObjectType.BAT,
                status = EquipmentTrackingStatus.DETECTED,
                sampledFrames = 10,
                detectedFrames = 6,
                maxConfidence = 0.84f
            )
        )
    )
}