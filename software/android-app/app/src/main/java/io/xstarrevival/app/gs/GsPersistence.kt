package io.xstarrevival.app.gs

import android.content.Context
import io.xstarrevival.core.groundstation.GeoPoint
import io.xstarrevival.core.groundstation.RecoveryPoint
import io.xstarrevival.core.model.XStarState
import org.json.JSONArray
import org.json.JSONObject

/** Small local-only persistence layer. Core flight functions never require cloud access. */
class GsPersistence(context: Context) {
    private val prefs = context.getSharedPreferences("xstar_ground_station", Context.MODE_PRIVATE)

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
                        batteryEndPercent = item.optNullableInt("endBat")
                    )
                )
            }
        }.sortedByDescending { it.startedAtEpochMs }
    }

    private fun loadRecoveryJson(): JSONArray = runCatching { JSONArray(prefs.getString(KEY_RECOVERY, "[]")) }.getOrElse { JSONArray() }
    private fun loadFlightJson(): JSONArray = runCatching { JSONArray(prefs.getString(KEY_FLIGHTS, "[]")) }.getOrElse { JSONArray() }

    private companion object {
        const val KEY_RECOVERY = "recovery_points"
        const val KEY_FLIGHTS = "flight_summaries"
        const val MAX_RECOVERY_POINTS = 180
        const val MAX_FLIGHT_RECORDS = 500
    }
}

data class PersistedFlightSummary(
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val maximumAltitudeM: Double?,
    val maximumSpeedMps: Double?,
    val batteryStartPercent: Int?,
    val batteryEndPercent: Int?
)

private fun JSONObject.putNullable(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)
private fun JSONObject.optNullableDouble(key: String): Double? = if (!has(key) || isNull(key)) null else optDouble(key).takeIf { it.isFinite() }
private fun JSONObject.optNullableInt(key: String): Int? = if (!has(key) || isNull(key)) null else optInt(key)
