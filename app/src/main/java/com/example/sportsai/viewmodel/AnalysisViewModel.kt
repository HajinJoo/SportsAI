package com.example.sportsai.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportsai.data.GeminiCoach
import com.example.sportsai.data.GeminiApiKeyStore
import com.example.sportsai.data.HighlightExtractor
import com.example.sportsai.data.HistoryRepository
import com.example.sportsai.data.PoseAnalyzer
import com.example.sportsai.data.TechniqueAnalyzer
import com.example.sportsai.data.VideoClipExporter
import com.example.sportsai.model.AnimationFrame
import com.example.sportsai.model.FramePose
import com.example.sportsai.model.HighlightClip
import com.example.sportsai.model.SessionEntry
import com.example.sportsai.model.Sport
import com.example.sportsai.model.TechniqueReport
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AnalysisUiState {
    data object Idle : AnalysisUiState
    data class Analyzing(val progress: Float, val stage: Stage = Stage.SCANNING) : AnalysisUiState {
        enum class Stage(val label: String) {
            SCANNING("Tracking your body frame by frame…"),
            COACHING("Building your coaching report…")
        }
    }
    data class Done(
        val report: TechniqueReport,
        val sport: Sport,
        val keyFrame: Bitmap? = null,
        val keyFramePose: FramePose? = null,
        val animationFrames: List<AnimationFrame> = emptyList(),
        val analysisId: Long,
        val sourceVideoUri: String,
        val videoDurationMs: Long,
        /** Filming date chosen by the user; null until they set it. */
        val filmedAtMillis: Long? = null,
        /** True once this session has been added to the progress timeline. */
        val savedToTimeline: Boolean = false,
        val sessionId: Long? = null
    ) : AnalysisUiState
    /** Viewing a past session from the timeline — similar to Done but read-only. */
    data class ViewingPastSession(
        val entry: SessionEntry,
        val sport: Sport
    ) : AnalysisUiState
    data class Error(val message: String) : AnalysisUiState
}

sealed interface HighlightEditUiState {
    data object Idle : HighlightEditUiState
    data class Saving(val clipId: Long) : HighlightEditUiState
    data class Error(val message: String) : HighlightEditUiState
}

data class GeminiSettingsUiState(
    val isLoading: Boolean = true,
    val configured: Boolean = false,
    val maskedKey: String? = null,
    val isTesting: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false
)

class AnalysisViewModel(app: Application) : AndroidViewModel(app) {

    private val analyzer = PoseAnalyzer(app.applicationContext)
    private val techniqueAnalyzer = TechniqueAnalyzer()
    private val geminiApiKeyStore = GeminiApiKeyStore(app.applicationContext)
    private val geminiCoach = GeminiCoach(geminiApiKeyStore::read)
    private val highlightExtractor = HighlightExtractor()
    private val videoClipExporter = VideoClipExporter(app.applicationContext)
    private val history = HistoryRepository(app.applicationContext)

    private val _uiState = MutableStateFlow<AnalysisUiState>(AnalysisUiState.Idle)
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    private val _timeline = MutableStateFlow<List<SessionEntry>>(emptyList())
    val timeline: StateFlow<List<SessionEntry>> = _timeline.asStateFlow()

    /** Currently selected metric filter (null = show all). */
    private val _selectedMetric = MutableStateFlow<String?>(null)
    val selectedMetric: StateFlow<String?> = _selectedMetric.asStateFlow()

    private val _highlightEditState = MutableStateFlow<HighlightEditUiState>(HighlightEditUiState.Idle)
    val highlightEditState: StateFlow<HighlightEditUiState> = _highlightEditState.asStateFlow()

    private val _geminiSettings = MutableStateFlow(GeminiSettingsUiState())
    val geminiSettings: StateFlow<GeminiSettingsUiState> = _geminiSettings.asStateFlow()

