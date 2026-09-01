package io.xstarrevival.app.gs

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.xstarrevival.app.H264LiveVideo
import io.xstarrevival.app.H264ReplayVideo
import io.xstarrevival.app.HeartbeatUiState
import io.xstarrevival.app.LiveVideoStatus
import io.xstarrevival.app.LiveVideoUiState
import io.xstarrevival.app.SimulatorFlightControls
import io.xstarrevival.app.TelemetrySource
import io.xstarrevival.app.VideoReplayStatus
import io.xstarrevival.app.VideoReplayUiState
import io.xstarrevival.core.command.CommandPhase
import io.xstarrevival.core.command.CommandStatus
import io.xstarrevival.core.command.isTerminal
import io.xstarrevival.core.model.ConnectionState
import io.xstarrevival.core.model.Severity
import io.xstarrevival.core.model.XStarState
import io.xstarrevival.core.groundstation.GeoPoint
import io.xstarrevival.core.groundstation.SmartFlightExecutionState
import io.xstarrevival.core.groundstation.SmartFlightMode
import io.xstarrevival.core.groundstation.SmartFlightPhase
import io.xstarrevival.core.sim.SimulatorScenario
import io.xstarrevival.core.sim.SimulatorControlInput
import io.xstarrevival.core.sim.SimulatorViewMode
import io.xstarrevival.core.video.H264VideoFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Exposure
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune

