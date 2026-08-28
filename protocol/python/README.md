# Offline Protocol Core

This package lets X-Star Revival make progress without aircraft/controller hardware.

## Current capabilities

- conservative MAVLink v1/v2 structural scanning
- system/component/message ID inventory
- H.264/RTSP/HTTP signature detection
- deterministic binary replay in configurable chunks
- JSON capture analysis
- unit tests using synthetic fixtures

## Important rule

A structurally plausible MAVLink frame is **not yet considered validated**. CRC-extra verification is deliberately deferred until we identify the exact Autel MAVLink dialect. This avoids encoding assumptions from generic MAVLink into X-Star-specific research.

## Run tests

```bash
cd protocol/python
python -m pip install -e '.[test]'
pytest
```

## Analyze a capture

```bash
python -m xstar_protocol.analyze capture.bin --pretty
```

The output includes candidate MAVLink frames plus RTSP, HTTP and H.264 signatures.

## Hardware-day workflow

When a controller capture becomes available:

1. preserve the original capture and SHA-256;
2. run the analyzer unchanged;
3. compare controller-only vs controller+aircraft captures;
4. identify stable candidate MAVLink system/component IDs;
5. identify video/camera signatures;
6. add only sanitized minimal fixtures to tests;
7. implement CRC/dialect knowledge only after evidence supports it.

## Next offline tasks

- streaming parser that handles frames split across USB reads
- candidate Autel USB framing discovery from repeated prefixes/lengths
- MAVLink heartbeat semantic decoder after dialect confirmation
- capture-diff tooling
- transport-independent telemetry state model
