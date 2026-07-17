package com.example.sportsai.data

import com.example.sportsai.model.FramePose
import com.example.sportsai.model.LandmarkPoint
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max

/** All poses detected in one video frame, still in full-frame pixel coordinates. */
internal data class MultiPoseFrame(
    val timestampMs: Long,
    val width: Int,
    val height: Int,
    val poses: List<List<LandmarkPoint>>
)

internal data class BatterSelection(
    val posesByFrameIndex: Map<Int, FramePose>,
    val matchScore: Float,
    val maxPeopleDetected: Int,
    val candidateTrackCount: Int,
    val accepted: Boolean,
    val coverage: Float = 0f,
    val completeness: Float = 0f,
    val gripEvidence: Float = 0f,
    val motionEvidence: Float = 0f,
    val rawMotionEvidence: Float = 0f,
    val transverseEvidence: Float = 0f,
    val winnerMargin: Float = 0f
)

/**
 * Follows every detected person through the clip, then selects the complete track with the
 * strongest two-hand swing evidence. This deliberately does not pick the largest or most central
 * person, which is how a catcher or umpire becomes the skeleton in a game-angle recording.
 */
internal class BatterPoseSelector {

    fun select(frames: List<MultiPoseFrame>): BatterSelection {
        val maxPeople = frames.maxOfOrNull { it.poses.size } ?: 0
        if (frames.isEmpty() || maxPeople == 0) return emptySelection(maxPeople)

        val tracks = associateTracks(frames).filter { it.observations.size >= MIN_TRACK_FRAMES }
        if (tracks.isEmpty()) return emptySelection(maxPeople)

        val scored = tracks.map { track -> track to scoreTrack(track, frames.size) }
            .sortedByDescending { it.second.total }
        val (bestTrack, bestScore) = scored.first()
        val runnerUp = scored.getOrNull(1)?.second?.total ?: 0.0
        val margin = (bestScore.total - runnerUp).coerceAtLeast(0.0)
        val onePersonScene = maxPeople == 1 && tracks.size == 1
        val strongMultiPersonEvidence = bestScore.coverage >= STRONG_EVIDENCE_COVERAGE &&
            bestScore.completeness >= STRONG_EVIDENCE_COMPLETENESS &&
            bestScore.handsTogether >= STRONG_EVIDENCE_GRIP &&
            margin >= STRONG_EVIDENCE_MARGIN
        val requiredMultiPersonMotion = if (strongMultiPersonEvidence) {
            MIN_STRONG_EVIDENCE_SWING_MOTION
        } else {
            MIN_SWING_MOTION
        }
        val accepted = bestTrack.observations.size >= MIN_TRACK_FRAMES &&
            bestScore.coverage >= MIN_TRACK_COVERAGE &&
            bestScore.completeness >= MIN_COMPLETENESS &&
            if (onePersonScene) {
                bestScore.total >= MIN_SINGLE_PERSON_SCORE &&
                    bestScore.handsTogether >= MIN_SINGLE_PERSON_GRIP &&
                    bestScore.rawMotion >= MIN_SINGLE_PERSON_SWING_MOTION &&
                    bestScore.transverse >= MIN_TRANSVERSE_SWING_EVIDENCE
            } else {
                bestScore.total >= MIN_MULTI_PERSON_SCORE &&
                    bestScore.rawMotion >= requiredMultiPersonMotion &&
                    bestScore.transverse >= MIN_TRANSVERSE_SWING_EVIDENCE &&
                    bestScore.handsTogether >= MIN_GRIP_EVIDENCE &&
                    margin >= MIN_WINNER_MARGIN
            }

        val matchScore = (
            bestScore.total * 0.72 +
                (margin * 2.5).coerceIn(0.0, 1.0) * 0.18 +
                bestScore.coverage * 0.10
            ).toFloat().coerceIn(0f, 1f)

        return BatterSelection(
            posesByFrameIndex = if (accepted) bestTrack.observations.associate { observation ->
                observation.frameIndex to FramePose(
                    timestampMs = frames[observation.frameIndex].timestampMs,
                    landmarks = observation.pose
                )
            } else emptyMap(),
            matchScore = matchScore,
            maxPeopleDetected = maxPeople,
            candidateTrackCount = tracks.size,
            accepted = accepted,
            coverage = bestScore.coverage.toFloat(),
            completeness = bestScore.completeness.toFloat(),
            gripEvidence = bestScore.handsTogether.toFloat(),
            motionEvidence = bestScore.motion.toFloat(),
            rawMotionEvidence = bestScore.rawMotion.toFloat(),
            transverseEvidence = bestScore.transverse.toFloat(),
            winnerMargin = margin.toFloat()
        )
    }

