package com.synth.synthmusic.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.searchHistoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "search_history")

/**
 * DataStore-backed storage for recent search queries.
 * Stores up to 10 entries, newest first, deduplicated case-insensitively.
 */
class SearchHistoryStore(private val context: Context) {
    private val dataStore: DataStore<Preferences> = context.searchHistoryDataStore

    companion object {
        private val SEARCH_HISTORY = stringPreferencesKey("search_history")
    }

    private val json = Json.Default

    /**
     * Emits the stored list of search queries, newest first.
     * Emits an empty list as the default and on parse errors.
     */
    val searchHistory: Flow<List<String>> = dataStore.data
        .map { prefs ->
            prefs[SEARCH_HISTORY]?.let { json.decodeFromString<List<String>>(it) } ?: emptyList()
        }
        .catch { e ->
            if (e is IOException) {
                emit(emptyList())
            } else {
                throw e
            }
        }

    /**
     * Add a search query to the history.
     * Trims whitespace; blank queries are ignored.
     * Removes any existing entry equal ignoring case, then inserts at position 0.
     * Caps the list to 10 entries.
     */
    suspend fun addQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return

        dataStore.edit { prefs ->
            val current = prefs[SEARCH_HISTORY]?.let {
                runCatching { json.decodeFromString<List<String>>(it) }.getOrNull()
            } ?: emptyList()

            val updated = current.toMutableList()
            updated.removeAll { it.equals(trimmed, ignoreCase = true) }
            updated.add(0, trimmed)
            if (updated.size > 10) updated.removeAt(10)

            prefs[SEARCH_HISTORY] = json.encodeToString(updated)
        }
    }

    /**
     * Remove a search query from the history by case-insensitive match.
     */
    suspend fun removeQuery(query: String) {
        dataStore.edit { prefs ->
            val current = prefs[SEARCH_HISTORY]?.let {
                runCatching { json.decodeFromString<List<String>>(it) }.getOrNull()
            } ?: emptyList()

            val updated = current.filter { !it.equals(query, ignoreCase = true) }
            prefs[SEARCH_HISTORY] = json.encodeToString(updated)
        }
    }

    /**
     * Clear all search history entries.
     */
    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs[SEARCH_HISTORY] = json.encodeToString(emptyList<String>())
        }
    }
}
