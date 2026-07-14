package com.example.sportsai

import com.example.sportsai.data.HighlightExtractor
import com.example.sportsai.data.TechniqueAnalyzer
import com.example.sportsai.model.AnalysisResult
import com.example.sportsai.model.Finding
import com.example.sportsai.model.FindingType
import com.example.sportsai.model.FramePose
import com.example.sportsai.model.LandmarkPoint
import com.example.sportsai.model.SessionEntry
import com.example.sportsai.model.Sport
import com.example.sportsai.model.metrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SportProgressTest {

    @Test
    fun localAnalyzerProducesEveryConfiguredMetricForEverySport() {
        val result = trackedResult()

        Sport.entries.forEach { sport ->
            val report = TechniqueAnalyzer().analyze(result, sport)
            assertEquals(sport.metrics.map { it.name }.toSet(), report.metricScores.keys)
            assertTrue(report.metricScores.values.all { it in 0..100 })
            val sentences = report.aiOverview.split('.').map(String::trim).filter(String::isNotEmpty)
            assertTrue("Expected a 3–4 sentence overview", sentences.size in 3..4)
        }
    }

    @Test
    fun highlightExtractorFindsAPlayableTimeRange() {
        val highlights = HighlightExtractor().extract(trackedResult(), Sport.BASEBALL_BAT)

        assertFalse(highlights.isEmpty())
        assertTrue(highlights.all { it.startMs >= 0L })
        assertTrue(highlights.all { it.endMs > it.startMs })
        assertTrue(highlights.all { it.score in 0..100 })
    }

    @Test
    fun savedSessionRecreatesTheFullHistoricalReport() {
        val finding = Finding(FindingType.GOOD, "Balance", "Stable through the finish.")
        val entry = SessionEntry(
            id = 42L,
            sportName = Sport.BASEBALL_PITCH.name,
            filmedAtMillis = 100L,
            score = 81,
            summary = "Strong session.",
            metrics = mapOf("Balance" to 88),
            aiOverview = "Sentence one. Sentence two. Sentence three.",
            findings = listOf(finding),
            detectionRate = 0.9f
        )

        val report = entry.toTechniqueReport(Sport.BASEBALL_PITCH)

        assertEquals(81, report.overallScore)
        assertEquals(entry.metrics, report.metricScores)
        assertEquals(listOf(finding), report.findings)
        assertEquals(entry.aiOverview, report.aiOverview)
    }

    private fun trackedResult(): AnalysisResult {
        val frames = (0..7).map { index ->
            val wristTravel = index * index * 9f
            FramePose(
                timestampMs = index * 200L,
                landmarks = listOf(
                    point(11, 110f, 120f),
                    point(12, 210f, 120f),
                    point(13, 95f, 175f),
                    point(14, 225f, 170f),
                    point(15, 80f + wristTravel / 3f, 225f - wristTravel / 4f),
                    point(16, 240f + wristTravel, 220f - wristTravel / 2f),
                    point(23, 125f, 250f),
                    point(24, 195f, 250f),
                    point(25, 120f, 340f - index * 2f),
                    point(26, 200f, 340f + index * 2f),
                    point(27, 105f, 430f),
                    point(28, 215f, 430f)
                )
            )
        }
        return AnalysisResult(
            framesSampled = frames.size,
            framesWithPose = frames.size,
            timeline = frames,
            durationMs = frames.last().timestampMs
        )
    }

    private fun point(type: Int, x: Float, y: Float) = LandmarkPoint(
        type = type,
        x = x,
        y = y,
        inFrameLikelihood = 0.99f
    )
}
