package com.example.sportsai.model

/** One analyzed clip saved to the progress timeline. */
data class SessionEntry(
    val id: Long,              // unique (analysis time in millis)
    val sportName: String,     // Sport enum name
    val filmedAtMillis: Long,  // when the clip was filmed
    val score: Int,
    val summary: String
)

