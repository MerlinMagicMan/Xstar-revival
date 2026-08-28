package io.xstarrevival.autelsdk

import android.content.Context
import android.media.MediaCodec
import com.autel.common.CallbackWithOneParam
import com.autel.common.CallbackWithTwoParams
import com.autel.common.author.AuthorityState
import com.autel.common.battery.BatteryState
import com.autel.common.battery.xstar.XStarBatteryInfo
import com.autel.common.camera.CameraProduct
import com.autel.common.camera.base.MediaMode
import com.autel.common.camera.base.MediaStatus
import com.autel.common.camera.r12.R12CameraInfo
import com.autel.common.dsp.DspVersionInfo
import com.autel.common.dsp.RFData
import com.autel.common.error.AutelError
import com.autel.common.flycontroller.ARMWarning
import com.autel.common.flycontroller.FlyControllerInfo
import com.autel.common.flycontroller.FlyControllerVersionInfo
import com.autel.common.flycontroller.MagnetometerState
import com.autel.common.gimbal.GimbalState
import com.autel.common.gimbal.GimbalVersionInfo
import com.autel.common.product.AutelProductType
import com.autel.common.remotecontroller.RemoteControllerConnectState
import com.autel.common.remotecontroller.RemoteControllerInfo
import com.autel.common.remotecontroller.RemoteControllerVersionInfo
import com.autel.sdk.Autel
import com.autel.sdk.ProductConnectListener
import com.autel.sdk.battery.XStarBattery
import com.autel.sdk.camera.AutelBaseCamera
import com.autel.sdk.camera.AutelCameraManager
import com.autel.sdk.camera.AutelR12
import com.autel.sdk.dsp.XStarDsp
import com.autel.sdk.flycontroller.XStarFlyController
import com.autel.sdk.gimbal.XStarGimbal
import com.autel.sdk.product.BaseProduct
import com.autel.sdk.product.XStarAircraft
import com.autel.sdk.remotecontroller.AutelRemoteController
import com.autel.sdk.video.AutelCodec
import com.autel.sdk.video.AutelCodecListener
import io.xstarrevival.core.adapter.AutelAngle
import io.xstarrevival.core.adapter.AutelAngleUnit
import io.xstarrevival.core.adapter.AutelAttitude
import io.xstarrevival.core.adapter.AutelDistance
import io.xstarrevival.core.adapter.AutelDistanceUnit
import io.xstarrevival.core.adapter.AutelSdkBridge
import io.xstarrevival.core.adapter.AutelSdkComponent
import io.xstarrevival.core.adapter.AutelSdkObservation
import io.xstarrevival.core.adapter.AutelWarningSeverity
import io.xstarrevival.core.video.H264VideoFrame
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Receive-only binding for X-Star/X-Star Premium in Autel's official Android AAR.
 *
 * This file intentionally references no flight-control write, remote-stick,
 * mission, gimbal-motion, camera-actuation, pairing, calibration, destructive,
 * or configuration-write method.
 */
