# Protocol Map

Status: CONFIRMED_LIVE, CONFIRMED_STATIC, COMMUNITY_REPORTED, or HYPOTHESIS.

| Layer | Current assessment |
|---|---|
| Mobile ↔ RC physical transport | HYPOTHESIS: USB |
| Mobile-side framing | CONFIRMED_STATIC: Autel `UsbPkt_*` symbols |
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
