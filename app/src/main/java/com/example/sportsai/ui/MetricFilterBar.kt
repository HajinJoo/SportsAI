package com.example.sportsai.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sportsai.model.Sport
import com.example.sportsai.model.metrics
import com.example.sportsai.ui.theme.ScoreHigh
import com.example.sportsai.ui.theme.ScoreLow

/**
 * A horizontal scrollable row of filter chips for sport-specific metrics.
 * Shows an "All" chip plus chips for each area, with optional trend arrows.
 */
@Composable
fun MetricFilterBar(
    sport: Sport,
    selectedMetric: String?,
    onMetricSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    /** Previous and current metric scores for trend display. */
    currentMetrics: Map<String, Int> = emptyMap(),
    previousMetrics: Map<String, Int> = emptyMap()
) {
    val areas = metricsForSport(sport)

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .animateContentSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // "All" chip
        FilterChip(
            selected = selectedMetric == null,
            onClick = { onMetricSelected(null) },
            label = {
                Text(
                    "All",
                    fontWeight = if (selectedMetric == null) FontWeight.Bold else FontWeight.Medium
                )
            },
            shape = RoundedCornerShape(12.dp),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                selectedLabelColor = MaterialTheme.colorScheme.primary
            )
        )

        areas.forEach { area ->
            val current = currentMetrics[area]
            val previous = previousMetrics[area]
            val delta = if (current != null && previous != null) current - previous else null
            val isSelected = selectedMetric == area

            FilterChip(
                selected = isSelected,
                onClick = { onMetricSelected(if (isSelected) null else area) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            area,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                        // Show score if available.
                        if (current != null) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "$current",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Trend arrow.
                        if (delta != null && delta != 0) {
                            Spacer(Modifier.width(4.dp))
                            val arrow = if (delta > 0) "↑" else "↓"
                            val color = if (delta > 0) ScoreHigh else ScoreLow
                            Text(
                                arrow,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

/** Returns the metric area names for the given sport. */
fun metricsForSport(sport: Sport): List<String> = sport.metrics.map { it.name }

fun metricDescription(sport: Sport, metric: String): String =
    sport.metrics.firstOrNull { it.name == metric }?.description.orEmpty()
