# Android Autel SDK Bridge

This optional Android library is the hardware-specific, receive-only binding between Autel's legacy Mobile SDK AAR and app-core's `AutelSdkBridge` contract.

The proprietary AAR is deliberately not committed. Build with either:

```text
-PAUTEL_SDK_AAR=/absolute/path/to/autel-sdk-release.aar
```

or place the official file at:

```text
software/android-sdk-probe/app/libs/autel-sdk-release.aar
```

The binding accepts an app key at runtime and never logs it. It subscribes only to documented product, battery, flight, ultrasonic, remote, gimbal, camera, DSP, version, warning, and H.264 receive callbacks.

It contains no calls to takeoff, landing, RTH, virtual/raw sticks, mission operations, gimbal motion, pairing, calibration, camera/media actuation, configuration writes, deletion, formatting, Wi-Fi reset, or firmware update.

## Validated AAR

The first compile target is the official AutelSDK/AndroidSample `app/libs/autel-sdk-release.aar` retrieved from the `master` branch:

```text
Git blob: d7a533f33a184ac4db72a179fa846eea8965c6e1
Size: 12,241,390 bytes
SHA-256: 138bd68f0986ac7009362cde01f9e54e4ee33e0f2ed2548e382205a59dcd7e17
Archive timestamp: 2023-08-01
```

It contains `arm64-v8a` and `armeabi-v7a` builds of `libAutelPlayer`, `libAutelUtil`, `libNetWorkProxy`, and `libwhitename`.
