package io.xstarrevival.app.gs

import android.content.Context
import io.xstarrevival.core.groundstation.BatteryHealthAnalyzer
import io.xstarrevival.core.groundstation.GeoPoint
import io.xstarrevival.core.groundstation.RecoveryPoint
import io.xstarrevival.core.model.XStarState
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Small local-only persistence layer. Core flight functions never require cloud access. */
class GsPersistence(context: Context) {
    private val prefs = context.getSharedPreferences("xstar_ground_station", Context.MODE_PRIVATE)
    private val flightSamplesDirectory = File(context.filesDir, "flight_samples").apply { mkdirs() }

    fun saveRecoveryPoint(state: XStarState, timestampEpochMs: Long = System.currentTimeMillis()) {
        val lat = state.navigation.latitudeDeg ?: return
        val lon = state.navigation.longitudeDeg ?: return
        val point = JSONObject()
            .put("lat", lat)
            .put("lon", lon)
            .put("time", timestampEpochMs)
            .putNullable("alt", state.navigation.altitudeM)
            .putNullable("hdg", state.attitude.yawDeg)
            .putNullable("spd", state.navigation.groundSpeedMps)
            .putNullable("vs", state.navigation.verticalSpeedMps)
            .putNullable("bat", state.battery.percent)

        val history = loadRecoveryJson().apply {
            put(point)
            while (length() > MAX_RECOVERY_POINTS) remove(0)
        }
        prefs.edit().putString(KEY_RECOVERY, history.toString()).apply()
    }

