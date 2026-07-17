package com.example.sportsai.data

import com.example.sportsai.model.AnalysisResult
import com.example.sportsai.model.BiomechanicsMeasurement
import com.example.sportsai.model.CameraViewAssessment
import com.example.sportsai.model.EquipmentTrackingStatus
import com.example.sportsai.model.EquipmentTrackingSummary
import com.example.sportsai.model.FramePose
import com.example.sportsai.model.LandmarkPoint
import com.example.sportsai.model.MechanicsIssue
import com.example.sportsai.model.MechanicsIssueSeverity
import com.example.sportsai.model.SwingAnalysisSummary
import com.example.sportsai.model.SwingCameraView
import com.example.sportsai.model.SwingPhase
import com.example.sportsai.model.SwingPhaseSegment
import com.example.sportsai.model.TrackedObjectType
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Converts a Batter Lock timeline into explainable phases, measurements, and issue labels. */
internal class SwingMechanicsAnalyzer {

    fun analyze(result: AnalysisResult): SwingAnalysisSummary {
        val equipment = TrackedObjectType.entries.map { type ->
            val detections = result.trackedObjects.filter { it.type == type }
            EquipmentTrackingSummary(
                type = type,
                status = when {
                    result.objectDetectionFrames <= 0 -> EquipmentTrackingStatus.NOT_RUN
                    detections.isEmpty() -> EquipmentTrackingStatus.NOT_DETECTED
                    else -> EquipmentTrackingStatus.DETECTED
                },
                sampledFrames = result.objectDetectionFrames,
                detectedFrames = detections.map { it.timestampMs }.distinct().size,
                maxConfidence = detections.maxOfOrNull { it.confidence } ?: 0f
            )
        }
        val timeline = result.timeline
        val cameraView = classifyCameraView(timeline)
        val peakIndex = coordinatedBattingPeakIndex(timeline)
            ?: return SwingAnalysisSummary(cameraView = cameraView, equipment = equipment)
        if (timeline.size < MIN_TIMELINE_FRAMES) {
            return SwingAnalysisSummary(cameraView = cameraView, equipment = equipment)
        }

        val impactStartIndex = (peakIndex - 1).coerceAtLeast(1)
        val impactEndIndex = (peakIndex + 1).coerceAtMost(timeline.lastIndex)
        val detectedStrideStart = detectStrideStart(timeline, impactStartIndex)
        val strideStartIndex = (detectedStrideStart
            ?: (impactStartIndex * DEFAULT_STRIDE_FRACTION).roundToInt())
            .coerceIn(1, impactStartIndex)
        val baseConfidence = phaseConfidence(result)
        val phases = listOf(
            SwingPhaseSegment(
                phase = SwingPhase.STANCE,
                startMs = timeline.first().timestampMs,
                endMs = timeline[strideStartIndex].timestampMs,
                confidence = baseConfidence,
                evidence = "Stable setup before the first sustained ankle displacement."
            ),
            SwingPhaseSegment(
                phase = SwingPhase.STRIDE,
                startMs = timeline[strideStartIndex].timestampMs,
                endMs = timeline[impactStartIndex].timestampMs,
                confidence = if (detectedStrideStart != null) baseConfidence else baseConfidence * 0.7f,
                evidence = if (detectedStrideStart != null) {
                    "Stride begins at sustained ankle travel relative to the pelvis."
                } else {
                    "Stride boundary is estimated from the pre-peak motion window because ankle travel was incomplete."
                }
            ),
            SwingPhaseSegment(
                phase = SwingPhase.IMPACT_ZONE,
                startMs = timeline[impactStartIndex].timestampMs,
                endMs = timeline[impactEndIndex].timestampMs,
                confidence = baseConfidence,
                evidence = "Three-interval peak in coordinated two-hand motion; this does not confirm bat-ball contact."
            ),
            SwingPhaseSegment(
                phase = SwingPhase.FOLLOW_THROUGH,
                startMs = timeline[impactEndIndex].timestampMs,
                endMs = timeline.last().timestampMs,
                confidence = baseConfidence,
                evidence = "Tracked motion after the peak hand-speed window."
            )
        )

        val measurements = mutableListOf<BiomechanicsMeasurement>()
        val issues = mutableListOf<MechanicsIssue>()
        val motionScores = coordinatedBattingMotionScores(timeline)
        measurements += BiomechanicsMeasurement(
            key = "peak_hand_speed",
            label = "Peak coordinated hand speed",
            value = motionScores.getOrElse(peakIndex) { 0.0 },
            unit = "body-lengths/s",
            phase = SwingPhase.IMPACT_ZONE,
            evidence = "Torso-relative travel shared by both wrists; not measured bat speed."
        )

        peakExtension(timeline[peakIndex])?.let { extension ->
            measurements += BiomechanicsMeasurement(
                key = "arm_extension",
                label = "Peak arm extension",
                value = extension,
                unit = "deg",
                phase = SwingPhase.IMPACT_ZONE,
                evidence = "Largest visible elbow angle at the coordinated hand-speed peak."
            )
        }

        val stanceHead = timeline.subList(0, strideStartIndex + 1)
            .mapNotNull(::headOffsetFromPelvis).medianOrNull()
        val impactHead = timeline.subList(impactStartIndex, impactEndIndex + 1)
            .mapNotNull(::headOffsetFromPelvis).medianOrNull()
        if (stanceHead != null && impactHead != null) {
            val headDrop = impactHead - stanceHead
            measurements += BiomechanicsMeasurement(
                key = "head_vertical_change",
                label = "Head vertical change",
                value = headDrop,
                unit = "body-lengths",
                phase = SwingPhase.IMPACT_ZONE,
                evidence = "Head height relative to pelvis; positive values move downward in the image."
            )
            if (headDrop >= HEAD_DROP_THRESHOLD) {
                issues += MechanicsIssue(
                    code = "HEAD_DROP",
                    label = "Head drop",
                    severity = severity(headDrop, HEAD_DROP_PRIORITY_THRESHOLD),
                    confidence = issueConfidence(headDrop, HEAD_DROP_THRESHOLD),
                    phase = SwingPhase.IMPACT_ZONE,
                    evidence = "Head lowered ${format(headDrop)} body-lengths relative to the pelvis from stance to the impact zone.",
                    coachingCue = "Use controlled tee swings and keep the head centered over the pelvis through the hitting zone."
                )
            }
        }

        separationRange(timeline)?.let { range ->
            measurements += BiomechanicsMeasurement(
                key = "shoulder_hip_separation_range",
                label = "Shoulder-hip separation range",
                value = range,
                unit = "deg",
                evidence = "Relative model-depth orientation; omitted when depth is not reliable."
            )
        }

        val strideLeg = inferStrideLeg(timeline, strideStartIndex, impactStartIndex)
        if (cameraView.view == SwingCameraView.SIDE && strideLeg != null) {
            val impactKneeAngle = kneeAngle(timeline[impactEndIndex], strideLeg)
            val followKneeAngle = timeline.subList(impactEndIndex, timeline.size)
                .mapNotNull { kneeAngle(it, strideLeg) }
                .minOrNull()
            if (impactKneeAngle != null && followKneeAngle != null) {
                val angleLoss = impactKneeAngle - followKneeAngle
                measurements += BiomechanicsMeasurement(
                    key = "front_knee_angle_impact",
                    label = "Inferred front-knee angle",
                    value = impactKneeAngle,
                    unit = "deg",
                    phase = SwingPhase.IMPACT_ZONE,
                    evidence = "Stride-side leg inferred from ankle travel; this is a 2D projected joint angle, not a clinical measurement."
                )
                if (impactKneeAngle < FRONT_KNEE_MIN_ANGLE ||
                    angleLoss >= FRONT_KNEE_ANGLE_LOSS_THRESHOLD
                ) {
                    val magnitude = maxOf(
                        ((FRONT_KNEE_MIN_ANGLE - impactKneeAngle) / 20.0).coerceAtLeast(0.0),
                        angleLoss / FRONT_KNEE_ANGLE_LOSS_THRESHOLD
                    )
                    issues += MechanicsIssue(
                        code = "FRONT_KNEE_COLLAPSE",
                        label = "Front-knee collapse",
                        severity = if (impactKneeAngle < FRONT_KNEE_PRIORITY_ANGLE ||
                            angleLoss >= FRONT_KNEE_PRIORITY_ANGLE_LOSS
                        ) {
                            MechanicsIssueSeverity.PRIORITY
                        } else {
                            MechanicsIssueSeverity.WATCH
                        },
                        confidence = (0.6 + magnitude * 0.16).toFloat().coerceIn(0.58f, 0.9f),
                        phase = SwingPhase.IMPACT_ZONE,
                        evidence = "The 2D inferred front-knee angle was ${format(impactKneeAngle)} degrees and lost ${format(angleLoss)} degrees after the impact zone.",
                        coachingCue = "Land softly, then stabilize the front side while the hips and hands continue through."
                    )
                }
            }

            val trailLeg = oppositeKnee(strideLeg)
            kneeAngle(timeline[impactEndIndex], trailLeg)?.let { trailKneeAngle ->
                measurements += BiomechanicsMeasurement(
                    key = "trail_knee_angle_impact",
                    label = "Inferred trail-knee angle",
                    value = trailKneeAngle,
                    unit = "deg",
                    phase = SwingPhase.IMPACT_ZONE,
                    evidence = "Non-stride leg at the hand-speed peak; heel lift and force transfer are not inferred."
                )
                if (trailKneeAngle > TRAIL_KNEE_MAX_ANGLE) {
                    issues += MechanicsIssue(
                        code = "TRAIL_KNEE_DRIVE_LIMITED",
                        label = "Limited trail-knee drive",
                        severity = severity(trailKneeAngle, TRAIL_KNEE_PRIORITY_ANGLE),
                        confidence = issueConfidence(trailKneeAngle, TRAIL_KNEE_MAX_ANGLE),
                        phase = SwingPhase.IMPACT_ZONE,
                        evidence = "The inferred trail-knee angle remained ${format(trailKneeAngle)} degrees in the impact zone.",
                        coachingCue = "Use slow dry swings to let the rear knee turn under the hip without lunging the head forward."
                    )
                }
            }

            handLoadTravel(timeline, strideStartIndex, impactStartIndex, strideLeg)?.let { travel ->
                measurements += BiomechanicsMeasurement(
                    key = "hands_travel_with_stride",
                    label = "Hand travel with stride",
                    value = travel,
                    unit = "body-lengths",
                    phase = SwingPhase.STRIDE,
                    evidence = "Two-wrist center relative to the pelvis, signed positive in the inferred stride direction."
                )
                if (travel >= RUSHED_HANDS_THRESHOLD) {
                    issues += MechanicsIssue(
                        code = "RUSHED_HANDS",
                        label = "Hands drifting with the stride",
                        severity = severity(travel, RUSHED_HANDS_PRIORITY_THRESHOLD),
                        confidence = issueConfidence(travel, RUSHED_HANDS_THRESHOLD),
                        phase = SwingPhase.STRIDE,
                        evidence = "The hands moved ${format(travel)} body-lengths in the stride direction before the impact zone.",
                        coachingCue = "Rehearse a controlled stride while the hands stay gathered near the rear shoulder."
                    )
                }
            }
        }

        if (cameraView.view == SwingCameraView.REAR) {
            rearViewStability(timeline, strideStartIndex, impactStartIndex, impactEndIndex)?.let { stability ->
                measurements += BiomechanicsMeasurement(
                    key = "stance_spine_angle",
                    label = "Stance spine angle",
                    value = stability.stanceSpineAngle,
                    unit = "deg",
                    phase = SwingPhase.STANCE,
                    evidence = "Signed shoulder-center to pelvis-center tilt from image vertical."
                )
                measurements += BiomechanicsMeasurement(
                    key = "spine_angle_change",
                    label = "Spine-angle change",
                    value = stability.maxSpineChange,
                    unit = "deg",
                    phase = SwingPhase.IMPACT_ZONE,
                    evidence = "Largest 2D tilt change from the stance median through the impact-zone window."
                )
                measurements += BiomechanicsMeasurement(
                    key = "head_movement_range",
                    label = "Head movement range",
                    value = stability.headMovementRange,
                    unit = "body-lengths",
                    phase = SwingPhase.IMPACT_ZONE,
                    evidence = "Head travel relative to the pelvis from stride through the impact zone; this does not measure gaze."
                )
                if (stability.maxSpineChange >= SPINE_CHANGE_THRESHOLD) {
                    issues += MechanicsIssue(
                        code = "SPINE_ANGLE_LOSS",
                        label = "Spine angle changed through the turn",
                        severity = severity(stability.maxSpineChange, SPINE_CHANGE_PRIORITY_THRESHOLD),
                        confidence = issueConfidence(stability.maxSpineChange, SPINE_CHANGE_THRESHOLD),
                        phase = SwingPhase.IMPACT_ZONE,
                        evidence = "The 2D spine tilt changed up to ${format(stability.maxSpineChange)} degrees from the stance baseline.",
                        coachingCue = "Use mirror turns and keep the chest angle stable while the hips rotate underneath."
                    )
                }
                if (stability.headMovementRange >= HEAD_MOVEMENT_THRESHOLD) {
                    issues += MechanicsIssue(
                        code = "HEAD_INSTABILITY",
                        label = "Head movement around the rotation axis",
                        severity = severity(stability.headMovementRange, HEAD_MOVEMENT_PRIORITY_THRESHOLD),
                        confidence = issueConfidence(stability.headMovementRange, HEAD_MOVEMENT_THRESHOLD),
                        phase = SwingPhase.IMPACT_ZONE,
                        evidence = "The head moved through a ${format(stability.headMovementRange)} body-length range relative to the pelvis.",
                        coachingCue = "Make half-speed tee swings while keeping the head centered over the turning pelvis."
                    )
                }
            }
        }

        if (cameraView.view == SwingCameraView.REAR) earlyOpenEvidence(
            timeline,
            strideStartIndex,
            impactStartIndex
        )?.let { evidence ->
            measurements += BiomechanicsMeasurement(
                key = "shoulder_rotation_at_stride",
                label = "Shoulder rotation by stride end",
                value = evidence.shoulderChange,
                unit = "deg",
                phase = SwingPhase.STRIDE,
                evidence = "Change in relative model-depth shoulder orientation from stance."
            )
            if (evidence.shoulderChange >= EARLY_OPEN_SHOULDER_THRESHOLD &&
                evidence.shoulderLead >= EARLY_OPEN_LEAD_THRESHOLD
            ) {
                issues += MechanicsIssue(
                    code = "EARLY_OPEN",
                    label = "Early open",
                    severity = severity(evidence.shoulderLead, EARLY_OPEN_PRIORITY_THRESHOLD),
                    confidence = issueConfidence(evidence.shoulderLead, EARLY_OPEN_LEAD_THRESHOLD),
                    phase = SwingPhase.STRIDE,
                    evidence = "Shoulders rotated ${format(evidence.shoulderChange)} degrees by stride end, leading the hips by ${format(evidence.shoulderLead)} degrees.",
                    coachingCue = "Keep the chest closed during the stride and let the hips initiate the turn."
                )
            }
        }

        return SwingAnalysisSummary(
            cameraView = cameraView,
            phases = phases,
            measurements = measurements,
            issues = issues,
            equipment = equipment
        )
    }

