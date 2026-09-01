#!/usr/bin/env python3
"""Static checks for the Unreal simulator scaffold before Engine compilation is available."""

from __future__ import annotations

import json
from pathlib import Path

UNREAL_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = UNREAL_ROOT.parents[1]
SOURCE_ROOT = UNREAL_ROOT / "Source" / "XStarSimulator"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(message)


def main() -> int:
    project = json.loads((UNREAL_ROOT / "XStarSimulator.uproject").read_text(encoding="utf-8"))
    require(project.get("EngineAssociation") == "5.8", "Unreal project must target Engine 5.8")
    required = {
        "XStarSimulator.Build.cs",
        "XStarSimulator.cpp",
        "XStarTelemetryReceiverComponent.h",
        "XStarTelemetryReceiverComponent.cpp",
        "XStarDronePawn.h",
        "XStarDronePawn.cpp",
        "XStarSimulatorGameMode.h",
        "XStarSimulatorGameMode.cpp",
    }
    missing = sorted(name for name in required if not (SOURCE_ROOT / name).is_file())
    require(not missing, f"Unreal simulator scaffold is missing: {', '.join(missing)}")

    kotlin_protocol = (
        REPO_ROOT
        / "software/app-core/src/main/kotlin/io/xstarrevival/core/sim/SimulatorBridgeProtocol.kt"
    ).read_text(encoding="utf-8")
    python_protocol = (REPO_ROOT / "protocol/python/xstar_protocol/simulator_bridge.py").read_text(encoding="utf-8")
    receiver = (SOURCE_ROOT / "XStarTelemetryReceiverComponent.cpp").read_text(encoding="utf-8")
    receiver_header = (SOURCE_ROOT / "XStarTelemetryReceiverComponent.h").read_text(encoding="utf-8")
    all_source = "\n".join(path.read_text(encoding="utf-8") for path in SOURCE_ROOT.glob("*.*"))

    require("SIMULATOR_BRIDGE_PROTOCOL_VERSION = 1" in kotlin_protocol, "Kotlin protocol version drift")
    require("SIMULATOR_BRIDGE_UDP_PORT = 46_000" in kotlin_protocol, "Kotlin simulator port drift")
    require("PROTOCOL_VERSION = 1" in python_protocol, "Python protocol version drift")
    require("DEFAULT_PORT = 46_000" in python_protocol, "Python simulator port drift")
    require("constexpr int32 ProtocolVersion = 1" in receiver, "Unreal protocol version drift")
    require("ListenPort = 46000" in receiver_header, "Unreal port drift")
    require('TryGetBoolField(TEXT("simulated")' in receiver, "Unreal receiver must require the simulated marker")

    forbidden = ("SendTo(", "SendTo ", "Autel", "UsbManager", "XStarCommand", "CommandDispatcher")
    discovered = [token for token in forbidden if token in all_source]
    require(not discovered, f"Unreal visualizer contains forbidden output/hardware references: {discovered}")
    print("Unreal simulator scaffold audit passed (UE 5.8, protocol v1, UDP 46000, receive-only visualizer).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
