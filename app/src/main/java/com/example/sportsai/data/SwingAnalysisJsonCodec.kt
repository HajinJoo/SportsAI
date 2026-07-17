package com.example.sportsai.data

import com.example.sportsai.model.BiomechanicsMeasurement
import com.example.sportsai.model.CameraViewAssessment
import com.example.sportsai.model.EquipmentTrackingStatus
import com.example.sportsai.model.EquipmentTrackingSummary
import com.example.sportsai.model.MechanicsIssue
import com.example.sportsai.model.MechanicsIssueSeverity
import com.example.sportsai.model.SwingAnalysisSummary
import com.example.sportsai.model.SwingCameraView
import com.example.sportsai.model.SwingPhase
import com.example.sportsai.model.SwingPhaseSegment
import com.example.sportsai.model.TechniqueReport
import com.example.sportsai.model.TrackedObjectType
import org.json.JSONArray
import org.json.JSONObject

internal object SwingAnalysisJsonCodec {

    fun encodeReport(report: TechniqueReport): JSONObject = JSONObject()
        .put("schemaVersion", 2)
        .put("sport", report.sport)
        .put("analysisProfile", report.analysisProfile)
        .put("overallScore", report.overallScore)
        .put("detectionRate", report.detectionRate.toDouble())
        .put("metricScores", JSONObject().apply {
            report.metricScores.forEach { (name, value) -> put(name, value) }
        })
        .put("swingAnalysis", encodeSummary(report.swingAnalysis))

    fun encodeSummary(summary: SwingAnalysisSummary?): Any {
        if (summary == null) return JSONObject.NULL
        return JSONObject()
            .put("schemaVersion", summary.schemaVersion)
            .put("cameraView", JSONObject()
                .put("view", summary.cameraView.view.name)
                .put("confidence", summary.cameraView.confidence.toDouble())
                .put("usableFrames", summary.cameraView.usableFrames)
                .put("evidence", summary.cameraView.evidence))
            .put("phases", JSONArray().apply {
                summary.phases.forEach { segment ->
                    put(JSONObject()
                        .put("phase", segment.phase.name)
                        .put("startMs", segment.startMs)
                        .put("endMs", segment.endMs)
                        .put("confidence", segment.confidence.toDouble())
                        .put("evidence", segment.evidence))
                }
            })
            .put("measurements", JSONArray().apply {
                summary.measurements.forEach { measurement ->
                    put(JSONObject()
                        .put("key", measurement.key)
                        .put("label", measurement.label)
                        .put("value", measurement.value)
                        .put("unit", measurement.unit)
                        .put("phase", measurement.phase?.name ?: JSONObject.NULL)
                        .put("evidence", measurement.evidence))
                }
            })
            .put("issues", JSONArray().apply {
                summary.issues.forEach { issue ->
                    put(JSONObject()
                        .put("code", issue.code)
                        .put("label", issue.label)
                        .put("severity", issue.severity.name)
                        .put("confidence", issue.confidence.toDouble())
                        .put("phase", issue.phase.name)
                        .put("evidence", issue.evidence)
                        .put("coachingCue", issue.coachingCue))
                }
            })
            .put("equipment", JSONArray().apply {
                summary.equipment.forEach { equipment ->
                    put(JSONObject()
                        .put("type", equipment.type.name)
                        .put("status", equipment.status.name)
                        .put("sampledFrames", equipment.sampledFrames)
                        .put("detectedFrames", equipment.detectedFrames)
                        .put("maxConfidence", equipment.maxConfidence.toDouble()))
                }
            })
    }

    fun decodeSummary(json: JSONObject?): SwingAnalysisSummary? {
        if (json == null) return null
        val cameraViewJson = json.optJSONObject("cameraView")
        return SwingAnalysisSummary(
            schemaVersion = json.optInt("schemaVersion", 1),
            cameraView = if (cameraViewJson == null) {
                CameraViewAssessment()
            } else {
                CameraViewAssessment(
                    view = enumValue<SwingCameraView>(cameraViewJson.optString("view"))
                        ?: SwingCameraView.UNKNOWN,
                    confidence = cameraViewJson.optDouble("confidence", 0.0).toFloat(),
                    usableFrames = cameraViewJson.optInt("usableFrames"),
                    evidence = cameraViewJson.optString(
                        "evidence",
                        "Insufficient reliable pose geometry to classify the camera view."
                    )
                )
            },
            phases = json.optJSONArray("phases").objects().mapNotNull { item ->
                val phase = enumValue<SwingPhase>(item.optString("phase")) ?: return@mapNotNull null
                SwingPhaseSegment(
                    phase = phase,
                    startMs = item.optLong("startMs"),
                    endMs = item.optLong("endMs"),
                    confidence = item.optDouble("confidence", 0.0).toFloat(),
                    evidence = item.optString("evidence")
                )
            },
            measurements = json.optJSONArray("measurements").objects().mapNotNull { item ->
                val key = item.optString("key").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                BiomechanicsMeasurement(
                    key = key,
                    label = item.optString("label"),
                    value = item.optDouble("value"),
                    unit = item.optString("unit"),
                    phase = enumValue<SwingPhase>(item.optString("phase")),
                    evidence = item.optString("evidence")
                )
            },
            issues = json.optJSONArray("issues").objects().mapNotNull { item ->
                val phase = enumValue<SwingPhase>(item.optString("phase")) ?: return@mapNotNull null
                val severity = enumValue<MechanicsIssueSeverity>(item.optString("severity"))
                    ?: return@mapNotNull null
                val code = item.optString("code").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                MechanicsIssue(
                    code = code,
                    label = item.optString("label"),
                    severity = severity,
                    confidence = item.optDouble("confidence", 0.0).toFloat(),
                    phase = phase,
                    evidence = item.optString("evidence"),
                    coachingCue = item.optString("coachingCue")
                )
            },
            equipment = json.optJSONArray("equipment").objects().mapNotNull { item ->
                val type = enumValue<TrackedObjectType>(item.optString("type")) ?: return@mapNotNull null
                val status = enumValue<EquipmentTrackingStatus>(item.optString("status"))
                    ?: return@mapNotNull null
                EquipmentTrackingSummary(
                    type = type,
                    status = status,
                    sampledFrames = item.optInt("sampledFrames"),
                    detectedFrames = item.optInt("detectedFrames"),
                    maxConfidence = item.optDouble("maxConfidence", 0.0).toFloat()
                )
            }
        )
    }

    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull(::optJSONObject)
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String): T? =
        enumValues<T>().firstOrNull { it.name == value }
}