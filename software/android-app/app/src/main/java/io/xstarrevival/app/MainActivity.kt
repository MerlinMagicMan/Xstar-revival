package io.xstarrevival.app

import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
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
import androidx.core.content.FileProvider
import io.xstarrevival.core.model.ConnectionState
import io.xstarrevival.core.model.XStarState
import io.xstarrevival.core.replay.CaptureReplayState
import io.xstarrevival.core.replay.CaptureReplayStatus
import io.xstarrevival.core.sim.SimulatorControlInput
import io.xstarrevival.core.video.H264VideoFrame
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.Locale

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
                val benchCapture by vm.benchCapture.collectAsStateWithLifecycle()
                val controllerUsb by vm.controllerUsb.collectAsStateWithLifecycle()
                val controllerProbe by vm.controllerProbe.collectAsStateWithLifecycle()

                XStarApp(
                    state = state,
                    source = source,
                    availableSources = vm.availableSources,
                    platformName = platformName,
                    replayState = replayState,
                    heartbeat = heartbeat,
                    liveReadiness = vm.liveReadiness,
                    controllerUsb = controllerUsb,
                    controllerProbe = controllerProbe,
                    benchCapture = benchCapture,
                    liveVideoFrames = vm.liveVideoFrames,
                    onSourceSelected = vm::selectSource,
                    onConnect = vm::connect,
                    onDisconnect = vm::disconnect,
                    onRefresh = vm::refresh,
                    onPlayReplay = vm::playReplay,
                    onPauseReplay = vm::pauseReplay,
                    onRestartReplay = vm::restartReplay,
                    onReplaySpeedChanged = vm::setReplaySpeed,
                    onSimulatorControlsChanged = vm::setSimulatorControls,
                    onSimulatorToggleArm = vm::toggleSimulatorArm,
                    onSimulatorTakeOff = vm::simulatorTakeOff,
                    onSimulatorLand = vm::simulatorLand,
                    onSimulatorToggleRecording = vm::toggleSimulatorRecording,
                    onStartBenchCapture = vm::startBenchCapture,
                    onStopBenchCapture = vm::stopBenchCapture,
                    onShareBenchCapture = ::shareBenchCapture,
                    onStartControllerProbe = vm::startControllerProbe,
                    onStopControllerProbe = vm::stopControllerProbe
                )
            }
        }
    }

    private fun shareBenchCapture(path: String) {
        val archive = File(path)
        if (!archive.isFile) return
        val uri = FileProvider.getUriForFile(this, "$packageName.files", archive)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("X-Star bench capture", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(share, "Share passive X-Star capture"))
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
    liveReadiness: LiveReadinessUiState,
    controllerUsb: ControllerUsbUiState,
    controllerProbe: ControllerProbeUiState,
    benchCapture: BenchCaptureUiState,
    liveVideoFrames: Flow<H264VideoFrame>,
    onSourceSelected: (TelemetrySource) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onPlayReplay: () -> Unit,
    onPauseReplay: () -> Unit,
    onRestartReplay: () -> Unit,
    onReplaySpeedChanged: (Double) -> Unit,
    onSimulatorControlsChanged: (SimulatorControlInput) -> Unit,
    onSimulatorToggleArm: () -> Unit,
    onSimulatorTakeOff: () -> Unit,
    onSimulatorLand: () -> Unit,
    onSimulatorToggleRecording: () -> Unit,
    onStartBenchCapture: () -> Unit,
    onStopBenchCapture: () -> Unit,
    onShareBenchCapture: (String) -> Unit,
    onStartControllerProbe: () -> Unit,
    onStopControllerProbe: () -> Unit
) {
    var selectedView by rememberSaveable { mutableStateOf(AppView.DASHBOARD) }
    var benchReplayVideoPath by rememberSaveable { mutableStateOf<String?>(null) }

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

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
            if (source == TelemetrySource.OFFICIAL_AUTEL) {
                ControllerInputProbeControls(
                    controllerUsb = controllerUsb,
                    probe = controllerProbe,
                    onStart = onStartControllerProbe,
                    onStop = onStopControllerProbe
                )
                BenchCaptureControls(
                    connection = state.connection,
                    readiness = liveReadiness,
                    controllerUsb = controllerUsb,
                    capture = benchCapture,
                    replayActive = benchReplayVideoPath != null,
                    onStart = {
                        benchReplayVideoPath = null
                        onStartBenchCapture()
                    },
                    onStop = onStopBenchCapture,
                    onShare = onShareBenchCapture,
                    onReplay = { path ->
                        benchReplayVideoPath = path
                        selectedView = AppView.COCKPIT
                    },
                    onReturnLive = { benchReplayVideoPath = null }
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
                benchReplayVideoPath = benchReplayVideoPath,
                onSimulatorControlsChanged = onSimulatorControlsChanged,
                onSimulatorToggleArm = onSimulatorToggleArm,
                onSimulatorTakeOff = onSimulatorTakeOff,
                onSimulatorLand = onSimulatorLand,
                onSimulatorToggleRecording = onSimulatorToggleRecording,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ControllerInputProbeControls(
    controllerUsb: ControllerUsbUiState,
    probe: ControllerProbeUiState,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val linkStatus = controllerInputLinkStatus(controllerUsb, probe)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Controller input lab", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "USB link and stick-data stream are checked separately",
                style = MaterialTheme.typography.labelSmall
            )
            ControllerUsbStatusText(controllerUsb)
            Text(
                controllerInputLinkMessage(linkStatus, controllerUsb, probe),
                style = MaterialTheme.typography.bodySmall,
                color = if (linkStatus == ControllerInputLinkStatus.ERROR) {
                    MaterialTheme.colorScheme.error
                } else {
                    LocalContentColor.current
                }
            )
            if (linkStatus == ControllerInputLinkStatus.INPUT_STREAM_UNAVAILABLE &&
                controllerUsb.status == ControllerUsbStatus.XSTAR_LEGACY
            ) {
                Text(
                    "The legacy Autel stack relays controller inputs through aircraft-side endpoints. " +
                        "With the aircraft off, unavailable inputs are expected; this does not mean the sticks are bad.",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            probe.lastChunkHex?.let {
                Text("Latest bytes · $it", style = MaterialTheme.typography.labelSmall)
            }
            if (probe.stickFramesRead > 0) {
                val axes = probe.lastStickAxes.orEmpty().joinToString(" · ") {
                    String.format(Locale.US, "%+.2f", it)
                }
                Text(
                    "Simulator stick frames · ${probe.stickFramesRead} · axes $axes",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (probe.active) {
                Button(onClick = onStop) { Text("Stop listening") }
            } else {
                Button(onClick = onStart, enabled = controllerUsb.controllerDetected) {
                    Text(if (probe.status == ControllerProbeStatus.IDLE) "Check input stream" else "Check again")
                }
            }
            Text(
                "Passive check only · sends 0 bytes · limited to 20 seconds or 1 MB",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private fun controllerInputLinkMessage(
    status: ControllerInputLinkStatus,
    controllerUsb: ControllerUsbUiState,
    probe: ControllerProbeUiState
): String = when (status) {
    ControllerInputLinkStatus.DISCONNECTED -> "Connect the controller to check its input stream."
    ControllerInputLinkStatus.USB_READY -> "USB accessory is connected; stick-data stream has not been checked."
    ControllerInputLinkStatus.LISTENING ->
        "Checking input stream · ${probe.elapsedMs / 1000}s"
    ControllerInputLinkStatus.STREAMING ->
        "Input stream active · ${probe.bytesRead.formatBytes()} · ${probe.chunksRead} chunks"
    ControllerInputLinkStatus.INPUT_STREAM_UNAVAILABLE ->
        "USB accessory connected · controller input stream unavailable"
    ControllerInputLinkStatus.ERROR -> "Input check error · ${probe.error ?: "unknown"}"
}.let { message ->
    if (controllerUsb.status == ControllerUsbStatus.XSTAR_LEGACY &&
        status == ControllerInputLinkStatus.USB_READY
    ) {
        "$message Legacy X-Star controller recognized."
    } else {
        message
    }
}

@Composable
private fun BenchCaptureControls(
    connection: ConnectionState,
    readiness: LiveReadinessUiState,
    controllerUsb: ControllerUsbUiState,
    capture: BenchCaptureUiState,
    replayActive: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onShare: (String) -> Unit,
    onReplay: (String) -> Unit,
    onReturnLive: () -> Unit
) {
    var propsRemoved by rememberSaveable { mutableStateOf(false) }
    val connected = connection is ConnectionState.Connected
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Passive bench capture", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Controller ${controllerUsb.controllerDetected.readyLabel()} · SDK ${readiness.sdkIncluded.readyLabel()} · App key ${readiness.appKeyConfigured.readyLabel()} · Aircraft ${connected.readyLabel()}",
                style = MaterialTheme.typography.labelMedium
            )
            ControllerUsbStatusText(controllerUsb)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = propsRemoved, onCheckedChange = { propsRemoved = it })
                Text("I removed the propellers for powered bench testing.", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                when (capture.status) {
                    BenchCaptureStatus.IDLE -> "Ready for a bounded camera-stream capture."
                    BenchCaptureStatus.WAITING_FOR_KEYFRAME -> "Waiting for the next H.264 keyframe…"
                    BenchCaptureStatus.RECORDING ->
                        "Recording ${capture.framesWritten} frames · ${capture.bytesWritten.formatBytes()} · ${capture.elapsedMs / 1000}s"
                    BenchCaptureStatus.COMPLETE ->
                        "Capture complete · ${capture.framesWritten} frames · ${capture.telemetrySamples} telemetry samples · ${capture.bytesWritten.formatBytes()}"
                    BenchCaptureStatus.ERROR -> "Capture error · ${capture.error ?: "unknown"}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (capture.status == BenchCaptureStatus.ERROR) MaterialTheme.colorScheme.error else LocalContentColor.current
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (capture.active) {
                    Button(onClick = onStop) { Text("Stop capture") }
                } else {
                    Button(
                        onClick = onStart,
                        enabled = propsRemoved && readiness.sdkIncluded && readiness.appKeyConfigured && connected
                    ) { Text("Capture 30 seconds") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                capture.archivePath?.let { archive ->
                    OutlinedButton(onClick = { onShare(archive) }) { Text("Share ZIP") }
                }
                capture.videoPath?.let { video ->
                    if (replayActive) {
                        OutlinedButton(onClick = onReturnLive) { Text("Return live") }
                    } else {
                        OutlinedButton(onClick = { onReplay(video) }) { Text("Replay locally") }
                    }
                }
            }
            Text(
                "Limited to 30 seconds or 64 MB. The ZIP contains received video, frame timing, and redacted telemetry—not the app key, serial number, or GPS location. Camera imagery may still be sensitive.",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun ControllerUsbStatusText(state: ControllerUsbUiState) {
    val identity = state.identity
    val text = when (state.status) {
        ControllerUsbStatus.DISCONNECTED -> "Controller USB not detected"
        ControllerUsbStatus.XSTAR -> "Controller USB connected · ${identity?.model ?: "Autel accessory"}"
        ControllerUsbStatus.XSTAR_LEGACY -> "Controller USB connected · legacy X-Star accessory (${identity?.model})"
        ControllerUsbStatus.OTHER_ACCESSORY ->
            "USB accessory present but not recognized · ${identity?.manufacturer} / ${identity?.model}"
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = when (state.status) {
            ControllerUsbStatus.XSTAR, ControllerUsbStatus.XSTAR_LEGACY -> MaterialTheme.colorScheme.primary
            ControllerUsbStatus.OTHER_ACCESSORY -> MaterialTheme.colorScheme.error
            ControllerUsbStatus.DISCONNECTED -> LocalContentColor.current
        }
    )
}

private fun Boolean.readyLabel(): String = if (this) "READY" else "MISSING"

private fun Long.formatBytes(): String = when {
    this >= 1024L * 1024L -> "%.1f MB".format(this / (1024.0 * 1024.0))
    this >= 1024L -> "%.1f KB".format(this / 1024.0)
    else -> "$this B"
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
