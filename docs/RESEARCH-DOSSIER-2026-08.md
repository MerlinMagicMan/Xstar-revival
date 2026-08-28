# X-Star Revival Research Dossier — August 2026

## Current Decision

**Proceed.** The surviving evidence is now strong enough to justify a structured proof of concept.

The project has four independent sources of technical leverage:

1. the final legacy Starlink Android APK;
2. a potentially working legacy tablet running Starlink;
3. the aircraft's front USB maintenance/flight-data interface; and
4. surviving PX4-derived flight-log tooling and community research.

The battery problem also has a concrete engineering target: community teardown and rebuild work identifies a Texas Instruments `bq3055` smart-battery manager, consistent with the original 4-series lithium-polymer pack.

## Evidence Labels

- `CONFIRMED_STATIC` — observed directly in the uploaded APK or official documentation.
- `CONFIRMED_OFFICIAL` — published by Autel, the FCC, TI, PX4 or another primary source.
- `COMMUNITY_REPORTED` — credible owner/researcher report not yet reproduced by this project.
- `HYPOTHESIS` — inference awaiting live capture.

## 1. Official Product Baseline

`CONFIRMED_OFFICIAL`

- The X-Star Premium mobile device connects to the remote controller by USB.
- The original manual lists Android 4.0 or later for Starlink-era devices.
- X-Star Premium command/telemetry and video use separate radio systems: approximately 5.8 GHz and 900 MHz.
- The battery is rated 14.8 V and 4900 mAh, reports level/current/voltage/life/temperature, performs balancing and temperature protection, and uses approximately 17 V charge and 10.8 V discharge limits.
- Aircraft flight data can be read through the aircraft's front USB connection.
- The last official Android release remains Starlink 2.0.3.20 from October 2017.

Official download archive:
- https://shop.autelrobotics.com/pages/x-star-downloads

## 2. Legacy APK Architecture

`CONFIRMED_STATIC`

Uploaded artifact:

```text
Package: com.autel.maxlink
Version family: 2.0.3.20
SHA-256: 01d6aba3ebbb1e1672273e20dfe4fb44bfaf0a6c1c10499ed57c08e4f2e34702
```

The APK is signed with an Autel certificate issued in 2015 and contains only legacy 32-bit `armeabi` native libraries.

Important packages/symbols include:

```text
org.mavlink.library.*
com.autel.sdk.AutelNet.AutelMavlinkCore.*
UsbDevice / UsbInterface / UsbEndpoint / bulkTransfer
mavlink_start / mavlink_stop
UsbPkt_Init / UsbPkt_Compose / UsbPkt_Parse
NetWorkProxyJni_StartProxy / StopProxy / ReadProxyData / WriteProxyData
```

Observed services/endpoints:

```text
rtsp://192.168.1.200:8557/PSIA/Streaming/channels/2?videoCodecType=H.264
http://192.168.1.11/camera
http://192.168.1.11/events
http://127.0.0.1:8080/camera
http://127.0.0.1:8080/events
```

Observed addresses also include:

```text
192.168.1.2
192.168.1.11
192.168.1.20
192.168.1.200
10.1.1.1
10.1.1.2
```

### Working transport hypothesis

`HYPOTHESIS`

The Android app exchanges multiplexed packets with the remote over USB. A native proxy maps one or more packet channels into local or virtual network services carrying:

- MAVLink/Autel telemetry and commands;
- camera HTTP requests/events; and
- RTSP/H.264 video.

The most important unknown is the `UsbPkt_*` framing/channel map, not MAVLink or H.264 themselves.

## 3. Radio Architecture and FCC Evidence

`CONFIRMED_OFFICIAL`

Relevant U.S. FCC identifiers include:

```text
Aircraft: 2AGNTAC5809A
Remote:   2AGNTRC5809A
```

The filings expose grants in both the 902–928 MHz region and the 5.7–5.8 GHz region and include public external/internal photo exhibits and RF test reports.

References:
- https://fcc.report/FCC-ID/2AGNTAC5809A
- https://fcc.report/FCC-ID/2AGNTRC5809A

This independently supports the manual's two-link architecture. The mobile app does not need to reproduce either over-the-air link; it needs to speak to the existing remote controller over USB.

## 4. Aircraft USB / PX4-NuttX Path

`COMMUNITY_REPORTED`, supported by PX4 architecture

X-Star researchers reported that the aircraft's front USB port enumerates as a serial interface and exposes a NuttX shell at 115200 baud. Read-only process inspection reportedly showed running `MAVLINK` and `UAVCAN` tasks.

This is important because it provides a second path to validate the flight-controller architecture independently of Starlink:

```text
PC -> aircraft front USB -> NuttX shell / flight-data interface
```

Initial work must be read-only, with props removed. Suitable discovery commands, only where supported, include `help`, `ver`, `top`, `ps`, `free`, `df`, and `boardinfo`. Motor, PWM, mixer, bootloader, firmware-write and calibration commands are out of scope.

Community thread:
- https://autelpilots.com/threads/proposal-for-reverse-engineering-forum.1596/

