package io.xstarrevival.app.gs

import io.xstarrevival.core.model.ConnectionState
import io.xstarrevival.core.model.Severity
import io.xstarrevival.core.model.XStarState
import io.xstarrevival.core.groundstation.PreflightEvaluator
import io.xstarrevival.core.groundstation.PreflightLevel

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
    val connected = connection is ConnectionState.Connected
    if (!connected) return GsReadinessState(GsReadiness.OFFLINE, "DISCONNECTED")

    val report = PreflightEvaluator.evaluate(this)
    val critical = warnings.any { it.severity == Severity.CRITICAL }
    val blocking = report.checks.any { it.level == PreflightLevel.BLOCKER }
    val unavailable = report.checks.any {
        it.id in setOf("gps", "rc", "battery") && it.level == PreflightLevel.UNAVAILABLE
    }
    val advisory = warnings.any { it.severity == Severity.WARNING } ||
        report.checks.any { it.level == PreflightLevel.ADVISORY }
    return when {
        critical -> GsReadinessState(GsReadiness.CRITICAL, "CRITICAL — ACTION REQUIRED")
        blocking -> GsReadinessState(GsReadiness.CRITICAL, "PREFLIGHT BLOCKED")
        unavailable -> GsReadinessState(GsReadiness.CHECKING, "WAITING FOR PREFLIGHT DATA")
        advisory -> GsReadinessState(GsReadiness.WARNING, "PREFLIGHT ADVISORY")
        report.readyToFly -> GsReadinessState(GsReadiness.READY, "READY TO FLY — GPS")
        else -> GsReadinessState(GsReadiness.CHECKING, "CHECKING AIRCRAFT")
    }
}

fun batteryAccent(percent: Int?) = when {
    percent == null -> GsColors.Muted
    percent <= 10 -> GsColors.Red
    percent <= 25 -> GsColors.Amber
    else -> GsColors.Green
}
