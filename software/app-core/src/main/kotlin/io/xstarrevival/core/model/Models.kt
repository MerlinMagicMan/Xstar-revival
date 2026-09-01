package io.xstarrevival.core.model

import kotlin.math.absoluteValue

data class XStarState(
    val connection: ConnectionState = ConnectionState.Disconnected,
    val aircraft: AircraftState = AircraftState(),
    val battery: BatteryState = BatteryState(),
    val navigation: NavigationState = NavigationState(),
    val attitude: AttitudeState = AttitudeState(),
    val remote: RemoteState = RemoteState(),
    val camera: CameraState = CameraState(),
    val gimbal: GimbalState = GimbalState(),
    val imageLink: ImageLinkState = ImageLinkState(),
    val warnings: List<WarningState> = emptyList(),
    val diagnostics: DiagnosticsState = DiagnosticsState()
)

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Discovering : ConnectionState
    data class Connecting(val stage: String) : ConnectionState
    data class Connected(val transport: String, val product: String?) : ConnectionState
    data class Failed(val stage: String, val reason: String) : ConnectionState
}

data class AircraftState(
    val productName: String? = null,
    val firmwareVersion: String? = null,
    val armed: Boolean? = null,
    val flightMode: String? = null,
    val componentVersions: Map<String, String> = emptyMap()
)

data class BatteryState(
    val packId: String? = null,
    val percent: Int? = null,
    val packVoltageV: Double? = null,
    val currentA: Double? = null,
    val temperatureC: Double? = null,
    val designCapacityMah: Int? = null,
    val fullCapacityMah: Int? = null,
    val remainingCapacityMah: Int? = null,
    val cells: List<CellState> = emptyList(),
    val dischargeCount: Int? = null,
    val firmwareVersion: String? = null
) {
    val powerW: Double?
        get() = packVoltageV?.takeIf { it.isFinite() }?.let { voltage ->
            currentA?.takeIf { it.isFinite() }?.let { current -> voltage * current.absoluteValue }
        }

    val cellDeltaV: Double?
        get() = cells.mapNotNull { it.voltageV?.takeIf(Double::isFinite) }.takeIf { it.size >= 2 }?.let { values ->
            values.maxOrNull()!! - values.minOrNull()!!
        }
}

data class CellState(val index: Int, val voltageV: Double?)

data class NavigationState(
    val latitudeDeg: Double? = null,
    val longitudeDeg: Double? = null,
    val homeLatitudeDeg: Double? = null,
    val homeLongitudeDeg: Double? = null,
    val satellites: Int? = null,
    val gpsFix: String? = null,
    val altitudeM: Double? = null,
    val groundSpeedMps: Double? = null,
    val verticalSpeedMps: Double? = null,
    val ultrasonicHeightM: Double? = null,
    val ultrasonicHeightRaw: Double? = null
)

data class AttitudeState(
    val rollDeg: Double? = null,
    val pitchDeg: Double? = null,
    val yawDeg: Double? = null
)

data class RemoteState(
    val connected: Boolean? = null,
    val signalPercent: Int? = null,
    val batteryPercent: Int? = null,
    val imageSignalPercent: Int? = null,
    val firmwareVersion: String? = null,
    val calibrated: Boolean? = null,
    val stickMode: Int? = null,
    val sensitivity: Double? = null,
    val deadZone: Double? = null,
    val expo: Double? = null,
    val buttonAssignments: Map<String, String> = emptyMap(),
    val gimbalWheelReversed: Boolean? = null,
    val throttleInput: Double? = null,
    val yawInput: Double? = null,
    val pitchInput: Double? = null,
    val rollInput: Double? = null,
    val gimbalWheelInput: Double? = null,
    val opaqueControlMenu: List<Int>? = null
)

data class CameraState(
    val connected: Boolean? = null,
    val mode: String? = null,
    val recording: Boolean? = null,
    val exposureMode: String? = null,
    val iso: String? = null,
    val shutter: String? = null,
    val exposureCompensationEv: Double? = null,
    val whiteBalance: String? = null,
    val photoResolution: String? = null,
    val videoResolution: String? = null,
    val frameRateFps: Int? = null,
    val timerSeconds: Int? = null,
    val storageRemainingMb: Long? = null,
    val photosTaken: Int = 0,
    val videosTaken: Int = 0,
    val recordingDurationSeconds: Double? = null,
    val lastVideoDurationSeconds: Double? = null,
    val histogramEnabled: Boolean = false,
    val overexposureWarningEnabled: Boolean = false,
    val gridEnabled: Boolean = false,
    val centerPointEnabled: Boolean = false,
    val video: VideoState = VideoState()
)

data class GimbalState(
    val pitchDeg: Double? = null,
    val status: String? = null,
    val sensitivity: Double? = null,
    val smoothing: Double? = null,
    val pitchSpeed: Double? = null,
    val calibrated: Boolean? = null
)

data class ImageLinkState(
    val usbEnabled: Boolean? = null,
    val rfFrequencyHz: Double? = null,
    val rfSignalValue: Int? = null,
    val automaticChannel: Boolean? = null,
    val channel: Int? = null,
    val channelStrengths: List<Int> = emptyList(),
    val interferencePercent: Int? = null,
    val packetLossPercent: Double? = null,
    val latencyMs: Int? = null,
    val bandwidthMbps: Double? = null
)

data class VideoState(
    val receiving: Boolean = false,
    val codec: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val framesReceived: Long = 0,
    val bitrateBps: Long? = null
)

data class WarningState(
    val id: String,
    val severity: Severity,
    val message: String
)

enum class Severity { INFO, WARNING, CRITICAL }

data class DiagnosticsState(
    val source: String? = null,
    val lastUpdateEpochMs: Long? = null,
    val counters: Map<String, Long> = emptyMap(),
    val notes: List<String> = emptyList(),
    val protocolVersion: String? = null,
    val packets: List<ProtocolPacketTrace> = emptyList()
)

enum class ProtocolPacketDisposition { DECODED, OPAQUE, CRC_FAILURE }

data class ProtocolPacketTrace(
    val sequence: Long,
    val protocol: String,
    val messageId: Int,
    val componentId: Int,
    val lengthBytes: Int,
    val disposition: ProtocolPacketDisposition,
    val decodedName: String? = null,
    val rawHex: String
)
