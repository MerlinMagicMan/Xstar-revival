#!/usr/bin/env python3
"""Inspect the decoded header of an X-Star aggregate firmware package.

The analyzer is read-only. It validates the fields whose structure is supported
by recovered packages and deliberately reports the field at offset 0x07 as
opaque. It does not build firmware or communicate with any hardware.
"""

from __future__ import annotations

import argparse
import hashlib
import struct
from datetime import datetime, timezone
from pathlib import Path

from extract_xstar_firmware import parse_manifest, xor_decode


MAGIC = b"AT"
OPAQUE_FIELD_OFFSET = 0x07
TOTAL_LENGTH_OFFSET = 0x0B
BUILD_TIMESTAMP_OFFSET = 0x13
CONSTANT_FIELD_OFFSET = 0x17
PRODUCT_OFFSET = 0x1B
VENDOR_OFFSET = 0x5B
PRE_MANIFEST_MARKER_OFFSET = 0x101
MANIFEST_LENGTH_OFFSET = 0x103
MANIFEST_OFFSET = 0x107
PRE_MANIFEST_MARKER = b"\xB1\xC0"


def u32(data: bytes, offset: int) -> int:
    if offset < 0 or offset + 4 > len(data):
        raise ValueError(f"32-bit read outside aggregate at 0x{offset:X}")
    return struct.unpack_from("<I", data, offset)[0]


def fixed_ascii(data: bytes, start: int, end: int, label: str) -> str:
    raw = data[start:end].split(b"\0", 1)[0]
    try:
        return raw.decode("ascii")
    except UnicodeDecodeError as exc:
        raise ValueError(f"{label} is not ASCII") from exc


def analyze(raw: bytes) -> dict[str, object]:
    decoded = xor_decode(raw)
    if len(decoded) < MANIFEST_OFFSET:
        raise ValueError("aggregate is shorter than the fixed header")
    if decoded[:2] != MAGIC:
        raise ValueError(f"decoded magic {decoded[:2].hex()} != {MAGIC.hex()}")

    total_length = u32(decoded, TOTAL_LENGTH_OFFSET)
    if total_length != len(raw):
        raise ValueError(
            f"declared aggregate length {total_length} != actual length {len(raw)}"
        )
    if decoded[PRE_MANIFEST_MARKER_OFFSET:MANIFEST_LENGTH_OFFSET] != (
        PRE_MANIFEST_MARKER
    ):
        raise ValueError("pre-manifest marker mismatch")

    manifest, manifest_start, manifest_end = parse_manifest(decoded)
    manifest_length = u32(decoded, MANIFEST_LENGTH_OFFSET)
    if manifest_start != MANIFEST_OFFSET:
        raise ValueError(
            f"manifest starts at 0x{manifest_start:X}, expected 0x{MANIFEST_OFFSET:X}"
        )
    if manifest_end - manifest_start != manifest_length:
        raise ValueError(
            f"manifest length {manifest_end - manifest_start} != {manifest_length}"
        )

    timestamp = u32(decoded, BUILD_TIMESTAMP_OFFSET)
    return {
        "raw_sha256": hashlib.sha256(raw).hexdigest(),
        "total_length": total_length,
        "format_prefix": decoded[0x02:OPAQUE_FIELD_OFFSET].hex(),
        "opaque_field_0x07": decoded[OPAQUE_FIELD_OFFSET:TOTAL_LENGTH_OFFSET].hex(),
        "build_timestamp": timestamp,
        "build_time_utc": datetime.fromtimestamp(timestamp, timezone.utc).isoformat(),
        "constant_field_0x17": u32(decoded, CONSTANT_FIELD_OFFSET),
        "product": fixed_ascii(decoded, PRODUCT_OFFSET, VENDOR_OFFSET, "product"),
        "vendor": fixed_ascii(
            decoded, VENDOR_OFFSET, PRE_MANIFEST_MARKER_OFFSET, "vendor"
        ),
        "manifest_length": manifest_length,
        "component_count": len(manifest["data"]),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("firmware", type=Path, help="X-Star aggregate .bin")
    args = parser.parse_args()

    try:
        result = analyze(args.firmware.read_bytes())
    except (OSError, OverflowError, ValueError) as exc:
        parser.exit(1, f"error: {exc}\n")

    print(f"file={args.firmware}")
    for key, value in result.items():
        print(f"{key}={value}")
    print("opaque_field_0x07_semantics=UNASSIGNED")
    print("hardware_access=NONE")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
