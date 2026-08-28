# Android PoC

The first Android application is an **engineering probe**, not the consumer UI.

```text
X-STAR REVIVAL LAB

USB
○ Controller

PROTOCOL
○ Transport
○ MAVLink heartbeat

AIRCRAFT
Battery: —
GPS: —
Satellites: —
Altitude: —

CAMERA
○ API
○ FPV
```

## Implementation Order

1. Android USB enumeration
2. capture controller descriptors
3. bulk/control transfer experiments
4. Autel USB framing parser
5. MAVLink stream detector
6. heartbeat parser
7. telemetry state store
8. camera/proxy transport
9. RTSP player

## Explicit Non-Goals for PoC

- takeoff
- arm/disarm
- motor commands
- mission upload
- waypoint execution
- firmware flashing
