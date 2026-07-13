package com.example.sportsai.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sportsai.data.GeminiCoach
import com.example.sportsai.data.HistoryRepository
import com.example.sportsai.data.PoseAnalyzer
import com.example.sportsai.data.TechniqueAnalyzer
import com.example.sportsai.model.AnimationFrame
import com.example.sportsai.model.FramePose
import com.example.sportsai.model.SessionEntry
import com.example.sportsai.model.Sport
import com.example.sportsai.model.TechniqueReport
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AnalysisUiState {
    data object Idle : AnalysisUiState
    data class Analyzing(val progress: Float, val stage: Stage = Stage.SCANNING) : AnalysisUiState {
        enum class Stage(val label: String) {
            SCANNING("Tracking your body frame by frame…"),
            COACHING("Your AI coach is reviewing the clip…")
        }
    }
    data class Done(
        val report: TechniqueReport,
        val sport: Sport,
        val keyFrame: Bitmap? = null,
        val keyFramePose: FramePose? = null,
        val animationFrames: List<AnimationFrame> = emptyList(),
        /** Filming date chosen by the user; null until they set it. */
        val filmedAtMillis: Long? = null,
        /** True once this session has been added to the progress timeline. */
        val savedToTimeline: Boolean = false
    ) : AnalysisUiState
    data class Error(val message: String) : AnalysisUiState
}

class AnalysisViewModel(app: Application) : AndroidViewModel(app) {

    private val analyzer = PoseAnalyzer(app.applicationContext)
    private val techniqueAnalyzer = TechniqueAnalyzer()
    private val geminiCoach = GeminiCoach()
    private val history = HistoryRepository(app.applicationContext)

    private val _uiState = MutableStateFlow<AnalysisUiState>(AnalysisUiState.Idle)
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    private val _timeline = MutableStateFlow<List<SessionEntry>>(emptyList())
    val timeline: StateFlow<List<SessionEntry>> = _timeline.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _timeline.value = history.load()
        }
    }

    fun analyze(videoUri: Uri, sport: Sport) {
        _uiState.value = AnalysisUiState.Analyzing(0f)
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
                    techniqueAnalyzer.analyze(result, sport)
                }

                _uiState.value = AnalysisUiState.Done(
                    report = report,
                    sport = sport,
                    keyFrame = result.keyFrame,
                    keyFramePose = result.keyFramePose,
                    animationFrames = result.animationFrames
                )
            } catch (e: Exception) {
                _uiState.value = AnalysisUiState.Error(e.message ?: "Analysis failed")
            }
        }
    }

    /** Called when the user picks the filming date manually (video had no date metadata). */
    fun saveSessionWithDate(filmedAtMillis: Long) {
        val s = _uiState.value as? AnalysisUiState.Done ?: return
        if (s.savedToTimeline) return
        viewModelScope.launch(Dispatchers.IO) {
            persist(s.sport, filmedAtMillis, s.report)
            _uiState.value = s.copy(filmedAtMillis = filmedAtMillis, savedToTimeline = true)
        }
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _timeline.value = history.delete(id)
        }
    }

    private fun persist(sport: Sport, filmedAtMillis: Long, report: TechniqueReport) {
        val entry = SessionEntry(
            id = System.currentTimeMillis(),
            sportName = sport.name,
            filmedAtMillis = filmedAtMillis,
            score = report.overallScore,
            summary = report.summary
        )
        _timeline.value = history.add(entry)
    }

    fun reset() {
        _uiState.value = AnalysisUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        analyzer.close()
    }
}



