package io.xstarrevival.app.gs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.xstarrevival.core.groundstation.GeoPoint
import io.xstarrevival.app.TelemetrySource
import io.xstarrevival.core.command.CommandStatus
import io.xstarrevival.core.groundstation.MissionExecutionPhase
import io.xstarrevival.core.groundstation.MissionExecutionState
import io.xstarrevival.core.groundstation.MissionFinishBehavior
import io.xstarrevival.core.groundstation.MissionPlan
import io.xstarrevival.core.groundstation.MissionReviewAnalyzer
import io.xstarrevival.core.groundstation.MissionValidator
import io.xstarrevival.core.groundstation.MissionWaypoint
import io.xstarrevival.core.model.ConnectionState
import io.xstarrevival.core.model.XStarState
import io.xstarrevival.core.sim.SimulatorMissionModel
import java.util.UUID

@Composable
fun GsMissionV2Screen(
    state: XStarState,
    source: TelemetrySource,
    execution: MissionExecutionState,
    commandStatus: CommandStatus?,
    onStart: (MissionPlan) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onAbort: () -> Unit
) {
    val context = LocalContext.current
    val store = remember(context) { GsMissionStore(context.applicationContext) }
    var plans by remember { mutableStateOf(store.load()) }
    var active by remember { mutableStateOf(plans.firstOrNull()) }
    var mode by remember { mutableStateOf(GsMissionMode.WAYPOINTS) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("MISSION CONTROL", color = GsColors.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text("Offline planning · Configure → Review → Execute", color = GsColors.Muted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GsMissionMode.entries.forEach { option ->
                    if (mode == option) Button(onClick = { mode = option }) { Text(option.name) }
                    else OutlinedButton(onClick = { mode = option }) { Text(option.name) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        when (mode) {
            GsMissionMode.WAYPOINTS -> PersistentWaypointPlanner(
                state = state,
                plans = plans,
                active = active,
                onSelect = { active = it },
                onCreate = {
                    val plan = MissionPlan(UUID.randomUUID().toString(), "Untitled Mission", emptyList())
                    store.save(plan); plans = store.load(); active = plan
                },
                onSave = { plan -> store.save(plan); plans = store.load(); active = plan },
                onDelete = { plan -> store.delete(plan.id); plans = store.load(); active = plans.firstOrNull() },
                source = source,
                execution = execution,
                commandStatus = commandStatus,
                onStart = onStart,
                onPause = onPause,
                onResume = onResume,
                onAbort = onAbort,
                modifier = Modifier.weight(1f)
            )
            GsMissionMode.ORBIT -> OrbitEditor(state, Modifier.weight(1f))
            GsMissionMode.FOLLOW -> FollowEditor(state, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PersistentWaypointPlanner(
    state: XStarState,
    plans: List<MissionPlan>,
    active: MissionPlan?,
    onSelect: (MissionPlan) -> Unit,
    onCreate: () -> Unit,
    onSave: (MissionPlan) -> Unit,
    onDelete: (MissionPlan) -> Unit,
    source: TelemetrySource,
    execution: MissionExecutionState,
    commandStatus: CommandStatus?,
    onStart: (MissionPlan) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onAbort: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.width(250.dp).fillMaxHeight()) {
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("+ NEW MISSION") }
            Spacer(Modifier.height(10.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(plans, key = { it.id }) { plan ->
                    Card(
                        Modifier.fillMaxWidth().clickable { onSelect(plan) },
                        colors = CardDefaults.cardColors(containerColor = if (active?.id == plan.id) GsColors.Orange.copy(alpha=.16f) else GsColors.Panel)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(plan.name, color = GsColors.White, fontWeight = FontWeight.Bold)
                            Text("${plan.waypoints.size} waypoints · reserve ${plan.minimumBatteryReservePercent}%", color = GsColors.Muted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        if (active == null) {
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Text("Create a mission to begin planning", color = GsColors.Muted)
            }
        } else {
            MissionEditor(
                state, active, onSave, onDelete, source, execution, commandStatus,
                onStart, onPause, onResume, onAbort, Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun MissionEditor(
    state: XStarState,
    original: MissionPlan,
    onSave: (MissionPlan) -> Unit,
    onDelete: (MissionPlan) -> Unit,
    source: TelemetrySource,
    execution: MissionExecutionState,
    commandStatus: CommandStatus?,
    onStart: (MissionPlan) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onAbort: () -> Unit,
    modifier: Modifier = Modifier
) {
    var draft by remember(original.id, original.waypoints.size) { mutableStateOf(original) }
    var selectedWaypoint by remember(original.id) { mutableStateOf<Int?>(draft.waypoints.indices.firstOrNull()) }
    var reviewOpen by remember { mutableStateOf(false) }
    val validation = MissionValidator.validate(draft, state.takeIf { it.connection is ConnectionState.Connected })
    val review = MissionReviewAnalyzer.analyze(
        plan = draft,
        start = state.navigation.latitudeDeg?.let { latitude ->
            state.navigation.longitudeDeg?.let { longitude -> GeoPoint(latitude, longitude) }
        },
        currentBatteryPercent = state.battery.percent,
        supportedActions = SimulatorMissionModel.supportedWaypointActions,
        supportedFinishBehaviors = setOf(MissionFinishBehavior.HOVER, MissionFinishBehavior.LAND)
    )

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(Modifier.weight(.58f).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = GsColors.Panel), shape = RoundedCornerShape(16.dp)) {
            Box(Modifier.fillMaxSize()) {
                MissionPathCanvas(draft)
                Column(Modifier.align(Alignment.TopStart).padding(16.dp).width(250.dp)) {
                    TextField(
                        value = draft.name,
                        onValueChange = { draft = draft.copy(name = it) },
                        label = { Text("Mission name") },
                        singleLine = true
                    )
                }
                Row(Modifier.align(Alignment.BottomCenter).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val base = draft.waypoints.lastOrNull()?.position ?: GeoPoint(
                            state.navigation.latitudeDeg ?: 35.515,
                            state.navigation.longitudeDeg ?: -97.57
                        )
                        val n = draft.waypoints.size
                        val next = MissionWaypoint(
                            id = UUID.randomUUID().toString(),
                            position = GeoPoint(base.latitudeDeg + .0010, base.longitudeDeg + .0012),
                            altitudeM = 45.0,
                            speedMps = 5.0
                        )
                        draft = draft.copy(waypoints = draft.waypoints + next)
                        selectedWaypoint = n
                    }) { Text("+ WAYPOINT") }
                    OutlinedButton(onClick = { onSave(draft) }) { Text("SAVE") }
                    OutlinedButton(onClick = { onDelete(original) }) { Text("DELETE") }
                }
            }
        }
        Column(Modifier.weight(.42f).fillMaxHeight()) {
            Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = GsColors.Panel)) {
                LazyColumn(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        Text("WAYPOINTS", color = GsColors.Orange, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    items(draft.waypoints.indices.toList()) { index ->
                        val wp = draft.waypoints[index]
                        Card(
                            Modifier.fillMaxWidth().clickable { selectedWaypoint = index },
                            colors = CardDefaults.cardColors(containerColor = if (selectedWaypoint == index) GsColors.Orange.copy(alpha=.14f) else GsColors.Panel2)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("WP ${index + 1}", color = GsColors.White, fontWeight = FontWeight.Bold)
                                    Text("%.5f, %.5f".format(wp.position.latitudeDeg, wp.position.longitudeDeg), color = GsColors.Muted, fontSize = 9.sp)
                                }
                                Text("${wp.altitudeM.toInt()}m · ${wp.speedMps.toInt()}m/s", color = GsColors.Muted, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            selectedWaypoint?.takeIf { it in draft.waypoints.indices }?.let { index ->
                WaypointInspector(draft.waypoints[index]) { changed ->
                    draft = draft.copy(waypoints = draft.waypoints.toMutableList().also { it[index] = changed })
                }
            }
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = if (validation.canExecute) GsColors.Green.copy(alpha=.10f) else GsColors.Amber.copy(alpha=.10f))) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(if (validation.canExecute) "MISSION VALID" else "REVIEW REQUIRED", color = if (validation.canExecute) GsColors.Green else GsColors.Amber, fontWeight = FontWeight.Bold)
                    validation.issues.take(4).forEach { Text("• ${it.message}", color = GsColors.Muted, fontSize = 10.sp) }
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = { reviewOpen = true },
                        enabled = validation.canExecute && review.unsupportedActions.isEmpty() &&
                            review.unsupportedFinishBehavior == null && source == TelemetrySource.SIMULATOR &&
                            execution.phase !in setOf(MissionExecutionPhase.ACTIVE, MissionExecutionPhase.PAUSED),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("REVIEW & START") }
                    if (source != TelemetrySource.SIMULATOR) {
                        Text("Execution is enabled only for the isolated simulator. Planning and saving remain available offline.", color = GsColors.Muted, fontSize = 9.sp)
                    }
                    if (execution.phase != MissionExecutionPhase.IDLE) {
                        Spacer(Modifier.height(8.dp))
                        MissionExecutionPanel(execution, commandStatus, state, onPause, onResume, onAbort)
                    }
                }
            }
        }
    }

    if (reviewOpen) {
        AlertDialog(
            onDismissRequest = { reviewOpen = false },
            title = { Text("REVIEW MISSION") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GsSettingLine("Mission", draft.name)
                    GsSettingLine("Waypoints", draft.waypoints.size.toString())
                    GsSettingLine("Distance", "%.0f m".format(review.totalDistanceM))
                    GsSettingLine("Estimated duration", "%.0f s".format(review.estimatedDurationSeconds))
                    GsSettingLine("Maximum altitude", "${review.maximumAltitudeM.toInt()} m")
                    GsSettingLine("Estimated battery use", "${review.estimatedBatteryUsePercent}% (simulated)")
                    GsSettingLine("Projected battery", review.projectedBatteryPercent?.let { "$it%" } ?: "Unavailable")
                    GsSettingLine("Projected reserve margin", review.projectedReservePercent?.let { "$it%" } ?: "Unavailable")
                    GsSettingLine("Battery reserve", "${draft.minimumBatteryReservePercent}%")
                    val home = state.navigation.homeLatitudeDeg?.let { latitude ->
                        state.navigation.homeLongitudeDeg?.let { longitude -> "%.5f, %.5f".format(latitude, longitude) }
                    } ?: "Unavailable"
                    GsSettingLine("Home Point", home)
                    if (review.unsupportedActions.isNotEmpty()) {
                        Text("Unsupported actions: ${review.unsupportedActions.joinToString()}", color = GsColors.Red, fontSize = 10.sp)
                    }
                    review.unsupportedFinishBehavior?.let {
                        Text("Unsupported finish behavior: $it", color = GsColors.Red, fontSize = 10.sp)
                    }
                    validation.issues.forEach { Text("• ${it.message}", color = GsColors.Amber, fontSize = 10.sp) }
                }
            },
            dismissButton = { TextButton(onClick = { reviewOpen = false }) { Text("CANCEL") } },
            confirmButton = {
                Button(onClick = { onSave(draft); onStart(draft); reviewOpen = false }) { Text("START MISSION") }
            }
        )
    }
}

@Composable
private fun MissionExecutionPanel(
    execution: MissionExecutionState,
    commandStatus: CommandStatus?,
    state: XStarState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onAbort: () -> Unit
) {
    Text("EXECUTION · ${execution.phase}", color = GsColors.Orange, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    GsSettingLine("Current / next", "${execution.currentWaypoint ?: "—"} / ${execution.nextWaypoint ?: "—"}")
    GsSettingLine("Progress", "${(execution.progress * 100).toInt()}%")
    GsSettingLine("Remaining", execution.remainingDistanceM?.let { "%.0f m".format(it) } ?: "—")
    GsSettingLine("ETA", execution.etaSeconds?.let { "%.0f s".format(it) } ?: "—")
    GsSettingLine("Battery / reserve", "${state.battery.percent ?: "—"}% / ${execution.minimumBatteryReservePercent ?: "—"}%")
    GsSettingLine("Command", commandStatus?.phase?.name ?: "IDLE")
    execution.detail?.let { Text(it, color = GsColors.Muted, fontSize = 9.sp) }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(onClick = onPause, enabled = execution.phase == MissionExecutionPhase.ACTIVE) { Text("PAUSE") }
        Button(onClick = onResume, enabled = execution.phase == MissionExecutionPhase.PAUSED) { Text("RESUME") }
        OutlinedButton(
            onClick = onAbort,
            enabled = execution.phase in setOf(MissionExecutionPhase.ACTIVE, MissionExecutionPhase.PAUSED)
        ) { Text("ABORT") }
    }
}

@Composable
private fun WaypointInspector(wp: MissionWaypoint, onChange: (MissionWaypoint) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = GsColors.Panel), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text("WAYPOINT SETTINGS", color = GsColors.Orange, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("Altitude ${wp.altitudeM.toInt()} m", color = GsColors.White)
            Slider(value = wp.altitudeM.toFloat(), onValueChange = { onChange(wp.copy(altitudeM = it.toDouble())) }, valueRange = 2f..120f)
            Text("Speed ${"%.1f".format(wp.speedMps)} m/s", color = GsColors.White)
            Slider(value = wp.speedMps.toFloat(), onValueChange = { onChange(wp.copy(speedMps = it.toDouble())) }, valueRange = .5f..15f)
            Text("Gimbal ${wp.gimbalPitchDeg?.let { "%.0f°".format(it) } ?: "free"}", color = GsColors.Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun MissionPathCanvas(plan: MissionPlan) {
    Canvas(Modifier.fillMaxSize().padding(18.dp)) {
        val grid = Color.White.copy(alpha=.055f)
        repeat(10) { i -> drawLine(grid, Offset(size.width*i/9f,0f), Offset(size.width*i/9f,size.height),1f) }
        repeat(8) { i -> drawLine(grid, Offset(0f,size.height*i/7f), Offset(size.width,size.height*i/7f),1f) }
        if (plan.waypoints.isEmpty()) return@Canvas
        val lats = plan.waypoints.map { it.position.latitudeDeg }
        val lons = plan.waypoints.map { it.position.longitudeDeg }
        val minLat = lats.minOrNull()!!; val maxLat = lats.maxOrNull()!!
        val minLon = lons.minOrNull()!!; val maxLon = lons.maxOrNull()!!
        val latSpan = (maxLat-minLat).takeIf { it > 1e-7 } ?: 1e-7
        val lonSpan = (maxLon-minLon).takeIf { it > 1e-7 } ?: 1e-7
        val pts = plan.waypoints.map { wp ->
            val x = ((wp.position.longitudeDeg-minLon)/lonSpan).toFloat()
            val y = (1.0-(wp.position.latitudeDeg-minLat)/latSpan).toFloat()
            Offset(48f+x*(size.width-96f), 72f+y*(size.height-144f))
        }
        pts.zipWithNext().forEach { (a,b) -> drawLine(GsColors.Orange,a,b,4f) }
        pts.forEachIndexed { index,p ->
            drawCircle(GsColors.Orange,12f,p); drawCircle(Color.White,4f,p)
        }
    }
}

@Composable
private fun OrbitEditor(state: XStarState, modifier: Modifier = Modifier) {
    var radius by remember { mutableFloatStateOf(35f) }
    var altitude by remember { mutableFloatStateOf(45f) }
    var speed by remember { mutableFloatStateOf(4f) }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        GsSectionCard("ORBIT", Modifier.width(360.dp).fillMaxHeight()) {
            GsSettingLine("Point of interest", "Select on map")
            MissionSlider("Radius", radius, 10f..200f, "m") { radius = it }
            MissionSlider("Altitude", altitude, 5f..120f, "m") { altitude = it }
            MissionSlider("Speed", speed, 1f..10f, "m/s") { speed = it }
            GsSettingLine("Direction", "Clockwise")
            GsSettingLine("Camera", "Face POI")
            GsSettingLine("Completion", "Hover")
            Button(onClick = { }, enabled = state.connection is ConnectionState.Connected, modifier = Modifier.fillMaxWidth()) { Text("REVIEW ORBIT") }
        }
        Card(Modifier.weight(1f).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = GsColors.Panel)) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width/2,size.height/2); val r = size.minDimension*.30f
                drawCircle(GsColors.Orange.copy(alpha=.12f),r,c); drawCircle(GsColors.Orange,r,c,style=androidx.compose.ui.graphics.drawscope.Stroke(4f)); drawCircle(Color.White,8f,c); drawCircle(GsColors.Orange,12f,Offset(c.x+r,c.y))
            }
        }
    }
}

@Composable
private fun FollowEditor(state: XStarState, modifier: Modifier = Modifier) {
    var distance by remember { mutableFloatStateOf(20f) }
    var altitude by remember { mutableFloatStateOf(30f) }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        GsSectionCard("FOLLOW", Modifier.width(360.dp).fillMaxHeight()) {
            GsSettingLine("Target", "Operator device GPS")
            MissionSlider("Distance",distance,5f..100f,"m") { distance=it }
            MissionSlider("Altitude",altitude,5f..120f,"m") { altitude=it }
            GsSettingLine("Relative position", "Behind")
            GsSettingLine("Camera", "Face target")
            GsSettingLine("Target loss", "Hover")
            Button(onClick = { }, enabled = state.connection is ConnectionState.Connected, modifier = Modifier.fillMaxWidth()) { Text("REVIEW FOLLOW") }
        }
        Card(Modifier.weight(1f).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = GsColors.Panel)) {
            Box(Modifier.fillMaxSize()) { Text("FOLLOW GEOMETRY PREVIEW", Modifier.align(Alignment.Center), color = GsColors.Muted) }
        }
    }
}

@Composable
private fun MissionSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String, onChange: (Float)->Unit) {
    Column { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label,color=GsColors.Muted); Text("%.1f %s".format(value,unit),color=GsColors.White) }; Slider(value,onChange,valueRange=range) }
}
