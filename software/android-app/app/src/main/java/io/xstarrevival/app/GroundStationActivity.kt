package io.xstarrevival.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.xstarrevival.core.model.ConnectionState
import io.xstarrevival.core.model.Severity
import io.xstarrevival.core.model.XStarState
import kotlin.math.roundToInt

class GroundStationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            XStarGroundTheme {
                val vm: XStarViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                val source by vm.source.collectAsStateWithLifecycle()
                val heartbeat by vm.heartbeat.collectAsStateWithLifecycle()
                GroundStationApp(
                    state = state,
                    source = source,
                    heartbeat = heartbeat,
                    availableSources = vm.availableSources,
                    onSource = vm::selectSource,
                    onConnect = vm::connect,
                    onDisconnect = vm::disconnect,
                    onRefresh = vm::refresh,
                    onArm = vm::toggleSimulatorArm,
                    onTakeOff = vm::simulatorTakeOff,
                    onLand = vm::simulatorLand,
                    onRecord = vm::toggleSimulatorRecording
                )
            }
        }
    }
}

private val XOrange = Color(0xFFFF6A00)
private val XGreen = Color(0xFF46E08B)
private val XAmber = Color(0xFFFFC857)
private val XRed = Color(0xFFFF5B5B)
private val XInk = Color(0xFF080A0C)
private val XPanel = Color(0xFF11151A)
private val XPanel2 = Color(0xFF171C22)
private val XMuted = Color(0xFF98A2AD)

@Composable
private fun XStarGroundTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = { Surface(color = XInk, content = content) })
}

private enum class GroundPage(val label: String, val glyph: String) {
    GARAGE("Garage", "◆"),
    COCKPIT("Fly", "✦"),
    MISSIONS("Missions", "⌖"),
    RECORDS("Flights", "≋"),
    MEDIA("Media", "▣"),
    AIRCRAFT("Aircraft", "◇"),
    SETTINGS("Settings", "⚙")
}

@Composable
private fun GroundStationApp(
    state: XStarState,
    source: TelemetrySource,
    heartbeat: HeartbeatUiState,
    availableSources: List<TelemetrySource>,
    onSource: (TelemetrySource) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onArm: () -> Unit,
    onTakeOff: () -> Unit,
    onLand: () -> Unit,
    onRecord: () -> Unit
) {
    var page by rememberSaveable { mutableStateOf(GroundPage.GARAGE) }
    Row(Modifier.fillMaxSize().background(XInk)) {
        NavigationRail(page = page, onPage = { page = it })
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (page) {
                GroundPage.GARAGE -> GarageScreen(state, source, availableSources, onSource, onConnect, onDisconnect, onRefresh) { page = GroundPage.COCKPIT }
                GroundPage.COCKPIT -> ModernCockpit(state, source, heartbeat, onArm, onTakeOff, onLand, onRecord)
                GroundPage.MISSIONS -> MissionScreen(state)
                GroundPage.RECORDS -> FlightRecordsScreen()
                GroundPage.MEDIA -> MediaScreen(state)
                GroundPage.AIRCRAFT -> AircraftScreen(state)
                GroundPage.SETTINGS -> SettingsScreen()
            }
        }
    }
}

