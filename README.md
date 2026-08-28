# X-Star Revival

**Modern software and hardware preservation for the Autel X-Star and X-Star Premium ecosystem.**

> Status: Research / Proof of Concept  
> Primary objective: Restore safe use of X-Star hardware on modern Android devices without modifying aircraft flight-controller firmware.

## Mission

The X-Star Revival project exists to keep functional X-Star aircraft flying after the original mobile software and replacement-parts ecosystem aged out.

The project has two parallel tracks:

1. **Software preservation** — modern Android ground-control software, protocol documentation, telemetry, FPV, diagnostics, logs, and eventually feature parity with Starlink.
2. **Hardware preservation** — battery architecture research, diagnostics, repair/rebuild documentation, parts interchange, and long-term service knowledge.

## First Go/No-Go Milestone

Build a deliberately minimal Android engineering application that proves:

- USB connection to the X-Star Premium remote
- controller detection
- MAVLink/Autel protocol transport
- heartbeat/telemetry reception
- basic aircraft state
- camera connection
- live H.264 FPV video

No autonomous flight commands are required for the first milestone.

## Initial Evidence

Static inspection of the legacy Starlink APK shows strong evidence of:

- `com.MAVLink.*`
- `com.autel.sdk.AutelNet.AutelMavlinkCore.*`
- flight controller, remote controller, mission, gimbal and camera modules
- native MAVLink transport functions
- USB packet framing functions
- RTSP/H.264 video transport
- HTTP camera/event endpoints
- an Android-side USB/network proxy layer

See [`docs/FEASIBILITY.md`](docs/FEASIBILITY.md).

## Safety Boundary

Until explicitly promoted beyond the PoC stage:

- **Props off for bench testing.**
- Do not issue arbitrary arm, motor, takeoff, position, or mission commands.
- Preserve factory RC authority and failsafe behavior.
- Treat battery repair/rebuild work as high-energy lithium battery work requiring proper equipment and competent handling.
- Do not publish procedures that bypass battery protection without validated safeguards.

See [`SAFETY.md`](SAFETY.md).

## Repository Layout

```text
xstar-revival/
├── README.md
├── SAFETY.md
├── ROADMAP.md
├── docs/
├── research/
├── protocol/
├── software/
│   └── android/
├── hardware/
│   └── battery/
└── tools/
```

## Licensing Direction

The intended direction is:

- protocol specifications and interoperability tooling: open source
- research documentation: open
- polished consumer application: license to be determined

No Autel trademarks, artwork, proprietary binaries, or substantial copied/decompiled implementation code should be distributed.

## Project Principles

1. Interoperability, not firmware replacement.
2. Preserve physical-controller authority.
3. Document before automating.
4. Reimplement protocols cleanly.
5. Bench-test before flight-test.
6. Make preservation knowledge durable and portable.
7. Prefer transparent, reproducible research.