    fun loadRecoveryPoints(): List<RecoveryPoint> {
        val array = loadRecoveryJson()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val lat = item.optDouble("lat", Double.NaN)
                val lon = item.optDouble("lon", Double.NaN)
                if (!lat.isFinite() || !lon.isFinite()) continue
                add(
                    RecoveryPoint(
                        position = GeoPoint(lat, lon),
                        timestampEpochMs = item.optLong("time", 0L),
                        altitudeM = item.optNullableDouble("alt"),
                        headingDeg = item.optNullableDouble("hdg"),
                        groundSpeedMps = item.optNullableDouble("spd"),
                        verticalSpeedMps = item.optNullableDouble("vs"),
                        batteryPercent = item.optNullableInt("bat")
                    )
                )
            }
        }
    }

    fun saveFlightSummary(summary: PersistedFlightSummary) {
        val records = loadFlightJson()
        records.put(
            JSONObject()
                .put("start", summary.startedAtEpochMs)
                .put("end", summary.endedAtEpochMs)
                .putNullable("maxAlt", summary.maximumAltitudeM)
                .putNullable("maxSpeed", summary.maximumSpeedMps)
                .putNullable("startBat", summary.batteryStartPercent)
                .putNullable("endBat", summary.batteryEndPercent)
        )
        while (records.length() > MAX_FLIGHT_RECORDS) records.remove(0)
        prefs.edit().putString(KEY_FLIGHTS, records.toString()).apply()
        saveFlightSamples(summary)
        pruneFlightSampleFiles(records)
    }

    fun loadFlightSummaries(): List<PersistedFlightSummary> {
        val array = loadFlightJson()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    PersistedFlightSummary(
                        startedAtEpochMs = item.optLong("start"),
                        endedAtEpochMs = item.optLong("end"),
                        maximumAltitudeM = item.optNullableDouble("maxAlt"),
                        maximumSpeedMps = item.optNullableDouble("maxSpeed"),
                        batteryStartPercent = item.optNullableInt("startBat"),
                        batteryEndPercent = item.optNullableInt("endBat"),
                        samples = loadFlightSamples(item.optLong("start"))
                    )
                )
            }
        }.sortedByDescending { it.startedAtEpochMs }
    }

    fun saveAircraftProfile(profile: PersistedAircraftProfile) {
        val profiles = loadAircraftProfiles().filterNot { it.id == profile.id } + profile.normalized()
        val encoded = JSONArray().also { array ->
            profiles.sortedBy { it.createdAtEpochMs }.forEach { array.put(it.toJson()) }
        }
        prefs.edit().putString(KEY_AIRCRAFT_PROFILES, encoded.toString()).apply()
    }

    fun loadAircraftProfiles(): List<PersistedAircraftProfile> {
        val array = runCatching { JSONArray(prefs.getString(KEY_AIRCRAFT_PROFILES, "[]")) }.getOrElse { JSONArray() }
        return buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.toAircraftProfileOrNull()?.let(::add)
            }
        }.sortedBy { it.createdAtEpochMs }
    }

    fun deleteAircraftProfile(profileId: String) {
        val profiles = loadAircraftProfiles().filterNot { it.id == profileId }
        val encoded = JSONArray().also { array -> profiles.forEach { array.put(it.toJson()) } }
        prefs.edit().putString(KEY_AIRCRAFT_PROFILES, encoded.toString()).apply()
        if (loadActiveAircraftProfileId() == profileId) setActiveAircraftProfileId(profiles.firstOrNull()?.id)
    }

    fun setActiveAircraftProfileId(profileId: String?) {
        prefs.edit().apply {
            if (profileId == null) remove(KEY_ACTIVE_AIRCRAFT_PROFILE) else putString(KEY_ACTIVE_AIRCRAFT_PROFILE, profileId)
        }.apply()
    }

    fun loadActiveAircraftProfileId(): String? = prefs.getString(KEY_ACTIVE_AIRCRAFT_PROFILE, null)

    fun ensureIdentifiedBatteryProfile(
        packId: String,
        ratedCapacityMah: Int?,
        timestampEpochMs: Long = System.currentTimeMillis()
    ): PersistedBatteryProfile {
        val id = "identified:$packId"
        loadBatteryProfiles().firstOrNull { it.id == id }?.let { existing ->
            val reportedCapacity = ratedCapacityMah?.takeIf { it > 0 }
            if (reportedCapacity != null && reportedCapacity != existing.ratedCapacityMah) {
                return existing.copy(ratedCapacityMah = reportedCapacity).also(::saveBatteryProfile)
            }
            return existing
        }
        return PersistedBatteryProfile(
            id = id,
            name = "X-Star Pack ${packId.takeLast(4)}",
            kind = "ORIGINAL",
            ratedCapacityMah = ratedCapacityMah?.takeIf { it > 0 } ?: 4_900,
            telemetryIdentity = packId,
            createdAtEpochMs = timestampEpochMs
        ).also(::saveBatteryProfile)
    }

    fun saveBatteryProfile(profile: PersistedBatteryProfile) {
        val profiles = loadBatteryProfiles().filterNot { it.id == profile.id } + profile.normalized()
        val encoded = JSONArray().also { array -> profiles.sortedBy { it.createdAtEpochMs }.forEach { array.put(it.toJson()) } }
        prefs.edit().putString(KEY_BATTERY_PROFILES, encoded.toString()).apply()
    }

    fun loadBatteryProfiles(): List<PersistedBatteryProfile> {
        val array = runCatching { JSONArray(prefs.getString(KEY_BATTERY_PROFILES, "[]")) }.getOrElse { JSONArray() }
        return buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.toBatteryProfileOrNull()?.let(::add)
            }
        }.sortedBy { it.createdAtEpochMs }
    }

    fun setActiveBatteryProfileId(profileId: String?) {
        prefs.edit().apply {
            if (profileId == null) remove(KEY_ACTIVE_BATTERY_PROFILE) else putString(KEY_ACTIVE_BATTERY_PROFILE, profileId)
        }.apply()
    }

    fun loadActiveBatteryProfileId(): String? = prefs.getString(KEY_ACTIVE_BATTERY_PROFILE, null)

    fun saveBatteryHistorySample(
        profile: PersistedBatteryProfile,
        state: XStarState,
        timestampEpochMs: Long = System.currentTimeMillis()
    ) {
        val battery = state.battery
        if (listOf(battery.percent, battery.packVoltageV, battery.temperatureC, battery.fullCapacityMah).all { it == null }) return
        val assessment = BatteryHealthAnalyzer.assess(battery, profile.ratedCapacityMah)
        val sample = PersistedBatterySample(
            timestampEpochMs = timestampEpochMs,
            percent = battery.percent,
            packVoltageV = battery.packVoltageV?.takeIf { it.isFinite() },
            currentA = battery.currentA?.takeIf { it.isFinite() },
            temperatureC = battery.temperatureC?.takeIf { it.isFinite() },
            fullCapacityMah = battery.fullCapacityMah,
            cycleCount = battery.dischargeCount,
            healthPercent = assessment.healthPercent,
            cellVoltagesV = battery.cells.mapNotNull { it.voltageV?.takeIf(Double::isFinite) },
            cellDeltaV = battery.cellDeltaV?.takeIf { it.isFinite() }
        )
        val root = runCatching { JSONObject(prefs.getString(KEY_BATTERY_HISTORY, "{}") ?: "{}") }.getOrElse { JSONObject() }
        val history = root.optJSONArray(profile.id) ?: JSONArray()
        history.put(sample.toJson())
        while (history.length() > MAX_BATTERY_HISTORY_SAMPLES) history.remove(0)
        root.put(profile.id, history)
        prefs.edit().putString(KEY_BATTERY_HISTORY, root.toString()).apply()
    }

    fun loadBatteryHistory(profileId: String): List<PersistedBatterySample> {
        val root = runCatching { JSONObject(prefs.getString(KEY_BATTERY_HISTORY, "{}") ?: "{}") }.getOrElse { return emptyList() }
        val array = root.optJSONArray(profileId) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) array.optJSONObject(index)?.toBatterySampleOrNull()?.let(::add)
        }.sortedByDescending { it.timestampEpochMs }
    }

    private fun loadRecoveryJson(): JSONArray = runCatching { JSONArray(prefs.getString(KEY_RECOVERY, "[]")) }.getOrElse { JSONArray() }
    private fun loadFlightJson(): JSONArray = runCatching { JSONArray(prefs.getString(KEY_FLIGHTS, "[]")) }.getOrElse { JSONArray() }

    private fun saveFlightSamples(summary: PersistedFlightSummary) {
        if (summary.samples.isEmpty()) return
        val encoded = JSONArray().also { array ->
            summary.samples.forEach { sample ->
                array.put(
                    JSONObject()
                        .put("time", sample.timestampEpochMs)
                        .putNullable("lat", sample.latitudeDeg)
                        .putNullable("lon", sample.longitudeDeg)
                        .putNullable("alt", sample.altitudeM)
                        .putNullable("spd", sample.groundSpeedMps)
                        .putNullable("vs", sample.verticalSpeedMps)
                        .putNullable("hdg", sample.headingDeg)
                        .putNullable("bat", sample.batteryPercent)
                )
            }
        }
        runCatching { flightSampleFile(summary.startedAtEpochMs).writeText(encoded.toString()) }
    }

    private fun loadFlightSamples(startedAtEpochMs: Long): List<PersistedFlightSample> {
        val array = runCatching { JSONArray(flightSampleFile(startedAtEpochMs).readText()) }.getOrElse { return emptyList() }
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    PersistedFlightSample(
                        timestampEpochMs = item.optLong("time", startedAtEpochMs),
                        latitudeDeg = item.optNullableDouble("lat"),
                        longitudeDeg = item.optNullableDouble("lon"),
                        altitudeM = item.optNullableDouble("alt"),
                        groundSpeedMps = item.optNullableDouble("spd"),
                        verticalSpeedMps = item.optNullableDouble("vs"),
                        headingDeg = item.optNullableDouble("hdg"),
                        batteryPercent = item.optNullableInt("bat")
                    )
                )
            }
        }
    }

    private fun pruneFlightSampleFiles(records: JSONArray) {
        val retained = buildSet {
            val firstReplayIndex = (records.length() - MAX_FLIGHT_REPLAYS).coerceAtLeast(0)
            for (index in firstReplayIndex until records.length()) {
                records.optJSONObject(index)?.optLong("start")?.let(::add)
            }
        }
        flightSamplesDirectory.listFiles()?.forEach { file ->
            val start = file.name.removePrefix("flight_").removeSuffix(".json").toLongOrNull()
            if (start == null || start !in retained) runCatching { file.delete() }
        }
    }

    private fun flightSampleFile(startedAtEpochMs: Long) = File(flightSamplesDirectory, "flight_$startedAtEpochMs.json")

    private companion object {
        const val KEY_RECOVERY = "recovery_points"
        const val KEY_FLIGHTS = "flight_summaries"
        const val KEY_AIRCRAFT_PROFILES = "aircraft_profiles"
        const val KEY_ACTIVE_AIRCRAFT_PROFILE = "active_aircraft_profile"
        const val KEY_BATTERY_PROFILES = "battery_profiles"
        const val KEY_ACTIVE_BATTERY_PROFILE = "active_battery_profile"
        const val KEY_BATTERY_HISTORY = "battery_history"
        const val MAX_RECOVERY_POINTS = 180
        const val MAX_FLIGHT_RECORDS = 500
        const val MAX_FLIGHT_REPLAYS = 50
        const val MAX_BATTERY_HISTORY_SAMPLES = 720
    }
}