    private data class Observation(
        val frameIndex: Int,
        val poseIndex: Int,
        val timestampMs: Long,
        val pose: List<LandmarkPoint>
    )

    private data class Track(val observations: MutableList<Observation> = mutableListOf())

    private data class TrackScore(
        val total: Double,
        val coverage: Double,
        val completeness: Double,
        val handsTogether: Double,
        val motion: Double,
        val rawMotion: Double,
        val transverse: Double
    )

    private fun associateTracks(frames: List<MultiPoseFrame>): List<Track> {
        val tracks = mutableListOf<Track>()
        frames.forEachIndexed { frameIndex, frame ->
            val candidates = frame.poses.indices.toList()
            val recentTracks = tracks.indices.filter { trackIndex ->
                val last = tracks[trackIndex].observations.last()
                frameIndex - last.frameIndex <= MAX_TRACK_GAP &&
                    frame.timestampMs - last.timestampMs <= MAX_TRACK_GAP_MS
            }
            val possibleMatches = recentTracks.flatMap { trackIndex ->
                candidates.map { poseIndex ->
                    Match(
                        trackIndex,
                        poseIndex,
                        associationCost(
                            tracks[trackIndex],
                            frames,
                            frame.poses[poseIndex],
                            frame
                        )
                    )
                }
            }.filter { it.cost <= MAX_ASSOCIATION_COST }

            val assignedTracks = mutableSetOf<Int>()
            val assignedPoses = mutableSetOf<Int>()
            // At most four poses are produced. Solving the complete small assignment avoids a
            // locally-cheapest greedy match stealing the correct person from a second track.
            bestAssignment(candidates, possibleMatches).forEach { match ->
                tracks[match.trackIndex].observations += Observation(
                    frameIndex,
                    match.poseIndex,
                    frame.timestampMs,
                    frame.poses[match.poseIndex]
                )
                assignedTracks += match.trackIndex
                assignedPoses += match.poseIndex
            }
            candidates.filterNot(assignedPoses::contains).forEach { poseIndex ->
                tracks += Track(
                    mutableListOf(
                        Observation(frameIndex, poseIndex, frame.timestampMs, frame.poses[poseIndex])
                    )
                )
            }
        }
        return tracks
    }

    private data class Match(val trackIndex: Int, val poseIndex: Int, val cost: Double)

    private fun bestAssignment(poseIndices: List<Int>, matches: List<Match>): List<Match> {
        if (poseIndices.isEmpty() || matches.isEmpty()) return emptyList()
        val byPose = matches.groupBy { it.poseIndex }
        data class Assignment(val matches: List<Match>, val score: Double)
        var best: Assignment? = null
        var runnerUp: Assignment? = null

        fun search(
            poseOffset: Int,
            usedTracks: MutableSet<Int>,
            chosen: MutableList<Match>,
            cost: Double
        ) {
            if (poseOffset == poseIndices.size) {
                val candidate = Assignment(
                    matches = chosen.toList(),
                    score = cost + (poseIndices.size - chosen.size) * NEW_TRACK_COST
                )
                if (candidate.score < (best?.score ?: Double.POSITIVE_INFINITY)) {
                    runnerUp = best
                    best = candidate
                } else if (candidate.score < (runnerUp?.score ?: Double.POSITIVE_INFINITY)) {
                    runnerUp = candidate
                }
                return
            }

            search(poseOffset + 1, usedTracks, chosen, cost)
            byPose[poseIndices[poseOffset]].orEmpty().forEach { match ->
                if (usedTracks.add(match.trackIndex)) {
                    chosen += match
                    search(poseOffset + 1, usedTracks, chosen, cost + match.cost)
                    chosen.removeAt(chosen.lastIndex)
                    usedTracks.remove(match.trackIndex)
                }
            }
        }

        search(0, mutableSetOf(), mutableListOf(), 0.0)
        val winner = best ?: return emptyList()
        val alternative = runnerUp
        if (alternative != null && alternative.score - winner.score < ASSIGNMENT_AMBIGUITY_MARGIN) {
            // When two people overlap and both assignments are virtually equal, keep only pairs
            // common to both solutions. Fragmenting a track is safer than silently swapping the
            // batter with a catcher or umpire.
            return winner.matches.filter { selected ->
                alternative.matches.any { other ->
                    other.trackIndex == selected.trackIndex && other.poseIndex == selected.poseIndex
                }
            }
        }
        return winner.matches
    }

