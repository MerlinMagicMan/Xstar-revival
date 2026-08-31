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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.xstarrevival.core.model.ConnectionState
import io.xstarrevival.core.model.XStarState

enum class GsMissionMode { WAYPOINTS, ORBIT, FOLLOW }

private data class GsMissionDraft(
    val name: String,
    val distanceKm: Double,
    val minutes: Int,
    val batteryPercent: Int,
    val waypoints: Int
)

@Composable
fun GsMissionScreen(state: XStarState) {
    var mode by remember { mutableStateOf(GsMissionMode.WAYPOINTS) }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("MISSION CONTROL", color = GsColors.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text("Configure → Review → Execute", color = GsColors.Muted)
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
            GsMissionMode.WAYPOINTS -> WaypointPlanner(state, Modifier.weight(1f))
            GsMissionMode.ORBIT -> OrbitPlanner(state, Modifier.weight(1f))
            GsMissionMode.FOLLOW -> FollowPlanner(state, Modifier.weight(1f))
        }
    }
}

@Composable
private fun WaypointPlanner(state: XStarState, modifier: Modifier = Modifier) {
    var selected by remember { mutableStateOf<GsMissionDraft?>(null) }
    val missions = remember {
        listOf(
            GsMissionDraft("West Property Survey", 1.24, 9, 41, 12),
            GsMissionDraft("Lake Perimeter", 2.80, 17, 67, 15),
            GsMissionDraft("Roof Inspection", .46, 6, 28, 8)
        )
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(.34f)) {
            Text("SAVED MISSIONS", color = GsColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            missions.forEach { mission ->
                Card(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { selected = mission },
                    colors = CardDefaults.cardColors(containerColor = if (selected == mission) GsColors.Orange.copy(alpha = .14f) else GsColors.Panel)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(mission.name, color = GsColors.White, fontWeight = FontWeight.Bold)
                        Text("${mission.waypoints} WP · ${mission.distanceKm} km · ${mission.minutes} min", color = GsColors.Muted, fontSize = 12.sp)
                    }
                }
            }
            Button(onClick = { selected = GsMissionDraft("Untitled Mission", 0.0, 0, 0, 0) }) { Text("+ NEW MISSION") }
            Spacer(Modifier.height(8.dp))
            Text("Create by tapping points, drawing a route, or flying-and-marking once live mission recording is enabled.", color = GsColors.Muted, fontSize = 11.sp)
        }
        Card(Modifier.weight(.66f).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = GsColors.Panel), shape = RoundedCornerShape(18.dp)) {
            Box(Modifier.fillMaxSize()) {
                MissionMap()
                selected?.let { mission ->
                    Card(
                        Modifier.align(Alignment.BottomCenter).padding(18.dp).fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = .84f))
                    ) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(mission.name, color = GsColors.White, fontWeight = FontWeight.Bold)
                                Text("${mission.distanceKm} km · ${mission.minutes} min · est ${mission.batteryPercent}% battery", color = GsColors.Muted)
                            }
                            Button(onClick = { }) {
                                Text(if (state.connection is ConnectionState.Connected) "REVIEW & START" else "SAVE OFFLINE")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MissionMap() {
    Canvas(Modifier.fillMaxSize().padding(18.dp)) {
        val grid = Color.White.copy(alpha = .06f)
        repeat(10) { i -> drawLine(grid, Offset(size.width * i / 9f, 0f), Offset(size.width * i / 9f, size.height), 1f) }
        repeat(8) { i -> drawLine(grid, Offset(0f, size.height * i / 7f), Offset(size.width, size.height * i / 7f), 1f) }
        val pts = listOf(
            Offset(size.width * .18f, size.height * .72f),
            Offset(size.width * .36f, size.height * .32f),
            Offset(size.width * .68f, size.height * .28f),
            Offset(size.width * .78f, size.height * .65f),
            Offset(size.width * .46f, size.height * .78f)
        )
        pts.zipWithNext().forEach { (a, b) -> drawLine(GsColors.Orange, a, b, 4f) }
        pts.forEach { p -> drawCircle(GsColors.Orange, 11f, p); drawCircle(Color.White, 4f, p) }
    }
}

@Composable
private fun OrbitPlanner(state: XStarState, modifier: Modifier = Modifier) {
    var radius by remember { mutableFloatStateOf(35f) }
    var altitude by remember { mutableFloatStateOf(45f) }
    var speed by remember { mutableFloatStateOf(4f) }
    var laps by remember { mutableIntStateOf(2) }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        GsSectionCard("ORBIT CONFIGURATION", Modifier.width(360.dp).fillMaxHeight()) {
            Text("Point of Interest", color = GsColors.White, fontWeight = FontWeight.Bold)
            Text("Select on map", color = GsColors.Orange)
            Spacer(Modifier.height(12.dp))
            LabeledSlider("Radius", radius, "m", 10f..200f) { radius = it }
            LabeledSlider("Altitude", altitude, "m", 5f..120f) { altitude = it }
            LabeledSlider("Speed", speed, "m/s", 1f..10f) { speed = it }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Laps", color = GsColors.Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { if (laps > 1) laps-- }) { Text("−") }
                    Text(laps.toString(), color = GsColors.White, modifier = Modifier.align(Alignment.CenterVertically))
                    OutlinedButton(onClick = { if (laps < 20) laps++ }) { Text("+") }
                }
            }
            Spacer(Modifier.height(8.dp))
            GsSettingLine("Direction", "CW")
            GsSettingLine("Camera", "Face POI")
            GsSettingLine("Completion", "Hover")
            Spacer(Modifier.height(14.dp))
            Button(onClick = { }, enabled = state.connection is ConnectionState.Connected, modifier = Modifier.fillMaxWidth()) { Text("REVIEW ORBIT") }
        }
        OrbitPreview(radius, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun OrbitPreview(radius: Float, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = GsColors.Panel), shape = RoundedCornerShape(18.dp)) {
        Canvas(Modifier.fillMaxSize().padding(24.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension * .28f
            drawCircle(GsColors.Orange.copy(alpha = .25f), r, center)
            drawCircle(GsColors.Orange, r, center, style = androidx.compose.ui.graphics.drawscope.Stroke(4f))
            drawCircle(GsColors.White, 8f, center)
            drawCircle(GsColors.Orange, 11f, Offset(center.x + r, center.y))
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Text("Orbit radius ${radius.toInt()} m", Modifier.padding(18.dp), color = GsColors.Muted)
        }
    }
}

