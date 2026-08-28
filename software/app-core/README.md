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

## Official SDK read-only bridge

`AutelSdkBridge` is a proprietary-type-free boundary for an optional Android binding around Autel's legacy AAR. Its public surface contains only initialization, discovery, disconnection, passive refresh, typed observation callbacks, and receive-only H.264 frames.

The bridge normalizes:

- product and component availability;
- documented battery mV/mA/mAh values;
- GPS, home, altitude, velocity, ultrasonic height, and attitude;
- remote, image-link/RF, gimbal, and R12 state;
- component versions and warnings; and
- H.264 payload length, keyframe flag, callback timestamp, and frame statistics.

Values whose SDK unit is not established remain unknown rather than being guessed. The remote control-menu array remains opaque. The app-facing adapter exposes raw H.264 through `H264VideoSource`, which has no write method.

The standalone module pins its own Kotlin plugin and repositories, so its safety and normalization tests can run independently with `gradle test`.

## Deterministic passive capture

`H264CaptureWriter` stores untouched receive-only callback payloads behind hard duration and byte ceilings. It can retain bounded, clearly standard Annex-B SPS/PPS setup before the first SDK-marked keyframe; opaque or picture payloads before synchronization are not guessed. Its JSONL index records offsets, valid lengths, keyframe/configuration flags, monotonic elapsed time, and the raw SDK timestamp with source-defined units.

`SanitizedTelemetryCaptureWriter` produces a deterministic normalized-state JSONL companion. Its allowlist excludes position coordinates, opaque controller fields, identifiers, app keys, warning messages, and diagnostic notes. Neither writer has a transport write or aircraft-control path.

## Independent MAVLink decoder

`StandardMavlinkDecoder` incrementally reassembles MAVLink v1/v2 frames from arbitrary transport chunks. It only maps CRC-verified standard messages whose fields have a direct normalized meaning:

- `HEARTBEAT` (generic vehicle type and armed flag; custom flight mode stays unknown)
- `GPS_RAW_INT` and `GLOBAL_POSITION_INT`
- `ATTITUDE`
- `SYS_STATUS` and primary-pack `BATTERY_STATUS`

Unknown/custom messages remain opaque and are visible only through diagnostic counters. The decoder never emits protocol requests or flight-control writes, and `OpenXStarTransport` intentionally has no write method.
