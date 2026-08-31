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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.xstarrevival.app.gs.GsAircraftScreen
import io.xstarrevival.app.gs.GsCockpitScreen
import io.xstarrevival.app.gs.GsColors
import io.xstarrevival.app.gs.GsGarageScreen
import io.xstarrevival.app.gs.GsMediaScreen
import io.xstarrevival.app.gs.GsMissionScreen
import io.xstarrevival.app.gs.GsNavigationRail
import io.xstarrevival.app.gs.GsPage
import io.xstarrevival.app.gs.GsRecordsScreen
import io.xstarrevival.app.gs.GsSettingsScreen
import io.xstarrevival.app.gs.GsTheme
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
                    state = state,
                    source = source,
                    heartbeat = heartbeat,
                    availableSources = vm.availableSources,
                    liveVideoFrames = vm.liveVideoFrames,
                    onSource = vm::selectSource,
                    onConnect = vm::connect,
                    onDisconnect = vm::disconnect,
                    onRefresh = vm::refresh,
                    onSimulatorArm = vm::toggleSimulatorArm,
                    onSimulatorTakeOff = vm::simulatorTakeOff,
                    onSimulatorLand = vm::simulatorLand,
                    onSimulatorRecord = vm::toggleSimulatorRecording
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
    Row(Modifier.fillMaxSize().background(GsColors.Ink)) {
        GsNavigationRail(page = page, onPage = { page = it })
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (page) {
                GsPage.GARAGE -> GsGarageScreen(
                    state = state,
                    source = source,
                    availableSources = availableSources,
                    onSource = onSource,
                    onConnect = onConnect,
                    onDisconnect = onDisconnect,
                    onRefresh = onRefresh,
                    onEnterFlight = { page = GsPage.COCKPIT },
                    onAircraft = { page = GsPage.AIRCRAFT },
                    onMissions = { page = GsPage.MISSIONS },
                    onRecords = { page = GsPage.RECORDS }
                )
                GsPage.COCKPIT -> GsCockpitScreen(
                    state = state,
                    source = source,
                    heartbeat = heartbeat,
                    liveVideoFrames = liveVideoFrames,
                    onSimulatorArm = onSimulatorArm,
                    onSimulatorTakeOff = onSimulatorTakeOff,
                    onSimulatorLand = onSimulatorLand,
                    onSimulatorRecord = onSimulatorRecord,
                    onGoMissions = { page = GsPage.MISSIONS },
                    onGoAircraft = { page = GsPage.AIRCRAFT }
                )
                GsPage.MISSIONS -> GsMissionScreen(state)
                GsPage.RECORDS -> GsRecordsScreen(state)
                GsPage.MEDIA -> GsMediaScreen(state)
                GsPage.AIRCRAFT -> GsAircraftScreen(state)
                GsPage.SETTINGS -> GsSettingsScreen()
            }
        }
    }
}
