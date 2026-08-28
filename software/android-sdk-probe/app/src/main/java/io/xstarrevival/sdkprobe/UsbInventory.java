package io.xstarrevival.sdkprobe;

import android.content.Context;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Passive Android USB descriptor inventory. Performs no transfers. */
public final class UsbInventory {
    private UsbInventory() {}

    public static List<String> describe(Context context) {
        List<String> lines = new ArrayList<>();
        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (manager == null) {
            lines.add("USB manager unavailable");
            return lines;
        }

        Map<String, UsbDevice> devices = manager.getDeviceList();
        lines.add("USB device count: " + devices.size());
        for (UsbDevice device : devices.values()) {
            lines.add(String.format(
                    "USB %04x:%04x class=%d subclass=%d proto=%d interfaces=%d permission=%s",
                    device.getVendorId(), device.getProductId(), device.getDeviceClass(),
                    device.getDeviceSubclass(), device.getDeviceProtocol(), device.getInterfaceCount(),
                    manager.hasPermission(device)));

            if (android.os.Build.VERSION.SDK_INT >= 21) {
                lines.add("  manufacturer=" + redact(device.getManufacturerName()));
                lines.add("  product=" + redact(device.getProductName()));
                // Intentionally do not print serial number into public diagnostics.
            }

            for (int i = 0; i < device.getInterfaceCount(); i++) {
                UsbInterface intf = device.getInterface(i);
                lines.add(String.format(
                        "  if#%d alt=%d class=%d subclass=%d proto=%d endpoints=%d",
                        intf.getId(), intf.getAlternateSetting(), intf.getInterfaceClass(),
                        intf.getInterfaceSubclass(), intf.getInterfaceProtocol(), intf.getEndpointCount()));
                for (int e = 0; e < intf.getEndpointCount(); e++) {
                    UsbEndpoint ep = intf.getEndpoint(e);
                    lines.add(String.format(
                            "    ep=0x%02x dir=%s type=%s maxPacket=%d interval=%d",
                            ep.getAddress(), direction(ep.getDirection()), type(ep.getType()),
                            ep.getMaxPacketSize(), ep.getInterval()));
                }
            }
        }
        return lines;
    }

    private static String direction(int value) {
        return value == UsbConstants.USB_DIR_IN ? "IN" : "OUT";
    }

    private static String type(int value) {
        switch (value) {
            case UsbConstants.USB_ENDPOINT_XFER_CONTROL: return "CONTROL";
            case UsbConstants.USB_ENDPOINT_XFER_ISOC: return "ISO";
            case UsbConstants.USB_ENDPOINT_XFER_BULK: return "BULK";
            case UsbConstants.USB_ENDPOINT_XFER_INT: return "INTERRUPT";
            default: return "UNKNOWN(" + value + ")";
        }
    }

    private static String redact(String value) {
        if (value == null || value.trim().isEmpty()) return "—";
        return value.replaceAll("[\\r\\n]", " ");
    }
}