    private fun associationCost(
        track: Track,
        frames: List<MultiPoseFrame>,
        current: List<LandmarkPoint>,
        currentFrame: MultiPoseFrame
    ): Double {
        val previousObservation = track.observations.last()
        val previousFrame = frames[previousObservation.frameIndex]
        val previous = previousObservation.pose
        val before = geometry(previous, previousFrame)
        val after = geometry(current, currentFrame)
        val meanScale = ((before.scale + after.scale) / 2.0).coerceAtLeast(0.06)
        val elapsedSeconds = ((currentFrame.timestampMs - previousObservation.timestampMs) /
            1_000.0).coerceAtLeast(0.05)
        val directTravel = hypot(after.centerX - before.centerX, after.centerY - before.centerY) /
            meanScale
        val scaleChange = abs(ln((after.scale / before.scale.coerceAtLeast(0.01)).coerceAtLeast(0.01)))

        // A person cannot teleport multiple torso lengths between adjacent samples. The gate
        // expands for a brief detector occlusion while remaining tight at the normal 10 fps rate.
        val allowedTravel = (0.56 + elapsedSeconds * 1.75).coerceIn(0.65, 1.35)
        if (directTravel > allowedTravel || scaleChange > MAX_LOG_SCALE_CHANGE) {
            return Double.POSITIVE_INFINITY
        }

        val predictedCenter = predictedCenter(track, frames, elapsedSeconds, before)
        val predictedTravel = hypot(
            after.centerX - predictedCenter.first,
            after.centerY - predictedCenter.second
        ) / meanScale
        val keypointChange = normalizedLandmarkChange(previous, previousFrame, current, currentFrame)
        return predictedTravel * 0.50 + directTravel * 0.20 +
            scaleChange * 0.14 + keypointChange * 0.16
    }

    private fun predictedCenter(
        track: Track,
        frames: List<MultiPoseFrame>,
        elapsedSeconds: Double,
        lastGeometry: Geometry
    ): Pair<Double, Double> {
        val prior = track.observations.getOrNull(track.observations.lastIndex - 1)
            ?: return lastGeometry.centerX to lastGeometry.centerY
        val last = track.observations.last()
        val priorGeometry = geometry(prior.pose, frames[prior.frameIndex])
        val previousElapsed = ((last.timestampMs - prior.timestampMs) / 1_000.0)
            .coerceAtLeast(0.05)
        val velocityX = ((lastGeometry.centerX - priorGeometry.centerX) / previousElapsed)
            .coerceIn(-MAX_NORMALIZED_VELOCITY, MAX_NORMALIZED_VELOCITY)
        val velocityY = ((lastGeometry.centerY - priorGeometry.centerY) / previousElapsed)
            .coerceIn(-MAX_NORMALIZED_VELOCITY, MAX_NORMALIZED_VELOCITY)
        return (lastGeometry.centerX + velocityX * elapsedSeconds) to
            (lastGeometry.centerY + velocityY * elapsedSeconds)
    }

    private data class Geometry(val centerX: Double, val centerY: Double, val scale: Double)

