package io.xstarrevival.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.xstarrevival.core.model.ConnectionState
import io.xstarrevival.core.model.XStarState
import io.xstarrevival.core.replay.CaptureReplayState
import io.xstarrevival.core.replay.CaptureReplayStatus
import io.xstarrevival.core.video.H264VideoFrame
import kotlinx.coroutines.flow.Flow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val vm: XStarViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                val source by vm.source.collectAsStateWithLifecycle()
                val platformName by vm.platformName.collectAsStateWithLifecycle()
                val replayState by vm.replayState.collectAsStateWithLifecycle()
                val heartbeat by vm.heartbeat.collectAsStateWithLifecycle()

                XStarApp(
                    state = state,
                    source = source,
                    availableSources = vm.availableSources,
                    platformName = platformName,
                    replayState = replayState,
                    heartbeat = heartbeat,
                    liveVideoFrames = vm.liveVideoFrames,
                    onSourceSelected = vm::selectSource,
                    onConnect = vm::connect,
                    onDisconnect = vm::disconnect,
                    onRefresh = vm::refresh,
                    onPlayReplay = vm::playReplay,
                    onPauseReplay = vm::pauseReplay,
                    onRestartReplay = vm::restartReplay,
                    onReplaySpeedChanged = vm::setReplaySpeed
                )
            }
        }
    }
}

private enum class AppView(val label: String) {
    DASHBOARD("Dashboard"),
    COCKPIT("Cockpit / FPV")
}

