package com.openstream.app

import android.content.Context
import org.json.JSONArray

/** Simple persistent list of recently played sources (URLs or file paths). */
object StreamHistory {
    private const val PREFS = "openstream"
    private const val KEY = "history"

    fun load(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            JSONArray(raw).let { arr -> (0 until arr.length()).map { arr.getString(it) } }
        }.getOrDefault(emptyList())
    }

    fun add(context: Context, source: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val updated = (listOf(source) + load(context).filter { it != source }).take(20)
        prefs.edit().putString(KEY, JSONArray(updated).toString()).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
