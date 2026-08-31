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
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.xstarrevival.core.groundstation.BatteryHealthAnalyzer
import io.xstarrevival.core.model.CellState
import io.xstarrevival.core.model.XStarState
import java.text.DateFormat
import java.util.Date
import kotlin.math.absoluteValue

@Composable
fun GsAircraftScreen(
    state: XStarState,
    batteryProfiles: List<PersistedBatteryProfile> = emptyList(),
    activeBatteryProfileId: String? = null,
    batteryHistory: List<PersistedBatterySample> = emptyList(),
    onSelectBatteryProfile: (String) -> Unit = {},
    onSaveBatteryProfile: (PersistedBatteryProfile) -> Unit = {}
) {
    val cells = if (state.battery.cells.isNotEmpty()) state.battery.cells else (1..4).map { CellState(it, null) }
    val activeProfile = batteryProfiles.firstOrNull { it.id == activeBatteryProfileId }
    val health = BatteryHealthAnalyzer.assess(state.battery, activeProfile?.ratedCapacityMah)
    val cellVoltages = state.battery.cells.mapNotNull { it.voltageV?.takeIf(Double::isFinite) }
    val cellAverage = cellVoltages.takeIf { it.isNotEmpty() }?.average()
    var addingProfile by remember { mutableStateOf(false) }
    var profileName by remember { mutableStateOf("") }
    var profileCapacity by remember(state.battery.designCapacityMah) {
        mutableStateOf((state.battery.designCapacityMah ?: 4_900).toString())
    }
    var profileKind by remember { mutableStateOf("CUSTOM") }
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
                    GsBigMetric(health.healthPercent?.let { "$it%" } ?: "—", "Est. health")
                }
                GsSettingLine("Pack identity", state.battery.packId ?: "Not reported")
                GsSettingLine("Current", state.battery.currentA?.let { "%.2f A".format(it) } ?: "—")
                GsSettingLine("Power", health.powerW?.let { "%.0f W".format(it.absoluteValue) } ?: "—")
                GsSettingLine("Remaining capacity", state.battery.remainingCapacityMah?.let { "$it mAh" } ?: "—")
                GsSettingLine("Estimated remaining flight", health.estimatedRemainingFlightMinutes?.let(::formatBatteryMinutes) ?: "—")
                Spacer(Modifier.height(14.dp))
                cells.forEach { cell ->
                    val voltage = cell.voltageV?.takeIf { it.isFinite() }
                    val deviation = if (voltage != null && cellAverage != null) voltage - cellAverage else null
                    val abnormal = deviation?.absoluteValue?.let { it >= .04 } == true
                    GsSettingLine(
                        "Cell ${cell.index}${if (abnormal) "  !" else ""}",
                        voltage?.let { value ->
                            "%.3f V%s".format(value, deviation?.let { "  (%+.3f)".format(it) }.orEmpty())
                        } ?: "—"
                    )
                }
                GsSettingLine("Highest cell", cellVoltages.maxOrNull()?.let { "%.3f V".format(it) } ?: "—")
                GsSettingLine("Lowest cell", cellVoltages.minOrNull()?.let { "%.3f V".format(it) } ?: "—")
                GsSettingLine("Cell delta", state.battery.cellDeltaV?.let { "%.3f V".format(it) } ?: "—")
                GsSettingLine("Estimated full capacity", state.battery.fullCapacityMah?.let { "$it mAh" } ?: "—")
                GsSettingLine("Design capacity", state.battery.designCapacityMah?.let { "$it mAh" } ?: "—")
                GsSettingLine("BMS firmware", state.battery.firmwareVersion ?: "—")
                GsSettingLine("Status", batteryStatus(state, health))
                if (health.advisories.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    health.advisories.forEach { Text("!  $it", color = GsColors.Amber, fontSize = 12.sp) }
                }
            }
        }
        item {
            GsSectionCard("BATTERY PROFILE") {
                GsSettingLine("Active profile", activeProfile?.name ?: "No profile selected")
                GsSettingLine("Pack type", activeProfile?.kind?.replace('_', ' ') ?: "—")
                GsSettingLine("Rated capacity", activeProfile?.let { "${it.ratedCapacityMah} mAh" } ?: "—")
                if (batteryProfiles.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        batteryProfiles.forEach { profile ->
                            OutlinedButton(
                                onClick = { onSelectBatteryProfile(profile.id) },
                                enabled = profile.id != activeBatteryProfileId,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(profile.name.take(30), fontSize = 10.sp) }
                        }
                    }
                }
                OutlinedButton(onClick = { addingProfile = !addingProfile }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (addingProfile) "CANCEL NEW PROFILE" else "ADD BATTERY PROFILE")
                }
                if (addingProfile) {
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it.take(40) },
                        label = { Text("Profile name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = profileCapacity,
                        onValueChange = { profileCapacity = it.filter(Char::isDigit).take(5) },
                        label = { Text("Rated capacity (mAh)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = { profileKind = nextBatteryProfileKind(profileKind) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("TYPE: ${profileKind.replace('_', ' ')}") }
                    Button(
                        onClick = {
                            val now = System.currentTimeMillis()
                            onSaveBatteryProfile(
                                PersistedBatteryProfile(
                                    id = "manual:$now",
                                    name = profileName,
                                    kind = profileKind,
                                    ratedCapacityMah = profileCapacity.toIntOrNull() ?: 4_900,
                                    telemetryIdentity = state.battery.packId,
                                    createdAtEpochMs = now
                                ).normalized()
                            )
                            profileName = ""
                            addingProfile = false
                        },
                        enabled = profileName.isNotBlank() && (profileCapacity.toIntOrNull() ?: 0) in 500..20_000,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("SAVE AND ACTIVATE") }
                }
                Text("Profiles support original, rebuilt, aftermarket, custom-capacity, and alternate-BMS packs.", color = GsColors.Muted, fontSize = 10.sp)
            }
        }
        item {
            BatteryHistoryCard(batteryHistory)
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

@Composable
private fun BatteryHistoryCard(history: List<PersistedBatterySample>) {
    val newest = history.firstOrNull()
    val oldest = history.lastOrNull()
    GsSectionCard("BATTERY HISTORY") {
        GsSettingLine("Samples", history.size.toString())
        GsSettingLine("Estimated health trend", when {
            newest?.healthPercent != null && oldest?.healthPercent != null -> "${oldest.healthPercent}% → ${newest.healthPercent}%"
            else -> "Insufficient capacity telemetry"
        })
        GsSettingLine("Highest temperature", history.mapNotNull { it.temperatureC }.maxOrNull()?.let { "%.1f°C".format(it) } ?: "—")
        GsSettingLine("Lowest cell", history.flatMap { it.cellVoltagesV }.minOrNull()?.let { "%.3f V".format(it) } ?: "—")
        GsSettingLine("High-temperature events", countBatteryEvents(history) { it.highTemperatureEvent }.toString())
        GsSettingLine("Low-voltage events", countBatteryEvents(history) { it.lowVoltageEvent }.toString())
        GsSettingLine("Cell-imbalance events", countBatteryEvents(history) { it.imbalanceEvent }.toString())
        history.take(8).forEach { sample ->
            val timestamp = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(sample.timestampEpochMs))
            GsSettingLine(timestamp, "${sample.percent?.let { "$it%" } ?: "—"} · ${sample.healthPercent?.let { "$it% health" } ?: "health —"}")
        }
        if (history.isEmpty()) Text("History is stored locally once a battery profile is active.", color = GsColors.Muted, fontSize = 11.sp)
    }
}

private fun formatBatteryMinutes(minutes: Double): String {
    val totalSeconds = (minutes.coerceAtLeast(0.0) * 60.0).toInt()
    return "%d:%02d at current load".format(totalSeconds / 60, totalSeconds % 60)
}

private fun batteryStatus(state: XStarState, health: io.xstarrevival.core.groundstation.BatteryHealthAssessment): String = when {
    state.battery.percent == null -> "TELEMETRY UNAVAILABLE"
    health.advisories.isNotEmpty() -> "REVIEW ${health.band}"
    health.healthPercent == null -> "TELEMETRY PARTIAL"
    else -> "HEALTHY ${health.band}"
}

private fun nextBatteryProfileKind(current: String): String {
    val kinds = listOf("ORIGINAL", "REBUILT", "AFTERMARKET", "CUSTOM", "ALTERNATE_BMS")
    return kinds[(kinds.indexOf(current).takeIf { it >= 0 } ?: 0).plus(1) % kinds.size]
}

internal fun countBatteryEvents(
    historyNewestFirst: List<PersistedBatterySample>,
    active: (PersistedBatterySample) -> Boolean
): Int {
    var previouslyActive = false
    var events = 0
    historyNewestFirst.asReversed().forEach { sample ->
        val isActive = active(sample)
        if (isActive && !previouslyActive) events += 1
        previouslyActive = isActive
    }
    return events
}
