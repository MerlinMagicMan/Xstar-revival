package io.xstarrevival.core.protocol.mavlink

import io.xstarrevival.core.adapter.OpenXStarPlatformAdapter
import io.xstarrevival.core.adapter.OpenXStarTransport
import io.xstarrevival.core.model.ConnectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StandardMavlinkDecoderTest {
    @Test
    fun `chunked passive capture becomes normalized platform state`() = runTest {
        val capture = loadCapture("/captures/standard-mavlink-replay.hex")
        val chunkSizes = intArrayOf(1, 2, 5, 3, 17, 4, 29, 7, 11)
        val chunks = capture.chunkedBy(chunkSizes)
        val transport = object : OpenXStarTransport {
            override val description = "fixture-capture"
            override val incoming = flow {
                chunks.forEach { emit(it) }
            }
            override suspend fun connect() = Unit
            override suspend fun disconnect() = Unit
        }
        val platform = OpenXStarPlatformAdapter(this, transport, StandardMavlinkDecoder())

        try {
            platform.connect()
            advanceUntilIdle()
            val state = platform.state.value

            assertEquals(ConnectionState.Connected("fixture-capture", "MAVLink quadrotor"), state.connection)
            assertEquals("MAVLink quadrotor", state.aircraft.productName)
            assertEquals(true, state.aircraft.armed)
            assertNull(state.aircraft.flightMode, "custom_mode remains opaque without an autopilot dialect")

            assertNear(41.5882234, state.navigation.latitudeDeg)
            assertNear(-93.5822234, state.navigation.longitudeDeg)
            assertEquals(14, state.navigation.satellites)
            assertEquals("3D FIX", state.navigation.gpsFix)
            assertNear(42.0, state.navigation.altitudeM)
            assertNear(5.0, state.navigation.groundSpeedMps)
            assertNear(1.25, state.navigation.verticalSpeedMps)

            assertNear(5.729578, state.attitude.rollDeg, 0.00001)
            assertNear(-11.459156, state.attitude.pitchDeg, 0.00001)
            assertNear(171.887339, state.attitude.yawDeg, 0.00001)

            assertEquals(76, state.battery.percent)
            assertNear(15.42, state.battery.packVoltageV)
            assertNear(4.10, state.battery.currentA)
            assertNear(28.50, state.battery.temperatureC)
            assertEquals(4, state.battery.cells.size)
            assertNear(3.85, state.battery.cells[0].voltageV)

            assertEquals(8L, state.diagnostics.counters["mavlink_frames"])
            assertEquals(6L, state.diagnostics.counters["mavlink_decoded_frames"])
            assertEquals(1L, state.diagnostics.counters["mavlink_opaque_frames"])
            assertEquals(1L, state.diagnostics.counters["mavlink_crc_failures"])
            assertEquals(1L, state.diagnostics.counters["mavlink_heartbeats"])
        } finally {
            platform.disconnect()
            advanceUntilIdle()
        }
    }

    @Test
    fun `reset discards an incomplete frame tail`() {
        val decoder = StandardMavlinkDecoder()
        assertTrue(decoder.decode(hex("fe090a0101004433")).isEmpty())
        decoder.reset()
        assertTrue(decoder.decode(hex("2211020c8004033be9")).isEmpty())
    }

    private fun loadCapture(path: String): ByteArray {
        val text = checkNotNull(javaClass.getResource(path)) { "Missing capture fixture $path" }.readText()
        return hex(text.lineSequence().filterNot { it.trimStart().startsWith("#") }.joinToString(""))
    }

    private fun hex(value: String): ByteArray {
        val compact = value.filterNot(Char::isWhitespace)
        require(compact.length % 2 == 0)
        return ByteArray(compact.length / 2) { index ->
            compact.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun ByteArray.chunkedBy(sizes: IntArray): List<ByteArray> {
        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        var sizeIndex = 0
        while (offset < size) {
            val end = (offset + sizes[sizeIndex % sizes.size]).coerceAtMost(size)
            chunks += copyOfRange(offset, end)
            offset = end
            sizeIndex += 1
        }
        return chunks
    }

    private fun assertNear(expected: Double, actual: Double?, tolerance: Double = 0.0000001) {
        assertTrue(actual != null && abs(expected - actual) <= tolerance, "Expected $expected, got $actual")
    }
}
