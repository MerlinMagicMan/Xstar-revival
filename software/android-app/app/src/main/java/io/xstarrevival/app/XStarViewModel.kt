package io.xstarrevival.app

import android.app.Application
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.xstarrevival.core.XStarPlatform
import io.xstarrevival.core.adapter.AutelSdkBridge
import io.xstarrevival.core.adapter.AutelSdkPlatformAdapter
import io.xstarrevival.core.adapter.OpenXStarPlatformAdapter
import io.xstarrevival.core.command.ArmCommand
import io.xstarrevival.core.command.AbortMissionCommand
import io.xstarrevival.core.command.CancelReturnToHomeCommand
import io.xstarrevival.core.command.CalibrateGimbalCommand
import io.xstarrevival.core.command.CalibrateControllerCommand
import io.xstarrevival.core.command.ChangeCameraModeCommand
import io.xstarrevival.core.command.CommandDispatcher
import io.xstarrevival.core.command.CommandStatus
import io.xstarrevival.core.command.ConfigureCameraCommand
import io.xstarrevival.core.command.ConfigureGimbalCommand
import io.xstarrevival.core.command.ConfigureControllerCommand
import io.xstarrevival.core.command.DisarmCommand
import io.xstarrevival.core.command.LandCommand
import io.xstarrevival.core.command.PauseMissionCommand
import io.xstarrevival.core.command.ResumeMissionCommand
import io.xstarrevival.core.command.ReturnToHomeCommand
import io.xstarrevival.core.command.SetExposureCommand
import io.xstarrevival.core.command.SetGimbalPitchCommand
import io.xstarrevival.core.command.SetVideoLinkChannelCommand
import io.xstarrevival.core.command.StartRecordingCommand
import io.xstarrevival.core.command.StartWaypointMissionCommand
import io.xstarrevival.core.command.StartCourseLockCommand
import io.xstarrevival.core.command.StartFollowCommand
import io.xstarrevival.core.command.StartHomeLockCommand
import io.xstarrevival.core.command.StartOrbitCommand
import io.xstarrevival.core.command.StopCourseLockCommand
import io.xstarrevival.core.command.StopFollowCommand
import io.xstarrevival.core.command.StopHomeLockCommand
import io.xstarrevival.core.command.StopOrbitCommand
import io.xstarrevival.core.command.StopRecordingCommand
import io.xstarrevival.core.command.TakePhotoCommand
import io.xstarrevival.core.command.RecenterGimbalCommand
import io.xstarrevival.core.command.TakeoffCommand
import io.xstarrevival.core.mock.MockXStarPlatform
import io.xstarrevival.core.groundstation.MissionExecutionState
import io.xstarrevival.core.groundstation.MissionPlan
import io.xstarrevival.core.groundstation.GeoPoint
import io.xstarrevival.core.groundstation.SmartFlightExecutionState
import io.xstarrevival.core.model.ConnectionState
import io.xstarrevival.core.model.XStarState
import io.xstarrevival.core.protocol.mavlink.StandardMavlinkDecoder
import io.xstarrevival.core.replay.CaptureReplayState
import io.xstarrevival.core.replay.CaptureReplayTransport
import io.xstarrevival.core.replay.StandardMavlinkDemoCapture
import io.xstarrevival.core.sim.SimulatorControlInput
import io.xstarrevival.core.sim.SimulatorControllerAction
import io.xstarrevival.core.sim.SimulatorControllerResponseProfile
import io.xstarrevival.core.sim.SimulatorCommandAdapter
import io.xstarrevival.core.sim.SimulatorScenario
import io.xstarrevival.core.sim.SimulatorViewMode
import io.xstarrevival.core.sim.SimulatorXStarPlatform
import io.xstarrevival.core.video.H264VideoFrame
import io.xstarrevival.core.video.H264CaptureStopReason
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

enum class TelemetrySource(val label: String) {
    MOCK("Mock"),
    MAVLINK_REPLAY("MAVLink Replay"),
    SIMULATOR("Flight Simulator"),
    OFFICIAL_AUTEL("Live X-Star")
}

data class HeartbeatUiState(
    val ageMs: Long? = null,
    val stale: Boolean = false
)

data class LiveReadinessUiState(
    val sdkIncluded: Boolean,
    val appKeyConfigured: Boolean
)