class OfficialAutelSdkBridge(
    context: Context,
    private val appKey: String
) : AutelSdkBridge {
    private val applicationContext = context.applicationContext
    private val initialized = AtomicBoolean(false)
    private val mutableObservations = MutableSharedFlow<AutelSdkObservation>(
        extraBufferCapacity = OBSERVATION_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val mutableVideoFrames = MutableSharedFlow<H264VideoFrame>(
        extraBufferCapacity = VIDEO_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override val observations: Flow<AutelSdkObservation> = mutableObservations.asSharedFlow()
    override val videoFrames: Flow<H264VideoFrame> = mutableVideoFrames.asSharedFlow()
    override val description: String = "Autel Mobile SDK official AAR"

    private var product: XStarAircraft? = null
    private var battery: XStarBattery? = null
    private var flyController: XStarFlyController? = null
    private var remoteController: AutelRemoteController? = null
    private var gimbal: XStarGimbal? = null
    private var dsp: XStarDsp? = null
    private var cameraManager: AutelCameraManager? = null
    private var camera: AutelBaseCamera? = null
    private var r12: AutelR12? = null
    private var codec: AutelCodec? = null

    override suspend fun initialize() {
        if (initialized.get()) return
        require(appKey.isNotBlank()) { "AUTEL_APP_KEY is required for the official SDK binding" }

        suspendCancellableCoroutine { continuation ->
            Autel.init(
                applicationContext,
                appKey,
                object : CallbackWithOneParam<AuthorityState> {
                    override fun onSuccess(state: AuthorityState) {
                        initialized.set(true)
                        emit(AutelSdkObservation.Diagnostic("Autel SDK authorization state: ${state.name}"))
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onFailure(error: AutelError) {
                        initialized.set(false)
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("Autel SDK authorization failed: ${safeError(error)}")
                            )
                        }
                    }
                }
            )
        }
    }

    override suspend fun connect() {
        check(initialized.get()) { "Autel SDK must initialize before product discovery" }
        Autel.setProductConnectListener(productConnectListener)
        emit(AutelSdkObservation.Diagnostic("Waiting for X-Star product callback"))
    }

    override suspend fun refreshReadOnlyState() {
        requestReadOnlySnapshots()
    }

    override suspend fun disconnect() {
        clearModuleListeners()
        Autel.setProductConnectListener(null)
        clearReferences()
        if (initialized.getAndSet(false)) Autel.destroy()
        emit(AutelSdkObservation.ProductDisconnected)
    }

    private val productConnectListener = object : ProductConnectListener {
        override fun productConnected(baseProduct: BaseProduct) {
            bindProduct(baseProduct)
        }

        override fun productDisconnected() {
            clearModuleListeners()
            clearReferences()
            emit(AutelSdkObservation.ProductDisconnected)
        }
    }

    private fun bindProduct(baseProduct: BaseProduct) {
        val type = baseProduct.getType()
        if (type != AutelProductType.PREMIUM && type != AutelProductType.X_STAR) {
            emit(AutelSdkObservation.Diagnostic("Unsupported Autel product callback: ${type.name}"))
            return
        }
        val aircraft = baseProduct as? XStarAircraft
        if (aircraft == null) {
            emit(AutelSdkObservation.Diagnostic("X-Star product did not implement XStarAircraft"))
            return
        }

        clearModuleListeners()
        product = aircraft
        battery = aircraft.getBattery()
        flyController = aircraft.getFlyController()
        remoteController = aircraft.getRemoteController()
        gimbal = aircraft.getGimbal()
        dsp = aircraft.getDsp()
        cameraManager = aircraft.getCameraManager()
        codec = aircraft.getCodec()

        val components = buildSet {
            if (battery != null) add(AutelSdkComponent.BATTERY)
            if (flyController != null) add(AutelSdkComponent.FLIGHT_CONTROLLER)
            if (remoteController != null) add(AutelSdkComponent.REMOTE_CONTROLLER)
            if (gimbal != null) add(AutelSdkComponent.GIMBAL)
            if (dsp != null) add(AutelSdkComponent.DSP)
            if (cameraManager != null) add(AutelSdkComponent.CAMERA)
            if (codec != null) add(AutelSdkComponent.CODEC)
            if (aircraft.getAlbum() != null) add(AutelSdkComponent.ALBUM)
            if (aircraft.getMissionManager() != null) add(AutelSdkComponent.MISSION_MANAGER)
        }
        emit(
            AutelSdkObservation.ProductConnected(
                productName = type.getDescription()?.takeIf { it.isNotBlank() } ?: type.name,
                availableComponents = components
            )
        )

        subscribeBattery()
        subscribeFlightController()
        subscribeRemoteController()
        subscribeGimbal()
        subscribeCameraManager()
        subscribeCodec()
        requestReadOnlySnapshots()
    }

    private fun subscribeBattery() {
        battery?.setBatteryStateListener(callback("battery-state") { state -> emitBatteryState(state) })
    }

    private fun emitBatteryState(state: BatteryState) {
        val xstarInfo = state as? XStarBatteryInfo
        emit(
            AutelSdkObservation.Battery(
                percent = state.getRemainingPercent(),
                packVoltageMv = state.getVoltage().toDouble(),
                currentMa = state.getCurrent().toDouble(),
                temperatureC = state.getTemperature().toDouble(),
                designCapacityMah = state.getDesignedCapacity().roundToInt(),
                fullCapacityMah = xstarInfo?.getFullChargeCapacity(),
                remainingCapacityMah = state.getCapacity().roundToInt(),
                cellVoltagesMv = state.getVoltageCells().map(Int::toInt),
                dischargeCount = xstarInfo?.getNumberOfDischarges()?.roundToInt(),
                firmwareVersion = xstarInfo?.getVersion()
            )
        )
    }

    private fun subscribeFlightController() {
        flyController?.setFlyControllerInfoListener(
            callback("flight-state") { info -> emitFlightInfo(info) }
        )
        flyController?.setUltraSonicHeightInfoListener(
            callback("ultrasonic-height") { value ->
                emit(
                    AutelSdkObservation.Flight(
                        ultrasonicHeight = AutelDistance(value.toDouble(), AutelDistanceUnit.UNKNOWN)
                    )
                )
            }
        )
        flyController?.setWarningListener(
            object : CallbackWithTwoParams<ARMWarning, MagnetometerState> {
                override fun onSuccess(arm: ARMWarning, magnetometer: MagnetometerState) {
                    emit(
                        AutelSdkObservation.Warning(
                            id = "flight-controller",
                            severity = if (arm.name == "NO_WARN") AutelWarningSeverity.INFO else AutelWarningSeverity.WARNING,
                            message = "ARM=${arm.name}; magnetometer=${magnetometer.name}"
                        )
                    )
                }

                override fun onFailure(error: AutelError) = emitError("flight-warning", error)
            }
        )
    }

    private fun emitFlightInfo(info: FlyControllerInfo) {
        val gps = info.getGPSInfo()
        val coordinate = gps?.getCoordinate()
        val home = info.getFlyHome()
        val homeCoordinate = home?.takeIf { it.isValid() }?.getAutelCoord3D()
        val speed = info.getAltitudeAndSpeedInfo()
        val attitude = info.getAttitudeInfo()
        val status = info.getFlyControllerStatus()
        emit(
            AutelSdkObservation.Flight(
                latitudeDeg = coordinate?.getLatitude(),
                longitudeDeg = coordinate?.getLongitude(),
                homeLatitudeDeg = homeCoordinate?.getLatitude(),
                homeLongitudeDeg = homeCoordinate?.getLongitude(),
                satellites = gps?.getGpsCount(),
                gpsFix = status?.let { if (it.isGpsValid()) "VALID" else "INVALID" },
                altitudeM = speed?.getAltitude()?.toDouble(),
                groundSpeedMps = speed?.getSpeed()?.toDouble(),
                verticalSpeedMps = null,
                attitude = attitude?.let {
                    AutelAttitude(
                        roll = it.getRoll(),
                        pitch = it.getPitch(),
                        yaw = it.getYaw(),
                        unit = AutelAngleUnit.DEGREES
                    )
                },
                armed = null,
                flightMode = status?.getFlyMode()?.name
            )
        )
    }

    private fun subscribeRemoteController() {
        remoteController?.setConnectStateListener(
            callback("remote-connect") { state ->
                emit(
                    AutelSdkObservation.Remote(
                        connected = state == RemoteControllerConnectState.connect ||
                            state == RemoteControllerConnectState.reconnect
                    )
                )
            }
        )
        remoteController?.setInfoDataListener(
            callback("remote-state") { info -> emitRemoteInfo(info) }
        )
        remoteController?.setControlMenuListener(
            callback("remote-control-menu") { values ->
                emit(AutelSdkObservation.Remote(opaqueControlMenu = values.toList()))
            }
        )
    }

    private fun emitRemoteInfo(info: RemoteControllerInfo) {
        emit(
            AutelSdkObservation.Remote(
                signalPercent = info.getControllerSignalPercentage(),
                batteryPercent = info.getBatteryCapacityPercentage(),
                imageSignalPercent = info.getDSPPercentage()
            )
        )
    }

    private fun subscribeGimbal() {
        gimbal?.setAngleListener(
            callback("gimbal-angle") { value ->
                emit(AutelSdkObservation.Gimbal(pitch = AutelAngle(value.toDouble(), AutelAngleUnit.DEGREES)))
            }
        )
        gimbal?.setGimbalStateListener(
            callback("gimbal-state") { state -> emit(AutelSdkObservation.Gimbal(status = state.name)) }
        )
    }

    private fun subscribeCameraManager() {
        cameraManager?.setCameraChangeListener(
            object : CallbackWithTwoParams<CameraProduct, AutelBaseCamera> {
                override fun onSuccess(product: CameraProduct, connectedCamera: AutelBaseCamera) {
                    bindCamera(connectedCamera)
                }

                override fun onFailure(error: AutelError) = emitError("camera-connect", error)
            }
        )
    }

    private fun bindCamera(connectedCamera: AutelBaseCamera) {
        clearCameraListeners()
        camera = connectedCamera
        r12 = connectedCamera as? AutelR12
        emit(AutelSdkObservation.Camera(connected = true))

        connectedCamera.setMediaModeListener(
            callback("camera-media-mode") { mode -> emit(AutelSdkObservation.Camera(mode = mode.name)) }
        )
        connectedCamera.setMediaStateListener(
            object : CallbackWithTwoParams<MediaStatus, String> {
                override fun onSuccess(status: MediaStatus, detail: String) {
                    val recording = when (status) {
                        MediaStatus.RECORD_START -> true
                        MediaStatus.RECORD_STOP,
                        MediaStatus.RECORD_FAILED_WRITE_ERROR,
                        MediaStatus.RECORD_FAILED_SDCARD_REMOVED -> false
                        else -> null
                    }
                    emit(AutelSdkObservation.Camera(recording = recording))
                }

                override fun onFailure(error: AutelError) = emitError("camera-media-state", error)
            }
        )
        r12?.setInfoListener(callback("r12-info") { info -> emitR12Info(info) })
        requestCameraSnapshot()
    }

    private fun emitR12Info(info: R12CameraInfo) {
        emit(
            AutelSdkObservation.Camera(
                iso = info.getISO()?.toString(),
                shutter = info.getShutterSpeed()?.toString()
            )
        )
    }

    private fun subscribeCodec() {
        codec?.setCodecListener(
            object : AutelCodecListener {
                override fun onFrameStream(buffer: ByteArray, isIFrame: Boolean, size: Int, pts: Long) {
                    val validSize = size.coerceIn(0, buffer.size)
                    if (validSize == 0) return
                    mutableVideoFrames.tryEmit(
                        H264VideoFrame(
                            bytes = buffer.copyOf(validSize),
                            isKeyFrame = isIFrame,
                            validSize = validSize,
                            presentationTimestamp = pts
                        )
                    )
                }

                override fun onDecodedFrameStream(
                    buffer: ByteBuffer,
                    info: MediaCodec.BufferInfo,
                    isIFrame: Boolean,
                    width: Int,
                    height: Int,
                    formatType: Int
                ) = Unit

                override fun onCanceled() {
                    emit(AutelSdkObservation.Diagnostic("Autel H.264 callback canceled"))
                }

                override fun onFailure(error: AutelError) = emitError("h264-stream", error)
            },
            null
        )
    }

    private fun requestReadOnlySnapshots() {
        requestBatterySnapshot()
        requestDspSnapshot()
        requestCameraSnapshot()
        requestComponentVersions()
    }

    private fun requestBatterySnapshot() {
        battery?.getVoltageCells(callback("battery-cells") { values ->
            emit(AutelSdkObservation.Battery(cellVoltagesMv = values.map(Int::toInt)))
        })
        battery?.getVoltage(callback("battery-voltage") { value ->
            emit(AutelSdkObservation.Battery(packVoltageMv = value.toDouble()))
        })
        battery?.getCurrent(callback("battery-current") { value ->
            emit(AutelSdkObservation.Battery(currentMa = value.toDouble()))
        })
        battery?.getTemperature(callback("battery-temperature") { value ->
            emit(AutelSdkObservation.Battery(temperatureC = value.toDouble()))
        })
        battery?.getDesignCapacity(callback("battery-design-capacity") { value ->
            emit(AutelSdkObservation.Battery(designCapacityMah = value.roundToInt()))
        })
        battery?.getCapacity(callback("battery-capacity") { value ->
            emit(AutelSdkObservation.Battery(remainingCapacityMah = value.roundToInt()))
        })
        battery?.getRemainingPercent(callback("battery-percent") { value ->
            emit(AutelSdkObservation.Battery(percent = value))
        })
        battery?.getFullChargeCapacity(callback("battery-full-capacity") { value ->
            emit(AutelSdkObservation.Battery(fullCapacityMah = value))
        })
        battery?.getDischargeCount(callback("battery-discharge-count") { value ->
            emit(AutelSdkObservation.Battery(dischargeCount = value))
        })
        battery?.getVersion(callback("battery-version") { value ->
            emit(AutelSdkObservation.Battery(firmwareVersion = value))
            emit(AutelSdkObservation.ComponentVersions(mapOf("battery" to value)))
        })
    }

    private fun requestDspSnapshot() {
        dsp?.let { imageLink ->
            emit(AutelSdkObservation.ImageLink(usbEnabled = imageLink.isUSBEnable()))
            imageLink.getCurrentRFData(
                RF_RETRY_COUNT,
                callback("dsp-current-rf") { data -> emitRfData(data) }
            )
        }
    }

    private fun emitRfData(data: RFData) {
        emit(
            AutelSdkObservation.ImageLink(
                rfFrequencyHz = data.hz.toDouble(),
                rfSignalValue = data.value
            )
        )
    }

    private fun requestCameraSnapshot() {
        camera?.getMediaMode(callback("camera-media-mode-query") { mode: MediaMode ->
            emit(AutelSdkObservation.Camera(mode = mode.name))
        })
        r12?.getExposureMode(callback("r12-exposure-mode") { value ->
            emit(AutelSdkObservation.Camera(exposureMode = value.toString()))
        })
        r12?.getISO(callback("r12-iso") { value ->
            emit(AutelSdkObservation.Camera(iso = value.toString()))
        })
        r12?.getShutter(callback("r12-shutter") { value ->
            emit(AutelSdkObservation.Camera(shutter = value.toString()))
        })
    }

    private fun requestComponentVersions() {
        flyController?.getVersionInfo(callback("flight-controller-version") { emitFlyVersions(it) })
        dsp?.getVersionInfo(callback("dsp-version") { emitDspVersions(it) })
        gimbal?.getVersionInfo(callback("gimbal-version") { emitGimbalVersions(it) })
        remoteController?.getVersionInfo(callback("remote-version") { emitRemoteVersions(it) })
        camera?.getVersion(callback("camera-version") { value ->
            emit(AutelSdkObservation.ComponentVersions(mapOf("camera" to value)))
        })
    }

    private fun emitFlyVersions(info: FlyControllerVersionInfo) {
        emit(
            AutelSdkObservation.ComponentVersions(
                versionMap(
                    "flight-controller" to info.getFlyControllerVersion(),
                    "optical-flow" to info.getOpticalFlowVersion(),
                    "sonar" to info.getSonarVersion()
                )
            )
        )
    }

    private fun emitDspVersions(info: DspVersionInfo) {
        emit(
            AutelSdkObservation.ComponentVersions(
                versionMap(
                    "dsp" to info.getDSPVersion(),
                    "transfer-board" to info.getTransferBoardVersion()
                )
            )
        )
    }

    private fun emitGimbalVersions(info: GimbalVersionInfo) {
        emit(
            AutelSdkObservation.ComponentVersions(
                versionMap(
                    "gimbal" to info.getGimbalVersion(),
                    "gimbal-bootloader" to info.getBootloaderVersion(),
                    "gimbal-pitch-esc" to info.getPitchESCVersion(),
                    "gimbal-roll-esc" to info.getRollESCVersion(),
                    "gimbal-yaw-esc" to info.getYawESCVersion()
                )
            )
        )
    }

    private fun emitRemoteVersions(info: RemoteControllerVersionInfo) {
        emit(
            AutelSdkObservation.ComponentVersions(
                versionMap(
                    "remote-controller" to info.getRemoteControlVersion(),
                    "repeater" to info.getRepeaterVersion(),
                    "rf-rx" to info.getRFRXVersion(),
                    "rf-tx" to info.getRFTXVersion()
                )
            )
        )
    }

    private fun clearModuleListeners() {
        runCatching { battery?.setBatteryStateListener(null) }
        runCatching { flyController?.setFlyControllerInfoListener(null) }
        runCatching { flyController?.setUltraSonicHeightInfoListener(null) }
        runCatching { flyController?.setWarningListener(null) }
        runCatching { remoteController?.setConnectStateListener(null) }
        runCatching { remoteController?.setInfoDataListener(null) }
        runCatching { remoteController?.setControlMenuListener(null) }
        runCatching { gimbal?.setAngleListener(null) }
        runCatching { gimbal?.setGimbalStateListener(null) }
        runCatching { cameraManager?.setCameraChangeListener(null) }
        clearCameraListeners()
        // The official AAR marks both setCodecListener parameters non-null and
        // exposes cancel() as the supported way to stop codec delivery.
        runCatching { codec?.cancel() }
    }

    private fun clearCameraListeners() {
        runCatching { camera?.setMediaModeListener(null) }
        runCatching { camera?.setMediaStateListener(null) }
        runCatching { camera?.setSDCardStateListener(null) }
        runCatching { r12?.setInfoListener(null) }
        runCatching { r12?.setHistogramListener(null) }
        camera = null
        r12 = null
    }

    private fun clearReferences() {
        product = null
        battery = null
        flyController = null
        remoteController = null
        gimbal = null
        dsp = null
        cameraManager = null
        camera = null
        r12 = null
        codec = null
    }

    private fun <T> callback(operation: String, success: (T) -> Unit): CallbackWithOneParam<T> =
        object : CallbackWithOneParam<T> {
            override fun onSuccess(value: T) = success(value)
            override fun onFailure(error: AutelError) = emitError(operation, error)
        }

    private fun emitError(operation: String, error: AutelError) {
        emit(AutelSdkObservation.Diagnostic("$operation failed: ${safeError(error)}"))
    }

    private fun emit(observation: AutelSdkObservation) {
        mutableObservations.tryEmit(observation)
    }

    private fun versionMap(vararg values: Pair<String, String?>): Map<String, String> =
        values.mapNotNull { (name, value) -> value?.takeIf { it.isNotBlank() }?.let { name to it } }.toMap()

    private fun safeError(error: AutelError?): String = error?.getDescription()?.takeIf { it.isNotBlank() } ?: "unknown"

    private companion object {
        const val OBSERVATION_BUFFER = 128
        const val VIDEO_BUFFER = 64
        const val RF_RETRY_COUNT = 3
    }
}
