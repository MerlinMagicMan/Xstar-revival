# X-Star SDK Capability Matrix

## Purpose

This document is the source-of-truth inventory for the public Android API surface documented in **Autel Mobile SDK 2.0.11.79** for the X-Star, X-Star Premium, and R12 camera. It answers two questions for every relevant public member:

1. Can X-Star Revival use a documented interface instead of reverse-engineering it?
2. Is the member passive, or can it change device state or move the aircraft?

The matrix is deliberately conservative. An API is not treated as an X-Star capability merely because its type exists in the same SDK archive. Product-specific ownership or an inheritance path from `XStarPremiumAircraft` must be visible in the documentation. Unknown USB framing, transport endpoints, remote-control payload layouts, and proprietary Autel messages remain unknown.

## Source scope and integrity

| Source | Role | SHA-256 |
|---|---|---|
| `SDK Api_V2.0.11.79.zip` | Generated Java API documentation mined for types, inheritance, methods, fields, and callbacks | `aebfab479d1f44046ec0f6f48cf040f4223b4d6d00d5bb2641d8b7ee702c5cd8` |
| `Autel_Robotics_Android_SDK_release_notes.pdf` | Product, module, release-date, and supported-firmware provenance | `c09c55109c1817df86d0b8b58f4ee2fad7290bae0157dae996095b1ca712d7b1` |
| `X-Star_Premium__User_Manual_EN.pdf` | Hardware topology and operator-facing behavior cross-check | `22d43b1f0c543da653dcd8d04b3264c66d782a5b939047a8fceac52e3702bb42` |

Archive review found 856 entries, including 763 HTML pages. It contains generated documentation, not an Android library: no AAR, JAR, class, Java source, or native-library payload was present. The archive passed CRC and path-traversal checks. One Windows shortcut was present and was not executed. Source documents are not committed to this repository; only derived facts and hashes are retained.

### Compiled AAR cross-check

The receive-only binding was compiled against `app/libs/autel-sdk-release.aar` from Autel's official Android sample repository (Git blob `d7a533f33a184ac4db72a179fa846eea8965c6e1`, SHA-256 `138bd68f0986ac7009362cde01f9e54e4ee33e0f2ed2548e382205a59dcd7e17`). The 12,241,390-byte archive contains both `arm64-v8a` and `armeabi-v7a` native libraries.

Binary inspection found two relevant differences from the 2.0.11.79 documentation corpus:

- `AutelCodecListener` also requires a decoded-frame callback. Revival implements it as a no-op and consumes only the documented raw H.264 receive callback.
- `AutelFlyController` exposes `setRemoteControlStick(...)`. This is an aircraft-control write and is explicitly included in the source audit's forbidden-call list.

The AAR remains uncommitted and no undocumented binary member is treated as safe merely because it exists.

### Coverage boundary

This inventory includes:

- the SDK entry point and product connection callback;
- the full product inheritance path for X-Star Premium;
- all documented direct and inherited methods on the X-Star battery, flight-controller, DSP, and gimbal interfaces;
- the shared remote-controller, album, mission, codec, camera-manager, base-camera, and X-Star R12 interfaces reachable from the product;
- X-Star mission data objects and real-time mission callbacks;
- telemetry data exposed by those interfaces; and
- callback contracts used by the surface.

It intentionally collapses ReactiveX interfaces such as `RxXStarBattery` into their callback equivalents because they mirror the same operations rather than adding aircraft capabilities. It also omits methods inherited only from Java/Android framework base classes, ordinary enum helpers (`values`, `valueOf`, `find`), and EVO/EVO II-only APIs.

An automated coverage comparison checked **237 direct method rows** across the selected public interfaces (including overloads and narrowed/overridden product getters) and found no unaccounted method names after the documented Rx and EVO-only exclusions.

## Classification legend

| Code | Meaning | Probe policy |
|---|---|---|
| **O** | Observe: query, event subscription, or received telemetry | Allowed in the read-only probe |
| **L** | Local/display/receive lifecycle; no aircraft or camera setting is changed | Allowed with normal resource cleanup |
| **C** | Configuration write to aircraft, controller, battery, camera, DSP, or gimbal | Excluded from the read-only probe |
| **M** | Media actuation: take photo, record, or change capture mode | Excluded from the read-only probe |
| **A** | Aircraft, gimbal-motion, pairing, calibration, or mission actuation | Compile-time excluded from the read-only probe |
| **D** | Destructive or disruptive operation such as format, delete, reset, or Wi-Fi credential change | Compile-time excluded |
| **U** | Shared SDK symbol whose availability on X-Star is not established | Do not implement as X-Star functionality without hardware evidence |

Evidence labels:

- **XSTAR** — declared by an X-Star/R12-specific interface or data type;
- **INHERITED** — declared by a shared base interface that the X-Star product demonstrably exposes;
- **RELEASE** — established by the 2.0 release notes or X-Star manual;
- **SHARED-ONLY** — present in the SDK corpus without a documented X-Star access path.

> A method beginning with `set` is not automatically a write. Listener registration methods are **O** because they subscribe to received data. Conversely, changing an RF route or flight limit is a write even if it looks like ordinary configuration.

## Supported baseline

The release notes are dated **2019-04-29** and explicitly list X-Star, X-Star Premium, and the R12 camera as supported by Mobile SDK 2.0. The stated X-Star firmware baseline is:

