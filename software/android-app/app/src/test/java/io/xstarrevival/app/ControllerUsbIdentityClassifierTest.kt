package io.xstarrevival.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ControllerUsbIdentityClassifierTest {
    @Test
    fun `recognizes standard Autel Starlink accessory`() {
        val state = ControllerUsbIdentityClassifier.classify(
            listOf(ControllerUsbIdentity("com.autel", "Starlink", "1.0"))
        )

        assertEquals(ControllerUsbStatus.XSTAR, state.status)
        assertEquals(ControllerUsbTransport.ACCESSORY, state.transport)
        assertTrue(state.controllerDetected)
    }

    @Test
    fun `recognizes exact legacy X-Star controller identity`() {
        val identity = ControllerUsbIdentity("ammlab.org", "HelloADK", "1.0")

        val state = ControllerUsbIdentityClassifier.classify(listOf(identity))

        assertEquals(ControllerUsbStatus.XSTAR_LEGACY, state.status)
        assertEquals(ControllerUsbTransport.ACCESSORY, state.transport)
        assertEquals(identity, state.identity)
        assertTrue(state.controllerDetected)
    }

    @Test
    fun `does not broadly accept similar HelloADK accessories`() {
        val state = ControllerUsbIdentityClassifier.classify(
            listOf(ControllerUsbIdentity("someone.example", "HelloADK", "1.0"))
        )

        assertEquals(ControllerUsbStatus.OTHER_ACCESSORY, state.status)
        assertFalse(state.controllerDetected)
    }

    @Test
    fun `prefers an Autel identity if multiple accessories are reported`() {
        val state = ControllerUsbIdentityClassifier.classify(
            listOf(
                ControllerUsbIdentity("example.org", "Other", "1.0"),
                ControllerUsbIdentity("com.autel", "Autel Explorer", "1.0")
            )
        )

        assertEquals(ControllerUsbStatus.XSTAR, state.status)
        assertEquals(ControllerUsbTransport.ACCESSORY, state.transport)
    }

    @Test
    fun `reports disconnected for an empty inventory`() {
        val state = ControllerUsbIdentityClassifier.classify(emptyList())

        assertEquals(ControllerUsbStatus.DISCONNECTED, state.status)
        assertFalse(state.controllerDetected)
    }

    @Test
    fun `recognizes the direct Autel remote controller USB identity`() {
        val directRemote = ControllerUsbIdentity(
            manufacturer = "Autel",
            model = "Remote Control",
            version = "2.00",
            vendorId = 0x6175,
            productId = 0x5243
        )

        val state = ControllerUsbIdentityClassifier.classify(
            accessories = emptyList(),
            devices = listOf(directRemote)
        )

        assertEquals(ControllerUsbStatus.XSTAR, state.status)
        assertEquals(ControllerUsbTransport.DIRECT_CDC, state.transport)
        assertEquals(directRemote, state.identity)
        assertTrue(state.controllerDetected)
    }

    @Test
    fun `does not trust a lookalike direct USB device by name alone`() {
        val lookalike = ControllerUsbIdentity(
            manufacturer = "Autel",
            model = "Remote Control",
            version = "2.00",
            vendorId = 0x1234,
            productId = 0x5678
        )

        val state = ControllerUsbIdentityClassifier.classify(
            accessories = emptyList(),
            devices = listOf(lookalike)
        )

        assertEquals(ControllerUsbStatus.OTHER_ACCESSORY, state.status)
        assertFalse(state.controllerDetected)
    }
}
