package io.xstarrevival.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import io.xstarrevival.core.sim.RcSimulatorAccessoryDecoder
import io.xstarrevival.core.sim.RcSimulatorButtonFrame
import io.xstarrevival.core.sim.RcSimulatorStickFrame
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class ControllerProbeStatus { IDLE, READING, COMPLETE, ERROR }

internal enum class ControllerProbeStopReason { USER, DURATION_LIMIT, SIZE_LIMIT, END_OF_STREAM, ERROR }

internal enum class ControllerInputLinkStatus {
    DISCONNECTED,
    USB_READY,
    LISTENING,
    STREAMING,
    INPUT_STREAM_UNAVAILABLE,
    ERROR
}

internal data class ControllerProbeUiState(
    val status: ControllerProbeStatus = ControllerProbeStatus.IDLE,
    val bytesRead: Long = 0,
    val chunksRead: Long = 0,
    val elapsedMs: Long = 0,
    val lastChunkHex: String? = null,
    val stopReason: ControllerProbeStopReason? = null,
    val capturePath: String? = null,
    val stickFramesRead: Long = 0,
    val lastStickAxes: List<Double>? = null,
    val buttonFramesRead: Long = 0,
    val lastButtonEventId: Int? = null,
    val lastButtonStateValue: Int? = null,
    val lastButtonName: String? = null,
    val error: String? = null
) {
    val active: Boolean
        get() = status == ControllerProbeStatus.READING
}

internal fun controllerInputLinkStatus(
    controllerUsb: ControllerUsbUiState,
    probe: ControllerProbeUiState
): ControllerInputLinkStatus = when {
    !controllerUsb.controllerDetected -> ControllerInputLinkStatus.DISCONNECTED
    probe.status == ControllerProbeStatus.ERROR -> ControllerInputLinkStatus.ERROR
    probe.bytesRead > 0 -> ControllerInputLinkStatus.STREAMING
    probe.status == ControllerProbeStatus.READING -> ControllerInputLinkStatus.LISTENING
    probe.status == ControllerProbeStatus.COMPLETE -> ControllerInputLinkStatus.INPUT_STREAM_UNAVAILABLE
    else -> ControllerInputLinkStatus.USB_READY
}

internal object ControllerProbeBounds {
    fun bytesToKeep(bytesRead: Long, incomingBytes: Int, maxBytes: Long): Int =
        (maxBytes - bytesRead).coerceIn(0, incomingBytes.toLong()).toInt()
}

/**
 * Bounded receive-only probe for the controller's Android Open Accessory stream.
 *
 * This class deliberately constructs only a [FileInputStream] for the USB descriptor. It contains
 * no USB output stream and no accessory write operation. The file output stores received bytes in
 * the app's private cache so confirmed controls can later become deterministic replay fixtures.
 */
