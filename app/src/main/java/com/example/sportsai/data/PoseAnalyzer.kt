package com.example.sportsai.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.sportsai.model.AnalysisResult
import com.example.sportsai.model.AnimationFrame
import com.example.sportsai.model.AthleteTrackingInfo
import com.example.sportsai.model.AthleteTrackingMode
import com.example.sportsai.model.FramePose
import com.example.sportsai.model.LandmarkPoint
import com.example.sportsai.model.Sport
import com.example.sportsai.model.TrackedObject
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Samples video frames and runs bundled on-device pose models. Batting uses multi-person
 * MediaPipe video tracking plus [BatterPoseSelector]; other sports retain accurate ML Kit.
 */
class PoseAnalyzer(private val context: Context) {

    private val singlePoseDetectorDelegate = lazy {
        val options = AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.SINGLE_IMAGE_MODE)
            .build()
        PoseDetection.getClient(options)
    }
    private val singlePoseDetector by singlePoseDetectorDelegate

    suspend fun analyze(
        videoUri: Uri,
        sport: Sport = Sport.BASEBALL_PITCH,
        targetFps: Int = 5,
        onProgress: (Float) -> Unit = {}
    ): AnalysisResult = withContext(Dispatchers.Default) {
        if (sport == Sport.BASEBALL_BAT) {
            // A baseball swing can cross the hitting zone between two 5 fps samples.
            analyzeBatting(videoUri, maxOf(targetFps, MIN_BATTING_FPS), onProgress)
        } else {
            analyzeSinglePerson(videoUri, targetFps, onProgress)
        }
    }

    private suspend fun analyzeSinglePerson(
        videoUri: Uri,
        targetFps: Int,
        onProgress: (Float) -> Unit
    ): AnalysisResult {
        val retriever = MediaMetadataRetriever()
        val samples = mutableListOf<SinglePoseSample>()
        val animationFrames = mutableListOf<AnimationFrame>()
        var framesSampled = 0
        var framesWithPose = 0
        var keyFrame: Bitmap? = null
        var keyFramePose: FramePose? = null
        var bestFrameQuality = 0.0
        var bestSample: SinglePoseSample? = null
        var durationMs = 0L
        try {
            retriever.setDataSource(context, videoUri)
            durationMs = duration(retriever)
            val videoSize = videoFrameSize(retriever)
            val stepMs = sampleStep(targetFps)
            var timestampMs = 0L
            while (timestampMs <= durationMs) {
                currentCoroutineContext().ensureActive()
                val bitmap = scaledFrameAt(
                    retriever,
                    timestampMs,
                    videoSize,
                    MAX_DETECTION_DIMEN
                )
                if (bitmap != null) {
                    framesSampled++
                    try {
                        val landmarks = scaleLandmarksToSource(
                            detectSinglePose(bitmap),
                            bitmap,
                            videoSize
                        )
                        if (landmarks.isNotEmpty()) {
                            framesWithPose++
                            val sample = SinglePoseSample(
                                pose = FramePose(timestampMs, landmarks),
                                width = videoSize?.width ?: bitmap.width,
                                height = videoSize?.height ?: bitmap.height
                            )
                            samples += sample
                            val quality = poseQuality(landmarks)
                            if (quality > bestFrameQuality) {
                                bestFrameQuality = quality
                                bestSample = sample
                            }
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
                reportProgress(
                    timestampMs = timestampMs,
                    durationMs = durationMs,
                    onProgress = onProgress,
                    end = DETECTION_PROGRESS_END
                )
                timestampMs += stepMs
            }

            val replaySamples = evenlySpaced(samples, MAX_REPLAY_FRAMES)
            replaySamples.forEachIndexed { replayIndex, sample ->
                currentCoroutineContext().ensureActive()
                val original = scaledFrameAt(
                    retriever,
                    sample.pose.timestampMs,
                    videoSize,
                    MAX_ANIM_DIMEN
                )
                if (original != null) {
                    try {
                        val scaleX = original.width.toFloat() / sample.width.coerceAtLeast(1)
                        val scaleY = original.height.toFloat() / sample.height.coerceAtLeast(1)
                        animationFrames += AnimationFrame(
                            bitmap = original.copy(Bitmap.Config.ARGB_8888, false),
                            pose = scalePose(sample.pose, scaleX, scaleY)
                        )
                    } finally {
                        original.recycle()
                    }
                }
                val replayFraction = (replayIndex + 1f) / replaySamples.size.coerceAtLeast(1)
                onProgress(
                    DETECTION_PROGRESS_END +
                        replayFraction * (1f - DETECTION_PROGRESS_END)
                )
            }

            bestSample?.let { sample ->
                val original = scaledFrameAt(
                    retriever,
                    sample.pose.timestampMs,
                    videoSize,
                    MAX_KEY_FRAME_DIMEN
                )
                if (original != null) {
                    try {
                        val scaleX = original.width.toFloat() / sample.width.coerceAtLeast(1)
                        val scaleY = original.height.toFloat() / sample.height.coerceAtLeast(1)
                        keyFrame = original.copy(Bitmap.Config.ARGB_8888, false)
                        keyFramePose = scalePose(
                            sample.pose,
                            scaleX,
                            scaleY
                        )
                    } finally {
                        original.recycle()
                    }
                }
            }
            onProgress(1f)
        } catch (cancellation: CancellationException) {
            animationFrames.forEach { frame -> frame.bitmap.recycle() }
            keyFrame?.recycle()
            throw cancellation
        } catch (error: Exception) {
            animationFrames.forEach { frame -> frame.bitmap.recycle() }
            keyFrame?.recycle()
            throw IllegalStateException(
                "On-device pose tracking could not process this video. Try a shorter MP4 clip and analyze it again.",
                error
            )
        } finally {
            runCatching { retriever.release() }
        }
        val timeline = samples.map { it.pose }
        return AnalysisResult(
            framesSampled = framesSampled,
            framesWithPose = framesWithPose,
            timeline = timeline,
            keyFrame = keyFrame,
            keyFramePose = keyFramePose,
            animationFrames = animationFrames,
            durationMs = durationMs,
            athleteTracking = AthleteTrackingInfo(
                mode = AthleteTrackingMode.SINGLE_PERSON,
                matchScore = if (timeline.isEmpty()) 0f else 1f,
                maxPeopleDetected = if (timeline.isEmpty()) 0 else 1,
                trackedFrames = timeline.size
            )
        )
    }

    private data class SinglePoseSample(
        val pose: FramePose,
        val width: Int,
        val height: Int
    )

    private suspend fun analyzeBatting(
        videoUri: Uri,
        targetFps: Int,
        onProgress: (Float) -> Unit
    ): AnalysisResult {
        val retriever = MediaMetadataRetriever()
        // Keep only coordinates during the full scan. Retaining a 480 px ARGB replay bitmap at
        // 10 fps can consume hundreds of megabytes on an ordinary phone video.
        val frames = mutableListOf<MultiPoseFrame>()
        val animationFrames = mutableListOf<AnimationFrame>()
        var framesSampled = 0
        var durationMs = 0L
        var keyFrame: Bitmap? = null
        var keyFramePose: FramePose? = null
        var detector: BatterPoseDetector? = null
        var equipmentDetector: BatBallDetector? = null
        val trackedObjects = mutableListOf<TrackedObject>()
        var objectDetectionFrames = 0
        try {
            retriever.setDataSource(context, videoUri)
            durationMs = duration(retriever)
            if (durationMs > MAX_BATTING_CLIP_MS) {
                throw BattingClipTooLongException(
                    "Trim batting video to 30 seconds or less and keep one complete swing in the clip."
                )
            }
            val videoSize = videoFrameSize(retriever)
            detector = BatterPoseDetector(context)
            equipmentDetector = runCatching { BatBallDetector(context) }.getOrNull()
            val stepMs = sampleStep(targetFps)
            var timestampMs = 0L
            while (timestampMs <= durationMs) {
                currentCoroutineContext().ensureActive()
                framesSampled++
                val bitmap = scaledFrameAt(
                    retriever,
                    timestampMs,
                    videoSize,
                    MAX_DETECTION_DIMEN
                )
                if (bitmap != null) {
                    try {
                        val poses = detector.detect(bitmap, timestampMs).map { pose ->
                            scaleLandmarksToSource(pose, bitmap, videoSize)
                        }
                        val sourceWidth = videoSize?.width ?: bitmap.width
                        val sourceHeight = videoSize?.height ?: bitmap.height
                        val activeEquipmentDetector = equipmentDetector
                        if (activeEquipmentDetector != null) {
                            try {
                                trackedObjects += activeEquipmentDetector.detect(
                                    bitmap = bitmap,
                                    timestampMs = timestampMs,
                                    sourceWidth = sourceWidth,
                                    sourceHeight = sourceHeight
                                )
                                objectDetectionFrames++
                            } catch (_: RuntimeException) {
                                runCatching { activeEquipmentDetector.close() }
                                equipmentDetector = null
                            }
                        }
                        // Empty frames are intentional: they make track coverage and long gaps honest.
                        frames += MultiPoseFrame(
                            timestampMs = timestampMs,
                            width = videoSize?.width ?: bitmap.width,
                            height = videoSize?.height ?: bitmap.height,
                            poses = poses
                        )
                    } finally {
                        bitmap.recycle()
                    }
                } else {
                    // Keep failed decode attempts in the sequence. Dropping them would compress
                    // time and make track coverage/continuity look better than the evidence.
                    frames += MultiPoseFrame(
                        timestampMs = timestampMs,
                        width = videoSize?.width ?: 1,
                        height = videoSize?.height ?: 1,
                        poses = emptyList()
                    )
                }
                reportProgress(
                    timestampMs = timestampMs,
                    durationMs = durationMs,
                    onProgress = onProgress,
                    end = DETECTION_PROGRESS_END
                )
                timestampMs += stepMs
            }
            // Inference is complete; release the native model before replay/key-frame decoding.
            detector.close()
            detector = null

            val selection = BatterPoseSelector().select(frames)
            if (!selection.accepted) {
                onProgress(1f)
                return AnalysisResult(
                    framesSampled = framesSampled,
                    framesWithPose = 0,
                    timeline = emptyList(),
                    durationMs = durationMs,
                    athleteTracking = AthleteTrackingInfo(
                        mode = AthleteTrackingMode.BATTER_NOT_CONFIDENT,
                        matchScore = selection.matchScore,
                        maxPeopleDetected = selection.maxPeopleDetected,
                        trackedFrames = 0,
                        candidateTrackCount = selection.candidateTrackCount,
                        trackCoverage = selection.coverage,
                        landmarkCompleteness = selection.completeness,
                        gripEvidence = selection.gripEvidence,
                        motionEvidence = selection.motionEvidence,
                        rawMotionEvidence = selection.rawMotionEvidence,
                        transverseEvidence = selection.transverseEvidence,
                        winnerMargin = selection.winnerMargin
                    ),
                    trackedObjects = trackedObjects,
                    objectDetectionFrames = objectDetectionFrames
                )
            }

            val selectedEntries = selection.posesByFrameIndex.entries.sortedBy { it.key }
            val timeline = selectedEntries.map { it.value }
            val keyPose = mostUsefulBattingFrame(timeline)
            val actionReplayEntries = keyPose?.let { peak ->
                selectedEntries.filter { entry ->
                    abs(entry.value.timestampMs - peak.timestampMs) <= BATTING_REPLAY_RADIUS_MS
                }.takeIf { it.size >= MIN_ACTION_REPLAY_FRAMES }
            } ?: selectedEntries
            val replayEntries = evenlySpaced(actionReplayEntries, MAX_BATTING_REPLAY_FRAMES)
            replayEntries.forEachIndexed { replayIndex, entry ->
                currentCoroutineContext().ensureActive()
                val frameInfo = frames[entry.key]
                val original = scaledFrameAt(
                    retriever,
                    entry.value.timestampMs,
                    videoSize,
                    MAX_ANIM_DIMEN
                )
                if (original != null) {
                    try {
                        val scaleX = original.width.toFloat() /
                            frameInfo.width.coerceAtLeast(1)
                        val scaleY = original.height.toFloat() /
                            frameInfo.height.coerceAtLeast(1)
                        animationFrames += AnimationFrame(
                            bitmap = original.copy(Bitmap.Config.ARGB_8888, false),
                            pose = scalePose(entry.value, scaleX, scaleY)
                        )
                    } finally {
                        original.recycle()
                    }
                }
                val replayFraction = (replayIndex + 1f) / replayEntries.size.coerceAtLeast(1)
                onProgress(
                    DETECTION_PROGRESS_END +
                        replayFraction * (1f - DETECTION_PROGRESS_END)
                )
            }

            val keySample = keyPose?.let { pose ->
                frames.firstOrNull { it.timestampMs == pose.timestampMs }
            }
            if (keyPose != null && keySample != null) {
                val original = scaledFrameAt(
                    retriever,
                    keyPose.timestampMs,
                    videoSize,
                    MAX_KEY_FRAME_DIMEN
                )
                if (original != null) {
                    try {
                        val scaleX = original.width.toFloat() / keySample.width.coerceAtLeast(1)
                        val scaleY = original.height.toFloat() / keySample.height.coerceAtLeast(1)
                        keyFrame = original.copy(Bitmap.Config.ARGB_8888, false)
                        keyFramePose = scalePose(
                            keyPose,
                            scaleX,
                            scaleY
                        )
                    } finally {
                        original.recycle()
                    }
                }
            }
            onProgress(1f)
            return AnalysisResult(
                framesSampled = framesSampled,
                framesWithPose = timeline.size,
                timeline = timeline,
                keyFrame = keyFrame,
                keyFramePose = keyFramePose,
                animationFrames = animationFrames,
                durationMs = durationMs,
                athleteTracking = AthleteTrackingInfo(
                    mode = AthleteTrackingMode.BATTER_LOCKED,
                    matchScore = selection.matchScore,
                    maxPeopleDetected = selection.maxPeopleDetected,
                    trackedFrames = timeline.size,
                    candidateTrackCount = selection.candidateTrackCount,
                    trackCoverage = selection.coverage,
                    landmarkCompleteness = selection.completeness,
                    gripEvidence = selection.gripEvidence,
                    motionEvidence = selection.motionEvidence,
                    rawMotionEvidence = selection.rawMotionEvidence,
                    transverseEvidence = selection.transverseEvidence,
                    winnerMargin = selection.winnerMargin
                ),
                trackedObjects = trackedObjects,
                objectDetectionFrames = objectDetectionFrames
            )
        } catch (cancellation: CancellationException) {
            animationFrames.forEach { frame -> frame.bitmap.recycle() }
            keyFrame?.recycle()
            throw cancellation
        } catch (error: BattingClipTooLongException) {
            throw error
        } catch (error: RuntimeException) {
            animationFrames.forEach { frame -> frame.bitmap.recycle() }
            keyFrame?.recycle()
            throw IllegalStateException(
                "Offline Batter Lock could not process this video. Try a shorter MP4 clip and analyze it again.",
                error
            )
        } finally {
            runCatching { detector?.close() }
            runCatching { equipmentDetector?.close() }
            runCatching { retriever.release() }
        }
    }

    private suspend fun detectSinglePose(bitmap: Bitmap): List<LandmarkPoint> =
        // ML Kit's Task does not expose cancellation for this bitmap. Await its callback before
        // recycling the frame, then the scan loop observes coroutine cancellation immediately.
        suspendCoroutine { continuation ->
            singlePoseDetector.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { pose ->
                    continuation.resume(
                        pose.allPoseLandmarks.map { landmark ->
                            LandmarkPoint(
                                type = landmark.landmarkType,
                                x = landmark.position.x,
                                y = landmark.position.y,
                                inFrameLikelihood = landmark.inFrameLikelihood,
                                z = landmark.position3D.z
                            )
                        }
                    )
                }
                .addOnFailureListener { error ->
                    continuation.resumeWithException(error)
                }
        }

    fun close() {
        if (singlePoseDetectorDelegate.isInitialized()) singlePoseDetector.close()
    }

    private fun duration(retriever: MediaMetadataRetriever): Long =
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L

    private fun frameAt(retriever: MediaMetadataRetriever, timestampMs: Long): Bitmap? =
        retriever.getFrameAtTime(
            timestampMs * 1_000L,
            MediaMetadataRetriever.OPTION_CLOSEST
        )

    private data class VideoFrameSize(val width: Int, val height: Int)

    private fun videoFrameSize(retriever: MediaMetadataRetriever): VideoFrameSize? {
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull() ?: return null
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull() ?: return null
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull() ?: 0
        val oriented = orientedVideoFrameSize(width, height, rotation) ?: return null
        return VideoFrameSize(oriented.first, oriented.second)
    }

    private fun scaleLandmarksToSource(
        landmarks: List<LandmarkPoint>,
        decoded: Bitmap,
        sourceSize: VideoFrameSize?
    ): List<LandmarkPoint> = if (sourceSize == null) {
        landmarks
    } else {
        scaleLandmarksToFrame(
            landmarks = landmarks,
            decodedWidth = decoded.width,
            decodedHeight = decoded.height,
            sourceWidth = sourceSize.width,
            sourceHeight = sourceSize.height
        )
    }

    /** Decode close to the model/display size instead of materializing every 4K source frame. */
    private fun scaledFrameAt(
        retriever: MediaMetadataRetriever,
        timestampMs: Long,
        sourceSize: VideoFrameSize?,
        maxDimension: Int
    ): Bitmap? {
        val decoded = if (sourceSize != null && maxOf(sourceSize.width, sourceSize.height) > maxDimension) {
            // Android preserves the source aspect ratio inside these bounds. A square bound also
            // handles 90°/270° rotation metadata without accidentally shrinking portrait clips.
            retriever.getScaledFrameAtTime(
                timestampMs * 1_000L,
                MediaMetadataRetriever.OPTION_CLOSEST,
                maxDimension,
                maxDimension
            )
        } else {
            frameAt(retriever, timestampMs)
        } ?: return null

        val actualLongest = maxOf(decoded.width, decoded.height)
        if (actualLongest <= maxDimension) return decoded
        val factor = maxDimension.toFloat() / actualLongest
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * factor).roundToInt().coerceAtLeast(1),
            (decoded.height * factor).roundToInt().coerceAtLeast(1),
            true
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private fun sampleStep(targetFps: Int): Long =
        (1_000L / targetFps.coerceAtLeast(1)).coerceAtLeast(1L)

    private fun reportProgress(
        timestampMs: Long,
        durationMs: Long,
        onProgress: (Float) -> Unit,
        start: Float = 0f,
        end: Float = 1f
    ) {
        if (durationMs > 0L) {
            val fraction = (timestampMs.toFloat() / durationMs).coerceIn(0f, 1f)
            onProgress(start + fraction * (end - start))
        }
    }

    private fun poseQuality(landmarks: List<LandmarkPoint>): Double {
        val reliable = landmarks.filter { it.inFrameLikelihood >= 0.5f }
        return reliable.size + reliable.map { it.inFrameLikelihood.toDouble() }.averageOrZero()
    }

    private fun mostUsefulBattingFrame(timeline: List<FramePose>): FramePose? {
        if (timeline.isEmpty()) return null
        val peakIndex = coordinatedBattingPeakIndex(timeline)
        return peakIndex?.let(timeline::get)
            ?: timeline.maxByOrNull { poseQuality(it.landmarks) }
    }

    private companion object {
        const val MAX_DETECTION_DIMEN = 840
        const val MAX_ANIM_DIMEN = 480
        const val MAX_KEY_FRAME_DIMEN = 1280
        const val MIN_BATTING_FPS = 10
        const val MAX_BATTING_CLIP_MS = 30_000L
        const val MAX_REPLAY_FRAMES = 48
        const val MAX_BATTING_REPLAY_FRAMES = 24
        const val BATTING_REPLAY_RADIUS_MS = 1_500L
        const val MIN_ACTION_REPLAY_FRAMES = 8
        const val DETECTION_PROGRESS_END = 0.90f
    }

    private fun scalePose(frame: FramePose, scaleX: Float, scaleY: Float): FramePose =
        if (scaleX == 1f && scaleY == 1f) frame else frame.copy(
            landmarks = frame.landmarks.map { point ->
                point.copy(x = point.x * scaleX, y = point.y * scaleY, z = point.z * scaleX)
            }
        )

    private fun <T> evenlySpaced(values: List<T>, limit: Int): List<T> {
        if (values.size <= limit || limit < 2) return values
        return (0 until limit).map { position ->
            val index = (position * values.lastIndex.toDouble() / (limit - 1))
                .roundToInt()
            values[index]
        }.distinct()
    }

    private fun List<Double>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()

    private class BattingClipTooLongException(message: String) : IllegalArgumentException(message)
}

internal fun scaleLandmarksToFrame(
    landmarks: List<LandmarkPoint>,
    decodedWidth: Int,
    decodedHeight: Int,
    sourceWidth: Int,
    sourceHeight: Int
): List<LandmarkPoint> {
    if (decodedWidth == sourceWidth && decodedHeight == sourceHeight) return landmarks
    val scaleX = sourceWidth.toFloat() / decodedWidth.coerceAtLeast(1)
    val scaleY = sourceHeight.toFloat() / decodedHeight.coerceAtLeast(1)
    return landmarks.map { point ->
        point.copy(x = point.x * scaleX, y = point.y * scaleY, z = point.z * scaleX)
    }
}

internal fun orientedVideoFrameSize(width: Int, height: Int, rotationDegrees: Int): Pair<Int, Int>? {
    if (width <= 0 || height <= 0) return null
    val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
    return if (normalizedRotation == 90 || normalizedRotation == 270) {
        height to width
    } else {
        width to height
    }
}
