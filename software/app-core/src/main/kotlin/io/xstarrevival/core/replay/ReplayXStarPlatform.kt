package io.xstarrevival.core.replay

import io.xstarrevival.core.XStarPlatform
import io.xstarrevival.core.event.XStarEvent
import io.xstarrevival.core.event.XStarReducer
import io.xstarrevival.core.model.ConnectionState
import io.xstarrevival.core.model.DiagnosticsState
import io.xstarrevival.core.model.XStarState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ReplayFrame(
    val delayMs: Long,
    val event: XStarEvent
)

class ReplayXStarPlatform(
    private val frames: List<ReplayFrame>,
    private val loop: Boolean = false,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : XStarPlatform {
    override val name: String = "Capture Replay"

    private val mutableState = MutableStateFlow(
        XStarState(diagnostics = DiagnosticsState(source = "replay"))
    )
    override val state: StateFlow<XStarState> = mutableState.asStateFlow()

    private var replayJob: Job? = null

    override suspend fun connect() {
        replayJob?.cancel()
        mutableState.value = XStarState(
            connection = ConnectionState.Connected("replay", "X-Star capture"),
            diagnostics = DiagnosticsState(source = "replay")
        )
        replayJob = scope.launch { runReplay() }
    }

    override suspend fun disconnect() {
        replayJob?.cancel()
        replayJob = null
        mutableState.value = XStarState(
            connection = ConnectionState.Disconnected,
            diagnostics = DiagnosticsState(source = "replay")
        )
    }

    override suspend fun refresh() {
        // Replay is event-driven. No read request is required.
    }

    private suspend fun runReplay() {
        if (frames.isEmpty()) return
        do {
            for (frame in frames) {
                if (!scope.isActive) return
                if (frame.delayMs > 0) delay(frame.delayMs)
                mutableState.value = XStarReducer.reduce(mutableState.value, frame.event)
            }
        } while (loop && scope.isActive)
    }
}
