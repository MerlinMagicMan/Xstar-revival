package io.xstarrevival.app

import io.xstarrevival.app.gs.GsReadiness
import io.xstarrevival.app.gs.readiness
import io.xstarrevival.core.model.BatteryState
import io.xstarrevival.core.model.CellState
import io.xstarrevival.core.model.ConnectionState
import io.xstarrevival.core.model.NavigationState
import io.xstarrevival.core.model.RemoteState
import io.xstarrevival.core.model.XStarState
import kotlin.test.Test
import kotlin.test.assertEquals

class GsReadinessTest {
    @Test
    fun satellitesAloneDoNotMakeAircraftReady() {
        val state = XStarState(
            connection = ConnectionState.Connected("test", "X-Star Premium"),
            navigation = NavigationState(satellites = 12)
        )

        assertEquals(GsReadiness.CHECKING, state.readiness().level)
    }

    @Test
    fun unsafeBatteryOverridesHealthyGps() {
        val state = XStarState(
            connection = ConnectionState.Connected("test", "X-Star Premium"),
            navigation = NavigationState(satellites = 12),
            remote = RemoteState(connected = true),
            battery = BatteryState(
                percent = 8,
                temperatureC = 25.0,
                cells = listOf(CellState(1, 4.1), CellState(2, 4.1))
            )
        )

        assertEquals(GsReadiness.CRITICAL, state.readiness().level)
    }
}
