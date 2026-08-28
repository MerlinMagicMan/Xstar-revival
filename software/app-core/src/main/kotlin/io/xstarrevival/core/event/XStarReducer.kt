package io.xstarrevival.core.event

import io.xstarrevival.core.model.*

object XStarReducer {
    fun reduce(state: XStarState, event: XStarEvent, nowEpochMs: Long = System.currentTimeMillis()): XStarState {
        val next = when (event) {
            is XStarEvent.ConnectionChanged -> state.copy(connection = event.value)
            is XStarEvent.ProductIdentified -> state.copy(
                connection = when (val connection = state.connection) {
                    is ConnectionState.Connected -> connection.copy(product = event.name)
                    else -> connection
                },
                aircraft = state.aircraft.copy(
                    productName = event.name,
                    firmwareVersion = event.firmwareVersion
                )
            )
            is XStarEvent.ArmStateChanged -> state.copy(
                aircraft = state.aircraft.copy(armed = event.armed, flightMode = event.flightMode)
            )
            is XStarEvent.BatterySnapshot -> state.copy(
                battery = state.battery.copy(
                    percent = event.percent,
                    packVoltageV = event.packVoltageV,
                    currentA = event.currentA,
                    temperatureC = event.temperatureC,
                    designCapacityMah = event.designCapacityMah,
                    fullCapacityMah = event.fullCapacityMah,
                    remainingCapacityMah = event.remainingCapacityMah,
                    cells = event.cellVoltagesV.mapIndexed { index, voltage -> CellState(index + 1, voltage) }
                )
            )
            is XStarEvent.NavigationSnapshot -> state.copy(
                navigation = NavigationState(
                    latitudeDeg = event.latitudeDeg,
                    longitudeDeg = event.longitudeDeg,
                    homeLatitudeDeg = event.homeLatitudeDeg,
                    homeLongitudeDeg = event.homeLongitudeDeg,
                    satellites = event.satellites,
                    gpsFix = event.gpsFix,
                    altitudeM = event.altitudeM,
                    groundSpeedMps = event.groundSpeedMps,
                    verticalSpeedMps = event.verticalSpeedMps
                )
            )
            is XStarEvent.AttitudeSnapshot -> state.copy(
                attitude = AttitudeState(event.rollDeg, event.pitchDeg, event.yawDeg)
            )
            is XStarEvent.RemoteSnapshot -> state.copy(
                remote = RemoteState(event.connected, event.signalPercent, event.batteryPercent)
            )
            is XStarEvent.CameraSnapshot -> state.copy(
                camera = state.camera.copy(
                    connected = event.connected,
                    mode = event.mode,
                    recording = event.recording
                )
            )
            is XStarEvent.VideoSnapshot -> state.copy(
                camera = state.camera.copy(
                    video = VideoState(
                        receiving = event.receiving,
                        codec = event.codec,
                        width = event.width,
                        height = event.height,
                        framesReceived = event.framesReceived,
                        bitrateBps = event.bitrateBps
                    )
                )
            )
            is XStarEvent.WarningsReplaced -> state.copy(warnings = event.warnings)
            is XStarEvent.DiagnosticCounter -> state.copy(
                diagnostics = state.diagnostics.copy(
                    counters = state.diagnostics.counters + (event.key to event.value)
                )
            )
            is XStarEvent.DiagnosticNote -> state.copy(
                diagnostics = state.diagnostics.copy(notes = state.diagnostics.notes + event.value)
            )
        }
        return next.copy(diagnostics = next.diagnostics.copy(lastUpdateEpochMs = nowEpochMs))
    }
}
