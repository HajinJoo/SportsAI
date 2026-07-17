package com.example.sportsai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sportsai.model.Sport
import com.example.sportsai.model.metrics
import com.example.sportsai.ui.theme.ScoreHigh
import com.example.sportsai.ui.theme.ScoreLow
import com.example.sportsai.ui.theme.ScoreMid
import com.example.sportsai.ui.theme.SkyCyan

@Composable
fun AiSkillOverviewCard(overview: String, modifier: Modifier = Modifier) {
    if (overview.isBlank()) return
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(34.dp).background(SkyCyan.copy(alpha = 0.16f), RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("S", color = SkyCyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Skill overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Your current level in 3–4 sentences", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(overview, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun PerformanceMetricsSection(
    sport: Sport,
    current: Map<String, Int>,
    modifier: Modifier = Modifier,
    previous: Map<String, Int> = emptyMap(),
    selectedMetric: String? = null,
    onMetricSelected: (String?) -> Unit = {},
    showFilter: Boolean = true
) {
    val definitions = sport.metrics
    val visible = if (selectedMetric == null) definitions
    else definitions.filter { it.name == selectedMetric }

    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("SKILL METRICS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Text("0–100", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(7.dp))
        Text(
            "Filter the movement, then compare scores with the previous session analyzed the same way.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (showFilter) {
            Spacer(Modifier.height(10.dp))
            MetricFilterBar(
                sport = sport,
                selectedMetric = selectedMetric,
                onMetricSelected = onMetricSelected,
                currentMetrics = current,
                previousMetrics = previous,
                modifier = Modifier.fillMaxWidth()
            )
        }
        selectedMetric?.let { metric ->
            val description = metricDescription(sport, metric)
            if (description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Text(description, Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            visible.forEach { definition ->
                MetricScoreRow(
                    name = definition.name,
                    description = definition.description,
                    score = current[definition.name],
                    previousScore = previous[definition.name]
                )
            }
        }
    }
}

@Composable
private fun MetricScoreRow(
    name: String,
    description: String,
    score: Int?,
    previousScore: Int?
) {
    val value = score ?: 0
    val delta = if (score != null && previousScore != null) score - previousScore else null
    val trendColor = when {
        delta == null -> MaterialTheme.colorScheme.onSurfaceVariant
        delta > 0 -> ScoreHigh
        delta < 0 -> ScoreLow
        else -> ScoreMid
    }
    val trend = when {
        score == null -> "Not measured"
        delta == null -> "Baseline"
        delta > 0 -> "Improved +$delta"
        delta < 0 -> "Down ${-delta}"
        else -> "No change"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (score == null) "—" else "$score",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = if (score == null) MaterialTheme.colorScheme.onSurfaceVariant else metricColor(value)
                    )
                    Text(trend, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = trendColor)
                }
            }
            Spacer(Modifier.height(9.dp))
            LinearProgressIndicator(
                progress = { if (score == null) 0f else value.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth().height(7.dp),
                color = if (score == null) MaterialTheme.colorScheme.outline else metricColor(value),
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.13f)
            )
        }
    }
}

private fun metricColor(score: Int): Color = when {
    score >= 75 -> ScoreHigh
    score >= 50 -> ScoreMid
    else -> ScoreLow
}
