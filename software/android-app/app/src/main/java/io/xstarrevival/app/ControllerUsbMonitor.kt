package io.xstarrevival.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class ControllerUsbIdentity(
    val manufacturer: String,
    val model: String,
    val version: String,
    val vendorId: Int? = null,
    val productId: Int? = null
)

internal enum class ControllerUsbStatus {
    DISCONNECTED,
    XSTAR,
    XSTAR_LEGACY,
    OTHER_ACCESSORY
}

internal enum class ControllerUsbTransport {
    ACCESSORY,
    DIRECT_CDC
}

internal data class ControllerUsbUiState(
    val status: ControllerUsbStatus = ControllerUsbStatus.DISCONNECTED,
    val identity: ControllerUsbIdentity? = null,
    val transport: ControllerUsbTransport? = null
) {
    val controllerDetected: Boolean
        get() = status == ControllerUsbStatus.XSTAR || status == ControllerUsbStatus.XSTAR_LEGACY
}

internal object ControllerUsbIdentityClassifier {
    const val AUTEL_REMOTE_VENDOR_ID = 0x6175
    const val AUTEL_REMOTE_PRODUCT_ID = 0x5243

    private val standardIdentities = setOf(
        ControllerUsbIdentity("com.autel", "Starlink", "1.0"),
        ControllerUsbIdentity("com.autel", "Autel Explorer", "1.0")
    )
    private val legacyXStarIdentity = ControllerUsbIdentity("ammlab.org", "HelloADK", "1.0")

    fun classify(
        accessories: List<ControllerUsbIdentity>,
        devices: List<ControllerUsbIdentity> = emptyList()
    ): ControllerUsbUiState {
        val standard = accessories.firstOrNull { it in standardIdentities }
        if (standard != null) {
            return ControllerUsbUiState(
                ControllerUsbStatus.XSTAR,
                standard,
                ControllerUsbTransport.ACCESSORY
            )
        }

        val legacy = accessories.firstOrNull { it == legacyXStarIdentity }
        if (legacy != null) {
            return ControllerUsbUiState(
                ControllerUsbStatus.XSTAR_LEGACY,
                legacy,
                ControllerUsbTransport.ACCESSORY
            )
        }

        val direct = devices.firstOrNull { isDirectXStarDevice(it) }
        if (direct != null) {
            return ControllerUsbUiState(
                ControllerUsbStatus.XSTAR,
                direct,
                ControllerUsbTransport.DIRECT_CDC
            )
        }

        val other = accessories.firstOrNull() ?: devices.firstOrNull()
        return if (other == null) {
            ControllerUsbUiState()
        } else {
            ControllerUsbUiState(ControllerUsbStatus.OTHER_ACCESSORY, other)
        }
    }

    fun isDirectXStarDevice(identity: ControllerUsbIdentity): Boolean =
        identity.vendorId == AUTEL_REMOTE_VENDOR_ID &&
            identity.productId == AUTEL_REMOTE_PRODUCT_ID
}

/**
 * Observes Android's read-only USB accessory inventory. It never opens the accessory or writes
 * to it; the official SDK remains the sole owner of any future receive-only data transport.
 */
internal class ControllerUsbMonitor(context: Context) : AutoCloseable {
    private val applicationContext = context.applicationContext
    private val usbManager = applicationContext.getSystemService(UsbManager::class.java)
    private val mutableState = MutableStateFlow(ControllerUsbUiState())
    val state: StateFlow<ControllerUsbUiState> = mutableState.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_ACCESSORY_ATTACHED,
                UsbManager.ACTION_USB_ACCESSORY_DETACHED,
                UsbManager.ACTION_USB_DEVICE_ATTACHED,
                UsbManager.ACTION_USB_DEVICE_DETACHED,
                AUTEL_USB_ACCESSORY_ATTACHED -> refresh()
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
            addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            // Autel's UsbStartActivity translates Android's attach intent to this broadcast.
            addAction(AUTEL_USB_ACCESSORY_ATTACHED)
        }
        ContextCompat.registerReceiver(
            applicationContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        refresh()
    }

    fun refresh() {
        val accessories = usbManager.accessoryList.orEmpty().map { it.toControllerUsbIdentity() }
        val devices = usbManager.deviceList.values.map { it.toControllerUsbIdentity() }
        mutableState.value = ControllerUsbIdentityClassifier.classify(accessories, devices)
    }

    override fun close() {
        runCatching { applicationContext.unregisterReceiver(receiver) }
    }

    private companion object {
        const val AUTEL_USB_ACCESSORY_ATTACHED = "com.autel.sdk.action.USB_ACCESSORY_ATTACHED"
    }
}

internal fun UsbAccessory.toControllerUsbIdentity() = ControllerUsbIdentity(
    manufacturer = manufacturer.orEmpty(),
    model = model.orEmpty(),
    version = version.orEmpty()
)

internal fun UsbDevice.toControllerUsbIdentity() = ControllerUsbIdentity(
    manufacturer = runCatching { manufacturerName.orEmpty().trim() }.getOrDefault(""),
    model = runCatching { productName.orEmpty().trim() }.getOrDefault(""),
    version = runCatching { version.orEmpty().trim() }.getOrDefault(""),
    vendorId = vendorId,
    productId = productId
)
