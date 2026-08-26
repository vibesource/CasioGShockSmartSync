package org.avmedia.gshockGoogleSync

sealed class Screens(val route: String) {
    data object Time : Screens("Time")
    data object Alarms : Screens("Alarms")
    data object Events : Screens("Events")
    data object Actions : Screens("Actions")
    data object MissionLog : Screens("MissionLog")
    data object Settings : Screens("Settings")
}
