package io.xstarrevival.core.video

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class H264CaptureWriterTest {
    @Test
    fun `capture starts on keyframe and indexes untouched valid payloads`() {
        val video = ByteArrayOutputStream()
        val index = ByteArrayOutputStream()
        var elapsed = 100L
        val writer = H264CaptureWriter(video, index, 1024, 30_000) { elapsed }

        writer.append(frame(byteArrayOf(9, 9), keyframe = false, validSize = 2, timestamp = 10))
        elapsed = 112L
        writer.append(frame(byteArrayOf(0, 0, 0, 1, 0x65, 7, 99), keyframe = true, validSize = 6, timestamp = 20))
        elapsed = 145L
        writer.append(frame(byteArrayOf(0, 0, 1, 0x41, 8), keyframe = false, validSize = 5, timestamp = 30))
        writer.finish(H264CaptureStopReason.USER)
        writer.close()

        assertContentEquals(
            byteArrayOf(0, 0, 0, 1, 0x65, 7, 0, 0, 1, 0x41, 8),
            video.toByteArray()
        )
        val records = index.toString(Charsets.UTF_8).lineSequence().filter { it.isNotBlank() }.toList()
        assertEquals(4, records.size)
        assertTrue(records[0].contains("source-defined"))
        assertTrue(records[1].contains("\"offset\":0,\"length\":6,\"keyframe\":true,\"codec_setup\":false"))
        assertTrue(records[1].contains("\"sdk_timestamp\":20,\"elapsed_ms\":12"))
        assertTrue(records[2].contains("\"offset\":6,\"length\":5,\"keyframe\":false"))
        assertTrue(records[3].contains("\"dropped_before_keyframe\":1"))
        assertTrue(records[3].contains("\"stop_reason\":\"USER\""))
    }

    @Test
    fun `preserves standard parameter sets immediately before first keyframe`() {
        val video = ByteArrayOutputStream()
        val index = ByteArrayOutputStream()
        var elapsed = 1_000L
        val writer = H264CaptureWriter(video, index, 1024, 30_000) { elapsed }
        val opaque = byteArrayOf(0x58, 0x53, 0x52)
        val setup = byteArrayOf(0, 0, 0, 1, 0x67, 0x42, 0, 0, 1, 0x68, 0xce.toByte())
        val keyframe = byteArrayOf(0, 0, 0, 1, 0x65, 0x80.toByte())

        writer.append(frame(opaque, keyframe = false, validSize = opaque.size, timestamp = 1))
        elapsed = 1_010L
        writer.append(frame(setup, keyframe = false, validSize = setup.size, timestamp = 2))
        elapsed = 1_020L
        writer.append(frame(keyframe, keyframe = true, validSize = keyframe.size, timestamp = 3))
        writer.finish(H264CaptureStopReason.USER)
        writer.close()

        assertContentEquals(setup + keyframe, video.toByteArray())
        val records = index.toString(Charsets.UTF_8).lineSequence().filter { it.isNotBlank() }.toList()
        assertTrue(records[1].contains("\"codec_setup\":true"))
        assertTrue(records[1].contains("\"sdk_timestamp\":2,\"elapsed_ms\":10"))
        assertTrue(records[2].contains("\"keyframe\":true,\"codec_setup\":false"))
        assertTrue(records[3].contains("\"dropped_before_keyframe\":1"))
    }

    @Test
    fun `byte ceiling stops before writing an oversized frame`() {
        val video = ByteArrayOutputStream()
        val index = ByteArrayOutputStream()
        val writer = H264CaptureWriter(video, index, maxBytes = 5, maxDurationMs = 1_000) { 0 }

        val stats = writer.append(frame(byteArrayOf(1, 2, 3, 4, 5, 6), keyframe = true, validSize = 6))
        writer.close()

        assertTrue(stats.complete)
        assertEquals(H264CaptureStopReason.BYTE_LIMIT, stats.stopReason)
        assertEquals(0, stats.framesWritten)
        assertEquals(0, video.size())
    }

    @Test
    fun `duration ceiling is monotonic and final`() {
        val video = ByteArrayOutputStream()
        val index = ByteArrayOutputStream()
        var elapsed = 4_000L
        val writer = H264CaptureWriter(video, index, maxBytes = 100, maxDurationMs = 100) { elapsed }
        elapsed = 4_100L

        val stopped = writer.append(frame(byteArrayOf(1), keyframe = true, validSize = 1))
        val unchanged = writer.append(frame(byteArrayOf(2), keyframe = true, validSize = 1))
        writer.close()

        assertEquals(H264CaptureStopReason.DURATION_LIMIT, stopped.stopReason)
        assertEquals(stopped, unchanged)
        assertFalse(stopped.synchronized)
        assertEquals(0, video.size())
    }

    private fun frame(
        bytes: ByteArray,
        keyframe: Boolean,
        validSize: Int,
        timestamp: Long = 0
    ) = H264VideoFrame(bytes, keyframe, validSize, timestamp)
}
