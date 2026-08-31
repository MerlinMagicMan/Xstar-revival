package io.xstarrevival.core.sim

import io.xstarrevival.core.command.ArmCommand
import io.xstarrevival.core.command.CommandDispatcher
import io.xstarrevival.core.command.CommandPhase
import io.xstarrevival.core.command.ChangeCameraModeCommand
import io.xstarrevival.core.command.CalibrateGimbalCommand
import io.xstarrevival.core.command.ConfigureCameraCommand
import io.xstarrevival.core.command.ConfigureGimbalCommand
import io.xstarrevival.core.command.RecenterGimbalCommand
import io.xstarrevival.core.command.SetExposureCommand
import io.xstarrevival.core.command.SetGimbalPitchCommand
import io.xstarrevival.core.command.StartRecordingCommand
import io.xstarrevival.core.command.StopRecordingCommand
import io.xstarrevival.core.command.TakePhotoCommand
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

    @Test
    fun `camera commands reconcile complete production settings`() = runTest {
        val platform = SimulatorXStarPlatform(backgroundScope, tickMs = 50)
        platform.connect()
        val dispatcher = CommandDispatcher(this, { platform.state.value }, SimulatorCommandAdapter(platform))

        assertEquals(CommandPhase.COMPLETED, dispatcher.dispatchAndAwait(ChangeCameraModeCommand("PHOTO")).phase)
        assertEquals(CommandPhase.COMPLETED, dispatcher.dispatchAndAwait(TakePhotoCommand).phase)
        assertEquals(1, platform.state.value.camera.photosTaken)
        assertEquals(
            CommandPhase.COMPLETED,
            dispatcher.dispatchAndAwait(SetExposureCommand(400, .004, -.7)).phase
        )
        assertEquals("400", platform.state.value.camera.iso)
        assertEquals(.004, platform.state.value.camera.shutter?.toDouble())
        assertEquals(-.7, platform.state.value.camera.exposureCompensationEv)

        val configuration = mapOf(
            "white_balance" to "CLOUDY",
            "photo_resolution" to "8 MP",
            "video_resolution" to "1080P",
            "frame_rate" to "60",
            "timer_seconds" to "5",
            "histogram" to "true",
            "overexposure_warning" to "true",
            "grid" to "true",
            "center_point" to "true"
        )
        assertEquals(
            CommandPhase.COMPLETED,
            dispatcher.dispatchAndAwait(ConfigureCameraCommand(configuration)).phase
        )
        assertEquals("CLOUDY", platform.state.value.camera.whiteBalance)
        assertEquals("1080P", platform.state.value.camera.videoResolution)
        assertEquals(60, platform.state.value.camera.frameRateFps)
        assertTrue(platform.state.value.camera.histogramEnabled)
        assertTrue(platform.state.value.camera.gridEnabled)

        platform.disconnect()
    }

    @Test
    fun `gimbal commands reconcile pitch calibration and response settings`() = runTest {
        val platform = SimulatorXStarPlatform(backgroundScope, tickMs = 50)
        platform.connect()
        val dispatcher = CommandDispatcher(this, { platform.state.value }, SimulatorCommandAdapter(platform))

        assertEquals(CommandPhase.COMPLETED, dispatcher.dispatchAndAwait(SetGimbalPitchCommand(-42.0)).phase)
        assertEquals(-42.0, platform.state.value.gimbal.pitchDeg)
        assertEquals(
            CommandPhase.COMPLETED,
            dispatcher.dispatchAndAwait(ConfigureGimbalCommand(.7, .8, .4)).phase
        )
        assertEquals(.7, platform.state.value.gimbal.sensitivity)
        assertEquals(.8, platform.state.value.gimbal.smoothing)
        assertEquals(.4, platform.state.value.gimbal.pitchSpeed)
        assertEquals(CommandPhase.COMPLETED, dispatcher.dispatchAndAwait(RecenterGimbalCommand).phase)
        assertEquals(0.0, platform.state.value.gimbal.pitchDeg)
        assertEquals(CommandPhase.COMPLETED, dispatcher.dispatchAndAwait(CalibrateGimbalCommand).phase)
        assertEquals("CALIBRATED", platform.state.value.gimbal.status)
        assertTrue(platform.state.value.gimbal.calibrated == true)

        platform.disconnect()
    }
}
