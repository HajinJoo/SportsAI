package com.example.sportsai

import android.graphics.BitmapFactory
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sportsai.data.BatterPoseDetector
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class BatterPoseDetectorInstrumentedTest {

    @Test
    fun bundledOfflineModelDetectsBatterAndCatcherAsSeparatePeople() {
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
        val poses = BatterPoseDetector(instrumentation.targetContext).use { detector ->
            detector.detect(bitmap, timestampMs = 0L)
        }
        bitmap.recycle()

        assertTrue("Expected separate batter and catcher poses, detected ${poses.size}", poses.size >= 2)
        assertTrue(poses.take(2).all { pose -> pose.count { it.inFrameLikelihood >= 0.5f } >= 12 })
    }

    private companion object {
        const val PRODUCTION_DETECTION_BOUND = 840
    }
}
