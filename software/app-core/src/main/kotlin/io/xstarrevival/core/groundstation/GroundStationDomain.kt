package io.xstarrevival.core.groundstation

import io.xstarrevival.core.model.BatteryState
import io.xstarrevival.core.model.XStarState
import kotlin.math.max
import kotlin.math.min

data class GeoPoint(val latitudeDeg: Double, val longitudeDeg: Double)

enum class MissionFinishBehavior { HOVER, RETURN_HOME, LAND }
enum class MissionLostLinkBehavior { CONTINUE, RETURN_HOME, HOVER }
enum class WaypointHeadingMode { NEXT_WAYPOINT, FIXED, POI }
enum class WaypointActionType { TAKE_PHOTO, START_VIDEO, STOP_VIDEO, HOVER, ROTATE, SET_GIMBAL, SET_SPEED }

data class WaypointAction(
    val type: WaypointActionType,
    val value: Double? = null,
    val durationSeconds: Double? = null
)

data class MissionWaypoint(
    val id: String,
    val position: GeoPoint,
    val altitudeM: Double,
    val speedMps: Double,
    val headingMode: WaypointHeadingMode = WaypointHeadingMode.NEXT_WAYPOINT,
    val headingDeg: Double? = null,
    val gimbalPitchDeg: Double? = null,
    val delaySeconds: Double = 0.0,
    val actions: List<WaypointAction> = emptyList()
)

data class MissionPlan(
    val id: String,
    val name: String,
    val waypoints: List<MissionWaypoint>,
    val finishBehavior: MissionFinishBehavior = MissionFinishBehavior.HOVER,
    val lostLinkBehavior: MissionLostLinkBehavior = MissionLostLinkBehavior.RETURN_HOME,
    val minimumBatteryReservePercent: Int = 25
)

enum class MissionIssueSeverity { INFO, WARNING, BLOCKING }

data class MissionIssue(val severity: MissionIssueSeverity, val message: String)

data class MissionValidation(
    val issues: List<MissionIssue>,
    val canExecute: Boolean = issues.none { it.severity == MissionIssueSeverity.BLOCKING }
)

object MissionValidator {
    fun validate(plan: MissionPlan, aircraft: XStarState? = null): MissionValidation {
        val issues = mutableListOf<MissionIssue>()
        if (plan.name.isBlank()) issues += MissionIssue(MissionIssueSeverity.BLOCKING, "Mission name is required")
        if (plan.waypoints.isEmpty()) issues += MissionIssue(MissionIssueSeverity.BLOCKING, "Add at least one waypoint")
        if (plan.waypoints.size > 15) issues += MissionIssue(MissionIssueSeverity.WARNING, "Legacy X-Star mission compatibility is safest at 15 waypoints or fewer")
        if (plan.minimumBatteryReservePercent !in 10..80) issues += MissionIssue(MissionIssueSeverity.BLOCKING, "Battery reserve must be between 10% and 80%")

        plan.waypoints.forEachIndexed { index, waypoint ->
            val n = index + 1
            if (waypoint.position.latitudeDeg !in -90.0..90.0 || waypoint.position.longitudeDeg !in -180.0..180.0) {
                issues += MissionIssue(MissionIssueSeverity.BLOCKING, "Waypoint $n has invalid coordinates")
            }
            if (waypoint.altitudeM !in 2.0..500.0) issues += MissionIssue(MissionIssueSeverity.BLOCKING, "Waypoint $n altitude is outside 2–500 m")
            if (waypoint.speedMps !in 0.5..20.0) issues += MissionIssue(MissionIssueSeverity.BLOCKING, "Waypoint $n speed is outside 0.5–20 m/s")
            if (waypoint.delaySeconds !in 0.0..300.0) issues += MissionIssue(MissionIssueSeverity.BLOCKING, "Waypoint $n delay is outside 0–300 s")
            waypoint.gimbalPitchDeg?.let {
                if (it !in -90.0..30.0) issues += MissionIssue(MissionIssueSeverity.BLOCKING, "Waypoint $n gimbal pitch is outside -90° to +30°")
            }
            waypoint.headingDeg?.let {
                if (it !in 0.0..360.0) issues += MissionIssue(MissionIssueSeverity.BLOCKING, "Waypoint $n heading is outside 0–360°")
            }
        }

        aircraft?.let { state ->
            val battery = state.battery.percent
            if (battery != null && battery <= plan.minimumBatteryReservePercent) {
                issues += MissionIssue(MissionIssueSeverity.BLOCKING, "Aircraft battery is already at or below mission reserve")
            }
            val satellites = state.navigation.satellites
            if (satellites != null && satellites < 6) issues += MissionIssue(MissionIssueSeverity.BLOCKING, "GPS satellite count is too low for autonomous flight")
        }
        return MissionValidation(issues)
    }
}

