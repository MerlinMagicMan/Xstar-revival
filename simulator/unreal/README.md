# X-Star Unreal Simulator

This Unreal Engine 5.8 project is the high-fidelity visual layer for the isolated Android flight simulator. The Android app remains authoritative for commands, missions, failures, warnings, and deterministic tests. Unreal receives explicitly simulated telemetry over UDP port `46000` and cannot send data to USB, the Autel SDK, a radio, or an aircraft.

## First launch

1. Finish installing Unreal Engine 5.8 and the full Xcode application, then select that Xcode as
   the active developer directory. UnrealBuildTool currently requires a complete macOS 15.2 SDK;
   Apple's standalone Command Line Tools are not sufficient.
2. Run `tools/run_simulator_bridge_receiver.py --once` from the repository root to verify Android-to-Mac telemetry before opening Unreal.
3. Open `simulator/unreal/XStarSimulator.uproject`.
4. Allow Unreal to compile the `XStarSimulator` C++ module.
5. Create and save a basic level, set `XStarSimulatorGameMode`, place an `XStarDronePawn`, and press Play.
6. On Android, choose **Flight Simulator** and connect. The pawn will follow simulated latitude, longitude, altitude, attitude, gimbal, battery, and controller telemetry.

The initial pawn intentionally uses an engine cube so the networking and flight loop can be verified without downloading third-party assets. Replace it with the final X-Star model after the receiver compiles and live telemetry is visible.

## Protocol

- Name: `xstar-simulator`
- Version: `1`
- Type: `telemetry`
- Transport: UDP broadcast and Android emulator host
- Port: `46000`
- Maximum datagram: 64 KiB
- Mandatory safety marker: `"simulated": true`

The Unreal receiver rejects incompatible versions, command frames, frames without the safety marker, invalid JSON, and oversized datagrams.
