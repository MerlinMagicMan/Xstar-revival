# X-Star Revival

**Modern software and hardware preservation for the Autel X-Star and X-Star Premium ecosystem.**

> Status: Research / Proof of Concept  
> Primary objective: Restore safe use of X-Star hardware on modern Android devices without modifying aircraft flight-controller firmware.

## Mission

The X-Star Revival project exists to keep functional X-Star aircraft flying after the original mobile software and replacement-parts ecosystem aged out.

The project has two parallel tracks:

1. **Software preservation** — modern Android ground-control software, protocol documentation, telemetry, FPV, diagnostics, logs, and eventually feature parity with Starlink.
2. **Hardware preservation** — battery architecture research, diagnostics, repair/rebuild documentation, parts interchange, and long-term service knowledge.

## Product Roadmap

The authoritative feature and product roadmap is [`docs/FEATURE-ROADMAP.md`](docs/FEATURE-ROADMAP.md).

It covers the full product vision: modern flight planning, Glass Cockpit, Vision Copilot, Subject Lock, Ghost Flight, 3D/property intelligence, flight simulation, Landing Assistant, visual obstacle/RTH capabilities, multi-aircraft support, aftermarket batteries, parts preservation, Revival Link/BYO remotes, AI flight analysis, phased safety gates, and long-term moonshots.

## Current Highest-Priority Lead

Autel's official public Mobile SDK sample contains explicit `X_STAR` and `PREMIUM` product routes, dedicated `XStarPremiumAircraft` modules, battery and flight-controller APIs, video callbacks, USB accessory handling and ARM64 build configuration.

Therefore the first implementation experiment is now:

> **Test the official Autel SDK as a read-only ARM64 X-Star Premium adapter before fully reconstructing the USB protocol.**

This does not replace the independent protocol goal. The intended architecture supports both:

```text
X-Star Revival App
        |
XStarPlatform interface
        |
        +-- OfficialAutelSdkAdapter
        |
        +-- OpenXStarAdapter
```

See [`docs/OFFICIAL-AUTEL-SDK-PATH.md`](docs/OFFICIAL-AUTEL-SDK-PATH.md) and [`software/android-sdk-probe/README.md`](software/android-sdk-probe/README.md).

## First Go/No-Go Milestone

Build a deliberately minimal Android engineering application that proves:

- USB connection to the X-Star Premium remote
- controller/product detection
- telemetry reception
- basic aircraft and battery state
- camera/video connection
- live H.264 FPV video

No autonomous flight commands are required for the first milestone.

If the official SDK is blocked by authentication, product whitelist, licensing or native compatibility, the fallback path remains:

- reconstruct Autel USB framing;
- expose MAVLink/Autel telemetry;
- recreate camera/event services; and
- decode H.264 using a modern Android pipeline.

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

Official Autel SDK source independently confirms X-Star/X-Star Premium product interfaces and video, battery, flight-controller, remote-controller, gimbal, DSP and mission modules.

## Research / Product Index

- [`docs/FEATURE-ROADMAP.md`](docs/FEATURE-ROADMAP.md) — authoritative feature roadmap and phased product strategy
- [`docs/RESEARCH-DOSSIER-2026-08.md`](docs/RESEARCH-DOSSIER-2026-08.md) — consolidated feasibility findings
- [`docs/OFFICIAL-AUTEL-SDK-PATH.md`](docs/OFFICIAL-AUTEL-SDK-PATH.md) — official SDK opportunity and decision tree
- [`docs/LEGACY-TABLET-PRESERVATION.md`](docs/LEGACY-TABLET-PRESERVATION.md) — golden-reference tablet capture procedure
- [`docs/RADIO-AND-TRANSPORT.md`](docs/RADIO-AND-TRANSPORT.md) — USB, proxy and RF architecture
- [`docs/FLIGHT-LOG-FORMAT.md`](docs/FLIGHT-LOG-FORMAT.md) — PX4-derived log research
- [`docs/ARTIFACT-INVENTORY.md`](docs/ARTIFACT-INVENTORY.md) — hashes, provenance and preservation policy
- [`hardware/battery/BQ3055-RESEARCH.md`](hardware/battery/BQ3055-RESEARCH.md) — smart-battery research and read-only validation plan
- [`docs/FEASIBILITY.md`](docs/FEASIBILITY.md) — initial go/no-go assessment

## Safety Boundary

Until explicitly promoted beyond the PoC stage:

- **Props off for all powered-aircraft bench testing.**
- The first Android probe is read-only and must not compile flight-control actions.
- Do not issue arbitrary arm, motor, takeoff, position or mission commands.
- Preserve factory RC authority and failsafe behavior.
- Do not flash firmware during initial protocol research.
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
│   ├── android/
│   └── android-sdk-probe/
├── hardware/
│   └── battery/
└── tools/
```

## Licensing Direction

The intended direction is:

- protocol specifications and interoperability tooling: open source
- research documentation: open
- polished consumer application: license to be determined

No Autel trademarks, artwork, proprietary binaries, firmware or substantial copied/decompiled implementation code should be distributed without established rights.

The official SDK's redistribution and commercial-use terms must be resolved before it becomes a shipping dependency.

## Project Principles

1. Interoperability, not firmware replacement.
2. Preserve physical-controller authority.
3. Read-only before write/control.
4. Document before automating.
5. Reimplement protocols cleanly.
6. Bench-test before flight-test.
7. Make preservation knowledge durable and portable.
8. Prefer transparent, reproducible research.
