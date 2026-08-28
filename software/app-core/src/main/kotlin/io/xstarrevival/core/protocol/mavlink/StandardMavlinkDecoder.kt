package io.xstarrevival.core.protocol.mavlink

import io.xstarrevival.core.adapter.OpenXStarDecoder
import io.xstarrevival.core.event.XStarEvent
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sqrt

/**
 * Passive decoder for the small, CRC-verified subset of the standard MAVLink
 * dialect that maps directly onto [io.xstarrevival.core.model.XStarState].
 *
 * Structurally plausible custom messages are counted but intentionally remain
 * opaque. Their CRC-extra values and field layouts are not guessed here.
 */
class StandardMavlinkDecoder : OpenXStarDecoder {
    private val scanner = MavlinkStreamScanner()
    private var frames = 0L
    private var decodedFrames = 0L
    private var opaqueFrames = 0L
    private var crcFailures = 0L
    private var heartbeats = 0L
    private var navigation = XStarEvent.NavigationSnapshot()
    private var battery = XStarEvent.BatterySnapshot()

    override fun decode(chunk: ByteArray): List<XStarEvent> {
        val candidates = scanner.feed(chunk)
        if (candidates.isEmpty()) return emptyList()

        val events = mutableListOf<XStarEvent>()
        candidates.forEach { frame ->
            frames += 1
            val definition = STANDARD_MESSAGES[frame.messageId]
            if (definition == null || !frame.hasSupportedEnvelope) {
                opaqueFrames += 1
                return@forEach
            }
            if (frame.payload.size < definition.minimumLength || !frame.hasValidCrc(definition.crcExtra)) {
                crcFailures += 1
                return@forEach
            }

            decodedFrames += 1
            events += decodeStandardFrame(frame)
        }

        events += XStarEvent.DiagnosticCounter("mavlink_frames", frames)
        events += XStarEvent.DiagnosticCounter("mavlink_decoded_frames", decodedFrames)
        events += XStarEvent.DiagnosticCounter("mavlink_opaque_frames", opaqueFrames)
        events += XStarEvent.DiagnosticCounter("mavlink_crc_failures", crcFailures)
        events += XStarEvent.DiagnosticCounter("mavlink_heartbeats", heartbeats)
        return events
    }

    override fun reset() {
        scanner.reset()
        frames = 0
        decodedFrames = 0
        opaqueFrames = 0
        crcFailures = 0
        heartbeats = 0
        navigation = XStarEvent.NavigationSnapshot()
        battery = XStarEvent.BatterySnapshot()
    }

    private fun decodeStandardFrame(frame: MavlinkFrame): List<XStarEvent> = when (frame.messageId) {
        HEARTBEAT -> decodeHeartbeat(frame)
        SYS_STATUS -> listOf(decodeSystemStatus(frame.payload))
        GPS_RAW_INT -> listOf(decodeGpsRaw(frame.payload))
        ATTITUDE -> listOf(decodeAttitude(frame.payload))
        GLOBAL_POSITION_INT -> listOf(decodeGlobalPosition(frame.payload))
        BATTERY_STATUS -> decodeBatteryStatus(frame.payload)
        else -> emptyList()
    }

    private fun decodeHeartbeat(frame: MavlinkFrame): List<XStarEvent> {
        heartbeats += 1
        val payload = frame.payload
        val vehicleType = payload.u8(4)
        val autopilot = payload.u8(5)
        val baseMode = payload.u8(6)

        // MAV_COMP_ID_AUTOPILOT1 plus a real autopilot type prevents camera or
        // gimbal heartbeats from being mislabeled as the aircraft.
        if (frame.componentId != AUTOPILOT_COMPONENT || autopilot == MAV_AUTOPILOT_INVALID) {
            return emptyList()
        }

        return listOf(
            XStarEvent.ProductIdentified(mavTypeName(vehicleType), null),
            XStarEvent.ArmStateChanged(armed = baseMode and MAV_MODE_FLAG_SAFETY_ARMED != 0, flightMode = null)
        )
    }

    private fun decodeSystemStatus(payload: ByteArray): XStarEvent.BatterySnapshot {
        val voltageMv = payload.u16(14).takeUnless { it == U16_UNKNOWN }
        val currentCa = payload.i16(16).takeUnless { it == -1 }
        val remaining = payload.i8(30).takeUnless { it == -1 }
        battery = battery.copy(
            percent = remaining?.coerceIn(0, 100),
            packVoltageV = voltageMv?.div(1000.0),
            currentA = currentCa?.div(100.0)
        )
        return battery
    }

