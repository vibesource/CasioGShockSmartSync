package org.avmedia.gshockGoogleSync.ui.actions

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.avmedia.gshockGoogleSync.data.altitude.AltitudeCorrectionQualityGate
import org.avmedia.gshockGoogleSync.data.diagnostics.SyncDiagnosticsStore
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.scratchpad.TimeSettingsStorage
import org.avmedia.gshockGoogleSync.services.LocationProvider
import org.avmedia.gshockGoogleSync.ui.time.SolarTimeHelper
import org.avmedia.gshockGoogleSync.utils.LocalDataStorage
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.WatchInfo
import timber.log.Timber
import kotlin.math.roundToInt
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchTimeUpdater @Inject constructor(
    private val api: GShockRepository,
    private val timeSettingsStorage: TimeSettingsStorage,
    private val syncDiagnosticsStore: SyncDiagnosticsStore,
    @param:ApplicationContext private val context: Context
) {
    /**
     * Calculates the correct time offset according to the user's settings
     * (e.g. Local Mean Time, Local Solar Time, Fine adjustment) and sends it to the watch.
     */
    suspend fun updateTime() {
        timeSettingsStorage.load()
        val fineAdjustment = LocalDataStorage.getFineTimeAdjustment(context)
        val timeZoneOption = timeSettingsStorage.getTimeZoneOption()
        val timeZoneOffset = SolarTimeHelper.calculateTimeOffset(context, timeZoneOption)

        Timber.d("Setting time to watch with fine adjustment: $timeZoneOption $fineAdjustment and timezone offset: $timeZoneOffset")

        // Current time is the terminal GG-B100 packet: the watch disconnects immediately after
        // accepting it. Scheduled altitude correction must be placed directly before it rather
        // than launched in response to HomeTimeUpdated, when it is already too late.
        if (api.isAutoTimeStarted() && WatchInfo.hasAltimeterCorrection) {
            val location = runCatching {
                LocationProvider.getFreshLocation(
                    context,
                    timeoutMillis = 4_000,
                    maxUpdateAgeMillis = AltitudeCorrectionQualityGate.MAX_FIX_AGE_MILLIS,
                )
            }.onFailure { error ->
                Timber.e(error, "Unable to obtain scheduled altimeter location")
            }.getOrNull()
            val quality = AltitudeCorrectionQualityGate.evaluate(location)
            val altitude = (quality as? AltitudeCorrectionQualityGate.Result.Accepted)
                ?.altitudeMetres

            api.setTimeWithAltimeterCorrection(
                altitudeMetres = altitude,
                timeMs = null,
                offsetFormSystemTime = fineAdjustment + timeZoneOffset,
            )
            val message = when (quality) {
                is AltitudeCorrectionQualityGate.Result.Accepted -> {
                    val vertical = quality.verticalAccuracyMetres?.let {
                        ", v±${it.roundToInt()} m"
                    } ?: ", vertical accuracy unavailable"
                    "Queued ${quality.altitudeMetres} m before final time packet " +
                        "(h±${quality.horizontalAccuracyMetres.roundToInt()} m$vertical, " +
                        "age ${quality.ageMillis / 1_000} s)"
                }
                is AltitudeCorrectionQualityGate.Result.Rejected -> {
                    "Skipped correction: ${quality.reason}; queued unavailable response"
                }
            }
            syncDiagnosticsStore.record("ALTITUDE_CORRECTION", message)
            Timber.i("Scheduled altimeter correction: $message")
        } else {
            // Keep timeMs NULL so system time is sampled immediately before the final write.
            api.setTime(timeMs = null, offsetFormSystemTime = fineAdjustment + timeZoneOffset)
        }
        ProgressEvents.onNext("HomeTimeUpdated")
    }
}
