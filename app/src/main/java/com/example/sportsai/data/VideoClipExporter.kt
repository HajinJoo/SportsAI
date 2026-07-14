package com.example.sportsai.data

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.example.sportsai.model.HighlightClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Creates precise app-private MP4 highlight files without uploading the source video. */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class VideoClipExporter(context: Context) {

    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "highlights")

    suspend fun export(
        sourceUri: Uri,
        clip: HighlightClip,
        sessionId: Long
    ): HighlightClip {
        require(clip.endMs > clip.startMs) { "Highlight end must be after its start" }
        val (target, temporary) = withContext(Dispatchers.IO) {
            val outputDirectory = safeExistingParent(clip.videoPath)
                ?: File(root, sessionId.toString()).apply { mkdirs() }
            val target = File(outputDirectory, "highlight_${clip.id}.mp4")
            val temporary = File(
                outputDirectory,
                "highlight_${clip.id}_${System.nanoTime()}.tmp.mp4"
            )
            temporary.delete()
            target to temporary
        }

        try {
            exportExactRange(sourceUri, clip.startMs, clip.endMs, temporary)
            return withContext(Dispatchers.IO) {
                check(temporary.length() > 0L) { "The highlight video was empty" }
                if (target.exists() && !target.delete()) {
                    error("Could not replace the previous highlight")
                }
                if (!temporary.renameTo(target)) {
                    temporary.copyTo(target, overwrite = true)
                    temporary.delete()
                }
                clip.copy(videoPath = target.absolutePath)
            }
        } catch (error: Exception) {
            withContext(Dispatchers.IO) { temporary.delete() }
            throw error
        }
    }

    fun deleteSession(sessionId: Long) {
        val directory = File(root, sessionId.toString())
        if (directory.isDirectory && isInsideRoot(directory)) {
            directory.deleteRecursively()
        }
    }

    /**
     * Media3 decodes around non-keyframe boundaries, so the saved file starts at the selected
     * action instead of silently including everything from an older keyframe.
     */
    private suspend fun exportExactRange(
        sourceUri: Uri,
        startMs: Long,
        endMs: Long,
        output: File
    ) = withContext(Dispatchers.Main.immediate) {
        output.parentFile?.mkdirs()
        suspendCancellableCoroutine { continuation ->
            val mediaItem = MediaItem.Builder()
                .setUri(sourceUri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs.coerceAtLeast(0L))
                        .setEndPositionMs(endMs)
                        .build()
                )
                .build()
            val editedMediaItem = EditedMediaItem.Builder(mediaItem).build()
            lateinit var transformer: Transformer
            transformer = Transformer.Builder(appContext)
                .addListener(
                    object : Transformer.Listener {
                        override fun onCompleted(
                            composition: Composition,
                            exportResult: ExportResult
                        ) {
                            if (continuation.isActive) continuation.resume(Unit)
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(exportException)
                            }
                        }
                    }
                )
                .build()
            continuation.invokeOnCancellation {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    transformer.cancel()
                } else {
                    Handler(Looper.getMainLooper()).post(transformer::cancel)
                }
            }
            transformer.start(editedMediaItem, output.absolutePath)
        }
    }

    private fun safeExistingParent(videoPath: String): File? {
        if (videoPath.isBlank()) return null
        val parent = File(videoPath).parentFile ?: return null
        return parent.takeIf { it.isDirectory && isInsideRoot(it) }
    }

    private fun isInsideRoot(file: File): Boolean = runCatching {
        file.canonicalPath.startsWith(root.canonicalPath + File.separator)
    }.getOrDefault(false)
}
