package io.xstarrevival.app.gs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.xstarrevival.core.groundstation.BatteryHealthAnalyzer
import io.xstarrevival.core.model.CellState
import io.xstarrevival.core.model.XStarState

@Composable
fun GsAircraftScreen(state: XStarState) {
    val cells = if (state.battery.cells.isNotEmpty()) state.battery.cells else (1..4).map { CellState(it, null) }
    val health = BatteryHealthAnalyzer.assess(state.battery)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("AIRCRAFT", color = GsColors.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text(state.aircraft.productName ?: "X-Star Premium", color = GsColors.Muted)
        }
        item {
            GsSectionCard("FLIGHT BATTERY") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    GsBigMetric(state.battery.percent?.let { "$it%" } ?: "—", "Charge")
                    GsBigMetric(state.battery.packVoltageV?.let { "%.2fV".format(it) } ?: "—", "Voltage")
                    GsBigMetric(state.battery.temperatureC?.let { "%.1f°C".format(it) } ?: "—", "Temperature")
                    GsBigMetric(state.battery.dischargeCount?.toString() ?: "—", "Cycles")
                    GsBigMetric(health.healthPercent?.let { "$it%" } ?: "—", "Health")
                }
                Spacer(Modifier.height(14.dp))
                cells.forEach { cell ->
                    GsSettingLine("Cell ${cell.index}", cell.voltageV?.let { "%.3f V".format(it) } ?: "—")
                }
                GsSettingLine("Cell delta", state.battery.cellDeltaV?.let { "%.3f V".format(it) } ?: "—")
                GsSettingLine("Estimated full capacity", state.battery.fullCapacityMah?.let { "$it mAh" } ?: "—")
                GsSettingLine("Design capacity", state.battery.designCapacityMah?.let { "$it mAh" } ?: "—")
                if (health.advisories.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    health.advisories.forEach { Text("!  $it", color = GsColors.Amber, fontSize = 12.sp) }
                }
            }
        }
        item {
            GsSectionCard("REMOTE CONTROLLER") {
                GsSettingLine("Connection", state.remote.connected?.let { if (it) "Connected" else "Disconnected" } ?: "—")
                GsSettingLine("Battery", state.remote.batteryPercent?.let { "$it%" } ?: "—")
                GsSettingLine("Signal", state.remote.signalPercent?.let { "$it%" } ?: "—")
                GsSettingLine("Image link", state.remote.imageSignalPercent?.let { "$it%" } ?: "—")
            }
        }
        item {
            GsSectionCard("VIDEO LINK") {
                GsSettingLine("RF frequency", state.imageLink.rfFrequencyHz?.let { "%.3f MHz".format(it / 1e6) } ?: "—")
                GsSettingLine("RF signal", state.imageLink.rfSignalValue?.toString() ?: "—")
                GsSettingLine("Video", if (state.camera.video.receiving) "Receiving ${state.camera.video.codec ?: ""}" else "—")
                GsSettingLine("Resolution", if (state.camera.video.width != null && state.camera.video.height != null) "${state.camera.video.width}×${state.camera.video.height}" else "—")
                GsSettingLine("Frames received", state.camera.video.framesReceived.toString())
            }
        }
        item {
            GsSectionCard("GIMBAL") {
                GsSettingLine("Pitch", state.gimbal.pitchDeg?.let { "%.1f°".format(it) } ?: "—")
                GsSettingLine("Status", state.gimbal.status ?: "—")
            }
        }
        item {
            GsSectionCard("NAVIGATION / FLIGHT CONTROLLER") {
                GsSettingLine("Flight mode", state.aircraft.flightMode ?: "—")
                GsSettingLine("Armed", state.aircraft.armed?.toString() ?: "—")
                GsSettingLine("GPS fix", state.navigation.gpsFix ?: "—")
                GsSettingLine("Satellites", state.navigation.satellites?.toString() ?: "—")
                GsSettingLine("Altitude", state.navigation.altitudeM?.let { "%.1f m".format(it) } ?: "—")
                GsSettingLine("Ground speed", state.navigation.groundSpeedMps?.let { "%.1f m/s".format(it) } ?: "—")
            }
        }
        item {
            GsSectionCard("SYSTEM / DIAGNOSTICS") {
                GsSettingLine("Firmware", state.aircraft.firmwareVersion ?: "—")
                GsSettingLine("Protocol source", state.diagnostics.source ?: "—")
                GsSettingLine("Last update", state.diagnostics.lastUpdateEpochMs?.toString() ?: "—")
                state.diagnostics.counters.entries.take(8).forEach { (key, value) -> GsSettingLine(key, value.toString()) }
                state.diagnostics.notes.take(5).forEach { Text(it, color = GsColors.Muted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        }
    }
}
