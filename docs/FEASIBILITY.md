# Technical Feasibility — Initial Assessment

## Decision

**GO — proceed to proof-of-concept.**

The project is not yet proven end-to-end, but static inspection of the legacy Starlink Android application exposes enough architectural evidence to justify implementation.

## MAVLink / Flight Protocol

Observed package/function naming includes:

```text
com.MAVLink.*
com.autel.sdk.AutelNet.AutelMavlinkCore.*
mavlink_start
mavlink_stop
```

This strongly indicates MAVLink/PX4-derived concepts with Autel-specific layers rather than a wholly opaque flight protocol.

## Video

Observed legacy endpoint:

```text
rtsp://192.168.1.200:8557/PSIA/Streaming/channels/2?videoCodecType=H.264
```

A modern implementation should use a maintained Android decoder pipeline rather than porting the obsolete bundled player.

## Camera / Events

Observed endpoint strings include:

```text
http://192.168.1.11/camera
http://192.168.1.11/events
http://127.0.0.1:8080/camera
http://127.0.0.1:8080/events
```

## USB / Proxy Layer

Observed native symbols include:

```text
UsbPkt_Init
UsbPkt_Compose
UsbPkt_ComposeV
UsbPkt_Size
UsbPkt_Parse
Java_com_autel_video_NetWorkProxyJni_StartProxy
Java_com_autel_video_NetWorkProxyJni_StopProxy
Java_com_autel_video_NetWorkProxyJni_WriteProxyData
Java_com_autel_video_NetWorkProxyJni_ReadProxyData
```

The leading hypothesis is that the original app used a USB framing/proxy layer to expose camera, video and MAVLink services as network-like endpoints.

## Primary Technical Risk

**USB/proxy reconstruction** is currently the largest unknown. MAVLink decoding, telemetry display, maps, H.264 playback and modern Android UI are comparatively conventional once transport is established.

## PoC Success Criteria

A modern Android device must:

1. detect the X-Star Premium controller;
2. establish transport;
3. receive a heartbeat;
4. display meaningful telemetry;
5. establish camera connectivity; and
6. show live FPV video.

No autonomous flight command is required for this gate.
