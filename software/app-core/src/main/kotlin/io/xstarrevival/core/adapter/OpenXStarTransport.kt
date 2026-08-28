package io.xstarrevival.core.adapter

import io.xstarrevival.core.event.XStarEvent
import kotlinx.coroutines.flow.Flow

/** Raw read-only transport for the independent implementation. */
interface OpenXStarTransport {
    val incoming: Flow<ByteArray>
    val description: String

    suspend fun connect()
    suspend fun disconnect()
}

/** Converts transport bytes into normalized application events. */
interface OpenXStarDecoder {
    fun decode(chunk: ByteArray): List<XStarEvent>
    fun reset()
}
