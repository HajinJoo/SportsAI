package com.example.sportsai.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.sportsai.model.FramePose
import com.example.sportsai.model.LandmarkPoint

// ML Kit landmark type constants.
private const val LEFT_SHOULDER = 11
private const val RIGHT_SHOULDER = 12
private const val LEFT_ELBOW = 13
private const val RIGHT_ELBOW = 14
private const val LEFT_WRIST = 15
private const val RIGHT_WRIST = 16
private const val LEFT_HIP = 23
private const val RIGHT_HIP = 24
private const val LEFT_KNEE = 25
private const val RIGHT_KNEE = 26
private const val LEFT_ANKLE = 27
private const val RIGHT_ANKLE = 28

// Bone connections to draw between landmarks.
internal val BONES = listOf(
    LEFT_SHOULDER to RIGHT_SHOULDER,
    LEFT_SHOULDER to LEFT_ELBOW,
    LEFT_ELBOW to LEFT_WRIST,
    RIGHT_SHOULDER to RIGHT_ELBOW,
    RIGHT_ELBOW to RIGHT_WRIST,
    LEFT_SHOULDER to LEFT_HIP,
    RIGHT_SHOULDER to RIGHT_HIP,
    LEFT_HIP to RIGHT_HIP,
    LEFT_HIP to LEFT_KNEE,
    LEFT_KNEE to LEFT_ANKLE,
    RIGHT_HIP to RIGHT_KNEE,
    RIGHT_KNEE to RIGHT_ANKLE
)

/** Only draw major body joints (skip the 20+ face/hand/foot points ML Kit reports). */
internal val BODY_JOINTS = setOf(
    LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_ELBOW, RIGHT_ELBOW, LEFT_WRIST, RIGHT_WRIST,
    LEFT_HIP, RIGHT_HIP, LEFT_KNEE, RIGHT_KNEE, LEFT_ANKLE, RIGHT_ANKLE
)

/** Skip landmarks the model isn't confident about so the skeleton doesn't jitter. */
internal const val DRAW_MIN_LIKELIHOOD = 0.5f

/**
 * Draws the detected pose skeleton on top of the representative key frame,
 * so the user can see exactly what the AI measured.
 */
@Composable
fun SkeletonOverlay(
    bitmap: Bitmap,
    pose: FramePose,
    modifier: Modifier = Modifier
) {
    val image = bitmap.asImageBitmap()
    val imgW = bitmap.width.toFloat()
    val imgH = bitmap.height.toFloat()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(imgW / imgH)
    ) {
        // Draw the frame scaled to fill the canvas.
        drawImage(
            image = image,
            dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
        )

        val scaleX = size.width / imgW
        val scaleY = size.height / imgH
        val visible = pose.landmarks.filter {
            it.type in BODY_JOINTS && it.inFrameLikelihood >= DRAW_MIN_LIKELIHOOD
        }
        val byType = visible.associateBy { it.type }

        fun toOffset(p: LandmarkPoint) = Offset(p.x * scaleX, p.y * scaleY)

        // Bones.
        BONES.forEach { (a, b) ->
            val pa = byType[a]
            val pb = byType[b]
            if (pa != null && pb != null) {
                drawLine(
                    color = Color(0xFF00E5FF),
                    start = toOffset(pa),
                    end = toOffset(pb),
                    strokeWidth = 6f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        }

        // Joints.
        visible.forEach { p ->
            drawJoint(toOffset(p))
        }
    }
}

private fun DrawScope.drawJoint(center: Offset) {
    drawCircle(color = Color.White, radius = 8f, center = center)
    drawCircle(
        color = Color(0xFFFF4081),
        radius = 8f,
        center = center,
        style = Stroke(width = 3f)
    )
}


