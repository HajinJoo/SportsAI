package com.example.sportsai.model

/** One analyzed clip saved to the progress timeline. */
data class SessionEntry(
    val id: Long,              // unique (analysis time in millis)
    val sportName: String,     // Sport enum name
    val filmedAtMillis: Long,  // when the clip was filmed
    val score: Int,
    val summary: String,
    /** Per-area metric scores, e.g. "Arm Action" → 85. */
    val metrics: Map<String, Int> = emptyMap(),
    /** A 3–4 sentence AI-generated skill overview. */
    val aiOverview: String = "",
    /** AI-picked highlight clips from the analyzed video. */
    val highlights: List<HighlightClip> = emptyList(),
    /** Full coaching findings so a historical result can be reopened. */
    val findings: List<Finding> = emptyList(),
    val detectionRate: Float = 0f,
    /** Persisted picker URI used to re-cut a highlight when available. */
    val sourceVideoUri: String = "",
    val videoDurationMs: Long = 0L,
    /** Identifies the compatible metric formula/prompt family for progress comparisons. */
    val analysisProfile: String = AnalysisProfiles.LEGACY_UNKNOWN,
    /** Structured local batting analysis retained when reopening a saved report. */
    val swingAnalysis: SwingAnalysisSummary? = null
) {
    fun toTechniqueReport(sport: Sport): TechniqueReport = TechniqueReport(
        sport = sport.displayName,
        overallScore = score,
        summary = summary,
        findings = findings,
        detectionRate = detectionRate,
        metricScores = metrics,
        aiOverview = aiOverview,
        highlights = highlights,
        analysisProfile = analysisProfile,
        swingAnalysis = swingAnalysis
    )
}

