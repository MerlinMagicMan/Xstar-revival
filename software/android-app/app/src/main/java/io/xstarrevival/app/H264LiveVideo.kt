package io.xstarrevival.app

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import io.xstarrevival.core.video.H264VideoFrame
import java.io.Closeable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

enum class LiveVideoStatus { WAITING_FOR_SURFACE, WAITING_FOR_KEYFRAME, PLAYING, ERROR }

data class LiveVideoUiState(
    val status: LiveVideoStatus = LiveVideoStatus.WAITING_FOR_SURFACE,
    val framesReceived: Long = 0,
    val framesRendered: Long = 0,
    val framesDropped: Long = 0,
    val error: String? = null
)

/** Decodes the official SDK's receive-only H.264 callback beneath the cockpit HUD. */
@Composable
fun H264LiveVideo(
    frames: Flow<H264VideoFrame>,
    modifier: Modifier = Modifier,
    onStateChanged: (LiveVideoUiState) -> Unit
) {
    val currentStateCallback by rememberUpdatedState(onStateChanged)
    var decoderSurface by remember { mutableStateOf<Surface?>(null) }

    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { context ->
            TextureView(context).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        texture: android.graphics.SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        decoderSurface?.release()
                        decoderSurface = Surface(texture)
                    }

                    override fun onSurfaceTextureSizeChanged(
                        texture: android.graphics.SurfaceTexture,
                        width: Int,
                        height: Int
                    ) = Unit

                    override fun onSurfaceTextureDestroyed(texture: android.graphics.SurfaceTexture): Boolean {
                        decoderSurface?.release()
                        decoderSurface = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(texture: android.graphics.SurfaceTexture) = Unit
                }
            }
        }
    )

    LaunchedEffect(decoderSurface, frames) {
        val surface = decoderSurface ?: run {
            currentStateCallback(LiveVideoUiState())
            return@LaunchedEffect
        }
        val receiver = LiveAvcReceiver(surface)
        try {
            withContext(Dispatchers.Default) {
                receiver.start()
                frames.collect { frame ->
                    val state = receiver.accept(frame)
                    withContext(Dispatchers.Main.immediate) { currentStateCallback(state) }
                }
            }
        } catch (error: Exception) {
            if (currentCoroutineContext().isActive) {
                currentStateCallback(receiver.state(error.message ?: error.javaClass.simpleName))
            }
        } finally {
            receiver.close()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            decoderSurface?.release()
            decoderSurface = null
        }
    }
}

private class LiveAvcReceiver(private val surface: Surface) : Closeable {
    private var codec: MediaCodec? = null
    private var received = 0L
    private var rendered = 0L
    private var dropped = 0L
    private var synchronized = false
    private var inputSequence = 0L

    fun start() {
        val decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec = decoder
        val format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            INITIAL_VIDEO_WIDTH,
            INITIAL_VIDEO_HEIGHT
        ).apply {
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_SIZE)
        }
        decoder.configure(format, surface, null, 0)
        decoder.start()
    }

    fun accept(frame: H264VideoFrame): LiveVideoUiState {
        received++
        if (!synchronized) {
            if (!frame.isKeyFrame) {
                dropped++
                return state()
            }
            synchronized = true
        }

        val decoder = checkNotNull(codec)
        drain(decoder)
        val inputIndex = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
        if (inputIndex < 0) {
            dropped++
            return state()
        }
        val payload = frame.payload()
        val input = checkNotNull(decoder.getInputBuffer(inputIndex))
        if (payload.size > input.capacity()) {
            dropped++
            decoder.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs(), 0)
            return state()
        }
        input.clear()
        input.put(payload)
        decoder.queueInputBuffer(
            inputIndex,
            0,
            payload.size,
            presentationTimeUs(),
            if (frame.isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
        )
        inputSequence++
        drain(decoder)
        return state()
    }

    fun state(error: String? = null): LiveVideoUiState = LiveVideoUiState(
        status = when {
            error != null -> LiveVideoStatus.ERROR
            rendered > 0 -> LiveVideoStatus.PLAYING
            else -> LiveVideoStatus.WAITING_FOR_KEYFRAME
        },
        framesReceived = received,
        framesRendered = rendered,
        framesDropped = dropped,
        error = error
    )

    private fun presentationTimeUs(): Long = inputSequence * FRAME_DURATION_US

    private fun drain(decoder: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val outputIndex = decoder.dequeueOutputBuffer(info, 0)
            when {
                outputIndex >= 0 -> {
                    decoder.releaseOutputBuffer(outputIndex, true)
                    rendered++
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                else -> return
            }
        }
    }

    override fun close() {
        codec?.let { decoder ->
            runCatching { decoder.stop() }
            runCatching { decoder.release() }
        }
        codec = null
    }

    private companion object {
        // SPS/PPS in the standard AVC stream supplies the actual picture size.
        const val INITIAL_VIDEO_WIDTH = 1280
        const val INITIAL_VIDEO_HEIGHT = 720
        const val FRAME_DURATION_US = 1_000_000L / 30L
        const val CODEC_TIMEOUT_US = 5_000L
        const val MAX_INPUT_SIZE = 2 * 1024 * 1024
    }
}
