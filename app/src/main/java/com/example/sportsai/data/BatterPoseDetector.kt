package com.example.sportsai.data

import android.content.Context
import android.graphics.Bitmap
import com.example.sportsai.model.LandmarkPoint
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import kotlin.math.min

/** Bundled MediaPipe neural detector that returns every visible pose needed for Batter Lock. */
internal class BatterPoseDetector(context: Context) : AutoCloseable {

    private val landmarker: PoseLandmarker
    private var retainedInput: Bitmap? = null
    private var closed = false

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET)
            .setDelegate(Delegate.CPU)
            .build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.VIDEO)
            .setNumPoses(MAX_POSES)
            .setMinPoseDetectionConfidence(0.45f)
            .setMinPosePresenceConfidence(0.45f)
            .setMinTrackingConfidence(0.45f)
            .setOutputSegmentationMasks(false)
            .build()
        landmarker = PoseLandmarker.createFromOptions(context, options)
    }

    fun detect(bitmap: Bitmap, timestampMs: Long): List<List<LandmarkPoint>> {
        check(!closed) { "BatterPoseDetector is already closed" }
        val width = bitmap.width
        val height = bitmap.height
        // BitmapImageBuilder keeps the backing Bitmap rather than copying its pixels. MediaPipe's
        // native VIDEO runner can finish releasing that storage just after detectForVideo returns,
        // so give it an owned copy and retain that copy through the next inference. The caller may
        // safely recycle its decoded video frame as soon as this method returns.
        val ownedArgb = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val previousInput = retainedInput
        return try {
            val mpImage = BitmapImageBuilder(ownedArgb).build()
            try {
                landmarker.detectForVideo(mpImage, timestampMs).landmarks().map { pose ->
                    pose.mapIndexed { index, landmark ->
                        val visibility = landmark.visibility().orElse(0f)
                        val presence = landmark.presence().orElse(0f)
                        LandmarkPoint(
                            type = index,
                            x = landmark.x() * width,
                            y = landmark.y() * height,
                            inFrameLikelihood = min(visibility, presence),
                            z = landmark.z() * width
                        )
                    }
                }
            } finally {
                mpImage.close()
            }
        } finally {
            retainedInput = ownedArgb
            previousInput?.recycle()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            landmarker.close()
        } finally {
            retainedInput?.recycle()
            retainedInput = null
        }
    }

    private companion object {
        const val MODEL_ASSET = "pose_landmarker_full.task"
        const val MAX_POSES = 4
    }
}
