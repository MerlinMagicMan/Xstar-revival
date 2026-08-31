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
import io.xstarrevival.app.gs.GsMissionV2Screen
import io.xstarrevival.app.gs.GsNavigationRail
import io.xstarrevival.app.gs.GsPage
import io.xstarrevival.app.gs.GsPersistence
import io.xstarrevival.app.gs.GsRecordsScreen
import io.xstarrevival.app.gs.GsSessionTracker
import io.xstarrevival.app.gs.GsSettingsV2Screen
import io.xstarrevival.app.gs.GsTheme
import io.xstarrevival.app.gs.PersistedFlightSummary
import io.xstarrevival.core.groundstation.RecoveryPoint
import io.xstarrevival.core.model.XStarState
import io.xstarrevival.core.video.H264VideoFrame
import kotlinx.coroutines.flow.Flow

class GroundStationV2Activity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GsTheme {
                val vm: XStarViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                val source by vm.source.collectAsStateWithLifecycle()
                val heartbeat by vm.heartbeat.collectAsStateWithLifecycle()
                GroundStationV2App(
                    state, source, heartbeat, vm.availableSources, vm.liveVideoFrames,
                    vm::selectSource, vm::connect, vm::disconnect, vm::refresh,
                    vm::toggleSimulatorArm, vm::simulatorTakeOff, vm::simulatorLand, vm::toggleSimulatorRecording
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
    availableSources: List<TelemetrySource>,
    liveVideoFrames: Flow<H264VideoFrame>,
    onSource: (TelemetrySource) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onSimulatorArm: () -> Unit,
    onSimulatorTakeOff: () -> Unit,
    onSimulatorLand: () -> Unit,
    onSimulatorRecord: () -> Unit
) {
    var page by rememberSaveable { mutableStateOf(GsPage.GARAGE) }
    val context = LocalContext.current
    val persistence = remember(context) { GsPersistence(context.applicationContext) }
    val sessionTracker = remember(persistence) { GsSessionTracker(persistence) }
    var recoveryPoints by remember { mutableStateOf<List<RecoveryPoint>>(persistence.loadRecoveryPoints()) }
    var flightSummaries by remember { mutableStateOf<List<PersistedFlightSummary>>(persistence.loadFlightSummaries()) }
    var lastRecoveryWriteMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(
        state.navigation.latitudeDeg,
        state.navigation.longitudeDeg,
        state.navigation.altitudeM,
        state.navigation.groundSpeedMps,
        state.battery.percent,
        state.aircraft.armed
    ) {
        val now = System.currentTimeMillis()
        if (state.navigation.latitudeDeg != null && state.navigation.longitudeDeg != null && now - lastRecoveryWriteMs >= 1_000L) {
            persistence.saveRecoveryPoint(state, now)
            lastRecoveryWriteMs = now
            recoveryPoints = persistence.loadRecoveryPoints()
        }
        if (sessionTracker.observe(state, now)) flightSummaries = persistence.loadFlightSummaries()
    }

    Row(Modifier.fillMaxSize().background(GsColors.Ink)) {
        GsNavigationRail(page = page, onPage = { page = it })
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (page) {
                GsPage.GARAGE -> GsGarageScreen(
                    state, source, availableSources, onSource, onConnect, onDisconnect, onRefresh,
                    { page = GsPage.COCKPIT }, { page = GsPage.AIRCRAFT }, { page = GsPage.MISSIONS }, { page = GsPage.RECORDS }
                )
                GsPage.COCKPIT -> GsCockpitScreen(
                    state, source, heartbeat, liveVideoFrames,
                    onSimulatorArm, onSimulatorTakeOff, onSimulatorLand, onSimulatorRecord,
                    { page = GsPage.MISSIONS }, { page = GsPage.AIRCRAFT }
                )
                GsPage.MISSIONS -> GsMissionV2Screen(state)
                GsPage.RECORDS -> GsRecordsScreen(state, recoveryPoints, flightSummaries)
                GsPage.MEDIA -> GsMediaScreen(state)
                GsPage.AIRCRAFT -> GsAircraftScreen(state)
                GsPage.SETTINGS -> GsSettingsV2Screen()
                GsPage.HELP -> GsAcademyScreen()
            }
        }
    }
}
