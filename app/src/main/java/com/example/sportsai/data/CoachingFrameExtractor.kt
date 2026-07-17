package com.example.sportsai.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.sportsai.model.AnalysisResult
import com.example.sportsai.model.AnimationFrame
import com.example.sportsai.model.FramePose
import com.example.sportsai.model.HighlightClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Re-opens only the detected action window so Gemini receives clear, action-focused frames. */
class CoachingFrameExtractor(private val context: Context) {

    suspend fun extract(
        videoUri: Uri,
        result: AnalysisResult,
        actionWindow: HighlightClip?,
        maxFrames: Int = MAX_FRAMES
    ): List<AnimationFrame> = withContext(Dispatchers.IO) {
        val poses = selectCoachingPoses(result.timeline, actionWindow, maxFrames)
        if (poses.isEmpty()) return@withContext emptyList()

        val retriever = MediaMetadataRetriever()
        val extracted = mutableListOf<AnimationFrame>()
        try {
            retriever.setDataSource(context, videoUri)
            poses.forEach { pose ->
                val source = retriever.getFrameAtTime(
                    pose.timestampMs * 1_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST
                ) ?: return@forEach

                val longest = maxOf(source.width, source.height).toFloat()
                val scale = if (longest > MAX_IMAGE_DIMENSION) {
                    MAX_IMAGE_DIMENSION / longest
                } else {
                    1f
                }
                val bitmap = if (scale < 1f) {
                    Bitmap.createScaledBitmap(
                        source,
                        (source.width * scale).roundToInt().coerceAtLeast(1),
                        (source.height * scale).roundToInt().coerceAtLeast(1),
                        true
                    ).also { source.recycle() }
                } else {
                    source
                }
                extracted += AnimationFrame(bitmap, scalePose(pose, scale))
            }
        } catch (_: Exception) {
            extracted.forEach { it.bitmap.recycle() }
            return@withContext emptyList()
        } finally {
            retriever.release()
        }
        extracted
    }

    private fun scalePose(frame: FramePose, factor: Float): FramePose =
        if (factor >= 1f) frame else frame.copy(
            landmarks = frame.landmarks.map { point ->
                point.copy(x = point.x * factor, y = point.y * factor, z = point.z * factor)
            }
        )

    private companion object {
        const val MAX_FRAMES = 8
        const val MAX_IMAGE_DIMENSION = 1_280f
    }
}

/** Pure selection logic kept separately so action-window coverage is regression tested. */
internal fun selectCoachingPoses(
    timeline: List<FramePose>,
    actionWindow: HighlightClip?,
    maxFrames: Int
): List<FramePose> {
    if (timeline.isEmpty() || maxFrames <= 0) return emptyList()
    val inAction = actionWindow?.let { clip ->
        timeline.filter { it.timestampMs in clip.startMs..clip.endMs }
    }.orEmpty()
    val candidates = inAction.ifEmpty { timeline }
    if (candidates.size <= maxFrames) return candidates
    if (maxFrames == 1) return listOf(candidates[candidates.size / 2])

    return (0 until maxFrames).map { index ->
        val sourceIndex = (index.toDouble() * candidates.lastIndex / (maxFrames - 1))
            .roundToInt()
        candidates[sourceIndex]
    }.distinctBy { it.timestampMs }
}
