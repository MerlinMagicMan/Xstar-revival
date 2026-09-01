import json

import pytest

from xstar_protocol.simulator_bridge import (
    SimulatorBridgeFrameError,
    decode_simulator_telemetry,
    summarize_simulator_frame,
)


def telemetry_payload(**overrides):
    frame = {
        "protocol": "xstar-simulator",
        "version": 1,
        "type": "telemetry",
        "simulated": True,
        "sequence": 7,
        "emittedAtEpochMs": 1000,
        "aircraft": {"phase": "FLYING", "altitudeM": 12.5, "yawDeg": 90.0},
        "controller": {"throttle": 0.2, "yaw": 0.0, "pitch": 0.4, "roll": -0.1},
        "battery": {"percent": 80},
        "camera": {"recording": False},
        "warnings": [],
    }
    frame.update(overrides)
    return json.dumps(frame).encode()


def test_decodes_versioned_explicit_simulator_frame():
    decoded = decode_simulator_telemetry(telemetry_payload())
    assert decoded.sequence == 7
    assert decoded.aircraft["phase"] == "FLYING"
    assert "alt=12.50m" in summarize_simulator_frame(decoded, ("192.168.1.4", 50000))


@pytest.mark.parametrize(
    "override",
    [
        {"simulated": False},
        {"version": 2},
        {"type": "command"},
        {"aircraft": []},
        {"sequence": True},
        {"emittedAtEpochMs": True},
    ],
)
def test_rejects_non_simulator_or_incompatible_frames(override):
    with pytest.raises(SimulatorBridgeFrameError):
        decode_simulator_telemetry(telemetry_payload(**override))


def test_rejects_invalid_json_and_oversized_datagrams():
    with pytest.raises(SimulatorBridgeFrameError):
        decode_simulator_telemetry(b"not-json")
    with pytest.raises(SimulatorBridgeFrameError):
        decode_simulator_telemetry(b"x" * (64 * 1024 + 1))