@Composable
private fun FollowPlanner(state: XStarState, modifier: Modifier = Modifier) {
    var distance by remember { mutableFloatStateOf(20f) }
    var altitude by remember { mutableFloatStateOf(30f) }
    var speed by remember { mutableFloatStateOf(6f) }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        GsSectionCard("FOLLOW CONFIGURATION", Modifier.width(360.dp).fillMaxHeight()) {
            GsSettingLine("Target", "Operator device GPS")
            LabeledSlider("Distance", distance, "m", 5f..100f) { distance = it }
            LabeledSlider("Altitude", altitude, "m", 5f..120f) { altitude = it }
            LabeledSlider("Max speed", speed, "m/s", 1f..15f) { speed = it }
            GsSettingLine("Relative position", "Behind")
            GsSettingLine("Camera", "Face target")
            GsSettingLine("Target loss", "Hover")
            Spacer(Modifier.height(14.dp))
            Button(onClick = { }, enabled = state.connection is ConnectionState.Connected, modifier = Modifier.fillMaxWidth()) { Text("REVIEW FOLLOW") }
        }
        Card(Modifier.weight(1f).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = GsColors.Panel), shape = RoundedCornerShape(18.dp)) {
            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize().padding(24.dp)) {
                    val center = Offset(size.width * .5f, size.height * .45f)
                    drawCircle(GsColors.Green, 10f, center)
                    drawCircle(GsColors.Green.copy(alpha = .12f), size.minDimension * .17f, center)
                    val drone = Offset(center.x, center.y + size.minDimension * .25f)
                    drawLine(GsColors.Orange.copy(alpha = .6f), center, drone, 3f)
                    drawCircle(GsColors.Orange, 12f, drone)
                }
                Text("Target relationship preview", Modifier.align(Alignment.BottomCenter).padding(18.dp), color = GsColors.Muted)
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, unit: String, range: ClosedFloatingPointRange<Float>, onValue: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = GsColors.Muted)
            Text("${"%.1f".format(value)} $unit", color = GsColors.White)
        }
        Slider(value = value, onValueChange = onValue, valueRange = range)
    }
}
