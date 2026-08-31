package io.xstarrevival.core.command

import io.xstarrevival.core.groundstation.MissionValidator
import io.xstarrevival.core.groundstation.MissionIssueSeverity
import io.xstarrevival.core.groundstation.PreflightEvaluator
import io.xstarrevival.core.model.ConnectionState
import io.xstarrevival.core.model.Severity
import io.xstarrevival.core.model.XStarState

enum class CommandIssueSeverity { ADVISORY, BLOCKING }

data class CommandIssue(val severity: CommandIssueSeverity, val message: String)

data class CommandValidation(
    val supported: Boolean,
    val issues: List<CommandIssue>,
    val canDispatch: Boolean = supported && issues.none { it.severity == CommandIssueSeverity.BLOCKING }
)

class CommandSafetyValidator {
    fun validate(
        command: AircraftCommand,
        state: XStarState,
        supportedCommands: Set<CommandKind>,
        activeCommands: Set<CommandKind> = emptySet()
    ): CommandValidation {
        if (command.kind !in supportedCommands) {
            return CommandValidation(
                supported = false,
                issues = listOf(CommandIssue(CommandIssueSeverity.BLOCKING, "${command.kind} is not supported by this transport"))
            )
        }

        val issues = mutableListOf<CommandIssue>()
        if (state.connection !is ConnectionState.Connected) {
            issues += blocking("Aircraft is not connected")
        }
        if (
            command.kind != CommandKind.EMERGENCY_LAND &&
            command.kind in EXCLUSIVE_FLIGHT_COMMANDS &&
            activeCommands.any { it in EXCLUSIVE_FLIGHT_COMMANDS }
        ) {
            issues += blocking("Another flight command is already active")
        }

        when (command) {
            ArmCommand -> validateArm(state, issues)
            DisarmCommand -> validateDisarm(state, issues)
            TakeoffCommand -> validateTakeoff(state, issues)
            LandCommand, EmergencyLandCommand -> requireAirborne(state, issues)
            ReturnToHomeCommand -> validateRth(state, issues)
            CancelReturnToHomeCommand -> {
                if (!state.aircraft.flightMode.orEmpty().contains("RETURN", ignoreCase = true)) {
                    issues += blocking("Return-to-Home is not active")
                }
            }
            is ChangeFlightModeCommand -> if (command.mode.isBlank()) issues += blocking("Flight mode is required")
            is SetHomePointCommand -> validateCoordinates(command.position.latitudeDeg, command.position.longitudeDeg, issues)
            is SetRthAltitudeCommand -> if (command.altitudeM !in 20.0..500.0) issues += blocking("RTH altitude must be between 20 m and 500 m")
            is StartWaypointMissionCommand -> {
                MissionValidator.validate(command.mission, state).issues.forEach { issue ->
                    issues += CommandIssue(
                        severity = if (issue.severity == MissionIssueSeverity.BLOCKING) {
                            CommandIssueSeverity.BLOCKING
                        } else {
                            CommandIssueSeverity.ADVISORY
                        },
                        message = issue.message
                    )
                }
                if (!PreflightEvaluator.evaluate(state).readyToFly) {
                    issues += blocking("Preflight checks are not ready")
                }
                requireHome(state, issues)
            }
            PauseMissionCommand, ResumeMissionCommand -> requireAirborne(state, issues)
            AbortMissionCommand -> Unit
            is GoToWaypointCommand -> {
                requireAirborne(state, issues)
                validateCoordinates(command.waypoint.latitudeDeg, command.waypoint.longitudeDeg, issues)
                if (command.altitudeM !in 2.0..500.0) issues += blocking("Waypoint altitude must be between 2 m and 500 m")
                if (command.speedMps !in 0.5..20.0) issues += blocking("Waypoint speed must be between 0.5 m/s and 20 m/s")
            }
            is StartOrbitCommand -> {
                requireAirborne(state, issues)
                validateCoordinates(command.pointOfInterest.latitudeDeg, command.pointOfInterest.longitudeDeg, issues)
                if (command.radiusM !in 5.0..500.0) issues += blocking("Orbit radius must be between 5 m and 500 m")
                if (command.altitudeM !in 2.0..500.0) issues += blocking("Orbit altitude must be between 2 m and 500 m")
                if (command.speedMps !in 0.5..15.0) issues += blocking("Orbit speed must be between 0.5 m/s and 15 m/s")
            }
            StopOrbitCommand, StopFollowCommand, StopCourseLockCommand, StopHomeLockCommand -> requireAirborne(state, issues)
            is StartFollowCommand -> {
                requireAirborne(state, issues)
                if (command.distanceM !in 5.0..200.0) issues += blocking("Follow distance must be between 5 m and 200 m")
                if (command.altitudeM !in 2.0..500.0) issues += blocking("Follow altitude must be between 2 m and 500 m")
            }
            is StartCourseLockCommand -> {
                requireAirborne(state, issues)
                if (command.headingDeg !in 0.0..360.0) issues += blocking("Course Lock heading must be between 0° and 360°")
            }
            StartHomeLockCommand -> {
                requireAirborne(state, issues)
                requireHome(state, issues)
            }
            is CameraCommand -> validateCamera(state, command, issues)
            is SetGimbalPitchCommand -> {
                if (command.pitchDeg !in -90.0..30.0) issues += blocking("Gimbal pitch must be between -90° and 30°")
            }
            RecenterGimbalCommand, CalibrateGimbalCommand -> Unit
            is ConfigureGimbalCommand -> {
                if (command.sensitivity !in 0.0..1.0) issues += blocking("Gimbal sensitivity must be between 0 and 1")
                if (command.smoothing !in 0.0..1.0) issues += blocking("Gimbal smoothing must be between 0 and 1")
            }
            is SetFlightLimitsCommand -> {
                if (command.maximumAltitudeM !in 30.0..500.0) issues += blocking("Maximum altitude must be between 30 m and 500 m")
                if (command.maximumDistanceM !in 50.0..5_000.0) issues += blocking("Maximum distance must be between 50 m and 5,000 m")
            }
            is SetLedsCommand -> Unit
            is ConfigureControllerCommand -> {
                if (command.sensitivity !in 0.0..1.0) issues += blocking("Controller sensitivity must be between 0 and 1")
                if (command.deadZone !in 0.0..0.25) issues += blocking("Controller dead zone must be between 0 and 0.25")
            }
            is SetVideoLinkChannelCommand -> {
                if (!command.automatic && (command.channel == null || command.channel !in 1..13)) {
                    issues += blocking("Manual video-link channel must be between 1 and 13")
                }
            }
            is SetBatteryWarningsCommand -> {
                if (command.criticalPercent !in 5..25) issues += blocking("Critical battery warning must be between 5% and 25%")
                if (command.lowPercent !in 15..50) issues += blocking("Low battery warning must be between 15% and 50%")
                if (command.lowPercent <= command.criticalPercent) issues += blocking("Low battery warning must be above the critical threshold")
            }
        }

        if (command.kind in SAFETY_CRITICAL_COMMANDS && state.warnings.any { it.severity == Severity.CRITICAL }) {
            issues += blocking("Aircraft has an active critical warning")
        }
        return CommandValidation(supported = true, issues = issues.distinct())
    }

