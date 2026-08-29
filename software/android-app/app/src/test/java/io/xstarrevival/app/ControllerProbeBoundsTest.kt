package io.xstarrevival.app

import kotlin.test.Test
import kotlin.test.assertEquals

class ControllerProbeBoundsTest {
    @Test
    fun `keeps a full chunk below the limit`() {
        assertEquals(512, ControllerProbeBounds.bytesToKeep(1_024, 512, 4_096))
    }

    @Test
    fun `truncates the final chunk at the limit`() {
        assertEquals(24, ControllerProbeBounds.bytesToKeep(1_000, 512, 1_024))
    }

    @Test
    fun `keeps nothing after the limit`() {
        assertEquals(0, ControllerProbeBounds.bytesToKeep(1_024, 512, 1_024))
    }
}
