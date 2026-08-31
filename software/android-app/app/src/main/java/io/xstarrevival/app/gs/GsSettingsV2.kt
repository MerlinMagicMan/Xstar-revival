package io.xstarrevival.app.gs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import io.xstarrevival.app.TelemetrySource
import io.xstarrevival.core.model.XStarState

enum class GsSettingsFamily(val title: String, val subtitle: String) {
    FLIGHT("Flight Control", "Limits, RTH, Beginner Mode, ATTI and IOC"),
    REMOTE("Remote Controller", "Stick mode, calibration and response"),
    VIDEO("Video Link", "RF channel selection and diagnostics"),
    BATTERY("Aircraft Battery", "Warnings, reserves and health thresholds"),
    GIMBAL("Gimbal", "Pitch response, smoothing and calibration"),
    GENERAL("General", "Units, aircraft behavior, logging and accessibility")
}

@Composable
fun GsSettingsV2Screen(
    state: XStarState,
    source: TelemetrySource,
    onSimulatorVideoLinkChannel: (Boolean, Int?) -> Unit,
    onSimulatorControllerConfiguration: (Int, Double, Double, Double, Map<String, String>, Boolean) -> Unit,
    onSimulatorControllerCalibration: () -> Unit,
    onBatteryHistory: () -> Unit
) {
    val context = LocalContext.current
    val store = remember(context) { GsSettingsStore(context.applicationContext) }
    var selected by remember { mutableStateOf(GsSettingsFamily.FLIGHT) }
    var settings by remember(store) { mutableStateOf(store.load()) }
    val update: (GsUserSettings) -> Unit = { next ->
        settings = next.normalized()
    }
    LaunchedEffect(settings) {
        delay(250L)
        store.save(settings)
    }
    Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.width(290.dp).fillMaxHeight()) {
            Text("SETTINGS", color = GsColors.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text("Starlink-compatible organization", color = GsColors.Muted)
            Spacer(Modifier.height(16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(GsSettingsFamily.entries) { family ->
                    Card(
                        Modifier.fillMaxWidth().clickable { selected = family },
                        colors = CardDefaults.cardColors(containerColor = if (selected == family) GsColors.Orange.copy(alpha = .16f) else GsColors.Panel),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(family.title, color = if (selected == family) GsColors.Orange else GsColors.White, fontWeight = FontWeight.Bold)
                            Text(family.subtitle, color = GsColors.Muted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        Card(Modifier.weight(1f).fillMaxHeight(), colors = CardDefaults.cardColors(containerColor = GsColors.Panel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxSize().padding(22.dp)) {
                Text(selected.title.uppercase(), color = GsColors.Orange, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                when (selected) {
                    GsSettingsFamily.FLIGHT -> FlightControlSettings(settings, update)
                    GsSettingsFamily.REMOTE -> RemoteSettings(
                        settings,
                        update,
                        state,
                        source,
                        onSimulatorControllerConfiguration,
                        onSimulatorControllerCalibration
                    )
                    GsSettingsFamily.VIDEO -> VideoLinkSettings(
                        settings,
                        update,
                        state,
                        source,
                        onSimulatorVideoLinkChannel
                    )
                    GsSettingsFamily.BATTERY -> BatterySettings(settings, update, onBatteryHistory)
                    GsSettingsFamily.GIMBAL -> GimbalSettings(settings, update)
                    GsSettingsFamily.GENERAL -> GeneralSettings(settings, update)
                }
            }
        }
    }
}

@Composable
private fun FlightControlSettings(settings: GsUserSettings, update: (GsUserSettings) -> Unit) {
    SettingsToggle("Beginner Mode", "Restricts speed, altitude and radius", settings.beginnerMode) { update(settings.copy(beginnerMode = it)) }
    SettingsSlider("Maximum altitude", settings.maximumAltitudeM, 30f..500f, "m") { update(settings.copy(maximumAltitudeM = it)) }
    SettingsSlider("Maximum distance", settings.maximumDistanceM, 50f..3000f, "m") { update(settings.copy(maximumDistanceM = it)) }
    SettingsSlider("Return-to-Home altitude", settings.rthAltitudeM, 20f..150f, "m") { update(settings.copy(rthAltitudeM = it)) }
    SettingsToggle("Allow ATTI mode", "Enable only after pilot understands GPS-free flight behavior", settings.allowAttiMode) { update(settings.copy(allowAttiMode = it)) }
    SettingsToggle("IOC / intelligent orientation", "Exposes Course Lock and Home Lock when supported", settings.iocEnabled) { update(settings.copy(iocEnabled = it)) }
    SafetyNote("Live parameter writes remain disabled until each Autel command and acknowledgement path is verified. These controls are the production UI contract, not an unsafe guessed transmitter.")
}

@Composable
private fun RemoteSettings(
    settings: GsUserSettings,
    update: (GsUserSettings) -> Unit,
    state: XStarState,
    source: TelemetrySource,
    onSimulatorConfiguration: (Int, Double, Double, Double, Map<String, String>, Boolean) -> Unit,
    onSimulatorCalibration: () -> Unit
) {
    val canCommand = source == TelemetrySource.SIMULATOR
    val grounded = state.aircraft.armed != true && (state.navigation.altitudeM ?: 1.0) <= .2
    SettingsToggle("Stick Mode 2", "Throttle/yaw left; pitch/roll right", settings.controllerMode2) { update(settings.copy(controllerMode2 = it)) }
    SettingsSlider("Stick sensitivity", settings.controllerSensitivity, .1f..1f, "") { update(settings.copy(controllerSensitivity = it)) }
    SettingsSlider("Center dead zone", settings.controllerDeadZone, 0f..0.2f, "") { update(settings.copy(controllerDeadZone = it)) }
    SettingsSlider("Stick expo", settings.controllerExpo, 0f..1f, "") { update(settings.copy(controllerExpo = it)) }
    SettingsToggle("Reverse gimbal wheel", "Invert the camera pitch wheel direction", settings.controllerGimbalWheelReversed) {
        update(settings.copy(controllerGimbalWheelReversed = it))
    }
    SettingsAction("C1 assignment", settings.controllerC1Action.replace('_', ' ')) {
        update(settings.copy(controllerC1Action = nextControllerAction(settings.controllerC1Action)))
    }
    SettingsAction("C2 assignment", settings.controllerC2Action.replace('_', ' ')) {
        update(settings.copy(controllerC2Action = nextControllerAction(settings.controllerC2Action)))
    }
    Button(
        onClick = {
            onSimulatorConfiguration(
                if (settings.controllerMode2) 2 else 1,
                settings.controllerSensitivity.toDouble(),
                settings.controllerDeadZone.toDouble(),
                settings.controllerExpo.toDouble(),
                mapOf("C1" to settings.controllerC1Action, "C2" to settings.controllerC2Action),
                settings.controllerGimbalWheelReversed
            )
        },
        enabled = canCommand,
        modifier = Modifier.fillMaxWidth()
    ) { Text("APPLY CONTROLLER PROFILE") }
    SettingsAction(
        "Controller calibration",
        "Center sticks and calibrate full travel",
        enabled = canCommand && grounded,
        onClick = onSimulatorCalibration
    )
    GsSettingLine("Connection", state.remote.connected?.let { if (it) "Connected" else "Disconnected" } ?: "Unknown")
    GsSettingLine("Controller battery", state.remote.batteryPercent?.let { "$it%" } ?: "Unavailable")
    GsSettingLine("Signal strength", state.remote.signalPercent?.let { "$it%" } ?: "Unavailable")
    GsSettingLine("Firmware", state.remote.firmwareVersion ?: "Unavailable")
    GsSettingLine("Calibration", state.remote.calibrated?.let { if (it) "Calibrated" else "Required" } ?: "Unknown")
    GsSettingLine("Active stick mode", state.remote.stickMode?.let { "Mode $it" } ?: "Unavailable")
    GsSettingLine(
        "Stick input T/Y/P/R",
        listOf(state.remote.throttleInput, state.remote.yawInput, state.remote.pitchInput, state.remote.rollInput)
            .takeIf { values -> values.all { it != null } }
            ?.joinToString(" / ") { "%.2f".format(it) }
            ?: "Unavailable"
    )
    GsSettingLine("Gimbal wheel", state.remote.gimbalWheelInput?.let { "%.2f".format(it) } ?: "Unavailable")
    if (!canCommand) Text("Controller writes remain disabled for receive-only hardware sources.", color = GsColors.Muted, fontSize = 10.sp)
    if (!grounded) Text("Controller calibration requires the aircraft landed and disarmed.", color = GsColors.Amber, fontSize = 10.sp)
}

@Composable
private fun VideoLinkSettings(
    settings: GsUserSettings,
    update: (GsUserSettings) -> Unit,
    state: XStarState,
    source: TelemetrySource,
    onSimulatorVideoLinkChannel: (Boolean, Int?) -> Unit
) {
    val canCommand = source == TelemetrySource.SIMULATOR
    val airborne = state.aircraft.armed == true && (state.navigation.altitudeM ?: 0.0) > .2
    SettingsToggle("Automatic channel", "Prefer the least-congested validated channel", settings.videoChannelAutomatic) {
        if (!it && airborne) return@SettingsToggle
        update(settings.copy(videoChannelAutomatic = it))
        if (canCommand) onSimulatorVideoLinkChannel(it, if (it) null else settings.videoChannel)
    }
    SettingsSlider("Manual channel", settings.videoChannel.toFloat(), 1f..13f, "") { update(settings.copy(videoChannel = it.toInt())) }
    OutlinedButton(
        onClick = { onSimulatorVideoLinkChannel(false, settings.videoChannel) },
        enabled = canCommand && !settings.videoChannelAutomatic && !airborne,
        modifier = Modifier.fillMaxWidth()
    ) { Text("APPLY CHANNEL ${settings.videoChannel}") }
    GsSettingLine("Active mode", state.imageLink.automaticChannel?.let { if (it) "AUTO" else "MANUAL" } ?: "Unavailable")
    GsSettingLine("Active channel", state.imageLink.channel?.toString() ?: "Unavailable")
    GsSettingLine("RF frequency", state.imageLink.rfFrequencyHz?.let { "%.3f MHz".format(it / 1_000_000.0) } ?: "Unavailable")
    GsSettingLine("Signal", state.imageLink.rfSignalValue?.let { "$it%" } ?: "Unavailable")
    GsSettingLine("Interference", state.imageLink.interferencePercent?.let { "$it%" } ?: "Unavailable")
    GsSettingLine("Packet loss / latency", when {
        state.imageLink.packetLossPercent != null && state.imageLink.latencyMs != null ->
            "%.1f%% / %d ms".format(state.imageLink.packetLossPercent, state.imageLink.latencyMs)
        else -> "Unavailable"
    })
    GsSettingLine("Bandwidth", state.imageLink.bandwidthMbps?.let { "%.1f Mbps".format(it) } ?: "Unavailable")
    Text("CHANNEL ANALYZER", color = GsColors.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    val strengths = state.imageLink.channelStrengths
    Row(Modifier.fillMaxWidth().height(96.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
        (1..13).forEach { channel ->
            val strength = strengths.getOrNull(channel - 1)?.coerceIn(0, 100) ?: 0
            val selected = channel == state.imageLink.channel
            androidx.compose.foundation.layout.Box(
                Modifier.weight(1f).fillMaxHeight((strength / 100f).coerceAtLeast(.04f))
                    .background(if (selected) GsColors.Orange else GsColors.Blue.copy(alpha = .72f), RoundedCornerShape(3.dp))
                    .then(if (channel == settings.videoChannel) Modifier.border(1.dp, Color.White, RoundedCornerShape(3.dp)) else Modifier),
                contentAlignment = Alignment.BottomCenter
            ) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().clickable { update(settings.copy(videoChannel = channel)) }.padding(horizontal = 1.dp)) {
                    Text(channel.toString(), Modifier.align(Alignment.BottomCenter), color = Color.White, fontSize = 7.sp)
                }
            }
        }
    }
    if (strengths.isEmpty()) Text("Per-channel telemetry is unavailable for this source.", color = GsColors.Muted, fontSize = 10.sp)
    if (!canCommand) Text("Channel writes remain disabled for receive-only hardware sources.", color = GsColors.Muted, fontSize = 10.sp)
    if (airborne) Text("Manual channel changes are locked while airborne.", color = GsColors.Amber, fontSize = 10.sp)
    SafetyNote("Channel switching while airborne will require an acknowledgement-capable transport and a review/confirm step.")
}

@Composable
private fun BatterySettings(settings: GsUserSettings, update: (GsUserSettings) -> Unit, onBatteryHistory: () -> Unit) {
    SettingsSlider("Low battery warning", settings.lowBatteryPercent.toFloat(), 20f..50f, "%") { update(settings.copy(lowBatteryPercent = it.toInt())) }
    SettingsSlider("Critical battery warning", settings.criticalBatteryPercent.toFloat(), 8f..25f, "%") { update(settings.copy(criticalBatteryPercent = it.toInt())) }
    SettingsSlider("Mission reserve", settings.missionReservePercent.toFloat(), 15f..50f, "%") { update(settings.copy(missionReservePercent = it.toInt())) }
    SettingsSlider("Cell-imbalance warning", settings.cellDeltaWarningV, .02f..0.15f, "V") { update(settings.copy(cellDeltaWarningV = it)) }
    SettingsAction("Battery history", "Cycles, health, capacity and temperature events", onClick = onBatteryHistory)
}

@Composable
private fun GimbalSettings(settings: GsUserSettings, update: (GsUserSettings) -> Unit) {
    SettingsSlider("Pitch speed", settings.gimbalPitchSpeed, .1f..1f, "") { update(settings.copy(gimbalPitchSpeed = it)) }
    SettingsSlider("Smoothing", settings.gimbalSmoothing, 0f..1f, "") { update(settings.copy(gimbalSmoothing = it)) }
    SettingsAction("Recenter gimbal", "Return camera to neutral forward view")
    SettingsAction("Gimbal calibration", "Run calibration after aircraft is level and stationary")
}

@Composable
private fun GeneralSettings(settings: GsUserSettings, update: (GsUserSettings) -> Unit) {
    SettingsToggle("Metric units", "Meters, m/s and Celsius", settings.metricUnits) { update(settings.copy(metricUnits = it)) }
    SettingsToggle("High-visibility cockpit", "Increase HUD opacity and outdoor legibility", settings.highVisibility) { update(settings.copy(highVisibility = it)) }
    SettingsToggle("Audible safety alerts", "Battery, link loss, RTH and critical states", settings.audibleAlerts) { update(settings.copy(audibleAlerts = it)) }
    SettingsToggle("Haptic feedback", "Confirm critical actions and warnings", settings.haptics) { update(settings.copy(haptics = it)) }
    SettingsToggle("Heading-up maps", "Rotate operational maps to aircraft heading", settings.mapHeadingUp) { update(settings.copy(mapHeadingUp = it)) }
    SettingsToggle("Local telemetry logs", "Store diagnostic data only on this device", settings.localLogs) { update(settings.copy(localLogs = it)) }
    SettingsToggle("Developer mode", "Expose protocol diagnostics and packet tools", settings.developerMode) { update(settings.copy(developerMode = it)) }
    SettingsAction("Firmware information", "Aircraft, controller, camera and battery versions")
    SettingsAction("Export diagnostics", "Create a support bundle without transmitting it automatically")
}

@Composable
private fun SettingsToggle(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, color = GsColors.White, fontWeight = FontWeight.Medium); Text(subtitle, color = GsColors.Muted, fontSize = 11.sp) }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SettingsSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String, onValue: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = GsColors.White)
            Text(if (unit == "V") "%.3f %s".format(value, unit) else "%.1f%s".format(value, if (unit.isBlank()) "" else " $unit"), color = GsColors.Muted)
        }
        Slider(value = value, onValueChange = onValue, valueRange = range)
    }
}

@Composable
private fun SettingsAction(title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit = {}) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = GsColors.Panel2.copy(alpha = if (enabled) 1f else .45f))
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, color = GsColors.White, fontWeight = FontWeight.Medium); Text(subtitle, color = GsColors.Muted, fontSize = 11.sp) }
            Text("›", color = GsColors.Orange, fontSize = 24.sp)
        }
    }
}

private fun nextControllerAction(current: String): String {
    val actions = listOf("NONE", "TAKE_PHOTO", "RECORD", "RECENTER_GIMBAL", "MAP")
    return actions[(actions.indexOf(current).takeIf { it >= 0 } ?: 0).plus(1) % actions.size]
}

@Composable
private fun SafetyNote(text: String) {
    Card(Modifier.fillMaxWidth().padding(top = 12.dp), colors = CardDefaults.cardColors(containerColor = GsColors.Amber.copy(alpha = .10f))) {
        Text(text, Modifier.padding(14.dp), color = GsColors.Amber, fontSize = 11.sp)
    }
}
