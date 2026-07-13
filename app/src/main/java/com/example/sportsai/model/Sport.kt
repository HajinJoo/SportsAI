package com.example.sportsai.model

/** Sports the analyzer supports, each with its own biomechanics rule set. */
enum class Sport(val displayName: String, val emoji: String, val filmingTip: String) {
    BASEBALL_PITCH(
        "Pitching",
        "⚾",
        "Film from the side so your throwing arm and stride leg are both visible."
    ),
    BASEBALL_BAT(
        "Batting",
        "🥎",
        "Film from the side (or slightly behind) so your full swing and hips are visible."
    ),
    BASKETBALL_SHOT(
        "Basketball shot",
        "🏀",
        "Film from the side of your shooting hand so your elbow and knees are visible."
    )
}

