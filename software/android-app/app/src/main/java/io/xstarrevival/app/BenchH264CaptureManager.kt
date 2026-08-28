package io.xstarrevival.app

import android.content.Context
import android.os.SystemClock
import io.xstarrevival.core.video.H264CaptureStats
import io.xstarrevival.core.video.H264CaptureStopReason
import io.xstarrevival.core.video.H264CaptureWriter
import io.xstarrevival.core.video.H264VideoFrame
import io.xstarrevival.core.model.XStarState
import io.xstarrevival.core.replay.SanitizedTelemetryCaptureWriter
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

enum class BenchCaptureStatus { IDLE, WAITING_FOR_KEYFRAME, RECORDING, COMPLETE, ERROR }

data class BenchCaptureUiState(
    val status: BenchCaptureStatus = BenchCaptureStatus.IDLE,
    val captureId: String? = null,
    val framesWritten: Long = 0,
    val keyframesWritten: Long = 0,
    val bytesWritten: Long = 0,
    val framesDroppedBeforeKeyframe: Long = 0,
    val telemetrySamples: Long = 0,
    val elapsedMs: Long = 0,
    val stopReason: H264CaptureStopReason? = null,
    val archivePath: String? = null,
    val videoPath: String? = null,
    val error: String? = null
) {
    val active: Boolean
        get() = status == BenchCaptureStatus.WAITING_FOR_KEYFRAME || status == BenchCaptureStatus.RECORDING
}

