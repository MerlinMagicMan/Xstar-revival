package io.xstarrevival.core.sim

/**
 * A validated stick frame recovered from the RC V1.0.1.5 transport.
 *
 * Axis order is intentionally left as 0-3 until a one-control-at-a-time live
 * capture proves which physical stick owns each channel.
 */
sealed interface RcSimulatorAccessoryFrame {
    val outerChannel: Int
}

data class RcSimulatorStickFrame(
    override val outerChannel: Int,
    val innerChannel: Int,
    val controlPayload: List<Int>,
    val axisValues: List<Int>
) : RcSimulatorAccessoryFrame {
    val normalizedAxes: List<Double> = axisValues.map { value ->
        ((value - AXIS_CENTER).toDouble() / AXIS_SPAN).coerceIn(-1.0, 1.0)
    }

    private companion object {
        const val AXIS_CENTER = 0x400
        const val AXIS_SPAN = 665.0
    }
}

/** A checksum-validated selector 0x81 event from the stock RC button path. */
data class RcSimulatorButtonFrame(
    override val outerChannel: Int,
    val messageId: Int,
    val sequence: Int,
    val eventPayload: List<Int>,
    val eventId: Int,
    val stateValue: Int
) : RcSimulatorAccessoryFrame {
    val controlName: String? = when (eventId) {
        1 -> "KNOB"
        2 -> "RECORD"
        3 -> "SETTINGS"
        4 -> "PHOTO"
        5 -> "SELECTOR"
        6 -> "FLIGHT_STICK_MODE"
        else -> null
    }
}

/**
 * Incrementally decodes the nested A5/AA framing produced by the simulator-only
 * RC patch. The stock additive checksums and the relevant channel/message
 * fields are verified. The decoder has no Android, USB, radio, or aircraft
 * dependency.
 */
class RcSimulatorAccessoryDecoder {
    private var pending = byteArrayOf()

    fun append(chunk: ByteArray, count: Int = chunk.size): List<RcSimulatorStickFrame> {
        return appendFrames(chunk, count).filterIsInstance<RcSimulatorStickFrame>()
    }

    fun appendFrames(
        chunk: ByteArray,
        count: Int = chunk.size
    ): List<RcSimulatorAccessoryFrame> {
        require(count in 0..chunk.size) { "count must be within the supplied chunk" }
        if (count == 0) return emptyList()

        val input = ByteArray(pending.size + count)
        pending.copyInto(input)
        chunk.copyInto(input, destinationOffset = pending.size, endIndex = count)
        pending = byteArrayOf()

        val decoded = mutableListOf<RcSimulatorAccessoryFrame>()
        var cursor = 0
        while (cursor < input.size) {
            val start = input.indexOfHeader(OUTER_HEADER, cursor)
            if (start < 0) break
            if (input.size - start < HEADER_BYTES) {
                pending = input.copyOfRange(start, input.size)
                break
            }

            val encodedLength = input[start + 2].unsigned()
            val totalLength = encodedLength + FRAME_OVERHEAD
            if (encodedLength < MIN_ENCODED_LENGTH || totalLength > MAX_FRAME_BYTES) {
                cursor = start + 1
                continue
            }
            if (input.size - start < totalLength) {
                pending = input.copyOfRange(start, input.size)
                break
            }
            if (!input.hasValidChecksum(start, totalLength)) {
                cursor = start + 1
                continue
            }

            val outerPayload = input.copyOfRange(
                start + HEADER_BYTES,
                start + totalLength - CHECKSUM_BYTES
            )
            decodePayload(input[start + 1].unsigned(), outerPayload)?.let(decoded::add)
            cursor = start + totalLength
        }
        return decoded
    }

    fun reset() {
        pending = byteArrayOf()
    }

    private fun decodePayload(
        outerChannel: Int,
        innerFrame: ByteArray
    ): RcSimulatorAccessoryFrame? {
        if (outerChannel != CONTROL_CHANNEL || innerFrame.isEmpty()) return null
        return when (innerFrame[0].unsigned()) {
            STICK_HEADER -> decodeStickPayload(outerChannel, innerFrame)
            BUTTON_HEADER -> decodeButtonPayload(outerChannel, innerFrame)
            else -> null
        }
    }

