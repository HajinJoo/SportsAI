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
    val highlights: List<HighlightClip> = emptyList()
)

