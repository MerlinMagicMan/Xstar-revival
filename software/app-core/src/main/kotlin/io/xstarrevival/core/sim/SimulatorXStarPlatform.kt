package io.xstarrevival.core.sim

import io.xstarrevival.core.XStarPlatform
import io.xstarrevival.core.groundstation.MissionExecutionPhase
import io.xstarrevival.core.groundstation.MissionExecutionState
import io.xstarrevival.core.groundstation.MissionFinishBehavior
import io.xstarrevival.core.groundstation.MissionPlan
import io.xstarrevival.core.groundstation.GeoPoint
import io.xstarrevival.core.groundstation.SmartFlightExecutionState
import io.xstarrevival.core.groundstation.SmartFlightMode
import io.xstarrevival.core.groundstation.SmartFlightPhase
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
import kotlinx.coroutines.yield

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
    private val mutableSmartFlightExecution = MutableStateFlow(SmartFlightExecutionState())
    val smartFlightExecution: StateFlow<SmartFlightExecutionState> = mutableSmartFlightExecution.asStateFlow()

    private var snapshot = SimulatorSnapshot()
    private var input = SimulatorControlInput()
    private var ticker: Job? = null
    private var frozenLinkLossState: XStarState? = null
    private var missionRuntime: SimulatorMissionRuntime? = null
    private var smartFlightRuntime: SimulatorSmartFlightRuntime? = null

    override suspend fun connect() {
        ticker?.cancel()
        snapshot = SimulatorSnapshot()
        input = SimulatorControlInput()
        frozenLinkLossState = null
        mutableScenario.value = SimulatorScenario.NORMAL_FLIGHT
        missionRuntime = null
        mutableMissionExecution.value = MissionExecutionState()
        smartFlightRuntime = null
        mutableSmartFlightExecution.value = SmartFlightExecutionState()
        publish()
        ticker = scope.launch {
            while (isActive) {
                delay(tickMs)
                val missionControlsLocked = missionRuntime?.phase in setOf(
                    MissionExecutionPhase.ACTIVE,
                    MissionExecutionPhase.PAUSED
                )
                val autonomousControlsLocked = smartFlightRuntime?.let {
                    it.phase == SmartFlightPhase.ACTIVE && it.mode in AUTONOMOUS_SMART_FLIGHT_MODES
                } == true
                val effectiveInput = if (missionControlsLocked || autonomousControlsLocked) {
                    SimulatorControlInput()
                } else {
                    SimulatorSmartFlightModel.transformControls(snapshot, smartFlightRuntime, input)
                }
                snapshot = SimulatorFlightModel.step(
                    snapshot,
                    effectiveInput,
                    tickMs / 1000.0
                )
                missionRuntime?.let { runtime ->
                    val result = SimulatorMissionModel.step(snapshot, runtime, tickMs / 1000.0)
                    snapshot = result.snapshot
                    missionRuntime = result.runtime
                }
                smartFlightRuntime?.let { runtime ->
                    val result = SimulatorSmartFlightModel.step(snapshot, runtime, tickMs / 1000.0)
                    snapshot = result.snapshot
                    smartFlightRuntime = result.runtime
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
        smartFlightRuntime = null
        mutableSmartFlightExecution.value = SmartFlightExecutionState()
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

    suspend fun startReturnToHome(): Boolean {
        smartFlightRuntime?.takeIf { it.phase == SmartFlightPhase.ACTIVE }?.let { active ->
            smartFlightRuntime = SimulatorSmartFlightModel.cancel(active, "Interrupted by Return-to-Home")
            publish()
            yield()
        }
        val runtime = missionRuntime
        if (runtime != null && runtime.phase in setOf(MissionExecutionPhase.ACTIVE, MissionExecutionPhase.PAUSED)) {
            missionRuntime = SimulatorMissionModel.abort(runtime, "Mission interrupted by Return-to-Home")
        }
        input = SimulatorControlInput()
        smartFlightRuntime = SimulatorSmartFlightModel.startRth(snapshot)
        publish()
        return true
    }

    fun cancelReturnToHome(): Boolean = cancelSmartFlight(SmartFlightMode.RETURN_TO_HOME, "Return-to-Home cancelled")

    fun startOrbit(
        center: GeoPoint,
        radiusM: Double,
        altitudeM: Double,
        speedMps: Double,
        clockwise: Boolean,
        laps: Int
    ): Boolean {
        if (!canStartSmartFlight()) return false
        input = SimulatorControlInput()
        smartFlightRuntime = SimulatorSmartFlightModel.startOrbit(
            snapshot, center, radiusM, altitudeM, speedMps, clockwise, laps
        )
        publish()
        return true
    }

    fun stopOrbit(): Boolean = cancelSmartFlight(SmartFlightMode.ORBIT, "Orbit stopped")

    fun startFollow(distanceM: Double, altitudeM: Double, speedMps: Double, target: GeoPoint?): Boolean {
        if (!canStartSmartFlight()) return false
        input = SimulatorControlInput()
        smartFlightRuntime = SimulatorSmartFlightModel.startFollow(distanceM, altitudeM, speedMps, target)
        publish()
        return true
    }

    fun stopFollow(): Boolean = cancelSmartFlight(SmartFlightMode.FOLLOW, "Follow stopped")

    fun startCourseLock(headingDeg: Double): Boolean {
        if (!canStartSmartFlight()) return false
        smartFlightRuntime = SimulatorSmartFlightModel.startCourseLock(headingDeg)
        publish()
        return true
    }

    fun stopCourseLock(): Boolean = cancelSmartFlight(SmartFlightMode.COURSE_LOCK, "Course Lock stopped")

    fun startHomeLock(): Boolean {
        if (!canStartSmartFlight()) return false
        smartFlightRuntime = SimulatorSmartFlightModel.startHomeLock(snapshot)
        publish()
        return true
    }

    fun stopHomeLock(): Boolean = cancelSmartFlight(SmartFlightMode.HOME_LOCK, "Home Lock stopped")

    private fun canStartSmartFlight(): Boolean =
        smartFlightRuntime?.phase != SmartFlightPhase.ACTIVE &&
            missionRuntime?.phase !in setOf(MissionExecutionPhase.ACTIVE, MissionExecutionPhase.PAUSED)

    private fun cancelSmartFlight(mode: SmartFlightMode, detail: String): Boolean {
        val runtime = smartFlightRuntime ?: return false
        if (runtime.mode != mode || runtime.phase != SmartFlightPhase.ACTIVE) return false
        smartFlightRuntime = SimulatorSmartFlightModel.cancel(runtime, detail)
        publish()
        return true
    }

    fun setScenario(value: SimulatorScenario) {
        if (value == mutableScenario.value) return
        val missionWasActive = missionRuntime?.phase in setOf(MissionExecutionPhase.ACTIVE, MissionExecutionPhase.PAUSED)
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
        smartFlightRuntime = when (value) {
            SimulatorScenario.GPS_LOST,
            SimulatorScenario.COMPASS_FAILURE,
            SimulatorScenario.COMPLETE_LINK_LOSS,
            SimulatorScenario.CONNECTION_LOSS_DURING_MISSION -> smartFlightRuntime?.let {
                SimulatorSmartFlightModel.fail(it, "${value.label} interrupted smart flight")
            }
            SimulatorScenario.HOME_UNAVAILABLE -> smartFlightRuntime?.let {
                if (it.mode in setOf(SmartFlightMode.RETURN_TO_HOME, SmartFlightMode.HOME_LOCK)) {
                    SimulatorSmartFlightModel.fail(it, "Home Point became unavailable")
                } else it
            }
            SimulatorScenario.FORCED_LANDING -> smartFlightRuntime?.let {
                SimulatorSmartFlightModel.cancel(it, "Forced landing interrupted smart flight")
            }
            SimulatorScenario.RTH_DURING_MISSION -> {
                if (missionWasActive && snapshot.phase != SimulatorFlightPhase.GROUNDED) {
                    SimulatorSmartFlightModel.startRth(snapshot)
                } else smartFlightRuntime
            }
            else -> smartFlightRuntime
        }
        publish()
    }

    private fun publish() {
        mutableMissionExecution.value = SimulatorMissionModel.state(snapshot, missionRuntime)
        mutableSmartFlightExecution.value = SimulatorSmartFlightModel.state(snapshot, smartFlightRuntime)
        mutableState.value = SimulatorScenarioApplier.apply(
            frozenLinkLossState ?: normalizedBaseState(),
            mutableScenario.value
        )
    }

    private fun normalizedBaseState(): XStarState {
        val base = SimulatorFlightModel.toXStarState(snapshot)
        val missionPhase = missionRuntime?.phase
        val mode = when {
            smartFlightRuntime?.phase == SmartFlightPhase.ACTIVE -> when (smartFlightRuntime?.mode) {
                SmartFlightMode.RETURN_TO_HOME -> "RETURN TO HOME"
                SmartFlightMode.ORBIT -> "ORBIT"
                SmartFlightMode.FOLLOW -> "FOLLOW"
                SmartFlightMode.COURSE_LOCK -> "COURSE LOCK"
                SmartFlightMode.HOME_LOCK -> "HOME LOCK"
                else -> null
            }
            else -> when (missionPhase) {
                MissionExecutionPhase.ACTIVE -> "WAYPOINT MISSION"
                MissionExecutionPhase.PAUSED -> "MISSION PAUSED"
                else -> null
            }
        }
        return if (mode == null) base else base.copy(aircraft = base.aircraft.copy(flightMode = mode))
    }

    private companion object {
        val AUTONOMOUS_SMART_FLIGHT_MODES = setOf(
            SmartFlightMode.RETURN_TO_HOME,
            SmartFlightMode.ORBIT,
            SmartFlightMode.FOLLOW
        )
        val LINK_LOSS_SCENARIOS = setOf(
            SimulatorScenario.COMPLETE_LINK_LOSS,
            SimulatorScenario.CONNECTION_LOSS_DURING_MISSION
        )
    }
}
