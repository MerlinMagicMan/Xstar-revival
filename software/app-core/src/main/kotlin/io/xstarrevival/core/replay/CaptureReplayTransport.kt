package io.xstarrevival.core.replay

import io.xstarrevival.core.adapter.OpenXStarTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CaptureChunk(
    val delayMs: Long,
    val bytes: ByteArray
)

enum class CaptureReplayStatus { IDLE, PLAYING, PAUSED, COMPLETE }

data class CaptureReplayState(
    val status: CaptureReplayStatus = CaptureReplayStatus.IDLE,
    val chunkIndex: Int = 0,
    val chunkCount: Int = 0,
    val speed: Double = 1.0
) {
    val progress: Float
        get() = if (chunkCount == 0) 0f else chunkIndex.toFloat() / chunkCount
}

/**
 * Feeds recorded byte chunks into the same passive transport boundary used by
 * future USB hardware. It deliberately exposes no write or command API.
 */
class CaptureReplayTransport(
    private val scope: CoroutineScope,
    private val chunks: List<CaptureChunk>,
    override val description: String = "capture replay"
) : OpenXStarTransport {
    private val mutableIncoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = chunks.size.coerceAtLeast(1))
    override val incoming: Flow<ByteArray> = mutableIncoming.asSharedFlow()

    private val mutablePlayback = MutableStateFlow(CaptureReplayState(chunkCount = chunks.size))
    val playback: StateFlow<CaptureReplayState> = mutablePlayback.asStateFlow()

    private var connected = false
    private var playbackJob: Job? = null

    override suspend fun connect() {
        connected = true
        mutablePlayback.value = mutablePlayback.value.copy(status = CaptureReplayStatus.IDLE)
    }

    override suspend fun disconnect() {
        connected = false
        playbackJob?.cancel()
        playbackJob = null
        mutablePlayback.value = CaptureReplayState(chunkCount = chunks.size, speed = mutablePlayback.value.speed)
    }

    fun play() {
        if (!connected || playbackJob?.isActive == true || mutablePlayback.value.status == CaptureReplayStatus.COMPLETE) return
        mutablePlayback.value = mutablePlayback.value.copy(status = CaptureReplayStatus.PLAYING)
        playbackJob = scope.launch {
            while (mutablePlayback.value.chunkIndex < chunks.size) {
                val chunk = chunks[mutablePlayback.value.chunkIndex]
                val scaledDelay = (chunk.delayMs / mutablePlayback.value.speed).toLong().coerceAtLeast(0)
                if (scaledDelay > 0) delay(scaledDelay)
                mutableIncoming.emit(chunk.bytes.copyOf())
                mutablePlayback.value = mutablePlayback.value.copy(chunkIndex = mutablePlayback.value.chunkIndex + 1)
            }
            mutablePlayback.value = mutablePlayback.value.copy(status = CaptureReplayStatus.COMPLETE)
            playbackJob = null
        }
    }

    fun pause() {
        if (mutablePlayback.value.status != CaptureReplayStatus.PLAYING) return
        playbackJob?.cancel()
        playbackJob = null
        mutablePlayback.value = mutablePlayback.value.copy(status = CaptureReplayStatus.PAUSED)
    }

    fun restart() {
        if (!connected) return
        playbackJob?.cancel()
        playbackJob = null
        mutablePlayback.value = mutablePlayback.value.copy(
            status = CaptureReplayStatus.IDLE,
            chunkIndex = 0
        )
        play()
    }

    fun setSpeed(speed: Double) {
        require(speed in SUPPORTED_SPEEDS) { "Unsupported replay speed: $speed" }
        mutablePlayback.value = mutablePlayback.value.copy(speed = speed)
    }

    companion object {
        val SUPPORTED_SPEEDS = setOf(0.5, 1.0, 2.0)
    }
}
