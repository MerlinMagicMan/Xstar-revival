# Safety Policy

X-Star Revival interacts with an aircraft capable of causing property damage or serious injury and with high-energy lithium battery packs.

## Flight Software

During Research/PoC:

- Remove propellers for bench testing.
- Do not test motor-start or takeoff commands indoors.
- Do not transmit undocumented commands to a powered aircraft with props installed.
- Keep the factory remote controller connected and authoritative.
- Never disable or bypass factory failsafes merely to achieve feature parity.
- Read-only telemetry and video are the preferred first targets.
- Every write command must have a documented purpose, packet definition, expected response, timeout behavior, and rollback/failsafe analysis before flight use.

## Battery Research

Lithium polymer cells can ignite if punctured, shorted, overcharged, overheated, internally damaged, or incorrectly rebuilt.

The project may document battery architecture and validated service findings, but do not assume a pack is safe because it accepts charge. Cell replacement must preserve correct series configuration, balance wiring, temperature sensing, insulation, current capability, and BMS behavior. Rebuilt packs should be validated under controlled conditions before aircraft use.

## Publication Standard

Community reports should be labeled as confirmed, reproduced, plausible/unverified, or disproven. Do not convert an anecdotal repair into an endorsed procedure without validation.
