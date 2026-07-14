package com.example.sportsai.data

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import com.example.sportsai.model.HighlightClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/** Creates app-private MP4 highlight files without uploading the source video. */
class VideoClipExporter(context: Context) {

    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "highlights")

    suspend fun export(
        sourceUri: Uri,
        clip: HighlightClip,
        sessionId: Long
    ): HighlightClip = withContext(Dispatchers.IO) {
        require(clip.endMs > clip.startMs) { "Highlight end must be after its start" }
        val outputDirectory = safeExistingParent(clip.videoPath)
            ?: File(root, sessionId.toString()).apply { mkdirs() }
        val target = File(outputDirectory, "highlight_${clip.id}.mp4")
        val temporary = File(outputDirectory, "highlight_${clip.id}_${System.nanoTime()}.tmp.mp4")

        try {
            remuxRange(sourceUri, clip.startMs, clip.endMs, temporary)
            check(temporary.length() > 0L) { "The highlight video was empty" }
            if (target.exists() && !target.delete()) {
                error("Could not replace the previous highlight")
            }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            clip.copy(videoPath = target.absolutePath)
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    fun deleteSession(sessionId: Long) {
        val directory = File(root, sessionId.toString())
        if (directory.isDirectory && isInsideRoot(directory)) {
            directory.deleteRecursively()
        }
    }

    private fun remuxRange(sourceUri: Uri, startMs: Long, endMs: Long, output: File) {
        output.parentFile?.mkdirs()
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        try {
            extractor.setDataSource(appContext, sourceUri, null)
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            readRotation(sourceUri)?.let(muxer::setOrientationHint)

            val trackMap = mutableMapOf<Int, Int>()
            var bufferSize = DEFAULT_BUFFER_SIZE
            for (trackIndex in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(trackIndex)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue
                extractor.selectTrack(trackIndex)
                trackMap[trackIndex] = muxer.addTrack(format)
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    bufferSize = maxOf(bufferSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                }
            }
            check(trackMap.isNotEmpty()) { "No audio or video track was found" }

            muxer.start()
            muxerStarted = true
            val buffer = ByteBuffer.allocateDirect(bufferSize.coerceAtMost(MAX_BUFFER_SIZE))
            val info = MediaCodec.BufferInfo()
            val requestedStartUs = startMs.coerceAtLeast(0L) * 1_000L
            val requestedEndUs = endMs * 1_000L
            extractor.seekTo(requestedStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            var firstSampleUs = -1L
            var samplesWritten = 0
            while (true) {
                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs < 0L || sampleTimeUs > requestedEndUs) break
                val sourceTrack = extractor.sampleTrackIndex
                val destinationTrack = trackMap[sourceTrack]
                if (destinationTrack == null) {
                    if (!extractor.advance()) break
                    continue
                }

                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                if (firstSampleUs < 0L) firstSampleUs = sampleTimeUs
                val extractorFlags = extractor.sampleFlags
                check(extractorFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) {
                    "Encrypted videos cannot be cut into a local highlight"
                }
                var codecFlags = 0
                if (extractorFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                }
                if (extractorFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0) {
                    codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
                }
                info.set(
                    0,
                    sampleSize,
                    (sampleTimeUs - firstSampleUs).coerceAtLeast(0L),
                    codecFlags
                )
                muxer.writeSampleData(destinationTrack, buffer, info)
                samplesWritten++
                if (!extractor.advance()) break
            }
            check(samplesWritten > 0) { "No video samples were found inside that range" }
        } finally {
            extractor.release()
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
        }
    }

    private fun readRotation(sourceUri: Uri): Int? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(appContext, sourceUri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()
        } finally {
            retriever.release()
        }
    }.getOrNull()

    private fun safeExistingParent(videoPath: String): File? {
        if (videoPath.isBlank()) return null
        val parent = File(videoPath).parentFile ?: return null
        return parent.takeIf { it.isDirectory && isInsideRoot(it) }
    }

    private fun isInsideRoot(file: File): Boolean = runCatching {
        file.canonicalPath.startsWith(root.canonicalPath + File.separator)
    }.getOrDefault(false)

    private companion object {
        const val DEFAULT_BUFFER_SIZE = 4 * 1024 * 1024
        const val MAX_BUFFER_SIZE = 32 * 1024 * 1024
    }
}
