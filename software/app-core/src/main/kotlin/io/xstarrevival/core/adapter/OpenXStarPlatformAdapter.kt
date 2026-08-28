package io.xstarrevival.core.adapter

import io.xstarrevival.core.event.XStarEvent
import io.xstarrevival.core.model.ConnectionState
import io.xstarrevival.core.model.DiagnosticsState
import io.xstarrevival.core.model.XStarState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class OpenXStarPlatformAdapter(
    private val scope: CoroutineScope,
    private val transport: OpenXStarTransport,
    private val decoder: OpenXStarDecoder
) : EventBackedPlatform(scope) {
    override val name: String = "Open X-Star"
    private var readJob: Job? = null

    override suspend fun connect() {
        replaceState(
            XStarState(
                connection = ConnectionState.Connecting("usb-transport"),
                diagnostics = DiagnosticsState(source = "open-mavlink")
            )
        )
        decoder.reset()
        try {
            transport.connect()
            emit(XStarEvent.ConnectionChanged(ConnectionState.Connected(transport.description, null)))
            readJob?.cancel()
            readJob = scope.launch {
                transport.incoming.collect { chunk ->
                    decoder.decode(chunk).forEach(::emit)
                }
            }
        } catch (t: Throwable) {
            emit(XStarEvent.ConnectionChanged(ConnectionState.Failed("open-transport", t.message ?: t::class.simpleName.orEmpty())))
            throw t
        }
    }

    override suspend fun refresh() {
        emit(XStarEvent.DiagnosticNote("Open transport refresh is passive until request framing is validated"))
    }

    override suspend fun disconnect() {
        readJob?.cancel()
        readJob = null
        try {
            transport.disconnect()
        } finally {
            decoder.reset()
            replaceState(XStarState(connection = ConnectionState.Disconnected))
        }
    }
}
