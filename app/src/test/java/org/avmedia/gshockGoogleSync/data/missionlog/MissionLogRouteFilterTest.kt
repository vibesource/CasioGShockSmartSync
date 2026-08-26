package org.avmedia.gshockGoogleSync.data.missionlog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionLogRouteFilterTest {
    @Test
    fun `accepts first accurate fix`() {
        assertTrue(MissionLogRouteFilter.accepts(point(0, 51.5, -0.1, 12f), null, 60f))
    }

    @Test
    fun `rejects inaccurate fix`() {
        assertFalse(MissionLogRouteFilter.accepts(point(0, 51.5, -0.1, 61f), null, 60f))
    }

    @Test
    fun `rejects cached fix from before route start`() {
        assertFalse(
            MissionLogRouteFilter.accepts(
                candidate = point(10_000, 51.5, -0.1, 12f),
                previous = null,
                maximumAccuracyMetres = 60f,
                notBeforeEpochMillis = 10_001,
            ),
        )
    }

    @Test
    fun `rejects impossible jump and non increasing time`() {
        val previous = point(1_000, 51.5, -0.1, 10f)
        assertFalse(
            MissionLogRouteFilter.accepts(
                point(2_000, 51.51, -0.1, 10f),
                previous,
                60f,
            ),
        )
        assertFalse(MissionLogRouteFilter.accepts(previous, previous, 60f))
    }

    @Test
    fun `accepts walking movement`() {
        val previous = point(1_000, 51.5, -0.1, 10f)
        assertTrue(
            MissionLogRouteFilter.accepts(
                point(61_000, 51.5005, -0.1, 10f),
                previous,
                60f,
            ),
        )
    }

    private fun point(time: Long, latitude: Double, longitude: Double, accuracy: Float) =
        StoredRoutePoint(time, latitude, longitude, accuracyMetres = accuracy)
}
