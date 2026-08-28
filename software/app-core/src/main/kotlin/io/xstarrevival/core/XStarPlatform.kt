package io.xstarrevival.core

import io.xstarrevival.core.model.XStarState
import kotlinx.coroutines.flow.StateFlow

/**
 * Read-only platform contract for the current project phase.
 *
 * Flight-control methods are intentionally absent. Future control capabilities
 * must live behind a separate, explicitly safety-reviewed interface.
 */
interface XStarPlatform {
    val state: StateFlow<XStarState>
    val name: String

    suspend fun connect()
    suspend fun disconnect()

    /**
     * Request an immediate read-only refresh where the backing platform supports it.
     */
    suspend fun refresh()
}
