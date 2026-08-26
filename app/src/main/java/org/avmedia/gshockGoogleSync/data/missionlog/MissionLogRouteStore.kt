package org.avmedia.gshockGoogleSync.data.missionlog

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import javax.inject.Inject
import javax.inject.Singleton

data class StoredRoutePoint(
    val timestampEpochMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMetres: Double? = null,
    val accuracyMetres: Float? = null,
    val verticalAccuracyMetres: Float? = null,
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90 degrees" }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180 degrees" }
    }
}

data class MissionLogRoute(
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long,
    val points: List<StoredRoutePoint>,
)

data class ActiveMissionLogRoute(
    val isActive: Boolean = false,
    val startedAtEpochMillis: Long? = null,
    val pointCount: Int = 0,
)

object MissionLogRouteMetrics {
    fun distanceMetres(points: List<StoredRoutePoint>): Double =
        points.zipWithNext().sumOf { (start, end) -> distanceMetres(start, end) }

    fun distanceMetres(start: StoredRoutePoint, end: StoredRoutePoint): Double {
        val latitude1 = Math.toRadians(start.latitude)
        val latitude2 = Math.toRadians(end.latitude)
        val latitudeDelta = latitude2 - latitude1
        val longitudeDelta = Math.toRadians(end.longitude - start.longitude)
        val a = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
            cos(latitude1) * cos(latitude2) *
            sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
        return EARTH_RADIUS_METRES * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private const val EARTH_RADIUS_METRES = 6_371_000.0
}

object MissionLogRouteFilter {
    private const val MAX_ROUTE_SPEED_METRES_PER_SECOND = 15.0

    fun accepts(
        candidate: StoredRoutePoint,
        previous: StoredRoutePoint?,
        maximumAccuracyMetres: Float,
        notBeforeEpochMillis: Long? = null,
    ): Boolean {
        if (notBeforeEpochMillis != null && candidate.timestampEpochMillis < notBeforeEpochMillis) {
            return false
        }
        if (candidate.accuracyMetres?.let { it > maximumAccuracyMetres } == true) return false
        if (previous == null) return true
        val elapsedSeconds = (candidate.timestampEpochMillis - previous.timestampEpochMillis) / 1_000.0
        if (elapsedSeconds <= 0) return false
        return MissionLogRouteMetrics.distanceMetres(previous, candidate) / elapsedSeconds <=
            MAX_ROUTE_SPEED_METRES_PER_SECOND
    }
}

/**
 * Incremental, process-safe storage for the route that is currently being recorded.
 * Each location is appended immediately, avoiding the data-loss and rewrite cost of
 * holding an hours-long route only in memory or SharedPreferences.
 */
@Singleton
class MissionLogRouteStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val routeFile: File
        get() = File(context.filesDir, ACTIVE_ROUTE_FILE)
    private val _recordingState = MutableStateFlow(loadRecordingState())

    val recordingState: StateFlow<ActiveMissionLogRoute> = _recordingState.asStateFlow()

    @Synchronized
    fun begin(startedAtEpochMillis: Long = System.currentTimeMillis()) {
        routeFile.writeText("")
        check(
            preferences.edit()
                .putBoolean(KEY_ACTIVE, true)
                .putLong(KEY_STARTED_AT, startedAtEpochMillis)
                .commit(),
        ) { "Unable to start Mission Log route storage" }
        _recordingState.value = ActiveMissionLogRoute(true, startedAtEpochMillis, 0)
    }

    @Synchronized
    fun resumeOrBegin(startedAtEpochMillis: Long = System.currentTimeMillis()) {
        if (!isActive()) begin(startedAtEpochMillis)
    }

    @Synchronized
    fun add(point: StoredRoutePoint) {
        if (!isActive()) return
        routeFile.appendText(encode(point) + "\n")
        _recordingState.value = _recordingState.value.copy(
            isActive = true,
            pointCount = _recordingState.value.pointCount + 1,
        )
    }

    @Synchronized
    fun finish(endedAtEpochMillis: Long = System.currentTimeMillis()): MissionLogRoute? {
        if (!isActive()) return null
        val startedAt = preferences.getLong(KEY_STARTED_AT, endedAtEpochMillis)
        val points = if (routeFile.exists()) {
            routeFile.useLines { lines -> lines.mapNotNull(::decode).toList() }
        } else {
            emptyList()
        }
        check(preferences.edit().clear().commit()) { "Unable to finish Mission Log route storage" }
        if (routeFile.exists() && !routeFile.delete()) {
            routeFile.writeText("")
        }
        _recordingState.value = ActiveMissionLogRoute()
        return MissionLogRoute(startedAt, endedAtEpochMillis, points)
    }

    fun isActive(): Boolean = preferences.getBoolean(KEY_ACTIVE, false)

    private fun loadRecordingState(): ActiveMissionLogRoute {
        if (!isActive()) return ActiveMissionLogRoute()
        val pointCount = if (routeFile.exists()) routeFile.useLines { it.count() } else 0
        return ActiveMissionLogRoute(
            isActive = true,
            startedAtEpochMillis = preferences.getLong(KEY_STARTED_AT, 0L).takeIf { it > 0 },
            pointCount = pointCount,
        )
    }

    private fun encode(point: StoredRoutePoint): String = listOf(
        point.timestampEpochMillis.toString(),
        point.latitude.toString(),
        point.longitude.toString(),
        point.altitudeMetres?.toString().orEmpty(),
        point.accuracyMetres?.toString().orEmpty(),
        point.verticalAccuracyMetres?.toString().orEmpty(),
    ).joinToString(",")

    private fun decode(line: String): StoredRoutePoint? = runCatching {
        val fields = line.split(',')
        require(fields.size in 5..6)
        StoredRoutePoint(
            timestampEpochMillis = fields[0].toLong(),
            latitude = fields[1].toDouble(),
            longitude = fields[2].toDouble(),
            altitudeMetres = fields[3].takeIf(String::isNotEmpty)?.toDouble(),
            accuracyMetres = fields[4].takeIf(String::isNotEmpty)?.toFloat(),
            verticalAccuracyMetres = fields.getOrNull(5)?.takeIf(String::isNotEmpty)?.toFloat(),
        )
    }.getOrNull()

    private companion object {
        const val PREFERENCES_NAME = "mission_log_route"
        const val KEY_ACTIVE = "active"
        const val KEY_STARTED_AT = "started_at"
        const val ACTIVE_ROUTE_FILE = "mission_log_active_route.csv"
    }
}
