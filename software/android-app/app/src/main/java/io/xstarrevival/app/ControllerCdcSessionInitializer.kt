package io.xstarrevival.app

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface

/**
 * Starts only the volatile USB CDC-ACM session advertised by the direct controller port.
 *
 * The two class requests configure 115200/8-N-1 and assert DTR/RTS. They do not contain an Autel
 * command, use a vendor request, access a data-OUT endpoint, or write persistent device storage.
 */
internal object ControllerCdcSessionInitializer {
    private const val USB_RECIPIENT_INTERFACE = 0x01
    private const val REQUEST_TIMEOUT_MS = 1_000

    const val BAUD_RATE = 115_200
    const val SET_LINE_CODING_REQUEST = 0x20
    const val SET_CONTROL_LINE_STATE_REQUEST = 0x22
    const val DTR_RTS_ENABLED = 0x03
    const val REQUEST_TYPE = UsbConstants.USB_DIR_OUT or
        UsbConstants.USB_TYPE_CLASS or USB_RECIPIENT_INTERFACE

    fun lineCoding1152008N1(): ByteArray = byteArrayOf(
        0x00,
        0xC2.toByte(),
        0x01,
        0x00,
        0x00,
        0x00,
        0x08
    )

    fun initialize(connection: UsbDeviceConnection, controlInterface: UsbInterface): String? {
        val lineCoding = lineCoding1152008N1()
        val lineCodingResult = connection.controlTransfer(
            REQUEST_TYPE,
            SET_LINE_CODING_REQUEST,
            0,
            controlInterface.id,
            lineCoding,
            lineCoding.size,
            REQUEST_TIMEOUT_MS
        )
        if (lineCodingResult != lineCoding.size) {
            return "CDC line setup failed ($lineCodingResult/${lineCoding.size})"
        }

        val lineStateResult = connection.controlTransfer(
            REQUEST_TYPE,
            SET_CONTROL_LINE_STATE_REQUEST,
            DTR_RTS_ENABLED,
            controlInterface.id,
            null,
            0,
            REQUEST_TIMEOUT_MS
        )
        if (lineStateResult != 0) return "CDC DTR setup failed ($lineStateResult)"

        return null
    }
}
