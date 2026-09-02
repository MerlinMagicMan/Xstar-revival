#!/usr/bin/env python3
"""Build an offline, decoded RC firmware image for simulator research.

The patch is intentionally limited to the known V1.0.1.5 decoded application.
It reroutes the existing stick frame from the aircraft CAN queue to the
controller's existing USART1 framed-data sender. It does not wrap, encrypt,
flash, discover, or communicate with a physical controller.

The output is a research artifact, not an installable firmware package. The
RC-PRO wrapper integrity field and a recovery procedure must be understood
before any physical-controller write is considered.
"""

from __future__ import annotations

import argparse
import hashlib
import struct
import sys
from pathlib import Path

from decode_rc_firmware import (
    APPLICATION_BASE,
    validate_stm32_image,
    write_new_file,
)


KNOWN_DECODED_SHA256 = (
    "3a7180278ed9e4046ed57d188e09d5168ae8b61c29381c4d9869e83f258ae718"
)
CALLBACK_ADDRESS = 0x080277C4
CALLBACK_END = 0x080277F8
AA_FRAME_BUILDER = 0x08028058
USART1_FRAME_SENDER = 0x080280E0
STICK_BUFFER = 0x200017B8

# Complete selector 0x210 callback from the known decoded V1.0.1.5 image.
# Keeping an exact preimage prevents this tool from patching a merely similar
# release or a previously modified file.
EXPECTED_CALLBACK = bytes.fromhex(
    "70b50a46094d298a1144802901d9012070bd01462c8a064892b203190320"
    "00f039fc20442882002070bd0000680b0020b8170020"
)

# Position-independent parts of the replacement callback. The two Thumb BL
# instructions are filled from their absolute source and target addresses.
PATCH_TEMPLATE = bytes.fromhex(
    "30b57c2911d80a460146094c23460320"  # bounds check and AA builder setup
    "00000000"  # BL AA_FRAME_BUILDER
    "0546024621460320"  # USART1 sender setup
    "00000000"  # BL USART1_FRAME_SENDER
    "002814bf0020012030bd"  # callback success/failure convention
    "012030bd0000"  # oversize failure and alignment
    "b8170020"  # STICK_BUFFER literal
)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def encode_thumb_bl(source_address: int, target_address: int) -> bytes:
    """Encode a Thumb-2 BL from *source_address* to *target_address*."""

    offset = target_address - (source_address + 4)
    if offset % 2:
        raise ValueError("Thumb BL target is not halfword aligned")
    if not -(1 << 24) <= offset < (1 << 24):
        raise ValueError("Thumb BL target is outside the signed 25-bit range")

    sign = (offset >> 24) & 1
    i1 = (offset >> 23) & 1
    i2 = (offset >> 22) & 1
    j1 = ((~i1) & 1) ^ sign
    j2 = ((~i2) & 1) ^ sign
    imm10 = (offset >> 12) & 0x3FF
    imm11 = (offset >> 1) & 0x7FF
    first = 0xF000 | (sign << 10) | imm10
    second = 0xD000 | (j1 << 13) | (j2 << 11) | imm11
    return struct.pack("<HH", first, second)


def build_callback_patch() -> bytes:
    patch = bytearray(PATCH_TEMPLATE)
    patch[0x10:0x14] = encode_thumb_bl(CALLBACK_ADDRESS + 0x10, AA_FRAME_BUILDER)
    patch[0x1C:0x20] = encode_thumb_bl(CALLBACK_ADDRESS + 0x1C, USART1_FRAME_SENDER)
    if len(patch) != CALLBACK_END - CALLBACK_ADDRESS:
        raise AssertionError("replacement callback does not exactly fill its code region")
    if struct.unpack_from("<I", patch, 0x30)[0] != STICK_BUFFER:
        raise AssertionError("replacement callback has an unexpected buffer literal")
    return bytes(patch)


def patch_decoded_image(decoded: bytes) -> tuple[bytes, int]:
    actual_hash = sha256(decoded)
    if actual_hash != KNOWN_DECODED_SHA256:
        raise ValueError(
            "unsupported decoded firmware: expected V1.0.1.5 SHA-256 "
            f"{KNOWN_DECODED_SHA256}, got {actual_hash}"
        )

    validate_stm32_image(decoded)
    start = CALLBACK_ADDRESS - APPLICATION_BASE
    end = CALLBACK_END - APPLICATION_BASE
    if decoded[start:end] != EXPECTED_CALLBACK:
        raise ValueError("selector 0x210 callback does not match the verified preimage")

    patch = build_callback_patch()
    output = decoded[:start] + patch + decoded[end:]
    validate_stm32_image(output)

    if len(output) != len(decoded):
        raise AssertionError("replacement changed the decoded image length")
    changed = sum(left != right for left, right in zip(decoded, output))
    if output[:start] != decoded[:start] or output[end:] != decoded[end:]:
        raise AssertionError("bytes outside the callback region changed")
    return output, changed


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("firmware", type=Path, help="decoded V1.0.1.5 RC application")
    parser.add_argument(
        "-o",
        "--output",
        type=Path,
        help="output path (default: INPUT.simulator-only.bin)",
    )
    args = parser.parse_args()

    try:
        decoded = args.firmware.read_bytes()
        patched, changed = patch_decoded_image(decoded)
        output = args.output or Path(f"{args.firmware}.simulator-only.bin")
        write_new_file(output, patched)
    except (FileNotFoundError, OSError, ValueError) as exc:
        parser.exit(1, f"error: {exc}\n")

    print(f"input={args.firmware}")
    print(f"input_sha256={sha256(decoded)}")
    print(f"output={output}")
    print(f"output_sha256={sha256(patched)}")
    print(f"callback_address=0x{CALLBACK_ADDRESS:08X}")
    print(f"callback_length={CALLBACK_END - CALLBACK_ADDRESS}")
    print(f"changed_bytes={changed}")
    print("stick_frame=AA/channel-3")
    print("stick_output=USART1/A5/channel-3")
    print("aircraft_can_stick_queue=DISABLED_BY_CALLBACK_REPLACEMENT")
    print("artifact=DECODED_OFFLINE_RESEARCH_IMAGE")
    print("flashable=NO")
    print("hardware_access=NONE")
    return 0


if __name__ == "__main__":
    sys.exit(main())
