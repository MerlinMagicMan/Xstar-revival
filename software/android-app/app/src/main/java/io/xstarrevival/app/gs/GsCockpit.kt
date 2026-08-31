package io.xstarrevival.app.gs

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import io.xstarrevival.core.model.XStarState
import io.xstarrevival.core.groundstation.GeoPoint
import io.xstarrevival.core.groundstation.SmartFlightExecutionState
import io.xstarrevival.core.groundstation.SmartFlightMode
import io.xstarrevival.core.groundstation.SmartFlightPhase
import io.xstarrevival.core.sim.SimulatorScenario
import io.xstarrevival.core.sim.SimulatorControlInput
import io.xstarrevival.core.video.H264VideoFrame
import kotlinx.coroutines.flow.Flow
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun GsCockpitScreen(
    state: XStarState,
    source: TelemetrySource,
    heartbeat: HeartbeatUiState,
    commandStatus: CommandStatus?,
    simulatorScenario: SimulatorScenario,
    smartFlight: SmartFlightExecutionState,
    liveVideoFrames: Flow<H264VideoFrame>,
    onSimulatorArm: () -> Unit,
    onSimulatorTakeOff: () -> Unit,
    onSimulatorLand: () -> Unit,
    onSimulatorRecord: () -> Unit,
    onSimulatorScenario: (SimulatorScenario) -> Unit,
    onSimulatorControls: (SimulatorControlInput) -> Unit,
    onStartRth: () -> Unit,
    onCancelRth: () -> Unit,
    onGoMissions: () -> Unit,
    onGoAircraft: () -> Unit
) {
    var dialog by remember { mutableStateOf<CockpitDialog?>(null) }
    val interactiveLockActive = smartFlight.phase == SmartFlightPhase.ACTIVE &&
        smartFlight.mode in setOf(SmartFlightMode.COURSE_LOCK, SmartFlightMode.HOME_LOCK)
    val commandBusy = source == TelemetrySource.SIMULATOR && commandStatus?.phase?.isTerminal == false && !interactiveLockActive
    val grounded = (state.navigation.altitudeM ?: 0.0) <= 0.2
    val airborne = state.aircraft.armed == true && !grounded
    val rthActive = smartFlight.mode == SmartFlightMode.RETURN_TO_HOME && smartFlight.phase == SmartFlightPhase.ACTIVE
    val homeAvailable = state.navigation.homeLatitudeDeg != null && state.navigation.homeLongitudeDeg != null
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        GsVideoSurface(state, source, liveVideoFrames, Modifier.fillMaxSize())
        GsTopTelemetry(state, source, heartbeat, Modifier.align(Alignment.TopCenter))
        GsFlightRail(
            onSmart = { dialog = CockpitDialog.SmartFlight },
            onHealth = { dialog = CockpitDialog.Health },
            onRth = { dialog = CockpitDialog.Rth },
            onMap = onGoMissions,
            onHome = { dialog = CockpitDialog.Home },
            onScenario = if (source == TelemetrySource.SIMULATOR) ({ dialog = CockpitDialog.Scenario }) else null,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp)
        )
        GsCameraRail(
            state = state,
            source = source,
            onRecord = onSimulatorRecord,
            commandEnabled = !commandBusy,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp)
        )
        GsMiniMap(state, Modifier.align(Alignment.BottomStart).padding(18.dp), onClick = onGoMissions)
        GsReadinessPill(state, Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp), onClick = { dialog = CockpitDialog.Health })
        if (source == TelemetrySource.SIMULATOR) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { dialog = CockpitDialog.Controls }, enabled = !commandBusy) { Text("CONTROLS") }
                OutlinedButton(onClick = onSimulatorArm, enabled = !commandBusy && grounded) {
                    Text(if (state.aircraft.armed == true) "DISARM" else "ARM")
                }
                Button(onClick = onSimulatorTakeOff, enabled = !commandBusy && grounded) { Text("TAKEOFF") }
                OutlinedButton(
                    onClick = onSimulatorLand,
                    enabled = !commandBusy && airborne && smartFlight.phase != SmartFlightPhase.ACTIVE
                ) { Text("LAND") }
            }
            commandStatus?.let {
                GsCommandStatusPill(it, Modifier.align(Alignment.TopStart).padding(start = 94.dp, top = 58.dp))
            }
            if (smartFlight.phase != SmartFlightPhase.IDLE) {
                GsSmartFlightStatusPill(
                    smartFlight,
                    state,
                    Modifier.align(Alignment.TopStart).padding(start = 94.dp, top = 112.dp)
                )
            }
        }
        if (state.warnings.isNotEmpty()) {
            Card(
                Modifier.align(Alignment.TopCenter).padding(top = 56.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = .82f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    state.warnings.take(2).forEach { warning ->
                        Text(warning.message, color = when (warning.severity.name) {
                            "CRITICAL" -> GsColors.Red
                            "WARNING" -> GsColors.Amber
                            else -> GsColors.Blue
                        }, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    when (dialog) {
        CockpitDialog.Health -> GsHealthDialog(state, onGoAircraft, onDismiss = { dialog = null })
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
                    onLand = onSimulatorLand,
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
        null -> Unit
    }
}

private enum class CockpitDialog { Health, Rth, SmartFlight, Home, Scenario, Controls }

@Composable
private fun GsVideoSurface(state: XStarState, source: TelemetrySource, liveVideoFrames: Flow<H264VideoFrame>, modifier: Modifier = Modifier) {
    var liveState by remember { mutableStateOf(LiveVideoUiState()) }
    var replayState by remember { mutableStateOf(VideoReplayUiState()) }
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
            TelemetrySource.MOCK, TelemetrySource.SIMULATOR -> GsArtificialHorizon(state, Modifier.fillMaxSize())
        }
        val message = when {
            source == TelemetrySource.OFFICIAL_AUTEL && liveState.status == LiveVideoStatus.ERROR -> "VIDEO LINK ERROR"
            source == TelemetrySource.OFFICIAL_AUTEL && liveState.status != LiveVideoStatus.PLAYING -> "WAITING FOR LIVE H.264"
            source == TelemetrySource.MAVLINK_REPLAY && replayState.status == VideoReplayStatus.ERROR -> "REPLAY VIDEO ERROR"
            source == TelemetrySource.MAVLINK_REPLAY && replayState.status != VideoReplayStatus.PLAYING -> "WAITING FOR REPLAY VIDEO"
            source == TelemetrySource.SIMULATOR && !state.camera.video.receiving -> "VIDEO LINK LOST — TELEMETRY REMAINS"
            source == TelemetrySource.SIMULATOR -> "SIMULATION — VALIDATED LOCAL COMMANDS"
            source == TelemetrySource.MOCK -> "MOCK TELEMETRY"
            else -> null
        }
        message?.let {
            Text(
                it,
                Modifier.align(Alignment.BottomCenter).padding(bottom = 58.dp).background(Color.Black.copy(alpha = .7f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
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
private fun GsTopTelemetry(state: XStarState, source: TelemetrySource, heartbeat: HeartbeatUiState, modifier: Modifier = Modifier) {
    Row(
        modifier.background(Color.Black.copy(alpha = .72f)).fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        GsTelemetryItem("MODE", state.aircraft.flightMode ?: if (state.connection is ConnectionState.Connected) "GPS" else "OFFLINE")
        GsTelemetryItem("H", state.navigation.altitudeM?.let { "%.1fm".format(it) } ?: "—")
        GsTelemetryItem("V/S", state.navigation.verticalSpeedMps?.let { "%.1f".format(it) } ?: "—")
        GsTelemetryItem("SPD", state.navigation.groundSpeedMps?.let { "%.1f".format(it) } ?: "—")
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
    onHome: () -> Unit,
    onScenario: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GsRailButton("✦", "SMART", onSmart)
        GsRailButton("✓", "HEALTH", onHealth)
        GsRailButton("⌂", "RTH", onRth)
        GsRailButton("⌖", "MAP", onMap)
        GsRailButton("H", "HOME", onHome)
        if (onScenario != null) GsRailButton("⚠", "SCENARIO", onScenario, GsColors.Amber)
    }
}

@Composable
private fun GsCameraRail(
    state: XStarState,
    source: TelemetrySource,
    onRecord: () -> Unit,
    commandEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        GsRailButton(
            glyph = if (state.camera.recording == true) "■" else "●",
            label = if (state.camera.recording == true) "STOP" else "REC",
            onClick = if (source == TelemetrySource.SIMULATOR && commandEnabled) onRecord else ({ }),
            accent = if (state.camera.recording == true) GsColors.Red else GsColors.Orange
        )
        GsRailButton("◉", "PHOTO") { }
        GsRailButton("EV", state.camera.exposureMode ?: "AUTO") { }
        GsRailButton("↕", state.gimbal.pitchDeg?.let { "${it.roundToInt()}°" } ?: "GIMBAL") { }
        GsRailButton("⚙", "CAM") { }
    }
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
        else -> "${state.navigation.gpsFix} · ${state.navigation.satellites ?: 0} satellites; accuracy not reported"
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
private fun GsRailButton(glyph: String, label: String, onClick: () -> Unit, accent: Color = GsColors.White) {
    Column(
        Modifier.width(64.dp).background(Color.Black.copy(alpha = .64f), RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(glyph, color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = GsColors.Muted, fontSize = 8.sp)
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
        modifier.size(190.dp, 120.dp).background(Color(0xDD10151B), RoundedCornerShape(14.dp))
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
            state.navigation.latitudeDeg?.let { "%.5f".format(it) } ?: "OFFLINE",
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
        modifier.background(Color.Black.copy(alpha = .75f), RoundedCornerShape(999.dp))
            .border(1.dp, color.copy(alpha = .5f), RoundedCornerShape(999.dp)).clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GsDot(color)
        Spacer(Modifier.width(8.dp))
        Text(readiness.label, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun GsHealthDialog(state: XStarState, onGoAircraft: () -> Unit, onDismiss: () -> Unit) {
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
                GsHealthRow("GPS / GLONASS", (state.navigation.satellites ?: 0) >= 6, "${state.navigation.satellites ?: 0} satellites")
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
                Text("${state.battery.packVoltageV?.let { "%.2f V".format(it) } ?: "—"}   ${state.battery.percent?.let { "$it%" } ?: "—"}   ${state.battery.temperatureC?.let { "%.1f°C".format(it) } ?: "—"}")
                Text("Cell delta ${state.battery.cellDeltaV?.let { "%.3f V".format(it) } ?: "—"} · Cycles ${state.battery.dischargeCount ?: "—"}")
            }
        }
    )
}

@Composable
private fun GsHealthRow(name: String, good: Boolean, detail: String? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text((if (good) "✓  " else "!  ") + name, color = if (good) GsColors.Green else GsColors.Amber)
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
