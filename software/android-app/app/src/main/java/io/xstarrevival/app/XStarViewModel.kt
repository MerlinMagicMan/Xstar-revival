package io.xstarrevival.app

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.xstarrevival.core.XStarPlatform
import io.xstarrevival.core.adapter.AutelSdkBridge
import io.xstarrevival.core.adapter.AutelSdkPlatformAdapter
import io.xstarrevival.core.adapter.OpenXStarPlatformAdapter
import io.xstarrevival.core.mock.MockXStarPlatform
import io.xstarrevival.core.model.XStarState
import io.xstarrevival.core.protocol.mavlink.StandardMavlinkDecoder
import io.xstarrevival.core.replay.CaptureReplayState
import io.xstarrevival.core.replay.CaptureReplayTransport
import io.xstarrevival.core.replay.StandardMavlinkDemoCapture
import io.xstarrevival.core.video.H264VideoFrame
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

enum class TelemetrySource(val label: String) {
    MOCK("Mock"),
    MAVLINK_REPLAY("MAVLink Replay"),
    OFFICIAL_AUTEL("Live X-Star")
}

data class HeartbeatUiState(
    val ageMs: Long? = null,
    val stale: Boolean = false
)

class XStarViewModel(application: Application) : AndroidViewModel(application) {
    private val mockPlatform = MockXStarPlatform(viewModelScope)
    private val replayTransport = CaptureReplayTransport(
        scope = viewModelScope,
        chunks = StandardMavlinkDemoCapture.chunks,
        description = "synthetic MAVLink capture"
    )
    private val replayPlatform = OpenXStarPlatformAdapter(
        scope = viewModelScope,
        transport = replayTransport,
        decoder = StandardMavlinkDecoder()
    )
    private val officialPlatform = createOfficialPlatform()

    val availableSources: List<TelemetrySource> = buildList {
        add(TelemetrySource.MOCK)
        add(TelemetrySource.MAVLINK_REPLAY)
        if (officialPlatform != null) add(TelemetrySource.OFFICIAL_AUTEL)
    }
    val liveVideoFrames: Flow<H264VideoFrame> = officialPlatform?.h264Frames ?: emptyFlow()

    private var activePlatform: XStarPlatform = mockPlatform
    private var platformCollectionJob: Job? = null

    private val mutableState = MutableStateFlow(XStarState())
    val state: StateFlow<XStarState> = mutableState.asStateFlow()

    private val mutableSource = MutableStateFlow(TelemetrySource.MOCK)
    val source: StateFlow<TelemetrySource> = mutableSource.asStateFlow()

    private val mutablePlatformName = MutableStateFlow(mockPlatform.name)
    val platformName: StateFlow<String> = mutablePlatformName.asStateFlow()

    val replayState: StateFlow<CaptureReplayState> = replayTransport.playback

    private val mutableHeartbeat = MutableStateFlow(HeartbeatUiState())
    val heartbeat: StateFlow<HeartbeatUiState> = mutableHeartbeat.asStateFlow()

    private var lastHeartbeatCount = 0L
    private var lastHeartbeatElapsedMs: Long? = null

    init {
        collectActivePlatform()
        viewModelScope.launch {
            while (isActive) {
                updateHeartbeatAge()
                delay(HEARTBEAT_TICK_MS)
            }
        }
    }

    fun selectSource(value: TelemetrySource) {
        if (value == mutableSource.value) return
        if (value == TelemetrySource.OFFICIAL_AUTEL && officialPlatform == null) return
        viewModelScope.launch {
            activePlatform.disconnect()
            mutableSource.value = value
            activePlatform = when (value) {
                TelemetrySource.MOCK -> mockPlatform
                TelemetrySource.MAVLINK_REPLAY -> replayPlatform
                TelemetrySource.OFFICIAL_AUTEL -> checkNotNull(officialPlatform)
            }
            mutablePlatformName.value = activePlatform.name
            mutableState.value = XStarState()
            resetHeartbeat()
            collectActivePlatform()
            connectActivePlatform()
        }
    }

    fun connect() {
        viewModelScope.launch { connectActivePlatform() }
    }

    fun disconnect() {
        viewModelScope.launch {
            activePlatform.disconnect()
            resetHeartbeat()
        }
    }

    fun refresh() {
        viewModelScope.launch { activePlatform.refresh() }
    }

    fun playReplay() = replayTransport.play()

    fun pauseReplay() = replayTransport.pause()

    fun restartReplay() {
        if (mutableSource.value != TelemetrySource.MAVLINK_REPLAY) return
        viewModelScope.launch {
            replayPlatform.disconnect()
            resetHeartbeat()
            replayPlatform.connect()
            yield()
            replayTransport.restart()
        }
    }

    fun setReplaySpeed(speed: Double) = replayTransport.setSpeed(speed)

    private suspend fun connectActivePlatform() {
        runCatching { activePlatform.connect() }
        if (activePlatform === replayPlatform) {
            yield()
            replayTransport.restart()
        }
    }

    private fun collectActivePlatform() {
        platformCollectionJob?.cancel()
        platformCollectionJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            activePlatform.state.collect { next ->
                mutableState.value = next
                observeHeartbeat(next)
            }
        }
    }

    private fun observeHeartbeat(next: XStarState) {
        if (mutableSource.value != TelemetrySource.MAVLINK_REPLAY) return
        val count = next.diagnostics.counters["mavlink_heartbeats"] ?: 0L
        if (count < lastHeartbeatCount) resetHeartbeat()
        if (count > lastHeartbeatCount) {
            lastHeartbeatElapsedMs = SystemClock.elapsedRealtime()
            lastHeartbeatCount = count
            mutableHeartbeat.value = HeartbeatUiState(ageMs = 0, stale = false)
        }
    }

    private fun updateHeartbeatAge() {
        if (mutableSource.value != TelemetrySource.MAVLINK_REPLAY) {
            mutableHeartbeat.value = HeartbeatUiState()
            return
        }
        val observedAt = lastHeartbeatElapsedMs ?: return
        val age = (SystemClock.elapsedRealtime() - observedAt).coerceAtLeast(0)
        mutableHeartbeat.value = HeartbeatUiState(ageMs = age, stale = age > HEARTBEAT_STALE_MS)
    }

    private fun resetHeartbeat() {
        lastHeartbeatCount = 0
        lastHeartbeatElapsedMs = null
        mutableHeartbeat.value = HeartbeatUiState()
    }

    private fun createOfficialPlatform(): AutelSdkPlatformAdapter? {
        if (!BuildConfig.AUTEL_SDK_AVAILABLE) return null
        return runCatching {
            val bridgeClass = Class.forName("io.xstarrevival.autelsdk.OfficialAutelSdkBridge")
            val constructor = bridgeClass.getConstructor(android.content.Context::class.java, String::class.java)
            val bridge = constructor.newInstance(getApplication<Application>(), BuildConfig.AUTEL_APP_KEY) as AutelSdkBridge
            AutelSdkPlatformAdapter(viewModelScope, bridge)
        }.getOrNull()
    }

    override fun onCleared() {
        replayTransport.pause()
        super.onCleared()
    }

    private companion object {
        const val HEARTBEAT_TICK_MS = 250L
        const val HEARTBEAT_STALE_MS = 2_500L
    }
}
