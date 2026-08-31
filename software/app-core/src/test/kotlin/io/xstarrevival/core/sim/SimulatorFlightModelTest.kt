package io.xstarrevival.core.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimulatorFlightModelTest {
    @Test
    fun `grounded aircraft ignores flight sticks`() {
        val result = SimulatorFlightModel.step(
            SimulatorSnapshot(),
            SimulatorControlInput(throttle = 1.0, yaw = 1.0, pitch = 1.0, roll = 1.0),
            0.1
        )

        assertEquals(SimulatorFlightPhase.GROUNDED, result.phase)
        assertEquals(0.0, result.altitudeM)
        assertEquals(0.0, result.groundSpeedMps)
        assertEquals(0.0, result.yawDeg)
    }

    @Test
    fun `takeoff climbs to a deterministic hover`() {
        var state = SimulatorFlightModel.takeOff(SimulatorSnapshot())
        repeat(20) { state = SimulatorFlightModel.step(state, SimulatorControlInput(), 0.1) }

        assertEquals(SimulatorFlightPhase.FLYING, state.phase)
        assertEquals(2.0, state.altitudeM)
        assertEquals(0.0, state.verticalSpeedMps)
        assertTrue(SimulatorFlightModel.toXStarState(state).aircraft.armed == true)
    }

    @Test
    fun `flying controls update normalized telemetry`() {
        var state = SimulatorSnapshot(phase = SimulatorFlightPhase.FLYING, altitudeM = 2.0, yawDeg = 350.0)
        repeat(10) {
            state = SimulatorFlightModel.step(
                state,
                SimulatorControlInput(throttle = 0.5, yaw = 1.0, pitch = 0.75, roll = -0.5),
                0.1
            )
        }
        val telemetry = SimulatorFlightModel.toXStarState(state)

        assertTrue(state.altitudeM > 3.0)
        assertTrue(state.yawDeg in 70.0..90.0)
        assertTrue(state.groundSpeedMps > 0.0)
        assertTrue((telemetry.navigation.latitudeDeg ?: 0.0) != 41.8781)
        assertEquals("SIM-XSTAR-BATTERY-001", telemetry.battery.packId)
        assertEquals(4900, telemetry.battery.designCapacityMah)
        assertEquals(4700, telemetry.battery.fullCapacityMah)
        assertEquals(41, telemetry.battery.dischargeCount)
        assertEquals("local-simulator", telemetry.diagnostics.source)
    }

    @Test
    fun `landing reaches ground and disarms`() {
        var state = SimulatorFlightModel.land(
            SimulatorSnapshot(phase = SimulatorFlightPhase.FLYING, altitudeM = 1.0)
        )
        repeat(20) { state = SimulatorFlightModel.step(state, SimulatorControlInput(), 0.1) }
        val telemetry = SimulatorFlightModel.toXStarState(state)

        assertEquals(SimulatorFlightPhase.GROUNDED, state.phase)
        assertEquals(0.0, state.altitudeM)
        assertFalse(telemetry.aircraft.armed ?: true)
    }

    @Test
    fun `descending to the surface cannot remain in flying state`() {
        val result = SimulatorFlightModel.step(
            SimulatorSnapshot(phase = SimulatorFlightPhase.FLYING, altitudeM = 0.1),
            SimulatorControlInput(throttle = -1.0),
            0.1
        )

        assertEquals(SimulatorFlightPhase.GROUNDED, result.phase)
        assertEquals(0.0, result.altitudeM)
        assertEquals(0.0, result.verticalSpeedMps)
    }

    @Test
    fun `inputs and flight envelope remain bounded`() {
        var state = SimulatorSnapshot(phase = SimulatorFlightPhase.FLYING, altitudeM = 119.9)
        repeat(100) {
            state = SimulatorFlightModel.step(
                state,
                SimulatorControlInput(99.0, -99.0, 99.0, -99.0, -99.0),
                0.25
            )
        }

        assertEquals(120.0, state.altitudeM)
        assertTrue(state.rollDeg in -28.0..28.0)
        assertTrue(state.pitchDeg in -28.0..28.0)
        assertTrue(state.yawDeg in 0.0..<360.0)
        assertTrue(state.gimbalPitchDeg in -90.0..30.0)
    }
}
