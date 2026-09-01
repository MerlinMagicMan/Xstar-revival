package io.xstarrevival.app

import android.view.KeyEvent
import io.xstarrevival.core.sim.SimulatorControllerAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SimulatorControllerButtonMapTest {
    @Test
    fun simulatorSafetyActionsHaveDeterministicButtons() {
        assertEquals(SimulatorControllerAction.TOGGLE_ARM, SimulatorControllerButtonMap.actionFor(KeyEvent.KEYCODE_BUTTON_START, emptyMap()))
        assertEquals(SimulatorControllerAction.TAKE_OFF, SimulatorControllerButtonMap.actionFor(KeyEvent.KEYCODE_BUTTON_A, emptyMap()))
        assertEquals(SimulatorControllerAction.LAND, SimulatorControllerButtonMap.actionFor(KeyEvent.KEYCODE_BUTTON_B, emptyMap()))
        assertEquals(SimulatorControllerAction.RETURN_TO_HOME, SimulatorControllerButtonMap.actionFor(KeyEvent.KEYCODE_BUTTON_THUMBL, emptyMap()))
        assertEquals(SimulatorControllerAction.CANCEL_RETURN_TO_HOME, SimulatorControllerButtonMap.actionFor(KeyEvent.KEYCODE_BUTTON_THUMBR, emptyMap()))
    }

    @Test
    fun cButtonsHonorOnlySupportedSimulatorAssignments() {
        assertEquals(
            SimulatorControllerAction.TAKE_PHOTO,
            SimulatorControllerButtonMap.actionFor(KeyEvent.KEYCODE_BUTTON_L1, mapOf("C1" to "TAKE_PHOTO"))
        )
        assertEquals(
            SimulatorControllerAction.RECENTER_GIMBAL,
            SimulatorControllerButtonMap.actionFor(KeyEvent.KEYCODE_BUTTON_R1, mapOf("C2" to "RECENTER_GIMBAL"))
        )
        assertEquals(
            SimulatorControllerAction.TOGGLE_CAMERA_VIEW,
            SimulatorControllerButtonMap.actionFor(KeyEvent.KEYCODE_BUTTON_R1, mapOf("C2" to "VIEW"))
        )
        assertNull(SimulatorControllerButtonMap.actionFor(KeyEvent.KEYCODE_BUTTON_L1, mapOf("C1" to "MAP")))
    }

    @Test
    fun selectSwitchesSimulatorCameraView() {
        assertEquals(
            SimulatorControllerAction.TOGGLE_CAMERA_VIEW,
            SimulatorControllerButtonMap.actionFor(KeyEvent.KEYCODE_BUTTON_SELECT, emptyMap())
        )
    }
}
