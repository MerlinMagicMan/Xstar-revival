package io.xstarrevival.core.command

import io.xstarrevival.core.groundstation.GeoPoint
import io.xstarrevival.core.groundstation.MissionPlan

enum class CommandKind {
    ARM,
    DISARM,
    TAKEOFF,
    LAND,
    RETURN_TO_HOME,
    CANCEL_RETURN_TO_HOME,
    EMERGENCY_LAND,
    CHANGE_FLIGHT_MODE,
    SET_HOME_POINT,
    SET_RTH_ALTITUDE,
    START_WAYPOINT_MISSION,
    PAUSE_MISSION,
    RESUME_MISSION,
    ABORT_MISSION,
    GO_TO_WAYPOINT,
    START_ORBIT,
    STOP_ORBIT,
    START_FOLLOW,
    STOP_FOLLOW,
    START_COURSE_LOCK,
    STOP_COURSE_LOCK,
    START_HOME_LOCK,
    STOP_HOME_LOCK,
    TAKE_PHOTO,
    START_RECORDING,
    STOP_RECORDING,
    CHANGE_CAMERA_MODE,
    SET_EXPOSURE,
    CONFIGURE_CAMERA,
    SET_GIMBAL_PITCH,
    RECENTER_GIMBAL,
    CALIBRATE_GIMBAL,
    CONFIGURE_GIMBAL,
    SET_FLIGHT_LIMITS,
    SET_LEDS,
    CONFIGURE_CONTROLLER,
    SET_VIDEO_LINK_CHANNEL,
    SET_BATTERY_WARNINGS
}

sealed interface AircraftCommand {
    val kind: CommandKind
}

sealed interface FlightCommand : AircraftCommand
data object ArmCommand : FlightCommand { override val kind = CommandKind.ARM }
data object DisarmCommand : FlightCommand { override val kind = CommandKind.DISARM }
data object TakeoffCommand : FlightCommand { override val kind = CommandKind.TAKEOFF }
data object LandCommand : FlightCommand { override val kind = CommandKind.LAND }
data object ReturnToHomeCommand : FlightCommand { override val kind = CommandKind.RETURN_TO_HOME }
data object CancelReturnToHomeCommand : FlightCommand { override val kind = CommandKind.CANCEL_RETURN_TO_HOME }
data object EmergencyLandCommand : FlightCommand { override val kind = CommandKind.EMERGENCY_LAND }
data class ChangeFlightModeCommand(val mode: String) : FlightCommand { override val kind = CommandKind.CHANGE_FLIGHT_MODE }

sealed interface NavigationCommand : AircraftCommand
data class SetHomePointCommand(val position: GeoPoint) : NavigationCommand { override val kind = CommandKind.SET_HOME_POINT }
data class SetRthAltitudeCommand(val altitudeM: Double) : NavigationCommand { override val kind = CommandKind.SET_RTH_ALTITUDE }
data class StartWaypointMissionCommand(val mission: MissionPlan) : NavigationCommand { override val kind = CommandKind.START_WAYPOINT_MISSION }
data object PauseMissionCommand : NavigationCommand { override val kind = CommandKind.PAUSE_MISSION }
data object ResumeMissionCommand : NavigationCommand { override val kind = CommandKind.RESUME_MISSION }
data object AbortMissionCommand : NavigationCommand { override val kind = CommandKind.ABORT_MISSION }
data class GoToWaypointCommand(val waypoint: GeoPoint, val altitudeM: Double, val speedMps: Double) : NavigationCommand {
    override val kind = CommandKind.GO_TO_WAYPOINT
}

