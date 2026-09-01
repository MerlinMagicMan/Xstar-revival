package io.xstarrevival.core.sim

import io.xstarrevival.core.model.XStarState

const val SIMULATOR_BRIDGE_PROTOCOL_VERSION = 1
const val SIMULATOR_BRIDGE_UDP_PORT = 46_000

enum class SimulatorViewMode { FPV, CHASE }

/** Versioned, simulator-only telemetry envelope consumed by desktop and Unreal visualizers. */
object SimulatorBridgeProtocol {
    fun telemetryJson(
        state: XStarState,
        sequence: Long,
        emittedAtEpochMs: Long,
        viewMode: SimulatorViewMode = SimulatorViewMode.FPV
    ): String = buildString(1_024) {
        append('{')
        field("protocol", "xstar-simulator")
        comma()
        field("version", SIMULATOR_BRIDGE_PROTOCOL_VERSION)
        comma()
        field("type", "telemetry")
        comma()
        field("simulated", true)
        comma()
        field("sequence", sequence)
        comma()
        field("emittedAtEpochMs", emittedAtEpochMs)
        comma()
        append("\"aircraft\":{")
        field("phase", state.aircraft.flightMode)
        comma()
        field("armed", state.aircraft.armed)
        comma()
        field("latitudeDeg", state.navigation.latitudeDeg)
        comma()
        field("longitudeDeg", state.navigation.longitudeDeg)
        comma()
        field("homeLatitudeDeg", state.navigation.homeLatitudeDeg)
        comma()
        field("homeLongitudeDeg", state.navigation.homeLongitudeDeg)
        comma()
        field("altitudeM", state.navigation.altitudeM)
        comma()
        field("groundSpeedMps", state.navigation.groundSpeedMps)
        comma()
        field("verticalSpeedMps", state.navigation.verticalSpeedMps)
        comma()
        field("rollDeg", state.attitude.rollDeg)
        comma()
        field("pitchDeg", state.attitude.pitchDeg)
        comma()
        field("yawDeg", state.attitude.yawDeg)
        append('}')
        comma()
        append("\"controller\":{")
        field("throttle", state.remote.throttleInput)
        comma()
        field("yaw", state.remote.yawInput)
        comma()
        field("pitch", state.remote.pitchInput)
        comma()
        field("roll", state.remote.rollInput)
        comma()
        field("gimbal", state.remote.gimbalWheelInput)
        append('}')
        comma()
        append("\"battery\":{")
        field("percent", state.battery.percent)
        comma()
        field("voltageV", state.battery.packVoltageV)
        comma()
        field("currentA", state.battery.currentA)
        comma()
        field("temperatureC", state.battery.temperatureC)
        append('}')
        comma()
        append("\"camera\":{")
        field("mode", state.camera.mode)
        comma()
        field("viewMode", viewMode.name)
        comma()
        field("recording", state.camera.recording)
        comma()
        field("gimbalPitchDeg", state.gimbal.pitchDeg)
        comma()
        field("photosTaken", state.camera.photosTaken)
        append('}')
        comma()
        append("\"warnings\":[")
        state.warnings.forEachIndexed { index, warning ->
            if (index > 0) comma()
            append('{')
            field("severity", warning.severity.name)
            comma()
            field("message", warning.message)
            append('}')
        }
        append(']')
        append('}')
    }

    private fun StringBuilder.field(name: String, value: String?) {
        quoted(name)
        append(':')
        if (value == null) append("null") else quoted(value)
    }

    private fun StringBuilder.field(name: String, value: Boolean?) {
        quoted(name)
        append(':')
        append(value?.toString() ?: "null")
    }

    private fun StringBuilder.field(name: String, value: Number?) {
        quoted(name)
        append(':')
        val finite = when (value) {
            is Double -> value.takeIf(Double::isFinite)
            is Float -> value.takeIf(Float::isFinite)
            else -> value
        }
        append(finite?.toString() ?: "null")
    }

    private fun StringBuilder.quoted(value: String) {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
            }
        }
        append('"')
    }

    private fun StringBuilder.comma() {
        append(',')
    }
}