    private fun classifyCameraView(timeline: List<FramePose>): CameraViewAssessment {
        val ratios = timeline.mapNotNull { it.projectedWidthRatio() }
        val minimumCoverage = (timeline.size * MIN_VIEW_COVERAGE).roundToInt().coerceAtLeast(3)
        if (ratios.size < minimumCoverage) return CameraViewAssessment(usableFrames = ratios.size)

        val ratio = ratios.medianOrNull() ?: return CameraViewAssessment(usableFrames = ratios.size)
        val view = when {
            ratio <= SIDE_VIEW_MAX_RATIO -> SwingCameraView.SIDE
            ratio >= REAR_VIEW_MIN_RATIO -> SwingCameraView.REAR
            else -> SwingCameraView.UNKNOWN
        }
        val confidence = when (view) {
            SwingCameraView.SIDE ->
                (0.62 + (SIDE_VIEW_MAX_RATIO - ratio) * 0.9).toFloat().coerceIn(0.62f, 0.94f)
            SwingCameraView.REAR ->
                (0.62 + (ratio - REAR_VIEW_MIN_RATIO) * 0.9).toFloat().coerceIn(0.62f, 0.94f)
            SwingCameraView.UNKNOWN -> 0f
        }
        val evidence = if (view == SwingCameraView.UNKNOWN) {
            "Projected shoulder/hip width ratio ${format(ratio)} fell inside the ambiguous camera-view range across ${ratios.size} reliable frames."
        } else {
            "${view.displayName} inferred from a median projected shoulder/hip width of ${format(ratio)} torso-lengths across ${ratios.size} reliable frames."
        }
        return CameraViewAssessment(view, confidence, ratios.size, evidence)
    }

