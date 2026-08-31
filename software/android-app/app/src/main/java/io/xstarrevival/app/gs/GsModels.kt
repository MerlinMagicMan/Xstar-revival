package io.xstarrevival.app.gs

import io.xstarrevival.core.model.ConnectionState
import io.xstarrevival.core.model.Severity
import io.xstarrevival.core.model.XStarState

enum class GsPage(val label: String, val glyph: String) {
    GARAGE("Garage", "◆"),
    COCKPIT("Fly", "✦"),
    MISSIONS("Missions", "⌖"),
    RECORDS("Flights", "≋"),
    MEDIA("Media", "▣"),
    AIRCRAFT("Aircraft", "◇"),
    SETTINGS("Settings", "⚙"),
    HELP("Academy", "?")
}

enum class GsReadiness { OFFLINE, CHECKING, READY, WARNING, CRITICAL }

data class GsReadinessState(val level: GsReadiness, val label: String)

fun XStarState.readiness(): GsReadinessState {
    val critical = warnings.any { it.severity == Severity.CRITICAL }
    val warning = warnings.any { it.severity == Severity.WARNING }
    val connected = connection is ConnectionState.Connected
    val sats = navigation.satellites ?: 0
    return when {
        critical -> GsReadinessState(GsReadiness.CRITICAL, "CRITICAL — ACTION REQUIRED")
        !connected -> GsReadinessState(GsReadiness.OFFLINE, "DISCONNECTED")
        warning -> GsReadinessState(GsReadiness.WARNING, "ATTENTION REQUIRED")
        sats >= 6 -> GsReadinessState(GsReadiness.READY, "READY TO FLY — GPS")
        else -> GsReadinessState(GsReadiness.CHECKING, "CHECKING AIRCRAFT")
    }
}

fun batteryAccent(percent: Int?) = when {
    percent == null -> GsColors.Muted
    percent <= 10 -> GsColors.Red
    percent <= 25 -> GsColors.Amber
    else -> GsColors.Green
}
