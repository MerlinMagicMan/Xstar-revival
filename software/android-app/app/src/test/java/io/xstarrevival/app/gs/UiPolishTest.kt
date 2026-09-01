package io.xstarrevival.app.gs

import io.xstarrevival.core.groundstation.GeoPoint
import io.xstarrevival.core.groundstation.RecoveryPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UiPolishTest {
    @Test
    fun academyShipsEveryRequiredOfflineGuideWithContent() {
        val required = setOf("manual", "controller", "battery", "compass", "imu", "missions", "troubleshooting", "firmware", "recovery")
        assertEquals(required, academyTopics.mapTo(mutableSetOf()) { it.id })
        assertTrue(academyTopics.all { topic -> topic.summary.isNotBlank() && topic.sections.size >= 3 })
        assertTrue(academyTopics.flatMap { it.sections }.all { it.heading.isNotBlank() && it.body.length >= 40 })
    }

    @Test
    fun flightExportIncludesSummaryAndReplayTelemetry() {
        val record = PersistedFlightSummary(
            startedAtEpochMs = 100L,
            endedAtEpochMs = 500L,
            maximumAltitudeM = 12.5,
            maximumSpeedMps = 6.5,
            batteryStartPercent = 90,
            batteryEndPercent = 82,
            samples = listOf(PersistedFlightSample(200L, 35.1, -97.2, 10.0, 4.0, -.2, 180.0, 86))
        )

        val export = buildFlightExport(record)

        assertTrue(export.contains("maximum_altitude_m,12.5"))
        assertTrue(export.contains("timestamp_epoch_ms,latitude_deg,longitude_deg"))
        assertTrue(export.contains("200,35.1,-97.2,10.0,4.0,180.0,86"))
    }

    @Test
    fun recoveryExportIncludesEveryPersistedPoint() {
        val points = listOf(
            RecoveryPoint(GeoPoint(35.0, -97.0), 10L, 12.0, 90.0, 3.0, 0.0, 70),
            RecoveryPoint(GeoPoint(35.1, -97.1), 20L, 13.0, 91.0, 4.0, .1, 69)
        )

        val export = buildRecoveryExport(points)

        assertTrue(export.contains("10,35.0,-97.0,12.0,3.0,90.0,70"))
        assertTrue(export.contains("20,35.1,-97.1,13.0,4.0,91.0,69"))
    }

    @Test
    fun telemetryFormattersHonorUnitsAndUnknownValues() {
        assertEquals("10.0 m", formatAltitude(10.0, metric = true))
        assertEquals("33 ft", formatAltitude(10.0, metric = false))
        assertEquals("22.4 mph", formatGroundSpeed(10.0, metric = false))
        assertEquals("68.0°F", formatTemperature(20.0, metric = false))
        assertEquals("—", formatVerticalSpeed(null, metric = true))
    }
}
