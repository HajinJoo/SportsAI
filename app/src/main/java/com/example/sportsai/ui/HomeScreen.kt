package com.example.sportsai.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sportsai.model.Finding
import com.example.sportsai.model.FindingType
import com.example.sportsai.model.SessionEntry
import com.example.sportsai.model.Sport
import com.example.sportsai.model.TechniqueReport
import com.example.sportsai.ui.theme.GoodGreen
import com.example.sportsai.ui.theme.ScoreHigh
import com.example.sportsai.ui.theme.ScoreLow
import com.example.sportsai.ui.theme.ScoreMid
import com.example.sportsai.ui.theme.TipBlue
import com.example.sportsai.ui.theme.WarnAmber
import com.example.sportsai.viewmodel.AnalysisUiState
import com.example.sportsai.viewmodel.AnalysisViewModel

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: AnalysisViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val timeline by viewModel.timeline.collectAsStateWithLifecycle()
    var selectedSport by remember { mutableStateOf(Sport.BASEBALL_PITCH) }

    val pickVideo = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) viewModel.analyze(uri, selectedSport)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        HeroHeader()
        Spacer(Modifier.height(24.dp))

        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
            label = "screenState"
        ) { s ->
            when (s) {
                is AnalysisUiState.Idle -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IdleContent(
                        selectedSport = selectedSport,
                        onSportSelected = { selectedSport = it },
                        onPickClip = {
                            pickVideo.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.VideoOnly
                                )
                            )
                        }
                    )
                    Spacer(Modifier.height(24.dp))
                    TimelineSection(
                        entries = timeline,
                        sport = selectedSport,
                        onDelete = { viewModel.deleteSession(it) }
                    )
                    Spacer(Modifier.height(32.dp))
                }

                is AnalysisUiState.Analyzing -> AnalyzingContent(s)

                is AnalysisUiState.Done -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Filming date: confirmed banner, or ask the user if unknown.
                    if (s.savedToTimeline && s.filmedAtMillis != null) {
                        FilmedDateBanner(s.filmedAtMillis)
                        Spacer(Modifier.height(12.dp))
                    } else if (!s.savedToTimeline) {
                        AskDateCard(onDatePicked = { viewModel.saveSessionWithDate(it) })
                        Spacer(Modifier.height(12.dp))
                    }
                    ReportView(
                        report = s.report,
                        keyFrame = s.keyFrame,
                        keyFramePose = s.keyFramePose,
                        animationFrames = s.animationFrames
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.reset() },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Analyze another clip")
                    }
                    Spacer(Modifier.height(32.dp))
                }

                is AnalysisUiState.ViewingPastSession -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FilmedDateBanner(s.entry.filmedAtMillis)
                    Spacer(Modifier.height(12.dp))
                    ReportView(report = s.entry.toTechniqueReport(s.sport))
                    Spacer(Modifier.height(20.dp))
                    OutlinedButton(onClick = { viewModel.reset() }) {
                        Text("Back")
                    }
                }

                is AnalysisUiState.Error -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("😕", style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Something went wrong",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { viewModel.reset() }) {
                        Text("Try again")
                    }
                }
            }
        }
    }
}

// --- Header -----------------------------------------------------------------

