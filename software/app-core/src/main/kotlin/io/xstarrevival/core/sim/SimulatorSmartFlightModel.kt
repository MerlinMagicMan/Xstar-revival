package io.xstarrevival.core.sim

import io.xstarrevival.core.groundstation.GeoPoint
import io.xstarrevival.core.groundstation.SmartFlightExecutionState
import io.xstarrevival.core.groundstation.SmartFlightMode
import io.xstarrevival.core.groundstation.SmartFlightPhase
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

sealed interface SimulatorSmartFlightRuntime {
    val mode: SmartFlightMode
    val phase: SmartFlightPhase
    val detail: String
}

data class SimulatorRthRuntime(
    override val phase: SmartFlightPhase = SmartFlightPhase.ACTIVE,
    override val detail: String = "Climbing to Return-to-Home altitude",
    val returnAltitudeM: Double = 20.0,
    val initialDistanceM: Double = 0.0,
    val stage: SimulatorRthStage = SimulatorRthStage.CLIMBING
) : SimulatorSmartFlightRuntime {
    override val mode = SmartFlightMode.RETURN_TO_HOME
}

enum class SimulatorRthStage { CLIMBING, RETURNING, LANDING }

data class SimulatorOrbitRuntime(
    val center: GeoPoint,
    val radiusM: Double,
    val altitudeM: Double,
    val speedMps: Double,
    val clockwise: Boolean,
    val targetLaps: Int,
    val angleRad: Double,
    val sweptRadians: Double = 0.0,
    override val phase: SmartFlightPhase = SmartFlightPhase.ACTIVE,
    override val detail: String = "Entering orbit"
) : SimulatorSmartFlightRuntime {
    override val mode = SmartFlightMode.ORBIT
}

data class SimulatorFollowRuntime(
    val distanceM: Double,
    val altitudeM: Double,
    val speedMps: Double,
    val target: GeoPoint,
    override val phase: SmartFlightPhase = SmartFlightPhase.ACTIVE,
    override val detail: String = "Following operator target"
) : SimulatorSmartFlightRuntime {
    override val mode = SmartFlightMode.FOLLOW
}

data class SimulatorCourseLockRuntime(
    val headingDeg: Double,
    override val phase: SmartFlightPhase = SmartFlightPhase.ACTIVE,
    override val detail: String = "Course-relative controls active"
) : SimulatorSmartFlightRuntime {
    override val mode = SmartFlightMode.COURSE_LOCK
}

data class SimulatorHomeLockRuntime(
    val referenceHeadingDeg: Double,
    override val phase: SmartFlightPhase = SmartFlightPhase.ACTIVE,
    override val detail: String = "Home-relative controls active"
) : SimulatorSmartFlightRuntime {
    override val mode = SmartFlightMode.HOME_LOCK
}

data class SimulatorSmartFlightStep(
    val snapshot: SimulatorSnapshot,
    val runtime: SimulatorSmartFlightRuntime
)

object SimulatorSmartFlightModel {
    private const val HOME_LATITUDE = 41.8781
    private const val HOME_LONGITUDE = -87.6298
    private const val METERS_PER_LATITUDE_DEGREE = 111_111.0
    private const val METERS_PER_LONGITUDE_DEGREE = 83_000.0
    private const val MAX_VERTICAL_RATE_MPS = 3.0

    fun startRth(snapshot: SimulatorSnapshot) = SimulatorRthRuntime(
        returnAltitudeM = max(20.0, snapshot.altitudeM),
        initialDistanceM = hypot(snapshot.northM, snapshot.eastM)
    )

    fun startOrbit(
        snapshot: SimulatorSnapshot,
        center: GeoPoint,
        radiusM: Double,
        altitudeM: Double,
        speedMps: Double,
        clockwise: Boolean,
        laps: Int
    ): SimulatorOrbitRuntime {
        val centerNorth = north(center)
        val centerEast = east(center)
        val angle = atan2(snapshot.eastM - centerEast, snapshot.northM - centerNorth)
        return SimulatorOrbitRuntime(center, radiusM, altitudeM, speedMps, clockwise, laps, angle)
    }

    fun startFollow(distanceM: Double, altitudeM: Double, speedMps: Double, target: GeoPoint?) =
        SimulatorFollowRuntime(distanceM, altitudeM, speedMps, target ?: GeoPoint(HOME_LATITUDE, HOME_LONGITUDE))

