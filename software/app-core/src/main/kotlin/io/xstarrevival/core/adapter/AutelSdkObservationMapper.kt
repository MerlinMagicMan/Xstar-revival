package io.xstarrevival.core.adapter

import io.xstarrevival.core.event.XStarEvent
import io.xstarrevival.core.model.ConnectionState
import io.xstarrevival.core.model.Severity
import io.xstarrevival.core.model.WarningState
import kotlin.math.PI

object AutelSdkObservationMapper {
    fun map(observation: AutelSdkObservation, transportDescription: String): List<XStarEvent> = when (observation) {
        is AutelSdkObservation.ProductConnected -> buildList {
            add(
                XStarEvent.ConnectionChanged(
                    ConnectionState.Connected(transportDescription, observation.productName)
                )
            )
            add(XStarEvent.ProductIdentified(observation.productName, observation.firmwareVersion))
            if (observation.availableComponents.isNotEmpty()) {
                add(
                    XStarEvent.DiagnosticNote(
                        "Official SDK components: " + observation.availableComponents
                            .map { it.name }
                            .sorted()
                            .joinToString(",")
                    )
                )
            }
        }

        AutelSdkObservation.ProductDisconnected -> listOf(
            XStarEvent.VideoSnapshot(receiving = false),
            XStarEvent.ConnectionChanged(ConnectionState.Disconnected)
        )

        is AutelSdkObservation.ComponentVersions -> listOf(
            XStarEvent.ComponentVersionsSnapshot(observation.values.toMap())
        )

        is AutelSdkObservation.Battery -> listOf(
            XStarEvent.BatterySnapshot(
                percent = observation.percent,
                packVoltageV = observation.packVoltageMv?.div(1_000.0),
                currentA = observation.currentMa?.div(1_000.0),
                temperatureC = observation.temperatureC,
                designCapacityMah = observation.designCapacityMah,
                fullCapacityMah = observation.fullCapacityMah,
                remainingCapacityMah = observation.remainingCapacityMah,
                cellVoltagesV = observation.cellVoltagesMv.map { it?.div(1_000.0) },
                dischargeCount = observation.dischargeCount,
                firmwareVersion = observation.firmwareVersion
            )
        )

        is AutelSdkObservation.Flight -> buildList {
            val ultrasonic = observation.ultrasonicHeight?.metersOrNull()
            val attitude = observation.attitude?.degreesOrNull()
            add(XStarEvent.ArmStateChanged(observation.armed, observation.flightMode))
            add(
                XStarEvent.NavigationSnapshot(
                    latitudeDeg = observation.latitudeDeg,
                    longitudeDeg = observation.longitudeDeg,
                    homeLatitudeDeg = observation.homeLatitudeDeg,
                    homeLongitudeDeg = observation.homeLongitudeDeg,
                    satellites = observation.satellites,
                    gpsFix = observation.gpsFix,
                    altitudeM = observation.altitudeM,
                    groundSpeedMps = observation.groundSpeedMps,
                    verticalSpeedMps = observation.verticalSpeedMps,
                    ultrasonicHeightM = ultrasonic
                )
            )
            add(
                XStarEvent.AttitudeSnapshot(
                    rollDeg = attitude?.roll,
                    pitchDeg = attitude?.pitch,
                    yawDeg = attitude?.yaw
                )
            )
            if (observation.ultrasonicHeight?.unit == AutelDistanceUnit.UNKNOWN) {
                add(XStarEvent.DiagnosticNote("Ultrasonic height received with unknown SDK unit; raw value not normalized"))
            }
            if (observation.attitude?.unit == AutelAngleUnit.UNKNOWN) {
                add(XStarEvent.DiagnosticNote("Attitude received with unknown SDK angle unit; raw values not normalized"))
            }
        }

        is AutelSdkObservation.Remote -> listOf(
            XStarEvent.RemoteSnapshot(
                connected = observation.connected,
                signalPercent = observation.signalPercent,
                batteryPercent = observation.batteryPercent,
                imageSignalPercent = observation.imageSignalPercent,
                opaqueControlMenu = observation.opaqueControlMenu?.toList()
            )
        )

        is AutelSdkObservation.Camera -> listOf(
            XStarEvent.CameraSnapshot(
                connected = observation.connected,
                mode = observation.mode,
                recording = observation.recording,
                exposureMode = observation.exposureMode,
                iso = observation.iso,
                shutter = observation.shutter
            )
        )

        is AutelSdkObservation.Gimbal -> buildList {
            val pitchDegrees = observation.pitch?.degreesOrNull()
            add(XStarEvent.GimbalSnapshot(pitchDegrees, observation.status))
            if (observation.pitch?.unit == AutelAngleUnit.UNKNOWN) {
                add(XStarEvent.DiagnosticNote("Gimbal angle received with unknown SDK unit; raw value not normalized"))
            }
        }

        is AutelSdkObservation.ImageLink -> listOf(
            XStarEvent.ImageLinkSnapshot(
                usbEnabled = observation.usbEnabled,
                rfFrequencyHz = observation.rfFrequencyHz,
                rfSignalValue = observation.rfSignalValue
            )
        )

        is AutelSdkObservation.Warning -> listOf(
            XStarEvent.WarningObserved(
                WarningState(
                    id = observation.id,
                    severity = when (observation.severity) {
                        AutelWarningSeverity.INFO -> Severity.INFO
                        AutelWarningSeverity.WARNING -> Severity.WARNING
                        AutelWarningSeverity.CRITICAL -> Severity.CRITICAL
                    },
                    message = observation.message
                )
            )
        )

        is AutelSdkObservation.Diagnostic -> listOf(XStarEvent.DiagnosticNote(observation.message))
    }

    private fun AutelDistance.metersOrNull(): Double? = when (unit) {
        AutelDistanceUnit.METERS -> value
        AutelDistanceUnit.UNKNOWN -> null
    }

    private fun AutelAngle.degreesOrNull(): Double? = when (unit) {
        AutelAngleUnit.DEGREES -> value
        AutelAngleUnit.RADIANS -> value * 180.0 / PI
        AutelAngleUnit.UNKNOWN -> null
    }

    private fun AutelAttitude.degreesOrNull(): AutelAttitude? = when (unit) {
        AutelAngleUnit.DEGREES -> this
        AutelAngleUnit.RADIANS -> copy(
            roll = roll * 180.0 / PI,
            pitch = pitch * 180.0 / PI,
            yaw = yaw * 180.0 / PI,
            unit = AutelAngleUnit.DEGREES
        )
        AutelAngleUnit.UNKNOWN -> null
    }
}