    private fun FramePose.projectedWidthRatio(): Double? {
        val leftShoulder = point(LEFT_SHOULDER) ?: return null
        val rightShoulder = point(RIGHT_SHOULDER) ?: return null
        val leftHip = point(LEFT_HIP) ?: return null
        val rightHip = point(RIGHT_HIP) ?: return null
        val shoulders = shoulderCenter() ?: return null
        val pelvis = pelvisCenter() ?: return null
        val torsoLength = hypot(pelvis.first - shoulders.first, pelvis.second - shoulders.second)
            .takeIf { it >= MIN_BODY_SCALE } ?: return null
        val shoulderWidth = hypot(
            (rightShoulder.x - leftShoulder.x).toDouble(),
            (rightShoulder.y - leftShoulder.y).toDouble()
        )
        val hipWidth = hypot(
            (rightHip.x - leftHip.x).toDouble(),
            (rightHip.y - leftHip.y).toDouble()
        )
        return ((shoulderWidth + hipWidth) / 2.0) / torsoLength
    }

    private fun detectStrideStart(timeline: List<FramePose>, impactStartIndex: Int): Int? {
        val first = timeline.first()
        val initialLeft = ankleOffset(first, LEFT_ANKLE)
        val initialRight = ankleOffset(first, RIGHT_ANKLE)
        if (initialLeft == null && initialRight == null) return null
        return (1..impactStartIndex).firstOrNull { index ->
            val frame = timeline[index]
            val leftTravel = travel(initialLeft, ankleOffset(frame, LEFT_ANKLE))
            val rightTravel = travel(initialRight, ankleOffset(frame, RIGHT_ANKLE))
            maxOf(leftTravel, rightTravel) >= STRIDE_TRAVEL_THRESHOLD
        }
    }

