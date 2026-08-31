package io.xstarrevival.core.groundstation

import io.xstarrevival.core.model.ConnectionState
import io.xstarrevival.core.model.Severity
import io.xstarrevival.core.model.XStarState

enum class PreflightLevel { PASS, ADVISORY, BLOCKER, UNAVAILABLE }

data class PreflightCheck(
    val id: String,
    val label: String,
    val level: PreflightLevel,
    val detail: String? = null
)

data class PreflightReport(
    val checks: List<PreflightCheck>,
    val readyToFly: Boolean = checks.none { it.level == PreflightLevel.BLOCKER } &&
        REQUIRED_CHECKS.all { requiredId ->
            checks.any { it.id == requiredId && it.level != PreflightLevel.UNAVAILABLE }
        }
)

private val REQUIRED_CHECKS = setOf("connection", "gps", "rc", "battery", "warnings")

object PreflightEvaluator {
    fun evaluate(state: XStarState): PreflightReport {
        val checks = mutableListOf<PreflightCheck>()
        val connected = state.connection is ConnectionState.Connected
        checks += check("connection", "Aircraft connection", connected, "Connected", "No aircraft link")

        val satellites = state.navigation.satellites
        checks += when {
            satellites == null -> PreflightCheck("gps", "GPS / GLONASS", PreflightLevel.UNAVAILABLE, "No satellite telemetry")
            satellites >= 10 -> PreflightCheck("gps", "GPS / GLONASS", PreflightLevel.PASS, "$satellites satellites")
            satellites >= 6 -> PreflightCheck("gps", "GPS / GLONASS", PreflightLevel.ADVISORY, "$satellites satellites")
            else -> PreflightCheck("gps", "GPS / GLONASS", PreflightLevel.BLOCKER, "$satellites satellites")
        }

        checks += when (state.remote.connected) {
            true -> PreflightCheck("rc", "Remote controller", PreflightLevel.PASS, state.remote.signalPercent?.let { "$it% signal" })
            false -> PreflightCheck("rc", "Remote controller", PreflightLevel.BLOCKER, "Disconnected")
            null -> PreflightCheck("rc", "Remote controller", PreflightLevel.UNAVAILABLE, "State unavailable")
        }

        val batteryPercent = state.battery.percent
        checks += when {
            batteryPercent == null -> PreflightCheck("battery", "Flight battery", PreflightLevel.UNAVAILABLE, "No battery telemetry")
            batteryPercent <= 10 -> PreflightCheck("battery", "Flight battery", PreflightLevel.BLOCKER, "$batteryPercent%")
            batteryPercent <= 25 -> PreflightCheck("battery", "Flight battery", PreflightLevel.ADVISORY, "$batteryPercent%")
            else -> PreflightCheck("battery", "Flight battery", PreflightLevel.PASS, "$batteryPercent%")
        }

        val delta = state.battery.cellDeltaV
        checks += when {
            delta == null -> PreflightCheck("cells", "Battery cells", PreflightLevel.UNAVAILABLE, "Cell telemetry unavailable")
            delta >= 0.12 -> PreflightCheck("cells", "Battery cells", PreflightLevel.BLOCKER, "Δ %.3f V".format(delta))
            delta >= 0.05 -> PreflightCheck("cells", "Battery cells", PreflightLevel.ADVISORY, "Δ %.3f V".format(delta))
            else -> PreflightCheck("cells", "Battery cells", PreflightLevel.PASS, "Δ %.3f V".format(delta))
        }

        val temp = state.battery.temperatureC
        checks += when {
            temp == null -> PreflightCheck("battery_temp", "Battery temperature", PreflightLevel.UNAVAILABLE)
            temp >= 60.0 || temp <= -10.0 -> PreflightCheck("battery_temp", "Battery temperature", PreflightLevel.BLOCKER, "%.1f°C".format(temp))
            temp >= 50.0 || temp <= 5.0 -> PreflightCheck("battery_temp", "Battery temperature", PreflightLevel.ADVISORY, "%.1f°C".format(temp))
            else -> PreflightCheck("battery_temp", "Battery temperature", PreflightLevel.PASS, "%.1f°C".format(temp))
        }

        checks += when {
            state.camera.connected == true -> PreflightCheck("camera", "Camera", PreflightLevel.PASS, state.camera.mode)
            state.camera.connected == false -> PreflightCheck("camera", "Camera", PreflightLevel.ADVISORY, "Disconnected")
            else -> PreflightCheck("camera", "Camera", PreflightLevel.UNAVAILABLE)
        }

        val criticalWarnings = state.warnings.count { it.severity == Severity.CRITICAL }
        checks += if (criticalWarnings > 0) {
            PreflightCheck("warnings", "Aircraft warnings", PreflightLevel.BLOCKER, "$criticalWarnings critical")
        } else {
            PreflightCheck("warnings", "Aircraft warnings", PreflightLevel.PASS, "No critical warnings")
        }

        val homeReady = state.navigation.homeLatitudeDeg != null && state.navigation.homeLongitudeDeg != null
        checks += if (homeReady) PreflightCheck("home", "Home point", PreflightLevel.PASS, "Set")
        else PreflightCheck("home", "Home point", PreflightLevel.ADVISORY, "Not reported")

        return PreflightReport(checks)
    }

    private fun check(id: String, label: String, ok: Boolean, okDetail: String, badDetail: String) =
        PreflightCheck(id, label, if (ok) PreflightLevel.PASS else PreflightLevel.BLOCKER, if (ok) okDetail else badDetail)
}
