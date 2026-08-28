# Official Autel Mobile SDK Path

## Executive Finding

The strongest research discovery to date is that Autel's own public Mobile SDK sample repository contains explicit, first-class code paths for both `X_STAR` and `PREMIUM` products.

This changes the preferred development order.

Before fully reimplementing Autel's USB framing and protocol stack, the project should first test whether the official legacy Mobile SDK can still authenticate and connect to an X-Star Premium on a modern ARM64 Android device.

If it works, the SDK may provide:

- USB/accessory connection management;
- product detection;
- aircraft telemetry;
- battery state, including individual-cell voltages;
- flight-controller state;
- remote-controller state;
- gimbal control/state;
- camera and DSP interfaces;
- live H.264 video callbacks/decoding; and
- mission APIs.

If authentication or X-Star support no longer works, the sample code and public iOS headers still provide a high-quality semantic specification for a clean independent implementation.

## Official Repositories

```text
AutelSDK/AndroidSample
AutelSDK/iOS_SdkSample
```

These are maintained under Autel's official GitHub organization.

## Android Evidence

### Product routing

The Android sample handles both:

```text
AutelProductType.X_STAR
AutelProductType.PREMIUM
```

and routes `PREMIUM` to dedicated X-Star Premium UI/modules.

### X-Star Premium modules

The sample contains dedicated activities/layouts for:

```text
XStarPremiumAircraft
XStarPremiumBattery
XStarPremiumFlyController
XStarPremiumGimbal
XStarPremiumDsp
XStarPremiumRemoteController
XStarPremiumWaypointMission
XStarPremiumOrbitMission
XStarPremiumFollowMission
```

This is not merely a generic drone sample. The source casts the connected product to `XStarPremiumAircraft` and obtains actual battery, flight-controller, gimbal, DSP and remote-controller interfaces.

### Battery API surface

The X-Star battery sample exposes methods/listeners for:

```text
individual cell voltage
pack voltage
current
temperature
design capacity
full-charge capacity
remaining capacity/percentage
real-time battery state
```

This is valuable for both the replacement ground-control app and the battery-preservation workstream.

### Video API surface

The official sample provides:

- `AutelCodecView` for ready-made display;
- `AutelCodecListener.onFrameStream(...)` for raw H.264 frame data;
- decoded-frame callbacks backed by Android `MediaCodec`.

This may let the PoC display FPV without manually reconstructing the RTSP path first.

### USB/proxy continuity

The official sample still references:

```text
com.autel.video.NetWorkProxyJni
com.autel.sdk.action.USB_ACCESSORY_ATTACHED
```

and begins its product flow from the USB accessory broadcast. This aligns closely with the legacy Starlink architecture discovered in the preserved APK.

### Package/application identity

The sample build currently uses:

```text
applicationId "com.autel.maxlink"
```

which is the same package name as legacy Starlink. This may be intentional for SDK whitelist/USB compatibility, but it also creates an installation/signing conflict with the original app and must be handled deliberately in our probe.

### ARM64 evidence

The official sample's build configuration includes:

```text
armeabi-v7a
arm64-v8a
```

and a 2023 official commit titled `fix the whitelist bug` added ARM64 packaging rules for Autel native libraries.

The public repository contains a roughly 11.7 MB `autel-sdk-release.aar`. Its source-level integration and Gradle configuration indicate ARM64 support, but this project has not yet completed an independent binary inventory of every native library inside that AAR.

## iOS Evidence

The official iOS framework headers explicitly define:

```text
AUTELDevice_Drone_XStarPremium
AUTELDevice_Drone_XStar
```

and document that these types create an Autel drone instance.

The public framework header set also exposes interfaces for:

- battery;
- camera;
- video feed;
- main controller;
- remote controller;
- gimbal;
- navigation;
- waypoint/orbit/follow missions; and
- an X-Star-specific remote-controller type.

The iOS headers are useful as readable API contracts even if the Android AAR remains opaque.

## Current Uncertainty: Authentication and Product Whitelist

Autel's current Mobile SDK documentation requires:

1. a registered Autel developer account;
2. a unique app key for the application;
3. matching application identity/name where required; and
4. successful SDK authentication before normal initialization.

The Android sample initializes with an app key. Its history mentions app-key verification, secondary authentication and whitelist fixes.

At the same time, Autel's current public Mobile SDK V1 product list names EVO II V1/V2, not X-Star. Therefore, the existence of X-Star classes does **not** prove that Autel's present authentication service will authorize a newly registered X-Star application.

Possible outcomes:

### Outcome A — SDK authenticates and connects to X-Star Premium

Best case. We use the official SDK as an interim compatibility layer and build a modern read-only app around it while evaluating licensing and long-term dependency risk.

### Outcome B — SDK authenticates but reports unsupported/unknown product

We capture the USB/proxy behavior and inspect whether the transport still starts. The SDK source/interfaces remain useful, but we may need to implement the product layer ourselves.

### Outcome C — SDK fails because app key/whitelist service no longer supports this path

We preserve exact errors/logs, test the historical SDK version used by Starlink where lawful, and continue with clean transport reimplementation.

### Outcome D — SDK connects but native libraries fail on modern Android

We inventory ABIs and dependencies, test on the S20 and S25, and determine whether a different official AAR revision has the required ARM64 support.

## Licensing and Product Strategy

The official sample repository does not, by itself, establish that its bundled SDK binary can be redistributed in a commercial application without an Autel agreement.

Before a commercial release, determine:

- SDK license terms;
- app-key issuance terms;
- supported-product restrictions;
- redistribution rights for the AAR/native libraries;
- whether Autel can revoke or discontinue authentication; and
- trademark/compatibility wording.

Even if the official SDK works technically, the long-term preservation project should avoid becoming wholly dependent on an online authorization service that can disappear.

Recommended architecture:

```text
X-Star Revival App
        |
XStarPlatform interface
        |
        +-- OfficialAutelSdkAdapter   (fastest PoC, if authorized)
        |
        +-- OpenXStarAdapter          (independent protocol implementation)
```

This lets us use the official path to learn and ship a prototype without abandoning the durable open-protocol goal.

## Read-Only SDK Probe

The first probe should do only the following:

1. initialize the SDK and log authentication result;
2. register product-connect/disconnect listeners;
3. display detected product type;
4. report availability of battery, flight controller, remote, gimbal, DSP/camera and codec interfaces;
5. subscribe to read-only battery state;
6. subscribe to read-only flight state;
7. display raw/decoded video if available;
8. record structured diagnostic logs.

It must not expose or invoke:

- arm/disarm;
- motor start;
- takeoff/landing;
- mission upload/start;
- virtual stick;
- parameter writes;
- calibration;
- firmware update; or
- battery writes.

All powered-aircraft testing remains props-off until the read-only connection path is fully understood.

## Decision Gate

The official SDK route is considered technically viable when a modern ARM64 Android device can:

- authenticate;
- detect `PREMIUM` or an equivalent X-Star product type;
- receive battery or flight-controller telemetry; and
- receive/display video.

If it fails, the probe must produce enough logs to identify whether the failure is:

- app-key authentication;
- product whitelist;
- USB accessory recognition;
- native ABI/loading;
- transport startup;
- aircraft/controller firmware compatibility; or
- higher-level product discovery.

## Priority Recommendation

**Run the official SDK probe before spending significant effort on raw USB frame reconstruction.**

The working legacy tablet remains essential because it provides a known-good behavioral baseline and can distinguish a modern SDK/authentication failure from a controller, cable or aircraft problem.
