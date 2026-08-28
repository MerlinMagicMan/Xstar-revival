# Android Official-SDK Probe

## Purpose

This module will test the shortest known path to a modern X-Star Premium application: Autel's official legacy Mobile SDK and its explicit `XStarPremiumAircraft` APIs.

This is an engineering diagnostic, not a flight application.

## Safety Mode

The probe is **read-only by design**.

Permitted:

- SDK authentication status
- USB/product connection status
- product type
- component availability
- battery state and cell voltages
- flight-controller state/telemetry
- remote-controller state
- gimbal state
- camera/DSP connection state
- H.264 video reception and display
- structured logs

Prohibited:

- arming/disarming
- motor start
- takeoff/landing
- virtual-stick commands
- mission upload/start
- parameter writes
- calibrations
- firmware updates
- battery/BMS writes

Aircraft propellers must be removed whenever the aircraft is powered during this phase.

## Architecture

```text
SdkProbeApplication
├── SdkAuthenticationProbe
├── ProductConnectionProbe
├── ComponentInventoryProbe
├── BatteryReadOnlyProbe
├── FlightStateReadOnlyProbe
├── VideoReadOnlyProbe
└── DiagnosticsRecorder
```

Every call must pass through a read-only allowlist. The application should not compile any activity or service that exposes write/control APIs.

## Intended UI

```text
X-STAR OFFICIAL SDK PROBE

SDK AUTH
○ Not initialized

USB / PRODUCT
○ No product
Type: —

COMPONENTS
Battery: —
Flight controller: —
Remote: —
Gimbal: —
Camera/DSP: —
Codec: —

TELEMETRY
Pack voltage: —
Cell 1–4: —
Temperature: —
Battery: —
GPS: —
Satellites: —
Altitude: —

VIDEO
○ No stream

[Export diagnostics]
```

## Diagnostic Events

Use structured JSONL records such as:

```json
{
  "time":"2026-08-28T15:00:00Z",
  "event":"sdk_auth_result",
  "success":false,
  "error_code":"...",
  "error_description":"..."
}
```

Required event families:

```text
app_start
sdk_native_library_load
sdk_auth_start
sdk_auth_result
usb_accessory_attached
usb_accessory_permission
product_connect
product_disconnect
product_type
component_inventory
battery_state
flight_state
video_state
video_frame_stats
exception
```

Redact app keys, serial numbers, coordinates and other identifiers from public logs.

## Build Questions to Resolve

- Which official AAR revision should be tested first?
- Does the current AAR contain all required `arm64-v8a` native libraries?
- Can Autel still issue an app key that authorizes the legacy SDK path?
- Must the package remain `com.autel.maxlink`, and how does signing/whitelisting interact with it?
- Does a modern target SDK change USB accessory behavior?
- Does the SDK detect the controller before the aircraft is powered?
- Does it report `PREMIUM`, `X_STAR`, or `UNKNOWN`?
- Can the raw H.264 callback work even if higher-level product support is restricted?

## Test Matrix

| Device | Purpose |
|---|---|
| Legacy tablet | Known-good Starlink behavioral baseline |
| Galaxy S20 | Intermediate Android/ABI compatibility |
| Galaxy S25 Ultra | Primary modern ARM64 target |

Run the same controller, cable, aircraft and battery where possible so device/software differences are isolated.

## First Success Gate

The probe succeeds when the Galaxy S25 Ultra can authenticate, detect the controller/X-Star Premium, report at least one real telemetry value, and receive at least one valid video frame.

A partial connection is still valuable if diagnostics identify exactly where the official path fails.
