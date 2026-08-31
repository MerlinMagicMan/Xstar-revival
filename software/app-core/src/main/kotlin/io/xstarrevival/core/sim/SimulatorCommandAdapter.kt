package io.xstarrevival.core.sim

import io.xstarrevival.core.command.ArmCommand
import io.xstarrevival.core.command.AbortMissionCommand
import io.xstarrevival.core.command.CancelReturnToHomeCommand
import io.xstarrevival.core.command.CommandAcknowledgement
import io.xstarrevival.core.command.CommandCompletion
import io.xstarrevival.core.command.CommandKind
import io.xstarrevival.core.command.CommandRequest
import io.xstarrevival.core.command.CommandTransport
import io.xstarrevival.core.command.DisarmCommand
import io.xstarrevival.core.command.EmergencyLandCommand
import io.xstarrevival.core.command.LandCommand
import io.xstarrevival.core.command.PauseMissionCommand
import io.xstarrevival.core.command.RecenterGimbalCommand
import io.xstarrevival.core.command.ReturnToHomeCommand
import io.xstarrevival.core.command.ResumeMissionCommand
import io.xstarrevival.core.command.SetGimbalPitchCommand
import io.xstarrevival.core.command.StartRecordingCommand
import io.xstarrevival.core.command.StartFollowCommand
import io.xstarrevival.core.command.StartCourseLockCommand
import io.xstarrevival.core.command.StartHomeLockCommand
import io.xstarrevival.core.command.StartOrbitCommand
import io.xstarrevival.core.command.StartWaypointMissionCommand
import io.xstarrevival.core.command.StopRecordingCommand
import io.xstarrevival.core.command.StopFollowCommand
import io.xstarrevival.core.command.StopCourseLockCommand
import io.xstarrevival.core.command.StopHomeLockCommand
import io.xstarrevival.core.command.StopOrbitCommand
import io.xstarrevival.core.command.TakePhotoCommand
import io.xstarrevival.core.command.TakeoffCommand
import io.xstarrevival.core.groundstation.MissionExecutionPhase
import io.xstarrevival.core.groundstation.SmartFlightMode
import io.xstarrevival.core.groundstation.SmartFlightPhase
import io.xstarrevival.core.model.ConnectionState
import io.xstarrevival.core.model.XStarState
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
        CommandKind.RECENTER_GIMBAL,
        CommandKind.START_WAYPOINT_MISSION,
        CommandKind.PAUSE_MISSION,
        CommandKind.RESUME_MISSION,
        CommandKind.ABORT_MISSION,
        CommandKind.RETURN_TO_HOME,
        CommandKind.CANCEL_RETURN_TO_HOME,
        CommandKind.START_ORBIT,
        CommandKind.STOP_ORBIT,
        CommandKind.START_FOLLOW,
        CommandKind.STOP_FOLLOW,
        CommandKind.START_COURSE_LOCK,
        CommandKind.STOP_COURSE_LOCK,
        CommandKind.START_HOME_LOCK,
        CommandKind.STOP_HOME_LOCK
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
            is StartWaypointMissionCommand -> if (!platform.startMission(command.mission)) {
                return CommandAcknowledgement.Rejected("Simulator could not start the mission")
            }
            PauseMissionCommand -> if (!platform.pauseMission()) {
                return CommandAcknowledgement.Rejected("No active simulator mission can be paused")
            }
            ResumeMissionCommand -> if (!platform.resumeMission()) {
                return CommandAcknowledgement.Rejected("No paused simulator mission can be resumed")
            }
            AbortMissionCommand -> if (!platform.abortMission()) {
                return CommandAcknowledgement.Rejected("No active simulator mission can be aborted")
            }
            ReturnToHomeCommand -> if (!platform.startReturnToHome()) {
                return CommandAcknowledgement.Rejected("Simulator could not start Return-to-Home")
            }
            CancelReturnToHomeCommand -> if (!platform.cancelReturnToHome()) {
                return CommandAcknowledgement.Rejected("Return-to-Home is not active")
            }
            is StartOrbitCommand -> if (!platform.startOrbit(
                    command.pointOfInterest,
                    command.radiusM,
                    command.altitudeM,
                    command.speedMps,
                    command.clockwise,
                    command.laps
                )
            ) {
                return CommandAcknowledgement.Rejected("Simulator could not start Orbit")
            }
            StopOrbitCommand -> if (!platform.stopOrbit()) {
                return CommandAcknowledgement.Rejected("Orbit is not active")
            }
            is StartFollowCommand -> if (!platform.startFollow(
                    command.distanceM,
                    command.altitudeM,
                    command.speedMps,
                    command.target
                )
            ) {
                return CommandAcknowledgement.Rejected("Simulator could not start Follow")
            }
            StopFollowCommand -> if (!platform.stopFollow()) {
                return CommandAcknowledgement.Rejected("Follow is not active")
            }
            is StartCourseLockCommand -> if (!platform.startCourseLock(command.headingDeg)) {
                return CommandAcknowledgement.Rejected("Simulator could not start Course Lock")
            }
            StopCourseLockCommand -> if (!platform.stopCourseLock()) {
                return CommandAcknowledgement.Rejected("Course Lock is not active")
            }
            StartHomeLockCommand -> if (!platform.startHomeLock()) {
                return CommandAcknowledgement.Rejected("Simulator could not start Home Lock")
            }
            StopHomeLockCommand -> if (!platform.stopHomeLock()) {
                return CommandAcknowledgement.Rejected("Home Lock is not active")
            }
            else -> return CommandAcknowledgement.Unsupported("${command.kind} is not implemented by the simulator")
        }
        return CommandAcknowledgement.Accepted("Simulator accepted ${request.command.kind}")
    }

    override suspend fun awaitCompletion(request: CommandRequest): CommandCompletion {
        val command = request.command
        return when (command) {
            ArmCommand -> awaitState(request) { it.aircraft.armed == true && it.aircraft.flightMode == "ARMED" }
            DisarmCommand -> awaitState(request) { it.aircraft.armed == false }
            TakeoffCommand -> awaitState(request) { it.aircraft.flightMode == "FLYING" }
            LandCommand, EmergencyLandCommand -> awaitState(request) { it.aircraft.flightMode == "GROUNDED" }
            StartRecordingCommand -> awaitState(request) { it.camera.recording == true }
            StopRecordingCommand -> awaitState(request) { it.camera.recording == false }
            is SetGimbalPitchCommand -> awaitState(request) { state ->
                state.gimbal.pitchDeg?.let { kotlin.math.abs(it - command.pitchDeg.coerceIn(-90.0, 30.0)) < 0.01 } == true
            }
            RecenterGimbalCommand -> awaitState(request) {
                it.gimbal.pitchDeg?.let { pitch -> kotlin.math.abs(pitch) < 0.01 } == true
            }
            TakePhotoCommand -> CommandCompletion.Completed("Simulator state reconciled for ${command.kind}")
            is StartWaypointMissionCommand -> awaitMissionTerminal()
            PauseMissionCommand -> awaitMissionPhase(MissionExecutionPhase.PAUSED)
            ResumeMissionCommand -> awaitMissionPhase(MissionExecutionPhase.ACTIVE)
            AbortMissionCommand -> awaitMissionPhase(MissionExecutionPhase.ABORTED)
            ReturnToHomeCommand -> awaitSmartTerminal(SmartFlightMode.RETURN_TO_HOME)
            CancelReturnToHomeCommand -> awaitSmartCancellation(SmartFlightMode.RETURN_TO_HOME)
            is StartOrbitCommand -> awaitSmartTerminal(SmartFlightMode.ORBIT)
            StopOrbitCommand -> awaitSmartCancellation(SmartFlightMode.ORBIT)
            is StartFollowCommand -> awaitSmartTerminal(SmartFlightMode.FOLLOW)
            StopFollowCommand -> awaitSmartCancellation(SmartFlightMode.FOLLOW)
            is StartCourseLockCommand -> awaitSmartTerminal(SmartFlightMode.COURSE_LOCK)
            StopCourseLockCommand -> awaitSmartCancellation(SmartFlightMode.COURSE_LOCK)
            StartHomeLockCommand -> awaitSmartTerminal(SmartFlightMode.HOME_LOCK)
            StopHomeLockCommand -> awaitSmartCancellation(SmartFlightMode.HOME_LOCK)
            else -> CommandCompletion.Failed("${command.kind} has no simulator completion rule")
        }
    }

    private suspend fun awaitState(
        request: CommandRequest,
        reconciled: (XStarState) -> Boolean
    ): CommandCompletion {
        val state = platform.state.first { current ->
            current.connection !is ConnectionState.Connected ||
                (request.command.kind in FLIGHT_START_COMMANDS && current.warnings.any { it.id == "sim-forced-landing" }) ||
                reconciled(current)
        }
        if (state.connection !is ConnectionState.Connected) {
            return CommandCompletion.Failed("Simulator aircraft link was lost before state reconciliation")
        }
        if (request.command.kind in FLIGHT_START_COMMANDS && state.warnings.any { it.id == "sim-forced-landing" }) {
            return CommandCompletion.Failed("Simulator forced landing interrupted ${request.command.kind}")
        }
        return CommandCompletion.Completed("Simulator state reconciled for ${request.command.kind}")
    }

    private suspend fun awaitMissionPhase(phase: MissionExecutionPhase): CommandCompletion {
        val state = platform.missionExecution.first { it.phase == phase || it.phase in MISSION_TERMINAL_PHASES }
        return when {
            state.phase == phase -> CommandCompletion.Completed(state.detail)
            state.phase == MissionExecutionPhase.ABORTED -> CommandCompletion.Cancelled(state.detail)
            else -> CommandCompletion.Failed(state.detail ?: "Mission entered ${state.phase}")
        }
    }

    private suspend fun awaitMissionTerminal(): CommandCompletion {
        val state = platform.missionExecution.first { it.phase in MISSION_TERMINAL_PHASES }
        return when (state.phase) {
            MissionExecutionPhase.COMPLETED -> CommandCompletion.Completed(state.detail)
            MissionExecutionPhase.ABORTED -> CommandCompletion.Cancelled(state.detail)
            else -> CommandCompletion.Failed(state.detail ?: "Mission failed")
        }
    }

    private suspend fun awaitSmartTerminal(mode: SmartFlightMode): CommandCompletion {
        val state = platform.smartFlightExecution.first {
            it.mode == mode && it.phase in SMART_TERMINAL_PHASES
        }
        return when (state.phase) {
            SmartFlightPhase.COMPLETED -> CommandCompletion.Completed(state.detail)
            SmartFlightPhase.CANCELLED -> CommandCompletion.Cancelled(state.detail)
            else -> CommandCompletion.Failed(state.detail ?: "$mode failed")
        }
    }

    private suspend fun awaitSmartCancellation(mode: SmartFlightMode): CommandCompletion {
        val state = platform.smartFlightExecution.first {
            it.mode == mode && it.phase in SMART_TERMINAL_PHASES
        }
        return if (state.phase == SmartFlightPhase.CANCELLED) {
            CommandCompletion.Completed(state.detail)
        } else {
            CommandCompletion.Failed(state.detail ?: "$mode did not cancel")
        }
    }

    private companion object {
        val FLIGHT_START_COMMANDS = setOf(CommandKind.ARM, CommandKind.TAKEOFF)
        val MISSION_TERMINAL_PHASES = setOf(
            MissionExecutionPhase.COMPLETED,
            MissionExecutionPhase.ABORTED,
            MissionExecutionPhase.FAILED
        )
        val SMART_TERMINAL_PHASES = setOf(
            SmartFlightPhase.COMPLETED,
            SmartFlightPhase.CANCELLED,
            SmartFlightPhase.FAILED
        )
    }
}
