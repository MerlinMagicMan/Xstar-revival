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
        assertTrue(state.controllerDetected)
    }

    @Test
    fun `recognizes exact legacy X-Star controller identity`() {
        val identity = ControllerUsbIdentity("ammlab.org", "HelloADK", "1.0")

        val state = ControllerUsbIdentityClassifier.classify(listOf(identity))

        assertEquals(ControllerUsbStatus.XSTAR_LEGACY, state.status)
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
    }

    @Test
    fun `reports disconnected for an empty inventory`() {
        val state = ControllerUsbIdentityClassifier.classify(emptyList())

        assertEquals(ControllerUsbStatus.DISCONNECTED, state.status)
        assertFalse(state.controllerDetected)
    }
}