    private fun geometry(pose: List<LandmarkPoint>, frame: MultiPoseFrame): Geometry {
        val reliable = pose.filter { it.inFrameLikelihood >= ASSOCIATION_LIKELIHOOD }
        val torso = TORSO_TYPES.mapNotNull { type -> reliable.point(type) }
        val source = torso.ifEmpty { reliable }.ifEmpty { pose }
        val centerX = source.map { it.x / frame.width.coerceAtLeast(1) }.averageFloatOr(0.5)
        val centerY = source.map { it.y / frame.height.coerceAtLeast(1) }.averageFloatOr(0.5)
        val minY = source.minOfOrNull { it.y / frame.height.coerceAtLeast(1) } ?: 0.45f
        val maxY = source.maxOfOrNull { it.y / frame.height.coerceAtLeast(1) } ?: 0.55f
        val bboxHeight = (maxY - minY).toDouble().coerceAtLeast(0.04)
        val shoulderWidth = distanceNormalized(
            reliable.point(LEFT_SHOULDER), reliable.point(RIGHT_SHOULDER), frame
        ) ?: 0.0
        return Geometry(centerX, centerY, max(bboxHeight, shoulderWidth * 1.8))
    }

    private fun normalizedLandmarkChange(
        previous: List<LandmarkPoint>,
        previousFrame: MultiPoseFrame,
        current: List<LandmarkPoint>,
        currentFrame: MultiPoseFrame
    ): Double {
        val beforeGeometry = geometry(previous, previousFrame)
        val afterGeometry = geometry(current, currentFrame)
        val changes = ASSOCIATION_TYPES.mapNotNull { type ->
            val before = previous.point(type) ?: return@mapNotNull null
            val after = current.point(type) ?: return@mapNotNull null
            if (before.inFrameLikelihood < ASSOCIATION_LIKELIHOOD ||
                after.inFrameLikelihood < ASSOCIATION_LIKELIHOOD
            ) return@mapNotNull null
            val beforeX = before.x / previousFrame.width - beforeGeometry.centerX
            val beforeY = before.y / previousFrame.height - beforeGeometry.centerY
            val afterX = after.x / currentFrame.width - afterGeometry.centerX
            val afterY = after.y / currentFrame.height - afterGeometry.centerY
            hypot(afterX - beforeX, afterY - beforeY) /
                ((beforeGeometry.scale + afterGeometry.scale) / 2.0).coerceAtLeast(0.06)
        }
        return changes.averageOr(0.5)
    }

    private fun scoreTrack(track: Track, totalFrameCount: Int): TrackScore {
        val observations = track.observations
        val coverage = observations.size.toDouble() / totalFrameCount.coerceAtLeast(1)
        val completeness = observations.map { observation ->
            REQUIRED_BATTER_TYPES.count { type ->
                observation.pose.point(type)?.inFrameLikelihood?.let { it >= RELIABLE_LIKELIHOOD } == true
            }.toDouble() / REQUIRED_BATTER_TYPES.size
        }.averageOr(0.0)
        val confidence = observations.flatMap { observation ->
            observation.pose.filter { it.type in REQUIRED_BATTER_TYPES }
                .map { it.inFrameLikelihood.toDouble() }
        }.averageOr(0.0).coerceIn(0.0, 1.0)
        val handsTogether = observations.mapNotNull { observation ->
            gripScore(observation.pose)
        }.averageOr(0.0)
        val handHeight = observations.mapNotNull { observation ->
            battingHandHeightScore(observation.pose)
        }.averageOr(0.45)
        val upright = observations.mapNotNull { observation ->
            stanceScore(observation.pose)
        }.averageOr(0.45)
        val swingEvidence = swingMotionEvidence(observations)
        val motion = swingEvidence.weighted
        val rawMotion = swingEvidence.raw
        val transverse = swingEvidence.transverse

        val total = (
            coverage.coerceIn(0.0, 1.0) * 0.14 +
                completeness * 0.12 +
                confidence * 0.09 +
                handsTogether * 0.23 +
                handHeight * 0.10 +
                upright * 0.10 +
                motion * 0.22
            ).coerceIn(0.0, 1.0)
        return TrackScore(
            total,
            coverage,
            completeness,
            handsTogether,
            motion,
            rawMotion,
            transverse
        )
    }

