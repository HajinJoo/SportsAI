package com.example.sportsai.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sportsai.model.AnimationFrame
import com.example.sportsai.model.Finding
import com.example.sportsai.model.FindingType
import com.example.sportsai.model.FramePose
import com.example.sportsai.model.HighlightClip
import com.example.sportsai.model.SessionEntry
import com.example.sportsai.model.Sport
import com.example.sportsai.model.TechniqueReport
import com.example.sportsai.ui.theme.EnergyOrange
import com.example.sportsai.ui.theme.GoodGreen
import com.example.sportsai.ui.theme.ScoreHigh
import com.example.sportsai.ui.theme.ScoreLow
import com.example.sportsai.ui.theme.ScoreMid
import com.example.sportsai.ui.theme.SkyCyan
import com.example.sportsai.ui.theme.TipBlue
import com.example.sportsai.ui.theme.WarnAmber
import com.example.sportsai.viewmodel.AnalysisUiState
import com.example.sportsai.viewmodel.AnalysisViewModel
import com.example.sportsai.viewmodel.HighlightEditUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val PageShape = RoundedCornerShape(28.dp)
private val CardShape = RoundedCornerShape(22.dp)
private val SmallShape = RoundedCornerShape(14.dp)

private enum class DashboardTab(val label: String) {
    HOME("Home"),
    UPLOAD("Upload"),
    TIMELINE("Timeline"),
    SETTINGS("Settings")
}

@Composable
fun PremiumSportsDashboard(
    modifier: Modifier = Modifier,
    viewModel: AnalysisViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val timeline by viewModel.timeline.collectAsStateWithLifecycle()
    val selectedMetric by viewModel.selectedMetric.collectAsStateWithLifecycle()
    val highlightEditState by viewModel.highlightEditState.collectAsStateWithLifecycle()
    val geminiSettings by viewModel.geminiSettings.collectAsStateWithLifecycle()
    var selectedSport by rememberSaveable { mutableStateOf(Sport.BASEBALL_PITCH) }
    var selectedTab by rememberSaveable { mutableStateOf(DashboardTab.HOME) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.analyze(it, selectedSport) }
    }

    PremiumBackground(modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            bottomBar = {
                DashboardBottomBar(
                    selected = selectedTab,
                    analysisState = state,
                    onSelect = { selectedTab = it }
                )
            }
        ) { navigationPadding ->
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .padding(navigationPadding)
            ) {
                val horizontalPadding = if (maxWidth >= 600.dp) 32.dp else 18.dp
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        top = 12.dp,
                        bottom = 32.dp
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item {
                        Column(Modifier.widthIn(max = 760.dp)) {
                            BrandBar(
                                geminiConfigured = geminiSettings.configured,
                                geminiSettingsLoading = geminiSettings.isLoading,
                                onOpenSettings = { selectedTab = DashboardTab.SETTINGS }
                            )
                            Spacer(Modifier.height(22.dp))
                        }
                    }
                    item {
                        AnimatedContent(
                            targetState = selectedTab,
                            transitionSpec = {
                                (fadeIn(tween(280)) + scaleIn(initialScale = 0.985f)) togetherWith
                                    fadeOut(tween(160))
                            },
                            label = "dashboardTab",
                            modifier = Modifier.widthIn(max = 760.dp)
                        ) { tab ->
                            when (tab) {
                                DashboardTab.HOME -> HomeDashboard(
                                    timeline = timeline,
                                    onStartAnalysis = { selectedTab = DashboardTab.UPLOAD },
                                    onOpenTimeline = { selectedTab = DashboardTab.TIMELINE }
                                )

                                DashboardTab.UPLOAD -> UploadDestination(
                                    state = state,
                                    timeline = timeline,
                                    sport = selectedSport,
                                    selectedMetric = selectedMetric,
                                    highlightEditState = highlightEditState,
                                    onSportChange = {
                                        selectedSport = it
                                        viewModel.setMetricFilter(null)
                                    },
                                    onMetricSelected = viewModel::setMetricFilter,
                                    onPickVideo = {
                                        picker.launch(
                                            PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.VideoOnly
                                            )
                                        )
                                    },
                                    onSaveDate = viewModel::saveSessionWithDate,
                                    onReset = viewModel::reset,
                                    onBackToTimeline = {
                                        viewModel.reset()
                                        selectedTab = DashboardTab.TIMELINE
                                    },
                                    onUpdateHighlight = viewModel::updateHighlight,
                                    onClearHighlightError = viewModel::clearHighlightEditError
                                )

                                DashboardTab.TIMELINE -> TimelineDestination(
                                    entries = timeline,
                                    sport = selectedSport,
                                    selectedMetric = selectedMetric,
                                    onSportChange = {
                                        selectedSport = it
                                        viewModel.setMetricFilter(null)
                                    },
                                    onMetricSelected = viewModel::setMetricFilter,
                                    onDelete = viewModel::deleteSession,
                                    onStartAnalysis = { selectedTab = DashboardTab.UPLOAD },
                                    onOpenSession = { id ->
                                        viewModel.loadSession(id)
                                        selectedTab = DashboardTab.UPLOAD
                                    }
                                )

                                DashboardTab.SETTINGS -> SettingsDestination(
                                    state = geminiSettings,
                                    onSaveAndTest = viewModel::saveAndTestGeminiApiKey,
                                    onTestSavedKey = viewModel::testSavedGeminiApiKey,
                                    onRemoveKey = viewModel::removeGeminiApiKey,
                                    onDismissMessage = viewModel::clearGeminiSettingsMessage
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PremiumBackground(modifier: Modifier, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier.background(
            Brush.verticalGradient(
                listOf(
                    scheme.background,
                    scheme.surfaceContainer.copy(alpha = 0.72f),
                    scheme.background
                )
            )
        )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(scheme.primary.copy(alpha = 0.13f), Color.Transparent)
                ),
                radius = size.minDimension * 0.7f,
                center = Offset(size.width * 0.95f, size.height * 0.02f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(SkyCyan.copy(alpha = 0.07f), Color.Transparent)
                ),
                radius = size.minDimension * 0.6f,
                center = Offset(0f, size.height * 0.72f)
            )
        }
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground) {
            content()
        }
    }
}

