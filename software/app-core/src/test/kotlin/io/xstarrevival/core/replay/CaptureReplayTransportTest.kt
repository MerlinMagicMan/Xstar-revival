package io.xstarrevival.core.replay

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureReplayTransportTest {
    @Test
    fun `play pause and resume preserve byte order and progress`() = runTest {
        val chunks = listOf(
            CaptureChunk(100, byteArrayOf(1)),
            CaptureChunk(100, byteArrayOf(2)),
            CaptureChunk(100, byteArrayOf(3))
        )
        val transport = CaptureReplayTransport(this, chunks)
        val received = mutableListOf<ByteArray>()
        val collectJob = launch { transport.incoming.toList(received) }

        transport.connect()
        transport.play()
        advanceTimeBy(150)
        runCurrent()
        transport.pause()

        assertEquals(CaptureReplayStatus.PAUSED, transport.playback.value.status)
        assertEquals(1, transport.playback.value.chunkIndex)
        assertContentEquals(byteArrayOf(1), received.single())

        transport.setSpeed(2.0)
        transport.play()
        advanceUntilIdle()

        assertEquals(CaptureReplayStatus.COMPLETE, transport.playback.value.status)
        assertEquals(3, transport.playback.value.chunkIndex)
        assertEquals(1f, transport.playback.value.progress)
        assertContentEquals(byteArrayOf(2), received[1])
        assertContentEquals(byteArrayOf(3), received[2])

        collectJob.cancel()
        transport.disconnect()
    }

    @Test
    fun `restart replays from the first chunk`() = runTest {
        val transport = CaptureReplayTransport(this, listOf(CaptureChunk(0, byteArrayOf(9))))
        val received = mutableListOf<ByteArray>()
        val collectJob = launch { transport.incoming.toList(received) }

        transport.connect()
        transport.restart()
        advanceUntilIdle()
        transport.restart()
        advanceUntilIdle()

        assertEquals(2, received.size)
        assertContentEquals(byteArrayOf(9), received[0])
        assertContentEquals(byteArrayOf(9), received[1])

        collectJob.cancel()
        transport.disconnect()
    }
}