@Composable
private fun NavigationRail(page: GroundPage, onPage: (GroundPage) -> Unit) {
    Column(
        Modifier.width(92.dp).fillMaxHeight().background(Color(0xFF0D1014)).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("X★", color = XOrange, fontWeight = FontWeight.Black, fontSize = 22.sp)
        Spacer(Modifier.height(8.dp))
        GroundPage.entries.forEach { item ->
            val selected = item == page
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp).clickable { onPage(item) }
                    .background(if (selected) XOrange.copy(alpha = .14f) else Color.Transparent, RoundedCornerShape(12.dp))
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(item.glyph, color = if (selected) XOrange else XMuted, fontSize = 18.sp)
                Text(item.label, color = if (selected) Color.White else XMuted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun GarageScreen(
    state: XStarState,
    source: TelemetrySource,
    availableSources: List<TelemetrySource>,
    onSource: (TelemetrySource) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onEnterFlight: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("X-STAR", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Text("Open Ground Station", color = XMuted)
                }
                ConnectionPill(state.connection)
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = XPanel), shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    DroneSilhouette(Modifier.size(230.dp, 140.dp))
                    Spacer(Modifier.width(24.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(state.aircraft.productName ?: "X-Star Premium", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("Firmware ${state.aircraft.firmwareVersion ?: "—"}", color = XMuted)
                        Text(if (state.connection is ConnectionState.Connected) "CONNECTED" else "NOT CONNECTED", color = if (state.connection is ConnectionState.Connected) XGreen else XAmber, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Button(onClick = onEnterFlight) { Text("ENTER FLIGHT") }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard("FLIGHT BATTERY", state.battery.percent?.let { "$it%" } ?: "—", state.battery.packVoltageV?.let { "%.2f V".format(it) } ?: "No telemetry", Modifier.weight(1f))
                SummaryCard("CONTROLLER", state.remote.batteryPercent?.let { "$it%" } ?: "—", state.remote.signalPercent?.let { "Signal $it%" } ?: "No telemetry", Modifier.weight(1f))
                SummaryCard("GPS", state.navigation.satellites?.toString() ?: "—", state.navigation.gpsFix ?: "No fix", Modifier.weight(1f))
                SummaryCard("STORAGE", "—", if (state.camera.connected == true) "Camera online" else "Camera status unknown", Modifier.weight(1f))
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = XPanel2), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("DATA SOURCE", color = Color.White, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableSources.forEach { option ->
                            if (source == option) Button(onClick = { onSource(option) }) { Text(option.label) }
                            else OutlinedButton(onClick = { onSource(option) }) { Text(option.label) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onConnect) { Text("Connect") }
                        OutlinedButton(onClick = onRefresh) { Text("Refresh") }
                        OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernCockpit(
    state: XStarState,
    source: TelemetrySource,
    heartbeat: HeartbeatUiState,
    onArm: () -> Unit,
    onTakeOff: () -> Unit,
    onLand: () -> Unit,
    onRecord: () -> Unit
) {
    var showHealth by remember { mutableStateOf(false) }
    var showRth by remember { mutableStateOf(false) }
    var showSmart by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        ArtificialViewport(state, Modifier.fillMaxSize())
        TopTelemetryBar(state, source, heartbeat, Modifier.align(Alignment.TopCenter))
        FlightRail(
            onHealth = { showHealth = true },
            onRth = { showRth = true },
            onSmart = { showSmart = true },
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp)
        )
        CameraRail(state, onRecord, Modifier.align(Alignment.CenterEnd).padding(end = 14.dp))
        MiniMap(state, Modifier.align(Alignment.BottomStart).padding(18.dp))
        BottomFlightStatus(state, Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp))
        if (source == TelemetrySource.SIMULATOR) {
            Row(Modifier.align(Alignment.BottomEnd).padding(18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onArm) { Text(if (state.aircraft.armed == true) "Disarm" else "Arm") }
                Button(onClick = onTakeOff) { Text("Takeoff") }
                OutlinedButton(onClick = onLand) { Text("Land") }
            }
        }
    }
    if (showHealth) HealthDialog(state) { showHealth = false }
    if (showRth) ConfirmActionDialog("RETURN TO HOME", "Aircraft will climb to the configured RTH altitude and return to the current home point.", "START RTH", onDismiss = { showRth = false }) { showRth = false }
    if (showSmart) SmartFlightDialog(onDismiss = { showSmart = false })
}

@Composable
private fun TopTelemetryBar(state: XStarState, source: TelemetrySource, heartbeat: HeartbeatUiState, modifier: Modifier = Modifier) {
    Row(
        modifier.background(Color.Black.copy(alpha = .72f)).fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TelemetryItem("MODE", state.aircraft.flightMode ?: if (state.connection is ConnectionState.Connected) "GPS" else "OFFLINE")
        TelemetryItem("H", state.navigation.altitudeM?.let { "%.1fm".format(it) } ?: "—")
        TelemetryItem("V/S", state.navigation.verticalSpeedMps?.let { "%.1f".format(it) } ?: "—")
        TelemetryItem("SPD", state.navigation.groundSpeedMps?.let { "%.1f".format(it) } ?: "—")
        TelemetryItem("SAT", state.navigation.satellites?.toString() ?: "—")
        TelemetryItem("RC", state.remote.signalPercent?.let { "$it%" } ?: "—")
        TelemetryItem("HD", state.remote.imageSignalPercent?.let { "$it%" } ?: "—")
        TelemetryItem("BAT", state.battery.percent?.let { "$it%" } ?: "—", batteryColor(state.battery.percent))
        TelemetryItem("SRC", source.name.take(4))
        if (heartbeat.stale) Text("STALE", color = XRed, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TelemetryItem(label: String, value: String, color: Color = Color.White) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = XMuted, fontSize = 9.sp)
        Text(value, color = color, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FlightRail(onHealth: () -> Unit, onRth: () -> Unit, onSmart: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RailButton("✦", "SMART", onSmart)
        RailButton("✓", "HEALTH", onHealth)
        RailButton("⌂", "RTH", onRth)
        RailButton("⌖", "MAP") { }
        RailButton("H", "HOME") { }
    }
}

@Composable
private fun CameraRail(state: XStarState, onRecord: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        RailButton(if (state.camera.recording == true) "■" else "●", if (state.camera.recording == true) "STOP" else "REC", onRecord, if (state.camera.recording == true) XRed else XOrange)
        RailButton("◉", "PHOTO") { }
        RailButton("EV", state.camera.exposureMode ?: "AUTO") { }
        RailButton("↕", state.gimbal.pitchDeg?.let { "${it.roundToInt()}°" } ?: "GIMBAL") { }
        RailButton("⚙", "CAM") { }
    }
}

@Composable
private fun RailButton(glyph: String, label: String, onClick: () -> Unit, accent: Color = Color.White) {
    Column(
        Modifier.width(64.dp).background(Color.Black.copy(alpha = .62f), RoundedCornerShape(12.dp)).border(1.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(glyph, color = accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = XMuted, fontSize = 8.sp)
    }
}

@Composable
private fun ArtificialViewport(state: XStarState, modifier: Modifier = Modifier) {
    Canvas(modifier.background(Color(0xFF10161B))) {
        drawRect(Color(0xFF244C67), size = androidx.compose.ui.geometry.Size(size.width, size.height * .52f))
        drawRect(Color(0xFF283328), topLeft = Offset(0f, size.height * .52f), size = androidx.compose.ui.geometry.Size(size.width, size.height * .48f))
        val center = Offset(size.width / 2f, size.height / 2f)
        val line = Color.White.copy(alpha = .7f)
        drawLine(line, Offset(center.x - 65f, center.y), Offset(center.x - 15f, center.y), 3f)
        drawLine(line, Offset(center.x + 15f, center.y), Offset(center.x + 65f, center.y), 3f)
        drawCircle(line, 5f, center, style = Stroke(2f))
        val yaw = state.attitude.yawDeg ?: 0.0
        drawCircle(XOrange.copy(alpha = .7f), 32f, Offset(center.x, 84f), style = Stroke(3f))
        drawLine(XOrange, Offset(center.x, 84f), Offset(center.x + (yaw % 30).toFloat(), 54f), 3f)
    }
}

@Composable
private fun MiniMap(state: XStarState, modifier: Modifier = Modifier) {
    Box(modifier.size(190.dp, 120.dp).background(Color(0xCC10151B), RoundedCornerShape(14.dp)).border(1.dp, Color.White.copy(alpha = .16f), RoundedCornerShape(14.dp))) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val grid = Color.White.copy(alpha = .08f)
            repeat(5) { i -> drawLine(grid, Offset(size.width * i / 4f, 0f), Offset(size.width * i / 4f, size.height), 1f) }
            repeat(4) { i -> drawLine(grid, Offset(0f, size.height * i / 3f), Offset(size.width, size.height * i / 3f), 1f) }
            drawCircle(XOrange, 7f, Offset(size.width * .62f, size.height * .42f))
            drawCircle(XGreen, 6f, Offset(size.width * .28f, size.height * .70f), style = Stroke(2f))
        }
        Text("MAP", Modifier.align(Alignment.TopStart).padding(8.dp), color = XMuted, fontSize = 9.sp)
        Text(state.navigation.latitudeDeg?.let { "%.5f".format(it) } ?: "OFFLINE", Modifier.align(Alignment.BottomEnd).padding(8.dp), color = XMuted, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun BottomFlightStatus(state: XStarState, modifier: Modifier = Modifier) {
    val connected = state.connection is ConnectionState.Connected
    val critical = state.warnings.any { it.severity == Severity.CRITICAL }
    val ready = connected && !critical && (state.navigation.satellites ?: 0) >= 6
    val text = when {
        critical -> "CRITICAL — ACTION REQUIRED"
        !connected -> "DISCONNECTED"
        ready -> "READY TO FLY — GPS"
        else -> "CHECKING AIRCRAFT"
    }
    val color = when {
        critical -> XRed
        ready -> XGreen
        connected -> XAmber
        else -> XMuted
    }
    Row(modifier.background(Color.Black.copy(alpha = .72f), RoundedCornerShape(999.dp)).border(1.dp, color.copy(alpha = .5f), RoundedCornerShape(999.dp)).padding(horizontal = 18.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(999.dp)))
        Spacer(Modifier.width(8.dp))
        Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun HealthDialog(state: XStarState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onDismiss) { Text("DONE") } },
        title = { Text("AIRCRAFT HEALTH") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HealthRow("Flight controller", state.connection is ConnectionState.Connected)
                HealthRow("GPS / GLONASS", (state.navigation.satellites ?: 0) >= 6, "${state.navigation.satellites ?: 0} satellites")
                HealthRow("Remote controller", state.remote.connected == true || state.connection is ConnectionState.Connected, state.remote.signalPercent?.let { "$it% signal" })
                HealthRow("HD video", state.camera.video.receiving || state.remote.imageSignalPercent != null, state.remote.imageSignalPercent?.let { "$it% signal" })
                HealthRow("Camera", state.camera.connected == true, state.camera.mode)
                HorizontalDivider()
                Text("BATTERY", fontWeight = FontWeight.Bold)
                Text("${state.battery.packVoltageV?.let { "%.2f V".format(it) } ?: "—"}   ${state.battery.percent?.let { "$it%" } ?: "—"}   ${state.battery.temperatureC?.let { "%.1f°C".format(it) } ?: "—"}")
                Text("Cell delta ${state.battery.cellDeltaV?.let { "%.3f V".format(it) } ?: "—"} · Cycles ${state.battery.dischargeCount ?: "—"}")
            }
        }
    )
}

@Composable
private fun HealthRow(name: String, good: Boolean, detail: String? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text((if (good) "✓  " else "!  ") + name, color = if (good) XGreen else XAmber)
        if (detail != null) Text(detail, color = XMuted, fontSize = 12.sp)
    }
}

@Composable
private fun ConfirmActionDialog(title: String, body: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(body) }, dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }, confirmButton = { Button(onClick = onConfirm) { Text(confirm) } })
}

@Composable
private fun SmartFlightDialog(onDismiss: () -> Unit) {
    val modes = listOf("Manual", "Orbit", "Follow", "Waypoints", "Course Lock", "Home Lock")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SMART FLIGHT") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { modes.forEach { mode -> Card(Modifier.fillMaxWidth().clickable { }, colors = CardDefaults.cardColors(containerColor = XPanel2)) { Text(mode, Modifier.padding(14.dp), color = Color.White) } } } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CLOSE") } }
    )
}

