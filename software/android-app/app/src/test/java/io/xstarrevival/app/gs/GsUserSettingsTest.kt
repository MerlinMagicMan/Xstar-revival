package io.xstarrevival.app.gs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GsUserSettingsTest {
    @Test
    fun `normalization bounds every persisted numeric preference`() {
        val normalized = GsUserSettings(
            maximumAltitudeM = 999f,
            maximumDistanceM = -1f,
            rthAltitudeM = 5f,
            controllerSensitivity = 2f,
            controllerDeadZone = -1f,
            controllerExpo = Float.POSITIVE_INFINITY,
            controllerC1Action = "EJECT",
            videoChannel = 99,
            lowBatteryPercent = 10,
            criticalBatteryPercent = 25,
            missionReservePercent = 90,
            cellDeltaWarningV = 1f,
            gimbalPitchSpeed = 0f,
            gimbalSmoothing = 2f
        ).normalized()

        assertEquals(500f, normalized.maximumAltitudeM)
        assertEquals(50f, normalized.maximumDistanceM)
        assertEquals(20f, normalized.rthAltitudeM)
        assertEquals(1f, normalized.controllerSensitivity)
        assertEquals(0f, normalized.controllerDeadZone)
        assertEquals(.35f, normalized.controllerExpo)
        assertEquals("TAKE_PHOTO", normalized.controllerC1Action)
        assertEquals(13, normalized.videoChannel)
        assertEquals(26, normalized.lowBatteryPercent)
        assertEquals(25, normalized.criticalBatteryPercent)
        assertEquals(50, normalized.missionReservePercent)
        assertEquals(.15f, normalized.cellDeltaWarningV)
        assertEquals(.1f, normalized.gimbalPitchSpeed)
        assertEquals(1f, normalized.gimbalSmoothing)
    }

    @Test
    fun `normalization preserves application choices`() {
        val normalized = GsUserSettings(
            metricUnits = false,
            highVisibility = true,
            audibleAlerts = false,
            haptics = false,
            mapHeadingUp = true,
            localLogs = false,
            developerMode = true,
            controllerC2Action = "VIEW"
        ).normalized()

        assertTrue(!normalized.metricUnits)
        assertTrue(normalized.highVisibility)
        assertTrue(!normalized.audibleAlerts)
        assertTrue(!normalized.haptics)
        assertTrue(normalized.mapHeadingUp)
        assertTrue(!normalized.localLogs)
        assertTrue(normalized.developerMode)
        assertEquals("VIEW", normalized.controllerC2Action)
    }

    @Test
    fun `normalization replaces non finite persisted values`() {
        val normalized = GsUserSettings(
            maximumAltitudeM = Float.NaN,
            controllerSensitivity = Float.POSITIVE_INFINITY,
            cellDeltaWarningV = Float.NEGATIVE_INFINITY
        ).normalized()

        assertEquals(120f, normalized.maximumAltitudeM)
        assertEquals(.55f, normalized.controllerSensitivity)
        assertEquals(.08f, normalized.cellDeltaWarningV)
    }

    @Test
    fun `simulator video only accepts local http endpoints`() {
        assertEquals(
            "http://xstar-simulator.local:8080/custom.html",
            normalizeSimulatorVideoUrl("http://XSTAR-SIMULATOR.local:8080/custom.html")
        )
        assertEquals(DEFAULT_SIMULATOR_VIDEO_URL, normalizeSimulatorVideoUrl("https://example.com/player.html"))
        assertEquals(DEFAULT_SIMULATOR_VIDEO_URL, normalizeSimulatorVideoUrl("http://192.168.1.44:8080/player.html"))
        assertEquals(DEFAULT_SIMULATOR_VIDEO_URL, normalizeSimulatorVideoUrl("http://8.8.8.8/player.html"))
        assertEquals(DEFAULT_SIMULATOR_VIDEO_URL, normalizeSimulatorVideoUrl("file:///tmp/player.html"))
    }

    @Test
    fun `simulator player URL disables browser controls and auto connects`() {
        val playerUrl = simulatorPlayerUrl("http://xstar-simulator.local:8080")
        assertTrue("AutoConnect=true" in playerUrl)
        assertTrue("AutoPlayVideo=true" in playerUrl)
        assertTrue("TouchInput=false" in playerUrl)
        assertTrue("GamepadInput=false" in playerUrl)
    }
}