    /** Incremented on each settings action so a stale network result cannot overwrite a newer choice. */
    private var geminiSettingsOperationId = 0L

    init {
        val operationId = geminiSettingsOperationId
        viewModelScope.launch {
            _timeline.value = withContext(Dispatchers.IO) { history.load() }
            val savedSettings = withContext(Dispatchers.IO) { readStoredGeminiSettings() }
            if (operationId == geminiSettingsOperationId) {
                _geminiSettings.value = savedSettings
            }
        }
    }

    fun saveAndTestGeminiApiKey(apiKey: String) {
        val normalized = apiKey.trim()
        if (normalized.isEmpty()) {
            _geminiSettings.value = _geminiSettings.value.copy(
                isLoading = false,
                statusMessage = "Enter a Gemini API key first.",
                isError = true
            )
            return
        }

        val operationId = ++geminiSettingsOperationId
        _geminiSettings.value = _geminiSettings.value.copy(
            isLoading = false,
            isTesting = true,
            statusMessage = "Encrypting and saving this key…",
            isError = false
        )
        viewModelScope.launch {
            val saveError = runCatching {
                withContext(Dispatchers.IO) { geminiApiKeyStore.save(normalized) }
            }.exceptionOrNull()
            if (operationId != geminiSettingsOperationId) return@launch

            if (saveError != null) {
                _geminiSettings.value = readStoredGeminiSettings().copy(
                    statusMessage = "This key could not be saved securely on the device.",
                    isError = true
                )
                return@launch
            }

            _geminiSettings.value = readStoredGeminiSettings().copy(
                isTesting = true,
                statusMessage = "Key saved. Testing Gemini…"
            )
            val result = geminiCoach.testConnection()
            if (operationId != geminiSettingsOperationId) return@launch
            _geminiSettings.value = readStoredGeminiSettings().copy(
                isTesting = false,
                statusMessage = if (result.successful) {
                    result.message
                } else {
                    "Key saved, but ${result.message.replaceFirstChar { it.lowercase() }}"
                },
                isError = !result.successful
            )
        }
    }

    fun testSavedGeminiApiKey() {
        if (!_geminiSettings.value.configured) {
            _geminiSettings.value = _geminiSettings.value.copy(
                isLoading = false,
                statusMessage = "Add and save an API key before testing Gemini.",
                isError = true
            )
            return
        }

        val operationId = ++geminiSettingsOperationId
        _geminiSettings.value = _geminiSettings.value.copy(
            isTesting = true,
            statusMessage = "Testing the saved key…",
            isError = false
        )
        viewModelScope.launch {
            val result = geminiCoach.testConnection()
            if (operationId != geminiSettingsOperationId) return@launch
            _geminiSettings.value = readStoredGeminiSettings().copy(
                statusMessage = result.message,
                isError = !result.successful
            )
        }
    }

    fun removeGeminiApiKey() {
        val operationId = ++geminiSettingsOperationId
        _geminiSettings.value = GeminiSettingsUiState(
            isLoading = false,
            statusMessage = "Removing the saved key…"
        )
        viewModelScope.launch {
            withContext(Dispatchers.IO) { geminiApiKeyStore.clear() }
            if (operationId != geminiSettingsOperationId) return@launch
            _geminiSettings.value = GeminiSettingsUiState(
                isLoading = false,
                statusMessage = "API key removed. SportsAI will use offline coaching."
            )
        }
    }

    fun clearGeminiSettingsMessage() {
        _geminiSettings.value = _geminiSettings.value.copy(
            statusMessage = null,
            isError = false
        )
    }

