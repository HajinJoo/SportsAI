package com.example.sportsai.data

import com.example.sportsai.model.AnalysisResult
import com.example.sportsai.model.FramePose
import com.example.sportsai.model.HighlightClip
import com.example.sportsai.model.LandmarkPoint
import com.example.sportsai.model.Sport
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

/** Selects the strongest complete sports action from the pose timeline. */
class HighlightExtractor {

    fun extract(result: AnalysisResult, sport: Sport, maxClips: Int = 1): List<HighlightClip> {
        val timeline = result.timeline
        if (maxClips <= 0 || timeline.size < MIN_FRAMES) return emptyList()

        val side = dominantArm(timeline)
        val rawScores = if (sport == Sport.BASEBALL_BAT) {
            coordinatedBattingMotionScores(timeline)
        } else {
            timeline.indices.map { index ->
                if (index == 0) 0.0 else {
                actionScore(
                    sport = sport,
                    previous = timeline[index - 1],
                    current = timeline[index],
                    side = side
                )
                }
            }
        }
        // Batting uses the strongest three-interval median so a single wrist jitter or camera move
        // cannot become the highlight. Other sports retain the short moving-average filter.
        val scores = if (sport == Sport.BASEBALL_BAT) {
            rawScores.indices.map { index ->
                if (index < 2 || index >= rawScores.lastIndex) 0.0 else {
                    rawScores.subList(index - 1, index + 2).sorted()[1]
                }
            }
        } else {
            rawScores.indices.map { index ->
                val from = (index - 1).coerceAtLeast(0)
                val to = (index + 1).coerceAtMost(rawScores.lastIndex)
                rawScores.subList(from, to + 1).average()
            }
        }
        val peakIndex = if (sport == Sport.BASEBALL_BAT) {
            coordinatedBattingPeakIndex(timeline)
        } else {
            val usableIndices = 1 until scores.size
            usableIndices.maxByOrNull(scores::get)
        } ?: return emptyList()
        val peakScore = scores[peakIndex]
        if (!peakScore.isFinite() || peakScore <= 0.0) return emptyList()

        val (preRollMs, postRollMs) = when (sport) {
            Sport.BASEBALL_PITCH -> 1_000L to 850L
            Sport.BASEBALL_BAT -> 900L to 750L
            Sport.BASKETBALL_SHOT -> 1_050L to 950L
        }
        val centerMs = timeline[peakIndex].timestampMs
        val videoEndMs = max(result.durationMs, timeline.last().timestampMs)
        val startMs = (centerMs - preRollMs).coerceAtLeast(0L)
        val endMs = (centerMs + postRollMs).coerceAtMost(videoEndMs)
        if (endMs - startMs < MIN_CLIP_MS) return emptyList()

        val nonZero = scores.drop(1).filter { it > 0.0 }.sorted()
        val median = nonZero.getOrElse(nonZero.size / 2) { peakScore }
        val prominence = (peakScore / median.coerceAtLeast(0.001) - 1.0).coerceIn(0.0, 2.0)
        val confidence = frameConfidence(timeline[peakIndex]).toDouble()
        val quality = (65.0 + prominence * 10.0 + confidence * 15.0).toInt().coerceIn(0, 100)

        return listOf(
            HighlightClip(
                id = System.currentTimeMillis(),
                label = when (sport) {
                    Sport.BASEBALL_PITCH -> "Best pitch · release"
                    Sport.BASEBALL_BAT -> "Best swing · peak hand speed"
                    Sport.BASKETBALL_SHOT -> "Best shot · release"
                },
                startMs = startMs,
                endMs = endMs,
                score = quality
            )
        )
    }

    private enum class Side { LEFT, RIGHT }

    private fun dominantArm(timeline: List<FramePose>): Side {
        fun travel(side: Side): Double = timeline.zipWithNext().sumOf { (previous, current) ->
            val scale = bodyScale(previous, current)
            val before = previous.point(side.wrist) ?: return@sumOf 0.0
            val after = current.point(side.wrist) ?: return@sumOf 0.0
            distance(before, after) / scale
        }
        return if (travel(Side.RIGHT) >= travel(Side.LEFT)) Side.RIGHT else Side.LEFT
    }

