# X-Star Battery / TI bq3055 Research Track

## Status

Community teardown and successful rebuild reports identify the X-Star intelligent-battery pack manager as a Texas Instruments `bq3055`. This must still be confirmed on a physical pack from our hardware, but it is now the leading component hypothesis.

## Official Pack Baseline

From the X-Star manual and charger teardown:

```text
Chemistry: lithium polymer
Nominal voltage: 14.8 V
Capacity: 4900 mAh
Topology: 4 cells in series (4S)
Normal stock-pack target: 16.8 V total / 4.20 V per cell
Manual protection ceiling: approximately 17 V
Approximate discharge cutoff: 10.8 V
Reported values: level, current, voltage, life, temperature
Pack features: balancing, temperature detection/protection
```

The manual's approximately 17 V statement is treated as an overcharge-protection threshold, not the normal charging setpoint. An original 14.8 V X-Star pack should use the standard **4S LiPo** profile, not LiHV. LiHV applies only to a documented rebuild using high-voltage cells with a deliberately validated BMS configuration.

## bq3055 Capabilities

Texas Instruments describes the bq3055 as a 2–4 series-cell Li-ion/Li-polymer battery-pack manager with:

- SMBus 1.1 communications;
- cell balancing;
- pack/cell voltage measurement;
- current and temperature measurement;
- protection and fault handling;
- capacity/state-of-charge gauging;
- configurable data flash;
- authentication capability;
- support for capacities in the X-Star pack's range.

Primary reference:
- https://www.ti.com/product/BQ3055

Relevant TI documents should be archived by title/version and linked from a source manifest rather than copied into the repo unless licensing permits.

## What Community Rebuilds Suggest

Credible owner reports describe the following high-level sequence:

1. open a failed pack and retain the enclosure, connector and control electronics;
2. replace the original 4S lithium-polymer cell assembly with electrically and mechanically appropriate cells;
3. preserve correct balance, current-sense and temperature connections;
4. communicate with the smart gauge over SMBus/I²C-compatible tooling;
5. reset or reprogram retained capacity/health state; and
6. validate the rebuilt pack before completing flight tests.

This indicates that the cells are not the only barrier. The gauge can retain state associated with the old cells and may reject, misreport or later invalidate a replacement assembly unless the smart-pack state is handled correctly.

Community reports are evidence of feasibility, not an endorsed repair procedure.

## Key Engineering Questions

### Electrical architecture

- Confirm exact cell count and parallel arrangement.
- Measure connector pinout and identify data/temperature pins.
- Identify current-sense resistor and high-current switching devices.
- Identify all balancing paths and connector order.
- Identify thermistor type, quantity and placement.
- Determine whether pack negative is switched or always present.
- Determine whether the stock BMS requires a charger-present or activation signal.

### Smart-gauge configuration

- Confirm the IC marking and hardware revision.
- Determine SMBus address and exposed Smart Battery System commands.
- Read manufacturer/device/chemistry/design-capacity data without writing.
- Determine sealed/unsealed/full-access state.
- Determine whether SHA-1 authentication is enabled and how the aircraft verifies the pack.
- Identify permanent-failure flags and safety-status registers.
- Determine which data-flash fields govern learned capacity, cycle count and state of health.
- Determine whether cell replacement requires a reset, learning cycle, data-flash update or all three.

### Aircraft telemetry

- Determine whether Starlink receives pack voltage only or individual cell values.
- Map temperature, current, capacity, cycle count and warning states.
- Correlate BMS readings with live MAVLink/Autel telemetry and flight logs.
- Determine the exact condition that produces `unknown battery` or refusal states.

## Read-Only First Protocol

The first physical battery session should not modify the pack.

1. Select a pack that will not be flown until research is complete.
2. Photograph the exterior, labels, connector and enclosure seams.
3. Measure pack voltage at the main terminals with proper precautions.
4. If opened by a competent technician, photograph both PCB sides and all cell/balance/temperature wiring before disconnecting anything.
5. Identify the gauge and support IC markings.
6. Map ground, power and likely SMBus pins with the pack isolated from the aircraft.
7. Use current-limited, isolated/read-only tooling where practical.
8. Capture standard SBS commands and status without issuing writes, resets, unseal keys or firmware operations.
9. Hash and preserve raw dumps privately; publish redacted decoded fields and methodology.

## Safety Boundary

Lithium-polymer packs can ignite violently after puncture, short circuit, overcharge, internal damage, cell mismatch or incorrect reconstruction.

The project must not treat these as safe merely because they power on:

- swollen packs;
- packs with physical damage;
- packs recovered from extreme over-discharge;
- packs with significant cell imbalance;
- rebuilt packs without verified temperature sensing/protection;
- packs whose BMS state disagrees with measured capacity/voltage;
- packs that produce intermittent `unknown battery` behavior.

A successful charge or short hover is not sufficient validation.

## Proposed Validation Ladder for Rebuilt Packs

A future professional procedure should include staged gates:

1. visual/electrical inspection;
2. insulation and continuity checks;
3. read-only BMS/status validation;
4. low-current controlled charge with temperature monitoring;
5. balance verification at multiple states of charge;
6. measured low-current capacity cycle;
7. controlled higher-current bench load;
8. connector/contact thermal check;
9. aircraft-powered telemetry test with props removed;
10. restrained/low-risk ground run where appropriate;
11. brief low-altitude flight in a safe area;
12. post-test teardown/readback review.

Exact thresholds must be defined by competent battery engineering, original-cell capability and measured aircraft current—not guessed from forum anecdotes.

## Product Opportunity

If the bq3055 hypothesis is confirmed, the project could eventually provide a read-only diagnostic utility that reports:

```text
pack voltage
individual cell voltages
cell delta
current
temperature
remaining/full/design capacity
cycle count
state of charge
state of health
safety and permanent-failure flags
authentication/recognition state
```

A later service tool might support validated rebuild workflows, but write operations must be separated from consumer diagnostics and guarded by explicit technician procedures.

## Deliverables

- high-resolution PCB/cell architecture photos;
- connector and balance/temperature pinout;
- component bill of materials where identifiable;
- read-only SBS command inventory;
- raw/decoded data manifest;
- mapping between BMS values, Starlink and aircraft logs;
- failure-mode catalog;
- cell specification and current requirement;
- validated rebuild test matrix;
- clear distinction between recovery, rebuild and replacement.

## Evidence Status

| Claim | Status |
|---|---|
| Original pack is 14.8 V / 4900 mAh LiPo | Confirmed official |
| Pack is 4S | Confirmed by charger teardown; consistent with official voltage |
| Stock-pack normal target is 16.8 V / 4.20 V per cell | Engineering conclusion from standard 4S LiPo chemistry |
| Manual's approximately 17 V figure is a protection ceiling | Strong interpretation of official protection language |
| Gauge is TI bq3055 | Multiple community reports; physical confirmation needed |
| Gauge communicates by SMBus | Confirmed bq3055 capability; pack confirmation needed |
| Cell replacement has been flight-tested | Community reported |
| Gauge state must be reset/relearned after cell replacement | Strong community evidence; exact procedure/config unknown |
| Authentication is active on X-Star | Unknown |