enum class BatteryHealthBand { EXCELLENT, GOOD, FAIR, POOR, UNKNOWN }

data class BatteryHealthAssessment(
    val healthPercent: Int?,
    val cellDeltaV: Double?,
    val band: BatteryHealthBand,
    val advisories: List<String>
)

object BatteryHealthAnalyzer {
    fun assess(battery: BatteryState): BatteryHealthAssessment {
        val advisories = mutableListOf<String>()
        val capacityHealth = if ((battery.designCapacityMah ?: 0) > 0 && battery.fullCapacityMah != null) {
            ((battery.fullCapacityMah.toDouble() / battery.designCapacityMah!!.toDouble()) * 100.0).toInt().coerceIn(0, 120)
        } else null
        val delta = battery.cellDeltaV
        if (delta != null) {
            when {
                delta >= 0.12 -> advisories += "Severe cell imbalance"
                delta >= 0.08 -> advisories += "High cell imbalance"
                delta >= 0.04 -> advisories += "Cell imbalance should be monitored"
            }
        }
        battery.temperatureC?.let { temp ->
            if (temp >= 60.0) advisories += "Battery temperature is critically high"
            else if (temp >= 50.0) advisories += "Battery temperature is high"
            else if (temp <= 0.0) advisories += "Battery is too cold for normal performance"
        }
        battery.dischargeCount?.let { cycles -> if (cycles >= 200) advisories += "High cycle count" }

        val band = when {
            capacityHealth == null -> BatteryHealthBand.UNKNOWN
            capacityHealth >= 95 -> BatteryHealthBand.EXCELLENT
            capacityHealth >= 85 -> BatteryHealthBand.GOOD
            capacityHealth >= 75 -> BatteryHealthBand.FAIR
            else -> BatteryHealthBand.POOR
        }
        return BatteryHealthAssessment(capacityHealth, delta, band, advisories)
    }
}

data class RecoveryPoint(
    val position: GeoPoint,
    val timestampEpochMs: Long,
    val altitudeM: Double?,
    val headingDeg: Double?,
    val groundSpeedMps: Double?,
    val verticalSpeedMps: Double?,
    val batteryPercent: Int?
)

class LastKnownAircraftTracker(private val capacity: Int = 180) {
    init { require(capacity >= 3) }
    private val samples = ArrayDeque<RecoveryPoint>()

    @Synchronized
    fun observe(state: XStarState, timestampEpochMs: Long) {
        val lat = state.navigation.latitudeDeg ?: return
        val lon = state.navigation.longitudeDeg ?: return
        val point = RecoveryPoint(
            position = GeoPoint(lat, lon),
            timestampEpochMs = timestampEpochMs,
            altitudeM = state.navigation.altitudeM,
            headingDeg = state.attitude.yawDeg,
            groundSpeedMps = state.navigation.groundSpeedMps,
            verticalSpeedMps = state.navigation.verticalSpeedMps,
            batteryPercent = state.battery.percent
        )
        samples.addLast(point)
        while (samples.size > capacity) samples.removeFirst()
    }

    @Synchronized fun last(): RecoveryPoint? = samples.lastOrNull()
    @Synchronized fun recent(count: Int = 3): List<RecoveryPoint> = samples.takeLast(max(0, min(count, samples.size)))
    @Synchronized fun path(): List<RecoveryPoint> = samples.toList()
    @Synchronized fun clear() = samples.clear()
}

data class FlightSummary(
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val maximumAltitudeM: Double?,
    val maximumGroundSpeedMps: Double?,
    val batteryStartPercent: Int?,
    val batteryEndPercent: Int?,
    val samples: Int
)

class FlightSessionRecorder {
    private var startedAt: Long? = null
    private var firstBattery: Int? = null
    private var lastBattery: Int? = null
    private var maxAltitude: Double? = null
    private var maxSpeed: Double? = null
    private var count: Int = 0

    @Synchronized
    fun start(timestampEpochMs: Long) {
        startedAt = timestampEpochMs
        firstBattery = null
        lastBattery = null
        maxAltitude = null
        maxSpeed = null
        count = 0
    }

    @Synchronized
    fun observe(state: XStarState) {
        if (startedAt == null) return
        state.battery.percent?.let {
            if (firstBattery == null) firstBattery = it
            lastBattery = it
        }
        state.navigation.altitudeM?.let { maxAltitude = maxAltitude?.let { old -> max(old, it) } ?: it }
        state.navigation.groundSpeedMps?.let { maxSpeed = maxSpeed?.let { old -> max(old, it) } ?: it }
        count++
    }

    @Synchronized
    fun finish(timestampEpochMs: Long): FlightSummary? {
        val start = startedAt ?: return null
        val summary = FlightSummary(start, timestampEpochMs, maxAltitude, maxSpeed, firstBattery, lastBattery, count)
        startedAt = null
        return summary
    }
}
