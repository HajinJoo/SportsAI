package com.example.sportsai

import android.app.Instrumentation
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sportsai.data.PoseAnalyzer
import com.example.sportsai.data.SwingReferenceArtifactCodec
import com.example.sportsai.data.SwingReferenceFeatureExtractor
import com.example.sportsai.data.SwingReferenceFeatures
import com.example.sportsai.data.SwingReferenceTrainer
import com.example.sportsai.data.TechniqueAnalyzer
import com.example.sportsai.model.AnalysisResult
import com.example.sportsai.model.AthleteTrackingMode
import com.example.sportsai.model.Sport
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Optional connected-device batch job for rights-cleared local footage. Raw videos stay outside
 * the app; only an aggregate reference artifact and a private per-clip audit report are written.
 */
class SwingReferenceTrainingInstrumentedTest {

    @Test
    fun trainSuppliedSwingDirectory() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val directoryText = arguments.getString(ARG_VIDEO_DIRECTORY)
        assumeTrue(
            "No $ARG_VIDEO_DIRECTORY supplied; optional reference training skipped",
            !directoryText.isNullOrBlank()
        )

        val videoDirectory = fileFromArgument(requireNotNull(directoryText))
        val expectedCount = arguments.getString(ARG_EXPECTED_COUNT)?.toIntOrNull()
            ?: DEFAULT_EXPECTED_COUNT
        val profileId = arguments.getString(ARG_PROFILE_ID) ?: DEFAULT_PROFILE_ID
        val rightsStatus = arguments.getString(ARG_RIGHTS_STATUS) ?: PROTOTYPE_RIGHTS_STATUS
        require(rightsStatus in ALLOWED_RIGHTS_STATUSES) {
            "$ARG_RIGHTS_STATUS must be one of ${ALLOWED_RIGHTS_STATUSES.joinToString()}."
        }
        val videos = videoDirectory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension.equals("mp4", ignoreCase = true) }
            .sortedWith(compareBy(::swingNumber, File::getName))
        require(videos.size == expectedCount) {
            "Expected $expectedCount MP4 files in ${videoDirectory.path}, found ${videos.size}."
        }

        val analyzer = PoseAnalyzer(instrumentation.targetContext)
        val diagnostics = mutableListOf<ClipDiagnostic>()
        try {
            videos.forEach { video ->
                val diagnostic = analyzeClip(video, analyzer)
                diagnostics += diagnostic
                instrumentation.sendStatus(
                    0,
                    Bundle().apply {
                        putString(
                            Instrumentation.REPORT_KEY_STREAMRESULT,
                            "Profiled ${diagnostic.fileName}: " +
                                "${if (diagnostic.accepted) "accepted" else diagnostic.rejectionReason} " +
                                "in ${diagnostic.analysisElapsedMs}ms\n"
                        )
                    }
                )
            }
        } finally {
            analyzer.close()
        }

        val outputDirectory = File(
            requireNotNull(videoDirectory.parentFile),
            OUTPUT_DIRECTORY
        ).apply { mkdirs() }
        val reportFile = File(outputDirectory, TRAINING_REPORT_FILE)
        val acceptedFeatures = diagnostics.mapNotNull(ClipDiagnostic::features)
        if (acceptedFeatures.size < MIN_ACCEPTED_EXAMPLES) {
            reportFile.writeText(
                trainingReportJson(
                    profileId = profileId,
                    rightsStatus = rightsStatus,
                    expectedCount = expectedCount,
                    diagnostics = diagnostics,
                    similarities = emptyMap()
                ).toString(2)
            )
            error(
                "Only ${acceptedFeatures.size} clips passed; at least $MIN_ACCEPTED_EXAMPLES are required. " +
                    "Pull $reportFile for rejection details."
            )
        }

        val model = SwingReferenceTrainer.train(acceptedFeatures)
        val artifact = SwingReferenceArtifactCodec.create(profileId, model)
        val artifactText = SwingReferenceArtifactCodec.encode(artifact)
        check(SwingReferenceArtifactCodec.decode(artifactText) == artifact) {
            "Generated swing reference artifact did not round-trip."
        }
        val similarities = diagnostics.mapNotNull { diagnostic ->
            diagnostic.features?.let { diagnostic.fileName to model.similarity(it) }
        }.toMap()
        val artifactFile = File(outputDirectory, REFERENCE_ARTIFACT_FILE)
        artifactFile.writeText(artifactText)
        reportFile.writeText(
            trainingReportJson(
                profileId = profileId,
                rightsStatus = rightsStatus,
                expectedCount = expectedCount,
                diagnostics = diagnostics,
                similarities = similarities
            ).toString(2)
        )

        assertEquals(
            "Every curated clip must pass before the reference can be integrated. Pull $reportFile for details.",
            expectedCount,
            acceptedFeatures.size
        )
        val outliers = similarities.filterValues { it < MIN_EXAMPLE_SIMILARITY }
        assertTrue(
            "Reference examples are not comparable: $outliers. Review camera view and clip labels in $reportFile.",
            outliers.isEmpty()
        )
        println("SWING_REFERENCE_ARTIFACT=${artifactFile.absolutePath}")
        println("SWING_REFERENCE_REPORT=${reportFile.absolutePath}")
    }

    private suspend fun analyzeClip(video: File, analyzer: PoseAnalyzer): ClipDiagnostic {
        val startedAt = SystemClock.elapsedRealtime()
        var result: AnalysisResult? = null
        return try {
            result = analyzer.analyze(Uri.fromFile(video), Sport.BASEBALL_BAT)
            val techniqueReport = TechniqueAnalyzer().analyze(result, Sport.BASEBALL_BAT)
            val features = SwingReferenceFeatureExtractor.extract(techniqueReport)
            val missingMetrics = SwingReferenceFeatureExtractor.requiredMetricNames -
                techniqueReport.metricScores.keys
            val rejectionReason = when {
                result.athleteTracking.mode != AthleteTrackingMode.BATTER_LOCKED ->
                    "batter_lock_not_confident"
                features == null -> "missing_metrics:${missingMetrics.joinToString(",")}"
                else -> null
            }
            ClipDiagnostic(
                fileName = video.name,
                sha256 = video.sha256(),
                accepted = rejectionReason == null,
                rejectionReason = rejectionReason,
                durationMs = result.durationMs,
                framesSampled = result.framesSampled,
                timelineFrames = result.timeline.size,
                detectionRate = result.detectionRate,
                trackingMode = result.athleteTracking.mode.name,
                matchScore = result.athleteTracking.matchScore,
                trackCoverage = result.athleteTracking.trackCoverage,
                landmarkCompleteness = result.athleteTracking.landmarkCompleteness,
                motionEvidence = result.athleteTracking.motionEvidence,
                metricScores = techniqueReport.metricScores,
                features = features,
                analysisElapsedMs = SystemClock.elapsedRealtime() - startedAt
            )
        } catch (error: Exception) {
            ClipDiagnostic(
                fileName = video.name,
                sha256 = video.sha256(),
                accepted = false,
                rejectionReason = "analysis_error:${error.javaClass.simpleName}",
                errorMessage = error.message?.take(MAX_ERROR_LENGTH),
                analysisElapsedMs = SystemClock.elapsedRealtime() - startedAt
            )
        } finally {
            result?.keyFrame?.recycle()
            result?.animationFrames?.forEach { frame -> frame.bitmap.recycle() }
        }
    }

    private fun trainingReportJson(
        profileId: String,
        rightsStatus: String,
        expectedCount: Int,
        diagnostics: List<ClipDiagnostic>,
        similarities: Map<String, Int>
    ): JSONObject = JSONObject()
        .put("schemaVersion", TRAINING_REPORT_SCHEMA_VERSION)
        .put("profileId", profileId)
        .put("referenceLabel", SwingReferenceArtifactCodec.REFERENCE_LABEL)
        .put("rightsStatus", rightsStatus)
        .put("expectedClipCount", expectedCount)
        .put("acceptedClipCount", diagnostics.count(ClipDiagnostic::accepted))
        .put("rejectedClipCount", diagnostics.count { !it.accepted })
        .put("cameraView", "unverified")
        .put("handedness", "unverified")
        .put(
            "clips",
            JSONArray().apply {
                diagnostics.forEach { diagnostic ->
                    put(diagnostic.toJson(similarities[diagnostic.fileName]))
                }
            }
        )

    private fun ClipDiagnostic.toJson(similarity: Int?): JSONObject = JSONObject()
        .put("fileName", fileName)
        .put("sha256", sha256)
        .put("accepted", accepted)
        .put("rejectionReason", rejectionReason ?: JSONObject.NULL)
        .put("errorMessage", errorMessage ?: JSONObject.NULL)
        .put("durationMs", durationMs)
        .put("framesSampled", framesSampled)
        .put("timelineFrames", timelineFrames)
        .put("detectionRate", detectionRate.toDouble())
        .put("trackingMode", trackingMode)
        .put("matchScore", matchScore.toDouble())
        .put("trackCoverage", trackCoverage.toDouble())
        .put("landmarkCompleteness", landmarkCompleteness.toDouble())
        .put("motionEvidence", motionEvidence.toDouble())
        .put("metricScores", JSONObject(metricScores))
        .put("analysisElapsedMs", analysisElapsedMs)
        .put("referenceSimilarity", similarity ?: JSONObject.NULL)

    private fun fileFromArgument(value: String): File = if (value.startsWith("file:")) {
        File(requireNotNull(Uri.parse(value).path))
    } else {
        File(value)
    }

    private fun swingNumber(file: File): Int =
        Regex("(\\d+)").find(file.nameWithoutExtension)?.value?.toIntOrNull() ?: Int.MAX_VALUE

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte ->
            String.format(Locale.US, "%02x", byte.toInt() and 0xff)
        }
    }

    private data class ClipDiagnostic(
        val fileName: String,
        val sha256: String,
        val accepted: Boolean,
        val rejectionReason: String?,
        val errorMessage: String? = null,
        val durationMs: Long = 0L,
        val framesSampled: Int = 0,
        val timelineFrames: Int = 0,
        val detectionRate: Float = 0f,
        val trackingMode: String = AthleteTrackingMode.BATTER_NOT_CONFIDENT.name,
        val matchScore: Float = 0f,
        val trackCoverage: Float = 0f,
        val landmarkCompleteness: Float = 0f,
        val motionEvidence: Float = 0f,
        val metricScores: Map<String, Int> = emptyMap(),
        val features: SwingReferenceFeatures? = null,
        val analysisElapsedMs: Long = 0L
    )

    private companion object {
        const val ARG_VIDEO_DIRECTORY = "swingVideoDirectory"
        const val ARG_EXPECTED_COUNT = "expectedSwingCount"
        const val ARG_PROFILE_ID = "swingProfileId"
        const val ARG_RIGHTS_STATUS = "swingRightsStatus"
        const val DEFAULT_EXPECTED_COUNT = 10
        const val DEFAULT_PROFILE_ID = "curated-swings-v1"
        const val PROTOTYPE_RIGHTS_STATUS = "prototype-only"
        const val LICENSED_RIGHTS_STATUS = "licensed-commercial-ml"
        val ALLOWED_RIGHTS_STATUSES = setOf(PROTOTYPE_RIGHTS_STATUS, LICENSED_RIGHTS_STATUS)
        const val MIN_ACCEPTED_EXAMPLES = 5
        const val MIN_EXAMPLE_SIMILARITY = 40
        const val MAX_ERROR_LENGTH = 240
        const val OUTPUT_DIRECTORY = "reference-training"
        const val REFERENCE_ARTIFACT_FILE = "swing-reference-profile.json"
        const val TRAINING_REPORT_FILE = "swing-reference-training-report.json"
        const val TRAINING_REPORT_SCHEMA_VERSION = 1
    }
}