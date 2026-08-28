# Roadmap

## Phase 0 — Research / Feasibility

### Legacy APK map
- [ ] Catalog Java/Dalvik packages and JNI entry points
- [ ] Map native libraries
- [ ] Identify USB VID/PID and interfaces
- [ ] Identify network endpoints and ports
- [ ] Map MAVLink dialect/custom messages
- [ ] Map camera HTTP APIs and RTSP behavior
- [ ] Map telemetry/state models and mission path

### Bench capture
- [ ] Run legacy Starlink on a compatible device
- [ ] Connect X-Star Premium remote
- [ ] Capture USB descriptors and traffic
- [ ] Correlate UI actions with messages
- [ ] Produce sanitized protocol fixtures

### Modern Android PoC
- [ ] Detect controller
- [ ] Establish USB transport
- [ ] Receive heartbeat
- [ ] Decode telemetry
- [ ] Connect camera
- [ ] Display H.264 FPV

**Go/No-Go gate:** all six PoC items demonstrated on a modern Android phone.

## Phase 1 — Read-Only Ground Station
Connection status, battery, GPS/satellites, attitude, altitude/speed, home point, warnings, FPV, camera state and flight logging.

## Phase 2 — Safe Device Control
Camera settings, photo/video, gimbal, RTH configuration, flight settings and media management.

## Phase 3 — Feature Parity
Maps, waypoints, orbit, follow, missions, offline maps and advanced flight logs.

## Phase 4 — Hardware Preservation
Battery architecture, BMS, connector/pinout, original cells, telemetry, failure modes, validated diagnostics, cell-replacement research and parts interchange.

## Phase 5 — Community Release
Compatibility matrix, contributor guide, protocol specification, reproducible fixtures, beta program, IP review and flight-test matrix.
