package io.xstarrevival.core.sim

import io.xstarrevival.core.model.AircraftState
import io.xstarrevival.core.model.AttitudeState
import io.xstarrevival.core.model.BatteryState
import io.xstarrevival.core.model.CameraState
import io.xstarrevival.core.model.CellState
import io.xstarrevival.core.model.ConnectionState
import io.xstarrevival.core.model.DiagnosticsState
import io.xstarrevival.core.model.GimbalState
import io.xstarrevival.core.model.NavigationState
import io.xstarrevival.core.model.RemoteState
import io.xstarrevival.core.model.VideoState
import io.xstarrevival.core.model.XStarState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Normalized inputs used only by the in-app simulator. No hardware transport consumes this type. */
data class SimulatorControlInput(
    val throttle: Double = 0.0,
    val yaw: Double = 0.0,
    val pitch: Double = 0.0,
    val roll: Double = 0.0,
    val gimbal: Double = 0.0
) {
    fun bounded() = copy(
        throttle = throttle.coerceIn(-1.0, 1.0),
        yaw = yaw.coerceIn(-1.0, 1.0),
        pitch = pitch.coerceIn(-1.0, 1.0),
        roll = roll.coerceIn(-1.0, 1.0),
        gimbal = gimbal.coerceIn(-1.0, 1.0)
    )
}

enum class SimulatorFlightPhase { GROUNDED, ARMED, TAKING_OFF, FLYING, LANDING }

data class SimulatorSnapshot(
    val phase: SimulatorFlightPhase = SimulatorFlightPhase.GROUNDED,
    val northM: Double = 0.0,
    val eastM: Double = 0.0,
    val altitudeM: Double = 0.0,
    val verticalSpeedMps: Double = 0.0,
    val groundSpeedMps: Double = 0.0,
    val rollDeg: Double = 0.0,
    val pitchDeg: Double = 0.0,
    val yawDeg: Double = 0.0,
    val gimbalPitchDeg: Double = -15.0,
    val batteryPercent: Double = 88.0,
    val recording: Boolean = false,
    val elapsedSeconds: Double = 0.0
)

/**
 * Small deterministic flight model for UI testing. It cannot address USB, radio, SDK, or aircraft APIs.
 */
object SimulatorFlightModel {
    private const val TAKEOFF_ALTITUDE_M = 2.0
    private const val TAKEOFF_RATE_MPS = 1.2
    private const val LANDING_RATE_MPS = 0.8
    private const val MAX_CLIMB_RATE_MPS = 3.0
    private const val MAX_HORIZONTAL_SPEED_MPS = 9.0
    private const val MAX_TILT_DEG = 28.0
    private const val MAX_YAW_RATE_DPS = 90.0
    private const val GIMBAL_RATE_DPS = 35.0

    fun arm(snapshot: SimulatorSnapshot): SimulatorSnapshot = when (snapshot.phase) {
        SimulatorFlightPhase.GROUNDED -> snapshot.copy(phase = SimulatorFlightPhase.ARMED)
        else -> snapshot
    }

    fun disarm(snapshot: SimulatorSnapshot): SimulatorSnapshot = when (snapshot.phase) {
        SimulatorFlightPhase.ARMED -> snapshot.copy(phase = SimulatorFlightPhase.GROUNDED)
        else -> snapshot
    }

    fun toggleArm(snapshot: SimulatorSnapshot): SimulatorSnapshot = when (snapshot.phase) {
        SimulatorFlightPhase.GROUNDED -> arm(snapshot)
        SimulatorFlightPhase.ARMED -> disarm(snapshot)
        else -> snapshot
    }

    fun takeOff(snapshot: SimulatorSnapshot): SimulatorSnapshot = when (snapshot.phase) {
        SimulatorFlightPhase.GROUNDED,
        SimulatorFlightPhase.ARMED -> snapshot.copy(phase = SimulatorFlightPhase.TAKING_OFF)
        else -> snapshot
    }

