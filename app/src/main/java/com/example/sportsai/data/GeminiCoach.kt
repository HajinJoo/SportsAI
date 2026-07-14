package com.example.sportsai.data

import android.graphics.Bitmap
import android.util.Base64
import com.example.sportsai.model.AnalysisResult
import com.example.sportsai.model.AnimationFrame
import com.example.sportsai.model.Finding
import com.example.sportsai.model.FindingType
import com.example.sportsai.model.Sport
import com.example.sportsai.model.TechniqueReport
import com.example.sportsai.model.metrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sends key frames from the clip to Google Gemini (a real multimodal AI model)
 * and asks it to coach the athlete's technique. Gemini looks at the actual images,
 * so the analysis and feedback are AI-generated, not just rule-based.
 */
data class GeminiConnectionResult(
    val successful: Boolean,
    val message: String
)

class GeminiCoach(private val apiKeyProvider: () -> String?) {

    private val model = MODEL
    private val endpoint =
        "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
    private val modelEndpoint =
        "https://generativelanguage.googleapis.com/v1beta/models/$model"

    val isConfigured: Boolean get() = !apiKeyProvider().isNullOrBlank()

    /**
     * @param frames the analyzed frames (bitmap + pose); a spread is sent to Gemini.
     * @param result raw pipeline result (used for detection rate + fallback).
     * @param sport which sport to coach.
     */
    suspend fun coach(
        frames: List<AnimationFrame>,
        result: AnalysisResult,
        sport: Sport
    ): TechniqueReport = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider()?.trim().orEmpty()
        require(apiKey.isNotEmpty()) { "Gemini API key not configured" }

        val selected = pickFrames(frames, max = 6)
        val body = buildRequest(selected, sport)

        val conn = (URL(endpoint).openConnection()
                as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty(API_KEY_HEADER, apiKey)
        }

        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""

            if (code !in 200..299) {
                throw RuntimeException(connectionMessage(code))
            }

