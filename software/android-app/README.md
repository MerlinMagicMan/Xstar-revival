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