    fun land(snapshot: SimulatorSnapshot): SimulatorSnapshot = when (snapshot.phase) {
        SimulatorFlightPhase.TAKING_OFF,
        SimulatorFlightPhase.FLYING -> snapshot.copy(phase = SimulatorFlightPhase.LANDING)
        else -> snapshot
    }

    fun toggleRecording(snapshot: SimulatorSnapshot): SimulatorSnapshot =
        snapshot.copy(recording = !snapshot.recording)

    fun setRecording(snapshot: SimulatorSnapshot, recording: Boolean): SimulatorSnapshot =
        snapshot.copy(recording = recording)

    fun setGimbalPitch(snapshot: SimulatorSnapshot, pitchDeg: Double): SimulatorSnapshot =
        snapshot.copy(gimbalPitchDeg = pitchDeg.coerceIn(-90.0, 30.0))

    fun step(
        snapshot: SimulatorSnapshot,
        rawInput: SimulatorControlInput,
        deltaSeconds: Double
    ): SimulatorSnapshot {
        val dt = deltaSeconds.coerceIn(0.0, 0.25)
        if (dt == 0.0) return snapshot
        val input = rawInput.bounded()
        val airborne = snapshot.phase in setOf(
            SimulatorFlightPhase.TAKING_OFF,
            SimulatorFlightPhase.FLYING,
            SimulatorFlightPhase.LANDING
        )

        val targetRoll = if (snapshot.phase == SimulatorFlightPhase.FLYING) input.roll * MAX_TILT_DEG else 0.0
        val targetPitch = if (snapshot.phase == SimulatorFlightPhase.FLYING) -input.pitch * MAX_TILT_DEG else 0.0
        val roll = approach(snapshot.rollDeg, targetRoll, 75.0 * dt)
        val pitch = approach(snapshot.pitchDeg, targetPitch, 75.0 * dt)
        val yaw = if (snapshot.phase == SimulatorFlightPhase.FLYING) {
            normalizeHeading(snapshot.yawDeg + input.yaw * MAX_YAW_RATE_DPS * dt)
        } else {
            snapshot.yawDeg
        }

        val horizontalSpeed = if (snapshot.phase == SimulatorFlightPhase.FLYING) {
            hypot(input.pitch, input.roll).coerceAtMost(1.0) * MAX_HORIZONTAL_SPEED_MPS
        } else {
            0.0
        }
        val headingRad = Math.toRadians(yaw)
        val forward = if (snapshot.phase == SimulatorFlightPhase.FLYING) input.pitch * MAX_HORIZONTAL_SPEED_MPS else 0.0
        val right = if (snapshot.phase == SimulatorFlightPhase.FLYING) input.roll * MAX_HORIZONTAL_SPEED_MPS else 0.0
        val northVelocity = forward * cos(headingRad) - right * sin(headingRad)
        val eastVelocity = forward * sin(headingRad) + right * cos(headingRad)

        val requestedVerticalSpeed = when (snapshot.phase) {
            SimulatorFlightPhase.TAKING_OFF -> TAKEOFF_RATE_MPS
            SimulatorFlightPhase.FLYING -> input.throttle * MAX_CLIMB_RATE_MPS
            SimulatorFlightPhase.LANDING -> -LANDING_RATE_MPS
            else -> 0.0
        }
        var altitude = (snapshot.altitudeM + requestedVerticalSpeed * dt).coerceIn(0.0, 120.0)
        var phase = snapshot.phase
        var verticalSpeed = requestedVerticalSpeed
        if (phase == SimulatorFlightPhase.TAKING_OFF && altitude >= TAKEOFF_ALTITUDE_M) {
            altitude = TAKEOFF_ALTITUDE_M
            verticalSpeed = 0.0
            phase = SimulatorFlightPhase.FLYING
        }
        if (phase == SimulatorFlightPhase.LANDING && altitude <= 0.0) {
            altitude = 0.0
            verticalSpeed = 0.0
            phase = SimulatorFlightPhase.GROUNDED
        }
        if (phase == SimulatorFlightPhase.FLYING && altitude <= 0.0 && requestedVerticalSpeed < 0.0) {
            altitude = 0.0
            verticalSpeed = 0.0
            phase = SimulatorFlightPhase.GROUNDED
        }
        if (!airborne) altitude = 0.0

        val gimbalPitch = (snapshot.gimbalPitchDeg + input.gimbal * GIMBAL_RATE_DPS * dt)
            .coerceIn(-90.0, 30.0)
        val load = if (airborne) 0.010 + horizontalSpeed * 0.0015 + abs(verticalSpeed) * 0.002 else 0.001

        return snapshot.copy(
            phase = phase,
            northM = snapshot.northM + northVelocity * dt,
            eastM = snapshot.eastM + eastVelocity * dt,
            altitudeM = altitude,
            verticalSpeedMps = verticalSpeed,
            groundSpeedMps = horizontalSpeed,
            rollDeg = roll,
            pitchDeg = pitch,
            yawDeg = yaw,
            gimbalPitchDeg = gimbalPitch,
            batteryPercent = (snapshot.batteryPercent - load * dt).coerceAtLeast(0.0),
            elapsedSeconds = snapshot.elapsedSeconds + dt
        )
    }