class XStarViewModel(application: Application) : AndroidViewModel(application) {
    private val controllerUsbMonitor = ControllerUsbMonitor(application)
    private val controllerUsbInputProbe = ControllerUsbInputProbe(application, viewModelScope)
    private val mockPlatform = MockXStarPlatform(viewModelScope)
    private val simulatorPlatform = SimulatorXStarPlatform(viewModelScope)
    private val simulatorCommands = CommandDispatcher(
        scope = viewModelScope,
        stateProvider = { simulatorPlatform.state.value },
        transport = SimulatorCommandAdapter(simulatorPlatform)
    )
    private val simulatorGamepadInput = SimulatorGamepadInputAdapter(
        profileProvider = {
            val remote = simulatorPlatform.state.value.remote
            SimulatorControllerResponseProfile(
                stickMode = remote.stickMode ?: 2,
                sensitivity = remote.sensitivity ?: .55,
                deadZone = remote.deadZone ?: .05,
                expo = remote.expo ?: .35,
                gimbalWheelReversed = remote.gimbalWheelReversed ?: false
            )
        },
        assignmentsProvider = { simulatorPlatform.state.value.remote.buttonAssignments },
        onControls = simulatorPlatform::setControls,
        onAction = ::handleSimulatorControllerAction
    )
    private val simulatorTelemetryBridge = SimulatorUdpTelemetryBridge(viewModelScope)
    private val replayTransport = CaptureReplayTransport(
        scope = viewModelScope,
        chunks = StandardMavlinkDemoCapture.chunks,
        description = "synthetic MAVLink capture"
    )
    private val replayPlatform = OpenXStarPlatformAdapter(
        scope = viewModelScope,
        transport = replayTransport,
        decoder = StandardMavlinkDecoder()
    )
    private val officialPlatform = createOfficialPlatform()

    val availableSources: List<TelemetrySource> = buildList {
        add(TelemetrySource.MOCK)
        add(TelemetrySource.MAVLINK_REPLAY)
        add(TelemetrySource.SIMULATOR)
        if (officialPlatform != null) add(TelemetrySource.OFFICIAL_AUTEL)
    }
    val liveVideoFrames: Flow<H264VideoFrame> = officialPlatform?.h264Frames ?: emptyFlow()
    val liveReadiness = LiveReadinessUiState(
        sdkIncluded = BuildConfig.AUTEL_SDK_AVAILABLE,
        appKeyConfigured = BuildConfig.AUTEL_APP_KEY.isNotBlank()
    )
    internal val controllerUsb: StateFlow<ControllerUsbUiState> = controllerUsbMonitor.state
    internal val controllerProbe: StateFlow<ControllerProbeUiState> = controllerUsbInputProbe.state
    internal val simulatorControllerInput: StateFlow<SimulatorControllerInputUiState> = simulatorGamepadInput.state
    val simulatorBridge: StateFlow<SimulatorBridgeUiState> = simulatorTelemetryBridge.state
    private val mutableSimulatorViewMode = MutableStateFlow(SimulatorViewMode.FPV)
    val simulatorViewMode: StateFlow<SimulatorViewMode> = mutableSimulatorViewMode.asStateFlow()

    private var activePlatform: XStarPlatform = mockPlatform
    private var platformCollectionJob: Job? = null

    private val mutableState = MutableStateFlow(XStarState())
    val state: StateFlow<XStarState> = mutableState.asStateFlow()
    private val benchCaptureManager = BenchH264CaptureManager(
        application,
        viewModelScope,
        liveVideoFrames,
        state,
        BuildConfig.VERSION_NAME,
        BuildConfig.AUTEL_SDK_SHA256
    )
    val benchCapture: StateFlow<BenchCaptureUiState> = benchCaptureManager.state

    private val mutableSource = MutableStateFlow(TelemetrySource.MOCK)
    val source: StateFlow<TelemetrySource> = mutableSource.asStateFlow()

    private val mutablePlatformName = MutableStateFlow(mockPlatform.name)
    val platformName: StateFlow<String> = mutablePlatformName.asStateFlow()