private data class MissionDraft(val name: String, val distanceKm: Double, val minutes: Int, val batteryPercent: Int, val waypoints: Int)

@Composable
private fun MissionScreen(state: XStarState) {
    var selected by remember { mutableStateOf<MissionDraft?>(null) }
    val missions = remember { listOf(
        MissionDraft("West Property Survey", 1.24, 9, 41, 12),
        MissionDraft("Lake Perimeter", 2.80, 17, 67, 15),
        MissionDraft("Roof Inspection", .46, 6, 28, 8)
    ) }
    Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(.38f)) {
            Text("MISSIONS", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text("Plan offline. Review before execution.", color = XMuted)
            Spacer(Modifier.height(16.dp))
            missions.forEach { mission ->
                Card(Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { selected = mission }, colors = CardDefaults.cardColors(containerColor = if (selected == mission) XOrange.copy(alpha = .14f) else XPanel)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(mission.name, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("${mission.waypoints} WP · ${mission.distanceKm} km · ${mission.minutes} min", color = XMuted, fontSize = 12.sp)
                    }
                }
            }
            Button(onClick = { selected = MissionDraft("Untitled Mission", 0.0, 0, 0, 0) }) { Text("+ NEW MISSION") }
        }
        Card(Modifier.weight(.62f).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = XPanel), shape = RoundedCornerShape(18.dp)) {
            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize().padding(18.dp)) {
                    val grid = Color.White.copy(alpha = .06f)
                    repeat(10) { i -> drawLine(grid, Offset(size.width * i / 9f, 0f), Offset(size.width * i / 9f, size.height), 1f) }
                    repeat(8) { i -> drawLine(grid, Offset(0f, size.height * i / 7f), Offset(size.width, size.height * i / 7f), 1f) }
                    val pts = listOf(Offset(size.width*.18f,size.height*.72f),Offset(size.width*.36f,size.height*.32f),Offset(size.width*.68f,size.height*.28f),Offset(size.width*.78f,size.height*.65f),Offset(size.width*.46f,size.height*.78f))
                    pts.zipWithNext().forEach { (a,b) -> drawLine(XOrange, a, b, 4f) }
                    pts.forEachIndexed { i,p -> drawCircle(XOrange, 11f, p); drawCircle(Color.White, 4f, p) }
                }
                selected?.let { mission ->
                    Card(Modifier.align(Alignment.BottomCenter).padding(18.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha=.82f))) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column { Text(mission.name, color = Color.White, fontWeight = FontWeight.Bold); Text("${mission.distanceKm} km · ${mission.minutes} min · est ${mission.batteryPercent}% battery", color = XMuted) }
                            Button(onClick = { }) { Text(if (state.connection is ConnectionState.Connected) "REVIEW & START" else "SAVE OFFLINE") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlightRecordsScreen() {
    val records = listOf("Today · 12:41 PM" to "14:22 · 2.1 km · 84 m", "Aug 30 · 6:18 PM" to "08:47 · 1.0 km · 52 m", "Aug 29 · 10:03 AM" to "21:11 · 3.8 km · 101 m")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("FLIGHT RECORDS", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black) }
        items(records) { (title, detail) ->
            Card(colors = CardDefaults.cardColors(containerColor = XPanel)) { Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text(title, color = Color.White, fontWeight = FontWeight.Bold); Text(detail, color = XMuted) }; Text("›", color = XOrange, fontSize = 28.sp) } }
        }
    }
}