    fun analyze(videoUri: Uri, sport: Sport) {
        cleanupUnsavedDraft()
        val analysisId = System.currentTimeMillis()
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                videoUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        _uiState.value = AnalysisUiState.Analyzing(0f)
        _highlightEditState.value = HighlightEditUiState.Idle
        viewModelScope.launch {
            try {
                val result = analyzer.analyze(videoUri) { progress ->
                    _uiState.value = AnalysisUiState.Analyzing(progress)
                }
                _uiState.value = AnalysisUiState.Analyzing(
                    1f, AnalysisUiState.Analyzing.Stage.COACHING
                )
                // Prefer AI (Gemini) coaching from the actual frames; fall back to the
                // on-device rule engine if the key is missing or the request fails.
                val report = try {
                    if (geminiCoach.isConfigured && result.animationFrames.isNotEmpty()) {
                        geminiCoach.coach(result.animationFrames, result, sport)
                    } else {
                        techniqueAnalyzer.analyze(result, sport)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (_geminiSettings.value.configured && !_geminiSettings.value.isTesting) {
                        _geminiSettings.value = _geminiSettings.value.copy(
                            statusMessage = "Gemini coaching was unavailable, so this report used offline coaching.",
                            isError = true
                        )
                    }
                    techniqueAnalyzer.analyze(result, sport)
                }

                // Extract highlight clips from the pose timeline.
                val highlights = highlightExtractor.extract(result, sport).map { clip ->
                    runCatching {
                        videoClipExporter.export(videoUri, clip, analysisId)
                    }.getOrDefault(clip)
                }
                val reportWithHighlights = report.copy(highlights = highlights)

                _uiState.value = AnalysisUiState.Done(
                    report = reportWithHighlights,
                    sport = sport,
                    keyFrame = result.keyFrame,
                    keyFramePose = result.keyFramePose,
                    animationFrames = result.animationFrames,
                    analysisId = analysisId,
                    sourceVideoUri = videoUri.toString(),
                    videoDurationMs = result.durationMs
                )
            } catch (e: Exception) {
                _uiState.value = AnalysisUiState.Error(e.message ?: "Analysis failed")
            }
        }
    }

    /** Called when the user picks the filming date manually (video had no date metadata). */
    fun saveSessionWithDate(filmedAtMillis: Long) {
        val initial = _uiState.value as? AnalysisUiState.Done ?: return
        if (initial.savedToTimeline) return
        viewModelScope.launch(Dispatchers.IO) {
            val latest = _uiState.value as? AnalysisUiState.Done ?: return@launch
            if (latest.analysisId != initial.analysisId || latest.savedToTimeline) return@launch
            val entry = persist(latest, filmedAtMillis)
            _uiState.value = latest.copy(
                filmedAtMillis = filmedAtMillis,
                savedToTimeline = true,
                sessionId = entry.id
            )
        }
    }

    /** Loads a past session and displays it as a ViewingPastSession state. */
    fun loadSession(sessionId: Long) {
        val cached = _timeline.value.firstOrNull { it.id == sessionId }
        if (cached != null) {
            val sport = runCatching { Sport.valueOf(cached.sportName) }.getOrNull() ?: return
            _highlightEditState.value = HighlightEditUiState.Idle
            _uiState.value = AnalysisUiState.ViewingPastSession(entry = cached, sport = sport)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val entry = history.findById(sessionId) ?: return@launch
            val sport = try { Sport.valueOf(entry.sportName) } catch (_: Exception) { return@launch }
            _highlightEditState.value = HighlightEditUiState.Idle
            _uiState.value = AnalysisUiState.ViewingPastSession(entry = entry, sport = sport)
        }
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _timeline.value = history.delete(id)
            val current = _uiState.value
            if (current is AnalysisUiState.ViewingPastSession && current.entry.id == id) {
                _uiState.value = AnalysisUiState.Idle
            }
        }
    }

    fun setMetricFilter(metric: String?) {
        _selectedMetric.value = metric
    }