sealed interface SmartFlightCommand : AircraftCommand
data class StartOrbitCommand(
    val pointOfInterest: GeoPoint,
    val radiusM: Double,
    val altitudeM: Double,
    val speedMps: Double,
    val clockwise: Boolean,
    val laps: Int = 1
) : SmartFlightCommand { override val kind = CommandKind.START_ORBIT }
data object StopOrbitCommand : SmartFlightCommand { override val kind = CommandKind.STOP_ORBIT }
data class StartFollowCommand(
    val distanceM: Double,
    val altitudeM: Double,
    val speedMps: Double = 5.0,
    val target: GeoPoint? = null
) : SmartFlightCommand {
    override val kind = CommandKind.START_FOLLOW
}
data object StopFollowCommand : SmartFlightCommand { override val kind = CommandKind.STOP_FOLLOW }
data class StartCourseLockCommand(val headingDeg: Double) : SmartFlightCommand { override val kind = CommandKind.START_COURSE_LOCK }
data object StopCourseLockCommand : SmartFlightCommand { override val kind = CommandKind.STOP_COURSE_LOCK }
data object StartHomeLockCommand : SmartFlightCommand { override val kind = CommandKind.START_HOME_LOCK }
data object StopHomeLockCommand : SmartFlightCommand { override val kind = CommandKind.STOP_HOME_LOCK }

sealed interface CameraCommand : AircraftCommand
data object TakePhotoCommand : CameraCommand { override val kind = CommandKind.TAKE_PHOTO }
data object StartRecordingCommand : CameraCommand { override val kind = CommandKind.START_RECORDING }
data object StopRecordingCommand : CameraCommand { override val kind = CommandKind.STOP_RECORDING }
data class ChangeCameraModeCommand(val mode: String) : CameraCommand { override val kind = CommandKind.CHANGE_CAMERA_MODE }
data class SetExposureCommand(val iso: Int?, val shutterSeconds: Double?, val compensationEv: Double?) : CameraCommand {
    override val kind = CommandKind.SET_EXPOSURE
}
data class ConfigureCameraCommand(val parameters: Map<String, String>) : CameraCommand { override val kind = CommandKind.CONFIGURE_CAMERA }

sealed interface GimbalCommand : AircraftCommand
data class SetGimbalPitchCommand(val pitchDeg: Double) : GimbalCommand { override val kind = CommandKind.SET_GIMBAL_PITCH }
data object RecenterGimbalCommand : GimbalCommand { override val kind = CommandKind.RECENTER_GIMBAL }
data object CalibrateGimbalCommand : GimbalCommand { override val kind = CommandKind.CALIBRATE_GIMBAL }
data class ConfigureGimbalCommand(
    val sensitivity: Double,
    val smoothing: Double,
    val pitchSpeed: Double = 0.5
) : GimbalCommand {
    override val kind = CommandKind.CONFIGURE_GIMBAL
}

sealed interface AircraftConfigurationCommand : AircraftCommand
data class SetFlightLimitsCommand(val maximumAltitudeM: Double, val maximumDistanceM: Double) : AircraftConfigurationCommand {
    override val kind = CommandKind.SET_FLIGHT_LIMITS
}
data class SetLedsCommand(val enabled: Boolean) : AircraftConfigurationCommand { override val kind = CommandKind.SET_LEDS }
data class ConfigureControllerCommand(val sensitivity: Double, val deadZone: Double) : AircraftConfigurationCommand {
    override val kind = CommandKind.CONFIGURE_CONTROLLER
}
data class SetVideoLinkChannelCommand(val automatic: Boolean, val channel: Int?) : AircraftConfigurationCommand {
    override val kind = CommandKind.SET_VIDEO_LINK_CHANNEL
}
data class SetBatteryWarningsCommand(val lowPercent: Int, val criticalPercent: Int) : AircraftConfigurationCommand {
    override val kind = CommandKind.SET_BATTERY_WARNINGS
}

data class CommandRequest(
    val id: String,
    val command: AircraftCommand,
    val createdAtEpochMs: Long,
    val timeoutMs: Long
)

enum class CommandPhase {
    IDLE,
    VALIDATING,
    READY,
    SENDING,
    ACKNOWLEDGED,
    ACTIVE,
    COMPLETED,
    REJECTED,
    FAILED,
    TIMED_OUT,
    CANCELLED,
    UNSUPPORTED
}

val CommandPhase.isTerminal: Boolean
    get() = this in setOf(
        CommandPhase.COMPLETED,
        CommandPhase.REJECTED,
        CommandPhase.FAILED,
        CommandPhase.TIMED_OUT,
        CommandPhase.CANCELLED,
        CommandPhase.UNSUPPORTED
    )

data class CommandStatus(
    val request: CommandRequest,
    val phase: CommandPhase,
    val detail: String? = null,
    val updatedAtEpochMs: Long = request.createdAtEpochMs
)
