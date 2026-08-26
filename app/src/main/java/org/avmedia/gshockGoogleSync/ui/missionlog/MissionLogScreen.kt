package org.avmedia.gshockGoogleSync.ui.missionlog

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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.avmedia.gshockGoogleSync.R
import org.avmedia.gshockGoogleSync.data.missionlog.StoredMissionLogSession
import org.avmedia.gshockGoogleSync.theme.GShockSmartSyncTheme
import org.avmedia.gshockGoogleSync.ui.common.ScreenTitle
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun MissionLogScreen(viewModel: MissionLogViewModel = hiltViewModel()) {
    val sessions by viewModel.sessions.collectAsState()

    GShockSmartSyncTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                ScreenTitle(stringResource(R.string.mission_log), Modifier)
                if (sessions.isEmpty()) {
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
private fun MissionLogSessionCard(session: StoredMissionLogSession) {
    var expanded by rememberSaveable(session.id) { mutableStateOf(false) }
    val samples = session.altitudeSamples
    val minimum = samples.minOfOrNull { it.altitudeMetres }
    val maximum = samples.maxOfOrNull { it.altitudeMetres }

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

            if (samples.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                AltitudeChart(session)
            }

            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) stringResource(R.string.show_less) else stringResource(R.string.show_details))
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
