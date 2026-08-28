package io.xstarrevival.core.video

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class H264VideoSourceTest {
    @Test
    fun `frame owns callback bytes and exposes only valid payload`() {
        val callbackBuffer = byteArrayOf(0, 0, 1, 0x65, 7, 8, 99, 100)
        val frame = H264VideoFrame(callbackBuffer, isKeyFrame = true, validSize = 6, presentationTimestamp = 10)
        callbackBuffer[4] = 42

        assertContentEquals(byteArrayOf(0, 0, 1, 0x65, 7, 8), frame.payload())
    }

    @Test
    fun `invalid callback size is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            H264VideoFrame(byteArrayOf(1, 2), isKeyFrame = false, validSize = 3, presentationTimestamp = 0)
        }
    }
}
