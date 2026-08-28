package io.xstarrevival.core.video

import java.io.Closeable
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Writer

enum class H264CaptureStopReason {
    USER,
    DURATION_LIMIT,
    BYTE_LIMIT,
    SOURCE_ENDED,
    ERROR
}

data class H264CaptureStats(
    val synchronized: Boolean = false,
    val framesWritten: Long = 0,
    val keyframesWritten: Long = 0,
    val bytesWritten: Long = 0,
    val framesDroppedBeforeKeyframe: Long = 0,
    val elapsedMs: Long = 0,
    val stopReason: H264CaptureStopReason? = null
) {
    val complete: Boolean get() = stopReason != null
}

/**
 * Writes untouched SDK H.264 callback payloads and a deterministic JSONL frame index.
 *
 * The SDK timestamp is retained as an opaque integer because its unit is not
 * documented. Capture begins with bounded standard SPS/PPS setup callbacks,
 * when observed, followed by an SDK-marked keyframe. Opaque and delta-picture
 * callbacks before that keyframe are dropped.
 */
class H264CaptureWriter(
    videoOutput: OutputStream,
    indexOutput: OutputStream,
    private val maxBytes: Long,
    private val maxDurationMs: Long,
    private val elapsedRealtimeMs: () -> Long
) : Closeable {
    private val video = videoOutput.buffered()
    private val index: Writer = OutputStreamWriter(indexOutput.buffered(), Charsets.UTF_8)
    private val startedAtMs = elapsedRealtimeMs()
    private var current = H264CaptureStats()
    private val pendingCodecSetup = mutableListOf<PendingCallback>()
    private var pendingCodecSetupBytes = 0L
    private var closed = false

    init {
        require(maxBytes > 0) { "maxBytes must be positive" }
        require(maxDurationMs > 0) { "maxDurationMs must be positive" }
        index.appendLine(
                "{\"record\":\"header\",\"format\":\"xstar-sdk-h264-callbacks\",\"version\":1," +
                "\"sdk_timestamp_units\":\"source-defined\",\"max_bytes\":$maxBytes," +
                "\"max_duration_ms\":$maxDurationMs}"
        )
    }

    @Synchronized
    fun append(frame: H264VideoFrame): H264CaptureStats {
        check(!closed) { "capture writer is closed" }
        if (current.complete) return current

        val elapsed = elapsed()
        if (elapsed >= maxDurationMs) return finish(H264CaptureStopReason.DURATION_LIMIT)

        val payload = frame.payload()
        if (!current.synchronized && !frame.isKeyFrame) {
            if (
                H264AnnexBInspector.isCodecSetup(payload) &&
                pendingCodecSetupBytes + payload.size <= MAX_PENDING_CODEC_SETUP_BYTES
            ) {
                pendingCodecSetup += PendingCallback(payload, frame.presentationTimestamp, elapsed)
                pendingCodecSetupBytes += payload.size
            } else {
                current = current.copy(
                    framesDroppedBeforeKeyframe = current.framesDroppedBeforeKeyframe + 1,
                    elapsedMs = elapsed
                )
            }
            return current
        }

        val bytesRequired = payload.size + if (current.synchronized) 0L else pendingCodecSetupBytes
        if (current.bytesWritten + bytesRequired > maxBytes) {
            return finish(H264CaptureStopReason.BYTE_LIMIT)
        }

        if (!current.synchronized) {
            pendingCodecSetup.forEach { setup ->
                writeCallback(
                    payload = setup.payload,
                    keyframe = false,
                    codecSetup = true,
                    sdkTimestamp = setup.sdkTimestamp,
                    elapsed = setup.elapsedMs
                )
            }
            pendingCodecSetup.clear()
            pendingCodecSetupBytes = 0
        }

        writeCallback(
            payload = payload,
            keyframe = frame.isKeyFrame,
            codecSetup = false,
            sdkTimestamp = frame.presentationTimestamp,
            elapsed = elapsed
        )
        current = current.copy(synchronized = true, elapsedMs = elapsed)
        return current
    }

    private fun writeCallback(
        payload: ByteArray,
        keyframe: Boolean,
        codecSetup: Boolean,
        sdkTimestamp: Long,
        elapsed: Long
    ) {
        if (current.bytesWritten + payload.size > maxBytes) {
            error("capture byte ceiling checked before callback write")
        }

        val offset = current.bytesWritten
        video.write(payload)
        index.appendLine(
            "{\"record\":\"frame\",\"index\":${current.framesWritten}," +
                "\"offset\":$offset,\"length\":${payload.size}," +
                "\"keyframe\":$keyframe,\"codec_setup\":$codecSetup," +
                "\"sdk_timestamp\":$sdkTimestamp,\"elapsed_ms\":$elapsed}"
        )
        current = current.copy(
            framesWritten = current.framesWritten + 1,
            keyframesWritten = current.keyframesWritten + if (keyframe) 1 else 0,
            bytesWritten = current.bytesWritten + payload.size,
            elapsedMs = elapsed
        )
    }

    @Synchronized
    fun finish(reason: H264CaptureStopReason): H264CaptureStats {
        if (current.complete) return current
        current = current.copy(elapsedMs = elapsed(), stopReason = reason)
        index.appendLine(
            "{\"record\":\"footer\",\"frames\":${current.framesWritten}," +
                "\"keyframes\":${current.keyframesWritten},\"bytes\":${current.bytesWritten}," +
                "\"dropped_before_keyframe\":${current.framesDroppedBeforeKeyframe}," +
                "\"elapsed_ms\":${current.elapsedMs},\"stop_reason\":\"${reason.name}\"}"
        )
        video.flush()
        index.flush()
        return current
    }

    @Synchronized
    fun stats(): H264CaptureStats = current.copy(elapsedMs = elapsed())

    override fun close() {
        synchronized(this) {
            if (closed) return
            if (!current.complete) finish(H264CaptureStopReason.SOURCE_ENDED)
            closed = true
        }
        runCatching { video.close() }
        runCatching { index.close() }
    }

    private fun elapsed(): Long = (elapsedRealtimeMs() - startedAtMs).coerceAtLeast(0)

    private data class PendingCallback(
        val payload: ByteArray,
        val sdkTimestamp: Long,
        val elapsedMs: Long
    )

    private companion object {
        const val MAX_PENDING_CODEC_SETUP_BYTES = 256L * 1024L
    }
}