@Composable
fun GsCockpitScreen(
    state: XStarState,
    source: TelemetrySource,
    heartbeat: HeartbeatUiState,
    commandStatus: CommandStatus?,
    simulatorScenario: SimulatorScenario,
    smartFlight: SmartFlightExecutionState,
    liveVideoFrames: Flow<H264VideoFrame>,
    simulatorViewMode: SimulatorViewMode,
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
    onSimulatorScenario: (SimulatorScenario) -> Unit,
    onSimulatorControls: (SimulatorControlInput) -> Unit,
    onToggleSimulatorViewMode: () -> Unit,
    onStartRth: () -> Unit,
    onCancelRth: () -> Unit,
    onGoMissions: () -> Unit,
    onGoAircraft: () -> Unit
) {
    val context = LocalContext.current
    val userSettings = remember(context) { GsSettingsStore(context.applicationContext).load() }
    var lastAlertSignature by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<CockpitDialog?>(null) }
    var overlayMode by rememberSaveable { mutableStateOf(CockpitOverlayMode.HUD) }
    val alert = state.warnings.maxByOrNull { it.severity.ordinal }
    val alertSignature = alert?.let { "${it.id}:${it.severity}:${it.message}" }
    LaunchedEffect(alertSignature, userSettings.audibleAlerts, userSettings.haptics) {
        if (alertSignature == null) {
            lastAlertSignature = null
        } else if (alertSignature != lastAlertSignature) {
            lastAlertSignature = alertSignature
            if (userSettings.haptics && (alert?.severity?.ordinal ?: 0) >= Severity.WARNING.ordinal) {
                context.getSystemService(Vibrator::class.java)?.takeIf { it.hasVibrator() }?.vibrate(
                    VibrationEffect.createOneShot(if (alert?.severity == Severity.CRITICAL) 350L else 160L, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            }
            if (alert?.severity == Severity.CRITICAL || userSettings.audibleAlerts && alert?.severity == Severity.WARNING) {
                val tone = runCatching {
                    ToneGenerator(AudioManager.STREAM_ALARM, if (alert?.severity == Severity.CRITICAL) 100 else 75)
                }.getOrNull()
                if (tone != null) try {
                    tone.startTone(if (alert?.severity == Severity.CRITICAL) ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD else ToneGenerator.TONE_PROP_BEEP2, 350)
                    delay(400L)
                } finally {
                    tone.release()
                }
            }
        }
    }
    val interactiveLockActive = smartFlight.phase == SmartFlightPhase.ACTIVE &&
        smartFlight.mode in setOf(SmartFlightMode.COURSE_LOCK, SmartFlightMode.HOME_LOCK)
    val commandBusy = source == TelemetrySource.SIMULATOR && commandStatus?.phase?.isTerminal == false && !interactiveLockActive
    val onGround = state.navigation.altitudeM?.let { it <= 0.2 } == true
    val airborne = state.aircraft.armed == true && state.navigation.altitudeM?.let { it > 0.2 } == true
    val rthActive = smartFlight.mode == SmartFlightMode.RETURN_TO_HOME && smartFlight.phase == SmartFlightPhase.ACTIVE
    val homeAvailable = state.navigation.homeLatitudeDeg != null && state.navigation.homeLongitudeDeg != null
    val showHud = overlayMode != CockpitOverlayMode.CLEAN
    val showMap = overlayMode == CockpitOverlayMode.FULL
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        GsVideoSurface(
            state,
            source,
            liveVideoFrames,
            userSettings.simulatorVideoUrl,
            Modifier.fillMaxSize()
        )
        if (showHud) {
            GsTopTelemetry(state, source, heartbeat, userSettings, Modifier.align(Alignment.TopCenter))
            GsFlightRail(
                onSmart = { dialog = CockpitDialog.SmartFlight },
                onHealth = { dialog = CockpitDialog.Health },
                onRth = { dialog = CockpitDialog.Rth },
                onMap = onGoMissions,
                onHome = if (showMap) ({ dialog = CockpitDialog.Home }) else null,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp, top = 56.dp, bottom = 8.dp),
                highVisibility = userSettings.highVisibility
            )
            GsCameraRail(
                state = state,
                source = source,
                onRecord = onSimulatorRecord,
                onPhoto = onSimulatorPhoto,
                onExposure = if (showMap) ({ dialog = CockpitDialog.Exposure }) else null,
                onCameraSettings = { dialog = CockpitDialog.Camera },
                onGimbal = { dialog = CockpitDialog.Gimbal },
                commandEnabled = !commandBusy,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp, top = 56.dp, bottom = 8.dp),
                highVisibility = userSettings.highVisibility
            )
            GsReadinessPill(
                state,
                Modifier.align(Alignment.TopEnd).padding(top = 49.dp, end = 78.dp),
                onClick = { dialog = CockpitDialog.Health }
            )
        }
        GsCockpitModeControls(
            overlayMode = overlayMode,
            simulatorViewMode = simulatorViewMode,
            simulatorActive = source == TelemetrySource.SIMULATOR,
            onOverlayMode = {
                overlayMode = when (overlayMode) {
                    CockpitOverlayMode.HUD -> CockpitOverlayMode.FULL
                    CockpitOverlayMode.FULL -> CockpitOverlayMode.CLEAN
                    CockpitOverlayMode.CLEAN -> CockpitOverlayMode.HUD
                }
            },
            onViewMode = onToggleSimulatorViewMode,
            onScenario = { dialog = CockpitDialog.Scenario },
            modifier = Modifier.align(Alignment.TopStart).padding(start = 78.dp, top = 47.dp)
        )
        if (showMap) {
            GsMiniMap(
                state,
                Modifier.align(Alignment.BottomStart).padding(start = 78.dp, bottom = 58.dp),
                onClick = onGoMissions
            )
        }
        if (source == TelemetrySource.SIMULATOR && showHud) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { dialog = CockpitDialog.Controls }, enabled = !commandBusy) { Text("CONTROLS") }
                OutlinedButton(onClick = onSimulatorArm, enabled = !commandBusy && onGround) {
                    Text(if (state.aircraft.armed == true) "DISARM" else "ARM")
                }
                Button(onClick = onSimulatorTakeOff, enabled = !commandBusy && onGround) { Text("TAKEOFF") }
                OutlinedButton(
                    onClick = { dialog = CockpitDialog.Land },
                    enabled = !commandBusy && airborne && smartFlight.phase != SmartFlightPhase.ACTIVE
                ) { Text("LAND") }
            }
            commandStatus?.let {
                GsCommandStatusPill(it, Modifier.align(Alignment.TopStart).padding(start = 78.dp, top = 88.dp))
            }
            if (smartFlight.phase != SmartFlightPhase.IDLE) {
                GsSmartFlightStatusPill(
                    smartFlight,
                    state,
                    Modifier.align(Alignment.TopStart).padding(start = 78.dp, top = 132.dp)
                )
            }
        }
        AnimatedVisibility(
            visible = state.warnings.isNotEmpty(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 92.dp),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = if (userSettings.highVisibility) .96f else .82f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    state.warnings.take(2).forEach { warning ->
                        Text("${warning.severity.name}: ${warning.message}", color = when (warning.severity.name) {
                            "CRITICAL" -> GsColors.Red
                            "WARNING" -> GsColors.Amber
                            "ADVISORY" -> GsColors.Blue
                            else -> GsColors.Blue
                        }, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    when (dialog) {
        CockpitDialog.Health -> GsHealthDialog(state, userSettings.metricUnits, onGoAircraft, onDismiss = { dialog = null })
        CockpitDialog.Rth -> GsConfirmActionDialog(
            title = if (rthActive) "CANCEL RETURN TO HOME" else "RETURN TO HOME",
            body = rthDialogBody(state, source, rthActive),
            confirm = if (rthActive) "CANCEL RTH" else "START RTH",
            confirmEnabled = source == TelemetrySource.SIMULATOR && (rthActive || airborne && homeAvailable),
            onDismiss = { dialog = null },
            onConfirm = {
                if (rthActive) onCancelRth() else onStartRth()
                dialog = null
            }
        )
        CockpitDialog.Land -> GsConfirmActionDialog(
            title = "LAND AIRCRAFT",
            body = "The simulator will descend vertically and disarm after touchdown. Confirm the landing area is clear.",
            confirm = "CONFIRM LAND",
            confirmEnabled = source == TelemetrySource.SIMULATOR && airborne,
            onDismiss = { dialog = null },
            onConfirm = {
                onSimulatorLand()
                dialog = null
            }
        )
        CockpitDialog.SmartFlight -> GsSmartFlightDialog(onGoMissions, onDismiss = { dialog = null })
        CockpitDialog.Home -> GsHomeDialog(state, onDismiss = { dialog = null })
        CockpitDialog.Scenario -> GsScenarioDialog(
            current = simulatorScenario,
            onSelect = {
                onSimulatorScenario(it)
                dialog = null
            },
            onDismiss = { dialog = null }
        )
        CockpitDialog.Controls -> AlertDialog(
            onDismissRequest = {
                onSimulatorControls(SimulatorControlInput())
                dialog = null
            },
            title = { Text("SIMULATOR FLIGHT CONTROLS") },
            text = {
                SimulatorFlightControls(
                    state = state,
                    onControlsChanged = onSimulatorControls,
                    onToggleArm = onSimulatorArm,
                    onTakeOff = onSimulatorTakeOff,
                    onLand = { dialog = CockpitDialog.Land },
                    onToggleRecording = onSimulatorRecord
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onSimulatorControls(SimulatorControlInput())
                    dialog = null
                }) { Text("CLOSE") }
            }
        )
        CockpitDialog.Exposure -> GsExposureDialog(
            state = state,
            enabled = source == TelemetrySource.SIMULATOR && !commandBusy,
            onApply = { iso, shutter, ev ->
                onSimulatorExposure(iso, shutter, ev)
                dialog = null
            },
            onDismiss = { dialog = null }
        )
        CockpitDialog.Camera -> GsCameraSettingsDialog(
            state = state,
            enabled = source == TelemetrySource.SIMULATOR && !commandBusy,
            onMode = onSimulatorCameraMode,
            onApply = {
                onSimulatorCameraConfiguration(it)
                dialog = null
            },
            onDismiss = { dialog = null }
        )
        CockpitDialog.Gimbal -> GsGimbalDialog(
            state = state,
            enabled = source == TelemetrySource.SIMULATOR && !commandBusy,
            onPitch = onSimulatorGimbalPitch,
            onRecenter = onSimulatorGimbalRecenter,
            onCalibrate = onSimulatorGimbalCalibration,
            onConfigure = onSimulatorGimbalConfiguration,
            onDismiss = { dialog = null }
        )
        null -> Unit
    }
}

private enum class CockpitDialog { Health, Rth, Land, SmartFlight, Home, Scenario, Controls, Exposure, Camera, Gimbal }
private enum class CockpitOverlayMode { HUD, FULL, CLEAN }

@Composable
private fun GsVideoSurface(
    state: XStarState,
    source: TelemetrySource,
    liveVideoFrames: Flow<H264VideoFrame>,
    simulatorVideoUrl: String,
    modifier: Modifier = Modifier
) {
    var liveState by remember { mutableStateOf(LiveVideoUiState()) }
    var replayState by remember { mutableStateOf(VideoReplayUiState()) }
    var unrealState by remember(simulatorVideoUrl) { mutableStateOf(UnrealVideoUiState()) }
    Box(modifier) {
        when (source) {
            TelemetrySource.OFFICIAL_AUTEL -> H264LiveVideo(
                frames = liveVideoFrames,
                modifier = Modifier.fillMaxSize(),
                onStateChanged = { liveState = it }
            )
            TelemetrySource.MAVLINK_REPLAY -> H264ReplayVideo(
                modifier = Modifier.fillMaxSize(),
                onStateChanged = { replayState = it }
            )
            TelemetrySource.SIMULATOR -> {
                GsArtificialHorizon(state, Modifier.fillMaxSize())
                if (unrealState.status != UnrealVideoStatus.ERROR) {
                    UnrealSimulatorVideo(
                        streamUrl = simulatorVideoUrl,
                        modifier = Modifier.fillMaxSize(),
                        onStateChanged = { unrealState = it }
                    )
                }
            }
            TelemetrySource.MOCK -> GsArtificialHorizon(state, Modifier.fillMaxSize())
        }
        GsCameraMonitoringOverlay(state, Modifier.fillMaxSize())
        val message = when {
            source == TelemetrySource.OFFICIAL_AUTEL && liveState.status == LiveVideoStatus.ERROR -> "VIDEO LINK ERROR"
            source == TelemetrySource.OFFICIAL_AUTEL && liveState.status != LiveVideoStatus.PLAYING -> "WAITING FOR LIVE H.264"
            source == TelemetrySource.MAVLINK_REPLAY && replayState.status == VideoReplayStatus.ERROR -> "REPLAY VIDEO ERROR"
            source == TelemetrySource.MAVLINK_REPLAY && replayState.status != VideoReplayStatus.PLAYING -> "WAITING FOR REPLAY VIDEO"
            source == TelemetrySource.SIMULATOR && unrealState.status == UnrealVideoStatus.ERROR ->
                "UNREAL VIDEO UNAVAILABLE — ARTIFICIAL HORIZON ACTIVE"
            source == TelemetrySource.SIMULATOR && unrealState.status == UnrealVideoStatus.LOADING ->
                "CONNECTING TO UNREAL VIDEO — CONTROLS REMAIN LOCAL"
            source == TelemetrySource.SIMULATOR && !state.camera.video.receiving -> "VIDEO LINK LOST — TELEMETRY REMAINS"
            source == TelemetrySource.SIMULATOR -> null
            source == TelemetrySource.MOCK -> "MOCK TELEMETRY"
            else -> null
        }
        message?.let {
            Text(
                it,
                Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .7f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                color = GsColors.Amber,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun GsArtificialHorizon(state: XStarState, modifier: Modifier = Modifier) {
    Canvas(modifier.background(Color(0xFF11171D))) {
        val horizon = size.height * .52f + ((state.attitude.pitchDeg ?: 0.0).coerceIn(-30.0, 30.0) / 30.0 * size.height * .22f).toFloat()
        drawRect(Color(0xFF27516C), size = Size(size.width, horizon.coerceAtLeast(0f)))
        drawRect(Color(0xFF29352A), topLeft = Offset(0f, horizon), size = Size(size.width, (size.height - horizon).coerceAtLeast(0f)))
        val center = Offset(size.width / 2f, size.height / 2f)
        val line = Color.White.copy(alpha = .72f)
        drawLine(line, Offset(center.x - 70f, center.y), Offset(center.x - 16f, center.y), 3f)
        drawLine(line, Offset(center.x + 16f, center.y), Offset(center.x + 70f, center.y), 3f)
        drawCircle(line, 5f, center, style = Stroke(2f))
        repeat(8) { index ->
            val x = size.width * index / 7f
            drawLine(Color.White.copy(alpha = .045f), Offset(x, 0f), Offset(x, size.height), 1f)
        }
        repeat(5) { index ->
            val y = size.height * index / 4f
            drawLine(Color.White.copy(alpha = .045f), Offset(0f, y), Offset(size.width, y), 1f)
        }
    }
}

@Composable
private fun GsCameraMonitoringOverlay(state: XStarState, modifier: Modifier = Modifier) {
    Box(modifier) {
        if (state.camera.gridEnabled || state.camera.centerPointEnabled) {
            Canvas(Modifier.fillMaxSize()) {
                val overlay = Color.White.copy(alpha = .38f)
                if (state.camera.gridEnabled) {
                    repeat(2) { index ->
                        val fraction = (index + 1) / 3f
                        drawLine(overlay, Offset(size.width * fraction, 0f), Offset(size.width * fraction, size.height), 1.5f)
                        drawLine(overlay, Offset(0f, size.height * fraction), Offset(size.width, size.height * fraction), 1.5f)
                    }
                }
                if (state.camera.centerPointEnabled) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(GsColors.Orange, 10f, center, style = Stroke(2f))
                    drawCircle(GsColors.Orange, 2.5f, center)
                }
            }
        }
        if (state.camera.histogramEnabled) {
            Card(
                Modifier.align(Alignment.BottomStart).padding(start = 222.dp, bottom = 18.dp).size(136.dp, 74.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = .68f))
            ) {
                Box(Modifier.fillMaxSize().padding(8.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val values = listOf(.08f, .18f, .31f, .46f, .67f, .82f, .71f, .55f, .38f, .24f, .13f)
                        val step = size.width / values.size
                        values.forEachIndexed { index, value ->
                            drawRect(
                                Color.White.copy(alpha = .7f),
                                topLeft = Offset(index * step, size.height * (1f - value)),
                                size = Size(step - 2f, size.height * value)
                            )
                        }
                    }
                    Text("HIST", Modifier.align(Alignment.TopStart), color = GsColors.Muted, fontSize = 7.sp)
                }
            }
        }
        if (state.camera.overexposureWarningEnabled) {
            Text(
                "OVEREXPOSURE MONITOR",
                Modifier.align(Alignment.TopEnd).padding(top = 58.dp, end = 92.dp)
                    .background(Color.Black.copy(alpha = .62f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                color = GsColors.Amber,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun GsExposureDialog(
    state: XStarState,
    enabled: Boolean,
    onApply: (Int?, Double?, Double?) -> Unit,
    onDismiss: () -> Unit
) {
    var iso by remember { mutableStateOf(state.camera.iso?.toIntOrNull()) }
    var shutter by remember { mutableStateOf(state.camera.shutter?.toDoubleOrNull()) }
    var ev by remember { mutableFloatStateOf((state.camera.exposureCompensationEv ?: 0.0).toFloat()) }
    val isoOptions = listOf<Int?>(null, 100, 200, 400, 800, 1600)
    val shutterOptions = listOf<Double?>(null, .000125, .001, .004, .008, .016667, .033333)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("CAMERA EXPOSURE") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CameraChoiceRow("ISO", isoOptions, iso, { it?.toString() ?: "AUTO" }) { iso = it }
                CameraChoiceRow("SHUTTER", shutterOptions, shutter, { formatShutter(it) }) { shutter = it }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Exposure compensation")
                    Text("%+.1f EV".format(ev), color = GsColors.Orange, fontFamily = FontFamily.Monospace)
                }
                Slider(value = ev, onValueChange = { ev = it }, valueRange = -3f..3f, steps = 11)
                GsSettingLine("Mode", if (iso == null && shutter == null) "Auto + EV" else "Manual")
                if (!enabled) Text("Camera writes are available only in the isolated simulator.", color = GsColors.Amber, fontSize = 10.sp)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
        confirmButton = {
            Button(onClick = { onApply(iso, shutter, ev.toDouble()) }, enabled = enabled) { Text("APPLY") }
        }
    )
}

@Composable
private fun GsCameraSettingsDialog(
    state: XStarState,
    enabled: Boolean,
    onMode: (String) -> Unit,
    onApply: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf(state.camera.mode ?: "VIDEO") }
    var whiteBalance by remember { mutableStateOf(state.camera.whiteBalance ?: "AUTO") }
    var photoResolution by remember { mutableStateOf(state.camera.photoResolution ?: "12 MP") }
    var videoResolution by remember { mutableStateOf(state.camera.videoResolution ?: "4K") }
    var frameRate by remember { mutableIntStateOf(state.camera.frameRateFps ?: 30) }
    var timer by remember { mutableIntStateOf(state.camera.timerSeconds ?: 0) }
    var histogram by remember { mutableStateOf(state.camera.histogramEnabled) }
    var overexposure by remember { mutableStateOf(state.camera.overexposureWarningEnabled) }
    var grid by remember { mutableStateOf(state.camera.gridEnabled) }
    var centerPoint by remember { mutableStateOf(state.camera.centerPointEnabled) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("CAMERA SETTINGS") },
        text = {
            LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    GsSettingLine("Storage remaining", state.camera.storageRemainingMb?.let { "%.1f GB".format(it / 1024.0) } ?: "Unavailable")
                    GsSettingLine("Photos captured", state.camera.photosTaken.toString())
                }
                item {
                    CameraChoiceRow("CAPTURE MODE", listOf("PHOTO", "VIDEO"), mode, { it }) {
                        mode = it
                        if (enabled) onMode(it)
                    }
                }
                item { CameraChoiceRow("WHITE BALANCE", listOf("AUTO", "SUNNY", "CLOUDY", "INCANDESCENT", "FLUORESCENT"), whiteBalance, { it }) { whiteBalance = it } }
                item { CameraChoiceRow("PHOTO", listOf("12 MP", "8 MP", "5 MP"), photoResolution, { it }) { photoResolution = it } }
                item { CameraChoiceRow("VIDEO", listOf("4K", "2.7K", "1080P"), videoResolution, { it }) { videoResolution = it } }
                item { CameraChoiceRow("FRAME RATE", listOf(24, 30, 60), frameRate, { "$it FPS" }) { frameRate = it } }
                item { CameraChoiceRow("TIMER", listOf(0, 3, 5, 10), timer, { if (it == 0) "OFF" else "${it}s" }) { timer = it } }
                item { CameraToggle("Histogram", histogram) { histogram = it } }
                item { CameraToggle("Overexposure warning", overexposure) { overexposure = it } }
                item { CameraToggle("Rule-of-thirds grid", grid) { grid = it } }
                item { CameraToggle("Center point", centerPoint) { centerPoint = it } }
                if (!enabled) item {
                    Text("Camera writes are available only in the isolated simulator.", color = GsColors.Amber, fontSize = 10.sp)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
        confirmButton = {
            Button(
                enabled = enabled,
                onClick = {
                    onApply(mapOf(
                        "white_balance" to whiteBalance,
                        "photo_resolution" to photoResolution,
                        "video_resolution" to videoResolution,
                        "frame_rate" to frameRate.toString(),
                        "timer_seconds" to timer.toString(),
                        "histogram" to histogram.toString(),
                        "overexposure_warning" to overexposure.toString(),
                        "grid" to grid.toString(),
                        "center_point" to centerPoint.toString()
                    ))
                }
            ) { Text("APPLY") }
        }
    )
}

@Composable
private fun GsGimbalDialog(
    state: XStarState,
    enabled: Boolean,
    onPitch: (Double) -> Unit,
    onRecenter: () -> Unit,
    onCalibrate: () -> Unit,
    onConfigure: (Double, Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var pitch by remember { mutableFloatStateOf((state.gimbal.pitchDeg ?: 0.0).toFloat()) }
    var sensitivity by remember { mutableFloatStateOf((state.gimbal.sensitivity ?: .5).toFloat()) }
    var smoothing by remember { mutableFloatStateOf((state.gimbal.smoothing ?: .6).toFloat()) }
    var pitchSpeed by remember { mutableFloatStateOf((state.gimbal.pitchSpeed ?: .5).toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("GIMBAL CONTROL") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                GsSettingLine("Status", state.gimbal.status ?: "Unavailable")
                GsSettingLine("Calibration", when (state.gimbal.calibrated) {
                    true -> "Calibrated"
                    false -> "Required"
                    null -> "Unknown"
                })
                GimbalSlider("Pitch", pitch, -90f..30f, "°") { pitch = it }
                GimbalSlider("Sensitivity", sensitivity, 0f..1f, "") { sensitivity = it }
                GimbalSlider("Smoothing", smoothing, 0f..1f, "") { smoothing = it }
                GimbalSlider("Pitch speed", pitchSpeed, .1f..1f, "") { pitchSpeed = it }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onRecenter(); onDismiss() },
                        enabled = enabled,
                        modifier = Modifier.weight(1f)
                    ) { Text("RECENTER") }
                    OutlinedButton(
                        onClick = { onCalibrate(); onDismiss() },
                        enabled = enabled && state.aircraft.armed != true && (state.navigation.altitudeM ?: 1.0) <= .2,
                        modifier = Modifier.weight(1f)
                    ) { Text("CALIBRATE") }
                }
                if (!enabled) Text("Gimbal writes are available only in the isolated simulator.", color = GsColors.Amber, fontSize = 10.sp)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
        confirmButton = {
            Button(
                enabled = enabled,
                onClick = {
                    onPitch(pitch.toDouble())
                    onConfigure(sensitivity.toDouble(), smoothing.toDouble(), pitchSpeed.toDouble())
                    onDismiss()
                }
            ) { Text("APPLY") }
        }
    )
}

@Composable
private fun GimbalSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    suffix: String,
    onValue: (Float) -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = GsColors.White)
            Text("%.1f%s".format(value, suffix), color = GsColors.Orange, fontFamily = FontFamily.Monospace)
        }
        Slider(value = value, onValueChange = onValue, valueRange = range)
    }
}

@Composable
private fun <T> CameraChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = GsColors.Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            options.forEach { option ->
                Card(
                    Modifier.weight(1f).clickable { onSelect(option) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (option == selected) GsColors.Orange.copy(alpha = .22f) else GsColors.Panel2
                    )
                ) {
                    Text(
                        label(option),
                        Modifier.align(Alignment.CenterHorizontally).padding(horizontal = 4.dp, vertical = 8.dp),
                        color = if (option == selected) GsColors.Orange else GsColors.White,
                        fontSize = 8.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraToggle(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = GsColors.White)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

private fun formatShutter(seconds: Double?): String = when (seconds) {
    null -> "AUTO"
    else -> if (seconds >= 1.0) "${seconds}s" else "1/${(1.0 / seconds).roundToInt()}"
}

@Composable
private fun GsTopTelemetry(
    state: XStarState,
    source: TelemetrySource,
    heartbeat: HeartbeatUiState,
    settings: GsUserSettings,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.background(Color.Black.copy(alpha = if (settings.highVisibility) .94f else .72f)).fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        GsTelemetryItem("MODE", state.aircraft.flightMode ?: if (state.connection is ConnectionState.Connected) "—" else "OFFLINE")
        GsTelemetryItem("H", formatAltitude(state.navigation.altitudeM, settings.metricUnits))
        GsTelemetryItem("V/S", formatVerticalSpeed(state.navigation.verticalSpeedMps, settings.metricUnits))
        GsTelemetryItem("SPD", formatGroundSpeed(state.navigation.groundSpeedMps, settings.metricUnits))
        GsTelemetryItem("SAT", state.navigation.satellites?.toString() ?: "—")
        GsTelemetryItem("RC", state.remote.signalPercent?.let { "$it%" } ?: "—")
        GsTelemetryItem("HD", state.remote.imageSignalPercent?.let { "$it%" } ?: "—")
        GsTelemetryItem("BAT", state.battery.percent?.let { "$it%" } ?: "—", batteryAccent(state.battery.percent))
        GsTelemetryItem("SRC", when (source) {
            TelemetrySource.OFFICIAL_AUTEL -> "LIVE"
            TelemetrySource.SIMULATOR -> "SIM"
            TelemetrySource.MAVLINK_REPLAY -> "REPLAY"
            TelemetrySource.MOCK -> "MOCK"
        })
        if (heartbeat.stale) {
            Text(
                "LOST ${heartbeat.ageMs?.let { "${it / 1_000}s" } ?: "LINK"}",
                color = GsColors.Red,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun GsCockpitModeControls(
    overlayMode: CockpitOverlayMode,
    simulatorViewMode: SimulatorViewMode,
    simulatorActive: Boolean,
    onOverlayMode: () -> Unit,
    onViewMode: () -> Unit,
    onScenario: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        GsCockpitModeChip("OVERLAY ${overlayMode.name}", onOverlayMode)
        if (simulatorActive) {
            GsCockpitModeChip("VIEW ${simulatorViewMode.name}", onViewMode, GsColors.Orange)
            GsCockpitModeChip("SCENE", onScenario)
        }
    }
}

@Composable
private fun GsCockpitModeChip(
    label: String,
    onClick: () -> Unit,
    accent: Color = GsColors.White
) {
    Text(
        label,
        modifier = Modifier
            .heightIn(min = 30.dp)
            .background(Color.Black.copy(alpha = .76f), RoundedCornerShape(8.dp))
            .border(1.dp, accent.copy(alpha = .42f), RoundedCornerShape(8.dp))
            .semantics { contentDescription = label; role = Role.Button }
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        color = accent,
        fontSize = 8.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace
    )
}

@Composable
private fun GsTelemetryItem(label: String, value: String, color: Color = GsColors.White) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = GsColors.Muted, fontSize = 9.sp)
        Text(value, color = color, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GsFlightRail(
    onSmart: () -> Unit,
    onHealth: () -> Unit,
    onRth: () -> Unit,
    onMap: () -> Unit,
    onHome: (() -> Unit)?,
    modifier: Modifier = Modifier,
    highVisibility: Boolean = false
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        GsRailButton(Icons.Default.AutoAwesome, "SMART", onSmart, highVisibility = highVisibility)
        GsRailButton(Icons.Default.HealthAndSafety, "HEALTH", onHealth, highVisibility = highVisibility)
        GsRailButton(Icons.Default.Home, "RTH", onRth, highVisibility = highVisibility)
        GsRailButton(Icons.Default.Map, "MAP", onMap, highVisibility = highVisibility)
        if (onHome != null) GsRailButton(Icons.Default.MyLocation, "HOME", onHome, highVisibility = highVisibility)
    }
}

@Composable
private fun GsCameraRail(
    state: XStarState,
    source: TelemetrySource,
    onRecord: () -> Unit,
    onPhoto: () -> Unit,
    onExposure: (() -> Unit)?,
    onCameraSettings: () -> Unit,
    onGimbal: () -> Unit,
    commandEnabled: Boolean,
    modifier: Modifier = Modifier,
    highVisibility: Boolean = false
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        GsRailButton(
            icon = Icons.Default.FiberManualRecord,
            label = if (state.camera.recording == true) "STOP" else "REC",
            onClick = onRecord,
            enabled = source == TelemetrySource.SIMULATOR && commandEnabled,
            accent = if (state.camera.recording == true) GsColors.Red else GsColors.Orange,
            highVisibility = highVisibility
        )
        if (state.camera.recording == true) {
            Text(state.camera.recordingDurationSeconds?.let(::formatRecordingTime) ?: "--:--", color = GsColors.Red, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Text(
                state.camera.storageRemainingMb?.let { "~${formatStorageTime(it, state.camera.videoResolution)} left" } ?: "storage —",
                color = GsColors.Muted,
                fontSize = 8.sp
            )
        }
        GsRailButton(Icons.Default.PhotoCamera, "PHOTO", onPhoto, enabled = source == TelemetrySource.SIMULATOR && commandEnabled, highVisibility = highVisibility)
        if (onExposure != null) {
            GsRailButton(
                Icons.Default.Exposure,
                state.camera.exposureCompensationEv?.let { "%+.1f EV".format(it) } ?: "AUTO EV",
                onExposure,
                highVisibility = highVisibility
            )
        }
        GsRailButton(Icons.Default.SwapVert, state.gimbal.pitchDeg?.let { "${it.roundToInt()}°" } ?: "GIMBAL", onGimbal, highVisibility = highVisibility)
        GsRailButton(Icons.Default.Tune, state.camera.mode ?: "CAM", onCameraSettings, highVisibility = highVisibility)
    }
}

private fun formatRecordingTime(seconds: Double): String {
    val total = seconds.coerceAtLeast(0.0).toInt()
    return "%02d:%02d".format(total / 60, total % 60)
}

private fun formatStorageTime(storageRemainingMb: Long, resolution: String?): String {
    val megabytesPerSecond = when (resolution?.uppercase()) {
        "4K" -> 8.0
        "2.7K" -> 5.0
        else -> 3.0
    }
    return formatRecordingTime(storageRemainingMb / megabytesPerSecond)
}

@Composable
private fun GsCommandStatusPill(status: CommandStatus, modifier: Modifier = Modifier) {
    val color = when (status.phase) {
        CommandPhase.COMPLETED -> GsColors.Green
        CommandPhase.REJECTED, CommandPhase.FAILED, CommandPhase.TIMED_OUT,
        CommandPhase.CANCELLED, CommandPhase.UNSUPPORTED -> GsColors.Red
        else -> GsColors.Orange
    }
    Card(
        modifier,
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = .84f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
            Text(
                "${status.request.command.kind.name.replace('_', ' ')} · ${status.phase.name}",
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            status.detail?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = GsColors.Muted, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun GsSmartFlightStatusPill(
    smartFlight: SmartFlightExecutionState,
    aircraftState: XStarState,
    modifier: Modifier = Modifier
) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = .84f))) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
            Text(
                "${smartFlight.mode.name.replace('_', ' ')} · ${smartFlight.phase}",
                color = GsColors.Blue,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            smartFlight.progress?.let { Text("Progress ${(it * 100).toInt()}%", color = GsColors.Muted, fontSize = 9.sp) }
            if (smartFlight.mode == SmartFlightMode.RETURN_TO_HOME) {
                val distance = smartFlight.distanceToTargetM ?: distanceToHomeMeters(aircraftState)
                val altitude = aircraftState.navigation.altitudeM
                val etaSeconds = rthEtaSeconds(distance, altitude)
                Text(
                    "Home ${formatMeters(distance)} · Alt ${formatMeters(altitude)} · ETA ${formatDuration(etaSeconds)}",
                    color = GsColors.White,
                    fontSize = 9.sp
                )
                Text("Battery ${aircraftState.battery.percent?.let { "$it%" } ?: "—"}", color = GsColors.Muted, fontSize = 9.sp)
            }
            smartFlight.detail?.let { Text(it, color = GsColors.Muted, fontSize = 9.sp) }
        }
    }
}

private fun rthDialogBody(state: XStarState, source: TelemetrySource, active: Boolean): String {
    if (source != TelemetrySource.SIMULATOR) {
        return "Live Return-to-Home transmission remains disabled until the aircraft command protocol is validated."
    }
    val altitude = state.navigation.altitudeM
    val returnAltitude = max(20.0, altitude ?: 0.0)
    val distance = distanceToHomeMeters(state)
    val etaSeconds = rthEtaSeconds(distance, altitude)
    val arrivalBattery = state.battery.percent?.let { current ->
        etaSeconds?.let { (current - it * 0.025).roundToInt().coerceAtLeast(0) }
    }
    val homeConfidence = when {
        state.navigation.homeLatitudeDeg == null || state.navigation.homeLongitudeDeg == null -> "Unavailable"
        state.navigation.gpsFix == null -> "Coordinates set; GPS accuracy not reported"
        else -> "${state.navigation.gpsFix} · ${state.navigation.satellites?.let { "$it satellites" } ?: "satellite count unavailable"}; accuracy not reported"
    }
    val action = if (active) {
        "Cancelling returns manual control and stops the automated return sequence."
    } else {
        "Simulator will climb to the RTH altitude, return to the confirmed Home Point, and land."
    }
    return buildString {
        appendLine(action)
        appendLine()
        appendLine("Current altitude       ${formatMeters(altitude)}")
        appendLine("RTH altitude           ${formatMeters(returnAltitude)}")
        appendLine("Home distance          ${formatMeters(distance)}")
        appendLine("Estimated arrival      ${arrivalBattery?.let { "$it% battery" } ?: "Unavailable"}")
        append("Home-point confidence  $homeConfidence")
    }
}

private fun distanceToHomeMeters(state: XStarState): Double? {
    val latitude = state.navigation.latitudeDeg ?: return null
    val longitude = state.navigation.longitudeDeg ?: return null
    val homeLatitude = state.navigation.homeLatitudeDeg ?: return null
    val homeLongitude = state.navigation.homeLongitudeDeg ?: return null
    val latitude1 = Math.toRadians(latitude)
    val latitude2 = Math.toRadians(homeLatitude)
    val deltaLatitude = latitude2 - latitude1
    val deltaLongitude = Math.toRadians(homeLongitude - longitude)
    val haversine = sin(deltaLatitude / 2) * sin(deltaLatitude / 2) +
        cos(latitude1) * cos(latitude2) * sin(deltaLongitude / 2) * sin(deltaLongitude / 2)
    return 2.0 * 6_371_000.0 * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
}

private fun rthEtaSeconds(distanceM: Double?, altitudeM: Double?): Int? {
    if (distanceM == null || altitudeM == null) return null
    val returnSeconds = distanceM / 8.0
    val landingSeconds = altitudeM / 0.8
    return (returnSeconds + landingSeconds).roundToInt().coerceAtLeast(0)
}

private fun formatMeters(value: Double?): String = value?.let { "${it.roundToInt()} m" } ?: "—"

private fun formatDuration(seconds: Int?): String = seconds?.let {
    "%02d:%02d".format(it / 60, it % 60)
} ?: "—"

@Composable
private fun GsRailButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    accent: Color = GsColors.White,
    enabled: Boolean = true,
    highVisibility: Boolean = false
) {
    Column(
        Modifier.width(52.dp).heightIn(min = 48.dp).background(
            Color.Black.copy(alpha = when {
                !enabled -> .56f
                highVisibility -> .94f
                else -> .72f
            }),
            RoundedCornerShape(12.dp)
        )
            .border(1.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(12.dp))
            .semantics { contentDescription = label; role = Role.Button }
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) accent else GsColors.Faint, modifier = Modifier.size(18.dp))
        Text(label, color = GsColors.Muted, fontSize = 7.sp)
    }
}

