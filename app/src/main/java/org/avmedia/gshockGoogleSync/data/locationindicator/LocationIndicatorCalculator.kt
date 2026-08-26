package org.avmedia.gshockGoogleSync.data.locationindicator

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

data class LocationIndicatorResult(
    val distanceMetres: Long,
    val bearingDegrees: Int,
)

object LocationIndicatorCalculator {
    private const val EARTH_RADIUS_METRES = 6_378_137.0

    fun calculate(
        currentLatitude: Double,
        currentLongitude: Double,
        targetLatitude: Double,
        targetLongitude: Double,
    ): LocationIndicatorResult {
        val latitude1 = Math.toRadians(currentLatitude)
        val latitude2 = Math.toRadians(targetLatitude)
        val longitudeDelta = Math.toRadians(targetLongitude - currentLongitude)
        val latitudeDelta = latitude2 - latitude1

        val haversine = sin(latitudeDelta / 2).let { it * it } +
            cos(latitude1) * cos(latitude2) *
            sin(longitudeDelta / 2).let { it * it }
        val distance = 2 * EARTH_RADIUS_METRES * atan2(sqrt(haversine), sqrt(1 - haversine))

        val y = sin(longitudeDelta) * cos(latitude2)
        val x = cos(latitude1) * sin(latitude2) -
            sin(latitude1) * cos(latitude2) * cos(longitudeDelta)
        val bearing = ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).roundToInt() % 360

        return LocationIndicatorResult(distance.roundToLong(), bearing)
    }
}