@Composable
private fun MediaScreen(state: XStarState) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("MEDIA", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black); Text(if (state.camera.connected == true) "Aircraft camera connected" else "Connect aircraft to browse onboard media", color = XMuted) }
        items((1..8).toList().chunked(4)) { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { row.forEach { i -> Box(Modifier.weight(1f).aspectRatio(16f/9f).background(XPanel2, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Text("MEDIA $i", color = XMuted) } } } }
    }
}

@Composable
private fun AircraftScreen(state: XStarState) {
    val cells = if (state.battery.cells.isNotEmpty()) state.battery.cells else (1..4).map { io.xstarrevival.core.model.CellState(it, null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("AIRCRAFT", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black); Text(state.aircraft.productName ?: "X-Star Premium", color = XMuted) }
        item { SectionCard("FLIGHT BATTERY") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { BigMetric(state.battery.percent?.let { "$it%" } ?: "—", "Charge"); BigMetric(state.battery.packVoltageV?.let { "%.2fV".format(it) } ?: "—", "Voltage"); BigMetric(state.battery.temperatureC?.let { "%.1f°C".format(it) } ?: "—", "Temperature"); BigMetric(state.battery.dischargeCount?.toString() ?: "—", "Cycles") }
            Spacer(Modifier.height(14.dp))
            cells.forEach { cell -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Cell ${cell.index}", color = XMuted); Text(cell.voltageV?.let { "%.3f V".format(it) } ?: "—", color = Color.White, fontFamily = FontFamily.Monospace) } }
            Text("Cell delta ${state.battery.cellDeltaV?.let { "%.3f V".format(it) } ?: "—"}", color = XMuted)
        } }
        item { SectionCard("REMOTE CONTROLLER") { SettingLine("Connection", state.remote.connected?.let { if (it) "Connected" else "Disconnected" } ?: "—"); SettingLine("Battery", state.remote.batteryPercent?.let { "$it%" } ?: "—"); SettingLine("Signal", state.remote.signalPercent?.let { "$it%" } ?: "—") } }
        item { SectionCard("VIDEO LINK") { SettingLine("RF frequency", state.imageLink.rfFrequencyHz?.let { "%.3f MHz".format(it / 1e6) } ?: "—"); SettingLine("RF signal", state.imageLink.rfSignalValue?.toString() ?: "—"); SettingLine("Video", if (state.camera.video.receiving) "Receiving ${state.camera.video.codec ?: ""}" else "—") } }
        item { SectionCard("GIMBAL") { SettingLine("Pitch", state.gimbal.pitchDeg?.let { "%.1f°".format(it) } ?: "—"); SettingLine("Status", state.gimbal.status ?: "—") } }
        item { SectionCard("SYSTEM") { SettingLine("Firmware", state.aircraft.firmwareVersion ?: "—"); SettingLine("Flight mode", state.aircraft.flightMode ?: "—"); SettingLine("Protocol source", state.diagnostics.source ?: "—") } }
    }
}

