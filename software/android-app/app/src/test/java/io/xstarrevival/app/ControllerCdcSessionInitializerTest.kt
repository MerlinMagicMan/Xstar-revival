package io.xstarrevival.app

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ControllerCdcSessionInitializerTest {
    @Test
    fun `uses the standard class interface request type`() {
        assertEquals(0x21, ControllerCdcSessionInitializer.REQUEST_TYPE)
        assertEquals(0x20, ControllerCdcSessionInitializer.SET_LINE_CODING_REQUEST)
        assertEquals(0x22, ControllerCdcSessionInitializer.SET_CONTROL_LINE_STATE_REQUEST)
        assertEquals(0x03, ControllerCdcSessionInitializer.DTR_RTS_ENABLED)
    }

    @Test
    fun `encodes 115200 baud eight data bits no parity and one stop bit`() {
        assertEquals(115_200, ControllerCdcSessionInitializer.BAUD_RATE)
        assertContentEquals(
            byteArrayOf(0x00, 0xC2.toByte(), 0x01, 0x00, 0x00, 0x00, 0x08),
            ControllerCdcSessionInitializer.lineCoding1152008N1()
        )
    }
}