            parseReport(text, sport, result.detectionRate)
        } finally {
            conn.disconnect()
        }
    }

    /** Validates a saved key against the exact Gemini model used for coaching. */
    suspend fun testConnection(): GeminiConnectionResult = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider()?.trim().orEmpty()
        if (apiKey.isEmpty()) {
            return@withContext GeminiConnectionResult(false, "Add an API key before testing Gemini.")
        }

        val connection = (URL(modelEndpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty(API_KEY_HEADER, apiKey)
        }
        try {
            val code = connection.responseCode
            (if (code in 200..299) connection.inputStream else connection.errorStream)?.close()
            if (code in 200..299) {
                GeminiConnectionResult(true, "Connected to Gemini 3.5 Flash.")
            } else {
                GeminiConnectionResult(false, connectionMessage(code))
            }
        } catch (_: IOException) {
            GeminiConnectionResult(false, "Could not reach Gemini. Check your internet connection and try again.")
        } catch (_: Exception) {
            GeminiConnectionResult(false, "Gemini could not be tested right now. Try again in a moment.")
        } finally {
            connection.disconnect()
        }
    }

    // --- Request building -------------------------------------------------

    private fun pickFrames(frames: List<AnimationFrame>, max: Int): List<AnimationFrame> {
        if (frames.size <= max) return frames
        val step = frames.size.toFloat() / max
        return (0 until max).map { frames[(it * step).toInt().coerceIn(0, frames.size - 1)] }
    }

    private fun buildRequest(frames: List<AnimationFrame>, sport: Sport): JSONObject {
        val parts = JSONArray()

        parts.put(JSONObject().put("text", promptFor(sport)))

        frames.forEach { f ->
            parts.put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", "image/jpeg")
                        .put("data", encodeJpeg(f.bitmap))
                )
            )
        }

        val contents = JSONArray().put(
            JSONObject().put("role", "user").put("parts", parts)
        )

        // Ask Gemini to return strict JSON matching our report schema.
        val metricProperties = JSONObject()
        val metricRequired = JSONArray()
        sport.metrics.forEach { metric ->
            metricProperties.put(
                metric.name,
                JSONObject()
                    .put("type", "INTEGER")
                    .put("minimum", 0)
                    .put("maximum", 100)
                    .put("description", metric.description)
            )
            metricRequired.put(metric.name)
        }
        val metricScoreSchema = JSONObject()
            .put("type", "OBJECT")
            .put("properties", metricProperties)
            .put("required", metricRequired)

        val schema = JSONObject()
            .put("type", "OBJECT")
            .put(
                "properties", JSONObject()
                    .put("score", JSONObject().put("type", "INTEGER"))
                    .put("summary", JSONObject().put("type", "STRING"))
                    .put("strengths", stringArraySchema())
                    .put("issues", stringArraySchema())
                    .put("tips", stringArraySchema())
                    .put("metricScores", metricScoreSchema)
                    .put("overview", JSONObject().put("type", "STRING"))
            )
            .put("required", JSONArray().put("score").put("summary")
                .put("strengths").put("issues").put("tips")
                .put("metricScores").put("overview"))

        val genConfig = JSONObject()
            .put("responseMimeType", "application/json")
            .put("responseSchema", schema)

        return JSONObject()
            .put("contents", contents)
            .put("generationConfig", genConfig)
    }

    private fun stringArraySchema() = JSONObject()
        .put("type", "ARRAY")
        .put("items", JSONObject().put("type", "STRING"))

    private fun metricAreasFor(sport: Sport): String =
        sport.metrics.joinToString { it.name }

    private fun promptFor(sport: Sport): String = """
        You are an expert ${sport.displayName} coach analyzing a player from these sequential
        frames of their motion (in order). Assess their technique from what you can see.

        Give:
        - score: an integer 0-100 overall technique rating.
        - summary: one short encouraging sentence.
        - strengths: 1-3 short bullet points on what they do well.
        - issues: 1-3 short bullet points on the biggest problems.
        - tips: 1-3 short, specific drills or cues to improve.
        - metricScores: a JSON object mapping each of these technique areas to a 0-100 score:
          ${metricAreasFor(sport)}.
          e.g. {"Arm Action": 82, "Lower Body": 65, "Trunk": 74, "Balance": 88}.
        - overview: a 3-4 sentence assessment of the athlete's overall skill. Mention their
          strongest area, their weakest area, and give a motivational direction for improvement.

        Treat every metric as a comparable 0-100 coaching score. Speed metrics describe visible
        movement-speed potential, not radar-measured mph or km/h. Ball Tracking describes visible
        head/eye-line stability and tracking behavior; do not claim measured ball-flight data.

        Be specific to ${sport.displayName} mechanics and keep every point concise and friendly.

        IMPORTANT: Always give a best-effort rating and full feedback. Even if the video is
        blurry, partially cropped, or the angle is not ideal, still infer as much as you can and
        provide a score plus strengths, issues, and tips. Never refuse or say you cannot see
        enough — just work with whatever is visible.
    """.trimIndent()

    private fun encodeJpeg(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    // --- Response parsing -------------------------------------------------

    private fun parseReport(response: String, sport: Sport, detectionRate: Float): TechniqueReport {
        val root = JSONObject(response)
        val textPart = root
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")

        val json = JSONObject(textPart)
        val score = json.optInt("score", 0).coerceIn(0, 100)
        val summary = json.optString("summary", "Analysis complete.")
        val overview = json.optString("overview", "").ifBlank {
            "$summary Your overall score is $score/100. Review the metric breakdown to identify " +
                "your strongest area and the clearest opportunity. Compare another session after practice to confirm progress."
        }

        val findings = mutableListOf<Finding>()
        addFindings(findings, json.optJSONArray("strengths"), FindingType.GOOD, "Strength")
        addFindings(findings, json.optJSONArray("issues"), FindingType.ISSUE, "To fix")
        addFindings(findings, json.optJSONArray("tips"), FindingType.TIP, "Drill")

        // Parse per-area metric scores.
        val metricScores = linkedMapOf<String, Int>()
        val metricsObj = json.optJSONObject("metricScores")
        sport.metrics.forEach { metric ->
            metricScores[metric.name] = metricsObj
                ?.optInt(metric.name, 0)
                ?.coerceIn(0, 100)
                ?: 0
        }

        return TechniqueReport(
            sport = sport.displayName,
            overallScore = score,
            summary = summary,
            findings = findings,
            detectionRate = detectionRate,
            metricScores = metricScores,
            aiOverview = overview
        )
    }

    private fun addFindings(
        target: MutableList<Finding>,
        array: JSONArray?,
        type: FindingType,
        area: String
    ) {
        if (array == null) return
        for (i in 0 until array.length()) {
            val msg = array.optString(i).trim()
            if (msg.isNotEmpty()) target.add(Finding(type, area, msg))
        }
    }

    companion object {
        const val MODEL = "gemini-3.5-flash"
        private const val API_KEY_HEADER = "x-goog-api-key"

        internal fun connectionMessage(statusCode: Int): String = when (statusCode) {
            400, 401, 403 -> "Google rejected this API key. Check the key in Google AI Studio and try again."
            404 -> "Gemini 3.5 Flash is not available for this API key or region."
            429 -> "Gemini reached its quota or rate limit. Check this key's usage in Google AI Studio."
            in 500..599 -> "Gemini is temporarily unavailable. Try again shortly."
            else -> "Gemini connection failed (HTTP $statusCode)."
        }
    }
}