    private fun gripScore(pose: List<LandmarkPoint>): Double? {
        val leftWrist = pose.reliablePoint(LEFT_WRIST) ?: return null
        val rightWrist = pose.reliablePoint(RIGHT_WRIST) ?: return null
        val scale = bodyScale(pose) ?: return null
        val ratio = distance(leftWrist, rightWrist) / scale
        return (1.35 - ratio).coerceIn(0.0, 1.0)
    }

    private fun battingHandHeightScore(pose: List<LandmarkPoint>): Double? {
        val leftWrist = pose.reliablePoint(LEFT_WRIST) ?: return null
        val rightWrist = pose.reliablePoint(RIGHT_WRIST) ?: return null
        val leftShoulder = pose.reliablePoint(LEFT_SHOULDER) ?: return null
        val rightShoulder = pose.reliablePoint(RIGHT_SHOULDER) ?: return null
        val leftHip = pose.reliablePoint(LEFT_HIP) ?: return null
        val rightHip = pose.reliablePoint(RIGHT_HIP) ?: return null
        val wristY = (leftWrist.y + rightWrist.y) / 2.0
        val shoulderY = (leftShoulder.y + rightShoulder.y) / 2.0
        val hipY = (leftHip.y + rightHip.y) / 2.0
        val torso = abs(hipY - shoulderY).coerceAtLeast(1.0)
        val relative = (wristY - shoulderY) / torso
        return when {
            relative in -0.65..0.75 -> 1.0
            relative < -0.65 -> (1.0 - abs(relative + 0.65) / 1.5).coerceIn(0.0, 1.0)
            else -> (1.0 - (relative - 0.75) / 1.2).coerceIn(0.0, 1.0)
        }
    }

    private fun stanceScore(pose: List<LandmarkPoint>): Double? {
        val angles = listOfNotNull(
            jointAngle(pose, LEFT_HIP, LEFT_KNEE, LEFT_ANKLE),
            jointAngle(pose, RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE)
        )
        if (angles.isEmpty()) return null
        val average = angles.average()
        return ((average - 80.0) / 70.0).coerceIn(0.0, 1.0)
    }

    private data class TimedSpeed(
        val fromMs: Long,
        val toMs: Long,
        val speed: Double,
        val rawSpeed: Double,
        val transverse: Double,
        val directionX: Double,
        val directionY: Double,
        val directionZ: Double
    )

    private data class SwingMotionEvidence(
        val weighted: Double,
        val raw: Double,
        val transverse: Double
    )

    private data class BurstEvidence(
        val weighted: Double,
        val raw: Double,
        val transverse: Double
    )

