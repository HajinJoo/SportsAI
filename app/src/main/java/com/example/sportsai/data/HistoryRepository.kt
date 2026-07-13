package com.example.sportsai.data

import android.content.Context
import com.example.sportsai.model.SessionEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Stores the user's analyzed sessions in a small JSON file so the progress
 * timeline survives app restarts.
 */
class HistoryRepository(context: Context) {

    private val file = File(context.filesDir, "session_history.json")
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
                    summary = o.optString("summary")
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
        val updated = load().filterNot { it.id == id }
        save(updated)
        updated
    }

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
            )
        }
        file.writeText(array.toString())
    }
}

