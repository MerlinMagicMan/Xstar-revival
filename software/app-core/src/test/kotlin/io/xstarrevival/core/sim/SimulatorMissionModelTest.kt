package io.xstarrevival.core.sim

import io.xstarrevival.core.command.AbortMissionCommand
import io.xstarrevival.core.command.CancelReturnToHomeCommand
import io.xstarrevival.core.command.CommandDispatcher
import io.xstarrevival.core.command.CommandPhase
import io.xstarrevival.core.command.PauseMissionCommand
import io.xstarrevival.core.command.ResumeMissionCommand
import io.xstarrevival.core.command.StartWaypointMissionCommand
import io.xstarrevival.core.groundstation.GeoPoint
import io.xstarrevival.core.groundstation.MissionExecutionPhase
import io.xstarrevival.core.groundstation.MissionFinishBehavior
import io.xstarrevival.core.groundstation.MissionLostLinkBehavior
import io.xstarrevival.core.groundstation.MissionPlan
import io.xstarrevival.core.groundstation.MissionWaypoint
import io.xstarrevival.core.groundstation.SmartFlightMode
import io.xstarrevival.core.groundstation.SmartFlightPhase
import io.xstarrevival.core.groundstation.WaypointAction
import io.xstarrevival.core.groundstation.WaypointActionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SimulatorMissionModelTest {
    @Test
    fun `mission takes off visits waypoints applies actions and completes`() {
        val plan = missionPlan()
        var snapshot = SimulatorSnapshot()
        var runtime = SimulatorMissionModel.start(plan)

        repeat(400) {
            snapshot = SimulatorFlightModel.step(snapshot, SimulatorControlInput(), 0.05)
            val step = SimulatorMissionModel.step(snapshot, runtime, 0.05)
            snapshot = step.snapshot
            runtime = step.runtime
            if (runtime.phase == MissionExecutionPhase.COMPLETED) return@repeat
        }

        assertEquals(MissionExecutionPhase.COMPLETED, runtime.phase)
        assertTrue(snapshot.recording)
        assertEquals(1.0, SimulatorMissionModel.state(snapshot, runtime).progress)
        assertEquals(0.0, SimulatorMissionModel.state(snapshot, runtime).remainingDistanceM)
    }

    @Test
    fun `paused mission holds position and resumed mission moves`() {
        val active = SimulatorMissionModel.start(missionPlan())
        val flying = SimulatorSnapshot(phase = SimulatorFlightPhase.FLYING, altitudeM = 2.0)
        val paused = checkNotNull(SimulatorMissionModel.pause(active))
        val held = SimulatorMissionModel.step(flying, paused, 1.0)

        assertEquals(flying.northM, held.snapshot.northM)
        val resumed = checkNotNull(SimulatorMissionModel.resume(paused))
        val moved = SimulatorMissionModel.step(flying, resumed, 1.0)
        assertNotEquals(flying.northM, moved.snapshot.northM)
    }

    @Test
    fun `dispatcher reconciles mission pause resume and completion`() = runTest {
        val platform = SimulatorXStarPlatform(backgroundScope, tickMs = 50)
        platform.connect()
        val dispatcher = CommandDispatcher(this, { platform.state.value }, SimulatorCommandAdapter(platform))
        val startId = dispatcher.dispatch(StartWaypointMissionCommand(missionPlan()))
        advanceTimeBy(2_200)
        runCurrent()
        assertEquals(CommandPhase.ACTIVE, dispatcher.statuses.value.getValue(startId).phase)

        assertEquals(CommandPhase.COMPLETED, dispatcher.dispatchAndAwait(PauseMissionCommand).phase)
        assertEquals(MissionExecutionPhase.PAUSED, platform.missionExecution.value.phase)
        assertEquals(CommandPhase.COMPLETED, dispatcher.dispatchAndAwait(ResumeMissionCommand).phase)

        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(MissionExecutionPhase.COMPLETED, platform.missionExecution.value.phase)
        assertEquals(CommandPhase.COMPLETED, dispatcher.statuses.value.getValue(startId).phase)
    }

    @Test
    fun `abort cancels active mission command`() = runTest {
        val platform = SimulatorXStarPlatform(backgroundScope, tickMs = 50)
        platform.connect()
        val dispatcher = CommandDispatcher(this, { platform.state.value }, SimulatorCommandAdapter(platform))
        val startId = dispatcher.dispatch(StartWaypointMissionCommand(missionPlan()))
        advanceTimeBy(100)
        runCurrent()

        assertEquals(CommandPhase.COMPLETED, dispatcher.dispatchAndAwait(AbortMissionCommand).phase)
        runCurrent()

        assertEquals(MissionExecutionPhase.ABORTED, platform.missionExecution.value.phase)
        assertEquals(CommandPhase.CANCELLED, dispatcher.statuses.value.getValue(startId).phase)
    }

    @Test
    fun `waypoint failure scenario fails mission and active command`() = runTest {
        val platform = SimulatorXStarPlatform(backgroundScope, tickMs = 50)
        platform.connect()
        val dispatcher = CommandDispatcher(this, { platform.state.value }, SimulatorCommandAdapter(platform))
        val startId = dispatcher.dispatch(StartWaypointMissionCommand(missionPlan()))
        advanceTimeBy(2_200)
        runCurrent()

        platform.setScenario(SimulatorScenario.WAYPOINT_FAILURE)
        runCurrent()

        assertEquals(MissionExecutionPhase.FAILED, platform.missionExecution.value.phase)
        assertEquals(CommandPhase.FAILED, dispatcher.statuses.value.getValue(startId).phase)
    }

    @Test
    fun `return home finish completes only after landing at home`() {
        val plan = missionPlan().copy(finishBehavior = MissionFinishBehavior.RETURN_HOME)
        var snapshot = SimulatorSnapshot()
        var runtime = SimulatorMissionModel.start(plan)
        var observedReturnHome = false

        repeat(1_600) {
            snapshot = SimulatorFlightModel.step(snapshot, SimulatorControlInput(), 0.05)
            val step = SimulatorMissionModel.step(snapshot, runtime, 0.05)
            snapshot = step.snapshot
            runtime = step.runtime
            observedReturnHome = observedReturnHome || runtime.returnHomeRuntime?.phase == SmartFlightPhase.ACTIVE
        }

        assertTrue(observedReturnHome)
        assertEquals(MissionExecutionPhase.COMPLETED, runtime.phase)
        assertEquals(SmartFlightPhase.COMPLETED, runtime.returnHomeRuntime?.phase)
        assertEquals(SimulatorFlightPhase.GROUNDED, snapshot.phase)
        assertTrue(kotlin.math.hypot(snapshot.northM, snapshot.eastM) < 0.8)
    }

    @Test
    fun `dispatcher publishes and can cancel mission return home`() = runTest {
        val platform = SimulatorXStarPlatform(backgroundScope, tickMs = 50)
        platform.connect()
        val dispatcher = CommandDispatcher(this, { platform.state.value }, SimulatorCommandAdapter(platform))
        val startId = dispatcher.dispatch(
            StartWaypointMissionCommand(missionPlan().copy(finishBehavior = MissionFinishBehavior.RETURN_HOME))
        )

        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(platform.missionExecution.value.returningHome)
        assertEquals(SmartFlightMode.RETURN_TO_HOME, platform.smartFlightExecution.value.mode)
        assertEquals(SmartFlightPhase.ACTIVE, platform.smartFlightExecution.value.phase)

        assertEquals(CommandPhase.COMPLETED, dispatcher.dispatchAndAwait(CancelReturnToHomeCommand).phase)
        runCurrent()
        assertEquals(MissionExecutionPhase.ABORTED, platform.missionExecution.value.phase)
        assertEquals(SmartFlightPhase.CANCELLED, platform.smartFlightExecution.value.phase)
        assertEquals(CommandPhase.CANCELLED, dispatcher.statuses.value.getValue(startId).phase)
    }

    @Test
    fun `dispatcher completes return home mission after landing`() = runTest {
        val platform = SimulatorXStarPlatform(backgroundScope, tickMs = 50)
        platform.connect()
        val dispatcher = CommandDispatcher(this, { platform.state.value }, SimulatorCommandAdapter(platform))

        val status = dispatcher.dispatchAndAwait(
            StartWaypointMissionCommand(missionPlan().copy(finishBehavior = MissionFinishBehavior.RETURN_HOME))
        )

        assertEquals(CommandPhase.COMPLETED, status.phase)
        assertEquals(MissionExecutionPhase.COMPLETED, platform.missionExecution.value.phase)
        assertEquals(SmartFlightPhase.COMPLETED, platform.smartFlightExecution.value.phase)
        assertEquals("GROUNDED", platform.state.value.aircraft.flightMode)
    }

    @Test
    fun `home loss fails mission return home and its command`() = runTest {
        val platform = SimulatorXStarPlatform(backgroundScope, tickMs = 50)
        platform.connect()
        val dispatcher = CommandDispatcher(this, { platform.state.value }, SimulatorCommandAdapter(platform))
        val startId = dispatcher.dispatch(
            StartWaypointMissionCommand(missionPlan().copy(finishBehavior = MissionFinishBehavior.RETURN_HOME))
        )
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(platform.missionExecution.value.returningHome)

        platform.setScenario(SimulatorScenario.HOME_UNAVAILABLE)
        runCurrent()

        assertEquals(MissionExecutionPhase.FAILED, platform.missionExecution.value.phase)
        assertEquals(SmartFlightPhase.FAILED, platform.smartFlightExecution.value.phase)
        assertEquals(CommandPhase.FAILED, dispatcher.statuses.value.getValue(startId).phase)
        assertTrue(platform.missionExecution.value.detail.orEmpty().contains("Home Point"))
    }

    @Test
    fun `land finish remains active until touchdown`() {
        val plan = missionPlan().copy(finishBehavior = MissionFinishBehavior.LAND)
        var snapshot = SimulatorSnapshot()
        var runtime = SimulatorMissionModel.start(plan)
        var observedLanding = false

        repeat(800) {
            snapshot = SimulatorFlightModel.step(snapshot, SimulatorControlInput(), 0.05)
            val step = SimulatorMissionModel.step(snapshot, runtime, 0.05)
            snapshot = step.snapshot
            runtime = step.runtime
            if (runtime.landingForFinish) {
                observedLanding = true
                assertEquals(MissionExecutionPhase.ACTIVE, runtime.phase)
            }
        }

        assertTrue(observedLanding)
        assertEquals(MissionExecutionPhase.COMPLETED, runtime.phase)
        assertEquals(SimulatorFlightPhase.GROUNDED, snapshot.phase)
        assertEquals("Mission complete · landed", runtime.detail)
    }

    @Test
    fun `lost link behavior maps to continue return home or hover`() {
        val snapshot = SimulatorSnapshot(
            phase = SimulatorFlightPhase.FLYING,
            northM = 30.0,
            altitudeM = 10.0
        )
        val continueRuntime = SimulatorMissionModel.applyLostLink(
            snapshot,
            SimulatorMissionModel.start(missionPlan().copy(lostLinkBehavior = MissionLostLinkBehavior.CONTINUE))
        )
        val returnRuntime = SimulatorMissionModel.applyLostLink(
            snapshot,
            SimulatorMissionModel.start(missionPlan().copy(lostLinkBehavior = MissionLostLinkBehavior.RETURN_HOME))
        )
        val hoverRuntime = SimulatorMissionModel.applyLostLink(
            snapshot,
            SimulatorMissionModel.start(missionPlan().copy(lostLinkBehavior = MissionLostLinkBehavior.HOVER))
        )

        assertEquals(MissionExecutionPhase.ACTIVE, continueRuntime.phase)
        assertEquals(null, continueRuntime.returnHomeRuntime)
        assertEquals(SmartFlightPhase.ACTIVE, returnRuntime.returnHomeRuntime?.phase)
        assertEquals(MissionExecutionPhase.PAUSED, hoverRuntime.phase)
        assertTrue(hoverRuntime.detail.orEmpty().contains("hovering"))
    }

    @Test
    fun `hover lost link failsafe holds and resumes after recovery`() = runTest {
        val platform = SimulatorXStarPlatform(backgroundScope, tickMs = 50)
        platform.connect()
        val dispatcher = CommandDispatcher(this, { platform.state.value }, SimulatorCommandAdapter(platform))
        val startId = dispatcher.dispatch(
            StartWaypointMissionCommand(missionPlan().copy(lostLinkBehavior = MissionLostLinkBehavior.HOVER))
        )
        advanceTimeBy(2_200)
        platform.setScenario(SimulatorScenario.RC_LINK_LOSS)
        runCurrent()
        val heldLatitude = platform.state.value.navigation.latitudeDeg

        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(MissionExecutionPhase.PAUSED, platform.missionExecution.value.phase)
        assertEquals(heldLatitude, platform.state.value.navigation.latitudeDeg)
        assertEquals(CommandPhase.ACTIVE, dispatcher.statuses.value.getValue(startId).phase)

        platform.setScenario(SimulatorScenario.RC_RECOVERY)
        assertEquals(CommandPhase.COMPLETED, dispatcher.dispatchAndAwait(ResumeMissionCommand).phase)
        advanceTimeBy(12_000)
        runCurrent()
        assertEquals(MissionExecutionPhase.COMPLETED, platform.missionExecution.value.phase)
        assertEquals(CommandPhase.COMPLETED, dispatcher.statuses.value.getValue(startId).phase)
    }

    @Test
    fun `return home lost link failsafe lands and completes mission`() = runTest {
        val platform = SimulatorXStarPlatform(backgroundScope, tickMs = 50)
        platform.connect()
        val dispatcher = CommandDispatcher(this, { platform.state.value }, SimulatorCommandAdapter(platform))
        val startId = dispatcher.dispatch(
            StartWaypointMissionCommand(missionPlan().copy(lostLinkBehavior = MissionLostLinkBehavior.RETURN_HOME))
        )
        advanceTimeBy(2_200)

        platform.setScenario(SimulatorScenario.RC_LINK_LOSS)
        runCurrent()
        assertTrue(platform.missionExecution.value.returningHome)
        assertEquals(SmartFlightMode.RETURN_TO_HOME, platform.smartFlightExecution.value.mode)

        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(MissionExecutionPhase.COMPLETED, platform.missionExecution.value.phase)
        assertEquals(CommandPhase.COMPLETED, dispatcher.statuses.value.getValue(startId).phase)
        assertEquals(SimulatorFlightPhase.GROUNDED.name, platform.state.value.aircraft.flightMode)
    }

    private fun missionPlan() = MissionPlan(
        id = "mission-1",
        name = "Simulator route",
        waypoints = listOf(
            MissionWaypoint(
                id = "wp-1",
                position = GeoPoint(41.87812, -87.6298),
                altitudeM = 2.0,
                speedMps = 5.0
            ),
            MissionWaypoint(
                id = "wp-2",
                position = GeoPoint(41.87814, -87.62978),
                altitudeM = 2.0,
                speedMps = 5.0,
                actions = listOf(WaypointAction(WaypointActionType.START_VIDEO))
            )
        )
    )
}
