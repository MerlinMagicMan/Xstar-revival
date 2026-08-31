package io.xstarrevival.core.sim

import io.xstarrevival.core.command.CommandDispatcher
import io.xstarrevival.core.command.CommandPhase
import io.xstarrevival.core.command.TakeoffCommand
import io.xstarrevival.core.model.ConnectionState
import io.xstarrevival.core.model.Severity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SimulatorScenarioTest {
    @Test
    fun `every selectable scenario produces tagged deterministic state`() {
        val base = SimulatorFlightModel.toXStarState(SimulatorSnapshot())

        SimulatorScenario.entries.forEach { scenario ->
            val first = SimulatorScenarioApplier.apply(base, scenario)
            val second = SimulatorScenarioApplier.apply(base, scenario)

            assertEquals(first, second, scenario.label)
            assertTrue(first.diagnostics.notes.any { it == "Scenario: ${scenario.label}" }, scenario.label)
        }
    }

    @Test
    fun `navigation failures remove only state that is no longer trustworthy`() {
        val base = SimulatorFlightModel.toXStarState(SimulatorSnapshot())
        val degraded = SimulatorScenarioApplier.apply(base, SimulatorScenario.GPS_DEGRADED)
        val lost = SimulatorScenarioApplier.apply(base, SimulatorScenario.GPS_LOST)
        val noHome = SimulatorScenarioApplier.apply(base, SimulatorScenario.HOME_UNAVAILABLE)

        assertEquals(5, degraded.navigation.satellites)
        assertTrue(degraded.navigation.latitudeDeg != null)
        assertNull(lost.navigation.latitudeDeg)
        assertNull(lost.navigation.longitudeDeg)
        assertEquals("ATTI", lost.aircraft.flightMode)
        assertNull(noHome.navigation.homeLatitudeDeg)
        assertTrue(noHome.navigation.latitudeDeg != null)
    }

    @Test
    fun `video loss preserves telemetry and aircraft connection`() {
        val state = SimulatorScenarioApplier.apply(
            SimulatorFlightModel.toXStarState(SimulatorSnapshot()),
            SimulatorScenario.VIDEO_LOSS
        )

        assertTrue(state.connection is ConnectionState.Connected)
        assertFalse(state.camera.video.receiving)
        assertTrue(state.navigation.latitudeDeg != null)
        assertTrue(state.warnings.any { it.id == "sim-video-loss" && it.severity == Severity.WARNING })
    }

    @Test
    fun `battery scenarios expose actionable normalized values`() {
        val base = SimulatorFlightModel.toXStarState(SimulatorSnapshot())
        val critical = SimulatorScenarioApplier.apply(base, SimulatorScenario.CRITICAL_BATTERY)
        val hot = SimulatorScenarioApplier.apply(base, SimulatorScenario.HIGH_TEMPERATURE)
        val imbalance = SimulatorScenarioApplier.apply(base, SimulatorScenario.CELL_IMBALANCE)
        val degraded = SimulatorScenarioApplier.apply(base, SimulatorScenario.DEGRADED_BATTERY)

        assertEquals(8, critical.battery.percent)
        assertEquals(65.0, hot.battery.temperatureC)
        assertTrue((imbalance.battery.cellDeltaV ?: 0.0) > 0.18)
        assertTrue((degraded.battery.fullCapacityMah ?: Int.MAX_VALUE) < (degraded.battery.designCapacityMah ?: 0))
    }

    @Test
    fun `complete link loss blocks commands while preserving last telemetry`() = runTest {
        val platform = SimulatorXStarPlatform(backgroundScope)
        platform.connect()
        platform.setScenario(SimulatorScenario.COMPLETE_LINK_LOSS)
        val dispatcher = CommandDispatcher(this, { platform.state.value }, SimulatorCommandAdapter(platform))

        val result = dispatcher.dispatchAndAwait(TakeoffCommand)

        assertEquals(CommandPhase.REJECTED, result.phase)
        assertTrue(platform.state.value.navigation.latitudeDeg != null)
        assertTrue(platform.state.value.connection is ConnectionState.Disconnected)
    }

    @Test
    fun `link loss during an active command fails reconciliation`() = runTest {
        val platform = SimulatorXStarPlatform(backgroundScope, tickMs = 50)
        platform.connect()
        val dispatcher = CommandDispatcher(this, { platform.state.value }, SimulatorCommandAdapter(platform))
        val id = dispatcher.dispatch(TakeoffCommand)
        runCurrent()
        assertEquals(CommandPhase.ACTIVE, dispatcher.statuses.value.getValue(id).phase)

        platform.setScenario(SimulatorScenario.COMPLETE_LINK_LOSS)
        runCurrent()

        assertEquals(CommandPhase.FAILED, dispatcher.statuses.value.getValue(id).phase)
        assertTrue(dispatcher.statuses.value.getValue(id).detail.orEmpty().contains("link was lost"))
    }

    @Test
    fun `complete link loss freezes last known telemetry until recovery`() = runTest {
        val platform = SimulatorXStarPlatform(backgroundScope, tickMs = 50)
        platform.connect()
        platform.takeOff()
        advanceTimeBy(2_000)
        platform.setControls(SimulatorControlInput(pitch = 1.0))
        advanceTimeBy(500)
        runCurrent()
        platform.setScenario(SimulatorScenario.COMPLETE_LINK_LOSS)
        val lastLatitude = platform.state.value.navigation.latitudeDeg
        val lastBattery = platform.state.value.battery.percent

        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(lastLatitude, platform.state.value.navigation.latitudeDeg)
        assertEquals(lastBattery, platform.state.value.battery.percent)
        assertTrue(platform.state.value.connection is ConnectionState.Disconnected)

        platform.setScenario(SimulatorScenario.NORMAL_FLIGHT)
        assertTrue(platform.state.value.connection is ConnectionState.Connected)
    }

    @Test
    fun `forced landing scenario drives an airborne model to ground`() = runTest {
        val platform = SimulatorXStarPlatform(backgroundScope, tickMs = 50)
        platform.connect()
        platform.takeOff()
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals("FLYING", platform.state.value.aircraft.flightMode)

        platform.setScenario(SimulatorScenario.FORCED_LANDING)
        advanceTimeBy(3_000)
        runCurrent()

        assertEquals("GROUNDED", platform.state.value.aircraft.flightMode)
        assertFalse(platform.state.value.aircraft.armed ?: true)
        assertTrue(platform.state.value.warnings.any { it.id == "sim-forced-landing" })
    }
}
