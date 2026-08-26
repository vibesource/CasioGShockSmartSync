package org.avmedia.gshockGoogleSync.data.missionlog

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class MissionLogRouteProfile(
    val intervalMillis: Long,
    val minimumDistanceMetres: Float,
) {
    DETAILED(intervalMillis = 10_000, minimumDistanceMetres = 5f),
    BALANCED(intervalMillis = 60_000, minimumDistanceMetres = 20f),
    BATTERY_SAVER(intervalMillis = 120_000, minimumDistanceMetres = 50f),
}

@Singleton
class MissionLogRouteProfileStore @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _profile = MutableStateFlow(load())

    val profile: StateFlow<MissionLogRouteProfile> = _profile.asStateFlow()

    fun set(profile: MissionLogRouteProfile) {
        check(preferences.edit().putString(KEY_PROFILE, profile.name).commit()) {
            "Unable to save Mission Log GPS profile"
        }
        _profile.value = profile
    }

    private fun load(): MissionLogRouteProfile = runCatching {
        MissionLogRouteProfile.valueOf(
            preferences.getString(KEY_PROFILE, null) ?: MissionLogRouteProfile.BALANCED.name,
        )
    }.getOrDefault(MissionLogRouteProfile.BALANCED)

    private companion object {
        const val PREFERENCES_NAME = "mission_log_route_settings"
        const val KEY_PROFILE = "profile"
    }
}