    private fun inferStrideLeg(
        timeline: List<FramePose>,
        strideStartIndex: Int,
        impactStartIndex: Int
    ): Int? {
        val start = timeline[strideStartIndex]
        val end = timeline[impactStartIndex]
        val leftTravel = travel(ankleOffset(start, LEFT_ANKLE), ankleOffset(end, LEFT_ANKLE))
        val rightTravel = travel(ankleOffset(start, RIGHT_ANKLE), ankleOffset(end, RIGHT_ANKLE))
        val maximum = maxOf(leftTravel, rightTravel)
        if (maximum < MIN_STRIDE_LEG_TRAVEL) return null
        return if (leftTravel >= rightTravel) LEFT_KNEE else RIGHT_KNEE
    }

    private fun ankleOffset(frame: FramePose, ankleType: Int): Pair<Double, Double>? {
        val ankle = frame.point(ankleType) ?: return null
        val pelvis = frame.pelvisCenter() ?: return null
        val scale = frame.bodyScale() ?: return null
        return (ankle.x - pelvis.first) / scale to (ankle.y - pelvis.second) / scale
    }

    private fun travel(first: Pair<Double, Double>?, second: Pair<Double, Double>?): Double {
        if (first == null || second == null) return 0.0
        return hypot(second.first - first.first, second.second - first.second)
    }