@Composable
private fun SettingsScreen() {
    val sections = listOf("Flight Control" to "Limits, RTH altitude, Beginner Mode, ATTI, IOC", "Remote Controller" to "Stick mode, calibration, sensitivity, mappings", "Video Link" to "Auto/manual channel, signal analyzer", "Aircraft Battery" to "Warnings, thresholds, health and cells", "Gimbal" to "Pitch speed, smoothing, calibration", "General" to "Units, aircraft identity, logs, firmware", "App" to "Map, alerts, accessibility, developer mode")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("SETTINGS", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black) }
        items(sections) { (name, detail) -> Card(Modifier.fillMaxWidth().clickable { }, colors = CardDefaults.cardColors(containerColor = XPanel)) { Row(Modifier.padding(18.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(name, color = Color.White, fontWeight = FontWeight.Bold); Text(detail, color = XMuted, fontSize = 12.sp) }; Text("›", color = XOrange, fontSize = 28.sp) } } }
    }
}

@Composable
private fun ConnectionPill(connection: ConnectionState) {
    val (text, color) = when (connection) {
        ConnectionState.Disconnected -> "DISCONNECTED" to XMuted
        ConnectionState.Discovering -> "SCANNING" to XAmber
        is ConnectionState.Connecting -> "CONNECTING" to XAmber
        is ConnectionState.Connected -> "CONNECTED" to XGreen
        is ConnectionState.Failed -> "FAILED" to XRed
    }
    Text(text, Modifier.background(color.copy(alpha=.14f), RoundedCornerShape(999.dp)).border(1.dp, color.copy(alpha=.55f), RoundedCornerShape(999.dp)).padding(horizontal=14.dp, vertical=8.dp), color=color, fontWeight=FontWeight.Bold, fontSize=11.sp)
}

