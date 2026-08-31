package io.xstarrevival.core.mock

import io.xstarrevival.core.XStarPlatform
import io.xstarrevival.core.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

class MockXStarPlatform(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : XStarPlatform {
    override val name: String = "Mock X-Star Premium"

    private val mutableState = MutableStateFlow(XStarState())
    override val state: StateFlow<XStarState> = mutableState.asStateFlow()

    private var ticker: Job? = null
    private var tick = 0L

    override suspend fun connect() {
        mutableState.value = mutableState.value.copy(connection = ConnectionState.Discovering)
        delay(150)
        mutableState.value = mutableState.value.copy(connection = ConnectionState.Connecting("mock transport"))
        delay(150)
        mutableState.value = baselineState()
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(1000)
                tick += 1
                updateTelemetry()
            }
        }
    }

    override suspend fun disconnect() {
        ticker?.cancel()
        ticker = null
        mutableState.value = XStarState(connection = ConnectionState.Disconnected)
    }

    override suspend fun refresh() {
        if (mutableState.value.connection is ConnectionState.Connected) updateTelemetry()
    }

    private fun baselineState() = XStarState(
        connection = ConnectionState.Connected("mock", "X-Star Premium"),
        aircraft = AircraftState(
            productName = "X-Star Premium",
            firmwareVersion = "2.0.12-sim",
            armed = false,
            flightMode = "GPS"
        ),
        battery = BatteryState(
            packId = "MOCK-XSTAR-BATTERY-001",
            percent = 82,
            packVoltageV = 16.12,
            currentA = 1.3,
            temperatureC = 28.4,
            designCapacityMah = 4900,
            fullCapacityMah = 4630,
            remainingCapacityMah = 3795,
            cells = listOf(4.031, 4.027, 4.034, 4.028).mapIndexed { i, v -> CellState(i + 1, v) }
        ),
        navigation = NavigationState(
            satellites = 16,
            gpsFix = "3D FIX",
            altitudeM = 0.0,
            groundSpeedMps = 0.0,
            verticalSpeedMps = 0.0
        ),
        attitude = AttitudeState(rollDeg = 0.2, pitchDeg = -0.4, yawDeg = 183.0),
        remote = RemoteState(connected = true, signalPercent = 96, batteryPercent = 74),
        camera = CameraState(
            connected = true,
            mode = "VIDEO",
            recording = false,
            video = VideoState(receiving = true, codec = "H.264", width = 1920, height = 1080, framesReceived = 1)
        ),
        diagnostics = DiagnosticsState(source = "mock", lastUpdateEpochMs = System.currentTimeMillis())
    )

    private fun updateTelemetry() {
        val current = mutableState.value
        val phase = tick / 8.0
        val batteryPercent = ((current.battery.percent ?: 82) - if (tick % 45L == 0L) 1 else 0).coerceAtLeast(0)
        val nominalCell = 3.75 + batteryPercent / 100.0 * 0.30
        val cells = (0 until 4).map { index ->
            CellState(index + 1, nominalCell + sin(phase + index) * 0.004)
        }
        mutableState.value = current.copy(
            battery = current.battery.copy(
                percent = batteryPercent,
                packVoltageV = cells.sumOf { it.voltageV ?: 0.0 },
                currentA = 1.2 + sin(phase) * 0.2,
                temperatureC = 28.4 + sin(phase / 2.0) * 0.5,
                remainingCapacityMah = (4630 * batteryPercent / 100.0).toInt(),
                cells = cells
            ),
            attitude = current.attitude.copy(
                rollDeg = sin(phase) * 1.2,
                pitchDeg = sin(phase * 0.7) * 0.8,
                yawDeg = (183.0 + tick * 0.05) % 360.0
            ),
            camera = current.camera.copy(
                video = current.camera.video.copy(framesReceived = current.camera.video.framesReceived + 30)
            ),
            diagnostics = current.diagnostics.copy(
                lastUpdateEpochMs = System.currentTimeMillis(),
                counters = current.diagnostics.counters + ("mock_ticks" to tick)
            )
        )
    }
}
