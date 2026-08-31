package io.xstarrevival.core.sim

import io.xstarrevival.core.groundstation.MissionExecutionPhase
import io.xstarrevival.core.groundstation.MissionExecutionState
import io.xstarrevival.core.groundstation.MissionFinishBehavior
import io.xstarrevival.core.groundstation.MissionPlan
import io.xstarrevival.core.groundstation.MissionWaypoint
import io.xstarrevival.core.groundstation.SmartFlightPhase
import io.xstarrevival.core.groundstation.WaypointActionType
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

data class SimulatorMissionRuntime(
    val plan: MissionPlan,
    val phase: MissionExecutionPhase = MissionExecutionPhase.ACTIVE,
    val waypointIndex: Int = 0,
    val holdRemainingSeconds: Double? = null,
    val returnHomeRuntime: SimulatorRthRuntime? = null,
    val detail: String? = "Mission accepted"
)

data class SimulatorMissionStep(
    val snapshot: SimulatorSnapshot,
    val runtime: SimulatorMissionRuntime
)

object SimulatorMissionModel {
    val supportedWaypointActions = setOf(
        WaypointActionType.TAKE_PHOTO,
        WaypointActionType.START_VIDEO,
        WaypointActionType.STOP_VIDEO,
        WaypointActionType.ROTATE,
        WaypointActionType.SET_GIMBAL
    )
    private const val HOME_LATITUDE = 41.8781
    private const val HOME_LONGITUDE = -87.6298
    private const val METERS_PER_LATITUDE_DEGREE = 111_111.0
    private const val METERS_PER_LONGITUDE_DEGREE = 83_000.0
    private const val ARRIVAL_HORIZONTAL_M = 0.75
    private const val ARRIVAL_ALTITUDE_M = 0.35
    private const val MAX_MISSION_CLIMB_RATE_MPS = 3.0

    fun start(plan: MissionPlan) = SimulatorMissionRuntime(plan = plan)

    fun pause(runtime: SimulatorMissionRuntime): SimulatorMissionRuntime? =
        runtime.takeIf { it.phase == MissionExecutionPhase.ACTIVE && it.returnHomeRuntime == null }
            ?.copy(phase = MissionExecutionPhase.PAUSED, detail = "Mission paused")

    fun resume(runtime: SimulatorMissionRuntime): SimulatorMissionRuntime? =
        runtime.takeIf { it.phase == MissionExecutionPhase.PAUSED }
            ?.copy(phase = MissionExecutionPhase.ACTIVE, detail = "Mission resumed")

    fun abort(runtime: SimulatorMissionRuntime, detail: String = "Mission aborted") = runtime.copy(
        phase = MissionExecutionPhase.ABORTED,
        returnHomeRuntime = runtime.returnHomeRuntime?.copy(phase = SmartFlightPhase.CANCELLED, detail = detail),
        detail = detail
    )

    fun fail(runtime: SimulatorMissionRuntime, detail: String) = runtime.copy(
        phase = MissionExecutionPhase.FAILED,
        returnHomeRuntime = runtime.returnHomeRuntime?.copy(phase = SmartFlightPhase.FAILED, detail = detail),
        detail = detail
    )

