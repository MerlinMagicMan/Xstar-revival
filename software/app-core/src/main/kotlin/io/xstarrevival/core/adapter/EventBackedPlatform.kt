package io.xstarrevival.core.adapter

import io.xstarrevival.core.XStarPlatform
import io.xstarrevival.core.event.XStarEvent
import io.xstarrevival.core.event.XStarReducer
import io.xstarrevival.core.model.XStarState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

abstract class EventBackedPlatform(
    private val scope: CoroutineScope,
    initialState: XStarState = XStarState()
) : XStarPlatform {
    private val mutableState = MutableStateFlow(initialState)
    override val state: StateFlow<XStarState> = mutableState.asStateFlow()
    private var eventJob: Job? = null

    protected fun startCollecting(events: kotlinx.coroutines.flow.Flow<XStarEvent>) {
        eventJob?.cancel()
        eventJob = scope.launch {
            events.collect { event ->
                mutableState.value = XStarReducer.reduce(mutableState.value, event)
            }
        }
    }

    protected fun emit(event: XStarEvent) {
        mutableState.value = XStarReducer.reduce(mutableState.value, event)
    }

    protected fun stopCollecting() {
        eventJob?.cancel()
        eventJob = null
    }
}
