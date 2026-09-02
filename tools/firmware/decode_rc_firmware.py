#!/usr/bin/env python3
"""Decode an extracted Autel X-Star RC-PRO application image.

This is an offline file converter. It does not discover, open, command, or
write to a remote controller. The input is left unchanged and the output is
only written after the decrypted image passes STM32 structural checks.
"""

from __future__ import annotations

import argparse
import hashlib
import shutil
import struct
import subprocess
import sys
from pathlib import Path

from analyze_rc_firmware import BLOCK_SIZE, RcImage, load_image


AES_128_KEY = "2b7e151628aed2a6abf7158809cf4f3c"
FLASH_START = 0x08000000
FLASH_END = 0x08200000
APPLICATION_BASE = 0x08013000
SRAM_START = 0x20000000
SRAM_END = 0x20080000
STM32_CRC32_POLYNOMIAL = 0x04C11DB7


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def stm32_word_crc32(data: bytes) -> int:
    """Return the wrapper CRC used by the X-Star STM32 component loader.

    The loader initializes the CRC to zero, pads the decoded payload to a
    32-bit boundary with erased-flash bytes, and feeds little-endian words to
    the non-reflected STM32 CRC-32 peripheral.
    """

    remainder = len(data) % 4
    if remainder:
        data += b"\xFF" * (4 - remainder)

    crc = 0
    for offset in range(0, len(data), 4):
        crc ^= struct.unpack_from("<I", data, offset)[0]
        for _ in range(32):
            if crc & 0x80000000:
                crc = ((crc << 1) ^ STM32_CRC32_POLYNOMIAL) & 0xFFFFFFFF
            else:
                crc = (crc << 1) & 0xFFFFFFFF
    return crc


def decrypt_payload(image: RcImage, openssl: str) -> tuple[bytes, bytes]:
    if len(image.payload) % BLOCK_SIZE:
        raise ValueError("encrypted payload is not a whole number of AES blocks")

    command = [
        openssl,
        "enc",
        "-d",
        "-aes-128-ecb",
        "-K",
        AES_128_KEY,
        "-nopad",
    ]
    result = subprocess.run(
        command,
        input=image.payload,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if result.returncode:
        detail = result.stderr.decode("utf-8", errors="replace").strip()
        raise RuntimeError(f"OpenSSL failed to decode the payload: {detail}")

    padded = result.stdout
    if len(padded) != len(image.payload):
        raise ValueError(
            f"decoded size {len(padded)} differs from encrypted size {len(image.payload)}"
        )

    declared_length = image.declared_payload_length
    decoded = padded[:declared_length]
    padding = padded[declared_length:]
    if any(value != 0xFF for value in padding):
        raise ValueError("decoded trailing bytes are not the expected 0xFF flash padding")
    calculated_check = stm32_word_crc32(decoded)
    if calculated_check != image.check_word:
        raise ValueError(
            "decoded wrapper CRC mismatch: "
            f"0x{calculated_check:08X} != 0x{image.check_word:08X}"
        )
    return decoded, padding


def validate_stm32_image(
    decoded: bytes, application_base: int = APPLICATION_BASE
) -> tuple[int, int, int, int]:
    if len(decoded) < 8:
        raise ValueError("decoded image is too short for an STM32 vector table")

    initial_sp, reset_vector = struct.unpack_from("<II", decoded)
    reset_address = reset_vector & ~1
    if initial_sp % 4 or not SRAM_START <= initial_sp < SRAM_END:
        raise ValueError(f"invalid STM32 initial stack pointer 0x{initial_sp:08X}")
    if not reset_vector & 1:
        raise ValueError(f"reset vector 0x{reset_vector:08X} is not a Thumb address")
    if not FLASH_START <= reset_address < FLASH_END:
        raise ValueError(f"reset vector 0x{reset_vector:08X} is outside STM32 flash")
    reset_offset = reset_address - application_base
    if not 0 <= reset_offset < len(decoded):
        raise ValueError(
            f"reset vector 0x{reset_vector:08X} does not map into the packaged "
            f"application at 0x{application_base:08X}"
        )
    reset_probe = decoded[reset_offset : reset_offset + 8]
    if not reset_probe or all(value in (0x00, 0xFF) for value in reset_probe):
        raise ValueError(
            f"reset handler at file offset 0x{reset_offset:X} contains no plausible code"
        )

    valid_vectors = 0
    application_vectors = 0
    for offset in range(4, min(len(decoded), 0x100), 4):
        vector = struct.unpack_from("<I", decoded, offset)[0]
        if vector in (0, 0xFFFFFFFF):
            continue
        address = vector & ~1
        if vector & 1 and FLASH_START <= address < FLASH_END:
            valid_vectors += 1
            if application_base <= address < application_base + len(decoded):
                application_vectors += 1
    if valid_vectors < 8:
        raise ValueError(
            f"only {valid_vectors} plausible interrupt vectors were found; expected at least 8"
        )
    if application_vectors < 8:
        raise ValueError(
            f"only {application_vectors} vectors map into the packaged application; "
            "expected at least 8"
        )
    return initial_sp, reset_vector, valid_vectors, application_vectors


def resolve_openssl(requested: str) -> str:
    resolved = shutil.which(requested)
    if not resolved:
        raise FileNotFoundError(
            f"OpenSSL executable {requested!r} was not found; install it or pass --openssl"
        )
    return resolved


def write_new_file(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    try:
        with path.open("xb") as stream:
            stream.write(data)
    except FileExistsError as exc:
        raise FileExistsError(f"refusing to overwrite existing output: {path}") from exc


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("firmware", type=Path, help="extracted X3P_RC_*.BIN component")
    parser.add_argument(
        "-o",
        "--output",
        type=Path,
        help="decoded output path (default: INPUT.decoded.bin)",
    )
    parser.add_argument(
        "--openssl",
        default="openssl",
        help="OpenSSL executable name or path (default: openssl)",
    )
    args = parser.parse_args()

    try:
        image = load_image(args.firmware)
        decoded, padding = decrypt_payload(image, resolve_openssl(args.openssl))
        initial_sp, reset_vector, valid_vectors, application_vectors = validate_stm32_image(
            decoded
        )
        output = args.output or Path(f"{args.firmware}.decoded.bin")
        write_new_file(output, decoded)
    except (FileNotFoundError, OSError, RuntimeError, ValueError) as exc:
        parser.exit(1, f"error: {exc}\n")

    print(f"input={args.firmware}")
    print(f"input_sha256={sha256(image.data)}")
    print(f"product={image.product}")
    print(f"version={image.version}")
    print("cipher=AES-128-ECB")
    print(f"decoded_length={len(decoded)}")
    print(f"padding_length={len(padding)}")
    print("padding_byte=0xFF")
    print(f"wrapper_crc32=0x{stm32_word_crc32(decoded):08X}")
    print("wrapper_crc32_verification=MATCH")
    print(f"application_base=0x{APPLICATION_BASE:08X}")
    print(f"initial_stack_pointer=0x{initial_sp:08X}")
    print(f"reset_vector=0x{reset_vector:08X}")
    print(f"reset_file_offset=0x{(reset_vector & ~1) - APPLICATION_BASE:X}")
    print(f"plausible_interrupt_vectors={valid_vectors}")
    print(f"application_interrupt_vectors={application_vectors}")
    print(f"decoded_sha256={sha256(decoded)}")
    print(f"output={output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
