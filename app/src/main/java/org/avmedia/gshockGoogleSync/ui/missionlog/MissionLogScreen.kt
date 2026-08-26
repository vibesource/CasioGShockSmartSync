package org.avmedia.gshockGoogleSync.ui.missionlog

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.data.missionlog.StoredMissionLogSession
import org.avmedia.gshockGoogleSync.data.missionlog.StoredRoutePoint
import org.avmedia.gshockGoogleSync.data.missionlog.MissionLogRouteMetrics
import org.avmedia.gshockGoogleSync.data.missionlog.ActiveMissionLogRoute
import org.avmedia.gshockGoogleSync.data.missionlog.MissionLogRouteProfile
import org.avmedia.gshockGoogleSync.data.missionlog.MissionLogGpx
import org.avmedia.gshockGoogleSync.data.missionlog.ROUTE_ALTITUDE_DATUM_ANDROID_MSL
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.io.File

@Composable
fun MissionLogScreen(viewModel: MissionLogViewModel = hiltViewModel()) {
    val sessions by viewModel.sessions.collectAsState()
    val recordingState by viewModel.recordingState.collectAsState()
    val routeProfile by viewModel.routeProfile.collectAsState()

    GShockSmartSyncTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                ScreenTitle(stringResource(R.string.mission_log), Modifier)
                if (sessions.isEmpty() && !recordingState.isActive) {
                    Text(
                        text = stringResource(R.string.no_mission_logs),
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (recordingState.isActive) {
                            item(key = "active-mission-log-route") {
                                ActiveMissionLogCard(recordingState, routeProfile)
                            }
                        }
                        items(sessions, key = { it.id }) { session ->
                            MissionLogSessionCard(session)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveMissionLogCard(
    state: ActiveMissionLogRoute,
    profile: MissionLogRouteProfile,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Mission Log recording", style = MaterialTheme.typography.titleMedium)
            Text("${profile.displayName()} GPS · ${state.pointCount} points")
            state.startedAtEpochMillis?.let { startedAt ->
                Text(
                    "Started ${formatCapturedAt(startedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                "Stop Mission Log from the watch to finalize this route.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun MissionLogSessionCard(session: StoredMissionLogSession) {
    val context = LocalContext.current
    var expanded by rememberSaveable(session.id) { mutableStateOf(false) }
    val samples = session.altitudeSamples
    val minimum = samples.minOfOrNull { it.altitudeMetres }
    val maximum = samples.maxOfOrNull { it.altitudeMetres }
    val exercise = session.exercise
    val route = session.routePoints.orEmpty()

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = commandLabel(session.command),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = formatCapturedAt(session.capturedAtEpochMillis),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(8.dp))
            val range = if (minimum != null && maximum != null) {
                " · $minimum–$maximum m"
            } else {
                ""
            }
            Text(
                text = "${samples.size} altitude samples$range",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${session.altitudePoints.size} watch-memory altitude points",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (route.isNotEmpty()) {
                Text(
                    text = "GPS route · ${route.size} points · ${formatDistance(MissionLogRouteMetrics.distanceMetres(route))}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            exercise?.currentDay?.let { currentDay ->
                val totals = buildList {
                    currentDay.steps?.let { add("$it steps") }
                    currentDay.exercise?.let { add("exercise $it") }
                }
                if (totals.isNotEmpty()) {
                    Text(
                        text = "Watch day total · ${totals.joinToString(" · ")}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (samples.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                AltitudeChart(session)
            }
            if (route.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                RouteTrace(route)
            }

            Row {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) stringResource(R.string.show_less) else stringResource(R.string.show_details))
                }
                if (route.isNotEmpty()) {
                    TextButton(onClick = {
                        runCatching { shareGpx(context, session) }
                            .onFailure {
                                Toast.makeText(context, "Could not export GPX", Toast.LENGTH_SHORT).show()
                            }
                    }) {
                        Text("Export GPX")
                    }
                }
            }

            if (expanded) {
                session.altitudeStartUtc?.let {
                    DetailLine("Altitude series", formatUtc(it))
                }
                session.watchTimestampUtc?.let {
                    DetailLine("Watch event", formatUtc(it))
                }
                DetailLine("Raw altitude", "${decodedSize(session.altitudeRawBase64)} bytes")
                DetailLine("Raw exercise", "${decodedSize(session.exerciseRawBase64)} bytes")
                exercise?.let { decoded ->
                    DetailLine(
                        "Populated step slots",
                        "${decoded.stepSlots.count { it != null }} of ${decoded.stepSlots.size}",
                    )
                    DetailLine(
                        "Populated exercise slots",
                        "${decoded.exerciseSlots.count { it != null }} of ${decoded.exerciseSlots.size}",
                    )
                }
                session.routeStartedAtEpochMillis?.let {
                    DetailLine("Route started", formatCapturedAt(it))
                }
                session.routeEndedAtEpochMillis?.let {
                    DetailLine("Route ended", formatCapturedAt(it))
                }
                if (route.isNotEmpty()) {
                    DetailLine("GPS points", route.size.toString())
                    DetailLine("Route distance", formatDistance(MissionLogRouteMetrics.distanceMetres(route)))
                    DetailLine(
                        "GPX elevation",
                        if (session.routeAltitudeDatum == ROUTE_ALTITUDE_DATUM_ANDROID_MSL) {
                            "Mean sea level"
                        } else {
                            "Omitted (unverified GPS datum)"
                        },
                    )
                }

                if (session.altitudePoints.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Watch altitude point memory (latest 14)", style = MaterialTheme.typography.titleSmall)
                    session.altitudePoints.forEach { point ->
                        Text(
                            text = "${formatUtc(point.timestampUtc)}  ·  ${point.altitudeMetres} m",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun shareGpx(context: android.content.Context, session: StoredMissionLogSession) {
    val directory = File(context.cacheDir, "mission-log-exports").apply { mkdirs() }
    val file = File(directory, "gg-b100-${session.capturedAtEpochMillis}.gpx")
    file.writeText(MissionLogGpx.encode(session))
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/gpx+xml"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Export Mission Log route"))
}

@Composable
private fun RouteTrace(points: List<StoredRoutePoint>) {
    val routeColor = MaterialTheme.colorScheme.primary
    val frameColor = MaterialTheme.colorScheme.outlineVariant
    val startColor = MaterialTheme.colorScheme.tertiary
    val projected = remember(points) {
        val meanLatitudeRadians = Math.toRadians(points.map { it.latitude }.average())
        points.map { point ->
            point.longitude * kotlin.math.cos(meanLatitudeRadians) to point.latitude
        }
    }
    val minimumX = projected.minOf { it.first }
    val maximumX = projected.maxOf { it.first }
    val minimumY = projected.minOf { it.second }
    val maximumY = projected.maxOf { it.second }
    val rangeX = (maximumX - minimumX).coerceAtLeast(0.000001)
    val rangeY = (maximumY - minimumY).coerceAtLeast(0.000001)

    Text("GPS route", style = MaterialTheme.typography.labelSmall)
    Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
        drawRect(color = frameColor, style = Stroke(width = 1.dp.toPx()))
        val padding = 10.dp.toPx()
        val width = (size.width - 2 * padding).coerceAtLeast(1f)
        val height = (size.height - 2 * padding).coerceAtLeast(1f)
        val offsets = projected.map { (x, y) ->
            Offset(
                x = padding + ((x - minimumX) / rangeX * width).toFloat(),
                y = padding + ((maximumY - y) / rangeY * height).toFloat(),
            )
        }
        if (offsets.size == 1) {
            drawCircle(startColor, radius = 5.dp.toPx(), center = offsets.first())
        } else {
            val path = Path().apply {
                moveTo(offsets.first().x, offsets.first().y)
                offsets.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, routeColor, style = Stroke(width = 3.dp.toPx()))
            drawCircle(startColor, radius = 5.dp.toPx(), center = offsets.first())
            drawCircle(routeColor, radius = 5.dp.toPx(), center = offsets.last())
        }
    }
}

@Composable
private fun AltitudeChart(session: StoredMissionLogSession) {
    val samples = session.altitudeSamples
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val values = remember(samples) { samples.map { it.altitudeMetres } }
    val minimum = values.minOrNull() ?: return
    val maximum = values.maxOrNull() ?: return
    val observedRange = maximum - minimum
    val displayRange = observedRange.coerceAtLeast(4).toFloat()
    val lowerBound = minimum - (displayRange - observedRange) / 2f

    Text("$maximum m", style = MaterialTheme.typography.labelSmall)

    Canvas(modifier = Modifier.fillMaxWidth().height(90.dp)) {
        drawRect(color = gridColor, style = Stroke(width = 1.dp.toPx()))
        if (values.size == 1) {
            drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(size.width / 2, size.height / 2))
            return@Canvas
        }
        val points = values.mapIndexed { index, altitude ->
            Offset(
                x = size.width * index / (values.lastIndex.toFloat()),
                y = size.height - size.height * (altitude - lowerBound) / displayRange,
            )
        }
        points.zipWithNext().forEach { (start, end) ->
            drawLine(lineColor, start, end, strokeWidth = 2.dp.toPx())
        }
        points.forEach { point -> drawCircle(lineColor, 3.dp.toPx(), point) }
    }
    Text("$minimum m", style = MaterialTheme.typography.labelSmall)
    val firstTime = samples.firstOrNull()?.timestampUtc
    val lastTime = samples.lastOrNull()?.timestampUtc
    if (firstTime != null && lastTime != null) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatUtc(firstTime, "HH:mm"), style = MaterialTheme.typography.labelSmall)
            Text(formatUtc(lastTime, "HH:mm"), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

private fun commandLabel(command: String): String = when (command) {
    "START" -> "Mission Log started"
    "STOP" -> "Mission Log stopped"
    "CONTINUE" -> "Mission Log continued"
    else -> "Mission Log"
}

private fun formatCapturedAt(epochMillis: Long): String =
    DateTimeFormatter.ofPattern("dd MMM, HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMillis))

private fun formatUtc(value: String, pattern: String = "dd MMM yyyy, HH:mm:ss"): String =
    runCatching {
        LocalDateTime.parse(value)
            .atOffset(ZoneOffset.UTC)
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern(pattern))
    }.getOrDefault(value)

private fun decodedSize(base64: String): Int = (base64.length * 3 / 4) - base64.takeLast(2).count { it == '=' }

private fun formatDistance(distanceMetres: Double): String =
    if (distanceMetres < 1_000) {
        "${distanceMetres.toInt()} m"
    } else {
        "%.2f km".format(distanceMetres / 1_000)
    }

private fun MissionLogRouteProfile.displayName(): String = when (this) {
    MissionLogRouteProfile.DETAILED -> "Detailed"
    MissionLogRouteProfile.BALANCED -> "Balanced"
    MissionLogRouteProfile.BATTERY_SAVER -> "Battery saver"
}
