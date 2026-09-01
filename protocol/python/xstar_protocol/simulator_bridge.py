"""Receive and validate the simulator-only UDP telemetry protocol."""

from __future__ import annotations

import json
import socket
from dataclasses import dataclass
from typing import Any

PROTOCOL_NAME = "xstar-simulator"
PROTOCOL_VERSION = 1
DEFAULT_PORT = 46_000
MAX_FRAME_BYTES = 64 * 1024


class SimulatorBridgeFrameError(ValueError):
    """Raised when a datagram is not a supported simulator telemetry frame."""


@dataclass(frozen=True)
class SimulatorTelemetryFrame:
    sequence: int
    emitted_at_epoch_ms: int
    aircraft: dict[str, Any]
    controller: dict[str, Any]
    battery: dict[str, Any]
    camera: dict[str, Any]
    warnings: tuple[dict[str, Any], ...]
    raw: dict[str, Any]


def decode_simulator_telemetry(payload: bytes | str) -> SimulatorTelemetryFrame:
    if isinstance(payload, bytes):
        if len(payload) > MAX_FRAME_BYTES:
            raise SimulatorBridgeFrameError("simulator telemetry frame exceeds 64 KiB")
        try:
            text = payload.decode("utf-8")
        except UnicodeDecodeError as exc:
            raise SimulatorBridgeFrameError("simulator telemetry is not UTF-8") from exc
    else:
        text = payload
    try:
        raw = json.loads(text)
    except json.JSONDecodeError as exc:
        raise SimulatorBridgeFrameError("simulator telemetry is not valid JSON") from exc
    if not isinstance(raw, dict):
        raise SimulatorBridgeFrameError("simulator telemetry root must be an object")
    if raw.get("protocol") != PROTOCOL_NAME:
        raise SimulatorBridgeFrameError("unexpected simulator protocol name")
    if raw.get("version") != PROTOCOL_VERSION:
        raise SimulatorBridgeFrameError("unsupported simulator protocol version")
    if raw.get("type") != "telemetry" or raw.get("simulated") is not True:
        raise SimulatorBridgeFrameError("frame is not explicitly simulator telemetry")
    sequence = raw.get("sequence")
    emitted = raw.get("emittedAtEpochMs")
    if isinstance(sequence, bool) or not isinstance(sequence, int) or sequence < 0:
        raise SimulatorBridgeFrameError("sequence must be a non-negative integer")
    if isinstance(emitted, bool) or not isinstance(emitted, int) or emitted < 0:
        raise SimulatorBridgeFrameError("emittedAtEpochMs must be a non-negative integer")
    objects = {}
    for name in ("aircraft", "controller", "battery", "camera"):
        value = raw.get(name)
        if not isinstance(value, dict):
            raise SimulatorBridgeFrameError(f"{name} must be an object")
        objects[name] = value
    warnings = raw.get("warnings")
    if not isinstance(warnings, list) or any(not isinstance(item, dict) for item in warnings):
        raise SimulatorBridgeFrameError("warnings must be an array of objects")
    return SimulatorTelemetryFrame(
        sequence=sequence,
        emitted_at_epoch_ms=emitted,
        aircraft=objects["aircraft"],
        controller=objects["controller"],
        battery=objects["battery"],
        camera=objects["camera"],
        warnings=tuple(warnings),
        raw=raw,
    )


def open_simulator_socket(bind: str = "0.0.0.0", port: int = DEFAULT_PORT) -> socket.socket:
    receiver = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    receiver.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    receiver.bind((bind, port))
    return receiver


def summarize_simulator_frame(frame: SimulatorTelemetryFrame, peer: tuple[str, int]) -> str:
    aircraft = frame.aircraft
    controller = frame.controller
    return (
        f"seq={frame.sequence} from={peer[0]} phase={aircraft.get('phase')} "
        f"alt={_number(aircraft.get('altitudeM'))}m "
        f"yaw={_number(aircraft.get('yawDeg'))}deg "
        f"sticks={_number(controller.get('throttle'))}/"
        f"{_number(controller.get('yaw'))}/"
        f"{_number(controller.get('pitch'))}/"
        f"{_number(controller.get('roll'))} warnings={len(frame.warnings)}"
    )


def _number(value: Any) -> str:
    return f"{value:.2f}" if isinstance(value, (int, float)) else "—"
