#!/usr/bin/env python3
"""Read-only structural analyzer for the extracted X-Star Battery V6.07 image.

This tool does not decrypt, modify, or flash firmware. It reports structural
properties that are useful when identifying the target MCU and inner encoding.

Usage:
    python tools/firmware/analyze_battery_v607.py X3P_BATTERY41_V6.07_20170627.BIN
"""

from __future__ import annotations

import argparse
import hashlib
import math
from collections import Counter, defaultdict
from pathlib import Path

EXPECTED_SIZE = 0x3800
EXPECTED_MD5 = "2c4aad6d78b12152c5ddf3fb4edc2cc9"
CANDIDATE_LOAD_BASE = 0xC800


def entropy(data: bytes) -> float:
    counts = Counter(data)
    total = len(data)
    return -sum((n / total) * math.log2(n / total) for n in counts.values())


def repeated_blocks(data: bytes, block_size: int):
    seen: dict[bytes, list[int]] = defaultdict(list)
    for off in range(0, len(data) - block_size + 1, block_size):
        seen[data[off : off + block_size]].append(off)
    return sorted(
        ((len(offsets), block, offsets) for block, offsets in seen.items() if len(offsets) > 1),
        reverse=True,
        key=lambda item: item[0],
    )


def longest_aligned_run(data: bytes, block_size: int):
    best = (0, None, None, None)
    off = 0
    while off + block_size <= len(data):
        block = data[off : off + block_size]
        end = off + block_size
        while end + block_size <= len(data) and data[end : end + block_size] == block:
            end += block_size
        count = (end - off) // block_size
        if count > best[0]:
            best = (count, off, end, block)
        off = end if end > off + block_size else off + block_size
    return best


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("image", type=Path)
    args = parser.parse_args()

    data = args.image.read_bytes()
    md5 = hashlib.md5(data).hexdigest()
    sha256 = hashlib.sha256(data).hexdigest()

    print(f"file: {args.image}")
    print(f"size: {len(data)} bytes (0x{len(data):X})")
    print(f"md5: {md5}")
    print(f"sha256: {sha256}")
    print(f"entropy: {entropy(data):.4f} bits/byte")
    print(f"expected size match: {len(data) == EXPECTED_SIZE}")
    print(f"manifest md5 match: {md5.lower() == EXPECTED_MD5}")

    for block_size in (4, 8, 16):
        reps = repeated_blocks(data, block_size)
        print(f"\nTop repeated aligned {block_size}-byte blocks:")
        for count, block, offsets in reps[:8]:
            print(
                f"  x{count:3d} {block.hex()} first=0x{offsets[0]:04X} "
                f"last=0x{offsets[-1]:04X}"
            )

    count, start, end, block = longest_aligned_run(data, 8)
    print("\nLongest identical aligned 8-byte run:")
    if block is not None:
        print(f"  block: {block.hex()}")
        print(f"  count: {count}")
        print(f"  file range: 0x{start:04X}-0x{end - 1:04X}")
        print(
            f"  candidate MSP430 address range if loaded at 0x{CANDIDATE_LOAD_BASE:04X}: "
            f"0x{CANDIDATE_LOAD_BASE + start:04X}-0x{CANDIDATE_LOAD_BASE + end - 1:04X}"
        )

    print("\nTail mapping under candidate 0xC800 load base:")
    for off in (0x3600, 0x3640, 0x3648, 0x37B8, 0x37C0, 0x37C8, 0x37F8):
        if off < len(data):
            chunk = data[off : min(off + 16, len(data))]
            print(
                f"  file 0x{off:04X} -> addr 0x{CANDIDATE_LOAD_BASE + off:04X}: "
                f"{chunk.hex()}"
            )

    print("\nInterpretation:")
    print("  * 0x3800 bytes exactly matches a 14 KiB contiguous image window.")
    print("  * A long run of identical aligned 8-byte blocks is consistent with an")
    print("    ECB-like 64-bit transformation over erased/padded flash data.")
    print("  * If the image maps to MSP430 flash starting at 0xC800, its last 64")
    print("    bytes land at 0xFFC0-0xFFFF, where MSP430 interrupt vectors live.")
    print("  * This is evidence for the MSP430-target hypothesis, not proof of the")
    print("    exact cipher, key, load base, or MCU part.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
