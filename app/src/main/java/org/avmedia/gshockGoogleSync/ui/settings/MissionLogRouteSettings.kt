package org.avmedia.gshockGoogleSync.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import org.avmedia.gshockGoogleSync.data.missionlog.MissionLogRouteProfile
import org.avmedia.gshockGoogleSync.data.missionlog.MissionLogRouteProfileStore
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import javax.inject.Inject

@HiltViewModel
class MissionLogRouteSettingsViewModel @Inject constructor(
    private val store: MissionLogRouteProfileStore,
) : ViewModel() {
    val profile = store.profile

    fun select(profile: MissionLogRouteProfile) = store.set(profile)
}

@Composable
fun MissionLogRouteSettings(
    viewModel: MissionLogRouteSettingsViewModel = hiltViewModel(),
) {
    val selected by viewModel.profile.collectAsState()
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Mission Log GPS")
            Text("Choose route detail and battery use. Changes apply to the next Mission Log.")
            MissionLogRouteProfile.entries.forEach { profile ->
                val (title, description) = profile.description()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.select(profile) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selected == profile,
                        onClick = { viewModel.select(profile) },
                    )
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(title)
                        Text(description)
                    }
                }
            }
        }
    }
}

private fun MissionLogRouteProfile.description(): Pair<String, String> = when (this) {
    MissionLogRouteProfile.DETAILED -> "Detailed" to "Every 10 seconds / 5 m · highest battery use"
    MissionLogRouteProfile.BALANCED -> "Balanced" to "Every 60 seconds / 20 m · recommended"
    MissionLogRouteProfile.BATTERY_SAVER -> "Battery saver" to "Every 2 minutes / 50 m"
}
