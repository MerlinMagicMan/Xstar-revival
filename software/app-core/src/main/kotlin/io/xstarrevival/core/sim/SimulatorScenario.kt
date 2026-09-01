package io.xstarrevival.core.sim

import io.xstarrevival.core.model.CellState
import io.xstarrevival.core.model.ConnectionState
import io.xstarrevival.core.model.Severity
import io.xstarrevival.core.model.WarningState
import io.xstarrevival.core.model.XStarState

enum class SimulatorScenarioCategory(val label: String) {
    NORMAL("Normal"),
    NAVIGATION("Navigation"),
    COMMUNICATIONS("Communications"),
    BATTERY("Battery"),
    MISSION("Mission")
}

enum class SimulatorScenario(
    val category: SimulatorScenarioCategory,
    val label: String
) {
    NORMAL_FLIGHT(SimulatorScenarioCategory.NORMAL, "Normal Flight"),
    LONG_FLIGHT(SimulatorScenarioCategory.NORMAL, "Long Flight"),
    MISSION_FLIGHT(SimulatorScenarioCategory.NORMAL, "Mission Flight"),

    GPS_DEGRADED(SimulatorScenarioCategory.NAVIGATION, "GPS Degraded"),
    GPS_LOST(SimulatorScenarioCategory.NAVIGATION, "GPS Lost"),
    HOME_UNAVAILABLE(SimulatorScenarioCategory.NAVIGATION, "Home Point Unavailable"),
    COMPASS_WARNING(SimulatorScenarioCategory.NAVIGATION, "Compass Warning"),
    COMPASS_FAILURE(SimulatorScenarioCategory.NAVIGATION, "Compass Failure"),

    WEAK_RC(SimulatorScenarioCategory.COMMUNICATIONS, "Weak RC"),
    RC_LINK_LOSS(SimulatorScenarioCategory.COMMUNICATIONS, "RC Link Loss"),
    RC_RECOVERY(SimulatorScenarioCategory.COMMUNICATIONS, "RC Recovery"),
    WEAK_HD_LINK(SimulatorScenarioCategory.COMMUNICATIONS, "Weak HD Link"),
    VIDEO_LOSS(SimulatorScenarioCategory.COMMUNICATIONS, "Video Loss"),
    VIDEO_RECOVERY(SimulatorScenarioCategory.COMMUNICATIONS, "Video Recovery"),
    COMPLETE_LINK_LOSS(SimulatorScenarioCategory.COMMUNICATIONS, "Complete Aircraft Link Loss"),

    LOW_BATTERY(SimulatorScenarioCategory.BATTERY, "Low Battery"),
    CRITICAL_BATTERY(SimulatorScenarioCategory.BATTERY, "Critical Battery"),
    HIGH_TEMPERATURE(SimulatorScenarioCategory.BATTERY, "High Temperature"),
    CELL_IMBALANCE(SimulatorScenarioCategory.BATTERY, "Excessive Cell Imbalance"),
    DEGRADED_BATTERY(SimulatorScenarioCategory.BATTERY, "Weak / Degraded Battery"),
    FORCED_LANDING(SimulatorScenarioCategory.BATTERY, "Forced Landing"),

    WAYPOINT_FAILURE(SimulatorScenarioCategory.MISSION, "Waypoint Failure"),
    MISSION_PAUSE(SimulatorScenarioCategory.MISSION, "Mission Pause"),
    MISSION_ABORT(SimulatorScenarioCategory.MISSION, "Mission Abort"),
    RTH_DURING_MISSION(SimulatorScenarioCategory.MISSION, "RTH During Mission"),
    CONNECTION_LOSS_DURING_MISSION(SimulatorScenarioCategory.MISSION, "Connection Loss During Mission")
}

