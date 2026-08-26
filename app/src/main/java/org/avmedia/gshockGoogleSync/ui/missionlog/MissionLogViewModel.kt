package org.avmedia.gshockGoogleSync.ui.missionlog

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import org.avmedia.gshockGoogleSync.data.missionlog.MissionLogStore
import org.avmedia.gshockGoogleSync.data.missionlog.MissionLogRouteStore
import org.avmedia.gshockGoogleSync.data.missionlog.MissionLogRouteProfileStore
import javax.inject.Inject

@HiltViewModel
class MissionLogViewModel @Inject constructor(
    store: MissionLogStore,
    routeStore: MissionLogRouteStore,
    profileStore: MissionLogRouteProfileStore,
) : ViewModel() {
    val sessions = store.sessions
    val recordingState = routeStore.recordingState
    val routeProfile = profileStore.profile
}
