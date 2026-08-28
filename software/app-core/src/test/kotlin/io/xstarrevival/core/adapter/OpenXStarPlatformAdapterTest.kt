package io.xstarrevival.core.adapter

import io.xstarrevival.core.event.XStarEvent
import io.xstarrevival.core.model.ConnectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class OpenXStarPlatformAdapterTest {
    @Test
    fun `transport chunks become normalized state`() = runTest {
        val incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 1)
        val transport = object : OpenXStarTransport {
            override val incoming = incoming
            override val description = "test-usb"
            override suspend fun connect() = Unit
            override suspend fun disconnect() = Unit
        }
        val decoder = object : OpenXStarDecoder {
            override fun decode(chunk: ByteArray) = listOf(
                XStarEvent.ProductIdentified("X-Star Premium", "2.0.12")
            )
            override fun reset() = Unit
        }

        val platform = OpenXStarPlatformAdapter(this, transport, decoder)
        platform.connect()
        advanceUntilIdle()
        incoming.emit(byteArrayOf(1, 2, 3))
        advanceUntilIdle()

        assertEquals("X-Star Premium", platform.state.value.aircraft.productName)
        assertEquals(ConnectionState.Connected("test-usb", null), platform.state.value.connection)
    }
}
