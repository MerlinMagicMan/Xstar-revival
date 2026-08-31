package io.xstarrevival.app.gs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import io.xstarrevival.core.groundstation.RecoveryPoint
import io.xstarrevival.core.model.XStarState

enum class RecordTab { FLIGHTS, FIND_AIRCRAFT }

@Composable
fun GsRecordsScreen(state: XStarState, recoveryPoints: List<RecoveryPoint> = emptyList()) {
    var tab by remember { mutableStateOf(RecordTab.FLIGHTS) }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("FLIGHT RECORDS", color = GsColors.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text("Local-first history and aircraft recovery", color = GsColors.Muted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tab == RecordTab.FLIGHTS) Button(onClick = { tab = RecordTab.FLIGHTS }) { Text("FLIGHTS") }
                else OutlinedButton(onClick = { tab = RecordTab.FLIGHTS }) { Text("FLIGHTS") }
                if (tab == RecordTab.FIND_AIRCRAFT) Button(onClick = { tab = RecordTab.FIND_AIRCRAFT }) { Text("FIND MY X-STAR") }
                else OutlinedButton(onClick = { tab = RecordTab.FIND_AIRCRAFT }) { Text("FIND MY X-STAR") }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (tab == RecordTab.FLIGHTS) FlightList(Modifier.weight(1f))
        else FindAircraftPanel(state, recoveryPoints, Modifier.weight(1f))
    }
}

@Composable
private fun FlightList(modifier: Modifier = Modifier) {
    val records = listOf(
        "Today · 12:41 PM" to "14:22 · 2.1 km · 84 m · 82→39%",
        "Aug 30 · 6:18 PM" to "08:47 · 1.0 km · 52 m · 91→64%",
        "Aug 29 · 10:03 AM" to "21:11 · 3.8 km · 101 m · 96→31%"
    )
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(records) { (title, detail) ->
            Card(colors = CardDefaults.cardColors(containerColor = GsColors.Panel)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(title, color = GsColors.White, fontWeight = FontWeight.Bold)
                        Text(detail, color = GsColors.Muted)
                    }
                    Text("›", color = GsColors.Orange, fontSize = 28.sp)
                }
            }
        }
    }
}

@Composable
private fun FindAircraftPanel(state: XStarState, recoveryPoints: List<RecoveryPoint>, modifier: Modifier = Modifier) {
    val last = recoveryPoints.lastOrNull()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(Modifier.weight(.58f).fillMaxSize(), colors = CardDefaults.cardColors(containerColor = GsColors.Panel), shape = RoundedCornerShape(18.dp)) {
            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize().padding(18.dp)) {
                    val grid = Color.White.copy(alpha = .06f)
                    repeat(9) { i -> drawLine(grid, Offset(size.width * i / 8f, 0f), Offset(size.width * i / 8f, size.height), 1f) }
                    repeat(7) { i -> drawLine(grid, Offset(0f, size.height * i / 6f), Offset(size.width, size.height * i / 6f), 1f) }
                    if (recoveryPoints.isNotEmpty()) {
                        val lats = recoveryPoints.map { it.position.latitudeDeg }
                        val lons = recoveryPoints.map { it.position.longitudeDeg }
                        val minLat = lats.minOrNull() ?: 0.0
                        val maxLat = lats.maxOrNull() ?: minLat
                        val minLon = lons.minOrNull() ?: 0.0
                        val maxLon = lons.maxOrNull() ?: minLon
                        val latSpan = (maxLat - minLat).takeIf { it > 0.000001 } ?: 0.000001
                        val lonSpan = (maxLon - minLon).takeIf { it > 0.000001 } ?: 0.000001
                        val points = recoveryPoints.map { point ->
                            val x = ((point.position.longitudeDeg - minLon) / lonSpan).toFloat()
                            val y = (1.0 - (point.position.latitudeDeg - minLat) / latSpan).toFloat()
                            Offset(24f + x * (size.width - 48f), 24f + y * (size.height - 48f))
                        }
                        points.zipWithNext().forEach { (a, b) -> drawLine(GsColors.Orange.copy(alpha = .72f), a, b, 4f) }
                        points.forEach { drawCircle(GsColors.Orange, 6f, it) }
                        drawCircle(GsColors.Red, 13f, points.last())
                    }
                }
                Text("LAST KNOWN PATH · ${recoveryPoints.size} samples", Modifier.align(Alignment.TopStart).padding(16.dp), color = GsColors.Muted, fontSize = 10.sp)
                if (recoveryPoints.isEmpty()) Text("No location has been recorded yet", Modifier.align(Alignment.Center), color = GsColors.Muted)
            }
        }
        GsSectionCard("LAST KNOWN AIRCRAFT", Modifier.weight(.42f).fillMaxSize()) {
            GsSettingLine("Latitude", last?.position?.latitudeDeg?.let { "%.6f".format(it) } ?: state.navigation.latitudeDeg?.let { "%.6f".format(it) } ?: "—")
            GsSettingLine("Longitude", last?.position?.longitudeDeg?.let { "%.6f".format(it) } ?: state.navigation.longitudeDeg?.let { "%.6f".format(it) } ?: "—")
            GsSettingLine("Altitude", last?.altitudeM?.let { "%.1f m".format(it) } ?: state.navigation.altitudeM?.let { "%.1f m".format(it) } ?: "—")
            GsSettingLine("Ground speed", last?.groundSpeedMps?.let { "%.1f m/s".format(it) } ?: state.navigation.groundSpeedMps?.let { "%.1f m/s".format(it) } ?: "—")
            GsSettingLine("Heading", last?.headingDeg?.let { "%.0f°".format(it) } ?: state.attitude.yawDeg?.let { "%.0f°".format(it) } ?: "—")
            GsSettingLine("Battery", last?.batteryPercent?.let { "$it%" } ?: state.battery.percent?.let { "$it%" } ?: "—")
            GsSettingLine("Stored samples", recoveryPoints.size.toString())
            Spacer(Modifier.height(12.dp))
            Text("Recent aircraft positions are stored only on this device so the last known location survives an app restart or radio-link loss.", color = GsColors.Muted, fontSize = 11.sp)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { }, enabled = last != null, modifier = Modifier.fillMaxWidth()) { Text("NAVIGATE TO LAST POSITION") }
            OutlinedButton(onClick = { }, enabled = recoveryPoints.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("EXPORT LAST PATH") }
        }
    }
}

