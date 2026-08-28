# X-Star Premium Radio and Mobile Transport Map

## Scope

This document separates three different links that are easy to conflate:

1. mobile device ↔ remote controller;
2. remote controller ↔ aircraft command/telemetry; and
3. aircraft camera ↔ remote controller video.

The replacement Android app should initially reproduce only link 1. The original remote and aircraft continue handling both over-the-air links.

## Official Link Architecture

The X-Star Premium manual and FCC material indicate two separate RF systems between the remote and aircraft:

| Function | Approximate band | Notes |
|---|---:|---|
| Command / aircraft telemetry | 5.8 GHz | Factory RC link |
| Camera video downlink | 902–928 MHz | X-Star Premium HD video path |
| Tablet / phone connection | USB | Mobile device connects physically to remote |

Relevant FCC IDs:

```text
Aircraft: 2AGNTAC5809A
Remote:   2AGNTRC5809A
```

Public filing indexes:

- https://fcc.report/FCC-ID/2AGNTAC5809A
- https://fcc.report/FCC-ID/2AGNTRC5809A

The filings include grants, RF test reports and internal/external photo exhibits. Component-level conclusions should be recorded only after the exhibits have been independently reviewed at sufficient resolution.

## Mobile-to-Remote Evidence

The original Starlink APK uses Android USB host APIs and contains references to:

```text
UsbDevice
UsbInterface
UsbEndpoint
bulkTransfer
com.autel.maxifly.usb.attach
```

The native proxy library exposes:

```text
UsbPkt_Init
UsbPkt_Compose
UsbPkt_ComposeV
UsbPkt_Size
UsbPkt_Parse
mavlink_start
mavlink_stop
NetWorkProxyJni_StartProxy
NetWorkProxyJni_StopProxy
NetWorkProxyJni_ReadProxyData
NetWorkProxyJni_WriteProxyData
```

This supports a layered model:

```text
Starlink UI / Autel SDK
          |
MAVLink + camera HTTP + RTSP
          |
Native network proxy / channel mux
          |
Autel USB packet framing
          |
Android USB bulk/control endpoints
          |
X-Star Premium remote
          |
Factory RF/video links
          |
Aircraft
```

## Known Application Endpoints

Observed static strings:

```text
RTSP video:
rtsp://192.168.1.200:8557/PSIA/Streaming/channels/2?videoCodecType=H.264

Camera API:
http://192.168.1.11/camera
http://127.0.0.1:8080/camera

Event API:
http://192.168.1.11/events
http://127.0.0.1:8080/events
```

Other observed IP addresses:

```text
192.168.1.2
192.168.1.11
192.168.1.20
192.168.1.200
10.1.1.1
10.1.1.2
```

## Competing Transport Hypotheses

### Hypothesis A — USB Ethernet-style transport

The controller may expose or emulate an IP network over USB, and the app may connect to remote addresses directly.

Expected evidence:

- a USB network interface;
- Android routes/interfaces created at attach time;
- direct TCP/RTSP sessions to 192.168.1.x;
- little or no custom framing visible to Java code.

### Hypothesis B — Multiplexed proprietary USB tunnel

The controller may expose vendor-specific USB endpoints. `libNetWorkProxy.so` may tunnel virtual IP/HTTP/RTSP/MAVLink streams through proprietary `UsbPkt_*` frames.

Expected evidence:

- vendor-specific interface class;
- bulk IN/OUT endpoints;
- local 127.0.0.1 proxy sockets;
- framing headers around multiple logical channel types;
- no native Android route to 192.168.1.x.

### Hypothesis C — Hybrid

A vendor-specific control path may establish an IP-like data path, while video or MAVLink uses separate USB channels.

This is plausible given the mixture of local-proxy and remote-IP endpoint strings.

## Fastest Discriminating Tests

With the working tablet and props removed:

1. capture `dumpsys usb` before and after controller attach;
2. capture `ip addr` and `ip route` before and after attach;
3. record `/proc/net/tcp*` and `/proc/net/udp*` during FPV;
4. inspect USB interface classes/endpoints on the host;
5. run logcat during attach and FPV startup;
6. correlate traffic with `StartProxy`, `mavlink_start` and RTSP events;
7. only then attempt USB packet capture.

## PoC Design Consequences

Do not begin by porting Autel's obsolete native libraries. The modern app should implement:

- a transport interface independent of protocol parsing;
- an Autel framing parser if required;
- a stream router exposing typed logical channels;
- a MAVLink decoder;
- a camera/event HTTP client;
- an RTSP/H.264 player;
- capture/replay fixtures for offline testing.

Suggested interfaces:

```kotlin
interface XStarTransport {
    val events: Flow<TransportEvent>
    suspend fun connect()
    suspend fun disconnect()
    suspend fun send(channel: LogicalChannel, payload: ByteArray)
}

enum class LogicalChannel {
    MAVLINK,
    CAMERA_HTTP,
    CAMERA_EVENTS,
    VIDEO,
    UNKNOWN
}
```

Outbound writes should remain disabled or strictly allow-listed during the read-only PoC.

## Open Questions

- Exact remote VID/PID?
- Vendor-specific or standard USB class?
- Interface/alternate-setting layout?
- Endpoint addresses and packet sizes?
- `UsbPkt_*` header, length, channel and checksum fields?
- Does video use the same USB framing as MAVLink?
- Is `127.0.0.1:8080` always created, or only for USB-connected X-Star Premium?
- Are 10.1.1.x addresses used by a different model/mode?
- Does the RTSP server require keep-alive or proprietary setup messages?