/** Deterministic normalized-state overlay used only by the software simulator. */
object SimulatorScenarioApplier {
    fun apply(rawBase: XStarState, scenario: SimulatorScenario): XStarState {
        val base = rawBase.copy(
            warnings = rawBase.warnings.filterNot { it.id.startsWith("sim-") },
            diagnostics = rawBase.diagnostics.copy(
                notes = rawBase.diagnostics.notes.filterNot {
                    it.startsWith("Scenario:") || it == "Long-flight profile" || it == "Mission-flight profile"
                }
            )
        )
        val affected = when (scenario) {
            SimulatorScenario.NORMAL_FLIGHT -> base
            SimulatorScenario.LONG_FLIGHT -> base.copy(
                battery = batteryAt(base, percent = 55),
                diagnostics = base.diagnostics.withScenarioNote("Long-flight profile")
            )
            SimulatorScenario.MISSION_FLIGHT -> base.copy(
                diagnostics = base.diagnostics.withScenarioNote("Mission-flight profile")
            )

            SimulatorScenario.GPS_DEGRADED -> base.copy(
                navigation = base.navigation.copy(satellites = 5, gpsFix = "DEGRADED"),
                warnings = base.warnings + warning("sim-gps-degraded", Severity.ADVISORY, "GPS signal degraded — smart flight unavailable")
            )
            SimulatorScenario.GPS_LOST -> base.copy(
                aircraft = base.aircraft.copy(flightMode = "ATTI"),
                navigation = base.navigation.copy(latitudeDeg = null, longitudeDeg = null, satellites = 0, gpsFix = "NO FIX"),
                warnings = base.warnings + warning("sim-gps-lost", Severity.CRITICAL, "GPS position lost — position accuracy unavailable")
            )
            SimulatorScenario.HOME_UNAVAILABLE -> base.copy(
                navigation = base.navigation.copy(homeLatitudeDeg = null, homeLongitudeDeg = null),
                warnings = base.warnings + warning("sim-home-unavailable", Severity.WARNING, "Home Point unavailable — RTH disabled")
            )
            SimulatorScenario.COMPASS_WARNING -> base.copy(
                warnings = base.warnings + warning("sim-compass-warning", Severity.WARNING, "Compass interference detected")
            )
            SimulatorScenario.COMPASS_FAILURE -> base.copy(
                aircraft = base.aircraft.copy(flightMode = "ATTI"),
                attitude = base.attitude.copy(yawDeg = null),
                warnings = base.warnings + warning("sim-compass-failure", Severity.CRITICAL, "Compass unavailable — heading is unknown")
            )

            SimulatorScenario.WEAK_RC -> base.copy(
                remote = base.remote.copy(signalPercent = 15),
                warnings = base.warnings + warning("sim-weak-rc", Severity.ADVISORY, "Remote-controller link is weak")
            )
            SimulatorScenario.RC_LINK_LOSS -> base.copy(
                remote = base.remote.copy(connected = false, signalPercent = 0),
                warnings = base.warnings + warning("sim-rc-loss", Severity.CRITICAL, "Remote-controller link lost — failsafe expected")
            )
            SimulatorScenario.RC_RECOVERY -> base.copy(
                remote = base.remote.copy(connected = true, signalPercent = 72),
                warnings = base.warnings + warning("sim-rc-recovery", Severity.INFO, "Remote-controller link recovered")
            )
            SimulatorScenario.WEAK_HD_LINK -> base.copy(
                remote = base.remote.copy(imageSignalPercent = 12),
                warnings = base.warnings + warning("sim-weak-hd", Severity.ADVISORY, "HD video link is weak")
            )
            SimulatorScenario.VIDEO_LOSS -> base.copy(
                camera = base.camera.copy(video = base.camera.video.copy(receiving = false)),
                warnings = base.warnings + warning("sim-video-loss", Severity.WARNING, "Video link lost — telemetry remains connected")
            )
            SimulatorScenario.VIDEO_RECOVERY -> base.copy(
                camera = base.camera.copy(video = base.camera.video.copy(receiving = true)),
                warnings = base.warnings + warning("sim-video-recovery", Severity.INFO, "Video link recovered")
            )
            SimulatorScenario.COMPLETE_LINK_LOSS -> base.copy(
                connection = ConnectionState.Disconnected,
                warnings = base.warnings + warning("sim-link-loss", Severity.CRITICAL, "Aircraft link lost — last known telemetry retained")
            )

            SimulatorScenario.LOW_BATTERY -> base.copy(
                battery = batteryAt(base, percent = 20),
                warnings = base.warnings + warning("sim-low-battery", Severity.WARNING, "Low battery — return or land soon")
            )
            SimulatorScenario.CRITICAL_BATTERY -> base.copy(
                battery = batteryAt(base, percent = 8),
                warnings = base.warnings + warning("sim-critical-battery", Severity.CRITICAL, "Critical battery — land now")
            )
            SimulatorScenario.HIGH_TEMPERATURE -> base.copy(
                battery = base.battery.copy(temperatureC = 65.0),
                warnings = base.warnings + warning("sim-high-temperature", Severity.CRITICAL, "Battery temperature critically high")
            )
            SimulatorScenario.CELL_IMBALANCE -> base.copy(
                battery = base.battery.copy(
                    packVoltageV = 16.18,
                    cells = listOf(CellState(1, 4.10), CellState(2, 4.09), CellState(3, 3.91), CellState(4, 4.08))
                ),
                warnings = base.warnings + warning("sim-cell-imbalance", Severity.CRITICAL, "Excessive battery cell imbalance")
            )
            SimulatorScenario.DEGRADED_BATTERY -> base.copy(
                battery = base.battery.copy(
                    percent = 78,
                    designCapacityMah = 4_900,
                    fullCapacityMah = 2_950,
                    remainingCapacityMah = 2_300,
                    dischargeCount = 240
                ),
                warnings = base.warnings + warning("sim-degraded-battery", Severity.ADVISORY, "Battery capacity is degraded")
            )
            SimulatorScenario.FORCED_LANDING -> base.copy(
                warnings = base.warnings + warning("sim-forced-landing", Severity.CRITICAL, "Forced landing active")
            )

            SimulatorScenario.WAYPOINT_FAILURE -> base.copy(
                aircraft = base.aircraft.copy(flightMode = "MISSION ERROR"),
                warnings = base.warnings + warning("sim-waypoint-failure", Severity.CRITICAL, "Waypoint execution failed")
            )
            SimulatorScenario.MISSION_PAUSE -> base.copy(
                aircraft = base.aircraft.copy(flightMode = "MISSION PAUSED"),
                warnings = base.warnings + warning("sim-mission-pause", Severity.WARNING, "Mission paused by scenario")
            )
            SimulatorScenario.MISSION_ABORT -> base.copy(
                aircraft = base.aircraft.copy(flightMode = "MISSION ABORTED"),
                warnings = base.warnings + warning("sim-mission-abort", Severity.WARNING, "Mission aborted by scenario")
            )
            SimulatorScenario.RTH_DURING_MISSION -> base.copy(
                aircraft = base.aircraft.copy(flightMode = "RETURN TO HOME"),
                warnings = base.warnings + warning("sim-mission-rth", Severity.WARNING, "Return-to-Home triggered during mission")
            )
            SimulatorScenario.CONNECTION_LOSS_DURING_MISSION -> base.copy(
                connection = ConnectionState.Disconnected,
                warnings = base.warnings + warning("sim-mission-link-loss", Severity.CRITICAL, "Aircraft link lost during mission")
            )
        }
        return affected.copy(
            diagnostics = affected.diagnostics.withScenarioNote("Scenario: ${scenario.label}")
        )
    }

    private fun batteryAt(state: XStarState, percent: Int): io.xstarrevival.core.model.BatteryState {
        val cellVoltage = 3.55 + 0.65 * percent / 100.0
        return state.battery.copy(
            percent = percent,
            packVoltageV = cellVoltage * 4,
            remainingCapacityMah = state.battery.fullCapacityMah?.let { it * percent / 100 },
            cells = List(4) { CellState(it + 1, cellVoltage + (it - 1.5) * 0.001) }
        )
    }

    private fun warning(id: String, severity: Severity, message: String) = WarningState(id, severity, message)

    private fun io.xstarrevival.core.model.DiagnosticsState.withScenarioNote(note: String) =
        copy(notes = (notes.filterNot { it.startsWith("Scenario:") } + note).distinct())
}
