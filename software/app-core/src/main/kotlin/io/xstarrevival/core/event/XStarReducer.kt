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
            is XStarEvent.ComponentVersionsSnapshot -> state.copy(
                aircraft = state.aircraft.copy(componentVersions = state.aircraft.componentVersions + event.values)
            )
            is XStarEvent.ArmStateChanged -> state.copy(
                aircraft = state.aircraft.copy(armed = event.armed, flightMode = event.flightMode)
            )
            is XStarEvent.BatterySnapshot -> state.copy(
                battery = state.battery.copy(
                    percent = event.percent ?: state.battery.percent,
                    packVoltageV = event.packVoltageV ?: state.battery.packVoltageV,
                    currentA = event.currentA ?: state.battery.currentA,
                    temperatureC = event.temperatureC ?: state.battery.temperatureC,
                    designCapacityMah = event.designCapacityMah ?: state.battery.designCapacityMah,
                    fullCapacityMah = event.fullCapacityMah ?: state.battery.fullCapacityMah,
                    remainingCapacityMah = event.remainingCapacityMah ?: state.battery.remainingCapacityMah,
                    cells = event.cellVoltagesV.takeIf { it.isNotEmpty() }
                        ?.mapIndexed { index, voltage -> CellState(index + 1, voltage) }
                        ?: state.battery.cells,
                    dischargeCount = event.dischargeCount ?: state.battery.dischargeCount,
                    firmwareVersion = event.firmwareVersion ?: state.battery.firmwareVersion
                )
            )
            is XStarEvent.NavigationSnapshot -> state.copy(
                navigation = state.navigation.copy(
                    latitudeDeg = event.latitudeDeg ?: state.navigation.latitudeDeg,
                    longitudeDeg = event.longitudeDeg ?: state.navigation.longitudeDeg,
                    homeLatitudeDeg = event.homeLatitudeDeg ?: state.navigation.homeLatitudeDeg,
                    homeLongitudeDeg = event.homeLongitudeDeg ?: state.navigation.homeLongitudeDeg,
                    satellites = event.satellites ?: state.navigation.satellites,
                    gpsFix = event.gpsFix ?: state.navigation.gpsFix,
                    altitudeM = event.altitudeM ?: state.navigation.altitudeM,
                    groundSpeedMps = event.groundSpeedMps ?: state.navigation.groundSpeedMps,
                    verticalSpeedMps = event.verticalSpeedMps ?: state.navigation.verticalSpeedMps,
                    ultrasonicHeightM = event.ultrasonicHeightM ?: state.navigation.ultrasonicHeightM,
                    ultrasonicHeightRaw = event.ultrasonicHeightRaw ?: state.navigation.ultrasonicHeightRaw
                )
            )
            is XStarEvent.AttitudeSnapshot -> state.copy(
                attitude = state.attitude.copy(
                    rollDeg = event.rollDeg ?: state.attitude.rollDeg,
                    pitchDeg = event.pitchDeg ?: state.attitude.pitchDeg,
                    yawDeg = event.yawDeg ?: state.attitude.yawDeg
                )
            )
            is XStarEvent.RemoteSnapshot -> state.copy(
                remote = state.remote.copy(
                    connected = event.connected ?: state.remote.connected,
                    signalPercent = event.signalPercent ?: state.remote.signalPercent,
                    batteryPercent = event.batteryPercent ?: state.remote.batteryPercent,
                    imageSignalPercent = event.imageSignalPercent ?: state.remote.imageSignalPercent,
                    opaqueControlMenu = event.opaqueControlMenu ?: state.remote.opaqueControlMenu
                )
            )
            is XStarEvent.CameraSnapshot -> state.copy(
                camera = state.camera.copy(
                    connected = event.connected ?: state.camera.connected,
                    mode = event.mode ?: state.camera.mode,
                    recording = event.recording ?: state.camera.recording,
                    exposureMode = event.exposureMode ?: state.camera.exposureMode,
                    iso = event.iso ?: state.camera.iso,
                    shutter = event.shutter ?: state.camera.shutter
                )
            )
            is XStarEvent.GimbalSnapshot -> state.copy(
                gimbal = state.gimbal.copy(
                    pitchDeg = event.pitchDeg ?: state.gimbal.pitchDeg,
                    status = event.status ?: state.gimbal.status
                )
            )
            is XStarEvent.ImageLinkSnapshot -> state.copy(
                imageLink = state.imageLink.copy(
                    usbEnabled = event.usbEnabled ?: state.imageLink.usbEnabled,
                    rfFrequencyHz = event.rfFrequencyHz ?: state.imageLink.rfFrequencyHz,
                    rfSignalValue = event.rfSignalValue ?: state.imageLink.rfSignalValue
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
            is XStarEvent.VideoFrameReceived -> state.copy(
                camera = state.camera.copy(
                    video = state.camera.video.copy(
                        receiving = true,
                        codec = "H.264",
                        framesReceived = state.camera.video.framesReceived + 1
                    )
                ),
                diagnostics = state.diagnostics.copy(
                    counters = state.diagnostics.counters + mapOf(
                        "official_h264_frames" to ((state.diagnostics.counters["official_h264_frames"] ?: 0L) + 1),
                        "official_h264_bytes" to ((state.diagnostics.counters["official_h264_bytes"] ?: 0L) + event.validBytes),
                        "official_h264_keyframes" to (
                            (state.diagnostics.counters["official_h264_keyframes"] ?: 0L) + if (event.isKeyFrame) 1 else 0
                        )
                    )
                )
            )
            is XStarEvent.WarningsReplaced -> state.copy(warnings = event.warnings)
            is XStarEvent.WarningObserved -> state.copy(
                warnings = state.warnings.filterNot { it.id == event.warning.id } + event.warning
            )
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