    private fun actionScore(
        sport: Sport,
        previous: FramePose,
        current: FramePose,
        side: Side
    ): Double {
        val elapsedSeconds = ((current.timestampMs - previous.timestampMs) / 1_000.0)
            .coerceIn(0.05, 1.0)
        val scale = bodyScale(previous, current)
        val wristSpeed = pointSpeed(previous, current, side.wrist, scale, elapsedSeconds)
        val otherWristSpeed = pointSpeed(previous, current, side.opposite.wrist, scale, elapsedSeconds)
        val handsSpeed = (wristSpeed + otherWristSpeed) / 2.0
        val upwardWristSpeed = upwardSpeed(previous, current, side.wrist, scale, elapsedSeconds)
        val elbowExtension = extensionRate(
            previous,
            current,
            side.shoulder,
            side.elbow,
            side.wrist,
            elapsedSeconds,
            positiveOnly = sport != Sport.BASEBALL_BAT
        )
        val shoulderTurn = lineTurnRate(previous, current, LEFT_SHOULDER, RIGHT_SHOULDER, elapsedSeconds)
        val hipTurn = lineTurnRate(previous, current, LEFT_HIP, RIGHT_HIP, elapsedSeconds)
        val torsoSeparation = separationChangeRate(previous, current, elapsedSeconds)
        val kneeDrive = listOf(
            extensionRate(previous, current, LEFT_HIP, LEFT_KNEE, LEFT_ANKLE, elapsedSeconds, true),
            extensionRate(previous, current, RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE, elapsedSeconds, true)
        ).average()
        val wristHeight = wristHeight(current, side)
        val confidenceMultiplier = 0.55 + frameConfidence(current) * 0.45
        val movementEvidence = wristSpeed + otherWristSpeed + elbowExtension + shoulderTurn +
            hipTurn + torsoSeparation + kneeDrive + upwardWristSpeed
        if (movementEvidence < MIN_ACTION_EVIDENCE) return 0.0

        val score = when (sport) {
            Sport.BASEBALL_PITCH ->
                wristSpeed * 2.5 + elbowExtension * 2.0 + shoulderTurn + torsoSeparation * 0.8 +
                    upwardWristSpeed * 0.35 + wristHeight * 0.5

            Sport.BASEBALL_BAT ->
                handsSpeed * 2.4 + max(wristSpeed, otherWristSpeed) * 0.7 +
                    torsoSeparation * 2.0 + shoulderTurn + hipTurn * 0.8 + elbowExtension * 0.5

            Sport.BASKETBALL_SHOT ->
                upwardWristSpeed * 2.2 + wristSpeed * 0.8 + elbowExtension * 2.0 +
                    kneeDrive * 1.2 + wristHeight * 1.5
        }
        return score.coerceAtLeast(0.0) * confidenceMultiplier
    }

    private fun FramePose.point(type: Int): LandmarkPoint? = landmarks.firstOrNull {
        it.type == type && it.inFrameLikelihood >= MIN_LIKELIHOOD
    }

    private fun bodyScale(previous: FramePose, current: FramePose): Double {
        fun shoulderWidth(frame: FramePose): Double? {
            val left = frame.point(LEFT_SHOULDER) ?: return null
            val right = frame.point(RIGHT_SHOULDER) ?: return null
            return distance(left, right)
        }
        return listOfNotNull(shoulderWidth(previous), shoulderWidth(current))
            .averageOrNull()
            ?.coerceAtLeast(MIN_BODY_SCALE)
            ?: MIN_BODY_SCALE
    }

    private fun pointSpeed(
        previous: FramePose,
        current: FramePose,
        type: Int,
        scale: Double,
        elapsedSeconds: Double
    ): Double {
        val before = previous.point(type) ?: return 0.0
        val after = current.point(type) ?: return 0.0
        return distance(before, after) / scale / elapsedSeconds
    }

    private fun upwardSpeed(
        previous: FramePose,
        current: FramePose,
        type: Int,
        scale: Double,
        elapsedSeconds: Double
    ): Double {
        val before = previous.point(type) ?: return 0.0
        val after = current.point(type) ?: return 0.0
        return ((before.y - after.y) / scale / elapsedSeconds).coerceAtLeast(0.0)
    }

