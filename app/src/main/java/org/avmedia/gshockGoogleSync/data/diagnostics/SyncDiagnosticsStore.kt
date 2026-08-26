package org.avmedia.gshockGoogleSync.data.diagnostics

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class SyncDiagnosticEvent(
    val timestampEpochMillis: Long,
    val type: String,
    val message: String,
)

@Singleton
class SyncDiagnosticsStore @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val _events = MutableStateFlow(load())

    val events: StateFlow<List<SyncDiagnosticEvent>> = _events.asStateFlow()

    @Synchronized
    fun record(type: String, message: String) {
        val updated = (
            listOf(SyncDiagnosticEvent(System.currentTimeMillis(), type, message)) + _events.value
        ).take(MAX_EVENTS)
        check(preferences.edit().putString(KEY_EVENTS, gson.toJson(updated)).commit()) {
            "Unable to persist synchronization diagnostics"
        }
        _events.value = updated
    }

    private fun load(): List<SyncDiagnosticEvent> {
        val json = preferences.getString(KEY_EVENTS, null) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<SyncDiagnosticEvent>>() {}.type
            gson.fromJson<List<SyncDiagnosticEvent>>(json, type).orEmpty()
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val PREFERENCES_NAME = "sync_diagnostics"
        const val KEY_EVENTS = "events_v1"
        const val MAX_EVENTS = 50
    }
}
