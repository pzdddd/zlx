package com.pzdd.note.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class NoteRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("pznote_notes", Context.MODE_PRIVATE)

    fun loadAll(): List<Note> {
        val raw = prefs.getString(KEY_NOTES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        Note(
                            id = o.getLong("id"),
                            title = o.optString("title"),
                            content = o.optString("content"),
                            isFavorite = o.optBoolean("isFavorite"),
                            isPinned = o.optBoolean("isPinned", false),
                            mode = o.optInt("mode", 0),
                            parentId = o.optLong("parentId", -1L),
                            createdAt = o.optLong("createdAt"),
                            updatedAt = o.optLong("updatedAt")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveAll(notes: List<Note>) {
        val arr = JSONArray()
        notes.forEach { n ->
            arr.put(JSONObject().apply {
                put("id", n.id)
                put("title", n.title)
                put("content", n.content)
                put("isFavorite", n.isFavorite)
                put("isPinned", n.isPinned)
                put("mode", n.mode)
                put("parentId", n.parentId)
                put("createdAt", n.createdAt)
                put("updatedAt", n.updatedAt)
            })
        }
        prefs.edit().putString(KEY_NOTES, arr.toString()).apply()
    }

    companion object {
        private const val KEY_NOTES = "notes_json"
    }
}