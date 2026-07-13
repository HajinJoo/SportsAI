package com.example.sportsai.model

import android.graphics.Bitmap

/** A single detected body landmark at normalized image coordinates plus confidence. */
data class LandmarkPoint(
    val type: Int,
    val x: Float,
    val y: Float,
    val inFrameLikelihood: Float
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

/** Result of running the pose pipeline over a whole clip. */
data class AnalysisResult(
    val framesSampled: Int,
    val framesWithPose: Int,
    val timeline: List<FramePose>,
    /** A representative frame (most landmarks detected) for the skeleton overlay. */
    val keyFrame: Bitmap? = null,
    val keyFramePose: FramePose? = null,
    /** Downscaled frames with poses for animated playback. */
    val animationFrames: List<AnimationFrame> = emptyList()
) {
    val detectionRate: Float
        get() = if (framesSampled == 0) 0f else framesWithPose.toFloat() / framesSampled
}


