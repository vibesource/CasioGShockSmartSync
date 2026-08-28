package org.avmedia.gshockGoogleSync.data.altitude

import org.avmedia.gshockGoogleSync.services.LocationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AltitudeCorrectionQualityGateTest {
    private val now = 1_000_000L

    @Test
    fun `accepts a fresh accurate mean-sea-level altitude`() {
        val result = AltitudeCorrectionQualityGate.evaluate(
            location(altitude = 68.6, horizontal = 8f, vertical = 12f, ageMillis = 1_200),
            now,
        )

        assertTrue(result is AltitudeCorrectionQualityGate.Result.Accepted)
        result as AltitudeCorrectionQualityGate.Result.Accepted
        assertEquals(69, result.altitudeMetres)
        assertEquals(1_200L, result.ageMillis)
    }

    @Test
    fun `accepts exact Casio accuracy and age limits`() {
        assertTrue(
            AltitudeCorrectionQualityGate.evaluate(
                location(
                    altitude = 68.0,
                    horizontal = 50f,
                    vertical = 30f,
                    ageMillis = 5_000,
                ),
                now,
            ) is AltitudeCorrectionQualityGate.Result.Accepted,
        )
    }

    @Test
    fun `allows missing vertical accuracy like the Casio filter`() {
        assertTrue(
            AltitudeCorrectionQualityGate.evaluate(
                location(altitude = 68.0, horizontal = 10f, vertical = null),
                now,
            ) is AltitudeCorrectionQualityGate.Result.Accepted,
        )
    }

    @Test
    fun `rejects unavailable altitude or horizontal accuracy`() {
        assertRejected(
            AltitudeCorrectionQualityGate.evaluate(
                location(altitude = null, horizontal = 10f, vertical = 5f),
                now,
            ),
            "mean-sea-level altitude unavailable",
        )
        assertRejected(
            AltitudeCorrectionQualityGate.evaluate(
                location(altitude = 68.0, horizontal = null, vertical = 5f),
                now,
            ),
            "horizontal accuracy unavailable",
        )
    }

    @Test
    fun `rejects fixes outside Casio accuracy limits`() {
        assertRejected(
            AltitudeCorrectionQualityGate.evaluate(
                location(altitude = 68.0, horizontal = 50.1f, vertical = 5f),
                now,
            ),
            "horizontal accuracy ±50.1 m exceeds 50 m",
        )
        assertRejected(
            AltitudeCorrectionQualityGate.evaluate(
                location(altitude = 68.0, horizontal = 10f, vertical = 30.1f),
                now,
            ),
            "vertical accuracy ±30.1 m exceeds 30 m",
        )
    }

    @Test
    fun `rejects fixes older than five seconds`() {
        assertRejected(
            AltitudeCorrectionQualityGate.evaluate(
                location(altitude = 68.0, horizontal = 10f, vertical = 5f, ageMillis = 5_001),
                now,
            ),
            "location fix is 5 s old",
        )
    }

    private fun location(
        altitude: Double?,
        horizontal: Float?,
        vertical: Float?,
        ageMillis: Long = 1_000,
    ) = LocationProvider.Location(
        latitude = 52.3157,
        longitude = -1.539,
        altitudeMetres = altitude,
        horizontalAccuracyMetres = horizontal,
        verticalAccuracyMetres = vertical,
        timestampEpochMillis = now - ageMillis,
    )

    private fun assertRejected(result: AltitudeCorrectionQualityGate.Result, reason: String) {
        assertTrue(result is AltitudeCorrectionQualityGate.Result.Rejected)
        assertEquals(reason, (result as AltitudeCorrectionQualityGate.Result.Rejected).reason)
    }
}
