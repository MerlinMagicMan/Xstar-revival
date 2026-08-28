package io.xstarrevival.core.adapter

import io.xstarrevival.core.event.XStarEvent
import io.xstarrevival.core.model.ConnectionState
import kotlinx.coroutines.CoroutineScope

/**
 * Hardware-independent adapter around an Android-specific AutelSdkBridge.
 * The proprietary SDK remains outside app-core.
 */
class AutelSdkPlatformAdapter(
    scope: CoroutineScope,
    private val bridge: AutelSdkBridge
) : EventBackedPlatform(scope) {
    override val name: String = "Official Autel SDK"

    override suspend fun connect() {
        emit(XStarEvent.ConnectionChanged(ConnectionState.Connecting("sdk-initialize")))
        startCollecting(bridge.events)
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
            emit(XStarEvent.ConnectionChanged(ConnectionState.Disconnected))
        }
    }
}
