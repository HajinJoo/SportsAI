package com.example.sportsai.model

/** Whether a finding is a strength, a problem, or a neutral note. */
enum class FindingType { GOOD, ISSUE, TIP }

/** A single piece of coaching feedback derived from the pose analysis. */
data class Finding(
    val type: FindingType,
    val area: String,
    val message: String
)

/** The human-facing coaching report for a clip. */
data class TechniqueReport(
    val sport: String,
    val overallScore: Int,          // 0..100
    val summary: String,
    val findings: List<Finding>,
    val detectionRate: Float,
    /** Per-area metric scores, e.g. "Arm Action" → 85. */
    val metricScores: Map<String, Int> = emptyMap(),
    /** A 3–4 sentence AI-generated skill overview. */
    val aiOverview: String = "",
    /** AI-picked highlight clips from the analyzed video. */
    val highlights: List<HighlightClip> = emptyList(),
    /** Stable scoring/prompt profile used to prevent incompatible progress comparisons. */
    val analysisProfile: String = AnalysisProfiles.LEGACY_UNKNOWN
)

object AnalysisProfiles {
    const val LEGACY_UNKNOWN = "legacy-unknown-v1"
    const val GEMINI_EVIDENCE_V2 = "gemini-3.5-flash-evidence-v2"

    fun offline(sport: Sport): String = when (sport) {
        Sport.BASEBALL_BAT -> "offline-batter-lock-v2"
        Sport.BASEBALL_PITCH -> "offline-pitch-v1"
        Sport.BASKETBALL_SHOT -> "offline-basketball-v1"
    }
}

