#!/usr/bin/env python3
"""Passive USB reconnaissance utility for X-Star Revival.

Default mode only enumerates devices/descriptors. Optional bulk-IN capture requires
explicit --capture, interface, endpoint and output parameters. No OUT transfers are
implemented by design.
"""

from __future__ import annotations

import argparse
import json
import platform
import sys
import time
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

try:
    import usb.core  # type: ignore
    import usb.util  # type: ignore
except Exception:
    usb = None


@dataclass
class EndpointInfo:
    address: int
    direction: str
    transfer_type: str
    max_packet_size: int
    interval: int


@dataclass
class InterfaceInfo:
    number: int
    alternate_setting: int
    class_code: int
    subclass: int
    protocol: int
    endpoints: list[EndpointInfo]


@dataclass
class DeviceInfo:
    vendor_id: int
    product_id: int
    bus: int | None
    address: int | None
    manufacturer: str | None
    product: str | None
    serial_number: str | None
    device_class: int
    device_subclass: int
    device_protocol: int
    interfaces: list[InterfaceInfo]


def transfer_type(attributes: int) -> str:
    return {0: "control", 1: "isochronous", 2: "bulk", 3: "interrupt"}.get(attributes & 0x3, "unknown")


def safe_string(dev: Any, index: int) -> str | None:
    if not index:
        return None
    try:
        return usb.util.get_string(dev, index)
    except Exception:
        return None


def describe_device(dev: Any) -> DeviceInfo:
    interfaces: list[InterfaceInfo] = []
    try:
        config = dev.get_active_configuration()
    except Exception:
        config = dev[0]

    for interface in config:
        eps: list[EndpointInfo] = []
        for ep in interface:
            direction = "in" if usb.util.endpoint_direction(ep.bEndpointAddress) == usb.util.ENDPOINT_IN else "out"
            eps.append(
                EndpointInfo(
                    address=int(ep.bEndpointAddress),
                    direction=direction,
                    transfer_type=transfer_type(int(ep.bmAttributes)),
                    max_packet_size=int(ep.wMaxPacketSize),
                    interval=int(ep.bInterval),
                )
            )
        interfaces.append(
            InterfaceInfo(
                number=int(interface.bInterfaceNumber),
                alternate_setting=int(interface.bAlternateSetting),
                class_code=int(interface.bInterfaceClass),
                subclass=int(interface.bInterfaceSubClass),
                protocol=int(interface.bInterfaceProtocol),
                endpoints=eps,
            )
        )

    return DeviceInfo(
        vendor_id=int(dev.idVendor),
        product_id=int(dev.idProduct),
        bus=getattr(dev, "bus", None),
        address=getattr(dev, "address", None),
        manufacturer=safe_string(dev, int(dev.iManufacturer)),
        product=safe_string(dev, int(dev.iProduct)),
        serial_number=safe_string(dev, int(dev.iSerialNumber)),
        device_class=int(dev.bDeviceClass),
        device_subclass=int(dev.bDeviceSubClass),
        device_protocol=int(dev.bDeviceProtocol),
        interfaces=interfaces,
    )


def enumerate_devices(vid: int | None, pid: int | None) -> list[tuple[Any, DeviceInfo]]:
    if usb is None:
        raise RuntimeError("PyUSB is not installed. Run: python -m pip install pyusb")
    found = usb.core.find(find_all=True, idVendor=vid, idProduct=pid)
    result = []
    for dev in found or []:
        try:
            result.append((dev, describe_device(dev)))
        except Exception as exc:
            print(f"warning: unable to fully describe {dev}: {exc}", file=sys.stderr)
    return result


def hex_id(value: int) -> str:
    return f"0x{value:04x}"


def printable(info: DeviceInfo) -> dict[str, Any]:
    data = asdict(info)
    data["vendor_id_hex"] = hex_id(info.vendor_id)
    data["product_id_hex"] = hex_id(info.product_id)
    for interface in data["interfaces"]:
        for ep in interface["endpoints"]:
            ep["address_hex"] = f"0x{ep['address']:02x}"
    return data


def bulk_in_capture(dev: Any, interface_no: int, endpoint: int, seconds: float, output: Path, packet_size: int) -> dict[str, Any]:
    """Capture only data already sent by the device on a bulk-IN endpoint."""
    if usb.util.endpoint_direction(endpoint) != usb.util.ENDPOINT_IN:
        raise ValueError("Refusing capture: endpoint is not IN")

    detached = False
    try:
        if hasattr(dev, "is_kernel_driver_active") and dev.is_kernel_driver_active(interface_no):
            dev.detach_kernel_driver(interface_no)
            detached = True
        usb.util.claim_interface(dev, interface_no)

        start = time.monotonic()
        chunks: list[bytes] = []
        timeouts = 0
        while time.monotonic() - start < seconds:
            try:
                packet = dev.read(endpoint, packet_size, timeout=250)
                chunks.append(bytes(packet))
            except usb.core.USBTimeoutError:
                timeouts += 1
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_bytes(b"".join(chunks))
        return {
            "duration_seconds": seconds,
            "chunks": len(chunks),
            "bytes": sum(map(len, chunks)),
            "timeouts": timeouts,
            "output": str(output),
        }
    finally:
        try:
            usb.util.release_interface(dev, interface_no)
        except Exception:
            pass
        if detached:
            try:
                dev.attach_kernel_driver(interface_no)
            except Exception:
                pass


def parse_int(value: str) -> int:
    return int(value, 0)


def main() -> int:
    parser = argparse.ArgumentParser(description="Passive X-Star USB reconnaissance")
    parser.add_argument("--vid", type=parse_int, help="filter vendor ID, e.g. 0x1234")
    parser.add_argument("--pid", type=parse_int, help="filter product ID, e.g. 0xabcd")
    parser.add_argument("--json", type=Path, help="write descriptor inventory to JSON")
    parser.add_argument("--capture", action="store_true", help="explicitly enable bulk-IN capture")
    parser.add_argument("--interface", type=int, help="USB interface number for capture")
    parser.add_argument("--endpoint", type=parse_int, help="bulk-IN endpoint address, e.g. 0x81")
    parser.add_argument("--seconds", type=float, default=10.0, help="capture duration")
    parser.add_argument("--packet-size", type=int, default=16384, help="maximum bulk-IN read size")
    parser.add_argument("--output", type=Path, help="raw capture output file")
    args = parser.parse_args()

    print(f"host={platform.platform()}")
    devices = enumerate_devices(args.vid, args.pid)
    inventory = [printable(info) for _, info in devices]
    print(json.dumps(inventory, indent=2))

    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps(inventory, indent=2) + "\n", encoding="utf-8")

    if args.capture:
        if len(devices) != 1:
            parser.error("--capture requires filters that resolve to exactly one USB device")
        if args.interface is None or args.endpoint is None or args.output is None:
            parser.error("--capture requires --interface, --endpoint and --output")
        dev, info = devices[0]
        valid = [ep for i in info.interfaces if i.number == args.interface for ep in i.endpoints if ep.address == args.endpoint]
        if not valid:
            parser.error("specified endpoint is not present on specified interface")
        if valid[0].direction != "in" or valid[0].transfer_type != "bulk":
            parser.error("capture is limited to bulk-IN endpoints")
        result = bulk_in_capture(dev, args.interface, args.endpoint, args.seconds, args.output, args.packet_size)
        print(json.dumps({"capture": result}, indent=2))

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
