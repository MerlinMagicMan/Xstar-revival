package io.xstarrevival.core.event

import io.xstarrevival.core.model.ConnectionState
import io.xstarrevival.core.model.WarningState

sealed interface XStarEvent {
    data class ConnectionChanged(val value: ConnectionState) : XStarEvent
    data class ProductIdentified(val name: String?, val firmwareVersion: String?) : XStarEvent
    data class ComponentVersionsSnapshot(val values: Map<String, String>) : XStarEvent
    data class ArmStateChanged(val armed: Boolean?, val flightMode: String?) : XStarEvent

    data class BatterySnapshot(
        val percent: Int? = null,
        val packVoltageV: Double? = null,
        val currentA: Double? = null,
        val temperatureC: Double? = null,
        val designCapacityMah: Int? = null,
        val fullCapacityMah: Int? = null,
        val remainingCapacityMah: Int? = null,
        val cellVoltagesV: List<Double?> = emptyList(),
        val dischargeCount: Int? = null,
        val firmwareVersion: String? = null
    ) : XStarEvent

    data class NavigationSnapshot(
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
    ) : XStarEvent

    data class AttitudeSnapshot(
        val rollDeg: Double? = null,
        val pitchDeg: Double? = null,
        val yawDeg: Double? = null
    ) : XStarEvent

    data class RemoteSnapshot(
        val connected: Boolean? = null,
        val signalPercent: Int? = null,
        val batteryPercent: Int? = null,
        val imageSignalPercent: Int? = null,
        val opaqueControlMenu: List<Int>? = null
    ) : XStarEvent

    data class CameraSnapshot(
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
        val storageRemainingMb: Long? = null
    ) : XStarEvent

    data class GimbalSnapshot(
        val pitchDeg: Double? = null,
        val status: String? = null,
        val sensitivity: Double? = null,
        val smoothing: Double? = null,
        val pitchSpeed: Double? = null,
        val calibrated: Boolean? = null
    ) : XStarEvent
    data class ImageLinkSnapshot(
        val usbEnabled: Boolean? = null,
        val rfFrequencyHz: Double? = null,
        val rfSignalValue: Int? = null
    ) : XStarEvent

    data class VideoSnapshot(
        val receiving: Boolean,
        val codec: String? = null,
        val width: Int? = null,
        val height: Int? = null,
        val framesReceived: Long = 0,
        val bitrateBps: Long? = null
    ) : XStarEvent

    data class VideoFrameReceived(val validBytes: Int, val isKeyFrame: Boolean) : XStarEvent

    data class WarningsReplaced(val warnings: List<WarningState>) : XStarEvent
    data class WarningObserved(val warning: WarningState) : XStarEvent
    data class DiagnosticCounter(val key: String, val value: Long) : XStarEvent
    data class DiagnosticNote(val value: String) : XStarEvent
}