@Composable
private fun HeroHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🏆", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(8.dp))
            Text(
                "SportsAI",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Your personal AI coach. Upload a clip — get pro-level feedback.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// --- Idle -------------------------------------------------------------------

@Composable
private fun IdleContent(
    selectedSport: Sport,
    onSportSelected: (Sport) -> Unit,
    onPickClip: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Choose your sport",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Sport.entries.forEach { sport ->
                SportCard(
                    sport = sport,
                    selected = selectedSport == sport,
                    onClick = { onSportSelected(sport) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onPickClip,
            shape = RoundedCornerShape(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 28.dp, vertical = 16.dp
            ),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("🎬  Pick a sports clip", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("📹", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(10.dp))
                Text(
                    selectedSport.filmingTip,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SportCard(
    sport: Sport,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.border(2.dp, borderColor, RoundedCornerShape(16.dp))
    ) {
        Column(
            Modifier.padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(sport.emoji, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                sport.displayName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

// --- Analyzing ----------------------------------------------------------------

@Composable
private fun AnalyzingContent(state: AnalysisUiState.Analyzing) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        val animated by animateFloatAsState(
            targetValue = state.progress,
            animationSpec = tween(300),
            label = "progress"
        )
        Box(contentAlignment = Alignment.Center) {
            val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            Canvas(Modifier.size(120.dp)) {
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 14f, cap = StrokeCap.Round)
                )
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(Color(0xFF1DB954), Color(0xFF00C2FF), Color(0xFF1DB954))
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    style = Stroke(width = 14f, cap = StrokeCap.Round)
                )
            }
            Text(
                "${(animated * 100).toInt()}%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            state.stage.label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Hang tight — great feedback takes a few seconds.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
        )
    }
}

// --- Report -------------------------------------------------------------------

@Composable
private fun ReportView(
    report: TechniqueReport,
    keyFrame: android.graphics.Bitmap? = null,
    keyFramePose: com.example.sportsai.model.FramePose? = null,
    animationFrames: List<com.example.sportsai.model.AnimationFrame> = emptyList()
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Animated skeleton playback (falls back to a still frame if unavailable).
        if (animationFrames.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(10.dp)) {
                    AnimatedSkeleton(
                        frames = animationFrames,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Your motion with the AI-tracked skeleton. Tap to pause.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        } else if (keyFrame != null && keyFramePose != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(10.dp)) {
                    SkeletonOverlay(
                        bitmap = keyFrame,
                        pose = keyFramePose,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "This is what the AI tracked on your body.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        ScoreCard(report)

        Spacer(Modifier.height(20.dp))

        val strengths = report.findings.filter { it.type == FindingType.GOOD }
        val issues = report.findings.filter { it.type == FindingType.ISSUE }
        val tips = report.findings.filter { it.type == FindingType.TIP }

        if (strengths.isNotEmpty()) {
            Section("✅ What's working", strengths, GoodGreen)
            Spacer(Modifier.height(16.dp))
        }
        if (issues.isNotEmpty()) {
            Section("⚠️ What to fix", issues, WarnAmber)
            Spacer(Modifier.height(16.dp))
        }
        if (tips.isNotEmpty()) {
            Section("💡 How to improve", tips, TipBlue)
        }
    }
}

@Composable
private fun ScoreCard(report: TechniqueReport) {
    val scoreColor = when {
        report.overallScore >= 75 -> ScoreHigh
        report.overallScore >= 50 -> ScoreMid
        else -> ScoreLow
    }
    var played by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { played = true }
    val animatedScore by animateFloatAsState(
        targetValue = if (played) report.overallScore / 100f else 0f,
        animationSpec = tween(900),
        label = "score"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Animated score ring.
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(110.dp)) {
                    val stroke = Stroke(width = 16f, cap = StrokeCap.Round)
                    val inset = 10f
                    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                    val topLeft = Offset(inset, inset)
                    drawArc(
                        color = scoreColor.copy(alpha = 0.15f),
                        startAngle = -90f, sweepAngle = 360f, useCenter = false,
                        style = stroke, size = arcSize, topLeft = topLeft
                    )
                    drawArc(
                        color = scoreColor,
                        startAngle = -90f,
                        sweepAngle = 360f * animatedScore,
                        useCenter = false,
                        style = stroke, size = arcSize, topLeft = topLeft
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${(animatedScore * 100).toInt()}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = scoreColor
                    )
                    Text(
                        "/100",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(18.dp))
            Column {
                Text(
                    report.sport,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    report.summary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, findings: List<Finding>, accent: Color) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        findings.forEach { finding ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier
                            .padding(top = 4.dp)
                            .size(8.dp)
                            .background(accent, CircleShape)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            finding.area,
                            style = MaterialTheme.typography.labelMedium,
                            color = accent,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(finding.message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

// --- Filming date -------------------------------------------------------------

private fun formatDate(millis: Long): String =
    java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
        .format(java.util.Date(millis))

@Composable
private fun FilmedDateBanner(filmedAtMillis: Long) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("📅", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(10.dp))
            Text(
                "Filmed ${formatDate(filmedAtMillis)} — saved to your timeline",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AskDateCard(onDatePicked: (Long) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📅", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "When was this filmed?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Set the date this clip was filmed to track your progress on the timeline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { showPicker = true },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Set filming date")
            }
        }
    }

    if (showPicker) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        dateState.selectedDateMillis?.let(onDatePicked)
                        showPicker = false
                    },
                    enabled = dateState.selectedDateMillis != null
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = dateState)
        }
    }
}

// --- Timeline -------------------------------------------------------------------

@Composable
private fun TimelineSection(
    entries: List<SessionEntry>,
    sport: Sport,
    onDelete: (Long) -> Unit
) {
    val sorted = entries
        .filter { it.sportName == sport.name }
        .sortedBy { it.filmedAtMillis }
    if (sorted.isEmpty()) return

    Column(Modifier.fillMaxWidth()) {
        Text(
            "📈 Your ${sport.displayName.lowercase()} timeline",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(10.dp))

        if (sorted.size >= 2) {
            ProgressChart(sorted)
            Spacer(Modifier.height(12.dp))
        }

        // Newest first, with delta vs previous session.
        sorted.asReversed().forEachIndexed { revIndex, entry ->
            val chronoIndex = sorted.size - 1 - revIndex
            val previous = if (chronoIndex > 0) sorted[chronoIndex - 1] else null
            TimelineRow(entry, previous, onDelete)
        }

        if (sorted.size == 1) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Analyze more clips over time to see how you improve!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProgressChart(sorted: List<SessionEntry>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(16.dp)
        ) {
            // Grid lines at 0/25/50/75/100.
            for (i in 0..4) {
                val y = size.height * (1f - i / 4f)
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
            }
            val stepX = if (sorted.size > 1) size.width / (sorted.size - 1) else 0f
            val points = sorted.mapIndexed { i, e ->
                Offset(stepX * i, size.height * (1f - e.score / 100f))
            }
            for (i in 0 until points.size - 1) {
                drawLine(
                    color = lineColor,
                    start = points[i],
                    end = points[i + 1],
                    strokeWidth = 7f,
                    cap = StrokeCap.Round
                )
            }
            points.forEach { p ->
                drawCircle(color = lineColor, radius = 10f, center = p)
                drawCircle(color = Color.White, radius = 5f, center = p)
            }
        }
    }
}

@Composable
private fun TimelineRow(
    entry: SessionEntry,
    previous: SessionEntry?,
    onDelete: (Long) -> Unit
) {
    val delta = previous?.let { entry.score - it.score }
    val deltaColor = when {
        delta == null -> MaterialTheme.colorScheme.onSurfaceVariant
        delta > 0 -> ScoreHigh
        delta < 0 -> ScoreLow
        else -> ScoreMid
    }
    val deltaText = when {
        delta == null -> "first session"
        delta > 0 -> "▲ +$delta since last"
        delta < 0 -> "▼ $delta since last"
        else -> "— same as last"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Score badge.
            Box(
                Modifier
                    .size(46.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${entry.score}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    formatDate(entry.filmedAtMillis),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    deltaText,
                    style = MaterialTheme.typography.labelMedium,
                    color = deltaColor,
                    fontWeight = FontWeight.Bold
                )
            }
            TextButton(onClick = { onDelete(entry.id) }) {
                Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}