    fun step(snapshot: SimulatorSnapshot, runtime: SimulatorMissionRuntime, deltaSeconds: Double): SimulatorMissionStep {
        if (runtime.phase != MissionExecutionPhase.ACTIVE) return SimulatorMissionStep(snapshot, runtime)
        runtime.returnHomeRuntime?.let { return stepReturnHome(snapshot, runtime, it, deltaSeconds) }
        if (snapshot.phase == SimulatorFlightPhase.GROUNDED || snapshot.phase == SimulatorFlightPhase.ARMED) {
            return SimulatorMissionStep(
                SimulatorFlightModel.takeOff(snapshot),
                runtime.copy(detail = "Taking off for waypoint 1")
            )
        }
        if (snapshot.phase != SimulatorFlightPhase.FLYING) return SimulatorMissionStep(snapshot, runtime)

        val waypoint = runtime.plan.waypoints.getOrNull(runtime.waypointIndex)
            ?: return finish(snapshot, runtime)
        runtime.holdRemainingSeconds?.let { remaining ->
            val nextRemaining = remaining - deltaSeconds
            return if (nextRemaining > 0.0) {
                SimulatorMissionStep(snapshot.copy(groundSpeedMps = 0.0, verticalSpeedMps = 0.0), runtime.copy(holdRemainingSeconds = nextRemaining))
            } else {
                advanceWaypoint(applyActions(snapshot, waypoint), runtime)
            }
        }

        val targetNorth = (waypoint.position.latitudeDeg - HOME_LATITUDE) * METERS_PER_LATITUDE_DEGREE
        val targetEast = (waypoint.position.longitudeDeg - HOME_LONGITUDE) * METERS_PER_LONGITUDE_DEGREE
        val northDelta = targetNorth - snapshot.northM
        val eastDelta = targetEast - snapshot.eastM
        val horizontalDistance = hypot(northDelta, eastDelta)
        val altitudeDelta = waypoint.altitudeM - snapshot.altitudeM
        if (horizontalDistance <= ARRIVAL_HORIZONTAL_M && kotlin.math.abs(altitudeDelta) <= ARRIVAL_ALTITUDE_M) {
            val arrived = snapshot.copy(
                northM = targetNorth,
                eastM = targetEast,
                altitudeM = waypoint.altitudeM,
                groundSpeedMps = 0.0,
                verticalSpeedMps = 0.0,
                gimbalPitchDeg = waypoint.gimbalPitchDeg?.coerceIn(-90.0, 30.0) ?: snapshot.gimbalPitchDeg
            )
            return if (waypoint.delaySeconds > 0.0) {
                SimulatorMissionStep(
                    arrived,
                    runtime.copy(holdRemainingSeconds = waypoint.delaySeconds, detail = "Holding at waypoint ${runtime.waypointIndex + 1}")
                )
            } else {
                advanceWaypoint(applyActions(arrived, waypoint), runtime)
            }
        }

        val horizontalStep = min(horizontalDistance, waypoint.speedMps * deltaSeconds)
        val north = if (horizontalDistance > 0.0) snapshot.northM + northDelta / horizontalDistance * horizontalStep else snapshot.northM
        val east = if (horizontalDistance > 0.0) snapshot.eastM + eastDelta / horizontalDistance * horizontalStep else snapshot.eastM
        val verticalStep = (altitudeDelta.coerceIn(-MAX_MISSION_CLIMB_RATE_MPS * deltaSeconds, MAX_MISSION_CLIMB_RATE_MPS * deltaSeconds))
        val heading = if (horizontalDistance > ARRIVAL_HORIZONTAL_M) {
            normalizeHeading(Math.toDegrees(atan2(eastDelta, northDelta)))
        } else {
            waypoint.headingDeg ?: snapshot.yawDeg
        }
        val updated = snapshot.copy(
            northM = north,
            eastM = east,
            altitudeM = (snapshot.altitudeM + verticalStep).coerceIn(0.0, 120.0),
            groundSpeedMps = if (horizontalDistance > ARRIVAL_HORIZONTAL_M) waypoint.speedMps else 0.0,
            verticalSpeedMps = if (deltaSeconds > 0.0) verticalStep / deltaSeconds else 0.0,
            yawDeg = normalizeHeading(heading),
            gimbalPitchDeg = waypoint.gimbalPitchDeg?.coerceIn(-90.0, 30.0) ?: snapshot.gimbalPitchDeg
        )
        return SimulatorMissionStep(updated, runtime.copy(detail = "Flying to waypoint ${runtime.waypointIndex + 1}"))
    }

