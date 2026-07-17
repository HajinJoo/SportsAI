package com.example.sportsai.data

import com.example.sportsai.model.FramePose
import com.example.sportsai.model.LandmarkPoint
import kotlin.math.hypot
import kotlin.math.sqrt

/** One torso-relative, coordinated two-hand motion interval. */
internal data class BattingMotionSample(
    val frameIndex: Int,
    val fromMs: Long,
    val toMs: Long,
    val score: Double,
    val rawScore: Double,
    val transverseEvidence: Double,
    val directionX: Double,
    val directionY: Double,
    val directionZ: Double
)

/**
 * Scores batting-hand motion in a torso-aligned coordinate system. Translation, zoom, and
 * in-plane camera roll are removed before wrist travel is measured, and opposite hand motion is
 * suppressed. This keeps camera movement and isolated pose jitter from becoming a swing peak.
 */
internal fun coordinatedBattingMotion(
    before: FramePose,
    after: FramePose,
    minimumSwingPlaneWeight: Double = MIN_SWING_PLANE_WEIGHT
): BattingMotionSample? {
    val elapsedMs = after.timestampMs - before.timestampMs
    if (elapsedMs !in 1..MAX_BATCH_MOTION_INTERVAL_MS) return null
    val beforeBasis = before.torsoBasis() ?: return null
    val afterBasis = after.torsoBasis() ?: return null
    val beforeLeft = before.normalizedPoint(LEFT_WRIST, beforeBasis) ?: return null
    val beforeRight = before.normalizedPoint(RIGHT_WRIST, beforeBasis) ?: return null
    val afterLeft = after.normalizedPoint(LEFT_WRIST, afterBasis) ?: return null
    val afterRight = after.normalizedPoint(RIGHT_WRIST, afterBasis) ?: return null
    val elapsedSeconds = elapsedMs / 1_000.0
    val leftDx = afterLeft.first - beforeLeft.first
    val leftDy = afterLeft.second - beforeLeft.second
    val leftDz = (afterLeft.third - beforeLeft.third) * DEPTH_MOTION_WEIGHT
    val rightDx = afterRight.first - beforeRight.first
    val rightDy = afterRight.second - beforeRight.second
    val rightDz = (afterRight.third - beforeRight.third) * DEPTH_MOTION_WEIGHT
    if (!listOf(leftDx, leftDy, leftDz, rightDx, rightDy, rightDz).all(Double::isFinite)) {
        return null
    }
    val leftTransverse = hypot(leftDx, leftDz)
    val rightTransverse = hypot(rightDx, rightDz)
    val leftDistance = hypot(leftTransverse, leftDy)
    val rightDistance = hypot(rightTransverse, rightDy)
    val leftTravel = leftDistance / elapsedSeconds
    val rightTravel = rightDistance / elapsedSeconds
    val denominator = leftDistance * rightDistance
    val directionAgreement = if (denominator < MIN_VECTOR_MAGNITUDE) 0.0 else {
        ((leftDx * rightDx + leftDy * rightDy + leftDz * rightDz) / denominator)
            .coerceIn(-1.0, 1.0)
    }
    val grip = listOf(before.gripScore(beforeBasis), after.gripScore(afterBasis))
        .filterNotNull()
        .averageOr(0.0)
    val gripWeight = MIN_GRIP_WEIGHT + (1.0 - MIN_GRIP_WEIGHT) * grip
    val transverseEvidence = if (denominator < MIN_VECTOR_MAGNITUDE) 0.0 else {
        minOf(leftTransverse / leftDistance, rightTransverse / rightDistance)
            .coerceIn(0.0, 1.0)
    }
    val rawScore = minOf(leftTravel, rightTravel) * ((directionAgreement + 1.0) / 2.0) *
        gripWeight
    return BattingMotionSample(
        frameIndex = -1,
        fromMs = before.timestampMs,
        toMs = after.timestampMs,
        score = rawScore * (minimumSwingPlaneWeight.coerceIn(0.0, 1.0) +
            (1.0 - minimumSwingPlaneWeight.coerceIn(0.0, 1.0)) * transverseEvidence),
        rawScore = rawScore,
        transverseEvidence = transverseEvidence,
        directionX = (leftDx + rightDx) / 2.0,
        directionY = (leftDy + rightDy) / 2.0,
        directionZ = (leftDz + rightDz) / 2.0
    )
}

internal fun coordinatedBattingMotionScores(timeline: List<FramePose>): List<Double> {
    val scores = MutableList(timeline.size) { 0.0 }
    timeline.zipWithNext().forEachIndexed { index, (before, after) ->
        scores[index + 1] = coordinatedBattingMotion(before, after)?.score ?: 0.0
    }
    return scores
}

