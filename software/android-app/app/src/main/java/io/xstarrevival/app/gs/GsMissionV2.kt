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
import io.xstarrevival.core.groundstation.MissionLostLinkBehavior
import io.xstarrevival.core.groundstation.MissionPlan
import io.xstarrevival.core.groundstation.MissionReviewAnalyzer
import io.xstarrevival.core.groundstation.MissionValidator
import io.xstarrevival.core.groundstation.MissionWaypoint
import io.xstarrevival.core.groundstation.SmartFlightExecutionState
import io.xstarrevival.core.groundstation.SmartFlightMode
import io.xstarrevival.core.groundstation.SmartFlightPhase
import io.xstarrevival.core.model.ConnectionState
import io.xstarrevival.core.model.XStarState
import io.xstarrevival.core.sim.SimulatorMissionModel
import java.util.UUID

@Composable
fun GsMissionV2Screen(
    state: XStarState,
    source: TelemetrySource,
    execution: MissionExecutionState,
    smartFlight: SmartFlightExecutionState,
    commandStatus: CommandStatus?,
    onStart: (MissionPlan) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onAbort: () -> Unit,
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
    val context = LocalContext.current
    val store = remember(context) { GsMissionStore(context.applicationContext) }
    var plans by remember { mutableStateOf(store.load()) }
    var active by remember { mutableStateOf(plans.firstOrNull()) }
    var mode by remember { mutableStateOf(GsMissionV2Mode.WAYPOINTS) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("MISSION CONTROL", color = GsColors.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text("Offline planning · Configure → Review → Execute", color = GsColors.Muted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GsMissionV2Mode.entries.forEach { option ->
                    if (mode == option) Button(onClick = { mode = option }) { Text(option.name) }
                    else OutlinedButton(onClick = { mode = option }) { Text(option.name) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        when (mode) {
            GsMissionV2Mode.WAYPOINTS -> PersistentWaypointPlanner(
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
                onCancelRth = onCancelRth,
                modifier = Modifier.weight(1f)
            )
            GsMissionV2Mode.ORBIT -> OrbitEditor(
                state, source, smartFlight, onStartOrbit, onStopOrbit, Modifier.weight(1f)
            )
            GsMissionV2Mode.FOLLOW -> FollowEditor(
                state, source, smartFlight, onStartFollow, onStopFollow, Modifier.weight(1f)
            )
            GsMissionV2Mode.COURSE_LOCK -> CourseLockEditor(
                state, source, smartFlight, onStartCourseLock, onStopCourseLock, Modifier.weight(1f)
            )
            GsMissionV2Mode.HOME_LOCK -> HomeLockEditor(
                state, source, smartFlight, onStartHomeLock, onStopHomeLock, Modifier.weight(1f)
            )
        }
    }
}

private enum class GsMissionV2Mode { WAYPOINTS, ORBIT, FOLLOW, COURSE_LOCK, HOME_LOCK }

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
    onCancelRth: () -> Unit,
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
                onStart, onPause, onResume, onAbort, onCancelRth, Modifier.weight(1f).fillMaxHeight()
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
    onCancelRth: () -> Unit,
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
        home = state.navigation.homeLatitudeDeg?.let { latitude ->
            state.navigation.homeLongitudeDeg?.let { longitude -> GeoPoint(latitude, longitude) }
        },
        currentBatteryPercent = state.battery.percent,
        supportedActions = SimulatorMissionModel.supportedWaypointActions,
        supportedFinishBehaviors = MissionFinishBehavior.entries.toSet()
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
            Card(colors = CardDefaults.cardColors(containerColor = GsColors.Panel)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("FINISH BEHAVIOR", color = GsColors.Orange, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MissionFinishBehavior.entries.forEach { behavior ->
                            if (draft.finishBehavior == behavior) {
                                Button(onClick = { draft = draft.copy(finishBehavior = behavior) }) {
                                    Text(behavior.name.replace('_', ' '))
                                }
                            } else {
                                OutlinedButton(onClick = { draft = draft.copy(finishBehavior = behavior) }) {
                                    Text(behavior.name.replace('_', ' '))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("LOST-LINK FAILSAFE", color = GsColors.Orange, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MissionLostLinkBehavior.entries.forEach { behavior ->
                            if (draft.lostLinkBehavior == behavior) {
                                Button(onClick = { draft = draft.copy(lostLinkBehavior = behavior) }) {
                                    Text(behavior.name.replace('_', ' '))
                                }
                            } else {
                                OutlinedButton(onClick = { draft = draft.copy(lostLinkBehavior = behavior) }) {
                                    Text(behavior.name.replace('_', ' '))
                                }
                            }
                        }
                    }
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
                        MissionExecutionPanel(execution, commandStatus, state, onPause, onResume, onAbort, onCancelRth)
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
                    GsSettingLine("Finish behavior", draft.finishBehavior.name.replace('_', ' '))
                    GsSettingLine("Lost-link failsafe", draft.lostLinkBehavior.name.replace('_', ' '))
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
    onAbort: () -> Unit,
    onCancelRth: () -> Unit
) {
    Text("EXECUTION · ${execution.phase}", color = GsColors.Orange, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    GsSettingLine("Current / next", "${execution.currentWaypoint ?: "—"} / ${execution.nextWaypoint ?: "—"}")
    GsSettingLine("Progress", "${(execution.progress * 100).toInt()}%")
    GsSettingLine(
        if (execution.returningHome) "Home distance" else "Remaining",
        execution.remainingDistanceM?.let { "%.0f m".format(it) } ?: "—"
    )
    GsSettingLine("ETA", execution.etaSeconds?.let { "%.0f s".format(it) } ?: "—")
    GsSettingLine("Battery / reserve", "${state.battery.percent ?: "—"}% / ${execution.minimumBatteryReservePercent ?: "—"}%")
    GsSettingLine("Command", commandStatus?.phase?.name ?: "IDLE")
    execution.detail?.let { Text(it, color = GsColors.Muted, fontSize = 9.sp) }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(
            onClick = onPause,
            enabled = execution.phase == MissionExecutionPhase.ACTIVE && !execution.finishInProgress
        ) { Text("PAUSE") }
        Button(onClick = onResume, enabled = execution.phase == MissionExecutionPhase.PAUSED) { Text("RESUME") }
        OutlinedButton(
            onClick = if (execution.returningHome) onCancelRth else onAbort,
            enabled = execution.phase in setOf(MissionExecutionPhase.ACTIVE, MissionExecutionPhase.PAUSED)
        ) { Text(if (execution.returningHome) "CANCEL RTH" else "ABORT") }
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
private fun OrbitEditor(
    state: XStarState,
    source: TelemetrySource,
    execution: SmartFlightExecutionState,
    onStart: (GeoPoint, Double, Double, Double, Boolean, Int) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    var radius by remember { mutableFloatStateOf(35f) }
    var altitude by remember { mutableFloatStateOf(45f) }
    var speed by remember { mutableFloatStateOf(4f) }
    var laps by remember { mutableFloatStateOf(1f) }
    var clockwise by remember { mutableStateOf(true) }
    var reviewOpen by remember { mutableStateOf(false) }
    val point = state.navigation.latitudeDeg?.let { latitude ->
        state.navigation.longitudeDeg?.let { longitude -> GeoPoint(latitude, longitude) }
    }
    val active = execution.mode == SmartFlightMode.ORBIT && execution.phase == SmartFlightPhase.ACTIVE
    val canStart = source == TelemetrySource.SIMULATOR && state.aircraft.armed == true &&
        (state.navigation.altitudeM ?: 0.0) > .2 && point != null && execution.phase != SmartFlightPhase.ACTIVE
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        GsSectionCard("ORBIT", Modifier.width(360.dp).fillMaxHeight()) {
            GsSettingLine("Point of interest", "Select on map")
            MissionSlider("Radius", radius, 10f..200f, "m") { radius = it }
            MissionSlider("Altitude", altitude, 5f..120f, "m") { altitude = it }
            MissionSlider("Speed", speed, 1f..10f, "m/s") { speed = it }
            MissionSlider("Laps", laps, 1f..10f, "") { laps = it }
            OutlinedButton(onClick = { clockwise = !clockwise }) { Text(if (clockwise) "CLOCKWISE" else "COUNTER-CLOCKWISE") }
            GsSettingLine("Camera", "Face POI")
            GsSettingLine("Completion", "Hover")
            if (active) {
                SmartFlightExecutionPanel(execution)
                OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("STOP ORBIT") }
            } else {
                Button(onClick = { reviewOpen = true }, enabled = canStart, modifier = Modifier.fillMaxWidth()) { Text("REVIEW ORBIT") }
            }
            if (source != TelemetrySource.SIMULATOR) Text("Orbit execution is simulator-only.", color = GsColors.Muted, fontSize = 9.sp)
        }
        Card(Modifier.weight(1f).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = GsColors.Panel)) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width/2,size.height/2); val r = size.minDimension*.30f
                drawCircle(GsColors.Orange.copy(alpha=.12f),r,c); drawCircle(GsColors.Orange,r,c,style=androidx.compose.ui.graphics.drawscope.Stroke(4f)); drawCircle(Color.White,8f,c); drawCircle(GsColors.Orange,12f,Offset(c.x+r,c.y))
            }
        }
    }
    if (reviewOpen && point != null) {
        AlertDialog(
            onDismissRequest = { reviewOpen = false },
            title = { Text("REVIEW ORBIT") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GsSettingLine("Point of interest", "%.5f, %.5f".format(point.latitudeDeg, point.longitudeDeg))
                    GsSettingLine("Radius / altitude", "${radius.toInt()} m / ${altitude.toInt()} m")
                    GsSettingLine("Speed", "%.1f m/s".format(speed))
                    GsSettingLine("Direction", if (clockwise) "Clockwise" else "Counter-clockwise")
                    GsSettingLine("Laps", laps.toInt().toString())
                }
            },
            dismissButton = { TextButton(onClick = { reviewOpen = false }) { Text("CANCEL") } },
            confirmButton = {
                Button(onClick = {
                    onStart(point, radius.toDouble(), altitude.toDouble(), speed.toDouble(), clockwise, laps.toInt())
                    reviewOpen = false
                }) { Text("START ORBIT") }
            }
        )
    }
}

@Composable
private fun FollowEditor(
    state: XStarState,
    source: TelemetrySource,
    execution: SmartFlightExecutionState,
    onStart: (Double, Double, Double) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    var distance by remember { mutableFloatStateOf(20f) }
    var altitude by remember { mutableFloatStateOf(30f) }
    var speed by remember { mutableFloatStateOf(5f) }
    var reviewOpen by remember { mutableStateOf(false) }
    val active = execution.mode == SmartFlightMode.FOLLOW && execution.phase == SmartFlightPhase.ACTIVE
    val canStart = source == TelemetrySource.SIMULATOR && state.aircraft.armed == true &&
        (state.navigation.altitudeM ?: 0.0) > .2 && execution.phase != SmartFlightPhase.ACTIVE
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        GsSectionCard("FOLLOW", Modifier.width(360.dp).fillMaxHeight()) {
            GsSettingLine("Target", "Simulated operator at Home Point")
            MissionSlider("Distance",distance,5f..100f,"m") { distance=it }
            MissionSlider("Altitude",altitude,5f..120f,"m") { altitude=it }
            MissionSlider("Speed",speed,1f..10f,"m/s") { speed=it }
            GsSettingLine("Relative position", "Behind")
            GsSettingLine("Camera", "Face target")
            GsSettingLine("Target loss", "Hover")
            if (active) {
                SmartFlightExecutionPanel(execution)
                OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("STOP FOLLOW") }
            } else {
                Button(onClick = { reviewOpen = true }, enabled = canStart, modifier = Modifier.fillMaxWidth()) { Text("REVIEW FOLLOW") }
            }
            if (source != TelemetrySource.SIMULATOR) Text("Follow execution is simulator-only.", color = GsColors.Muted, fontSize = 9.sp)
        }
        Card(Modifier.weight(1f).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = GsColors.Panel)) {
            Box(Modifier.fillMaxSize()) { Text("FOLLOW GEOMETRY PREVIEW", Modifier.align(Alignment.Center), color = GsColors.Muted) }
        }
    }
    if (reviewOpen) {
        AlertDialog(
            onDismissRequest = { reviewOpen = false },
            title = { Text("REVIEW FOLLOW") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GsSettingLine("Target", "Simulated operator at Home Point")
                    GsSettingLine("Distance / altitude", "${distance.toInt()} m / ${altitude.toInt()} m")
                    GsSettingLine("Maximum speed", "%.1f m/s".format(speed))
                    GsSettingLine("Target loss", "Hover")
                }
            },
            dismissButton = { TextButton(onClick = { reviewOpen = false }) { Text("CANCEL") } },
            confirmButton = {
                Button(onClick = {
                    onStart(distance.toDouble(), altitude.toDouble(), speed.toDouble())
                    reviewOpen = false
                }) { Text("START FOLLOW") }
            }
        )
    }
}

