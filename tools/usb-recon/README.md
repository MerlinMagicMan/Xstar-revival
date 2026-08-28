# USB Reconnaissance Tool

This tool supports Issue #1 and Issue #2 without requiring the legacy tablet.

## Goals

1. identify the X-Star Premium remote's USB VID/PID;
2. inventory interfaces and endpoints;
3. distinguish standard USB classes from vendor-specific transport;
4. optionally capture **bulk-IN only** data for frame-pattern analysis;
5. produce reproducible JSON descriptor fixtures.

## Install

```bash
python -m pip install pyusb
```

On Linux, libusb is usually already available. On Windows, PyUSB also needs a compatible libusb backend/driver for direct endpoint access. Descriptor enumeration can alternatively be cross-checked with USB Device Tree Viewer or USBPcap/Wireshark.

## First run — enumeration only

With the remote disconnected:

```bash
python usb_recon.py --json before.json
```

Connect/power the remote, then:

```bash
python usb_recon.py --json after.json
```

Compare the new device. Once its IDs are known:

```bash
python usb_recon.py --vid 0xVVVV --pid 0xPPPP --json xstar-remote.json
```

Do **not** publish full serial-number strings. Redact them before committing fixtures.

## Optional passive bulk-IN capture

Only after the descriptor map identifies a bulk-IN endpoint:

```bash
python usb_recon.py \
  --vid 0xVVVV \
  --pid 0xPPPP \
  --capture \
  --interface 0 \
  --endpoint 0x81 \
  --seconds 10 \
  --output capture.bin
```

The utility intentionally contains **no USB OUT-transfer implementation**. The first transport phase is passive/read-only.

## Suggested capture sequence

Use separate capture files and a written timestamp for each condition:

1. remote on, aircraft off;
2. remote on, aircraft on with **props removed**;
3. aircraft stationary;
4. gently change aircraft attitude by hand;
5. move one RC stick at a time without arming;
6. operate the gimbal with the physical controller;
7. start/stop the original camera using physical controller controls, if available.

Do not transmit raw packets, arm motors, run missions, calibrate, or flash firmware during this phase.

## What we are looking for

- stable frame headers/magic values;
- fixed/variable packet lengths;
- logical channel IDs;
- MAVLink v1/v2 start bytes inside payloads (`0xFE` / `0xFD`), if present;
- H.264 NAL start codes (`00 00 00 01` / `00 00 01`), if video shares the endpoint;
- traffic-rate changes correlated to telemetry, sticks, camera and gimbal actions.

Raw captures should remain private until identifiers and personal data are reviewed. Commit only sanitized fixtures and derived protocol documentation.
