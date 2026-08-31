package io.xstarrevival.app.gs

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class GsSettingsFamily(val title: String, val subtitle: String) {
    FLIGHT("Flight Control", "Limits, RTH, Beginner Mode, ATTI and IOC"),
    REMOTE("Remote Controller", "Stick mode, calibration and response"),
    VIDEO("Video Link", "RF channel selection and diagnostics"),
    BATTERY("Aircraft Battery", "Warnings, reserves and health thresholds"),
    GIMBAL("Gimbal", "Pitch response, smoothing and calibration"),
    GENERAL("General", "Units, aircraft behavior, logging and accessibility")
}

@Composable
fun GsSettingsV2Screen() {
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
                    GsSettingsFamily.REMOTE -> RemoteSettings(settings, update)
                    GsSettingsFamily.VIDEO -> VideoLinkSettings(settings, update)
                    GsSettingsFamily.BATTERY -> BatterySettings(settings, update)
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
private fun RemoteSettings(settings: GsUserSettings, update: (GsUserSettings) -> Unit) {
    SettingsToggle("Stick Mode 2", "Throttle/yaw left; pitch/roll right", settings.controllerMode2) { update(settings.copy(controllerMode2 = it)) }
    SettingsSlider("Stick sensitivity", settings.controllerSensitivity, .1f..1f, "") { update(settings.copy(controllerSensitivity = it)) }
    SettingsSlider("Center dead zone", settings.controllerDeadZone, 0f..0.2f, "") { update(settings.copy(controllerDeadZone = it)) }
    SettingsAction("Controller calibration", "Center sticks and calibrate full travel")
    SettingsAction("Button assignments", "Map supported controller buttons")
}

@Composable
private fun VideoLinkSettings(settings: GsUserSettings, update: (GsUserSettings) -> Unit) {
    SettingsToggle("Automatic channel", "Prefer the least-congested validated channel", settings.videoChannelAutomatic) { update(settings.copy(videoChannelAutomatic = it)) }
    SettingsSlider("Manual channel", settings.videoChannel.toFloat(), 1f..13f, "") { update(settings.copy(videoChannel = it.toInt())) }
    Text("CHANNEL ANALYZER", color = GsColors.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    Row(Modifier.fillMaxWidth().height(96.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
        listOf(.18f,.34f,.22f,.68f,.45f,.29f,.76f,.38f,.26f,.52f,.31f,.61f,.24f).forEachIndexed { index, strength ->
            androidx.compose.foundation.layout.Box(
                Modifier.weight(1f).fillMaxHeight(strength).then(if ((index + 1) == settings.videoChannel) Modifier else Modifier),
                contentAlignment = Alignment.BottomCenter
            ) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().clickable { update(settings.copy(videoChannel = index + 1)) }.padding(horizontal = 1.dp)) {
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(top = 2.dp).let { it })
                }
            }
        }
    }
    SafetyNote("Channel switching while airborne will require an acknowledgement-capable transport and a review/confirm step.")
}

@Composable
private fun BatterySettings(settings: GsUserSettings, update: (GsUserSettings) -> Unit) {
    SettingsSlider("Low battery warning", settings.lowBatteryPercent.toFloat(), 20f..50f, "%") { update(settings.copy(lowBatteryPercent = it.toInt())) }
    SettingsSlider("Critical battery warning", settings.criticalBatteryPercent.toFloat(), 8f..25f, "%") { update(settings.copy(criticalBatteryPercent = it.toInt())) }
    SettingsSlider("Mission reserve", settings.missionReservePercent.toFloat(), 15f..50f, "%") { update(settings.copy(missionReservePercent = it.toInt())) }
    SettingsSlider("Cell-imbalance warning", settings.cellDeltaWarningV, .02f..0.15f, "V") { update(settings.copy(cellDeltaWarningV = it)) }
    SettingsAction("Battery history", "Cycles, health, capacity and temperature events")
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
private fun SettingsAction(title: String, subtitle: String) {
    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { }, colors = CardDefaults.cardColors(containerColor = GsColors.Panel2)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, color = GsColors.White, fontWeight = FontWeight.Medium); Text(subtitle, color = GsColors.Muted, fontSize = 11.sp) }
            Text("›", color = GsColors.Orange, fontSize = 24.sp)
        }
    }
}

@Composable
private fun SafetyNote(text: String) {
    Card(Modifier.fillMaxWidth().padding(top = 12.dp), colors = CardDefaults.cardColors(containerColor = GsColors.Amber.copy(alpha = .10f))) {
        Text(text, Modifier.padding(14.dp), color = GsColors.Amber, fontSize = 11.sp)
    }
}
