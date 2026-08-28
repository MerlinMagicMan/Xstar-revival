# X-Star Revival App Core

This module defines the product-facing platform contract independently of Autel SDK, USB transport, and Android UI.

## Goal

The application consumes one interface:

```text
XStarPlatform
```

Implementations can be swapped without changing UI/domain logic:

```text
MockXStarPlatform
ReplayXStarPlatform
OfficialAutelSdkAdapter
OpenXStarPlatform
```

## Rules

- Domain state is immutable.
- Read-only capabilities are modeled separately from future control capabilities.
- No UI code parses MAVLink or USB packets.
- No protocol implementation depends on Android views.
- Unknown values stay unknown; do not invent defaults.
- Safety-critical write/control APIs are intentionally absent from this phase.

## Initial State Surface

- connection state
- aircraft identity
- battery pack and individual cell state
- GPS / satellites
- attitude
- altitude and speeds
- home position
- remote-controller status
- camera/video status
- warnings
- diagnostics

The mock implementation produces deterministic changing telemetry so UI and state handling can be developed without hardware.

## Independent MAVLink decoder

`StandardMavlinkDecoder` incrementally reassembles MAVLink v1/v2 frames from arbitrary transport chunks. It only maps CRC-verified standard messages whose fields have a direct normalized meaning:

- `HEARTBEAT` (generic vehicle type and armed flag; custom flight mode stays unknown)
- `GPS_RAW_INT` and `GLOBAL_POSITION_INT`
- `ATTITUDE`
- `SYS_STATUS` and primary-pack `BATTERY_STATUS`

Unknown/custom messages remain opaque and are visible only through diagnostic counters. The decoder never emits protocol requests or flight-control writes, and `OpenXStarTransport` intentionally has no write method.
