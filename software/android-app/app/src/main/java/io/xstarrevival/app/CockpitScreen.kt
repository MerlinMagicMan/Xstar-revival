package io.xstarrevival.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.xstarrevival.core.model.ConnectionState
import io.xstarrevival.core.model.XStarState
import io.xstarrevival.core.replay.CaptureReplayState
import io.xstarrevival.core.replay.CaptureReplayStatus
import io.xstarrevival.core.video.H264VideoFrame
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow

private val HudGreen = Color(0xFF8CFFD0)
private val HudAmber = Color(0xFFFFD166)
private val HudRed = Color(0xFFFF6B6B)
private val ViewportBlack = Color(0xFF05090B)

@Composable
fun CockpitScreen(
    state: XStarState,
    source: TelemetrySource,
    replayState: CaptureReplayState,
    heartbeat: HeartbeatUiState,
    liveVideoFrames: Flow<H264VideoFrame>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CockpitViewport(state, source, replayState, heartbeat, liveVideoFrames)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CockpitStatusCard(
                title = "LINK",
                value = when (state.connection) {
                    is ConnectionState.Connected -> if (heartbeat.stale) "STALE" else "OK"
                    else -> "OFFLINE"
                },
                modifier = Modifier.weight(1f)
            )
            CockpitStatusCard(
                title = "GPS",
                value = state.navigation.gpsFix ?: "—",
                modifier = Modifier.weight(1f)
            )
            CockpitStatusCard(
                title = "SOURCE",
                value = when (source) {
                    TelemetrySource.MOCK -> "MOCK"
                    TelemetrySource.MAVLINK_REPLAY -> "REPLAY"
                    TelemetrySource.OFFICIAL_AUTEL -> "LIVE"
                },
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            if (source == TelemetrySource.OFFICIAL_AUTEL)
                "Live mode is receive-only: official SDK telemetry and camera frames can enter this view, but no flight-control commands exist."
            else
                "The HUD is driven by normalized telemetry. Replay video is synthetic and clearly labeled.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun CockpitViewport(
    state: XStarState,
    source: TelemetrySource,
    replayState: CaptureReplayState,
    heartbeat: HeartbeatUiState,
    liveVideoFrames: Flow<H264VideoFrame>
) {
    var videoReplay by remember { mutableStateOf(VideoReplayUiState()) }
    var liveVideo by remember { mutableStateOf(LiveVideoUiState()) }
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(shape)
            .background(ViewportBlack)
            .border(1.dp, HudGreen.copy(alpha = 0.45f), shape)
    ) {
        when (source) {
            TelemetrySource.MAVLINK_REPLAY -> H264ReplayVideo(
                    modifier = Modifier.fillMaxSize(),
                    onStateChanged = { videoReplay = it }
                )
            TelemetrySource.OFFICIAL_AUTEL -> H264LiveVideo(
                    frames = liveVideoFrames,
                    modifier = Modifier.fillMaxSize(),
                    onStateChanged = { liveVideo = it }
                )
            TelemetrySource.MOCK -> ArtificialHorizon(
                    rollDeg = state.attitude.rollDeg ?: 0.0,
                    pitchDeg = state.attitude.pitchDeg ?: 0.0,
                    modifier = Modifier.fillMaxSize()
                )
        }

        HeadingTape(
            yawDeg = state.attitude.yawDeg,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
        )

        HudReadout(
            label = "SPD",
            value = state.navigation.groundSpeedMps?.let { "%.1f".format(it) } ?: "—",
            unit = "m/s",
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp)
        )
        HudReadout(
            label = "ALT",
            value = state.navigation.altitudeM?.let { "%.1f".format(it) } ?: "—",
            unit = "m",
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp),
            alignEnd = true
        )

        FlightReticle(modifier = Modifier.align(Alignment.Center).size(92.dp))

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HudLabel(
                if (state.connection is ConnectionState.Connected) "LINK" else "NO LINK",
                if (state.connection is ConnectionState.Connected && !heartbeat.stale) HudGreen else HudRed
            )
            HudLabel("GPS ${state.navigation.satellites ?: "—"}", HudGreen)
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HudLabel(
                "US ${state.navigation.ultrasonicHeightM?.let { "%.1fm".format(it) } ?: state.navigation.ultrasonicHeightRaw?.let { "%.2f?".format(it) } ?: "—"}",
                if (state.navigation.ultrasonicHeightM == null) HudAmber else HudGreen
            )
            HudLabel("BAT ${state.battery.percent?.let { "$it%" } ?: "—"}", batteryColor(state.battery.percent))
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                when (source) {
                    TelemetrySource.MAVLINK_REPLAY ->
                        "REPLAY ${replayState.status.name} · ${(replayState.progress * 100).roundToInt()}%"
                    TelemetrySource.OFFICIAL_AUTEL -> "LIVE X-STAR · RECEIVE ONLY"
                    TelemetrySource.MOCK -> "MOCK TELEMETRY"
                },
                color = HudGreen,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                when {
                    source == TelemetrySource.MOCK -> "SYNTHETIC VIEW · NO CAMERA FRAMES"
                    source == TelemetrySource.OFFICIAL_AUTEL && liveVideo.status == LiveVideoStatus.ERROR ->
                        "LIVE AVC ERROR · ${liveVideo.error ?: "UNKNOWN"}"
                    source == TelemetrySource.OFFICIAL_AUTEL && liveVideo.status == LiveVideoStatus.PLAYING ->
                        "LIVE H.264 · ${liveVideo.framesRendered} RENDERED · ${liveVideo.framesDropped} DROPPED"
                    source == TelemetrySource.OFFICIAL_AUTEL -> "LIVE H.264 · WAITING FOR KEYFRAME"
                    videoReplay.status == VideoReplayStatus.ERROR ->
                        "AVC DECODER ERROR · ${videoReplay.error ?: "UNKNOWN"}"
                    videoReplay.status == VideoReplayStatus.PLAYING ->
                        "SYNTHETIC H.264 · ${videoReplay.framesRendered}/${videoReplay.frameCount} · LOOP ${videoReplay.loopCount}"
                    else -> "H.264 REPLAY · WAITING FOR DECODER"
                },
                color = if (
                    videoReplay.status == VideoReplayStatus.ERROR || liveVideo.status == LiveVideoStatus.ERROR
                ) HudRed else HudAmber,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun ArtificialHorizon(rollDeg: Double, pitchDeg: Double, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val pitchOffset = (pitchDeg.coerceIn(-30.0, 30.0) / 30.0 * size.height * 0.45).toFloat()
        rotate(degrees = -rollDeg.toFloat(), pivot = center) {
            val horizonY = center.y + pitchOffset
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF153B5C), Color(0xFF4F86A8)),
                    startY = -size.height,
                    endY = horizonY
                ),
                topLeft = Offset(-size.width, -size.height),
                size = Size(size.width * 3f, horizonY + size.height)
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF48523A), Color(0xFF171B17)),
                    startY = horizonY,
                    endY = size.height * 2f
                ),
                topLeft = Offset(-size.width, horizonY),
                size = Size(size.width * 3f, size.height * 2f)
            )
            drawLine(HudGreen.copy(alpha = 0.9f), Offset(-size.width, horizonY), Offset(size.width * 2f, horizonY), 2f)

            (-20..20 step 5).filter { it != 0 }.forEach { degrees ->
                val y = horizonY - degrees / 30f * size.height * 0.45f
                val halfWidth = if (degrees % 10 == 0) size.width * 0.10f else size.width * 0.06f
                drawLine(
                    color = HudGreen.copy(alpha = 0.7f),
                    start = Offset(center.x - halfWidth, y),
                    end = Offset(center.x + halfWidth, y),
                    strokeWidth = 1.5f
                )
            }
        }

        val grid = HudGreen.copy(alpha = 0.08f)
        repeat(8) { index ->
            val x = size.width * index / 7f
            drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f)
        }
        repeat(5) { index ->
            val y = size.height * index / 4f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
        }
    }
}