| Component | Version |
|---|---|
| Aircraft bundle | `V2.0.12` |
| Camera | `V0.0.0.053` |
| Flight controller | `V2.00.38` |
| DSP / image transmission | `V0.01.60` |
| Repeater | `V1.01.50` (`WiFi V2.0.23`) |
| Gimbal | `V2.0.3.5` |
| Remote controller | `V1.0.1.5` |
| Battery | `V6.07` |
| Optical flow | `V0.6.8.0` |
| Sonar | `V0.1.0.6` |

These versions are a compatibility target, not proof that modern SDK authentication or product whitelisting still works.

## Executive route map

| Capability | Documented X-Star route | Direction | Revival decision |
|---|---|---:|---|
| Product connect/disconnect and type | `Autel` + `ProductConnectListener` + `BaseProduct.getType` | O | Official bridge first |
| Main FPV video bytes | `BaseProduct.getCodec` → `AutelCodecListener.onFrameStream` | O/L | Official bridge first; H.264 bytes, keyframe flag, length, and PTS are known |
| Ready-made FPV display | `AutelCodecView` | L | Optional probe aid; Revival decoder/HUD remains independent |
| GPS, attitude, altitude, velocity, flight state, warnings | `XStarFlyController.setFlyControllerInfoListener` | O | Official bridge first |
| Ultrasonic height | `setUltraSonicHeightInfoListener` | O | Official bridge first |
| Raw sonar samples | None documented | — | Reverse-engineering route |
| Raw optical-flow vectors or frames | None documented | — | Reverse-engineering route |
| Optical-flow and sonar firmware versions | `FlyControllerVersionInfo` | O | Official bridge first |
| Pack and individual-cell battery telemetry | `XStarBattery` / `BatteryState` | O | Official bridge first |
| Battery serial and lifetime/configuration values | inherited `AutelBattery` queries | O | Official bridge; handle identifiers as sensitive |
| RF channel/frequency and signal scan | `AutelDsp.getCurrentRFData/getRFDataList` | O | Official bridge first |
| Premium USB-enabled state | `XStarDsp.isUSBEnable` | O | Official bridge first |
| Wi-Fi SSID/password state | `XStarDsp.getCurrentSSIDInfo` | O | Official bridge; redact password from logs |
| Exact USB framing, VID/PID, proxy ports, endpoints | Not exposed by Javadoc | — | Reverse-engineering route; do not infer constants |
| Gimbal pitch and health state | `XStarGimbal` listeners | O | Official bridge first |
| Gimbal positioning | `XStarGimbal` motion calls | A | Later controlled adapter only |
| Remote battery/link/image-link state | `setInfoDataListener` | O | Official bridge first |
| Remote sticks/buttons/mode payload | `setControlMenuListener(int[])` | O | Capture opaquely until indices are proven |
| R12 exposure/capture state and histogram | `AutelR12` queries/listeners | O | Official bridge first |
| Camera settings, photo, record | `AutelR12` / `AutelBaseCamera` writes | C/M | Later media-control adapter |
| Album listing and resolution metadata | `AutelAlbum` | O | Official bridge first |
| Waypoint, orbit, follow mission state | `MissionManager` + X-Star mission types | O | Read-only mission status first |
| Mission upload/start/pause/resume/cancel | `MissionManager` | A | Later safety-gated adapter only |

## Callback contracts

Every asynchronous operation in this surface uses one of these documented contracts:

| Callback | Members | Meaning | Class |
|---|---|---|---:|
| `FailedCallback` | `onFailure(AutelError)` | Failure path inherited by other callbacks | O |
| `CallbackWithNoParam` | `onSuccess()`; inherited `onFailure` | Completion without result data | O |
| `CallbackWithOneParam<T>` | `onSuccess(T)`; inherited `onFailure` | One result or listener event | O |
| `CallbackWithTwoParams<T,D>` | `onSuccess(T,D)`; inherited `onFailure` | Two result/event values | O |
| `CallbackWithOneParamProgress<T>` | `onProgress(float)`; inherited `onSuccess(T)` and `onFailure` | Mission transfer progress and result | O |
| `ProductConnectListener` | `productConnected(BaseProduct)`, `productDisconnected()` | Product lifecycle | O |
| `AutelCodecListener` | `onFrameStream(byte[], boolean, int, long)`, `onCanceled()`, inherited `onFailure` | H.264 bytes, I-frame flag, valid size, PTS, cancellation, error | O |
| `OnRenderFrameInfoListener` | `onRenderFrameSizeChanged(int,int)`, `onRenderFrameTimestamp(long)` | Render dimensions and PTS | O |

Callbacks describe completion or received data; the risk class of an operation is determined by the operation that accepts the callback.

## SDK and product lifecycle

| Interface | Member | Effect | Class | Evidence |
|---|---|---|---:|---|
| `Autel` | `init(Context, AutelSdkConfig, CallbackWithNoParam)` | Initialize SDK | L | INHERITED |
| `Autel` | `init(Context, String appKey, CallbackWithNoParam)` | Initialize/authenticate with app key | L | INHERITED |
| `Autel` | `setProductConnectListener(ProductConnectListener)` | Subscribe to product connect/disconnect | O | INHERITED |
| `Autel` | `destroy()` | Tear down local SDK state | L | INHERITED |
| `BaseProduct` | `getType()` | Product type | O | INHERITED |
| `BaseProduct` | `getAlbum()` | Album interface | O | INHERITED |
| `BaseProduct` | `getBattery()` | Battery interface | O | INHERITED |
| `BaseProduct` | `getCameraManager()` | Camera-manager interface | O | INHERITED |
| `BaseProduct` | `getCodec()` | Codec/FPV interface | O | INHERITED |
| `BaseProduct` | `getDsp()` | Image-transmission interface | O | INHERITED |
| `BaseProduct` | `getFlyController()` | Flight-controller interface | O | INHERITED |
| `BaseProduct` | `getGimbal()` | Gimbal interface | O | INHERITED |
| `BaseProduct` | `getMissionManager()` | Mission interface | O | INHERITED |
| `BaseProduct` | `getRemoteController()` | Remote-controller interface | O | INHERITED |