data class PersistedFlightSummary(
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val maximumAltitudeM: Double?,
    val maximumSpeedMps: Double?,
    val batteryStartPercent: Int?,
    val batteryEndPercent: Int?,
    val samples: List<PersistedFlightSample> = emptyList()
)

data class PersistedFlightSample(
    val timestampEpochMs: Long,
    val latitudeDeg: Double?,
    val longitudeDeg: Double?,
    val altitudeM: Double?,
    val groundSpeedMps: Double?,
    val verticalSpeedMps: Double?,
    val headingDeg: Double?,
    val batteryPercent: Int?
)

data class PersistedAircraftProfile(
    val id: String,
    val nickname: String,
    val model: String,
    val serialNumber: String? = null,
    val firmwareVersion: String? = null,
    val lastConnectedEpochMs: Long? = null,
    val lastBatteryPercent: Int? = null,
    val lastLatitudeDeg: Double? = null,
    val lastLongitudeDeg: Double? = null,
    val healthState: String = "UNKNOWN",
    val createdAtEpochMs: Long
) {
    fun normalized() = copy(
        id = id.trim().take(80).ifEmpty { "aircraft-${createdAtEpochMs}" },
        nickname = nickname.trim().take(40).ifEmpty { "My X-Star" },
        model = model.trim().take(60).ifEmpty { "X-Star Premium" },
        serialNumber = serialNumber?.trim()?.take(60)?.takeIf { it.isNotEmpty() },
        firmwareVersion = firmwareVersion?.trim()?.take(60)?.takeIf { it.isNotEmpty() },
        lastBatteryPercent = lastBatteryPercent?.coerceIn(0, 100),
        lastLatitudeDeg = lastLatitudeDeg?.takeIf { it.isFinite() && it in -90.0..90.0 },
        lastLongitudeDeg = lastLongitudeDeg?.takeIf { it.isFinite() && it in -180.0..180.0 },
        healthState = healthState.takeIf { it in aircraftHealthStates } ?: "UNKNOWN"
    )
}