@Composable
private fun CourseLockEditor(
    state: XStarState,
    source: TelemetrySource,
    execution: SmartFlightExecutionState,
    onStart: (Double) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    var heading by remember { mutableFloatStateOf((state.attitude.yawDeg ?: 0.0).toFloat()) }
    var reviewOpen by remember { mutableStateOf(false) }
    val active = execution.mode == SmartFlightMode.COURSE_LOCK && execution.phase == SmartFlightPhase.ACTIVE
    val airborne = state.aircraft.armed == true && (state.navigation.altitudeM ?: 0.0) > .2
    val canStart = source == TelemetrySource.SIMULATOR && airborne && execution.phase != SmartFlightPhase.ACTIVE
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        GsSectionCard("COURSE LOCK", Modifier.width(360.dp).fillMaxHeight()) {
            Text(
                "Locks pitch/roll translation to a compass course while yaw remains independent.",
                color = GsColors.Muted,
                fontSize = 11.sp
            )
            MissionSlider("Locked heading", heading, 0f..359f, "°") { heading = it }
            GsSettingLine("Yaw control", "Independent")
            GsSettingLine("Throttle / gimbal", "Manual")
            if (active) {
                SmartFlightExecutionPanel(execution)
                OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("STOP COURSE LOCK") }
            } else {
                Button(onClick = { reviewOpen = true }, enabled = canStart, modifier = Modifier.fillMaxWidth()) {
                    Text("REVIEW COURSE LOCK")
                }
            }
            if (source != TelemetrySource.SIMULATOR) {
                Text("Course Lock execution is simulator-only.", color = GsColors.Muted, fontSize = 9.sp)
            }
        }
        Card(Modifier.weight(1f).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = GsColors.Panel)) {
            Box(Modifier.fillMaxSize()) {
                Text(
                    "LOCKED COURSE ${heading.toInt()}°\nAircraft yaw does not rotate the translation frame",
                    Modifier.align(Alignment.Center),
                    color = GsColors.Muted
                )
            }
        }
    }
    if (reviewOpen) {
        AlertDialog(
            onDismissRequest = { reviewOpen = false },
            title = { Text("REVIEW COURSE LOCK") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GsSettingLine("Locked course", "${heading.toInt()}°")
                    GsSettingLine("Pitch / roll", "Course-relative")
                    GsSettingLine("Yaw / throttle / gimbal", "Manual")
                }
            },
            dismissButton = { TextButton(onClick = { reviewOpen = false }) { Text("CANCEL") } },
            confirmButton = {
                Button(onClick = {
                    onStart(heading.toDouble())
                    reviewOpen = false
                }) { Text("START COURSE LOCK") }
            }
        )
    }
}

