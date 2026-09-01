#!/usr/bin/env python3
"""Listen for simulator-only telemetry broadcast by the Android app."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(REPO_ROOT / "protocol" / "python"))

from xstar_protocol.simulator_bridge import (  # noqa: E402
    DEFAULT_PORT,
    MAX_FRAME_BYTES,
    SimulatorBridgeFrameError,
    decode_simulator_telemetry,
    open_simulator_socket,
    summarize_simulator_frame,
)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bind", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    parser.add_argument("--once", action="store_true", help="Exit after the first valid telemetry frame")
    parser.add_argument("--record", type=Path, help="Append validated frames to a JSONL file")
    args = parser.parse_args()

    receiver = open_simulator_socket(args.bind, args.port)
    print(f"Listening for X-Star simulator telemetry on udp://{args.bind}:{args.port}")
    with receiver:
        while True:
            payload, peer = receiver.recvfrom(MAX_FRAME_BYTES + 1)
            try:
                frame = decode_simulator_telemetry(payload)
            except SimulatorBridgeFrameError as error:
                print(f"Ignored datagram from {peer[0]}: {error}", file=sys.stderr)
                continue
            print(summarize_simulator_frame(frame, peer), flush=True)
            if args.record:
                args.record.parent.mkdir(parents=True, exist_ok=True)
                with args.record.open("a", encoding="utf-8") as output:
                    output.write(json.dumps(frame.raw, separators=(",", ":")) + "\n")
            if args.once:
                return 0


if __name__ == "__main__":
    raise SystemExit(main())
