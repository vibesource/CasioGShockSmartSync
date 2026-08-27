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
import org.avmedia.gshockGoogleSync.data.missionlog.MissionLogRouteStore
import org.avmedia.gshockGoogleSync.data.steps.StepCounterStore
import org.avmedia.gshockGoogleSync.data.locationindicator.LocationIndicatorCalculator
import org.avmedia.gshockGoogleSync.data.locationindicator.LocationTargetStore
import org.avmedia.gshockGoogleSync.data.diagnostics.SyncDiagnosticsStore
import org.avmedia.gshockGoogleSync.services.NotificationMonitorService
import org.avmedia.gshockGoogleSync.services.LocationProvider
import org.avmedia.gshockGoogleSync.services.MissionLogRouteService
import org.avmedia.gshockGoogleSync.utils.ActivityProvider
import org.avmedia.gshockGoogleSync.utils.Utils
import org.avmedia.gshockGoogleSync.ui.common.AppSnackbar
import org.avmedia.gshockapi.AppNotification
import org.avmedia.gshockapi.EventAction
import org.avmedia.gshockapi.ProgressEvents
import org.avmedia.gshockapi.model.StepCounterData
import org.avmedia.gshockapi.model.LocationIndicatorCommand
import org.avmedia.gshockapi.model.LocationIndicatorFailure
import org.avmedia.gshockapi.protocols.GgB100ProtocolPackets.MissionLogState.Command
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainEventHandler(
    private val context: GShockApplication,
    private val repository: GShockRepository,
    private val screenManager: IScreenManager,
    private val missionLogStore: MissionLogStore,
    private val missionLogRouteStore: MissionLogRouteStore,
    private val stepCounterStore: StepCounterStore,
    private val locationTargetStore: LocationTargetStore,
    private val syncDiagnosticsStore: SyncDiagnosticsStore,
) {
    fun setupEventSubscription() {
        val eventActions = arrayOf(
            EventAction("ConnectionSetupComplete") {},
            EventAction("WatchInitializationCompleted") { handleWatchInitialization() },
            EventAction("ConnectionFailed") { handleConnectionFailure() },
            EventAction("Error") { handleError() },
            EventAction("WaitForConnection") { handleWaitForConnection() },
            EventAction("Disconnect") { handleDisconnect() },
            EventAction("HomeTimeUpdated") { handleHomeTimeUpdated() },
            EventAction("RunActions") { handleRunAction() },
            EventAction("AppNotification") { handleAppNotification() },
            EventAction("LocationServicesDisabled") { handleLocationServicesDisabled() },
            EventAction("StepCounterDataReceived") { handleStepCounterData() },
            EventAction("LocationIndicatorCommandReceived") {
                val command = ProgressEvents.getPayload("LocationIndicatorCommandReceived")
                    as? LocationIndicatorCommand ?: return@EventAction
                handleLocationIndicatorCommand(command, showResult = false)
            },
        )

        ProgressEvents.runEventActions(Utils.AppHashCode(), eventActions)
    }

    private fun handleStepCounterData() {
        val steps = ProgressEvents.getPayload("StepCounterDataReceived") as? StepCounterData ?: return
        runCatching { stepCounterStore.save(steps) }
            .onSuccess {
                Timber.i("Step count synchronized: ${steps.currentDaySteps}")
                syncDiagnosticsStore.record(
                    "STEP_SYNC",
                    "Step data received: ${steps.currentDaySteps ?: "unavailable"}",
                )
            }
            .onFailure { error -> Timber.e(error, "Step count could not be persisted") }
    }

    private fun handleWatchInitialization() {
        syncDiagnosticsStore.record("CONNECTION", connectionReasonLabel())
        if (repository.isLocationIndicatorConnection()) {
            handleLocationIndicatorConnection()
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

    private fun connectionReasonLabel(): String = when {
        repository.isAutoTimeStarted() -> "Automatic scheduled connection (reason 0x03)"
        repository.isMissionLogConnection() -> "Mission Log connection (reason 0x08)"
        repository.isLocationIndicatorConnection() -> "Location command connection (reason 0x07)"
        repository.isActionButtonPressed() -> "Application connection (reason 0x04)"
        repository.isNormalButtonPressed() -> "Manual connection"
        repository.isAlwaysConnectedConnectionPressed() -> "Always-connected request"
        repository.isFindPhoneButtonPressed() -> "Find-phone request"
        else -> "Unknown connection reason"
    }

    private fun handleHomeTimeUpdated() {
        syncDiagnosticsStore.record("TIME_SYNC", connectionReasonLabel())
    }

    private val locationIndicatorMutex = Mutex()

    private fun handleLocationIndicatorConnection() {
        CoroutineScope(Dispatchers.IO).launch {
            if (!locationIndicatorMutex.tryLock()) {
                Timber.d("Location command: ignoring overlapping initial request")
                return@launch
            }
            try {
                processLocationIndicatorCommand(repository.requestLocationIndicatorCommand(), true)
            } catch (error: Exception) {
                Timber.e(error, "Location command transfer failed")
                AppSnackbar("Location command failed: ${error.message ?: "unknown error"}")
            } finally {
                locationIndicatorMutex.unlock()
            }
        }
    }

    private fun handleLocationIndicatorCommand(
        command: LocationIndicatorCommand,
        showResult: Boolean,
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            if (!locationIndicatorMutex.tryLock()) {
                Timber.d("Location command: ignoring overlapping ${command.name} request")
                return@launch
            }
            try {
                processLocationIndicatorCommand(command, showResult)
            } catch (error: Exception) {
                Timber.e(error, "Location command ${command.name} failed")
                if (showResult) AppSnackbar("Location command failed: ${error.message ?: "unknown error"}")
            } finally {
                locationIndicatorMutex.unlock()
            }
        }
    }

    private suspend fun processLocationIndicatorCommand(
        command: LocationIndicatorCommand,
        showResult: Boolean,
    ) {
        when (command) {
            LocationIndicatorCommand.SAVE_CURRENT_LOCATION -> saveLocationMemory()
            LocationIndicatorCommand.DELETE_SAVED_LOCATION -> clearLocationMemory()
            LocationIndicatorCommand.CALCULATE_DISTANCE_AND_BEARING ->
                calculateLocationIndicator(showResult)
        }
    }

    private suspend fun saveLocationMemory() {
        val current = LocationProvider.getFreshLocation(context)
        if (current == null) {
            repository.respondLocationIndicator(
                LocationIndicatorCommand.SAVE_CURRENT_LOCATION,
                LocationIndicatorFailure.CURRENT_LOCATION_UNAVAILABLE.code,
            )
            AppSnackbar("Location Memory failed: fresh phone location is unavailable")
            return
        }

        val saveResult = runCatching {
            locationTargetStore.save(current.latitude, current.longitude)
        }
        val resultCode = if (saveResult.isSuccess) 0 else LocationIndicatorFailure.UNKNOWN.code
        repository.respondLocationIndicator(LocationIndicatorCommand.SAVE_CURRENT_LOCATION, resultCode)
        saveResult
            .onSuccess {
                Timber.i("Location Memory saved from watch request")
                AppSnackbar("Location Memory saved")
            }
            .onFailure { error ->
                Timber.e(error, "Location Memory could not be persisted")
                AppSnackbar("Location Memory could not be saved")
            }
    }

    private suspend fun clearLocationMemory() {
        val clearResult = runCatching { locationTargetStore.clear() }
        val resultCode = if (clearResult.isSuccess) 0 else LocationIndicatorFailure.UNKNOWN.code
        repository.respondLocationIndicator(LocationIndicatorCommand.DELETE_SAVED_LOCATION, resultCode)
        clearResult
            .onSuccess {
                Timber.i("Location Memory cleared from watch request")
                AppSnackbar("Location Memory cleared")
            }
            .onFailure { error ->
                Timber.e(error, "Location Memory could not be cleared")
                AppSnackbar("Location Memory could not be cleared")
            }
    }

    private suspend fun calculateLocationIndicator(showResult: Boolean) {
        val target = locationTargetStore.target.value
        if (target == null) {
            repository.respondLocationIndicator(
                LocationIndicatorCommand.CALCULATE_DISTANCE_AND_BEARING,
                LocationIndicatorFailure.NO_SAVED_DESTINATION.code,
            )
            if (showResult) AppSnackbar("Location Indicator failed: no destination is saved")
            return
        }

        val current = LocationProvider.getFreshLocation(context)
        if (current == null) {
            repository.respondLocationIndicator(
                LocationIndicatorCommand.CALCULATE_DISTANCE_AND_BEARING,
                LocationIndicatorFailure.CURRENT_LOCATION_UNAVAILABLE.code,
            )
            if (showResult) {
                AppSnackbar("Location Indicator failed: fresh phone location is unavailable")
            }
            return
        }

        val result = LocationIndicatorCalculator.calculate(
            currentLatitude = current.latitude,
            currentLongitude = current.longitude,
            targetLatitude = target.latitude,
            targetLongitude = target.longitude,
        )
        repository.respondLocationIndicator(
            LocationIndicatorCommand.CALCULATE_DISTANCE_AND_BEARING,
            resultCode = 0,
            distanceMetres = result.distanceMetres,
            bearingDegrees = result.bearingDegrees,
        )
        Timber.i("Location Indicator complete: ${result.distanceMetres}m at ${result.bearingDegrees}deg")
        if (showResult) {
            AppSnackbar("Location Indicator: ${result.distanceMetres} m at ${result.bearingDegrees}°")
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
                val routeResult = runCatching {
                    when (missionLog.state.command) {
                        Command.START -> {
                            missionLogRouteStore.begin()
                            runCatching { MissionLogRouteService.start(context) }
                                .getOrElse { error ->
                                    missionLogRouteStore.finish()
                                    throw error
                                }
                            null
                        }
                        Command.CONTINUE -> {
                            missionLogRouteStore.resumeOrBegin()
                            MissionLogRouteService.start(context)
                            null
                        }
                        Command.STOP -> {
                            missionLogRouteStore.finish().also { MissionLogRouteService.stop(context) }
                        }
                        else -> null
                    }
                }
                routeResult.onFailure { error ->
                    Timber.e(error, "Mission Log GPS route state could not be updated")
                }
                val saveResult = runCatching {
                    missionLogStore.save(missionLog, routeResult.getOrNull())
                }
                saveResult.onFailure { error ->
                    Timber.e(error, "Mission Log history could not be saved")
                }
                Timber.i(
                    "Mission Log complete: command=${missionLog.state.command}, " +
                        "altitude=${missionLog.altitudeData.size}B, " +
                        "exercise=${missionLog.exerciseData.size}B, " +
                        "route=${routeResult.getOrNull()?.points?.size ?: 0} points",
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