    fun startCourseLock(headingDeg: Double) = SimulatorCourseLockRuntime(normalizeHeading(headingDeg))

    fun startHomeLock(snapshot: SimulatorSnapshot) = SimulatorHomeLockRuntime(snapshot.yawDeg)

    fun cancel(runtime: SimulatorSmartFlightRuntime, detail: String): SimulatorSmartFlightRuntime = when (runtime) {
        is SimulatorRthRuntime -> runtime.copy(phase = SmartFlightPhase.CANCELLED, detail = detail)
        is SimulatorOrbitRuntime -> runtime.copy(phase = SmartFlightPhase.CANCELLED, detail = detail)
        is SimulatorFollowRuntime -> runtime.copy(phase = SmartFlightPhase.CANCELLED, detail = detail)
        is SimulatorCourseLockRuntime -> runtime.copy(phase = SmartFlightPhase.CANCELLED, detail = detail)
        is SimulatorHomeLockRuntime -> runtime.copy(phase = SmartFlightPhase.CANCELLED, detail = detail)
    }

    fun fail(runtime: SimulatorSmartFlightRuntime, detail: String): SimulatorSmartFlightRuntime = when (runtime) {
        is SimulatorRthRuntime -> runtime.copy(phase = SmartFlightPhase.FAILED, detail = detail)
        is SimulatorOrbitRuntime -> runtime.copy(phase = SmartFlightPhase.FAILED, detail = detail)
        is SimulatorFollowRuntime -> runtime.copy(phase = SmartFlightPhase.FAILED, detail = detail)
        is SimulatorCourseLockRuntime -> runtime.copy(phase = SmartFlightPhase.FAILED, detail = detail)
        is SimulatorHomeLockRuntime -> runtime.copy(phase = SmartFlightPhase.FAILED, detail = detail)
    }

    fun step(snapshot: SimulatorSnapshot, runtime: SimulatorSmartFlightRuntime, dt: Double): SimulatorSmartFlightStep {
        if (runtime.phase != SmartFlightPhase.ACTIVE) return SimulatorSmartFlightStep(snapshot, runtime)
        if (snapshot.phase == SimulatorFlightPhase.GROUNDED) {
            if (runtime is SimulatorRthRuntime && hypot(snapshot.northM, snapshot.eastM) <= 0.75) {
                return SimulatorSmartFlightStep(
                    snapshot,
                    runtime.copy(phase = SmartFlightPhase.COMPLETED, detail = "Return-to-Home complete")
                )
            }
            return SimulatorSmartFlightStep(snapshot, fail(runtime, "Aircraft landed before smart-flight completion"))
        }
        return when (runtime) {
            is SimulatorRthRuntime -> stepRth(snapshot, runtime, dt)
            is SimulatorOrbitRuntime -> stepOrbit(snapshot, runtime, dt)
            is SimulatorFollowRuntime -> stepFollow(snapshot, runtime, dt)
            is SimulatorCourseLockRuntime,
            is SimulatorHomeLockRuntime -> SimulatorSmartFlightStep(snapshot, runtime)
        }
    }

    fun transformControls(
        snapshot: SimulatorSnapshot,
        runtime: SimulatorSmartFlightRuntime?,
        input: SimulatorControlInput
    ): SimulatorControlInput {
        if (runtime?.phase != SmartFlightPhase.ACTIVE) return input
        val referenceHeading = when (runtime) {
            is SimulatorCourseLockRuntime -> runtime.headingDeg
            is SimulatorHomeLockRuntime -> {
                if (hypot(snapshot.northM, snapshot.eastM) > 1.0) {
                    normalizeHeading(Math.toDegrees(atan2(snapshot.eastM, snapshot.northM)))
                } else {
                    runtime.referenceHeadingDeg
                }
            }
            else -> return input
        }
        val relativeHeading = Math.toRadians(referenceHeading - snapshot.yawDeg)
        return input.copy(
            pitch = input.pitch * cos(relativeHeading) - input.roll * sin(relativeHeading),
            roll = input.pitch * sin(relativeHeading) + input.roll * cos(relativeHeading)
        ).bounded()
    }

