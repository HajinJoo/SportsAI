package com.example.sportsai

import com.example.sportsai.data.HighlightExtractor
import com.example.sportsai.data.TechniqueAnalyzer
import com.example.sportsai.model.AnalysisResult
import com.example.sportsai.model.AnalysisProfiles
import com.example.sportsai.model.AthleteTrackingInfo
import com.example.sportsai.model.AthleteTrackingMode
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
            val verifiedResult = if (sport == Sport.BASEBALL_BAT) {
                result.copy(
                    athleteTracking = AthleteTrackingInfo(
                        mode = AthleteTrackingMode.BATTER_LOCKED,
                        trackedFrames = result.timeline.size
                    )
                )
            } else result
            val report = TechniqueAnalyzer().analyze(verifiedResult, sport)
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
    fun highlightExtractorReturnsOneSportSpecificActionInsteadOfGenericClips() {
        val expectedLabels = mapOf(
            Sport.BASEBALL_PITCH to "Best pitch · release",
            Sport.BASEBALL_BAT to "Best swing · peak hand speed",
            Sport.BASKETBALL_SHOT to "Best shot · release"
        )

        expectedLabels.forEach { (sport, label) ->
            val highlights = HighlightExtractor().extract(clearActionResult(), sport, maxClips = 3)

            assertEquals("Expected one focused highlight for $sport", 1, highlights.size)
            val highlight = highlights.single()
            assertEquals(label, highlight.label)
            assertTrue("The selected range should include the action", 2_000L in highlight.startMs..highlight.endMs)
            assertTrue("The clip should exclude idle video at the beginning", highlight.startMs > 0L)
            assertTrue("The clip should exclude idle video at the end", highlight.endMs < 4_000L)
        }
    }

    @Test
    fun highlightExtractorDoesNotCallAStaticPoseAHighlight() {
        val stillFrames = (0..10).map { index ->
            FramePose(
                timestampMs = index * 200L,
                landmarks = actionLandmarks(0f)
            )
        }
        val result = AnalysisResult(
            framesSampled = stillFrames.size,
            framesWithPose = stillFrames.size,
            timeline = stillFrames,
            durationMs = 2_000L
        )

        Sport.entries.forEach { sport ->
            assertTrue(HighlightExtractor().extract(result, sport).isEmpty())
        }
    }

    @Test
    fun ambiguousBattingTrackIsNotScoredAsTheCatcherOrUmpire() {
        val result = AnalysisResult(
            framesSampled = 12,
            framesWithPose = 0,
            timeline = emptyList(),
            durationMs = 2_200L,
            athleteTracking = AthleteTrackingInfo(
                mode = AthleteTrackingMode.BATTER_NOT_CONFIDENT,
                matchScore = 0.31f,
                maxPeopleDetected = 3,
                trackedFrames = 0
            )
        )

        val report = TechniqueAnalyzer().analyze(result, Sport.BASEBALL_BAT)

        assertEquals(0, report.overallScore)
        assertTrue(report.metricScores.isEmpty())
        assertTrue(report.summary.contains("did not score", ignoreCase = true))
        assertTrue(report.findings.any { it.message.contains("3 people") })
    }

    @Test
    fun uncertainBattingModeCannotBeScoredEvenWhenCandidatePosesArePresent() {
        val base = trackedResult()
        val result = base.copy(
            athleteTracking = AthleteTrackingInfo(
                mode = AthleteTrackingMode.BATTER_NOT_CONFIDENT,
                matchScore = 0.49f,
                maxPeopleDetected = 3,
                trackedFrames = base.timeline.size
            )
        )

        val report = TechniqueAnalyzer().analyze(result, Sport.BASEBALL_BAT)

        assertEquals(0, report.overallScore)
        assertTrue(report.metricScores.isEmpty())
        assertTrue(report.summary.contains("did not score", ignoreCase = true))
    }

    @Test
    fun unverifiedSinglePersonBattingModeCannotBypassBatterLock() {
        val result = trackedResult().copy(
            athleteTracking = AthleteTrackingInfo(
                mode = AthleteTrackingMode.SINGLE_PERSON,
                trackedFrames = trackedResult().timeline.size
            )
        )

        val report = TechniqueAnalyzer().analyze(result, Sport.BASEBALL_BAT)

        assertEquals(0, report.overallScore)
        assertTrue(report.metricScores.isEmpty())
        assertTrue(report.summary.contains("Batter Lock"))
    }

    @Test
    fun missingBattingJointsRemainUnmeasuredInsteadOfBecomingTechniqueFaults() {
        val base = trackedResult()
        val upperBodyOnly = base.timeline.map { frame ->
            frame.copy(
                landmarks = frame.landmarks.filterNot { point ->
                    point.type in setOf(13, 14, 25, 26, 27, 28)
                }
            )
        }
        val result = base.copy(
            timeline = upperBodyOnly,
            athleteTracking = AthleteTrackingInfo(
                mode = AthleteTrackingMode.BATTER_LOCKED,
                matchScore = 0.82f,
                maxPeopleDetected = 2,
                trackedFrames = upperBodyOnly.size
            )
        )

        val report = TechniqueAnalyzer().analyze(result, Sport.BASEBALL_BAT)

        assertFalse(report.metricScores.containsKey("Swing Extension"))
        assertFalse(report.metricScores.containsKey("Lower-Body Load"))
        assertTrue(report.findings.any { it.area == "Visibility" })
        assertFalse(report.findings.any { it.message.contains("~0°") })
        assertFalse(report.findings.any { it.message.contains("standing tall", ignoreCase = true) })
    }

    @Test
    fun lockedBattingTrackExplainsWhoWasMeasured() {
        val base = trackedResult()
        val result = base.copy(
            athleteTracking = AthleteTrackingInfo(
                mode = AthleteTrackingMode.BATTER_LOCKED,
                matchScore = 0.88f,
                maxPeopleDetected = 3,
                trackedFrames = base.timeline.size
            )
        )

        val report = TechniqueAnalyzer().analyze(result, Sport.BASEBALL_BAT)

        assertTrue(report.summary.startsWith("Batter Lock"))
        assertTrue(report.findings.any { finding ->
            finding.area == "Batter Lock" && finding.message.contains("2 other people")
        })
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
        assertEquals(AnalysisProfiles.LEGACY_UNKNOWN, report.analysisProfile)
    }

    @Test
    fun offlineReportsCarryAStableProfileForCompatibleProgressComparisons() {
        Sport.entries.forEach { sport ->
            val report = TechniqueAnalyzer().analyze(trackedResult(), sport)
            assertEquals(AnalysisProfiles.offline(sport), report.analysisProfile)
        }
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

    private fun clearActionResult(): AnalysisResult {
        val frames = (0..20).map { index ->
            val phase = when (index) {
                in 0..9 -> 0f
                10 -> 0.45f
                else -> 1f
            }
            FramePose(
                timestampMs = index * 200L,
                landmarks = actionLandmarks(phase)
            )
        }
        return AnalysisResult(
            framesSampled = frames.size,
            framesWithPose = frames.size,
            timeline = frames,
            durationMs = 4_000L
        )
    }

    private fun actionLandmarks(phase: Float): List<LandmarkPoint> = listOf(
        point(11, 110f, 120f),
        point(12, 210f, 120f + phase * 22f),
        point(13, 95f + phase * 35f, 175f - phase * 25f),
        point(14, 225f + phase * 55f, 170f - phase * 40f),
        point(15, 80f + phase * 130f, 225f - phase * 90f),
        point(16, 240f + phase * 155f, 220f - phase * 130f),
        point(23, 125f, 250f + phase * 18f),
        point(24, 195f, 250f - phase * 18f),
        point(25, 120f, 340f - phase * 35f),
        point(26, 200f, 340f - phase * 40f),
        point(27, 105f, 430f),
        point(28, 215f, 430f)
    )

    private fun point(type: Int, x: Float, y: Float) = LandmarkPoint(
        type = type,
        x = x,
        y = y,
        inFrameLikelihood = 0.99f,
        z = when (type) {
            11 -> -20f
            12 -> 20f
            23 -> -8f
            24 -> 8f
            else -> 0f
        }
    )
}
