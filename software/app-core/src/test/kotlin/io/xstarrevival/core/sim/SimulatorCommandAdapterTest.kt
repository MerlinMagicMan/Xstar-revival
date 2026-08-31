package io.xstarrevival.core.sim

import io.xstarrevival.core.command.ArmCommand
import io.xstarrevival.core.command.CommandDispatcher
import io.xstarrevival.core.command.CommandPhase
import io.xstarrevival.core.command.SetGimbalPitchCommand
import io.xstarrevival.core.command.StartRecordingCommand
import io.xstarrevival.core.command.StopRecordingCommand
import io.xstarrevival.core.command.TakeoffCommand
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimulatorCommandAdapterTest {
    @Test
    fun `simulator commands reconcile against normalized state`() = runTest {
        val platform = SimulatorXStarPlatform(backgroundScope, tickMs = 50)
        platform.connect()
        val dispatcher = CommandDispatcher(this, { platform.state.value }, SimulatorCommandAdapter(platform))

        assertEquals(CommandPhase.COMPLETED, dispatcher.dispatchAndAwait(ArmCommand).phase)
        assertEquals(CommandPhase.COMPLETED, dispatcher.dispatchAndAwait(StartRecordingCommand).phase)
        assertTrue(platform.state.value.camera.recording == true)
        assertEquals(CommandPhase.COMPLETED, dispatcher.dispatchAndAwait(StopRecordingCommand).phase)
        assertEquals(CommandPhase.COMPLETED, dispatcher.dispatchAndAwait(SetGimbalPitchCommand(20.0)).phase)
        assertEquals(20.0, platform.state.value.gimbal.pitchDeg)
        assertEquals(CommandPhase.COMPLETED, dispatcher.dispatchAndAwait(TakeoffCommand).phase)
        assertEquals("FLYING", platform.state.value.aircraft.flightMode)

        platform.disconnect()
    }
}
