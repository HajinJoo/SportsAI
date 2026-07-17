package com.example.sportsai.data

import android.content.Context
import android.graphics.Bitmap
import com.example.sportsai.model.TrackedObject
import com.example.sportsai.model.TrackedObjectType
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import java.util.Locale

/** COCO object detector restricted to visible baseball bats and sports balls. */
internal class BatBallDetector(context: Context) : AutoCloseable {

    private val detector: ObjectDetector
    private var retainedInput: Bitmap? = null
    private var closed = false

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_ASSET)
            .setDelegate(Delegate.CPU)
            .build()
        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.VIDEO)
            .setDisplayNamesLocale("en")
            .setMaxResults(MAX_RESULTS)
            .setScoreThreshold(MIN_CONFIDENCE)
            .setCategoryAllowlist(ALLOWED_CATEGORIES)
            .build()
        detector = ObjectDetector.createFromOptions(context, options)
    }

    fun detect(
        bitmap: Bitmap,
        timestampMs: Long,
        sourceWidth: Int,
        sourceHeight: Int
    ): List<TrackedObject> {
        check(!closed) { "BatBallDetector is already closed" }
        val ownedArgb = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val previousInput = retainedInput
        return try {
            val image = BitmapImageBuilder(ownedArgb).build()
            try {
                val scaleX = sourceWidth.toFloat() / bitmap.width.coerceAtLeast(1)
                val scaleY = sourceHeight.toFloat() / bitmap.height.coerceAtLeast(1)
                detector.detectForVideo(image, timestampMs).detections().mapNotNull { detection ->
                    val category = detection.categories().maxByOrNull { it.score() }
                        ?: return@mapNotNull null
                    val type = when (category.categoryName().lowercase(Locale.US)) {
                        BAT_CATEGORY -> TrackedObjectType.BAT
                        BALL_CATEGORY -> TrackedObjectType.BALL
                        else -> return@mapNotNull null
                    }
                    val box = detection.boundingBox()
                    TrackedObject(
                        timestampMs = timestampMs,
                        type = type,
                        left = (box.left * scaleX).coerceIn(0f, sourceWidth.toFloat()),
                        top = (box.top * scaleY).coerceIn(0f, sourceHeight.toFloat()),
                        right = (box.right * scaleX).coerceIn(0f, sourceWidth.toFloat()),
                        bottom = (box.bottom * scaleY).coerceIn(0f, sourceHeight.toFloat()),
                        confidence = category.score().coerceIn(0f, 1f)
                    )
                }
            } finally {
                image.close()
            }
        } finally {
            retainedInput = ownedArgb
            previousInput?.recycle()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            detector.close()
        } finally {
            retainedInput?.recycle()
            retainedInput = null
        }
    }

    private companion object {
        const val MODEL_ASSET = "efficientdet_lite0_int8.tflite"
        const val MAX_RESULTS = 6
        const val MIN_CONFIDENCE = 0.20f
        const val BAT_CATEGORY = "baseball bat"
        const val BALL_CATEGORY = "sports ball"
        val ALLOWED_CATEGORIES = listOf(BAT_CATEGORY, BALL_CATEGORY)
    }
}