PX4 MAVLink shell reference:
- https://docs.px4.io/main/en/debug/mavlink_shell.html

## 5. Flight Logs Are a Major Shortcut

`CONFIRMED_BY_SURVIVING_TOOLING`

The open-source X-Star Log Viewer and its documentation map many X-Star log records to PX4-style flight data. Known records include attitude, IMU, GPS, local/global position, home position, battery, RC channels, actuator outputs, power and flight-control state.

Useful surviving resources:
- https://github.com/tomSny/XStarLogViewer
- https://xslogs.weebly.com/docs.html

Important observations:

- X-Star logs can be retrieved through the aircraft/Starlink flight-data workflow.
- Firmware V2 substantially changed record layouts and increased the message-format structure size.
- The surviving project is derived from Mission Planner/PX4 tooling, so licensing must be reviewed before reusing implementation code.
- The format documentation can still guide a clean parser and provide names/units for live telemetry.

## 6. Battery Architecture

`CONFIRMED_OFFICIAL` plus `COMMUNITY_REPORTED`

Official pack baseline:

```text
Chemistry: lithium polymer
Nominal voltage: 14.8 V
Capacity: 4900 mAh
Likely series arrangement: 4S
```

Multiple recent owner reports identify the pack manager as a Texas Instruments `bq3055`. TI specifies the bq3055 as a 2–4 series-cell Li-ion/Li-polymer pack manager with SMBus, balancing, protection, voltage/current/temperature measurement, capacity gauging and authentication capability.

Community rebuild reports describe:

- replacing the 4S cell assembly while retaining the original enclosure/electronics;
- communicating with the gauge over SMBus/I²C-compatible tooling;
- resetting/reprogramming retained health/capacity state; and
- completing subsequent flight testing.

This does **not** make every recovery technique safe. It does establish a credible path toward a documented professional rebuild workflow and, eventually, a service tool.

Primary component reference:
- https://www.ti.com/product/BQ3055

## 7. Firmware Preservation

The commonly referenced final X-Star Premium firmware filename is:

```text
X3P_FW_900M_V2.0.12.bin
```

The original hosted copy has become difficult to obtain, while community mirrors survive. Before storing or redistributing firmware, the project should:

1. verify provenance;
2. calculate cryptographic hashes;
3. identify embedded component versions;
4. record the official filename/source history;
5. distinguish archival metadata from redistribution rights; and
6. avoid flashing during early research.

Firmware updating is a separate risk domain and is not required to prove modern Android connectivity.

## 8. High-Value Artifact: Working Legacy Tablet

A tablet with Starlink already installed may be more useful than another APK mirror because it can expose:

- exact installed APK/version/signature;
- Android USB attach behavior;
- controller VID/PID, interfaces and endpoints;
- logcat output during connection;
- local socket/port usage;
- Starlink-created files and flight records;
- controller, aircraft and camera version information; and
- action-to-packet correlations during controlled testing.

Do not factory-reset, update, optimize, clean or uninstall anything before preservation. Follow `LEGACY-TABLET-PRESERVATION.md`.

## 9. Ordered Technical Attack Plan

### Milestone A — Preserve references

1. clone the working tablet's APK and accessible Starlink files;
2. record all hardware/software/firmware versions;
3. preserve at least one flight log;
4. archive hashes and metadata for every artifact.

### Milestone B — Identify transport

1. enumerate remote USB descriptors on the old tablet and a modern Android device;
2. capture Starlink logcat while connecting;
3. identify bulk/control endpoints;
4. correlate `UsbPkt_*` framing with observed transfers;
5. identify packet channels carrying MAVLink, HTTP and video.

### Milestone C — Read-only modern PoC

1. detect the remote;
2. open the required USB interface;
3. parse framing;
4. identify a MAVLink heartbeat;
5. decode battery/GPS/attitude telemetry;
6. establish the camera/event service;
7. render the RTSP/H.264 stream.

### Milestone D — Hardware preservation

1. photograph and map a non-serviceable battery pack;
2. confirm the bq3055 and surrounding circuitry;
3. capture read-only SBS/SMBus data;
4. map connector, balance and temperature wiring;
5. design a safe validation protocol for rebuilt packs.

## 10. Remaining Unknowns

- remote USB VID/PID, interface classes and endpoint map;
- exact `UsbPkt_*` frame format and channel identifiers;
- standard vs custom MAVLink dialect/checksum details;
- whether RTSP/HTTP are routed through an IP stack or packet-level proxy;
- exact aircraft USB device identity and shell behavior on our unit;
- bq3055 security/seal/authentication configuration;
- final firmware component manifest and trusted hash set;
- whether older signed Starlink releases differ materially in transport implementation.

## Overall Assessment

The project remains technically challenging, but it is no longer a blind reverse-engineering effort. We have recognizable protocol families, explicit endpoints, a likely packet proxy, a maintenance shell, open flight-log knowledge, public RF documentation and a known battery-management platform. The correct next step is controlled artifact preservation followed by read-only live captures.