@Composable
private fun GsMiniMap(state: XStarState, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val aircraft = state.navigation.latitudeDeg?.let { latitude ->
        state.navigation.longitudeDeg?.let { longitude -> GeoPoint(latitude, longitude) }
    }
    val home = state.navigation.homeLatitudeDeg?.let { latitude ->
        state.navigation.homeLongitudeDeg?.let { longitude -> GeoPoint(latitude, longitude) }
    }
    Box(
        modifier.size(150.dp, 94.dp).background(Color(0xDD10151B), RoundedCornerShape(14.dp))
            .border(1.dp, Color.White.copy(alpha = .16f), RoundedCornerShape(14.dp)).clickable(onClick = onClick)
    ) {
        GsOperationalMap(
            modifier = Modifier.fillMaxSize(),
            aircraft = aircraft,
            aircraftHeadingDeg = state.attitude.yawDeg,
            home = home,
            fitKey = aircraft,
            followAircraft = true,
            showControls = false,
            label = "LIVE MAP"
        )
        Text(
            state.navigation.latitudeDeg?.let { "%.5f".format(it) } ?: "NO GPS",
            Modifier.align(Alignment.BottomEnd).padding(8.dp), color = GsColors.Muted, fontSize = 8.sp, fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun GsReadinessPill(state: XStarState, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val readiness = state.readiness()
    val color = when (readiness.level) {
        GsReadiness.READY -> GsColors.Green
        GsReadiness.WARNING, GsReadiness.CHECKING -> GsColors.Amber
        GsReadiness.CRITICAL -> GsColors.Red
        GsReadiness.OFFLINE -> GsColors.Muted
    }
    Row(
        modifier.heightIn(min = 34.dp).background(Color.Black.copy(alpha = .75f), RoundedCornerShape(999.dp))
            .border(1.dp, color.copy(alpha = .5f), RoundedCornerShape(999.dp)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GsDot(color)
        Spacer(Modifier.width(8.dp))
        Text(readiness.label, color = color, fontWeight = FontWeight.Bold, fontSize = 9.sp)
    }
}

@Composable
private fun GsHealthDialog(state: XStarState, metricUnits: Boolean, onGoAircraft: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onGoAircraft) { Text("DETAILS") }
                Button(onClick = onDismiss) { Text("DONE") }
            }
        },
        title = { Text("AIRCRAFT HEALTH") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GsHealthRow("Flight controller", state.connection is ConnectionState.Connected)
                GsHealthRow(
                    "GPS / GLONASS",
                    state.navigation.satellites?.let { it >= 6 } == true,
                    state.navigation.satellites?.let { "$it satellites" } ?: "Unavailable"
                )
                GsHealthRow(
                    "Remote controller",
                    state.remote.connected ?: (state.connection is ConnectionState.Connected),
                    state.remote.signalPercent?.let { "$it% signal" }
                )
                GsHealthRow(
                    "HD video",
                    state.camera.video.receiving && (state.remote.imageSignalPercent?.let { it >= 20 } ?: true),
                    state.remote.imageSignalPercent?.let { "$it% signal" }
                )
                GsHealthRow("Camera", state.camera.connected == true, state.camera.mode)
                HorizontalDivider()
                Text("BATTERY", fontWeight = FontWeight.Bold)
                Text("${state.battery.packVoltageV?.let { "%.2f V".format(it) } ?: "—"}   ${state.battery.percent?.let { "$it%" } ?: "—"}   ${formatTemperature(state.battery.temperatureC, metricUnits)}")
                Text("Cell delta ${state.battery.cellDeltaV?.let { "%.3f V".format(it) } ?: "—"} · Cycles ${state.battery.dischargeCount ?: "—"}")
            }
        }
    )
}

