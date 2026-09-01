package io.xstarrevival.app.gs

import android.content.Intent
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.xstarrevival.app.TelemetrySource
import io.xstarrevival.core.groundstation.RecoveryPoint
import io.xstarrevival.core.model.XStarState
import java.text.DateFormat
import java.util.Date
import kotlin.math.max

enum class RecordTab { FLIGHTS, FIND_AIRCRAFT }

@Composable
fun GsRecordsScreen(
    state: XStarState,
    recoveryPoints: List<RecoveryPoint> = emptyList(),
    flightSummaries: List<PersistedFlightSummary> = emptyList()
) {
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
        if (tab == RecordTab.FLIGHTS) FlightList(flightSummaries, Modifier.weight(1f))
        else FindAircraftPanel(state, recoveryPoints, Modifier.weight(1f))
    }
}

@Composable
private fun FlightList(records: List<PersistedFlightSummary>, modifier: Modifier = Modifier) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (records.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = GsColors.Panel)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("NO RECORDED FLIGHTS YET", color = GsColors.White, fontWeight = FontWeight.Bold)
                        Text("A flight record is created automatically when telemetry indicates the aircraft becomes airborne or armed.", color = GsColors.Muted, fontSize = 12.sp)
                    }
                }
            }
        }
        items(records) { record ->
            val durationSeconds = max(0L, (record.endedAtEpochMs - record.startedAtEpochMs) / 1000L)
            val duration = "%02d:%02d".format(durationSeconds / 60L, durationSeconds % 60L)
            val battery = when {
                record.batteryStartPercent != null && record.batteryEndPercent != null -> "${record.batteryStartPercent}→${record.batteryEndPercent}%"
                else -> "battery —"
            }
            Card(colors = CardDefaults.cardColors(containerColor = GsColors.Panel)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(record.startedAtEpochMs)), color = GsColors.White, fontWeight = FontWeight.Bold)
                        Text("$duration · max ${record.maximumAltitudeM?.let { "%.1f m".format(it) } ?: "—"} · ${record.maximumSpeedMps?.let { "%.1f m/s".format(it) } ?: "—"} · $battery", color = GsColors.Muted)
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
fun GsMediaScreen(
    state: XStarState,
    source: TelemetrySource,
    mediaItems: List<PersistedMediaItem>,
    transfers: List<MediaTransferState>,
    onDownload: (Set<String>) -> Unit,
    onDelete: (Set<String>) -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    val context = LocalContext.current
    var filter by remember { mutableStateOf(MediaFilter.ALL) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var preview by remember { mutableStateOf<PersistedMediaItem?>(null) }
    var pendingDelete by remember { mutableStateOf<Set<String>>(emptySet()) }
    val visible = filterMedia(mediaItems, filter)
    val selected = mediaItems.filter { it.id in selectedIds }
    val downloadableIds = selected.filter { it.origin == MediaOrigin.AIRCRAFT }.mapTo(mutableSetOf()) { it.id }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("MEDIA", color = GsColors.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    Text("${mediaItems.count { it.origin == MediaOrigin.AIRCRAFT }} onboard · ${mediaItems.count { it.origin == MediaOrigin.LOCAL }} local", color = GsColors.Muted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("AIRCRAFT ${state.camera.storageRemainingMb?.let(::formatMegabytes) ?: "—"}", color = GsColors.White, fontSize = 11.sp)
                    Text("LOCAL ${formatBytes(context.filesDir.usableSpace)} free", color = GsColors.Muted, fontSize = 10.sp)
                }
            }
        }
        item {
            val capability = when {
                source == TelemetrySource.SIMULATOR -> "Simulator captures are listed as onboard media and can be downloaded locally. Previews and shares represent synthetic capture metadata."
                state.camera.connected == true -> "Live camera telemetry is connected; onboard listing and mutations remain unavailable on the receive-only protocol."
                else -> "Connect the simulator to create media. Live onboard listing degrades safely when unsupported."
            }
            GsSectionCard("MEDIA SOURCE") { Text(capability, color = GsColors.Muted, fontSize = 11.sp) }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MediaFilter.entries.forEach { option ->
                    if (option == filter) Button(onClick = { filter = option }, modifier = Modifier.weight(1f)) { Text(option.name, fontSize = 9.sp) }
                    else OutlinedButton(onClick = { filter = option }, modifier = Modifier.weight(1f)) { Text(option.name, fontSize = 9.sp) }
                }
            }
        }
        if (selectedIds.isNotEmpty()) {
            item {
                GsSectionCard("${selectedIds.size} SELECTED") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onDownload(downloadableIds) }, enabled = downloadableIds.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("DOWNLOAD") }
                        OutlinedButton(onClick = { shareMedia(context, selected) }, modifier = Modifier.weight(1f)) { Text("SHARE") }
                        OutlinedButton(onClick = { pendingDelete = selectedIds }, modifier = Modifier.weight(1f)) { Text("DELETE") }
                        OutlinedButton(onClick = { selectedIds = emptySet() }, modifier = Modifier.weight(1f)) { Text("CLEAR") }
                    }
                }
            }
        }
        if (transfers.isNotEmpty()) {
            item {
                GsSectionCard("TRANSFER QUEUE") {
                    transfers.takeLast(6).forEach { transfer ->
                        GsSettingLine(transfer.fileName, "${transfer.progressPercent}% · ${formatBytes(transfer.bytesPerSecond)}/s")
                        LinearProgressIndicator(
                            progress = { transfer.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        if (visible.isEmpty()) {
            item {
                GsSectionCard("NO MEDIA") {
                    Text("Capture a simulator photo or finish a simulator recording to populate the onboard library.", color = GsColors.Muted)
                }
            }
        }
        items(visible.chunked(3)) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { item ->
                    Card(
                        Modifier.weight(1f).clickable {
                            if (selectedIds.isEmpty()) preview = item
                            else selectedIds = selectedIds.toggle(item.id)
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (item.id in selectedIds) GsColors.Orange.copy(alpha = .22f) else GsColors.Panel2
                        )
                    ) {
                        Column {
                            MediaThumbnail(item, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                            Column(Modifier.padding(10.dp)) {
                                Text(item.fileName, color = GsColors.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text("${item.origin.name} · ${item.kind.name} · ${formatBytes(item.sizeBytes)}", color = GsColors.Muted, fontSize = 9.sp)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(if (item.favorite) "★ FAVORITE" else "☆ FAVORITE", Modifier.clickable { onToggleFavorite(item.id) }, color = GsColors.Amber, fontSize = 9.sp)
                                    Text(if (item.id in selectedIds) "SELECTED" else "SELECT", Modifier.clickable { selectedIds = selectedIds.toggle(item.id) }, color = GsColors.Orange, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }

    preview?.let { item ->
        Dialog(onDismissRequest = { preview = null }) {
            Card(colors = CardDefaults.cardColors(containerColor = GsColors.Panel)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MediaThumbnail(item, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    Text(item.fileName, color = GsColors.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    GsSettingLine("Source", item.origin.name)
                    GsSettingLine("Type", item.kind.name)
                    GsSettingLine("Captured", DateFormat.getDateTimeInstance().format(Date(item.createdAtEpochMs)))
                    GsSettingLine("Size", formatBytes(item.sizeBytes))
                    GsSettingLine("Resolution", item.resolution ?: "Unavailable")
                    GsSettingLine("Duration", item.durationSeconds?.let(::formatDuration) ?: "—")
                    GsSettingLine("Frame rate", item.frameRateFps?.let { "$it fps" } ?: "—")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onDownload(setOf(item.id)) }, enabled = item.origin == MediaOrigin.AIRCRAFT, modifier = Modifier.weight(1f)) { Text("DOWNLOAD") }
                        OutlinedButton(onClick = { shareMedia(context, listOf(item)) }, modifier = Modifier.weight(1f)) { Text("SHARE") }
                        OutlinedButton(onClick = { pendingDelete = setOf(item.id); preview = null }, modifier = Modifier.weight(1f)) { Text("DELETE") }
                    }
                    OutlinedButton(onClick = { preview = null }, modifier = Modifier.fillMaxWidth()) { Text("CLOSE") }
                }
            }
        }
    }

    if (pendingDelete.isNotEmpty()) {
        Dialog(onDismissRequest = { pendingDelete = emptySet() }) {
            Card(colors = CardDefaults.cardColors(containerColor = GsColors.Panel)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("DELETE ${pendingDelete.size} MEDIA ITEM${if (pendingDelete.size == 1) "" else "S"}?", color = GsColors.White, fontWeight = FontWeight.Bold)
                    Text("This removes the selected records from this local library. Simulator onboard records cannot be recovered after deletion.", color = GsColors.Muted, fontSize = 11.sp)
                    Button(onClick = {
                        onDelete(pendingDelete)
                        selectedIds = selectedIds - pendingDelete
                        pendingDelete = emptySet()
                    }, modifier = Modifier.fillMaxWidth()) { Text("CONFIRM DELETE") }
                    OutlinedButton(onClick = { pendingDelete = emptySet() }, modifier = Modifier.fillMaxWidth()) { Text("CANCEL") }
                }
            }
        }
    }
}

@Composable
private fun MediaThumbnail(item: PersistedMediaItem, modifier: Modifier = Modifier) {
    val seed = item.id.hashCode()
    val accent = if (item.kind == MediaKind.PHOTO) GsColors.Blue else GsColors.Orange
    Canvas(modifier.background(GsColors.Ink)) {
        drawRect(accent.copy(alpha = .28f))
        val horizon = size.height * (.45f + ((seed ushr 3) and 7) / 50f)
        drawRect(GsColors.Panel2, topLeft = Offset(0f, horizon), size = androidx.compose.ui.geometry.Size(size.width, size.height - horizon))
        drawCircle(accent.copy(alpha = .85f), size.minDimension * .10f, Offset(size.width * .75f, size.height * .28f))
        val ridge = listOf(
            Offset(0f, size.height * .72f),
            Offset(size.width * .28f, size.height * .42f),
            Offset(size.width * .52f, size.height * .68f),
            Offset(size.width * .72f, size.height * .50f),
            Offset(size.width, size.height * .74f)
        )
        for (index in 0 until ridge.lastIndex) drawLine(GsColors.Faint, ridge[index], ridge[index + 1], strokeWidth = 4f)
        if (item.kind == MediaKind.VIDEO) {
            val previewCenter = center
            drawCircle(Color.Black.copy(alpha = .55f), size.minDimension * .16f, previewCenter)
            drawLine(Color.White, Offset(previewCenter.x - 8f, previewCenter.y - 12f), Offset(previewCenter.x + 12f, previewCenter.y), 5f)
            drawLine(Color.White, Offset(previewCenter.x + 12f, previewCenter.y), Offset(previewCenter.x - 8f, previewCenter.y + 12f), 5f)
        }
    }
}

private fun Set<String>.toggle(id: String): Set<String> = if (id in this) this - id else this + id

private fun formatMegabytes(value: Long): String = if (value >= 1024L) "%.1f GB".format(value / 1024.0) else "$value MB"

private fun formatBytes(value: Long): String = when {
    value >= 1024L * 1024L * 1024L -> "%.1f GB".format(value / (1024.0 * 1024.0 * 1024.0))
    value >= 1024L * 1024L -> "%.1f MB".format(value / (1024.0 * 1024.0))
    value >= 1024L -> "%.1f KB".format(value / 1024.0)
    else -> "$value B"
}

private fun formatDuration(seconds: Double): String {
    val total = seconds.coerceAtLeast(0.0).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}

private fun shareMedia(context: android.content.Context, items: List<PersistedMediaItem>) {
    if (items.isEmpty()) return
    val summary = items.joinToString("\n") { item ->
        "${item.fileName} — ${item.kind.name.lowercase()}, ${formatBytes(item.sizeBytes)}, ${item.resolution ?: "resolution unavailable"}"
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "X-Star media (${items.size})")
        putExtra(Intent.EXTRA_TEXT, summary)
    }
    context.startActivity(Intent.createChooser(intent, "Share X-Star media metadata"))
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
