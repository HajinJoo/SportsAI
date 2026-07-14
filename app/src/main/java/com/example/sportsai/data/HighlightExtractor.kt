package com.example.sportsai.data

import com.example.sportsai.model.AnalysisResult
import com.example.sportsai.model.FramePose
import com.example.sportsai.model.HighlightClip
import com.example.sportsai.model.LandmarkPoint
import com.example.sportsai.model.Sport
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Analyzes the pose timeline to identify highlight moments in the clip.
 *
 * Highlights are the most interesting athletic moments — peak movement speed,
 * best body tracking quality, and sport-specific key moments (release point,
 * contact point, shot release).
 */
class HighlightExtractor {

    private companion object {
        const val LEFT_SHOULDER = 11
        const val RIGHT_SHOULDER = 12
        const val LEFT_WRIST = 15
        const val RIGHT_WRIST = 16
        const val LEFT_HIP = 23
        const val RIGHT_HIP = 24
        const val MIN_LIKELIHOOD = 0.5f
        /** Minimum frames before or after a key moment to include in a highlight. */
        const val CONTEXT_FRAMES = 3
    }

    /**
     * Extracts up to [maxClips] highlight clips from the given analysis result.
     * Each clip covers a key athletic moment and a few surrounding frames.
     */
    fun extract(result: AnalysisResult, sport: Sport, maxClips: Int = 3): List<HighlightClip> {
        val timeline = result.timeline
        if (timeline.size < 4) return emptyList()

        val candidates = mutableListOf<HighlightCandidate>()

        // 1. Find the frame with the highest joint velocity (peak action).
        findPeakMovement(timeline)?.let { candidates.add(it) }

        // 2. Find the frame with the best tracking quality.
        findBestTracking(timeline)?.let { candidates.add(it) }

        // 3. Sport-specific key moment.
        findKeyMoment(timeline, sport)?.let { candidates.add(it) }

        // De-duplicate overlapping clips and limit count.
        val merged = mergeOverlapping(candidates.sortedBy { it.centerIndex })
        return merged.take(maxClips).mapIndexed { i, c ->
            val start = timeline[c.startIndex].timestampMs
            val end = timeline[c.endIndex].timestampMs
            HighlightClip(
                id = System.currentTimeMillis() + i,
                label = c.label,
                startMs = start,
                endMs = end,
                score = c.score
            )
        }
    }

    // ------------------------------------------------------------------
    // Candidate detection
    // ------------------------------------------------------------------

    private data class HighlightCandidate(
        val label: String,
        val centerIndex: Int,
        val startIndex: Int,
        val endIndex: Int,
        val score: Int
    )

    /**
     * Finds the frame with the highest wrist velocity — the peak action moment.
     * This is typically the release point, contact point, or follow-through.
     */
    private fun findPeakMovement(timeline: List<FramePose>): HighlightCandidate? {
        if (timeline.size < 3) return null
        var bestIdx = -1
        var bestVelocity = 0.0

        for (i in 1 until timeline.lastIndex) {
            val prev = timeline[i - 1]
            val curr = timeline[i]
            val velocity = wristVelocity(prev, curr)
            if (velocity > bestVelocity) {
                bestVelocity = velocity
                bestIdx = i
            }
        }
        if (bestIdx < 0) return null

        val start = (bestIdx - CONTEXT_FRAMES).coerceAtLeast(0)
        val end = (bestIdx + CONTEXT_FRAMES).coerceAtMost(timeline.lastIndex)
        val quality = (bestVelocity * 100).toInt().coerceIn(0, 100)

        return HighlightCandidate("Peak action", bestIdx, start, end, quality)
    }

    /**
     * Finds the frame range with the highest average landmark confidence.
     */
    private fun findBestTracking(timeline: List<FramePose>): HighlightCandidate? {
        if (timeline.size < 3) return null
        var bestIdx = -1
        var bestConf = 0f

        timeline.forEachIndexed { i, frame ->
            val conf = frame.landmarks
                .filter { it.inFrameLikelihood >= MIN_LIKELIHOOD }
                .map { it.inFrameLikelihood }
                .average().toFloat()
            if (conf > bestConf) {
                bestConf = conf
                bestIdx = i
            }
        }
        if (bestIdx < 0) return null

        val start = (bestIdx - CONTEXT_FRAMES).coerceAtLeast(0)
        val end = (bestIdx + CONTEXT_FRAMES).coerceAtMost(timeline.lastIndex)
        val quality = (bestConf * 100).toInt().coerceIn(0, 100)

        return HighlightCandidate("Best form", bestIdx, start, end, quality)
    }

