package io.xstarrevival.core.sim

import io.xstarrevival.core.command.AbortMissionCommand
import io.xstarrevival.core.command.CommandDispatcher
import io.xstarrevival.core.command.CommandPhase
import io.xstarrevival.core.command.PauseMissionCommand
import io.xstarrevival.core.command.ResumeMissionCommand
import io.xstarrevival.core.command.StartWaypointMissionCommand
import io.xstarrevival.core.groundstation.GeoPoint
import io.xstarrevival.core.groundstation.MissionExecutionPhase
import io.xstarrevival.core.groundstation.MissionPlan
import io.xstarrevival.core.groundstation.MissionWaypoint
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