@Composable
private fun HomeLockEditor(
    state: XStarState,
    source: TelemetrySource,
    execution: SmartFlightExecutionState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    var reviewOpen by remember { mutableStateOf(false) }
    val active = execution.mode == SmartFlightMode.HOME_LOCK && execution.phase == SmartFlightPhase.ACTIVE
    val airborne = state.aircraft.armed == true && (state.navigation.altitudeM ?: 0.0) > .2
    val homeAvailable = state.navigation.homeLatitudeDeg != null && state.navigation.homeLongitudeDeg != null
    val canStart = source == TelemetrySource.SIMULATOR && airborne && homeAvailable && execution.phase != SmartFlightPhase.ACTIVE
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        GsSectionCard("HOME LOCK", Modifier.width(360.dp).fillMaxHeight()) {
            Text(
                "Pitch commands movement away from or toward Home; roll commands tangential movement around Home.",
                color = GsColors.Muted,
                fontSize = 11.sp
            )
            GsSettingLine("Home Point", if (homeAvailable) "Available" else "Unavailable")
            GsSettingLine("Pitch forward / back", "Away / toward Home")
            GsSettingLine("Roll", "Clockwise / counter-clockwise")
            GsSettingLine("Yaw / throttle / gimbal", "Manual")
            if (active) {
                SmartFlightExecutionPanel(execution)
                OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("STOP HOME LOCK") }
            } else {
                Button(onClick = { reviewOpen = true }, enabled = canStart, modifier = Modifier.fillMaxWidth()) {
                    Text("REVIEW HOME LOCK")
                }
            }
            if (source != TelemetrySource.SIMULATOR) {
                Text("Home Lock execution is simulator-only.", color = GsColors.Muted, fontSize = 9.sp)
            }
        }
        Card(Modifier.weight(1f).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = GsColors.Panel)) {
            Box(Modifier.fillMaxSize()) {
                Text(
                    "HOME-RELATIVE CONTROL FRAME\nTranslation remains anchored to the confirmed Home Point",
                    Modifier.align(Alignment.Center),
                    color = GsColors.Muted
                )
            }
        }
    }
    if (reviewOpen) {
        AlertDialog(
            onDismissRequest = { reviewOpen = false },
            title = { Text("REVIEW HOME LOCK") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GsSettingLine(
                        "Home Point",
                        state.navigation.homeLatitudeDeg?.let { latitude ->
                            state.navigation.homeLongitudeDeg?.let { longitude -> "%.5f, %.5f".format(latitude, longitude) }
                        } ?: "Unavailable"
                    )
                    GsSettingLine("Pitch", "Away from / toward Home")
                    GsSettingLine("Roll", "Tangential around Home")
                    GsSettingLine("Yaw / throttle / gimbal", "Manual")
                }
            },
            dismissButton = { TextButton(onClick = { reviewOpen = false }) { Text("CANCEL") } },
            confirmButton = {
                Button(onClick = {
                    onStart()
                    reviewOpen = false
                }) { Text("START HOME LOCK") }
            }
        )
    }
}

@Composable
private fun SmartFlightExecutionPanel(state: SmartFlightExecutionState) {
    Text("${state.mode.name.replace('_', ' ')} · ${state.phase}", color = GsColors.Orange, fontWeight = FontWeight.Bold)
    state.progress?.let { GsSettingLine("Progress", "${(it * 100).toInt()}%") }
    if (state.targetLaps != null) GsSettingLine("Laps", "${state.completedLaps ?: 0} / ${state.targetLaps}")
    state.distanceToTargetM?.let {
        GsSettingLine(if (state.mode == SmartFlightMode.HOME_LOCK) "Home distance" else "Target distance", "%.1f m".format(it))
    }
    state.detail?.let { Text(it, color = GsColors.Muted, fontSize = 9.sp) }
}

@Composable
private fun MissionSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String, onChange: (Float)->Unit) {
    Column { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label,color=GsColors.Muted); Text("%.1f %s".format(value,unit),color=GsColors.White) }; Slider(value,onChange,valueRange=range) }
}
