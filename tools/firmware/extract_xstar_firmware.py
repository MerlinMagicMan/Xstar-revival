#!/usr/bin/env python3
"""Extract component images from an Autel X-Star Premium aggregate firmware file.

Observed format for X3P_FW_900M_V2.0.12.bin:
- Entire aggregate is XOR-obfuscated byte-for-byte with 0xC8.
- Decoded header contains a JSON manifest beginning with {"data":...}.
- Seven bytes follow the JSON manifest before component 0.
- Component payloads are concatenated in manifest order.
- Six-byte separators occur between payloads: E1 E0 <next-id:u32 little-endian>.
- Four-byte trailer: E1 D1 55 55.

The tool is deliberately read-only. It never modifies the source firmware.
It validates every extracted component against the MD5 recorded in Autel's
embedded manifest.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

XOR_KEY = 0xC8
POST_MANIFEST_BYTES = 7
SEPARATOR_PREFIX = b"\xE1\xE0"
TRAILER = b"\xE1\xD1\x55\x55"


def xor_decode(data: bytes) -> bytes:
    return bytes(value ^ XOR_KEY for value in data)


def parse_manifest(decoded: bytes):
    start = decoded.find(b'{"data"')
    if start < 0:
        raise ValueError("decoded manifest marker not found")

    text = decoded[start:].decode("latin1")
    manifest, consumed_chars = json.JSONDecoder().raw_decode(text)
    if not isinstance(manifest, dict) or not isinstance(manifest.get("data"), list):
        raise ValueError("unexpected manifest structure")

    return manifest, start, start + consumed_chars


def extract(source: Path, out_dir: Path, write_decoded: bool = False) -> None:
    raw = source.read_bytes()
    decoded = xor_decode(raw)
    manifest, manifest_start, manifest_end = parse_manifest(decoded)
    entries = manifest["data"]

    cursor = manifest_end + POST_MANIFEST_BYTES
    out_dir.mkdir(parents=True, exist_ok=True)

    print(f"source_sha256={hashlib.sha256(raw).hexdigest()}")
    print(f"source_size={len(raw)}")
    print(f"xor_key=0x{XOR_KEY:02X}")
    print(f"manifest_offset=0x{manifest_start:X}")
    print(f"manifest_end=0x{manifest_end:X}")
    print(f"component_count={len(entries)}")

    (out_dir / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    if write_decoded:
        (out_dir / (source.name + ".decoded")).write_bytes(decoded)

    failures = []
    for index, entry in enumerate(entries):
        filename = entry["filename"]
        length = int(entry["length"])
        expected_md5 = str(entry["md5"]).upper()

        payload = decoded[cursor : cursor + length]
        if len(payload) != length:
            raise ValueError(f"truncated payload {filename}")

        actual_md5 = hashlib.md5(payload).hexdigest().upper()
        ok = actual_md5 == expected_md5
        print(
            f"{index:02d} offset=0x{cursor:X} length={length:9d} "
            f"md5={'OK' if ok else 'FAIL'} {filename}"
        )
        if not ok:
            failures.append((filename, expected_md5, actual_md5))

        (out_dir / filename).write_bytes(payload)
        cursor += length

        if index < len(entries) - 1:
            separator = decoded[cursor : cursor + 6]
            expected = SEPARATOR_PREFIX + (index + 1).to_bytes(4, "little")
            if separator != expected:
                raise ValueError(
                    f"unexpected separator after {filename}: "
                    f"{separator.hex()} != {expected.hex()}"
                )
            cursor += 6

    trailer = decoded[cursor : cursor + 4]
    if trailer != TRAILER:
        raise ValueError(f"unexpected trailer: {trailer.hex()} != {TRAILER.hex()}")
    cursor += 4

    if cursor != len(decoded):
        raise ValueError(f"unexpected trailing bytes: {len(decoded) - cursor}")

    if failures:
        for filename, expected, actual in failures:
            print(f"MD5 FAIL {filename}: expected={expected} got={actual}")
        raise SystemExit(2)

    print("verification=ALL_COMPONENT_MD5_MATCH")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("firmware", type=Path)
    parser.add_argument("-o", "--output", type=Path, default=Path("xstar-fw-extracted"))
    parser.add_argument("--write-decoded-container", action="store_true")
    args = parser.parse_args()
    extract(args.firmware, args.output, args.write_decoded_container)


if __name__ == "__main__":
    main()
