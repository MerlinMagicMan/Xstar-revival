#!/usr/bin/env python3
"""Disassemble a decoded X-Star battery MCU image with python-msp430-tools.

This is an offline/read-only research helper. It does not communicate with or
flash hardware. The input is expected to be the output of
`decode_battery_firmware.py`.

Dependency:
    pip install python-msp430-tools

The absolute load address is not yet proven, so --base is explicit and should
be treated as a hypothesis until hardware/linker evidence resolves it.
"""
from __future__ import annotations

import argparse
import io
from pathlib import Path


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("image", type=Path, help="decoded Battery V5.21/V6.07 image")
    ap.add_argument("-o", "--output", type=Path, help="assembly listing output")
    ap.add_argument("--base", type=lambda s: int(s, 0), default=0xC800,
                    help="candidate load address; default 0xC800 is a research hypothesis")
    ap.add_argument("--classic", action="store_true",
                    help="decode as classic MSP430 instead of MSP430X")
    args = ap.parse_args()

    try:
        from msp430.memory import Memory, Segment
        from msp430.asm.disassemble import MSP430Disassembler
    except ImportError as exc:
        raise SystemExit(
            "python-msp430-tools is required; install it in the research venv"
        ) from exc

    data = args.image.read_bytes()
    memory = Memory()
    memory.append(Segment(args.base, data))
    output = io.StringIO()
    dis = MSP430Disassembler(memory, msp430x=not args.classic)
    dis.disassemble(output)
    text = output.getvalue()

    header = (
        f"; X-Star battery research disassembly\n"
        f"; input={args.image}\n"
        f"; base=0x{args.base:X}\n"
        f"; mode={'MSP430' if args.classic else 'MSP430X'}\n"
        f"; WARNING: base/MCU identification is not yet proven.\n\n"
    )
    text = header + text
    if args.output:
        args.output.write_text(text)
        print(f"output={args.output}")
    else:
        print(text, end="")


if __name__ == "__main__":
    main()
