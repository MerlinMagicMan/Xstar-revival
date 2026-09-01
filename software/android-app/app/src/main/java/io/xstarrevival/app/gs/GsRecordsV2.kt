package io.xstarrevival.app.gs

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.xstarrevival.core.groundstation.GeoPoint
import io.xstarrevival.core.groundstation.RecoveryPoint
import io.xstarrevival.core.model.XStarState
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlin.math.max
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow

enum class GsRecordsTab { FLIGHTS, FIND_AIRCRAFT }

@Composable
fun GsRecordsV2Screen(
    state: XStarState,
    recoveryPoints: List<RecoveryPoint>,
    flightSummaries: List<PersistedFlightSummary>
) {
    var tab by remember { mutableStateOf(GsRecordsTab.FLIGHTS) }
    var selected by remember { mutableStateOf<PersistedFlightSummary?>(null) }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(if (selected == null) "FLIGHT RECORDS" else "FLIGHT DETAIL", color = GsColors.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text("Local-first telemetry history and aircraft recovery", color = GsColors.Muted)
            }
            if (selected != null) {
                OutlinedButton(onClick = { selected = null }) { Text("BACK TO FLIGHTS") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TabButton("FLIGHTS", tab == GsRecordsTab.FLIGHTS) { tab = GsRecordsTab.FLIGHTS }
                    TabButton("FIND MY X-STAR", tab == GsRecordsTab.FIND_AIRCRAFT) { tab = GsRecordsTab.FIND_AIRCRAFT }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        when {
            selected != null -> FlightDetail(selected!!, Modifier.weight(1f))
            tab == GsRecordsTab.FLIGHTS -> FlightListV2(flightSummaries, { selected = it }, Modifier.weight(1f))
            else -> RecoveryDetail(state, recoveryPoints, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick) { Text(label) } else OutlinedButton(onClick = onClick) { Text(label) }
}

@Composable
private fun FlightListV2(records: List<PersistedFlightSummary>, onSelect: (PersistedFlightSummary) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (records.isEmpty()) item {
            Card(colors = CardDefaults.cardColors(containerColor = GsColors.Panel)) {
                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NO FLIGHT RECORDS YET", color = GsColors.White, fontWeight = FontWeight.Bold)
                    Text("Flights are recorded automatically from normalized telemetry.", color = GsColors.Muted)
                }
            }
        }
        items(records) { record ->
            val duration = formatDuration(record.endedAtEpochMs - record.startedAtEpochMs)
            Card(Modifier.fillMaxWidth().clickable { onSelect(record) }, colors = CardDefaults.cardColors(containerColor = GsColors.Panel)) {
                Row(Modifier.fillMaxWidth().padding(17.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(record.startedAtEpochMs)), color = GsColors.White, fontWeight = FontWeight.Bold)
                        Text("$duration · ${record.maximumAltitudeM?.let { "max %.1f m".format(it) } ?: "alt —"} · ${record.maximumSpeedMps?.let { "%.1f m/s".format(it) } ?: "speed —"}", color = GsColors.Muted)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(record.batteryStartPercent?.let { "$it%" } ?: "—", color = GsColors.Green, fontFamily = FontFamily.Monospace)
                        Text(" → ", color = GsColors.Muted)
                        Text(record.batteryEndPercent?.let { "$it%" } ?: "—", color = batteryAccent(record.batteryEndPercent), fontFamily = FontFamily.Monospace)
                        Icon(Icons.Default.ChevronRight, contentDescription = "Open flight detail", tint = GsColors.Orange)
                    }
                }
            }
        }
    }
}

