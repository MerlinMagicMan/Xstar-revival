package io.xstarrevival.core.adapter

import io.xstarrevival.core.event.XStarEvent
import io.xstarrevival.core.model.ConnectionState
import io.xstarrevival.core.model.DiagnosticsState
import io.xstarrevival.core.model.XStarState
import io.xstarrevival.core.video.H264VideoFrame
import io.xstarrevival.core.video.H264VideoSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transform

/**
 * Hardware-independent adapter around an Android-specific AutelSdkBridge.
 * The proprietary SDK remains outside app-core.
 */
class AutelSdkPlatformAdapter(
    scope: CoroutineScope,
    private val bridge: AutelSdkBridge
) : EventBackedPlatform(scope), H264VideoSource {
    private val mutableH264Frames = MutableSharedFlow<H264VideoFrame>(
        extraBufferCapacity = VIDEO_FRAME_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val h264Frames: Flow<H264VideoFrame> = mutableH264Frames.asSharedFlow()

    override val name: String = "Official Autel SDK"

    override suspend fun connect() {
        replaceState(
            XStarState(
                connection = ConnectionState.Connecting("sdk-initialize"),
                diagnostics = DiagnosticsState(source = "official-autel-sdk")
            )
        )
        startCollecting(
            merge(
                bridge.observations.transform { observation ->
                    AutelSdkObservationMapper.map(observation, bridge.description).forEach { emit(it) }
                },
                bridge.videoFrames.map { frame ->
                    mutableH264Frames.tryEmit(frame)
                    XStarEvent.VideoFrameReceived(frame.validSize, frame.isKeyFrame)
                }
            )
        )
        try {
            bridge.initialize()
            emit(XStarEvent.ConnectionChanged(ConnectionState.Connecting("product-discovery")))
            bridge.connect()
        } catch (t: Throwable) {
            emit(XStarEvent.ConnectionChanged(ConnectionState.Failed("official-sdk", t.message ?: t::class.simpleName.orEmpty())))
            throw t
        }
    }

    override suspend fun refresh() {
        bridge.refreshReadOnlyState()
    }

    override suspend fun disconnect() {
        try {
            bridge.disconnect()
        } finally {
            stopCollecting()
            emit(XStarEvent.VideoSnapshot(receiving = false))
            emit(XStarEvent.ConnectionChanged(ConnectionState.Disconnected))
        }
    }

    private companion object {
        const val VIDEO_FRAME_BUFFER = 32
    }
}
