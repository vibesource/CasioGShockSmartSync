package org.avmedia.gshockGoogleSync.data.missionlog

import org.junit.Assert.assertTrue
import org.junit.Test

class MissionLogGpxTest {
    @Test
    fun `encodes route point with elevation and UTC time`() {
        val session = StoredMissionLogSession(
            id = "test",
            capturedAtEpochMillis = 0,
            command = "STOP",
            watchTimestampUtc = null,
            altitudeStartUtc = null,
            altitudeSamples = emptyList(),
            altitudePoints = emptyList(),
            endMarkerIndex = null,
            altitudeRawBase64 = "",
            exerciseRawBase64 = "",
            routePoints = listOf(StoredRoutePoint(1_000, 51.5, -0.1, 83.0, 5f)),
            routeAltitudeDatum = ROUTE_ALTITUDE_DATUM_ANDROID_MSL,
        )

        val gpx = MissionLogGpx.encode(session)

        assertTrue(gpx.contains("<trkpt lat=\"51.5\" lon=\"-0.1\">"))
        assertTrue(gpx.contains("<ele>83.0</ele>"))
        assertTrue(gpx.contains("<time>1970-01-01T00:00:01Z</time>"))
    }

    @Test
    fun `omits unverified legacy elevation`() {
        val session = StoredMissionLogSession(
            id = "legacy",
            capturedAtEpochMillis = 0,
            command = "STOP",
            watchTimestampUtc = null,
            altitudeStartUtc = null,
            altitudeSamples = emptyList(),
            altitudePoints = emptyList(),
            endMarkerIndex = null,
            altitudeRawBase64 = "",
            exerciseRawBase64 = "",
            routePoints = listOf(StoredRoutePoint(1_000, 51.5, -0.1, 118.2, 5f)),
        )

        val gpx = MissionLogGpx.encode(session)

        assertTrue(!gpx.contains("<ele>"))
    }
}