@Composable
private fun XStarApp(
    state: XStarState,
    source: TelemetrySource,
    availableSources: List<TelemetrySource>,
    platformName: String,
    replayState: CaptureReplayState,
    heartbeat: HeartbeatUiState,
    liveVideoFrames: Flow<H264VideoFrame>,
    onSourceSelected: (TelemetrySource) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onPlayReplay: () -> Unit,
    onPauseReplay: () -> Unit,
    onRestartReplay: () -> Unit,
    onReplaySpeedChanged: (Double) -> Unit
) {
    var selectedView by rememberSaveable { mutableStateOf(AppView.DASHBOARD) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("X-Star Revival", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(platformName, style = MaterialTheme.typography.labelMedium)
                }
                ConnectionBadge(state.connection)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                availableSources.forEach { option ->
                    FilterChip(
                        selected = source == option,
                        onClick = { onSourceSelected(option) },
                        label = { Text(option.label) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppView.entries.forEach { view ->
                    if (selectedView == view) {
                        Button(onClick = { selectedView = view }) { Text(view.label) }
                    } else {
                        OutlinedButton(onClick = { selectedView = view }) { Text(view.label) }
                    }
                }
            }

            if (source == TelemetrySource.MAVLINK_REPLAY) {
                ReplayControls(
                    replayState = replayState,
                    heartbeat = heartbeat,
                    onPlay = onPlayReplay,
                    onPause = onPauseReplay,
                    onRestart = onRestartReplay,
                    onSpeedChanged = onReplaySpeedChanged
                )
            }
        }

        HorizontalDivider()

        when (selectedView) {
            AppView.DASHBOARD -> DashboardScreen(
                state = state,
                onConnect = onConnect,
                onDisconnect = onDisconnect,
                onRefresh = onRefresh,
                modifier = Modifier.weight(1f)
            )
            AppView.COCKPIT -> CockpitScreen(
                state = state,
                source = source,
                replayState = replayState,
                heartbeat = heartbeat,
                liveVideoFrames = liveVideoFrames,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ConnectionBadge(connection: ConnectionState) {
    val text = when (connection) {
        ConnectionState.Disconnected -> "OFFLINE"
        ConnectionState.Discovering -> "DISCOVERING"
        is ConnectionState.Connecting -> "CONNECTING"
        is ConnectionState.Connected -> "CONNECTED"
        is ConnectionState.Failed -> "FAILED"
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ReplayControls(
    replayState: CaptureReplayState,
    heartbeat: HeartbeatUiState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onRestart: () -> Unit,
    onSpeedChanged: (Double) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Replay · ${replayState.status.displayName()}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (replayState.status == CaptureReplayStatus.COMPLETE) {
                        "Stream complete"
                    } else {
                        heartbeat.ageMs?.let { age ->
                            if (heartbeat.stale) "HEARTBEAT STALE" else "Heartbeat ${age / 1000.0f}s"
                        } ?: "Awaiting heartbeat"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (heartbeat.stale) MaterialTheme.colorScheme.error else LocalContentColor.current
                )
            }
            LinearProgressIndicator(
                progress = { replayState.progress },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when (replayState.status) {
                    CaptureReplayStatus.PLAYING -> OutlinedButton(onClick = onPause) { Text("Pause") }
                    CaptureReplayStatus.COMPLETE -> Button(onClick = onRestart) { Text("Replay") }
                    else -> Button(onClick = onPlay) { Text("Play") }
                }
                OutlinedButton(onClick = onRestart) { Text("Restart") }
                Text(
                    "${replayState.chunkIndex}/${replayState.chunkCount} chunks",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Speed", style = MaterialTheme.typography.labelMedium)
                listOf(0.5, 1.0, 2.0).forEach { speed ->
                    FilterChip(
                        selected = replayState.speed == speed,
                        onClick = { onSpeedChanged(speed) },
                        label = { Text("${speed}×") }
                    )
                }
            }
        }
    }
}

private fun CaptureReplayStatus.displayName(): String = name.lowercase().replaceFirstChar(Char::uppercase)

@Composable
private fun DashboardScreen(
    state: XStarState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ConnectionCard(state)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onConnect) { Text("Connect") }
            OutlinedButton(onClick = onRefresh) { Text("Refresh") }
            OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
        }

        AircraftCard(state)
        BatteryCard(state)
        NavigationCard(state)
        AttitudeCard(state)
        RemoteCard(state)
        GimbalAndLinkCard(state)
        CameraCard(state)
        DiagnosticsCard(state)
    }
}

@Composable
private fun ConnectionCard(state: XStarState) {
    val label = when (val connection = state.connection) {
        ConnectionState.Disconnected -> "Disconnected"
        ConnectionState.Discovering -> "Discovering"
        is ConnectionState.Connecting -> "Connecting · ${connection.stage}"
        is ConnectionState.Connected -> "Connected · ${connection.transport}"
        is ConnectionState.Failed -> "Failed · ${connection.stage}: ${connection.reason}"
    }
    SectionCard("Connection") {
        Text(label, fontWeight = FontWeight.SemiBold)
        state.aircraft.productName?.let { Text(it) }
    }
}

@Composable
private fun AircraftCard(state: XStarState) = SectionCard("Aircraft") {
    Metric("Product", state.aircraft.productName)
    Metric("Firmware", state.aircraft.firmwareVersion)
    Metric("Armed", state.aircraft.armed?.toString())
    Metric("Flight mode", state.aircraft.flightMode)
}

@Composable
private fun BatteryCard(state: XStarState) = SectionCard("Battery") {
    Metric("Remaining", state.battery.percent?.let { "$it%" })
    Metric("Pack", state.battery.packVoltageV?.let { "%.3f V".format(it) })
    Metric("Current", state.battery.currentA?.let { "%.2f A".format(it) })
    Metric("Temperature", state.battery.temperatureC?.let { "%.1f °C".format(it) })
    Metric("Remaining capacity", state.battery.remainingCapacityMah?.let { "$it mAh" })
    Metric("Full capacity", state.battery.fullCapacityMah?.let { "$it mAh" })
    Metric("Discharges", state.battery.dischargeCount?.toString())
    Metric("Firmware", state.battery.firmwareVersion)
    Metric("Cell delta", state.battery.cellDeltaV?.let { "%.3f V".format(it) })
    if (state.battery.cells.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        state.battery.cells.forEach { cell ->
            Metric("Cell ${cell.index}", cell.voltageV?.let { "%.3f V".format(it) })
        }
    }
}

@Composable
private fun NavigationCard(state: XStarState) = SectionCard("Navigation") {
    Metric("GPS", state.navigation.gpsFix)
    Metric("Satellites", state.navigation.satellites?.toString())
    Metric("Altitude", state.navigation.altitudeM?.let { "%.1f m".format(it) })
    Metric("Ultrasonic height", state.navigation.ultrasonicHeightM?.let { "%.2f m".format(it) })
    if (state.navigation.ultrasonicHeightM == null) {
        Metric("Ultrasonic raw", state.navigation.ultrasonicHeightRaw?.let { "%.3f SDK units".format(it) })
    }
    Metric("Ground speed", state.navigation.groundSpeedMps?.let { "%.1f m/s".format(it) })
    Metric("Vertical speed", state.navigation.verticalSpeedMps?.let { "%.1f m/s".format(it) })
}

@Composable
private fun AttitudeCard(state: XStarState) = SectionCard("Attitude") {
    Metric("Roll", state.attitude.rollDeg?.let { "%.1f°".format(it) })
    Metric("Pitch", state.attitude.pitchDeg?.let { "%.1f°".format(it) })
    Metric("Yaw", state.attitude.yawDeg?.let { "%.1f°".format(it) })
}

@Composable
private fun RemoteCard(state: XStarState) = SectionCard("Remote") {
    Metric("Connected", state.remote.connected?.toString())
    Metric("Signal", state.remote.signalPercent?.let { "$it%" })
    Metric("Battery", state.remote.batteryPercent?.let { "$it%" })
    Metric("Image link", state.remote.imageSignalPercent?.let { "$it%" })
}

@Composable
private fun GimbalAndLinkCard(state: XStarState) = SectionCard("Gimbal / Image link") {
    Metric("Gimbal pitch", state.gimbal.pitchDeg?.let { "%.1f°".format(it) })
    Metric("Gimbal status", state.gimbal.status)
    Metric("USB link", state.imageLink.usbEnabled?.let { if (it) "Enabled" else "Disabled" })
    Metric("RF frequency", state.imageLink.rfFrequencyHz?.let { "%.3f MHz".format(it / 1_000_000.0) })
    Metric("RF signal (raw)", state.imageLink.rfSignalValue?.toString())
}

@Composable
private fun CameraCard(state: XStarState) = SectionCard("Camera / FPV") {
    Metric("Camera", state.camera.connected?.let { if (it) "Connected" else "Disconnected" })
    Metric("Mode", state.camera.mode)
    Metric("Recording", state.camera.recording?.toString())
    Metric("Exposure", state.camera.exposureMode)
    Metric("ISO", state.camera.iso)
    Metric("Shutter", state.camera.shutter)
    Metric("Video", if (state.camera.video.receiving) "Receiving" else "No stream")
    Metric("Codec", state.camera.video.codec)
    Metric(
        "Resolution",
        if (state.camera.video.width != null && state.camera.video.height != null)
            "${state.camera.video.width}×${state.camera.video.height}" else null
    )
    Metric("Frames", state.camera.video.framesReceived.toString())
}

@Composable
private fun DiagnosticsCard(state: XStarState) = SectionCard("Diagnostics") {
    Metric("Source", state.diagnostics.source)
    Metric("Last update", state.diagnostics.lastUpdateEpochMs?.toString())
    state.diagnostics.counters.entries.sortedBy { it.key }.forEach { (key, value) ->
        Metric(key, value.toString())
    }
    state.warnings.forEach { warning ->
        Text("${warning.severity}: ${warning.message}")
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                content()
            }
        )
    }
}

@Composable
private fun Metric(label: String, value: String?) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value ?: "—", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