    val replayState: StateFlow<CaptureReplayState> = replayTransport.playback
    val commandStatus: StateFlow<CommandStatus?> = simulatorCommands.latest
    val commandHistory: StateFlow<List<CommandStatus>> = simulatorCommands.history
    val simulatorScenario: StateFlow<SimulatorScenario> = simulatorPlatform.scenario
    val missionExecution: StateFlow<MissionExecutionState> = simulatorPlatform.missionExecution
    val smartFlightExecution: StateFlow<SmartFlightExecutionState> = simulatorPlatform.smartFlightExecution

    private val mutableHeartbeat = MutableStateFlow(HeartbeatUiState())
    val heartbeat: StateFlow<HeartbeatUiState> = mutableHeartbeat.asStateFlow()

    private var lastHeartbeatCount = 0L
    private var lastHeartbeatElapsedMs: Long? = null
    private var connectionWasEstablished = false
    private var linkLostElapsedMs: Long? = null

    init {
        collectActivePlatform()
        viewModelScope.launch {
            while (isActive) {
                updateHeartbeatAge()
                delay(HEARTBEAT_TICK_MS)
            }
        }
    }

    fun selectSource(value: TelemetrySource) {
        if (value == mutableSource.value) return
        if (value == TelemetrySource.OFFICIAL_AUTEL && officialPlatform == null) return
        viewModelScope.launch {
            simulatorCommands.cancelAll()
            if (mutableSource.value == TelemetrySource.OFFICIAL_AUTEL) {
                benchCaptureManager.stop(H264CaptureStopReason.SOURCE_ENDED)
                controllerUsbInputProbe.stop(ControllerProbeStopReason.USER)
            }
            activePlatform.disconnect()
            mutableSource.value = value
            activePlatform = when (value) {
                TelemetrySource.MOCK -> mockPlatform
                TelemetrySource.MAVLINK_REPLAY -> replayPlatform
                TelemetrySource.SIMULATOR -> simulatorPlatform
                TelemetrySource.OFFICIAL_AUTEL -> checkNotNull(officialPlatform)
            }
            mutablePlatformName.value = activePlatform.name
            mutableState.value = XStarState()
            resetHeartbeat()
            collectActivePlatform()
            connectActivePlatform()
        }
    }

    fun connect() {
        viewModelScope.launch { connectActivePlatform() }
    }

    fun disconnect() {
        viewModelScope.launch {
            simulatorCommands.cancelAll()
            if (mutableSource.value == TelemetrySource.OFFICIAL_AUTEL) {
                benchCaptureManager.stop(H264CaptureStopReason.SOURCE_ENDED)
                controllerUsbInputProbe.stop(ControllerProbeStopReason.USER)
            }
            activePlatform.disconnect()
            resetHeartbeat()
        }
    }

    fun refresh() {
        controllerUsbMonitor.refresh()
        viewModelScope.launch { activePlatform.refresh() }
    }

    fun playReplay() = replayTransport.play()

    fun pauseReplay() = replayTransport.pause()

    fun restartReplay() {
        if (mutableSource.value != TelemetrySource.MAVLINK_REPLAY) return
        viewModelScope.launch {
            replayPlatform.disconnect()
            resetHeartbeat()
            replayPlatform.connect()
            yield()
            replayTransport.restart()
        }
    }

    fun setReplaySpeed(speed: Double) = replayTransport.setSpeed(speed)

    fun setSimulatorControls(input: SimulatorControlInput) {
        if (mutableSource.value == TelemetrySource.SIMULATOR) simulatorPlatform.setControls(input)
    }

    fun handleSimulatorControllerMotion(event: MotionEvent): Boolean =
        mutableSource.value == TelemetrySource.SIMULATOR && simulatorGamepadInput.handleMotion(event)

    fun handleSimulatorControllerKey(event: KeyEvent): Boolean =
        mutableSource.value == TelemetrySource.SIMULATOR && simulatorGamepadInput.handleKey(event)

    fun releaseSimulatorController() = simulatorGamepadInput.release()

    fun setSimulatorScenario(value: SimulatorScenario) {
        if (mutableSource.value == TelemetrySource.SIMULATOR) simulatorPlatform.setScenario(value)
    }

    fun startSimulatorMission(plan: MissionPlan) {
        if (mutableSource.value == TelemetrySource.SIMULATOR) {
            simulatorCommands.dispatch(StartWaypointMissionCommand(plan))
        }
    }

