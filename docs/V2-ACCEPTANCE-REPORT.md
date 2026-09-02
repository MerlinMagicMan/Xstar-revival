# Ground Station v2 Acceptance Report

Date: 2026-09-01

Bench version: `0.3.0-bench.1`

Package: `io.xstarrevival.app`

Minimum / target SDK: 26 / 35

## Result

The Ground Station v2 software milestone satisfies the supplied definition of done. The operator UI, persistence, centralized command lifecycle, simulator behavior, failure scenarios, mission execution, operational maps, camera/gimbal surfaces, media, battery tooling, diagnostics, offline Academy, CI gate, and installable bench APK are present.

The live Autel adapter remains intentionally receive-only. Real motor, flight, mission, camera, gimbal, controller-configuration, and firmware writes are not enabled because the legacy command protocol has not passed the separate live-integration gate. The v2 definition explicitly permits this boundary.

## Definition-of-done matrix

| Requirement | Result | Primary evidence |
|---|---|---|
| Complete operator UI | PASS | `GroundStationV2Activity.kt` routes Garage, Cockpit, Missions, Flights, Media, Aircraft, Settings, and Academy; all active screens have loading/empty/unavailable states. |
| Settings persist | PASS | `GsSettingsStore.kt` normalizes and persists every user setting. |
| Missions persist | PASS | `GsMissionStore.kt` stores full mission plans and safety behaviors. |
| Flight history persists | PASS | `GsPersistence.kt` stores bounded summaries plus replay samples. |
| Recovery history persists | PASS | `GsPersistence.kt` stores a bounded last-known path used by Find My X-Star. |
| Multi-aircraft Garage | PASS | Persistent, swipeable aircraft profiles retain nickname, model, serial, firmware, last connection, battery, location, and health. |
| Central command architecture | PASS | `CommandDispatcher`, `XStarCommand`, validation, transport abstraction, and status history are centralized in app-core. |
| Command lifecycle state | PASS | Every dispatched command reports queued, validating, dispatching, awaiting acknowledgement, and terminal state as applicable. |
| Simulator major commands | PASS | Arm/disarm, takeoff, land, camera, gimbal, RTH, waypoint mission, Orbit, Follow, Course Lock, Home Lock, controller configuration, video-link channel, pause/resume, and abort are modeled. |
| Simulator failure scenarios | PASS | GPS degradation/loss, compass faults, RC/video/link loss and recovery, battery faults, forced landing, and mission failures are selectable and tested. |
| Mission execution testable | PASS | Mission validation/review/execution, progress, finish behavior, lost-link behavior, failsafes, pause/resume/abort, RTH handoff, landing, and completion have deterministic tests. |
| Find My X-Star | PASS | Last-known coordinates/path persist, render on the operational map, open in a mapping app, and export as CSV. |
| Flight Records replay | PASS | Persisted telemetry samples replay with path, timeline controls, metrics, empty/partial states, and CSV export. |
| Functional maps | PASS | Offline-capable pan/zoom map geometry, aircraft/home/path/mission overlays, heading, fit, and map interaction replace placeholder canvases. |
| Camera/gimbal interfaces | PASS | Camera capture/exposure/image/monitoring controls and gimbal position/configuration/calibration interfaces are complete in the simulator; live writes stay disabled. |
| Battery diagnostics | PASS | Pack identity, cells, high/low/delta, current, power, temperature, capacity, health, remaining-time estimate, profiles, history, and abnormal events are implemented for available telemetry. |
| Four warning levels | PASS | Information, advisory, warning, and critical levels are represented independently and shown with text as well as color. Warning/critical haptic/audio behavior is configurable; critical sound remains safeguarded. |
| Advanced diagnostics | PASS | Connection, FC, GPS, IMU, compass, battery, protocol version, packet filters/detail, command/ACK history, bounded buffers, and redacted export are present. |
| Offline Academy | PASS | Nine bundled guides cover the X-Star manual, controller, battery, compass, IMU, missions, troubleshooting, firmware, and recovery. |
| CI gate | PASS | Android CI runs the receive-only audit, 145 Kotlin tests, lint, debug compilation/APK assembly, and artifact upload. Protocol CI covers Python versions 3.10 and 3.12. |
| Installable bench APK | PASS | A v2-signed debug APK was generated with the validated Autel SDK bridge and native libraries for `arm64-v8a` and `armeabi-v7a`. |

## Automated verification

- Kotlin/JVM tests: **145 passed**, 0 failed, 0 skipped.
- Python protocol tests: **12 passed**.
- Android lint: successful. Remaining notices are dependency-update suggestions, the deliberate landscape-first activity policy, and the min-SDK-qualified adaptive icon directory.
- Receive-only Autel audit: successful; **76** discovered Autel control/write calls remain excluded, the USB probe has no data-OUT path, direct CDC setup is limited to two allow-listed volatile class requests, and simulator commands cannot reach hardware.
- Debug APK assembly with the validated Autel AAR: successful.
- APK signature: APK Signature Scheme v2 verified, one signer.
- Validated Autel AAR SHA-256: `138bd68f0986ac7009362cde01f9e54e4ee33e0f2ed2548e382205a59dcd7e17`.

Reproduce the bench build with `tools/build_bench_apk.sh`. The script verifies the AAR and receive-only boundary, runs Android tests and lint, assembles the APK, verifies its signature when `apksigner` is available, and writes a SHA-256 sidecar.

## Bench artifact

- Local path: `artifacts/bench/xstar-ground-station-v0.3.0-bench.1-debug.apk`
- Size: approximately 33 MB
- SHA-256: `b45a3f9562f4b9ef576a3b41f60d085d52a0c0b402b6211bef6f8c77433e84d2`
- Bundled official receive-only bridge: yes
- Bundled Autel native ABIs: `arm64-v8a`, `armeabi-v7a`
- Embedded registered Autel app key: no

The APK is installable and can exercise the full simulator/replay UI plus passive USB-controller detection. The official SDK source is compiled in, but SDK authorization and real aircraft telemetry require rebuilding with the user's registered `AUTEL_APP_KEY`. No key is stored in Git, logs, diagnostic exports, or this artifact.

## Deliberate post-v2 boundary

Physical props-off, controlled-ground, hover, and smart-flight tests were not performed in this software-only environment. They require the user's aircraft/controller, a registered Autel app key, a safe test site, and the staged procedure in the development plan. These stages validate hardware behavior; they do not block the defined v2 software/bench-APK milestone and must not be bypassed to enable live writes.
