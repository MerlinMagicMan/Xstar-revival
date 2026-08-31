package io.xstarrevival.core.sim

import io.xstarrevival.core.command.ArmCommand
import io.xstarrevival.core.command.CommandAcknowledgement
import io.xstarrevival.core.command.CommandCompletion
import io.xstarrevival.core.command.CommandKind
import io.xstarrevival.core.command.CommandRequest
import io.xstarrevival.core.command.CommandTransport
import io.xstarrevival.core.command.DisarmCommand
import io.xstarrevival.core.command.EmergencyLandCommand
import io.xstarrevival.core.command.LandCommand
import io.xstarrevival.core.command.RecenterGimbalCommand
import io.xstarrevival.core.command.SetGimbalPitchCommand
import io.xstarrevival.core.command.StartRecordingCommand
import io.xstarrevival.core.command.StopRecordingCommand
import io.xstarrevival.core.command.TakePhotoCommand
import io.xstarrevival.core.command.TakeoffCommand
import kotlinx.coroutines.flow.first

/** Command transport for the isolated software simulator only. It has no live platform dependency. */
class SimulatorCommandAdapter(
    private val platform: SimulatorXStarPlatform
) : CommandTransport {
    override val name: String = "local simulator command adapter"
    override val supportedCommands: Set<CommandKind> = setOf(
        CommandKind.ARM,
        CommandKind.DISARM,
        CommandKind.TAKEOFF,
        CommandKind.LAND,
        CommandKind.EMERGENCY_LAND,
        CommandKind.TAKE_PHOTO,
        CommandKind.START_RECORDING,
        CommandKind.STOP_RECORDING,
        CommandKind.SET_GIMBAL_PITCH,
        CommandKind.RECENTER_GIMBAL
    )

    override suspend fun send(request: CommandRequest): CommandAcknowledgement {
        when (val command = request.command) {
            ArmCommand -> platform.arm()
            DisarmCommand -> platform.disarm()
            TakeoffCommand -> platform.takeOff()
            LandCommand, EmergencyLandCommand -> platform.land()
            TakePhotoCommand -> Unit
            StartRecordingCommand -> platform.setRecording(true)
            StopRecordingCommand -> platform.setRecording(false)
            is SetGimbalPitchCommand -> platform.setGimbalPitch(command.pitchDeg)
            RecenterGimbalCommand -> platform.setGimbalPitch(0.0)
            else -> return CommandAcknowledgement.Unsupported("${command.kind} is not implemented by the simulator")
        }
        return CommandAcknowledgement.Accepted("Simulator accepted ${request.command.kind}")
    }

    override suspend fun awaitCompletion(request: CommandRequest): CommandCompletion {
        val command = request.command
        when (command) {
            ArmCommand -> platform.state.first { it.aircraft.armed == true && it.aircraft.flightMode == "ARMED" }
            DisarmCommand -> platform.state.first { it.aircraft.armed == false }
            TakeoffCommand -> platform.state.first { it.aircraft.flightMode == "FLYING" }
            LandCommand, EmergencyLandCommand -> platform.state.first { it.aircraft.flightMode == "GROUNDED" }
            StartRecordingCommand -> platform.state.first { it.camera.recording == true }
            StopRecordingCommand -> platform.state.first { it.camera.recording == false }
            is SetGimbalPitchCommand -> platform.state.first { state ->
                state.gimbal.pitchDeg?.let { kotlin.math.abs(it - command.pitchDeg.coerceIn(-90.0, 30.0)) < 0.01 } == true
            }
            RecenterGimbalCommand -> platform.state.first { it.gimbal.pitchDeg?.let { pitch -> kotlin.math.abs(pitch) < 0.01 } == true }
            TakePhotoCommand -> Unit
            else -> return CommandCompletion.Failed("${command.kind} has no simulator completion rule")
        }
        return CommandCompletion.Completed("Simulator state reconciled for ${command.kind}")
    }
}