    fun pauseSimulatorMission() {
        if (mutableSource.value == TelemetrySource.SIMULATOR) simulatorCommands.dispatch(PauseMissionCommand)
    }

    fun resumeSimulatorMission() {
        if (mutableSource.value == TelemetrySource.SIMULATOR) simulatorCommands.dispatch(ResumeMissionCommand)
    }

    fun abortSimulatorMission() {
        if (mutableSource.value == TelemetrySource.SIMULATOR) simulatorCommands.dispatch(AbortMissionCommand)
    }

    fun startSimulatorRth() {
        if (mutableSource.value == TelemetrySource.SIMULATOR) simulatorCommands.dispatch(ReturnToHomeCommand)
    }

    fun cancelSimulatorRth() {
        if (mutableSource.value == TelemetrySource.SIMULATOR) simulatorCommands.dispatch(CancelReturnToHomeCommand)
    }

    fun startSimulatorOrbit(
        pointOfInterest: GeoPoint,
        radiusM: Double,
        altitudeM: Double,
        speedMps: Double,
        clockwise: Boolean,
        laps: Int
    ) {
        if (mutableSource.value == TelemetrySource.SIMULATOR) {
            simulatorCommands.dispatch(StartOrbitCommand(pointOfInterest, radiusM, altitudeM, speedMps, clockwise, laps))
        }
    }

    fun stopSimulatorOrbit() {
        if (mutableSource.value == TelemetrySource.SIMULATOR) simulatorCommands.dispatch(StopOrbitCommand)
    }

    fun startSimulatorFollow(distanceM: Double, altitudeM: Double, speedMps: Double) {
        if (mutableSource.value == TelemetrySource.SIMULATOR) {
            simulatorCommands.dispatch(StartFollowCommand(distanceM, altitudeM, speedMps))
        }
    }

    fun stopSimulatorFollow() {
        if (mutableSource.value == TelemetrySource.SIMULATOR) simulatorCommands.dispatch(StopFollowCommand)
    }

    fun startSimulatorCourseLock(headingDeg: Double) {
        if (mutableSource.value == TelemetrySource.SIMULATOR) {
            simulatorCommands.dispatch(StartCourseLockCommand(headingDeg))
        }
    }

    fun stopSimulatorCourseLock() {
        if (mutableSource.value == TelemetrySource.SIMULATOR) simulatorCommands.dispatch(StopCourseLockCommand)
    }

    fun startSimulatorHomeLock() {
        if (mutableSource.value == TelemetrySource.SIMULATOR) simulatorCommands.dispatch(StartHomeLockCommand)
    }

    fun stopSimulatorHomeLock() {
        if (mutableSource.value == TelemetrySource.SIMULATOR) simulatorCommands.dispatch(StopHomeLockCommand)
    }

    private fun handleSimulatorControllerAction(action: SimulatorControllerAction) {
        if (mutableSource.value != TelemetrySource.SIMULATOR) return
        when (action) {
            SimulatorControllerAction.TOGGLE_ARM -> toggleSimulatorArm()
            SimulatorControllerAction.TAKE_OFF -> simulatorTakeOff()
            SimulatorControllerAction.LAND -> simulatorLand()
            SimulatorControllerAction.RETURN_TO_HOME -> startSimulatorRth()
            SimulatorControllerAction.CANCEL_RETURN_TO_HOME -> cancelSimulatorRth()
            SimulatorControllerAction.TAKE_PHOTO -> takeSimulatorPhoto()
            SimulatorControllerAction.TOGGLE_RECORDING -> toggleSimulatorRecording()
            SimulatorControllerAction.RECENTER_GIMBAL -> recenterSimulatorGimbal()
            SimulatorControllerAction.TOGGLE_CAMERA_VIEW -> toggleSimulatorViewMode()
        }
    }

    fun toggleSimulatorViewMode() {
        if (mutableSource.value != TelemetrySource.SIMULATOR) return
        mutableSimulatorViewMode.value = when (mutableSimulatorViewMode.value) {
            SimulatorViewMode.FPV -> SimulatorViewMode.CHASE
            SimulatorViewMode.CHASE -> SimulatorViewMode.FPV
        }
        simulatorTelemetryBridge.publish(simulatorPlatform.state.value, mutableSimulatorViewMode.value)
    }