internal fun formatAltitude(valueMeters: Double?, metric: Boolean): String = valueMeters?.let {
    if (metric) "%.1f m".format(it) else "%.0f ft".format(it * 3.28084)
} ?: "—"

internal fun formatVerticalSpeed(valueMetersPerSecond: Double?, metric: Boolean): String = valueMetersPerSecond?.let {
    if (metric) "%.1f m/s".format(it) else "%.1f ft/s".format(it * 3.28084)
} ?: "—"

internal fun formatGroundSpeed(valueMetersPerSecond: Double?, metric: Boolean): String = valueMetersPerSecond?.let {
    if (metric) "%.1f m/s".format(it) else "%.1f mph".format(it * 2.236936)
} ?: "—"

internal fun formatTemperature(valueCelsius: Double?, metric: Boolean): String = valueCelsius?.let {
    if (metric) "%.1f°C".format(it) else "%.1f°F".format(it * 9.0 / 5.0 + 32.0)
} ?: "—"

@Composable
private fun GsHealthRow(name: String, good: Boolean, detail: String? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text((if (good) "READY · " else "CHECK · ") + name, color = if (good) GsColors.Green else GsColors.Amber)
        if (detail != null) Text(detail, color = GsColors.Muted, fontSize = 12.sp)
    }
}

