package org.avmedia.gshockGoogleSync.data.missionlog

import org.junit.Assert.assertEquals
import org.junit.Test

class MissionLogRouteMetricsTest {
    @Test
    fun `empty and one-point routes have zero distance`() {
        assertEquals(0.0, MissionLogRouteMetrics.distanceMetres(emptyList()), 0.0)
        assertEquals(
            0.0,
            MissionLogRouteMetrics.distanceMetres(listOf(point(51.5, -0.1))),
            0.0,
        )
    }

    @Test
    fun `distance follows consecutive GPS points`() {
        val distance = MissionLogRouteMetrics.distanceMetres(
            listOf(
                point(51.5000, -0.1000),
                point(51.5009, -0.1000),
                point(51.5009, -0.0986),
            ),
        )
        assertEquals(197.0, distance, 3.0)
    }

    private fun point(latitude: Double, longitude: Double) = StoredRoutePoint(
        timestampEpochMillis = 0,
        latitude = latitude,
        longitude = longitude,
    )
}
