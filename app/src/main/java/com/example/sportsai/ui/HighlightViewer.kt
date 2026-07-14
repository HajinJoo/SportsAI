package com.example.sportsai.ui

import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import android.util.Size
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.sportsai.model.AnimationFrame
import com.example.sportsai.model.HighlightClip
import com.example.sportsai.ui.theme.EnergyOrange
import com.example.sportsai.ui.theme.ScoreHigh
import com.example.sportsai.ui.theme.ScoreLow
import com.example.sportsai.ui.theme.SkyCyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

private val ViewerShape = RoundedCornerShape(28.dp)

/** Real AI-cut MP4 highlights. Tapping a video opens playback and boundary editing. */
@Composable
fun HighlightsSection(
    highlights: List<HighlightClip>,
    animationFrames: List<AnimationFrame>,
    sourceVideoUri: String,
    videoDurationMs: Long,
    onSave: (HighlightClip) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
    savingClipId: Long? = null,
    editError: String? = null
) {
    if (highlights.isEmpty()) return
    var viewingClip by remember { mutableStateOf<HighlightClip?>(null) }
    var startInEditor by remember { mutableStateOf(false) }

    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "AI HIGHLIGHTS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${highlights.size} CUT ${if (highlights.size == 1) "CLIP" else "CLIPS"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "SportsAI found the strongest complete action and cut it into a focused video. Tap it to watch immediately or adjust the start and end.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        if (savingClipId != null) {
            StatusBanner("Saving your edited highlight…", SkyCyan)
            Spacer(Modifier.height(8.dp))
        }
        editError?.let { message ->
            StatusBanner(message, ScoreLow, onClearError)
            Spacer(Modifier.height(8.dp))
        }

        highlights.forEachIndexed { index, clip ->
            HighlightCard(
                clip = clip,
                isSaving = savingClipId == clip.id,
                onOpen = { edit ->
                    startInEditor = edit
                    viewingClip = clip
                }
            )
            if (index != highlights.lastIndex) Spacer(Modifier.height(10.dp))
        }
    }

    viewingClip?.let { clip ->
        HighlightViewerDialog(
            clip = clip,
            allFrames = animationFrames,
            sourceVideoUri = sourceVideoUri,
            videoDurationMs = videoDurationMs,
            initiallyEditing = startInEditor,
            isSaving = savingClipId == clip.id,
            onDismiss = { viewingClip = null },
            onSave = { updated ->
                onSave(updated)
                viewingClip = null
            }
        )
    }
}