    fun toggleSimulatorArm() {
        if (mutableSource.value != TelemetrySource.SIMULATOR) return
        simulatorCommands.dispatch(
            if (simulatorPlatform.state.value.aircraft.armed == true) DisarmCommand else ArmCommand
        )
    }

    fun simulatorTakeOff() {
        if (mutableSource.value == TelemetrySource.SIMULATOR) simulatorCommands.dispatch(TakeoffCommand)
    }

    fun simulatorLand() {
        if (mutableSource.value == TelemetrySource.SIMULATOR) simulatorCommands.dispatch(LandCommand)
    }

    fun toggleSimulatorRecording() {
        if (mutableSource.value != TelemetrySource.SIMULATOR) return
        simulatorCommands.dispatch(
            if (simulatorPlatform.state.value.camera.recording == true) StopRecordingCommand else StartRecordingCommand
        )
    }

    fun takeSimulatorPhoto() {
        if (mutableSource.value == TelemetrySource.SIMULATOR) simulatorCommands.dispatch(TakePhotoCommand)
    }

    fun setSimulatorCameraMode(mode: String) {
        if (mutableSource.value == TelemetrySource.SIMULATOR) {
            simulatorCommands.dispatch(ChangeCameraModeCommand(mode))
        }
    }

    fun setSimulatorExposure(iso: Int?, shutterSeconds: Double?, compensationEv: Double?) {
        if (mutableSource.value == TelemetrySource.SIMULATOR) {
            simulatorCommands.dispatch(SetExposureCommand(iso, shutterSeconds, compensationEv))
        }
    }

    fun configureSimulatorCamera(parameters: Map<String, String>) {
        if (mutableSource.value == TelemetrySource.SIMULATOR) {
            simulatorCommands.dispatch(ConfigureCameraCommand(parameters))
        }
    }

    fun setSimulatorGimbalPitch(pitchDeg: Double) {
        if (mutableSource.value == TelemetrySource.SIMULATOR) {
            simulatorCommands.dispatch(SetGimbalPitchCommand(pitchDeg))
        }
    }

    fun recenterSimulatorGimbal() {
        if (mutableSource.value == TelemetrySource.SIMULATOR) simulatorCommands.dispatch(RecenterGimbalCommand)
    }

    fun calibrateSimulatorGimbal() {
        if (mutableSource.value == TelemetrySource.SIMULATOR) simulatorCommands.dispatch(CalibrateGimbalCommand)
    }

    fun configureSimulatorGimbal(sensitivity: Double, smoothing: Double, pitchSpeed: Double) {
        if (mutableSource.value == TelemetrySource.SIMULATOR) {
            simulatorCommands.dispatch(ConfigureGimbalCommand(sensitivity, smoothing, pitchSpeed))
        }
    }

    fun setSimulatorVideoLinkChannel(automatic: Boolean, channel: Int?) {
        if (mutableSource.value == TelemetrySource.SIMULATOR) {
            simulatorCommands.dispatch(SetVideoLinkChannelCommand(automatic, channel))
        }
    }

    fun configureSimulatorController(
        stickMode: Int,
        sensitivity: Double,
        deadZone: Double,
        expo: Double,
        buttonAssignments: Map<String, String>,
        gimbalWheelReversed: Boolean
    ) {
        if (mutableSource.value == TelemetrySource.SIMULATOR) {
            simulatorCommands.dispatch(
                ConfigureControllerCommand(
                    stickMode,
                    sensitivity,
                    deadZone,
                    expo,
                    buttonAssignments,
                    gimbalWheelReversed
                )
            )
        }
    }

    fun calibrateSimulatorController() {
        if (mutableSource.value == TelemetrySource.SIMULATOR) simulatorCommands.dispatch(CalibrateControllerCommand)
    }

    fun startBenchCapture() {
        val ready = mutableSource.value == TelemetrySource.OFFICIAL_AUTEL &&
            BuildConfig.AUTEL_SDK_AVAILABLE &&
            BuildConfig.AUTEL_APP_KEY.isNotBlank() &&
            state.value.connection is ConnectionState.Connected
        if (ready) benchCaptureManager.start()
    }