    private fun validateArm(state: XStarState, issues: MutableList<CommandIssue>) {
        if (state.aircraft.armed == true) issues += blocking("Aircraft is already armed")
        val preflight = PreflightEvaluator.evaluate(state)
        if (!preflight.readyToFly) issues += blocking("Preflight checks are not ready")
    }

    private fun validateDisarm(state: XStarState, issues: MutableList<CommandIssue>) {
        if (state.aircraft.armed != true) issues += blocking("Aircraft is not armed")
        when (val altitude = state.navigation.altitudeM) {
            null -> issues += blocking("Altitude state is unavailable")
            else -> if (altitude > 0.5) issues += blocking("Disarm is blocked while airborne")
        }
    }

    private fun validateTakeoff(state: XStarState, issues: MutableList<CommandIssue>) {
        when (val altitude = state.navigation.altitudeM) {
            null -> issues += blocking("Altitude state is unavailable")
            else -> if (altitude > 0.5) issues += blocking("Aircraft is already airborne")
        }
        val preflight = PreflightEvaluator.evaluate(state)
        if (!preflight.readyToFly) issues += blocking("Preflight checks are not ready")
        requireHome(state, issues)
    }

    private fun validateRth(state: XStarState, issues: MutableList<CommandIssue>) {
        requireAirborne(state, issues)
        requireHome(state, issues)
    }

    private fun requireAirborne(state: XStarState, issues: MutableList<CommandIssue>) {
        val airborne = state.aircraft.armed == true && (state.navigation.altitudeM ?: 0.0) > 0.2
        if (!airborne) issues += blocking("Aircraft is not airborne")
    }

    private fun requireHome(state: XStarState, issues: MutableList<CommandIssue>) {
        if (state.navigation.homeLatitudeDeg == null || state.navigation.homeLongitudeDeg == null) {
            issues += blocking("Home Point is unavailable")
        }
    }

    private fun validateCoordinates(latitude: Double, longitude: Double, issues: MutableList<CommandIssue>) {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) issues += blocking("Coordinates are invalid")
    }

    private fun validateCamera(state: XStarState, command: CameraCommand, issues: MutableList<CommandIssue>) {
        if (state.camera.connected != true) issues += blocking("Camera is not connected")
        when (command) {
            StartRecordingCommand -> if (state.camera.recording == true) issues += blocking("Camera is already recording")
            StopRecordingCommand -> if (state.camera.recording != true) issues += blocking("Camera is not recording")
            is ChangeCameraModeCommand -> if (command.mode.isBlank()) issues += blocking("Camera mode is required")
            is SetExposureCommand -> {
                if (command.iso != null && command.iso !in 50..25_600) issues += blocking("ISO is outside the supported range")
                if (command.shutterSeconds != null && command.shutterSeconds !in 0.000_125..30.0) issues += blocking("Shutter time is outside the supported range")
                if (command.compensationEv != null && command.compensationEv !in -5.0..5.0) issues += blocking("Exposure compensation is outside the supported range")
            }
            is ConfigureCameraCommand -> if (command.parameters.isEmpty()) issues += blocking("Camera configuration is empty")
            TakePhotoCommand -> Unit
        }
    }

    private fun blocking(message: String) = CommandIssue(CommandIssueSeverity.BLOCKING, message)

    private companion object {
        val EXCLUSIVE_FLIGHT_COMMANDS = setOf(
            CommandKind.TAKEOFF,
            CommandKind.LAND,
            CommandKind.RETURN_TO_HOME,
            CommandKind.EMERGENCY_LAND,
            CommandKind.START_WAYPOINT_MISSION,
            CommandKind.START_ORBIT,
            CommandKind.START_FOLLOW,
            CommandKind.GO_TO_WAYPOINT
        )
        val SAFETY_CRITICAL_COMMANDS = setOf(
            CommandKind.ARM,
            CommandKind.TAKEOFF,
            CommandKind.RETURN_TO_HOME,
            CommandKind.START_WAYPOINT_MISSION,
            CommandKind.START_ORBIT,
            CommandKind.START_FOLLOW,
            CommandKind.GO_TO_WAYPOINT
        )
    }
}
