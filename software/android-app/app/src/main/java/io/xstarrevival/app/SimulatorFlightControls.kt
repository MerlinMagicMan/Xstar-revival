package io.xstarrevival.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.xstarrevival.core.model.XStarState
import io.xstarrevival.core.sim.SimulatorControlInput
import kotlin.math.hypot

@Composable
internal fun SimulatorFlightControls(
    state: XStarState,
    onControlsChanged: (SimulatorControlInput) -> Unit,
    onToggleArm: () -> Unit,
    onTakeOff: () -> Unit,
    onLand: () -> Unit,
    onToggleRecording: () -> Unit
) {
    var controls by remember { mutableStateOf(SimulatorControlInput()) }
    var gimbal by remember { mutableFloatStateOf(0f) }
    val phase = state.aircraft.flightMode ?: "GROUNDED"
    val grounded = phase == "GROUNDED"
    val armed = phase == "ARMED"
    val flying = phase == "FLYING" || phase == "TAKING OFF"

    fun update(next: SimulatorControlInput) {
        controls = next
        onControlsChanged(next)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Virtual controller", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("$phase · local simulation only", style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    "ALT ${state.navigation.altitudeM?.let { "%.1f m".format(it) } ?: "—"}",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                VirtualJoystick(
                    label = "YAW  /  ALTITUDE",
                    modifier = Modifier.weight(1f),
                    onChanged = { x, y -> update(controls.copy(yaw = x.toDouble(), throttle = y.toDouble())) }
                )
                VirtualJoystick(
                    label = "ROLL  /  FORWARD",
                    modifier = Modifier.weight(1f),
                    onChanged = { x, y -> update(controls.copy(roll = x.toDouble(), pitch = y.toDouble())) }
                )
            }

            Text("CAMERA TILT", style = MaterialTheme.typography.labelSmall)
            Slider(
                value = gimbal,
                onValueChange = {
                    gimbal = it
                    update(controls.copy(gimbal = it.toDouble()))
                },
                onValueChangeFinished = {
                    gimbal = 0f
                    update(controls.copy(gimbal = 0.0))
                },
                valueRange = -1f..1f
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onToggleArm, enabled = grounded || armed) {
                    Text(if (armed) "Disarm" else "Arm")
                }
                Button(onClick = onTakeOff, enabled = grounded || armed) { Text("Take off") }
                OutlinedButton(onClick = onLand, enabled = flying) { Text("Land") }
            }
            OutlinedButton(onClick = onToggleRecording) {
                Text(if (state.camera.recording == true) "Stop virtual recording" else "Start virtual recording")
            }
        }
    }
}

@Composable
private fun VirtualJoystick(
    label: String,
    modifier: Modifier = Modifier,
    onChanged: (x: Float, y: Float) -> Unit
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var stick by remember { mutableStateOf(Offset.Zero) }

    fun update(position: Offset) {
        val radius = (minOf(size.width, size.height) / 2f).coerceAtLeast(1f)
        var x = (position.x - size.width / 2f) / radius
        var y = -(position.y - size.height / 2f) / radius
        val magnitude = hypot(x, y)
        if (magnitude > 1f) {
            x /= magnitude
            y /= magnitude
        }
        stick = Offset(x, y)
        onChanged(x, y)
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .onSizeChanged { size = it }
                .background(Color(0xFF10181B), CircleShape)
                .border(1.dp, Color(0xFF8CFFD0).copy(alpha = 0.55f), CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart =(::update),
                        onDragEnd = {
                            stick = Offset.Zero
                            onChanged(0f, 0f)
                        },
                        onDragCancel = {
                            stick = Offset.Zero
                            onChanged(0f, 0f)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            update(change.position)
                        }
                    )
                }
        ) {
            Canvas(Modifier.matchParentSize()) {
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val radius = minOf(this.size.width, this.size.height) * 0.36f
                drawLine(Color(0xFF8CFFD0).copy(alpha = 0.18f), Offset(center.x, 0f), Offset(center.x, this.size.height), 2f)
                drawLine(Color(0xFF8CFFD0).copy(alpha = 0.18f), Offset(0f, center.y), Offset(this.size.width, center.y), 2f)
                drawCircle(Color(0xFF8CFFD0).copy(alpha = 0.9f), radius = 18.dp.toPx(), center = center + stick * radius)
            }
        }
        Text(label, modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelSmall)
    }
}
