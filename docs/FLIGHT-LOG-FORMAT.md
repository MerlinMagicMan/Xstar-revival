# X-Star Flight-Log Research

## Why Flight Logs Matter

The X-Star's surviving flight-log ecosystem gives this project a semantic map of the aircraft before live USB transport is decoded. It can help us identify telemetry messages, units, state fields and firmware differences.

Useful surviving resources:

- X-Star Log Viewer source: https://github.com/tomSny/XStarLogViewer
- X-Star Log Viewer documentation: https://xslogs.weebly.com/docs.html

The viewer was derived from Mission Planner/PX4 tooling. Review its license before reusing implementation code. Prefer a clean parser based on documented/observed formats and test fixtures.

## Acquisition Path

The original X-Star workflow uses the aircraft's front USB connection and Starlink's advanced flight-control settings to read flight data.

Preservation goals:

1. obtain at least one original pre-V2 log if available;
2. obtain at least one V2.x log from our aircraft;
3. preserve the matching aircraft/remote/camera firmware versions;
4. preserve the version of any `sdlog2_dump.py` script bundled with the download;
5. hash every source file before conversion;
6. create redacted fixtures without home coordinates or serials.

## Known Record Families

Surviving documentation maps records including:

| Record family | Likely content |
|---|---|
| `ATT` / `ATSP` / `ATTC` | attitude, setpoints and control |
| `IMU` / `SENS` | inertial sensors and sensor data |
| `LPOS` / `LPSP` | local position and setpoint |
| `GPS` | GNSS data |
| `GPOS` / `GVSP` | global position and velocity/setpoint |
| `HOME` | home-position data |
| `RC` | remote-control channels |
| `OUTn` | actuator outputs |
| `BATT` | voltage, filtered voltage, current and capacity |
| `PWR` | power-system state |
| `STAT` | flight/control status |
| `FLOW` | optical-flow data, where present |
| `ARSP` | airspeed-related data, where present |
| `DBUG` | debug values |
| `TIME` | time synchronization/data |

The record names and exact fields must be verified against actual files from our firmware.

## RC Channel Mapping Reported by Existing Documentation

The surviving documentation assigns:

```text
Channel 0: roll
Channel 1: pitch
Channel 2: yaw
Channel 3: throttle
Channel 4: gimbal
Channel 5: button/action inputs
Channel 6: flight mode
```

Treat this as a working map until reproduced with controlled bench input and logs.

## Firmware V1 vs V2

Community work around X-Star firmware V2 found that:

- the log structure changed substantially;
- many log record types were replaced or expanded;
- the message-format packet length reportedly changed from 89 bytes in older tooling to 333 bytes for the newer format;
- updated PX4-style `sdlog2_dump.py` work could parse the new format;
- `VER_Arch` represented flight-controller firmware, not simply a file-format version.

These are valuable clues but should be verified from preserved scripts and real logs rather than copied uncritically.

## Proposed Clean Parser Architecture

```text
raw .bin
   |
record synchronizer
   |
format-definition records
   |
versioned record decoder
   |
normalized telemetry model
   +--> JSONL
   +--> CSV
   +--> KML/GeoJSON (redacted by default)
   +--> protocol comparison fixtures
```

The parser should:

- retain unknown records and raw bytes;
- never discard fields merely because their meaning is unknown;
- support both V1 and V2 layouts;
- include source offsets for every decoded record;
- distinguish signed/unsigned and scaling assumptions;
- preserve timestamps exactly;
- redact coordinates only in derived/public outputs, never in the private source archive;
- produce deterministic output for tests.

## Cross-Correlation with Live MAVLink

Once live telemetry is available, conduct controlled, props-off tests:

1. hold aircraft stationary;
2. gently change roll/pitch by hand;
3. rotate yaw by hand;
4. move controller sticks without arming;
5. operate gimbal using the physical controller;
6. observe battery telemetry over time;
7. compare live values with resulting log records.

This can identify Autel custom MAVLink messages and field scaling much faster than packet analysis alone.

## Public Fixture Policy

A sanitized public log fixture must remove or transform:

- latitude/longitude/home position;
- serial numbers/device identifiers;
- account identifiers;
- timestamps where they create privacy risk;
- personal filenames or paths.

Document every transformation so parser tests remain reproducible.

## Initial Deliverables

- `docs/flight-log-v1.md`
- `docs/flight-log-v2.md`
- a field dictionary with units/evidence level;
- a small parser independent of Android UI code;
- private original logs with SHA-256 manifests;
- sanitized binary fixtures;
- comparison report between flight logs and live telemetry.

## Open Questions

- Which exact firmware version is installed on our aircraft?
- Are V2 logs standard PX4 SDLOG2 plus Autel records, or a forked container?
- Which records are emitted during power-on but no flight?
- Does battery telemetry include individual cell voltages or only pack-level data?
- Which record reflects flight mode and failsafe state?
- Can the aircraft export logs directly as USB mass storage, serial transfer, or only through a command path?
- Does the working legacy tablet retain historical mobile-side flight records in addition to aircraft logs?