    fun state(snapshot: SimulatorSnapshot, runtime: SimulatorMissionRuntime?): MissionExecutionState {
        if (runtime == null) return MissionExecutionState()
        val hasReturnHome = runtime.returnHomeRuntime != null
        val returningHome = runtime.returnHomeRuntime?.phase == SmartFlightPhase.ACTIVE
        val index = runtime.waypointIndex.coerceAtMost(runtime.plan.waypoints.lastIndex.coerceAtLeast(0))
        val remaining = if (hasReturnHome) hypot(snapshot.northM, snapshot.eastM) else remainingDistance(snapshot, runtime.plan, index)
        val returnProgress = runtime.returnHomeRuntime?.let { SimulatorSmartFlightModel.state(snapshot, it).progress }
        val progress = when (runtime.phase) {
            MissionExecutionPhase.COMPLETED -> 1.0
            else -> returnProgress?.let { 0.9 + it * 0.1 }
                ?: if (runtime.plan.waypoints.isEmpty()) 0.0 else index.toDouble() / runtime.plan.waypoints.size
        }
        return MissionExecutionState(
            missionId = runtime.plan.id,
            missionName = runtime.plan.name,
            phase = runtime.phase,
            currentWaypoint = (index + 1).takeIf {
                !hasReturnHome && runtime.plan.waypoints.isNotEmpty() && runtime.phase !in TERMINAL_PHASES
            },
            nextWaypoint = (index + 2).takeIf {
                !hasReturnHome && it <= runtime.plan.waypoints.size && runtime.phase !in TERMINAL_PHASES
            },
            waypointCount = runtime.plan.waypoints.size,
            minimumBatteryReservePercent = runtime.plan.minimumBatteryReservePercent,
            returningHome = returningHome,
            progress = progress.coerceIn(0.0, 1.0),
            remainingDistanceM = if (runtime.phase == MissionExecutionPhase.COMPLETED) 0.0 else remaining,
            etaSeconds = if (hasReturnHome) remaining / 8.0 + snapshot.altitudeM / 0.8 else estimateEtaSeconds(snapshot, runtime.plan, index),
            detail = runtime.detail
        )
    }

    private fun advanceWaypoint(snapshot: SimulatorSnapshot, runtime: SimulatorMissionRuntime): SimulatorMissionStep {
        val nextIndex = runtime.waypointIndex + 1
        return if (nextIndex >= runtime.plan.waypoints.size) {
            finish(snapshot, runtime)
        } else {
            SimulatorMissionStep(
                snapshot,
                runtime.copy(waypointIndex = nextIndex, holdRemainingSeconds = null, detail = "Advancing to waypoint ${nextIndex + 1}")
            )
        }
    }

    private fun finish(snapshot: SimulatorSnapshot, runtime: SimulatorMissionRuntime): SimulatorMissionStep {
        if (runtime.plan.finishBehavior == MissionFinishBehavior.RETURN_HOME) {
            return SimulatorMissionStep(
                snapshot.copy(groundSpeedMps = 0.0, verticalSpeedMps = 0.0),
                runtime.copy(
                    waypointIndex = runtime.plan.waypoints.size,
                    holdRemainingSeconds = null,
                    returnHomeRuntime = SimulatorSmartFlightModel.startRth(snapshot),
                    detail = "Waypoint route complete · starting Return-to-Home"
                )
            )
        }
        val finishedSnapshot = when (runtime.plan.finishBehavior) {
            MissionFinishBehavior.LAND -> SimulatorFlightModel.land(snapshot)
            MissionFinishBehavior.HOVER -> snapshot.copy(groundSpeedMps = 0.0, verticalSpeedMps = 0.0)
            MissionFinishBehavior.RETURN_HOME -> snapshot
        }
        return SimulatorMissionStep(
            finishedSnapshot,
            runtime.copy(
                phase = MissionExecutionPhase.COMPLETED,
                waypointIndex = runtime.plan.waypoints.size,
                holdRemainingSeconds = null,
                detail = "Mission complete"
            )
        )
    }

