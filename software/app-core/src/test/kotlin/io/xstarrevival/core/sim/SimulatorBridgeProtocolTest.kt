package io.xstarrevival.core.sim

import io.xstarrevival.core.model.Severity
import io.xstarrevival.core.model.WarningState
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class SimulatorBridgeProtocolTest {
    @Test
    fun `telemetry envelope is versioned explicitly simulated and escaped`() {
        val state = SimulatorFlightModel.toXStarState(SimulatorSnapshot()).copy(
            warnings = listOf(WarningState("sim-test", Severity.ADVISORY, "Wind \"gust\"\nactive"))
        )
        val json = SimulatorBridgeProtocol.telemetryJson(state, sequence = 42, emittedAtEpochMs = 1_000)

        assertContains(json, "\"protocol\":\"xstar-simulator\"")
        assertContains(json, "\"version\":1")
        assertContains(json, "\"simulated\":true")
        assertContains(json, "\"sequence\":42")
        assertContains(json, "Wind \\\"gust\\\"\\nactive")
        assertContains(json, "\"controller\":")
        assertContains(json, "\"aircraft\":")
        assertContains(json, "\"viewMode\":\"FPV\"")
        assertFalse(json.contains("NaN"))
    }

    @Test
    fun `camera view is carried to visualizers without changing the flight state`() {
        val state = SimulatorFlightModel.toXStarState(SimulatorSnapshot())
        val json = SimulatorBridgeProtocol.telemetryJson(
            state,
            sequence = 7,
            emittedAtEpochMs = 2_000,
            viewMode = SimulatorViewMode.CHASE
        )

        assertContains(json, "\"viewMode\":\"CHASE\"")
        assertContains(json, "\"altitudeM\":0.0")
    }
}
