# App State Architecture

## Boundary

The user interface never speaks USB, MAVLink, RTSP, HTTP camera protocols, or Autel SDK objects directly.

```text
UI
 |
 v
XStarPlatform
 |
 +-- MockXStarPlatform
 +-- ReplayXStarPlatform
 +-- OfficialAutelSdkAdapter
 +-- OpenXStarPlatform
 |
 v
normalized XStarEvent stream
 |
 v
XStarReducer
 |
 v
immutable XStarState
```

## Why this boundary matters

The X-Star Revival project is expected to move through several transport implementations while protocol knowledge improves. The product should not need to be rewritten when that happens.

A battery percentage from the official Autel SDK and a battery percentage decoded from an independent MAVLink message must become the same domain event/state.

## Safety boundary

`XStarPlatform` is read-only in this phase. It exposes only:

- connect
- disconnect
- refresh
- state observation

Flight commands do not belong on this interface.

If control is introduced later, it must use a separate safety-reviewed capability interface so a telemetry-only implementation cannot accidentally inherit motor/mission authority.

## Unknown values

Telemetry fields use nullable values intentionally. Zero is meaningful in flight data and cannot be used to represent unknown information.

Examples:

```text
altitudeM = null   -> not received/known
altitudeM = 0.0    -> received and actually zero
armed = null       -> state unknown
armed = false      -> confirmed disarmed
```

## Replay development

Captured hardware traffic will be decoded into normalized events. `ReplayXStarPlatform` then feeds those events through the same reducer used by live adapters.

This allows:

- deterministic UI reproduction of hardware sessions;
- regression tests from sanitized captures;
- protocol-decoder changes without needing the aircraft present;
- side-by-side comparison of official-SDK and open-protocol behavior.

## Current normalized state

- connection lifecycle
- product/firmware identity
- armed/flight mode status
- pack battery state
- individual cell voltages and cell delta
- GPS/fix/satellites
- position/home position
- altitude/ground/vertical speed
- roll/pitch/yaw
- remote connection/signal/battery
- camera mode/recording state
- video codec/resolution/frame counters/bitrate
- warnings
- diagnostic counters and notes

The model will expand only when an adapter or real capture provides evidence for additional fields.
