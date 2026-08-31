#!/usr/bin/env python3
"""Structural differential analyzer for decoded X-Star battery MCU images.

Read-only. This tool does not flash hardware or modify source images.

It is intentionally architecture-light: it inventories long moved/equal regions,
MSP430/MSP430X instruction signatures, erased-flash runs, likely code/data anchors,
and candidate Smart Battery command constants before a full disassembler is wired in.
"""
from __future__ import annotations

import argparse
import collections
import difflib
import hashlib
import struct
from pathlib import Path

RET = b"\x30\x41"               # canonical MSP430 RET (MOV @SP+,PC)
MSP430X_EXT_MASK = 0xF800
MSP430X_EXT_VALUE = 0x1800

SMART_BATTERY_COMMANDS = {
    0x00: "ManufacturerAccess", 0x08: "Temperature", 0x09: "Voltage",
    0x0A: "Current", 0x0B: "AverageCurrent", 0x0C: "MaxError",
    0x0D: "RelativeSOC", 0x0E: "AbsoluteSOC", 0x0F: "RemainingCapacity",
    0x10: "FullChargeCapacity", 0x14: "ChargingCurrent",
    0x15: "ChargingVoltage", 0x16: "BatteryStatus", 0x17: "CycleCount",
    0x18: "DesignCapacity", 0x19: "DesignVoltage", 0x1B: "ManufactureDate",
    0x1C: "SerialNumber", 0x20: "ManufacturerName", 0x21: "DeviceName",
    0x22: "DeviceChemistry", 0x23: "ManufacturerData",
}


def words(data: bytes):
    for off in range(0, len(data) - 1, 2):
        yield off, struct.unpack_from("<H", data, off)[0]


def erased_runs(data: bytes, minimum: int = 16):
    out = []
    start = None
    for i, value in enumerate(data + b"\x00"):
        if value == 0xFF and start is None:
            start = i
        elif value != 0xFF and start is not None:
            if i - start >= minimum:
                out.append((start, i - start))
            start = None
    return sorted(out, key=lambda item: item[1], reverse=True)


def moved_matches(old: bytes, new: bytes, minimum: int = 24):
    matcher = difflib.SequenceMatcher(None, old, new, autojunk=False)
    return sorted(
        (m for m in matcher.get_matching_blocks() if m.size >= minimum),
        key=lambda m: m.size,
        reverse=True,
    )


def signature_counts(data: bytes):
    ret_offsets = [i for i in range(0, len(data)-1, 2) if data[i:i+2] == RET]
    ext_offsets = [off for off, word in words(data) if (word & MSP430X_EXT_MASK) == MSP430X_EXT_VALUE]
    return ret_offsets, ext_offsets


def command_immediates(data: bytes):
    # Architecture-neutral heuristic: list aligned 16-bit literal occurrences of
    # Smart Battery command values. These are leads, not proof of use.
    hits = collections.defaultdict(list)
    for off, word in words(data):
        if word in SMART_BATTERY_COMMANDS:
            hits[word].append(off)
    return hits


def summarize(label: str, data: bytes):
    print(f"[{label}]")
    print(f"size={len(data)} sha256={hashlib.sha256(data).hexdigest()}")
    ret, ext = signature_counts(data)
    print(f"msp430_ret_signature_count={len(ret)}")
    print(f"msp430x_extension_word_candidates={len(ext)}")
    print("largest_erased_runs=" + ",".join(f"0x{o:X}:{n}" for o,n in erased_runs(data)[:8]))
    hits = command_immediates(data)
    for cmd in sorted(hits):
        print(f"smart_battery_literal_0x{cmd:02X}_{SMART_BATTERY_COMMANDS[cmd]}=" +
              ",".join(f"0x{x:X}" for x in hits[cmd][:16]))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("old", type=Path)
    ap.add_argument("new", type=Path)
    ap.add_argument("--min-match", type=int, default=24)
    args = ap.parse_args()

    old = args.old.read_bytes(); new = args.new.read_bytes()
    summarize("old", old); summarize("new", new)

    print("[moved/common regions]")
    matches = moved_matches(old, new, args.min_match)
    for m in matches[:40]:
        print(f"old=0x{m.a:X} new=0x{m.b:X} size={m.size} delta={m.b-m.a:+d}")

    if len(old) == len(new):
        changed = sum(a != b for a,b in zip(old,new))
        print(f"changed_bytes={changed}/{len(old)} ({changed/len(old):.2%})")

if __name__ == "__main__":
    main()
