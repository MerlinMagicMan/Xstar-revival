package io.xstarrevival.core.adapter

import io.xstarrevival.core.video.H264VideoFrame
import kotlinx.coroutines.flow.Flow

/**
 * Narrow read-only seam around Autel's legacy Mobile SDK.
 *
 * The app-core module deliberately does not depend on Autel's proprietary AAR.
 * An Android integration module can implement this bridge when the SDK binary
 * and app-key/authentication path are available.
 */
interface AutelSdkBridge {
    /** Hot callback streams owned by the Android SDK binding. */
    val observations: Flow<AutelSdkObservation>
    val videoFrames: Flow<H264VideoFrame>
    val description: String

    suspend fun initialize()
    suspend fun connect()
    suspend fun disconnect()
    suspend fun refreshReadOnlyState()
}