    private fun headOffsetFromPelvis(frame: FramePose): Double? {
        val pelvis = frame.pelvisCenter() ?: return null
        val scale = frame.bodyScale() ?: return null
        val head = frame.point(NOSE) ?: frame.shoulderCenter()?.let { center ->
            LandmarkPoint(NOSE, center.first.toFloat(), center.second.toFloat(), 1f)
        } ?: return null
        return (head.y - pelvis.second) / scale
    }

    private fun peakExtension(frame: FramePose): Double? = listOfNotNull(
        jointAngle(frame, LEFT_SHOULDER, LEFT_ELBOW, LEFT_WRIST),
        jointAngle(frame, RIGHT_SHOULDER, RIGHT_ELBOW, RIGHT_WRIST)
    ).maxOrNull()

    private fun kneeAngle(frame: FramePose, kneeType: Int): Double? =
        if (kneeType == LEFT_KNEE) {
            jointAngle(frame, LEFT_HIP, LEFT_KNEE, LEFT_ANKLE)
        } else {
            jointAngle(frame, RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE)
        }

    private fun oppositeKnee(kneeType: Int): Int =
        if (kneeType == LEFT_KNEE) RIGHT_KNEE else LEFT_KNEE

    private fun handLoadTravel(
        timeline: List<FramePose>,
        strideStartIndex: Int,
        impactStartIndex: Int,
        strideKnee: Int
    ): Double? {
        val ankleType = if (strideKnee == LEFT_KNEE) LEFT_ANKLE else RIGHT_ANKLE
        val strideStart = ankleOffset(timeline.first(), ankleType) ?: return null
        val strideEnd = ankleOffset(timeline[impactStartIndex], ankleType) ?: return null
        val strideDirection = strideEnd.first - strideStart.first
        if (abs(strideDirection) < MIN_STRIDE_DIRECTION_TRAVEL) return null
        val stanceHands = timeline.subList(0, strideStartIndex + 1)
            .mapNotNull { it.wristCenterOffset()?.first }
            .medianOrNull() ?: return null
        val strideEndHands = timeline[impactStartIndex].wristCenterOffset()?.first ?: return null
        return (strideEndHands - stanceHands) * if (strideDirection >= 0.0) 1.0 else -1.0
    }