`XStarAircraft` narrows the return types of battery, DSP, flight-controller, gimbal, and remote-controller getters. `XStarPremiumAircraft` inherits the entire `XStarAircraft` and `BaseProduct` surface and declares no additional direct methods.

## Battery

### Queries and telemetry

| API member | Returned data | Class | Evidence |
|---|---|---:|---|
| `XStarBattery.getCapacity(CallbackWithOneParam<Float>)` | Remaining capacity, mAh | O | XSTAR |
| `getCurrent(CallbackWithOneParam<Float>)` | Pack current, mA | O | XSTAR |
| `getDesignCapacity(CallbackWithOneParam<Float>)` | Design capacity, mAh | O | XSTAR |
| `getRemainingPercent(CallbackWithOneParam<Integer>)` | State of charge | O | XSTAR |
| `getTemperature(CallbackWithOneParam<Float>)` | Pack temperature, °C | O | XSTAR |
| `getVoltage(CallbackWithOneParam<Float>)` | Pack voltage, mV | O | XSTAR |
| `getVoltageCells(CallbackWithOneParam<int[]>)` | Individual cell voltages, mV | O | XSTAR |
| `setBatteryStateListener(CallbackWithOneParam<BatteryState>)` | Real-time aggregate battery stream | O | XSTAR |
| `AutelBattery.getCriticalBatteryNotifyThreshold(CallbackWithOneParam<Float>)` | Critical warning threshold | O | INHERITED |
| `getDischargeCount(CallbackWithOneParam<Integer>)` | Documented discharge/charge count | O | INHERITED |
| `getDischargeDay(CallbackWithOneParam<Integer>)` | Self-discharge idle delay | O | INHERITED |
| `getFullChargeCapacity(CallbackWithOneParam<Integer>)` | Learned full-charge capacity, mAh | O | INHERITED |
| `getLowBatteryNotifyThreshold(CallbackWithOneParam<Float>)` | Low warning threshold | O | INHERITED |
| `getParameterSupportManager()` | Supported parameter/range metadata | O | INHERITED |
| `getSerialNumber(CallbackWithOneParam<String>)` | Battery serial; sensitive identifier | O | INHERITED |
| `getVersion(CallbackWithOneParam<String>)` | Battery firmware version | O | INHERITED |

`BatteryState` exposes warning state, remaining capacity, current, design capacity, remaining percentage, estimated flight minutes, temperature, pack voltage, and individual-cell voltages. `XStarBatteryInfo` adds full-charge capacity, discharge count, serial number, firmware version, and overheat state.

### Writes

| API member | Effect | Class | Evidence |
|---|---|---:|---|
| `setCriticalBatteryNotifyThreshold(float, CallbackWithNoParam)` | Changes critical warning threshold | C | INHERITED |
| `setDischargeDay(int, CallbackWithNoParam)` | Changes self-discharge policy | C | INHERITED |
| `setLowBatteryNotifyThreshold(float, CallbackWithNoParam)` | Changes low warning threshold | C | INHERITED |

No cell balancing, charge control, protection bypass, raw SMBus transaction, or battery firmware-write API is documented in this surface.

## Flight controller and sensors

### Passive state

| API member | Returned data | Class | Evidence |
|---|---|---:|---|
| `XStarFlyController.setConnectStateListener(CallbackWithOneParam<FlyControllerConnectState>)` | Flight-controller link state | O | XSTAR |
| `setFlyControllerInfoListener(CallbackWithOneParam<FlyControllerInfo>)` | Aggregate GPS, attitude, altitude/speed, flight status, home state | O | XSTAR |
| `setUltraSonicHeightInfoListener(CallbackWithOneParam<Float>)` | Height measured by ultrasonic sensor | O | XSTAR |
| `getParameterRangeManager()` | X-Star flight parameter ranges | O | XSTAR |
| `AutelFlyController.getLedPilotLamp(CallbackWithOneParam<LedPilotLamp>)` | Aircraft LED setting | O | INHERITED |
| `getMaxHeight(CallbackWithOneParam<Float>)` | Configured altitude limit | O | INHERITED |
| `getMaxHorizontalSpeed(CallbackWithOneParam<Float>)` | Configured speed limit | O | INHERITED |
| `getMaxRange(CallbackWithOneParam<Float>)` | Configured range limit | O | INHERITED |
| `getReturnHeight(CallbackWithOneParam<Float>)` | Configured return-to-home altitude | O | INHERITED |
| `getSerialNumber(CallbackWithOneParam<String>)` | Aircraft serial; sensitive identifier | O | INHERITED |
| `getVersionInfo(CallbackWithOneParam<FlyControllerVersionInfo>)` | FC, optical-flow, and sonar firmware versions | O | INHERITED |
| `isAttitudeModeEnable(CallbackWithOneParam<Boolean>)` | ATTI-mode permission state | O | INHERITED |
| `isBeginnerModeEnable(CallbackWithOneParam<Boolean>)` | Beginner-mode state | O | INHERITED |
| `setCalibrateCompassListener(CallbackWithOneParam<CalibrateCompassStatus>)` | Observe calibration status only | O | INHERITED |
| `setWarningListener(CallbackWithTwoParams<ARMWarning, MagnetometerState>)` | Arming and magnetometer warnings | O | INHERITED |

