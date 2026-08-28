package org.avmedia.gshockGoogleSync.data.altitude

import org.avmedia.gshockGoogleSync.services.LocationProvider
import kotlin.math.roundToInt

object AltitudeCorrectionQualityGate {
    const val MAX_HORIZONTAL_ACCURACY_METRES = 50f
    const val MAX_VERTICAL_ACCURACY_METRES = 30f
    const val MAX_FIX_AGE_MILLIS = 5_000L

    sealed interface Result {
        data class Accepted(
            val altitudeMetres: Int,
            val horizontalAccuracyMetres: Float,
            val verticalAccuracyMetres: Float?,
            val ageMillis: Long,
        ) : Result

        data class Rejected(val reason: String) : Result
    }

    fun evaluate(
        location: LocationProvider.Location?,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): Result {
        if (location == null) return Result.Rejected("no fresh location fix")

        val altitude = location.altitudeMetres
        if (altitude == null || !altitude.isFinite()) {
            return Result.Rejected("mean-sea-level altitude unavailable")
        }

        val horizontalAccuracy = location.horizontalAccuracyMetres
        if (horizontalAccuracy == null || !horizontalAccuracy.isFinite() || horizontalAccuracy < 0f) {
            return Result.Rejected("horizontal accuracy unavailable")
        }
        if (horizontalAccuracy > MAX_HORIZONTAL_ACCURACY_METRES) {
            return Result.Rejected(
                "horizontal accuracy ±$horizontalAccuracy m exceeds " +
                    "${MAX_HORIZONTAL_ACCURACY_METRES.roundToInt()} m",
            )
        }

        val verticalAccuracy = location.verticalAccuracyMetres
        if (verticalAccuracy != null) {
            if (!verticalAccuracy.isFinite() || verticalAccuracy < 0f) {
                return Result.Rejected("vertical accuracy invalid")
            }
            if (verticalAccuracy > MAX_VERTICAL_ACCURACY_METRES) {
                return Result.Rejected(
                    "vertical accuracy ±$verticalAccuracy m exceeds " +
                        "${MAX_VERTICAL_ACCURACY_METRES.roundToInt()} m",
                )
            }
        }

        val timestamp = location.timestampEpochMillis
            ?: return Result.Rejected("location timestamp unavailable")
        val ageMillis = (nowEpochMillis - timestamp).coerceAtLeast(0L)
        if (ageMillis > MAX_FIX_AGE_MILLIS) {
            return Result.Rejected("location fix is ${ageMillis / 1_000} s old")
        }

        return Result.Accepted(
            altitudeMetres = altitude.roundToInt(),
            horizontalAccuracyMetres = horizontalAccuracy,
            verticalAccuracyMetres = verticalAccuracy,
            ageMillis = ageMillis,
        )
    }
}
