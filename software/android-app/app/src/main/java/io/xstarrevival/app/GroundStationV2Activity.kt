package io.xstarrevival.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.xstarrevival.app.gs.GsAcademyScreen
import io.xstarrevival.app.gs.GsAircraftScreen
import io.xstarrevival.app.gs.GsCockpitScreen
import io.xstarrevival.app.gs.GsColors
import io.xstarrevival.app.gs.GsGarageScreen
import io.xstarrevival.app.gs.GsMediaScreen
import io.xstarrevival.app.gs.GsMediaStore
import io.xstarrevival.app.gs.GsMissionV2Screen
import io.xstarrevival.app.gs.GsNavigationRail
import io.xstarrevival.app.gs.GsPage
import io.xstarrevival.app.gs.GsPersistence
import io.xstarrevival.app.gs.GsRecordsV2Screen
import io.xstarrevival.app.gs.GsSessionTracker
import io.xstarrevival.app.gs.GsSettingsV2Screen
import io.xstarrevival.app.gs.GsTheme
import io.xstarrevival.app.gs.PersistedFlightSummary
import io.xstarrevival.app.gs.PersistedBatteryProfile
import io.xstarrevival.app.gs.PersistedBatterySample
import io.xstarrevival.app.gs.PersistedAircraftProfile
import io.xstarrevival.app.gs.PersistedMediaItem
import io.xstarrevival.app.gs.MediaOrigin
import io.xstarrevival.app.gs.MediaTransferState
import io.xstarrevival.app.gs.readiness
import io.xstarrevival.core.groundstation.RecoveryPoint
import io.xstarrevival.core.groundstation.MissionExecutionState
import io.xstarrevival.core.groundstation.MissionPlan
import io.xstarrevival.core.groundstation.GeoPoint
import io.xstarrevival.core.groundstation.SmartFlightExecutionState
import io.xstarrevival.core.command.CommandStatus
import io.xstarrevival.core.model.XStarState
import io.xstarrevival.core.sim.SimulatorScenario
import io.xstarrevival.core.sim.SimulatorControlInput
import io.xstarrevival.core.video.H264VideoFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class GroundStationV2Activity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GsTheme {
                val vm: XStarViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                val source by vm.source.collectAsStateWithLifecycle()
                val heartbeat by vm.heartbeat.collectAsStateWithLifecycle()
                val commandStatus by vm.commandStatus.collectAsStateWithLifecycle()
                val commandHistory by vm.commandHistory.collectAsStateWithLifecycle()
                val simulatorScenario by vm.simulatorScenario.collectAsStateWithLifecycle()
                val missionExecution by vm.missionExecution.collectAsStateWithLifecycle()
                val smartFlightExecution by vm.smartFlightExecution.collectAsStateWithLifecycle()
                GroundStationV2App(
                    state, source, heartbeat, commandStatus, commandHistory, simulatorScenario, missionExecution, smartFlightExecution,
                    vm.availableSources, vm.liveVideoFrames,
                    vm::selectSource, vm::connect, vm::disconnect, vm::refresh,
                    vm::toggleSimulatorArm, vm::simulatorTakeOff, vm::simulatorLand, vm::toggleSimulatorRecording,
                    vm::takeSimulatorPhoto, vm::setSimulatorCameraMode,
                    vm::setSimulatorExposure, vm::configureSimulatorCamera,
                    vm::setSimulatorGimbalPitch, vm::recenterSimulatorGimbal,
                    vm::calibrateSimulatorGimbal, vm::configureSimulatorGimbal,
                    vm::setSimulatorVideoLinkChannel,
                    vm::configureSimulatorController, vm::calibrateSimulatorController,
                    vm::setSimulatorScenario, vm::setSimulatorControls,
                    vm::startSimulatorMission, vm::pauseSimulatorMission,
                    vm::resumeSimulatorMission, vm::abortSimulatorMission,
                    vm::startSimulatorRth, vm::cancelSimulatorRth,
                    vm::startSimulatorOrbit, vm::stopSimulatorOrbit,
                    vm::startSimulatorFollow, vm::stopSimulatorFollow,
                    vm::startSimulatorCourseLock, vm::stopSimulatorCourseLock,
                    vm::startSimulatorHomeLock, vm::stopSimulatorHomeLock
                )
            }
        }
    }
}

