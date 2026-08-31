package io.xstarrevival.core.replay

import io.xstarrevival.core.model.AircraftState
import io.xstarrevival.core.model.DiagnosticsState
import io.xstarrevival.core.model.ImageLinkState
import io.xstarrevival.core.model.NavigationState
import io.xstarrevival.core.model.RemoteState
import io.xstarrevival.core.model.XStarState
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SanitizedTelemetryCaptureWriterTest {
    @Test
    fun `shareable timeline retains diagnostics but excludes private and opaque fields`() {
        val output = ByteArrayOutputStream()
        val writer = SanitizedTelemetryCaptureWriter(output, maxBytes = 100_000) { 10 }
        writer.append(
            XStarState(
                aircraft = AircraftState(productName = "X-Star Premium", firmwareVersion = "2.0.12"),
                navigation = NavigationState(
                    latitudeDeg = 41.8781,
                    longitudeDeg = -87.6298,
                    homeLatitudeDeg = 41.9,
                    homeLongitudeDeg = -87.7,
                    satellites = 14,
                    altitudeM = 22.4
                ),
                remote = RemoteState(
                    signalPercent = 82,
                    firmwareVersion = "sim-rc-1",
                    calibrated = true,
                    stickMode = 2,
                    sensitivity = .55,
                    deadZone = .05,
                    expo = .35,
                    buttonAssignments = mapOf("C2" to "MAP", "C1" to "TAKE_PHOTO"),
                    gimbalWheelReversed = true,
                    throttleInput = .25,
                    opaqueControlMenu = listOf(999_991, 999_992)
                ),
                battery = io.xstarrevival.core.model.BatteryState(packId = "PRIVATE-BATTERY-ID"),
                imageLink = ImageLinkState(
                    automaticChannel = false,
                    channel = 6,
                    channelStrengths = listOf(10, 20, 30),
                    interferencePercent = 18,
                    packetLossPercent = .8,
                    latencyMs = 48,
                    bandwidthMbps = 8.2
                ),
                diagnostics = DiagnosticsState(
                    source = "official-autel-sdk",
                    counters = mapOf("official_h264_frames" to 3),
                    notes = listOf("serial=PRIVATE-SERIAL", "key=PRIVATE-APP-KEY")
                )
            )
        )
        writer.close()

        val capture = output.toString(Charsets.UTF_8)
        assertTrue(capture.contains("X-Star Premium"))
        assertTrue(capture.contains("\"satellites\":14"))
        assertTrue(capture.contains("official_h264_frames"))
        assertTrue(capture.contains("\"channel\":6"))
        assertTrue(capture.contains("\"latency_ms\":48"))
        assertTrue(capture.contains("\"firmware_version\":\"sim-rc-1\""))
        assertTrue(capture.contains("\"calibrated\":true"))
        assertTrue(capture.contains("\"stick_mode\":2"))
        assertTrue(capture.contains("\"button_assignments\":{\"C1\":\"TAKE_PHOTO\", \"C2\":\"MAP\"}"))
        assertTrue(capture.contains("\"throttle_input\":0.25"))
        assertFalse(capture.contains("41.8781"))
        assertFalse(capture.contains("-87.6298"))
        assertFalse(capture.contains("999991"))
        assertFalse(capture.contains("PRIVATE-SERIAL"))
        assertFalse(capture.contains("PRIVATE-APP-KEY"))
        assertFalse(capture.contains("PRIVATE-BATTERY-ID"))
        assertFalse(capture.contains("latitude"))
        assertFalse(capture.contains("longitude"))
        assertFalse(capture.contains("opaque_control_menu"))
    }
}