@Composable
private fun HighlightCard(
    clip: HighlightClip,
    isSaving: Boolean,
    onOpen: (Boolean) -> Unit
) {
    val thumbnail by produceState<Bitmap?>(initialValue = null, clip.videoPath) {
        value = if (clip.videoPath.isBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                ThumbnailUtils.createVideoThumbnail(
                    File(clip.videoPath),
                    Size(960, 540),
                    null
                )
            }.getOrNull()
        }
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            onClick = { onOpen(false) },
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(
                            Brush.linearGradient(
                                listOf(MaterialTheme.colorScheme.primary, SkyCyan)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    thumbnail?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "${clip.label} highlight video",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.62f),
                        contentColor = Color.White
                    ) {
                        Text(
                            "▶",
                            Modifier.padding(horizontal = 17.dp, vertical = 13.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Surface(
                        modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.68f),
                        contentColor = Color.White
                    ) {
                        Text(
                            if (clip.editedByUser) "AI PICK · EDITED" else "AI PICK",
                            Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(clip.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "${formatMs(clip.startMs)} – ${formatMs(clip.endMs)} · score ${clip.score}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { onOpen(true) },
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EnergyOrange.copy(alpha = 0.14f),
                            contentColor = EnergyOrange
                        )
                    ) {
                        Text(if (isSaving) "Saving…" else "Edit", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun HighlightViewerDialog(
    clip: HighlightClip,
    allFrames: List<AnimationFrame>,
    sourceVideoUri: String,
    videoDurationMs: Long,
    initiallyEditing: Boolean,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (HighlightClip) -> Unit
) {
    var isEditing by remember(clip.id) { mutableStateOf(initiallyEditing) }
    var editStart by remember(clip.id) { mutableFloatStateOf(clip.startMs.toFloat()) }
    var editEnd by remember(clip.id) { mutableFloatStateOf(clip.endMs.toFloat()) }
    val maxTime = maxOf(videoDurationMs, clip.endMs + 500L, 1_000L).toFloat()
    val canEditOriginal = sourceVideoUri.isNotBlank()
    // Prefer the source for both viewing and editing: it is already known to be playable and lets
    // us seek to the exact selected action. The generated MP4 remains a viewing fallback.
    val playbackUri = if (canEditOriginal) sourceVideoUri else clip.videoPath
    val playbackStart = if (canEditOriginal) editStart.toLong() else 0L
    val playbackEnd = if (canEditOriginal) editEnd.toLong()
    else (clip.endMs - clip.startMs).coerceAtLeast(250L)
    val fallbackUri = if (!isEditing && canEditOriginal) clip.videoPath else ""

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.88f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = ViewerShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.fillMaxWidth(0.94f)
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (isEditing) "VIDEO EDITOR" else "AI HIGHLIGHT",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Black
                            )
                            Text(clip.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = onDismiss) { Text("Close") }
                    }
                    Spacer(Modifier.height(12.dp))

                    if (playbackUri.isNotBlank()) {
                        HighlightVideoPlayer(
                            uriString = playbackUri,
                            startMs = playbackStart,
                            endMs = playbackEnd,
                            fallbackUriString = fallbackUri,
                            fallbackEndMs = (clip.endMs - clip.startMs).coerceAtLeast(250L),
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(20.dp))
                        )
                    } else {
                        val frames = allFrames.filter {
                            it.pose.timestampMs in editStart.toLong()..editEnd.toLong()
                        }.ifEmpty { allFrames }
                        if (frames.isNotEmpty()) {
                            AnimatedSkeleton(frames, Modifier.clip(RoundedCornerShape(20.dp)))
                        } else {
                            Box(
                                Modifier.fillMaxWidth().height(210.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Video preview unavailable")
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        "${formatMs(editStart.toLong())} – ${formatMs(editEnd.toLong())}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (isEditing) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Move either boundary, then preview the exact selected section before saving the new cut.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        BoundarySlider(
                            label = "START",
                            value = editStart,
                            maxTime = maxTime,
                            color = MaterialTheme.colorScheme.primary,
                            enabled = !isSaving,
                            onChange = { editStart = it.coerceAtMost(editEnd - MIN_CLIP_MS) }
                        )
                        BoundarySlider(
                            label = "END",
                            value = editEnd,
                            maxTime = maxTime,
                            color = SkyCyan,
                            enabled = !isSaving,
                            onChange = { editEnd = it.coerceAtLeast(editStart + MIN_CLIP_MS) }
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isEditing) {
                            OutlinedButton(
                                onClick = {
                                    editStart = clip.startMs.toFloat()
                                    editEnd = clip.endMs.toFloat()
                                    isEditing = false
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                enabled = !isSaving,
                                shape = RoundedCornerShape(14.dp)
                            ) { Text("Cancel", fontWeight = FontWeight.Bold) }
                            Button(
                                onClick = {
                                    onSave(
                                        clip.copy(
                                            startMs = editStart.toLong(),
                                            endMs = editEnd.toLong(),
                                            editedByUser = true
                                        )
                                    )
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                enabled = !isSaving && canEditOriginal,
                                shape = RoundedCornerShape(14.dp)
                            ) { Text(if (isSaving) "Saving…" else "Save cut", fontWeight = FontWeight.Bold) }
                        } else {
                            OutlinedButton(
                                onClick = { isEditing = true },
                                modifier = Modifier.weight(1f).height(48.dp),
                                enabled = canEditOriginal,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = EnergyOrange)
                            ) { Text("✂ Edit clip", fontWeight = FontWeight.Bold) }
                            Button(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) { Text("Done", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoundarySlider(
    label: String,
    value: Float,
    maxTime: Float,
    color: Color,
    enabled: Boolean,
    onChange: (Float) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(10.dp))
        Slider(
            value = value.coerceIn(0f, maxTime),
            onValueChange = onChange,
            valueRange = 0f..maxTime,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color)
        )
    }
}

@Composable
private fun HighlightVideoPlayer(
    uriString: String,
    startMs: Long,
    endMs: Long,
    fallbackUriString: String = "",
    fallbackEndMs: Long = 0L,
    modifier: Modifier = Modifier
) {
    var player by remember { mutableStateOf<VideoView?>(null) }
    var useFallback by remember(uriString, fallbackUriString) { mutableStateOf(false) }
    var playbackFailed by remember(uriString, fallbackUriString) { mutableStateOf(false) }
    val activeUri = if (useFallback) fallbackUriString else uriString
    val activeStart = if (useFallback) 0L else startMs
    val activeEnd = if (useFallback) fallbackEndMs else endMs

    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                VideoView(context).apply {
                    val controls = MediaController(context)
                    controls.setAnchorView(this)
                    setMediaController(controls)
                    player = this
                }
            },
            update = { view ->
                val playbackKey = "$activeUri:$activeStart:$activeEnd"
                if (activeUri.isNotBlank() && view.tag != playbackKey) {
                    view.tag = playbackKey
                    view.setOnErrorListener { _, _, _ ->
                        if (!useFallback && fallbackUriString.isNotBlank()) {
                            useFallback = true
                        } else {
                            playbackFailed = true
                        }
                        true
                    }
                    val uri = if (activeUri.contains("://")) Uri.parse(activeUri)
                    else Uri.fromFile(File(activeUri))
                    view.setVideoURI(uri)
                    view.setOnPreparedListener {
                        playbackFailed = false
                        view.seekTo(activeStart.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                        view.start()
                    }
                }
            }
        )
        if (playbackFailed) {
            Text(
                "Video preview unavailable",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(20.dp)
            )
        }
    }

    LaunchedEffect(player, activeUri, activeStart, activeEnd) {
        val view = player ?: return@LaunchedEffect
        while (true) {
            delay(120)
            val actualEnd = if (view.duration > 0) minOf(activeEnd, view.duration.toLong()) else activeEnd
            if (view.currentPosition.toLong() >= actualEnd - 40L) {
                view.seekTo(activeStart.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                view.start()
            }
        }
    }
    DisposableEffect(player) {
        onDispose { runCatching { player?.stopPlayback() } }
    }
}

@Composable
private fun StatusBanner(message: String, color: Color, onDismiss: (() -> Unit)? = null) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.12f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(9.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            onDismiss?.let { TextButton(onClick = it) { Text("Dismiss") } }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1_000L
    val min = totalSec / 60L
    val sec = totalSec % 60L
    val tenth = (ms % 1_000L) / 100L
    return if (min > 0L) "$min:${sec.toString().padStart(2, '0')}.$tenth" else "$sec.${tenth}s"
}

private const val MIN_CLIP_MS = 250f
