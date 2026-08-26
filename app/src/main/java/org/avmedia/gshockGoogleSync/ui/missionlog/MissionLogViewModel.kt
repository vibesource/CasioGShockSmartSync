package org.avmedia.gshockGoogleSync.ui.missionlog

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import org.avmedia.gshockGoogleSync.data.missionlog.MissionLogStore
import javax.inject.Inject

@HiltViewModel
class MissionLogViewModel @Inject constructor(
    store: MissionLogStore,
) : ViewModel() {
    val sessions = store.sessions
}
