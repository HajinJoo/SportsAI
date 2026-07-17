package com.example.sportsai.data

/** Keeps every displayed skill overview within the product's promised three-to-four sentences. */
internal fun normalizeSkillOverview(
    primary: String,
    fallbacks: List<String> = emptyList()
): String {
    val defaults = listOf(
        "SportsAI reviewed the visible movement evidence.",
        "Use the metric breakdown to identify your strongest area.",
        "Focus on the lowest visible metric in your next practice.",
        "Compare another session after practice to confirm progress."
    )
    val selected = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    (listOf(primary) + fallbacks + defaults).forEach { text ->
        splitOverviewSentences(text).forEach { sentence ->
            val normalized = sentence.trim().replace(Regex("\\s+"), " ")
                .let { value ->
                    if (value.lastOrNull() in setOf('.', '!', '?')) value else "$value."
                }
            if (normalized.isNotBlank() && seen.add(normalized.lowercase())) {
                selected += normalized
            }
        }
    }
    return selected.take(4).joinToString(" ")
}

internal fun splitOverviewSentences(text: String): List<String> = text
    .trim()
    .split(Regex("(?<=[.!?])\\s+"))
    .map(String::trim)
    .filter(String::isNotBlank)