    fun stopBenchCapture() = benchCaptureManager.stop()

    fun startControllerProbe() {
        if (mutableSource.value in setOf(TelemetrySource.SIMULATOR, TelemetrySource.OFFICIAL_AUTEL) &&
            controllerUsb.value.controllerDetected
        ) {
            controllerUsbInputProbe.start()
        }
    }

    fun stopControllerProbe() = controllerUsbInputProbe.stop()

    private suspend fun connectActivePlatform() {
        runCatching { activePlatform.connect() }
        if (activePlatform === replayPlatform) {
            yield()
            replayTransport.restart()
        }
    }

    private fun collectActivePlatform() {
        platformCollectionJob?.cancel()
        platformCollectionJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            activePlatform.state.collect { next ->
                mutableState.value = next
                if (mutableSource.value == TelemetrySource.SIMULATOR) {
                    simulatorTelemetryBridge.publish(next, mutableSimulatorViewMode.value)
                }
                observeHeartbeat(next)
            }
        }
    }

    private fun observeHeartbeat(next: XStarState) {
        if (mutableSource.value == TelemetrySource.MAVLINK_REPLAY) {
            val count = next.diagnostics.counters["mavlink_heartbeats"] ?: 0L
            if (count < lastHeartbeatCount) resetHeartbeat()
            if (count > lastHeartbeatCount) {
                lastHeartbeatElapsedMs = SystemClock.elapsedRealtime()
                lastHeartbeatCount = count
                mutableHeartbeat.value = HeartbeatUiState(ageMs = 0, stale = false)
            }
            return
        }
        when (next.connection) {
            is ConnectionState.Connected -> {
                connectionWasEstablished = true
                linkLostElapsedMs = null
                mutableHeartbeat.value = HeartbeatUiState(ageMs = 0, stale = false)
            }
            ConnectionState.Disconnected,
            is ConnectionState.Failed -> if (connectionWasEstablished && linkLostElapsedMs == null) {
                linkLostElapsedMs = SystemClock.elapsedRealtime()
            }
            else -> Unit
        }
    }

    private fun updateHeartbeatAge() {
        if (mutableSource.value != TelemetrySource.MAVLINK_REPLAY) {
            val lostAt = linkLostElapsedMs
            mutableHeartbeat.value = if (lostAt == null) {
                HeartbeatUiState()
            } else {
                HeartbeatUiState(
                    ageMs = (SystemClock.elapsedRealtime() - lostAt).coerceAtLeast(0),
                    stale = true
                )
            }
            return
        }
        val observedAt = lastHeartbeatElapsedMs ?: return
        val age = (SystemClock.elapsedRealtime() - observedAt).coerceAtLeast(0)
        mutableHeartbeat.value = HeartbeatUiState(ageMs = age, stale = age > HEARTBEAT_STALE_MS)
    }

    private fun resetHeartbeat() {
        lastHeartbeatCount = 0
        lastHeartbeatElapsedMs = null
        connectionWasEstablished = false
        linkLostElapsedMs = null
        mutableHeartbeat.value = HeartbeatUiState()
    }

    private fun createOfficialPlatform(): AutelSdkPlatformAdapter? {
        if (!BuildConfig.AUTEL_SDK_AVAILABLE) return null
        return runCatching {
            val bridgeClass = Class.forName("io.xstarrevival.autelsdk.OfficialAutelSdkBridge")
            val constructor = bridgeClass.getConstructor(android.content.Context::class.java, String::class.java)
            val bridge = constructor.newInstance(getApplication<Application>(), BuildConfig.AUTEL_APP_KEY) as AutelSdkBridge
            AutelSdkPlatformAdapter(viewModelScope, bridge)
        }.getOrNull()
    }

    override fun onCleared() {
        simulatorCommands.cancelAll()
        benchCaptureManager.stop(H264CaptureStopReason.SOURCE_ENDED)
        controllerUsbInputProbe.close()
        replayTransport.pause()
        controllerUsbMonitor.close()
        simulatorTelemetryBridge.close()
        super.onCleared()
    }

    private companion object {
        const val HEARTBEAT_TICK_MS = 250L
        const val HEARTBEAT_STALE_MS = 2_500L
    }
}