    fun state(snapshot: SimulatorSnapshot, runtime: SimulatorSmartFlightRuntime?): SmartFlightExecutionState {
        if (runtime == null) return SmartFlightExecutionState()
        return when (runtime) {
            is SimulatorRthRuntime -> SmartFlightExecutionState(
                mode = runtime.mode,
                phase = runtime.phase,
                progress = when {
                    runtime.phase == SmartFlightPhase.COMPLETED -> 1.0
                    runtime.stage == SimulatorRthStage.CLIMBING ->
                        (snapshot.altitudeM / runtime.returnAltitudeM * 0.2).coerceIn(0.0, 0.2)
                    runtime.stage == SimulatorRthStage.RETURNING -> {
                        val distance = hypot(snapshot.northM, snapshot.eastM)
                        0.2 + 0.6 * (1.0 - distance / runtime.initialDistanceM.coerceAtLeast(0.1)).coerceIn(0.0, 1.0)
                    }
                    else -> 0.8 + 0.2 * (1.0 - snapshot.altitudeM / runtime.returnAltitudeM).coerceIn(0.0, 1.0)
                },
                distanceToTargetM = hypot(snapshot.northM, snapshot.eastM),
                detail = runtime.detail
            )
            is SimulatorOrbitRuntime -> SmartFlightExecutionState(
                mode = runtime.mode,
                phase = runtime.phase,
                progress = (runtime.sweptRadians / (2 * PI * runtime.targetLaps)).coerceIn(0.0, 1.0),
                completedLaps = (runtime.sweptRadians / (2 * PI)).toInt().coerceAtMost(runtime.targetLaps),
                targetLaps = runtime.targetLaps,
                distanceToTargetM = abs(hypot(snapshot.northM - north(runtime.center), snapshot.eastM - east(runtime.center)) - runtime.radiusM),
                detail = runtime.detail
            )
            is SimulatorFollowRuntime -> SmartFlightExecutionState(
                mode = runtime.mode,
                phase = runtime.phase,
                distanceToTargetM = hypot(snapshot.northM - north(runtime.target), snapshot.eastM - east(runtime.target)),
                detail = runtime.detail
            )
            is SimulatorCourseLockRuntime -> SmartFlightExecutionState(
                mode = runtime.mode,
                phase = runtime.phase,
                detail = if (runtime.phase == SmartFlightPhase.ACTIVE) {
                    "Course locked to ${runtime.headingDeg.toInt()}° · yaw remains independent"
                } else {
                    runtime.detail
                }
            )
            is SimulatorHomeLockRuntime -> SmartFlightExecutionState(
                mode = runtime.mode,
                phase = runtime.phase,
                distanceToTargetM = hypot(snapshot.northM, snapshot.eastM),
                detail = if (runtime.phase == SmartFlightPhase.ACTIVE) {
                    "Pitch controls away/toward Home · roll controls orbit direction"
                } else {
                    runtime.detail
                }
            )
        }
    }

    private fun stepRth(snapshot: SimulatorSnapshot, runtime: SimulatorRthRuntime, dt: Double): SimulatorSmartFlightStep {
        return when (runtime.stage) {
            SimulatorRthStage.CLIMBING -> {
                if (snapshot.altitudeM + 0.2 < runtime.returnAltitudeM) {
                    val climbed = moveAltitude(snapshot, runtime.returnAltitudeM, dt).copy(groundSpeedMps = 0.0)
                    SimulatorSmartFlightStep(climbed, runtime.copy(detail = "Climbing to ${runtime.returnAltitudeM.toInt()} m RTH altitude"))
                } else {
                    SimulatorSmartFlightStep(snapshot, runtime.copy(stage = SimulatorRthStage.RETURNING, detail = "Returning to Home Point"))
                }
            }
            SimulatorRthStage.RETURNING -> {
                val distance = hypot(snapshot.northM, snapshot.eastM)
                if (distance > 0.75) {
                    SimulatorSmartFlightStep(moveHorizontal(snapshot, 0.0, 0.0, 8.0, dt), runtime.copy(detail = "Returning to Home Point"))
                } else {
                    SimulatorSmartFlightStep(
                        SimulatorFlightModel.land(snapshot.copy(northM = 0.0, eastM = 0.0)),
                        runtime.copy(stage = SimulatorRthStage.LANDING, detail = "Landing at Home Point")
                    )
                }
            }
            SimulatorRthStage.LANDING -> {
                if (snapshot.altitudeM <= 0.0 || snapshot.phase == SimulatorFlightPhase.GROUNDED) {
                    SimulatorSmartFlightStep(snapshot, runtime.copy(phase = SmartFlightPhase.COMPLETED, detail = "Return-to-Home complete"))
                } else {
                    SimulatorSmartFlightStep(snapshot, runtime)
                }
            }
        }
    }

