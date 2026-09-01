package io.xstarrevival.core.event

import io.xstarrevival.core.model.XStarState
import io.xstarrevival.core.model.ProtocolPacketDisposition
import io.xstarrevival.core.model.ProtocolPacketTrace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class XStarReducerTest {
    @Test
    fun batteryEventBuildsCellsAndDelta() {
        val next = XStarReducer.reduce(
            XStarState(),
            XStarEvent.BatterySnapshot(
                packId = "pack-1",
                percent = 80,
                packVoltageV = 16.08,
                cellVoltagesV = listOf(4.020, 4.018, 4.025, 4.017)
            ),
            nowEpochMs = 123L
        )

        assertEquals(80, next.battery.percent)
        assertEquals("pack-1", next.battery.packId)
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
                storageRemainingMb = 24_000L,
                photosTaken = 4,
                videosTaken = 2,
                recordingDurationSeconds = 12.5,
                lastVideoDurationSeconds = 44.0
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
        assertEquals(4, state.camera.photosTaken)
        assertEquals(2, state.camera.videosTaken)
        assertEquals(12.5, state.camera.recordingDurationSeconds)
        assertEquals(44.0, state.camera.lastVideoDurationSeconds)
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

    @Test
    fun remoteSnapshotNormalizesControllerProfileAndInputs() {
        val state = XStarReducer.reduce(
            XStarState(),
            XStarEvent.RemoteSnapshot(
                connected = true,
                firmwareVersion = "rc-1",
                calibrated = true,
                stickMode = 2,
                sensitivity = .6,
                deadZone = .05,
                expo = .4,
                buttonAssignments = mapOf("C1" to "MAP"),
                gimbalWheelReversed = true,
                throttleInput = .2,
                yawInput = -.1,
                pitchInput = .3,
                rollInput = -.4,
                gimbalWheelInput = .5
            ),
            1L
        )

        assertEquals("rc-1", state.remote.firmwareVersion)
        assertEquals(true, state.remote.calibrated)
        assertEquals(2, state.remote.stickMode)
        assertEquals(.4, state.remote.expo)
        assertEquals("MAP", state.remote.buttonAssignments["C1"])
        assertEquals(-.4, state.remote.rollInput)
        assertEquals(.5, state.remote.gimbalWheelInput)
    }

    @Test
    fun diagnosticPacketAndNoteBuffersAreBounded() {
        var state = XStarState()
        repeat(250) { index ->
            state = XStarReducer.reduce(
                state,
                XStarEvent.ProtocolPacketObserved(
                    ProtocolPacketTrace(index.toLong(), "TEST/1", index, 1, 4, ProtocolPacketDisposition.DECODED, "TEST", "00 01")
                ),
                index.toLong()
            )
            state = XStarReducer.reduce(state, XStarEvent.DiagnosticNote("note-$index"), index.toLong())
        }

        assertEquals(200, state.diagnostics.packets.size)
        assertEquals(50L, state.diagnostics.packets.first().sequence)
        assertEquals(100, state.diagnostics.notes.size)
        assertEquals("note-150", state.diagnostics.notes.first())
    }

}