data class PersistedBatteryProfile(
    val id: String,
    val name: String,
    val kind: String,
    val ratedCapacityMah: Int,
    val telemetryIdentity: String? = null,
    val createdAtEpochMs: Long
) {
    fun normalized() = copy(
        name = name.trim().take(40).ifEmpty { "Battery Pack" },
        kind = kind.takeIf { it in batteryProfileKinds } ?: "CUSTOM",
        ratedCapacityMah = ratedCapacityMah.coerceIn(500, 20_000)
    )
}

data class PersistedBatterySample(
    val timestampEpochMs: Long,
    val percent: Int?,
    val packVoltageV: Double?,
    val currentA: Double?,
    val temperatureC: Double?,
    val fullCapacityMah: Int?,
    val cycleCount: Int?,
    val healthPercent: Int?,
    val cellVoltagesV: List<Double>,
    val cellDeltaV: Double?
) {
    val highTemperatureEvent: Boolean get() = temperatureC?.let { it >= 50.0 } == true
    val lowVoltageEvent: Boolean get() = cellVoltagesV.minOrNull()?.let { it <= 3.4 } == true
    val imbalanceEvent: Boolean get() = cellDeltaV?.let { it >= .08 } == true
}

internal val batteryProfileKinds = setOf("ORIGINAL", "REBUILT", "AFTERMARKET", "CUSTOM", "ALTERNATE_BMS")
internal val aircraftHealthStates = setOf("READY", "CHECKING", "WARNING", "CRITICAL", "OFFLINE", "UNKNOWN")