/** Returns the middle of the strongest three-interval burst, never a single-frame spike. */
internal fun coordinatedBattingPeakIndex(timeline: List<FramePose>): Int? {
    if (timeline.size < MIN_PEAK_FRAMES) return null
    val samples = timeline.zipWithNext().mapIndexed { index, (before, after) ->
        coordinatedBattingMotion(before, after)?.copy(frameIndex = index + 1)
    }
    var run = mutableListOf<BattingMotionSample>()
    var bestIndex: Int? = null
    var bestMedian = 0.0

    fun scoreRun() {
        if (run.size >= MIN_PEAK_INTERVALS) {
            run.windowed(MIN_PEAK_INTERVALS).forEach { window ->
                val median = window.map { it.score }.sorted()[window.size / 2]
                val pathLength = window.sumOf { it.directionMagnitude() }
                val consistency = if (pathLength < MIN_VECTOR_MAGNITUDE) 0.0 else {
                    vectorMagnitude(
                        window.sumOf { it.directionX },
                        window.sumOf { it.directionY },
                        window.sumOf { it.directionZ }
                    ) / pathLength
                }.coerceIn(0.0, 1.0)
                val windowScore = median * consistency
                if (windowScore > bestMedian) {
                    bestMedian = windowScore
                    bestIndex = window[window.size / 2].frameIndex
                }
            }
        }
        run = mutableListOf()
    }

    samples.forEach { sample ->
        val contiguous = sample != null &&
            (run.isEmpty() || run.last().toMs == sample.fromMs)
        if (!contiguous) scoreRun()
        if (sample != null) run += sample
    }
    scoreRun()
    return bestIndex?.takeIf { bestMedian >= MIN_PEAK_MOTION }
}

private data class TorsoBasis(
    val centerX: Double,
    val centerY: Double,
    val centerZ: Double,
    val axisX: Double,
    val axisY: Double,
    val scale: Double
)

private fun FramePose.torsoBasis(): TorsoBasis? {
    val leftShoulder = point(LEFT_SHOULDER) ?: return null
    val rightShoulder = point(RIGHT_SHOULDER) ?: return null
    val torso = listOf(LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_HIP, RIGHT_HIP)
        .mapNotNull(::point)
    if (torso.size < 3) return null
    val shoulderDx = (rightShoulder.x - leftShoulder.x).toDouble()
    val shoulderDy = (rightShoulder.y - leftShoulder.y).toDouble()
    val shoulderWidth = hypot(shoulderDx, shoulderDy)
    if (shoulderWidth < MIN_BODY_SCALE) return null
    val hipPoints = listOfNotNull(point(LEFT_HIP), point(RIGHT_HIP))
    val shoulderCenterX = (leftShoulder.x + rightShoulder.x) / 2.0
    val shoulderCenterY = (leftShoulder.y + rightShoulder.y) / 2.0
    val torsoLength = if (hipPoints.isNotEmpty()) {
        val hipX = hipPoints.map { it.x }.average()
        val hipY = hipPoints.map { it.y }.average()
        hypot(hipX - shoulderCenterX, hipY - shoulderCenterY)
    } else 0.0
    return TorsoBasis(
        centerX = torso.map { it.x }.average(),
        centerY = torso.map { it.y }.average(),
        centerZ = torso.map { it.z }.average(),
        axisX = shoulderDx / shoulderWidth,
        axisY = shoulderDy / shoulderWidth,
        scale = maxOf(shoulderWidth, torsoLength * 0.8).coerceAtLeast(MIN_BODY_SCALE)
    )
}

private data class RelativePoint(val first: Double, val second: Double, val third: Double)

private fun FramePose.normalizedPoint(type: Int, basis: TorsoBasis): RelativePoint? {
    val point = point(type) ?: return null
    val dx = (point.x - basis.centerX) / basis.scale
    val dy = (point.y - basis.centerY) / basis.scale
    return RelativePoint(
        first = dx * basis.axisX + dy * basis.axisY,
        second = -dx * basis.axisY + dy * basis.axisX,
        third = (point.z - basis.centerZ) / basis.scale
    )
}

private fun BattingMotionSample.directionMagnitude(): Double =
    vectorMagnitude(directionX, directionY, directionZ)

private fun vectorMagnitude(x: Double, y: Double, z: Double): Double =
    sqrt(x * x + y * y + z * z)

private fun FramePose.gripScore(basis: TorsoBasis): Double? {
    val left = point(LEFT_WRIST) ?: return null
    val right = point(RIGHT_WRIST) ?: return null
    val ratio = hypot((right.x - left.x).toDouble(), (right.y - left.y).toDouble()) / basis.scale
    return (1.35 - ratio).coerceIn(0.0, 1.0)
}

private fun FramePose.point(type: Int): LandmarkPoint? = landmarks.firstOrNull {
    it.type == type && it.inFrameLikelihood >= MIN_RELIABLE_LIKELIHOOD
}

private fun List<Double>.averageOr(fallback: Double): Double =
    if (isEmpty()) fallback else average()

private const val LEFT_SHOULDER = 11
private const val RIGHT_SHOULDER = 12
private const val LEFT_WRIST = 15
private const val RIGHT_WRIST = 16
private const val LEFT_HIP = 23
private const val RIGHT_HIP = 24
private const val MAX_BATCH_MOTION_INTERVAL_MS = 250L
private const val MIN_PEAK_INTERVALS = 3
private const val MIN_PEAK_FRAMES = MIN_PEAK_INTERVALS + 1
private const val MIN_RELIABLE_LIKELIHOOD = 0.45f
private const val MIN_BODY_SCALE = 1.0
private const val MIN_VECTOR_MAGNITUDE = 0.001
private const val MIN_GRIP_WEIGHT = 0.35
private const val DEPTH_MOTION_WEIGHT = 0.65
private const val MIN_SWING_PLANE_WEIGHT = 0.15
private const val MIN_PEAK_MOTION = 0.04
