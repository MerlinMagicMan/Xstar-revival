package io.xstarrevival.core.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RcSimulatorAccessoryDecoderTest {
    @Test
    fun `decodes the verified nested stick framing and big endian axes`() {
        val controlPayload = byteArrayOf(
            0x01, 0x67,
            0x04, 0x00,
            0x06, 0x99.toByte(),
            0x04, 0x00,
            0x12, 0x34
        )
        val result = RcSimulatorAccessoryDecoder().append(stickFrame(controlPayload)).single()

        assertEquals(3, result.outerChannel)
        assertEquals(3, result.innerChannel)
        assertEquals(listOf(0x167, 0x400, 0x699, 0x400), result.axisValues)
        assertEquals(-1.0, result.normalizedAxes[0], .0001)
        assertEquals(0.0, result.normalizedAxes[1], .0001)
        assertEquals(1.0, result.normalizedAxes[2], .0001)
    }

    @Test
    fun `retains a fragmented frame and resynchronizes after noise`() {
        val first = stickFrame(axisPayload(0x400, 0x401, 0x402, 0x403))
        val second = stickFrame(axisPayload(0x410, 0x420, 0x430, 0x440))
        val decoder = RcSimulatorAccessoryDecoder()

        assertTrue(decoder.append(byteArrayOf(0x55, 0x66) + first.copyOfRange(0, 5)).isEmpty())
        val result = decoder.append(first.copyOfRange(5, first.size) + byteArrayOf(0x00) + second)

        assertEquals(2, result.size)
        assertEquals(listOf(0x400, 0x401, 0x402, 0x403), result[0].axisValues)
        assertEquals(listOf(0x410, 0x420, 0x430, 0x440), result[1].axisValues)
    }

    @Test
    fun `rejects corrupt outer and inner checksums`() {
        val valid = stickFrame(axisPayload(0x400, 0x400, 0x400, 0x400))
        val badOuter = valid.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() }
        val badInner = valid.copyOf().also {
            val innerChecksum = it.lastIndex - 1
            it[innerChecksum] = (it[innerChecksum] + 1).toByte()
            it[it.lastIndex] = checksum(it, 1, it.lastIndex)
        }

        val result = RcSimulatorAccessoryDecoder().append(badOuter + badInner + valid)

        assertEquals(1, result.size)
    }

    @Test
    fun `decodes checksum valid stock button events without guessing state semantics`() {
        val result = RcSimulatorAccessoryDecoder()
            .appendFrames(STATIC_PHOTO_BUTTON_FIXTURE)
            .single() as RcSimulatorButtonFrame

        assertEquals(3, result.outerChannel)
        assertEquals(0x10, result.messageId)
        assertEquals(0x2A, result.sequence)
        assertEquals(4, result.eventId)
        assertEquals(1, result.stateValue)
        assertEquals("PHOTO", result.controlName)
        assertEquals(8, result.eventPayload.size)
    }

    @Test
    fun `rejects corrupt button CRC and retains a following stick frame`() {
        val corruptButton = buttonFrame(eventId = 2, state = 1).also {
            val innerCrcLow = it.lastIndex - 2
            it[innerCrcLow] = (it[innerCrcLow] + 1).toByte()
            it[it.lastIndex] = checksum(it, 1, it.lastIndex)
        }
        val stick = stickFrame(axisPayload(0x400, 0x410, 0x420, 0x430))

        val result = RcSimulatorAccessoryDecoder().appendFrames(corruptButton + stick)

        assertEquals(1, result.size)
        assertTrue(result.single() is RcSimulatorStickFrame)
    }

    @Test
    fun `reset discards a partial frame`() {
        val frame = stickFrame(axisPayload(0x400, 0x400, 0x400, 0x400))
        val decoder = RcSimulatorAccessoryDecoder()
        assertTrue(decoder.append(frame.copyOfRange(0, 4)).isEmpty())

        decoder.reset()

        assertTrue(decoder.append(frame.copyOfRange(4, frame.size)).isEmpty())
    }

    private fun axisPayload(vararg axes: Int): ByteArray = axes.flatMap { value ->
        listOf((value ushr 8).toByte(), value.toByte())
    }.toByteArray()

    private fun stickFrame(controlPayload: ByteArray): ByteArray =
        checkedFrame(0xA5, 3, checkedFrame(0xAA, 3, controlPayload))

    private fun buttonFrame(eventId: Int, state: Int, sequence: Int = 0): ByteArray {
        val payload = byteArrayOf(eventId.toByte(), state.toByte(), 0, 0, 0, 0, 0, 0)
        val inner = byteArrayOf(
            0xFE.toByte(), payload.size.toByte(), sequence.toByte(),
            0x10, 1, 0xF0.toByte()
        ) + payload + byteArrayOf(0, 0)
        val crc = x25Crc(inner, 1, inner.size - 2)
        inner[inner.size - 2] = crc.toByte()
        inner[inner.size - 1] = (crc ushr 8).toByte()
        return checkedFrame(0xA5, 3, inner)
    }

    private fun checkedFrame(header: Int, channel: Int, payload: ByteArray): ByteArray {
        val result = byteArrayOf(header.toByte(), channel.toByte(), (payload.size + 1).toByte()) +
            payload + byteArrayOf(0)
        result[result.lastIndex] = checksum(result, 1, result.lastIndex)
        return result
    }

    private fun checksum(bytes: ByteArray, start: Int, endExclusive: Int): Byte =
        (bytes.copyOfRange(start, endExclusive).sumOf { it.toInt() and 0xFF } and 0xFF).toByte()

    private fun x25Crc(bytes: ByteArray, start: Int, endExclusive: Int): Int {
        var crc = 0xFFFF
        for (index in start until endExclusive) {
            var temporary = (bytes[index].toInt() and 0xFF) xor (crc and 0xFF)
            temporary = (temporary xor (temporary shl 4)) and 0xFF
            crc = ((crc ushr 8) xor (temporary shl 8) xor (temporary shl 3) xor
                (temporary ushr 4)) and 0xFFFF
        }
        return crc
    }

    private companion object {
        val STATIC_PHOTO_BUTTON_FIXTURE = intArrayOf(
            0xA5, 0x03, 0x11,
            0xFE, 0x08, 0x2A, 0x10, 0x01, 0xF0,
            0x04, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0xEB, 0xC0,
            0xF5
        ).map { it.toByte() }.toByteArray()
    }
}
