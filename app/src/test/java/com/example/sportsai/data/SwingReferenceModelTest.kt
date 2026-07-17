package com.example.sportsai.data

import com.example.sportsai.model.Sport
import com.example.sportsai.model.TechniqueReport
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwingReferenceModelTest {

    @Test
    fun requiresEnoughExamplesToDescribeVariation() {
        val error = runCatching {
            SwingReferenceTrainer.train(List(4) { features(0.5) })
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun referenceLikeSwingScoresAboveDistantSwing() {
        val examples = listOf(0.46, 0.48, 0.49, 0.50, 0.51, 0.52, 0.54).map(::features)
        val model = SwingReferenceTrainer.train(examples)

        val referenceLike = model.similarity(features(0.50))
        val distant = model.similarity(features(0.85))

        assertEquals(7, model.exampleCount)
        assertTrue(referenceLike >= 95)
        assertTrue(distant <= 10)
        assertTrue(referenceLike > distant)
    }

    @Test
    fun medianCenterResistsOneExtremeExample() {
        val examples = listOf(0.47, 0.48, 0.49, 0.50, 0.51, 0.52, 1.0).map(::features)
        val model = SwingReferenceTrainer.train(examples)

        assertEquals(0.50, model.center.rotationSequence, 0.0001)
        assertTrue(model.similarity(features(0.50)) > model.similarity(features(1.0)))
    }

    @Test
    fun reportExtractionRequiresEveryBattingMetric() {
        val complete = report(
            mapOf(
                "Hip Rotation" to 78,
                "Ball Tracking" to 80,
                "Swing Extension" to 90,
                "Lower-Body Load" to 82,
                "Bat Speed Potential" to 76
            )
        )
        val partial = report(complete.metricScores - "Swing Extension")

        val features = SwingReferenceFeatureExtractor.extract(complete)

        assertEquals(0.78, features?.rotationSequence ?: 0.0, 0.0001)
        assertEquals(0.76, features?.handSpeed ?: 0.0, 0.0001)
        assertNull(SwingReferenceFeatureExtractor.extract(partial))
    }

    @Test
    fun reportTrainingSurfacesRejectedClips() {
        val completeMetrics = mapOf(
            "Hip Rotation" to 78,
            "Ball Tracking" to 80,
            "Swing Extension" to 90,
            "Lower-Body Load" to 82,
            "Bat Speed Potential" to 76
        )
        val reports = List(6) { report(completeMetrics) } +
            report(completeMetrics - "Ball Tracking")

        val result = SwingReferenceTrainer.trainReports(reports)

        assertEquals(6, result.acceptedExampleCount)
        assertEquals(1, result.rejectedExampleCount)
        assertEquals(6, result.exampleSimilarities.size)
        assertTrue(result.exampleSimilarities.all { it == 100 })
    }

    @Test
    fun artifactRoundTripsWithoutSourceVideoData() {
        val model = SwingReferenceTrainer.train(
            listOf(0.46, 0.48, 0.50, 0.52, 0.54).map(::features)
        )
        val artifact = SwingReferenceArtifactCodec.create("curated-swings-v1", model)

        val encoded = SwingReferenceArtifactCodec.encode(artifact)
        val decoded = SwingReferenceArtifactCodec.decode(encoded)

        assertEquals(artifact, decoded)
        assertTrue(encoded.contains(SwingReferenceArtifactCodec.REFERENCE_LABEL))
        assertTrue(!encoded.contains("videoUri"))
        assertTrue(!encoded.contains("landmarks"))
    }

    @Test
    fun artifactRejectsUnknownSchemaAndAnalysisProfile() {
        val model = SwingReferenceTrainer.train(List(5) { features(0.5) })
        val encoded = SwingReferenceArtifactCodec.encode(
            SwingReferenceArtifactCodec.create("curated-swings-v1", model)
        )
        val wrongSchema = JSONObject(encoded).put("schemaVersion", 99).toString()
        val wrongProfile = JSONObject(encoded).put("analysisProfile", "offline-batting-old").toString()

        assertTrue(runCatching { SwingReferenceArtifactCodec.decode(wrongSchema) }.isFailure)
        assertTrue(runCatching { SwingReferenceArtifactCodec.decode(wrongProfile) }.isFailure)
    }

    private fun features(value: Double) = SwingReferenceFeatures(
        rotationSequence = value,
        headStability = value,
        swingExtension = value,
        lowerBodyLoad = value,
        handSpeed = value
    )

    private fun report(metricScores: Map<String, Int>) = TechniqueReport(
        sport = Sport.BASEBALL_BAT.displayName,
        overallScore = 80,
        summary = "Test report",
        findings = emptyList(),
        detectionRate = 1f,
        metricScores = metricScores,
        aiOverview = "Test overview"
    )
}