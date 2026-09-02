# Controller-in-the-loop simulator

## Objective

Exercise the complete Ground Station operator workflow with a physical controller and a simulated aircraft before any command is allowed near live hardware. The deterministic Kotlin simulator remains the behavioral authority; Unreal Engine supplies the high-fidelity world, aircraft, and camera presentation.

## Implemented path

```text
Android HID/gamepad event
        |
        v
bounded axis normalization -> dead zone -> expo -> sensitivity -> stick mode
        |
        v
SimulatorControlInput
        |
        v
SimulatorXStarPlatform + simulator-only CommandDispatcher
        |
        +--> Ground Station telemetry, warnings, missions, records and diagnostics
        |
        +--> versioned UDP telemetry (simulated=true)
                  |
                  +--> Python reference receiver
                  +--> Unreal 5.8 receive-only visualizer
```

The Android activity consumes controller events only when **Flight Simulator** is the selected source. Releasing or backgrounding the activity zeros every stick channel. Neither the controller mapper nor the UDP visualizer has a reference to the Autel bridge or any aircraft command transport.

## Default controller map

| Physical input | Simulator action |
|---|---|
| Left stick | Mode 2: throttle/yaw; Mode 1: pitch/yaw |
| Right stick | Mode 2: pitch/roll; Mode 1: throttle/roll |
| Extra axis / wheel | Gimbal pitch |
| START | Arm/disarm |
| A | Take off |
| B | Land |
| X / camera | Take photo |
| Y / record | Start/stop recording |
| Left-stick click | Return to Home |
| Right-stick click / Back | Cancel Return to Home |
| L1 | Configured C1 action |
| R1 | Configured C2 action |

Every discrete action still passes through normal simulator command validation and acknowledgement state. A controller button cannot bypass arming, flight-state, mission, or safety validation.

## Using the factory X-Star controller

The factory controller presents an Android Open Accessory identity rather than a proven standard HID gamepad. Three reversible routes are retained, in this order:

1. **Android HID path** — if a controller firmware/mode exposes axes and buttons through Android `InputDevice`, it works with the mapper directly.
2. **Official SDK passive path** — the validated AAR exposes `setControlMenuListener(int[])` and navigation-button callbacks. The `int[]` layout is still intentionally opaque; a non-empty capture with one control moved at a time is required before mapping indices or ranges.
3. **External USB-HID adapter** — if the controller will not expose inputs without an aircraft relay, use a removable microcontroller interface at a documented controller/trainer signal point. It should present a standard gamepad and must not inject signals into the controller radio path.

No controller PCB trace should be cut. Existing live evidence shows that the
native USB keepalive works but controller-upload callbacks produced no data
with the aircraft powered off, which suggests the stick stream may depend on
the aircraft-side relay. No firmware should be flashed until its package
integrity checks and a complete backup/recovery route are proven.

The 2026-09-02 office retest closed the remaining software-only gaps. Android successfully matched
and granted permission for the controller's exact `ammlab.org / HelloADK / 1.0` accessory identity.
Two new bounded receive-only captures remained exactly zero bytes while the left stick moved through
full travel. The only older non-empty raw capture contained seven identical native keepalive replies
and no additional payload. The controller's reserved Micro-USB CDC service port also emitted no
startup or stick data. With no aircraft available, the standard accessory path, official SDK upload
path, and reserved service port therefore expose no usable control stream. Route 3 remains the
proven aircraft-off fallback; its electrical attachment must remain
removable and isolated from the controller radio/flight-command path.

Static decoding of the final V1.0.1.5 controller application has now opened a
preferable intermediate route. The stock firmware already samples four axes,
maps the selector/photo/record/knob/settings controls, and builds control
messages through one common dispatcher. It also preserves a simulated-flight
UI. The discrete-control stream is now traced through framed messages to the
MCU's internal USART1 path. The stick stream is traced through an `0xAA` frame,
a bounded buffer and 8-byte frames to the MCU's bxCAN1 peripheral. These two
known stock paths support either a later passive board trace or the selected
software-only rerouting experiment. See `REMOTE-CONTROLLER-FIRMWARE.md` for the
reproducible address map, the matching FCC internal-board photographs and the
limits of this finding.

Static pin initialization narrows the passive targets further. The discrete
control stream uses USART1 at 115,200 baud on PA9/TX and PA10/RX; a receive-only
capture needs PA9 and ground only. The stick stream uses remapped CAN1 on
PB8/RX and PB9/TX at 1 Mbit/s. Capture that stream from CAN-H/CAN-L after its
transceiver, using an isolated adapter in listen-only mode. MCU logic pins must
not be connected directly to a USB CAN adapter.

The selected next route is software-only and does not require opening the
controller. A hash-locked offline patcher now replaces only the stock stick
callback: it builds the same bounded `0xAA` frame, sends that frame through the
existing USART1 framed-data routine, and deliberately leaves the aircraft CAN
stick queue inactive. The controller's normal button callback is unchanged.
The RC-PRO wrapper check is now solved, and exact-hash rebuilders produce both
the replacement component and a structurally complete aggregate package. A
full offline re-extraction verifies every component MD5, but the aggregate
header field at offset `0x07` remains unlabelled and no controller
backup/recovery procedure has been proven. The research package therefore must
not be installed. Once those safeguards exist, the first live question is
whether the separate USB/video processor forwards the rerouted bytes to the
Android accessory link. See `REMOTE-CONTROLLER-FIRMWARE.md` for the exact patch
boundary and checks.

The Android input lab is ready for that test. Its incremental decoder accepts
only checksum-valid nested `0xA5`/`0xAA` channel-3 frames, extracts the first
four big-endian axes and normalizes the statically proven `0x400` center and
`0x299` span. It deliberately labels them axis 0-3 until one-control-at-a-time
movement proves the physical ordering; it does not guess a flight mapping.

## Coverage and remaining boundary

The simulator can verify the app UI, physical control ergonomics, axis curves, button assignments, command lifecycle, flight dynamics, missions, smart-flight modes, camera/gimbal behavior, warnings, failure injection, logging, replay, and Unreal visualization.

It cannot prove the final Autel USB/radio framing, live command acknowledgement semantics, firmware-specific limits, or real aircraft dynamics. Those remain a deliberately thin, staged props-off hardware adapter test after simulator acceptance—not an assumption derived from simulator success.

## Desktop smoke test

```bash
python3 tools/run_simulator_bridge_receiver.py
```

Select **Flight Simulator** in the Android app and connect. Valid frames show the sequence, phase, altitude, yaw, controller channels, and warning count. The Unreal receiver consumes the same protocol on UDP port `46000`.
