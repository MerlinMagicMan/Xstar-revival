package io.xstarrevival.core.model

data class XStarState(
    val connection: ConnectionState = ConnectionState.Disconnected,
    val aircraft: AircraftState = AircraftState(),
    val battery: BatteryState = BatteryState(),
    val navigation: NavigationState = NavigationState(),
    val attitude: AttitudeState = AttitudeState(),
    val remote: RemoteState = RemoteState(),
    val camera: CameraState = CameraState(),
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
    val flightMode: String? = null
)

data class BatteryState(
    val percent: Int? = null,
    val packVoltageV: Double? = null,
    val currentA: Double? = null,
    val temperatureC: Double? = null,
    val designCapacityMah: Int? = null,
    val fullCapacityMah: Int? = null,
    val remainingCapacityMah: Int? = null,
    val cells: List<CellState> = emptyList()
) {
    val cellDeltaV: Double?
        get() = cells.mapNotNull { it.voltageV }.takeIf { it.size >= 2 }?.let { values ->
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
    val verticalSpeedMps: Double? = null
)

data class AttitudeState(
    val rollDeg: Double? = null,
    val pitchDeg: Double? = null,
    val yawDeg: Double? = null
)

data class RemoteState(
    val connected: Boolean? = null,
    val signalPercent: Int? = null,
    val batteryPercent: Int? = null
)

data class CameraState(
    val connected: Boolean? = null,
    val mode: String? = null,
    val recording: Boolean? = null,
    val video: VideoState = VideoState()
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
    val notes: List<String> = emptyList()
)
