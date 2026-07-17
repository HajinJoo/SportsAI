package com.example.sportsai.model

/** A comparable, sport-specific performance signal shown in results and history. */
data class SportMetric(
    val name: String,
    val description: String
)

/**
 * Metrics intentionally use 0–100 coaching scores. Speed entries are pose-based
 * potential scores, not radar measurements, and ball tracking is a visual/head-
 * stability signal rather than a claim that the app measured ball flight.
 */
val Sport.metrics: List<SportMetric>
    get() = when (this) {
        Sport.BASEBALL_PITCH -> listOf(
            SportMetric("Pitch Speed Potential", "Pose-based arm speed and sequencing; not radar-measured ball speed."),
            SportMetric("Arm Action", "Throwing-arm load and elbow position."),
            SportMetric("Stride Power", "Front-leg brace and lower-body force transfer."),
            SportMetric("Trunk Rotation", "Core rotation through release."),
            SportMetric("Balance", "Head stability and repeatable direction.")
        )

        Sport.BASEBALL_BAT -> listOf(
            SportMetric("Bat Speed Potential", "Pose-based hand-speed potential; not sensor-measured bat speed."),
            SportMetric("Ball Tracking", "On-device head-stability proxy; not measured gaze or ball flight."),
            SportMetric("Hip Rotation", "Depth-supported hip-to-shoulder rotation sequencing; unmeasured when depth is unreliable."),
            SportMetric("Swing Extension", "Arm extension near the estimated peak hand-speed window; not detected bat-ball contact."),
            SportMetric("Lower-Body Load", "Knee-flexion loading proxy; not measured leg power.")
        )

        Sport.BASKETBALL_SHOT -> listOf(
            SportMetric("Release Speed", "Pose-based hand and arm release-speed score."),
            SportMetric(
                "Ball Tracking",
                "On-device head/upper-body stability proxy; not measured gaze, target alignment, or ball flight."
            ),
            SportMetric("Set Point", "Elbow bend and shooting-pocket position."),
            SportMetric("Follow-through", "Arm extension through release."),
            SportMetric("Leg Drive", "Knee load and ground-up power."),
            SportMetric("Alignment", "Wrist, elbow, and shoulder stacking."),
            SportMetric("Balance", "Body control through takeoff and landing.")
        )
    }

/** True when a numeric report/session score averages only the metric areas visible in the clip. */
val TechniqueReport.isPartialMetricScore: Boolean
    get() = metricScores.isNotEmpty() && metricScores.size < configuredMetricCount(sport)

val SessionEntry.isPartialMetricScore: Boolean
    get() {
        val sport = runCatching { Sport.valueOf(sportName) }.getOrNull() ?: return false
        return metrics.isNotEmpty() && metrics.size < sport.metrics.size
    }

private fun configuredMetricCount(displayName: String): Int =
    Sport.entries.firstOrNull { it.displayName == displayName }?.metrics?.size ?: 0
