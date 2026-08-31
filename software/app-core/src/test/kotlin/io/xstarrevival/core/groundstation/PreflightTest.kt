package io.xstarrevival.core.groundstation

import io.xstarrevival.core.model.AircraftState
import io.xstarrevival.core.model.BatteryState
import io.xstarrevival.core.model.CameraState
import io.xstarrevival.core.model.CellState
import io.xstarrevival.core.model.ConnectionState
import io.xstarrevival.core.model.NavigationState
import io.xstarrevival.core.model.RemoteState
import io.xstarrevival.core.model.XStarState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreflightTest {
    @Test
    fun healthyAircraftIsReady() {
        val state = XStarState(
            connection = ConnectionState.Connected("test", "X-Star Premium"),
            aircraft = AircraftState(armed = false),
            navigation = NavigationState(satellites = 14, homeLatitudeDeg = 35.0, homeLongitudeDeg = -97.0),
            remote = RemoteState(connected = true, signalPercent = 95),
            battery = BatteryState(
                percent = 88,
                temperatureC = 28.0,
                cells = listOf(CellState(1, 4.10), CellState(2, 4.09), CellState(3, 4.10), CellState(4, 4.09))
            ),
            camera = CameraState(connected = true)
        )
        assertTrue(PreflightEvaluator.evaluate(state).readyToFly)
    }

    @Test
    fun lowBatteryBlocksTakeoff() {
        val state = XStarState(
            connection = ConnectionState.Connected("test", "X-Star Premium"),
            navigation = NavigationState(satellites = 12),
            remote = RemoteState(connected = true),
            battery = BatteryState(percent = 8)
        )
        val report = PreflightEvaluator.evaluate(state)
        assertFalse(report.readyToFly)
        assertTrue(report.checks.any { it.id == "battery" && it.level == PreflightLevel.BLOCKER })
    }
}