internal class ControllerUsbInputProbe(
    context: Context,
    private val scope: CoroutineScope,
    private val elapsedRealtimeMs: () -> Long = SystemClock::elapsedRealtime
) : AutoCloseable {
    private val applicationContext = context.applicationContext
    private val usbManager = applicationContext.getSystemService(UsbManager::class.java)
    private val captureDirectory = File(applicationContext.cacheDir, CAPTURE_DIRECTORY)
    private val mutableState = MutableStateFlow(ControllerProbeUiState())
    val state: StateFlow<ControllerProbeUiState> = mutableState.asStateFlow()

    private val requestedStop = AtomicReference<ControllerProbeStopReason?>(null)
    private val descriptorLock = Any()
    private var descriptor: ParcelFileDescriptor? = null
    private var deviceConnection: UsbDeviceConnection? = null
    private var captureJob: Job? = null
    private var timeoutJob: Job? = null

    fun start() {
        if (captureJob?.isActive == true) return
        if (!captureDirectory.exists() && !captureDirectory.mkdirs()) {
            fail("Could not create the private controller capture directory")
            return
        }
        val accessory = usbManager.accessoryList.orEmpty().firstOrNull { candidate ->
            ControllerUsbIdentityClassifier.classify(
                listOf(candidate.toControllerUsbIdentity())
            ).controllerDetected
        }
        if (accessory != null) {
            startAccessory(accessory)
            return
        }

        val device = usbManager.deviceList.values.firstOrNull { candidate ->
            ControllerUsbIdentityClassifier.isDirectXStarDevice(candidate.toControllerUsbIdentity())
        }
        if (device != null) {
            startDirectDevice(device)
            return
        }

        fail("No recognized X-Star controller USB connection is attached")
    }

    private fun startAccessory(accessory: android.hardware.usb.UsbAccessory) {
        if (!usbManager.hasPermission(accessory)) {
            fail("Android has not granted accessory permission")
            return
        }
        val opened = usbManager.openAccessory(accessory)
        if (opened == null) {
            fail("Android could not open the controller accessory")
            return
        }

        requestedStop.set(null)
        synchronized(descriptorLock) { descriptor = opened }
        mutableState.value = ControllerProbeUiState(status = ControllerProbeStatus.READING)
        captureJob = scope.launch(Dispatchers.IO) {
            try {
                FileInputStream(opened.fileDescriptor).use { usbInput ->
                    recordIncoming { buffer -> usbInput.read(buffer) }
                }
            } finally {
                closeDescriptor(opened)
            }
        }
        startTimeout()
    }

    private fun startDirectDevice(device: UsbDevice) {
        if (!usbManager.hasPermission(device)) {
            val permissionIntent = PendingIntent.getBroadcast(
                applicationContext,
                0,
                Intent(ACTION_USB_PERMISSION).setPackage(applicationContext.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            runCatching { usbManager.requestPermission(device, permissionIntent) }
                .onFailure { fail("Android could not request controller USB permission") }
            if (mutableState.value.status != ControllerProbeStatus.ERROR) {
                fail("Allow controller access, then tap the check button again")
            }
            return
        }

        val receiveTarget = findReceiveTarget(device)
        if (receiveTarget == null) {
            fail("The controller has no receive-only USB endpoint")
            return
        }
        val connection = usbManager.openDevice(device)
        if (connection == null) {
            fail("Android could not open the controller USB device")
            return
        }
        if (!connection.claimInterface(receiveTarget.usbInterface, true)) {
            connection.close()
            fail("Android could not claim the controller receive interface")
            return
        }

        requestedStop.set(null)
        synchronized(descriptorLock) { deviceConnection = connection }
        mutableState.value = ControllerProbeUiState(status = ControllerProbeStatus.READING)
        captureJob = scope.launch(Dispatchers.IO) {
            try {
                recordIncoming { buffer ->
                    val count = connection.bulkTransfer(
                        receiveTarget.endpoint,
                        buffer,
                        buffer.size,
                        DIRECT_USB_READ_TIMEOUT_MS
                    )
                    if (count < 0 && requestedStop.get() == null) 0 else count
                }
            } finally {
                closeDeviceConnection(connection, receiveTarget.usbInterface)
            }
        }
        startTimeout()
    }

    private fun startTimeout() {
        timeoutJob = scope.launch {
            delay(MAX_CAPTURE_DURATION_MS)
            stop(ControllerProbeStopReason.DURATION_LIMIT)
        }
    }

    fun stop(reason: ControllerProbeStopReason = ControllerProbeStopReason.USER) {
        if (captureJob?.isActive != true) return
        requestedStop.compareAndSet(null, reason)
        timeoutJob?.cancel()
        synchronized(descriptorLock) {
            runCatching { descriptor?.close() }
            descriptor = null
            runCatching { deviceConnection?.close() }
            deviceConnection = null
        }
    }

    private fun recordIncoming(readChunk: (ByteArray) -> Int) {
        pruneOldCaptures()
        val captureFile = File(captureDirectory, "${captureId()}.bin")
        val startedAt = elapsedRealtimeMs()
        var bytesRead = 0L
        var chunksRead = 0L
        var lastHex: String? = null
        var stickFramesRead = 0L
        var lastStickAxes: List<Double>? = null
        var buttonFramesRead = 0L
        var lastButtonEventId: Int? = null
        var lastButtonStateValue: Int? = null
        var lastButtonName: String? = null
        var failure: String? = null
        var reason: ControllerProbeStopReason? = null
        var lastPublishedAt = startedAt
        val frameDecoder = RcSimulatorAccessoryDecoder()

        try {
            ControllerProbeCaptureSink(captureFile, MAX_CAPTURE_BYTES).use { captureSink ->
                val buffer = ByteArray(READ_BUFFER_BYTES)
                while (true) {
                    val requestedReason = requestedStop.get()
                    if (requestedReason != null) {
                        reason = requestedReason
                        break
                    }
                    val count = readChunk(buffer)
                    if (count < 0) {
                        reason = ControllerProbeStopReason.END_OF_STREAM
                        break
                    }
                    if (count == 0) continue
                    val keep = captureSink.append(buffer, count)
                    if (keep > 0) {
                        bytesRead = captureSink.bytesWritten
                        chunksRead++
                        lastHex = buffer.copyOfRange(0, minOf(keep, HEX_PREVIEW_BYTES)).toHex()
                        val accessoryFrames = frameDecoder.appendFrames(buffer, keep)
                        val stickFrames = accessoryFrames.filterIsInstance<RcSimulatorStickFrame>()
                        val buttonFrames = accessoryFrames.filterIsInstance<RcSimulatorButtonFrame>()
                        stickFramesRead += stickFrames.size
                        lastStickAxes = stickFrames.lastOrNull()?.normalizedAxes ?: lastStickAxes
                        buttonFramesRead += buttonFrames.size
                        buttonFrames.lastOrNull()?.let { button ->
                            lastButtonEventId = button.eventId
                            lastButtonStateValue = button.stateValue
                            lastButtonName = button.controlName
                        }
                        val now = elapsedRealtimeMs()
                        if (now - lastPublishedAt >= UI_UPDATE_INTERVAL_MS) {
                            publish(
                                bytesRead,
                                chunksRead,
                                now - startedAt,
                                lastHex,
                                stickFramesRead,
                                lastStickAxes,
                                buttonFramesRead,
                                lastButtonEventId,
                                lastButtonStateValue,
                                lastButtonName
                            )
                            lastPublishedAt = now
                        }
                    }
                    if (keep < count || bytesRead >= MAX_CAPTURE_BYTES) {
                        reason = ControllerProbeStopReason.SIZE_LIMIT
                        break
                    }
                }
            }
        } catch (error: IOException) {
            reason = requestedStop.get()
            if (reason == null) {
                reason = ControllerProbeStopReason.ERROR
                failure = error.message ?: error.javaClass.simpleName
            }
        } catch (error: Exception) {
            reason = ControllerProbeStopReason.ERROR
            failure = error.message ?: error.javaClass.simpleName
        } finally {
            timeoutJob?.cancel()
            timeoutJob = null
            captureJob = null
            val finalReason = requestedStop.get() ?: reason ?: ControllerProbeStopReason.END_OF_STREAM
            val elapsed = (elapsedRealtimeMs() - startedAt).coerceAtLeast(0)
            if (failure != null) captureFile.delete()
            mutableState.value = ControllerProbeUiState(
                status = if (failure == null) ControllerProbeStatus.COMPLETE else ControllerProbeStatus.ERROR,
                bytesRead = bytesRead,
                chunksRead = chunksRead,
                elapsedMs = elapsed,
                lastChunkHex = lastHex,
                stopReason = finalReason,
                capturePath = captureFile.takeIf { failure == null }?.absolutePath,
                stickFramesRead = stickFramesRead,
                lastStickAxes = lastStickAxes,
                buttonFramesRead = buttonFramesRead,
                lastButtonEventId = lastButtonEventId,
                lastButtonStateValue = lastButtonStateValue,
                lastButtonName = lastButtonName,
                error = failure
            )
        }
    }

    private fun publish(
        bytesRead: Long,
        chunksRead: Long,
        elapsedMs: Long,
        lastHex: String?,
        stickFramesRead: Long,
        lastStickAxes: List<Double>?,
        buttonFramesRead: Long,
        lastButtonEventId: Int?,
        lastButtonStateValue: Int?,
        lastButtonName: String?
    ) {
        mutableState.value = ControllerProbeUiState(
            status = ControllerProbeStatus.READING,
            bytesRead = bytesRead,
            chunksRead = chunksRead,
            elapsedMs = elapsedMs.coerceAtLeast(0),
            lastChunkHex = lastHex,
            stickFramesRead = stickFramesRead,
            lastStickAxes = lastStickAxes,
            buttonFramesRead = buttonFramesRead,
            lastButtonEventId = lastButtonEventId,
            lastButtonStateValue = lastButtonStateValue,
            lastButtonName = lastButtonName
        )
    }

    private fun fail(message: String) {
        mutableState.value = ControllerProbeUiState(
            status = ControllerProbeStatus.ERROR,
            stopReason = ControllerProbeStopReason.ERROR,
            error = message
        )
    }

    private fun closeDescriptor(opened: ParcelFileDescriptor) {
        synchronized(descriptorLock) {
            runCatching { opened.close() }
            if (descriptor === opened) descriptor = null
        }
    }

    private fun closeDeviceConnection(connection: UsbDeviceConnection, usbInterface: UsbInterface) {
        synchronized(descriptorLock) {
            runCatching { connection.releaseInterface(usbInterface) }
            runCatching { connection.close() }
            if (deviceConnection === connection) deviceConnection = null
        }
    }

    private fun findReceiveTarget(device: UsbDevice): DirectUsbReceiveTarget? {
        for (interfaceIndex in 0 until device.interfaceCount) {
            val candidate = device.getInterface(interfaceIndex)
            if (candidate.interfaceClass != UsbConstants.USB_CLASS_CDC_DATA) continue
            for (endpointIndex in 0 until candidate.endpointCount) {
                val endpoint = candidate.getEndpoint(endpointIndex)
                if (endpoint.direction == UsbConstants.USB_DIR_IN &&
                    endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK
                ) {
                    return DirectUsbReceiveTarget(candidate, endpoint)
                }
            }
        }
        return null
    }

    private fun pruneOldCaptures() {
        captureDirectory.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending(File::lastModified)
            ?.drop(MAX_RETAINED_CAPTURES - 1)
            ?.forEach(File::delete)
    }

    private fun captureId(): String = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).run {
        timeZone = TimeZone.getTimeZone("UTC")
        "xstar-controller-${format(Date())}"
    }

    override fun close() = stop(ControllerProbeStopReason.USER)

    private fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }

    private companion object {
        const val ACTION_USB_PERMISSION = "io.xstarrevival.app.USB_CONTROLLER_PERMISSION"
        const val CAPTURE_DIRECTORY = "captures/controller-probes"
        const val MAX_CAPTURE_DURATION_MS = 20_000L
        const val MAX_CAPTURE_BYTES = 1024L * 1024L
        const val MAX_RETAINED_CAPTURES = 8
        const val READ_BUFFER_BYTES = 16 * 1024
        const val HEX_PREVIEW_BYTES = 24
        const val UI_UPDATE_INTERVAL_MS = 100L
        const val DIRECT_USB_READ_TIMEOUT_MS = 250
    }
}

private data class DirectUsbReceiveTarget(
    val usbInterface: UsbInterface,
    val endpoint: UsbEndpoint
)
