package io.xstarrevival.core.command

import io.xstarrevival.core.model.AircraftState
import io.xstarrevival.core.model.BatteryState
import io.xstarrevival.core.model.CameraState
import io.xstarrevival.core.model.CellState
import io.xstarrevival.core.model.ConnectionState
import io.xstarrevival.core.model.NavigationState
import io.xstarrevival.core.model.RemoteState
import io.xstarrevival.core.model.Severity
import io.xstarrevival.core.model.WarningState
import io.xstarrevival.core.model.XStarState
import io.xstarrevival.core.groundstation.GeoPoint
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandSafetyValidatorTest {
    private val validator = CommandSafetyValidator()

    @Test
    fun `unsupported command is rejected before transport`() {
        val result = validator.validate(TakeoffCommand, healthyState(), emptySet())

        assertFalse(result.supported)
        assertFalse(result.canDispatch)
        assertTrue(result.issues.any { "not supported" in it.message })
    }

    @Test
    fun `takeoff passes for a connected healthy simulator`() {
        val result = validator.validate(
            TakeoffCommand,
            healthyState(),
            setOf(CommandKind.TAKEOFF)
        )

        assertTrue(result.supported)
        assertTrue(result.canDispatch)
    }

    @Test
    fun `connection preflight and home failures block takeoff`() {
        val result = validator.validate(
            TakeoffCommand,
            XStarState(connection = ConnectionState.Disconnected),
            setOf(CommandKind.TAKEOFF)
        )

        assertFalse(result.canDispatch)
        assertTrue(result.issues.any { it.message == "Aircraft is not connected" })
        assertTrue(result.issues.any { it.message == "Preflight checks are not ready" })
        assertTrue(result.issues.any { it.message == "Home Point is unavailable" })
    }

    @Test
    fun `active exclusive flight command blocks a conflicting command`() {
        val airborne = healthyState().copy(
            aircraft = AircraftState(armed = true, flightMode = "FLYING"),
            navigation = healthyState().navigation.copy(altitudeM = 10.0)
        )

        val result = validator.validate(
            LandCommand,
            airborne,
            setOf(CommandKind.LAND),
            activeCommands = setOf(CommandKind.RETURN_TO_HOME)
        )

        assertFalse(result.canDispatch)
        assertTrue(result.issues.any { it.message == "Another flight command is already active" })
    }

    @Test
    fun `camera and configuration ranges are validated`() {
        val camera = validator.validate(
            SetExposureCommand(iso = 100_000, shutterSeconds = 60.0, compensationEv = 8.0),
            healthyState(),
            setOf(CommandKind.SET_EXPOSURE)
        )
        val channel = validator.validate(
            SetVideoLinkChannelCommand(automatic = false, channel = null),
            healthyState(),
            setOf(CommandKind.SET_VIDEO_LINK_CHANNEL)
        )

        assertFalse(camera.canDispatch)
        assertTrue(camera.issues.size >= 3)
        assertFalse(channel.canDispatch)
    }

    @Test
    fun `emergency landing remains available during conflicts and critical warnings`() {
        val airborne = healthyState().copy(
            aircraft = AircraftState(armed = true, flightMode = "FLYING"),
            navigation = healthyState().navigation.copy(altitudeM = 10.0),
            warnings = listOf(WarningState(id = "motor", severity = Severity.CRITICAL, message = "Motor fault"))
        )

        val result = validator.validate(
            EmergencyLandCommand,
            airborne,
            setOf(CommandKind.EMERGENCY_LAND),
            activeCommands = setOf(CommandKind.START_WAYPOINT_MISSION)
        )

        assertTrue(result.canDispatch)
    }

    @Test
    fun `unknown altitude blocks takeoff and disarm`() {
        val state = healthyState().copy(navigation = healthyState().navigation.copy(altitudeM = null))

        val takeoff = validator.validate(TakeoffCommand, state, setOf(CommandKind.TAKEOFF))
        val disarm = validator.validate(
            DisarmCommand,
            state.copy(aircraft = AircraftState(armed = true, flightMode = "ARMED")),
            setOf(CommandKind.DISARM)
        )

        assertFalse(takeoff.canDispatch)
        assertFalse(disarm.canDispatch)
        assertTrue(takeoff.issues.any { it.message == "Altitude state is unavailable" })
        assertTrue(disarm.issues.any { it.message == "Altitude state is unavailable" })
    }

    @Test
    fun `rth can override an active autonomous command`() {
        val airborne = healthyState().copy(
            aircraft = AircraftState(armed = true, flightMode = "WAYPOINT MISSION"),
            navigation = healthyState().navigation.copy(altitudeM = 20.0)
        )

        val result = validator.validate(
            ReturnToHomeCommand,
            airborne,
            setOf(CommandKind.RETURN_TO_HOME),
            activeCommands = setOf(CommandKind.START_WAYPOINT_MISSION)
        )

        assertTrue(result.canDispatch)
    }

    @Test
    fun `orbit and follow parameters remain bounded`() {
        val airborne = healthyState().copy(
            aircraft = AircraftState(armed = true, flightMode = "FLYING"),
            navigation = healthyState().navigation.copy(altitudeM = 20.0)
        )
        val orbit = validator.validate(
            StartOrbitCommand(GeoPoint(35.0, -97.0), 20.0, 20.0, 5.0, true, laps = 0),
            airborne,
            setOf(CommandKind.START_ORBIT)
        )
        val follow = validator.validate(
            StartFollowCommand(20.0, 20.0, speedMps = 20.0),
            airborne,
            setOf(CommandKind.START_FOLLOW)
        )

        assertFalse(orbit.canDispatch)
        assertTrue(orbit.issues.any { it.message.contains("laps") })
        assertFalse(follow.canDispatch)
        assertTrue(follow.issues.any { it.message.contains("speed") })
    }

    @Test
    fun `ioc lock modes require valid navigation state`() {
        val airborne = healthyState().copy(
            aircraft = AircraftState(armed = true, flightMode = "FLYING"),
            navigation = healthyState().navigation.copy(
                altitudeM = 20.0,
                homeLatitudeDeg = null,
                homeLongitudeDeg = null
            )
        )
        val course = validator.validate(
            StartCourseLockCommand(-1.0),
            airborne,
            setOf(CommandKind.START_COURSE_LOCK)
        )
        val home = validator.validate(
            StartHomeLockCommand,
            airborne,
            setOf(CommandKind.START_HOME_LOCK)
        )

        assertFalse(course.canDispatch)
        assertTrue(course.issues.any { it.message.contains("heading") })
        assertFalse(home.canDispatch)
        assertTrue(home.issues.any { it.message.contains("Home Point") })
    }

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
