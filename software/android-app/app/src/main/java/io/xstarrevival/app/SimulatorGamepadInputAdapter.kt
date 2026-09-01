package io.xstarrevival.app

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import io.xstarrevival.core.sim.SimulatorControlInput
import io.xstarrevival.core.sim.SimulatorControllerAction
import io.xstarrevival.core.sim.SimulatorControllerInputMapper
import io.xstarrevival.core.sim.SimulatorControllerResponseProfile
import io.xstarrevival.core.sim.SimulatorPhysicalControllerInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SimulatorControllerInputSource { NONE, ANDROID_HID_GAMEPAD }

data class SimulatorControllerInputUiState(
    val connected: Boolean = false,
    val source: SimulatorControllerInputSource = SimulatorControllerInputSource.NONE,
    val deviceId: Int? = null,
    val deviceName: String? = null,
    val controls: SimulatorControlInput = SimulatorControlInput(),
    val lastAction: SimulatorControllerAction? = null,
    val eventCount: Long = 0,
    val lastEventAtEpochMs: Long? = null
)

internal object SimulatorControllerButtonMap {
    fun actionFor(keyCode: Int, assignments: Map<String, String>): SimulatorControllerAction? = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_START -> SimulatorControllerAction.TOGGLE_ARM
        KeyEvent.KEYCODE_BUTTON_A -> SimulatorControllerAction.TAKE_OFF
        KeyEvent.KEYCODE_BUTTON_B -> SimulatorControllerAction.LAND
        KeyEvent.KEYCODE_BUTTON_X,
        KeyEvent.KEYCODE_CAMERA -> SimulatorControllerAction.TAKE_PHOTO
        KeyEvent.KEYCODE_BUTTON_Y,
        KeyEvent.KEYCODE_MEDIA_RECORD -> SimulatorControllerAction.TOGGLE_RECORDING
        KeyEvent.KEYCODE_BUTTON_THUMBL -> SimulatorControllerAction.RETURN_TO_HOME
        KeyEvent.KEYCODE_BUTTON_THUMBR,
        KeyEvent.KEYCODE_BACK -> SimulatorControllerAction.CANCEL_RETURN_TO_HOME
        KeyEvent.KEYCODE_BUTTON_SELECT -> SimulatorControllerAction.TOGGLE_CAMERA_VIEW
        KeyEvent.KEYCODE_BUTTON_L1 -> configuredAction(assignments["C1"])
        KeyEvent.KEYCODE_BUTTON_R1 -> configuredAction(assignments["C2"])
        else -> null
    }

    private fun configuredAction(value: String?): SimulatorControllerAction? = when (value) {
        "TAKE_PHOTO" -> SimulatorControllerAction.TAKE_PHOTO
        "RECORD" -> SimulatorControllerAction.TOGGLE_RECORDING
        "RECENTER_GIMBAL" -> SimulatorControllerAction.RECENTER_GIMBAL
        "VIEW" -> SimulatorControllerAction.TOGGLE_CAMERA_VIEW
        else -> null
    }
}

