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

No controller PCB trace should be cut and no firmware should be flashed until the HID and passive SDK routes are disproven. Existing live evidence shows that the native USB keepalive works but controller-upload callbacks produced no data with the aircraft powered off, which suggests the stick stream may depend on the aircraft-side relay.

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
UI. The next experiment is therefore a receive-only trace of the registered
handlers for selectors `0x81` and `0x210`. If that message path can be
exposed through the controller's existing ports, no controller modification or
external stick adapter is needed. See `REMOTE-CONTROLLER-FIRMWARE.md` for the
reproducible address map and the limits of this finding.

## Coverage and remaining boundary

The simulator can verify the app UI, physical control ergonomics, axis curves, button assignments, command lifecycle, flight dynamics, missions, smart-flight modes, camera/gimbal behavior, warnings, failure injection, logging, replay, and Unreal visualization.

It cannot prove the final Autel USB/radio framing, live command acknowledgement semantics, firmware-specific limits, or real aircraft dynamics. Those remain a deliberately thin, staged props-off hardware adapter test after simulator acceptance—not an assumption derived from simulator success.

## Desktop smoke test

```bash
python3 tools/run_simulator_bridge_receiver.py
```

Select **Flight Simulator** in the Android app and connect. Valid frames show the sequence, phase, altitude, yaw, controller channels, and warning count. The Unreal receiver consumes the same protocol on UDP port `46000`.