    private data class RearViewStability(
        val stanceSpineAngle: Double,
        val maxSpineChange: Double,
        val headMovementRange: Double
    )

    private fun rearViewStability(
        timeline: List<FramePose>,
        strideStartIndex: Int,
        impactStartIndex: Int,
        impactEndIndex: Int
    ): RearViewStability? {
        val stanceAngle = timeline.subList(0, strideStartIndex + 1)
            .mapNotNull { it.spineAngle() }
            .medianOrNull() ?: return null
        val impactAngles = timeline.subList(impactStartIndex, impactEndIndex + 1)
            .mapNotNull { it.spineAngle() }
        if (impactAngles.isEmpty()) return null
        val maxChange = impactAngles.maxOf { abs(it - stanceAngle) }
        val headOffsets = timeline.subList(strideStartIndex, impactEndIndex + 1)
            .mapNotNull { it.headOffset2d() }
        if (headOffsets.size < 2) return null
        val headRange = hypot(
            headOffsets.maxOf { it.first } - headOffsets.minOf { it.first },
            headOffsets.maxOf { it.second } - headOffsets.minOf { it.second }
        )
        return RearViewStability(stanceAngle, maxChange, headRange)
    }

    private fun jointAngle(frame: FramePose, first: Int, vertex: Int, third: Int): Double? {
        val a = frame.point(first) ?: return null
        val b = frame.point(vertex) ?: return null
        val c = frame.point(third) ?: return null
        val radians = atan2((c.y - b.y).toDouble(), (c.x - b.x).toDouble()) -
            atan2((a.y - b.y).toDouble(), (a.x - b.x).toDouble())
        var degrees = abs(Math.toDegrees(radians)) % 360.0
        if (degrees > 180.0) degrees = 360.0 - degrees
        return degrees
    }

    private fun separationRange(timeline: List<FramePose>): Double? {
        val values = timeline.mapNotNull { frame ->
            val shoulders = frame.depthOrientation(LEFT_SHOULDER, RIGHT_SHOULDER) ?: return@mapNotNull null
            val hips = frame.depthOrientation(LEFT_HIP, RIGHT_HIP) ?: return@mapNotNull null
            signedAngleDifference(shoulders, hips)
        }
        if (values.size < (timeline.size * MIN_DEPTH_COVERAGE).roundToInt().coerceAtLeast(3)) return null
        return values.max() - values.min()
    }

    private data class EarlyOpenEvidence(val shoulderChange: Double, val shoulderLead: Double)