`FlyControllerInfo` and its nested values expose:

| Data interface | Fields |
|---|---|
| `GPSInfo` | Coordinate, satellite count, satellite strength |
| `AttitudeInfo` | Pitch, roll, yaw; documented default unit is the SDK's `ANGLE` unit |
| `AltitudeAndSpeedInfo` | Altitude in metres; scalar horizontal speed and X/Y/Z speeds in metres per second |
| `FlyControllerStatus` | Arm error code, no-fly warning, flight mode, main flight state, compass/GPS/home validity, RC-signal loss, overheating, RTH pending, range/height limits, stick limiting, takeoff validity, warming, wind warning, and an RTK-support flag |
| `FlyHome` | 3D home coordinate, validity, and comparison via `isHomeChanged(AutelCoordinate3D)` |
| `FlyControllerVersionInfo` | Flight-controller, optical-flow, and sonar firmware versions |

The documentation does **not** give units or coordinate frames for every numeric field. Revival must preserve raw values alongside normalized values until hardware captures establish units, sign, origin, and update rate.

### Aircraft/configuration actuation

| API member | Effect | Class | Evidence |
|---|---|---:|---|
| `cancelLand(CallbackWithNoParam)` | Cancels landing | A | INHERITED |
| `cancelReturn(CallbackWithNoParam)` | Cancels RTH and commands hover | A | INHERITED |
| `goHome(CallbackWithNoParam)` | Starts RTH | A | INHERITED |
| `land(CallbackWithNoParam)` | Commands landing | A | INHERITED |
| `takeOff(CallbackWithNoParam)` | Commands takeoff | A | INHERITED |
| `setAircraftLocationAsHomePoint(CallbackWithNoParam)` | Changes home point | A | INHERITED |
| `setLocationAsHomePoint(double,double,CallbackWithNoParam)` | Changes home point to phone coordinate | A | INHERITED |
| `startCalibrateCompass(CallbackWithOneParam<CalibrateCompassStatus>)` | Starts calibration | A | INHERITED |
| `setAttitudeModeEnable(boolean,CallbackWithNoParam)` | Changes flight-mode permission | C | INHERITED |
| `setBeginnerModeEnable(boolean,CallbackWithNoParam)` | Changes flight envelope mode | C | INHERITED |
| `setLedPilotLamp(LedPilotLamp,CallbackWithNoParam)` | Changes aircraft LEDs | C | INHERITED |
| `setMaxHeight(double,CallbackWithNoParam)` | Changes altitude limit | C | INHERITED |
| `setMaxHorizontalSpeed(double,CallbackWithNoParam)` | Changes horizontal speed limit | C | INHERITED |
| `setMaxRange(double,CallbackWithNoParam)` | Changes range limit | C | INHERITED |
| `setReturnHeight(double,CallbackWithNoParam)` | Changes RTH altitude | C | INHERITED |

No raw optical-flow vectors, optical-flow frames, raw sonar waveforms, motor commands, virtual-stick API, or arbitrary parameter-write function is documented on `XStarFlyController`.

## DSP, RF, Wi-Fi, and USB state

| API member | Effect/data | Class | Evidence |
|---|---|---:|---|
| `AutelDsp.getCurrentRFData(int,CallbackWithOneParam<RFData>)` | Current frequency and signal value | O | INHERITED |
| `getRFDataList(int,CallbackWithOneParam<List<RFData>>)` | Available/scanned frequencies and signal values | O | INHERITED |
| `getVersionInfo(CallbackWithOneParam<DspVersionInfo>)` | Image-transmission firmware version | O | INHERITED |
| `XStarDsp.getCurrentSSIDInfo()` | `WiFiInfo` SSID and password | O | XSTAR |
| `isUSBEnable()` | Whether USB mode is enabled | O | XSTAR |
| `setCurrentRFData(int,int,CallbackWithNoParam)` | Changes RF route/frequency | C | INHERITED |
| `resetWifi()` | Resets Wi-Fi settings | D | XSTAR |
| `updateNewSSIDInfo(String,String,CallbackWithNoParam)` | Changes SSID/password | D | XSTAR |

`RFData.frequency` is a public float documented in hertz; `RFData.value` is a public signal-strength integer. Their absolute calibration and sign convention are not documented. `WiFiInfo.getPassword()` is sensitive and must never be written to normal logs.

The API names `CameraInfoConfig.USB.IP`, `PORT`, and `PLAY_PORT`, but the generated documentation does not disclose their values. This is evidence that a USB network/proxy route exists, not evidence for a particular address or endpoint.

## FPV video and rendering