@Composable
private fun GroundStationV2App(
    state: XStarState,
    source: TelemetrySource,
    heartbeat: HeartbeatUiState,
    commandStatus: CommandStatus?,
    commandHistory: List<CommandStatus>,
    simulatorScenario: SimulatorScenario,
    missionExecution: MissionExecutionState,
    smartFlightExecution: SmartFlightExecutionState,
    availableSources: List<TelemetrySource>,
    liveVideoFrames: Flow<H264VideoFrame>,
    onSource: (TelemetrySource) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onSimulatorArm: () -> Unit,
    onSimulatorTakeOff: () -> Unit,
    onSimulatorLand: () -> Unit,
    onSimulatorRecord: () -> Unit,
    onSimulatorPhoto: () -> Unit,
    onSimulatorCameraMode: (String) -> Unit,
    onSimulatorExposure: (Int?, Double?, Double?) -> Unit,
    onSimulatorCameraConfiguration: (Map<String, String>) -> Unit,
    onSimulatorGimbalPitch: (Double) -> Unit,
    onSimulatorGimbalRecenter: () -> Unit,
    onSimulatorGimbalCalibration: () -> Unit,
    onSimulatorGimbalConfiguration: (Double, Double, Double) -> Unit,
    onSimulatorVideoLinkChannel: (Boolean, Int?) -> Unit,
    onSimulatorControllerConfiguration: (Int, Double, Double, Double, Map<String, String>, Boolean) -> Unit,
    onSimulatorControllerCalibration: () -> Unit,
    onSimulatorScenario: (SimulatorScenario) -> Unit,
    onSimulatorControls: (SimulatorControlInput) -> Unit,
    onStartMission: (MissionPlan) -> Unit,
    onPauseMission: () -> Unit,
    onResumeMission: () -> Unit,
    onAbortMission: () -> Unit,
    onStartRth: () -> Unit,
    onCancelRth: () -> Unit,
    onStartOrbit: (GeoPoint, Double, Double, Double, Boolean, Int) -> Unit,
    onStopOrbit: () -> Unit,
    onStartFollow: (Double, Double, Double) -> Unit,
    onStopFollow: () -> Unit,
    onStartCourseLock: (Double) -> Unit,
    onStopCourseLock: () -> Unit,
    onStartHomeLock: () -> Unit,
    onStopHomeLock: () -> Unit
) {
    var page by rememberSaveable { mutableStateOf(GsPage.GARAGE) }
    val context = LocalContext.current
    val persistence = remember(context) { GsPersistence(context.applicationContext) }
    val sessionTracker = remember(persistence) { GsSessionTracker(persistence) }
    val mediaStore = remember(context) { GsMediaStore(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()
    var recoveryPoints by remember { mutableStateOf<List<RecoveryPoint>>(persistence.loadRecoveryPoints()) }
    var flightSummaries by remember { mutableStateOf<List<PersistedFlightSummary>>(persistence.loadFlightSummaries()) }
    var batteryProfiles by remember { mutableStateOf<List<PersistedBatteryProfile>>(persistence.loadBatteryProfiles()) }
    var activeBatteryProfileId by remember { mutableStateOf(persistence.loadActiveBatteryProfileId()) }
    var batteryHistory by remember { mutableStateOf<List<PersistedBatterySample>>(activeBatteryProfileId?.let(persistence::loadBatteryHistory).orEmpty()) }
    var mediaItems by remember { mutableStateOf<List<PersistedMediaItem>>(mediaStore.load()) }
    var mediaTransfers by remember { mutableStateOf<List<MediaTransferState>>(emptyList()) }
    val initialAircraftProfiles = remember(persistence) {
        persistence.loadAircraftProfiles().ifEmpty {
            val now = System.currentTimeMillis()
            listOf(PersistedAircraftProfile("aircraft-default", "My X-Star", "X-Star Premium", createdAtEpochMs = now).also(persistence::saveAircraftProfile))
        }
    }
    var aircraftProfiles by remember { mutableStateOf(initialAircraftProfiles) }
    var activeAircraftProfileId by remember {
        mutableStateOf(persistence.loadActiveAircraftProfileId()?.takeIf { id -> initialAircraftProfiles.any { it.id == id } } ?: initialAircraftProfiles.first().id)
    }
    var lastRecoveryWriteMs by remember { mutableLongStateOf(0L) }
    var lastBatteryHistoryWriteMs by remember { mutableLongStateOf(0L) }
    var lastAircraftProfileWriteMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(activeAircraftProfileId) {
        persistence.setActiveAircraftProfileId(activeAircraftProfileId)
    }

    LaunchedEffect(
        state.navigation.latitudeDeg,
        state.navigation.longitudeDeg,
        state.navigation.altitudeM,
        state.navigation.groundSpeedMps,
        state.battery.percent,
        state.battery.packId,
        state.battery.packVoltageV,
        state.battery.currentA,
        state.battery.temperatureC,
        state.battery.fullCapacityMah,
        state.battery.dischargeCount,
        state.battery.cellDeltaV,
        state.aircraft.armed,
        state.aircraft.productName,
        state.aircraft.firmwareVersion,
        state.connection,
        state.warnings
    ) {
        val now = System.currentTimeMillis()
        if (state.navigation.latitudeDeg != null && state.navigation.longitudeDeg != null && now - lastRecoveryWriteMs >= 1_000L) {
            persistence.saveRecoveryPoint(state, now)
            lastRecoveryWriteMs = now
            recoveryPoints = persistence.loadRecoveryPoints()
        }
        if (sessionTracker.observe(state, now)) flightSummaries = persistence.loadFlightSummaries()

        if (state.connection is io.xstarrevival.core.model.ConnectionState.Connected && now - lastAircraftProfileWriteMs >= 10_000L) {
            aircraftProfiles.firstOrNull { it.id == activeAircraftProfileId }?.let { profile ->
                val refreshed = profile.copy(
                    model = state.aircraft.productName ?: profile.model,
                    firmwareVersion = state.aircraft.firmwareVersion ?: profile.firmwareVersion,
                    lastConnectedEpochMs = now,
                    lastBatteryPercent = state.battery.percent ?: profile.lastBatteryPercent,
                    lastLatitudeDeg = state.navigation.latitudeDeg ?: profile.lastLatitudeDeg,
                    lastLongitudeDeg = state.navigation.longitudeDeg ?: profile.lastLongitudeDeg,
                    healthState = state.readiness().level.name
                ).normalized()
                persistence.saveAircraftProfile(refreshed)
                aircraftProfiles = persistence.loadAircraftProfiles()
                lastAircraftProfileWriteMs = now
            }
        }

        val identifiedProfile = state.battery.packId?.let { packId ->
            persistence.ensureIdentifiedBatteryProfile(
                packId,
                state.battery.designCapacityMah ?: state.battery.fullCapacityMah,
                now
            )
        }
        var profilesForSample = batteryProfiles
        var profileIdForSample = activeBatteryProfileId
        if (identifiedProfile != null) {
            profilesForSample = persistence.loadBatteryProfiles()
            batteryProfiles = profilesForSample
            val active = profilesForSample.firstOrNull { it.id == activeBatteryProfileId }
            if (active == null || (active.telemetryIdentity != null && active.telemetryIdentity != state.battery.packId)) {
                profileIdForSample = identifiedProfile.id
                activeBatteryProfileId = profileIdForSample
                persistence.setActiveBatteryProfileId(identifiedProfile.id)
            }
        }
        val activeProfile = profilesForSample.firstOrNull { it.id == profileIdForSample }
        if (activeProfile != null && now - lastBatteryHistoryWriteMs >= 60_000L) {
            persistence.saveBatteryHistorySample(activeProfile, state, now)
            lastBatteryHistoryWriteMs = now
            batteryHistory = persistence.loadBatteryHistory(activeProfile.id)
        }
    }

    LaunchedEffect(source, state.camera.photosTaken, state.camera.videosTaken) {
        if (source == TelemetrySource.SIMULATOR) {
            val now = System.currentTimeMillis()
            val photosChanged = mediaStore.captureSimulatorPhotos(state.camera, now)
            val videosChanged = mediaStore.captureSimulatorVideos(state.camera, now)
            if (photosChanged || videosChanged) mediaItems = mediaStore.load()
        }
    }

    Row(Modifier.fillMaxSize().background(GsColors.Ink)) {
        GsNavigationRail(page = page, onPage = { page = it })
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (page) {
                GsPage.GARAGE -> GsGarageScreen(
                    state, source, availableSources, onSource, onConnect, onDisconnect, onRefresh,
                    { page = GsPage.COCKPIT }, { page = GsPage.AIRCRAFT }, { page = GsPage.MISSIONS }, { page = GsPage.RECORDS },
                    aircraftProfiles, activeAircraftProfileId,
                    { profileId ->
                        activeAircraftProfileId = profileId
                        persistence.setActiveAircraftProfileId(profileId)
                    },
                    { profile ->
                        persistence.saveAircraftProfile(profile)
                        aircraftProfiles = persistence.loadAircraftProfiles()
                        activeAircraftProfileId = profile.id
                    },
                    { profileId ->
                        if (aircraftProfiles.size > 1) {
                            persistence.deleteAircraftProfile(profileId)
                            aircraftProfiles = persistence.loadAircraftProfiles()
                            activeAircraftProfileId = persistence.loadActiveAircraftProfileId() ?: aircraftProfiles.first().id
                        }
                    }
                )
                GsPage.COCKPIT -> GsCockpitScreen(
                    state, source, heartbeat, commandStatus, simulatorScenario, smartFlightExecution, liveVideoFrames,
                    onSimulatorArm, onSimulatorTakeOff, onSimulatorLand, onSimulatorRecord,
                    onSimulatorPhoto, onSimulatorCameraMode, onSimulatorExposure, onSimulatorCameraConfiguration,
                    onSimulatorGimbalPitch, onSimulatorGimbalRecenter,
                    onSimulatorGimbalCalibration, onSimulatorGimbalConfiguration,
                    onSimulatorScenario, onSimulatorControls, onStartRth, onCancelRth,
                    { page = GsPage.MISSIONS }, { page = GsPage.AIRCRAFT }
                )
                GsPage.MISSIONS -> GsMissionV2Screen(
                    state = state,
                    source = source,
                    execution = missionExecution,
                    smartFlight = smartFlightExecution,
                    commandStatus = commandStatus,
                    onStart = onStartMission,
                    onPause = onPauseMission,
                    onResume = onResumeMission,
                    onAbort = onAbortMission,
                    onCancelRth = onCancelRth,
                    onStartOrbit = onStartOrbit,
                    onStopOrbit = onStopOrbit,
                    onStartFollow = onStartFollow,
                    onStopFollow = onStopFollow,
                    onStartCourseLock = onStartCourseLock,
                    onStopCourseLock = onStopCourseLock,
                    onStartHomeLock = onStartHomeLock,
                    onStopHomeLock = onStopHomeLock
                )
                GsPage.RECORDS -> GsRecordsV2Screen(state, recoveryPoints, flightSummaries)
                GsPage.MEDIA -> GsMediaScreen(
                    state = state,
                    source = source,
                    mediaItems = mediaItems,
                    transfers = mediaTransfers,
                    onDownload = { itemIds ->
                        mediaItems.filter { it.id in itemIds && it.origin == MediaOrigin.AIRCRAFT }.forEach { item ->
                            if (mediaTransfers.any { it.mediaId == item.id && !it.completed }) return@forEach
                            coroutineScope.launch {
                                val speed = (item.sizeBytes / 2L).coerceAtLeast(1L)
                                mediaTransfers = mediaTransfers.filterNot { it.mediaId == item.id } +
                                    MediaTransferState(item.id, item.fileName, 0, speed)
                                (10..100 step 10).forEach { progress ->
                                    delay(120L)
                                    mediaTransfers = mediaTransfers.map { transfer ->
                                        if (transfer.mediaId == item.id) transfer.copy(progressPercent = progress, completed = progress == 100)
                                        else transfer
                                    }
                                }
                                mediaStore.download(setOf(item.id))
                                mediaItems = mediaStore.load()
                            }
                        }
                    },
                    onDelete = { itemIds ->
                        mediaStore.delete(itemIds)
                        mediaItems = mediaStore.load()
                    },
                    onToggleFavorite = { itemId ->
                        mediaStore.toggleFavorite(itemId)
                        mediaItems = mediaStore.load()
                    }
                )
                GsPage.AIRCRAFT -> GsAircraftScreen(
                    state = state,
                    batteryProfiles = batteryProfiles,
                    activeBatteryProfileId = activeBatteryProfileId,
                    batteryHistory = batteryHistory,
                    onSelectBatteryProfile = { profileId ->
                        activeBatteryProfileId = profileId
                        persistence.setActiveBatteryProfileId(profileId)
                        batteryProfiles.firstOrNull { it.id == profileId }?.let { profile ->
                            persistence.saveBatteryHistorySample(profile, state)
                        }
                        batteryHistory = persistence.loadBatteryHistory(profileId)
                        lastBatteryHistoryWriteMs = System.currentTimeMillis()
                    },
                    onSaveBatteryProfile = { profile ->
                        persistence.saveBatteryProfile(profile)
                        batteryProfiles = persistence.loadBatteryProfiles()
                        activeBatteryProfileId = profile.id
                        persistence.setActiveBatteryProfileId(profile.id)
                        persistence.saveBatteryHistorySample(profile, state)
                        batteryHistory = persistence.loadBatteryHistory(profile.id)
                        lastBatteryHistoryWriteMs = System.currentTimeMillis()
                    }
                )
                GsPage.SETTINGS -> GsSettingsV2Screen(
                    state,
                    source,
                    onSimulatorVideoLinkChannel,
                    onSimulatorControllerConfiguration,
                    onSimulatorControllerCalibration,
                    onSimulatorGimbalRecenter,
                    onSimulatorGimbalCalibration,
                    onSimulatorGimbalConfiguration,
                    { page = GsPage.AIRCRAFT },
                    commandHistory
                )
                GsPage.HELP -> GsAcademyScreen()
            }
        }
    }
}
