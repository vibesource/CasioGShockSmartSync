package org.avmedia.gshockGoogleSync.data.locationindicator

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationIndicatorCalculatorTest {
    @Test
    fun identicalPointsAreZeroDistance() {
        assertEquals(
            LocationIndicatorResult(0, 0),
            LocationIndicatorCalculator.calculate(51.5, -0.1, 51.5, -0.1),
        )
    }

    @Test
    fun oneKilometreEastHasExpectedDistanceAndBearing() {
        val longitudeDelta = Math.toDegrees(1_000.0 / 6_378_137.0)
        assertEquals(
            LocationIndicatorResult(1_000, 90),
            LocationIndicatorCalculator.calculate(0.0, 0.0, 0.0, longitudeDelta),
        )
    }

    @Test
    fun oneKilometreWestHasExpectedDistanceAndBearing() {
        val longitudeDelta = Math.toDegrees(1_000.0 / 6_378_137.0)
        assertEquals(
            LocationIndicatorResult(1_000, 270),
            LocationIndicatorCalculator.calculate(0.0, 0.0, 0.0, -longitudeDelta),
        )
    }
}
