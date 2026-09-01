package io.xstarrevival.app

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import android.view.TextureView
import androidx.annotation.RawRes
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
import io.xstarrevival.core.video.H264AccessUnit
import io.xstarrevival.core.video.H264AnnexBScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

enum class VideoReplayStatus { WAITING_FOR_SURFACE, PLAYING, ERROR }

data class VideoReplayUiState(
    val status: VideoReplayStatus = VideoReplayStatus.WAITING_FOR_SURFACE,
    val framesRendered: Int = 0,
    val frameCount: Int = 0,
    val loopCount: Int = 0,
    val error: String? = null
)

/** Renders an original raw H.264 fixture through Android's AVC decoder. */
@Composable
fun H264ReplayVideo(
    modifier: Modifier = Modifier,
    @RawRes resourceId: Int = R.raw.xstar_synthetic_fpv,
    onStateChanged: (VideoReplayUiState) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    H264ReplaySurface(
        modifier = modifier,
        streamKey = resourceId,
        loadStream = {
            withContext(Dispatchers.IO) {
                context.resources.openRawResource(resourceId).use { it.readBytes() }
            }
        },
        onStateChanged = onStateChanged
    )
}

/** Replays the last private bench capture without reconnecting to the aircraft. */
@Composable
fun H264CapturedVideo(
    videoPath: String,
    modifier: Modifier = Modifier,
    onStateChanged: (VideoReplayUiState) -> Unit
) {
    H264ReplaySurface(
        modifier = modifier,
        streamKey = videoPath,
        loadStream = { withContext(Dispatchers.IO) { File(videoPath).readBytes() } },
        onStateChanged = onStateChanged
    )
}

@Composable
@SuppressLint("Recycle") // Surface is released on replacement, TextureView destruction, and composition disposal.
private fun H264ReplaySurface(
    modifier: Modifier,
    streamKey: Any,
    loadStream: suspend () -> ByteArray,
    onStateChanged: (VideoReplayUiState) -> Unit
) {
    val currentStateCallback by rememberUpdatedState(onStateChanged)
    var decoderSurface by remember { mutableStateOf<Surface?>(null) }

    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { context ->
            TextureView(context).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(texture: android.graphics.SurfaceTexture, width: Int, height: Int) {
                        decoderSurface?.release()
                        decoderSurface = Surface(texture)
                    }

                    override fun onSurfaceTextureSizeChanged(texture: android.graphics.SurfaceTexture, width: Int, height: Int) = Unit

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

    LaunchedEffect(decoderSurface, streamKey) {
        val surface = decoderSurface ?: run {
            currentStateCallback(VideoReplayUiState())
            return@LaunchedEffect
        }
        val stream = loadStream()
        val player = AnnexBMediaCodecPlayer(stream)
        try {
            player.playLoop(surface) { currentStateCallback(it) }
        } catch (error: Exception) {
            if (currentCoroutineContext().isActive) {
                currentStateCallback(
                    VideoReplayUiState(
                        status = VideoReplayStatus.ERROR,
                        frameCount = player.frameCount,
                        error = error.message ?: error.javaClass.simpleName
                    )
                )
            }
        } finally {
            player.close()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            decoderSurface?.release()
            decoderSurface = null
        }
    }
}

private class AnnexBMediaCodecPlayer(stream: ByteArray) : Closeable {
    private val accessUnits = parseAccessUnits(stream)
    private var codec: MediaCodec? = null
    val frameCount: Int get() = accessUnits.size

    suspend fun playLoop(surface: Surface, update: (VideoReplayUiState) -> Unit) = withContext(Dispatchers.Default) {
        check(accessUnits.isNotEmpty()) { "The H.264 stream contains no complete pictures" }
        val decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec = decoder
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, VIDEO_WIDTH, VIDEO_HEIGHT).apply {
            setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FRAME_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_SIZE)
        }
        decoder.configure(format, surface, null, 0)
        decoder.start()

        var loop = 0
        while (currentCoroutineContext().isActive) {
            loop++
            accessUnits.forEachIndexed { index, accessUnit ->
                queueInput(decoder, accessUnit)
                if (index == 0 || (index + 1) % 5 == 0 || index == accessUnits.lastIndex) {
                    withContext(Dispatchers.Main.immediate) {
                        update(
                            VideoReplayUiState(
                                status = VideoReplayStatus.PLAYING,
                                framesRendered = index + 1,
                                frameCount = accessUnits.size,
                                loopCount = loop
                            )
                        )
                    }
                }
                delay(FRAME_DURATION_MS)
                drainOutput(decoder)
            }
            decoder.flush()
        }
    }

    private fun queueInput(decoder: MediaCodec, accessUnit: H264AccessUnit) {
        while (true) {
            val index = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
            if (index >= 0) {
                val buffer = checkNotNull(decoder.getInputBuffer(index))
                check(accessUnit.bytes.size <= buffer.capacity()) {
                    "H.264 access unit ${accessUnit.bytes.size} exceeds decoder input capacity ${buffer.capacity()}"
                }
                buffer.clear()
                buffer.put(accessUnit.bytes)
                decoder.queueInputBuffer(index, 0, accessUnit.bytes.size, accessUnit.presentationTimeUs, 0)
                return
            }
            drainOutput(decoder)
        }
    }

    private fun drainOutput(decoder: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val outputIndex = decoder.dequeueOutputBuffer(info, 0)
            when {
                outputIndex >= 0 -> decoder.releaseOutputBuffer(outputIndex, true)
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
        const val VIDEO_WIDTH = 640
        const val VIDEO_HEIGHT = 360
        const val VIDEO_FRAME_RATE = 15
        const val FRAME_DURATION_MS = 1_000L / VIDEO_FRAME_RATE
        const val CODEC_TIMEOUT_US = 10_000L
        const val MAX_INPUT_SIZE = 512 * 1024

        fun parseAccessUnits(stream: ByteArray): List<H264AccessUnit> {
            val scanner = H264AnnexBScanner(frameRate = VIDEO_FRAME_RATE)
            val output = mutableListOf<H264AccessUnit>()
            var offset = 0
            val chunkSizes = intArrayOf(1, 7, 511, 2048, 4093)
            var chunkIndex = 0
            while (offset < stream.size) {
                val end = (offset + chunkSizes[chunkIndex % chunkSizes.size]).coerceAtMost(stream.size)
                output += scanner.push(stream.copyOfRange(offset, end))
                offset = end
                chunkIndex++
            }
            output += scanner.endOfStream()
            return output
        }
    }
}