    private fun stepReturnHome(
        snapshot: SimulatorSnapshot,
        runtime: SimulatorMissionRuntime,
        returnHome: SimulatorRthRuntime,
        deltaSeconds: Double
    ): SimulatorMissionStep {
        val result = SimulatorSmartFlightModel.step(snapshot, returnHome, deltaSeconds)
        val rth = result.runtime as SimulatorRthRuntime
        val missionPhase = when (rth.phase) {
            SmartFlightPhase.COMPLETED -> MissionExecutionPhase.COMPLETED
            SmartFlightPhase.CANCELLED -> MissionExecutionPhase.ABORTED
            SmartFlightPhase.FAILED -> MissionExecutionPhase.FAILED
            else -> MissionExecutionPhase.ACTIVE
        }
        val detail = when (missionPhase) {
            MissionExecutionPhase.COMPLETED -> "Mission complete · Return-to-Home landed"
            MissionExecutionPhase.ABORTED -> rth.detail
            MissionExecutionPhase.FAILED -> rth.detail
            else -> "Mission finish · ${rth.detail}"
        }
        return SimulatorMissionStep(
            result.snapshot,
            runtime.copy(
                phase = missionPhase,
                waypointIndex = runtime.plan.waypoints.size,
                returnHomeRuntime = rth,
                detail = detail
            )
        )
    }

    private fun applyActions(snapshot: SimulatorSnapshot, waypoint: MissionWaypoint): SimulatorSnapshot {
        var result = snapshot
        waypoint.actions.forEach { action ->
            result = when (action.type) {
                WaypointActionType.START_VIDEO -> result.copy(recording = true)
                WaypointActionType.STOP_VIDEO -> result.copy(recording = false)
                WaypointActionType.SET_GIMBAL -> result.copy(gimbalPitchDeg = (action.value ?: result.gimbalPitchDeg).coerceIn(-90.0, 30.0))
                WaypointActionType.ROTATE -> result.copy(yawDeg = normalizeHeading(action.value ?: result.yawDeg))
                WaypointActionType.TAKE_PHOTO, WaypointActionType.HOVER, WaypointActionType.SET_SPEED -> result
            }
        }
        return result
    }

    private fun remainingDistance(snapshot: SimulatorSnapshot, plan: MissionPlan, fromIndex: Int): Double {
        if (plan.waypoints.isEmpty() || fromIndex !in plan.waypoints.indices) return 0.0
        var north = snapshot.northM
        var east = snapshot.eastM
        var distance = 0.0
        plan.waypoints.drop(fromIndex).forEach { waypoint ->
            val targetNorth = (waypoint.position.latitudeDeg - HOME_LATITUDE) * METERS_PER_LATITUDE_DEGREE
            val targetEast = (waypoint.position.longitudeDeg - HOME_LONGITUDE) * METERS_PER_LONGITUDE_DEGREE
            distance += hypot(targetNorth - north, targetEast - east)
            north = targetNorth
            east = targetEast
        }
        return distance
    }

    private fun estimateEtaSeconds(snapshot: SimulatorSnapshot, plan: MissionPlan, fromIndex: Int): Double? {
        if (plan.waypoints.isEmpty() || fromIndex !in plan.waypoints.indices) return 0.0
        val remaining = remainingDistance(snapshot, plan, fromIndex)
        val speed = plan.waypoints.drop(fromIndex).map { it.speedMps }.average().takeIf { it > 0.0 } ?: return null
        return remaining / speed + plan.waypoints.drop(fromIndex).sumOf { it.delaySeconds }
    }

    private fun normalizeHeading(value: Double): Double = (value % 360.0 + 360.0) % 360.0

    private val TERMINAL_PHASES = setOf(
        MissionExecutionPhase.COMPLETED,
        MissionExecutionPhase.ABORTED,
        MissionExecutionPhase.FAILED
    )
}
