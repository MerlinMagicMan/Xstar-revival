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
        assertFalse(json.contains("NaN"))
    }
}
