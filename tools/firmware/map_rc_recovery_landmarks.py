#!/usr/bin/env python3
"""Verify recovery-relevant landmarks in the final decoded RC-PRO image.

This is a hash-locked, read-only static analyzer for the preserved X-Star
Premium RC-PRO V1.0.1.5 application. It does not discover or open hardware,
send controller commands, build firmware, or perform an update.
"""

from __future__ import annotations

import argparse
import hashlib
import struct
from pathlib import Path

from decode_rc_firmware import APPLICATION_BASE, FLASH_START, validate_stm32_image


KNOWN_DECODED_SHA256 = (
    "3a7180278ed9e4046ed57d188e09d5168ae8b61c29381c4d9869e83f258ae718"
)
KNOWN_DECODED_LENGTH = 380_228

VTOR_SETUP_OFFSET = 0x22C30
VTOR_SETUP_PREFIX = bytes.fromhex("72b600212748f2f70bf962b6")
APPLICATION_BASE_LITERAL_OFFSET = 0x22CD4
VTOR_FUNCTION_OFFSET = 0x14E50
VTOR_FUNCTION = bytes.fromhex("024a11400143024801607047")
VTOR_ALIGNMENT_MASK_OFFSET = 0x14E5C
VTOR_REGISTER_OFFSET = 0x14E60

GM8136_CALLBACK_OFFSET = 0x20FF8
GM8136_CALLBACK_PREFIX = bytes.fromhex("2de9fe43072803d0")
GM8136_LENGTH_CHECK_OFFSET = 0x21010
GM8136_LENGTH_CHECK = bytes.fromhex("0a2a01d0")
GM8136_CALLBACK_POINTER_OFFSET = 0x1FB0C
GM8136_CALLBACK_POINTER = 0x08033FF9
GM8136_STRINGS = {
    "gm8136_upgrade_parse": (0x2110C, b"/============GM8136 UPGRADE PARSE==========/"),
    "upgrade_request": (0x21148, b"UPGRADE:Req:[%d]"),
    "upgrade_device": (0x2115C, b"UPGRADE:Dev:[%d]"),
    "upgrade_status": (0x21170, b"UPGRADE:Sts:[%d]"),
    "upgrade_percent": (0x21184, b"UPGRADE:Percent:[%d]"),
    "upgrade_retry": (0x2119C, b"UPGRADE:Retry:[%d]"),
    "upgrade_version": (0x211B4, b"UPGRADE:Ver:[0x%x]"),
}

FLASH_UNLOCK_KEY_1 = 0x45670123
FLASH_UNLOCK_KEY_2 = 0xCDEF89AB
EXPECTED_FLASH_KEY_OFFSETS = {
    FLASH_UNLOCK_KEY_1: (0x93C8, 0x93E4),
    FLASH_UNLOCK_KEY_2: (0x93D0, 0x93EC),
}
VERIFIED_DIRECT_FLASH_TARGETS = (
    ("pre_application_page", 0x08012800, 0x216A4),
    ("post_application_page", 0x0807F800, 0x2171C),
)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def require_bytes(data: bytes, offset: int, expected: bytes, label: str) -> None:
    actual = data[offset : offset + len(expected)]
    if actual != expected:
        raise ValueError(
            f"{label} mismatch at 0x{offset:X}: "
            f"{actual.hex()} != {expected.hex()}"
        )


def u32(data: bytes, offset: int) -> int:
    if offset < 0 or offset + 4 > len(data):
        raise ValueError(f"32-bit read outside image at 0x{offset:X}")
    return struct.unpack_from("<I", data, offset)[0]


def find_u32(data: bytes, value: int) -> tuple[int, ...]:
    encoded = struct.pack("<I", value)
    offsets: list[int] = []
    start = 0
    while True:
        offset = data.find(encoded, start)
        if offset < 0:
            return tuple(offsets)
        offsets.append(offset)
        start = offset + 1