@Composable
private fun GsConfirmActionDialog(
    title: String,
    body: String,
    confirm: String,
    confirmEnabled: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
        confirmButton = { Button(onClick = onConfirm, enabled = confirmEnabled) { Text(confirm) } }
    )
}

@Composable
private fun GsSmartFlightDialog(onGoMissions: () -> Unit, onDismiss: () -> Unit) {
    val modes = listOf("Manual", "Orbit", "Follow", "Waypoints", "Course Lock", "Home Lock")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SMART FLIGHT") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                modes.forEach { mode ->
                    Card(
                        Modifier.fillMaxWidth().clickable {
                            if (mode == "Waypoints" || mode == "Orbit" || mode == "Follow") onGoMissions()
                        },
                        colors = CardDefaults.cardColors(containerColor = GsColors.Panel2)
                    ) { Text(mode, Modifier.padding(14.dp), color = GsColors.White) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CLOSE") } }
    )
}

@Composable
private fun GsHomeDialog(state: XStarState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("HOME POINT") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GsSettingLine("Latitude", state.navigation.homeLatitudeDeg?.let { "%.6f".format(it) } ?: "—")
                GsSettingLine("Longitude", state.navigation.homeLongitudeDeg?.let { "%.6f".format(it) } ?: "—")
                Text("Changing the Home point while airborne should require explicit confirmation once the command path is validated.", color = GsColors.Muted, fontSize = 12.sp)
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("DONE") } }
    )
}

@Composable
private fun GsScenarioDialog(
    current: SimulatorScenario,
    onSelect: (SimulatorScenario) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SIMULATOR SCENARIO") },
        text = {
            LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(SimulatorScenario.entries) { index, scenario ->
                    if (index == 0 || SimulatorScenario.entries[index - 1].category != scenario.category) {
                        Text(
                            scenario.category.label.uppercase(),
                            color = GsColors.Orange,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = if (index == 0) 0.dp else 8.dp)
                        )
                    }
                    Card(
                        Modifier.fillMaxWidth().clickable { onSelect(scenario) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (scenario == current) GsColors.Orange.copy(alpha = .18f) else GsColors.Panel2
                        )
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(scenario.label, color = GsColors.White)
                            if (scenario == current) Text("ACTIVE", color = GsColors.Green, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CLOSE") } }
    )
}
