package org.avmedia.gshockGoogleSync.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import org.avmedia.gshockGoogleSync.MainActivity
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.data.missionlog.MissionLogRouteStore
import org.avmedia.gshockGoogleSync.data.missionlog.MissionLogRouteProfileStore
import org.avmedia.gshockGoogleSync.data.missionlog.MissionLogRouteProfile
import org.avmedia.gshockGoogleSync.data.missionlog.MissionLogRouteFilter
import org.avmedia.gshockGoogleSync.data.missionlog.StoredRoutePoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MissionLogRouteService : Service() {
    @Inject lateinit var routeStore: MissionLogRouteStore
    @Inject lateinit var profileStore: MissionLogRouteProfileStore

    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var receivingLocations = false
    private var recordingProfile = MissionLogRouteProfile.BALANCED
    private var lastAcceptedPoint: StoredRoutePoint? = null
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { location ->
                val normalized = LocationProvider.Location.fromAndroidLocation(location)
                val point = StoredRoutePoint(
                    timestampEpochMillis = normalized.timestampEpochMillis ?: location.time,
                    latitude = normalized.latitude,
                    longitude = normalized.longitude,
                    altitudeMetres = normalized.altitudeMetres,
                    accuracyMetres = normalized.horizontalAccuracyMetres,
                )
                if (!MissionLogRouteFilter.accepts(
                        candidate = point,
                        previous = lastAcceptedPoint,
                        maximumAccuracyMetres = recordingProfile.maximumAccuracyMetres,
                    )
                ) {
                    Timber.d("Mission Log GPS fix rejected: accuracy=${location.accuracy}m")
                    return@forEach
                }
                routeStore.add(point)
                lastAcceptedPoint = point
            }
            updateNotification()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!routeStore.isActive()) {
            stopSelf()
            return START_NOT_STICKY
        }
        recordingProfile = profileStore.profile.value
        promoteToForeground()
        requestLocationUpdates()
        return START_STICKY
    }

    override fun onDestroy() {
        if (receivingLocations) locationClient.removeLocationUpdates(locationCallback)
        receivingLocations = false
        super.onDestroy()
    }

    private fun promoteToForeground() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Mission Log route",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_watch_later_black_24dp)
            .setContentTitle("Mission Log active")
            .setContentText(
                "${recordingProfile.displayName()} · ${routeStore.recordingState.value.pointCount} GPS points",
            )
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun requestLocationUpdates() {
        if (receivingLocations) return
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted) {
            Timber.e("Mission Log route cannot start without precise location permission")
            stopSelf()
            return
        }
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            recordingProfile.intervalMillis,
        )
            .setMinUpdateIntervalMillis(recordingProfile.intervalMillis)
            .setMinUpdateDistanceMeters(recordingProfile.minimumDistanceMetres)
            .build()
        try {
            locationClient.requestLocationUpdates(request, locationCallback, mainLooper)
            receivingLocations = true
            Timber.i(
                "Mission Log GPS route recording started: ${recordingProfile.name}, " +
                    "interval=${recordingProfile.intervalMillis}ms, " +
                    "distance=${recordingProfile.minimumDistanceMetres}m",
            )
        } catch (error: SecurityException) {
            Timber.e(error, "Mission Log GPS route permission was rejected")
            stopSelf()
        }
    }

    companion object {
        private const val CHANNEL_ID = "mission_log_route"
        private const val NOTIFICATION_ID = 5594
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MissionLogRouteService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MissionLogRouteService::class.java))
        }
    }
}

private fun MissionLogRouteProfile.displayName(): String = when (this) {
    MissionLogRouteProfile.DETAILED -> "Detailed"
    MissionLogRouteProfile.BALANCED -> "Balanced"
    MissionLogRouteProfile.BATTERY_SAVER -> "Battery saver"
}
