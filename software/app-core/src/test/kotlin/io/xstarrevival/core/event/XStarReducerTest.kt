package io.xstarrevival.core.event

import io.xstarrevival.core.model.XStarState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class XStarReducerTest {
    @Test
    fun batteryEventBuildsCellsAndDelta() {
        val next = XStarReducer.reduce(
            XStarState(),
            XStarEvent.BatterySnapshot(
                percent = 80,
                packVoltageV = 16.08,
                cellVoltagesV = listOf(4.020, 4.018, 4.025, 4.017)
            ),
            nowEpochMs = 123L
        )

        assertEquals(80, next.battery.percent)
        assertEquals(4, next.battery.cells.size)
        assertTrue((next.battery.cellDeltaV ?: 1.0) < 0.01)
        assertEquals(123L, next.diagnostics.lastUpdateEpochMs)
    }

    @Test
    fun diagnosticsAreAdditive() {
        var state = XStarState()
        state = XStarReducer.reduce(state, XStarEvent.DiagnosticCounter("frames", 10), 1L)
        state = XStarReducer.reduce(state, XStarEvent.DiagnosticNote("heartbeat detected"), 2L)

        assertEquals(10L, state.diagnostics.counters["frames"])
        assertEquals(listOf("heartbeat detected"), state.diagnostics.notes)
        assertEquals(2L, state.diagnostics.lastUpdateEpochMs)
    }

    @Test
    fun partialOfficialSnapshotsPreservePreviouslyObservedValues() {
        var state = XStarState()
        state = XStarReducer.reduce(
            state,
            XStarEvent.BatterySnapshot(percent = 82, cellVoltagesV = listOf(4.01, 4.00, 4.02, 3.99)),
            1L
        )
        state = XStarReducer.reduce(state, XStarEvent.BatterySnapshot(temperatureC = 29.5), 2L)
        state = XStarReducer.reduce(
            state,
            XStarEvent.NavigationSnapshot(latitudeDeg = 41.0, longitudeDeg = -87.0, altitudeM = 20.0),
            3L
        )
        state = XStarReducer.reduce(state, XStarEvent.NavigationSnapshot(ultrasonicHeightM = 2.2), 4L)

        assertEquals(82, state.battery.percent)
        assertEquals(4, state.battery.cells.size)
        assertEquals(29.5, state.battery.temperatureC)
        assertEquals(41.0, state.navigation.latitudeDeg)
        assertEquals(20.0, state.navigation.altitudeM)
        assertEquals(2.2, state.navigation.ultrasonicHeightM)
    }

}