    private fun decodeStickPayload(
        outerChannel: Int,
        innerFrame: ByteArray
    ): RcSimulatorStickFrame? {
        if (innerFrame.size < MIN_STICK_FRAME_BYTES) return null

        val encodedLength = innerFrame[2].unsigned()
        if (encodedLength + FRAME_OVERHEAD != innerFrame.size) return null
        if (!innerFrame.hasValidChecksum(0, innerFrame.size)) return null

        val innerChannel = innerFrame[1].unsigned()
        if (innerChannel != CONTROL_CHANNEL) return null
        val payload = innerFrame.copyOfRange(HEADER_BYTES, innerFrame.size - CHECKSUM_BYTES)
            .map { it.unsigned() }
        if (payload.size < AXIS_BYTES) return null
        val axes = List(AXIS_COUNT) { index ->
            (payload[index * 2] shl 8) or payload[index * 2 + 1]
        }
        return RcSimulatorStickFrame(outerChannel, innerChannel, payload, axes)
    }

    private fun decodeButtonPayload(
        outerChannel: Int,
        innerFrame: ByteArray
    ): RcSimulatorButtonFrame? {
        if (innerFrame.size != BUTTON_FRAME_BYTES) return null
        val payloadLength = innerFrame[1].unsigned()
        if (payloadLength != BUTTON_PAYLOAD_BYTES) return null
        if (innerFrame.size != payloadLength + BUTTON_FRAME_OVERHEAD) return null
        if (innerFrame[3].unsigned() != BUTTON_MESSAGE_ID) return null
        if (innerFrame[4].unsigned() != BUTTON_PROTOCOL_VERSION) return null
        if (innerFrame[5].unsigned() != BUTTON_MESSAGE_TYPE) return null

        val calculatedCrc = innerFrame.x25Crc(
            start = 1,
            endExclusive = innerFrame.size - BUTTON_CRC_BYTES
        )
        val expectedCrc = innerFrame[innerFrame.size - 2].unsigned() or
            (innerFrame[innerFrame.size - 1].unsigned() shl 8)
        if (calculatedCrc != expectedCrc) return null

        val payload = innerFrame.copyOfRange(6, 6 + payloadLength).map { it.unsigned() }
        return RcSimulatorButtonFrame(
            outerChannel = outerChannel,
            messageId = BUTTON_MESSAGE_ID,
            sequence = innerFrame[2].unsigned(),
            eventPayload = payload,
            eventId = payload[0],
            stateValue = payload[1]
        )
    }

    private fun ByteArray.indexOfHeader(header: Int, fromIndex: Int): Int {
        for (index in fromIndex until size) {
            if (this[index].unsigned() == header) return index
        }
        return -1
    }

    private fun ByteArray.hasValidChecksum(start: Int, totalLength: Int): Boolean {
        var sum = 0
        for (index in start + 1 until start + totalLength - CHECKSUM_BYTES) {
            sum = (sum + this[index].unsigned()) and 0xFF
        }
        return sum == this[start + totalLength - CHECKSUM_BYTES].unsigned()
    }

    private fun ByteArray.x25Crc(start: Int, endExclusive: Int): Int {
        var crc = 0xFFFF
        for (index in start until endExclusive) {
            var temporary = unsigned(index) xor (crc and 0xFF)
            temporary = (temporary xor (temporary shl 4)) and 0xFF
            crc = ((crc ushr 8) xor (temporary shl 8) xor (temporary shl 3) xor
                (temporary ushr 4)) and 0xFFFF
        }
        return crc
    }

    private fun ByteArray.unsigned(index: Int): Int = this[index].unsigned()

    private fun Byte.unsigned(): Int = toInt() and 0xFF

    private companion object {
        const val OUTER_HEADER = 0xA5
        const val STICK_HEADER = 0xAA
        const val BUTTON_HEADER = 0xFE
        const val CONTROL_CHANNEL = 3
        const val HEADER_BYTES = 3
        const val CHECKSUM_BYTES = 1
        const val FRAME_OVERHEAD = HEADER_BYTES
        const val MIN_ENCODED_LENGTH = 1
        const val MAX_FRAME_BYTES = 258
        const val AXIS_COUNT = 4
        const val AXIS_BYTES = AXIS_COUNT * 2
        const val MIN_STICK_FRAME_BYTES = HEADER_BYTES + AXIS_BYTES + CHECKSUM_BYTES
        const val BUTTON_PAYLOAD_BYTES = 8
        const val BUTTON_FRAME_OVERHEAD = 8
        const val BUTTON_FRAME_BYTES = BUTTON_PAYLOAD_BYTES + BUTTON_FRAME_OVERHEAD
        const val BUTTON_CRC_BYTES = 2
        const val BUTTON_MESSAGE_ID = 0x10
        const val BUTTON_PROTOCOL_VERSION = 1
        const val BUTTON_MESSAGE_TYPE = 0xF0
    }
}
