package io.xstarrevival.core.sim

import io.xstarrevival.core.command.CommandDispatcher
import io.xstarrevival.core.command.CommandPhase
import io.xstarrevival.core.command.ReturnToHomeCommand
import io.xstarrevival.core.command.StartFollowCommand
import io.xstarrevival.core.command.StartOrbitCommand
import io.xstarrevival.core.command.StopFollowCommand
import io.xstarrevival.core.groundstation.GeoPoint
import io.xstarrevival.core.groundstation.SmartFlightMode
import io.xstarrevival.core.groundstation.SmartFlightPhase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SimulatorSmartFlightModelTest {
    @Test
    fun `rth climbs returns and lands at home`() {
        var snapshot = SimulatorSnapshot(
            phase = SimulatorFlightPhase.FLYING,
            northM = 40.0,
            eastM = 25.0,
            altitudeM = 8.0
        )
        var runtime: SimulatorSmartFlightRuntime = SimulatorSmartFlightModel.startRth(snapshot)

        repeat(1_400) {
            snapshot = SimulatorFlightModel.step(snapshot, SimulatorControlInput(), 0.05)
            val result = SimulatorSmartFlightModel.step(snapshot, runtime, 0.05)
            snapshot = result.snapshot
            runtime = result.runtime
        }

        assertEquals(SmartFlightPhase.COMPLETED, runtime.phase)
        assertEquals(SimulatorFlightPhase.GROUNDED, snapshot.phase)
        assertTrue(kotlin.math.hypot(snapshot.northM, snapshot.eastM) < 0.8)
    }

    @Test
    fun `orbit completes configured laps and reports progress`() {
        var snapshot = SimulatorSnapshot(phase = SimulatorFlightPhase.FLYING, northM = 10.0, altitudeM = 10.0)
        var runtime: SimulatorSmartFlightRuntime = SimulatorSmartFlightModel.startOrbit(
            snapshot = snapshot,
            center = GeoPoint(41.8781, -87.6298),
            radiusM = 10.0,
            altitudeM = 10.0,
            speedMps = 10.0,
            clockwise = true,
            laps = 2
        )

        repeat(300) {
            val result = SimulatorSmartFlightModel.step(snapshot, runtime, 0.05)
            snapshot = result.snapshot
            runtime = result.runtime
        }
        val state = SimulatorSmartFlightModel.state(snapshot, runtime)

        assertEquals(SmartFlightPhase.COMPLETED, runtime.phase)
        assertEquals(1.0, state.progress)
        assertEquals(2, state.completedLaps)
    }

    @Test
    fun `follow moves to configured offset and remains active until stopped`() {
        var snapshot = SimulatorSnapshot(phase = SimulatorFlightPhase.FLYING, northM = 50.0, altitudeM = 10.0)
        var runtime: SimulatorSmartFlightRuntime = SimulatorSmartFlightModel.startFollow(20.0, 10.0, 5.0, null)

        repeat(400) {
            val result = SimulatorSmartFlightModel.step(snapshot, runtime, 0.05)
            snapshot = result.snapshot
            runtime = result.runtime
        }

        assertEquals(SmartFlightPhase.ACTIVE, runtime.phase)
        assertTrue(kotlin.math.abs(snapshot.northM + 20.0) < 0.1)
        assertEquals(SmartFlightPhase.CANCELLED, SimulatorSmartFlightModel.cancel(runtime, "Stopped").phase)
    }

    @Test
    fun `dispatcher reconciles rth orbit and follow stop`() = runTest {
        val platform = SimulatorXStarPlatform(backgroundScope, tickMs = 50)
        platform.connect()
        platform.takeOff()
        advanceTimeBy(2_000)
        runCurrent()
        val dispatcher = CommandDispatcher(this, { platform.state.value }, SimulatorCommandAdapter(platform))

        val orbit = dispatcher.dispatchAndAwait(
            StartOrbitCommand(
                pointOfInterest = GeoPoint(41.8781, -87.6298),
                radiusM = 5.0,
                altitudeM = 2.0,
                speedMps = 10.0,
                clockwise = true,
                laps = 1
            )
        )
        assertEquals(CommandPhase.COMPLETED, orbit.phase)

        val followId = dispatcher.dispatch(StartFollowCommand(distanceM = 5.0, altitudeM = 2.0, speedMps = 5.0))
        runCurrent()
        assertEquals(CommandPhase.ACTIVE, dispatcher.statuses.value.getValue(followId).phase)
        assertEquals(CommandPhase.COMPLETED, dispatcher.dispatchAndAwait(StopFollowCommand).phase)
        runCurrent()
        assertEquals(CommandPhase.CANCELLED, dispatcher.statuses.value.getValue(followId).phase)

        platform.setControls(SimulatorControlInput(pitch = 1.0))
        advanceTimeBy(1_000)
        platform.setControls(SimulatorControlInput())
        val rth = dispatcher.dispatchAndAwait(ReturnToHomeCommand)
        assertEquals(CommandPhase.COMPLETED, rth.phase)
        assertEquals(SmartFlightMode.RETURN_TO_HOME, platform.smartFlightExecution.value.mode)
        assertEquals(SmartFlightPhase.COMPLETED, platform.smartFlightExecution.value.phase)
    }

    @Test
    fun `gps loss fails an active orbit and its command`() = runTest {
        val platform = SimulatorXStarPlatform(backgroundScope, tickMs = 50)
        platform.connect()
        platform.takeOff()
        advanceTimeBy(2_000)
        val dispatcher = CommandDispatcher(this, { platform.state.value }, SimulatorCommandAdapter(platform))
        val id = dispatcher.dispatch(
            StartOrbitCommand(GeoPoint(41.8781, -87.6298), 20.0, 2.0, 2.0, true, laps = 10)
        )
        runCurrent()
        assertEquals(CommandPhase.ACTIVE, dispatcher.statuses.value.getValue(id).phase)

        platform.setScenario(SimulatorScenario.GPS_LOST)
        runCurrent()

        assertEquals(SmartFlightPhase.FAILED, platform.smartFlightExecution.value.phase)
        assertEquals(CommandPhase.FAILED, dispatcher.statuses.value.getValue(id).phase)
    }
}
