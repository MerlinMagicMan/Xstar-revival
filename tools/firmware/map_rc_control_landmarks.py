#!/usr/bin/env python3
"""Locate controller/simulator landmarks in a decoded RC-PRO STM32 image.

The output is an address map for static analysis. The tool only reads a local
decoded image and never communicates with controller hardware.
"""

from __future__ import annotations

import argparse
import hashlib
import struct
from collections import defaultdict
from pathlib import Path

from decode_rc_firmware import APPLICATION_BASE, validate_stm32_image


LANDMARKS = (
    b"USB\0",
    b"CANSELKEY:%d",
    b"CANPHKEY:%d",
    b"CANRECKEY:%d",
    b"CANKNOB:%d",
    b"CANSETKEY:%d",
    b"INPUT:Calibrate Joysticks",
    b"INPUT:Enter Calibrate Joysticks",
    b"INPUT:Exit Calibrate Joysticks",
    b"RFMODE",
    b"CANMODE",
    b"DEBUGROCKER",
    b"DEBUGKEY",
    b"FLYSTICK[%d]",
    b"PhoneSet:CanMode[%d]",
    b"App-controlled stick disabled",
    b"SIMULATED FLIGHT",
    b"Use the command sticks",
    b"Command sticks error",
)


KNOWN_CONTROL_PATHS = {
    "3a7180278ed9e4046ed57d188e09d5168ae8b61c29381c4d9869e83f258ae718": (
        "X3P RC-PRO V1.0.1.5",
        (
            ("physical_button_scan", 0x8F9C),
            ("axis_sampler", 0xA478),
            ("selector_0x210_callback", 0x147C4),
            ("selector_0x81_callback", 0x14BD8),
            ("aa_frame_builder", 0x15058),
            ("a5_usart1_frame_builder", 0x150E0),
            ("link_state_machine", 0x6204),
            ("can1_mailbox_writer", 0x57D0),
            ("can1_8byte_chunker", 0x194B0),
            ("common_dispatcher", 0x19558),
            ("usart1_tx_enqueue", 0x1B56C),
            ("main_control_update", 0x1D074),
        ),
    ),
}

KNOWN_PERIPHERALS = {
    "3a7180278ed9e4046ed57d188e09d5168ae8b61c29381c4d9869e83f258ae718": (
        ("usart1_data_register", 0x40013804),
        ("can1_register_base", 0x40006400),
    ),
}


def find_all(data: bytes, needle: bytes) -> list[int]:
    offsets: list[int] = []
    start = 0
    while True:
        offset = data.find(needle, start)
        if offset < 0:
            return offsets
        offsets.append(offset)
        start = offset + 1


def thumb_adr_references(data: bytes, base: int) -> dict[int, list[int]]:
    """Return targets of 16-bit Thumb ADR instructions and their addresses."""
    references: dict[int, list[int]] = defaultdict(list)
    for offset in range(0, len(data) - 1, 2):
        instruction = struct.unpack_from("<H", data, offset)[0]
        if instruction & 0xF800 != 0xA000:
            continue
        address = base + offset
        displacement = (instruction & 0xFF) * 4
        target = ((address + 4) & ~3) + displacement
        references[target].append(address)
    return references


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("firmware", type=Path, help="decoded RC-PRO STM32 image")
    parser.add_argument(
        "--base",
        type=lambda value: int(value, 0),
        default=APPLICATION_BASE,
        help=f"application base address (default: 0x{APPLICATION_BASE:08X})",
    )
    args = parser.parse_args()

    try:
        data = args.firmware.read_bytes()
        initial_sp, reset_vector, valid_vectors, application_vectors = (
            validate_stm32_image(data, args.base)
        )
    except (OSError, ValueError) as exc:
        parser.exit(1, f"error: {exc}\n")
    references = thumb_adr_references(data, args.base)

    print(f"file={args.firmware}")
    print(f"base=0x{args.base:08X}")
    print(f"initial_stack_pointer=0x{initial_sp:08X}")
    print(f"reset_vector=0x{reset_vector:08X}")
    print(f"reset_file_offset=0x{(reset_vector & ~1) - args.base:X}")
    print(f"plausible_interrupt_vectors={valid_vectors}")
    print(f"application_interrupt_vectors={application_vectors}")
    digest = hashlib.sha256(data).hexdigest()
    known = KNOWN_CONTROL_PATHS.get(digest)
    if known:
        image_name, control_paths = known
        print(f"known_image={image_name}")
        for label, offset in control_paths:
            print(
                f"control_path={label}\taddress=0x{args.base + offset:08X}"
                f"\tfile_offset=0x{offset:X}"
            )
        for label, address in KNOWN_PERIPHERALS[digest]:
            print(f"peripheral={label}\taddress=0x{address:08X}")
    else:
        print("known_image=UNKNOWN")
    for landmark in LANDMARKS:
        label = landmark.rstrip(b"\0").decode("ascii")
        offsets = find_all(data, landmark)
        if not offsets:
            print(f"landmark={label}\tNOT_FOUND")
            continue
        for offset in offsets:
            address = args.base + offset
            callers = ",".join(f"0x{value:08X}" for value in references.get(address, ()))
            print(
                f"landmark={label}\taddress=0x{address:08X}"
                f"\tthumb_adr_refs={callers or '-'}"
            )


if __name__ == "__main__":
    main()
