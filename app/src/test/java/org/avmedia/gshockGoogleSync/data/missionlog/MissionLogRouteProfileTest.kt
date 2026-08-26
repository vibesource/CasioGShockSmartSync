package org.avmedia.gshockGoogleSync.data.missionlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionLogRouteProfileTest {
    @Test
    fun `balanced is the intended default cadence`() {
        assertEquals(60_000L, MissionLogRouteProfile.BALANCED.intervalMillis)
        assertEquals(20f, MissionLogRouteProfile.BALANCED.minimumDistanceMetres)
        assertEquals(60f, MissionLogRouteProfile.BALANCED.maximumAccuracyMetres)
    }

    @Test
    fun `profiles trade route detail for fewer updates`() {
        val profiles = listOf(
            MissionLogRouteProfile.DETAILED,
            MissionLogRouteProfile.BALANCED,
            MissionLogRouteProfile.BATTERY_SAVER,
        )
        assertTrue(profiles.zipWithNext().all { (a, b) -> a.intervalMillis < b.intervalMillis })
        assertTrue(
            profiles.zipWithNext().all { (a, b) ->
                a.minimumDistanceMetres < b.minimumDistanceMetres
            },
        )
    }
}
