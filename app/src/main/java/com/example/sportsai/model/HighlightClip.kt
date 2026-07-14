package com.example.sportsai.model

/**
 * An AI-identified highlight segment from the analyzed video.
 * Each clip represents a key athletic moment (e.g., release point, contact, best form)
 * with the frame range and a quality score.
 */
data class HighlightClip(
    val id: Long,
    val label: String,
    val startMs: Long,
    val endMs: Long,
    /** Quality score for this highlight (0–100). */
    val score: Int = 0,
    /** App-private MP4 created from the original clip. */
    val videoPath: String = "",
    /** True after the athlete changes the AI-picked boundaries. */
    val editedByUser: Boolean = false
)
