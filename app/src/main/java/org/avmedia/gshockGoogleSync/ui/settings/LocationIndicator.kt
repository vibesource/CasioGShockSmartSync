package org.avmedia.gshockGoogleSync.ui.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.avmedia.gshockGoogleSync.data.locationindicator.LocationTargetStore
import org.avmedia.gshockGoogleSync.services.LocationProvider
import org.avmedia.gshockGoogleSync.ui.common.AppCard
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class LocationIndicatorViewModel @Inject constructor(
    private val targetStore: LocationTargetStore,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    val target = targetStore.target

    fun save(latitudeText: String, longitudeText: String): String {
        val latitude = latitudeText.trim().toDoubleOrNull()
            ?: return "Enter a valid latitude"
        val longitude = longitudeText.trim().toDoubleOrNull()
            ?: return "Enter a valid longitude"
        if (latitude !in -90.0..90.0) return "Latitude must be between -90 and 90"
        if (longitude !in -180.0..180.0) return "Longitude must be between -180 and 180"
        return runCatching { targetStore.save(latitude, longitude) }
            .fold(onSuccess = { "Location Indicator destination saved" }, onFailure = { "Could not save destination" })
    }

    fun useCurrentLocation(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val location = LocationProvider.getFreshLocation(context)
            if (location == null) {
                onResult("A fresh phone location is unavailable")
                return@launch
            }
            onResult(
                runCatching { targetStore.save(location.latitude, location.longitude) }
                    .fold(
                        onSuccess = { "Current location saved as destination" },
                        onFailure = { "Could not save destination" },
                    ),
            )
        }
    }

    fun clear(): String = runCatching { targetStore.clear() }
        .fold(onSuccess = { "Location Indicator destination cleared" }, onFailure = { "Could not clear destination" })

    fun hasBackgroundLocation(): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

    fun openPermissionSettings() {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Composable
fun LocationIndicatorSettings(
    viewModel: LocationIndicatorViewModel = hiltViewModel(),
) {
    val target by viewModel.target.collectAsState()
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }

    LaunchedEffect(target) {
        latitude = target?.latitude?.toString().orEmpty()
        longitude = target?.longitude?.toString().orEmpty()
    }

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Location Memory & Indicator")
            Text(
                if (target == null) {
                    "Save a destination here, or hold the watch's connect button to remember your current position."
                } else {
                    "Destination: ${target?.latitude}, ${target?.longitude}"
                },
            )
            if (!viewModel.hasBackgroundLocation()) {
                Text(
                    "For updates while Smart Sync is closed or the phone is locked, open " +
                        "Permissions → Location, select Allow all the time, and keep " +
                        "Precise location enabled.",
                )
                OutlinedButton(
                    onClick = viewModel::openPermissionSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Background location settings") }
            }
            OutlinedTextField(
                value = latitude,
                onValueChange = { latitude = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Latitude") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            OutlinedTextField(
                value = longitude,
                onValueChange = { longitude = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Longitude") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { org.avmedia.gshockGoogleSync.ui.common.AppSnackbar(viewModel.save(latitude, longitude)) },
                    modifier = Modifier.weight(1f),
                ) { Text("Save") }
                OutlinedButton(
                    onClick = {
                        viewModel.useCurrentLocation {
                            org.avmedia.gshockGoogleSync.ui.common.AppSnackbar(it)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Use current") }
            }
            if (target != null) {
                OutlinedButton(
                    onClick = { org.avmedia.gshockGoogleSync.ui.common.AppSnackbar(viewModel.clear()) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Clear destination") }
            }
        }
    }
}
