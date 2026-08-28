from __future__ import annotations

import argparse
import json
from pathlib import Path

from .mavlink import scan_mavlink, summarize
from .signatures import scan_signatures


def analyze(data: bytes) -> dict:
    frames = scan_mavlink(data)
    signatures = scan_signatures(data)
    return {
        "bytes": len(data),
        "mavlink": summarize(frames),
        "mavlink_frames": [
            {
                "offset": f.offset,
                "version": f.version,
                "payload_length": f.payload_length,
                "sequence": f.sequence,
                "system_id": f.system_id,
                "component_id": f.component_id,
                "message_id": f.message_id,
                "signed": f.signed,
                "frame_length": f.frame_length,
            }
            for f in frames
        ],
        "signatures": [
            {"kind": h.kind, "offset": h.offset, "detail": h.detail}
            for h in signatures
        ],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Analyze an X-Star USB capture offline")
    parser.add_argument("capture", type=Path)
    parser.add_argument("--pretty", action="store_true")
    args = parser.parse_args()

    result = analyze(args.capture.read_bytes())
    print(json.dumps(result, indent=2 if args.pretty else None, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
