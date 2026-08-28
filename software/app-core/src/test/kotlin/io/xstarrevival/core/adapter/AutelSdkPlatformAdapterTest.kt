package io.xstarrevival.core.adapter

import io.xstarrevival.core.model.ConnectionState
import io.xstarrevival.core.video.H264VideoFrame
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AutelSdkPlatformAdapterTest {
    @Test
    fun `official callbacks become state and raw H264 remains available`() = runTest {
        val bridge = FakeAutelSdkBridge()
        val platform = AutelSdkPlatformAdapter(this, bridge)
        val receivedFrames = mutableListOf<H264VideoFrame>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            platform.h264Frames.take(1).toList(receivedFrames)
        }

        try {
            platform.connect()
            advanceUntilIdle()
            bridge.observations.emit(
                AutelSdkObservation.ProductConnected(
                    "X-Star Premium",
                    "2.0.12",
                    setOf(AutelSdkComponent.BATTERY, AutelSdkComponent.FLIGHT_CONTROLLER, AutelSdkComponent.CODEC)
                )
            )
            bridge.observations.emit(
                AutelSdkObservation.Battery(
                    percent = 81,
                    packVoltageMv = 16_020.0,
                    currentMa = -1_250.0,
                    cellVoltagesMv = listOf(4_010, 4_007, 4_005, 3_998)
                )
            )
            bridge.observations.emit(
                AutelSdkObservation.Flight(
                    latitudeDeg = 41.8781,
                    longitudeDeg = -87.6298,
                    satellites = 14,
                    altitudeM = 22.4,
                    ultrasonicHeight = AutelDistance(2.3, AutelDistanceUnit.METERS),
                    attitude = AutelAttitude(1.5, -2.0, 181.0, AutelAngleUnit.DEGREES),
                    armed = false,
                    flightMode = "GPS"
                )
            )
            bridge.observations.emit(
                AutelSdkObservation.ImageLink(usbEnabled = true, rfFrequencyHz = 906_000_000.0, rfSignalValue = 68)
            )
            bridge.videoFrames.emit(
                H264VideoFrame(
                    bytes = byteArrayOf(0, 0, 0, 1, 0x65, 1, 2, 99),
                    isKeyFrame = true,
                    validSize = 7,
                    presentationTimestamp = 42L
                )
            )
            advanceUntilIdle()

            val state = platform.state.value
            assertEquals(ConnectionState.Connected("test-autel-sdk", "X-Star Premium"), state.connection)
            assertEquals(16.02, state.battery.packVoltageV)
            assertEquals(4, state.battery.cells.size)
            assertEquals(2.3, state.navigation.ultrasonicHeightM)
            assertEquals(1.5, state.attitude.rollDeg)
            assertEquals(true, state.imageLink.usbEnabled)
            assertEquals(1L, state.camera.video.framesReceived)
            assertEquals(7L, state.diagnostics.counters["official_h264_bytes"])
            assertEquals(1, receivedFrames.size)
            assertContentEquals(byteArrayOf(0, 0, 0, 1, 0x65, 1, 2), receivedFrames.single().payload())
            assertTrue(bridge.initialized)
            assertTrue(bridge.connected)
        } finally {
            platform.disconnect()
            advanceUntilIdle()
        }

        assertFalse(bridge.connected)
        assertEquals(ConnectionState.Disconnected, platform.state.value.connection)
        assertFalse(platform.state.value.camera.video.receiving)
    }

    @Test
    fun `bridge contract contains no control methods`() {
        val forbiddenNames = setOf(
            "takeOff",
            "land",
            "goHome",
            "prepareMission",
            "startMission",
            "setGimbalAngle",
            "startRecordVideo",
            "startTakePhoto",
            "formatSDCard",
            "setCurrentRFData"
        )

        val bridgeMethods = AutelSdkBridge::class.java.methods.map { it.name }.toSet()
        assertTrue(bridgeMethods.intersect(forbiddenNames).isEmpty())
    }

    private class FakeAutelSdkBridge : AutelSdkBridge {
        override val observations = MutableSharedFlow<AutelSdkObservation>(extraBufferCapacity = 16)
        override val videoFrames = MutableSharedFlow<H264VideoFrame>(extraBufferCapacity = 4)
        override val description = "test-autel-sdk"
        var initialized = false
        var connected = false

        override suspend fun initialize() {
            initialized = true
        }

        override suspend fun connect() {
            connected = true
        }

        override suspend fun disconnect() {
            connected = false
        }

        override suspend fun refreshReadOnlyState() = Unit
    }
}