| API member | Effect/data | Class | Evidence |
|---|---|---:|---|
| `BaseProduct.getCodec()` | Obtains the receive-side video interface | O | INHERITED |
| `AutelCodec.setCodecListener(AutelCodecListener,Handler)` | Registers listener and starts video stream/codec | L | INHERITED |
| `AutelCodecListener.onFrameStream(byte[],boolean,int,long)` | Receives H.264 data, I-frame flag, valid length, and PTS | O | INHERITED |
| `AutelCodecListener.onCanceled()` / `onFailure(AutelError)` | Stream terminal/error callbacks | O | INHERITED |
| `AutelCodec.cancel()` | Stops receive/codec lifecycle | L | INHERITED |
| `AutelCodecView(Context[,AttributeSet])` | Ready-made `TextureView` renderer | L | INHERITED |
| `startDecode(SurfaceTexture,int,int,boolean)` | Starts local decode | L | INHERITED |
| `pause()` / `resume()` / `stopCodec()` | Local decoder lifecycle | L | INHERITED |
| `surfaceSizeChanged(int,int)` | Updates render surface size | L | INHERITED |
| `setOnRenderFrameInfoListener(OnRenderFrameInfoListener)` | Render dimensions/timestamp subscription | O | INHERITED |
| `isOverExposureEnabled()` | Reads local overexposure-overlay state | O | INHERITED |
| `setOverExposure(boolean,int)` | Changes local display overlay | L | INHERITED |

This is the best-known path to the cockpit FPV view. It removes the need to guess the H.264 elementary-stream semantics at the app boundary, but it does not reveal the underlying USB packet framing, network proxy, codec parameters, latency behavior, or stream endpoint.

## Camera manager and R12 camera

### Connection and passive base-camera state

| API member | Returned data | Class | Evidence |
|---|---|---:|---|
| `AutelCameraManager.setCameraChangeListener(CallbackWithTwoParams<CameraProduct,AutelBaseCamera>)` | Camera product and connection/interface change | O | INHERITED |
| `AutelBaseCamera.getCurrentRecordTime(CallbackWithOneParam<Integer>)` | Current record duration | O | INHERITED |
| `getGpsCoordinateType(CallbackWithOneParam<Integer>)` | Camera GPS-coordinate encoding setting; values undocumented here | O | INHERITED |
| `getMediaMode(CallbackWithOneParam<MediaMode>)` | Photo/video mode | O | INHERITED |
| `getProduct()` | Camera model | O | INHERITED |
| `getSDCardFreeSpace(CallbackWithOneParam<Long>)` | Free bytes | O | INHERITED |
| `getSDCardState(CallbackWithOneParam<SDCardState>)` | Card state | O | INHERITED |
| `getStateInfo(CallbackWithOneParam<BaseStateInfo>)` | R12 returns `R12StateInfo` | O | INHERITED |
| `getVersion(CallbackWithOneParam<String>)` | Camera firmware | O | INHERITED |
| `getWorkState(CallbackWithOneParam<WorkState>)` | Camera work state | O | INHERITED |
| `setMediaModeListener(CallbackWithOneParam<MediaMode>)` | Real-time mode events | O | INHERITED |
| `setMediaStateListener(CallbackWithTwoParams<MediaStatus,String>)` | Capture/record events and associated string | O | INHERITED |
| `setSDCardStateListener(CallbackWithOneParam<SDCardState>)` | Card-state events | O | INHERITED |

### R12-specific queries and listeners

All members below are **O / XSTAR**:

| Group | API members and returned values |
|---|---|
| Exposure | `getAutoExposureLockState(AutoExposureLockState)`, `getExposure(ExposureCompensation)`, `getExposureMode(ExposureMode)`, `getISO(CameraISO)`, `getShutter(ShutterSpeed)` |
| Image look | `getColorStyle(ColorStyle)`, `getPhotoStyle(PhotoStyle)`, `getWhiteBalance(WhiteBalance)`, `getAntiFlicker(AntiFlicker)`, `is3DNoiseReductionEnable(Boolean)` |
| Photo | `getAspectRatio(PhotoAspectRatio)`, `getPhotoAEBCount(PhotoAEBCount)`, `getPhotoBurstCount(PhotoBurstCount)`, `getPhotoFormat(PhotoFormat)`, `getPhotoSum(Integer)`, `getPhotoTimelapseInterval(PhotoTimelapseInterval)` |
| Video | `getLeftRecordTime(Long)`, `getVideoFormat(VideoFormat)`, `getVideoResolutionAndFrameRate(VideoResolutionAndFps)`, `getVideoStandard(VideoStandard)`, `isSubtitleEnable(Boolean)` |
| Metering/zoom | `getDigitalZoomScale(Integer)`, `getSpotMeteringArea(SpotMeteringArea)` |
| Live state | `isHistogramEnable(Boolean)`, `setHistogramListener(CallbackWithOneParam<int[]>)`, `setInfoListener(CallbackWithOneParam<R12CameraInfo>)` |
| Capability metadata | `getParameterRangeManager()` |

`R12CameraInfo` provides live exposure compensation, ISO, and shutter speed. `R12StateInfo` provides AEB count, anti-flicker, auto-exposure lock, burst count, color style, digital zoom, exposure mode, aspect ratio, photo format/style/timelapse, video format/resolution/frame rate/standard, white balance, histogram/subtitle flags, and inherited gimbal-lock/media/card/product/work states.

### Camera settings, media, and destructive operations

