package io.xstarrevival.app.gs

import android.content.Context
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
        const val MAX_RECOVERY_POINTS = 180
        const val MAX_FLIGHT_RECORDS = 500
        const val MAX_FLIGHT_REPLAYS = 50
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

private fun JSONObject.putNullable(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)
private fun JSONObject.optNullableDouble(key: String): Double? = if (!has(key) || isNull(key)) null else optDouble(key).takeIf { it.isFinite() }
private fun JSONObject.optNullableInt(key: String): Int? = if (!has(key) || isNull(key)) null else optInt(key)