    private fun extensionRate(
        previous: FramePose,
        current: FramePose,
        first: Int,
        joint: Int,
        last: Int,
        elapsedSeconds: Double,
        positiveOnly: Boolean
    ): Double {
        val before = jointAngle(previous, first, joint, last) ?: return 0.0
        val after = jointAngle(current, first, joint, last) ?: return 0.0
        val change = if (positiveOnly) (after - before).coerceAtLeast(0.0) else abs(after - before)
        return change / 180.0 / elapsedSeconds
    }

    private fun jointAngle(frame: FramePose, first: Int, joint: Int, last: Int): Double? {
        val a = frame.point(first) ?: return null
        val b = frame.point(joint) ?: return null
        val c = frame.point(last) ?: return null
        val abX = (a.x - b.x).toDouble()
        val abY = (a.y - b.y).toDouble()
        val cbX = (c.x - b.x).toDouble()
        val cbY = (c.y - b.y).toDouble()
        val denominator = hypot(abX, abY) * hypot(cbX, cbY)
        if (denominator < 0.001) return null
        return Math.toDegrees(acos(((abX * cbX + abY * cbY) / denominator).coerceIn(-1.0, 1.0)))
    }

    private fun lineTurnRate(
        previous: FramePose,
        current: FramePose,
        leftType: Int,
        rightType: Int,
        elapsedSeconds: Double
    ): Double {
        val before = lineAngle(previous, leftType, rightType) ?: return 0.0
        val after = lineAngle(current, leftType, rightType) ?: return 0.0
        return angleDifference(before, after) / 180.0 / elapsedSeconds
    }

    private fun separationChangeRate(
        previous: FramePose,
        current: FramePose,
        elapsedSeconds: Double
    ): Double {
        fun separation(frame: FramePose): Double? {
            val shoulders = lineAngle(frame, LEFT_SHOULDER, RIGHT_SHOULDER) ?: return null
            val hips = lineAngle(frame, LEFT_HIP, RIGHT_HIP) ?: return null
            return angleDifference(shoulders, hips)
        }
        val before = separation(previous) ?: return 0.0
        val after = separation(current) ?: return 0.0
        return abs(after - before) / 180.0 / elapsedSeconds
    }

    private fun lineAngle(frame: FramePose, leftType: Int, rightType: Int): Double? {
        val left = frame.point(leftType) ?: return null
        val right = frame.point(rightType) ?: return null
        return Math.toDegrees(atan2((right.y - left.y).toDouble(), (right.x - left.x).toDouble()))
    }

    private fun wristHeight(frame: FramePose, side: Side): Double {
        val wrist = frame.point(side.wrist) ?: return 0.0
        val shoulder = frame.point(side.shoulder) ?: return 0.0
        val scale = bodyScale(frame, frame)
        return ((shoulder.y - wrist.y) / scale).coerceAtLeast(0.0)
    }

    private fun frameConfidence(frame: FramePose): Float {
        val tracked = frame.landmarks.filter { it.inFrameLikelihood >= MIN_LIKELIHOOD }
        return if (tracked.isEmpty()) 0f else tracked.map { it.inFrameLikelihood }.average().toFloat()
    }

    private fun distance(a: LandmarkPoint, b: LandmarkPoint): Double =
        hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())

    private fun angleDifference(first: Double, second: Double): Double {
        val difference = abs(first - second) % 360.0
        return minOf(difference, 360.0 - difference)
    }

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

    private val Side.wrist: Int get() = if (this == Side.LEFT) LEFT_WRIST else RIGHT_WRIST
    private val Side.elbow: Int get() = if (this == Side.LEFT) LEFT_ELBOW else RIGHT_ELBOW
    private val Side.shoulder: Int get() = if (this == Side.LEFT) LEFT_SHOULDER else RIGHT_SHOULDER
    private val Side.opposite: Side get() = if (this == Side.LEFT) Side.RIGHT else Side.LEFT

    private companion object {
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
        const val MIN_FRAMES = 4
        const val MIN_CLIP_MS = 500L
        const val MIN_LIKELIHOOD = 0.5f
        const val MIN_BODY_SCALE = 24.0
        const val MIN_ACTION_EVIDENCE = 0.04
    }
}
