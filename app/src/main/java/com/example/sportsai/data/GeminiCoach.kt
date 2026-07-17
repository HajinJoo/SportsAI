package com.example.sportsai.data

import android.graphics.Bitmap
import android.util.Base64
import com.example.sportsai.model.AnalysisResult
import com.example.sportsai.model.AnalysisProfiles
import com.example.sportsai.model.AnimationFrame
import com.example.sportsai.model.Finding
import com.example.sportsai.model.FindingType
import com.example.sportsai.model.LandmarkPoint
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
import kotlin.math.roundToInt

/**
 * Sends key frames from the clip to Google Gemini (a real multimodal AI model)
 * and asks it to coach the athlete's technique. Gemini looks at the actual images,
 * so the analysis and feedback are AI-generated, not just rule-based.
 */
data class GeminiConnectionResult(
    val successful: Boolean,
    val message: String
)

class InsufficientVisualEvidenceException(
    val visibilitySummary: String,
    val limitations: List<String>
) : Exception(visibilitySummary)

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

        val selected = pickFrames(frames, max = 8)
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

            parseReport(text, sport, result.detectionRate, selected.size)
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

        frames.forEachIndexed { index, frame ->
            parts.put(
                JSONObject().put(
                    "text",
                    frameEvidence(frame, index, frames.size)
                )
            )
            parts.put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", "image/jpeg")
                        .put("data", encodeJpeg(frame.bitmap))
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
                    .put("athleteVisible", JSONObject().put("type", "BOOLEAN")
                        .put("description", "True only when the same athlete is directly visible clearly enough to assess across at least three submitted frames."))
                    .put("visibilityConfidence", JSONObject().put("type", "INTEGER")
                        .put("minimum", 0).put("maximum", 100)
                        .put("description", "Confidence that the athlete and relevant action are actually visible, not confidence in an inferred guess."))
                    .put("observedFrameLabels", stringArraySchema(
                        "Exact submitted labels such as Frame 1 that were directly inspected and support the assessment."
                    ))
                    .put("visibilitySummary", JSONObject().put("type", "STRING")
                        .put("description", "One factual sentence stating which athlete, body parts, equipment, and action range can actually be seen."))
                    .put("limitations", stringArraySchema(
                        "Concrete visibility limits such as an unseen ball, cropped feet, blur, obstruction, or missing action phase. Empty only if none apply."
                    ))
                    .put("score", JSONObject().put("type", "INTEGER")
                        .put("minimum", 0).put("maximum", 100)
                        .put("description", "Evidence-grounded overall technique score; use 0 when athleteVisible is false."))
                    .put("summary", JSONObject().put("type", "STRING")
                        .put("description", "One concise coaching sentence grounded only in visible evidence."))
                    .put("strengths", stringArraySchema(
                        "One to three visible strengths; each item must cite at least one exact frame label."
                    ))
                    .put("issues", stringArraySchema(
                        "One to three visible issues; each item must cite at least one exact frame label."
                    ))
                    .put("tips", stringArraySchema(
                        "One to three specific drills tied to a cited visible issue; do not invent an unseen fault."
                    ))
                    .put("metricScores", metricScoreSchema)
                    .put("overview", JSONObject().put("type", "STRING")
                        .put("description", "A three- or four-sentence evidence-grounded skill overview that distinguishes visible movement potential from measured speed or ball flight."))
            )
            .put("required", JSONArray().put("athleteVisible")
                .put("visibilityConfidence").put("observedFrameLabels")
                .put("visibilitySummary").put("limitations")
                .put("score").put("summary")
                .put("strengths").put("issues").put("tips")
                .put("metricScores").put("overview"))

        val genConfig = JSONObject()
            .put("responseMimeType", "application/json")
            .put("responseSchema", schema)
            .put("temperature", 0.2)

        return JSONObject()
            .put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemInstruction()))
                )
            )
            .put("contents", contents)
            .put("generationConfig", genConfig)
    }

    private fun stringArraySchema(description: String? = null) = JSONObject()
        .put("type", "ARRAY")
        .put("items", JSONObject().put("type", "STRING"))
        .apply { if (description != null) put("description", description) }

    private fun metricAreasFor(sport: Sport): String =
        sport.metrics.joinToString { it.name }

    internal fun systemInstruction(): String = """
        You are an evidence-grounded sports video analyst. The submitted images are the primary
        evidence. Inspect the pixels in every labeled frame before scoring. Never assume an athlete,
        bat, ball, contact event, throwing hand, handedness, gaze direction, speed, or action phase
        that is not directly visible. On-device pose coordinates are supplemental tracker signals,
        not proof of an object or event. In each frame, assess only the visible person whose body
        aligns with the supplied body-box and keypoints; ignore nearby catchers, umpires, defenders,
        or spectators. If that target cannot be matched to one visible athlete across at least three
        frames, set athleteVisible to false and do not manufacture coaching.
        Return only the requested JSON; do not reveal chain-of-thought.
    """.trimIndent()

    internal fun promptFor(sport: Sport): String = """
        Analyze these sequential, time-labeled frames as an expert ${sport.displayName} coach.
        First perform the visual-visibility gate, then assess only mechanics supported by pixels in
        specific frames. Each frame is preceded by its exact label, time, temporal region, image
        size, and supplemental pose-tracker evidence.

        VISIBILITY GATE:
        - Confirm that the same target athlete matching the supplied body-box and keypoints is
          directly visible in at least three submitted images; do not choose the largest bystander.
        - State what body parts and relevant equipment are actually visible across the sequence.
        - If the athlete is too small, blurred, obstructed, cropped, absent, or the relevant action
          is not captured, set athleteVisible=false, score=0, use empty strengths/issues/tips, and
          explain the filming limitation. Do not fill gaps with a typical ${sport.displayName} motion.

        ${sportEvidenceRules(sport)}

        EVIDENCE RULES:
        - Every strength and issue must start with or include an exact citation such as "Frame 4".
        - Compare multiple frames before claiming sequencing, rotation, balance, or improvement.
        - Do not identify the athlete or infer age, gender, experience level, or intent.
        - Do not claim measured mph, km/h, launch angle, ball flight, or contact quality from stills.
        - A pose point may guide attention but cannot override what is visibly present in the image.

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
        movement-speed potential, not radar-measured mph or km/h. Ball Tracking is only a visible
        head/upper-body stability coaching proxy; do not claim measured gaze, target alignment,
        or ball-flight data.

        Be specific to ${sport.displayName} mechanics, concise, friendly, and honest about limits.
    """.trimIndent()

    private fun sportEvidenceRules(sport: Sport): String = when (sport) {
        Sport.BASEBALL_BAT -> """
            BATTING CHECKLIST:
            - Verify that the body-box target is visibly the batter and that a bat is visibly present;
              never substitute the catcher/umpire or treat pose landmarks as a bat.
            - Look for visible setup/balance, load, stride, hip-to-shoulder sequence, hand path,
              head stability, contact-zone positioning, extension, and finish only where captured.
            - Never claim bat-ball contact unless the ball and contact are unambiguously visible.
              Otherwise say "contact-zone frame," not "contact."
            - Bat Speed Potential means visible body/hand sequencing, never measured bat velocity.
            - Ball Tracking means visible head/upper-body stability only; it does not prove gaze,
              eye-line direction, or ball trajectory.
        """.trimIndent()

        Sport.BASEBALL_PITCH -> """
            PITCHING CHECKLIST:
            - Verify the pitcher and throwing action are visibly present before assessing them.
            - Assess visible balance, leg lift/drive, stride, hip-to-shoulder sequence, arm action,
              release-zone posture, front-side stability, and follow-through only where captured.
            - Do not name the throwing hand unless it is visually clear, and do not claim a release
              instant or pitch speed when the ball is not visible.
        """.trimIndent()

        Sport.BASKETBALL_SHOT -> """
            SHOOTING CHECKLIST:
            - Verify the shooter and shooting motion are visibly present before assessing them.
            - Assess visible base, dip, leg drive, elbow/wrist alignment, release-zone posture,
              balance, and follow-through only where captured.
            - Do not claim a make, miss, arc, spin, or release speed unless that evidence is visible.
        """.trimIndent()
    }

    private fun frameEvidence(frame: AnimationFrame, index: Int, total: Int): String {
        val temporalRegion = when {
            total <= 1 -> "single captured moment"
            index == 0 -> "action-window start"
            index == total - 1 -> "action-window end"
            index.toDouble() / (total - 1) < 0.4 -> "early action window"
            index.toDouble() / (total - 1) < 0.7 -> "middle/peak-motion window"
            else -> "late action window"
        }
        return buildString {
            append("Frame ${index + 1} of $total | t=")
            append(String.format(java.util.Locale.US, "%.2fs", frame.pose.timestampMs / 1_000.0))
            append(" | $temporalRegion | image=${frame.bitmap.width}x${frame.bitmap.height}. ")
            append(poseEvidence(frame))
            append(" The image immediately following this label is the primary evidence.")
        }
    }

    private fun poseEvidence(frame: AnimationFrame): String {
        val reliable = frame.pose.landmarks.filter { it.inFrameLikelihood >= 0.5f }
        if (reliable.isEmpty()) return "Pose tracker: no reliable body points."
        val minX = reliable.minOf { it.x }.coerceIn(0f, frame.bitmap.width.toFloat())
        val maxX = reliable.maxOf { it.x }.coerceIn(0f, frame.bitmap.width.toFloat())
        val minY = reliable.minOf { it.y }.coerceIn(0f, frame.bitmap.height.toFloat())
        val maxY = reliable.maxOf { it.y }.coerceIn(0f, frame.bitmap.height.toFloat())
        val pointText = KEY_BODY_POINTS.mapNotNull { (type, name) ->
            reliable.firstOrNull { it.type == type }?.let { point ->
                "$name=${normalizedPoint(point, frame.bitmap.width, frame.bitmap.height)}"
            }
        }.joinToString(", ")
        return "Pose tracker (supplemental): ${reliable.size} reliable points; " +
            "body-box=${normalizedBox(minX, minY, maxX, maxY, frame.bitmap.width, frame.bitmap.height)}; " +
            "keypoints[$pointText]."
    }

    private fun normalizedPoint(point: LandmarkPoint, width: Int, height: Int): String {
        val x = (point.x / width.coerceAtLeast(1) * 1_000).roundToInt().coerceIn(0, 1_000)
        val y = (point.y / height.coerceAtLeast(1) * 1_000).roundToInt().coerceIn(0, 1_000)
        val confidence = (point.inFrameLikelihood * 100).roundToInt().coerceIn(0, 100)
        return "($x,$y,c=$confidence)"
    }

    private fun normalizedBox(
        minX: Float,
        minY: Float,
        maxX: Float,
        maxY: Float,
        width: Int,
        height: Int
    ): String = listOf(
        (minX / width.coerceAtLeast(1) * 1_000).roundToInt(),
        (minY / height.coerceAtLeast(1) * 1_000).roundToInt(),
        (maxX / width.coerceAtLeast(1) * 1_000).roundToInt(),
        (maxY / height.coerceAtLeast(1) * 1_000).roundToInt()
    ).joinToString(prefix = "(", postfix = ")")

    private fun encodeJpeg(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    // --- Response parsing -------------------------------------------------

    private fun parseReport(
        response: String,
        sport: Sport,
        detectionRate: Float,
        submittedFrameCount: Int
    ): TechniqueReport {
        val root = JSONObject(response)
        val textPart = root
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")

        val json = JSONObject(textPart)
        val visibilitySummary = json.optString(
            "visibilitySummary",
            "Gemini did not provide a visual-evidence assessment."
        ).trim()
        val limitations = json.optJSONArray("limitations").toStringList()
        validateVisualEvidence(
            athleteVisible = json.optBoolean("athleteVisible", false),
            confidence = json.optInt("visibilityConfidence", 0),
            observedFrameLabels = json.optJSONArray("observedFrameLabels").toStringList(),
            visibilitySummary = visibilitySummary,
            limitations = limitations,
            submittedFrameCount = submittedFrameCount
        )
        val score = json.optInt("score", 0).coerceIn(0, 100)
        val summary = json.optString("summary", "Analysis complete.")
        val requestedOverview = json.optString("overview", "").trim()

        val findings = mutableListOf<Finding>()
        findings += Finding(FindingType.TIP, "Visual evidence", visibilitySummary)
        limitations.forEach { limitation ->
            findings += Finding(FindingType.TIP, "Camera limit", limitation)
        }
        addFindings(
            findings, json.optJSONArray("strengths"), FindingType.GOOD, "Strength",
            submittedFrameCount
        )
        addFindings(
            findings, json.optJSONArray("issues"), FindingType.ISSUE, "To fix",
            submittedFrameCount
        )
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
        val strongest = metricScores.maxByOrNull { it.value }
        val weakest = metricScores.minByOrNull { it.value }
        val overview = normalizeSkillOverview(
            primary = requestedOverview.takeIf {
                splitOverviewSentences(it).size in 3..4
            }.orEmpty(),
            fallbacks = listOfNotNull(
                summary,
                strongest?.let { "${it.key} is currently your strongest area at ${it.value}/100." },
                weakest?.let { "${it.key} is your clearest opportunity at ${it.value}/100." },
                "Use the recommended drill in your next practice, then compare another session to confirm progress."
            )
        )

        return TechniqueReport(
            sport = sport.displayName,
            overallScore = score,
            summary = summary,
            findings = findings,
            detectionRate = detectionRate,
            metricScores = metricScores,
            aiOverview = overview,
            analysisProfile = AnalysisProfiles.GEMINI_EVIDENCE_V2
        )
    }

    private fun addFindings(
        target: MutableList<Finding>,
        array: JSONArray?,
        type: FindingType,
        area: String,
        submittedFrameCount: Int? = null
    ) {
        if (array == null) return
        for (i in 0 until array.length()) {
            val msg = array.optString(i).trim()
            val citationIsValid = submittedFrameCount == null ||
                validFrameNumbers(msg, submittedFrameCount).isNotEmpty()
            if (msg.isNotEmpty() && citationIsValid) target.add(Finding(type, area, msg))
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            optString(index).trim().takeIf { it.isNotEmpty() }
        }
    }

    companion object {
        const val MODEL = "gemini-3.5-flash"
        private const val API_KEY_HEADER = "x-goog-api-key"
        private val KEY_BODY_POINTS = listOf(
            11 to "leftShoulder", 12 to "rightShoulder",
            13 to "leftElbow", 14 to "rightElbow",
            15 to "leftWrist", 16 to "rightWrist",
            23 to "leftHip", 24 to "rightHip",
            25 to "leftKnee", 26 to "rightKnee",
            27 to "leftAnkle", 28 to "rightAnkle"
        )

        internal fun connectionMessage(statusCode: Int): String = when (statusCode) {
            400, 401, 403 -> "Google rejected this API key. Check the key in Google AI Studio and try again."
            404 -> "Gemini 3.5 Flash is not available for this API key or region."
            429 -> "Gemini reached its quota or rate limit. Check this key's usage in Google AI Studio."
            in 500..599 -> "Gemini is temporarily unavailable. Try again shortly."
            else -> "Gemini connection failed (HTTP $statusCode)."
        }
    }
}

internal fun validateVisualEvidence(
    athleteVisible: Boolean,
    confidence: Int,
    observedFrameLabels: List<String>,
    visibilitySummary: String,
    limitations: List<String>,
    submittedFrameCount: Int = Int.MAX_VALUE
) {
    val distinctFrames = observedFrameLabels
        .flatMap { validFrameNumbers(it, submittedFrameCount) }
        .distinct()
        .size
    if (!athleteVisible || confidence < 55 || distinctFrames < 3) {
        throw InsufficientVisualEvidenceException(visibilitySummary, limitations)
    }
}

private val FRAME_CITATION = Regex("""\bFrame\s+(\d+)\b""", RegexOption.IGNORE_CASE)

private fun validFrameNumbers(text: String, submittedFrameCount: Int): List<Int> =
    FRAME_CITATION.findAll(text).mapNotNull { match ->
        match.groupValues[1].toIntOrNull()?.takeIf { it in 1..submittedFrameCount }
    }.toList()

