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
            SportMetric("Ball Tracking", "Head and eye-line stability used as a visual tracking signal."),
            SportMetric("Hip Rotation", "Hip and torso turn through the contact zone."),
            SportMetric("Contact Extension", "Arm extension through contact."),
            SportMetric("Lower Body Power", "Athletic load and leg drive.")
        )

        Sport.BASKETBALL_SHOT -> listOf(
            SportMetric("Release Speed", "Pose-based hand and arm release-speed score."),
            SportMetric("Ball Tracking", "Head and eye-line stability toward the target."),
            SportMetric("Set Point", "Elbow bend and shooting-pocket position."),
            SportMetric("Follow-through", "Arm extension through release."),
            SportMetric("Leg Drive", "Knee load and ground-up power."),
            SportMetric("Alignment", "Wrist, elbow, and shoulder stacking."),
            SportMetric("Balance", "Body control through takeoff and landing.")
        )
    }
