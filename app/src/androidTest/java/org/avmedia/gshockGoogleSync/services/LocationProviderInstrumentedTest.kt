package org.avmedia.gshockGoogleSync.services

import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocationProviderInstrumentedTest {
    @Test
    fun convertsObservedEllipsoidHeightToMeanSeaLevel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val raw = Location("recorded-gps-fix").apply {
            latitude = 52.3156997
            longitude = -1.5390498
            altitude = 118.19999694824219
        }

        val converted = LocationProvider.Location.fromAndroidLocation(context, raw).altitudeMetres

        assertNotNull(converted)
        val altitude = requireNotNull(converted)
        assertTrue("Expected local MSL altitude, got $altitude", altitude in 55.0..80.0)
    }
}
