package com.example.sportsai.model

import android.graphics.Bitmap

/** A single detected body landmark in full-frame pixel coordinates plus confidence. */
data class LandmarkPoint(
    val type: Int,
    val x: Float,
    val y: Float,
    val inFrameLikelihood: Float,
    /** Relative model depth. Useful for rotation direction, never treated as metric distance. */
    val z: Float = 0f
)

/** All landmarks detected for one sampled video frame. */
data class FramePose(
    val timestampMs: Long,
    val landmarks: List<LandmarkPoint>
)

/** A frame bitmap paired with its detected pose, used for animated skeleton playback. */
data class AnimationFrame(
    val bitmap: Bitmap,
    val pose: FramePose
)

enum class TrackedObjectType { BAT, BALL }

/** A lightweight detector result in full-frame pixel coordinates. */
data class TrackedObject(
    val timestampMs: Long,
    val type: TrackedObjectType,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val confidence: Float
)

enum class AthleteTrackingMode {
    /** Existing single-person tracking used for pitching and basketball. */
    SINGLE_PERSON,
    /** Multi-person video tracking selected one continuous swing-like batter track. */
    BATTER_LOCKED,
    /** People were visible, but no track had enough batting evidence to score honestly. */
    BATTER_NOT_CONFIDENT
}

/** Explains which athlete track supplied the skeleton and downstream measurements. */
data class AthleteTrackingInfo(
    val mode: AthleteTrackingMode = AthleteTrackingMode.SINGLE_PERSON,
    /** Heuristic 0..1 track-match score; it is not a calibrated probability. */
    val matchScore: Float = 1f,
    val maxPeopleDetected: Int = 1,
    val trackedFrames: Int = 0,
    /** Diagnostics used by regression tests and honest retry guidance. */
    val candidateTrackCount: Int = 0,
    val trackCoverage: Float = 0f,
    val landmarkCompleteness: Float = 0f,
    val gripEvidence: Float = 0f,
    val motionEvidence: Float = 0f,
    val rawMotionEvidence: Float = 0f,
    /** Fraction of the winning two-hand burst moving across the x/depth swing plane. */
    val transverseEvidence: Float = 0f,
    val winnerMargin: Float = 0f
)

/** Result of running the pose pipeline over a whole clip. */
data class AnalysisResult(
    val framesSampled: Int,
    val framesWithPose: Int,
    val timeline: List<FramePose>,
    /** A representative frame (most landmarks detected) for the skeleton overlay. */
    val keyFrame: Bitmap? = null,
    val keyFramePose: FramePose? = null,
    /** Downscaled frames with poses for animated playback. */
    val animationFrames: List<AnimationFrame> = emptyList(),
    /** Duration reported by the source video container. */
    val durationMs: Long = 0L,
    val athleteTracking: AthleteTrackingInfo = AthleteTrackingInfo(),
    /** Bat/ball observations from a separate object detector; pose landmarks never imply them. */
    val trackedObjects: List<TrackedObject> = emptyList(),
    /** Number of sampled frames successfully submitted to the object detector. */
    val objectDetectionFrames: Int = 0
) {
    val detectionRate: Float
        get() = if (framesSampled == 0) 0f else framesWithPose.toFloat() / framesSampled
}


