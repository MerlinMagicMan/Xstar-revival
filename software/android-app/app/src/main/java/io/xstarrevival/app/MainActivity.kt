package io.xstarrevival.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.xstarrevival.core.model.ConnectionState
import io.xstarrevival.core.model.XStarState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val vm: XStarViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                XStarDashboard(
                    state = state,
                    platformName = vm.platformName,
                    onConnect = vm::connect,
                    onDisconnect = vm::disconnect,
                    onRefresh = vm::refresh
                )
            }
        }
    }
}

@Composable
private fun XStarDashboard(
    state: XStarState,
    platformName: String,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("X-Star Revival", fontWeight = FontWeight.Bold)
                        Text(platformName, style = MaterialTheme.typography.labelSmall)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
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
            CameraCard(state)
            DiagnosticsCard(state)
        }
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
}

@Composable
private fun CameraCard(state: XStarState) = SectionCard("Camera / FPV") {
    Metric("Camera", state.camera.connected?.let { if (it) "Connected" else "Disconnected" })
    Metric("Mode", state.camera.mode)
    Metric("Recording", state.camera.recording?.toString())
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