| Group | API members | Class | Evidence |
|---|---|---:|---|
| Base settings | `setCameraPattern`, `setGpsCoordinateType`, `setMediaMode`, `lockGimbalWhenTakePhoto`, `resetDefaults` | C | INHERITED |
| Capture | `startTakePhoto`, `stopTakePhoto`, `startRecordVideo`, `stopRecordVideo` | M | INHERITED |
| Storage | `formatSDCard` | D | INHERITED |
| R12 exposure | `setAutoExposureLockState`, `setExposure`, `setExposureMode`, `setISO`, `setShutter` | C | XSTAR |
| R12 image look | `setAntiFlicker`, `setColorStyle`, `setPhotoStyle(int,int,int)`, `setPhotoStyle(PhotoStyleType)`, `setWhiteBalance`, `set3DNoiseReductionEnable` | C | XSTAR |
| R12 photo | `setAspectRatio`, `setPhotoAEBCount`, `setPhotoBurstCount`, `setPhotoFormat`, `setPhotoTimelapseInterval` | C | XSTAR |
| R12 video | `setVideoFormat`, `setVideoResolutionAndFrameRate`, `setVideoStandard`, `setVideoSubtitleEnable` | C | XSTAR |
| R12 metering/zoom | `setDigitalZoomScale`, `setSpotMeteringArea` | C | XSTAR |

Every camera write above accepts a `CallbackWithNoParam` except the overload parameters shown by its name; the two `setPhotoStyle` overloads respectively accept three integers or `PhotoStyleType`. Listener registrations are not included in the write table.

## Gimbal

| API member | Effect/data | Class | Evidence |
|---|---|---:|---|
| `AutelGimbal.getGimbalLimitUpward(CallbackWithOneParam<Boolean>)` | Reads upward-tilt permission | O | INHERITED |
| `getGimbalWorkMode(CallbackWithOneParam<GimbalWorkMode>)` | Reads work mode | O | INHERITED |
| `getParameterRangeManager()` | X-Star gimbal ranges | O | XSTAR |
| `getVersionInfo(CallbackWithOneParam<GimbalVersionInfo>)` | Firmware version | O | INHERITED |
| `XStarGimbal.setAngleListener(CallbackWithOneParam<Integer>)` | Pitch-angle stream | O | XSTAR |
| `setGimbalStateListener(CallbackWithOneParam<GimbalState>)` | Health/calibration/protection state stream | O | XSTAR |
| `setGimbalLimitUpward(boolean,CallbackWithNoParam)` | Changes tilt limit permission | C | INHERITED |
| `setGimbalWorkMode(GimbalWorkMode,CallbackWithNoParam)` | Changes stabilization/work mode | C | INHERITED |
| `setGimbalAngle(float)` | Commands pitch angle | A | XSTAR |
| `setGimbalAngleWithSpeed(int)` | Commands pitch motion/offset at speed | A | XSTAR |
| `setRollAdjustData(GimbalRollAngleAdjust,CallbackWithOneParam<Double>)` | Commands roll-axis adjustment | A | XSTAR |

`GimbalState` includes normal, angle-limit, sleep/protection, motor-shutdown, over-temperature, hardware-failure, and calibration states. State availability is documented; exact integer angle units/ranges must be taken from the parameter manager or hardware capture rather than assumed.

`GimbalVersionInfo` provides gimbal firmware, bootloader, gimbal serial number, and pitch/roll/yaw ESC firmware versions. The serial number is a sensitive identifier.

## Remote controller

### Passive state and input

| API member | Returned data | Class | Evidence |
|---|---|---:|---|
| `getCommandStickMode(CallbackWithOneParam<RemoteControllerCommandStickMode>)` | Stick layout | O | INHERITED |
| `getGimbalDialAdjustSpeed(CallbackWithOneParam<Integer>)` | Gimbal dial speed setting | O | INHERITED |
| `getLanguage(CallbackWithOneParam<RemoteControllerLanguage>)` | Controller language | O | INHERITED |
| `getLengthUnit(CallbackWithOneParam<RemoteControllerParameterUnit>)` | Display unit | O | INHERITED |
| `getParameterRangeManager()` | Controller ranges | O | INHERITED |
| `getRFPower(CallbackWithOneParam<RFPower>)` | Transmit-power setting | O | INHERITED |
| `getSerialNumber(CallbackWithOneParam<String>)` | Controller serial; sensitive identifier | O | INHERITED |
| `getTeachingMode(CallbackWithOneParam<TeachingMode>)` | Controller teaching mode | O | INHERITED |
| `getVersionInfo(CallbackWithOneParam<RemoteControllerVersionInfo>)` | Controller firmware versions | O | INHERITED |
| `getYawCoefficient(CallbackWithOneParam<Float>)` | Yaw sensitivity, documented range 0.2–0.7 | O | INHERITED |
| `setConnectStateListener(CallbackWithOneParam<RemoteControllerConnectState>)` | Controller link state | O | INHERITED |
| `setControlMenuListener(CallbackWithOneParam<int[]>)` | Stick, gimbal button/dial, control button, flight-mode, and combo-button payload | O | INHERITED |
| `setInfoDataListener(CallbackWithOneParam<RemoteControllerInfo>)` | Controller battery, controller signal, image-link signal, calibration state | O | INHERITED |
| `setRemoteButtonControllerListener(CallbackWithOneParam<RemoteControllerNavigateButtonEvent>)` | Navigation-button events | O | INHERITED |

The `int[]` layout of `setControlMenuListener` is not described by the Javadoc. Revival may capture and replay it as opaque data, but must not publish guessed stick indices, ranges, or button meanings as fact.

### Pairing and configuration

