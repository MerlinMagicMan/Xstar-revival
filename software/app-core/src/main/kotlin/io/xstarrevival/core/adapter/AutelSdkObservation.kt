package io.xstarrevival.core.adapter

/**
 * Proprietary-type-free observations emitted by the Android Autel SDK binding.
 *
 * Only values exposed by the read-only allowlist are modeled here. Control,
 * calibration, pairing, camera actuation, parameter writes, and mission upload
 * deliberately have no representation.
 */
sealed interface AutelSdkObservation {
    data class ProductConnected(
        val productName: String?,
        val firmwareVersion: String? = null,
        val availableComponents: Set<AutelSdkComponent> = emptySet()
    ) : AutelSdkObservation

    data object ProductDisconnected : AutelSdkObservation

    data class ComponentVersions(
        val values: Map<String, String>
    ) : AutelSdkObservation

    data class Battery(
        val percent: Int? = null,
        val packVoltageMv: Double? = null,
        val currentMa: Double? = null,
        val temperatureC: Double? = null,
        val designCapacityMah: Int? = null,
        val fullCapacityMah: Int? = null,
        val remainingCapacityMah: Int? = null,
        val cellVoltagesMv: List<Int?> = emptyList(),
        val dischargeCount: Int? = null,
        val firmwareVersion: String? = null
    ) : AutelSdkObservation

    data class Flight(
        val latitudeDeg: Double? = null,
        val longitudeDeg: Double? = null,
        val homeLatitudeDeg: Double? = null,
        val homeLongitudeDeg: Double? = null,
        val satellites: Int? = null,
        val gpsFix: String? = null,
        val altitudeM: Double? = null,
        val groundSpeedMps: Double? = null,
        val verticalSpeedMps: Double? = null,
        val ultrasonicHeight: AutelDistance? = null,
        val attitude: AutelAttitude? = null,
        val armed: Boolean? = null,
        val flightMode: String? = null
    ) : AutelSdkObservation

    data class Remote(
        val connected: Boolean? = null,
        val signalPercent: Int? = null,
        val batteryPercent: Int? = null,
        val imageSignalPercent: Int? = null,
        val opaqueControlMenu: List<Int>? = null
    ) : AutelSdkObservation

    data class Camera(
        val connected: Boolean? = null,
        val mode: String? = null,
        val recording: Boolean? = null,
        val exposureMode: String? = null,
        val iso: String? = null,
        val shutter: String? = null
    ) : AutelSdkObservation

    data class Gimbal(
        val pitch: AutelAngle? = null,
        val status: String? = null
    ) : AutelSdkObservation

    data class ImageLink(
        val usbEnabled: Boolean? = null,
        val rfFrequencyHz: Double? = null,
        val rfSignalValue: Int? = null
    ) : AutelSdkObservation

    data class Warning(
        val id: String,
        val severity: AutelWarningSeverity,
        val message: String
    ) : AutelSdkObservation

    data class Diagnostic(val message: String) : AutelSdkObservation
}

enum class AutelSdkComponent {
    BATTERY,
    FLIGHT_CONTROLLER,
    REMOTE_CONTROLLER,
    GIMBAL,
    CAMERA,
    DSP,
    CODEC,
    ALBUM,
    MISSION_MANAGER
}

data class AutelDistance(val value: Double, val unit: AutelDistanceUnit)
enum class AutelDistanceUnit { METERS, UNKNOWN }

data class AutelAngle(val value: Double, val unit: AutelAngleUnit)
data class AutelAttitude(val roll: Double, val pitch: Double, val yaw: Double, val unit: AutelAngleUnit)
enum class AutelAngleUnit { DEGREES, RADIANS, UNKNOWN }

enum class AutelWarningSeverity { INFO, WARNING, CRITICAL }