private fun PersistedAircraftProfile.toJson() = JSONObject()
    .put("id", id)
    .put("nickname", nickname)
    .put("model", model)
    .putNullable("serial", serialNumber)
    .putNullable("firmware", firmwareVersion)
    .putNullable("lastConnected", lastConnectedEpochMs)
    .putNullable("lastBattery", lastBatteryPercent)
    .putNullable("lastLat", lastLatitudeDeg)
    .putNullable("lastLon", lastLongitudeDeg)
    .put("health", healthState)
    .put("created", createdAtEpochMs)

private fun JSONObject.toAircraftProfileOrNull(): PersistedAircraftProfile? {
    val id = optString("id").takeIf { it.isNotBlank() } ?: return null
    return PersistedAircraftProfile(
        id = id,
        nickname = optString("nickname", "My X-Star"),
        model = optString("model", "X-Star Premium"),
        serialNumber = optString("serial").takeIf { has("serial") && !isNull("serial") && it.isNotBlank() },
        firmwareVersion = optString("firmware").takeIf { has("firmware") && !isNull("firmware") && it.isNotBlank() },
        lastConnectedEpochMs = optLong("lastConnected").takeIf { has("lastConnected") && !isNull("lastConnected") && it > 0 },
        lastBatteryPercent = optNullableInt("lastBattery"),
        lastLatitudeDeg = optNullableDouble("lastLat"),
        lastLongitudeDeg = optNullableDouble("lastLon"),
        healthState = optString("health", "UNKNOWN"),
        createdAtEpochMs = optLong("created", 0L)
    ).normalized()
}

private fun PersistedBatteryProfile.toJson() = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("kind", kind)
    .put("rated", ratedCapacityMah)
    .putNullable("identity", telemetryIdentity)
    .put("created", createdAtEpochMs)

private fun JSONObject.toBatteryProfileOrNull(): PersistedBatteryProfile? {
    val id = optString("id").takeIf { it.isNotBlank() } ?: return null
    return PersistedBatteryProfile(
        id = id,
        name = optString("name", "Battery Pack"),
        kind = optString("kind", "CUSTOM"),
        ratedCapacityMah = optInt("rated", 4_900),
        telemetryIdentity = optString("identity").takeIf { has("identity") && !isNull("identity") && it.isNotBlank() },
        createdAtEpochMs = optLong("created", 0L)
    ).normalized()
}

private fun PersistedBatterySample.toJson() = JSONObject()
    .put("time", timestampEpochMs)
    .putNullable("percent", percent)
    .putNullable("voltage", packVoltageV)
    .putNullable("current", currentA)
    .putNullable("temperature", temperatureC)
    .putNullable("full", fullCapacityMah)
    .putNullable("cycles", cycleCount)
    .putNullable("health", healthPercent)
    .put("cells", JSONArray(cellVoltagesV))
    .putNullable("delta", cellDeltaV)

private fun JSONObject.toBatterySampleOrNull(): PersistedBatterySample? {
    val timestamp = optLong("time", 0L).takeIf { it > 0 } ?: return null
    val cells = optJSONArray("cells")?.let { array ->
        buildList { for (index in 0 until array.length()) array.optDouble(index, Double.NaN).takeIf { it.isFinite() }?.let(::add) }
    }.orEmpty()
    return PersistedBatterySample(
        timestampEpochMs = timestamp,
        percent = optNullableInt("percent"),
        packVoltageV = optNullableDouble("voltage"),
        currentA = optNullableDouble("current"),
        temperatureC = optNullableDouble("temperature"),
        fullCapacityMah = optNullableInt("full"),
        cycleCount = optNullableInt("cycles"),
        healthPercent = optNullableInt("health"),
        cellVoltagesV = cells,
        cellDeltaV = optNullableDouble("delta")
    )
}

private fun JSONObject.putNullable(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)
private fun JSONObject.optNullableDouble(key: String): Double? = if (!has(key) || isNull(key)) null else optDouble(key).takeIf { it.isFinite() }
private fun JSONObject.optNullableInt(key: String): Int? = if (!has(key) || isNull(key)) null else optInt(key)
