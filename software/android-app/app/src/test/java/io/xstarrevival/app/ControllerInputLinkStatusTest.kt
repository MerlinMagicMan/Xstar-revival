package io.xstarrevival.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ControllerInputLinkStatusTest {
    private val connectedController = ControllerUsbUiState(
        status = ControllerUsbStatus.XSTAR_LEGACY,
        identity = ControllerUsbIdentity("ammlab.org", "HelloADK", "1.0")
    )

    @Test
    fun `connected accessory is ready before input check`() {
        assertEquals(
            ControllerInputLinkStatus.USB_READY,
            controllerInputLinkStatus(connectedController, ControllerProbeUiState())
        )
    }

    @Test
    fun `zero byte completed check preserves usb connection and marks stream unavailable`() {
        val probe = ControllerProbeUiState(
            status = ControllerProbeStatus.COMPLETE,
            stopReason = ControllerProbeStopReason.DURATION_LIMIT
        )

        assertEquals(
            ControllerInputLinkStatus.INPUT_STREAM_UNAVAILABLE,
            controllerInputLinkStatus(connectedController, probe)
        )
    }

    @Test
    fun `received bytes mark input stream active`() {
        val probe = ControllerProbeUiState(
            status = ControllerProbeStatus.COMPLETE,
            bytesRead = 80,
            chunksRead = 5,
            stopReason = ControllerProbeStopReason.USER
        )

        assertEquals(
            ControllerInputLinkStatus.STREAMING,
            controllerInputLinkStatus(connectedController, probe)
        )
    }

    @Test
    fun `missing accessory wins over stale probe result`() {
        val probe = ControllerProbeUiState(
            status = ControllerProbeStatus.COMPLETE,
            bytesRead = 80,
            chunksRead = 5
        )

        assertEquals(
            ControllerInputLinkStatus.DISCONNECTED,
            controllerInputLinkStatus(ControllerUsbUiState(), probe)
        )
    }
}
