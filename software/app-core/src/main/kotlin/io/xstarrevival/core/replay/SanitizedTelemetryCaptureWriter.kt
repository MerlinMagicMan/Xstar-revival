package io.xstarrevival.core.replay

import io.xstarrevival.core.model.ConnectionState
import io.xstarrevival.core.model.XStarState
import java.io.Closeable
import java.io.OutputStream

data class SanitizedTelemetryCaptureStats(
    val samplesWritten: Long = 0,
    val bytesWritten: Long = 0,
    val samplesDroppedAtLimit: Long = 0
)

/** Writes a privacy-reduced normalized-state timeline for shareable bench captures. */
class SanitizedTelemetryCaptureWriter(
    output: OutputStream,
    private val maxBytes: Long,
    private val elapsedRealtimeMs: () -> Long
) : Closeable {
    private val sink = output.buffered()
    private val startedAtMs = elapsedRealtimeMs()
    private var current = SanitizedTelemetryCaptureStats()
    private var closed = false

    init {
        require(maxBytes > 0) { "maxBytes must be positive" }
        writeRecord(
            linkedMapOf(
                "record" to "header",
                "format" to "xstar-sanitized-normalized-state",
                "version" to 1,
                "privacy" to "coordinates, opaque controls, identifiers, app keys, and diagnostic notes excluded"
            )
        )
    }

    @Synchronized
    fun append(state: XStarState): SanitizedTelemetryCaptureStats {
        check(!closed) { "telemetry capture writer is closed" }
        val connection = when (val value = state.connection) {
            ConnectionState.Disconnected -> mapOf("status" to "DISCONNECTED")
            ConnectionState.Discovering -> mapOf("status" to "DISCOVERING")
            is ConnectionState.Connecting -> mapOf("status" to "CONNECTING", "stage" to value.stage)
            is ConnectionState.Connected -> mapOf(
                "status" to "CONNECTED",
                "transport" to value.transport,
                "product" to value.product
            )
            is ConnectionState.Failed -> mapOf("status" to "FAILED", "stage" to value.stage)
        }
        val record = linkedMapOf<String, Any?>(
            "record" to "sample",
            "elapsed_ms" to elapsed(),
            "connection" to connection,
            "aircraft" to linkedMapOf(
                "product_name" to state.aircraft.productName,
                "firmware_version" to state.aircraft.firmwareVersion,
                "armed" to state.aircraft.armed,
                "flight_mode" to state.aircraft.flightMode,
                "component_versions" to state.aircraft.componentVersions.toSortedMap()
            ),
            "battery" to linkedMapOf(
                "percent" to state.battery.percent,
                "pack_voltage_v" to state.battery.packVoltageV,
                "current_a" to state.battery.currentA,
                "temperature_c" to state.battery.temperatureC,
                "remaining_capacity_mah" to state.battery.remainingCapacityMah,
                "cell_voltages_v" to state.battery.cells.map { it.voltageV },
                "cell_delta_v" to state.battery.cellDeltaV,
                "discharge_count" to state.battery.dischargeCount
            ),
            "navigation_redacted" to linkedMapOf(
                "satellites" to state.navigation.satellites,
                "gps_fix" to state.navigation.gpsFix,
                "altitude_m" to state.navigation.altitudeM,
                "ground_speed_mps" to state.navigation.groundSpeedMps,
                "vertical_speed_mps" to state.navigation.verticalSpeedMps,
                "ultrasonic_height_m" to state.navigation.ultrasonicHeightM,
                "ultrasonic_height_raw" to state.navigation.ultrasonicHeightRaw
            ),
            "attitude" to linkedMapOf(
                "roll_deg" to state.attitude.rollDeg,
                "pitch_deg" to state.attitude.pitchDeg,
                "yaw_deg" to state.attitude.yawDeg
            ),
            "remote_redacted" to linkedMapOf(
                "connected" to state.remote.connected,
                "signal_percent" to state.remote.signalPercent,
                "battery_percent" to state.remote.batteryPercent,
                "image_signal_percent" to state.remote.imageSignalPercent,
                "firmware_version" to state.remote.firmwareVersion,
                "calibrated" to state.remote.calibrated,
                "stick_mode" to state.remote.stickMode,
                "sensitivity" to state.remote.sensitivity,
                "dead_zone" to state.remote.deadZone,
                "expo" to state.remote.expo,
                "button_assignments" to state.remote.buttonAssignments.toSortedMap(),
                "gimbal_wheel_reversed" to state.remote.gimbalWheelReversed,
                "throttle_input" to state.remote.throttleInput,
                "yaw_input" to state.remote.yawInput,
                "pitch_input" to state.remote.pitchInput,
                "roll_input" to state.remote.rollInput,
                "gimbal_wheel_input" to state.remote.gimbalWheelInput
            ),
            "camera" to linkedMapOf(
                "connected" to state.camera.connected,
                "mode" to state.camera.mode,
                "recording" to state.camera.recording,
                "exposure_mode" to state.camera.exposureMode,
                "iso" to state.camera.iso,
                "shutter" to state.camera.shutter,
                "photos_taken" to state.camera.photosTaken,
                "videos_taken" to state.camera.videosTaken,
                "recording_duration_seconds" to state.camera.recordingDurationSeconds,
                "video_receiving" to state.camera.video.receiving,
                "frames_received" to state.camera.video.framesReceived
            ),
            "gimbal" to linkedMapOf(
                "pitch_deg" to state.gimbal.pitchDeg,
                "status" to state.gimbal.status
            ),
            "image_link" to linkedMapOf(
                "usb_enabled" to state.imageLink.usbEnabled,
                "rf_frequency_hz" to state.imageLink.rfFrequencyHz,
                "rf_signal_value" to state.imageLink.rfSignalValue,
                "automatic_channel" to state.imageLink.automaticChannel,
                "channel" to state.imageLink.channel,
                "channel_strengths" to state.imageLink.channelStrengths,
                "interference_percent" to state.imageLink.interferencePercent,
                "packet_loss_percent" to state.imageLink.packetLossPercent,
                "latency_ms" to state.imageLink.latencyMs,
                "bandwidth_mbps" to state.imageLink.bandwidthMbps
            ),
            "warnings" to state.warnings.map { warning ->
                linkedMapOf(
                    "id" to warning.id,
                    "severity" to warning.severity.name
                )
            },
            "diagnostics_redacted" to linkedMapOf(
                "source" to state.diagnostics.source,
                "counters" to state.diagnostics.counters.toSortedMap()
            )
        )
        if (!writeRecord(record)) {
            current = current.copy(samplesDroppedAtLimit = current.samplesDroppedAtLimit + 1)
        } else {
            current = current.copy(samplesWritten = current.samplesWritten + 1)
        }
        return current
    }

    @Synchronized
    fun stats(): SanitizedTelemetryCaptureStats = current

    override fun close() {
        synchronized(this) {
            if (closed) return
            writeRecord(
                linkedMapOf(
                    "record" to "footer",
                    "samples" to current.samplesWritten,
                    "bytes" to current.bytesWritten,
                    "samples_dropped_at_limit" to current.samplesDroppedAtLimit
                ),
                countAgainstLimit = false
            )
            closed = true
        }
        runCatching { sink.close() }
    }

    private fun writeRecord(record: Map<String, Any?>, countAgainstLimit: Boolean = true): Boolean {
        val bytes = (Json.encode(record) + "\n").toByteArray(Charsets.UTF_8)
        if (countAgainstLimit && current.bytesWritten + bytes.size > maxBytes) return false
        sink.write(bytes)
        current = current.copy(bytesWritten = current.bytesWritten + bytes.size)
        return true
    }

    private fun elapsed(): Long = (elapsedRealtimeMs() - startedAtMs).coerceAtLeast(0)

    private object Json {
        fun encode(value: Any?): String = when (value) {
            null -> "null"
            is String -> "\"${escape(value)}\""
            is Boolean, is Byte, is Short, is Int, is Long -> value.toString()
            is Float -> if (value.isFinite()) value.toString() else "null"
            is Double -> if (value.isFinite()) value.toString() else "null"
            is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}") { (key, item) ->
                "${encode(key.toString())}:${encode(item)}"
            }
            is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]") { encode(it) }
            else -> encode(value.toString())
        }

        private fun escape(value: String): String = buildString(value.length + 8) {
            value.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
                }
            }
        }
    }
}
