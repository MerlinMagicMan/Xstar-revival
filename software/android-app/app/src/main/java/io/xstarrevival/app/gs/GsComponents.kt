package io.xstarrevival.app.gs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.xstarrevival.core.model.ConnectionState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings

@Composable
fun GsNavigationRail(page: GsPage, onPage: (GsPage) -> Unit) {
    Column(
        Modifier.width(92.dp).fillMaxHeight().background(Color(0xFF0D1014)).verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("X★", color = GsColors.Orange, fontWeight = FontWeight.Black, fontSize = 22.sp)
        Spacer(Modifier.height(8.dp))
        GsPage.entries.forEach { item ->
            val selected = item == page
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    .heightIn(min = 56.dp)
                    .semantics {
                        contentDescription = "${item.label} screen"
                        role = Role.Tab
                        this.selected = selected
                    }
                    .clickable { onPage(item) }
                    .background(if (selected) GsColors.Orange.copy(alpha = .14f) else Color.Transparent, RoundedCornerShape(12.dp))
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(item.icon(), contentDescription = null, tint = if (selected) GsColors.Orange else GsColors.Muted, modifier = Modifier.size(20.dp))
                Text(item.label, color = if (selected) GsColors.White else GsColors.Muted, fontSize = 10.sp)
            }
        }
    }
}

private fun GsPage.icon(): ImageVector = when (this) {
    GsPage.GARAGE -> Icons.Default.Home
    GsPage.COCKPIT -> Icons.Default.FlightTakeoff
    GsPage.MISSIONS -> Icons.Default.Route
    GsPage.RECORDS -> Icons.Default.History
    GsPage.MEDIA -> Icons.Default.PhotoLibrary
    GsPage.AIRCRAFT -> Icons.Default.AirplanemodeActive
    GsPage.SETTINGS -> Icons.Default.Settings
    GsPage.HELP -> Icons.Default.School
}

@Composable
fun GsConnectionPill(connection: ConnectionState) {
    val (text, color) = when (connection) {
        ConnectionState.Disconnected -> "DISCONNECTED" to GsColors.Muted
        ConnectionState.Discovering -> "SCANNING" to GsColors.Amber
        is ConnectionState.Connecting -> "CONNECTING" to GsColors.Amber
        is ConnectionState.Connected -> "CONNECTED" to GsColors.Green
        is ConnectionState.Failed -> "FAILED" to GsColors.Red
    }
    Text(
        text,
        Modifier.background(color.copy(alpha = .14f), RoundedCornerShape(999.dp))
            .border(1.dp, color.copy(alpha = .55f), RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        color = color,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp
    )
}

@Composable
fun GsSummaryCard(title: String, value: String, detail: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = GsColors.Panel), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = GsColors.Muted, fontSize = 10.sp)
            Text(value, color = GsColors.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = GsColors.Muted, fontSize = 11.sp)
        }
    }
}

@Composable
fun GsSectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = GsColors.Panel), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(title, color = GsColors.Orange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun GsSettingLine(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = GsColors.Muted, modifier = Modifier.weight(1f))
        Text(value, color = GsColors.White, fontFamily = FontFamily.Monospace, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

@Composable
fun GsBigMetric(value: String, label: String) {
    Column {
        Text(value, color = GsColors.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text(label, color = GsColors.Muted, fontSize = 10.sp)
    }
}

@Composable
fun GsDot(color: Color, sizeDp: Int = 8) {
    Box(Modifier.size(sizeDp.dp).background(color, RoundedCornerShape(999.dp)))
}