| API member | Effect | Class | Evidence |
|---|---|---:|---|
| `enterPairing(CallbackWithNoParam)` / `exitPairing()` | Changes pairing state | A | INHERITED |
| `setCommandStickMode(...,CallbackWithNoParam)` | Changes stick layout | C | INHERITED |
| `setGimbalDialAdjustSpeed(int,CallbackWithNoParam)` | Changes dial response | C | INHERITED |
| `setLanguage(...,CallbackWithNoParam)` | Changes controller language | C | INHERITED |
| `setParameterUnit(...,CallbackWithNoParam)` | Changes display unit | C | INHERITED |
| `setRFPower(...,CallbackWithNoParam)` | Changes RF transmit power | C | INHERITED |
| `setStickCalibration(...,CallbackWithNoParam)` | Runs/advances control calibration | A | INHERITED |
| `setTeachingMode(...,CallbackWithNoParam)` | Changes controller operating mode | C | INHERITED |
| `setYawCoefficient(float,CallbackWithNoParam)` | Changes yaw response | C | INHERITED |

`getRcCustomKey` and `setRcCustomKey` are explicitly documented as **EVO II only**. They are not X-Star capabilities and are excluded.

## Album and media files

| API member | Effect/data | Class | Evidence |
|---|---|---:|---|
| `getMedia([AlbumType,]int start,int count,CallbackWithOneParam<List<MediaInfo>>)` | Lists normal camera media | O | INHERITED |
| `getFMCMedia([AlbumType,]int start,int count,CallbackWithOneParam<List<MediaInfo>>)` | Lists camera-server/FMC media | O | INHERITED |
| `getVideoResolutionFromHttpHeader(MediaInfo,CallbackWithOneParam<VideoResolutionAndFps>)` | Reads video metadata | O | INHERITED |
| `getFMCVideoResolutionFromHttpHeader(MediaInfo,CallbackWithOneParam<VideoResolutionAndFps>)` | Reads FMC video metadata | O | INHERITED |
| `getVideoResolutionFromLocalFile(File)` | Reads local file metadata | L | INHERITED |
| `getParameterRangeManager()` | Album capability ranges | O | INHERITED |
| `deleteMedia(MediaInfo or List<MediaInfo>,CallbackWithOneParam<List<MediaInfo>>)` | Deletes camera media | D | INHERITED |
| `deleteFMCMedia(MediaInfo or List<MediaInfo>,CallbackWithOneParam<List<MediaInfo>>)` | Deletes camera-server/FMC media | D | INHERITED |

Bracketed parameters indicate the documented overload with and without `AlbumType`; the single-item and list delete forms are both present.

`MediaInfo` exposes file size, creation-time string, large and small thumbnail URLs, original-media URL, video-play URL, video encoding format, and video resolution/frame rate. URLs may contain device-local addresses or access material and should be redacted from ordinary logs.

## Supporting state models

These read-only values are returned by the capability methods above and are part of the capture schema:

| Model | Documented values | Evidence |
|---|---|---|
| `DspVersionInfo` | DSP firmware and transfer-board firmware | INHERITED |
| `GimbalVersionInfo` | Bootloader, gimbal serial, gimbal firmware, pitch/roll/yaw ESC firmware | INHERITED |
| `RemoteControllerVersionInfo` | Remote-control, repeater, RF receive, and RF transmit firmware | INHERITED |
| `RemoteControllerInfo` | Controller battery percentage, controller signal percentage, image-transmission/DSP percentage, stick calibration state | INHERITED |
| `BaseStateInfo` | Gimbal-lock state during capture, media mode, SD-card state, camera type, camera work state | INHERITED |
| `R12CameraInfo` | Exposure compensation, ISO, shutter speed | XSTAR |
| `R12StateInfo` | R12 capture and configuration state enumerated in the camera section | XSTAR |
| `MediaInfo` | Size, time, thumbnails, media/play URLs, encode format, resolution/frame rate | INHERITED |
| `AutelCoordinate3D` | Latitude, longitude, altitude; constructors/getters/setters are local data operations | INHERITED |

The model setters on `AutelCoordinate3D`, `Waypoint`, and other mission plan objects mutate only local Java objects. They become aircraft control only when a plan is sent through `MissionManager.prepareMission`.

## Missions

### Observation

| API member | Returned data | Class | Evidence |
|---|---|---:|---|
| `downloadMission(CallbackWithOneParamProgress<AutelMission>)` | Downloads current mission and progress | O | INHERITED |
| `getCurrentMission()` | Current mission object | O | INHERITED |
| `getMissionExecuteState()` | Current execution state | O | INHERITED |
| `setRealTimeInfoListener(CallbackWithOneParam<RealTimeInfo>)` | Mission real-time event stream | O | INHERITED |

`downloadMissionForEvo` is explicitly EVO-specific and is not counted as an X-Star capability.

X-Star mission real-time data includes:

- `OrbitRealTimeInfo`: angular velocity, orbit center coordinate, current mission state, completed/current lap, and radius;
- `WaypointRealTimeInfo`: angular velocity, current mission state, next waypoint coordinate, and current waypoint index.

### Local mission data versus aircraft commands

