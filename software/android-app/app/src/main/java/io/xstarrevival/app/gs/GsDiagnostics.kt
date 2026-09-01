package io.xstarrevival.app.gs

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.xstarrevival.app.TelemetrySource
import io.xstarrevival.core.command.CommandStatus
import io.xstarrevival.core.model.ConnectionState
import io.xstarrevival.core.model.ProtocolPacketDisposition
import io.xstarrevival.core.model.ProtocolPacketTrace
import io.xstarrevival.core.model.XStarState
import java.text.DateFormat
import java.util.Date

private enum class PacketFilter { ALL, DECODED, OPAQUE, CRC_FAILURE }

@Composable
fun GsDiagnosticsInspector(
    state: XStarState,
    source: TelemetrySource,
    commandHistory: List<CommandStatus>
) {
    var packetFilter by remember { mutableStateOf(PacketFilter.ALL) }
    var selectedPacket by remember { mutableStateOf<ProtocolPacketTrace?>(null) }
    val packets = state.diagnostics.packets.filter { packet ->
        when (packetFilter) {
            PacketFilter.ALL -> true
            PacketFilter.DECODED -> packet.disposition == ProtocolPacketDisposition.DECODED
            PacketFilter.OPAQUE -> packet.disposition == ProtocolPacketDisposition.OPAQUE
            PacketFilter.CRC_FAILURE -> packet.disposition == ProtocolPacketDisposition.CRC_FAILURE
        }
    }.asReversed()

    GsSectionCard("CONNECTION") {
        GsSettingLine("Selected source", source.label)
        GsSettingLine("Normalized source", state.diagnostics.source ?: "Unavailable")
        GsSettingLine("Connection", connectionLabel(state.connection))
        GsSettingLine("Transport", connectionTransport(state.connection))
        GsSettingLine("Latency", state.imageLink.latencyMs?.let { "$it ms" } ?: "Unavailable")
        GsSettingLine("Packet loss", state.imageLink.packetLossPercent?.let { "%.2f%%".format(it) } ?: "Unavailable")
        GsSettingLine("Last update", state.diagnostics.lastUpdateEpochMs?.let(::formatTimestamp) ?: "Unavailable")
        state.diagnostics.counters.toSortedMap().forEach { (key, value) -> GsSettingLine(key, value.toString()) }
    }
    GsSectionCard("FLIGHT CONTROLLER") {
        GsSettingLine("Mode", state.aircraft.flightMode ?: "Unavailable")
        GsSettingLine("Armed flag", state.aircraft.armed?.toString() ?: "Unavailable")
        GsSettingLine("Connection flag", (state.connection is ConnectionState.Connected).toString())
        GsSettingLine("Warnings / errors", state.warnings.size.toString())
        state.warnings.take(10).forEach { warning ->
            Text("${warning.severity}: ${warning.id} — ${warning.message}", color = GsColors.Amber, fontSize = 10.sp)
        }
    }
    GsSectionCard("GPS / IMU / COMPASS") {
        GsSettingLine("Coordinates", when {
            state.navigation.latitudeDeg != null && state.navigation.longitudeDeg != null ->
                "%.6f, %.6f".format(state.navigation.latitudeDeg, state.navigation.longitudeDeg)
            else -> "Unavailable"
        })
        GsSettingLine("Satellites", state.navigation.satellites?.toString() ?: "Unavailable")
        GsSettingLine("Fix", state.navigation.gpsFix ?: "Unavailable")
        GsSettingLine("GPS accuracy / HDOP", "Unavailable from this source")
        GsSettingLine("Accelerometer", "Unavailable from normalized telemetry")
        GsSettingLine("Gyroscope", "Unavailable from normalized telemetry")
        GsSettingLine("IMU temperature", "Unavailable from normalized telemetry")
        GsSettingLine("Compass heading", state.attitude.yawDeg?.let { "%.1f°".format(it) } ?: "Unavailable")
        GsSettingLine("Compass calibration", "Unavailable from normalized telemetry")
    }
    GsSectionCard("BATTERY RAW TELEMETRY") {
        GsSettingLine("Percent / voltage", "${state.battery.percent ?: "—"}% / ${state.battery.packVoltageV?.let { "%.3f V".format(it) } ?: "—"}")
        GsSettingLine("Current / temperature", "${state.battery.currentA?.let { "%.3f A".format(it) } ?: "—"} / ${state.battery.temperatureC?.let { "%.2f°C".format(it) } ?: "—"}")
        GsSettingLine("Remaining / full / design", "${state.battery.remainingCapacityMah ?: "—"} / ${state.battery.fullCapacityMah ?: "—"} / ${state.battery.designCapacityMah ?: "—"} mAh")
        state.battery.cells.forEach { cell -> GsSettingLine("Cell ${cell.index}", cell.voltageV?.let { "%.4f V".format(it) } ?: "Unavailable") }
    }
    GsSectionCard("PROTOCOL INSPECTOR") {
        GsSettingLine("Protocol version", state.diagnostics.protocolVersion ?: "Unavailable")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PacketFilter.entries.forEach { filter ->
                if (filter == packetFilter) Button(onClick = { packetFilter = filter }, modifier = Modifier.weight(1f)) { Text(filter.name, fontSize = 8.sp) }
                else OutlinedButton(onClick = { packetFilter = filter }, modifier = Modifier.weight(1f)) { Text(filter.name, fontSize = 8.sp) }
            }
        }
        packets.take(50).forEach { packet ->
            Column(
                Modifier.fillMaxWidth().clickable { selectedPacket = packet }.padding(vertical = 5.dp)
            ) {
                Text("#${packet.sequence}  ${packet.decodedName ?: "MSG ${packet.messageId}"}", color = GsColors.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                Text("${packet.protocol} · component ${packet.componentId} · ${packet.lengthBytes} bytes · ${packet.disposition}", color = GsColors.Muted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            }
        }
        if (packets.isEmpty()) Text("No packet traces available for this source.", color = GsColors.Muted, fontSize = 10.sp)
        selectedPacket?.let { packet ->
            Text("RAW PACKET #${packet.sequence}", color = GsColors.Orange, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            Text(packet.rawHex, color = GsColors.White, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
            OutlinedButton(onClick = { selectedPacket = null }, modifier = Modifier.fillMaxWidth()) { Text("CLOSE PACKET") }
        }
    }
    GsSectionCard("COMMAND / ACK LOG") {
        commandHistory.takeLast(80).asReversed().forEach { status ->
            GsSettingLine(
                "${status.request.id} · ${status.request.command.kind}",
                "${status.phase}${status.detail?.let { " · $it" }.orEmpty()}"
            )
        }
        if (commandHistory.isEmpty()) Text("No simulator commands have been dispatched in this session.", color = GsColors.Muted, fontSize = 10.sp)
    }
    GsSectionCard("DIAGNOSTIC NOTES") {
        state.diagnostics.notes.takeLast(50).asReversed().forEach { note -> Text(note, color = GsColors.Muted, fontSize = 10.sp) }
        if (state.diagnostics.notes.isEmpty()) Text("No diagnostic notes.", color = GsColors.Muted, fontSize = 10.sp)
    }
}

internal fun buildDiagnosticReport(
    state: XStarState,
    source: TelemetrySource,
    commandHistory: List<CommandStatus>,
    includeRawPackets: Boolean
): String = buildString {
    appendLine("X-Star Ground Station diagnostic report")
    appendLine("generated=${System.currentTimeMillis()}")
    appendLine("source=${source.name}")
    appendLine("normalized_source=${state.diagnostics.source ?: "unavailable"}")
    appendLine("connection=${connectionLabel(state.connection)}")
    appendLine("transport=${connectionTransport(state.connection)}")
    appendLine("protocol=${state.diagnostics.protocolVersion ?: "unavailable"}")
    appendLine("coordinates=REDACTED")
    appendLine("counters=${state.diagnostics.counters.toSortedMap()}")
    appendLine("flight_mode=${state.aircraft.flightMode ?: "unavailable"}")
    appendLine("armed=${state.aircraft.armed ?: "unavailable"}")
    appendLine("gps=${state.navigation.gpsFix ?: "unavailable"}, satellites=${state.navigation.satellites ?: "unavailable"}")
    appendLine("battery_percent=${state.battery.percent ?: "unavailable"}, voltage=${state.battery.packVoltageV ?: "unavailable"}, current=${state.battery.currentA ?: "unavailable"}")
    appendLine("warnings=${state.warnings.map { "${it.id}:${it.severity}" }}")
    appendLine("notes=REDACTED (${state.diagnostics.notes.size})")
    appendLine("packets=${state.diagnostics.packets.size}")
    state.diagnostics.packets.takeLast(200).forEach { packet ->
        append("packet #${packet.sequence} ${packet.protocol} msg=${packet.messageId} component=${packet.componentId} length=${packet.lengthBytes} disposition=${packet.disposition}")
        if (includeRawPackets) append(" raw=${packet.rawHex}")
        appendLine()
    }
    appendLine("command_transitions=${commandHistory.size}")
    commandHistory.takeLast(200).forEach { status ->
        appendLine("command ${status.request.id} kind=${status.request.command.kind} phase=${status.phase} detail=${status.detail ?: ""}")
    }
}

fun shareDiagnosticReport(
    context: Context,
    state: XStarState,
    source: TelemetrySource,
    commandHistory: List<CommandStatus>,
    includeRawPackets: Boolean
) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "X-Star diagnostic report")
        putExtra(Intent.EXTRA_TEXT, buildDiagnosticReport(state, source, commandHistory, includeRawPackets))
    }
    context.startActivity(Intent.createChooser(intent, "Export X-Star diagnostics"))
}

private fun connectionLabel(connection: ConnectionState): String = when (connection) {
    ConnectionState.Disconnected -> "Disconnected"
    ConnectionState.Discovering -> "Discovering"
    is ConnectionState.Connecting -> "Connecting: ${connection.stage}"
    is ConnectionState.Connected -> "Connected: ${connection.product}"
    is ConnectionState.Failed -> "Failed: ${connection.stage}"
}

private fun connectionTransport(connection: ConnectionState): String = when (connection) {
    is ConnectionState.Connected -> connection.transport
    else -> "Unavailable"
}

private fun formatTimestamp(timestampEpochMs: Long): String = DateFormat.getDateTimeInstance().format(Date(timestampEpochMs))
