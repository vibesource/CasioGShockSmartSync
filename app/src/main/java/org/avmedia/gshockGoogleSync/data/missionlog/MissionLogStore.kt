package org.avmedia.gshockGoogleSync.data.missionlog

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.avmedia.gshockapi.model.MissionLogData
import javax.inject.Inject
import javax.inject.Singleton

data class StoredMissionLogSession(
    val id: String,
    val capturedAtEpochMillis: Long,
    val command: String,
    val watchTimestampUtc: String?,
    val altitudeStartUtc: String?,
    val altitudeSamples: List<StoredAltitudeSample>,
    val altitudePoints: List<StoredAltitudePoint>,
    val endMarkerIndex: Int?,
    val altitudeRawBase64: String,
    val exerciseRawBase64: String,
)

data class StoredAltitudeSample(
    val index: Int,
    val altitudeMetres: Int,
    val timestampUtc: String?,
)

data class StoredAltitudePoint(
    val slot: Int,
    val altitudeMetres: Int,
    val timestampUtc: String,
    val metadataHex: String,
)

/** Bounded, lossless local Mission Log history. */
@Singleton
class MissionLogStore @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val lock = Any()
    private val _sessions = MutableStateFlow(load())

    val sessions: StateFlow<List<StoredMissionLogSession>> = _sessions.asStateFlow()

    fun save(data: MissionLogData): StoredMissionLogSession = synchronized(lock) {
        val capturedAt = System.currentTimeMillis()
        val decoded = data.altitude
        val session = StoredMissionLogSession(
            id = "$capturedAt-${data.state.command.name}",
            capturedAtEpochMillis = capturedAt,
            command = data.state.command.name,
            watchTimestampUtc = data.state.timestampUtc?.toString(),
            altitudeStartUtc = decoded?.startTimeUtc?.toString(),
            altitudeSamples = decoded?.samples.orEmpty().map { sample ->
                StoredAltitudeSample(
                    index = sample.index,
                    altitudeMetres = sample.altitudeMetres,
                    timestampUtc = sample.timestampUtc?.toString(),
                )
            },
            altitudePoints = decoded?.points.orEmpty().map { point ->
                StoredAltitudePoint(
                    slot = point.slot,
                    altitudeMetres = point.altitudeMetres,
                    timestampUtc = point.timestampUtc.toString(),
                    metadataHex = point.metadataHex,
                )
            },
            endMarkerIndex = decoded?.endMarkerIndex,
            altitudeRawBase64 = Base64.encodeToString(data.altitudeData, Base64.NO_WRAP),
            exerciseRawBase64 = Base64.encodeToString(data.exerciseData, Base64.NO_WRAP),
        )
        val updated = (listOf(session) + _sessions.value).take(MAX_SESSIONS)
        check(preferences.edit().putString(SESSIONS_KEY, gson.toJson(updated)).commit()) {
            "Unable to persist Mission Log history"
        }
        _sessions.value = updated
        session
    }

    private fun load(): List<StoredMissionLogSession> {
        val json = preferences.getString(SESSIONS_KEY, null) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<StoredMissionLogSession>>() {}.type
            gson.fromJson<List<StoredMissionLogSession>>(json, type).orEmpty()
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val PREFERENCES_NAME = "mission_log_history"
        const val SESSIONS_KEY = "sessions_v1"
        const val MAX_SESSIONS = 50
    }
}
