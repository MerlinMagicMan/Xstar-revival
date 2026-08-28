#!/usr/bin/env python3
"""Offline pattern analysis for passive X-Star USB captures."""

from __future__ import annotations

import argparse
import collections
import hashlib
import json
from pathlib import Path


def offsets(data: bytes, needle: bytes, limit: int = 1000) -> list[int]:
    found: list[int] = []
    start = 0
    while len(found) < limit:
        i = data.find(needle, start)
        if i < 0:
            break
        found.append(i)
        start = i + 1
    return found


def repeated_prefixes(data: bytes, width: int, stride: int) -> list[dict]:
    if width <= 0 or stride <= 0:
        return []
    counter = collections.Counter(data[i:i + width] for i in range(0, max(0, len(data) - width + 1), stride))
    return [
        {"hex": key.hex(), "count": count}
        for key, count in counter.most_common(20)
        if count > 1
    ]


def byte_frequency(data: bytes) -> list[dict]:
    count = collections.Counter(data)
    return [{"byte": f"0x{k:02x}", "count": v} for k, v in count.most_common(32)]


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("capture", type=Path)
    p.add_argument("--stride", type=int, default=64, help="candidate packet stride for repeated-prefix scan")
    p.add_argument("--prefix-width", type=int, default=8)
    p.add_argument("--json", type=Path)
    args = p.parse_args()

    data = args.capture.read_bytes()
    signatures = {
        "mavlink_v1_fe": offsets(data, b"\xfe"),
        "mavlink_v2_fd": offsets(data, b"\xfd"),
        "h264_start4": offsets(data, b"\x00\x00\x00\x01"),
        "h264_start3": offsets(data, b"\x00\x00\x01"),
        "jpeg_soi": offsets(data, b"\xff\xd8\xff"),
        "http": offsets(data, b"HTTP/"),
        "rtsp": offsets(data, b"RTSP/"),
    }

    report = {
        "file": str(args.capture),
        "size": len(data),
        "sha256": hashlib.sha256(data).hexdigest(),
        "signature_counts": {k: len(v) for k, v in signatures.items()},
        "signature_offsets_first_1000": signatures,
        "byte_frequency_top32": byte_frequency(data),
        "repeated_prefixes": repeated_prefixes(data, args.prefix_width, args.stride),
        "notes": [
            "0xFE/0xFD hits are candidates only; random binary data can contain these bytes.",
            "H.264 start codes are stronger evidence when followed by plausible NAL unit types.",
            "Use multiple controlled captures and compare deltas before assigning protocol semantics.",
        ],
    }

    text = json.dumps(report, indent=2)
    print(text)
    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(text + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
