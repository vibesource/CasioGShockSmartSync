package org.avmedia.gshockGoogleSync.data.missionlog

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
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

object MissionLogRouteMetrics {
    fun distanceMetres(points: List<StoredRoutePoint>): Double =
        points.zipWithNext().sumOf { (start, end) -> distanceMetres(start, end) }

    private fun distanceMetres(start: StoredRoutePoint, end: StoredRoutePoint): Double {
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

    @Synchronized
    fun begin(startedAtEpochMillis: Long = System.currentTimeMillis()) {
        routeFile.writeText("")
        check(
            preferences.edit()
                .putBoolean(KEY_ACTIVE, true)
                .putLong(KEY_STARTED_AT, startedAtEpochMillis)
                .commit(),
        ) { "Unable to start Mission Log route storage" }
    }

    @Synchronized
    fun resumeOrBegin(startedAtEpochMillis: Long = System.currentTimeMillis()) {
        if (!isActive()) begin(startedAtEpochMillis)
    }

    @Synchronized
    fun add(point: StoredRoutePoint) {
        if (!isActive()) return
        routeFile.appendText(encode(point) + "\n")
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
        return MissionLogRoute(startedAt, endedAtEpochMillis, points)
    }

    fun isActive(): Boolean = preferences.getBoolean(KEY_ACTIVE, false)

    private fun encode(point: StoredRoutePoint): String = listOf(
        point.timestampEpochMillis.toString(),
        point.latitude.toString(),
        point.longitude.toString(),
        point.altitudeMetres?.toString().orEmpty(),
        point.accuracyMetres?.toString().orEmpty(),
    ).joinToString(",")

    private fun decode(line: String): StoredRoutePoint? = runCatching {
        val fields = line.split(',')
        require(fields.size == 5)
        StoredRoutePoint(
            timestampEpochMillis = fields[0].toLong(),
            latitude = fields[1].toDouble(),
            longitude = fields[2].toDouble(),
            altitudeMetres = fields[3].takeIf(String::isNotEmpty)?.toDouble(),
            accuracyMetres = fields[4].takeIf(String::isNotEmpty)?.toFloat(),
        )
    }.getOrNull()

    private companion object {
        const val PREFERENCES_NAME = "mission_log_route"
        const val KEY_ACTIVE = "active"
        const val KEY_STARTED_AT = "started_at"
        const val ACTIVE_ROUTE_FILE = "mission_log_active_route.csv"
    }
}
