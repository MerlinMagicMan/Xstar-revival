#!/usr/bin/env python3
"""Rebuild a verified RC-PRO component for the simulator-only RC patch.

This offline tool accepts only the exact final stock RC component and exact
decoded simulator image. It updates the solved STM32 wrapper CRC, restores the
stock AES-128-ECB packaging, and verifies a full decrypt round trip.

The output is a component research artifact, not a complete aggregate update.
This tool has no device discovery, USB, flashing, or controller-write code.
"""

from __future__ import annotations

import argparse
import binascii
import hashlib
import struct
import subprocess
import sys
from pathlib import Path

from analyze_rc_firmware import BLOCK_SIZE, HEADER_SIZE, load_image
from decode_rc_firmware import (
    AES_128_KEY,
    decrypt_payload,
    resolve_openssl,
    sha256,
    stm32_word_crc32,
    validate_stm32_image,
    write_new_file,
)


KNOWN_STOCK_COMPONENT_SHA256 = (
    "ac490bc3c3ec48c23b7dd6910fcf7ae593dc3dbc71ee6e125174a3b3d8bf4bb4"
)
KNOWN_SIMULATOR_DECODED_SHA256 = (
    "98844babe115f5e5e62f0965b6b05b6f9370098bb438c21aa75fc1cf29da5019"
)
CHECK_WORD_OFFSET = 0x24


def encrypt_payload(decoded: bytes, declared_length: int, openssl: str) -> bytes:
    if len(decoded) != declared_length:
        raise ValueError(
            f"decoded length {len(decoded)} does not match wrapper length {declared_length}"
        )
    padded_length = ((declared_length + BLOCK_SIZE - 1) // BLOCK_SIZE) * BLOCK_SIZE
    padded = decoded + b"\xFF" * (padded_length - declared_length)
    result = subprocess.run(
        [openssl, "enc", "-e", "-aes-128-ecb", "-K", AES_128_KEY, "-nopad"],
        input=padded,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if result.returncode:
        detail = result.stderr.decode("utf-8", errors="replace").strip()
        raise RuntimeError(f"OpenSSL failed to encode the payload: {detail}")
    if len(result.stdout) != padded_length:
        raise ValueError("encoded payload length differs from the required padded length")
    return result.stdout


def rebuild_component(stock_path: Path, decoded: bytes, openssl: str) -> bytes:
    stock = load_image(stock_path)
    if sha256(stock.data) != KNOWN_STOCK_COMPONENT_SHA256:
        raise ValueError("stock component is not the verified RC-PRO V1.0.1.5 image")
    if sha256(decoded) != KNOWN_SIMULATOR_DECODED_SHA256:
        raise ValueError("decoded input is not the verified simulator-only RC image")

    stock_decoded, _ = decrypt_payload(stock, openssl)
    validate_stm32_image(stock_decoded)
    validate_stm32_image(decoded)

    header = bytearray(stock.data[:HEADER_SIZE])
    wrapper_crc = stm32_word_crc32(decoded)
    struct.pack_into("<I", header, CHECK_WORD_OFFSET, wrapper_crc)
    encrypted = encrypt_payload(decoded, stock.declared_payload_length, openssl)
    rebuilt = bytes(header) + encrypted

    if len(rebuilt) != len(stock.data):
        raise AssertionError("rebuilt component length differs from the stock component")
    round_trip = subprocess.run(
        [openssl, "enc", "-d", "-aes-128-ecb", "-K", AES_128_KEY, "-nopad"],
        input=encrypted,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if round_trip.returncode or round_trip.stdout[: len(decoded)] != decoded:
        raise ValueError("rebuilt component failed its decrypt round-trip verification")
    if any(value != 0xFF for value in round_trip.stdout[len(decoded) :]):
        raise ValueError("rebuilt component has invalid erased-flash padding")
    return rebuilt


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("stock_component", type=Path, help="verified stock V1.0.1.5 component")
    parser.add_argument("decoded_simulator", type=Path, help="verified decoded simulator image")
    parser.add_argument("-o", "--output", type=Path, required=True)
    parser.add_argument("--openssl", default="openssl")
    args = parser.parse_args()

    try:
        openssl = resolve_openssl(args.openssl)
        decoded = args.decoded_simulator.read_bytes()
        rebuilt = rebuild_component(args.stock_component, decoded, openssl)
        write_new_file(args.output, rebuilt)
    except (FileNotFoundError, OSError, RuntimeError, ValueError) as exc:
        parser.exit(1, f"error: {exc}\n")

    print(f"stock_component={args.stock_component}")
    print(f"decoded_simulator={args.decoded_simulator}")
    print(f"output={args.output}")
    print(f"output_sha256={hashlib.sha256(rebuilt).hexdigest()}")
    print(f"wrapper_crc32=0x{stm32_word_crc32(decoded):08X}")
    print(f"component_jamcrc=0x{(~binascii.crc32(rebuilt) & 0xFFFFFFFF):08X}")
    print("wrapper_crc32_verification=MATCH")
    print("aes_round_trip=MATCH")
    print("component_length=MATCH_STOCK")
    print("artifact=OFFLINE_RC_COMPONENT")
    print("complete_aggregate=NO")
    print("controller_write=NONE")
    return 0


if __name__ == "__main__":
    sys.exit(main())
