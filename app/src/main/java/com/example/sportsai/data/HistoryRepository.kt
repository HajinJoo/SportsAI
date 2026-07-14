package com.example.sportsai.data

import android.content.Context
import com.example.sportsai.model.Finding
import com.example.sportsai.model.FindingType
import com.example.sportsai.model.HighlightClip
import com.example.sportsai.model.SessionEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Stores the user's analyzed sessions in a small JSON file so the progress
 * timeline survives app restarts.
 */
class HistoryRepository(context: Context) {

    private val filesDir = context.filesDir
    private val file = File(filesDir, "session_history.json")
    private val lock = Any()

    fun load(): List<SessionEntry> = synchronized(lock) {
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                SessionEntry(
                    id = o.optLong("id"),
                    sportName = o.optString("sport"),
                    filmedAtMillis = o.optLong("filmedAt"),
                    score = o.optInt("score"),
                    summary = o.optString("summary"),
                    metrics = parseMetrics(o.optJSONObject("metrics")),
                    aiOverview = o.optString("aiOverview", ""),
                    highlights = parseHighlights(o.optJSONArray("highlights")),
                    findings = parseFindings(o.optJSONArray("findings")),
                    detectionRate = o.optDouble("detectionRate", 0.0).toFloat(),
                    sourceVideoUri = o.optString("sourceVideoUri", ""),
                    videoDurationMs = o.optLong("videoDurationMs", 0L)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Adds an entry, persists, and returns the updated full list. */
    fun add(entry: SessionEntry): List<SessionEntry> = synchronized(lock) {
        val updated = load() + entry
        save(updated)
        updated
    }

    fun delete(id: Long): List<SessionEntry> = synchronized(lock) {
        val loaded = load()
        loaded.firstOrNull { it.id == id }?.highlights?.forEach { deletePrivateVideo(it.videoPath) }
        val updated = loaded.filterNot { it.id == id }
        save(updated)
        updated
    }

    fun update(entry: SessionEntry): List<SessionEntry> = synchronized(lock) {
        val updated = load().map { if (it.id == entry.id) entry else it }
        save(updated)
        updated
    }

    /** Looks up a single session by id. */
    fun findById(id: Long): SessionEntry? = load().firstOrNull { it.id == id }

    private fun save(entries: List<SessionEntry>) {
        val array = JSONArray()
        entries.forEach { e ->
            array.put(
                JSONObject()
                    .put("id", e.id)
                    .put("sport", e.sportName)
                    .put("filmedAt", e.filmedAtMillis)
                    .put("score", e.score)
                    .put("summary", e.summary)
                    .put("metrics", serializeMetrics(e.metrics))
                    .put("aiOverview", e.aiOverview)
                    .put("highlights", serializeHighlights(e.highlights))
                    .put("findings", serializeFindings(e.findings))
                    .put("detectionRate", e.detectionRate.toDouble())
                    .put("sourceVideoUri", e.sourceVideoUri)
                    .put("videoDurationMs", e.videoDurationMs)
            )
        }
        file.writeText(array.toString())
    }

    // --- JSON helpers for metrics ---

    private fun serializeMetrics(metrics: Map<String, Int>): JSONObject {
        val obj = JSONObject()
        metrics.forEach { (k, v) -> obj.put(k, v) }
        return obj
    }

    private fun parseMetrics(obj: JSONObject?): Map<String, Int> {
        if (obj == null) return emptyMap()
        val map = mutableMapOf<String, Int>()
        obj.keys().forEach { key -> map[key] = obj.optInt(key) }
        return map
    }

    // --- JSON helpers for highlights ---

    private fun serializeHighlights(highlights: List<HighlightClip>): JSONArray {
        val arr = JSONArray()
        highlights.forEach { h ->
            arr.put(
                JSONObject()
                    .put("id", h.id)
                    .put("label", h.label)
                    .put("startMs", h.startMs)
                    .put("endMs", h.endMs)
                    .put("score", h.score)
                    .put("videoPath", h.videoPath)
                    .put("editedByUser", h.editedByUser)
            )
        }
        return arr
    }

    private fun parseHighlights(arr: JSONArray?): List<HighlightClip> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            HighlightClip(
                id = o.optLong("id"),
                label = o.optString("label"),
                startMs = o.optLong("startMs"),
                endMs = o.optLong("endMs"),
                score = o.optInt("score"),
                videoPath = o.optString("videoPath", ""),
                editedByUser = o.optBoolean("editedByUser", false)
            )
        }
    }

    private fun serializeFindings(findings: List<Finding>): JSONArray {
        val array = JSONArray()
        findings.forEach { finding ->
            array.put(
                JSONObject()
                    .put("type", finding.type.name)
                    .put("area", finding.area)
                    .put("message", finding.message)
            )
        }
        return array
    }

    private fun parseFindings(array: JSONArray?): List<Finding> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val type = runCatching {
                FindingType.valueOf(item.optString("type"))
            }.getOrNull() ?: return@mapNotNull null
            Finding(
                type = type,
                area = item.optString("area"),
                message = item.optString("message")
            )
        }
    }

    private fun deletePrivateVideo(path: String) {
        if (path.isBlank()) return
        runCatching {
            val candidate = File(path)
            val privateRoot = filesDir.canonicalPath + File.separator
            if (candidate.canonicalPath.startsWith(privateRoot)) candidate.delete()
        }
    }
}