/** Creates bounded, local-only bench captures from the receive-only video flow. */
class BenchH264CaptureManager(
    context: Context,
    private val scope: CoroutineScope,
    private val frames: Flow<H264VideoFrame>,
    private val telemetry: Flow<XStarState>,
    private val appVersion: String,
    private val sdkAarSha256: String,
    private val elapsedRealtimeMs: () -> Long = SystemClock::elapsedRealtime
) {
    private val captureDirectory = File(context.cacheDir, CAPTURE_DIRECTORY)
    private val mutableState = MutableStateFlow(BenchCaptureUiState())
    val state: StateFlow<BenchCaptureUiState> = mutableState.asStateFlow()

    private var captureJob: Job? = null
    private val requestedStop = AtomicReference<H264CaptureStopReason?>(null)

    fun start() {
        if (captureJob?.isActive == true) return
        requestedStop.set(null)
        val captureId = captureId()
        mutableState.value = BenchCaptureUiState(
            status = BenchCaptureStatus.WAITING_FOR_KEYFRAME,
            captureId = captureId
        )
        captureJob = scope.launch(Dispatchers.IO) { record(captureId) }
    }

    fun stop(reason: H264CaptureStopReason = H264CaptureStopReason.USER) {
        val job = captureJob ?: return
        if (!job.isActive) return
        requestedStop.compareAndSet(null, reason)
        job.cancel()
    }

    private suspend fun record(captureId: String) {
        if (!captureDirectory.exists() && !captureDirectory.mkdirs()) {
            mutableState.value = mutableState.value.copy(
                status = BenchCaptureStatus.ERROR,
                error = "Could not create the private capture directory"
            )
            return
        }
        pruneOldArchives()
        val videoFile = File(captureDirectory, "$captureId.h264.part")
        val indexFile = File(captureDirectory, "$captureId.frames.jsonl.part")
        val telemetryFile = File(captureDirectory, "$captureId.telemetry.jsonl.part")
        val finalVideoFile = File(captureDirectory, "$captureId.h264")
        val finalIndexFile = File(captureDirectory, "$captureId.frames.jsonl")
        val finalTelemetryFile = File(captureDirectory, "$captureId.telemetry.jsonl")
        val archiveFile = File(captureDirectory, "$captureId.zip")
        var writer: H264CaptureWriter? = null
        var telemetryWriter: SanitizedTelemetryCaptureWriter? = null
        var stopReason: H264CaptureStopReason? = null
        var failure: String? = null

        try {
            val captureWriter = H264CaptureWriter(
                videoOutput = FileOutputStream(videoFile),
                indexOutput = FileOutputStream(indexFile),
                maxBytes = MAX_CAPTURE_BYTES,
                maxDurationMs = MAX_CAPTURE_DURATION_MS,
                elapsedRealtimeMs = elapsedRealtimeMs
            )
            writer = captureWriter
            val sanitizedTelemetryWriter = SanitizedTelemetryCaptureWriter(
                output = FileOutputStream(telemetryFile),
                maxBytes = MAX_TELEMETRY_BYTES,
                elapsedRealtimeMs = elapsedRealtimeMs
            )
            telemetryWriter = sanitizedTelemetryWriter
            withTimeout(MAX_CAPTURE_DURATION_MS) {
                coroutineScope {
                    val telemetryJob = launch {
                        telemetry.collect { state -> sanitizedTelemetryWriter.append(state) }
                    }
                    try {
                        frames.takeWhile { frame ->
                            val stats = captureWriter.append(frame)
                            publish(captureId, stats, sanitizedTelemetryWriter.stats().samplesWritten)
                            !stats.complete
                        }.collect()
                    } finally {
                        telemetryJob.cancelAndJoin()
                    }
                }
            }
            stopReason = captureWriter.stats().stopReason ?: H264CaptureStopReason.SOURCE_ENDED
        } catch (_: TimeoutCancellationException) {
            stopReason = H264CaptureStopReason.DURATION_LIMIT
        } catch (_: CancellationException) {
            stopReason = requestedStop.get() ?: H264CaptureStopReason.SOURCE_ENDED
        } catch (error: Exception) {
            stopReason = H264CaptureStopReason.ERROR
            failure = error.message ?: error.javaClass.simpleName
        } finally {
            val finalStats = writer?.runCatching {
                finish(stopReason ?: H264CaptureStopReason.ERROR)
            }?.getOrElse { error ->
                failure = failure ?: error.message ?: error.javaClass.simpleName
                checkNotNull(writer).stats().copy(stopReason = H264CaptureStopReason.ERROR)
            }
            telemetryWriter?.close()
            writer?.close()

            val archive = if (finalStats != null) {
                runCatching {
                    finalizePart(videoFile, finalVideoFile)
                    finalizePart(indexFile, finalIndexFile)
                    finalizePart(telemetryFile, finalTelemetryFile)
                    createArchive(
                        archiveFile,
                        captureId,
                        finalVideoFile,
                        finalIndexFile,
                        finalTelemetryFile
                    )
                    archiveFile
                }.getOrElse { error ->
                    failure = failure ?: error.message ?: error.javaClass.simpleName
                    archiveFile.delete()
                    finalVideoFile.delete()
                    finalIndexFile.delete()
                    finalTelemetryFile.delete()
                    null
                }
            } else {
                null
            }
            videoFile.delete()
            indexFile.delete()
            telemetryFile.delete()
            captureJob = null

            mutableState.value = BenchCaptureUiState(
                status = if (failure == null && archive != null) BenchCaptureStatus.COMPLETE else BenchCaptureStatus.ERROR,
                captureId = captureId,
                framesWritten = finalStats?.framesWritten ?: 0,
                keyframesWritten = finalStats?.keyframesWritten ?: 0,
                bytesWritten = finalStats?.bytesWritten ?: 0,
                framesDroppedBeforeKeyframe = finalStats?.framesDroppedBeforeKeyframe ?: 0,
                telemetrySamples = telemetryWriter?.stats()?.samplesWritten ?: 0,
                elapsedMs = finalStats?.elapsedMs ?: 0,
                stopReason = finalStats?.stopReason ?: stopReason,
                archivePath = archive?.absolutePath,
                videoPath = finalVideoFile.takeIf { archive != null && finalStats?.framesWritten != 0L }?.absolutePath,
                error = failure
            )
        }
    }

    private fun publish(captureId: String, stats: H264CaptureStats, telemetrySamples: Long) {
        mutableState.value = BenchCaptureUiState(
            status = if (stats.synchronized) BenchCaptureStatus.RECORDING else BenchCaptureStatus.WAITING_FOR_KEYFRAME,
            captureId = captureId,
            framesWritten = stats.framesWritten,
            keyframesWritten = stats.keyframesWritten,
            bytesWritten = stats.bytesWritten,
            framesDroppedBeforeKeyframe = stats.framesDroppedBeforeKeyframe,
            telemetrySamples = telemetrySamples,
            elapsedMs = stats.elapsedMs,
            stopReason = stats.stopReason
        )
    }

    private fun createArchive(
        archive: File,
        captureId: String,
        video: File,
        index: File,
        telemetry: File
    ) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(archive))).use { zip ->
            zip.putNextEntry(ZipEntry("$captureId.h264"))
            video.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("$captureId.telemetry.jsonl"))
            telemetry.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("$captureId.frames.jsonl"))
            index.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("README.txt"))
            zip.write(CAPTURE_README.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("session.json"))
            zip.write(
                (
                    "{\"format\":\"xstar-passive-bench-capture\",\"version\":1," +
                        "\"capture_id\":${jsonString(captureId)}," +
                        "\"app_version\":${jsonString(appVersion)}," +
                        "\"sdk_aar_sha256\":${jsonString(sdkAarSha256)}," +
                        "\"max_duration_ms\":$MAX_CAPTURE_DURATION_MS," +
                        "\"max_video_bytes\":$MAX_CAPTURE_BYTES}\n"
                    ).toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()
        }
    }

    private fun pruneOldArchives() {
        captureDirectory.listFiles { file -> file.extension == "zip" }
            ?.sortedByDescending(File::lastModified)
            ?.drop(MAX_RETAINED_ARCHIVES - 1)
            ?.forEach { archive ->
                val base = archive.nameWithoutExtension
                archive.delete()
                File(captureDirectory, "$base.h264").delete()
                File(captureDirectory, "$base.frames.jsonl").delete()
                File(captureDirectory, "$base.telemetry.jsonl").delete()
            }
        captureDirectory.listFiles { file -> file.name.endsWith(".part") }
            ?.forEach(File::delete)
    }

    private fun finalizePart(part: File, destination: File) {
        if (part.renameTo(destination)) return
        part.inputStream().use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        check(part.delete()) { "Could not remove temporary capture file ${part.name}" }
    }

    private fun jsonString(value: String): String = "\"" + value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n") + "\""

    private fun captureId(): String = SimpleDateFormat("yyyyMMdd-HHmmss-SSS-'xstar-bench'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date())

    private companion object {
        const val CAPTURE_DIRECTORY = "captures"
        const val MAX_CAPTURE_DURATION_MS = 30_000L
        const val MAX_CAPTURE_BYTES = 64L * 1024L * 1024L
        const val MAX_TELEMETRY_BYTES = 2L * 1024L * 1024L
        const val MAX_RETAINED_ARCHIVES = 5
        val CAPTURE_README = """
            X-Star Revival passive H.264 bench capture

            The .h264 file contains untouched SDK H.264 callback payloads beginning with bounded,
            standard Annex-B SPS/PPS setup when observed, followed by an SDK-marked keyframe.
            The JSONL index preserves byte offsets, lengths, keyframe flags, local elapsed time,
            and the original SDK timestamp as an opaque value with source-defined units.
            The telemetry JSONL contains normalized state with coordinates, opaque controller
            inputs, identifiers, app keys, warning messages, and diagnostic notes excluded.

            This archive can contain identifiable camera imagery. Review it before sharing.
            It contains no app key, aircraft commands, serial number, or normalized GPS location.
        """.trimIndent() + "\n"
    }
}
