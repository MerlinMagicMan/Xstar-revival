package io.xstarrevival.core.command

import io.xstarrevival.core.model.AircraftState
import io.xstarrevival.core.model.BatteryState
import io.xstarrevival.core.model.CameraState
import io.xstarrevival.core.model.CellState
import io.xstarrevival.core.model.ConnectionState
import io.xstarrevival.core.model.NavigationState
import io.xstarrevival.core.model.RemoteState
import io.xstarrevival.core.model.XStarState
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CommandDispatcherTest {
    @Test
    fun `accepted command follows complete lifecycle in order`() = runTest {
        val transport = FakeTransport()
        val dispatcher = CommandDispatcher(this, ::healthyState, transport)

        val result = dispatcher.dispatchAndAwait(ArmCommand)

        assertEquals(CommandPhase.COMPLETED, result.phase)
        assertEquals(1, transport.sendCount)
        assertEquals(
            listOf(
                CommandPhase.IDLE,
                CommandPhase.VALIDATING,
                CommandPhase.READY,
                CommandPhase.SENDING,
                CommandPhase.ACKNOWLEDGED,
                CommandPhase.ACTIVE,
                CommandPhase.COMPLETED
            ),
            dispatcher.history.value.map { it.phase }
        )
    }

    @Test
    fun `validation rejection never reaches transport`() = runTest {
        val transport = FakeTransport()
        val disconnected = XStarState(connection = ConnectionState.Disconnected)
        val dispatcher = CommandDispatcher(this, { disconnected }, transport)

        val result = dispatcher.dispatchAndAwait(ArmCommand)

        assertEquals(CommandPhase.REJECTED, result.phase)
        assertEquals(0, transport.sendCount)
        assertTrue(result.detail.orEmpty().contains("not connected"))
    }

    @Test
    fun `unsupported command ends as unsupported without send`() = runTest {
        val transport = FakeTransport(supportedCommands = setOf(CommandKind.ARM))
        val dispatcher = CommandDispatcher(this, ::healthyState, transport)

        val result = dispatcher.dispatchAndAwait(TakePhotoCommand)

        assertEquals(CommandPhase.UNSUPPORTED, result.phase)
        assertEquals(0, transport.sendCount)
    }

    @Test
    fun `completion timeout is terminal and keeps one end to end deadline`() = runTest {
        val transport = FakeTransport(waitForever = true)
        val dispatcher = CommandDispatcher(this, ::healthyState, transport)

        val result = dispatcher.dispatchAndAwait(ArmCommand, timeoutMs = 100)

        assertEquals(CommandPhase.TIMED_OUT, result.phase)
        assertEquals(100, testScheduler.currentTime)
    }

    @Test
    fun `operator cancellation becomes terminal cancelled state`() = runTest {
        val transport = FakeTransport(waitForever = true)
        val dispatcher = CommandDispatcher(this, ::healthyState, transport)
        val id = dispatcher.dispatch(ArmCommand, timeoutMs = 10_000)
        testScheduler.runCurrent()

        assertTrue(dispatcher.cancel(id))
        testScheduler.runCurrent()

        assertEquals(CommandPhase.CANCELLED, dispatcher.statuses.value.getValue(id).phase)
        assertFalse(dispatcher.cancel("missing"))
    }

    @Test
    fun `immediate cancellation cannot be overwritten by completion`() = runTest {
        val dispatcher = CommandDispatcher(this, ::healthyState, FakeTransport())
        val id = dispatcher.dispatch(ArmCommand)

        assertTrue(dispatcher.cancel(id))
        testScheduler.runCurrent()

        assertEquals(CommandPhase.CANCELLED, dispatcher.statuses.value.getValue(id).phase)
        assertFalse(dispatcher.cancel(id))
    }

    private class FakeTransport(
        override val supportedCommands: Set<CommandKind> = CommandKind.entries.toSet(),
        private val waitForever: Boolean = false
    ) : CommandTransport {
        override val name = "fake"
        var sendCount = 0

        override suspend fun send(request: CommandRequest): CommandAcknowledgement {
            sendCount += 1
            return CommandAcknowledgement.Accepted("accepted")
        }

        override suspend fun awaitCompletion(request: CommandRequest): CommandCompletion {
            if (waitForever) awaitCancellation()
            return CommandCompletion.Completed("reconciled")
        }
    }

    companion object {
        private fun healthyState() = XStarState(
            connection = ConnectionState.Connected("test", "X-Star Premium"),
            aircraft = AircraftState(armed = false, flightMode = "GROUNDED"),
            navigation = NavigationState(
                satellites = 14,
                altitudeM = 0.0,
                homeLatitudeDeg = 35.0,
                homeLongitudeDeg = -97.0
            ),
            remote = RemoteState(connected = true, signalPercent = 95),
            battery = BatteryState(
                percent = 88,
                temperatureC = 28.0,
                cells = listOf(CellState(1, 4.10), CellState(2, 4.09), CellState(3, 4.10), CellState(4, 4.09))
            ),
            camera = CameraState(connected = true, recording = false)
        )
    }
}