@Composable
private fun FlightDetail(record: PersistedFlightSummary, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val samples = record.samples
    var replayIndex by remember(record.startedAtEpochMs) { mutableIntStateOf(0) }
    var playing by remember(record.startedAtEpochMs) { mutableStateOf(false) }
    val currentSample = samples.getOrNull(replayIndex)
    val positionedSamples = samples.mapIndexedNotNull { index, sample ->
        val latitude = sample.latitudeDeg ?: return@mapIndexedNotNull null
        val longitude = sample.longitudeDeg ?: return@mapIndexedNotNull null
        IndexedValue(index, GeoPoint(latitude, longitude))
    }

    LaunchedEffect(playing, record.startedAtEpochMs) {
        while (playing && replayIndex < samples.lastIndex) {
            val currentTime = samples[replayIndex].timestampEpochMs
            val nextTime = samples[replayIndex + 1].timestampEpochMs
            delay((nextTime - currentTime).coerceIn(100L, 2_000L))
            replayIndex++
        }
        if (replayIndex >= samples.lastIndex) playing = false
    }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(Modifier.weight(.62f).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = GsColors.Panel), shape = RoundedCornerShape(16.dp)) {
            Box(Modifier.fillMaxSize()) {
                val currentPositionIndex = positionedSamples.indexOfLast { it.index <= replayIndex }
                GsOperationalMap(
                    modifier = Modifier.fillMaxSize(),
                    aircraft = positionedSamples.getOrNull(currentPositionIndex)?.value,
                    aircraftHeadingDeg = currentSample?.headingDeg,
                    flightPath = positionedSamples.map { it.value },
                    fitKey = record.startedAtEpochMs,
                    label = "RECORDED TELEMETRY · ${samples.size} SAMPLES"
                )
                Text("RECORDED TELEMETRY PATH · ${samples.size} SAMPLES", Modifier.align(Alignment.TopStart).padding(16.dp), color = GsColors.Muted, fontSize = 10.sp)
                if (positionedSamples.isEmpty()) {
                    Text(
                        if (samples.isEmpty()) "This legacy summary has no replay samples" else "This flight contains no GPS position samples",
                        Modifier.align(Alignment.Center),
                        color = GsColors.Muted
                    )
                }
                Row(Modifier.align(Alignment.BottomCenter).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { playing = false; replayIndex = (replayIndex - 1).coerceAtLeast(0) },
                        enabled = replayIndex > 0
                    ) { Icon(Icons.Default.SkipPrevious, contentDescription = "Previous replay sample") }
                    Button(
                        onClick = {
                            if (replayIndex >= samples.lastIndex) replayIndex = 0
                            playing = !playing
                        },
                        enabled = samples.size > 1
                    ) {
                        Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                        Text(if (playing) "PAUSE" else "REPLAY")
                    }
                    OutlinedButton(
                        onClick = { playing = false; replayIndex = (replayIndex + 1).coerceAtMost(samples.lastIndex) },
                        enabled = replayIndex < samples.lastIndex
                    ) { Icon(Icons.Default.SkipNext, contentDescription = "Next replay sample") }
                }
            }
        }
        Column(Modifier.weight(.38f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GsSectionCard("SUMMARY") {
                GsSettingLine("Started", DateFormat.getDateTimeInstance().format(Date(record.startedAtEpochMs)))
                GsSettingLine("Duration", formatDuration(record.endedAtEpochMs - record.startedAtEpochMs))
                GsSettingLine("Maximum altitude", record.maximumAltitudeM?.let { "%.1f m".format(it) } ?: "—")
                GsSettingLine("Maximum speed", record.maximumSpeedMps?.let { "%.1f m/s".format(it) } ?: "—")
                GsSettingLine("Battery start", record.batteryStartPercent?.let { "$it%" } ?: "—")
                GsSettingLine("Battery end", record.batteryEndPercent?.let { "$it%" } ?: "—")
                GsSettingLine("Replay samples", samples.size.toString())
            }
            GsSectionCard("REPLAY TELEMETRY") {
                GsSettingLine("Elapsed", currentSample?.let { formatDuration(it.timestampEpochMs - record.startedAtEpochMs) } ?: "—")
                GsSettingLine("Altitude", currentSample?.altitudeM?.let { "%.1f m".format(it) } ?: "—")
                GsSettingLine("Ground speed", currentSample?.groundSpeedMps?.let { "%.1f m/s".format(it) } ?: "—")
                GsSettingLine("Heading", currentSample?.headingDeg?.let { "%.0f°".format(it) } ?: "—")
                GsSettingLine("Battery", currentSample?.batteryPercent?.let { "$it%" } ?: "—")
            }
            OutlinedButton(
                onClick = { shareRecordExport(context, "X-Star flight ${record.startedAtEpochMs}", buildFlightExport(record)) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("EXPORT FLIGHT") }
        }
    }
}