def verify_known_image(data: bytes) -> tuple[int, int, int, int]:
    digest = sha256(data)
    if digest != KNOWN_DECODED_SHA256:
        raise ValueError(
            "input is not the exact preserved RC-PRO V1.0.1.5 decoded image: "
            f"{digest}"
        )
    if len(data) != KNOWN_DECODED_LENGTH:
        raise ValueError(
            f"decoded length {len(data)} != expected {KNOWN_DECODED_LENGTH}"
        )

    vectors = validate_stm32_image(data)
    require_bytes(data, VTOR_SETUP_OFFSET, VTOR_SETUP_PREFIX, "VTOR startup call")
    require_bytes(data, VTOR_FUNCTION_OFFSET, VTOR_FUNCTION, "VTOR register writer")
    if u32(data, APPLICATION_BASE_LITERAL_OFFSET) != APPLICATION_BASE:
        raise ValueError("application-base literal mismatch")
    if u32(data, VTOR_ALIGNMENT_MASK_OFFSET) != 0x1FFFFF80:
        raise ValueError("VTOR alignment-mask literal mismatch")
    if u32(data, VTOR_REGISTER_OFFSET) != 0xE000ED08:
        raise ValueError("SCB VTOR register literal mismatch")

    require_bytes(
        data,
        GM8136_CALLBACK_OFFSET,
        GM8136_CALLBACK_PREFIX,
        "GM8136 status callback",
    )
    require_bytes(
        data,
        GM8136_LENGTH_CHECK_OFFSET,
        GM8136_LENGTH_CHECK,
        "GM8136 ten-byte payload check",
    )
    if u32(data, GM8136_CALLBACK_POINTER_OFFSET) != GM8136_CALLBACK_POINTER:
        raise ValueError("GM8136 callback table pointer mismatch")
    for label, (offset, expected) in GM8136_STRINGS.items():
        require_bytes(data, offset, expected, label)

    for key, expected_offsets in EXPECTED_FLASH_KEY_OFFSETS.items():
        actual_offsets = find_u32(data, key)
        if actual_offsets != expected_offsets:
            raise ValueError(
                f"flash key 0x{key:08X} offsets {actual_offsets} "
                f"!= {expected_offsets}"
            )
    for label, address, offset in VERIFIED_DIRECT_FLASH_TARGETS:
        if u32(data, offset) != address:
            raise ValueError(f"{label} literal mismatch")
        if APPLICATION_BASE <= address < APPLICATION_BASE + len(data):
            raise ValueError(f"{label} unexpectedly points inside the application")

    return vectors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("firmware", type=Path, help="decoded RC-PRO V1.0.1.5 image")
    args = parser.parse_args()

    try:
        data = args.firmware.read_bytes()
        initial_sp, reset_vector, valid_vectors, application_vectors = (
            verify_known_image(data)
        )
    except (OSError, ValueError) as exc:
        parser.exit(1, f"error: {exc}\n")

    print(f"file={args.firmware}")
    print("known_image=X3P RC-PRO V1.0.1.5")
    print(f"decoded_sha256={sha256(data)}")
    print(f"decoded_length={len(data)}")
    print(f"application_base=0x{APPLICATION_BASE:08X}")
    print(f"application_end_exclusive=0x{APPLICATION_BASE + len(data):08X}")
    print(f"pre_application_flash_span=0x{APPLICATION_BASE - FLASH_START:X}")
    print(f"initial_stack_pointer=0x{initial_sp:08X}")
    print(f"reset_vector=0x{reset_vector:08X}")
    print(f"plausible_interrupt_vectors={valid_vectors}")
    print(f"application_interrupt_vectors={application_vectors}")
    print(f"vector_table_register=0x{u32(data, VTOR_REGISTER_OFFSET):08X}")
    print(f"configured_vector_table_base=0x{APPLICATION_BASE:08X}")
    print(
        "update_status_callback="
        f"0x{APPLICATION_BASE + GM8136_CALLBACK_OFFSET:08X}"
    )
    print("update_status_source=GM8136")
    print("update_status_device_selector=7")
    print("update_status_payload_length=10")
    for label, address, _ in VERIFIED_DIRECT_FLASH_TARGETS:
        print(f"verified_direct_flash_target={label}\taddress=0x{address:08X}")
    print("packaged_pre_application_flash_span=OMITTED")
    print("pre_application_flash_contents=UNKNOWN")
    print("application_self_reflash_path=NOT_FOUND_IN_VERIFIED_DIRECT_CALL_MAP")
    print("bootloader_recovery_path=NOT_PROVEN")
    print("hardware_access=NONE")
    print("controller_write=NONE")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
