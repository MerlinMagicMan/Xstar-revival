#!/usr/bin/env python3
"""Decode the observed Autel X-Star Battery V5.21/V6.07 payload transform.

Research result:
- the battery component is transformed with an 8-byte repeating XOR mask;
- the mask is 15 63 8F 5C CD D3 E2 79;
- therefore ciphertext EA 9C 70 A3 32 2C 1D 86 decodes to eight 0xFF bytes,
  matching erased flash;
- applying the mask to the full 0x3800-byte image produces code-like MSP430
  content and literal erased-flash runs near the top of the candidate memory map.

This utility is offline/read-only with respect to hardware. It can optionally
write a decoded local research image; it never flashes a battery or aircraft.
No proprietary firmware is embedded in this script.
"""

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path

MASK = bytes.fromhex("15638F5CCDD3E279")
KNOWN_ERASED_CIPHER = bytes.fromhex("EA9C70A3322C1D86")
EXPECTED_LENGTH = 0x3800
CANDIDATE_BASE = 0xC800


def transform(data: bytes) -> bytes:
    return bytes(value ^ MASK[index % len(MASK)] for index, value in enumerate(data))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("image", type=Path)
    parser.add_argument("-o", "--output", type=Path)
    args = parser.parse_args()

    raw = args.image.read_bytes()
    decoded = transform(raw)

    print(f"input_size=0x{len(raw):X} ({len(raw)})")
    print("input_sha256=" + hashlib.sha256(raw).hexdigest())
    print("xor_mask=" + MASK.hex().upper())
    print("candidate_base=0x%04X" % CANDIDATE_BASE)
    print("candidate_end=0x%04X" % (CANDIDATE_BASE + len(raw) - 1))
    print("known_erased_cipher_count=" + str(raw.count(KNOWN_ERASED_CIPHER)))
    print("decoded_ff8_count=" + str(decoded.count(b"\xFF" * 8)))
    print("decoded_head=" + decoded[:64].hex().upper())
    print("decoded_tail=" + decoded[-64:].hex().upper())

    if len(raw) != EXPECTED_LENGTH:
        print("warning=unexpected_image_length")

    # Strong self-check: the observed repeated ciphertext block must decode to FF*8.
    if transform(KNOWN_ERASED_CIPHER) != b"\xFF" * 8:
        raise SystemExit("internal transform self-check failed")

    if args.output:
        args.output.write_bytes(decoded)
        print("output=" + str(args.output))
        print("output_sha256=" + hashlib.sha256(decoded).hexdigest())


if __name__ == "__main__":
    main()
