# X-Star Revival Android App

This is the product-facing Android application, currently running entirely against `MockXStarPlatform`.

## Current capabilities

- connect/disconnect lifecycle
- aircraft/product summary
- live simulated battery telemetry
- four individual cell voltages and pack delta
- GPS/satellite/altitude/speed status
- roll/pitch/yaw
- remote-controller status
- camera/video status and frame counter
- diagnostic counters

The mock backend changes telemetry continuously so lifecycle/state rendering can be developed and tested without X-Star hardware.

## Build

From this directory:

```bash
./gradlew :app:assembleDebug
```

The project includes `../app-core` as the `:appCore` module.

## Telemetry sources and cockpit

The app can switch at runtime between the changing X-Star mock and a synthetic, timestamped MAVLink byte capture. Replay bytes pass through `CaptureReplayTransport`, `OpenXStarPlatformAdapter`, and `StandardMavlinkDecoder`; the UI does not receive pre-normalized fixture events.

Replay controls support play, pause, restart, 0.5×/1×/2× speed, progress, stream completion, and heartbeat-staleness display.

The Cockpit / FPV screen renders its telemetry HUD from `XStarState`. In MAVLink replay mode, an original raw H.264 Annex-B fixture is split by the app-core scanner and decoded to a `TextureView` with Android `MediaCodec`, exercising real AVC pixels beneath the Compose HUD. The clip is explicitly marked synthetic and is not X-Star camera footage. Future receive-only camera bytes can replace the fixture without changing the HUD or decoder boundary; Autel USB/channel framing remains deliberately unspecified.

## Adapter plan

The ViewModel owns an `XStarPlatform` only. Moving to live hardware should be a dependency-selection change rather than a UI rewrite:

```kotlin
private val platform: XStarPlatform = MockXStarPlatform(viewModelScope)
```

becomes either:

```text
OfficialAutelSdkAdapter
```

or:

```text
OpenXStarPlatform
```

when those adapters are ready.

The official bridge's allowed observations and compile-time forbidden control calls are defined in the [X-Star SDK Capability Matrix](../../docs/XSTAR-SDK-CAPABILITY-MATRIX.md). Live SDK work must preserve that read-only boundary.

App core now provides a typed `AutelSdkBridge` observation contract and `H264VideoSource`. The Android AAR binding can feed documented SDK callbacks into that seam without placing proprietary SDK types in the UI or domain model. The remaining hardware-specific step is implementing that binding against a locally supplied official AAR and authenticated app key.

## Safety

There are no flight controls in this application. Current actions are connect, disconnect and read-only refresh only.
