package io.xstarrevival.app.gs

import android.content.Context
import io.xstarrevival.core.groundstation.GeoPoint
import io.xstarrevival.core.groundstation.MissionFinishBehavior
import io.xstarrevival.core.groundstation.MissionLostLinkBehavior
import io.xstarrevival.core.groundstation.MissionPlan
import io.xstarrevival.core.groundstation.MissionWaypoint
import io.xstarrevival.core.groundstation.WaypointHeadingMode
import org.json.JSONArray
import org.json.JSONObject

class GsMissionStore(context: Context) {
    private val prefs = context.getSharedPreferences("xstar_ground_station_missions", Context.MODE_PRIVATE)

    fun load(): List<MissionPlan> {
        val array = runCatching { JSONArray(prefs.getString(KEY, "[]") ?: "[]") }.getOrElse { JSONArray() }
        return buildList {
            for (index in 0 until array.length()) {
                decode(array.optJSONObject(index) ?: continue)?.let(::add)
            }
        }
    }

    fun save(plan: MissionPlan) {
        val plans = load().toMutableList()
        val index = plans.indexOfFirst { it.id == plan.id }
        if (index >= 0) plans[index] = plan else plans.add(plan)
        write(plans)
    }

    fun delete(id: String) = write(load().filterNot { it.id == id })

    private fun write(plans: List<MissionPlan>) {
        val array = JSONArray()
        plans.forEach { array.put(encode(it)) }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private fun encode(plan: MissionPlan): JSONObject = JSONObject()
        .put("id", plan.id)
        .put("name", plan.name)
        .put("finish", plan.finishBehavior.name)
        .put("lost", plan.lostLinkBehavior.name)
        .put("reserve", plan.minimumBatteryReservePercent)
        .put("waypoints", JSONArray().also { points ->
            plan.waypoints.forEach { wp ->
                points.put(
                    JSONObject()
                        .put("id", wp.id)
                        .put("lat", wp.position.latitudeDeg)
                        .put("lon", wp.position.longitudeDeg)
                        .put("alt", wp.altitudeM)
                        .put("speed", wp.speedMps)
                        .put("headingMode", wp.headingMode.name)
                        .put("heading", wp.headingDeg ?: JSONObject.NULL)
                        .put("gimbal", wp.gimbalPitchDeg ?: JSONObject.NULL)
                        .put("delay", wp.delaySeconds)
                )
            }
        })

    private fun decode(json: JSONObject): MissionPlan? = runCatching {
        val pointsJson = json.optJSONArray("waypoints") ?: JSONArray()
        val points = buildList {
            for (index in 0 until pointsJson.length()) {
                val wp = pointsJson.getJSONObject(index)
                add(
                    MissionWaypoint(
                        id = wp.getString("id"),
                        position = GeoPoint(wp.getDouble("lat"), wp.getDouble("lon")),
                        altitudeM = wp.getDouble("alt"),
                        speedMps = wp.getDouble("speed"),
                        headingMode = enumValueOrDefault(wp.optString("headingMode"), WaypointHeadingMode.NEXT_WAYPOINT),
                        headingDeg = wp.nullableDouble("heading"),
                        gimbalPitchDeg = wp.nullableDouble("gimbal"),
                        delaySeconds = wp.optDouble("delay", 0.0)
                    )
                )
            }
        }
        MissionPlan(
            id = json.getString("id"),
            name = json.optString("name", "Untitled Mission"),
            waypoints = points,
            finishBehavior = enumValueOrDefault(json.optString("finish"), MissionFinishBehavior.HOVER),
            lostLinkBehavior = enumValueOrDefault(json.optString("lost"), MissionLostLinkBehavior.RETURN_HOME),
            minimumBatteryReservePercent = json.optInt("reserve", 25)
        )
    }.getOrNull()

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback

    private fun JSONObject.nullableDouble(key: String): Double? = if (!has(key) || isNull(key)) null else getDouble(key)

    private companion object { const val KEY = "missions" }
}
