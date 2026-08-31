package io.xstarrevival.app.gs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.xstarrevival.core.groundstation.GeoPoint
import io.xstarrevival.core.groundstation.OperationalMapCamera
import io.xstarrevival.core.groundstation.OperationalMapGeometry
import io.xstarrevival.core.groundstation.OperationalMapPixel
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Network-independent operational plot used anywhere aircraft geometry must remain available offline.
 * A future cached basemap can render beneath this layer without changing any flight-control behavior.
 */
@Composable
fun GsOperationalMap(
    modifier: Modifier = Modifier,
    aircraft: GeoPoint? = null,
    aircraftHeadingDeg: Double? = null,
    home: GeoPoint? = null,
    operator: GeoPoint? = null,
    pointOfInterest: GeoPoint? = null,
    flightPath: List<GeoPoint> = emptyList(),
    missionWaypoints: List<GeoPoint> = emptyList(),
    selectedWaypointIndex: Int? = null,
    orbitRadiusM: Double? = null,
    fitKey: Any? = Unit,
    followAircraft: Boolean = false,
    showControls: Boolean = true,
    label: String = "OFFLINE NAV PLOT",
    onMapTap: ((GeoPoint) -> Unit)? = null
) {
    val pointsToFit = remember(aircraft, home, operator, pointOfInterest, flightPath, missionWaypoints) {
        buildList {
            addAll(flightPath)
            addAll(missionWaypoints)
            listOfNotNull(aircraft, home, operator, pointOfInterest).forEach(::add)
        }
    }
    val fallback = aircraft ?: home ?: operator ?: pointOfInterest ?: GeoPoint(0.0, 0.0)
    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    var headingUp by remember { mutableStateOf(false) }
    var camera by remember {
        mutableStateOf(OperationalMapGeometry.fit(pointsToFit, 800.0, 500.0, fallback = fallback))
    }

    LaunchedEffect(fitKey, mapSize) {
        if (mapSize.width > 0 && mapSize.height > 0) {
            camera = OperationalMapGeometry.fit(
                points = pointsToFit,
                widthPx = mapSize.width.toDouble(),
                heightPx = mapSize.height.toDouble(),
                paddingPx = if (showControls) 72.0 else 24.0,
                rotationDeg = if (headingUp) aircraftHeadingDeg ?: 0.0 else 0.0,
                fallback = fallback
            )
        }
    }
    LaunchedEffect(headingUp, aircraftHeadingDeg) {
        camera = camera.copy(rotationDeg = if (headingUp) aircraftHeadingDeg ?: 0.0 else 0.0)
    }
    LaunchedEffect(followAircraft, aircraft) {
        if (followAircraft && aircraft != null) camera = camera.copy(center = aircraft)
    }

    val interactionModifier = Modifier
        .pointerInput(mapSize, onMapTap, camera) {
            if (onMapTap != null) {
                detectTapGestures { offset ->
                    onMapTap(
                        OperationalMapGeometry.unproject(
                            OperationalMapPixel(offset.x.toDouble(), offset.y.toDouble()),
                            camera,
                            mapSize.width.toDouble(),
                            mapSize.height.toDouble()
                        )
                    )
                }
            }
        }
        .pointerInput(mapSize, camera, showControls) {
            if (showControls) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val centerPixel = OperationalMapPixel(
                        mapSize.width / 2.0 - pan.x,
                        mapSize.height / 2.0 - pan.y
                    )
                    camera = camera.copy(
                        center = OperationalMapGeometry.unproject(
                            centerPixel,
                            camera,
                            mapSize.width.toDouble(),
                            mapSize.height.toDouble()
                        ),
                        metersPerPixel = (camera.metersPerPixel / zoom).coerceIn(0.05, 50_000.0)
                    )
                }
            }
        }

    Box(
        modifier
            .background(Color(0xFF10171D), RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(12.dp))
            .onSizeChanged { mapSize = it }
            .then(interactionModifier)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val project: (GeoPoint) -> Offset = { point ->
                OperationalMapGeometry.project(
                    point,
                    camera,
                    size.width.toDouble(),
                    size.height.toDouble()
                ).let { Offset(it.x.toFloat(), it.y.toFloat()) }
            }
            val grid = Color.White.copy(alpha = .055f)
            repeat(11) { index ->
                val x = size.width * index / 10f
                drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f)
            }
            repeat(7) { index ->
                val y = size.height * index / 6f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
            }

            flightPath.map(project).zipWithNext().forEach { (start, end) ->
                drawLine(GsColors.Blue.copy(alpha = .82f), start, end, 4f)
            }
            missionWaypoints.map(project).let { points ->
                points.zipWithNext().forEach { (start, end) -> drawLine(GsColors.Orange, start, end, 4f) }
                points.forEachIndexed { index, point ->
                    drawCircle(
                        if (index == selectedWaypointIndex) Color.White else GsColors.Orange,
                        if (index == selectedWaypointIndex) 13f else 10f,
                        point
                    )
                    drawCircle(GsColors.Panel, 4f, point)
                }
            }

            if (pointOfInterest != null) {
                val center = project(pointOfInterest)
                orbitRadiusM?.let { radius ->
                    drawCircle(
                        GsColors.Orange.copy(alpha = .7f),
                        (radius / camera.metersPerPixel).toFloat(),
                        center,
                        style = Stroke(3f)
                    )
                }
                drawCircle(GsColors.Orange.copy(alpha = .18f), 14f, center)
                drawCircle(GsColors.Orange, 8f, center, style = Stroke(3f))
                drawCircle(GsColors.Orange, 2.5f, center)
            }
            if (home != null) drawHomeMarker(project(home))
            if (operator != null) drawOperatorMarker(project(operator))
            if (aircraft != null) drawAircraftMarker(
                project(aircraft),
                headingDeg = aircraftHeadingDeg ?: 0.0,
                mapRotationDeg = camera.rotationDeg
            )

            val northAngle = Math.toRadians(-camera.rotationDeg)
            val northCenter = Offset(size.width - 28f, 32f)
            val northTip = northCenter + Offset(
                (sin(northAngle) * 15.0).toFloat(),
                (-cos(northAngle) * 15.0).toFloat()
            )
            drawLine(Color.White.copy(alpha = .8f), northCenter, northTip, 3f)
            drawCircle(Color.White.copy(alpha = .5f), 18f, northCenter, style = Stroke(1.5f))
        }

        Column(Modifier.align(Alignment.TopStart).padding(10.dp)) {
            Text(label, color = GsColors.Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(
                "1 px = ${formatMapScale(camera.metersPerPixel)}",
                color = GsColors.Muted,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        if (showControls) {
            Column(
                Modifier.align(Alignment.CenterEnd).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MapButton("+") { camera = camera.copy(metersPerPixel = (camera.metersPerPixel / 1.6).coerceAtLeast(.05)) }
                MapButton("−") { camera = camera.copy(metersPerPixel = (camera.metersPerPixel * 1.6).coerceAtMost(50_000.0)) }
                MapButton(if (headingUp) "HDG" else "N↑") { headingUp = !headingUp }
                if (aircraft != null) MapButton("AC") { camera = camera.copy(center = aircraft) }
                if (home != null) MapButton("H") { camera = camera.copy(center = home) }
            }
        }
        Row(
            Modifier.align(Alignment.BottomStart).padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MapLegendDot(GsColors.Orange, "MISSION / POI")
            MapLegendDot(GsColors.Blue, "TRACK")
        }
    }
}

@Composable
private fun MapButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MapLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.background(color, RoundedCornerShape(99.dp)).padding(3.dp))
        Text(label, color = GsColors.Muted, fontSize = 7.sp)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHomeMarker(center: Offset) {
    val path = Path().apply {
        moveTo(center.x, center.y - 11f)
        lineTo(center.x + 11f, center.y)
        lineTo(center.x + 7f, center.y + 10f)
        lineTo(center.x - 7f, center.y + 10f)
        lineTo(center.x - 11f, center.y)
        close()
    }
    drawPath(path, GsColors.Green.copy(alpha = .25f))
    drawPath(path, GsColors.Green, style = Stroke(2.5f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOperatorMarker(center: Offset) {
    drawCircle(GsColors.Blue.copy(alpha = .2f), 12f, center)
    drawCircle(GsColors.Blue, 7f, center, style = Stroke(2.5f))
    drawLine(GsColors.Blue, center + Offset(-11f, 0f), center + Offset(11f, 0f), 2f)
    drawLine(GsColors.Blue, center + Offset(0f, -11f), center + Offset(0f, 11f), 2f)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAircraftMarker(
    center: Offset,
    headingDeg: Double,
    mapRotationDeg: Double
) {
    val relative = Math.toRadians(headingDeg - mapRotationDeg)
    fun vector(right: Double, forward: Double) = Offset(
        (right * cos(relative) + forward * sin(relative)).toFloat(),
        (right * sin(relative) - forward * cos(relative)).toFloat()
    )
    val path = Path().apply {
        val tip = center + vector(0.0, 16.0)
        val left = center + vector(-9.0, -10.0)
        val tail = center + vector(0.0, -5.0)
        val right = center + vector(9.0, -10.0)
        moveTo(tip.x, tip.y)
        lineTo(left.x, left.y)
        lineTo(tail.x, tail.y)
        lineTo(right.x, right.y)
        close()
    }
    drawCircle(Color.Black.copy(alpha = .45f), 18f, center)
    drawPath(path, Color.White)
    drawPath(path, GsColors.Red, style = Stroke(2f))
}

private fun formatMapScale(metersPerPixel: Double): String = when {
    metersPerPixel >= 1_000.0 -> "${(metersPerPixel / 1_000.0 * 10).roundToInt() / 10.0} km"
    metersPerPixel >= 10.0 -> "${metersPerPixel.roundToInt()} m"
    else -> "${(metersPerPixel * 10).roundToInt() / 10.0} m"
}
