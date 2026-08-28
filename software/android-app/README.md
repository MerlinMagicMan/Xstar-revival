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

The Cockpit / FPV screen renders an artificial horizon and telemetry HUD from `XStarState`. Its scene is explicitly marked synthetic: camera status metadata is not treated as decoded video, and no actual FPV pixels are shown until a validated camera transport and video decoder are implemented.

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

## Safety

There are no flight controls in this application. Current actions are connect, disconnect and read-only refresh only.
