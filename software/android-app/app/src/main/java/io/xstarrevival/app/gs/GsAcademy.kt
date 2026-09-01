package io.xstarrevival.app.gs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class AcademyTopic(
    val id: String,
    val title: String,
    val summary: String,
    val sections: List<AcademySection>
)

internal data class AcademySection(val heading: String, val body: String)

internal val academyTopics = listOf(
    AcademyTopic("manual", "X-Star Premium manual", "Aircraft layout, startup, flight modes, landing and storage.", listOf(
        AcademySection("Before power-up", "Remove the gimbal guard, inspect propellers and arms, confirm the battery is latched, and place the aircraft on a level surface with the props clear."),
        AcademySection("Startup order", "Power the controller, open the ground station, then power the aircraft. Wait for link, telemetry, Home Point, and preflight checks before arming."),
        AcademySection("Shutdown", "Land and disarm first. Power off the aircraft, then the controller. Allow a warm battery to cool before charging or storage.")
    )),
    AcademyTopic("controller", "Remote controller guide", "Mode 2 controls, calibration, buttons and link recovery.", listOf(
        AcademySection("Mode 2", "The left stick controls throttle and yaw. The right stick controls pitch and roll. Release both sticks to center before arming."),
        AcademySection("Calibration", "Remove propellers, keep the aircraft disarmed, center all controls, then move sticks and the gimbal wheel smoothly through their full travel when prompted."),
        AcademySection("Link recovery", "Keep the aircraft in sight, orient the antennas correctly, move away from interference, and allow failsafe behavior to complete. Do not power-cycle an airborne controller unless the documented recovery procedure requires it.")
    )),
    AcademyTopic("battery", "Battery safety", "Cell balance, temperature, storage charge and rebuilt-pack cautions.", listOf(
        AcademySection("Inspect", "Do not fly a swollen, leaking, impact-damaged, unusually hot, or badly imbalanced pack. Verify every cell and the pack latch before flight."),
        AcademySection("Operate", "Use conservative reserves and land early when voltage sags, cell delta rises, temperature is abnormal, or the remaining-time estimate is uncertain."),
        AcademySection("Store", "Cool the pack in a fire-resistant location and use the charger's storage procedure. Never leave charging batteries unattended."),
        AcademySection("Rebuilt packs", "Treat capacity and state-of-charge estimates as unverified until several gentle cycles agree with measured cell voltage and consumed capacity.")
    )),
    AcademyTopic("compass", "Compass calibration", "When to calibrate, safe setup and interference checks.", listOf(
        AcademySection("Calibrate only when needed", "Calibrate after the app requests it, after relevant hardware work, or when testing confirms a persistent heading error. Repeated calibration can hide magnetic interference."),
        AcademySection("Choose the site", "Move away from vehicles, reinforced concrete, speakers, buried utilities, watches, phones, and other magnetic objects."),
        AcademySection("Verify", "After calibration, confirm the map heading follows the aircraft and that the compass reports no interference before takeoff.")
    )),
    AcademyTopic("imu", "IMU guidance", "Warm-up, level-surface calibration and post-calibration checks.", listOf(
        AcademySection("Warm-up", "Let the aircraft remain motionless while the IMU initializes. Do not arm while attitude or temperature status is unavailable."),
        AcademySection("Calibration", "Remove propellers, use a rigid level surface, keep the aircraft still, and follow every requested orientation without rushing."),
        AcademySection("After calibration", "Restart if directed and verify level attitude, stable accelerometer/gyro status, and a clean preflight result.")
    )),
    AcademyTopic("missions", "Mission and smart-flight guide", "Configure, review, execute, pause and abort automated flight safely.", listOf(
        AcademySection("Configure", "Set every waypoint, altitude, speed, action, finish behavior, lost-link behavior, reserve, and Home Point."),
        AcademySection("Review", "Inspect the route, terrain clearance, distance, duration, battery estimate, GPS health, and all warnings. Never use an automated mode without a clear manual escape plan."),
        AcademySection("Execute", "Maintain visual line of sight. Watch current waypoint, progress, link, GPS, and battery. Pause or abort when the environment differs from the plan.")
    )),
    AcademyTopic("troubleshooting", "Troubleshooting", "Separate telemetry, video, GPS, controller and protocol failures.", listOf(
        AcademySection("Video only", "If telemetry remains healthy, keep flying by instruments and visual line of sight, reduce range, and land. Inspect the Video Link analyzer after landing."),
        AcademySection("RC or telemetry loss", "Allow the configured failsafe to act, keep the takeoff area clear, and watch for automatic reconnection. Use last-known position only after confirming the aircraft is no longer updating."),
        AcademySection("GPS degradation", "Stop autonomous modes, avoid aggressive movement, and land in a clear area if position hold is unreliable."),
        AcademySection("Protocol issue", "Enable Developer Mode only when needed, reproduce safely in the simulator or props-off setup, and export a redacted diagnostic report.")
    )),
    AcademyTopic("firmware", "Firmware preservation notes", "Identify versions and avoid destructive changes during archival work.", listOf(
        AcademySection("Record first", "Capture aircraft, controller, camera, battery/BMS, app, and protocol versions before changing anything."),
        AcademySection("Preserve compatibility", "Do not install unknown packages or mix component versions without a verified recovery path and checksums."),
        AcademySection("Current app policy", "This build reports available firmware metadata but does not perform firmware writes.")
    )),
    AcademyTopic("recovery", "Recovery procedures", "Find My X-Star, last-known path and incident handling.", listOf(
        AcademySection("During loss", "Note the last heading, altitude, battery, wind, and failsafe. Keep the controller powered and avoid leaving the launch point while Return-to-Home may still be active."),
        AcademySection("Search", "Use Find My X-Star to review the locally persisted path and open the final coordinate in a mapping app. Search from the last point along heading and wind direction."),
        AcademySection("After recovery", "Disconnect the battery, photograph damage, quarantine a damaged pack, preserve logs, and inspect the aircraft before another power-up.")
    ))
)

@Composable
fun GsAcademyScreen() {
    var selected by remember { mutableStateOf<AcademyTopic?>(null) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("X-STAR ACADEMY", color = GsColors.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text("Bundled offline operator guides, preservation notes and troubleshooting", color = GsColors.Muted)
        }
        items(academyTopics, key = { it.id }) { topic ->
            Card(onClick = { selected = topic }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = GsColors.Panel)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(topic.title, color = GsColors.White, fontWeight = FontWeight.Bold)
                        Text(topic.summary, color = GsColors.Muted, fontSize = 12.sp)
                    }
                    Text("OPEN", color = GsColors.Orange, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
        item {
            GsSectionCard("ABOUT THIS BUILD") {
                GsSettingLine("App", "X-Star Ground Station")
                GsSettingLine("Academy", "Bundled offline")
                GsSettingLine("Official bridge", "Receive-only until verified")
                Text("The app never invents unsupported telemetry or silently transmits unverified aircraft commands.", color = GsColors.Muted, fontSize = 12.sp)
            }
        }
    }

    selected?.let { topic ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(topic.title.uppercase()) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item { Text(topic.summary, color = GsColors.Muted) }
                    items(topic.sections) { section ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(section.heading.uppercase(), color = GsColors.Orange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(section.body, color = GsColors.White)
                        }
                    }
                    item { Text("Available without an internet connection.", color = GsColors.Green, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("DONE") } }
        )
    }
}
