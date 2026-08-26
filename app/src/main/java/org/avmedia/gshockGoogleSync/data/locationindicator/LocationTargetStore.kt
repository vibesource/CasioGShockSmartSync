package org.avmedia.gshockGoogleSync.data.locationindicator

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class LocationTarget(
    val latitude: Double,
    val longitude: Double,
    val savedAtEpochMillis: Long,
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90 degrees" }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180 degrees" }
    }
}

/** Stores the single destination used when the GG-B100 requests Location Indicator data. */
@Singleton
class LocationTargetStore @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _target = MutableStateFlow(load())

    val target: StateFlow<LocationTarget?> = _target.asStateFlow()

    fun save(latitude: Double, longitude: Double): LocationTarget {
        val target = LocationTarget(latitude, longitude, System.currentTimeMillis())
        check(
            preferences.edit()
                .putString(KEY_LATITUDE, target.latitude.toString())
                .putString(KEY_LONGITUDE, target.longitude.toString())
                .putLong(KEY_SAVED_AT, target.savedAtEpochMillis)
                .commit(),
        ) { "Unable to persist Location Indicator destination" }
        _target.value = target
        return target
    }

    fun clear() {
        check(preferences.edit().clear().commit()) {
            "Unable to clear Location Indicator destination"
        }
        _target.value = null
    }

    private fun load(): LocationTarget? = runCatching {
        val latitude = preferences.getString(KEY_LATITUDE, null)?.toDoubleOrNull()
            ?: return null
        val longitude = preferences.getString(KEY_LONGITUDE, null)?.toDoubleOrNull()
            ?: return null
        LocationTarget(latitude, longitude, preferences.getLong(KEY_SAVED_AT, 0L))
    }.getOrNull()

    private companion object {
        const val PREFERENCES_NAME = "location_indicator"
        const val KEY_LATITUDE = "target_latitude"
        const val KEY_LONGITUDE = "target_longitude"
        const val KEY_SAVED_AT = "target_saved_at"
    }
}
