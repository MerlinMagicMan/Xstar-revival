# Proposed Architecture

## Android Stack

- Kotlin
- Jetpack Compose
- Coroutines / Flow
- Android USB Host API
- Media3 / MediaCodec where compatible
- local-first flight logging

## Modules

```text
app
├── ui
├── domain
├── telemetry
├── video
├── camera
├── diagnostics
└── logging

xstar-protocol
├── mavlink
├── autel
├── framing
└── fixtures

xstar-transport
├── usb
├── proxy
└── network

xstar-hardware
└── battery-model
```

## Critical Design Rule

UI code must never construct raw aircraft packets directly.

```text
UI
 -> Domain command
 -> Safety/validation gate
 -> Protocol encoder
 -> Transport
```

For the PoC, outbound writes remain disabled except for messages strictly required to establish/read the connection.
