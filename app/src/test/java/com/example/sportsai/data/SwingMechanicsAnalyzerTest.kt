package com.example.sportsai.data

import com.example.sportsai.model.AnalysisResult
import com.example.sportsai.model.AthleteTrackingInfo
import com.example.sportsai.model.AthleteTrackingMode
import com.example.sportsai.model.EquipmentTrackingStatus
import com.example.sportsai.model.FramePose
import com.example.sportsai.model.LandmarkPoint
import com.example.sportsai.model.SwingCameraView
import com.example.sportsai.model.SwingPhase
import com.example.sportsai.model.TrackedObject
import com.example.sportsai.model.TrackedObjectType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwingMechanicsAnalyzerTest {

    @Test
    fun classifiesSideRearAndAmbiguousViewsFromNormalizedPoseGeometry() {
        val side = SwingMechanicsAnalyzer().analyze(result(swingTimeline()))
        val rear = SwingMechanicsAnalyzer().analyze(result(swingTimeline(bodyWidthScale = 1.7f)))
        val ambiguous = SwingMechanicsAnalyzer().analyze(result(swingTimeline(bodyWidthScale = 1.35f)))

        assertEquals(SwingCameraView.SIDE, side.cameraView.view)
        assertEquals(SwingCameraView.REAR, rear.cameraView.view)
        assertEquals(SwingCameraView.UNKNOWN, ambiguous.cameraView.view)
        assertTrue(side.cameraView.confidence >= 0.6f)
        assertTrue(rear.cameraView.confidence >= 0.6f)
        assertTrue(ambiguous.cameraView.evidence.contains("ambiguous"))
    }

    @Test
    fun segmentsSwingAndSummarizesEquipmentDetections() {
        val timeline = swingTimeline()
        val result = result(
            timeline = timeline,
            trackedObjects = listOf(
                TrackedObject(400L, TrackedObjectType.BAT, 360f, 190f, 520f, 280f, 0.74f),
                TrackedObject(500L, TrackedObjectType.BAT, 390f, 180f, 550f, 270f, 0.81f)
            ),
            objectDetectionFrames = timeline.size
        )

        val summary = SwingMechanicsAnalyzer().analyze(result)

        assertEquals(
            listOf(
                SwingPhase.STANCE,
                SwingPhase.STRIDE,
                SwingPhase.IMPACT_ZONE,
                SwingPhase.FOLLOW_THROUGH
            ),
            summary.phases.map { it.phase }
        )
        assertTrue(summary.phases.zipWithNext().all { (first, second) ->
            first.endMs <= second.startMs
        })
        assertTrue(summary.measurements.any { it.key == "peak_hand_speed" })
        val bat = summary.equipment.first { it.type == TrackedObjectType.BAT }
        val ball = summary.equipment.first { it.type == TrackedObjectType.BALL }
        assertEquals(EquipmentTrackingStatus.DETECTED, bat.status)
        assertEquals(2, bat.detectedFrames)
        assertEquals(EquipmentTrackingStatus.NOT_DETECTED, ball.status)
    }

    @Test
    fun labelsRequestedFaultsOnlyFromNumericEvidence() {
        val sideSummary = SwingMechanicsAnalyzer().analyze(
            result(timeline = swingTimeline(withFaults = true))
        )
        val rearSummary = SwingMechanicsAnalyzer().analyze(
            result(timeline = swingTimeline(withFaults = true, bodyWidthScale = 1.7f))
        )

        val sideCodes = sideSummary.issues.map { it.code }.toSet()
        val rearCodes = rearSummary.issues.map { it.code }.toSet()
        assertTrue("Expected HEAD_DROP in $sideCodes", "HEAD_DROP" in sideCodes)
        assertTrue(
            "Expected FRONT_KNEE_COLLAPSE in $sideCodes",
            "FRONT_KNEE_COLLAPSE" in sideCodes
        )
        assertTrue("Side analysis must not emit EARLY_OPEN", "EARLY_OPEN" !in sideCodes)
        assertTrue("Expected EARLY_OPEN in $rearCodes", "EARLY_OPEN" in rearCodes)
        assertTrue("Rear analysis must not emit FRONT_KNEE_COLLAPSE", "FRONT_KNEE_COLLAPSE" !in rearCodes)
        assertTrue(
            (sideSummary.issues + rearSummary.issues).all {
                it.confidence in 0f..1f && it.evidence.isNotBlank()
            }
        )
    }

    private fun result(
        timeline: List<FramePose>,
        trackedObjects: List<TrackedObject> = emptyList(),
        objectDetectionFrames: Int = 0
    ) = AnalysisResult(
        framesSampled = timeline.size,
        framesWithPose = timeline.size,
        timeline = timeline,
        durationMs = timeline.last().timestampMs,
        athleteTracking = AthleteTrackingInfo(
            mode = AthleteTrackingMode.BATTER_LOCKED,
            matchScore = 0.92f,
            trackedFrames = timeline.size,
            trackCoverage = 1f,
            landmarkCompleteness = 0.96f
        ),
        trackedObjects = trackedObjects,
        objectDetectionFrames = objectDetectionFrames
    )

    private fun swingTimeline(
        withFaults: Boolean = false,
        bodyWidthScale: Float = 1f
    ): List<FramePose> {
        val handTravel = listOf(0f, 0f, 0f, 12f, 34f, 58f, 82f, 92f, 96f, 98f)
        return handTravel.mapIndexed { index, travel ->
            val strideTravel = when {
                index < 2 -> 0f
                index < 5 -> (index - 1) * 14f
                else -> 56f
            }
            val headDrop = if (withFaults && index >= 4) 44f else 0f
            val collapsedKneeX = if (withFaults && index >= 6) 300f else 243f - strideTravel / 2f
            val shoulderDepth = if (withFaults && index >= 3) 50f else 10f
            pose(
                timestampMs = index * 100L,
                handTravel = travel,
                strideTravel = strideTravel,
                headDrop = headDrop,
                leftKneeX = collapsedKneeX,
                shoulderDepth = shoulderDepth,
                bodyWidthScale = bodyWidthScale
            )
        }
    }

    private fun pose(
        timestampMs: Long,
        handTravel: Float,
        strideTravel: Float,
        headDrop: Float,
        leftKneeX: Float,
        shoulderDepth: Float,
        bodyWidthScale: Float
    ): FramePose = FramePose(
        timestampMs = timestampMs,
        landmarks = listOf(
            point(0, 300f, 120f + headDrop),
            point(11, 300f - 50f * bodyWidthScale, 200f, -shoulderDepth),
            point(12, 300f + 50f * bodyWidthScale, 200f, shoulderDepth),
            point(13, 325f + handTravel * 0.45f, 235f),
            point(14, 345f + handTravel * 0.45f, 240f),
            point(15, 345f + handTravel, 250f),
            point(16, 365f + handTravel, 255f),
            point(23, 300f - 35f * bodyWidthScale, 360f, -10f),
            point(24, 300f + 35f * bodyWidthScale, 360f, 10f),
            point(25, leftKneeX, 440f),
            point(26, 350f, 440f),
            point(27, 260f - strideTravel, 520f),
            point(28, 355f, 520f)
        )
    )

    private fun point(type: Int, x: Float, y: Float, z: Float = 0f) =
        LandmarkPoint(type, x, y, inFrameLikelihood = 0.99f, z = z)
}