/** Routes Android HID/gamepad events exclusively into the local simulator callbacks. */
internal class SimulatorGamepadInputAdapter(
    private val profileProvider: () -> SimulatorControllerResponseProfile,
    private val assignmentsProvider: () -> Map<String, String>,
    private val onControls: (SimulatorControlInput) -> Unit,
    private val onAction: (SimulatorControllerAction) -> Unit,
    private val clockMs: () -> Long = System::currentTimeMillis
) {
    private val mutableState = MutableStateFlow(SimulatorControllerInputUiState())
    val state: StateFlow<SimulatorControllerInputUiState> = mutableState.asStateFlow()

    fun handleMotion(event: MotionEvent): Boolean {
        if (!event.isControllerMotion()) return false
        val device = event.device ?: return false
        val leftX = event.normalizedAxis(device, MotionEvent.AXIS_X)
        val leftY = event.normalizedAxis(device, MotionEvent.AXIS_Y)
        val rightXAxis = device.firstPresentAxis(event.source, MotionEvent.AXIS_Z, MotionEvent.AXIS_RX)
        val rightYAxis = device.firstPresentAxis(event.source, MotionEvent.AXIS_RZ, MotionEvent.AXIS_RY)
        val rightX = rightXAxis?.let { event.normalizedAxis(device, it) } ?: 0.0
        val rightY = rightYAxis?.let { event.normalizedAxis(device, it) } ?: 0.0
        val gimbalAxis = device.firstPresentAxis(
            event.source,
            *listOf(MotionEvent.AXIS_RY, MotionEvent.AXIS_RX, MotionEvent.AXIS_BRAKE, MotionEvent.AXIS_GAS)
                .filterNot { it == rightXAxis || it == rightYAxis }
                .toIntArray()
        )
        val gimbal = when (gimbalAxis) {
            MotionEvent.AXIS_BRAKE -> event.normalizedAxis(device, MotionEvent.AXIS_BRAKE) -
                event.normalizedAxis(device, MotionEvent.AXIS_GAS)
            else -> gimbalAxis?.let { event.normalizedAxis(device, it) } ?: 0.0
        }
        val controls = SimulatorControllerInputMapper.map(
            SimulatorPhysicalControllerInput(leftX, leftY, rightX, rightY, gimbal),
            profileProvider()
        )
        onControls(controls)
        publish(device, controls, mutableState.value.lastAction)
        return true
    }

    fun handleKey(event: KeyEvent): Boolean {
        if (!event.isControllerKey()) return false
        val action = SimulatorControllerButtonMap.actionFor(event.keyCode, assignmentsProvider()) ?: return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            onAction(action)
            publish(event.device, mutableState.value.controls, action)
        }
        return true
    }

    fun release() {
        onControls(SimulatorControlInput())
        mutableState.value = mutableState.value.copy(controls = SimulatorControlInput())
    }

    private fun publish(device: InputDevice?, controls: SimulatorControlInput, action: SimulatorControllerAction?) {
        val current = mutableState.value
        mutableState.value = current.copy(
            connected = device != null,
            source = if (device == null) SimulatorControllerInputSource.NONE else SimulatorControllerInputSource.ANDROID_HID_GAMEPAD,
            deviceId = device?.id,
            deviceName = device?.name,
            controls = controls,
            lastAction = action,
            eventCount = current.eventCount + 1,
            lastEventAtEpochMs = clockMs()
        )
    }
}

private fun MotionEvent.isControllerMotion(): Boolean =
    action == MotionEvent.ACTION_MOVE &&
        (source.hasInputSource(InputDevice.SOURCE_JOYSTICK) || source.hasInputSource(InputDevice.SOURCE_GAMEPAD))

private fun KeyEvent.isControllerKey(): Boolean =
    source.hasInputSource(InputDevice.SOURCE_GAMEPAD) || source.hasInputSource(InputDevice.SOURCE_JOYSTICK)

private fun Int.hasInputSource(expected: Int): Boolean = this and expected == expected

private fun InputDevice.firstPresentAxis(source: Int, vararg axes: Int): Int? =
    axes.firstOrNull { getMotionRange(it, source) != null || motionRanges.any { range -> range.axis == it } }

private fun MotionEvent.normalizedAxis(device: InputDevice, axis: Int): Double {
    val range = device.getMotionRange(axis, source) ?: device.motionRanges.firstOrNull { it.axis == axis }
    val value = getAxisValue(axis).toDouble()
    if (range == null) return value.coerceIn(-1.0, 1.0)
    val center = (range.min + range.max) / 2.0
    val halfRange = ((range.max - range.min) / 2.0).coerceAtLeast(.0001)
    val normalized = ((value - center) / halfRange).coerceIn(-1.0, 1.0)
    val flat = (range.flat / halfRange).coerceIn(0.0, 1.0)
    return if (kotlin.math.abs(normalized) <= flat) 0.0 else normalized
}
