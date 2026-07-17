package com.example.sportsai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sportsai.data.BatBallDetector
import com.example.sportsai.model.TrackedObjectType
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class BatBallDetectorInstrumentedTest {

    @Test
    fun bundledObjectModelDetectsVisibleBatInSourceCoordinates() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val original = instrumentation.context.assets.open("softball_batter_catcher.jpg").use {
            BitmapFactory.decodeStream(it)
        }
        val longest = maxOf(original.width, original.height)
        val factor = (PRODUCTION_DETECTION_BOUND.toFloat() / longest).coerceAtMost(1f)
        val bitmap = if (factor < 1f) {
            Bitmap.createScaledBitmap(
                original,
                (original.width * factor).roundToInt(),
                (original.height * factor).roundToInt(),
                true
            ).also { original.recycle() }
        } else {
            original
        }
        val objects = BatBallDetector(instrumentation.targetContext).use { detector ->
            detector.detect(
                bitmap = bitmap,
                timestampMs = 0L,
                sourceWidth = bitmap.width,
                sourceHeight = bitmap.height
            )
        }

        assertTrue(
            "Expected the clearly visible bat, detected $objects",
            objects.any { it.type == TrackedObjectType.BAT }
        )
        assertTrue(objects.all { detection ->
            detection.left in 0f..bitmap.width.toFloat() &&
                detection.right in 0f..bitmap.width.toFloat() &&
                detection.top in 0f..bitmap.height.toFloat() &&
                detection.bottom in 0f..bitmap.height.toFloat() &&
                detection.left < detection.right && detection.top < detection.bottom &&
                detection.confidence in 0f..1f
        })
        bitmap.recycle()
    }

    private companion object {
        const val PRODUCTION_DETECTION_BOUND = 840
    }
}