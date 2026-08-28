package io.xstarrevival.core.replay

import io.xstarrevival.core.adapter.OpenXStarPlatformAdapter
import io.xstarrevival.core.model.ConnectionState
import io.xstarrevival.core.protocol.mavlink.StandardMavlinkDecoder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureReplayPlatformIntegrationTest {
    @Test
    fun `demo capture flows through open adapter into normalized state`() = runTest {
        val transport = CaptureReplayTransport(this, StandardMavlinkDemoCapture.chunks, "demo-capture")
        val platform = OpenXStarPlatformAdapter(this, transport, StandardMavlinkDecoder())

        try {
            platform.connect()
            runCurrent()
            transport.restart()
            advanceUntilIdle()

            val state = platform.state.value
            assertEquals(ConnectionState.Connected("demo-capture", "MAVLink quadrotor"), state.connection)
            assertEquals("MAVLink quadrotor", state.aircraft.productName)
            assertEquals(14, state.navigation.satellites)
            assertEquals("3D FIX", state.navigation.gpsFix)
            assertEquals(76, state.battery.percent)
            assertEquals(4, state.battery.cells.size)
            assertEquals(6L, state.diagnostics.counters["mavlink_decoded_frames"])
            assertEquals(1L, state.diagnostics.counters["mavlink_opaque_frames"])
            assertEquals(1L, state.diagnostics.counters["mavlink_crc_failures"])
            assertEquals(CaptureReplayStatus.COMPLETE, transport.playback.value.status)
            assertTrue(transport.playback.value.progress == 1f)
        } finally {
            platform.disconnect()
            advanceUntilIdle()
        }
    }
}
