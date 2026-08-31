package io.xstarrevival.core.event

import io.xstarrevival.core.model.XStarState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class XStarReducerTest {
    @Test
    fun batteryEventBuildsCellsAndDelta() {
        val next = XStarReducer.reduce(
            XStarState(),
            XStarEvent.BatterySnapshot(
                percent = 80,
                packVoltageV = 16.08,
                cellVoltagesV = listOf(4.020, 4.018, 4.025, 4.017)
            ),
            nowEpochMs = 123L
        )

        assertEquals(80, next.battery.percent)
        assertEquals(4, next.battery.cells.size)
        assertTrue((next.battery.cellDeltaV ?: 1.0) < 0.01)
        assertEquals(123L, next.diagnostics.lastUpdateEpochMs)
    }

    @Test
    fun diagnosticsAreAdditive() {
        var state = XStarState()
        state = XStarReducer.reduce(state, XStarEvent.DiagnosticCounter("frames", 10), 1L)
        state = XStarReducer.reduce(state, XStarEvent.DiagnosticNote("heartbeat detected"), 2L)

        assertEquals(10L, state.diagnostics.counters["frames"])
        assertEquals(listOf("heartbeat detected"), state.diagnostics.notes)
        assertEquals(2L, state.diagnostics.lastUpdateEpochMs)
    }

    @Test
    fun partialOfficialSnapshotsPreservePreviouslyObservedValues() {
        var state = XStarState()
        state = XStarReducer.reduce(
            state,
            XStarEvent.BatterySnapshot(percent = 82, cellVoltagesV = listOf(4.01, 4.00, 4.02, 3.99)),
            1L
        )
        state = XStarReducer.reduce(state, XStarEvent.BatterySnapshot(temperatureC = 29.5), 2L)
        state = XStarReducer.reduce(
            state,
            XStarEvent.NavigationSnapshot(latitudeDeg = 41.0, longitudeDeg = -87.0, altitudeM = 20.0),
            3L
        )
        state = XStarReducer.reduce(state, XStarEvent.NavigationSnapshot(ultrasonicHeightM = 2.2), 4L)

        assertEquals(82, state.battery.percent)
        assertEquals(4, state.battery.cells.size)
        assertEquals(29.5, state.battery.temperatureC)
        assertEquals(41.0, state.navigation.latitudeDeg)
        assertEquals(20.0, state.navigation.altitudeM)
        assertEquals(2.2, state.navigation.ultrasonicHeightM)
    }

    @Test
    fun cameraSnapshotNormalizesProductionSettings() {
        val state = XStarReducer.reduce(
            XStarState(),
            XStarEvent.CameraSnapshot(
                connected = true,
                mode = "VIDEO",
                exposureMode = "MANUAL",
                iso = "400",
                shutter = "0.004",
                exposureCompensationEv = -.7,
                whiteBalance = "CLOUDY",
                photoResolution = "12 MP",
                videoResolution = "4K",
                frameRateFps = 30,
                timerSeconds = 5,
                storageRemainingMb = 24_000L
            ),
            1L
        )

        assertEquals("MANUAL", state.camera.exposureMode)
        assertEquals("400", state.camera.iso)
        assertEquals(-.7, state.camera.exposureCompensationEv)
        assertEquals("CLOUDY", state.camera.whiteBalance)
        assertEquals("4K", state.camera.videoResolution)
        assertEquals(30, state.camera.frameRateFps)
        assertEquals(24_000L, state.camera.storageRemainingMb)
    }

    @Test
    fun gimbalSnapshotNormalizesCalibrationAndResponseSettings() {
        val state = XStarReducer.reduce(
            XStarState(),
            XStarEvent.GimbalSnapshot(
                pitchDeg = -35.0,
                status = "READY",
                sensitivity = .7,
                smoothing = .8,
                pitchSpeed = .4,
                calibrated = true
            ),
            1L
        )

        assertEquals(-35.0, state.gimbal.pitchDeg)
        assertEquals(.7, state.gimbal.sensitivity)
        assertEquals(.8, state.gimbal.smoothing)
        assertEquals(.4, state.gimbal.pitchSpeed)
        assertTrue(state.gimbal.calibrated == true)
    }

    @Test
    fun imageLinkSnapshotNormalizesAnalyzerMetrics() {
        val state = XStarReducer.reduce(
            XStarState(),
            XStarEvent.ImageLinkSnapshot(
                automaticChannel = false,
                channel = 4,
                channelStrengths = listOf(20, 30, 40, 80),
                interferencePercent = 20,
                packetLossPercent = 1.2,
                latencyMs = 55,
                bandwidthMbps = 7.4
            ),
            1L
        )

        assertEquals(false, state.imageLink.automaticChannel)
        assertEquals(4, state.imageLink.channel)
        assertEquals(80, state.imageLink.channelStrengths.last())
        assertEquals(1.2, state.imageLink.packetLossPercent)
        assertEquals(55, state.imageLink.latencyMs)
        assertEquals(7.4, state.imageLink.bandwidthMbps)
    }

}
