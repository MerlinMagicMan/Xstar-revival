package io.xstarrevival.core.video

import kotlinx.coroutines.flow.Flow

/** A receive-only H.264 callback payload. Timestamp units remain source-defined. */
class H264VideoFrame(
    bytes: ByteArray,
    val isKeyFrame: Boolean,
    val validSize: Int,
    val presentationTimestamp: Long
) {
    private val frameBytes: ByteArray = bytes.copyOf()
    val bytes: ByteArray get() = frameBytes.copyOf()

    init {
        require(validSize in 0..bytes.size) {
            "validSize must be between zero and the callback buffer size"
        }
    }

    fun payload(): ByteArray = frameBytes.copyOf(validSize)
}

/** Optional platform capability for compressed video reception; it has no write method. */
interface H264VideoSource {
    val h264Frames: Flow<H264VideoFrame>
}