    private fun decodeGpsRaw(payload: ByteArray): XStarEvent.NavigationSnapshot {
        val fixType = payload.u8(28)
        val hasPositionFix = fixType >= GPS_FIX_2D
        val speedCms = payload.u16(24).takeUnless { it == U16_UNKNOWN }
        val satellites = payload.u8(29).takeUnless { it == U8_UNKNOWN }
        navigation = navigation.copy(
            latitudeDeg = payload.i32(8).takeIf { hasPositionFix }?.div(1e7),
            longitudeDeg = payload.i32(12).takeIf { hasPositionFix }?.div(1e7),
            satellites = satellites,
            gpsFix = gpsFixName(fixType),
            groundSpeedMps = speedCms?.div(100.0)
        )
        return navigation
    }

    private fun decodeAttitude(payload: ByteArray) = XStarEvent.AttitudeSnapshot(
        rollDeg = payload.f32(4).radiansToDegrees(),
        pitchDeg = payload.f32(8).radiansToDegrees(),
        yawDeg = payload.f32(12).radiansToDegrees()
    )

    private fun decodeGlobalPosition(payload: ByteArray): XStarEvent.NavigationSnapshot {
        val northCms = payload.i16(20)
        val eastCms = payload.i16(22)
        val downCms = payload.i16(24)
        navigation = navigation.copy(
            latitudeDeg = payload.i32(4) / 1e7,
            longitudeDeg = payload.i32(8) / 1e7,
            // GLOBAL_POSITION_INT defines relative_alt as altitude above home.
            altitudeM = payload.i32(16) / 1000.0,
            groundSpeedMps = sqrt(northCms.toDouble() * northCms + eastCms.toDouble() * eastCms) / 100.0,
            // MAVLink NED velocity is positive down; app vertical speed is positive up.
            verticalSpeedMps = -downCms / 100.0
        )
        return navigation
    }

    private fun decodeBatteryStatus(payload: ByteArray): List<XStarEvent> {
        // The current state surface represents one aircraft pack. Do not merge
        // other BATTERY_STATUS instances into it.
        if (payload.u8(32) != PRIMARY_BATTERY_ID) return emptyList()

        val temperatureCdeg = payload.i16(8).takeUnless { it == I16_UNKNOWN }
        val currentCa = payload.i16(30).takeUnless { it == -1 }
        val remaining = payload.i8(35).takeUnless { it == -1 }
        val reportedVoltages = (0 until 10)
            .map { payload.u16(10 + it * 2) }
            .takeWhile { it != U16_UNKNOWN }
        val cellVoltages = reportedVoltages.takeIf { it.size >= 2 }
            ?.map { it / 1000.0 }
            .orEmpty()

        battery = battery.copy(
            percent = remaining?.coerceIn(0, 100),
            packVoltageV = reportedVoltages.takeIf { it.isNotEmpty() }?.sum()?.div(1000.0),
            currentA = currentCa?.div(100.0),
            temperatureC = temperatureCdeg?.div(100.0),
            cellVoltagesV = cellVoltages
        )
        return listOf(battery)
    }

    private fun mavTypeName(type: Int): String = "MAVLink " + when (type) {
        0 -> "vehicle"
        1 -> "fixed-wing"
        2 -> "quadrotor"
        3 -> "coaxial helicopter"
        4 -> "helicopter"
        13 -> "hexarotor"
        14 -> "octorotor"
        else -> "vehicle (type $type)"
    }

    private fun gpsFixName(type: Int): String = when (type) {
        0 -> "NO GPS"
        1 -> "NO FIX"
        2 -> "2D FIX"
        3 -> "3D FIX"
        4 -> "DGPS"
        5 -> "RTK FLOAT"
        6 -> "RTK FIXED"
        7 -> "STATIC"
        8 -> "PPP"
        else -> "UNKNOWN ($type)"
    }

    private fun Float.radiansToDegrees(): Double = toDouble() * 180.0 / PI

    private companion object {
        const val HEARTBEAT = 0
        const val SYS_STATUS = 1
        const val GPS_RAW_INT = 24
        const val ATTITUDE = 30
        const val GLOBAL_POSITION_INT = 33
        const val BATTERY_STATUS = 147

        const val AUTOPILOT_COMPONENT = 1
        const val MAV_AUTOPILOT_INVALID = 8
        const val MAV_MODE_FLAG_SAFETY_ARMED = 128
        const val GPS_FIX_2D = 2
        const val PRIMARY_BATTERY_ID = 0
        const val U8_UNKNOWN = 0xff
        const val U16_UNKNOWN = 0xffff
        const val I16_UNKNOWN = 0x7fff

        val STANDARD_MESSAGES = mapOf(
            HEARTBEAT to MessageDefinition(minimumLength = 9, crcExtra = 50),
            SYS_STATUS to MessageDefinition(minimumLength = 31, crcExtra = 124),
            GPS_RAW_INT to MessageDefinition(minimumLength = 30, crcExtra = 24),
            ATTITUDE to MessageDefinition(minimumLength = 28, crcExtra = 39),
            GLOBAL_POSITION_INT to MessageDefinition(minimumLength = 28, crcExtra = 104),
            BATTERY_STATUS to MessageDefinition(minimumLength = 36, crcExtra = 154)
        )
    }
}

