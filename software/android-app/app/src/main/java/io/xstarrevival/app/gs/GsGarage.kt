package io.xstarrevival.app.gs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.xstarrevival.app.TelemetrySource
import io.xstarrevival.core.model.ConnectionState
import io.xstarrevival.core.model.XStarState
import java.text.DateFormat
import java.util.Date
import java.util.UUID

@Composable
fun GsGarageScreen(
    state: XStarState,
    source: TelemetrySource,
    availableSources: List<TelemetrySource>,
    onSource: (TelemetrySource) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onEnterFlight: () -> Unit,
    onAircraft: () -> Unit,
    onMissions: () -> Unit,
    onRecords: () -> Unit,
    aircraftProfiles: List<PersistedAircraftProfile>,
    activeAircraftProfileId: String,
    onSelectAircraftProfile: (String) -> Unit,
    onSaveAircraftProfile: (PersistedAircraftProfile) -> Unit,
    onDeleteAircraftProfile: (String) -> Unit
) {
    var editingProfile by remember { mutableStateOf<PersistedAircraftProfile?>(null) }
    val activeProfile = aircraftProfiles.firstOrNull { it.id == activeAircraftProfileId } ?: aircraftProfiles.first()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("X-STAR", color = GsColors.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Text("Open Ground Station", color = GsColors.Muted)
                }
                GsConnectionPill(state.connection)
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 12.dp)) {
                items(aircraftProfiles, key = { it.id }) { profile ->
                    AircraftProfileCard(
                        profile = profile,
                        selected = profile.id == activeAircraftProfileId,
                        onSelect = { onSelectAircraftProfile(profile.id) },
                        onEdit = { editingProfile = profile }
                    )
                }
                item {
                    Card(
                        onClick = {
                            val now = System.currentTimeMillis()
                            editingProfile = PersistedAircraftProfile(
                                id = "manual:${UUID.randomUUID()}",
                                nickname = "X-Star ${aircraftProfiles.size + 1}",
                                model = "X-Star Premium",
                                createdAtEpochMs = now
                            )
                        },
                        modifier = Modifier.size(190.dp, 154.dp),
                        colors = CardDefaults.cardColors(containerColor = GsColors.Panel2)
                    ) {
                        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("ADD AIRCRAFT", color = GsColors.Orange, fontWeight = FontWeight.Bold)
                            Text("Create another local profile", color = GsColors.Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = GsColors.Panel), shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    GsDroneSilhouette(Modifier.size(230.dp, 140.dp))
                    Spacer(Modifier.width(24.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(activeProfile.nickname, color = GsColors.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(state.aircraft.productName ?: activeProfile.model, color = GsColors.Muted)
                        Text("Firmware ${state.aircraft.firmwareVersion ?: activeProfile.firmwareVersion ?: "—"}", color = GsColors.Muted)
                        val connected = state.connection is ConnectionState.Connected
                        Text(if (connected) "CONNECTED" else "NOT CONNECTED", color = if (connected) GsColors.Green else GsColors.Amber, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onEnterFlight) { Text("ENTER FLIGHT") }
                            OutlinedButton(onClick = onAircraft) { Text("AIRCRAFT") }
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GsSummaryCard("FLIGHT BATTERY", state.battery.percent?.let { "$it%" } ?: "—", state.battery.packVoltageV?.let { "%.2f V".format(it) } ?: "No telemetry", Modifier.weight(1f))
                GsSummaryCard("CONTROLLER", state.remote.batteryPercent?.let { "$it%" } ?: "—", state.remote.signalPercent?.let { "Signal $it%" } ?: "No telemetry", Modifier.weight(1f))
                GsSummaryCard("GPS", state.navigation.satellites?.toString() ?: "—", state.navigation.gpsFix ?: "No fix", Modifier.weight(1f))
                GsSummaryCard("VIDEO", if (state.camera.video.receiving) "LIVE" else "—", state.camera.video.codec ?: "No stream", Modifier.weight(1f))
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = GsColors.Panel2), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("DATA SOURCE", color = GsColors.White, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableSources.forEach { option ->
                            if (source == option) Button(onClick = { onSource(option) }) { Text(option.label) }
                            else OutlinedButton(onClick = { onSource(option) }) { Text(option.label) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onConnect) { Text("CONNECT") }
                        OutlinedButton(onClick = onRefresh) { Text("REFRESH") }
                        OutlinedButton(onClick = onDisconnect) { Text("DISCONNECT") }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickLaunch("MISSIONS", "Plan routes offline", onMissions, Modifier.weight(1f))
                QuickLaunch("FLIGHT RECORDS", "Review flights and telemetry", onRecords, Modifier.weight(1f))
                QuickLaunch("FIND MY X-STAR", "Last known aircraft position", onRecords, Modifier.weight(1f))
            }
        }
    }

    editingProfile?.let { profile ->
        AircraftProfileDialog(
            profile = profile,
            canDelete = aircraftProfiles.size > 1 && aircraftProfiles.any { it.id == profile.id },
            onDismiss = { editingProfile = null },
            onSave = {
                onSaveAircraftProfile(it)
                editingProfile = null
            },
            onDelete = {
                onDeleteAircraftProfile(profile.id)
                editingProfile = null
            }
        )
    }
}

@Composable
private fun AircraftProfileCard(
    profile: PersistedAircraftProfile,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        onClick = onSelect,
        modifier = Modifier.size(250.dp, 154.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) GsColors.Orange.copy(alpha = .16f) else GsColors.Panel)
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(profile.nickname, color = if (selected) GsColors.Orange else GsColors.White, fontWeight = FontWeight.Bold)
                TextButton(onClick = onEdit) { Text("EDIT", fontSize = 10.sp) }
            }
            Text(profile.model, color = GsColors.Muted, fontSize = 12.sp)
            Text("Serial ${profile.serialNumber ?: "not recorded"}", color = GsColors.Muted, fontSize = 11.sp)
            Text("Firmware ${profile.firmwareVersion ?: "—"} · Battery ${profile.lastBatteryPercent?.let { "$it%" } ?: "—"}", color = GsColors.Muted, fontSize = 11.sp)
            Text(
                profile.lastConnectedEpochMs?.let { "Last connected ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))}" }
                    ?: "Never connected",
                color = GsColors.Muted,
                fontSize = 11.sp
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(profile.lastLocationLabel(), color = GsColors.Muted, fontSize = 10.sp)
                Text(profile.healthState, color = aircraftHealthColor(profile.healthState), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AircraftProfileDialog(
    profile: PersistedAircraftProfile,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (PersistedAircraftProfile) -> Unit,
    onDelete: () -> Unit
) {
    var nickname by remember(profile.id) { mutableStateOf(profile.nickname) }
    var model by remember(profile.id) { mutableStateOf(profile.model) }
    var serial by remember(profile.id) { mutableStateOf(profile.serialNumber.orEmpty()) }
    var deleteOpen by remember(profile.id) { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Card(colors = CardDefaults.cardColors(containerColor = GsColors.Panel)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("AIRCRAFT PROFILE", color = GsColors.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                OutlinedTextField(nickname, { nickname = it.take(40) }, label = { Text("Nickname") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(model, { model = it.take(60) }, label = { Text("Model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(serial, { serial = it.take(60) }, label = { Text("Serial (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = { onSave(profile.copy(nickname = nickname, model = model, serialNumber = serial).normalized()) },
                    enabled = nickname.isNotBlank() && model.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("SAVE AND SELECT") }
                if (canDelete) OutlinedButton(onClick = { deleteOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("DELETE PROFILE") }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("CANCEL") }
            }
        }
    }
    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            title = { Text("DELETE AIRCRAFT PROFILE?") },
            text = { Text("${profile.nickname} and its saved Garage identity will be removed. Flight and recovery records are retained.") },
            dismissButton = { TextButton(onClick = { deleteOpen = false }) { Text("CANCEL") } },
            confirmButton = { Button(onClick = onDelete) { Text("CONFIRM DELETE") } }
        )
    }
}

private fun PersistedAircraftProfile.lastLocationLabel(): String = when {
    lastLatitudeDeg != null && lastLongitudeDeg != null -> "%.4f, %.4f".format(lastLatitudeDeg, lastLongitudeDeg)
    else -> "Location —"
}

private fun aircraftHealthColor(health: String) = when (health) {
    "READY" -> GsColors.Green
    "WARNING", "CHECKING" -> GsColors.Amber
    "CRITICAL" -> GsColors.Red
    else -> GsColors.Muted
}

@Composable
private fun QuickLaunch(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = GsColors.Panel)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = GsColors.White, fontWeight = FontWeight.Bold)
            Text(subtitle, color = GsColors.Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun GsDroneSilhouette(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val ink = GsColors.Orange
        drawCircle(ink.copy(alpha = .16f), size.minDimension * .42f, c)
        drawRoundRect(ink, topLeft = Offset(c.x - 42f, c.y - 18f), size = Size(84f, 36f), cornerRadius = CornerRadius(15f))
        val arms = listOf(Offset(-72f, -38f), Offset(72f, -38f), Offset(-72f, 38f), Offset(72f, 38f))
        arms.forEach { d ->
            drawLine(ink, c, c + d, 8f)
            drawCircle(ink, 22f, c + d, style = Stroke(5f))
        }
    }
}
