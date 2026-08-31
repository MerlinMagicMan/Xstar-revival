package io.xstarrevival.app.gs

import android.content.Context

data class GsUserSettings(
    val beginnerMode: Boolean = false,
    val maximumAltitudeM: Float = 120f,
    val maximumDistanceM: Float = 500f,
    val rthAltitudeM: Float = 60f,
    val allowAttiMode: Boolean = false,
    val iocEnabled: Boolean = true,
    val controllerMode2: Boolean = true,
    val controllerSensitivity: Float = .55f,
    val controllerDeadZone: Float = .05f,
    val videoChannelAutomatic: Boolean = true,
    val videoChannel: Int = 5,
    val lowBatteryPercent: Int = 30,
    val criticalBatteryPercent: Int = 15,
    val missionReservePercent: Int = 25,
    val cellDeltaWarningV: Float = .08f,
    val gimbalPitchSpeed: Float = .5f,
    val gimbalSmoothing: Float = .6f,
    val metricUnits: Boolean = true,
    val highVisibility: Boolean = false,
    val audibleAlerts: Boolean = true,
    val haptics: Boolean = true,
    val mapHeadingUp: Boolean = false,
    val localLogs: Boolean = true,
    val developerMode: Boolean = false
) {
    fun normalized(): GsUserSettings {
        val normalizedCritical = criticalBatteryPercent.coerceIn(8, 25)
        return copy(
        maximumAltitudeM = maximumAltitudeM.boundedOr(120f, 30f, 500f),
        maximumDistanceM = maximumDistanceM.boundedOr(500f, 50f, 3_000f),
        rthAltitudeM = rthAltitudeM.boundedOr(60f, 20f, 150f),
        controllerSensitivity = controllerSensitivity.boundedOr(.55f, .1f, 1f),
        controllerDeadZone = controllerDeadZone.boundedOr(.05f, 0f, .2f),
        videoChannel = videoChannel.coerceIn(1, 13),
        lowBatteryPercent = lowBatteryPercent.coerceIn(20, 50).coerceAtLeast(normalizedCritical + 1),
        criticalBatteryPercent = normalizedCritical,
        missionReservePercent = missionReservePercent.coerceIn(15, 50),
        cellDeltaWarningV = cellDeltaWarningV.boundedOr(.08f, .02f, .15f),
        gimbalPitchSpeed = gimbalPitchSpeed.boundedOr(.5f, .1f, 1f),
        gimbalSmoothing = gimbalSmoothing.boundedOr(.6f, 0f, 1f)
        )
    }
}

private fun Float.boundedOr(default: Float, minimum: Float, maximum: Float): Float =
    if (isFinite()) coerceIn(minimum, maximum) else default

class GsSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("xstar-user-settings-v1", Context.MODE_PRIVATE)

    fun load(): GsUserSettings = GsUserSettings(
        beginnerMode = preferences.getBoolean("beginner_mode", false),
        maximumAltitudeM = preferences.getFloat("maximum_altitude_m", 120f),
        maximumDistanceM = preferences.getFloat("maximum_distance_m", 500f),
        rthAltitudeM = preferences.getFloat("rth_altitude_m", 60f),
        allowAttiMode = preferences.getBoolean("allow_atti_mode", false),
        iocEnabled = preferences.getBoolean("ioc_enabled", true),
        controllerMode2 = preferences.getBoolean("controller_mode_2", true),
        controllerSensitivity = preferences.getFloat("controller_sensitivity", .55f),
        controllerDeadZone = preferences.getFloat("controller_dead_zone", .05f),
        videoChannelAutomatic = preferences.getBoolean("video_channel_automatic", true),
        videoChannel = preferences.getInt("video_channel", 5),
        lowBatteryPercent = preferences.getInt("low_battery_percent", 30),
        criticalBatteryPercent = preferences.getInt("critical_battery_percent", 15),
        missionReservePercent = preferences.getInt("mission_reserve_percent", 25),
        cellDeltaWarningV = preferences.getFloat("cell_delta_warning_v", .08f),
        gimbalPitchSpeed = preferences.getFloat("gimbal_pitch_speed", .5f),
        gimbalSmoothing = preferences.getFloat("gimbal_smoothing", .6f),
        metricUnits = preferences.getBoolean("metric_units", true),
        highVisibility = preferences.getBoolean("high_visibility", false),
        audibleAlerts = preferences.getBoolean("audible_alerts", true),
        haptics = preferences.getBoolean("haptics", true),
        mapHeadingUp = preferences.getBoolean("map_heading_up", false),
        localLogs = preferences.getBoolean("local_logs", true),
        developerMode = preferences.getBoolean("developer_mode", false)
    ).normalized()

    fun save(settings: GsUserSettings) {
        val value = settings.normalized()
        preferences.edit()
            .putBoolean("beginner_mode", value.beginnerMode)
            .putFloat("maximum_altitude_m", value.maximumAltitudeM)
            .putFloat("maximum_distance_m", value.maximumDistanceM)
            .putFloat("rth_altitude_m", value.rthAltitudeM)
            .putBoolean("allow_atti_mode", value.allowAttiMode)
            .putBoolean("ioc_enabled", value.iocEnabled)
            .putBoolean("controller_mode_2", value.controllerMode2)
            .putFloat("controller_sensitivity", value.controllerSensitivity)
            .putFloat("controller_dead_zone", value.controllerDeadZone)
            .putBoolean("video_channel_automatic", value.videoChannelAutomatic)
            .putInt("video_channel", value.videoChannel)
            .putInt("low_battery_percent", value.lowBatteryPercent)
            .putInt("critical_battery_percent", value.criticalBatteryPercent)
            .putInt("mission_reserve_percent", value.missionReservePercent)
            .putFloat("cell_delta_warning_v", value.cellDeltaWarningV)
            .putFloat("gimbal_pitch_speed", value.gimbalPitchSpeed)
            .putFloat("gimbal_smoothing", value.gimbalSmoothing)
            .putBoolean("metric_units", value.metricUnits)
            .putBoolean("high_visibility", value.highVisibility)
            .putBoolean("audible_alerts", value.audibleAlerts)
            .putBoolean("haptics", value.haptics)
            .putBoolean("map_heading_up", value.mapHeadingUp)
            .putBoolean("local_logs", value.localLogs)
            .putBoolean("developer_mode", value.developerMode)
            .apply()
    }
}
