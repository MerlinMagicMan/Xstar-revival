package io.xstarrevival.core.sim

/**
 * A validated stick frame recovered from the RC V1.0.1.5 transport.
 *
 * Axis order is intentionally left as 0-3 until a one-control-at-a-time live
 * capture proves which physical stick owns each channel.
 */
data class RcSimulatorStickFrame(
    val outerChannel: Int,
    val innerChannel: Int,
    val controlPayload: List<Int>,
    val axisValues: List<Int>
) {
    val normalizedAxes: List<Double> = axisValues.map { value ->
        ((value - AXIS_CENTER).toDouble() / AXIS_SPAN).coerceIn(-1.0, 1.0)
    }

    private companion object {
        const val AXIS_CENTER = 0x400
        const val AXIS_SPAN = 665.0
    }
}

/**
 * Incrementally decodes the nested A5/AA framing produced by the simulator-only
 * RC patch. Both stock additive checksums and both channel fields are verified.
 * The decoder has no Android, USB, radio, or aircraft dependency.
 */
class RcSimulatorAccessoryDecoder {
    private var pending = byteArrayOf()

    fun append(chunk: ByteArray, count: Int = chunk.size): List<RcSimulatorStickFrame> {
        require(count in 0..chunk.size) { "count must be within the supplied chunk" }
        if (count == 0) return emptyList()

        val input = ByteArray(pending.size + count)
        pending.copyInto(input)
        chunk.copyInto(input, destinationOffset = pending.size, endIndex = count)
        pending = byteArrayOf()

        val decoded = mutableListOf<RcSimulatorStickFrame>()
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
            decodeStickPayload(input[start + 1].unsigned(), outerPayload)?.let(decoded::add)
            cursor = start + totalLength
        }
        return decoded
    }

    fun reset() {
        pending = byteArrayOf()
    }

    private fun decodeStickPayload(
        outerChannel: Int,
        innerFrame: ByteArray
    ): RcSimulatorStickFrame? {
        if (outerChannel != CONTROL_CHANNEL || innerFrame.size < MIN_STICK_FRAME_BYTES) return null
        if (innerFrame[0].unsigned() != INNER_HEADER) return null

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

    private fun Byte.unsigned(): Int = toInt() and 0xFF

    private companion object {
        const val OUTER_HEADER = 0xA5
        const val INNER_HEADER = 0xAA
        const val CONTROL_CHANNEL = 3
        const val HEADER_BYTES = 3
        const val CHECKSUM_BYTES = 1
        const val FRAME_OVERHEAD = HEADER_BYTES
        const val MIN_ENCODED_LENGTH = 1
        const val MAX_FRAME_BYTES = 258
        const val AXIS_COUNT = 4
        const val AXIS_BYTES = AXIS_COUNT * 2
        const val MIN_STICK_FRAME_BYTES = HEADER_BYTES + AXIS_BYTES + CHECKSUM_BYTES
    }
}
