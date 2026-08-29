package io.xstarrevival.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class ControllerUsbIdentity(
    val manufacturer: String,
    val model: String,
    val version: String
)

internal enum class ControllerUsbStatus {
    DISCONNECTED,
    XSTAR,
    XSTAR_LEGACY,
    OTHER_ACCESSORY
}

internal data class ControllerUsbUiState(
    val status: ControllerUsbStatus = ControllerUsbStatus.DISCONNECTED,
    val identity: ControllerUsbIdentity? = null
) {
    val controllerDetected: Boolean
        get() = status == ControllerUsbStatus.XSTAR || status == ControllerUsbStatus.XSTAR_LEGACY
}

internal object ControllerUsbIdentityClassifier {
    private val standardIdentities = setOf(
        ControllerUsbIdentity("com.autel", "Starlink", "1.0"),
        ControllerUsbIdentity("com.autel", "Autel Explorer", "1.0")
    )
    private val legacyXStarIdentity = ControllerUsbIdentity("ammlab.org", "HelloADK", "1.0")

    fun classify(accessories: List<ControllerUsbIdentity>): ControllerUsbUiState {
        val standard = accessories.firstOrNull { it in standardIdentities }
        if (standard != null) return ControllerUsbUiState(ControllerUsbStatus.XSTAR, standard)

        val legacy = accessories.firstOrNull { it == legacyXStarIdentity }
        if (legacy != null) return ControllerUsbUiState(ControllerUsbStatus.XSTAR_LEGACY, legacy)

        val other = accessories.firstOrNull()
        return if (other == null) {
            ControllerUsbUiState()
        } else {
            ControllerUsbUiState(ControllerUsbStatus.OTHER_ACCESSORY, other)
        }
    }
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
                AUTEL_USB_ACCESSORY_ATTACHED -> refresh()
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED)
            addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED)
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
        val identities = usbManager.accessoryList.orEmpty().map { it.toIdentity() }
        mutableState.value = ControllerUsbIdentityClassifier.classify(identities)
    }

    override fun close() {
        runCatching { applicationContext.unregisterReceiver(receiver) }
    }

    private fun UsbAccessory.toIdentity() = ControllerUsbIdentity(
        manufacturer = manufacturer.orEmpty(),
        model = model.orEmpty(),
        version = version.orEmpty()
    )

    private companion object {
        const val AUTEL_USB_ACCESSORY_ATTACHED = "com.autel.sdk.action.USB_ACCESSORY_ATTACHED"
    }
}