    private fun swingMotionEvidence(observations: List<Observation>): SwingMotionEvidence {
        val coordinatedSpeeds = observations.zipWithNext().map { (before, after) ->
            coordinatedHandSpeed(before, after)
        }
        val contiguousRuns = mutableListOf<MutableList<TimedSpeed>>()
        var currentRun = mutableListOf<TimedSpeed>()
        fun finishCurrentRun() {
            if (currentRun.isNotEmpty()) {
                contiguousRuns += currentRun
                currentRun = mutableListOf()
            }
        }
        coordinatedSpeeds.forEach { sample ->
            val continuesRun = sample != null &&
                sample.toMs - sample.fromMs <= MAX_MOTION_INTERVAL_MS &&
                (currentRun.isEmpty() || currentRun.last().toMs == sample.fromMs)
            if (!continuesRun) finishCurrentRun()
            if (sample != null && sample.toMs - sample.fromMs <= MAX_MOTION_INTERVAL_MS) {
                currentRun += sample
            }
        }
        finishCurrentRun()
        val validRuns = contiguousRuns.filter { it.size >= MIN_CONTIGUOUS_MOTION_INTERVALS }
        if (validRuns.isEmpty()) return SwingMotionEvidence(0.0, 0.0, 0.0)
        // A real swing is a brief burst inside a longer setup/follow-through. Use the strongest
        // three-interval rolling median so one jitter spike cannot win, but a short swing is not
        // diluted by several seconds of stillness. Occlusion boundaries are never compressed.
        val strongest = validRuns.flatMap { run ->
            run.windowed(MIN_CONTIGUOUS_MOTION_INTERVALS)
        }.map { window ->
                val weightedMedian = window.map { it.speed }.sorted()[window.size / 2]
                val rawMedian = window.map { it.rawSpeed }.sorted()[window.size / 2]
                val pathLength = window.sumOf { it.directionMagnitude() }
                val consistency = if (pathLength < 0.001) 0.0 else {
                    vectorMagnitude(
                        window.sumOf { it.directionX },
                        window.sumOf { it.directionY },
                        window.sumOf { it.directionZ }
                    ) / pathLength
                }.coerceIn(0.0, 1.0)
                BurstEvidence(
                    weighted = weightedMedian * consistency,
                    raw = rawMedian * consistency,
                    transverse = window.map { it.transverse }.sorted()[window.size / 2] *
                        consistency
                )
        }.maxByOrNull { it.weighted }
            ?: return SwingMotionEvidence(0.0, 0.0, 0.0)
        val raw = ((strongest.raw - 0.45) / 3.0).coerceIn(0.0, 1.0)
        val weighted = ((strongest.weighted - 0.45) / 3.0).coerceIn(0.0, 1.0)
        return SwingMotionEvidence(
            weighted = weighted,
            raw = raw,
            transverse = if (raw > 0.0) strongest.transverse.coerceIn(0.0, 1.0) else 0.0
        )
    }

    private fun coordinatedHandSpeed(
        before: Observation,
        after: Observation
    ): TimedSpeed? {
        val motion = coordinatedBattingMotion(
            FramePose(before.timestampMs, before.pose),
            FramePose(after.timestampMs, after.pose)
        ) ?: return null
        return TimedSpeed(
            fromMs = before.timestampMs,
            toMs = after.timestampMs,
            speed = motion.score,
            rawSpeed = motion.rawScore,
            transverse = motion.transverseEvidence,
            directionX = motion.directionX,
            directionY = motion.directionY,
            directionZ = motion.directionZ
        )
    }

    private fun TimedSpeed.directionMagnitude(): Double =
        vectorMagnitude(directionX, directionY, directionZ)

    private fun vectorMagnitude(x: Double, y: Double, z: Double): Double =
        kotlin.math.sqrt(x * x + y * y + z * z)

    private fun bodyScale(pose: List<LandmarkPoint>): Double? {
        val leftShoulder = pose.reliablePoint(LEFT_SHOULDER)
        val rightShoulder = pose.reliablePoint(RIGHT_SHOULDER)
        val leftHip = pose.reliablePoint(LEFT_HIP)
        val rightHip = pose.reliablePoint(RIGHT_HIP)
        val shoulderWidth = if (leftShoulder != null && rightShoulder != null) {
            distance(leftShoulder, rightShoulder)
        } else 0.0
        val torsoLength = if (leftShoulder != null && rightShoulder != null &&
            leftHip != null && rightHip != null
        ) {
            val shoulderX = (leftShoulder.x + rightShoulder.x) / 2f
            val shoulderY = (leftShoulder.y + rightShoulder.y) / 2f
            val hipX = (leftHip.x + rightHip.x) / 2f
            val hipY = (leftHip.y + rightHip.y) / 2f
            hypot((hipX - shoulderX).toDouble(), (hipY - shoulderY).toDouble())
        } else 0.0
        return max(shoulderWidth, torsoLength * 0.80).takeIf { it >= 1.0 }
    }

    private fun jointAngle(pose: List<LandmarkPoint>, first: Int, joint: Int, last: Int): Double? {
        val a = pose.reliablePoint(first) ?: return null
        val b = pose.reliablePoint(joint) ?: return null
        val c = pose.reliablePoint(last) ?: return null
        val abX = (a.x - b.x).toDouble()
        val abY = (a.y - b.y).toDouble()
        val cbX = (c.x - b.x).toDouble()
        val cbY = (c.y - b.y).toDouble()
        val denominator = hypot(abX, abY) * hypot(cbX, cbY)
        if (denominator < 0.001) return null
        return Math.toDegrees(acos(((abX * cbX + abY * cbY) / denominator).coerceIn(-1.0, 1.0)))
    }