@Composable
private fun FlightReticle(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val gap = size.width * 0.12f
        val arm = size.width * 0.30f
        drawCircle(HudGreen, radius = 3f, center = center)
        drawLine(HudGreen, Offset(center.x - arm, center.y), Offset(center.x - gap, center.y), 3f)
        drawLine(HudGreen, Offset(center.x + gap, center.y), Offset(center.x + arm, center.y), 3f)
        drawLine(HudGreen, Offset(center.x - arm, center.y), Offset(center.x - arm, center.y + gap), 3f)
        drawLine(HudGreen, Offset(center.x + arm, center.y), Offset(center.x + arm, center.y + gap), 3f)
    }
}

@Composable
private fun HeadingTape(yawDeg: Double?, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("HDG", color = HudGreen.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
        Text(
            yawDeg?.let { "%03d°".format(((it % 360 + 360) % 360).roundToInt()) } ?: "—",
            color = HudGreen,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun HudReadout(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false
) {
    Column(modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(label, color = HudGreen.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
        Text(
            value,
            color = HudGreen,
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        Text(unit, color = HudGreen.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun HudLabel(value: String, color: Color) {
    Text(
        value,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun CockpitStatusCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
    }
}

private fun batteryColor(percent: Int?): Color = when {
    percent == null -> HudAmber
    percent <= 20 -> HudRed
    percent <= 40 -> HudAmber
    else -> HudGreen
}
