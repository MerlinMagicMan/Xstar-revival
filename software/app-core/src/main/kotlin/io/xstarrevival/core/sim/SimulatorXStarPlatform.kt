package io.xstarrevival.core.sim

import io.xstarrevival.core.XStarPlatform
import io.xstarrevival.core.model.ConnectionState
import io.xstarrevival.core.model.XStarState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Software-only platform. Control methods mutate this local model and are never connected to a live adapter.
 */
class SimulatorXStarPlatform(
    private val scope: CoroutineScope,
    private val tickMs: Long = 50L
) : XStarPlatform {
    override val name: String = "Virtual X-Star Flight Simulator"

    private val mutableState = MutableStateFlow(XStarState())
    override val state: StateFlow<XStarState> = mutableState.asStateFlow()

    private var snapshot = SimulatorSnapshot()
    private var input = SimulatorControlInput()
    private var ticker: Job? = null

    override suspend fun connect() {
        ticker?.cancel()
        snapshot = SimulatorSnapshot()
        input = SimulatorControlInput()
        publish()
        ticker = scope.launch {
            while (isActive) {
                delay(tickMs)
                snapshot = SimulatorFlightModel.step(snapshot, input, tickMs / 1000.0)
                publish()
            }
        }
    }

    override suspend fun disconnect() {
        ticker?.cancel()
        ticker = null
        input = SimulatorControlInput()
        mutableState.value = XStarState(connection = ConnectionState.Disconnected)
    }

    override suspend fun refresh() = publish()

    fun setControls(value: SimulatorControlInput) {
        input = value.bounded()
    }

    fun toggleArm() {
        snapshot = SimulatorFlightModel.toggleArm(snapshot)
        publish()
    }

    fun arm() {
        snapshot = SimulatorFlightModel.arm(snapshot)
        publish()
    }

    fun disarm() {
        snapshot = SimulatorFlightModel.disarm(snapshot)
        publish()
    }

    fun takeOff() {
        snapshot = SimulatorFlightModel.takeOff(snapshot)
        publish()
    }

    fun land() {
        snapshot = SimulatorFlightModel.land(snapshot)
        publish()
    }

    fun toggleRecording() {
        snapshot = SimulatorFlightModel.toggleRecording(snapshot)
        publish()
    }

    fun setRecording(recording: Boolean) {
        snapshot = SimulatorFlightModel.setRecording(snapshot, recording)
        publish()
    }

    fun setGimbalPitch(pitchDeg: Double) {
        snapshot = SimulatorFlightModel.setGimbalPitch(snapshot, pitchDeg)
        publish()
    }

    private fun publish() {
        mutableState.value = SimulatorFlightModel.toXStarState(snapshot)
    }
}
