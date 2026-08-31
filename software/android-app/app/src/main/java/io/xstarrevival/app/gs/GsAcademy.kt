package io.xstarrevival.app.gs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class AcademyTopic(val title: String, val detail: String, val status: String)

@Composable
fun GsAcademyScreen() {
    val topics = listOf(
        AcademyTopic("Preflight checklist", "GPS, compass, IMU, RC, video, battery and Home-point checks before takeoff.", "BUILT IN"),
        AcademyTopic("X-Star Premium flight basics", "Flight modes, takeoff, landing, Return-to-Home and safe loss-of-link behavior.", "OFFLINE"),
        AcademyTopic("Battery safety", "Cell balance, storage charge, temperature, cycle history and rebuilt-pack cautions.", "OFFLINE"),
        AcademyTopic("Compass and IMU calibration", "When calibration is appropriate and when repeated calibration can hide another problem.", "GUIDE"),
        AcademyTopic("Waypoint missions", "Build, validate, review, execute, pause and abort autonomous routes safely.", "GUIDE"),
        AcademyTopic("Orbit and Follow", "POI selection, radius, altitude, relative position and target-loss behavior.", "GUIDE"),
        AcademyTopic("Find My X-Star", "Use persisted last-known telemetry and recent flight path after a link loss.", "GUIDE"),
        AcademyTopic("Video-link troubleshooting", "Distinguish telemetry loss from video-only loss and inspect RF/channel health.", "GUIDE"),
        AcademyTopic("Legacy firmware preservation", "Identify installed versions and avoid destructive updates while archival work is ongoing.", "REFERENCE"),
        AcademyTopic("Diagnostic bundle", "Capture local telemetry and protocol diagnostics for reproducible troubleshooting.", "REFERENCE")
    )
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("X-STAR ACADEMY", color = GsColors.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text("Offline operator guides, preservation notes and troubleshooting", color = GsColors.Muted)
        }
        items(topics) { topic ->
            Card(Modifier.fillMaxWidth().clickable { }, colors = CardDefaults.cardColors(containerColor = GsColors.Panel)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(topic.title, color = GsColors.White, fontWeight = FontWeight.Bold)
                        Text(topic.detail, color = GsColors.Muted, fontSize = 11.sp)
                    }
                    Text(topic.status, color = GsColors.Orange, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
        item {
            GsSectionCard("ABOUT THIS BUILD") {
                GsSettingLine("App", "X-Star Ground Station")
                GsSettingLine("Core mode", "Offline-first")
                GsSettingLine("Official bridge", "Receive-only until verified")
                Text("The app never invents unsupported telemetry or silently transmits unverified aircraft commands.", color = GsColors.Muted, fontSize = 11.sp)
            }
        }
    }
}
