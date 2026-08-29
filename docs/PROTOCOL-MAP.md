# Protocol Map

Status: CONFIRMED_LIVE, CONFIRMED_STATIC, COMMUNITY_REPORTED, or HYPOTHESIS.

| Layer | Current assessment |
|---|---|
| Mobile ↔ RC physical transport | CONFIRMED_LIVE: Android Open Accessory over USB |
| Mobile-side framing | CONFIRMED_LIVE: Autel native proxy outer framing and keepalive response |
| Flight telemetry | CONFIRMED_STATIC: MAVLink/PX4-derived code present |
| Autel extensions | CONFIRMED_STATIC: Autel MAVLink core present |
| FPV video | CONFIRMED_STATIC: RTSP + H.264 endpoint string |
| Camera API | CONFIRMED_STATIC: HTTP-like endpoints |
| Event stream | CONFIRMED_STATIC: HTTP-like endpoint |
| RC ↔ aircraft RF | Unknown / not required initially |
| Firmware update protocol | Unknown / out of scope initially |

## Observed Network Addresses

```text
192.168.1.2
192.168.1.11
192.168.1.20
192.168.1.200
10.1.1.1
10.1.1.2
127.0.0.1:8080
```

These require live validation before being treated as stable protocol documentation.

## Aircraft-off Controller Bench Findings

The X-Star Premium controller presented this exact Android Open Accessory identity:

```text
manufacturer: ammlab.org
model:        HelloADK
version:      1.0
```

A bounded aircraft-off bench probe using Autel's native proxy confirmed two-way USB transport. Five
identical 16-byte keepalives were written and five 16-byte responses were received:

```text
phone -> controller: 40 75 74 03 08 00 43 87 00 00 00 00 00 00 00 00
controller -> phone: 40 75 74 03 08 00 43 87 5a 9a 32 b8 00 00 00 00
```

This establishes that accessory discovery, Android permission, descriptor opening, USB output, and
USB input all work. The four TCP listeners exposed by the same proxy routed toward these internal
endpoints:

```text
local 6686 -> 192.168.1.1:6685
local 6685 -> 192.168.1.8:6685
local 6687 -> 192.168.1.11:6685
local 6688 -> 192.168.1.15:6685
```

With the aircraft unpowered, every internal route aborted or timed out. Both SDK generations tested
also produced no remote-controller upload callbacks: the legacy TCP upload subscription and the
newer JSON-over-UDP `IphoneSettingParam` upload subscription. This strongly indicates that the SDK's
stick-data path depends on an aircraft-side relay or product connection even though the phone can
communicate with the controller itself.

Only the native keepalive and the SDK's controller-upload enable/disable subscriptions were sent.
No arm, motor, takeoff, calibration, firmware, mission, parameter-write, or other flight-control
operation was invoked. The changing four-byte response field and all other proprietary frames remain
opaque; the project must not assign semantics to them without repeatable evidence.