| API/member | Effect | Class | Evidence |
|---|---|---:|---|
| `FollowMission.create()`; fields `location`, `finishedAction`; `update(Location)` | Builds/updates local follow target data | L | XSTAR |
| `OrbitMission` fields `lat`, `lng`, `radius`, `speed`, `laps`, `finishedAction` | Local orbit plan data | L | XSTAR |
| `Waypoint(AutelCoordinate3D)`, getters/setters for coordinate and delay | Local waypoint data | L | XSTAR |
| `WaypointMission` fields `wpList`, `speed`, `finishedAction` | Local waypoint plan data | L | XSTAR |
| `prepareMission(AutelMission,CallbackWithOneParamProgress<Boolean>)` | Transfers/prepares mission data for aircraft use | A | INHERITED |
| `startMission(CallbackWithNoParam)` | Starts prepared mission | A | INHERITED |
| `pauseMission(CallbackWithNoParam)` | Pauses active mission | A | INHERITED |
| `resumeMission(CallbackWithNoParam)` | Resumes active mission | A | INHERITED |
| `cancelMission([int],CallbackWithNoParam)` | Cancels active mission; overload with type exists | A | INHERITED |
| `yawRestore(CallbackWithNoParam)` | Commands yaw-axis restoration | A | INHERITED |

Constructing a mission object is local and safe. Passing it to `prepareMission` crosses the aircraft-control boundary and is forbidden in the read-only probe.

## Shared vision symbols are not X-Star proof

The archive contains `VisualWarnState`, `VisualWarningStatus`, `VisualWarningInfo`, and many visual/obstacle/tracking enum values, including `OPTICAL_FLOW_INVALIDATE`. Those symbols are shared across a multi-product SDK corpus. No documented X-Star interface in this archive returns `VisualWarningStatus` or raw visual-sensor data.

Therefore:

| Claim | Status |
|---|---|
| X-Star firmware has named optical-flow and sonar components | Confirmed by release notes and version API |
| X-Star SDK reports ultrasonic height | Confirmed by X-Star-specific listener |
| X-Star SDK exposes raw optical-flow frames/vectors | Not found |
| X-Star SDK exposes obstacle maps, tracking, or vision-warning status | Unverified; shared symbols alone are insufficient |
| X-Star supports EVO-style obstacle avoidance or tracking APIs | Not established and must not be claimed |

## Read-only probe contract

### Allowlist

The first official-SDK adapter may compile only:

- SDK initialization/destruction and product lifecycle;
- product/module getters;
- getters, listener registrations, and their callbacks marked **O**;
- codec receive/decode lifecycle marked **L**;
- local log/replay, UI, and decoder operations; and
- local mission object parsing only if it is isolated from `MissionManager.prepareMission`.

It should normalize at minimum:

- product type and connection state;
- GPS coordinate, satellites, and signal strength;
- attitude, altitude, X/Y/Z and horizontal speed;
- flight mode/state and warnings;
- ultrasonic height;
- pack voltage/current/temperature/capacity/percent/time, cell voltages, and cell delta;
- aircraft, battery, DSP, gimbal, remote, camera, optical-flow, and sonar firmware versions;
- RF frequency/signal scan, USB-enabled state, and redacted Wi-Fi presence;
- gimbal angle/state;
- remote link/battery/image-link state and opaque input events;
- camera/card/exposure/record state and histogram; and
- H.264 frame bytes, keyframe flag, valid length, and PTS.

### Compile-time denylist

The read-only artifact must not reference methods classified **C**, **M**, **A**, or **D**. In particular, it must not compile:

- takeoff, landing, RTH, home-point, mission prepare/start/pause/resume/cancel, or yaw restore;
- compass, stick, or pairing operations;
- flight limits, attitude/beginner mode, RF power/channel, controller response, or battery thresholds;
- gimbal motion;
- camera setting, record, or shutter commands;
- album deletion, SD-card format, reset defaults, or Wi-Fi credential/reset calls.

This boundary should be enforced structurally with a minimal bridge interface, not only by hiding buttons in the UI.

## Known-interface versus reverse-engineering backlog

### Build now through the documented bridge

1. Product discovery and compatibility diagnostics.
2. Full passive aircraft, battery, controller, gimbal, DSP, camera, and mission-state capture.
3. Official H.264 callback integration beneath the existing cockpit HUD.
4. Deterministic capture/replay records that store original values and normalized state.
5. A capability handshake recording which queries/listeners actually work on X-Star Premium firmware `V2.0.12`.

### Preserve for independent protocol work

1. USB accessory descriptors, packet framing, endpoint/proxy topology, and reconnect behavior.
2. Autel-specific MAVLink messages and component IDs not safely identifiable as standard MAVLink.
3. Camera HTTP/event/stream endpoints and exact codec negotiation.
4. Meaning, indices, ranges, and bit layouts of the remote `int[]` control-menu payload.
5. Units/update rates where the API supplies values but no contract.
6. Raw Starpoint optical-flow data, raw sonar data, and any private sensor bus.
7. An offline path that does not depend on an Autel authentication/whitelist service.

### Do not guess

- Do not assign undocumented USB IP addresses or ports from constant names alone.
- Do not label shared EVO visual APIs as X-Star capabilities.
- Do not map opaque remote-array indices without repeatable capture evidence.
- Do not infer RF signal units or polarity from a field named `value`.
- Do not infer telemetry units or frames where the generated docs omit them.
- Do not translate unknown Autel frames into flight state merely because their shape resembles MAVLink.

## Decision

The official SDK route is now much more than a connection experiment. It is a documented, product-specific semantic bridge for nearly the entire passive cockpit: flight state, batteries, ultrasonic height, controller, DSP/RF, gimbal, R12 camera, albums, mission status, and H.264 FPV.

It is **not** a durable transport specification. Revival should use it to accelerate the read-only cockpit and to generate known-good captures, while keeping `OpenXStarPlatform` as the long-term independent path. Every capture should retain unknown bytes and original SDK values so future protocol work can be validated without inventing semantics.
