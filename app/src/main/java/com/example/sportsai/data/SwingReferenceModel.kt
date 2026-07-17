package com.example.sportsai.data

import com.example.sportsai.model.AnalysisProfiles
import com.example.sportsai.model.Sport
import com.example.sportsai.model.TechniqueReport
import org.json.JSONObject
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

private const val MIN_REFERENCE_EXAMPLES = 5

/** Dimensionless mechanics measurements used by the positive-only swing reference model. */
internal data class SwingReferenceFeatures(
    val rotationSequence: Double,
    val headStability: Double,
    val swingExtension: Double,
    val lowerBodyLoad: Double,
    val handSpeed: Double
) {
    fun values(): List<Double> = listOf(
        rotationSequence,
        headStability,
        swingExtension,
        lowerBodyLoad,
        handSpeed
    )

    init {
        require(values().all { it.isFinite() && it in 0.0..1.0 }) {
            "Swing reference features must be finite values from 0 to 1."
        }
    }
}

/**
 * Describes similarity to a small set of accepted examples. This is not a good/bad classifier:
 * positive-only examples cannot establish what every poor swing looks like.
 */
internal data class SwingReferenceModel(
    val center: SwingReferenceFeatures,
    val robustScale: SwingReferenceFeatures,
    val exampleCount: Int
) {
    init {
        require(exampleCount >= MIN_REFERENCE_EXAMPLES) {
            "A swing reference model requires at least $MIN_REFERENCE_EXAMPLES accepted examples."
        }
        require(robustScale.values().all { it > 0.0 }) {
            "Swing reference scales must be greater than zero."
        }
    }

    fun similarity(features: SwingReferenceFeatures): Int {
        val squaredDistance = center.values()
            .zip(robustScale.values())
            .zip(features.values())
            .sumOf { (reference, observed) ->
                val (middle, scale) = reference
                ((observed - middle) / scale).coerceIn(-MAX_STANDARD_DISTANCE, MAX_STANDARD_DISTANCE)
                    .pow(2)
            } / FEATURE_COUNT
        val distance = sqrt(squaredDistance)
        return (100.0 * exp(-SIMILARITY_DECAY * distance * distance))
            .toInt()
            .coerceIn(0, 100)
    }

    private companion object {
        const val FEATURE_COUNT = 5.0
        const val MAX_STANDARD_DISTANCE = 4.0
        const val SIMILARITY_DECAY = 0.35
    }
}

internal data class SwingReferenceArtifact(
    val schemaVersion: Int,
    val profileId: String,
    val label: String,
    val analysisProfile: String,
    val model: SwingReferenceModel
)

internal object SwingReferenceArtifactCodec {
    const val SCHEMA_VERSION = 1
    const val REFERENCE_LABEL = "Similarity to curated reference swings"
    private val profileIdPattern = Regex("[a-z0-9][a-z0-9._-]{2,63}")
    private val featureNames = listOf(
        "rotationSequence",
        "headStability",
        "swingExtension",
        "lowerBodyLoad",
        "handSpeed"
    )

    fun create(profileId: String, model: SwingReferenceModel): SwingReferenceArtifact {
        require(profileIdPattern.matches(profileId)) {
            "Reference profile IDs must be 3-64 lowercase letters, digits, dots, dashes, or underscores."
        }
        return SwingReferenceArtifact(
            schemaVersion = SCHEMA_VERSION,
            profileId = profileId,
            label = REFERENCE_LABEL,
            analysisProfile = AnalysisProfiles.offline(Sport.BASEBALL_BAT),
            model = model
        )
    }

    fun encode(artifact: SwingReferenceArtifact): String {
        validate(artifact)
        val features = JSONObject()
        featureNames.forEachIndexed { index, name ->
            features.put(
                name,
                JSONObject()
                    .put("center", artifact.model.center.values()[index])
                    .put("robustScale", artifact.model.robustScale.values()[index])
            )
        }
        return JSONObject()
            .put("schemaVersion", artifact.schemaVersion)
            .put("profileId", artifact.profileId)
            .put("label", artifact.label)
            .put("analysisProfile", artifact.analysisProfile)
            .put("exampleCount", artifact.model.exampleCount)
            .put("features", features)
            .toString(2)
    }

