#!/usr/bin/env python3
"""Read-only structural analysis for decoded X-Star battery MCU images.

Input must already be de-obfuscated with decode_battery_firmware.py.
This tool does not flash hardware or alter source files.

It reports features useful for identifying the MCU/linker layout without
pretending that an address map is proven before the exact MSP430 part is known.
"""

from __future__ import annotations

import argparse
import collections
import hashlib
from pathlib import Path

RET_WORD = 0x4130  # canonical MSP430 MOV @SP+,PC / RET encoding
PAIR_MARKER = 0x010C


def words(data: bytes):
    for off in range(0, len(data) - 1, 2):
        yield off, int.from_bytes(data[off:off + 2], "little")


def ff_runs(data: bytes):
    out = []
    start = None
    for i, value in enumerate(data):
        if value == 0xFF and start is None:
            start = i
        elif value != 0xFF and start is not None:
            out.append((start, i - start))
            start = None
    if start is not None:
        out.append((start, len(data) - start))
    return sorted(out, key=lambda item: item[1], reverse=True)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("image", type=Path)
    args = ap.parse_args()

    data = args.image.read_bytes()
    ws = list(words(data))
    values = [value for _, value in ws]

    print(f"file={args.image}")
    print(f"length=0x{len(data):X} ({len(data)})")
    print(f"sha256={hashlib.sha256(data).hexdigest()}")
    print(f"ret_0x4130_count={values.count(RET_WORD)}")
    print(f"pair_marker_0x010c_count={values.count(PAIR_MARKER)}")

    if len(data) >= 2:
        print(f"final_word_le=0x{int.from_bytes(data[-2:], 'little'):04X}")

    runs = ff_runs(data)
    for index, (offset, length) in enumerate(runs[:8]):
        print(f"ff_run_{index}=offset:0x{offset:X},length:0x{length:X}({length})")

    # The two recovered battery generations both have a striking 50-byte tail:
    # twelve 4-byte records beginning with 0x010C followed by one final word.
    if len(data) >= 50:
        tail = data[-50:]
        records = []
        record_shape = True
        for off in range(0, 48, 4):
            marker = int.from_bytes(tail[off:off + 2], "little")
            value = int.from_bytes(tail[off + 2:off + 4], "little")
            records.append((marker, value))
            if marker != PAIR_MARKER:
                record_shape = False
        print(f"tail_50_record_shape={record_shape}")
        if record_shape:
            print("tail_records=" + ",".join(f"010C:{value:04X}" for _, value in records))
            print(f"tail_final_word=0x{int.from_bytes(tail[-2:], 'little'):04X}")

    counter = collections.Counter(data)
    print(f"byte_ff_count={counter[0xFF]}")
    print(f"byte_00_count={counter[0x00]}")


if __name__ == "__main__":
    main()