    private fun stepOrbit(snapshot: SimulatorSnapshot, runtime: SimulatorOrbitRuntime, dt: Double): SimulatorSmartFlightStep {
        val centerNorth = north(runtime.center)
        val centerEast = east(runtime.center)
        val direction = if (runtime.clockwise) -1.0 else 1.0
        val angularStep = direction * runtime.speedMps / runtime.radiusM * dt
        val nextAngle = runtime.angleRad + angularStep
        val targetNorth = centerNorth + cos(nextAngle) * runtime.radiusM
        val targetEast = centerEast + sin(nextAngle) * runtime.radiusM
        val moved = moveHorizontal(moveAltitude(snapshot, runtime.altitudeM, dt), targetNorth, targetEast, runtime.speedMps, dt)
            .copy(yawDeg = normalizeHeading(Math.toDegrees(nextAngle + if (runtime.clockwise) -PI / 2 else PI / 2)))
        val swept = runtime.sweptRadians + abs(angularStep)
        val complete = swept >= 2 * PI * runtime.targetLaps
        return SimulatorSmartFlightStep(
            moved.copy(groundSpeedMps = if (complete) 0.0 else runtime.speedMps),
            runtime.copy(
                angleRad = nextAngle,
                sweptRadians = swept,
                phase = if (complete) SmartFlightPhase.COMPLETED else SmartFlightPhase.ACTIVE,
                detail = if (complete) "Orbit complete" else "Orbiting point of interest"
            )
        )
    }

    private fun stepFollow(snapshot: SimulatorSnapshot, runtime: SimulatorFollowRuntime, dt: Double): SimulatorSmartFlightStep {
        val targetNorth = north(runtime.target) - runtime.distanceM
        val targetEast = east(runtime.target)
        val moved = moveHorizontal(moveAltitude(snapshot, runtime.altitudeM, dt), targetNorth, targetEast, runtime.speedMps, dt)
        return SimulatorSmartFlightStep(moved, runtime.copy(detail = "Maintaining ${runtime.distanceM.toInt()} m follow distance"))
    }

    private fun moveHorizontal(snapshot: SimulatorSnapshot, north: Double, east: Double, speed: Double, dt: Double): SimulatorSnapshot {
        val northDelta = north - snapshot.northM
        val eastDelta = east - snapshot.eastM
        val distance = hypot(northDelta, eastDelta)
        if (distance <= 0.05) return snapshot.copy(northM = north, eastM = east, groundSpeedMps = 0.0)
        val amount = min(distance, speed * dt)
        return snapshot.copy(
            northM = snapshot.northM + northDelta / distance * amount,
            eastM = snapshot.eastM + eastDelta / distance * amount,
            groundSpeedMps = if (amount < distance) speed else 0.0,
            yawDeg = normalizeHeading(Math.toDegrees(atan2(eastDelta, northDelta)))
        )
    }

    private fun moveAltitude(snapshot: SimulatorSnapshot, altitude: Double, dt: Double): SimulatorSnapshot {
        val delta = altitude - snapshot.altitudeM
        val amount = delta.coerceIn(-MAX_VERTICAL_RATE_MPS * dt, MAX_VERTICAL_RATE_MPS * dt)
        return snapshot.copy(
            altitudeM = (snapshot.altitudeM + amount).coerceIn(0.0, 120.0),
            verticalSpeedMps = if (dt > 0.0) amount / dt else 0.0
        )
    }

    private fun north(point: GeoPoint) = (point.latitudeDeg - HOME_LATITUDE) * METERS_PER_LATITUDE_DEGREE
    private fun east(point: GeoPoint) = (point.longitudeDeg - HOME_LONGITUDE) * METERS_PER_LONGITUDE_DEGREE
    private fun normalizeHeading(value: Double) = (value % 360.0 + 360.0) % 360.0
}