    private fun earlyOpenEvidence(
        timeline: List<FramePose>,
        strideStartIndex: Int,
        impactStartIndex: Int
    ): EarlyOpenEvidence? {
        val stance = timeline.subList(0, strideStartIndex + 1)
        val stanceShoulder = stance.mapNotNull {
            it.depthOrientation(LEFT_SHOULDER, RIGHT_SHOULDER)
        }.circularMedianOrNull() ?: return null
        val stanceHip = stance.mapNotNull {
            it.depthOrientation(LEFT_HIP, RIGHT_HIP)
        }.circularMedianOrNull() ?: return null
        val strideEnd = timeline[impactStartIndex]
        val endShoulder = strideEnd.depthOrientation(LEFT_SHOULDER, RIGHT_SHOULDER) ?: return null
        val endHip = strideEnd.depthOrientation(LEFT_HIP, RIGHT_HIP) ?: return null
        val shoulderChange = abs(signedAngleDifference(endShoulder, stanceShoulder))
        val hipChange = abs(signedAngleDifference(endHip, stanceHip))
        return EarlyOpenEvidence(shoulderChange, shoulderChange - hipChange)
    }

    private fun FramePose.depthOrientation(leftType: Int, rightType: Int): Double? {
        val left = point(leftType) ?: return null
        val right = point(rightType) ?: return null
        val width = hypot((right.x - left.x).toDouble(), (right.y - left.y).toDouble())
        if (width < MIN_BODY_SCALE || abs(right.z - left.z) < width * MIN_DEPTH_RATIO) return null
        return Math.toDegrees(atan2((right.z - left.z).toDouble(), (right.x - left.x).toDouble()))
    }

    private fun FramePose.point(type: Int): LandmarkPoint? =
        landmarks.firstOrNull { it.type == type && it.inFrameLikelihood >= MIN_LIKELIHOOD }

    private fun FramePose.shoulderCenter(): Pair<Double, Double>? {
        val left = point(LEFT_SHOULDER) ?: return null
        val right = point(RIGHT_SHOULDER) ?: return null
        return (left.x + right.x) / 2.0 to (left.y + right.y) / 2.0
    }

    private fun FramePose.pelvisCenter(): Pair<Double, Double>? {
        val left = point(LEFT_HIP) ?: return null
        val right = point(RIGHT_HIP) ?: return null
        return (left.x + right.x) / 2.0 to (left.y + right.y) / 2.0
    }

    private fun FramePose.wristCenterOffset(): Pair<Double, Double>? {
        val left = point(LEFT_WRIST) ?: return null
        val right = point(RIGHT_WRIST) ?: return null
        val pelvis = pelvisCenter() ?: return null
        val scale = bodyScale() ?: return null
        return ((left.x + right.x) / 2.0 - pelvis.first) / scale to
            ((left.y + right.y) / 2.0 - pelvis.second) / scale
    }

    private fun FramePose.headOffset2d(): Pair<Double, Double>? {
        val head = point(NOSE) ?: return null
        val pelvis = pelvisCenter() ?: return null
        val scale = bodyScale() ?: return null
        return (head.x - pelvis.first) / scale to (head.y - pelvis.second) / scale
    }

    private fun FramePose.spineAngle(): Double? {
        val shoulders = shoulderCenter() ?: return null
        val pelvis = pelvisCenter() ?: return null
        return Math.toDegrees(atan2(shoulders.first - pelvis.first, pelvis.second - shoulders.second))
    }

    private fun FramePose.bodyScale(): Double? {
        val shoulders = shoulderCenter() ?: return null
        val pelvis = pelvisCenter() ?: return null
        val leftShoulder = point(LEFT_SHOULDER) ?: return null
        val rightShoulder = point(RIGHT_SHOULDER) ?: return null
        return maxOf(
            hypot(rightShoulder.x - leftShoulder.x, rightShoulder.y - leftShoulder.y).toDouble(),
            hypot(pelvis.first - shoulders.first, pelvis.second - shoulders.second)
        ).takeIf { it >= MIN_BODY_SCALE }
    }