@Composable
private fun RecoveryDetail(state: XStarState, recoveryPoints: List<RecoveryPoint>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val last = recoveryPoints.lastOrNull()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(Modifier.weight(.62f).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = GsColors.Panel), shape = RoundedCornerShape(16.dp)) {
            Box(Modifier.fillMaxSize()) {
                val currentAircraft = last?.position ?: state.navigation.latitudeDeg?.let { latitude ->
                    state.navigation.longitudeDeg?.let { longitude -> GeoPoint(latitude, longitude) }
                }
                val home = state.navigation.homeLatitudeDeg?.let { latitude ->
                    state.navigation.homeLongitudeDeg?.let { longitude -> GeoPoint(latitude, longitude) }
                }
                GsOperationalMap(
                    modifier = Modifier.fillMaxSize(),
                    aircraft = currentAircraft,
                    aircraftHeadingDeg = last?.headingDeg ?: state.attitude.yawDeg,
                    home = home,
                    flightPath = recoveryPoints.map { it.position },
                    fitKey = recoveryPoints.size,
                    label = "LAST KNOWN PATH · ${recoveryPoints.size} SAMPLES"
                )
                Text("LAST KNOWN PATH · ${recoveryPoints.size} SAMPLES", Modifier.align(Alignment.TopStart).padding(16.dp), color=GsColors.Muted,fontSize=10.sp)
                if (recoveryPoints.isEmpty()) Text("No recorded aircraft position",Modifier.align(Alignment.Center),color=GsColors.Muted)
            }
        }
        GsSectionCard("LAST KNOWN AIRCRAFT", Modifier.weight(.38f).fillMaxHeight()) {
            val lat = last?.position?.latitudeDeg ?: state.navigation.latitudeDeg
            val lon = last?.position?.longitudeDeg ?: state.navigation.longitudeDeg
            GsSettingLine("Latitude", lat?.let { "%.6f".format(it) } ?: "—")
            GsSettingLine("Longitude", lon?.let { "%.6f".format(it) } ?: "—")
            GsSettingLine("Altitude", (last?.altitudeM ?: state.navigation.altitudeM)?.let { "%.1f m".format(it) } ?: "—")
            GsSettingLine("Heading", (last?.headingDeg ?: state.attitude.yawDeg)?.let { "%.0f°".format(it) } ?: "—")
            GsSettingLine("Speed", (last?.groundSpeedMps ?: state.navigation.groundSpeedMps)?.let { "%.1f m/s".format(it) } ?: "—")
            GsSettingLine("Battery", (last?.batteryPercent ?: state.battery.percent)?.let { "$it%" } ?: "—")
            Spacer(Modifier.height(12.dp))
            Button(
                enabled = lat != null && lon != null,
                onClick = {
                    if (lat != null && lon != null) {
                        val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(X-Star%20Last%20Position)")
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("NAVIGATE TO LAST POSITION") }
            OutlinedButton(
                onClick = { shareRecordExport(context, "X-Star recovery path", buildRecoveryExport(recoveryPoints)) },
                enabled = recoveryPoints.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("EXPORT RECOVERY PATH") }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val seconds=max(0L,ms/1000L); return "%02d:%02d".format(seconds/60L,seconds%60L)
}

internal fun buildFlightExport(record: PersistedFlightSummary): String = buildString {
    appendLine("X-Star Ground Station flight export")
    appendLine("started_epoch_ms,${record.startedAtEpochMs}")
    appendLine("ended_epoch_ms,${record.endedAtEpochMs}")
    appendLine("maximum_altitude_m,${record.maximumAltitudeM ?: ""}")
    appendLine("maximum_speed_mps,${record.maximumSpeedMps ?: ""}")
    appendLine("battery_start_percent,${record.batteryStartPercent ?: ""}")
    appendLine("battery_end_percent,${record.batteryEndPercent ?: ""}")
    appendLine()
    appendLine("timestamp_epoch_ms,latitude_deg,longitude_deg,altitude_m,ground_speed_mps,heading_deg,battery_percent")
    record.samples.forEach { sample ->
        appendLine(listOf(
            sample.timestampEpochMs,
            sample.latitudeDeg ?: "",
            sample.longitudeDeg ?: "",
            sample.altitudeM ?: "",
            sample.groundSpeedMps ?: "",
            sample.headingDeg ?: "",
            sample.batteryPercent ?: ""
        ).joinToString(","))
    }
}

internal fun buildRecoveryExport(points: List<RecoveryPoint>): String = buildString {
    appendLine("X-Star Ground Station recovery path export")
    appendLine("timestamp_epoch_ms,latitude_deg,longitude_deg,altitude_m,ground_speed_mps,heading_deg,battery_percent")
    points.forEach { point ->
        appendLine(listOf(
            point.timestampEpochMs,
            point.position.latitudeDeg,
            point.position.longitudeDeg,
            point.altitudeM ?: "",
            point.groundSpeedMps ?: "",
            point.headingDeg ?: "",
            point.batteryPercent ?: ""
        ).joinToString(","))
    }
}

private fun shareRecordExport(context: android.content.Context, subject: String, body: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Export X-Star data")) }
}