@Composable
private fun BrandBar(
    geminiConfigured: Boolean,
    geminiSettingsLoading: Boolean,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrandMark(44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "SPORTSAI",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                letterSpacing = MaterialTheme.typography.labelLarge.letterSpacing
            )
            Text(
                "Performance intelligence",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val badgeColor = when {
            geminiSettingsLoading -> SkyCyan
            geminiConfigured -> MaterialTheme.colorScheme.primary
            else -> WarnAmber
        }
        Surface(
            onClick = onOpenSettings,
            shape = CircleShape,
            color = badgeColor.copy(alpha = 0.12f),
            contentColor = badgeColor,
            border = androidx.compose.foundation.BorderStroke(
                1.dp, badgeColor.copy(alpha = 0.28f)
            ),
            modifier = Modifier.semantics {
                contentDescription = "AI settings: ${when {
                    geminiSettingsLoading -> "checking"
                    geminiConfigured -> "Gemini key saved"
                    else -> "offline coaching"
                }}"
            }
        ) {
            Row(
                Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(7.dp).background(badgeColor, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        geminiSettingsLoading -> "CHECKING"
                        geminiConfigured -> "GEMINI"
                        else -> "OFFLINE"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor
                )
            }
        }
    }
}

@Composable
private fun BrandMark(size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primary, SkyCyan)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "S",
            color = Color(0xFF071A12),
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
private fun DashboardBottomBar(
    selected: DashboardTab,
    analysisState: AnalysisUiState,
    onSelect: (DashboardTab) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, bottom = 4.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 390.dp)
                .fillMaxWidth()
                .height(60.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 16.dp,
            tonalElevation = 4.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.26f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DashboardTab.entries.forEach { tab ->
                    val active = selected == tab
                    val showStatus = tab == DashboardTab.UPLOAD && analysisState !is AnalysisUiState.Idle
                    val statusColor = when (analysisState) {
                        is AnalysisUiState.Analyzing -> SkyCyan
                        is AnalysisUiState.Done -> ScoreHigh
                        is AnalysisUiState.ViewingPastSession -> ScoreHigh
                        is AnalysisUiState.Error -> ScoreLow
                        AnalysisUiState.Idle -> Color.Transparent
                    }
                    val statusText = when (analysisState) {
                        is AnalysisUiState.Analyzing -> "Analysis in progress"
                        is AnalysisUiState.Done -> "Analysis ready"
                        is AnalysisUiState.ViewingPastSession -> "Historical analysis open"
                        is AnalysisUiState.Error -> "Analysis error"
                        AnalysisUiState.Idle -> "Ready to upload"
                    }
                    val tabColor = if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                else Color.Transparent
                            )
                            .selectable(
                                selected = active,
                                onClick = { onSelect(tab) },
                                role = Role.Tab
                            )
                            .semantics {
                                if (tab == DashboardTab.UPLOAD) stateDescription = statusText
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                                DashboardTabIcon(tab, tabColor, Modifier.size(19.dp))
                                if (showStatus) {
                                    Box(
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .size(7.dp)
                                            .background(statusColor, CircleShape)
                                            .border(
                                                1.dp,
                                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                                CircleShape
                                            )
                                    )
                                }
                            }
                            Text(
                                tab.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (active) FontWeight.Black else FontWeight.SemiBold,
                                color = tabColor,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardTabIcon(tab: DashboardTab, color: Color, modifier: Modifier = Modifier) {
    val knobCenterColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Canvas(modifier) {
        val stroke = 2.dp.toPx()
        when (tab) {
            DashboardTab.HOME -> {
                val roof = Path().apply {
                    moveTo(size.width * 0.12f, size.height * 0.48f)
                    lineTo(size.width * 0.50f, size.height * 0.16f)
                    lineTo(size.width * 0.88f, size.height * 0.48f)
                }
                drawPath(roof, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                val house = Path().apply {
                    moveTo(size.width * 0.22f, size.height * 0.43f)
                    lineTo(size.width * 0.22f, size.height * 0.86f)
                    lineTo(size.width * 0.78f, size.height * 0.86f)
                    lineTo(size.width * 0.78f, size.height * 0.43f)
                }
                drawPath(house, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            DashboardTab.UPLOAD -> {
                drawCircle(color, size.minDimension * 0.40f, style = Stroke(stroke))
                drawLine(color, Offset(size.width * 0.50f, size.height * 0.28f), Offset(size.width * 0.50f, size.height * 0.72f), stroke, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.28f, size.height * 0.50f), Offset(size.width * 0.72f, size.height * 0.50f), stroke, StrokeCap.Round)
            }
            DashboardTab.TIMELINE -> {
                val trend = Path().apply {
                    moveTo(size.width * 0.12f, size.height * 0.76f)
                    lineTo(size.width * 0.38f, size.height * 0.52f)
                    lineTo(size.width * 0.57f, size.height * 0.63f)
                    lineTo(size.width * 0.88f, size.height * 0.25f)
                }
                drawPath(trend, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(color, stroke * 1.15f, Offset(size.width * 0.12f, size.height * 0.76f))
                drawCircle(color, stroke * 1.15f, Offset(size.width * 0.88f, size.height * 0.25f))
            }
            DashboardTab.SETTINGS -> {
                val left = size.width * 0.16f
                val right = size.width * 0.84f
                val ys = listOf(size.height * 0.25f, size.height * 0.50f, size.height * 0.75f)
                val knobs = listOf(size.width * 0.36f, size.width * 0.68f, size.width * 0.46f)
                ys.forEachIndexed { index, y ->
                    drawLine(color, Offset(left, y), Offset(right, y), stroke, StrokeCap.Round)
                    drawCircle(color, stroke * 1.6f, Offset(knobs[index], y))
                    drawCircle(
                        knobCenterColor,
                        stroke * 0.72f,
                        Offset(knobs[index], y)
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeDashboard(
    timeline: List<SessionEntry>,
    onStartAnalysis: () -> Unit,
    onOpenTimeline: () -> Unit
) {
    Column {
        HeroPanel()
        Spacer(Modifier.height(28.dp))
        SectionHeading("Your training hub", "01", "OVERVIEW")
        Spacer(Modifier.height(12.dp))
        HomeStats(timeline)
        Spacer(Modifier.height(14.dp))
        HomeActionPanel(onStartAnalysis, onOpenTimeline, timeline.isNotEmpty())
        if (timeline.isNotEmpty()) {
            Spacer(Modifier.height(26.dp))
            RecentSessionPanel(timeline.maxBy { it.filmedAtMillis }, onOpenTimeline)
        }
    }
}

@Composable
private fun UploadDestination(
    state: AnalysisUiState,
    timeline: List<SessionEntry>,
    sport: Sport,
    selectedMetric: String?,
    highlightEditState: HighlightEditUiState,
    onSportChange: (Sport) -> Unit,
    onMetricSelected: (String?) -> Unit,
    onPickVideo: () -> Unit,
    onSaveDate: (Long) -> Unit,
    onReset: () -> Unit,
    onBackToTimeline: () -> Unit,
    onUpdateHighlight: (HighlightClip) -> Unit,
    onClearHighlightError: () -> Unit
) {
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            (fadeIn(tween(320)) + scaleIn(initialScale = 0.985f)) togetherWith fadeOut(tween(170))
        },
        label = "uploadState"
    ) { current ->
        when (current) {
            AnalysisUiState.Idle -> Column {
                Text("UPLOAD & ANALYZE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(5.dp))
                Text("Start a new analysis", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(24.dp))
                SectionHeading("Choose your movement", "01")
                Spacer(Modifier.height(12.dp))
                SportSelector(sport, onSportChange)
                Spacer(Modifier.height(20.dp))
                UploadPanel(sport, onPickVideo)
            }

            is AnalysisUiState.Analyzing -> AnalysisExperience(current)
            is AnalysisUiState.Done -> {
                val previous = timeline
                    .filter { it.sportName == current.sport.name && it.id != current.sessionId }
                    .maxByOrNull { it.filmedAtMillis }
                ResultsDashboard(
                    state = current,
                    previousSession = previous,
                    selectedMetric = selectedMetric,
                    highlightEditState = highlightEditState,
                    onMetricSelected = onMetricSelected,
                    onSaveDate = onSaveDate,
                    onAnalyzeAnother = onReset,
                    onUpdateHighlight = onUpdateHighlight,
                    onClearHighlightError = onClearHighlightError
                )
            }
            is AnalysisUiState.ViewingPastSession -> {
                val sorted = timeline
                    .filter { it.sportName == current.sport.name }
                    .sortedBy { it.filmedAtMillis }
                val index = sorted.indexOfFirst { it.id == current.entry.id }
                PastSessionDashboard(
                    state = current,
                    previousSession = sorted.getOrNull(index - 1),
                    selectedMetric = selectedMetric,
                    highlightEditState = highlightEditState,
                    onMetricSelected = onMetricSelected,
                    onBackToTimeline = onBackToTimeline,
                    onUpdateHighlight = onUpdateHighlight,
                    onClearHighlightError = onClearHighlightError
                )
            }
            is AnalysisUiState.Error -> ErrorExperience(current.message, onReset)
        }
    }
}

@Composable
private fun TimelineDestination(
    entries: List<SessionEntry>,
    sport: Sport,
    selectedMetric: String?,
    onSportChange: (Sport) -> Unit,
    onMetricSelected: (String?) -> Unit,
    onDelete: (Long) -> Unit,
    onStartAnalysis: () -> Unit,
    onOpenSession: (Long) -> Unit
) {
    val sportEntries = entries.filter { it.sportName == sport.name }
    Column {
        Text("PROGRESS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(5.dp))
        Text("Your performance timeline", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(22.dp))
        SectionHeading("Filter by movement", "01")
        Spacer(Modifier.height(12.dp))
        SportSelector(sport, onSportChange)
        Spacer(Modifier.height(28.dp))
        TimelinePanel(
            entries = entries,
            sport = sport,
            selectedMetric = selectedMetric,
            onMetricSelected = onMetricSelected,
            onDelete = onDelete,
            onOpenSession = onOpenSession
        )
        if (sportEntries.isEmpty()) {
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onStartAnalysis,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("Analyze your first clip", fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("→", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun HomeStats(entries: List<SessionEntry>) {
    val best = entries.maxOfOrNull { it.score }
    val sports = entries.map { it.sportName }.distinct().size
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        SummaryStat("SESSIONS", "${entries.size}", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        SummaryStat("BEST SCORE", best?.toString() ?: "—", ScoreHigh, Modifier.weight(1f))
        SummaryStat("SPORTS", "$sports", SkyCyan, Modifier.weight(1f))
    }
}

@Composable
private fun HomeActionPanel(
    onStartAnalysis: () -> Unit,
    onOpenTimeline: () -> Unit,
    hasHistory: Boolean
) {
    Card(
        shape = PageShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Ready for your next rep?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Upload a clip and turn your movement into a focused coaching plan.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onStartAnalysis,
                modifier = Modifier.fillMaxWidth().height(55.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("Start new analysis", fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("→", style = MaterialTheme.typography.titleLarge)
            }
            if (hasHistory) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onOpenTimeline, modifier = Modifier.fillMaxWidth()) {
                    Text("View full timeline", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RecentSessionPanel(entry: SessionEntry, onOpenTimeline: () -> Unit) {
    Column {
        SectionHeading("Latest session", "02", formatSessionDate(entry.filmedAtMillis).uppercase())
        Spacer(Modifier.height(12.dp))
        Surface(
            onClick = onOpenTimeline,
            shape = CardShape,
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(58.dp).background(scoreColor(entry.score).copy(alpha = 0.14f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${entry.score}", style = MaterialTheme.typography.titleLarge, color = scoreColor(entry.score), fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        runCatching { Sport.valueOf(entry.sportName).displayName }.getOrDefault(entry.sportName),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        entry.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun HeroPanel() {
    Card(
        shape = PageShape,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = Color.White
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
                            Color(0xFF14A866),
                            Color(0xFF087C78)
                        )
                    )
                )
        ) {
            Canvas(Modifier.matchParentSize()) {
                val white = Color.White.copy(alpha = 0.10f)
                drawCircle(white, size.width * 0.38f, Offset(size.width, 0f))
                drawCircle(white.copy(alpha = 0.5f), size.width * 0.22f, Offset(size.width, 0f))
                drawArc(
                    color = Color.White.copy(alpha = 0.12f),
                    startAngle = 200f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.48f, size.height * 0.12f),
                    size = Size(size.width * 0.6f, size.width * 0.6f),
                    style = Stroke(width = 5f, cap = StrokeCap.Round)
                )
            }
            Column(Modifier.padding(24.dp)) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.14f),
                    contentColor = Color.White
                ) {
                    Text(
                        "BUILT FOR ATHLETES",
                        Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    "Move better.\nPlay smarter.",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    lineHeight = MaterialTheme.typography.displaySmall.lineHeight
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Turn any sports clip into clear, personalized coaching in minutes.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.86f),
                    modifier = Modifier.widthIn(max = 430.dp)
                )
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroMetric("33", "body points", Modifier.weight(1f))
                    HeroMetric("AI", "coach review", Modifier.weight(1f))
                    HeroMetric("∞", "progress", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HeroMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.Black.copy(alpha = 0.12f),
        contentColor = Color.White,
        modifier = modifier
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Text(value, color = Color.White, fontWeight = FontWeight.Black)
            Text(
                label,
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SectionHeading(title: String, number: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            number,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        trailing?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SportSelector(selected: Sport, onSelect: (Sport) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Sport.entries.forEach { sport ->
            val active = sport == selected
            Surface(
                onClick = { onSelect(sport) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 104.dp)
                    .border(
                        width = if (active) 2.dp else 1.dp,
                        color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        shape = CardShape
                    )
                    .animateContentSize(),
                shape = CardShape,
                color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.11f)
                else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f)
                ,
                contentColor = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            ) {
                Column(
                    Modifier.padding(horizontal = 8.dp, vertical = 15.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(sport.emoji, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        sport.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(
                        Modifier
                            .size(if (active) 6.dp else 4.dp)
                            .background(
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                CircleShape
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun UploadPanel(sport: Sport, onPickVideo: () -> Unit) {
    Card(
        shape = PageShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        SkyCyan.copy(alpha = 0.25f),
                        Color.Transparent
                    )
                ),
                PageShape
            )
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(50.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), SmallShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("▶", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Upload your clip", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Video stays on your device until AI review",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onPickVideo,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Choose video", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("→", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Top) {
                Text("PRO TIP", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = EnergyOrange)
                Spacer(Modifier.width(10.dp))
                Text(
                    sport.filmingTip,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AnalysisExperience(state: AnalysisUiState.Analyzing) {
    val progress by animateFloatAsState(state.progress.coerceIn(0f, 1f), tween(450), label = "analysisProgress")
    val infinite = rememberInfiniteTransition(label = "scanner")
    val pulse by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "pulse"
    )
    val scanning = state.stage == AnalysisUiState.Analyzing.Stage.SCANNING
    val primary = MaterialTheme.colorScheme.primary

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(18.dp))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Text(
                if (scanning) "STAGE 1 OF 2" else "STAGE 2 OF 2",
                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(30.dp))
        Box(
            modifier = Modifier
                .size(230.dp)
                .graphicsLayer { scaleX = pulse; scaleY = pulse }
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                    contentDescription = state.stage.label
                },
            contentAlignment = Alignment.Center
        ) {
            val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(track, radius = size.minDimension * 0.49f, style = Stroke(2f))
                drawCircle(track, radius = size.minDimension * 0.38f, style = Stroke(2f))
                drawArc(
                    brush = Brush.sweepGradient(listOf(primary, SkyCyan, primary)),
                    startAngle = -90f,
                    sweepAngle = if (scanning) 360f * progress else 350f,
                    useCenter = false,
                    topLeft = Offset(14f, 14f),
                    size = Size(size.width - 28f, size.height - 28f),
                    style = Stroke(14f, cap = StrokeCap.Round)
                )
                val cx = size.width / 2f
                val cy = size.height / 2f
                val body = primary.copy(alpha = 0.75f)
                drawCircle(body, 12f, Offset(cx, cy - 48f))
                drawLine(body, Offset(cx, cy - 34f), Offset(cx, cy + 24f), 8f, StrokeCap.Round)
                drawLine(body, Offset(cx, cy - 18f), Offset(cx - 31f, cy + 2f), 7f, StrokeCap.Round)
                drawLine(body, Offset(cx, cy - 18f), Offset(cx + 31f, cy + 2f), 7f, StrokeCap.Round)
                drawLine(body, Offset(cx, cy + 23f), Offset(cx - 25f, cy + 58f), 7f, StrokeCap.Round)
                drawLine(body, Offset(cx, cy + 23f), Offset(cx + 25f, cy + 58f), 7f, StrokeCap.Round)
                listOf(
                    Offset(cx, cy - 48f), Offset(cx, cy - 18f),
                    Offset(cx - 31f, cy + 2f), Offset(cx + 31f, cy + 2f),
                    Offset(cx, cy + 23f), Offset(cx - 25f, cy + 58f), Offset(cx + 25f, cy + 58f)
                ).forEach { drawCircle(SkyCyan, 6f, it) }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (scanning) "${(progress * 100).roundToInt()}%" else "AI",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black
                )
            }
        }
        Spacer(Modifier.height(30.dp))
        AnimatedContent(state.stage, label = "stageLabel") { stage ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (stage == AnalysisUiState.Analyzing.Stage.SCANNING) "Mapping your movement" else "Building your coaching plan",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (stage == AnalysisUiState.Analyzing.Stage.SCANNING)
                        "Tracking posture, balance and joint positions frame by frame."
                    else "Comparing key moments and turning them into clear next steps.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 470.dp)
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        AnalysisSteps(scanning)
    }
}

@Composable
private fun AnalysisSteps(scanning: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        StepPill("01", "Body tracking", active = true, complete = !scanning, Modifier.weight(1f))
        Box(Modifier.width(20.dp).height(2.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)))
        StepPill("02", "Coach review", active = !scanning, complete = false, Modifier.weight(1f))
    }
}

@Composable
private fun StepPill(number: String, label: String, active: Boolean, complete: Boolean, modifier: Modifier) {
    val highlighted = active || complete
    Surface(
        modifier = modifier,
        shape = SmallShape,
        color = if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.11f)
        else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (highlighted) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).background(
                    if (highlighted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (complete) "✓" else number,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = if (highlighted) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ResultsDashboard(
    state: AnalysisUiState.Done,
    previousSession: SessionEntry?,
    selectedMetric: String?,
    highlightEditState: HighlightEditUiState,
    onMetricSelected: (String?) -> Unit,
    onSaveDate: (Long) -> Unit,
    onAnalyzeAnother: () -> Unit,
    onUpdateHighlight: (HighlightClip) -> Unit,
    onClearHighlightError: () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ANALYSIS COMPLETE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(5.dp))
                Text("Your performance report", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Text("✓", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(18.dp))
        PremiumScoreCard(state.report)
        Spacer(Modifier.height(16.dp))
        if (state.savedToTimeline && state.filmedAtMillis != null) {
            SavedDateBar(state.filmedAtMillis)
        } else {
            DatePrompt(onSaveDate)
        }
        Spacer(Modifier.height(20.dp))
        AiSkillOverviewCard(state.report.aiOverview)
        Spacer(Modifier.height(20.dp))
        PerformanceMetricsSection(
            sport = state.sport,
            current = state.report.metricScores,
            previous = previousSession?.metrics.orEmpty(),
            selectedMetric = selectedMetric,
            onMetricSelected = onMetricSelected
        )
        Spacer(Modifier.height(24.dp))
        MotionPanel(state.animationFrames, state.keyFrame, state.keyFramePose)
        Spacer(Modifier.height(24.dp))
        HighlightsSection(
            highlights = state.report.highlights,
            animationFrames = state.animationFrames,
            sourceVideoUri = state.sourceVideoUri,
            videoDurationMs = state.videoDurationMs,
            savingClipId = (highlightEditState as? HighlightEditUiState.Saving)?.clipId,
            editError = (highlightEditState as? HighlightEditUiState.Error)?.message,
            onSave = onUpdateHighlight,
            onClearError = onClearHighlightError
        )
        if (state.report.highlights.isNotEmpty()) Spacer(Modifier.height(24.dp))
        CoachingReport(state.report)
        Spacer(Modifier.height(26.dp))
        Button(
            onClick = onAnalyzeAnother,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text("Analyze another clip", fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text("→", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun PastSessionDashboard(
    state: AnalysisUiState.ViewingPastSession,
    previousSession: SessionEntry?,
    selectedMetric: String?,
    highlightEditState: HighlightEditUiState,
    onMetricSelected: (String?) -> Unit,
    onBackToTimeline: () -> Unit,
    onUpdateHighlight: (HighlightClip) -> Unit,
    onClearHighlightError: () -> Unit
) {
    val report = state.entry.toTechniqueReport(state.sport)
    Column {
        Text("PAST ANALYSIS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(5.dp))
        Text(formatSessionDate(state.entry.filmedAtMillis), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(5.dp))
        Text("Saved ${state.sport.displayName.lowercase()} result", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        PremiumScoreCard(report)
        Spacer(Modifier.height(18.dp))
        AiSkillOverviewCard(report.aiOverview)
        Spacer(Modifier.height(20.dp))
        PerformanceMetricsSection(
            sport = state.sport,
            current = state.entry.metrics,
            previous = previousSession?.metrics.orEmpty(),
            selectedMetric = selectedMetric,
            onMetricSelected = onMetricSelected
        )
        Spacer(Modifier.height(24.dp))
        HighlightsSection(
            highlights = state.entry.highlights,
            animationFrames = emptyList(),
            sourceVideoUri = state.entry.sourceVideoUri,
            videoDurationMs = state.entry.videoDurationMs,
            savingClipId = (highlightEditState as? HighlightEditUiState.Saving)?.clipId,
            editError = (highlightEditState as? HighlightEditUiState.Error)?.message,
            onSave = onUpdateHighlight,
            onClearError = onClearHighlightError
        )
        if (state.entry.highlights.isNotEmpty()) Spacer(Modifier.height(24.dp))
        if (report.findings.isNotEmpty()) {
            CoachingReport(report)
            Spacer(Modifier.height(24.dp))
        }
        OutlinedButton(
            onClick = onBackToTimeline,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text("← Back to timeline", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PremiumScoreCard(report: TechniqueReport) {
    val scoreColor = scoreColor(report.overallScore)
    var start by remember(report) { mutableStateOf(false) }
    LaunchedEffect(report) { start = true }
    val fraction by animateFloatAsState(if (start) report.overallScore / 100f else 0f, tween(1100), label = "scoreReveal")
    val rating = when {
        report.overallScore >= 85 -> "ELITE FORM"
        report.overallScore >= 70 -> "STRONG BASE"
        report.overallScore >= 50 -> "BUILDING"
        else -> "STARTING POINT"
    }

    Card(
        shape = PageShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(Modifier.fillMaxWidth()) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(
                    Brush.radialGradient(listOf(scoreColor.copy(alpha = 0.18f), Color.Transparent)),
                    size.width * 0.7f,
                    Offset(size.width, 0f)
                )
            }
            BoxWithConstraints(Modifier.fillMaxWidth().padding(22.dp)) {
                val compact = maxWidth < 430.dp
                if (compact) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ScoreGauge(fraction, scoreColor)
                        Spacer(Modifier.height(18.dp))
                        ScoreCopy(report, rating, centered = true)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ScoreGauge(fraction, scoreColor)
                        Spacer(Modifier.width(24.dp))
                        ScoreCopy(report, rating, centered = false, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreGauge(fraction: Float, color: Color) {
    Box(Modifier.size(148.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(16f, cap = StrokeCap.Round)
            val inset = 12f
            val arc = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(color.copy(alpha = 0.13f), -90f, 360f, false, Offset(inset, inset), arc, 1f, stroke)
            drawArc(
                Brush.sweepGradient(listOf(color, SkyCyan, color)),
                -90f, 360f * fraction, false, Offset(inset, inset), arc, 1f, stroke
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(fraction * 100).roundToInt()}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = color)
            Text("OVERALL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ScoreCopy(
    report: TechniqueReport,
    rating: String,
    centered: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Surface(
            shape = CircleShape,
            color = scoreColor(report.overallScore).copy(alpha = 0.13f),
            contentColor = scoreColor(report.overallScore)
        ) {
            Text(
                rating,
                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = scoreColor(report.overallScore)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(report.sport, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = if (centered) TextAlign.Center else TextAlign.Start)
        Spacer(Modifier.height(6.dp))
        Text(report.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = if (centered) TextAlign.Center else TextAlign.Start)
    }
}

@Composable
private fun MotionPanel(frames: List<AnimationFrame>, bitmap: android.graphics.Bitmap?, pose: FramePose?) {
    if (frames.isEmpty() && (bitmap == null || pose == null)) return
    Column {
        SectionHeading("Movement replay", "02", "AI TRACKED")
        Spacer(Modifier.height(12.dp))
        Card(
            shape = PageShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Column(Modifier.padding(10.dp)) {
                Box {
                    if (frames.isNotEmpty()) {
                        AnimatedSkeleton(frames, Modifier.clip(RoundedCornerShape(20.dp)))
                    } else if (bitmap != null && pose != null) {
                        SkeletonOverlay(bitmap, pose, Modifier.clip(RoundedCornerShape(20.dp)))
                    }
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.62f),
                        contentColor = Color.White
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).background(SkyCyan, CircleShape))
                            Spacer(Modifier.width(6.dp))
                            Text("POSE MAP", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(
                    if (frames.isNotEmpty()) "Tap the replay to pause or continue" else "Representative tracked frame",
                    Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun CoachingReport(report: TechniqueReport) {
    val groups = listOf(
        Triple("What's working", GoodGreen, report.findings.filter { it.type == FindingType.GOOD }),
        Triple("Biggest opportunities", WarnAmber, report.findings.filter { it.type == FindingType.ISSUE }),
        Triple("Your next session", TipBlue, report.findings.filter { it.type == FindingType.TIP })
    ).filter { it.third.isNotEmpty() }

    Column {
        SectionHeading("Coach's breakdown", "03", "${report.findings.size} INSIGHTS")
        Spacer(Modifier.height(12.dp))
        groups.forEachIndexed { index, (title, color, findings) ->
            InsightGroup(title, color, findings, index + 1)
            if (index != groups.lastIndex) Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun InsightGroup(title: String, accent: Color, findings: List<Finding>, number: Int) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f),
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).background(accent.copy(alpha = 0.15f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Text("0$number", style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            findings.forEachIndexed { index, finding ->
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 7.dp).size(7.dp).background(accent, CircleShape))
                    Spacer(Modifier.width(11.dp))
                    Column {
                        Text(finding.area.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = accent)
                        Spacer(Modifier.height(3.dp))
                        Text(finding.message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePrompt(onSave: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Surface(
        shape = CardShape,
        color = EnergyOrange.copy(alpha = 0.10f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, EnergyOrange.copy(alpha = 0.28f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(EnergyOrange.copy(alpha = 0.16f), SmallShape), contentAlignment = Alignment.Center) {
                Text("▦", color = EnergyOrange, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Add this to your timeline", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Set when this clip was filmed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { open = true }) { Text("Set date", fontWeight = FontWeight.Bold, color = EnergyOrange) }
        }
    }

    if (open) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.coerceAtMost(System.currentTimeMillis())?.let(onSave)
                    open = false
                }) { Text("Save date") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } }
        ) { DatePicker(picker) }
    }
}

@Composable
private fun SavedDateBar(date: Long) {
    Surface(
        shape = CardShape,
        color = GoodGreen.copy(alpha = 0.10f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, GoodGreen.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("✓", color = GoodGreen, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(10.dp))
            Text("${formatSessionDate(date)} added to your progress timeline", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun TimelinePanel(
    entries: List<SessionEntry>,
    sport: Sport,
    selectedMetric: String?,
    onMetricSelected: (String?) -> Unit,
    onDelete: (Long) -> Unit,
    onOpenSession: (Long) -> Unit
) {
    val sorted = entries.filter { it.sportName == sport.name }.sortedBy { it.filmedAtMillis }
    var deleteTarget by remember { mutableStateOf<SessionEntry?>(null) }
    Column {
        SectionHeading("Progress timeline", "02", if (sorted.isEmpty()) "NEW" else "${sorted.size} SESSIONS")
        Spacer(Modifier.height(12.dp))
        if (sorted.isEmpty()) {
            EmptyTimeline(sport)
        } else {
            val latest = sorted.last()
            val previous = sorted.getOrNull(sorted.lastIndex - 1)
            PerformanceMetricsSection(
                sport = sport,
                current = latest.metrics,
                previous = previous?.metrics.orEmpty(),
                selectedMetric = selectedMetric,
                onMetricSelected = onMetricSelected
            )
            Spacer(Modifier.height(16.dp))
            TimelineSummary(sorted, selectedMetric)
            Spacer(Modifier.height(12.dp))
            ProgressChartPremium(sorted, selectedMetric, onOpenSession)
            Spacer(Modifier.height(12.dp))
            sorted.asReversed().take(8).forEachIndexed { index, entry ->
                val chronologicalIndex = sorted.indexOf(entry)
                val previous = sorted.getOrNull(chronologicalIndex - 1)
                SessionRow(
                    entry = entry,
                    previous = previous,
                    selectedMetric = selectedMetric,
                    newest = index == 0,
                    onOpen = { onOpenSession(entry.id) },
                    onDelete = { deleteTarget = entry }
                )
                if (index != sorted.asReversed().take(8).lastIndex) Spacer(Modifier.height(8.dp))
            }
        }
    }
    deleteTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Remove this session?") },
            text = { Text("The ${formatSessionDate(entry.filmedAtMillis)} score will be removed from your timeline.") },
            confirmButton = {
                TextButton(onClick = { onDelete(entry.id); deleteTarget = null }) { Text("Remove", color = ScoreLow) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Keep it") } }
        )
    }
}

@Composable
private fun EmptyTimeline(sport: Sport) {
    Surface(
        shape = PageShape,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.75f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(58.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape), contentAlignment = Alignment.Center) {
                Text("↗", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(12.dp))
            Text("Your progress starts here", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(
                "Analyze your first ${sport.displayName.lowercase()} clip to establish a baseline.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun TimelineSummary(sorted: List<SessionEntry>, selectedMetric: String?) {
    val values = sorted.mapNotNull { sessionValue(it, selectedMetric) }
    val latest = values.lastOrNull()
    val best = values.maxOrNull()
    val change = if (values.size > 1) values.last() - values.first() else 0
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        SummaryStat("LATEST", latest?.toString() ?: "—", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        SummaryStat("BEST", best?.toString() ?: "—", ScoreHigh, Modifier.weight(1f))
        SummaryStat("CHANGE", if (values.isEmpty()) "—" else if (change > 0) "+$change" else "$change", if (change >= 0) ScoreHigh else ScoreLow, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryStat(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = SmallShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(Modifier.padding(13.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = color)
        }
    }
}

@Composable
private fun ProgressChartPremium(
    sorted: List<SessionEntry>,
    selectedMetric: String?,
    onOpenSession: (Long) -> Unit
) {
    val plotted = sorted.mapNotNull { entry ->
        sessionValue(entry, selectedMetric)?.let { entry to it }
    }
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.13f)
    val pointCenter = MaterialTheme.colorScheme.surface
    val metricLabel = selectedMetric ?: "Overall score"
    val description = if (plotted.isEmpty()) "$metricLabel has no saved data" else
        "$metricLabel progress from ${plotted.first().second} to ${plotted.last().second} across ${plotted.size} sessions"
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Performance trend", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(metricLabel.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Spacer(Modifier.height(12.dp))
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(145.dp)
                    .semantics { contentDescription = description }
            ) {
                for (i in 0..4) {
                    val y = size.height * i / 4f
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), 2f)
                }
                if (plotted.size == 1) {
                    val point = Offset(size.width / 2f, size.height * (1f - plotted[0].second / 100f))
                    drawCircle(line, 10f, point)
                    drawCircle(pointCenter, 4f, point)
                } else if (plotted.size > 1) {
                    val step = size.width / (plotted.size - 1)
                    val points = plotted.mapIndexed { index, (_, value) -> Offset(step * index, size.height * (1f - value / 100f)) }
                    points.zipWithNext().forEach { (a, b) -> drawLine(line, a, b, 7f, StrokeCap.Round) }
                    points.forEach { point ->
                        drawCircle(line, 10f, point)
                        drawCircle(pointCenter, 4f, point)
                    }
                }
            }
            if (plotted.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onOpenSession(plotted.first().first.id) }) {
                        Text(formatSessionDate(plotted.first().first.filmedAtMillis), style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.weight(1f))
                    if (plotted.size > 1) {
                        TextButton(onClick = { onOpenSession(plotted.last().first.id) }) {
                            Text(formatSessionDate(plotted.last().first.filmedAtMillis), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    entry: SessionEntry,
    previous: SessionEntry?,
    selectedMetric: String?,
    newest: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val value = sessionValue(entry, selectedMetric)
    val previousValue = previous?.let { sessionValue(it, selectedMetric) }
    val delta = if (value != null && previousValue != null) value - previousValue else null
    val deltaColor = when { delta == null -> MaterialTheme.colorScheme.onSurfaceVariant; delta > 0 -> ScoreHigh; delta < 0 -> ScoreLow; else -> ScoreMid }
    Surface(
        onClick = onOpen,
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            val displayValue = value ?: 0
            Box(Modifier.size(50.dp).background(scoreColor(displayValue).copy(alpha = 0.13f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Text(value?.toString() ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = scoreColor(displayValue))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatSessionDate(entry.filmedAtMillis), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                    if (newest) {
                        Spacer(Modifier.width(7.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.primary
                        ) {
                            Text("LATEST", Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Text(
                    when { value == null -> "Metric not saved"; delta == null -> "Baseline session"; delta > 0 -> "↑ $delta points from prior"; delta < 0 -> "↓ ${-delta} points from prior"; else -> "No change from prior" },
                    style = MaterialTheme.typography.labelMedium,
                    color = deltaColor,
                    fontWeight = FontWeight.SemiBold
                )
                Text("Tap date to open the full analysis", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onDelete, contentPadding = PaddingValues(10.dp)) {
                Text("×", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun sessionValue(entry: SessionEntry, selectedMetric: String?): Int? =
    if (selectedMetric == null) entry.score else entry.metrics[selectedMetric]

@Composable
private fun ErrorExperience(message: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 36.dp)) {
        Box(Modifier.size(76.dp).background(ScoreLow.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
            Text("!", style = MaterialTheme.typography.headlineLarge, color = ScoreLow, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(20.dp))
        Text("We couldn't analyze that clip", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 460.dp), maxLines = 4, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(22.dp))
        OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(18.dp), modifier = Modifier.height(52.dp)) {
            Text("Try another clip", fontWeight = FontWeight.Bold)
        }
    }
}

private fun scoreColor(score: Int): Color = when {
    score >= 75 -> ScoreHigh
    score >= 50 -> ScoreMid
    else -> ScoreLow
}

private fun formatSessionDate(millis: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(millis))