    private fun phaseConfidence(result: AnalysisResult): Float {
        val tracking = result.athleteTracking
        val quality = if (tracking.matchScore > 0f) tracking.matchScore else result.detectionRate
        val completeness = if (tracking.landmarkCompleteness > 0f) {
            tracking.landmarkCompleteness
        } else {
            result.detectionRate
        }
        return (quality * 0.55f + completeness * 0.45f).coerceIn(0.35f, 0.98f)
    }

    private fun severity(value: Double, priorityThreshold: Double): MechanicsIssueSeverity =
        if (value >= priorityThreshold) MechanicsIssueSeverity.PRIORITY else MechanicsIssueSeverity.WATCH

    private fun issueConfidence(value: Double, threshold: Double): Float =
        (0.58 + ((value - threshold) / threshold).coerceAtLeast(0.0) * 0.22)
            .toFloat()
            .coerceIn(0.55f, 0.92f)

    private fun signedAngleDifference(first: Double, second: Double): Double {
        var difference = (first - second) % 360.0
        if (difference > 180.0) difference -= 360.0
        if (difference < -180.0) difference += 360.0
        return difference
    }

    private fun List<Double>.medianOrNull(): Double? =
        takeIf { it.isNotEmpty() }?.sorted()?.let { it[it.size / 2] }

    private fun List<Double>.circularMedianOrNull(): Double? {
        if (isEmpty()) return null
        val anchor = first()
        return map { anchor + signedAngleDifference(it, anchor) }.sorted()[size / 2]
    }

    private fun format(value: Double): String = String.format(java.util.Locale.US, "%.1f", value)

    private companion object {
        const val NOSE = 0
        const val LEFT_SHOULDER = 11
        const val RIGHT_SHOULDER = 12
        const val LEFT_ELBOW = 13
        const val RIGHT_ELBOW = 14
        const val LEFT_WRIST = 15
        const val RIGHT_WRIST = 16
        const val LEFT_HIP = 23
        const val RIGHT_HIP = 24
        const val LEFT_KNEE = 25
        const val RIGHT_KNEE = 26
        const val LEFT_ANKLE = 27
        const val RIGHT_ANKLE = 28
        const val MIN_TIMELINE_FRAMES = 5
        const val MIN_LIKELIHOOD = 0.5f
        const val MIN_BODY_SCALE = 8.0
        const val MIN_DEPTH_RATIO = 0.04
        const val MIN_DEPTH_COVERAGE = 0.35
        const val MIN_VIEW_COVERAGE = 0.45
        const val SIDE_VIEW_MAX_RATIO = 0.58
        const val REAR_VIEW_MIN_RATIO = 0.82
        const val DEFAULT_STRIDE_FRACTION = 0.45
        const val STRIDE_TRAVEL_THRESHOLD = 0.08
        const val MIN_STRIDE_LEG_TRAVEL = 0.05
        const val HEAD_DROP_THRESHOLD = 0.16
        const val HEAD_DROP_PRIORITY_THRESHOLD = 0.25
        const val MIN_STRIDE_DIRECTION_TRAVEL = 0.05
        const val FRONT_KNEE_MIN_ANGLE = 145.0
        const val FRONT_KNEE_PRIORITY_ANGLE = 130.0
        const val FRONT_KNEE_ANGLE_LOSS_THRESHOLD = 16.0
        const val FRONT_KNEE_PRIORITY_ANGLE_LOSS = 24.0
        const val TRAIL_KNEE_MAX_ANGLE = 145.0
        const val TRAIL_KNEE_PRIORITY_ANGLE = 160.0
        const val RUSHED_HANDS_THRESHOLD = 0.12
        const val RUSHED_HANDS_PRIORITY_THRESHOLD = 0.2
        const val SPINE_CHANGE_THRESHOLD = 8.0
        const val SPINE_CHANGE_PRIORITY_THRESHOLD = 14.0
        const val HEAD_MOVEMENT_THRESHOLD = 0.18
        const val HEAD_MOVEMENT_PRIORITY_THRESHOLD = 0.28
        const val EARLY_OPEN_SHOULDER_THRESHOLD = 14.0
        const val EARLY_OPEN_LEAD_THRESHOLD = 8.0
        const val EARLY_OPEN_PRIORITY_THRESHOLD = 16.0
    }
}