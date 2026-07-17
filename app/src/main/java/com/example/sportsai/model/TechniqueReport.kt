package com.example.sportsai.model

/** Whether a finding is a strength, a problem, or a neutral note. */
enum class FindingType { GOOD, ISSUE, TIP }

/** A single piece of coaching feedback derived from the pose analysis. */
data class Finding(
    val type: FindingType,
    val area: String,
    val message: String
)

enum class SwingPhase(val displayName: String) {
    STANCE("Stance"),
    STRIDE("Stride"),
    IMPACT_ZONE("Impact zone"),
    FOLLOW_THROUGH("Follow-through")
}

enum class SwingCameraView(val displayName: String) {
    SIDE("Side view"),
    REAR("Rear view"),
    UNKNOWN("View not confirmed")
}

data class CameraViewAssessment(
    val view: SwingCameraView = SwingCameraView.UNKNOWN,
    val confidence: Float = 0f,
    val usableFrames: Int = 0,
    val evidence: String = "Insufficient reliable pose geometry to classify the camera view."
)

data class SwingPhaseSegment(
    val phase: SwingPhase,
    val startMs: Long,
    val endMs: Long,
    val confidence: Float,
    val evidence: String
)

data class BiomechanicsMeasurement(
    val key: String,
    val label: String,
    val value: Double,
    val unit: String,
    val phase: SwingPhase? = null,
    val evidence: String
)

enum class MechanicsIssueSeverity { WATCH, PRIORITY }

data class MechanicsIssue(
    val code: String,
    val label: String,
    val severity: MechanicsIssueSeverity,
    val confidence: Float,
    val phase: SwingPhase,
    val evidence: String,
    val coachingCue: String
)

enum class EquipmentTrackingStatus { DETECTED, NOT_DETECTED, NOT_RUN }

data class EquipmentTrackingSummary(
    val type: TrackedObjectType,
    val status: EquipmentTrackingStatus,
    val sampledFrames: Int,
    val detectedFrames: Int,
    val maxConfidence: Float
)

/** Structured stage-2 output that is passed to the LLM and rendered in the report. */
data class SwingAnalysisSummary(
    val schemaVersion: Int = 2,
    val cameraView: CameraViewAssessment = CameraViewAssessment(),
    val phases: List<SwingPhaseSegment> = emptyList(),
    val measurements: List<BiomechanicsMeasurement> = emptyList(),
    val issues: List<MechanicsIssue> = emptyList(),
    val equipment: List<EquipmentTrackingSummary> = emptyList()
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
    val analysisProfile: String = AnalysisProfiles.LEGACY_UNKNOWN,
    /** Machine-readable local analysis used as the factual basis for generated coaching text. */
    val swingAnalysis: SwingAnalysisSummary? = null
)

object AnalysisProfiles {
    const val LEGACY_UNKNOWN = "legacy-unknown-v1"
    const val GEMINI_EVIDENCE_V2 = "gemini-3.5-flash-evidence-v2"

    fun offline(sport: Sport): String = when (sport) {
        Sport.BASEBALL_BAT -> "offline-batter-lock-v3"
        Sport.BASEBALL_PITCH -> "offline-pitch-v1"
        Sport.BASKETBALL_SHOT -> "offline-basketball-v1"
    }
}