private data class MessageDefinition(val minimumLength: Int, val crcExtra: Int)

private data class MavlinkFrame(
    val headerWithoutMagic: ByteArray,
    val payload: ByteArray,
    val checksum: Int,
    val componentId: Int,
    val messageId: Int,
    val hasSupportedEnvelope: Boolean
) {
    fun hasValidCrc(crcExtra: Int): Boolean = mavlinkCrc(headerWithoutMagic + payload, crcExtra) == checksum
}

private class MavlinkStreamScanner {
    private val buffer = mutableListOf<Byte>()

    fun feed(chunk: ByteArray): List<MavlinkFrame> {
        chunk.forEach(buffer::add)
        val frames = mutableListOf<MavlinkFrame>()

        while (buffer.isNotEmpty()) {
            val magicIndex = buffer.indexOfFirst { it.u8() == MAVLINK_V1_MAGIC || it.u8() == MAVLINK_V2_MAGIC }
            if (magicIndex < 0) {
                buffer.clear()
                break
            }
            repeat(magicIndex) { buffer.removeAt(0) }

            val magic = buffer[0].u8()
            val minimumFrameLength = if (magic == MAVLINK_V1_MAGIC) 8 else 12
            if (buffer.size < minimumFrameLength) break

            val payloadLength = buffer[1].u8()
            val signed = magic == MAVLINK_V2_MAGIC && buffer[2].u8() and MAVLINK_V2_SIGNED != 0
            val frameLength = minimumFrameLength + payloadLength + if (signed) MAVLINK_SIGNATURE_LENGTH else 0
            if (buffer.size < frameLength) break

            val raw = ByteArray(frameLength) { buffer[it] }
            repeat(frameLength) { buffer.removeAt(0) }
            val headerLength = if (magic == MAVLINK_V1_MAGIC) 5 else 9
            val payloadOffset = 1 + headerLength
            val checksumOffset = payloadOffset + payloadLength
            val messageId = if (magic == MAVLINK_V1_MAGIC) {
                raw[5].u8()
            } else {
                raw[7].u8() or (raw[8].u8() shl 8) or (raw[9].u8() shl 16)
            }
            val componentId = if (magic == MAVLINK_V1_MAGIC) raw[4].u8() else raw[6].u8()
            val hasSupportedEnvelope = magic == MAVLINK_V1_MAGIC || raw[2].u8() and MAVLINK_V2_SIGNED.inv() == 0
            frames += MavlinkFrame(
                headerWithoutMagic = raw.copyOfRange(1, 1 + headerLength),
                payload = raw.copyOfRange(payloadOffset, checksumOffset),
                checksum = raw[checksumOffset].u8() or (raw[checksumOffset + 1].u8() shl 8),
                componentId = componentId,
                messageId = messageId,
                hasSupportedEnvelope = hasSupportedEnvelope
            )
        }

        return frames
    }

    fun reset() = buffer.clear()

    private companion object {
        const val MAVLINK_V1_MAGIC = 0xfe
        const val MAVLINK_V2_MAGIC = 0xfd
        const val MAVLINK_V2_SIGNED = 0x01
        const val MAVLINK_SIGNATURE_LENGTH = 13
    }
}

private fun mavlinkCrc(bytes: ByteArray, crcExtra: Int): Int {
    var crc = 0xffff
    (bytes.asIterable() + crcExtra.toByte()).forEach { byte ->
        var tmp = byte.u8() xor (crc and 0xff)
        tmp = (tmp xor (tmp shl 4)) and 0xff
        crc = ((crc shr 8) xor (tmp shl 8) xor (tmp shl 3) xor (tmp shr 4)) and 0xffff
    }
    return crc
}

private fun Byte.u8(): Int = toInt() and 0xff
private fun ByteArray.u8(offset: Int): Int = this[offset].u8()
private fun ByteArray.i8(offset: Int): Int = this[offset].toInt()
private fun ByteArray.u16(offset: Int): Int = littleEndian(offset, 2).short.toInt() and 0xffff
private fun ByteArray.i16(offset: Int): Int = littleEndian(offset, 2).short.toInt()
private fun ByteArray.i32(offset: Int): Int = littleEndian(offset, 4).int
private fun ByteArray.f32(offset: Int): Float = Float.fromBits(i32(offset))
private fun ByteArray.littleEndian(offset: Int, length: Int): ByteBuffer =
    ByteBuffer.wrap(this, offset, length).order(ByteOrder.LITTLE_ENDIAN)
