package com.example.sportsai.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.sportsai.model.AnalysisResult
import com.example.sportsai.model.FramePose
import com.example.sportsai.model.LandmarkPoint
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Samples frames from a video clip and runs on-device ML Kit pose detection on each.
 * This is the core "AI" pipeline for the MVP.
 */
class PoseAnalyzer(private val context: Context) {

    private val detector by lazy {
        val options = AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.SINGLE_IMAGE_MODE)
            .build()
        PoseDetection.getClient(options)
    }

    /**
     * @param videoUri the clip to analyze
     * @param targetFps how many frames per second to sample (keep low for speed)
     * @param onProgress called with a value in 0f..1f as sampling proceeds
     */
    suspend fun analyze(
        videoUri: Uri,
        targetFps: Int = 5,
        onProgress: (Float) -> Unit = {}
    ): AnalysisResult = withContext(Dispatchers.Default) {
        val retriever = MediaMetadataRetriever()
        val timeline = mutableListOf<FramePose>()
        val animationFrames = mutableListOf<com.example.sportsai.model.AnimationFrame>()
        var framesSampled = 0
        var framesWithPose = 0
        var keyFrame: Bitmap? = null
        var keyFramePose: FramePose? = null
        var bestLandmarkCount = 0
        var durationMs = 0L
        try {
            retriever.setDataSource(context, videoUri)
            durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L

            val stepMs = (1000L / targetFps).coerceAtLeast(1L)
            var t = 0L
            while (t <= durationMs) {
                val bitmap: Bitmap? = retriever.getFrameAtTime(
                    t * 1000L, // microseconds
                    MediaMetadataRetriever.OPTION_CLOSEST
                )
                if (bitmap != null) {
                    framesSampled++
                    val landmarks = detectPose(bitmap, t)
                    if (landmarks.isNotEmpty()) {
                        framesWithPose++
                        val framePose = FramePose(t, landmarks)
                        timeline.add(framePose)
                        // Downscaled copy + matching-scaled pose for animated playback.
                        val factor = animScaleFactor(bitmap.width, bitmap.height)
                        animationFrames.add(
                            com.example.sportsai.model.AnimationFrame(
                                bitmap = downscale(bitmap, factor),
                                pose = scalePose(framePose, factor)
                            )
                        )
                        // Keep the frame with the most confident landmarks for the overlay.
                        if (landmarks.size > bestLandmarkCount) {
                            bestLandmarkCount = landmarks.size
                            keyFrame?.recycle()
                            keyFrame = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                            keyFramePose = framePose
                        }
                    }
                    bitmap.recycle()
                }
                if (durationMs > 0) onProgress((t.toFloat() / durationMs).coerceIn(0f, 1f))
                t += stepMs
            }
            onProgress(1f)
        } finally {
            retriever.release()
        }
        AnalysisResult(
            framesSampled = framesSampled,
            framesWithPose = framesWithPose,
            timeline = timeline,
            keyFrame = keyFrame,
            keyFramePose = keyFramePose,
            animationFrames = animationFrames,
            durationMs = durationMs
        )
    }

    private suspend fun detectPose(bitmap: Bitmap, timestampMs: Long): List<LandmarkPoint> =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            detector.process(image)
                .addOnSuccessListener { pose ->
                    val points = pose.allPoseLandmarks.map { lm ->
                        LandmarkPoint(
                            type = lm.landmarkType,
                            x = lm.position.x,
                            y = lm.position.y,
                            inFrameLikelihood = lm.inFrameLikelihood
                        )
                    }
                    cont.resume(points)
                }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }

    fun close() = detector.close()

    private companion object {
        const val MAX_ANIM_DIMEN = 480f
    }

    /** Scale factor to fit the frame's longest side within MAX_ANIM_DIMEN (never upscales). */
    private fun animScaleFactor(width: Int, height: Int): Float {
        val longest = maxOf(width, height).toFloat()
        return if (longest <= MAX_ANIM_DIMEN) 1f else MAX_ANIM_DIMEN / longest
    }

    private fun downscale(bitmap: Bitmap, factor: Float): Bitmap {
        if (factor >= 1f) return bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val w = (bitmap.width * factor).toInt().coerceAtLeast(1)
        val h = (bitmap.height * factor).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private fun scalePose(frame: FramePose, factor: Float): FramePose =
        if (factor >= 1f) frame
        else FramePose(
            timestampMs = frame.timestampMs,
            landmarks = frame.landmarks.map {
                it.copy(x = it.x * factor, y = it.y * factor)
            }
        )
}


