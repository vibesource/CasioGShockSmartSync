package org.avmedia.gshockGoogleSync

import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.provider.Settings
import android.app.AlertDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.data.missionlog.MissionLogStore
import org.avmedia.gshockGoogleSync.data.steps.StepCounterStore
import org.avmedia.gshockGoogleSync.data.locationindicator.LocationIndicatorCalculator
import org.avmedia.gshockGoogleSync.data.locationindicator.LocationTargetStore
import org.avmedia.gshockGoogleSync.services.NotificationMonitorService
import org.avmedia.gshockGoogleSync.services.LocationProvider
import org.avmedia.gshockGoogleSync.utils.ActivityProvider
import org.avmedia.gshockGoogleSync.utils.Utils
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockapi.AppNotification
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.model.StepCounterData
import org.avmedia.gshockapi.model.LocationIndicatorFailure
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainEventHandler(
    private val context: GShockApplication,
    private val repository: GShockRepository,
    private val screenManager: IScreenManager,
    private val missionLogStore: MissionLogStore,
    private val stepCounterStore: StepCounterStore,
    private val locationTargetStore: LocationTargetStore,
) {
    fun setupEventSubscription() {
        val eventActions = arrayOf(
            EventAction("ConnectionSetupComplete") {},
            EventAction("WatchInitializationCompleted") { handleWatchInitialization() },
            EventAction("ConnectionFailed") { handleConnectionFailure() },
            EventAction("Error") { handleError() },
            EventAction("WaitForConnection") { handleWaitForConnection() },
            EventAction("Disconnect") { handleDisconnect() },
            EventAction("HomeTimeUpdated") {},
            EventAction("RunActions") { handleRunAction() },
            EventAction("AppNotification") { handleAppNotification() },
            EventAction("LocationServicesDisabled") { handleLocationServicesDisabled() },
            EventAction("StepCounterDataReceived") { handleStepCounterData() },
            EventAction("LocationIndicatorRefreshRequested") {
                handleLocationIndicatorConnection(isRefresh = true)
            },
        )

        ProgressEvents.runEventActions(Utils.AppHashCode(), eventActions)
    }

    private fun handleStepCounterData() {
        val steps = ProgressEvents.getPayload("StepCounterDataReceived") as? StepCounterData ?: return
        runCatching { stepCounterStore.save(steps) }
            .onSuccess { Timber.i("Step count synchronized: ${steps.currentDaySteps}") }
            .onFailure { error -> Timber.e(error, "Step count could not be persisted") }
    }

    private fun handleWatchInitialization() {
        if (repository.isLocationIndicatorConnection()) {
            handleLocationIndicatorConnection(isRefresh = false)
            return
        }
        if (repository.isMissionLogConnection()) {
            handleMissionLogConnection()
            return
        }
        if (repository.supportsAppNotifications()) {
            NotificationMonitorService.startService(context)
        }
        screenManager.showContentSelector(repository)
    }

    private val locationIndicatorMutex = Mutex()

    private fun handleLocationIndicatorConnection(isRefresh: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            if (!locationIndicatorMutex.tryLock()) {
                Timber.d("Location Indicator: ignoring overlapping refresh request")
                return@launch
            }
            try {
                val target = locationTargetStore.target.value
                if (target == null) {
                    runCatching {
                        if (isRefresh) {
                            repository.updateLocationIndicatorFailure(
                                LocationIndicatorFailure.NO_SAVED_DESTINATION,
                            )
                        } else {
                            repository.failLocationIndicator(
                                LocationIndicatorFailure.NO_SAVED_DESTINATION,
                            )
                        }
                    }.onFailure {
                        Timber.e(it, "Location Indicator failure response could not be sent")
                    }
                    if (!isRefresh) AppSnackbar("Location Indicator failed: no destination is saved")
                    return@launch
                }

                val current = LocationProvider.getFreshLocation(context)
                if (current == null) {
                    runCatching {
                        if (isRefresh) {
                            repository.updateLocationIndicatorFailure(
                                LocationIndicatorFailure.CURRENT_LOCATION_UNAVAILABLE,
                            )
                        } else {
                            repository.failLocationIndicator(
                                LocationIndicatorFailure.CURRENT_LOCATION_UNAVAILABLE,
                            )
                        }
                    }.onFailure {
                        Timber.e(it, "Location Indicator failure response could not be sent")
                    }
                    if (!isRefresh) {
                        AppSnackbar("Location Indicator failed: fresh phone location is unavailable")
                    }
                    return@launch
                }

                val result = LocationIndicatorCalculator.calculate(
                    currentLatitude = current.latitude,
                    currentLongitude = current.longitude,
                    targetLatitude = target.latitude,
                    targetLongitude = target.longitude,
                )
                runCatching {
                    if (isRefresh) {
                        repository.updateLocationIndicator(
                            result.distanceMetres,
                            result.bearingDegrees,
                        )
                    } else {
                        repository.completeLocationIndicator(
                            result.distanceMetres,
                            result.bearingDegrees,
                        )
                    }
                }.onSuccess {
                    Timber.i(
                        "Location Indicator ${if (isRefresh) "refresh" else "complete"}: " +
                            "${result.distanceMetres}m at ${result.bearingDegrees}deg",
                    )
                    if (!isRefresh) {
                        AppSnackbar(
                            "Location Indicator: ${result.distanceMetres} m at " +
                                "${result.bearingDegrees}°",
                        )
                    }
                }.onFailure { error ->
                    Timber.e(error, "Location Indicator transfer failed")
                    if (!isRefresh) {
                        AppSnackbar(
                            "Location Indicator failed: ${error.message ?: "unknown error"}",
                        )
                    }
                }
            } finally {
                locationIndicatorMutex.unlock()
            }
        }
    }

    private fun handleMissionLogConnection() {
        CoroutineScope(Dispatchers.IO).launch {
            val location = LocationProvider.getLocation(context)
            if (location == null) {
                Timber.e("Mission Log: no current or cached phone location")
                AppSnackbar("Mission Log failed: phone location is unavailable")
                return@launch
            }

            runCatching {
                repository.downloadMissionLog(location.latitude, location.longitude)
            }.onSuccess { missionLog ->
                val saveResult = runCatching { missionLogStore.save(missionLog) }
                saveResult.onFailure { error ->
                    Timber.e(error, "Mission Log history could not be saved")
                }
                Timber.i(
                    "Mission Log complete: command=${missionLog.state.command}, " +
                        "altitude=${missionLog.altitudeData.size}B, " +
                        "exercise=${missionLog.exerciseData.size}B",
                )
                AppSnackbar(
                    if (saveResult.isSuccess) {
                        "Mission Log received (${missionLog.state.command.name.lowercase()})"
                    } else {
                        "Mission Log received, but history could not be saved"
                    },
                )
            }.onFailure { error ->
                Timber.e(error, "Mission Log transfer failed")
                AppSnackbar("Mission Log failed: ${error.message ?: "unknown error"}")
            }
        }
    }

    private fun handleAppNotification() {
        if (repository.supportsAppNotifications()) {
            val appNotification = ProgressEvents.getPayload("AppNotification") as AppNotification?
            if (appNotification != null) {
                repository.sendAppNotification(appNotification)
            }
        }
    }

    private fun handleConnectionFailure() {
        Timber.e("Failed to connect to the watch")
    }

    private fun handleRunAction() {
        screenManager.showRunActionsScreen()

        CoroutineScope(Dispatchers.Main).launch {
            delay(3000)
            screenManager.showContentSelector(repository)
        }
    }

    private fun handleError() {
        val payload = ProgressEvents.getPayload("Error") as String?
        val message = payload
            ?: context.getString(
                R.string.apierror_ensure_the_official_g_shock_app_is_not_running
            )
        Timber.e("API Error event. payload=${payload ?: "<none, using default message>"}")

        repository.disconnect()
        screenManager.showError(message)
        screenManager.showPreConnectionScreen()
    }

    private fun handleDisconnect() {
        ProgressEvents.getPayload("Disconnect")?.let { device ->
            repository.teardownConnection(device as BluetoothDevice)
        }

        Executors.newSingleThreadScheduledExecutor().schedule({
            screenManager.showInitialScreen()
        }, 0L, TimeUnit.SECONDS)
    }

    private fun handleWaitForConnection() {
        CoroutineScope(Dispatchers.Default).launch {
            context.deviceAssociationManager.checkPairedDevicesOrNotify()
        }
    }

    private fun handleLocationServicesDisabled() {
        CoroutineScope(Dispatchers.Main).launch {
            val activity = ActivityProvider.getCurrentActivity() ?: run {
                Timber.e("No activity available to show location services dialog")
                return@launch
            }

            AlertDialog.Builder(activity)
                .setTitle("Location Services Required")
                .setMessage("Android requires Location Services to be enabled for Bluetooth scanning. This app does not use your location — it is an Android system requirement.")
                .setPositiveButton("Open Settings") { _, _ ->
                    activity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton("Cancel", null)
                .setCancelable(false)
                .show()
        }
    }
}
