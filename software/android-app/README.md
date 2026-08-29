# X-Star Revival Android App

This is the product-facing Android application. Its public build includes mock and deterministic replay modes; a local opt-in build can add the official receive-only Autel SDK adapter.

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

The Cockpit / FPV screen renders its telemetry HUD from `XStarState`. In MAVLink replay mode, an original raw H.264 Annex-B fixture is split by the app-core scanner and decoded to a `TextureView` with Android `MediaCodec`, exercising real AVC pixels beneath the Compose HUD. The clip is explicitly marked synthetic and is not X-Star camera footage.

When the optional official SDK binding is present, the same cockpit decodes the SDK's documented receive-only H.264 callback. It waits for a keyframe, supplies the standard AVC stream to `MediaCodec`, and reports rendered/dropped frames beneath the HUD. No proprietary USB framing is inferred.

### Passive bench capture

The live source includes a deliberately bounded hardware-capture workflow for decoder bring-up:

- it requires the official SDK, an app key, a connected product, and explicit confirmation that the propellers are removed;
- it records only received H.264 callbacks and normalized telemetry for at most 30 seconds or 64 MB;
- standard Annex-B SPS/PPS setup callbacks are retained before the first SDK-marked keyframe, while opaque pre-keyframe payloads remain unclassified and are dropped;
- the frame index preserves the SDK callback timestamp as a source-defined opaque integer rather than guessing its unit;
- telemetry export excludes GPS coordinates, controller opaque values, identifiers, app keys, warning text, and diagnostic notes; and
- the resulting ZIP can be shared explicitly or its private H.264 stream replayed locally beneath the cockpit HUD.

Camera imagery itself can still be identifying. Captures stay in the app cache, only the five newest archives are retained, and Android's share provider exposes only that capture directory.

## Optional live X-Star build

The proprietary AAR and SDK app key are intentionally excluded from Git. Supply both only in your local environment:

```bash
AUTEL_SDK_AAR=/absolute/path/to/autel-sdk-release.aar \
AUTEL_APP_KEY=your_registered_app_key \
./gradlew :app:assembleDebug
```

The live screen reports controller USB presence separately from SDK authorization and aircraft
product discovery. In addition to Autel's `Starlink` and `Autel Explorer` accessory identities,
the app narrowly recognizes the exact legacy `ammlab.org / HelloADK / 1.0` identity observed from
an X-Star Premium controller. Detection only reads Android's accessory inventory; it does not open
the accessory or send control data.

The validated AAR from Autel's Android sample repository has SHA-256 `138bd68f0986ac7009362cde01f9e54e4ee33e0f2ed2548e382205a59dcd7e17` and contains both `arm64-v8a` and `armeabi-v7a` native libraries. When the file is absent, the `Live X-Star` source and all proprietary classes are omitted from the build.

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

The official bridge's allowed observations and compile-time forbidden control calls are defined in the [X-Star SDK Capability Matrix](../../docs/XSTAR-SDK-CAPABILITY-MATRIX.md). Local Gradle builds and public CI both run an explicit forbidden-call audit.

App core provides a typed `AutelSdkBridge` observation contract and `H264VideoSource`. The Android binding feeds documented SDK callbacks into that seam without placing proprietary SDK types in the UI or domain model.

## Safety

There are no flight controls in this application. Current actions are connect, disconnect, read-only refresh, passive receive-only capture, and local replay only.
