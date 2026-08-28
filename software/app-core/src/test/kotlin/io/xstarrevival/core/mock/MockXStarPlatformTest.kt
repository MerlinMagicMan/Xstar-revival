package io.xstarrevival.core.mock

import io.xstarrevival.core.model.ConnectionState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MockXStarPlatformTest {
    @Test
    fun connectsWithRealisticReadOnlyState() = runTest {
        val platform = MockXStarPlatform(this)
        try {
            platform.connect()

            val state = platform.state.value
            assertIs<ConnectionState.Connected>(state.connection)
            assertEquals("X-Star Premium", state.aircraft.productName)
            assertEquals(4, state.battery.cells.size)
            assertNotNull(state.battery.cellDeltaV)
            assertTrue((state.battery.cellDeltaV ?: 1.0) < 0.05)
            assertEquals("H.264", state.camera.video.codec)
            assertTrue(state.camera.video.receiving)
        } finally {
            platform.disconnect()
        }
    }

    @Test
    fun disconnectClearsLiveState() = runTest {
        val platform = MockXStarPlatform(this)
        platform.connect()
        platform.disconnect()

        assertIs<ConnectionState.Disconnected>(platform.state.value.connection)
        assertEquals(emptyList(), platform.state.value.battery.cells)
    }
}