    fun updateHighlight(updated: HighlightClip) {
        if (_highlightEditState.value is HighlightEditUiState.Saving) return
        val state = _uiState.value
        val sourceUri = when (state) {
            is AnalysisUiState.Done -> state.sourceVideoUri
            is AnalysisUiState.ViewingPastSession -> state.entry.sourceVideoUri
            else -> ""
        }
        val sessionId = when (state) {
            is AnalysisUiState.Done -> state.analysisId
            is AnalysisUiState.ViewingPastSession -> state.entry.id
            else -> 0L
        }
        if (sourceUri.isBlank() || sessionId == 0L) {
            _highlightEditState.value = HighlightEditUiState.Error(
                "The original video is no longer available. You can still watch the saved highlight."
            )
            return
        }

        _highlightEditState.value = HighlightEditUiState.Saving(updated.id)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val exported = videoClipExporter.export(
                    Uri.parse(sourceUri),
                    updated.copy(editedByUser = true),
                    sessionId
                )
                applyHighlightUpdate(exported)
                _highlightEditState.value = HighlightEditUiState.Idle
            } catch (error: Exception) {
                _highlightEditState.value = HighlightEditUiState.Error(
                    error.message ?: "The highlight could not be updated"
                )
            }
        }
    }

    fun clearHighlightEditError() {
        if (_highlightEditState.value is HighlightEditUiState.Error) {
            _highlightEditState.value = HighlightEditUiState.Idle
        }
    }

    private fun applyHighlightUpdate(updated: HighlightClip) {
        when (val current = _uiState.value) {
            is AnalysisUiState.Done -> {
                val updatedReport = current.report.copy(
                    highlights = current.report.highlights.map {
                        if (it.id == updated.id) updated else it
                    }
                )
                val updatedState = current.copy(report = updatedReport)
                _uiState.value = updatedState
                current.sessionId?.let { savedId ->
                    val saved = _timeline.value.firstOrNull { it.id == savedId } ?: return@let
                    val updatedEntry = saved.copy(highlights = updatedReport.highlights)
                    _timeline.value = history.update(updatedEntry)
                }
            }

            is AnalysisUiState.ViewingPastSession -> {
                val updatedEntry = current.entry.copy(
                    highlights = current.entry.highlights.map {
                        if (it.id == updated.id) updated else it
                    }
                )
                _timeline.value = history.update(updatedEntry)
                _uiState.value = current.copy(entry = updatedEntry)
            }

            else -> Unit
        }
    }

    private fun persist(state: AnalysisUiState.Done, filmedAtMillis: Long): SessionEntry {
        val entry = SessionEntry(
            id = state.analysisId,
            sportName = state.sport.name,
            filmedAtMillis = filmedAtMillis,
            score = state.report.overallScore,
            summary = state.report.summary,
            metrics = state.report.metricScores,
            aiOverview = state.report.aiOverview,
            highlights = state.report.highlights,
            findings = state.report.findings,
            detectionRate = state.report.detectionRate,
            sourceVideoUri = state.sourceVideoUri,
            videoDurationMs = state.videoDurationMs
        )
        _timeline.value = history.add(entry)
        return entry
    }

    private fun readStoredGeminiSettings(): GeminiSettingsUiState {
        val maskedKey = geminiApiKeyStore.maskedKey()
        return GeminiSettingsUiState(
            isLoading = false,
            configured = maskedKey != null,
            maskedKey = maskedKey
        )
    }

    fun reset() {
        cleanupUnsavedDraft()
        _highlightEditState.value = HighlightEditUiState.Idle
        _uiState.value = AnalysisUiState.Idle
    }

    private fun cleanupUnsavedDraft() {
        val current = _uiState.value as? AnalysisUiState.Done ?: return
        if (!current.savedToTimeline) {
            viewModelScope.launch(Dispatchers.IO) {
                videoClipExporter.deleteSession(current.analysisId)
            }
        }
    }

    override fun onCleared() {
        val current = _uiState.value as? AnalysisUiState.Done
        if (current != null && !current.savedToTimeline) {
            videoClipExporter.deleteSession(current.analysisId)
        }
        super.onCleared()
        analyzer.close()
    }
}
