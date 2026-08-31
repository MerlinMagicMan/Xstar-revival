package io.xstarrevival.core.sim

import io.xstarrevival.core.XStarPlatform
import io.xstarrevival.core.groundstation.MissionExecutionPhase
import io.xstarrevival.core.groundstation.MissionExecutionState
import io.xstarrevival.core.groundstation.MissionFinishBehavior
import io.xstarrevival.core.groundstation.MissionPlan
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
    private val mutableScenario = MutableStateFlow(SimulatorScenario.NORMAL_FLIGHT)
    val scenario: StateFlow<SimulatorScenario> = mutableScenario.asStateFlow()
    private val mutableMissionExecution = MutableStateFlow(MissionExecutionState())
    val missionExecution: StateFlow<MissionExecutionState> = mutableMissionExecution.asStateFlow()

    private var snapshot = SimulatorSnapshot()
    private var input = SimulatorControlInput()
    private var ticker: Job? = null
    private var frozenLinkLossState: XStarState? = null
    private var missionRuntime: SimulatorMissionRuntime? = null

    override suspend fun connect() {
        ticker?.cancel()
        snapshot = SimulatorSnapshot()
        input = SimulatorControlInput()
        frozenLinkLossState = null
        mutableScenario.value = SimulatorScenario.NORMAL_FLIGHT
        missionRuntime = null
        mutableMissionExecution.value = MissionExecutionState()
        publish()
        ticker = scope.launch {
            while (isActive) {
                delay(tickMs)
                val missionControlsLocked = missionRuntime?.phase in setOf(
                    MissionExecutionPhase.ACTIVE,
                    MissionExecutionPhase.PAUSED
                )
                snapshot = SimulatorFlightModel.step(
                    snapshot,
                    if (missionControlsLocked) SimulatorControlInput() else input,
                    tickMs / 1000.0
                )
                missionRuntime?.let { runtime ->
                    val result = SimulatorMissionModel.step(snapshot, runtime, tickMs / 1000.0)
                    snapshot = result.snapshot
                    missionRuntime = result.runtime
                }
                publish()
            }
        }
    }

    override suspend fun disconnect() {
        ticker?.cancel()
        ticker = null
        input = SimulatorControlInput()
        frozenLinkLossState = null
        missionRuntime = null
        mutableMissionExecution.value = MissionExecutionState()
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

    fun startMission(plan: MissionPlan): Boolean {
        if (missionRuntime?.phase in setOf(MissionExecutionPhase.ACTIVE, MissionExecutionPhase.PAUSED)) return false
        if (
            plan.waypoints.isEmpty() ||
            plan.finishBehavior == MissionFinishBehavior.RETURN_HOME ||
            plan.waypoints.any { it.altitudeM > 120.0 } ||
            plan.waypoints.flatMap { it.actions }.any { it.type !in SimulatorMissionModel.supportedWaypointActions }
        ) return false
        val started = SimulatorMissionModel.start(plan)
        input = SimulatorControlInput()
        missionRuntime = when (mutableScenario.value) {
            SimulatorScenario.WAYPOINT_FAILURE -> SimulatorMissionModel.fail(started, "Waypoint execution failed")
            SimulatorScenario.MISSION_PAUSE -> SimulatorMissionModel.pause(started)
            SimulatorScenario.MISSION_ABORT -> SimulatorMissionModel.abort(started, "Mission aborted by scenario")
            SimulatorScenario.RTH_DURING_MISSION -> SimulatorMissionModel.abort(started, "Mission interrupted by Return-to-Home")
            SimulatorScenario.COMPLETE_LINK_LOSS,
            SimulatorScenario.CONNECTION_LOSS_DURING_MISSION -> SimulatorMissionModel.fail(started, "Aircraft link lost during mission")
            else -> started
        }
        publish()
        return true
    }

    fun pauseMission(): Boolean {
        val paused = missionRuntime?.let(SimulatorMissionModel::pause) ?: return false
        missionRuntime = paused
        publish()
        return true
    }

    fun resumeMission(): Boolean {
        val resumed = missionRuntime?.let(SimulatorMissionModel::resume) ?: return false
        missionRuntime = resumed
        if (mutableScenario.value == SimulatorScenario.MISSION_PAUSE) {
            mutableScenario.value = SimulatorScenario.NORMAL_FLIGHT
        }
        publish()
        return true
    }

    fun abortMission(detail: String = "Mission aborted"): Boolean {
        val runtime = missionRuntime ?: return false
        if (runtime.phase !in setOf(MissionExecutionPhase.ACTIVE, MissionExecutionPhase.PAUSED)) return false
        missionRuntime = SimulatorMissionModel.abort(runtime, detail)
        publish()
        return true
    }

    fun setScenario(value: SimulatorScenario) {
        if (value == mutableScenario.value) return
        frozenLinkLossState = if (value in LINK_LOSS_SCENARIOS) {
            normalizedBaseState()
        } else {
            null
        }
        mutableScenario.value = value
        if (value == SimulatorScenario.FORCED_LANDING) {
            snapshot = SimulatorFlightModel.land(snapshot)
        }
        missionRuntime = when (value) {
            SimulatorScenario.WAYPOINT_FAILURE -> missionRuntime?.let { SimulatorMissionModel.fail(it, "Waypoint execution failed") }
            SimulatorScenario.MISSION_PAUSE -> missionRuntime?.let(SimulatorMissionModel::pause) ?: missionRuntime
            SimulatorScenario.MISSION_ABORT -> missionRuntime?.let { SimulatorMissionModel.abort(it, "Mission aborted by scenario") }
            SimulatorScenario.RTH_DURING_MISSION -> missionRuntime?.let { SimulatorMissionModel.abort(it, "Mission interrupted by Return-to-Home") }
            SimulatorScenario.COMPLETE_LINK_LOSS,
            SimulatorScenario.CONNECTION_LOSS_DURING_MISSION -> missionRuntime?.let {
                SimulatorMissionModel.fail(it, "Aircraft link lost during mission")
            }
            else -> missionRuntime
        }
        publish()
    }

    private fun publish() {
        mutableMissionExecution.value = SimulatorMissionModel.state(snapshot, missionRuntime)
        mutableState.value = SimulatorScenarioApplier.apply(
            frozenLinkLossState ?: normalizedBaseState(),
            mutableScenario.value
        )
    }

    private fun normalizedBaseState(): XStarState {
        val base = SimulatorFlightModel.toXStarState(snapshot)
        val missionPhase = missionRuntime?.phase
        val mode = when (missionPhase) {
            MissionExecutionPhase.ACTIVE -> "WAYPOINT MISSION"
            MissionExecutionPhase.PAUSED -> "MISSION PAUSED"
            else -> null
        }
        return if (mode == null) base else base.copy(aircraft = base.aircraft.copy(flightMode = mode))
    }

    private companion object {
        val LINK_LOSS_SCENARIOS = setOf(
            SimulatorScenario.COMPLETE_LINK_LOSS,
            SimulatorScenario.CONNECTION_LOSS_DURING_MISSION
        )
    }
}
