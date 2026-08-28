package io.xstarrevival.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.xstarrevival.core.XStarPlatform
import io.xstarrevival.core.mock.MockXStarPlatform
import io.xstarrevival.core.model.XStarState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class XStarViewModel : ViewModel() {
    private val platform: XStarPlatform = MockXStarPlatform(viewModelScope)

    val state: StateFlow<XStarState> = platform.state
    val platformName: String get() = platform.name

    fun connect() {
        viewModelScope.launch { platform.connect() }
    }

    fun disconnect() {
        viewModelScope.launch { platform.disconnect() }
    }

    fun refresh() {
        viewModelScope.launch { platform.refresh() }
    }

    override fun onCleared() {
        viewModelScope.launch { platform.disconnect() }
        super.onCleared()
    }
}