@Composable
private fun SummaryCard(title: String, value: String, detail: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = XPanel), shape = RoundedCornerShape(14.dp)) { Column(Modifier.padding(16.dp)) { Text(title, color=XMuted, fontSize=10.sp); Text(value, color=Color.White, fontSize=24.sp, fontWeight=FontWeight.Bold); Text(detail, color=XMuted, fontSize=11.sp) } }
}

@Composable
private fun DroneSilhouette(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width/2f, size.height/2f)
        val ink = XOrange
        drawCircle(ink.copy(alpha=.16f), size.minDimension*.42f, c)
        drawRoundRect(ink, topLeft=Offset(c.x-42f,c.y-18f), size=androidx.compose.ui.geometry.Size(84f,36f), cornerRadius=androidx.compose.ui.geometry.CornerRadius(15f))
        val arms = listOf(Offset(-72f,-38f),Offset(72f,-38f),Offset(-72f,38f),Offset(72f,38f))
        arms.forEach { d -> drawLine(ink, c, c+d, 8f); drawCircle(ink, 22f, c+d, style=Stroke(5f)) }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(colors=CardDefaults.cardColors(containerColor=XPanel), shape=RoundedCornerShape(16.dp)) { Column(Modifier.fillMaxWidth().padding(18.dp)) { Text(title, color=XOrange, fontWeight=FontWeight.Bold, fontSize=12.sp); Spacer(Modifier.height(12.dp)); content() } }
}

@Composable
private fun SettingLine(label: String, value: String) { Row(Modifier.fillMaxWidth().padding(vertical=6.dp), horizontalArrangement=Arrangement.SpaceBetween) { Text(label, color=XMuted); Text(value, color=Color.White, fontFamily=FontFamily.Monospace) } }

@Composable
private fun BigMetric(value: String, label: String) { Column { Text(value, color=Color.White, fontSize=22.sp, fontWeight=FontWeight.Bold, fontFamily=FontFamily.Monospace); Text(label, color=XMuted, fontSize=10.sp) } }

private fun batteryColor(percent: Int?): Color = when {
    percent == null -> XMuted
    percent <= 10 -> XRed
    percent <= 25 -> XAmber
    else -> XGreen
}