@Composable
fun GsMediaScreen(state: XStarState) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("MEDIA", color = GsColors.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text(if (state.camera.connected == true) "Aircraft camera connected" else "Connect aircraft to browse onboard media", color = GsColors.Muted)
        }
        items((1..12).toList().chunked(4)) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { i ->
                    Box(
                        Modifier.weight(1f).aspectRatio(16f / 9f).background(GsColors.Panel2, RoundedCornerShape(12.dp)).clickable { },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("▣", color = GsColors.Faint, fontSize = 28.sp)
                            Text("MEDIA $i", color = GsColors.Muted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GsSettingsScreen() {
    val sections = listOf(
        "Flight Control" to "Limits, RTH altitude, Beginner Mode, ATTI, IOC",
        "Remote Controller" to "Stick mode, calibration, sensitivity, mappings",
        "Video Link" to "Auto/manual channel, signal analyzer",
        "Aircraft Battery" to "Warnings, thresholds, health and cells",
        "Gimbal" to "Pitch speed, smoothing, calibration",
        "General" to "Units, aircraft identity, logs, firmware"
    )
    var metric by remember { mutableStateOf(true) }
    var highVisibility by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Text("SETTINGS", color = GsColors.White, fontSize = 26.sp, fontWeight = FontWeight.Black) }
        items(sections) { (name, detail) ->
            Card(Modifier.fillMaxWidth().clickable { }, colors = CardDefaults.cardColors(containerColor = GsColors.Panel)) {
                Row(Modifier.padding(18.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, color = GsColors.White, fontWeight = FontWeight.Bold)
                        Text(detail, color = GsColors.Muted, fontSize = 12.sp)
                    }
                    Text("›", color = GsColors.Orange, fontSize = 28.sp)
                }
            }
        }
        item {
            GsSectionCard("APP") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text("Metric units", color = GsColors.White); Text("Use SI units internally and in the HUD", color = GsColors.Muted, fontSize = 11.sp) }
                    Switch(checked = metric, onCheckedChange = { metric = it })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text("High visibility HUD", color = GsColors.White); Text("Stronger backgrounds and larger outdoor telemetry", color = GsColors.Muted, fontSize = 11.sp) }
                    Switch(checked = highVisibility, onCheckedChange = { highVisibility = it })
                }
                GsSettingLine("Map cache", "Offline ready")
                GsSettingLine("Telemetry logs", "Local only")
                GsSettingLine("Critical alerts", "Enabled")
                GsSettingLine("Developer diagnostics", "Available")
            }
        }
    }
}
