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
}
