package com.example.sportsai.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntSize
import com.example.sportsai.model.AnimationFrame
import kotlinx.coroutines.delay

/**
 * Plays the analyzed clip back as a looping animation with the pose skeleton
 * drawn on every frame — a "moving skeleton" instead of a single still image.
 */
@Composable
fun AnimatedSkeleton(
    frames: List<AnimationFrame>,
    modifier: Modifier = Modifier,
    frameDurationMs: Long = 120L
) {
    if (frames.isEmpty()) return

    var index by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }

    LaunchedEffect(frames, playing) {
        while (playing) {
            delay(frameDurationMs)
            index = (index + 1) % frames.size
        }
    }

    val frame = frames[index]
    val bitmap = frame.bitmap
    val image = remember(frame) { bitmap.asImageBitmap() }
    val imgW = bitmap.width.toFloat()
    val imgH = bitmap.height.toFloat()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(imgW / imgH)
            .clickable { playing = !playing }
    ) {
        drawImage(
            image = image,
            dstSize = IntSize(size.width.toInt(), size.height.toInt())
        )

        val scaleX = size.width / imgW
        val scaleY = size.height / imgH
        val visible = frame.pose.landmarks.filter {
            it.type in BODY_JOINTS && it.inFrameLikelihood >= DRAW_MIN_LIKELIHOOD
        }
        val byType = visible.associateBy { it.type }

        BONES.forEach { (a, b) ->
            val pa = byType[a]
            val pb = byType[b]
            if (pa != null && pb != null) {
                drawLine(
                    color = Color(0xFF00E5FF),
                    start = Offset(pa.x * scaleX, pa.y * scaleY),
                    end = Offset(pb.x * scaleX, pb.y * scaleY),
                    strokeWidth = 6f,
                    cap = StrokeCap.Round
                )
            }
        }

        visible.forEach { p ->
            val c = Offset(p.x * scaleX, p.y * scaleY)
            drawCircle(color = Color.White, radius = 7f, center = c)
            drawCircle(
                color = Color(0xFFFF4081),
                radius = 7f,
                center = c,
                style = Stroke(width = 3f)
            )
        }
    }
}