    fun decode(json: String): SwingReferenceArtifact {
        val root = JSONObject(json)
        val features = root.getJSONObject("features")
        val centers = featureNames.map { features.getJSONObject(it).getDouble("center") }
        val scales = featureNames.map { features.getJSONObject(it).getDouble("robustScale") }
        return SwingReferenceArtifact(
            schemaVersion = root.getInt("schemaVersion"),
            profileId = root.getString("profileId"),
            label = root.getString("label"),
            analysisProfile = root.getString("analysisProfile"),
            model = SwingReferenceModel(
                center = features(centers),
                robustScale = features(scales),
                exampleCount = root.getInt("exampleCount")
            )
        ).also(::validate)
    }

    private fun validate(artifact: SwingReferenceArtifact) {
        require(artifact.schemaVersion == SCHEMA_VERSION) {
            "Unsupported swing reference schema ${artifact.schemaVersion}."
        }
        require(profileIdPattern.matches(artifact.profileId)) { "Invalid swing reference profile ID." }
        require(artifact.label == REFERENCE_LABEL) { "Unexpected swing reference label." }
        require(artifact.analysisProfile == AnalysisProfiles.offline(Sport.BASEBALL_BAT)) {
            "Swing reference was generated by an incompatible analysis profile."
        }
    }

    private fun features(values: List<Double>) = SwingReferenceFeatures(
        rotationSequence = values[0],
        headStability = values[1],
        swingExtension = values[2],
        lowerBodyLoad = values[3],
        handSpeed = values[4]
    )
}

internal data class SwingReferenceTrainingResult(
    val model: SwingReferenceModel,
    val acceptedExampleCount: Int,
    val rejectedExampleCount: Int,
    val exampleSimilarities: List<Int>
)

internal object SwingReferenceFeatureExtractor {
    val requiredMetricNames = listOf(
        "Hip Rotation",
        "Ball Tracking",
        "Swing Extension",
        "Lower-Body Load",
        "Bat Speed Potential"
    )

    fun extract(report: TechniqueReport): SwingReferenceFeatures? {
        if (report.sport != Sport.BASEBALL_BAT.displayName) return null
        val scores = report.metricScores
        return SwingReferenceFeatures(
            rotationSequence = scores.normalized(requiredMetricNames[0]) ?: return null,
            headStability = scores.normalized(requiredMetricNames[1]) ?: return null,
            swingExtension = scores.normalized(requiredMetricNames[2]) ?: return null,
            lowerBodyLoad = scores.normalized(requiredMetricNames[3]) ?: return null,
            handSpeed = scores.normalized(requiredMetricNames[4]) ?: return null
        )
    }

    private fun Map<String, Int>.normalized(name: String): Double? =
        get(name)?.takeIf { it in 0..100 }?.div(100.0)
}

internal object SwingReferenceTrainer {
    fun train(examples: List<SwingReferenceFeatures>): SwingReferenceModel {
        require(examples.size >= MIN_REFERENCE_EXAMPLES) {
            "At least $MIN_REFERENCE_EXAMPLES accepted swings are required for a reference profile."
        }
        val columns = (0 until FEATURE_COUNT).map { index ->
            examples.map { it.values()[index] }.sorted()
        }
        val centers = columns.map(::median)
        val scales = columns.mapIndexed { index, values ->
            val deviations = values.map { value -> kotlin.math.abs(value - centers[index]) }.sorted()
            maxOf(median(deviations) * MAD_TO_STANDARD_DEVIATION, MIN_ROBUST_SCALE)
        }
        return SwingReferenceModel(
            center = features(centers),
            robustScale = features(scales),
            exampleCount = examples.size
        )
    }

    fun trainReports(reports: List<TechniqueReport>): SwingReferenceTrainingResult {
        val features = reports.mapNotNull(SwingReferenceFeatureExtractor::extract)
        val model = train(features)
        return SwingReferenceTrainingResult(
            model = model,
            acceptedExampleCount = features.size,
            rejectedExampleCount = reports.size - features.size,
            exampleSimilarities = features.map(model::similarity)
        )
    }

    private fun median(sorted: List<Double>): Double {
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        } else {
            sorted[middle]
        }
    }

    private fun features(values: List<Double>) = SwingReferenceFeatures(
        rotationSequence = values[0],
        headStability = values[1],
        swingExtension = values[2],
        lowerBodyLoad = values[3],
        handSpeed = values[4]
    )

    private const val FEATURE_COUNT = 5
    private const val MIN_ROBUST_SCALE = 0.04
    private const val MAD_TO_STANDARD_DEVIATION = 1.4826
}