package org.avmedia.gshockGoogleSync.data.missionlog

import java.time.Instant

object MissionLogGpx {
    fun encode(session: StoredMissionLogSession): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"Casio G-Shock Smart Sync\" ")
        append("xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        append("  <trk><name>GG-B100 Mission Log</name><trkseg>\n")
        session.routePoints.orEmpty().forEach { point ->
            append("    <trkpt lat=\"").append(point.latitude)
                .append("\" lon=\"").append(point.longitude).append("\">")
            if (session.routeAltitudeDatum == ROUTE_ALTITUDE_DATUM_ANDROID_MSL) {
                point.altitudeMetres?.let { append("<ele>").append(it).append("</ele>") }
            }
            append("<time>").append(Instant.ofEpochMilli(point.timestampEpochMillis)).append("</time>")
            append("</trkpt>\n")
        }
        append("  </trkseg></trk>\n</gpx>\n")
    }
}
