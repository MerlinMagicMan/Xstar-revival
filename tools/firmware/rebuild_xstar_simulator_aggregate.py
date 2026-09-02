#!/usr/bin/env python3
"""Rebuild the final X-Star aggregate with the simulator-only RC component.

Only the exact preserved V2.0.12 aggregate and exact verified simulator RC
component are accepted. Component length is unchanged, the compact manifest is
updated in place, and every component MD5 is revalidated before output.

The aggregate header's unlabelled field at offset 0x07 is preserved, not
claimed as solved. The output remains an offline research artifact and this
tool contains no device, updater, USB, or flashing code.
"""

from __future__ import annotations

import argparse
import binascii
import hashlib
import json
import sys
from pathlib import Path

from extract_xstar_firmware import (
    POST_MANIFEST_BYTES,
    SEPARATOR_PREFIX,
    TRAILER,
    parse_manifest,
    xor_decode,
)
from decode_rc_firmware import write_new_file


KNOWN_AGGREGATE_SHA256 = (
    "fe6c66bed25ac01395f3b9082accddf2989cd7a91e848fecb61e82d9b82a64d7"
)
KNOWN_SIMULATOR_COMPONENT_SHA256 = (
    "57746ce022839d59a350791e9020474150a6e080c31b9d77f089cf06912085a9"
)
RC_COMPONENT_TYPE = 8
HEADER_UNRESOLVED_FIELD_OFFSET = 0x07
HEADER_UNRESOLVED_FIELD_SIZE = 4
XOR_KEY = 0xC8


def component_layout(decoded: bytes, manifest: dict) -> list[tuple[dict, int, int]]:
    _, _, manifest_end = parse_manifest(decoded)
    cursor = manifest_end + POST_MANIFEST_BYTES
    layout: list[tuple[dict, int, int]] = []
    entries = manifest["data"]
    for index, entry in enumerate(entries):
        length = int(entry["length"])
        end = cursor + length
        if end > len(decoded):
            raise ValueError(f"truncated component {entry['filename']}")
        layout.append((entry, cursor, end))
        cursor = end
        if index < len(entries) - 1:
            expected = SEPARATOR_PREFIX + (index + 1).to_bytes(4, "little")
            if decoded[cursor : cursor + 6] != expected:
                raise ValueError(f"invalid separator after {entry['filename']}")
            cursor += 6
    if decoded[cursor : cursor + len(TRAILER)] != TRAILER:
        raise ValueError("invalid aggregate trailer")
    if cursor + len(TRAILER) != len(decoded):
        raise ValueError("unexpected aggregate trailing bytes")
    return layout


def validate_component_hashes(decoded: bytes, manifest: dict) -> None:
    for entry, start, end in component_layout(decoded, manifest):
        actual = hashlib.md5(decoded[start:end]).hexdigest().upper()
        if actual != str(entry["md5"]).upper():
            raise ValueError(
                f"component MD5 mismatch for {entry['filename']}: "
                f"{actual} != {entry['md5']}"
            )


def rebuild_aggregate(source_raw: bytes, replacement: bytes) -> tuple[bytes, dict]:
    if hashlib.sha256(source_raw).hexdigest() != KNOWN_AGGREGATE_SHA256:
        raise ValueError("source is not the verified X-Star V2.0.12 aggregate")
    if hashlib.sha256(replacement).hexdigest() != KNOWN_SIMULATOR_COMPONENT_SHA256:
        raise ValueError("replacement is not the verified simulator RC component")

    decoded = xor_decode(source_raw)
    manifest, manifest_start, manifest_end = parse_manifest(decoded)
    validate_component_hashes(decoded, manifest)
    matches = [
        item
        for item in component_layout(decoded, manifest)
        if item[0]["type"] == RC_COMPONENT_TYPE
    ]
    if len(matches) != 1:
        raise ValueError(f"expected one RC component, found {len(matches)}")
    entry, component_start, component_end = matches[0]
    if len(replacement) != component_end - component_start:
        raise ValueError("replacement RC component length differs from the stock component")

    entry["md5"] = hashlib.md5(replacement).hexdigest().upper()
    entry["crc32"] = (~binascii.crc32(replacement)) & 0xFFFFFFFF
    manifest_bytes = json.dumps(manifest, separators=(",", ":")).encode("ascii")
    if len(manifest_bytes) != manifest_end - manifest_start:
        raise ValueError(
            "updated manifest length changed; refusing to alter unresolved aggregate header fields"
        )

    rebuilt_decoded = bytearray(decoded)
    rebuilt_decoded[manifest_start:manifest_end] = manifest_bytes
    rebuilt_decoded[component_start:component_end] = replacement
    rebuilt_manifest, _, _ = parse_manifest(bytes(rebuilt_decoded))
    validate_component_hashes(bytes(rebuilt_decoded), rebuilt_manifest)
    rebuilt_raw = bytes(value ^ XOR_KEY for value in rebuilt_decoded)
    return rebuilt_raw, entry


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("stock_aggregate", type=Path)
    parser.add_argument("simulator_component", type=Path)
    parser.add_argument("-o", "--output", type=Path, required=True)
    args = parser.parse_args()

    try:
        source_raw = args.stock_aggregate.read_bytes()
        replacement = args.simulator_component.read_bytes()
        rebuilt, entry = rebuild_aggregate(source_raw, replacement)
        write_new_file(args.output, rebuilt)
    except (FileNotFoundError, OSError, ValueError) as exc:
        parser.exit(1, f"error: {exc}\n")

    field_end = HEADER_UNRESOLVED_FIELD_OFFSET + HEADER_UNRESOLVED_FIELD_SIZE
    unresolved = xor_decode(source_raw)[HEADER_UNRESOLVED_FIELD_OFFSET:field_end]
    print(f"stock_aggregate={args.stock_aggregate}")
    print(f"simulator_component={args.simulator_component}")
    print(f"output={args.output}")
    print(f"output_sha256={hashlib.sha256(rebuilt).hexdigest()}")
    print(f"rc_md5={entry['md5']}")
    print(f"rc_component_jamcrc=0x{int(entry['crc32']):08X}")
    print(f"preserved_header_field_0x07={unresolved.hex()}")
    print("component_md5_verification=ALL_MATCH")
    print("manifest_length=MATCH_STOCK")
    print("aggregate_length=MATCH_STOCK")
    print("aggregate_header_integrity=UNRESOLVED")
    print("artifact=OFFLINE_AGGREGATE_RESEARCH_IMAGE")
    print("controller_write=NONE")
    return 0


if __name__ == "__main__":
    sys.exit(main())
