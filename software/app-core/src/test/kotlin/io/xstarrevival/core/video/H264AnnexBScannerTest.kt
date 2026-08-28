package io.xstarrevival.core.video

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class H264AnnexBScannerTest {
    @Test
    fun `groups delimiter-framed pictures across arbitrary byte chunks`() {
        val stream = annexB(
            nal(9, 0xf0),
            nal(7, 0x42, 0x00, 0x1e),
            nal(8, 0xce, 0x06, 0xe2),
            nal(5, 0x80),
            nal(9, 0xf0),
            nal(1, 0x80)
        )
        val scanner = H264AnnexBScanner(frameRate = 20)
        val units = mutableListOf<H264AccessUnit>()

        listOf(1, 2, 5, 3, 11, 4, 7, 100).fold(0) { offset, size ->
            val end = (offset + size).coerceAtMost(stream.size)
            if (offset < end) units += scanner.push(stream.copyOfRange(offset, end))
            end
        }
        units += scanner.endOfStream()

        assertEquals(2, units.size)
        assertTrue(units[0].keyFrame)
        assertEquals(setOf(9, 7, 8, 5), units[0].nalTypes)
        assertEquals(0L, units[0].presentationTimeUs)
        assertFalse(units[1].keyFrame)
        assertEquals(50_000L, units[1].presentationTimeUs)
        assertContentEquals(stream, units[0].bytes + units[1].bytes)
    }

    @Test
    fun `uses standard first macroblock field when delimiters are absent`() {
        val scanner = H264AnnexBScanner()
        val stream = annexB(
            nal(7, 0x42),
            nal(8, 0xce),
            nal(5, 0x80),
            nal(1, 0x40),
            nal(1, 0x80)
        )

        val units = scanner.push(stream) + scanner.endOfStream()

        assertEquals(2, units.size)
        assertEquals(setOf(7, 8, 5, 1), units[0].nalTypes)
        assertEquals(setOf(1), units[1].nalTypes)
    }

    @Test
    fun `ignores opaque prefix and resets timestamps deterministically`() {
        val scanner = H264AnnexBScanner(frameRate = 10)
        val stream = byteArrayOf(0x58, 0x53, 0x52) + annexB(nal(9, 0xf0), nal(5, 0x80))

        val first = scanner.push(stream) + scanner.endOfStream()
        scanner.reset()
        val second = scanner.push(stream) + scanner.endOfStream()

        assertEquals(1, first.size)
        assertEquals(0L, first.single().presentationTimeUs)
        assertEquals(0L, second.single().presentationTimeUs)
        assertFalse(first.single().bytes.startsWith(byteArrayOf(0x58, 0x53, 0x52)))
    }

    private fun annexB(vararg nals: ByteArray): ByteArray = nals.fold(ByteArray(0)) { all, nal ->
        all + byteArrayOf(0, 0, 0, 1) + nal
    }

    private fun nal(type: Int, vararg payload: Int): ByteArray =
        byteArrayOf((0x60 or type).toByte()) + payload.map(Int::toByte).toByteArray()

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}