    private fun List<LandmarkPoint>.point(type: Int): LandmarkPoint? = firstOrNull { it.type == type }

    private fun List<LandmarkPoint>.reliablePoint(type: Int): LandmarkPoint? =
        firstOrNull { it.type == type && it.inFrameLikelihood >= RELIABLE_LIKELIHOOD }

    private fun distance(first: LandmarkPoint, second: LandmarkPoint): Double =
        hypot((second.x - first.x).toDouble(), (second.y - first.y).toDouble())

    private fun distanceNormalized(
        first: LandmarkPoint?,
        second: LandmarkPoint?,
        frame: MultiPoseFrame
    ): Double? {
        if (first == null || second == null) return null
        return hypot(
            (second.x - first.x).toDouble() / frame.width.coerceAtLeast(1),
            (second.y - first.y).toDouble() / frame.height.coerceAtLeast(1)
        )
    }

    private fun List<Double>.averageOr(default: Double): Double =
        if (isEmpty()) default else average()

    private fun List<Float>.averageFloatOr(default: Double): Double =
        if (isEmpty()) default else average()

    private fun emptySelection(maxPeople: Int) = BatterSelection(
        posesByFrameIndex = emptyMap(),
        matchScore = 0f,
        maxPeopleDetected = maxPeople,
        candidateTrackCount = 0,
        accepted = false
    )

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
        const val RELIABLE_LIKELIHOOD = 0.45f
        const val ASSOCIATION_LIKELIHOOD = 0.30f
        // At 10 fps this bridges up to four missing samples (500 ms between observations).
        const val MAX_TRACK_GAP = 5
        const val MAX_TRACK_GAP_MS = 500L
        const val MAX_ASSOCIATION_COST = 1.05
        const val NEW_TRACK_COST = 0.62
        const val ASSIGNMENT_AMBIGUITY_MARGIN = 0.08
        const val MAX_LOG_SCALE_CHANGE = 0.55
        const val MAX_NORMALIZED_VELOCITY = 1.8
        const val MAX_MOTION_INTERVAL_MS = 250L
        const val MIN_CONTIGUOUS_MOTION_INTERVALS = 3
        const val MIN_TRACK_FRAMES = 3
        const val MIN_TRACK_COVERAGE = 0.45
        const val MIN_COMPLETENESS = 0.45
        const val MIN_SINGLE_PERSON_SCORE = 0.42
        const val MIN_SINGLE_PERSON_GRIP = 0.18
        const val MIN_SINGLE_PERSON_SWING_MOTION = 0.06
        const val MIN_MULTI_PERSON_SCORE = 0.48
        const val MIN_SWING_MOTION = 0.10
        const val MIN_STRONG_EVIDENCE_SWING_MOTION = 0.07
        const val MIN_TRANSVERSE_SWING_EVIDENCE = 0.25
        const val MIN_GRIP_EVIDENCE = 0.22
        const val MIN_WINNER_MARGIN = 0.035
        const val STRONG_EVIDENCE_COVERAGE = 0.70
        const val STRONG_EVIDENCE_COMPLETENESS = 0.70
        const val STRONG_EVIDENCE_GRIP = 0.75
        const val STRONG_EVIDENCE_MARGIN = 0.12

        val TORSO_TYPES = listOf(LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_HIP, RIGHT_HIP)
        val ASSOCIATION_TYPES = listOf(
            LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_WRIST, RIGHT_WRIST,
            LEFT_HIP, RIGHT_HIP, LEFT_KNEE, RIGHT_KNEE
        )
        val REQUIRED_BATTER_TYPES = listOf(
            LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_ELBOW, RIGHT_ELBOW,
            LEFT_WRIST, RIGHT_WRIST, LEFT_HIP, RIGHT_HIP,
            LEFT_KNEE, RIGHT_KNEE, LEFT_ANKLE, RIGHT_ANKLE
        )
    }
}