    fun toXStarState(snapshot: SimulatorSnapshot): XStarState {
        val battery = snapshot.batteryPercent.toInt().coerceIn(0, 100)
        val cellVoltage = 3.55 + 0.65 * snapshot.batteryPercent / 100.0
        val armed = snapshot.phase != SimulatorFlightPhase.GROUNDED
        return XStarState(
            connection = ConnectionState.Connected("local simulator", "Virtual X-Star Premium"),
            aircraft = AircraftState(
                productName = "Virtual X-Star Premium",
                firmwareVersion = "sim-1",
                armed = armed,
                flightMode = snapshot.phase.name.replace('_', ' ')
            ),
            battery = BatteryState(
                percent = battery,
                packVoltageV = cellVoltage * 4,
                currentA = if (armed) 8.0 + snapshot.groundSpeedMps else 0.4,
                temperatureC = 27.0 + if (armed) 4.0 else 0.0,
                fullCapacityMah = 4900,
                remainingCapacityMah = (4900 * snapshot.batteryPercent / 100.0).toInt(),
                cells = List(4) { CellState(it + 1, cellVoltage + (it - 1.5) * 0.001) }
            ),
            navigation = NavigationState(
                latitudeDeg = 41.8781 + snapshot.northM / 111_111.0,
                longitudeDeg = -87.6298 + snapshot.eastM / 83_000.0,
                homeLatitudeDeg = 41.8781,
                homeLongitudeDeg = -87.6298,
                satellites = 18,
                gpsFix = "SIM 3D FIX",
                altitudeM = snapshot.altitudeM,
                groundSpeedMps = snapshot.groundSpeedMps,
                verticalSpeedMps = snapshot.verticalSpeedMps,
                ultrasonicHeightM = snapshot.altitudeM.takeIf { it <= 6.0 }
            ),
            attitude = AttitudeState(snapshot.rollDeg, snapshot.pitchDeg, snapshot.yawDeg),
            remote = RemoteState(connected = true, signalPercent = 100, batteryPercent = 100, imageSignalPercent = 100),
            camera = CameraState(
                connected = true,
                mode = "VIDEO",
                recording = snapshot.recording,
                video = VideoState(receiving = true, codec = "SYNTHETIC")
            ),
            gimbal = GimbalState(pitchDeg = snapshot.gimbalPitchDeg, status = "SIMULATED"),
            diagnostics = DiagnosticsState(
                source = "local-simulator",
                counters = mapOf("sim_steps" to (snapshot.elapsedSeconds * 20.0).toLong()),
                notes = listOf("Software-only simulator; no hardware command path")
            )
        )
    }

    private fun approach(value: Double, target: Double, maximumChange: Double): Double = when {
        value < target -> (value + maximumChange).coerceAtMost(target)
        value > target -> (value - maximumChange).coerceAtLeast(target)
        else -> value
    }

    private fun normalizeHeading(value: Double): Double = (value % 360.0 + 360.0) % 360.0
}