    /**
     * Finds the sport-specific key athletic moment.
     * - Pitching: highest wrist → release point
     * - Batting: peak trunk rotation → contact zone
     * - Basketball: highest wrist-above-shoulder → release
     */
    private fun findKeyMoment(timeline: List<FramePose>, sport: Sport): HighlightCandidate? {
        if (timeline.size < 3) return null

        val (label, bestIdx) = when (sport) {
            Sport.BASEBALL_PITCH -> {
                // Release point: frame where throwing wrist is highest (smallest y).
                var idx = -1; var bestY = Float.MAX_VALUE
                timeline.forEachIndexed { i, f ->
                    val wy = throwingWristY(f)
                    if (wy != null && wy < bestY) { bestY = wy; idx = i }
                }
                "Release point" to idx
            }
            Sport.BASEBALL_BAT -> {
                // Contact zone: maximum hip–shoulder separation.
                var idx = -1; var bestSep = 0.0
                timeline.forEachIndexed { i, f ->
                    val sep = trunkRotation(f)
                    if (sep != null && sep > bestSep) { bestSep = sep; idx = i }
                }
                "Contact zone" to idx
            }
            Sport.BASKETBALL_SHOT -> {
                // Shot release: frame where wrist is highest above shoulder.
                var idx = -1; var bestDelta = 0f
                timeline.forEachIndexed { i, f ->
                    val delta = wristAboveShoulder(f)
                    if (delta != null && delta > bestDelta) { bestDelta = delta; idx = i }
                }
                "Shot release" to idx
            }
        }

        if (bestIdx < 0) return null
        val start = (bestIdx - CONTEXT_FRAMES).coerceAtLeast(0)
        val end = (bestIdx + CONTEXT_FRAMES).coerceAtMost(timeline.lastIndex)
        return HighlightCandidate(label, bestIdx, start, end, 85)
    }

    // ------------------------------------------------------------------
    // Geometry helpers
    // ------------------------------------------------------------------

    private fun FramePose.point(type: Int): LandmarkPoint? =
        landmarks.firstOrNull { it.type == type && it.inFrameLikelihood >= MIN_LIKELIHOOD }

    private fun wristVelocity(prev: FramePose, curr: FramePose): Double {
        val pw = prev.point(RIGHT_WRIST) ?: prev.point(LEFT_WRIST) ?: return 0.0
        val cw = curr.point(RIGHT_WRIST) ?: curr.point(LEFT_WRIST) ?: return 0.0
        return hypot((cw.x - pw.x).toDouble(), (cw.y - pw.y).toDouble())
    }

    private fun throwingWristY(frame: FramePose): Float? {
        val rw = frame.point(RIGHT_WRIST)
        val lw = frame.point(LEFT_WRIST)
        return when {
            rw != null && lw != null -> minOf(rw.y, lw.y) // higher wrist (smaller y)
            rw != null -> rw.y
            lw != null -> lw.y
            else -> null
        }
    }

    private fun trunkRotation(frame: FramePose): Double? {
        val ls = frame.point(LEFT_SHOULDER) ?: return null
        val rs = frame.point(RIGHT_SHOULDER) ?: return null
        val lh = frame.point(LEFT_HIP) ?: return null
        val rh = frame.point(RIGHT_HIP) ?: return null
        val shoulderAngle = kotlin.math.atan2(
            (rs.y - ls.y).toDouble(), (rs.x - ls.x).toDouble()
        )
        val hipAngle = kotlin.math.atan2(
            (rh.y - lh.y).toDouble(), (rh.x - lh.x).toDouble()
        )
        return abs(Math.toDegrees(shoulderAngle - hipAngle))
    }

    private fun wristAboveShoulder(frame: FramePose): Float? {
        val rw = frame.point(RIGHT_WRIST)
        val lw = frame.point(LEFT_WRIST)
        val rs = frame.point(RIGHT_SHOULDER)
        val ls = frame.point(LEFT_SHOULDER)
        val wrist = rw ?: lw ?: return null
        val shoulder = rs ?: ls ?: return null
        val delta = shoulder.y - wrist.y // positive = wrist above shoulder
        return if (delta > 0) delta else null
    }

    // ------------------------------------------------------------------
    // Merge overlapping clips
    // ------------------------------------------------------------------

    private fun mergeOverlapping(sorted: List<HighlightCandidate>): List<HighlightCandidate> {
        if (sorted.isEmpty()) return emptyList()
        val result = mutableListOf(sorted.first())
        for (c in sorted.drop(1)) {
            val last = result.last()
            if (c.startIndex <= last.endIndex + 1) {
                // Overlaps — keep the one with the higher score.
                if (c.score > last.score) {
                    result[result.lastIndex] = c
                }
            } else {
                result.add(c)
            }
        }
        return result
    }
}
